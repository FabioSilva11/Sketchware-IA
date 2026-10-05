package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.AiChatSettingsHelper;
import pro.sketchware.chat.AiOperationContext;
import pro.sketchware.chat.AiProviderService;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.ContextBuilder;
import pro.sketchware.chat.Tool;
import pro.sketchware.chat.agentsdk.tools.ProviderCatalogPayload;
import pro.sketchware.chat.agentsdk.tools.ProviderToolCapabilitiesResolver;
import pro.sketchware.chat.agentsdk.tools.ToolCatalog;
import pro.sketchware.chat.agentsdk.tools.ToolSpec;
import pro.sketchware.chat.agentsdk.tools.ToolSpecSerializer;
import pro.sketchware.chat.toolcalling.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production {@link AgentLlmGateway}: one streaming provider request per turn
 * through {@link AiProviderService#sendStreamingMessage}, using the shared
 * {@link ContextBuilder} (provider-aware history, budgets, compaction) and
 * the provider's native tool-call envelope when available.
 *
 * <p>This is what lets the {@link AgentRuntime} fully replace the legacy
 * loop: the runtime sits on the exact same transport the legacy chat used —
 * streamed deltas, reasoning, structured tool calls — with the SDK loop on
 * top.</p>
 *
 * <p>Contracts implemented here:</p>
 * <ul>
 *   <li><b>setDeltaListener</b> (item 6): the runtime-registered listener is
 *       STORED and used during streaming; it replaces (never merges with)
 *       the constructor hook and is cleared by the runtime after the turn.</li>
 *   <li><b>frozen operationContext</b> (items 10/11): provider/model/chatMode
 *       come from the turn's {@link AiOperationContext}; preferences are only
 *       a fallback BEFORE a run starts, never a per-turn mutable source.</li>
 *   <li><b>usage</b> (item 17): the turn carries provider-reported
 *       {@link TokenUsage} when the provider envelope declares it.</li>
 *   <li><b>native-only tools</b>: calls are deduplicated by callId (never
 *       name) and text is never mined for tool protocols.</li>
 * </ul>
 */
public final class AxionAgentGateway implements AgentLlmGateway, AgentLlmGateway.NativeToolCallsOnly {

    private final AiProviderService aiService;
    private final String fallbackChatMode;
    /** Constructor hook kept for compatibility; the runtime listener wins. */
    private final java.util.function.Consumer<String> deltaListenerHook;
    /** Listener registered by the runtime for the CURRENT turn (item 6). */
    private volatile java.util.function.Consumer<String> turnDeltaListener;
    private volatile boolean cancelRequested;
    private volatile TokenUsage lastUsage;
    /** Frozen model name from the run's operation context (capability resolution). */
    private volatile String frozenModelName = "";
    /** Last capability-aware catalog serialization of this gateway. */
    private volatile ProviderCatalogPayload lastToolPayload;

    public AxionAgentGateway(AiProviderService aiService, String chatMode) {
        this(aiService, chatMode, null);
    }

    /** Stream listener that ALSO declares the native-only tool protocol. */
    private interface NativeOnlyStreamListener extends
            AiProviderService.StreamListener, AiProviderService.NativeToolCallsOnly {
    }

    /**
     * @param deltaListenerHook optional bridge from provider content deltas to
     *                          the host UI while the request is in flight
     *                          (called on provider threads).
     */
    public AxionAgentGateway(AiProviderService aiService, String chatMode,
                             java.util.function.Consumer<String> deltaListenerHook) {
        this.aiService = aiService;
        this.fallbackChatMode = chatMode == null ? "agent" : chatMode;
        this.deltaListenerHook = deltaListenerHook;
    }

    @Override
    public void setDeltaListener(java.util.function.Consumer<String> listener) {
        // The runtime's listener REPLACES any previous one; setting null
        // clears it. No listener from a previous run can contaminate a new
        // one: the runtime sets it before every turn and clears it in the
        // turn's finally.
        this.turnDeltaListener = listener;
    }

    /** Provider usage of the most recent completed turn (null before the first). */
    public TokenUsage lastTurnUsage() {
        return lastUsage;
    }

    /**
     * Canonical catalog turn (migration): the ToolCatalog is the single
     * model-visible tool set. The catalog is NEVER flattened by the caller —
     * capability resolution happens HERE, at the wire, against the frozen
     * provider/model of the turn
     * ({@link ProviderToolCapabilitiesResolver#resolve}). The faithful
     * per-kind serialization is handed to the transport; providers that
     * cannot carry native freeform/namespace/tool_search shapes receive the
     * downgrade the declared capability allows (explicit, recorded in
     * {@link #lastToolPayload()}, never a silent schema rewrite).
     */
    @Override
    public LlmTurnOutput completeTurn(String systemPrompt,
                                      ToolCatalog catalog,
                                      List<ChatMessage> messages,
                                      AiOperationContext operationContext) throws Exception {
        String providerId = operationContext == null ? "" : operationContext.getProviderId();
        String modelName = operationContext == null ? "" : operationContext.getModelName();
        ProviderCatalogPayload payload = ToolSpecSerializer.toProviderPayload(
                catalog, ProviderToolCapabilitiesResolver.resolve(providerId, modelName));
        lastToolPayload = payload;
        return completeTurn(systemPrompt, payload.payload(), messages, operationContext);
    }

    /** Last capability-aware catalog serialization (null before the first turn). */
    public ProviderCatalogPayload lastToolPayload() {
        return lastToolPayload;
    }

    /** Convenience read of the last serialization's wire payload. */
    public JSONArray lastToolSerialization() {
        ProviderCatalogPayload last = lastToolPayload;
        return last == null ? new JSONArray() : last.payload();
    }

    @Override
    public LlmTurnOutput completeTurn(String systemPrompt,
                                      JSONArray tools,
                                      List<ChatMessage> messages,
                                      AiOperationContext operationContext) throws Exception {
        cancelRequested = false;
        lastUsage = null;

        ContextBuilder builder = new ContextBuilder(null, messages)
                .setAgentGuidance("")
                .setFinalResponseOnly(false)
                .setIncludeNativeReferences(true);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            builder.setExternalSystemPrompt(systemPrompt);
        }
        // Item 11: the run's frozen identity wins over global preferences.
        String chatMode = fallbackChatMode;
        String providerId = "";
        String modelName = "";
        if (operationContext != null) {
            if (operationContext.getChatMode() != null && !operationContext.getChatMode().trim().isEmpty()) {
                chatMode = operationContext.getChatMode();
            }
            providerId = operationContext.getProviderId();
            modelName = operationContext.getModelName();
            builder.setFrozenModelName(modelName);
        } else {
            // Fallback BEFORE the run starts (single-turn convenience paths):
            // read the global selection once — never refreshed per turn.
            android.content.SharedPreferences prefs =
                    pro.sketchware.SketchApplication.getContext()
                            .getSharedPreferences(pro.sketchware.chat.port.VoidPortSettings.PREFS_NAME,
                                    android.content.Context.MODE_PRIVATE);
            providerId = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        }
        ContextBuilder.Result request = builder.build(latestUserText(messages), chatMode, providerId);

        final CountDownLatch done = new CountDownLatch(1);
        final StringBuilder content = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        final AtomicReference<String> finishReason = new AtomicReference<>("");
        final List<ToolCall> toolCalls = java.util.Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<String> error = new AtomicReference<>(null);

        /**
         * Declares the native-only tool protocol (item 7 of the tool-call
         * contract): tool calls arrive ONLY from the provider envelope —
         * assistant text is never mined and never re-emitted as tool calls.
         */
        AiProviderService.StreamListener listener = new NativeOnlyStreamListener() {
            @Override
            public void onContent(String delta) {
                if (delta == null || delta.isEmpty()) {
                    return;
                }
                content.append(delta);
                // Item 6: the RUNTIME's listener is the streaming route; the
                // constructor hook (if any) is a legacy secondary bridge.
                java.util.function.Consumer<String> runtime = turnDeltaListener;
                if (runtime != null) {
                    runtime.accept(delta);
                } else if (deltaListenerHook != null) {
                    deltaListenerHook.accept(delta);
                }
            }

            @Override
            public void onReasoning(String delta) {
                if (delta != null) {
                    reasoning.append(delta);
                }
            }

            @Override
            public void onToolCall(String name, String arguments, String id) {
                if (name != null && !name.isEmpty()) {
                    toolCalls.add(new ToolCall(name, arguments == null ? "{}" : arguments, id));
                }
            }

            @Override
            public void onTokenUsage(pro.sketchware.chat.agentsdk.TokenUsage usage) {
                if (usage != null && !usage.isEstimated()) {
                    lastUsage = usage;
                }
            }

            @Override
            public void onFinalMessage(String fullContent, String fullReasoning, String msgFinishReason) {
                if (fullContent != null && fullContent.length() > content.length()) {
                    // Providers that deliver one final message instead of deltas.
                    content.setLength(0);
                    content.append(fullContent);
                }
                finishReason.set(msgFinishReason == null ? "" : msgFinishReason);
                done.countDown();
            }

            @Override
            public void onDebug(String message) {
            }

            @Override
            public void onError(String message, Throwable t) {
                error.set(message == null ? "Unknown streaming error" : message);
                done.countDown();
            }
        };

        // Tool-call execution contract: this listener declares the
        // NATIVE_TOOL_CALLS_ONLY protocol and this call carries the run's
        // FROZEN operationContext — the provider/model cannot change mid-run.
        aiService.sendStreamingMessage(request, tools, chatMode, operationContext, listener);
        try {
            if (!done.await(10, TimeUnit.MINUTES)) {
                return timeoutTurn("Streaming turn timed out.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return timeoutTurn("Streaming turn interrupted.");
        }
        if (error.get() != null) {
            return timeoutTurn(error.get());
        }
        if (cancelRequested) {
            return timeoutTurn("Turn cancelled by user.");
        }

        // Provider-structured calls only (tool-call execution contract):
        // deduplicated by callId (never by name). A JSON/XML/DSML block in
        // the text is text, and stays text.
        List<ToolCall> structured = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            if (call != null && call.isValid() && !containsCallId(structured, call)) {
                structured.add(call);
            }
        }
        boolean streamed = content.length() > 0;
        return new LlmTurnOutput(content.toString(), reasoning.toString(),
                finishReason.get(), structured, streamed, lastUsage);
    }

    /**
     * Transport-failure path — NEVER synthesized assistant text. A timeout,
     * interrupt, cancellation or network failure produces a FAILED turn with
     * an empty payload and a stable diagnostic code. The content field stays
     * empty (no {@code "Error: ..."} mining); the runtime reacts to the
     * semantic {@link LlmTurnOutput.Outcome}.
     */
    private static LlmTurnOutput timeoutTurn(String message) {
        return LlmTurnOutput.failure(
                LlmTurnOutput.CODE_EMPTY_ASSISTANT_PAYLOAD, message == null ? "" : message, null);
    }

    @Override
    public void cancel() {
        cancelRequested = true;
    }

    private static String latestUserText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.getType() == ChatMessage.TYPE_USER) {
                return message.getLlmContent();
            }
        }
        return "";
    }

    /** Dedupe by callId (never by name) across provider retries/duplicates. */
    private static boolean containsCallId(List<ToolCall> calls, ToolCall candidate) {
        for (ToolCall call : calls) {
            if (call.getId().equals(candidate.getId())) {
                return true;
            }
        }
        return false;
    }
}
