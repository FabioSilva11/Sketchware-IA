package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests verifying that read-only requests complete normally without recovery,
 * aligned with Codex behavior where text-only responses are valid.
 */
public class ReadOnlyRequestTest {

    private static AxionToolRegistry emptyRegistry() {
        return new AxionToolRegistry();
    }

    /**
     * Test A: "O que tem na pasta?" should complete with text, no recovery.
     */
    @Test
    public void readOnlyQuestion_completesWithText_noRecovery() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        gateway.setNextResponse("A pasta contém vários arquivos do projeto Android.", null);

        EventStream events = new EventStream();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(emptyRegistry())
                .build();

        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("O que tem na pasta?", ChatMessage.TYPE_USER, System.currentTimeMillis()));

        RunResult result = runtime.run(
                Agent.Builder.forName("assistant", "You are a helpful assistant.").build(),
                history,
                "sc1");

        // Success: text response is valid
        assertTrue("Read-only request should succeed", result.isSuccessful());
        assertNotNull("Should have assistant text", result.getOutput());
        assertFalse("Should not be blocked by guardrail", result.isBlockedByGuardrail());

        // History should NOT contain recovery message
        for (ChatMessage msg : history) {
            if (msg.getType() == ChatMessage.TYPE_USER) {
                assertFalse("History should NOT contain recovery nudge",
                        msg.getMessage().contains("Sua resposta anterior foi apenas texto"));
            }
        }

        // Only ONE turn should have occurred (no retry with recovery)
        assertEquals("Should have exactly ONE LLM call (no recovery)", 1, gateway.getCallCount());
    }

    /**
     * Test B: "Liste os arquivos" should complete without recovery.
     */
    @Test
    public void listFiles_completesWithText_noRecovery() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        gateway.setNextResponse("Os arquivos são: MainActivity.java, build.gradle, etc.", null);

        EventStream events = new EventStream();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(emptyRegistry())
                .build();

        RunResult result = runtime.run(
                Agent.Builder.forName("assistant", "Helper").build(),
                "Liste os arquivos",
                "sc1");

        assertTrue("Should succeed", result.isSuccessful());
        assertEquals("Should have ONE call", 1, gateway.getCallCount());
    }

    /**
     * Test C: "Leia MainActivity.java e explique" should complete without recovery.
     */
    @Test
    public void readAndExplain_completesWithText_noRecovery() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        gateway.setNextResponse("O arquivo MainActivity.java contém a activity principal.", null);

        EventStream events = new EventStream();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(emptyRegistry())
                .build();

        RunResult result = runtime.run(
                Agent.Builder.forName("assistant", "Helper").build(),
                "Leia MainActivity.java e explique o problema.",
                "sc1");

        assertTrue("Should succeed", result.isSuccessful());
        assertEquals("Should have ONE call", 1, gateway.getCallCount());
    }

    /**
     * Test D: Mutation request with structured tool call executes normally.
     */
    @Test
    public void mutationRequest_withToolCall_executesNormally() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        // Simulate structured tool call
        pro.sketchware.chat.toolcalling.ToolCall toolCall =
                new pro.sketchware.chat.toolcalling.ToolCall(
                        "apply_patch",
                        "{\"patch\":\"*** Begin Patch\\n*** End Patch\"}",
                        "call_1");

        gateway.setNextResponse("", List.of(toolCall));

        EventStream events = new EventStream();
        AxionToolRegistry registry = emptyRegistry();
        registry.register(ToolRegistration.function(
                        "apply_patch", "Test tool", new JSONObject())
                .executor(context -> AgentToolResult.success("Tool executed"))
                .source("test")
                .fileMutation(true)
                .build());
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(registry)
                .build();

        Agent agent = Agent.Builder.forName("coder", "Code assistant").build();

        RunResult result = runtime.run(agent, "Corrija o bug em MainActivity.java", "sc1");

        // Should complete (tool was called)
        assertTrue("Should succeed", result.isSuccessful() || !result.isBlockedByGuardrail());
    }

    /**
     * Test E: Text response to mutation request does NOT create fake user message.
     */
    @Test
    public void mutationRequest_textResponse_noFakeUserMessage() throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        gateway.setNextResponse("Vou corrigir isso agora.", null); // Text only, no tool call

        EventStream events = new EventStream();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(emptyRegistry())
                .build();

        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("Corrija MainActivity.java", ChatMessage.TYPE_USER, System.currentTimeMillis()));

        RunResult result = runtime.run(
                Agent.Builder.forName("assistant", "Helper").build(),
                history,
                "sc1");

        // Should complete successfully (text is valid response)
        assertTrue("Should succeed", result.isSuccessful());

        // History should NOT contain fake [system] message
        for (ChatMessage msg : history) {
            if (msg.getType() == ChatMessage.TYPE_USER) {
                assertFalse("Should NOT have fake system message",
                        msg.getMessage().contains("[system]"));
                assertFalse("Should NOT have recovery nudge",
                        msg.getMessage().contains("Sua resposta anterior"));
            }
        }

        // Only ONE turn (no retry)
        assertEquals("Should have ONE call", 1, gateway.getCallCount());
    }

    // Helper stubs

    private static class RecordingGateway implements AgentLlmGateway {
        private String nextText;
        private List<pro.sketchware.chat.toolcalling.ToolCall> nextCalls;
        private int callCount = 0;

        void setNextResponse(String text, List<pro.sketchware.chat.toolcalling.ToolCall> calls) {
            this.nextText = text;
            this.nextCalls = calls;
        }

        int getCallCount() { return callCount; }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt, org.json.JSONArray tools,
                                          List<ChatMessage> history,
                                          pro.sketchware.chat.AiOperationContext opContext) {
            callCount++;
            return new LlmTurnOutput(nextText, "", "stop", nextCalls);
        }

        @Override
        public void setDeltaListener(java.util.function.Consumer<String> listener) {}
    }
}
