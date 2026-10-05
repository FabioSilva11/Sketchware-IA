package pro.sketchware.chat.agentsdk;

/**
 * Thread-safe {@link AgentUiState} accumulation over the event stream. The
 * runtime feeds it every event and reads the resulting status when the host
 * asks; the host never computes transitions itself.
 */
public final class RunStatusReducer {

    private AgentUiState state = AgentUiState.ready();

    /** Feed one event; derives the next status. */
    public synchronized void reduce(AgentEvent event) {
        state = AgentUiState.reduce(state, event);
    }

    /** User asked to cancel: show CANCELLING until the run completes. */
    public synchronized void requestCancellation() {
        state = AgentUiState.cancelled();
    }

    /** Back to idle (e.g. before a fresh run). */
    public synchronized void reset() {
        state = AgentUiState.ready();
    }

    public synchronized AgentUiState state() {
        return state;
    }
}