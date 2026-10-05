package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.port.VoidPortToolsService;

/**
 * Executes one workspace MUTATION tool (create/delete/edit/rewrite/move/
 * rename/copy) through the real implementation in {@link
 * VoidPortToolsService}.
 *
 * <p>This class exists so the call path for every specialized mutation is
 * explicit and auditable:</p>
 *
 * <pre>
 * ToolRegistration -> WorkspaceMutationExecutor -> VoidPortToolsService.executeTool(...)
 * </pre>
 *
 * <p>{@code WorkspaceToolProvider.registerWorkspaceMutationTools} is the only
 * place that wires this executor into the registry, so there is a single,
 * greppable seam between "the model asked to mutate a file" and "the real
 * mutation happened".</p>
 */
final class WorkspaceMutationExecutor implements ToolExecutor {

    private final String toolName;

    WorkspaceMutationExecutor(String toolName) {
        this.toolName = toolName;
    }

    @Override
    public AgentToolResult execute(ToolExecutionContext context) {
        VoidPortToolsService.ToolOutcome outcome = VoidPortToolsService.runTool(
                context.scId(), toolName, context.functionArguments());
        // A refused write (generated folder, other project, editor open...) changed nothing: report it as one
        return outcome.failed ? AgentToolResult.error(outcome.text) : AgentToolResult.success(outcome.text);
    }
}
