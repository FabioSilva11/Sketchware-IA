package pro.sketchware.ia.tools;

/**
 * Structured result of a tool execution.
 *
 * Replaces the old string-sniffing heuristic ({@code looksLikeToolError})
 * that scanned tool output for words like "error"/"exception" and produced
 * false positives whenever a file containing those words was read.
 *
 * Error status is now decided explicitly at the execution boundary
 * (exception thrown, tool missing, or the tool's own explicit error
 * protocol of prefixing output with "Error"/"Erro"/etc.).
 */
public final class ToolExecResult {

    public final boolean ok;
    public final String output;

    private ToolExecResult(boolean ok, String output) {
        this.ok = ok;
        this.output = output == null ? "" : output;
    }

    public static ToolExecResult success(String output) {
        return new ToolExecResult(true, output);
    }

    public static ToolExecResult error(String output) {
        return new ToolExecResult(false, output);
    }

    /**
     * Wraps a legacy string result using the explicit error-prefix protocol
     * used by the Void-ported tools and MCP channels. Only start-of-string
     * prefixes are honoured — output that merely CONTAINS words like
     * "exception" is treated as success.
     */
    public static ToolExecResult fromLegacyString(String result) {
        if (result == null) {
            return error("Tool returned no result.");
        }
        String trimmed = result.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        boolean isError = lower.startsWith("error")
                || lower.startsWith("erro")
                || lower.startsWith("cannot ")
                || lower.startsWith("file not found")
                || lower.startsWith("blocked:")
                || lower.startsWith("comando bloqueado");
        return isError ? error(result) : success(result);
    }
}
