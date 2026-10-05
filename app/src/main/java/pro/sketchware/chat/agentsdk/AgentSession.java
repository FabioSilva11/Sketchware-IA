package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateful record of one agent run, mirroring the session concept of
 * openai-agents-js: messages, usage counters, permission audit trail and a
 * bounded event log.
 *
 * <p><b>Persistence contract (honest):</b> this class is a per-run,
 * IN-MEMORY record only. Nothing here is written to disk; durable state
 * lives elsewhere — chat history in {@code SqliteChatStorage}, file-change
 * audit in {@code FileChangeTracker} (Axion-internal files), and the run's
 * task state in {@link TaskMemory} + {@link TaskMemoryStore}. The event log
 * is bounded to {@value #MAX_EVENT_LOG} entries and is also memory-only.</p>
 *
 * <p>Thread-safety: mutated only by the runtime's run thread; snapshots are
 * defensive copies so UI threads can read freely.</p>
 */
public final class AgentSession {

    /** Lifecycle of a run. */
    public enum Status { IDLE, RUNNING, AWAITING_APPROVAL, COMPLETED, FAILED, CANCELLED }

    private final String scId;
    private final String agentName;
    private final List<ChatMessage> messages;
    private final List<AgentEvent> events = new ArrayList<>();
    private volatile Status status = Status.IDLE;
    private volatile PermissionRequest pendingRequest;
    private int llmCalls;
    private int toolCalls;
    private int approvalsGranted;
    private int approvalsDenied;
    private final long startedAtMs = System.currentTimeMillis();

    private static final int MAX_EVENT_LOG = 500;

    public AgentSession(String scId, String agentName, List<ChatMessage> history) {
        this.scId = scId == null ? "" : scId;
        this.agentName = agentName == null ? "" : agentName;
        this.messages = history == null
                ? new ArrayList<>()
                : new ArrayList<>(history);
    }

    public String getScId() {
        return scId;
    }

    public String getAgentName() {
        return agentName;
    }

    public Status getStatus() {
        return status;
    }

    public PermissionRequest getPendingRequest() {
        return pendingRequest;
    }

    public int getLlmCalls() {
        return llmCalls;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public int getApprovalsGranted() {
        return approvalsGranted;
    }

    public int getApprovalsDenied() {
        return approvalsDenied;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    /** Defensive copy of the message history (run-local additions included). */
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    /** Defensive copy of the bounded event log, oldest first. */
    public List<AgentEvent> getEvents() {
        return new ArrayList<>(events);
    }

    // ------------------------------------------------------------------
    // Mutations (runtime thread only)
    // ------------------------------------------------------------------

    void setStatus(Status status) {
        this.status = status == null ? Status.IDLE : status;
    }

    void recordEvent(AgentEvent event) {
        if (event == null) {
            return;
        }
        synchronized (events) {
            if (events.size() >= MAX_EVENT_LOG) {
                events.remove(0);
            }
            events.add(event);
        }
        if (event instanceof AgentEvent.ToolCallStarted
                || event instanceof AgentEvent.ToolCallCompleted
                || event instanceof AgentEvent.PolicyDenied) {
            toolCalls++;
        } else if (event instanceof AgentEvent.TurnStarted) {
            llmCalls++;
        } else if (event instanceof AgentEvent.PermissionResolved) {
            if (((AgentEvent.PermissionResolved) event).isAllowed()) {
                approvalsGranted++;
            } else {
                approvalsDenied++;
            }
        }
    }

    void markAwaitingApproval(PermissionRequest request) {
        this.pendingRequest = request;
        this.status = Status.AWAITING_APPROVAL;
    }

    void clearPendingApproval() {
        this.pendingRequest = null;
        if (this.status == Status.AWAITING_APPROVAL) {
            this.status = Status.RUNNING;
        }
    }

    /** Recent events serialized for persistence/telemetry. */
    public JSONArray exportRecentEventsJson(int maxEvents) {
        JSONArray array = new JSONArray();
        List<AgentEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        int from = Math.max(0, snapshot.size() - Math.max(1, maxEvents));
        for (int i = from; i < snapshot.size(); i++) {
            array.put(eventToJson(snapshot.get(i)));
        }
        return array;
    }

    private static JSONObject eventToJson(AgentEvent event) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", event.getClass().getSimpleName());
            json.put("ts", event.getTimestampMs());
            if (event instanceof AgentEvent.TurnStarted) {
                json.put("agent", ((AgentEvent.TurnStarted) event).getAgentName());
                json.put("turn", ((AgentEvent.TurnStarted) event).getTurn());
            } else if (event instanceof AgentEvent.ToolCallStarted) {
                json.put("tool", ((AgentEvent.ToolCallStarted) event).getTool());
            } else if (event instanceof AgentEvent.ToolCallCompleted) {
                AgentEvent.ToolCallCompleted completed = (AgentEvent.ToolCallCompleted) event;
                json.put("tool", completed.getTool());
                json.put("success", completed.isSuccess());
            } else if (event instanceof AgentEvent.PolicyDenied) {
                AgentEvent.PolicyDenied denied = (AgentEvent.PolicyDenied) event;
                json.put("tool", denied.getTool());
                json.put("reason", denied.getReason());
            } else if (event instanceof AgentEvent.Error) {
                json.put("message", ((AgentEvent.Error) event).getMessage());
            }
        } catch (Exception ignored) {
            // Export is best-effort by design.
        }
        return json;
    }
}
