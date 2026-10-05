package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.AiOperationContext;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.toolcalling.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The former {@code RunnerTest} scenarios, ported to the v2-only
 * {@link AgentRuntime} before the legacy Runner was deleted. Kept as the
 * regression floor for loop semantics: history shape, max-turns and
 * rejection paths.
 */
public class LegacyParityPortedTest {

    /** Gateway double with scripted turns plus history recording. */
    private static final class ScriptedHistoryGateway implements AgentLlmGateway {
        private final List<LlmTurnOutput> script = new ArrayList<>();
        private final List<String> systemPrompts = new ArrayList<>();
        final List<List<ChatMessage>> receivedHistories =
                Collections.synchronizedList(new ArrayList<>());
        private int index;

        void add(LlmTurnOutput turn) {
            script.add(turn);
        }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt, JSONArray tools,
                                          List<ChatMessage> messages,
                                          AiOperationContext operationContext) {
            systemPrompts.add(systemPrompt);
            receivedHistories.add(new ArrayList<>(messages));
            if (index < script.size()) {
                return script.get(index++);
            }
            return new LlmTurnOutput("done", "", "stop", Collections.emptyList());
        }
    }

    private static ToolCall call(String name, String argsJson) {
        return new ToolCall(name, argsJson, "call_1");
    }

    /** Registry carrying ONE tool: {@code echo} (mirrors the removed stub). */
    private static pro.sketchware.chat.agentsdk.tools.AxionToolRegistry echoRegistry() {
        pro.sketchware.chat.agentsdk.tools.AxionToolRegistry registry =
                new pro.sketchware.chat.agentsdk.tools.AxionToolRegistry();
        registry.register(pro.sketchware.chat.agentsdk.tools.ToolRegistration.function(
                        "echo", "Echoes the input.", new JSONObject())
                .executor(context -> AgentToolResult.success(
                        "echo:" + context.functionArguments().optString("text", "")))
                .source("test")
                .build());
        return registry;
    }

    @Test
    public void simpleTextRunReturnsFinalOutput() {
        ScriptedHistoryGateway gateway = new ScriptedHistoryGateway();
        gateway.add(new LlmTurnOutput("Hello!", "", "stop", Collections.emptyList()));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(echoRegistry())
                .build();
        Agent agent = Agent.Builder.forName("a", "instructions").build();

        RunResult result = runtime.run(agent, "hi", "sc1");

        assertTrue(result.isSuccessful());
        assertEquals("Hello!", result.getOutput());
        assertTrue("agent instructions must reach the gateway",
                gateway.systemPrompts.get(0).contains("instructions"));
    }

    @Test
    public void toolCallIsExecutedAndResultAppendedToHistory() {
        ScriptedHistoryGateway gateway = new ScriptedHistoryGateway();
        gateway.add(new LlmTurnOutput("", "", "tool_calls",
                Collections.singletonList(call("echo", "{\"text\":\"x\"}"))));
        gateway.add(new LlmTurnOutput("finished", "", "stop", Collections.emptyList()));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(echoRegistry())
                .permissions(new PermissionLayer(ToolPolicy.permissive(), null, null))
                .build();
        Agent agent = Agent.Builder.forName("a", "i").build();

        RunResult result = runtime.run(agent, "use the tool", "sc1");

        assertTrue(result.isSuccessful());
        assertEquals("finished", result.getOutput());
        List<ChatMessage> secondTurnHistory = gateway.receivedHistories.get(1);
        ChatMessage last = secondTurnHistory.get(secondTurnHistory.size() - 1);
        assertEquals(ChatMessage.TYPE_TOOL, last.getType());
        assertEquals("echo:x", last.getToolResult());
    }

    @Test
    public void unknownToolReturnsErrorResultToModel() {
        ScriptedHistoryGateway gateway = new ScriptedHistoryGateway();
        gateway.add(new LlmTurnOutput("", "", "tool_calls",
                Collections.singletonList(call("nope", "{}"))));
        gateway.add(new LlmTurnOutput("ok", "", "stop", Collections.emptyList()));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(echoRegistry())
                .build();
        Agent agent = Agent.Builder.forName("a", "i").build();

        RunResult result = runtime.run(agent, "go", "sc1");

        assertTrue(result.isSuccessful());
        List<ChatMessage> secondTurnHistory = gateway.receivedHistories.get(1);
        ChatMessage last = secondTurnHistory.get(secondTurnHistory.size() - 1);
        assertTrue(last.getToolResult().contains("unknown tool"));
    }

    @Test
    public void maxTurnsCeilingStopsRun() {
        ScriptedHistoryGateway gateway = new ScriptedHistoryGateway();
        gateway.add(new LlmTurnOutput("", "", "tool_calls",
                Collections.singletonList(call("echo", "{}"))));
        gateway.add(new LlmTurnOutput("", "", "tool_calls",
                Collections.singletonList(call("echo", "{}"))));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(echoRegistry())
                .permissions(new PermissionLayer(ToolPolicy.permissive(), null, null))
                .maxTurns(2)
                .build();
        Agent agent = Agent.Builder.forName("a", "i").build();

        RunResult result = runtime.run(agent, "loop", "sc1");

        assertFalse(result.isSuccessful());
        assertTrue(result.isMaxTurnsReached());
    }

    @Test
    public void rejectedApprovalSkipsExecution() {
        ScriptedHistoryGateway gateway = new ScriptedHistoryGateway();
        gateway.add(new LlmTurnOutput("", "", "tool_calls",
                Collections.singletonList(call("echo", "{\"text\":\"x\"}"))));
        gateway.add(new LlmTurnOutput("done", "", "stop", Collections.emptyList()));
        PermissionLayer layer = new PermissionLayer(ToolPolicy.interactive(),
                request -> PermissionDecision.DENY, null);
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .permissions(layer)
                .toolRegistry(echoRegistry())
                .build();
        Agent agent = Agent.Builder.forName("a", "i").build();

        RunResult result = runtime.run(agent, "go", "sc1");

        assertTrue(result.isSuccessful());
        List<ChatMessage> secondTurnHistory = gateway.receivedHistories.get(1);
        ChatMessage last = secondTurnHistory.get(secondTurnHistory.size() - 1);
        assertTrue(last.getToolResult().contains("denied"));
    }

    @Test
    public void inputGuardrailBlocksBeforeFirstLlmCall() {
        ScriptedHistoryGateway gateway = new ScriptedHistoryGateway();
        gateway.add(new LlmTurnOutput("never", "", "stop", Collections.emptyList()));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(echoRegistry())
                .inputGuardrails(new Guardrail() {
                    @Override
                    public GuardrailResult checkInput(String userInput) {
                        return GuardrailResult.block("banned");
                    }
                })
                .build();
        Agent agent = Agent.Builder.forName("a", "i").build();

        RunResult result = runtime.run(agent, "bad input", "sc1");

        assertFalse(result.isSuccessful());
        assertTrue(result.isBlockedByGuardrail());
        assertEquals("banned", result.getGuardrailResult().info());
        assertEquals(0, gateway.receivedHistories.size());
    }
}
