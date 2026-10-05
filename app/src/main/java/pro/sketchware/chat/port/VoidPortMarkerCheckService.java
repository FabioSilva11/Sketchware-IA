package pro.sketchware.chat.port;

import java.util.ArrayList;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.chat.ProjectPathResolver;

/**
 * Android diagnostic summary - simplified without embedded compiler.
 * The embedded compiler has been removed; this service now returns empty results.
 */
public final class VoidPortMarkerCheckService {
    private static final int MAX_ERRORS = 8;

    private VoidPortMarkerCheckService() {
    }

    public static class LintError {
        public final String code;
        public final String message;
        public final int startLineNumber;
        public final int endLineNumber;

        public LintError(String code, String message, int startLineNumber, int endLineNumber) {
            this.code = code != null ? code : "";
            this.message = message != null ? message : "";
            this.startLineNumber = startLineNumber;
            this.endLineNumber = endLineNumber;
        }
    }

    public static boolean hasErrors(String scId) {
        return false;
    }

    public static String buildErrorContext(String scId) {
        return "";
    }

    public static List<String> topErrors(String scId) {
        return new ArrayList<>();
    }

    public static List<LintError> getLintErrors(String scId, String filePath) {
        return new ArrayList<>();
    }
}
