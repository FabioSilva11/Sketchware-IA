package pro.sketchware.activities.chat.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * RetryManager provides intelligent retry logic with alternative strategies.
 *
 * When a tool fails, instead of simply retrying the same action:
 * 1. Analyze the failure reason
 * 2. Suggest alternative tool or approach
 * 3. Modify arguments if needed
 * 4. Avoid infinite retry loops
 *
 * This prevents the agent from:
 * - Repeatedly calling the same failing tool
 * - Getting stuck in error loops
 * - Burning tokens on useless retries
 *
 * Inspired by Cursor's smart retry and Void IDE's alternative strategy selection.
 */
public class RetryManager {

    private static final int MAX_RETRIES_PER_TOOL = 2;
    private static final int MAX_TOTAL_RETRIES = 4;

    /**
     * Retry decision.
     */
    public static class RetryDecision {
        private final boolean shouldRetry;
        private final String alternativeTool;
        private final String alternativeArgs;
        private final String reason;

        private RetryDecision(boolean shouldRetry, String alternativeTool,
                             String alternativeArgs, String reason) {
            this.shouldRetry = shouldRetry;
            this.alternativeTool = alternativeTool;
            this.alternativeArgs = alternativeArgs;
            this.reason = reason;
        }

        @NonNull
        public static RetryDecision noRetry(@NonNull String reason) {
            return new RetryDecision(false, null, null, reason);
        }

        @NonNull
        public static RetryDecision retry(@NonNull String alternativeTool,
                                         @NonNull String alternativeArgs,
                                         @NonNull String reason) {
            return new RetryDecision(true, alternativeTool, alternativeArgs, reason);
        }

        public boolean shouldRetry() {
            return shouldRetry;
        }

        @Nullable
        public String getAlternativeTool() {
            return alternativeTool;
        }

        @Nullable
        public String getAlternativeArgs() {
            return alternativeArgs;
        }

        @Nullable
        public String getAlternative() {
            return shouldRetry ? alternativeTool + " with modified args" : null;
        }

        @Nullable
        public String getReason() {
            return reason;
        }
    }

    /**
     * Decides whether to retry and suggests alternative approach.
     */
    @NonNull
    public static RetryDecision shouldRetry(@NonNull String toolName,
                                           @NonNull String args,
                                           @NonNull String errorResult,
                                           int consecutiveFailures,
                                           @NonNull List<ToolSequenceValidator.ToolUsage> recentHistory) {
        // 1. Check if we've exceeded retry limits
        if (consecutiveFailures >= MAX_TOTAL_RETRIES) {
            return RetryDecision.noRetry("Max total retries exceeded");
        }

        int toolRetryCount = countToolRetries(toolName, recentHistory);
        if (toolRetryCount >= MAX_RETRIES_PER_TOOL) {
            return RetryDecision.noRetry("Max retries for this tool exceeded");
        }

        // 2. Analyze error and suggest alternative
        String errorLower = errorResult.toLowerCase(java.util.Locale.ROOT);

        if ("edit_file".equals(toolName)
                && (errorLower.contains("did not match")
                || errorLower.contains("could not apply edit_file"))) {
            String filePath = extractFilePath(args);
            if (filePath != null) {
                return RetryDecision.retry(
                        "read_file",
                        "{\"uri\":\"" + escapeJson(filePath) + "\"}",
                        "The file changed or the edit context was stale; read the file again before retrying the edit"
                );
            }
        }

        // File not found errors
        if (errorLower.contains("file not found") ||
            errorLower.contains("no such file") ||
            errorLower.contains("does not exist")) {
            return handleFileNotFoundError(toolName, args, errorResult);
        }

        // Permission errors
        if (errorLower.contains("permission denied") ||
            errorLower.contains("access denied")) {
            return RetryDecision.noRetry("Permission error - cannot retry");
        }

        // Invalid arguments
        if (errorLower.contains("invalid argument") ||
            errorLower.contains("invalid parameter") ||
            errorLower.contains("missing required")) {
            return RetryDecision.noRetry("Invalid arguments - model should fix and retry");
        }

        // Timeout errors
        if (errorLower.contains("timeout") ||
            errorLower.contains("timed out")) {
            return RetryDecision.noRetry("A timed-out command may have side effects; do not rerun it automatically");
        }

        // File already exists
        if (errorLower.contains("already exists") ||
            errorLower.contains("file exists")) {
            return RetryDecision.noRetry("The target exists; inspect it before deciding whether to overwrite");
        }

        // Empty result (not an error, but might want different approach)
        if (errorLower.contains("no results") ||
            errorLower.contains("not found") ||
            errorLower.contains("empty")) {
            return handleEmptyResultError(toolName, args);
        }

        // Unknown error - don't retry automatically
        return RetryDecision.noRetry("Unknown error type - let model decide");
    }

    /**
     * Handles file not found errors.
     */
    @NonNull
    private static RetryDecision handleFileNotFoundError(@NonNull String toolName,
                                                         @NonNull String args,
                                                         @NonNull String errorResult) {
        // Suggest using search first
        if ("read_file".equals(toolName) || "edit_file".equals(toolName)) {
            String filePath = extractFilePath(args);
            if (filePath != null) {
                // Build search args
                String searchArgs = "{\"query\":\"" + escapeJson(getFileName(filePath)) + "\"}";
                return RetryDecision.retry(
                        "search_pathnames_only",
                        searchArgs,
                        "File not found - searching for it first"
                );
            }
        }

        return RetryDecision.noRetry("File not found and cannot determine search strategy");
    }

    /**
     * Handles empty result errors.
     */
    @NonNull
    private static RetryDecision handleEmptyResultError(@NonNull String toolName,
                                                        @NonNull String args) {
        // If search returned empty, try alternative search method
        if ("search_pathnames_only".equals(toolName)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(args);
                String query = json.getString("query");

                // Try search_for_files instead (searches content, not just names)
                String altArgs = "{\"query\": \"" + query + "\"}";
                return RetryDecision.retry(
                        "search_for_files",
                        altArgs,
                        "search_pathnames_only returned empty - trying search_for_files"
                );
            } catch (Exception ignored) {
            }
        }

        if ("search_for_files".equals(toolName)) {
            // Maybe try get_dir_tree to understand project structure
            return RetryDecision.retry(
                    "get_dir_tree",
                    "{\"uri\": \"\"}",
                    "Search returned empty - exploring project structure"
            );
        }

        return RetryDecision.noRetry("Empty result - model should try different query");
    }

    /**
     * Counts how many times a tool was retried.
     */
    private static int countToolRetries(@NonNull String toolName,
                                       @NonNull List<ToolSequenceValidator.ToolUsage> recentHistory) {
        int count = 0;
        for (ToolSequenceValidator.ToolUsage usage : recentHistory) {
            if (toolName.equals(usage.getToolName()) && !usage.wasSuccessful()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Extracts file path from args.
     */
    @Nullable
    private static String extractFilePath(@NonNull String args) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(args);
            if (json.has("uri")) {
                return json.getString("uri");
            }
            if (json.has("file_path")) {
                return json.getString("file_path");
            }
            if (json.has("path")) {
                return json.getString("path");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Extracts file name from path.
     */
    @NonNull
    private static String getFileName(@NonNull String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    @NonNull
    private static String escapeJson(@NonNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
