package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.EventStream;
import pro.sketchware.chat.agentsdk.PermissionLayer;
import pro.sketchware.chat.agentsdk.RunContext;
import pro.sketchware.chat.agentsdk.ToolPolicy;
import pro.sketchware.chat.toolcalling.ToolCall;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single canonical execution path for the registry-backed architecture,
 * ported from Codex {@code tools/router.rs} on top of {@link AxionToolRegistry}.
 *
 * <p>A structured tool call enters the router; it is resolved through:
 * registry lookup → exposure validation → {@link PermissionLayer} →
 * sandbox pre-check → {@link ToolExecutor} → {@link AgentToolResult}.</p>
 *
 * <p>Freeform tools bypass JSON argument parsing entirely: their raw wire
 * input reaches the executor verbatim, exactly like Codex freeform tools.</p>
 */
public final class AxionToolRouter {

    /** A provider-structured tool call (name + raw arguments + call id). */
    public static final class Route {
        private final String callId;
        private final String toolName;
        private final String arguments;

        public Route(String callId, String toolName, String arguments) {
            this.callId = callId == null || callId.trim().isEmpty()
                    ? "call_" + java.util.UUID.randomUUID()
                    : callId.trim();
            this.toolName = toolName == null ? "" : toolName.trim();
            this.arguments = arguments == null || arguments.trim().isEmpty()
                    ? "{}"
                    : arguments;
        }

        public String callId() {
            return callId;
        }

        public String toolName() {
            return toolName;
        }

        public String arguments() {
            return arguments;
        }
    }

    /** Outcome of routing one structured call. */
    public static final class Routed {
        private final Route call;
        private final AgentToolResult result;
        private final boolean wasDeduplicated;
        private final ToolRegistration registration;

        Routed(Route call, AgentToolResult result, boolean wasDeduplicated,
               ToolRegistration registration) {
            this.call = call;
            this.result = result;
            this.wasDeduplicated = wasDeduplicated;
            this.registration = registration;
        }

        public Route call() {
            return call;
        }

        public AgentToolResult result() {
            return result;
        }

        public boolean wasDeduplicated() {
            return wasDeduplicated;
        }

        public ToolRegistration registration() {
            return registration;
        }
    }

    /** Host hooks so the runtime emits events in order. */
    public interface LoopHooks {
        default void onToolCallStarted(Route call, ToolRegistration registration) {
        }

        default void onToolCallCompleted(Route call, ToolRegistration registration,
                                         AgentToolResult result) {
        }
    }

    private final AxionToolRegistry registry;
    private final PermissionLayer permissions;
    private final EventStream events;
    /** Every processed call id → its outcome (dedupe by callId, not name). */
    private final Map<String, Routed> processedByCallId = new LinkedHashMap<>();
    private int executedCount;

    public AxionToolRouter(AxionToolRegistry registry, PermissionLayer permissions, EventStream events) {
        if (registry == null) {
            throw new IllegalArgumentException("registry is required");
        }
        this.registry = registry;
        this.permissions = permissions;
        this.events = events;
    }

    public int executedCount() {
        return executedCount;
    }

    public int distinctCallIdsSeen() {
        return processedByCallId.size();
    }

    /** Clears the dedupe table (fresh run on the same router instance). */
    public void resetForNewRun() {
        processedByCallId.clear();
        executedCount = 0;
    }

    /**
     * Routes a structured call through the registry.
     */
    public Routed route(Route call, String scId, RunContext context, LoopHooks loopHooks) {
        // ---- 1) Deduplication by callId (never by name) -------------------
        Routed previous = processedByCallId.get(call.callId());
        if (previous != null) {
            return new Routed(call, previous.result(), true, previous.registration());
        }

        // ---- 2) Registry lookup by fully-qualified name -------------------
        ToolRegistration registration = registry.get(ToolName.parse(call.toolName()));
        if (registration == null) {
            // Namespace child calls use the plain child name in some providers:
            // last-chance resolution against the namespace children registry.
            registration = resolveChildAcrossNamespaces(call.toolName());
        }
        if (registration == null) {
            Routed unknown = new Routed(call,
                    AgentToolResult.error("Error: unknown tool '" + call.toolName() + "'."),
                    false, null);
            processedByCallId.put(call.callId(), unknown);
            executedCount++;
            if (loopHooks != null) {
                loopHooks.onToolCallCompleted(call, null, unknown.result());
            }
            return unknown;
        }

        // ---- 3) Exposure validation --------------------------------------
        ToolExposure exposure = registration.exposure();
        if (exposure.isHidden()) {
            Routed hidden = new Routed(call,
                    AgentToolResult.error("Error: tool '" + call.toolName()
                            + "' is not exposed to the model."),
                    false, registration);
            processedByCallId.put(call.callId(), hidden);
            executedCount++;
            if (loopHooks != null) {
                loopHooks.onToolCallCompleted(call, registration, hidden.result());
            }
            return hidden;
        }
        if (exposure.isDeferred() && !registry.isDeferredActivated(registration.spec().name())) {
            Routed deferred = new Routed(call,
                    AgentToolResult.error("Error: tool '" + call.toolName()
                            + "' is deferred. Use tool_search to discover and activate it first."),
                    false, registration);
            processedByCallId.put(call.callId(), deferred);
            executedCount++;
            if (loopHooks != null) {
                loopHooks.onToolCallCompleted(call, registration, deferred.result());
            }
            return deferred;
        }

        // ---- 4) Permission layer -----------------------------------------
        if (permissions != null) {
            PermissionLayer.Outcome outcome = permissions.check(
                    registration, new ToolCall(call.toolName(), call.arguments(), call.callId()), scId);
            if (outcome == PermissionLayer.Outcome.BLOCKED) {
                Routed blocked = new Routed(call,
                        AgentToolResult.error("Error: execution of '"
                                + call.toolName() + "' was denied by policy or user."),
                        false, registration);
                processedByCallId.put(call.callId(), blocked);
                executedCount++;
                if (loopHooks != null) {
                    loopHooks.onToolCallCompleted(call, registration, blocked.result());
                }
                return blocked;
            }
        }

        // ---- 5) Sandbox pre-check ----------------------------------------
        AgentToolResult sandboxBlock = null;
        if (registration.sandbox() != null) {
            sandboxBlock = registration.sandbox().preExecute(call.callId(), scId);
        }
        if (sandboxBlock != null) {
            Routed blocked = new Routed(call, sandboxBlock, false, registration);
            processedByCallId.put(call.callId(), blocked);
            executedCount++;
            if (loopHooks != null) {
                loopHooks.onToolCallCompleted(call, registration, blocked.result());
            }
            return blocked;
        }

        // ---- 6) Execution ------------------------------------------------
        if (loopHooks != null) {
            loopHooks.onToolCallStarted(call, registration);
        }
        AgentToolResult result = execute(registration, call, scId, context);
        Routed routed = new Routed(call, result, false, registration);
        processedByCallId.put(call.callId(), routed);
        executedCount++;
        if (loopHooks != null) {
            loopHooks.onToolCallCompleted(call, registration, result);
        }
        return routed;
    }

    /** Resolves a plain child name ({@code "curr_time"}) to a namespace child. */
    private ToolRegistration resolveChildAcrossNamespaces(String plainName) {
        for (ToolRegistration reg : registry.namespaceTools()) {
            String prefix = reg.spec().name().name();
            ToolRegistration child = registry.get(ToolName.namespaced(prefix, plainName));
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private AgentToolResult execute(ToolRegistration registration, Route call,
                                    String scId, RunContext context) {
        ToolExecutor executor = registration.executor();
        if (executor == null) {
            return AgentToolResult.error("Error: tool '" + call.toolName()
                    + "' has no executor registered.");
        }
        try {
            ToolSpec.Type type = registration.spec().type();
            ToolExecutionContext ctx;
            if (type == ToolSpec.Type.FREEFORM) {
                // Freeform tools receive the RAW wire input, never JSON args.
                ctx = new ToolExecutionContext(registration, scId, call.callId(), context,
                        null, call.arguments(), call.arguments(), events);
            } else {
                JSONObject args = parseArguments(call.arguments());
                if (args == null) {
                    return AgentToolResult.error("Error: invalid JSON arguments for '"
                            + call.toolName() + "'.");
                }
                ctx = new ToolExecutionContext(registration, scId, call.callId(), context,
                        args, null, call.arguments(), events);
            }
            return executor.execute(ctx);
        } catch (Exception e) {
            String message = e.getMessage();
            return AgentToolResult.error("Error: "
                    + (message == null ? e.getClass().getSimpleName() : message));
        }
    }

    private static JSONObject parseArguments(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(value);
        } catch (Exception e) {
            return null;
        }
    }
}