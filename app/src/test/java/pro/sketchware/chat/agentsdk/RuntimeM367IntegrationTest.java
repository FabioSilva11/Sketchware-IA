package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.toolcalling.ToolCall;
import pro.sketchware.chat.workspace.LocalFolderWorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integration tests for the round-2 quick wins wired into the AgentRuntime:
 * M3 RunBudget gate, M6 assistant delta streaming, M7 AGENTS.md injection.
 */
public class RuntimeM367IntegrationTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    // ------------------------------------------------------------------
    // M3: budget gate stops the run BEFORE the request that would exceed it
    // ------------------------------------------------------------------

    @Test
    public void budgetExhausted_stopsBeforeExtraRequest() {
        // Tiny budget: the estimate of turn 1 (input + max output) already
        // exceeds it, so not even the first request is sent.
        RecordingGateway gateway = new RecordingGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("should never be produced"));
        RunBudget budget = new RunBudget(64);
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(emptyRegistry())
                .budget(budget)
                .maxOutputTokensPerTurn(2048)
                .build();

        RunResult result = runtime.run(scriptedAgent(), "corrija o bug", "sc_m3");

        assertFalse(result.isSuccessful());
        assertTrue(result.getFailureReason().contains("budget"));
        assertEquals("no LLM request may be made on an exhausted budget",
                0, gateway.turnRequests);
    }

    @Test
    public void budgetSettlesAfterEachTurn_allowsNextTurnWithinBudget() {
        RecordingGateway gateway = new RecordingGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("resposta curta"));
        RunBudget budget = new RunBudget(100_000);
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(emptyRegistry())
                .budget(budget)
                .maxOutputTokensPerTurn(1024)
                .build();

        RunResult result = runtime.run(scriptedAgent(), "oi", "sc_m3b");

        assertTrue(result.isSuccessful());
        assertEquals(1, gateway.turnRequests);
        assertTrue("spent must reflect the reserved estimate",
                budget.spent() > 0);
    }

    // ------------------------------------------------------------------
    // M6: assistant deltas reach the EventStream
    // ------------------------------------------------------------------

    @Test
    public void deltaListener_emitsAssistantMessageDeltaEvents() {
        StreamingGateway gateway = new StreamingGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("texto final"));
        EventStream stream = new EventStream(Runnable::run, 64);
        List<AgentEvent> received = new ArrayList<>();
        stream.subscribe(received::add);

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(emptyRegistry())
                .events(stream)
                .build();
        RunResult result = runtime.run(scriptedAgent(), "oi", "sc_m6");

        assertTrue(result.isSuccessful());
        boolean sawDelta = false;
        for (AgentEvent event : received) {
            if (event instanceof AgentEvent.AssistantMessageDelta) {
                sawDelta = true;
            }
        }
        assertTrue("streaming gateway deltas must surface as events", sawDelta);
    }

    @Test
    public void deltaListenerIsClearedAfterTurn() {
        StreamingGateway gateway = new StreamingGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("fim"));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(emptyRegistry())
                .build();
        runtime.run(scriptedAgent(), "oi", "sc_m6b");
        assertTrue("listener must be detached after the turn",
                gateway.activeListener == null || gateway.clearedAfterTurn);
    }

    // ------------------------------------------------------------------
    // M7: AGENTS.md from the workspace lands in the system prompt
    // ------------------------------------------------------------------

    @Test
    public void projectInstructions_areInjectedIntoSystemPrompt() throws IOException {
        File root = temp.newFolder("ws");
        WorkspaceFileSystem fs = new LocalFolderWorkspaceFileSystem(root);
        fs.writeText("AGENTS.md", "# Regras do projeto\nSem TODOs no código.");
        // The runtime resolves instructions from the RUN's filesystem: force
        // the scId→workspace resolution instead of the legacy global override.
        RunContextFactory.setOverrideForTest(new RunContextFactory.Resolved(
                new WorkspaceIdentity("sc_m7", "", root.toURI().toString(), "ws", ""),
                fs));

        RecordingGateway gateway = new RecordingGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("ok"));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(emptyRegistry())
                .includeProjectInstructions(true)
                .build();

        try {
            RunResult result = runtime.run(scriptedAgent(), "oi", "sc_m7");
            assertTrue(result.isSuccessful());
            assertNotNull(gateway.lastSystemPrompt);
            assertTrue("system prompt must contain AGENTS.md content",
                    gateway.lastSystemPrompt.contains("Regras do projeto"));
        } finally {
            RunContextFactory.clearOverrideForTest();
        }
    }

    @Test
    public void projectInstructions_missingFileKeepsCleanPrompt() {
        RecordingGateway gateway = new RecordingGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("ok"));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(emptyRegistry())
                .includeProjectInstructions(true)
                .build();

        runtime.run(scriptedAgent(), "oi", "sc_m7b");

        assertNotNull(gateway.lastSystemPrompt);
        assertFalse(gateway.lastSystemPrompt.contains("AGENTS.md do workspace"));
    }

    @Test
    public void projectInstructions_injectionCanBeDisabled() throws IOException {
        File root = temp.newFolder("ws2");
        WorkspaceFileSystem fs = new LocalFolderWorkspaceFileSystem(root);
        fs.writeText("AGENTS.md", "# Regras\nnão injetar");

        RecordingGateway gateway = new RecordingGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("ok"));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(emptyRegistry())
                .includeProjectInstructions(false)
                .build();

        runtime.run(scriptedAgent(), "oi", "sc_m7c");

        assertFalse(gateway.lastSystemPrompt.contains("Regras"));
    }

    // ------------------------------------------------------------------
    // ProjectInstructions unit behavior
    // ------------------------------------------------------------------

    @Test
    public void projectInstructions_truncatesToBudget() throws IOException {
        File root = temp.newFolder("ws3");
        WorkspaceFileSystem fs = new LocalFolderWorkspaceFileSystem(root);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            big.append("linha ").append(i).append('\n');
        }
        fs.writeText("AGENTS.md", big.toString());

        String loaded = ProjectInstructions.load(fs, 200);
        assertTrue(loaded.length() < 260);
        assertTrue(loaded.contains("truncado"));
    }

    @Test
    public void projectInstructions_missingFileIsEmpty() throws IOException {
        File root = temp.newFolder("ws4");
        WorkspaceFileSystem fs = new LocalFolderWorkspaceFileSystem(root);
        assertEquals("", ProjectInstructions.load(fs, 500));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Agent scriptedAgent() {
        return Agent.Builder.forName("coordinator", "You are a helpful coding agent.")
                .build();
    }

    /** A registry carrying no model-facing tools (these tests need none). */
    private static AxionToolRegistry emptyRegistry() {
        return new AxionToolRegistry();
    }

    /** Records system prompts/requests; behaves like the scripted fake. */
    private static final class RecordingGateway implements AgentLlmGateway {
        private final List<FakeAgentLlmGateway.ScriptedTurn> script;
        private int cursor;
        int turnRequests;
        String lastSystemPrompt;

        RecordingGateway(FakeAgentLlmGateway.ScriptedTurn... turns) {
            this.script = Collections.unmodifiableList(java.util.Arrays.asList(turns));
        }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt, org.json.JSONArray tools,
                                          List<ChatMessage> messages,
                                          pro.sketchware.chat.AiOperationContext ctx) {
            turnRequests++;
            lastSystemPrompt = systemPrompt;
            if (cursor < script.size()) {
                FakeAgentLlmGateway.ScriptedTurn turn = script.get(cursor++);
                return new LlmTurnOutput(turn.content(), "", "stop", new ArrayList<>(turn.toolCalls()));
            }
            return new LlmTurnOutput("done", "", "stop", null);
        }
    }

    /** Gateway that streams deltas while producing the scripted final text. */
    private static final class StreamingGateway implements AgentLlmGateway {
        private final List<FakeAgentLlmGateway.ScriptedTurn> script;
        private int cursor;
        volatile java.util.function.Consumer<String> activeListener;
        volatile boolean clearedAfterTurn;

        StreamingGateway(FakeAgentLlmGateway.ScriptedTurn... turns) {
            this.script = Collections.unmodifiableList(java.util.Arrays.asList(turns));
        }

        @Override
        public void setDeltaListener(java.util.function.Consumer<String> listener) {
            if (listener == null) {
                clearedAfterTurn = activeListener != null;
            }
            this.activeListener = listener;
        }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt, org.json.JSONArray tools,
                                          List<ChatMessage> messages,
                                          pro.sketchware.chat.AiOperationContext ctx) {
            java.util.function.Consumer<String> listener = activeListener;
            if (listener != null) {
                listener.accept("texto ");
                listener.accept("final ");
            }
            if (cursor < script.size()) {
                FakeAgentLlmGateway.ScriptedTurn turn = script.get(cursor++);
                return new LlmTurnOutput(turn.content(), "", "stop", new ArrayList<>(turn.toolCalls()));
            }
            return new LlmTurnOutput("", "", "stop", null);
        }
    }
}
