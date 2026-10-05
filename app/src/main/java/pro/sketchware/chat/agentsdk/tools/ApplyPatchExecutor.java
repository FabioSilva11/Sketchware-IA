package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.ApplyPatchTool;
import pro.sketchware.chat.agentsdk.EventStream;
import pro.sketchware.chat.agentsdk.RunContext;

/**
 * Core {@code apply_patch} executor operating on the raw FREEFORM patch
 * document (Codex contract: the input is the patch text itself, never the
 * legacy {@code {"patch": "..."}} JSON envelope). All parse/validate/mutate
 * logic lives in the existing {@link ApplyPatchTool}; this class is the thin
 * freeform adapter that feeds the raw string through infrastructure capable of
 * producing an {@link ApplyPatchTool} bound to the run's workspace and event
 * stream.
 *
 * <p>Execution requires the {@link RunContext} workspace to be resolvable —
 * the same fail-closed rule {@code ApplyPatchTool.apply} enforces, so a run
 * without a pinned filesystem can never mutate the wrong project.</p>
 */
public final class ApplyPatchExecutor implements ToolExecutor {

    private final PatchToolFactory factory;

    /** Strategy producing a bound ApplyPatchTool for a run context. */
    public interface PatchToolFactory {
        ApplyPatchTool create(RunContext context, EventStream events, String scId);
    }

    public ApplyPatchExecutor(PatchToolFactory factory) {
        this.factory = factory;
    }

    @Override
    public AgentToolResult execute(ToolExecutionContext ctx) {
        String patch = ctx.freeformInput() == null ? "" : ctx.freeformInput();
        if (patch.trim().isEmpty()) {
            return AgentToolResult.error(
                    "Error: the patch document is empty. Send the patch text as the tool input "
                            + "(a FREEFORM tool: do not wrap the patch in JSON).");
        }
        ApplyPatchTool tool = factory.create(ctx.runContext(), ctx.events(), ctx.scId());
        return tool.apply(ctx.runContext(), patch);
    }
}