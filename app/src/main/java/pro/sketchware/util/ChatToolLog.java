package pro.sketchware.util;

import android.util.Log;

/**
 * Centralized, filterable logging for the AI chat tool pipeline.
 *
 * All chat-tool diagnostics use the single tag {@link #TAG} so you can isolate
 * them on-device with:  <pre>adb logcat -s SketchIA-Tool</pre>
 *
 * The goal is to make intermittent bugs visible: exactly which path the model
 * asked for, how the resolver mapped it (or why it returned null / left the
 * project scope), which tool ran, and whether it failed.
 *
 * Logging is gated by {@link #ENABLED} so it can be disabled in release builds
 * with a single flip.
 */
public final class ChatToolLog {

    public static final String TAG = "SketchIA-Tool";

    /** Toggle to silence all chat-tool logs. */
    public static boolean ENABLED = true;

    private ChatToolLog() {
    }

    public static void d(String area, String message) {
        if (ENABLED) {
            Log.d(TAG, "[" + area + "] " + message);
        }
    }

    public static void w(String area, String message) {
        if (ENABLED) {
            Log.w(TAG, "[" + area + "] " + message);
        }
    }

    public static void e(String area, String message, Throwable t) {
        if (ENABLED) {
            Log.e(TAG, "[" + area + "] " + message, t);
        }
    }

    /** Logs a path-resolution outcome (the core of the "left the project folder" bug). */
    public static void pathResolve(String scId, String requested, String mapped, boolean insideScope) {
        if (!ENABLED) {
            return;
        }
        Log.d(TAG, "[path] sc=" + scId
                + " requested=\"" + requested + "\""
                + " mapped=" + (mapped == null ? "<null:OUT_OF_SCOPE>" : "\"" + mapped + "\"")
                + " insideScope=" + insideScope);
    }

    /** Short, single-line preview of a value for logs. */
    public static String preview(String value, int max) {
        if (value == null) {
            return "<null>";
        }
        String flat = value.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "…(" + value.length() + " chars)";
    }
}
