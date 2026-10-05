package pro.sketchware.chat.agentsdk;

/**
 * Access port for host bridges to reach the {@link EventStream} a given
 * {@link AgentRuntime} emits to (item 8 of the migration). The runtime and
 * the UI bridge MUST observe the same stream — the bridge subscribes to the
 * stream returned here instead of constructing a second one.
 */
public final class AgentRuntimeEventAccess {

    private AgentRuntimeEventAccess() {
    }

    /**
     * The {@link EventStream} of {@code runtime} (or a fresh stream when the
     * runtime was built without one — which never happens with the Builder).
     */
    public static EventStream eventStreamOf(AgentRuntime runtime) {
        return runtime != null ? runtime.eventStream() : new EventStream();
    }
}
