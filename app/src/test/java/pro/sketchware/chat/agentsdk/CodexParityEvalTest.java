package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;
import pro.sketchware.chat.toolcalling.ToolCall;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Codex-parity evals: the scripted model asks how much context remains and
 * asks the user a structured question mid-run; the REAL runtime loop routes
 * both through the per-run tracker and the human-in-the-loop channel.
 * Assertions target final state, echoing the Codex testing pattern.
 */
public class CodexParityEvalTest {

    @Test
    public void eval_contextRemainingInformsWrapUp_andRunCompletes() throws Exception {
        RecordingInputChannel channel = new RecordingInputChannel();
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                // Turn 1: model checks the context budget mid-task.
                FakeAgentLlmGateway.ScriptedTurn.toolCall(
                        "get_context_remaining", "{}"),
                // Turn 2: with the report in hand, it wraps up.
                FakeAgentLlmGateway.ScriptedTurn.text(
                        "Contexto suficiente: concluí a análise dentro do limite."));

        EventStream events = new EventStream(Runnable::run, 256);
        List<AgentEvent> received = new ArrayList<>();
        events.subscribe(received::add);

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(coreRegistry(channel))
                .build();
        Agent agent = defaultAgent();

        RunResult result = runtime.run(agent, "Analise o projeto", "sc_ctx");

        assertTrue(result.isSuccessful());
        assertEquals(2, gateway.turnsConsumed());
        assertTrue("budget numbers must reach the model",
                result.getOutput().contains("dentro do limite"));

        // The tool result the model saw must be the real tracker report.
        AgentToolResult toolResult = firstCompletedToolResult(received, "get_context_remaining");
        assertNotNull("tracker report must reach the model via the real loop", toolResult);
        JSONObject report = new JSONObject(toolResult.output());
        assertTrue(report.has("tokens_left"));
        assertTrue(report.has("budget_enforced"));
    }

    @Test
    public void eval_requestUserInput_midRun_decisionComesFromUser() throws Exception {
        RecordingInputChannel channel = new RecordingInputChannel();
        channel.answer = "Kotlin, mantendo o estilo dos arquivos existentes.";
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                // Turn 1: ambiguity — model asks the user a structured question.
                FakeAgentLlmGateway.ScriptedTurn.toolCall("request_user_input",
                        new JSONObject().put("questions", new org.json.JSONArray()
                                .put(new JSONObject()
                                        .put("id", "lang")
                                        .put("header", "Language")
                                        .put("question", "Novos arquivos em Kotlin ou Java?")
                                        .put("options", new org.json.JSONArray()
                                                .put(new JSONObject()
                                                        .put("label", "Kotlin")
                                                        .put("description", "Novos arquivos em Kotlin."))
                                                .put(new JSONObject()
                                                        .put("label", "Java")
                                                        .put("description", "Novos arquivos em Java."))
                                                .put(new JSONObject()
                                                        .put("label", "Tanto faz")
                                                        .put("description", "Sem preferência.")))))
                                .toString()),
                // Turn 2: with the answer, it concludes.
                FakeAgentLlmGateway.ScriptedTurn.text(
                        "Entendido: novos arquivos em Kotlin, no estilo existente."));

        EventStream events = new EventStream(Runnable::run, 256);
        List<AgentEvent> received = new ArrayList<>();
        events.subscribe(received::add);

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(coreRegistry(channel))
                .build();

        RunResult result = runtime.run(defaultAgent(), "Adicione uma feature", "sc_user_input");

        assertTrue(result.isSuccessful());
        assertEquals(2, gateway.turnsConsumed());
        assertTrue("the user's answer must reach the model",
                result.getOutput().contains("Kotlin"));
        assertEquals("exactly one structured question was asked",
                1, channel.questions);
    }

    @Test
    public void eval_requestUserInput_dismissed_proceedsWithStatedDefault() throws Exception {
        RecordingInputChannel channel = new RecordingInputChannel();
        channel.decision = PermissionDecision.DENY;
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("request_user_input",
                        new JSONObject().put("questions", new org.json.JSONArray()
                                .put(new JSONObject()
                                        .put("id", "delete_logs")
                                        .put("header", "Delete?")
                                        .put("question", "Posso apagar os logs?")
                                        .put("options", new org.json.JSONArray()
                                                .put(new JSONObject()
                                                        .put("label", "Sim")
                                                        .put("description", "Apagar os logs antigos.")))))
                                .toString()),
                FakeAgentLlmGateway.ScriptedTurn.text(
                        "Sem resposta: mantive os logs e deixei a limpeza para você aprovar."));

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(new EventStream(Runnable::run, 64))
                .toolRegistry(coreRegistry(channel))
                .build();

        RunResult result = runtime.run(defaultAgent(), "Limpe o projeto", "sc_dismiss");

        assertTrue(result.isSuccessful());
        assertTrue(result.getOutput().contains("mantive os logs"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Core Codex-parity registry, request_user_input routed to the channel. */
    private static AxionToolRegistry coreRegistry(RecordingInputChannel channel) {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, channel);
        return registry;
    }

    /** The coordinator agent carries NO tools — the registry feeds the catalog. */
    private static Agent defaultAgent() {
        return Agent.Builder.forName("coder", "You complete coding tasks.").build();
    }

    private static AgentToolResult firstCompletedToolResult(List<AgentEvent> events, String toolName) {
        for (AgentEvent event : events) {
            if (event instanceof AgentEvent.ToolCallCompleted
                    && ((AgentEvent.ToolCallCompleted) event).getTool().equals(toolName)) {
                return ((AgentEvent.ToolCallCompleted) event).getResult();
            }
        }
        return null;
    }

    /** ApprovalHandler doubling as a scripted user. */
    private static final class RecordingInputChannel implements ApprovalHandler {
        PermissionDecision decision = PermissionDecision.ALLOW;
        String answer;
        int questions;

        @Override
        public PermissionDecision onRequest(PermissionRequest request) {
            questions++;
            return decision;
        }

        @Override
        public String lastResponseText() {
            return answer;
        }
    }
}
