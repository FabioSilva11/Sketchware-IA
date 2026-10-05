package pro.sketchware.chat.agentsdk;

/**
 * Observable execution state of the agent (Codex protocol parity): a single
 * status shown in the composer header — NEVER as chat messages. Reduction
 * rules live in {@link AgentUiState#reduce} and the state machine is driven
 * solely by {@link AgentEvent}s, so the UI never invents transitions.
 *
 * <p>Transitions:
 * <pre>
 *   RunStarted                -> WORKING
 *   TurnStarted               -> THINKING   (never cleared early)
 *   ToolCallStarted           -> RUNNING_TOOL (activeTool set)
 *   ToolCallCompleted         -> THINKING
 *   ApprovalRequired          -> WAITING_APPROVAL
 *   PermissionResolved        -> THINKING
 *   PolicyDenied              -> THINKING
 *   Error                     -> ERROR
 *   RunCompleted              -> READY      (errors/cancels already surfaced)
 *   requestCancellation()     -> CANCELLING -> READY on RunCompleted
 * </pre></p>
 */
public enum RunStatus {

    /** Idle: nothing running, banner hidden. */
    READY,

    /** The run started; preparing the first turn. */
    WORKING,

    /** The model is generating a reply (indicator must NOT disappear). */
    THINKING,

    /** A tool call is waiting for a permission decision. */
    WAITING_APPROVAL,

    /** A specific tool is executing now. */
    RUNNING_TOOL,

    /** The run reached its end successfully. */
    COMPLETED,

    /** A run-level error occurred (seen only in the moment; then READY). */
    ERROR,

    /** A cancellation is in flight. */
    CANCELLING
}