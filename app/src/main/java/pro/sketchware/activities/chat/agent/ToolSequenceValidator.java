package pro.sketchware.activities.chat.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * ToolSequenceValidator enforces correct tool execution order and dependencies.
 *
 * Critical rules:
 * - MUST read_file before edit_file
 * - MUST search before read (if file path unknown)
 * - MUST create before write
 * - MUST verify after critical mutations
 *
 * This prevents the agent from:
 * - Editing files without reading them first
 * - Creating duplicate files
 * - Making blind changes
 *
 * Inspired by Cursor's tool dependency validation and Void IDE's sequence enforcement.
 */
public class ToolSequenceValidator {

    /**
     * Result of validation.
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final String errorMessage;
        private final String requiredPredecessorTool;
        private final String suggestion;

        private ValidationResult(boolean isValid, String errorMessage,
                                String requiredPredecessorTool, String suggestion) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.requiredPredecessorTool = requiredPredecessorTool;
            this.suggestion = suggestion;
        }

        @NonNull
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null, null);
        }

        @NonNull
        public static ValidationResult invalid(@NonNull String errorMessage) {
            return new ValidationResult(false, errorMessage, null, null);
        }

        @NonNull
        public static ValidationResult requiresPredecessor(@NonNull String errorMessage,
                                                          @NonNull String requiredTool,
                                                          @Nullable String suggestion) {
            return new ValidationResult(false, errorMessage, requiredTool, suggestion);
        }

        public boolean isValid() {
            return isValid;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean requiresPredecessor() {
            return requiredPredecessorTool != null;
        }

        @Nullable
        public String getRequiredPredecessorTool() {
            return requiredPredecessorTool;
        }

        @Nullable
        public String getSuggestion() {
            return suggestion;
        }
    }

    /**
     * Tool usage history entry.
     */
    public static class ToolUsage {
        private final String toolName;
        private final String args;
        private final boolean wasSuccessful;
        private final long timestamp;

        public ToolUsage(String toolName, String args, boolean wasSuccessful) {
            this.toolName = toolName;
            this.args = args;
            this.wasSuccessful = wasSuccessful;
            this.timestamp = System.currentTimeMillis();
        }

        public String getToolName() {
            return toolName;
        }

        public String getArgs() {
            return args;
        }

        public boolean wasSuccessful() {
            return wasSuccessful;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Validates if the tool can be called given the current history.
     */
    @NonNull
    public static ValidationResult validate(@NonNull String toolName,
                                           @NonNull String args,
                                           @NonNull List<ToolUsage> recentHistory,
                                           @Nullable TaskPlanner.Plan plan) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return ValidationResult.invalid("Tool name cannot be empty");
        }

        // Check if tool requires predecessor
        if ("edit_file".equals(toolName)) {
            return validateEditFile(args, recentHistory);
        }

        if ("delete_file_or_folder".equals(toolName)) {
            return validateDeleteFile(args, recentHistory);
        }

        if ("rewrite_file".equals(toolName)) {
            return validateRewriteFile(args, recentHistory);
        }

        // Check plan constraints if available
        if (plan != null) {
            TaskPlanner.Step currentStep = plan.getCurrentStep();
            if (currentStep != null) {
                List<String> expectedTools = currentStep.getExpectedTools();
                if (!expectedTools.isEmpty() && !expectedTools.contains(toolName)) {
                    // Tool not expected in current step
                    String message = "Tool '" + toolName + "' not expected in current step. " +
                                   "Expected: " + String.join(", ", expectedTools);
                    return ValidationResult.invalid(message);
                }
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Validates edit_file - MUST read_file first on same file.
     */
    @NonNull
    private static ValidationResult validateEditFile(@NonNull String args,
                                                     @NonNull List<ToolUsage> recentHistory) {
        String targetFile = extractFilePathFromArgs(args);
        if (targetFile == null || targetFile.isEmpty()) {
            return ValidationResult.invalid("Cannot extract file path from edit_file arguments");
        }

        if (!hasSuccessfulFileTool("read_file", targetFile, recentHistory)) {
            return ValidationResult.requiresPredecessor(
                    "Error: MUST call read_file on '" + targetFile + "' before editing it",
                    "read_file",
                    "Read the file first to understand its current content before making changes"
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Validates delete_file_or_folder - should verify file exists.
     */
    @NonNull
    private static ValidationResult validateDeleteFile(@NonNull String args,
                                                       @NonNull List<ToolUsage> recentHistory) {
        String targetFile = extractFilePathFromArgs(args);
        if (targetFile == null || targetFile.isEmpty()) {
            return ValidationResult.invalid("Cannot extract file path from delete arguments");
        }

        boolean verified = false;
        for (ToolUsage usage : recentHistory) {
            if (!usage.wasSuccessful()) {
                continue;
            }
            if ("read_file".equals(usage.getToolName())
                    && targetFile.equals(extractFilePathFromArgs(usage.getArgs()))) {
                verified = true;
                break;
            }
            if (("search_pathnames_only".equals(usage.getToolName())
                    || "search_for_files".equals(usage.getToolName()))
                    && searchTargetsFile(usage.getArgs(), targetFile)) {
                verified = true;
                break;
            }
        }

        if (!verified) {
            return ValidationResult.requiresPredecessor(
                    "Warning: Deleting '" + targetFile + "' without verification. Consider using search first.",
                    "search_pathnames_only",
                    "Search for the file first to confirm its location"
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Validates rewrite_file - optionally check if file exists.
     */
    @NonNull
    private static ValidationResult validateRewriteFile(@NonNull String args,
                                                        @NonNull List<ToolUsage> recentHistory) {
        String targetFile = extractFilePathFromArgs(args);
        if (targetFile == null || targetFile.isEmpty()) {
            return ValidationResult.invalid("Cannot extract file path from rewrite_file arguments");
        }
        if (hasSuccessfulFileTool("read_file", targetFile, recentHistory)
                || hasSuccessfulFileTool("create_file_or_folder", targetFile, recentHistory)) {
            return ValidationResult.valid();
        }
        return ValidationResult.requiresPredecessor(
                "MUST read an existing file before rewriting it, or create the file first: '" + targetFile + "'",
                "read_file",
                "Read the target before overwriting it. For a new file, create it before writing content."
        );
    }

    private static boolean hasSuccessfulFileTool(@NonNull String toolName,
                                                 @NonNull String targetFile,
                                                 @NonNull List<ToolUsage> recentHistory) {
        for (ToolUsage usage : recentHistory) {
            if (usage.wasSuccessful() && toolName.equals(usage.getToolName())
                    && targetFile.equals(extractFilePathFromArgs(usage.getArgs()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean searchTargetsFile(@NonNull String args, @NonNull String targetFile) {
        String fileName = targetFile;
        int slash = targetFile.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < targetFile.length()) {
            fileName = targetFile.substring(slash + 1);
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(args);
            String query = json.optString("query", json.optString("search_query", ""));
            return !query.isEmpty() && (targetFile.contains(normalizeFilePath(query))
                    || fileName.equalsIgnoreCase(query)
                    || fileName.toLowerCase().contains(query.toLowerCase()));
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Extracts file path from tool arguments (handles various formats).
     */
    @Nullable
    private static String extractFilePathFromArgs(@NonNull String args) {
        if (args == null || args.trim().isEmpty()) {
            return null;
        }

        try {
            // Try to parse as JSON and extract uri or file_path
            org.json.JSONObject json = new org.json.JSONObject(args);

            if (json.has("uri")) {
                return normalizeFilePath(json.getString("uri"));
            }
            if (json.has("file_path")) {
                return normalizeFilePath(json.getString("file_path"));
            }
            if (json.has("path")) {
                return normalizeFilePath(json.getString("path"));
            }
        } catch (Exception ignored) {
            // Not JSON or missing field
        }

        return null;
    }

    /**
     * Normalizes file path for comparison.
     */
    @NonNull
    private static String normalizeFilePath(@Nullable String path) {
        if (path == null) {
            return "";
        }

        String normalized = path.trim().replace("\\", "/");

        // Remove leading ./
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        // Remove leading /
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }

    /**
     * Checks if a tool was successfully called recently.
     */
    public static boolean wasToolCalled(@NonNull String toolName,
                                       @NonNull List<ToolUsage> recentHistory) {
        for (ToolUsage usage : recentHistory) {
            if (toolName.equals(usage.getToolName()) && usage.wasSuccessful()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a specific file was read recently.
     */
    public static boolean wasFileRead(@NonNull String filePath,
                                     @NonNull List<ToolUsage> recentHistory) {
        String normalizedPath = normalizeFilePath(filePath);

        for (ToolUsage usage : recentHistory) {
            if ("read_file".equals(usage.getToolName())) {
                String readPath = extractFilePathFromArgs(usage.getArgs());
                if (normalizedPath.equals(readPath) && usage.wasSuccessful()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates a tool usage history entry.
     */
    @NonNull
    public static ToolUsage createUsage(@NonNull String toolName,
                                       @NonNull String args,
                                       boolean wasSuccessful) {
        return new ToolUsage(toolName, args, wasSuccessful);
    }
}
