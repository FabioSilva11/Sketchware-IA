package pro.sketchware.chat;



import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Keeps an interactive chat run important while the user temporarily opens
 * another app. The conversation and checkpoints remain app-private in SQLite;
 * this service only owns the required foreground notification.
 */
public final class ChatRunForegroundService extends Service {
    private static final String CHANNEL_ID = "axion_chat_runs";
    private static final int NOTIFICATION_ID = 0xA710;
    private static final String ACTION_START = BuildConfig.APPLICATION_ID + ".chat.RUN_START";
    private static final String EXTRA_PROJECT_ID = "project_id";
    private static final String EXTRA_THREAD_ID = "thread_id";
    private static final String EXTRA_OPERATION_ID = "operation_id";
    private static final String EXTRA_STATUS = "status";

    private static volatile ChatRunForegroundService instance;
    private static volatile String activeOperationId = "";
    private static volatile String activeProjectId = "";
    private static volatile String activeThreadId = "";
    private static volatile String activeStatus = "";

    public static void start(Context context, String projectId, String threadId,
                             String operationId, String status) {
        if (context == null || !ChatMessage.hasVisibleText(operationId)) return;
        activeOperationId = operationId.trim();
        activeProjectId = safe(projectId);
        activeThreadId = safe(threadId);
        activeStatus = safe(status);
        Intent intent = new Intent(context.getApplicationContext(), ChatRunForegroundService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROJECT_ID, activeProjectId)
                .putExtra(EXTRA_THREAD_ID, activeThreadId)
                .putExtra(EXTRA_OPERATION_ID, activeOperationId)
                .putExtra(EXTRA_STATUS, activeStatus);
        try {
            ContextCompat.startForegroundService(context.getApplicationContext(), intent);
        } catch (RuntimeException error) {
            // SQLite checkpoints remain active even on devices that reject a
            // foreground-service start because of vendor battery restrictions.
            ChatFlowLogger.error("background", "chat_service_start_failed", error);
        }
    }

    public static void update(Context context, String operationId, String status) {
        if (context == null || !matches(operationId)) return;
        activeStatus = safe(status);
        ChatRunForegroundService service = instance;
        if (service != null) {
            service.publishNotification();
        }
    }

    public static void stop(Context context, String operationId) {
        if (!matches(operationId)) return;
        activeOperationId = "";
        activeProjectId = "";
        activeThreadId = "";
        activeStatus = "";
        ChatRunForegroundService service = instance;
        if (service != null) {
            service.stopForeground(STOP_FOREGROUND_REMOVE);
            service.stopSelf();
        } else if (context != null) {
            try {
                NotificationManagerCompat.from(context.getApplicationContext())
                        .cancel(NOTIFICATION_ID);
            } catch (SecurityException ignored) {
            }
        }
    }

    private static boolean matches(String operationId) {
        return ChatMessage.hasVisibleText(operationId)
                && operationId.trim().equals(activeOperationId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String requestedOperation = intent == null ? ""
                : safe(intent.getStringExtra(EXTRA_OPERATION_ID));
        String requestedProject = intent == null ? ""
                : safe(intent.getStringExtra(EXTRA_PROJECT_ID));
        String requestedThread = intent == null ? ""
                : safe(intent.getStringExtra(EXTRA_THREAD_ID));
        String requestedStatus = intent == null ? ""
                : safe(intent.getStringExtra(EXTRA_STATUS));

        // Android requires startForeground promptly even if this delayed start
        // belongs to a run that already completed before Service.onCreate.
        startForeground(NOTIFICATION_ID, buildNotification(
                requestedProject, requestedThread, requestedStatus));
        if (!requestedOperation.equals(activeOperationId)) {
            if (activeOperationId.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(
                        activeProjectId, activeThreadId, activeStatus));
            }
            return START_NOT_STICKY;
        }
        publishNotification();
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void publishNotification() {
        try {
            NotificationManagerCompat.from(this).notify(
                    NOTIFICATION_ID,
                    buildNotification(activeProjectId, activeThreadId, activeStatus));
        } catch (SecurityException ignored) {
            // The foreground service remains valid even when the user denied
            // notification permission on Android 13+.
        }
    }

    private Notification buildNotification(String projectId, String threadId, String status) {
        Intent openChat = new Intent(this, ChatActivity.class)
                .putExtra("sc_id", safe(projectId))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                Math.abs(safe(threadId).hashCode()),
                openChat,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String visibleStatus = ChatMessage.hasVisibleText(status)
                ? status.trim() : getString(R.string.chat_status_thinking);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_axion)
                .setContentTitle(getString(R.string.chat_background_notification_title))
                .setContentText(visibleStatus)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.chat_background_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.chat_background_channel_description));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
