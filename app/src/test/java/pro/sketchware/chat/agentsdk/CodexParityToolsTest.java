package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests for the Codex-parity core tools through the registry+router path:
 * get_context_remaining (real ContextTracker report) and request_user_input
 * (fail-closed and routed through the ApprovalHandler).
 */
public class CodexParityToolsTest {

    private static final String QUESTION_ARGS =
            "{\"questions\":[{\"id\":\"q1\",\"header\":\"Choice\",\"question\":\"Qual usar?\","
                    + "\"options\":[{\"label\":\"Room\",\"description\":\"persistencia\"},"
                    + "{\"label\":\"SQLite\",\"description\":\"puro\"}]}]}";

    /** Routes one FUNCTION call through a fresh registry+router. */
    private AgentToolResult route(String toolName, String argsJson,
                                  ApprovalHandler handler, RunContext context) {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, handler);
        AxionToolRouter router = new AxionToolRouter(registry, null, null);
        AxionToolRouter.Routed routed = router.route(
                new AxionToolRouter.Route("call_" + System.nanoTime(), toolName, argsJson),
                "sc1", context, null);
        return routed.result();
    }

    private static ToolRegistration coreTool(String name, ApprovalHandler handler) {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, handler);
        ToolRegistration registration = registry.get(name);
        assertNotNull("core tool '" + name + "' must be registered", registration);
        return registration;
    }

    // ------------------------------------------------------------------
    // get_context_remaining
    // ------------------------------------------------------------------

    @Test
    public void contextRemaining_withoutBudget_reportsGracefulFallback() throws Exception {
        RunContext context = RunContext.bare("sc1", "agent", new ContextTracker(null));
        AgentToolResult result = route("get_context_remaining", "{}", null, context);

        assertFalse(result.isError());
        JSONObject report = new JSONObject(result.output());
        assertEquals(false, report.optBoolean("budget_enforced"));
        assertTrue(report.has("tokens_left"));
    }

    @Test
    public void contextRemaining_withoutTracker_returnsGracefulFallback() throws Exception {
        RunContext legacyContext = RunContext.bare("sc1", "agent", null);
        AgentToolResult result = route("get_context_remaining", "{}", null, legacyContext);

        assertFalse(result.isError());
        JSONObject report = new JSONObject(result.output());
        assertEquals(false, report.optBoolean("budget_enforced"));
    }

    @Test
    public void contextRemaining_withBudget_reportsRealNumbers() throws Exception {
        RunBudget budget = new RunBudget(10_000);
        RunBudget.Handle handle = budget.reserve(1_500);
        budget.settle(handle, 1_500, false);
        RunContext context = RunContext.bare("sc1", "agent", new ContextTracker(budget));
        context.contextTracker().recordInputEstimate(2_000);

        AgentToolResult result = route("get_context_remaining", "{}", null, context);

        assertFalse(result.isError());
        JSONObject report = new JSONObject(result.output());
        assertEquals(true, report.optBoolean("budget_enforced"));
        assertEquals(10_000L, report.optLong("maximum_tokens"));
        assertEquals(1_500L, report.optLong("used_tokens"));
        assertEquals(8_500L, report.optLong("tokens_left"));
    }

    @Test
    public void contextRemaining_registeredWithSchemaAndNoApproval() {
        ToolRegistration registration = coreTool("get_context_remaining", null);
        assertEquals("get_context_remaining", registration.spec().name().name());
        assertFalse(registration.requiresApproval());
        assertEquals("object", registration.spec().parameters().optString("type"));
    }

    // ------------------------------------------------------------------
    // request_user_input
    // ------------------------------------------------------------------

    @Test
    public void requestUserInput_withoutHandler_failsClosedWithGuidance() {
        AgentToolResult result = route(
                "request_user_input", QUESTION_ARGS, null,
                RunContext.bare("sc1", "agent", null));

        assertTrue(result.isError());
        assertTrue(result.output().contains("Proceed with the best safe default"));
    }

    @Test
    public void requestUserInput_emptyQuestion_isRejected() {
        AgentToolResult result = route(
                "request_user_input", "{}", null,
                RunContext.bare("sc1", "agent", null));

        assertTrue(result.isError());
        assertTrue(result.output().contains("questions"));
    }

    @Test
    public void requestUserInput_missingQuestionFields_isRejected() {
        AgentToolResult result = route(
                "request_user_input",
                "{\"questions\":[{\"id\":\"q\",\"header\":\"H\",\"question\":\"\","
                        + "\"options\":[{\"label\":\"A\",\"description\":\"a\"}]}]}",
                null,
                RunContext.bare("sc1", "agent", null));

        assertTrue(result.isError());
        assertTrue(result.output().contains("id', 'header' and 'question'"));
    }

    @Test
    public void requestUserInput_routesThroughHandlerAndReturnsAnswer() {
        RecordingInputChannel channel = new RecordingInputChannel();
        channel.answer = "Room, porque o projeto já usa SQL nas telas atuais.";

        AgentToolResult result = route("request_user_input", QUESTION_ARGS, channel,
                RunContext.bare("sc1", "agent", null));

        assertFalse(result.isError());
        assertTrue(result.output().contains("Room, porque"));
        assertEquals(1, channel.questions);
        assertEquals("request_user_input", channel.lastTool);
    }

    @Test
    public void requestUserInput_dismissedFallsBackToDefault() {
        RecordingInputChannel channel = new RecordingInputChannel();
        channel.decision = PermissionDecision.DENY;

        AgentToolResult result = route("request_user_input", QUESTION_ARGS, channel,
                RunContext.bare("sc1", "agent", null));

        assertFalse(result.isError());
        assertTrue(result.output().contains("dismissed"));
    }

    @Test
    public void requestUserInput_optionsAreNormalizedToMaxFour() throws Exception {
        RecordingInputChannel channel = new RecordingInputChannel();
        channel.answer = "opt 1";

        org.json.JSONArray six = new org.json.JSONArray();
        for (int i = 0; i < 6; i++) {
            six.put(new JSONObject().put("label", "opt " + (i + 1)));
        }
        String manyOptions = new JSONObject().put("questions", new org.json.JSONArray()
                .put(new JSONObject()
                        .put("id", "q")
                        .put("header", "H")
                        .put("question", "q")
                        .put("options", six))).toString();

        route("request_user_input", manyOptions, channel,
                RunContext.bare("sc1", "agent", null));

        int forwarded = new JSONObject(channel.lastReason)
                .optJSONArray("questions")
                .optJSONObject(0)
                .optJSONArray("options")
                .length();
        assertEquals("handler must receive at most 4 options", 4, forwarded);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** ApprovalHandler that doubles as a scripted user answering the question. */
    private static final class RecordingInputChannel implements ApprovalHandler {
        PermissionDecision decision = PermissionDecision.ALLOW;
        String answer;
        int questions;
        String lastTool;
        String lastReason;

        @Override
        public PermissionDecision onRequest(PermissionRequest request) {
            questions++;
            lastTool = request.getTool();
            lastReason = request.getReason();
            return decision;
        }

        @Override
        public String lastResponseText() {
            return answer;
        }
    }
}