package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.AiOperationContext;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.toolcalling.ToolCall;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic {@link AgentLlmGateway} for evals and integration tests:
 * each call to {@link #completeTurn} pops the next scripted turn.
 *
 * <p>This is the Axion port of the Codex test pattern — mock the model, run
 * the real loop, assert on final state. A test builds {@link ScriptedTurn}s
 * (assistant text or tool calls) and asserts on the {@link RunResult} plus
 * whatever side effects the scripted tool calls produced on a real temp
 * workspace.</p>
 */
public final class FakeAgentLlmGateway implements AgentLlmGateway {

    /** One scripted LLM response. */
    public static final class ScriptedTurn {
        private final String content;
        private final List<ToolCall> toolCalls;

        private ScriptedTurn(String content, List<ToolCall> toolCalls) {
            this.content = content == null ? "" : content;
            this.toolCalls = toolCalls == null ? Collections.emptyList() : toolCalls;
        }

        /** Assistant text turn (loop ends: no tool calls). */
        public static ScriptedTurn text(String content) {
            return new ScriptedTurn(content, null);
        }

        /** Tool-call turn (loop continues). */
        public static ScriptedTurn toolCall(String name, String jsonArgs) {
            return new ScriptedTurn("", Collections.singletonList(new ToolCall(name, jsonArgs, null)));
        }

        /** Multiple tool calls in one turn. */
        public static ScriptedTurn toolCalls(ToolCall... calls) {
            return new ScriptedTurn("", Arrays.asList(calls));
        }

        /** Assistant text AND structured tool call in the same turn (test 11). */
        public static ScriptedTurn textWithToolCall(String content, ToolCall... calls) {
            return new ScriptedTurn(content, Arrays.asList(calls));
        }

        public String content() {
            return content;
        }

        public List<ToolCall> toolCalls() {
            return toolCalls;
        }
    }

    private final List<ScriptedTurn> script;
    private final List<String> requestedSystemPrompts = new ArrayList<>();
    private final List<String> requestedToolSchemas = new ArrayList<>();
    private int cursor;

    public FakeAgentLlmGateway(ScriptedTurn... turns) {
        this.script = Collections.unmodifiableList(Arrays.asList(turns));
    }

    @Override
    public LlmTurnOutput completeTurn(String systemPrompt, JSONArray tools,
                                      List<ChatMessage> messages, AiOperationContext ctx) {
        requestedSystemPrompts.add(systemPrompt == null ? "" : systemPrompt);
        requestedToolSchemas.add(tools == null ? "" : tools.toString());
        if (cursor >= script.size()) {
            return new LlmTurnOutput("Error: script exhausted.", "", "stop", null);
        }
        ScriptedTurn turn = script.get(cursor++);
        return new LlmTurnOutput(turn.content, "", "stop", new ArrayList<>(turn.toolCalls));
    }

    public int turnsConsumed() {
        return cursor;
    }

    public List<String> requestedSystemPrompts() {
        return Collections.unmodifiableList(requestedSystemPrompts);
    }

    /** True when the gateway saw a schema for {@code toolName}. */
    public boolean wasToolExposed(String toolName) {
        for (String schemas : requestedToolSchemas) {
            if (schemas.contains("\"" + toolName + "\"")) {
                return true;
            }
        }
        return false;
    }
}
