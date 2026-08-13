package mod.jbk.diagnostic;

import android.content.Context;
import android.content.Intent;

import com.besome.sketch.tools.CompileLogActivity;

import java.io.FileReader;
import java.io.IOException;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class CompileErrorSaver {

    private static final String MESSAGE_NO_COMPILE_ERRORS_SAVED = "No compile errors have been saved yet.";
    private static final int DISPLAY_PREVIEW_HEAD_CHARS = 96 * 1024;
    private static final int DISPLAY_PREVIEW_TAIL_CHARS = 64 * 1024;
    private static final int DISPLAY_PREVIEW_MAX_CHARS = DISPLAY_PREVIEW_HEAD_CHARS + DISPLAY_PREVIEW_TAIL_CHARS;

    private final String sc_id;
    private final String path;

    /**
     * Create this helper class for saving compile errors.
     *
     * @param sc_id The Sketchware project ID for the project to operate on, like 605
     */
    public CompileErrorSaver(String sc_id) {
        this.sc_id = sc_id;
        path = FilePathUtil.getLastCompileLogPath(sc_id);
    }

    /**
     * Save a compile error in the project's last compile error file.
     *
     * @param errorText The text to save, if possible, with detailed messages
     */
    public void writeLogsToFile(String errorText) {
        if (logFileExists()) FileUtil.deleteFile(path);
        FileUtil.writeFile(path, errorText);
    }

    /**
     * Opens {@link CompileLogActivity} and shows the user the last compile error.
     */
    public void showLastErrors(Context context) {
        Intent intent = new Intent(context, CompileLogActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("showingLastError", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    /**
     * Clear the last saved error text.
     */
    public void deleteSavedLogs() {
        FileUtil.deleteFile(path);
    }

    /**
     * @return The last saved error text
     */
    public String getLogsFromFile() {
        if (!logFileExists()) return null;
        return FileUtil.readFile(path);
    }

    public String getDisplayLogsFromFile() {
        if (!logFileExists()) return null;
        return readDisplayPreview(path);
    }

    public String getPath() {
        return path;
    }

    public static String createDisplayPreview(String logs, String sourceDescription) {
        if (logs == null || logs.length() <= DISPLAY_PREVIEW_MAX_CHARS) {
            return logs;
        }

        int tailStart = Math.max(DISPLAY_PREVIEW_HEAD_CHARS, logs.length() - DISPLAY_PREVIEW_TAIL_CHARS);
        return logs.substring(0, DISPLAY_PREVIEW_HEAD_CHARS)
                + buildTruncatedNotice(logs.length(), tailStart - DISPLAY_PREVIEW_HEAD_CHARS, sourceDescription)
                + logs.substring(tailStart);
    }

    private static String readDisplayPreview(String path) {
        StringBuilder prefix = new StringBuilder(DISPLAY_PREVIEW_MAX_CHARS + 1);
        StringBuilder tail = new StringBuilder(DISPLAY_PREVIEW_TAIL_CHARS + 8192);
        long totalChars = 0;

        try (FileReader reader = new FileReader(path)) {
            char[] buffer = new char[8192];
            int length;
            while ((length = reader.read(buffer)) > 0) {
                totalChars += length;

                int remainingPrefix = DISPLAY_PREVIEW_MAX_CHARS + 1 - prefix.length();
                if (remainingPrefix > 0) {
                    prefix.append(buffer, 0, Math.min(length, remainingPrefix));
                }

                tail.append(buffer, 0, length);
                if (tail.length() > DISPLAY_PREVIEW_TAIL_CHARS) {
                    tail.delete(0, tail.length() - DISPLAY_PREVIEW_TAIL_CHARS);
                }
            }
        } catch (IOException e) {
            return "Unable to read compile log preview: " + e.getMessage();
        }

        if (totalChars <= DISPLAY_PREVIEW_MAX_CHARS) {
            return prefix.toString();
        }

        String head = prefix.substring(0, Math.min(DISPLAY_PREVIEW_HEAD_CHARS, prefix.length()));
        long omittedChars = Math.max(0, totalChars - head.length() - tail.length());
        return head + buildTruncatedNotice(totalChars, omittedChars, path) + tail;
    }

    private static String buildTruncatedNotice(long totalChars, long omittedChars, String sourceDescription) {
        StringBuilder notice = new StringBuilder();
        notice.append("\n\n----------\n");
        notice.append("Compile log preview truncated to avoid Android TextView OutOfMemoryError.\n");
        notice.append("Showing the beginning and the end of the log only.\n");
        notice.append("Full log size: ").append(totalChars).append(" chars. Omitted middle: ")
                .append(omittedChars).append(" chars.\n");
        if (sourceDescription != null && !sourceDescription.trim().isEmpty()) {
            notice.append("Full log source: ").append(sourceDescription).append("\n");
        }
        notice.append("----------\n\n");
        return notice.toString();
    }

    /**
     * Check if the last saved error text file exists.
     */
    public boolean logFileExists() {
        return FileUtil.isExistFile(path);
    }
}
