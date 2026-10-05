package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.ApprovalHandler;
import pro.sketchware.chat.agentsdk.PermissionDecision;

import org.json.JSONObject;
import org.junit.Test;

/**
 * request_user_input parity (Codex request_user_input_spec.rs): the JSON
 * contract is {"questions":[{"id","header","question","options"?}]} with a
 * maximum of three questions and four options; empty/malformed input is a
 * fail-closed error; with a handler, the first question reaches the user.
 */
public class RequestUserInputContractTest {

    private ToolRegistration reg(ApprovalHandler handler) {
        return ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("request_user_input"), "Asks the user a structured question.",
                        new JSONObject()))
                .executor(new RequestUserInputExecutor(handler))
                .build();
    }

    @Test
    public void missingQuestionsArrayIsError() throws Exception {
        AgentToolResult result = execute(reg(null), "{}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("questions"));
    }

    @Test
    public void emptyQuestionsArrayIsError() throws Exception {
        AgentToolResult result = execute(reg(null), "{\"questions\":[]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("at least one"));
    }

    @Test
    public void moreThanThreeQuestionsIsRejected() throws Exception {
        StringBuilder sb = new StringBuilder("{\"questions\":[");
        for (int i = 1; i <= 4; i++) {
            if (i > 1) {
                sb.append(',');
            }
            sb.append(oneQuestion("q" + i));
        }
        sb.append("]}");
        AgentToolResult result = execute(reg(null), sb.toString());
        assertTrue(result.isError());
        assertTrue(result.output().contains("at most 3"));
    }

    @Test
    public void questionNeedsIdHeaderAndQuestionText() throws Exception {
        // Missing 'question'
        AgentToolResult result = execute(reg(null),
                "{\"questions\":[{\"id\":\"q1\",\"header\":\"H\",\"options\":["
                        + "{\"label\":\"a\",\"description\":\"d\"}]}]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("non-empty"));
    }

    @Test
    public void optionNeedsLabel() throws Exception {
        AgentToolResult result = execute(reg(null),
                "{\"questions\":[{\"id\":\"q1\",\"header\":\"H\",\"question\":\"choice?\","
                        + "\"options\":[{\"label\":\"\",\"description\":\"d\"}]}]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("label"));
    }

    @Test
    public void questionWithoutOptionsIsRejected() throws Exception {
        AgentToolResult result = execute(reg(null),
                "{\"questions\":[{\"id\":\"q1\",\"header\":\"H\",\"question\":\"choice?\"}]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("options"));
    }

    @Test
    public void noHandlerFailsClosedWithSafeDefaultHint() throws Exception {
        AgentToolResult result = execute(reg(null),
                "{\"questions\":[{\"id\":\"q1\",\"header\":\"H\",\"question\":\"proceed?\","
                        + "\"options\":[{\"label\":\"yes\",\"description\":\"go\"},"
                        + "{\"label\":\"no\",\"description\":\"stop\"}]}]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("no user input channel"));
    }

    @Test
    public void handlerAnswerBecomesTheToolResult() throws Exception {
        ApprovalHandler allowed = new StubApprovalHandler(PermissionDecision.ALLOW);
        AgentToolResult result = execute(reg(allowed),
                "{\"questions\":[{\"id\":\"q1\",\"header\":\"H\",\"question\":\"proceed?\","
                        + "\"options\":[{\"label\":\"yes\",\"description\":\"go\"},"
                        + "{\"label\":\"no\",\"description\":\"stop\"}]}]}");
        assertFalse(result.isError());
        assertTrue(result.output().contains("User answer"));
    }

    private static String oneQuestion(String id) {
        return "{\"id\":\"" + id + "\",\"header\":\"H\",\"question\":\"Q?\",\"options\":["
                + "{\"label\":\"a\",\"description\":\"d\"},"
                + "{\"label\":\"b\",\"description\":\"e\"}]}";
    }

    private static AgentToolResult execute(ToolRegistration reg, String argsJson) throws Exception {
        ToolExecutionContext ctx = new ToolExecutionContext(reg, "sc", "call", null,
                new JSONObject(argsJson), null, argsJson);
        return reg.executor().execute(ctx);
    }

    /** Minimal I/O-less handler for tests (ALLOW + canned answer text). */
    static final class StubApprovalHandler implements ApprovalHandler {
        private final PermissionDecision decision;

        StubApprovalHandler(PermissionDecision decision) {
            this.decision = decision;
        }

        @Override
        public PermissionDecision onRequest(pro.sketchware.chat.agentsdk.PermissionRequest request) {
            return decision;
        }

        @Override
        public String lastResponseText() {
            return "canned answer";
        }
    }
}