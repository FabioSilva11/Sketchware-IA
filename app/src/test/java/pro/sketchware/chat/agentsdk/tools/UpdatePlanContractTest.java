package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.ChatPlanManager;
import pro.sketchware.chat.agentsdk.AgentToolResult;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * update_plan parity (Codex plan_spec.rs): the structured contract is exactly
 * {"explanation"?, "plan":[{"step","status"}]} — plan required, status enum,
 * at most one in_progress step, and the model-maintained plan lands in
 * ChatPlanManager so the UI surfaces it.
 */
public class UpdatePlanContractTest {

    private static final String SC = "sc_plan";

    @After
    public void tearDown() {
        ChatPlanManager.clearModelPlan(SC);
    }

    private ToolRegistration planReg() {
        return ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("update_plan"), "Updates the task plan.",
                        new JSONObject()))
                .executor(new UpdatePlanExecutor())
                .source("core")
                .build();
    }

    @Test
    public void validPlanPersistsToChatPlanManager() throws Exception {
        AgentToolResult result = run("{\"explanation\":\"revising\",\"plan\":["
                + "{\"step\":\"Inspect\",\"status\":\"pending\"},"
                + "{\"step\":\"Edit\",\"status\":\"in_progress\"},"
                + "{\"step\":\"Verify\",\"status\":\"completed\"}]}");
        assertFalse(result.isError());
        List<ChatPlanManager.Task> tasks = ChatPlanManager.buildPlan(null, SC,
                new ArrayList<>(), false, "");
        assertEquals(3, tasks.size());
        assertEquals("Inspect", tasks.get(0).title);
        assertEquals(ChatPlanManager.STATUS_PENDING, tasks.get(0).status);
        assertEquals(ChatPlanManager.STATUS_RUNNING, tasks.get(1).status);
        assertEquals(ChatPlanManager.STATUS_DONE, tasks.get(2).status);
    }

    @Test
    public void missingPlanArrayIsError() throws Exception {
        AgentToolResult result = run("{\"explanation\":\"no plan\"}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("plan"));
    }

    @Test
    public void moreThanTwoInProgressIsRejected() throws Exception {
        AgentToolResult result = run("{\"plan\":["
                + "{\"step\":\"A\",\"status\":\"in_progress\"},"
                + "{\"step\":\"B\",\"status\":\"in_progress\"}]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("at most one"));
    }

    @Test
    public void unknownStatusIsRejected() throws Exception {
        AgentToolResult result = run("{\"plan\":[{\"step\":\"A\",\"status\":\"doing\"}]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("unknown 'status'"));
    }

    @Test
    public void emptyPlanIsRejected() throws Exception {
        AgentToolResult result = run("{\"plan\":[]}");
        assertTrue(result.isError());
        assertTrue(result.output().contains("at least one"));
    }

    private static AgentToolResult run(String argsJson) throws Exception {
        ToolRegistration reg = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("update_plan"), "Updates the task plan.",
                        new JSONObject()))
                .executor(new UpdatePlanExecutor())
                .build();
        ToolExecutionContext ctx = new ToolExecutionContext(reg, SC, "call", null,
                new JSONObject(argsJson), null, argsJson);
        return reg.executor().execute(ctx);
    }
}