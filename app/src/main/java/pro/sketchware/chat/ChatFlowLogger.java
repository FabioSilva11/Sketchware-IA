package pro.sketchware.chat;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/** Writes a private, redacted TXT trace for chat diagnostics. */
public final class ChatFlowLogger {
    private static final String TAG = "ChatFlowLogger";
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_DETAIL_CHARS = 12_000;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(\\\"?(?:authorization|api[_-]?key|token|password|secret|cookie|set-cookie)\\\"?\\s*[:=]\\s*\\\"?(?:bearer\\s+)?)[^\\s,;\\\"}]+"
    );
    // SimpleDateFormats are not thread-safe: keep one per thread (logging happens from many threads).
    private static final ThreadLocal<SimpleDateFormat> FILE_DATE = ThreadLocal.withInitial(
            () -> new SimpleDateFormat("yyyy-MM-dd", Locale.US));
    private static final ThreadLocal<SimpleDateFormat> LINE_DATE = ThreadLocal.withInitial(
            () -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));
    private static volatile Handler writer;
    private static volatile File directory;

    private ChatFlowLogger() { }

    public static synchronized void initialize(Context context) {
        if (writer != null || context == null) return;
        directory = new File(context.getApplicationContext().getFilesDir(), "chat-logs");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Could not create chat log directory");
            return;
        }
        HandlerThread thread = new HandlerThread("chat-log-writer");
        thread.start();
        writer = new Handler(thread.getLooper());
        event("app", "logger_ready", "privateDir=" + directory.getAbsolutePath());
    }

    public static void event(String scope, String event, String detail) {
        Handler handler = writer;
        if (handler == null) return;
        String safeScope = clean(scope);
        String safeEvent = clean(event);
        String safeDetail = clean(detail);
        // Format on the writer thread only, so the shared formatter is never touched concurrently.
        handler.post(() -> append(
                LINE_DATE.get().format(new Date()) + " | " + safeScope + " | "
                        + safeEvent + " | " + safeDetail + "\n"));
    }

    public static void error(String scope, String event, Throwable error) {
        event(scope, event, error == null ? "unknown" : error.getClass().getSimpleName()
                + ": " + error.getMessage());
    }

    /** Redacts credentials before protocol data is shown in chat. */
    public static String redact(String value) {
        if (value == null) return "";
        return SECRET.matcher(value).replaceAll("$1[REDACTED]");
    }

    private static void append(String line) {
        try {
            if (directory == null) return;
            File file = new File(directory, "chat-flow-" + FILE_DATE.get().format(new Date()) + ".txt");
            if (file.exists() && file.length() >= MAX_FILE_BYTES) {
                file = new File(directory, "chat-flow-" + FILE_DATE.get().format(new Date()) + "-overflow.txt");
            }
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not persist chat trace", e);
        }
    }

    private static String clean(String value) {
        if (value == null) return "";
        String result = SECRET.matcher(value).replaceAll("$1[REDACTED]");
        result = result.replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() > MAX_DETAIL_CHARS
                ? result.substring(0, MAX_DETAIL_CHARS) + "…[truncated]" : result;
    }
}
