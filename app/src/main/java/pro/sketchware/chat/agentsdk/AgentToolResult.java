package pro.sketchware.chat.agentsdk;

/**
 * Typed result of a tool execution, mirroring the
 * text/structured-content shape of openai-agents-js tool output.
 */
public final class AgentToolResult {

    private final boolean isError;
    private final String output;

    private AgentToolResult(boolean isError, String output) {
        this.isError = isError;
        this.output = output == null ? "" : output;
    }

    public static AgentToolResult success(String output) {
        return new AgentToolResult(false, output);
    }

    public static AgentToolResult error(String output) {
        return new AgentToolResult(true, output);
    }

    public boolean isError() {
        return isError;
    }

    public String output() {
        return output;
    }
}
