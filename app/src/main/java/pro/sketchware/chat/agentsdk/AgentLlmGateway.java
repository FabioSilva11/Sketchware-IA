package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.AiOperationContext;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.ContextBuilder;
import pro.sketchware.chat.agentsdk.tools.ProviderToolCapabilities;
import pro.sketchware.chat.agentsdk.tools.ToolCatalog;
import pro.sketchware.chat.agentsdk.tools.ToolSpecSerializer;

import org.json.JSONArray;

import java.util.List;

/**
 * LLM boundary of the agentsdk, mirroring the model interface of
 * openai-agents-js: the {@link AgentRuntime} only talks to an LLM through
 * this port, which makes the whole run loop unit-testable with fakes.
 *
 * <p>Turn state lives in the {@code messages} list — the gateway appends the
 * assistant turn (including tool calls) and tool results as {@link ChatMessage}s,
 * so the next call carries full loop history and the {@code ContextBuilder}
 * can produce provider-native envelopes (OpenAI/Anthropic/Gemini/XML).</p>
 *
 * <p><b>Streaming contract (item 6 of the migration):</b> the runtime calls
 * {@link #setDeltaListener} before every turn and {@code setDeltaListener(null)}
 * in the turn's {@code finally}. Implementations MUST store the latest
 * listener and forward assistant text deltas to it during the turn — never
 * only to a listener fixed in the constructor, and never leaking a previous
 * run's listener into a new run.</p>
 *
 * <p><b>Structured-tool contract (item 7 of the tool-call execution
 * architecture):</b> tool calls arrive ONLY from the provider's structured
 * envelope (native tool calls accumulated by callId during streaming).
 * Assistant text is never mined for XML/JSON/DSML tool protocols and never
 * re-emitted as a tool call by a v2 gateway.</p>
 */
public interface AgentLlmGateway {

    /**
     * Marker that declares the NATIVE_TOOL_CALLS_ONLY protocol: the provider
     * delivers structured calls from its envelope and the gateway never
     * mines assistant text for tool calls. Set by the v2 gateways; legacy
     * text-protocol listeners do not implement it.
     */
    interface NativeToolCallsOnly {
    }

    /**
     * Runs one LLM turn for the active agent and returns the parsed turn.
     *
     * @param systemPrompt      resolved system instruction for the active agent
     * @param tools             OpenAI-style function schemas for the active agent's tools
     * @param messages          mutable conversation history; the gateway appends this turn
     * @param operationContext  frozen provider/model selection (may be {@code null})
     */
    LlmTurnOutput completeTurn(String systemPrompt,
                               JSONArray tools,
                               List<ChatMessage> messages,
                               AiOperationContext operationContext) throws Exception;

    /**
     * Runs one LLM turn against the canonical {@link ToolCatalog} instead of a
     * pre-serialized schema array. The default implementation resolves the
     * provider capability and serializes per capability via
     * {@link ToolSpecSerializer#toProviderPayload} (FUNCTION_ONLY for every
     * transport shipped by this app), then delegates to
     * {@link #completeTurn(String, JSONArray, List, AiOperationContext)}, which
     * keeps every existing gateway implementation working untouched. The
     * catalog is NEVER flattened by the caller — freeform/namespace kinds stay
     * faithful until the serializer — so a {@code FREEFORM → FUNCTION} downgrade
     * only ever happens through the declared fallback path.
     *
     * @param systemPrompt     resolved system instruction for the active agent
     * @param catalog          the canonical model-visible catalog snapshot
     * @param messages         mutable conversation history; the gateway appends this turn
     * @param operationContext frozen provider/model selection (may be {@code null})
     */
    default LlmTurnOutput completeTurn(String systemPrompt,
                                       ToolCatalog catalog,
                                       List<ChatMessage> messages,
                                       AiOperationContext operationContext) throws Exception {
        JSONArray schemas = ToolSpecSerializer.toProviderPayload(
                catalog, ProviderToolCapabilities.FUNCTION_ONLY).payload();
        return completeTurn(systemPrompt, schemas, messages, operationContext);
    }

    /** Cancels the in-flight request, if any. */
    default void cancel() {
    }

    /**
     * Registers the consumer of assistant text deltas for the NEXT turn.
     * The runtime sets it before each turn and clears it in the turn's
     * {@code finally}; a listener registered for a run must never survive
     * into another run. Default: no streaming support.
     */
    default void setDeltaListener(java.util.function.Consumer<String> listener) {
    }
}
