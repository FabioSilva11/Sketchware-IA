package pro.sketchware.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public final class TokenUsageStore {
    public interface Listener {
        void onTokenUsageChanged(long used, long budget, long remaining, long reserved);
    }

    private static final String PREFS = "api_token_usage";
    private static final String KEY_USED = "used_tokens";
    private static final String KEY_BUDGET = "token_budget";
    private static final String KEY_REMAINING = "credits_remaining";
    private static final String KEY_RESERVED = "credits_reserved";
    private static final String KEY_AUTHORITATIVE = "authoritative_wallet";
    public static final long DEFAULT_BUDGET = 1_000L;
    private static final List<Listener> listeners = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private TokenUsageStore() {}

    public static synchronized void record(Context context, long tokens) {
        if (context == null || tokens <= 0) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_AUTHORITATIVE, false)) return;
        long used = Math.max(0L, prefs.getLong(KEY_USED, 0L));
        long updated = used > Long.MAX_VALUE - tokens ? Long.MAX_VALUE : used + tokens;
        prefs.edit().putLong(KEY_USED, updated).apply();
        long budget = budget(context);
        notifyListeners(updated, budget, Math.max(0L, budget - updated), 0L);
    }

    public static long used(Context context) {
        if (context == null) return 0L;
        return Math.max(0L, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_USED, 0L));
    }

    public static long budget(Context context) {
        if (context == null) return DEFAULT_BUDGET;
        return Math.max(1L, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_BUDGET, DEFAULT_BUDGET));
    }

    public static void setBudget(Context context, long budget) {
        if (context == null || budget <= 0) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_BUDGET, budget).apply();
        long used = used(context);
        notifyListeners(used, budget, Math.max(0L, budget - used), 0L);
    }

    public static synchronized void updateFromServer(Context context, long used, long budget) {
        updateFromServer(context, used, budget, Math.max(0L, budget - used), 0L);
    }

    public static synchronized void updateFromServer(
            Context context,
            long used,
            long budget,
            long remaining,
            long reserved
    ) {
        if (context == null || budget <= 0) return;
        long safeUsed = Math.max(0L, used);
        long safeBudget = Math.max(1L, budget);
        long safeReserved = Math.max(0L, Math.min(reserved, safeBudget));
        long safeRemaining = Math.max(
                0L,
                Math.min(remaining, safeBudget - safeReserved));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_USED, safeUsed)
                .putLong(KEY_BUDGET, safeBudget)
                .putLong(KEY_REMAINING, safeRemaining)
                .putLong(KEY_RESERVED, safeReserved)
                .putBoolean(KEY_AUTHORITATIVE, true)
                .apply();
        notifyListeners(safeUsed, safeBudget, safeRemaining, safeReserved);
    }

    public static synchronized void subscribe(Context context, Listener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long used = used(context);
        long budget = budget(context);
        listener.onTokenUsageChanged(
                used,
                budget,
                Math.max(0L, prefs.getLong(KEY_REMAINING, budget - used)),
                Math.max(0L, prefs.getLong(KEY_RESERVED, 0L)));
    }

    public static synchronized void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }

    private static synchronized void notifyListeners(
            long used,
            long budget,
            long remaining,
            long reserved
    ) {
        List<Listener> snapshot = new ArrayList<>(listeners);
        main.post(() -> {
            for (Listener listener : snapshot) {
                listener.onTokenUsageChanged(used, budget, remaining, reserved);
            }
        });
    }
}
