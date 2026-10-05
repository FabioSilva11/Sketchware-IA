package pro.sketchware.chat.agentsdk;

/**
 * Immutable UI state derived from the {@link AgentEvent} stream. The host
 * shows this (the composer banner) without ever constructing chat bubbles.
 */
public final class AgentUiState {

    private final RunStatus runStatus;
    private final String activeTool;

    private AgentUiState(RunStatus runStatus, String activeTool) {
        this.runStatus = runStatus == null ? RunStatus.READY : runStatus;
        this.activeTool = activeTool == null ? "" : activeTool;
    }

    public static AgentUiState ready() {
        return new AgentUiState(RunStatus.READY, "");
    }

    public static AgentUiState cancelled() {
        return new AgentUiState(RunStatus.CANCELLING, "");
    }

    public RunStatus getRunStatus() {
        return runStatus;
    }

    /** The tool currently executing (only meaningful for RUNNING_TOOL). */
    public String getActiveTool() {
        return activeTool;
    }

    public boolean isBusy() {
        return runStatus != RunStatus.READY && runStatus != RunStatus.COMPLETED;
    }

    /**
     * Pure reduction: next state from a current state and one event. This is
     * the ONLY place transitions are decided (a single source of truth), so
     * the UI and the runtime never drift.
     */
    public static AgentUiState reduce(AgentUiState current, AgentEvent event) {
        AgentUiState base = current == null ? ready() : current;
        if (event == null) {
            return base;
        }
        if (event instanceof AgentEvent.RunStarted) {
            return new AgentUiState(RunStatus.WORKING, "");
        }
        if (event instanceof AgentEvent.TurnStarted
                || event instanceof AgentEvent.AssistantMessageDelta
                || event instanceof AgentEvent.AssistantMessage
                || event instanceof AgentEvent.ContextCompacted) {
            // The model is producing text: keep the indicator visible.
            return new AgentUiState(RunStatus.THINKING, "");
        }
        if (event instanceof AgentEvent.ToolCallStarted) {
            AgentEvent.ToolCallStarted started = (AgentEvent.ToolCallStarted) event;
            return new AgentUiState(RunStatus.RUNNING_TOOL, started.getTool());
        }
        if (event instanceof AgentEvent.ToolCallCompleted) {
            return new AgentUiState(RunStatus.THINKING, "");
        }
        if (event instanceof AgentEvent.ApprovalRequired) {
            AgentEvent.ApprovalRequired required = (AgentEvent.ApprovalRequired) event;
            return new AgentUiState(RunStatus.WAITING_APPROVAL, required.getTool());
        }
        if (event instanceof AgentEvent.PermissionResolved
                || event instanceof AgentEvent.PolicyDenied) {
            return new AgentUiState(RunStatus.THINKING, "");
        }
        if (event instanceof AgentEvent.Error) {
            return new AgentUiState(RunStatus.ERROR, "");
        }
        if (event instanceof AgentEvent.RunCompleted) {
            return AgentUiState.ready();
        }
        return base;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgentUiState)) {
            return false;
        }
        AgentUiState that = (AgentUiState) other;
        return runStatus == that.runStatus && activeTool.equals(that.activeTool);
    }

    @Override
    public int hashCode() {
        return runStatus.hashCode() * 31 + activeTool.hashCode();
    }

    @Override
    public String toString() {
        return "AgentUiState{runStatus=" + runStatus + ", activeTool='" + activeTool + "'}";
    }
}