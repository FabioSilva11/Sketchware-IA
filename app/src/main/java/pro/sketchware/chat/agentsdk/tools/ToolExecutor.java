package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;

/**
 * Executes a registered tool for one {@link ToolExecutionContext}, mirroring
 * Codex's separation between {@code ToolSpec} (declaration) and handler
 * (implementation): the spec describes the wire contract, the executor
 * performs the work.
 *
 * <p>Executors must never throw for model-caused input problems — return
 * {@link AgentToolResult#error(String)} instead; unexpected crashes are
 * converted to error results by the {@link AxionToolRouter}.</p>
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * Runs the tool.
     *
     * @param context the resolved execution request (FUNCTION args or
     *                FREEFORM input, never both)
     * @return the tool result
     * @throws Exception only for unexpected infrastructure failures; the
     *                   router converts them to {@link AgentToolResult#error}
     */
    AgentToolResult execute(ToolExecutionContext context) throws Exception;
}