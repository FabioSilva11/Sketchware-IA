package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.EventStream;
import pro.sketchware.chat.agentsdk.RunContext;

import org.json.JSONObject;

/**
 * One execution request resolved by the {@link AxionToolRouter}: the
 * registration identity, the run-scoped {@link RunContext} and either the
 * parsed FUNCTION arguments OR the raw FREEFORM input — never both.
 */
public final class ToolExecutionContext {

    private final ToolRegistration registration;
    private final String scId;
    private final String callId;
    private final RunContext runContext;
    private final JSONObject functionArguments;
    private final String freeformInput;
    private final String rawArguments;
    /** The run's event stream (from the router), or {@code null}. */
    private final EventStream events;

    ToolExecutionContext(ToolRegistration registration, String scId, String callId,
                         RunContext runContext, JSONObject functionArguments,
                         String freeformInput, String rawArguments, EventStream events) {
        this.registration = registration;
        this.scId = scId == null ? "" : scId;
        this.callId = callId;
        this.runContext = runContext;
        this.functionArguments = functionArguments;
        this.freeformInput = freeformInput;
        this.rawArguments = rawArguments;
        this.events = events;
    }

    /** Execution context without an event stream (tests/direct executor use). */
    ToolExecutionContext(ToolRegistration registration, String scId, String callId,
                         RunContext runContext, JSONObject functionArguments,
                         String freeformInput, String rawArguments) {
        this(registration, scId, callId, runContext, functionArguments,
                freeformInput, rawArguments, null);
    }

    /** The registration being executed. */
    public ToolRegistration registration() {
        return registration;
    }

    /** The session/workspace id of the run. */
    public String scId() {
        return scId;
    }

    /** The provider tool-call id (dedupe/audit). */
    public String callId() {
        return callId;
    }

    /** The run-scoped context (workspace, tracker, task memory). */
    public RunContext runContext() {
        return runContext;
    }

    /** Parsed FUNCTION arguments; {@code null} for freeform tools. */
    public JSONObject functionArguments() {
        return functionArguments;
    }

    /** Raw FREEFORM input; {@code null} for function tools. */
    public String freeformInput() {
        return freeformInput;
    }

    /** The untrimmed wire arguments string (diagnostics). */
    public String rawArguments() {
        return rawArguments;
    }

    /** The run's event stream (tools that announce filesystem effects). */
    public EventStream events() {
        return events;
    }
}