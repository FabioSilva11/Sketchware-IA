package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.toolcalling.ToolCall;

/**
 * A pending approval raised by the {@link PermissionLayer}, modeled after
 * Codex's {@code ReviewDecision}: everything the host UI needs to render a
 * confirmation dialog and everything needed to audit the outcome.
 */
public final class PermissionRequest {

    private final String id;
    private final String tool;
    private final ToolCall call;
    private final String reason;
    private final long createdAtMs;

    public PermissionRequest(String id, String tool, ToolCall call, String reason) {
        this.id = id == null ? "" : id;
        this.tool = tool == null ? "" : tool;
        this.call = call;
        this.reason = reason == null ? "" : reason;
        this.createdAtMs = System.currentTimeMillis();
    }

    /** Unique id of this request (used to correlate the resumed decision). */
    public String getId() {
        return id;
    }

    public String getTool() {
        return tool;
    }

    /** Raw call (name + arguments) awaiting approval. */
    public ToolCall getCall() {
        return call;
    }

    /** Human-readable explanation of why approval is needed. */
    public String getReason() {
        return reason;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }
}
