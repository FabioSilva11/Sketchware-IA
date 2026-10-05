package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.toolcalling.ToolCall;

/**
 * Typed agent runtime events, in the spirit of AgentScope Java's typed event
 * stream and Codex's protocol events: the UI observes what the agent is doing
 * instead of deducing it from response text.
 *
 * <p>Events are immutable snapshots; {@link EventStream} carries them to
 * subscribers. Every event carries the owning run's {@code scId}.</p>
 */
public abstract class AgentEvent {

    private final String scId;
    private final long timestampMs;

    protected AgentEvent(String scId) {
        this.scId = scId == null ? "" : scId;
        this.timestampMs = System.currentTimeMillis();
    }

    /** Conversation/session id of the run that emitted this event. */
    public String getScId() {
        return scId;
    }

    /** Wall-clock creation time of the event. */
    public long getTimestampMs() {
        return timestampMs;
    }

    // ------------------------------------------------------------------
    // Run lifecycle
    // ------------------------------------------------------------------

    /** The run started for this conversation. */
    public static final class RunStarted extends AgentEvent {
        public RunStarted(String scId) {
            super(scId);
        }
    }

    /** A loop iteration started for the given agent. */
    public static final class TurnStarted extends AgentEvent {
        private final String agentName;
        private final int turn;

        public TurnStarted(String scId, String agentName, int turn) {
            super(scId);
            this.agentName = agentName == null ? "" : agentName;
            this.turn = turn;
        }

        public String getAgentName() {
            return agentName;
        }

        /** 1-based turn number within the run. */
        public int getTurn() {
            return turn;
        }
    }

    /** Streaming delta of assistant text (prefix of the final AssistantMessage). */
    public static final class AssistantMessageDelta extends AgentEvent {
        private final String delta;

        public AssistantMessageDelta(String scId, String delta) {
            super(scId);
            this.delta = delta == null ? "" : delta;
        }

        public String getDelta() {
            return delta;
        }
    }

    /** Context was compacted (M4): old turns summarized, token count shrunk. */
    public static final class ContextCompacted extends AgentEvent {
        private final int beforeTokens;
        private final int afterTokens;

        public ContextCompacted(String scId, int beforeTokens, int afterTokens) {
            super(scId);
            this.beforeTokens = beforeTokens;
            this.afterTokens = afterTokens;
        }

        public int getBeforeTokens() {
            return beforeTokens;
        }

        public int getAfterTokens() {
            return afterTokens;
        }
    }

    /** Assistant text produced in this turn (before any tool execution). */
    public static final class AssistantMessage extends AgentEvent {
        private final String content;

        public AssistantMessage(String scId, String content) {
            super(scId);
            this.content = content == null ? "" : content;
        }

        public String getContent() {
            return content;
        }
    }

    /** The run ended (successfully or not). Terminal event of every run. */
    public static final class RunCompleted extends AgentEvent {
        private final boolean successful;
        private final String reason;

        public RunCompleted(String scId, boolean successful, String reason) {
            super(scId);
            this.successful = successful;
            this.reason = reason == null ? "" : reason;
        }

        public boolean isSuccessful() {
            return successful;
        }

        /** Empty for successful runs; failure/max-turns reason otherwise. */
        public String getReason() {
            return reason;
        }
    }

    // ------------------------------------------------------------------
    // Tool lifecycle
    // ------------------------------------------------------------------

    /** A tool call is about to execute. */
    public static final class ToolCallStarted extends AgentEvent {
        private final String tool;
        private final ToolCall call;

        public ToolCallStarted(String scId, String tool, ToolCall call) {
            super(scId);
            this.tool = tool == null ? "" : tool;
            this.call = call;
        }

        public String getTool() {
            return tool;
        }

        public ToolCall getCall() {
            return call;
        }
    }

    /** A tool call finished; {@code result.isError()} distinguishes failure. */
    public static final class ToolCallCompleted extends AgentEvent {
        private final String tool;
        private final ToolCall call;
        private final AgentToolResult result;

        public ToolCallCompleted(String scId, String tool, ToolCall call, AgentToolResult result) {
            super(scId);
            this.tool = tool == null ? "" : tool;
            this.call = call;
            this.result = result;
        }

        public String getTool() {
            return tool;
        }

        public ToolCall getCall() {
            return call;
        }

        public AgentToolResult getResult() {
            return result;
        }

        public boolean isSuccess() {
            return result != null && !result.isError();
        }
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    /**
     * A tool call needs a permission decision. Emitted after the policy
     * requested user input; the run parks until the host resolves the
     * {@link PermissionRequest} via the {@link ApprovalHandler} protocol.
     */
    public static final class ApprovalRequired extends AgentEvent {
        private final String tool;
        private final ToolCall call;
        private final PermissionRequest request;

        public ApprovalRequired(String scId, String tool, ToolCall call, PermissionRequest request) {
            super(scId);
            this.tool = tool == null ? "" : tool;
            this.call = call;
            this.request = request;
        }

        public String getTool() {
            return tool;
        }

        public ToolCall getCall() {
            return call;
        }

        public PermissionRequest getRequest() {
            return request;
        }
    }

    /** A permission decision was applied to a tool call, with its lifecycle state. */
    public static final class PermissionResolved extends AgentEvent {
        private final String tool;
        private final PermissionDecision decision;
        private final boolean allowed;
        private final ApprovalHandler.ApprovalState state;

        public PermissionResolved(String scId, String tool, PermissionDecision decision, boolean allowed) {
            this(scId, tool, decision, allowed, null);
        }

        public PermissionResolved(String scId, String tool, PermissionDecision decision,
                                  boolean allowed, ApprovalHandler.ApprovalState state) {
            super(scId);
            this.tool = tool == null ? "" : tool;
            this.decision = decision;
            this.allowed = allowed;
            this.state = state == null
                    ? (allowed ? ApprovalHandler.ApprovalState.ALLOWED : ApprovalHandler.ApprovalState.DENIED)
                    : state;
        }

        public String getTool() {
            return tool;
        }

        public PermissionDecision getDecision() {
            return decision;
        }

        public boolean isAllowed() {
            return allowed;
        }

        /** Lifecycle state: ALLOWED / DENIED / TIMED_OUT / CANCELLED. */
        public ApprovalHandler.ApprovalState getState() {
            return state;
        }
    }

    // ------------------------------------------------------------------
    // Workspace side effects
    // ------------------------------------------------------------------

    /** Kind of filesystem side effect reported by {@link FileChanged}. */
    public enum FileChangeKind {
        CREATED, MODIFIED, DELETED
    }

    /** A file inside the workspace changed as a result of a tool call. */
    public static final class FileChanged extends AgentEvent {
        private final String path;
        private final FileChangeKind kind;
        private final String tool;

        public FileChanged(String scId, String path, FileChangeKind kind, String tool) {
            super(scId);
            this.path = path == null ? "" : path;
            this.kind = kind == null ? FileChangeKind.MODIFIED : kind;
            this.tool = tool == null ? "" : tool;
        }

        public String getPath() {
            return path;
        }

        public FileChangeKind getKind() {
            return kind;
        }

        /** Tool that caused the change (for attribution in the UI). */
        public String getTool() {
            return tool;
        }
    }

    // ------------------------------------------------------------------
    // Sandbox
    // ------------------------------------------------------------------

    /** A tool call was denied by policy before reaching the user. */
    public static final class PolicyDenied extends AgentEvent {
        private final String tool;
        private final String reason;

        public PolicyDenied(String scId, String tool, String reason) {
            super(scId);
            this.tool = tool == null ? "" : tool;
            this.reason = reason == null ? "" : reason;
        }

        public String getTool() {
            return tool;
        }

        public String getReason() {
            return reason;
        }
    }

    // ------------------------------------------------------------------
    // Errors
    // ------------------------------------------------------------------

    /** A run-level error that did not abort the run (e.g. unknown tool). */
    public static final class Error extends AgentEvent {
        private final String message;

        public Error(String scId, String message) {
            super(scId);
            this.message = message == null ? "" : message;
        }

        public String getMessage() {
            return message;
        }
    }
}
