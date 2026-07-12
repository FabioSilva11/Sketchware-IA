package mod.hey.studios.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.utility.Network;

/** Notifies installed builds whenever the repository main branch advances. */
public final class AppUpdateNotifier {
    private static final String PREFS = "app_update_notifier";
    private static final String KEY_LAST_CHECK = "last_commit_check";
    private static final String KEY_LAST_NOTIFIED_SHA = "last_notified_commit_sha";
    private static final String KEY_UNKNOWN_BUILD_BASELINE = "unknown_build_baseline_sha";
    private static final String CHANNEL_ID = "app_updates";
    private static final int NOTIFICATION_ID = 7001;
    private static final long CHECK_INTERVAL_MS = 15L * 60L * 1000L;
    private static Handler monitorHandler;

    private AppUpdateNotifier() {}

    public static void checkForUpdates(Context context) {
        Context appContext = context.getApplicationContext();
        startPeriodicMonitoring(appContext);
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return;
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply();

        new Network().get(appContext.getString(R.string.link_github_main_commit_url), response -> {
            CommitInfo latest = parseCommit(response);
            if (latest == null) return;

            String installedSha = BuildConfig.GIT_HASH == null ? "" : BuildConfig.GIT_HASH.trim();
            if (installedSha.isEmpty() || "unknown".equalsIgnoreCase(installedSha)) {
                String baseline = prefs.getString(KEY_UNKNOWN_BUILD_BASELINE, "");
                if (baseline.isEmpty()) {
                    prefs.edit().putString(KEY_UNKNOWN_BUILD_BASELINE, latest.sha).apply();
                    return;
                }
                installedSha = baseline;
            }
            if (latest.sha.equalsIgnoreCase(installedSha)
                    || latest.sha.equals(prefs.getString(KEY_LAST_NOTIFIED_SHA, ""))) return;

            showUpdateNotification(appContext, latest);
            prefs.edit().putString(KEY_LAST_NOTIFIED_SHA, latest.sha).apply();
        });
    }

    private static synchronized void startPeriodicMonitoring(Context context) {
        if (monitorHandler != null) return;
        monitorHandler = new Handler(Looper.getMainLooper());
        monitorHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkForUpdates(context);
                monitorHandler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        }, CHECK_INTERVAL_MS);
    }

    private static CommitInfo parseCommit(String response) {
        if (response == null || response.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(response);
            String sha = root.optString("sha", "").trim();
            String url = root.optString("html_url", "").trim();
            JSONObject commit = root.optJSONObject("commit");
            String message = commit == null ? "" : commit.optString("message", "").trim();
            int newline = message.indexOf('\n');
            if (newline >= 0) message = message.substring(0, newline).trim();
            if (sha.isEmpty()) return null;
            return new CommitInfo(sha, message, url);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void showUpdateNotification(Context context, CommitInfo commit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        String target = commit.url.isEmpty() ? context.getString(R.string.link_github_url) : commit.url;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID,
                new Intent(Intent.ACTION_VIEW, Uri.parse(target)),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String shortSha = commit.sha.substring(0, Math.min(7, commit.sha.length()));
        String detail = commit.message.isEmpty() ? shortSha : commit.message;
        String text = context.getString(R.string.update_notification_message, detail);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sketchware_24)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static final class CommitInfo {
        final String sha;
        final String message;
        final String url;
        CommitInfo(String sha, String message, String url) {
            this.sha = sha;
            this.message = message;
            this.url = url;
        }
    }
}
