package pro.sketchware.chat;


import pro.sketchware.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;
import okio.Buffer;
import pro.sketchware.SketchApplication;
import pro.sketchware.chat.AiSettingsRepository;
import pro.sketchware.chat.AiChatSettingsHelper;
import pro.sketchware.chat.SecurePrefs;
import pro.sketchware.chat.ContextBuilder;
import pro.sketchware.chat.port.VoidPortExtractGrammar;
import pro.sketchware.chat.port.VoidPortLlmMessage;
import pro.sketchware.chat.port.VoidPortSettings;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderFamily;
import pro.sketchware.chat.provider.AiProviderAdapter;
import pro.sketchware.chat.provider.AiProviderAdapterRegistry;
import pro.sketchware.chat.toolcalling.DefaultToolCallDetector;
import pro.sketchware.chat.toolcalling.ToolCall;
import pro.sketchware.chat.toolcalling.ToolCallDetector;
import pro.sketchware.chat.toolcalling.ToolCallParseResult;
import pro.sketchware.chat.toolcalling.ToolCallResponse;

/**
 * Provider-aware AI service with OpenAI-compatible and Anthropic-specific
 * streaming paths, retries and protocol-independent tool-call detection.
 */
public class AiProviderService {
    private static final String TAG = "AiProviderService";
    private static final Pattern GEMINI_REPLACEMENT_PATTERN = Pattern.compile(
            "(?i)(?:use|using)\\s+models/([a-z0-9._-]+)");
    /** Política única de retry usada por chamadas streaming e bloqueantes. */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private static AiProviderService instance;

    private static final class HttpStatusException extends IOException {
        HttpStatusException(String message) {
            super(message);
        }
    }

    private static final class EmptyProviderResponseException extends IOException {
        private static final long serialVersionUID = 1L;

        EmptyProviderResponseException(String message) {
            super(message);
        }
    }

    private static final class UnsupportedProviderEnvelopeException extends IOException {
        private static final long serialVersionUID = 1L;

        UnsupportedProviderEnvelopeException(String message) {
            super(message);
        }
    }

    private final Context context;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private final AiSettingsRepository settingsRepository;
    private final AiProviderAdapterRegistry providerAdapters;
    private final AiStreamingTransport streamingTransport;
    private final ToolCallDetector toolCallDetector;
    private final AiRetryController retryController;
    private final AiErrorClassifier errorClassifier;
    private static final ExecutorService REQUEST_PREPARATION_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ai-request-preparation");
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });

    public interface StreamListener {
        void onContent(String delta);
        void onReasoning(String delta);
        void onToolCall(String name, String arguments, String id);
        void onFinalMessage(String fullContent, String fullReasoning, String finishReason);
        void onDebug(String message);
        void onError(String message, Throwable t);

        /** Atualização estruturada e amigável do andamento da operação. */
        default void onOperationStatus(AiOperationStatus status) {
            // Compatibilidade com listeners antigos.
        }

        /** Erro final já traduzido para apresentação ao usuário. */
        default void onUserFacingError(UserFacingError error, String requestId) {
            String message = error == null
                    ? null
                    : error.getTitle() + ": " + error.getMessage();
            onError(message, null);
        }

        /**
         * Provider-reported token usage of the current request (item 17 of
         * the migration). Only REAL reports are delivered here — an absent
         * report simply never calls this method, and the caller marks its
         * own fallback as estimated. Emitted at most once per request.
         */
        default void onTokenUsage(pro.sketchware.chat.agentsdk.TokenUsage usage) {
        }
    }

    /**
     * Request-scoped protocol declaration (tool-call execution contract,
     * item 7 — Codex {@code ResponseItem::FunctionCall} vs
     * {@code ResponseItem::Message}): a listener implementing this marker
     * receives tool calls ONLY from the provider's structured envelope.
     * Assistant text is never mined for XML/JSON/DSML tool protocols and
     * never re-emitted as {@code onToolCall}. Set exclusively by the
     * AgentRuntime v2 gateway; the legacy chat never sets it.
     */
    public interface NativeToolCallsOnly {
    }

    private static final class StreamPerf {
        private final long startedAt = SystemClock.elapsedRealtime();
        private long firstChunkAt;
        private int chunkCount;

        void onChunk(StreamListener listener) {
            chunkCount++;
            if (firstChunkAt != 0) {
                return;
            }
            firstChunkAt = SystemClock.elapsedRealtime();
            emitDebug(listener, "Stream TTFT=" + (firstChunkAt - startedAt) + "ms");
        }

        void onProgress(StreamListener listener, int chunkIndex) {
            if (chunkIndex == 4 || chunkIndex == 25 || chunkIndex == 100 || chunkIndex == 250
                    || chunkIndex == 500 || (chunkIndex > 500 && chunkIndex % 250 == 0)) {
                long now = SystemClock.elapsedRealtime();
                long sinceFirst = firstChunkAt > 0 ? now - firstChunkAt : now - startedAt;
                emitDebug(listener, "Stream progress: chunk #" + chunkIndex
                        + ", +" + (now - startedAt) + "ms, streamBody=" + sinceFirst + "ms");
            }
        }

        void finish(StreamListener listener, int contentChars, int reasoningChars) {
            long end = SystemClock.elapsedRealtime();
            long totalMs = end - startedAt;
            long bodyMs = firstChunkAt > 0 ? end - firstChunkAt : totalMs;
            long avgChunkMs = chunkCount > 1 ? bodyMs / (chunkCount - 1) : 0;
            emitDebug(listener, "Stream timing: total=" + totalMs + "ms"
                    + ", bodyAfterFirstChunk=" + bodyMs + "ms"
                    + ", chunks=" + chunkCount
                    + ", avgInterval=" + avgChunkMs + "ms/chunk"
                    + ", contentChars=" + contentChars
                    + ", reasoningChars=" + reasoningChars);
        }
    }

    private static final class ToolCallAccumulator {
        private final int index;
        private final String fallbackId;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
        private final StringBuilder id = new StringBuilder();

        ToolCallAccumulator(int index) {
            this.index = index;
            this.fallbackId = "tool_" + index + "_" + UUID.randomUUID();
        }

        void appendId(String value) {
            StreamingValueMerger.merge(id, value);
        }

        void appendName(String value) {
            StreamingValueMerger.merge(name, value);
        }

        void appendArgumentsDelta(String value) {
            if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value.trim())) {
                return;
            }
            arguments.append(value);
        }

        void mergeArgumentsSnapshot(String value) {
            StreamingValueMerger.merge(arguments, value);
        }

        String getExplicitId() {
            return id.toString().trim();
        }

        String getId() {
            String current = id.toString().trim();
            return current.isEmpty() ? fallbackId : current;
        }

        String getName() {
            return name.toString().trim();
        }

        String getArguments() {
            String raw = arguments.toString().trim();
            if (raw.isEmpty()) {
                return "{}";
            }
            try {
                return new JSONObject(raw).toString();
            } catch (Exception ignored) {
                return raw;
            }
        }

        boolean hasAnyPayload() {
            return name.length() > 0 || arguments.length() > 0 || id.length() > 0;
        }

        boolean isReady() {
            return !getName().isEmpty();
        }
    }

    /**
     * Wraps a {@link StreamListener} and records whether any content, reasoning
     * or tool call was already delivered to the UI. Retries are only safe while
     * nothing has been emitted — retrying after partial deltas would replay the
     * whole stream and duplicate text in the chat.
     */
    private static final class EmissionTracker implements StreamListener {
        private final StreamListener delegate;
        final java.util.concurrent.atomic.AtomicBoolean emitted =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        /** True when the delegate declared the native-only tool protocol. */
        final boolean nativeToolCallsOnly;

        EmissionTracker(StreamListener delegate) {
            this.delegate = delegate;
            this.nativeToolCallsOnly = delegate instanceof NativeToolCallsOnly
                    || (delegate instanceof EmissionTracker
                    && ((EmissionTracker) delegate).nativeToolCallsOnly);
        }

        @Override
        public void onContent(String delta) {
            emitted.set(true);
            delegate.onContent(delta);
        }

        @Override
        public void onReasoning(String delta) {
            emitted.set(true);
            delegate.onReasoning(delta);
        }

        @Override
        public void onToolCall(String name, String arguments, String id) {
            emitted.set(true);
            delegate.onToolCall(name, arguments, id);
        }

        @Override
        public void onFinalMessage(String fullContent, String fullReasoning, String finishReason) {
            emitted.set(true);
            delegate.onFinalMessage(fullContent, fullReasoning, finishReason);
        }

        @Override
        public void onTokenUsage(pro.sketchware.chat.agentsdk.TokenUsage usage) {
            delegate.onTokenUsage(usage);
        }

        @Override
        public void onDebug(String message) {
            delegate.onDebug(message);
        }

        @Override
        public void onError(String message, Throwable t) {
            delegate.onError(message, t);
        }

        @Override
        public void onOperationStatus(AiOperationStatus status) {
            delegate.onOperationStatus(status);
        }

        @Override
        public void onUserFacingError(UserFacingError error, String requestId) {
            delegate.onUserFacingError(error, requestId);
        }
    }

    private static final class AnthropicStreamState {
        final StringBuilder fullContent = new StringBuilder();
        final StringBuilder fullReasoning = new StringBuilder();
        /** One accumulator per tool_use content block, keyed by block index. */
        final Map<Integer, ToolCallAccumulator> toolBlocks = new LinkedHashMap<>();
        String stopReason = "";
    }

    private static final class OpenAiStreamState {
        final StringBuilder fullContent = new StringBuilder();
        final StringBuilder fullReasoning = new StringBuilder();
        final Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<>();
        String finishReason = "";
        String blockReason = "";
        String protocolError = "";
        String envelope = "";
        boolean recognizedPayload;
    }

    /** Keeps the complete raw stream visible in chat and every line in the private TXT trace. */
    private static final class ProtocolStreamTrace {
        private final String protocol;
        private final StringBuilder raw = new StringBuilder();
        private int lineCount;

        ProtocolStreamTrace(String protocol) {
            this.protocol = protocol;
        }

        void record(String line) {
            if (line == null) {
                return;
            }
            String safe = ChatFlowLogger.redact(line);
            lineCount++;
            raw.append(safe).append('\n');
            // One TXT entry per wire line avoids the logger's per-entry truncation
            // while still redacting credentials before data touches disk.
            ChatFlowLogger.event("protocol", "RECEBIDO " + protocol + " linha " + lineCount, safe);
        }

        void publish(StreamListener listener) {
            emitDebug(listener, "RECEBIDO " + protocol + " STREAM ("
                    + raw.length() + " chars, " + lineCount + " linhas)\n" + raw);
        }
    }

    private AiProviderService() {
        this.context = SketchApplication.getContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                // Watchdog: OkHttp's readTimeout applies to each read, i.e. the
                // maximum silence BETWEEN stream chunks. 0 (infinite) used to leave
                // the UI stuck on "Thinking" forever when a server opened the SSE
                // connection and never sent data. 180 s tolerates long reasoning
                // pauses while still recovering from dead connections.
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.settingsRepository = new AiSettingsRepository(context);
        this.providerAdapters = new AiProviderAdapterRegistry();
        this.toolCallDetector = new DefaultToolCallDetector();
        this.retryController = new AiRetryController();
        this.errorClassifier = new AiErrorClassifier(context);
        this.streamingTransport = new AiStreamingTransport(
                mainHandler,
                this::clientForProvider,
                this::buildHttpErrorMessage
        );
    }

    public static synchronized AiProviderService getInstance() {
        if (instance == null) {
            instance = new AiProviderService();
        }
        return instance;
    }

    public AiRequestHandle sendStreamingMessage(ContextBuilder.Result requestContext, JSONArray tools,
                                                      String chatMode, StreamListener listener) {
        return sendStreamingMessage(requestContext, tools, chatMode, null, listener);
    }

    /**
     * Envia uma operação usando o modelo e provedor imutáveis capturados no início do turno.
     * Alterações posteriores nas preferências só valem para uma nova operação.
     */
    public AiRequestHandle sendStreamingMessage(ContextBuilder.Result requestContext, JSONArray tools,
                                                      String chatMode,
                                                      @Nullable AiOperationContext operationContext,
                                                      StreamListener listener) {
        AiRequestHandle requestHandle = new AiRequestHandle();
        final AiSettingsRepository.Selection selection = operationContext == null
                ? settingsRepository.currentSelection()
                : settingsRepository.selection(operationContext.getProviderId(), operationContext.getModelName());
        REQUEST_PREPARATION_EXECUTOR.execute(() -> prepareAndDispatchStreamingRequest(
                selection, requestContext, tools, chatMode, operationContext, listener, requestHandle));
        return requestHandle;
    }

    private void prepareAndDispatchStreamingRequest(AiSettingsRepository.Selection selection,
                                                    ContextBuilder.Result requestContext,
                                                    JSONArray tools,
                                                    String chatMode,
                                                    @Nullable AiOperationContext suppliedOperationContext,
                                                    StreamListener listener,
                                                    AiRequestHandle requestHandle) {
        if (requestHandle.isCancelled()) {
            return;
        }
        try {
            String currentProvider = selection.providerId;
            String currentModel = selection.modelName;
            ProviderConfig providerConfig = selection.providerConfig;
            AiOperationContext operationContext = suppliedOperationContext != null
                    ? suppliedOperationContext
                    : AiOperationContext.builder()
                            .providerId(currentProvider)
                            .modelName(currentModel)
                            .chatMode(chatMode)
                            .build();
            emitOperationStatus(listener, operationContext, AiOperationState.PREPARING,
                    context.getString(R.string.ai_status_preparing_request), 1, 0L, true, false, null);
            String planError = planAccessError(
                    selection,
                    containsImagePayload(requestContext == null ? null : requestContext.getMessages())
            );
            if (!planError.isEmpty()) {
                listener.onUserFacingError(UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_access_unavailable_title))
                        .message(planError)
                        .canRetry(false)
                        .technicalCode("plan_restriction")
                        .build(), operationContext.getRequestId());
                return;
            }
            if (providerConfig == null) {
                listener.onUserFacingError(UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_provider_not_found_title))
                        .message(context.getString(R.string.ai_error_provider_not_found_message))
                        .canRetry(false)
                        .technicalCode("unsupported_provider")
                        .build(), operationContext.getRequestId());
                return;
            }
            if (!settingsRepository.isConfigured(selection)) {
                listener.onUserFacingError(UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_configuration_required_title))
                        .message(context.getString(R.string.ai_error_provider_not_configured_message))
                        .canRetry(false)
                        .technicalCode("provider_not_configured")
                        .build(), operationContext.getRequestId());
                return;
            }
            if (providerConfig.baseUrl.isEmpty()) {
                listener.onUserFacingError(UserFacingError.builder()
                        .title(context.getString(R.string.ai_error_endpoint_missing_title))
                        .message(context.getString(R.string.ai_error_endpoint_missing_message))
                        .canRetry(false)
                        .technicalCode("missing_endpoint")
                        .build(), operationContext.getRequestId());
                return;
            }
            if (requestHandle.isCancelled()) {
                return;
            }
            dispatchRequest(providerConfig, currentProvider, currentModel, requestContext,
                    filterToolsForPlan(tools), chatMode, operationContext, listener, 0, requestHandle);
        } catch (Exception exception) {
            if (!requestHandle.isCancelled()) {
                listener.onError("Request preparation error", exception);
            }
        }
    }

    public String sendTextMessage(String systemPrompt, String userPrompt) throws IOException {
        return sendTextMessage(
                settingsRepository.currentSelection(),
                systemPrompt,
                userPrompt,
                java.util.Collections.emptyList(),
                0L);
    }

    /**
     * Blocking text request with a transport-level deadline. Used by bounded
     * multi-agent roles so cancelling a workflow cannot leave OkHttp calls
     * occupying the specialist pool for the normal streaming read timeout.
     */
    public String sendTextMessage(String systemPrompt, String userPrompt,
                                  long callTimeoutMs) throws IOException {
        return sendTextMessage(
                settingsRepository.currentSelection(),
                systemPrompt,
                userPrompt,
                java.util.Collections.emptyList(),
                callTimeoutMs);
    }

    /** Uses a model from IaSettings without changing the app-wide current model. */
    public String sendTextMessage(String providerId, String modelName,
                                  String systemPrompt, String userPrompt) throws IOException {
        return sendTextMessage(providerId, modelName, systemPrompt, userPrompt, 0L);
    }

    /** Uses the frozen operation model with a bounded transport deadline. */
    public String sendTextMessage(String providerId, String modelName,
                                  String systemPrompt, String userPrompt,
                                  long callTimeoutMs) throws IOException {
        return sendTextMessage(
                settingsRepository.selection(providerId, modelName),
                systemPrompt,
                userPrompt,
                java.util.Collections.emptyList(),
                callTimeoutMs
        );
    }

    public String sendTextMessage(String providerId, String modelName,
                                  String systemPrompt, String userPrompt,
                                  java.util.List<String> imageDataUrls) throws IOException {
        return sendTextMessage(
                settingsRepository.selection(providerId, modelName),
                systemPrompt,
                userPrompt,
                imageDataUrls == null ? java.util.Collections.emptyList() : imageDataUrls,
                0L
        );
    }

    private String sendTextMessage(AiSettingsRepository.Selection selection,
                                   String systemPrompt, String userPrompt,
                                   java.util.List<String> imageDataUrls,
                                   long callTimeoutMs) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Blocking network call + Thread.sleep retries would ANR the app.
            throw new IllegalStateException("sendTextMessage must not be called on the main thread");
        }
        String currentProvider = selection.providerId;
        String currentModel = selection.modelName;
        ProviderConfig providerConfig = selection.providerConfig;
        String planError = planAccessError(selection,
                imageDataUrls != null && !imageDataUrls.isEmpty());
        if (!planError.isEmpty()) {
            throw new IOException(planError);
        }
        if (providerConfig == null) {
            throw new IOException("Unsupported provider: " + currentProvider);
        }
        if (!settingsRepository.isConfigured(selection)) {
            throw new IOException("Provider not enabled or API key missing: " + currentProvider);
        }
        if (providerConfig.baseUrl.isEmpty()) {
            throw new IOException("Provider endpoint is missing: " + currentProvider);
        }

        Request request = providerConfig.family == ProviderFamily.ANTHROPIC
                ? buildAnthropicTextRequest(providerConfig, currentModel, systemPrompt, userPrompt, imageDataUrls)
                : providerConfig.family == ProviderFamily.GEMINI
                ? buildGeminiTextRequest(providerConfig, currentModel, systemPrompt, userPrompt, imageDataUrls)
                : buildOpenAiCompatibleTextRequest(providerConfig, currentProvider, currentModel, systemPrompt, userPrompt, imageDataUrls);

        IOException lastException = null;
        boolean geminiModelFallbackUsed = false;
        for (int attemptNumber = 1; attemptNumber <= AiRetryController.MAX_ATTEMPTS; attemptNumber++) {
            OkHttpClient requestClient = clientForProvider(currentProvider);
            if (callTimeoutMs > 0L) {
                requestClient = requestClient.newBuilder()
                        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                        .build();
            }
            try (Response response = requestClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    if (providerConfig.family == ProviderFamily.GEMINI && !geminiModelFallbackUsed) {
                        String replacement = suggestedGeminiReplacement(
                                response.code(), responseBody, currentModel);
                        if (!replacement.isEmpty()) {
                            geminiModelFallbackUsed = true;
                            persistGeminiModelReplacement(currentProvider, currentModel, replacement);
                            currentModel = replacement;
                            request = buildGeminiTextRequest(providerConfig, currentModel,
                                    systemPrompt, userPrompt, imageDataUrls);
                            continue;
                        }
                    }
                    long retryAfterSeconds = AiRetryController.parseRetryAfter(response.header("Retry-After"));
                    AiRetryController.RetryDecision decision = retryController.shouldRetry(
                            attemptNumber, response.code(), responseBody,
                            retryAfterSeconds, false);
                    if (decision.shouldRetry()) {
                        sleepBeforeBlockingRetry(decision.getDelayMillis());
                        continue;
                    }
                    throw new HttpStatusException(
                            buildHttpErrorMessage(currentProvider, response.code(), responseBody));
                }

                String content = providerConfig.family == ProviderFamily.ANTHROPIC
                        ? parseAnthropicTextResponse(responseBody)
                        : providerConfig.family == ProviderFamily.GEMINI
                        ? parseGeminiTextResponse(responseBody)
                        : parseOpenAiCompatibleTextResponse(responseBody);
                if (content.trim().isEmpty()) {
                    throw new IOException("AI response content is empty");
                }
                return content;
            } catch (HttpStatusException error) {
                throw error;
            } catch (IOException error) {
                lastException = error;
                AiRetryController.RetryDecision decision = retryController.shouldRetry(
                        attemptNumber, -1, null, -1L, true);
                if (decision.shouldRetry()) {
                    sleepBeforeBlockingRetry(decision.getDelayMillis());
                    continue;
                }
                throw error;
            } catch (Exception error) {
                throw new IOException("Error processing AI response", error);
            }
        }

        throw lastException != null ? lastException : new IOException("Unknown AI request error");
    }

    private String planAccessError(
            AiSettingsRepository.Selection selection,
            boolean hasImages
    ) {
        return "";
    }

    private JSONArray filterToolsForPlan(JSONArray tools) {
        return tools;
    }

    private boolean containsImagePayload(Object value) {
        if (value instanceof JSONObject object) {
            JSONArray names = object.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String key = names.optString(i, "");
                if ("image_url".equals(key) || "inlineData".equals(key)
                        || "inline_data".equals(key)) {
                    return true;
                }
                if (containsImagePayload(object.opt(key))) {
                    return true;
                }
            }
        } else if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                if (containsImagePayload(array.opt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void dispatchRequest(ProviderConfig providerConfig, String providerId, String modelName,
                                 ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                 AiOperationContext operationContext,
                                 StreamListener rawListener, int retryCount, AiRequestHandle requestHandle) {
        // Wrap once so mid-stream retries can check whether deltas already reached the UI.
        StreamListener listener = rawListener instanceof EmissionTracker
                ? rawListener
                : new EmissionTracker(rawListener);
        String streamingKey = StreamingCapabilityRegistry.key(
                providerId, providerConfig.baseUrl, modelName);
        boolean useStreaming = StreamingCapabilityRegistry.shouldUseStreamingForSelection(streamingKey);
        if (!useStreaming) {
            emitDebug(listener, "Streaming desativado automaticamente para provider="
                    + providerId + ", model=" + modelName + "; usando JSON.");
        }
        if (providerConfig.family == ProviderFamily.ANTHROPIC) {
            sendAnthropicStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode,
                    listener, providerId, operationContext, retryCount, requestHandle, useStreaming);
        } else if (providerConfig.family == ProviderFamily.GEMINI) {
            sendGeminiStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode,
                    listener, providerId, operationContext, retryCount, requestHandle, useStreaming);
        } else {
            sendOpenAiCompatibleStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode,
                    listener, providerId, operationContext, retryCount, requestHandle, useStreaming, null);
        }
    }

    private void sendGeminiStreamingRequest(ProviderConfig providerConfig, String modelName,
                                            ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                            StreamListener listener, String providerId,
                                            AiOperationContext operationContext,
                                            int retryCount, AiRequestHandle requestHandle,
                                            boolean useStreaming) {
        sendGeminiStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode,
                listener, providerId, operationContext, retryCount, requestHandle,
                useStreaming, true);
    }

    private void sendGeminiStreamingRequest(ProviderConfig providerConfig, String modelName,
                                            ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                            StreamListener listener, String providerId,
                                            AiOperationContext operationContext,
                                            int retryCount, AiRequestHandle requestHandle,
                                            boolean useStreaming, boolean allowModelFallback) {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("contents", requestContext.getMessages());
            if (!TextUtils.isEmpty(requestContext.getSystemContext())) {
                jsonBody.put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("text", requestContext.getSystemContext()))));
            }
            jsonBody.put("generationConfig", new JSONObject()
                    .put("maxOutputTokens", VoidPortLlmMessage.maxOutputTokens(providerId, modelName)));

            boolean useNativeTools = requestContext.getProviderFormat() == ContextBuilder.ProviderFormat.GEMINI
                    && VoidPortLlmMessage.shouldUseNativeTools(providerId, modelName, providerConfig)
                    && tools != null
                    && tools.length() > 0
                    && !"normal".equals(chatMode);
            JSONArray geminiTools = new JSONArray();
            if (useNativeTools) {
                JSONArray convertedTools = convertToolsToGemini(tools);
                for (int i = 0; i < convertedTools.length(); i++) {
                    JSONObject converted = convertedTools.optJSONObject(i);
                    if (converted != null) {
                        geminiTools.put(converted);
                    }
                }
            }
            if (operationContext != null && operationContext.isWebSearchEnabled()) {
                // GenerateContent built-in Google Search grounding. Gemini 3 models
                // can combine it with the custom function declarations above.
                geminiTools.put(new JSONObject().put("google_search", new JSONObject()));
            }
            if (geminiTools.length() > 0) {
                jsonBody.put("tools", geminiTools);
                logProviderTools(listener, geminiTools);
            }

            // API key is sent via the x-goog-api-key header (see buildGeminiHeaders)
            // instead of a query parameter, so it never leaks into logs.
            AiProviderAdapter adapter = providerAdapters.get(providerConfig.family);
            String url = useStreaming
                    ? adapter.streamingUrl(providerConfig, modelName)
                    : adapter.nonStreamingUrl(providerConfig, modelName);

            emitDebug(listener, "LLM request -> provider=" + providerId
                    + ", model=" + modelName
                    + ", endpoint=" + sanitizeUrlForDebug(url.toString()));

            Request request = new Request.Builder()
                    .url(url)
                    .headers(adapter.headers(providerConfig))
                    .header("Accept", useStreaming
                            ? "text/event-stream, application/json"
                            : "application/json")
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();

            final String streamingKey = StreamingCapabilityRegistry.key(
                    providerId, providerConfig.baseUrl, modelName);
            StreamingFallbackHandler fallbackHandler = (statusCode, errorBody) -> {
                        if (allowModelFallback) {
                            String replacement = suggestedGeminiReplacement(
                                    statusCode, errorBody, modelName);
                            if (!replacement.isEmpty()) {
                                persistGeminiModelReplacement(providerId, modelName, replacement);
                                android.util.Log.i(TAG, "Gemini model retired; retrying with " + replacement);
                                sendGeminiStreamingRequest(providerConfig, replacement,
                                        requestContext, tools, chatMode, listener, providerId,
                                        operationContext, retryCount, requestHandle,
                                        useStreaming, false);
                                return true;
                            }
                        }
                        if (!useStreaming) return false;
                        if (!StreamingCapabilityRegistry.shouldAttemptFallback(
                                providerConfig.family, statusCode, errorBody)) {
                            return false;
                        }
                        if (StreamingCapabilityRegistry.isExplicitStreamingDisabledError(
                                statusCode, errorBody)) {
                            StreamingCapabilityRegistry.markUnsupported(streamingKey);
                        }
                        android.util.Log.i("AiProviderService",
                                "Streaming unsupported for current model; retrying Gemini silently without stream.");
                        sendGeminiStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode,
                                listener, providerId, operationContext, retryCount, requestHandle,
                                false, allowModelFallback);
                        return true;
                    };

            executeStreaming(request, retryCount, providerId, operationContext, listener, (call, response) -> {
                if (shouldParseAsJson(response, useStreaming)) {
                    String body = response.body() != null ? response.body().string() : "";
                    emitDebug(listener, "Gemini response mode: JSON fallback");
                    emitProtocolPayload(listener, "RECEBIDO GEMINI", body);
                    handleGeminiJsonResponse(body, requestContext, tools, listener);
                    StreamingCapabilityRegistry.markUnsupported(streamingKey);
                } else {
                    requireResponseSource(response, "Gemini");
                    emitDebug(listener, "Gemini response mode: SSE");
                    readGeminiEventStream(response.body().source(), requestContext, tools, listener);
                    StreamingCapabilityRegistry.markSupported(streamingKey);
                }
            }, fallbackHandler, requestHandle);
        } catch (Exception e) {
            listener.onError("Request preparation error", e);
        }
    }

    private void sendOpenAiCompatibleStreamingRequest(ProviderConfig providerConfig, String modelName,
                                                      ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                                      StreamListener listener, String providerId,
                                                   AiOperationContext operationContext,
                                                   int retryCount, AiRequestHandle requestHandle,
                                                   boolean useStreaming, String managedOperationId) {
        try {
            JSONArray messages = new JSONArray();
            if (!TextUtils.isEmpty(requestContext.getSystemContext())) {
                messages.put(new JSONObject()
                        .put("role", VoidPortLlmMessage.instructionRole(providerId, modelName))
                        .put("content", requestContext.getSystemContext()));
            }
            JSONArray history = requestContext.getMessages();
            for (int i = 0; i < history.length(); i++) {
                messages.put(history.get(i));
            }

            JSONObject jsonBody = new JSONObject();
            VoidPortLlmMessage.putModelIfNeeded(jsonBody, providerConfig, modelName);
            jsonBody.put("messages", messages);
            jsonBody.put("stream", useStreaming);
            if ("ollama".equals(providerId)) {
                // Ollama enables thinking by default for supported models. Off by
                // default here so the UI gets only the final answer; the user can
                // opt in via the "ollama_think_enabled" preference.
                jsonBody.put("think", ollamaThinkEnabled());
            }

            boolean useNativeTools = requestContext.getProviderFormat() == ContextBuilder.ProviderFormat.OPENAI
                    && VoidPortLlmMessage.shouldUseNativeTools(providerId, modelName, providerConfig)
                    && tools != null
                    && tools.length() > 0
                    && !"normal".equals(chatMode);
            if (useNativeTools) {
                JSONArray requestTools = OpenAiToolSchemaNormalizer.forRequest(
                        tools, "openai".equalsIgnoreCase(providerId));
                jsonBody.put("tools", requestTools);
                logProviderTools(listener, requestTools);
                if (!"ollama".equals(providerId)) {
                    jsonBody.put("tool_choice", "auto");
                }
            }

            AiProviderAdapter adapter = providerAdapters.get(providerConfig.family);
            String requestUrl = useStreaming
                    ? adapter.streamingUrl(providerConfig, modelName)
                    : adapter.nonStreamingUrl(providerConfig, modelName);
            emitDebug(listener, "LLM request -> provider=" + providerId
                    + ", model=" + modelName
                    + ", endpoint=" + sanitizeUrlForDebug(requestUrl));

            // Log do payload completo
            android.util.Log.d("AiProviderService", "=== REQUEST PAYLOAD ===");
            android.util.Log.d("AiProviderService", "Messages count: " + messages.length());
            android.util.Log.d("AiProviderService", "System context length: " + 
                (TextUtils.isEmpty(requestContext.getSystemContext()) ? 0 : requestContext.getSystemContext().length()));
            android.util.Log.d("AiProviderService", "Tools: " + (useNativeTools ? tools.length() : 0));
            android.util.Log.d("AiProviderService", "Stream: " + useStreaming);
            
            // Log primeiras e últimas mensagens para debug
            if (messages.length() > 0) {
                try {
                    JSONObject firstMsg = messages.getJSONObject(0);
                    android.util.Log.d("AiProviderService", "First message role: " + firstMsg.optString("role"));
                    String firstContent = firstMsg.optString("content", "");
                    android.util.Log.d("AiProviderService", "First message preview: "
                            + SecureLogger.safePreview(firstContent));
                    
                    if (messages.length() > 1) {
                        JSONObject lastMsg = messages.getJSONObject(messages.length() - 1);
                        android.util.Log.d("AiProviderService", "Last message role: " + lastMsg.optString("role"));
                        String lastContent = lastMsg.optString("content", "");
                        android.util.Log.d("AiProviderService", "Last message preview: "
                                + SecureLogger.safePreview(lastContent));
                    }
                } catch (Exception e) {
                    android.util.Log.w("AiProviderService", "Could not log message preview", e);
                }
            }

            Request.Builder requestBuilder = new Request.Builder()
                    .url(requestUrl)
                    .headers(adapter.headers(providerConfig))
                    .header("Accept", useStreaming
                            ? "text/event-stream, application/x-ndjson, application/json"
                            : "application/json")
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE));
            String resolvedManagedOperationId = managedOperationId;
            final String stableManagedOperationId = resolvedManagedOperationId;
            Request request = requestBuilder.build();
            final String streamingKey = StreamingCapabilityRegistry.key(
                    providerId, providerConfig.baseUrl, modelName);
            StreamingFallbackHandler fallbackHandler = useStreaming
                    ? (statusCode, errorBody) -> {
                        if (!StreamingCapabilityRegistry.shouldAttemptFallback(
                                providerConfig.family, statusCode, errorBody)) {
                            return false;
                        }
                        if (StreamingCapabilityRegistry.isExplicitStreamingDisabledError(
                                statusCode, errorBody)) {
                            StreamingCapabilityRegistry.markUnsupported(streamingKey);
                        }
                        android.util.Log.i("AiProviderService",
                                "Streaming unsupported for current model; retrying silently with stream=false.");
                        sendOpenAiCompatibleStreamingRequest(providerConfig, modelName, requestContext,
                                tools, chatMode, listener, providerId, operationContext, retryCount,
                                requestHandle, false, stableManagedOperationId);
                        return true;
                    }
                    : null;

            executeStreaming(request, retryCount, providerId, operationContext, listener, (call, response) -> {
                String contentType = response.header("Content-Type", "");
                emitDebug(listener, "LLM response <- contentType=" + (contentType.isEmpty() ? "unknown" : contentType));

                android.util.Log.d("AiProviderService", "=== RESPONSE HEADERS ===");
                android.util.Log.d("AiProviderService", "Content-Type: " + contentType);
                android.util.Log.d("AiProviderService", "X-Request-Id: " + response.header("X-Request-Id", "none"));

                if (shouldParseAsJson(response, useStreaming)) {
                    emitDebug(listener, "Response mode: JSON fallback");
                    String body = response.body() != null ? response.body().string() : "";
                    android.util.Log.d("AiProviderService", "Response body length: " + body.length() + " chars");
                    if (!body.isEmpty()) {
                        android.util.Log.d("AiProviderService", "Response body preview: "
                                + SecureLogger.safePreview(body));
                    }
                    emitProtocolPayload(listener, "RECEBIDO OPENAI", body);
                    handleOpenAiJsonResponse(body, requestContext, tools, listener);
                    StreamingCapabilityRegistry.markUnsupported(streamingKey);
                } else {
                    requireResponseSource(response, "OpenAI-compatible");
                    emitDebug(listener, "Response mode: SSE/NDJSON");
                    readOpenAiEventStream(response.body().source(), requestContext, tools, listener);
                    StreamingCapabilityRegistry.markSupported(streamingKey);
                }
            }, fallbackHandler, requestHandle);
        } catch (Exception e) {
            listener.onError("Request preparation error", e);
        }
    }

    private void sendAnthropicStreamingRequest(ProviderConfig providerConfig, String modelName,
                                               ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                               StreamListener listener, String providerId,
                                               AiOperationContext operationContext,
                                               int retryCount, AiRequestHandle requestHandle,
                                               boolean useStreaming) {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelName);
            jsonBody.put("messages", requestContext.getMessages());
            jsonBody.put("stream", useStreaming);
            jsonBody.put("max_tokens", VoidPortLlmMessage.maxOutputTokens(providerId, modelName));
            if (!TextUtils.isEmpty(requestContext.getSystemContext())) {
                // Prompt caching: the system prompt is identical on every turn of the
                // agent loop; marking it ephemeral lets Anthropic cache it, cutting
                // cost and latency dramatically on multi-step runs.
                jsonBody.put("system", new JSONArray().put(new JSONObject()
                        .put("type", "text")
                        .put("text", requestContext.getSystemContext())
                        .put("cache_control", new JSONObject().put("type", "ephemeral"))));
            }

            boolean useNativeTools = requestContext.getProviderFormat() == ContextBuilder.ProviderFormat.ANTHROPIC
                    && tools != null
                    && tools.length() > 0
                    && !"normal".equals(chatMode);
            if (useNativeTools) {
                JSONArray anthropicTools = convertToolsToAnthropic(tools);
                // Cache the (static) tool definitions too: cache_control on the last
                // tool covers the whole tools array as a cache prefix.
                JSONObject lastTool = anthropicTools.optJSONObject(anthropicTools.length() - 1);
                if (lastTool != null) {
                    lastTool.put("cache_control", new JSONObject().put("type", "ephemeral"));
                }
                jsonBody.put("tools", anthropicTools);
                jsonBody.put("tool_choice", new JSONObject().put("type", "auto"));
                logProviderTools(listener, anthropicTools);
            }

            AiProviderAdapter adapter = providerAdapters.get(providerConfig.family);
            Request request = new Request.Builder()
                    .url(useStreaming
                            ? adapter.streamingUrl(providerConfig, modelName)
                            : adapter.nonStreamingUrl(providerConfig, modelName))
                    .headers(adapter.headers(providerConfig))
                    .header("Accept", useStreaming
                            ? "text/event-stream, application/json"
                            : "application/json")
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();

            final String streamingKey = StreamingCapabilityRegistry.key(
                    providerId, providerConfig.baseUrl, modelName);
            StreamingFallbackHandler fallbackHandler = useStreaming
                    ? (statusCode, errorBody) -> {
                        if (!StreamingCapabilityRegistry.shouldAttemptFallback(
                                providerConfig.family, statusCode, errorBody)) {
                            return false;
                        }
                        if (StreamingCapabilityRegistry.isExplicitStreamingDisabledError(
                                statusCode, errorBody)) {
                            StreamingCapabilityRegistry.markUnsupported(streamingKey);
                        }
                        android.util.Log.i("AiProviderService",
                                "Streaming unsupported for current model; retrying Anthropic silently without stream.");
                        sendAnthropicStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode,
                                listener, providerId, operationContext, retryCount, requestHandle, false);
                        return true;
                    }
                    : null;

            executeStreaming(request, retryCount, providerId, operationContext, listener, (call, response) -> {
                if (shouldParseAsJson(response, useStreaming)) {
                    String body = response.body() != null ? response.body().string() : "";
                    emitDebug(listener, "Anthropic response mode: JSON fallback");
                    emitProtocolPayload(listener, "RECEBIDO ANTHROPIC", body);
                    handleAnthropicJsonResponse(body, requestContext, tools, listener);
                    StreamingCapabilityRegistry.markUnsupported(streamingKey);
                } else {
                    requireResponseSource(response, "Anthropic");
                    emitDebug(listener, "Anthropic response mode: SSE");
                    readAnthropicEventStream(response.body().source(), requestContext, tools, listener);
                    StreamingCapabilityRegistry.markSupported(streamingKey);
                }
            }, fallbackHandler, requestHandle);
        } catch (Exception e) {
            listener.onError("Request preparation error", e);
        }
    }

    private void executeStreaming(Request request, int retryCount, String providerId,
                                  AiOperationContext operationContext,
                                  StreamListener listener,
                                  ResponseHandler responseHandler,
                                  StreamingFallbackHandler streamingFallbackHandler,
                                  AiRequestHandle requestHandle) {
        if (requestHandle.isCancelled()) {
            return;
        }
        final int attemptNumber = retryCount + 1;
        final long requestStartedAt = SystemClock.elapsedRealtime();
        emitOperationStatus(listener, operationContext, AiOperationState.WAITING_PROVIDER,
                attemptNumber == 1
                        ? context.getString(R.string.ai_status_waiting_model)
                        : context.getString(R.string.ai_status_waiting_model_attempt,
                                attemptNumber, AiRetryController.MAX_ATTEMPTS),
                attemptNumber, 0L, true, false, null);
        android.util.Log.d("AiProviderService", "=== HTTP REQUEST START ===");
        android.util.Log.d("AiProviderService", "Request ID: " + operationContext.getRequestId());
        android.util.Log.d("AiProviderService", "Provider: " + providerId);
        android.util.Log.d("AiProviderService", "URL: " + sanitizeUrlForDebug(request.url().toString()));
        android.util.Log.d("AiProviderService", "Method: " + request.method());
        android.util.Log.d("AiProviderService", "Attempt: " + attemptNumber + "/" + AiRetryController.MAX_ATTEMPTS);
        ChatFlowLogger.event("http", "request_started", "requestId=" + operationContext.getRequestId()
                + ", provider=" + providerId + ", method=" + request.method()
                + ", attempt=" + attemptNumber);

        // X-Request-Id identifica UMA tentativa HTTP, não a operação inteira.
        // Cada retry de aplicação precisa de um novo valor para não ser confundido
        // pelo backend com uma requisição duplicada/já processada. O requestId da
        // operação continua sendo usado apenas para correlação local e diagnóstico.
        final String httpRequestId = operationContext.newHttpRequestId();
        final Request attemptRequest = request.newBuilder()
                .header("X-Request-Id", httpRequestId)
                .build();
        android.util.Log.d("AiProviderService", "Operation ID: " + operationContext.getRequestId());
        android.util.Log.d("AiProviderService", "HTTP Request ID: " + httpRequestId);
        emitDebug(listener, "HTTP request -> operationId=" + operationContext.getRequestId()
                + ", httpRequestId=" + httpRequestId + ", attempt=" + attemptNumber
                + ", method=" + attemptRequest.method() + ", url="
                + sanitizeUrlForDebug(attemptRequest.url().toString()));
        emitProtocolPayload(listener, "CABEÇALHOS ENVIADOS", attemptRequest.headers().toString());
        emitProtocolPayload(listener, "ENVIADO", requestBodyText(attemptRequest));

        Call call = clientForProvider(providerId).newCall(attemptRequest);
        // Streaming calls have no whole-call deadline: connect/read/write timeouts
        // already bound each network phase, and readTimeout limits silence between
        // chunks. A fixed 120 s call timeout truncated valid long generations.
        call.timeout().timeout(0, TimeUnit.SECONDS);
        requestHandle.attach(call);
        final java.util.concurrent.atomic.AtomicBoolean attemptFinished =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable waitingHeartbeat = new Runnable() {
            @Override
            public void run() {
                boolean streamVisible = listener instanceof EmissionTracker
                        && ((EmissionTracker) listener).emitted.get();
                if (attemptFinished.get() || requestHandle.isCancelled() || streamVisible) {
                    return;
                }
                long elapsedSeconds = Math.max(1L,
                        (SystemClock.elapsedRealtime() - requestStartedAt) / 1000L);
                emitOperationStatus(listener, operationContext, AiOperationState.WAITING_PROVIDER,
                        context.getString(R.string.ai_status_waiting_model_elapsed, elapsedSeconds),
                        attemptNumber, 0L, true, false, null);
                ChatFlowLogger.event("http", "still_waiting", "requestId="
                        + operationContext.getRequestId() + ", attempt=" + attemptNumber
                        + ", elapsedSeconds=" + elapsedSeconds);
                mainHandler.postDelayed(this, 10_000L);
            }
        };
        mainHandler.postDelayed(waitingHeartbeat, 10_000L);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call failedCall, IOException error) {
                attemptFinished.set(true);
                mainHandler.removeCallbacks(waitingHeartbeat);
                long duration = SystemClock.elapsedRealtime() - requestStartedAt;
                requestHandle.clear(failedCall);
                emitDebug(listener, "HTTP failure <- operationId=" + operationContext.getRequestId()
                        + ", attempt=" + attemptNumber + ", durationMs=" + duration
                        + ", error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
                if (failedCall.isCanceled() || requestHandle.isCancelled()) {
                    emitOperationStatus(listener, operationContext, AiOperationState.CANCELLED,
                            context.getString(R.string.ai_status_request_cancelled), attemptNumber, 0L, false, false, "cancelled");
                    listener.onError("cancelled", error);
                    return;
                }

                AiRetryController.RetryDecision decision = retryController.shouldRetry(
                        attemptNumber, -1, null, -1L, true);
                if (decision.shouldRetry()) {
                    UserFacingError friendly = errorClassifier.classifyNetworkError(error);
                    scheduleRetry(request, retryCount, providerId, operationContext, listener,
                            responseHandler, streamingFallbackHandler, decision, friendly, requestHandle);
                    return;
                }

                SecureLogger.logRequestComplete(operationContext.getRequestId(), duration, attemptNumber, false);
                listener.onUserFacingError(errorClassifier.classifyNetworkError(error),
                        operationContext.getRequestId());
            }

            @Override
            public void onResponse(Call respondedCall, Response response) throws IOException {
                long duration = SystemClock.elapsedRealtime() - requestStartedAt;
                android.util.Log.d("AiProviderService", "=== HTTP RESPONSE RECEIVED ===");
                android.util.Log.d("AiProviderService", "Request ID: " + operationContext.getRequestId());
                android.util.Log.d("AiProviderService", "Provider: " + providerId);
                android.util.Log.d("AiProviderService", "Duration: " + duration + "ms");
                android.util.Log.d("AiProviderService", "Status code: " + response.code());
                android.util.Log.d("AiProviderService", "Success: " + response.isSuccessful());
                ChatFlowLogger.event("http", "response_received", "requestId="
                        + operationContext.getRequestId() + ", status=" + response.code()
                        + ", success=" + response.isSuccessful() + ", durationMs=" + duration);

                if (!response.isSuccessful()) {
                    attemptFinished.set(true);
                    mainHandler.removeCallbacks(waitingHeartbeat);
                    int statusCode = response.code();
                    String retryAfterHeader = response.header("Retry-After");
                    long retryAfterSeconds = AiRetryController.parseRetryAfter(retryAfterHeader);
                    String errorBody = response.body() != null ? response.body().string() : "";
                    response.close();
                    requestHandle.clear(respondedCall);

                    // Erro de capacidade de streaming e recuperavel internamente.
                    // Nao envie o primeiro HTTP 400 para a UI: memorize a capacidade
                    // e repita a mesma operacao silenciosamente com stream=false.
                    boolean anythingEmitted = listener instanceof EmissionTracker
                            && ((EmissionTracker) listener).emitted.get();
                    if (!anythingEmitted
                            && !requestHandle.isCancelled()
                            && streamingFallbackHandler != null
                            && streamingFallbackHandler.handle(statusCode, errorBody)) {
                        ChatFlowLogger.event("http", "streaming_fallback_silent",
                                "requestId=" + operationContext.getRequestId()
                                        + ", status=" + statusCode);
                        return;
                    }

                    // Apenas erros que nao foram recuperados silenciosamente chegam
                    // ao registro visual/protocolo e ao classificador de erro.
                    emitProtocolPayload(listener, "CABEÇALHOS RECEBIDOS", response.headers().toString());
                    emitProtocolPayload(listener, "RECEBIDO HTTP " + statusCode, errorBody);

                    UserFacingError friendly = errorClassifier.classifyHttpError(
                            statusCode, errorBody, providerId);
                    AiRetryController.RetryDecision decision = retryController.shouldRetry(
                            attemptNumber, statusCode, errorBody, retryAfterSeconds, false);
                    if (friendly.canRetry() && decision.shouldRetry()) {
                        scheduleRetry(request, retryCount, providerId, operationContext, listener,
                                responseHandler, streamingFallbackHandler, decision, friendly, requestHandle);
                        return;
                    }

                    SecureLogger.logError(operationContext.getRequestId(), statusCode,
                            friendly.getTechnicalCode(), attemptNumber);
                    listener.onUserFacingError(friendly, operationContext.getRequestId());
                    return;
                }

                emitProtocolPayload(listener, "CABEÇALHOS RECEBIDOS", response.headers().toString());
                emitOperationStatus(listener, operationContext, AiOperationState.GENERATING_FINAL_RESPONSE,
                        context.getString(R.string.ai_status_processing_response),
                        attemptNumber, 0L, true, false, null);
                emitDebug(listener, "HTTP " + response.code()
                        + " em " + (SystemClock.elapsedRealtime() - requestStartedAt) + "ms");

                try (Response safeResponse = response) {
                    responseHandler.handle(respondedCall, safeResponse);
                    SecureLogger.logRequestComplete(operationContext.getRequestId(),
                            SystemClock.elapsedRealtime() - requestStartedAt, attemptNumber, true);
                } catch (Exception error) {
                    boolean anythingEmitted = listener instanceof EmissionTracker
                            && ((EmissionTracker) listener).emitted.get();
                    // An empty, successfully completed provider payload is a
                    // semantic failure, not a failed HTTP attempt. Retrying the
                    // same Request here reuses X-Axion-Operation-Id and the
                    // exactly-once gateway correctly rejects it as duplicate.
                    // AgentManager may perform one new logical call instead.
                    boolean emptyProviderResponse = error instanceof EmptyProviderResponseException;
                    boolean retryableFailure = error instanceof IOException
                            && !emptyProviderResponse
                            && !(error instanceof UnsupportedProviderEnvelopeException);
                    AiRetryController.RetryDecision decision = retryController.shouldRetry(
                            attemptNumber, -1, null, -1L, retryableFailure);
                    UserFacingError friendly = classifyProviderPayloadError(error);
                    if (!anythingEmitted && retryableFailure && decision.shouldRetry()) {
                        scheduleRetry(request, retryCount, providerId, operationContext, listener,
                                responseHandler, streamingFallbackHandler, decision, friendly, requestHandle);
                        return;
                    }
                    if (emptyProviderResponse) {
                        ChatFlowLogger.event("http", "empty_provider_payload",
                                "requestId=" + operationContext.getRequestId()
                                        + ", attempt=" + attemptNumber
                                        + ", transportRetry=false");
                    }
                    listener.onUserFacingError(friendly, operationContext.getRequestId());
                } finally {
                    attemptFinished.set(true);
                    mainHandler.removeCallbacks(waitingHeartbeat);
                    requestHandle.clear(respondedCall);
                }
            }
        });
    }

    private UserFacingError classifyProviderPayloadError(Exception error) {
        if (error instanceof EmptyProviderResponseException) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_empty_provider_title))
                    .message(context.getString(R.string.ai_error_empty_provider_message))
                    .actionLabel(context.getString(R.string.common_retry))
                    .canRetry(true)
                    .technicalCode("EMPTY_ASSISTANT_PAYLOAD")
                    .technicalDetails(error.getMessage())
                    .build();
        }
        if (error instanceof UnsupportedProviderEnvelopeException) {
            return UserFacingError.builder()
                    .title(context.getString(R.string.ai_error_incompatible_response_title))
                    .message(context.getString(R.string.ai_error_incompatible_response_message))
                    .canRetry(false)
                    .technicalCode("UNSUPPORTED_RESPONSE_ENVELOPE")
                    .technicalDetails(error.getMessage())
                    .build();
        }
        return errorClassifier.classifyNetworkError(error);
    }

    private void scheduleRetry(Request request, int retryCount, String providerId,
                               AiOperationContext operationContext,
                               StreamListener listener, ResponseHandler responseHandler,
                               StreamingFallbackHandler streamingFallbackHandler,
                               AiRetryController.RetryDecision decision,
                               UserFacingError friendlyError,
                               AiRequestHandle requestHandle) {
        if (requestHandle.isCancelled()) {
            return;
        }
        final int nextAttempt = retryCount + 2;
        final long delayMs = decision.getDelayMillis();
        SecureLogger.logRetry(operationContext.getRequestId(), nextAttempt,
                AiRetryController.MAX_ATTEMPTS, delayMs, decision.getReason());
        final long deadline = SystemClock.elapsedRealtime() + delayMs;

        Runnable countdown = new Runnable() {
            @Override
            public void run() {
                if (requestHandle.isCancelled()) {
                    return;
                }
                long remainingMs = Math.max(0L, deadline - SystemClock.elapsedRealtime());
                if (remainingMs <= 0L) {
                    emitOperationStatus(listener, operationContext, AiOperationState.SENDING_REQUEST,
                            context.getString(R.string.ai_status_retry_sending,
                                    nextAttempt, AiRetryController.MAX_ATTEMPTS),
                            nextAttempt, 0L, true, true,
                            friendlyError == null ? null : friendlyError.getTechnicalCode());
                    executeStreaming(request, retryCount + 1, providerId, operationContext,
                            listener, responseHandler, streamingFallbackHandler, requestHandle);
                    return;
                }

                long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
                String prefix = friendlyError == null
                        ? context.getString(R.string.ai_status_service_temp_unavailable)
                        : friendlyError.getMessage();
                emitOperationStatus(listener, operationContext, AiOperationState.WAITING_RETRY,
                        context.getString(R.string.ai_status_retry_countdown,
                                prefix, seconds, nextAttempt, AiRetryController.MAX_ATTEMPTS),
                        retryCount + 1, remainingMs, true, true,
                        friendlyError == null ? null : friendlyError.getTechnicalCode());
                mainHandler.postDelayed(this, Math.min(1000L, remainingMs));
            }
        };
        mainHandler.post(countdown);
    }

    private void emitOperationStatus(StreamListener listener, AiOperationContext operationContext,
                                     AiOperationState state, String message, int attempt,
                                     long retryAfterMillis, boolean cancellable,
                                     boolean retryable, @Nullable String errorCode) {
        if (listener == null || operationContext == null) {
            return;
        }
        listener.onOperationStatus(AiOperationStatus.builder(state)
                .userMessage(message == null ? "" : message)
                .currentAttempt(attempt)
                .maximumAttempts(AiRetryController.MAX_ATTEMPTS)
                .retryAfterMillis(retryAfterMillis)
                .provider(operationContext.getProviderId())
                .model(operationContext.getModelName())
                .requestId(operationContext.getRequestId())
                .cancellable(cancellable)
                .retryable(retryable)
                .errorCode(errorCode)
                .build());
    }

    private OkHttpClient clientForProvider(String providerId) {
        SharedPreferences prefs = SecurePrefs.of(context);
        JSONObject custom = pro.sketchware.chat.port.VoidPortSettings.getProviderConfigObject(prefs, providerId);
        boolean enabled = prefs.getBoolean("provider_proxy_enabled_" + providerId,
                custom != null && custom.optBoolean("proxyEnabled", false));
        String host = prefs.getString("provider_proxy_host_" + providerId,
                custom == null ? "" : custom.optString("proxyHost", "")).trim();
        String portRaw = prefs.getString("provider_proxy_port_" + providerId,
                custom == null ? "8080" : custom.optString("proxyPort", "8080")).trim();
        if (!enabled || host.isEmpty()) {
            return client;
        }

        int port;
        try {
            port = Integer.parseInt(portRaw);
        } catch (NumberFormatException e) {
            port = 8080;
        }
        String type = prefs.getString("provider_proxy_type_" + providerId,
                custom == null ? "http" : custom.optString("proxyType", "http"))
                .trim()
                .toLowerCase(java.util.Locale.US);
        Proxy.Type proxyType = "socks5".equals(type) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        OkHttpClient.Builder builder = client.newBuilder()
                .proxy(new Proxy(proxyType, new InetSocketAddress(host, port)));

        String username = prefs.getString("provider_proxy_username_" + providerId,
                custom == null ? "" : custom.optString("proxyUsername", "")).trim();
        String password = prefs.getString("provider_proxy_password_" + providerId,
                custom == null ? "" : custom.optString("proxyPassword", "")).trim();
        if (!username.isEmpty() && proxyType == Proxy.Type.HTTP) {
            builder.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(username, password))
                    .build());
        }
        return builder.build();
    }

    private void readOpenAiEventStream(BufferedSource source, ContextBuilder.Result requestContext,
                                       JSONArray tools, StreamListener listener) throws IOException {
        OpenAiStreamState state = new OpenAiStreamState();
        StreamPerf perf = new StreamPerf();
        ProtocolStreamTrace trace = new ProtocolStreamTrace("OPENAI");
        final int[] chunkCount = {0};
        final boolean[] loggedFormat = {false};
        try {
            StreamEventReader.read(source, new StreamEventReader.Listener() {
                @Override
                public void onRawLine(String line) {
                    trace.record(line);
                }

                @Override
                public void onEvent(String eventName, String data, boolean sse) {
                    if (data == null || data.trim().isEmpty() || "[DONE]".equals(data.trim())) {
                        return;
                    }
                    if (!loggedFormat[0]) {
                        emitDebug(listener, "Stream mode: " + (sse ? "SSE" : "NDJSON"));
                        loggedFormat[0] = true;
                    }
                    int index = ++chunkCount[0];
                    perf.onChunk(listener);
                    perf.onProgress(listener, index);
                    try {
                        JSONObject chunk = new JSONObject(data);
                        if (index <= 4) {
                            emitDebug(listener, summarizeOpenAiChunk(chunk, index));
                        }
                        handleOpenAiChunk(chunk, state, listener);
                    } catch (Exception error) {
                        emitDebug(listener, "Chunk parse error #" + index + ": " + previewForDebug(data));
                        Log.e(TAG, "Error parsing stream chunk: " + data, error);
                    }
                }
            });
        } finally {
            trace.publish(listener);
        }

        perf.finish(listener, state.fullContent.length(), state.fullReasoning.length());
        emitDebug(listener, "Stream finished: chunks=" + chunkCount[0]
                + ", contentChars=" + state.fullContent.length()
                + ", reasoningChars=" + state.fullReasoning.length());
        completeOpenAiRequest(state, requestContext, tools, listener);
    }

    private void handleOpenAiJsonResponse(String body, ContextBuilder.Result requestContext,
                                          JSONArray tools, StreamListener listener) throws IOException {
        OpenAiStreamState state = new OpenAiStreamState();
        final JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (Exception e) {
            throw new UnsupportedProviderEnvelopeException(
                    "O provedor retornou JSON inválido: " + previewForDebug(body));
        }

        android.util.Log.d("AiProviderService", "=== PARSING JSON RESPONSE ===");
        recordOpenAiUsage(json.optJSONObject("usage"));
        emitOpenAiUsage(json.optJSONObject("usage"), listener);

        OpenAiResponseEnvelopeParser.ParsedResponse parsed =
                OpenAiResponseEnvelopeParser.parse(json);
        mergeParsedResponse(parsed, state, listener);

        emitDebug(listener, "JSON envelope=" + parsed.envelope
                + ", recognized=" + parsed.recognized
                + ", contentChars=" + state.fullContent.length()
                + ", reasoningChars=" + state.fullReasoning.length()
                + ", toolCalls=" + parsed.toolCalls.size());
        completeOpenAiRequest(state, requestContext, tools, listener);
    }

    private void handleOpenAiChunk(JSONObject json, OpenAiStreamState state, StreamListener listener) {
        recordOpenAiUsage(json.optJSONObject("usage"));
        emitOpenAiUsage(json.optJSONObject("usage"), listener);

        String eventType = json.optString("type", "");
        if (eventType.startsWith("response.") || "error".equals(eventType)) {
            handleResponsesStreamEvent(json, state, listener);
            return;
        }

        JSONArray choices = json.optJSONArray("choices");
        JSONObject delta = null;
        if (choices != null) {
            state.recognizedPayload = true;
            state.envelope = "chat_completions_stream";
        }
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            if (choice != null) {
                delta = choice.optJSONObject("delta");
                if (delta == null) {
                    delta = choice.optJSONObject("message");
                }
                String finishReason = sanitizeStreamValue(choice.opt("finish_reason"));
                if (!finishReason.isEmpty()) {
                    state.finishReason = finishReason;
                }
            }
        }

        // Ollama native format signals truncation via done_reason.
        String doneReason = sanitizeStreamValue(json.opt("done_reason"));
        if (!doneReason.isEmpty()) {
            state.recognizedPayload = true;
            state.envelope = "ollama_stream";
            state.finishReason = doneReason;
        }

        // If no delta (OpenAI style), check for message (Ollama native style).
        if (delta == null) {
            delta = json.optJSONObject("message");
            if (delta != null) {
                state.recognizedPayload = true;
                state.envelope = "message_stream";
            }
        }

        if (delta != null) {
            String content = readStreamText(delta, "content");
            if (!content.isEmpty()) {
                appendOpenAiContentDelta(state, content, listener);
            }

            String reasoning = VoidPortExtractGrammar.readReasoningText(delta);
            if (reasoning.isEmpty() && delta == json.optJSONObject("message")) {
                // If it's Ollama native, thinking might be at top level of chunk.
                reasoning = VoidPortExtractGrammar.readReasoningText(json);
            }
            appendReasoningDelta(state, reasoning, listener);

            JSONArray toolCalls = delta.optJSONArray("tool_calls");
            appendOpenAiToolCalls(toolCalls, state);
            if (toolCalls == null || toolCalls.length() == 0) {
                appendOpenAiFunctionCall(delta.optJSONObject("function_call"), state);
            }
        } else if (json.has("content") || json.has("reasoning_content") || json.has("thinking")) {
            state.recognizedPayload = true;
            state.envelope = "flat_stream";
            String content = readStreamText(json, "content");
            if (!content.isEmpty()) {
                appendOpenAiContentDelta(state, content, listener);
            }
            appendReasoningDelta(state, VoidPortExtractGrammar.readReasoningText(json), listener);
            appendOpenAiToolCalls(json.optJSONArray("tool_calls"), state);
            appendOpenAiFunctionCall(json.optJSONObject("function_call"), state);
        }

        String providerError = readProviderError(json);
        if (!providerError.isEmpty()) {
            state.protocolError = providerError;
        }
    }

    private void handleResponsesStreamEvent(JSONObject event, OpenAiStreamState state,
                                            StreamListener listener) {
        state.recognizedPayload = true;
        state.envelope = "responses_stream";
        String type = event.optString("type", "");

        if ("response.output_text.delta".equals(type)) {
            appendOpenAiContentDelta(state, event.optString("delta", ""), listener);
            return;
        }
        if ("response.reasoning_text.delta".equals(type)
                || "response.reasoning_summary_text.delta".equals(type)) {
            appendReasoningDelta(state, event.optString("delta", ""), listener);
            return;
        }
        if ("response.function_call_arguments.delta".equals(type)) {
            int index = event.optInt("output_index", event.optInt("item_index", 0));
            ToolCallAccumulator accumulator = getToolAccumulator(state, index);
            accumulator.appendId(firstNonEmpty(
                    event.optString("call_id", ""), event.optString("item_id", "")));
            accumulator.appendName(event.optString("name", ""));
            accumulator.appendArgumentsDelta(event.optString("delta", ""));
            return;
        }
        if ("response.output_item.added".equals(type)
                || "response.output_item.done".equals(type)) {
            JSONObject item = event.optJSONObject("item");
            if (item != null) {
                mergeResponsesOutputItem(item,
                        event.optInt("output_index", event.optInt("item_index", 0)),
                        state, listener);
            }
            return;
        }
        if ("response.completed".equals(type)) {
            JSONObject response = event.optJSONObject("response");
            if (response != null) {
                mergeParsedResponse(OpenAiResponseEnvelopeParser.parse(response), state, listener);
            }
            state.finishReason = "completed";
            return;
        }
        if ("response.failed".equals(type) || "response.incomplete".equals(type)
                || "error".equals(type)) {
            state.protocolError = readProviderError(event);
            if (state.protocolError.isEmpty()) {
                JSONObject response = event.optJSONObject("response");
                state.protocolError = firstNonEmpty(
                        response == null ? "" : readProviderError(response),
                        response == null ? "" : sanitizeStreamValue(response.opt("incomplete_details")),
                        type);
            }
            state.finishReason = type;
        }
    }

    private void mergeResponsesOutputItem(JSONObject item, int index, OpenAiStreamState state,
                                          StreamListener listener) {
        String type = item.optString("type", "");
        if ("function_call".equals(type)) {
            ToolCallAccumulator accumulator = getToolAccumulator(state, index);
            accumulator.appendId(firstNonEmpty(item.optString("call_id", ""), item.optString("id", "")));
            accumulator.appendName(item.optString("name", ""));
            accumulator.mergeArgumentsSnapshot(sanitizeStreamValue(item.opt("arguments")));
            return;
        }
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("output", new JSONArray().put(item));
        } catch (Exception ignored) {
            return;
        }
        mergeParsedResponse(OpenAiResponseEnvelopeParser.parse(wrapper), state, listener);
    }

    private void mergeParsedResponse(OpenAiResponseEnvelopeParser.ParsedResponse parsed,
                                     OpenAiStreamState state, StreamListener listener) {
        if (parsed == null) {
            return;
        }
        state.recognizedPayload = state.recognizedPayload || parsed.recognized;
        if (!parsed.envelope.isEmpty()) {
            state.envelope = parsed.envelope;
        }
        if (!parsed.finishReason.isEmpty()) {
            state.finishReason = parsed.finishReason;
        }
        if (!parsed.blockReason.isEmpty()) {
            state.blockReason = parsed.blockReason;
        }
        if (!parsed.providerError.isEmpty()) {
            state.protocolError = parsed.providerError;
        }
        appendSnapshotDelta(state.fullContent, parsed.content, listener, false);
        appendSnapshotDelta(state.fullReasoning, parsed.reasoning, listener, true);
        appendNormalizedToolCalls(parsed.toolCalls, state);
    }

    private void appendSnapshotDelta(StringBuilder target, String snapshot,
                                     StreamListener listener, boolean reasoning) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        String current = target.toString();
        if (snapshot.equals(current) || current.startsWith(snapshot)) {
            return;
        }
        // Completed events often repeat the cumulative text already delivered by
        // delta events. Only append a true suffix; never duplicate a divergent
        // snapshot on top of streamed content.
        if (!current.isEmpty() && !snapshot.startsWith(current)) {
            return;
        }
        String delta = current.isEmpty() ? snapshot : snapshot.substring(current.length());
        if (delta.isEmpty()) {
            return;
        }
        target.append(delta);
        if (reasoning) {
            listener.onReasoning(delta);
        } else {
            listener.onContent(delta);
        }
    }

    private void appendReasoningDelta(OpenAiStreamState state, String reasoning,
                                      StreamListener listener) {
        if (reasoning == null || reasoning.isEmpty()) {
            return;
        }
        state.fullReasoning.append(reasoning);
        listener.onReasoning(reasoning);
    }

    private ToolCallAccumulator getToolAccumulator(OpenAiStreamState state, int index) {
        ToolCallAccumulator accumulator = state.toolCalls.get(index);
        if (accumulator == null) {
            accumulator = new ToolCallAccumulator(index);
            state.toolCalls.put(index, accumulator);
        }
        return accumulator;
    }

    private void appendNormalizedToolCalls(List<ToolCall> calls, OpenAiStreamState state) {
        if (calls == null) {
            return;
        }
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            if (call == null) {
                continue;
            }
            ToolCallAccumulator accumulator = findToolAccumulator(state, call, i);
            accumulator.appendId(call.getId());
            accumulator.appendName(call.getName());
            accumulator.mergeArgumentsSnapshot(call.getArguments());
        }
    }

    private ToolCallAccumulator findToolAccumulator(OpenAiStreamState state, ToolCall call,
                                                     int preferredIndex) {
        String callId = call == null ? "" : call.getId();
        if (callId != null && !callId.trim().isEmpty()) {
            for (ToolCallAccumulator existing : state.toolCalls.values()) {
                if (callId.equals(existing.getExplicitId())) {
                    return existing;
                }
            }
        }

        ToolCallAccumulator preferred = state.toolCalls.get(preferredIndex);
        if (preferred != null && (preferred.getName().isEmpty()
                || preferred.getName().equals(call == null ? "" : call.getName()))) {
            return preferred;
        }

        int index = Math.max(0, preferredIndex);
        while (state.toolCalls.containsKey(index)) {
            index++;
        }
        return getToolAccumulator(state, index);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String readProviderError(JSONObject json) {
        if (json == null) {
            return "";
        }
        Object error = json.opt("error");
        if (error instanceof JSONObject) {
            JSONObject object = (JSONObject) error;
            return firstNonEmpty(object.optString("message", ""),
                    object.optString("detail", ""), object.toString());
        }
        return error == null || error == JSONObject.NULL ? "" : String.valueOf(error);
    }

    private void appendOpenAiToolCalls(JSONArray toolCalls, OpenAiStreamState state) {
        if (toolCalls == null || toolCalls.length() == 0) {
            return;
        }

        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject toolCall = toolCalls.optJSONObject(i);
            if (toolCall == null) {
                continue;
            }

            // Accumulate ALL tool calls (parallel calls arrive with index 0..N).
            // Previously only index 0 was kept, silently dropping the rest.
            int index = toolCall.optInt("index", i);

            ToolCallAccumulator accumulator = getToolAccumulator(state, index);

            accumulator.appendId(sanitizeStreamValue(toolCall.opt("id")));
            JSONObject function = toolCall.optJSONObject("function");
            if (function != null) {
                accumulator.appendName(sanitizeStreamValue(function.opt("name")));
                accumulator.appendArgumentsDelta(sanitizeStreamValue(function.opt("arguments")));
            }
        }

        // Tool arguments arrive in chunks on most OpenAI-compatible streams.
        // Emit only after the stream finishes so AgentManager receives one complete JSON payload.
    }

    private void appendOpenAiFunctionCall(JSONObject functionCall, OpenAiStreamState state) {
        if (functionCall == null) {
            return;
        }
        ToolCallAccumulator accumulator = getToolAccumulator(state, 0);
        accumulator.appendName(sanitizeStreamValue(functionCall.opt("name")));
        accumulator.appendArgumentsDelta(sanitizeStreamValue(functionCall.opt("arguments")));
    }

    private void completeOpenAiRequest(OpenAiStreamState state, ContextBuilder.Result requestContext,
                                       JSONArray tools, StreamListener listener) throws IOException {
        String finalContent = state.fullContent.toString();
        String finalReasoning = state.fullReasoning.toString();
        if (state.fullReasoning.toString().trim().isEmpty()) {
            VoidPortExtractGrammar.ReasoningExtraction reasoningExtraction =
                    VoidPortExtractGrammar.extractThinkTaggedReasoning(finalContent);
            if (!reasoningExtraction.fullReasoning.isEmpty()) {
                finalContent = reasoningExtraction.fullText;
                state.fullReasoning.append(reasoningExtraction.fullReasoning);
                finalReasoning = state.fullReasoning.toString();
                listener.onReasoning(reasoningExtraction.fullReasoning);
                emitDebug(listener, "Reasoning extracted from <think> tags");
            }
        }
        if (!state.protocolError.isEmpty()) {
            throw new IOException("O provedor encerrou o stream com erro: " + state.protocolError);
        }
        List<ToolCall> nativeCalls = new ArrayList<>();
        boolean droppedTruncatedTool = false;
        boolean truncated = isTruncatedFinish(state.finishReason);
        for (ToolCallAccumulator accumulator : state.toolCalls.values()) {
            if (!accumulator.isReady()) {
                continue;
            }
            if (truncated && !isValidJsonObject(accumulator.getArguments())) {
                droppedTruncatedTool = true;
                emitDebug(listener, "Tool call dropped: response truncated (finish_reason="
                        + state.finishReason + ") with invalid JSON args, tool=" + accumulator.getName());
                continue;
            }
            nativeCalls.add(new ToolCall(
                    accumulator.getName(),
                    accumulator.getArguments(),
                    accumulator.getId()));
        }
        if (droppedTruncatedTool && nativeCalls.isEmpty()) {
            String warning = "\n\n[Aviso: a resposta foi truncada pelo limite de tokens e a chamada de ferramenta foi descartada. Tente novamente ou aumente o limite de saída.]";
            state.fullContent.append(warning);
            finalContent = state.fullContent.toString();
            listener.onContent(warning);
        }
        ToolCallParseResult detected = detectAndEmitToolCalls(
                finalContent,
                finalReasoning,
                nativeCalls,
                tools,
                listener);
        finalContent = detected.getRemainingContent();
        finalReasoning = detected.getRemainingReasoning();
        boolean hasToolCall = detected.hasToolCalls();

        if (finalContent.trim().isEmpty() && finalReasoning.trim().isEmpty()
                && !hasToolCall) {
            if (!state.blockReason.isEmpty()) {
                listener.onError("Resposta bloqueada pelo provedor (safety): " + state.blockReason, null);
                return;
            }
            if (!state.protocolError.isEmpty()) {
                throw new UnsupportedProviderEnvelopeException(
                        "O provedor retornou um erro dentro de uma resposta HTTP válida: "
                                + state.protocolError);
            }
            String details = "envelope=" + (state.envelope.isEmpty() ? "desconhecido" : state.envelope)
                    + (state.finishReason.isEmpty() ? "" : ", finishReason=" + state.finishReason);
            emitDebug(listener, "Final assistant payload was empty, " + details);
            if (!state.recognizedPayload) {
                throw new UnsupportedProviderEnvelopeException(
                        "Formato de resposta não reconhecido pelo cliente (" + details + ").");
            }
            throw new EmptyProviderResponseException(
                    "O provedor retornou um payload reconhecido, porém sem conteúdo, raciocínio ou chamadas de ferramenta ("
                            + details + ").");
        }
        emitDebug(listener, "Final assistant payload: contentChars=" + finalContent.length()
                + ", reasoningChars=" + finalReasoning.length()
                + ", toolProtocol=" + detected.getProtocol()
                + ", toolCalls=" + detected.getToolCalls().size());
        listener.onFinalMessage(finalContent, finalReasoning, state.finishReason);
    }

    private void readGeminiEventStream(BufferedSource source, ContextBuilder.Result requestContext,
                                       JSONArray tools, StreamListener listener) throws IOException {
        OpenAiStreamState state = new OpenAiStreamState();
        StreamPerf perf = new StreamPerf();
        ProtocolStreamTrace trace = new ProtocolStreamTrace("GEMINI");
        final int[] chunkCount = {0};
        try {
            StreamEventReader.read(source, new StreamEventReader.Listener() {
                @Override
                public void onRawLine(String line) {
                    trace.record(line);
                }

                @Override
                public void onEvent(String eventName, String data, boolean sse) {
                    if (data == null || data.trim().isEmpty() || "[DONE]".equals(data.trim())) {
                        return;
                    }
                    int index = ++chunkCount[0];
                    perf.onChunk(listener);
                    perf.onProgress(listener, index);
                    try {
                        JSONObject chunk = new JSONObject(data);
                        handleGeminiChunk(chunk, state, listener);
                    } catch (Exception error) {
                        emitDebug(listener, "Gemini chunk parse error #" + index + ": " + previewForDebug(data));
                        Log.e(TAG, "Error parsing Gemini stream chunk: " + data, error);
                    }
                }
            });
        } finally {
            trace.publish(listener);
        }
        perf.finish(listener, state.fullContent.length(), state.fullReasoning.length());
        emitDebug(listener, "Gemini stream finished: chunks=" + chunkCount[0]
                + ", contentChars=" + state.fullContent.length()
                + ", reasoningChars=" + state.fullReasoning.length());
        completeOpenAiRequest(state, requestContext, tools, listener);
    }

    private void handleGeminiJsonResponse(String body, ContextBuilder.Result requestContext,
                                          JSONArray tools, StreamListener listener) throws IOException {
        final JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (Exception e) {
            throw new UnsupportedProviderEnvelopeException(
                    "Gemini retornou JSON invalido: " + previewForDebug(body));
        }

        OpenAiStreamState state = new OpenAiStreamState();
        handleGeminiChunk(json, state, listener);
        state.envelope = "gemini_json";
        String providerError = readProviderError(json);
        if (!providerError.isEmpty()) {
            state.protocolError = providerError;
        }
        emitDebug(listener, "Gemini JSON: contentChars=" + state.fullContent.length()
                + ", reasoningChars=" + state.fullReasoning.length()
                + ", toolCalls=" + state.toolCalls.size());
        completeOpenAiRequest(state, requestContext, tools, listener);
    }

    private void handleGeminiChunk(JSONObject chunk, OpenAiStreamState state, StreamListener listener) {
        if (chunk.has("candidates") || chunk.has("promptFeedback") || chunk.has("usageMetadata")) {
            state.recognizedPayload = true;
            state.envelope = "gemini_stream";
        }
        JSONObject usage = chunk.optJSONObject("usageMetadata");
        if (usage != null) {
            long inputTokens = usage.optLong("promptTokenCount", 0);
            long outputTokens = usage.optLong("candidatesTokenCount", 0);
            long total = usage.optLong("totalTokenCount", 0);
            if (total <= 0) {
                total = inputTokens + outputTokens;
            }
            emitReportedUsage(listener, total > inputTokens && inputTokens > 0 ? inputTokens : total > outputTokens ? total - outputTokens : total, outputTokens, 0, 0, 0);
            TokenUsageStore.record(SketchApplication.getContext(), total);
        }
        // Safety block: Gemini reports it via promptFeedback.blockReason with no content.
        JSONObject promptFeedback = chunk.optJSONObject("promptFeedback");
        if (promptFeedback != null) {
            String blockReason = sanitizeStreamValue(promptFeedback.opt("blockReason"));
            if (!blockReason.isEmpty()) {
                state.blockReason = blockReason;
            }
        }
        JSONArray candidates = chunk.optJSONArray("candidates");
        for (int i = 0; candidates != null && i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            String finishReason = candidate == null ? "" : sanitizeStreamValue(candidate.opt("finishReason"));
            if (!finishReason.isEmpty() && !"STOP".equalsIgnoreCase(finishReason)) {
                state.finishReason = finishReason;
                if ("SAFETY".equalsIgnoreCase(finishReason) || "PROHIBITED_CONTENT".equalsIgnoreCase(finishReason)) {
                    state.blockReason = finishReason;
                }
            }
            JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            for (int j = 0; parts != null && j < parts.length(); j++) {
                JSONObject part = parts.optJSONObject(j);
                if (part == null) {
                    continue;
                }
                String text = part.optString("text", "");
                if (!text.isEmpty()) {
                    appendOpenAiContentDelta(state, text, listener);
                }
                JSONObject functionCall = part.optJSONObject("functionCall");
                if (functionCall != null) {
                    // Each functionCall part is a distinct (possibly parallel) tool call.
                    int index = state.toolCalls.size();
                    ToolCallAccumulator accumulator = new ToolCallAccumulator(index);
                    state.toolCalls.put(index, accumulator);
                    accumulator.appendName(functionCall.optString("name", ""));
                    accumulator.mergeArgumentsSnapshot(functionCall.optJSONObject("args") == null
                            ? "{}"
                            : functionCall.optJSONObject("args").toString());
                    accumulator.appendId(functionCall.optString("id", ""));
                }
            }
        }
    }

    private Request buildOpenAiCompatibleTextRequest(ProviderConfig providerConfig, String providerId,
                                                     String modelName, String systemPrompt, String userPrompt,
                                                     java.util.List<String> imageDataUrls) throws IOException {
        try {
            JSONArray messages = new JSONArray();
            if (!TextUtils.isEmpty(systemPrompt)) {
                messages.put(new JSONObject()
                        .put("role", VoidPortLlmMessage.instructionRole(providerId, modelName))
                        .put("content", systemPrompt));
            }
            Object userContent = userPrompt == null ? "" : userPrompt;
            if (!imageDataUrls.isEmpty()) {
                JSONArray content = new JSONArray().put(new JSONObject()
                        .put("type", "text").put("text", userContent));
                for (String dataUrl : imageDataUrls) {
                    content.put(new JSONObject().put("type", "image_url")
                            .put("image_url", new JSONObject().put("url", dataUrl)));
                }
                userContent = content;
            }
            messages.put(new JSONObject().put("role", "user").put("content", userContent));

            JSONObject jsonBody = new JSONObject();
            VoidPortLlmMessage.putModelIfNeeded(jsonBody, providerConfig, modelName);
            jsonBody.put("messages", messages);
            jsonBody.put("stream", false);
            if ("ollama".equals(providerId)) {
                jsonBody.put("think", ollamaThinkEnabled());
            }

            return new Request.Builder()
                    .url(VoidPortLlmMessage.resolveRequestUrl(providerConfig, modelName))
                    .headers(providerAdapters.get(providerConfig.family).headers(providerConfig))
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();
        } catch (Exception e) {
            throw new IOException("Request preparation error", e);
        }
    }

    private Request buildAnthropicTextRequest(ProviderConfig providerConfig, String modelName,
                                              String systemPrompt, String userPrompt,
                                              java.util.List<String> imageDataUrls) throws IOException {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelName);
            jsonBody.put("max_tokens", VoidPortLlmMessage.maxOutputTokens("anthropic", modelName));
            if (!TextUtils.isEmpty(systemPrompt)) {
                jsonBody.put("system", systemPrompt);
            }
            JSONArray messages = new JSONArray();
            Object userContent = userPrompt == null ? "" : userPrompt;
            if (!imageDataUrls.isEmpty()) {
                JSONArray content = new JSONArray().put(new JSONObject()
                        .put("type", "text").put("text", userContent));
                for (String dataUrl : imageDataUrls) {
                    String[] image = splitDataUrl(dataUrl);
                    content.put(new JSONObject().put("type", "image")
                            .put("source", new JSONObject().put("type", "base64")
                                    .put("media_type", image[0]).put("data", image[1])));
                }
                userContent = content;
            }
            messages.put(new JSONObject().put("role", "user").put("content", userContent));
            jsonBody.put("messages", messages);

            return new Request.Builder()
                    .url(providerConfig.baseUrl)
                    .headers(providerAdapters.get(providerConfig.family).headers(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();
        } catch (Exception e) {
            throw new IOException("Request preparation error", e);
        }
    }

    private Request buildGeminiTextRequest(ProviderConfig providerConfig, String modelName,
                                           String systemPrompt, String userPrompt,
                                           java.util.List<String> imageDataUrls) throws IOException {
        try {
            JSONObject jsonBody = new JSONObject();
            JSONArray parts = new JSONArray().put(new JSONObject()
                    .put("text", userPrompt == null ? "" : userPrompt));
            for (String dataUrl : imageDataUrls) {
                String[] image = splitDataUrl(dataUrl);
                parts.put(new JSONObject().put("inlineData", new JSONObject()
                        .put("mimeType", image[0]).put("data", image[1])));
            }
            jsonBody.put("contents", new JSONArray().put(new JSONObject()
                    .put("role", "user").put("parts", parts)));
            if (!TextUtils.isEmpty(systemPrompt)) {
                jsonBody.put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("text", systemPrompt))));
            }

            HttpUrl url = HttpUrl.parse(providerConfig.baseUrl + "/models/" + modelName + ":generateContent")
                    .newBuilder()
                    .build();

            return new Request.Builder()
                    .url(url)
                    .headers(providerAdapters.get(providerConfig.family).headers(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();
        } catch (Exception e) {
            throw new IOException("Request preparation error", e);
        }
    }

    private static String[] splitDataUrl(String dataUrl) throws IOException {
        if (dataUrl == null || !dataUrl.startsWith("data:") || !dataUrl.contains(";base64,")) {
            throw new IOException("Invalid image data URL");
        }
        int separator = dataUrl.indexOf(";base64,");
        return new String[]{dataUrl.substring(5, separator), dataUrl.substring(separator + 8)};
    }

    private String parseOpenAiCompatibleTextResponse(String body) throws IOException {
        try {
            JSONObject json = new JSONObject(body);
            recordOpenAiUsage(json.optJSONObject("usage"));
            JSONArray choices = json.optJSONArray("choices");
            JSONObject firstChoice = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
            JSONObject message = firstChoice != null ? firstChoice.optJSONObject("message") : json.optJSONObject("message");
            String content = message != null ? sanitizeStreamValue(message.opt("content")) : "";
            if (content.isEmpty() && json.has("content")) {
                content = sanitizeStreamValue(json.opt("content"));
            }
            if (content.isEmpty()) {
                String reasoning = message != null ? VoidPortExtractGrammar.readReasoningText(message) : "";
                if (reasoning.isEmpty()) {
                    reasoning = VoidPortExtractGrammar.readReasoningText(json);
                }
                content = reasoning;
            }
            return content;
        } catch (Exception e) {
            throw new IOException("Failed to parse AI response", e);
        }
    }

    private String parseAnthropicTextResponse(String body) throws IOException {
        try {
            JSONObject json = new JSONObject(body);
            JSONArray content = json.optJSONArray("content");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; content != null && i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block == null) {
                    continue;
                }
                if ("text".equals(block.optString("type", ""))) {
                    builder.append(block.optString("text", ""));
                }
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IOException("Failed to parse Anthropic response", e);
        }
    }

    private String parseGeminiTextResponse(String body) throws IOException {
        try {
            JSONObject json = new JSONObject(body);
            StringBuilder builder = new StringBuilder();
            JSONArray candidates = json.optJSONArray("candidates");
            JSONObject firstCandidate = candidates != null && candidates.length() > 0 ? candidates.optJSONObject(0) : null;
            JSONObject content = firstCandidate == null ? json.optJSONObject("content") : firstCandidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            for (int i = 0; parts != null && i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) {
                    builder.append(part.optString("text", ""));
                }
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IOException("Failed to parse Gemini response", e);
        }
    }

    private static String suggestedGeminiReplacement(int statusCode, String errorBody,
                                                     String currentModel) {
        if (statusCode != 404 || errorBody == null || errorBody.isEmpty()) return "";
        String normalized = errorBody.toLowerCase(java.util.Locale.US);
        if (!normalized.contains("no longer available")
                && !normalized.contains("not found")) return "";
        // Although listModels still advertises 2.5 Flash, generation is denied
        // for newer API accounts. This replacement was verified with the same
        // credential and keeps the user on the low-latency Flash family.
        if ("gemini-2.5-flash".equalsIgnoreCase(currentModel)) {
            return "gemini-3.5-flash-lite";
        }
        Matcher matcher = GEMINI_REPLACEMENT_PATTERN.matcher(errorBody);
        if (!matcher.find()) return "";
        String replacement = matcher.group(1) == null ? "" : matcher.group(1).trim();
        return replacement.equalsIgnoreCase(currentModel) ? "" : replacement;
    }

    private void persistGeminiModelReplacement(String providerId, String oldModel,
                                               String replacement) {
        SharedPreferences prefs = settingsRepository.preferences();
        List<String> models = new ArrayList<>(
                VoidPortSettings.getProviderModels(prefs, providerId));
        int oldIndex = models.indexOf(oldModel);
        models.remove(oldModel);
        if (!models.contains(replacement)) {
            if (oldIndex >= 0 && oldIndex <= models.size()) models.add(oldIndex, replacement);
            else models.add(replacement);
        }
        VoidPortSettings.setProviderModels(prefs, providerId, models);
        settingsRepository.select(providerId, replacement);
    }

    private void sleepBeforeBlockingRetry(long delayMillis) {
        try {
            Thread.sleep(Math.max(250L, delayMillis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleAnthropicJsonResponse(String body, ContextBuilder.Result requestContext,
                                             JSONArray tools, StreamListener listener) throws IOException {
        final JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (Exception e) {
            throw new UnsupportedProviderEnvelopeException(
                    "Anthropic retornou JSON invalido: " + previewForDebug(body));
        }

        String providerError = readProviderError(json);
        if (!providerError.isEmpty()) {
            throw new UnsupportedProviderEnvelopeException(
                    "Anthropic retornou erro: " + providerError);
        }

        AnthropicStreamState state = new AnthropicStreamState();
        state.stopReason = sanitizeStreamValue(json.opt("stop_reason"));

        JSONObject usage = json.optJSONObject("usage");
        if (usage != null) {
            long inputTokens = usage.optLong("input_tokens", 0)
                    + usage.optLong("cache_read_input_tokens", 0)
                    + usage.optLong("cache_creation_input_tokens", 0);
            long outputTokens = usage.optLong("output_tokens", 0);
            emitReportedUsage(listener, inputTokens, outputTokens,
                    usage.optLong("cache_read_input_tokens", 0)
                            + usage.optLong("cache_creation_input_tokens", 0),
                    0, 0);
            TokenUsageStore.record(SketchApplication.getContext(), inputTokens + outputTokens);
            emitDebug(listener, "Anthropic usage: input=" + inputTokens
                    + ", output=" + outputTokens);
        }

        JSONArray content = json.optJSONArray("content");
        for (int i = 0; content != null && i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) {
                continue;
            }
            String type = block.optString("type", "");
            if ("text".equals(type)) {
                appendAnthropicContentDelta(state, block.optString("text", ""), listener);
            } else if ("thinking".equals(type)) {
                String thinking = block.optString("thinking", "");
                if (!thinking.isEmpty()) {
                    state.fullReasoning.append(thinking);
                    listener.onReasoning(thinking);
                }
            } else if ("redacted_thinking".equals(type)) {
                String redacted = "[redacted_thinking]";
                state.fullReasoning.append(redacted);
                listener.onReasoning(redacted);
            } else if ("tool_use".equals(type)) {
                ToolCallAccumulator accumulator = new ToolCallAccumulator(i);
                accumulator.appendId(block.optString("id", ""));
                accumulator.appendName(block.optString("name", ""));
                Object input = block.opt("input");
                accumulator.mergeArgumentsSnapshot(
                        input == null || input == JSONObject.NULL ? "{}" : String.valueOf(input));
                state.toolBlocks.put(i, accumulator);
            }
        }

        List<ToolCall> nativeCalls = new ArrayList<>();
        boolean truncated = isTruncatedFinish(state.stopReason);
        boolean droppedTruncatedTool = false;
        for (ToolCallAccumulator accumulator : state.toolBlocks.values()) {
            if (!accumulator.isReady()) {
                continue;
            }
            if (truncated && !isValidJsonObject(accumulator.getArguments())) {
                droppedTruncatedTool = true;
                emitDebug(listener, "Anthropic tool call dropped: stop_reason=" + state.stopReason
                        + " with invalid JSON args, tool=" + accumulator.getName());
                continue;
            }
            nativeCalls.add(new ToolCall(
                    accumulator.getName(), accumulator.getArguments(), accumulator.getId()));
        }
        if (droppedTruncatedTool && nativeCalls.isEmpty()) {
            String warning = "\n\n[Aviso: a resposta foi truncada pelo limite de tokens e a chamada de ferramenta foi descartada. Tente novamente ou aumente o limite de saida.]";
            state.fullContent.append(warning);
            listener.onContent(warning);
        }

        ToolCallParseResult detected = detectAndEmitToolCalls(
                state.fullContent.toString(),
                state.fullReasoning.toString(),
                nativeCalls,
                tools,
                listener);
        if (detected.getRemainingContent().trim().isEmpty()
                && detected.getRemainingReasoning().trim().isEmpty()
                && !detected.hasToolCalls()) {
            listener.onError("Anthropic response was empty.", null);
            return;
        }
        emitDebug(listener, "Anthropic JSON tool detection: protocol=" + detected.getProtocol()
                + ", calls=" + detected.getToolCalls().size());
        listener.onFinalMessage(
                detected.getRemainingContent(), detected.getRemainingReasoning(), state.stopReason);
    }

    private void readAnthropicEventStream(BufferedSource source, ContextBuilder.Result requestContext,
                                          JSONArray tools,
                                          StreamListener listener) throws IOException {
        AnthropicStreamState state = new AnthropicStreamState();
        ProtocolStreamTrace trace = new ProtocolStreamTrace("ANTHROPIC");

        try {
            StreamEventReader.read(source, new StreamEventReader.Listener() {
                @Override
                public void onRawLine(String line) {
                    trace.record(line);
                }

                @Override
                public void onEvent(String eventName, String data, boolean sse) throws IOException {
                    dispatchAnthropicEvent(eventName, data, state, listener);
                }
            });
        } finally {
            trace.publish(listener);
        }

        List<ToolCall> nativeCalls = new ArrayList<>();
        boolean droppedTruncatedTool = false;
        boolean truncated = isTruncatedFinish(state.stopReason);
        for (ToolCallAccumulator accumulator : state.toolBlocks.values()) {
            if (!accumulator.isReady()) {
                continue;
            }
            if (truncated && !isValidJsonObject(accumulator.getArguments())) {
                droppedTruncatedTool = true;
                emitDebug(listener, "Anthropic tool call dropped: stop_reason=" + state.stopReason
                        + " with invalid JSON args, tool=" + accumulator.getName());
                continue;
            }
            nativeCalls.add(new ToolCall(
                    accumulator.getName(),
                    accumulator.getArguments(),
                    accumulator.getId()));
        }
        if (droppedTruncatedTool && nativeCalls.isEmpty()) {
            String warning = "\n\n[Aviso: a resposta foi truncada pelo limite de tokens e a chamada de ferramenta foi descartada. Tente novamente ou aumente o limite de saída.]";
            state.fullContent.append(warning);
            listener.onContent(warning);
        }
        ToolCallParseResult detected = detectAndEmitToolCalls(
                state.fullContent.toString(),
                state.fullReasoning.toString(),
                nativeCalls,
                tools,
                listener);
        if (detected.getRemainingContent().trim().isEmpty()
                && detected.getRemainingReasoning().trim().isEmpty()
                && !detected.hasToolCalls()) {
            listener.onError("Anthropic response was empty.", null);
            return;
        }
        emitDebug(listener, "Anthropic tool detection: protocol=" + detected.getProtocol()
                + ", calls=" + detected.getToolCalls().size());
        listener.onFinalMessage(
                detected.getRemainingContent(),
                detected.getRemainingReasoning(),
                state.stopReason);
    }

    private void dispatchAnthropicEvent(String eventName, String data,
                                         AnthropicStreamState state, StreamListener listener) throws IOException {
        if (data == null || data.trim().isEmpty() || "[DONE]".equals(data.trim())) {
            return;
        }
        JSONObject json;
        try {
            json = new JSONObject(data);
        } catch (Exception parseError) {
            // A single malformed chunk must not abort/restart the whole stream
            // (that would duplicate everything already emitted). Log and skip.
            Log.e(TAG, "Skipping malformed Anthropic chunk: " + previewForDebug(data), parseError);
            return;
        }
        try {
            String type = json.optString("type", eventName == null ? "" : eventName);

            if ("error".equals(type)) {
                JSONObject error = json.optJSONObject("error");
                throw new IOException(error == null ? "Anthropic stream error" : error.toString());
            }

            if ("message_start".equals(type)) {
                JSONObject message = json.optJSONObject("message");
                JSONObject usage = message == null ? null : message.optJSONObject("usage");
                if (usage != null) {
                    TokenUsageStore.record(
                            SketchApplication.getContext(),
                            usage.optLong("input_tokens", 0)
                                    + usage.optLong("cache_read_input_tokens", 0)
                                    + usage.optLong("cache_creation_input_tokens", 0));
                    emitDebug(listener, "Anthropic usage: input=" + usage.optInt("input_tokens", 0)
                            + ", cacheRead=" + usage.optInt("cache_read_input_tokens", 0)
                            + ", cacheWrite=" + usage.optInt("cache_creation_input_tokens", 0));
                }
                return;
            }

            if ("message_delta".equals(type)) {
                JSONObject usage = json.optJSONObject("usage");
                if (usage != null) {
                    emitReportedUsage(listener, usage.optLong("input_tokens", 0),
                            usage.optLong("output_tokens", 0),
                            usage.optLong("cache_read_input_tokens", 0),
                            usage.optLong("cache_creation_input_tokens", 0),
                            0);
                    TokenUsageStore.record(
                            SketchApplication.getContext(),
                            usage.optLong("output_tokens", 0));
                }
                JSONObject delta = json.optJSONObject("delta");
                String stopReason = delta == null ? "" : sanitizeStreamValue(delta.opt("stop_reason"));
                if (!stopReason.isEmpty()) {
                    state.stopReason = stopReason;
                }
                return;
            }

            if ("content_block_start".equals(type)) {
                int blockIndex = json.optInt("index", 0);
                JSONObject block = json.optJSONObject("content_block");
                if (block == null) {
                    return;
                }
                String blockType = block.optString("type", "");
                if ("text".equals(blockType)) {
                    String text = block.optString("text", "");
                    if (!text.isEmpty()) {
                        appendAnthropicContentDelta(state, text, listener);
                    }
                } else if ("thinking".equals(blockType)) {
                    String text = block.optString("thinking", "");
                    if (!text.isEmpty()) {
                        state.fullReasoning.append(text);
                        listener.onReasoning(text);
                    }
                } else if ("redacted_thinking".equals(blockType)) {
                    String text = "[redacted_thinking]";
                    state.fullReasoning.append(text);
                    listener.onReasoning(text);
                } else if ("tool_use".equals(blockType)) {
                    ToolCallAccumulator accumulator = new ToolCallAccumulator(blockIndex);
                    accumulator.appendId(block.optString("id", ""));
                    accumulator.appendName(block.optString("name", ""));
                    state.toolBlocks.put(blockIndex, accumulator);
                }
                return;
            }

            if ("content_block_delta".equals(type)) {
                int blockIndex = json.optInt("index", -1);
                JSONObject delta = json.optJSONObject("delta");
                if (delta == null) {
                    return;
                }
                String deltaType = delta.optString("type", "");
                if ("text_delta".equals(deltaType)) {
                    String text = delta.optString("text", "");
                    if (!text.isEmpty()) {
                        appendAnthropicContentDelta(state, text, listener);
                    }
                } else if ("thinking_delta".equals(deltaType)) {
                    String text = delta.optString("thinking", "");
                    if (!text.isEmpty()) {
                        state.fullReasoning.append(text);
                        listener.onReasoning(text);
                    }
                } else if ("input_json_delta".equals(deltaType)) {
                    ToolCallAccumulator accumulator = state.toolBlocks.get(blockIndex);
                    if (accumulator == null && !state.toolBlocks.isEmpty()) {
                        // Fallback: append to the most recently opened tool block.
                        for (ToolCallAccumulator candidate : state.toolBlocks.values()) {
                            accumulator = candidate;
                        }
                    }
                    if (accumulator != null) {
                        accumulator.appendArgumentsDelta(delta.optString("partial_json", ""));
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Falha ao processar evento Anthropic", e);
        }
    }

    /** User preference: enable Ollama's native "think" mode (default off). */
    private boolean ollamaThinkEnabled() {
        SharedPreferences prefs = SecurePrefs.of(context);
        return prefs.getBoolean("ollama_think_enabled", false);
    }

    /** True when the provider stopped because it hit the output-token limit. */
    private static boolean isTruncatedFinish(String reason) {
        if (reason == null) {
            return false;
        }
        String normalized = reason.trim().toLowerCase(java.util.Locale.US);
        return "length".equals(normalized)
                || "max_tokens".equals(normalized)
                || "max_output_tokens".equals(normalized);
    }

    private static boolean isValidJsonObject(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            new JSONObject(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Strips query strings from URLs before they reach debug logs (keys, tokens). */
    private static String sanitizeUrlForDebug(String url) {
        if (url == null) {
            return "";
        }
        int queryStart = url.indexOf('?');
        return queryStart >= 0 ? url.substring(0, queryStart) : url;
    }

    private JSONArray convertToolsToAnthropic(JSONArray openAiTools) {
        JSONArray anthropicTools = new JSONArray();
        for (int i = 0; i < openAiTools.length(); i++) {
            JSONObject openAiTool = openAiTools.optJSONObject(i);
            JSONObject function = openAiTool == null ? null : openAiTool.optJSONObject("function");
            if (function == null) {
                continue;
            }

            try {
                JSONObject anthropicTool = new JSONObject();
                anthropicTool.put("name", function.optString("name", ""));
                anthropicTool.put("description", function.optString("description", ""));
                anthropicTool.put("input_schema", function.optJSONObject("parameters") == null
                        ? new JSONObject().put("type", "object").put("properties", new JSONObject())
                        : function.optJSONObject("parameters"));
                anthropicTools.put(anthropicTool);
            } catch (Exception ignored) {
            }
        }
        return anthropicTools;
    }

    /**
     * Registra as ferramentas EFETIVAMENTE serializadas no payload final
     * enviado ao provider (toolIndex / toolName / toolType). A contagem vem do
     * array final — nunca de um número contado antes da serialização.
     */
    private void logProviderTools(StreamListener listener, JSONArray finalTools) {
        if (finalTools == null) {
            android.util.Log.d(TAG, "Tools count (final payload): 0");
            return;
        }
        android.util.Log.d(TAG, "Tools count (final payload): " + finalTools.length());
        int emitted = 0;
        for (int i = 0; i < finalTools.length(); i++) {
            JSONObject entry = finalTools.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            JSONArray declarations = entry.optJSONArray("functionDeclarations");
            if (declarations != null && declarations.length() > 0) {
                for (int j = 0; j < declarations.length(); j++) {
                    JSONObject decl = declarations.optJSONObject(j);
                    if (decl == null) {
                        continue;
                    }
                    android.util.Log.d(TAG, "toolIndex=" + emitted
                            + ", toolType=function"
                            + ", toolName=" + decl.optString("name", "?"));
                    emitted++;
                }
                continue;
            }
            String type = entry.optString("type", "?");
            JSONObject function = entry.optJSONObject("function");
            String name = function != null ? function.optString("name", "?")
                    : entry.optString("name", "?");
            android.util.Log.d(TAG, "toolIndex=" + emitted
                    + ", toolType=" + type
                    + ", toolName=" + name);
            emitted++;
        }
        emitDebug(listener, "ferramentas -> payload final com " + emitted
                + " tool(s) registradas");
    }

    private JSONArray convertToolsToGemini(JSONArray openAiTools) {
        JSONArray functionDeclarations = new JSONArray();
        for (int i = 0; i < openAiTools.length(); i++) {
            JSONObject openAiTool = openAiTools.optJSONObject(i);
            JSONObject function = openAiTool == null ? null : openAiTool.optJSONObject("function");
            if (function == null) {
                continue;
            }
            try {
                JSONObject declaration = new JSONObject();
                declaration.put("name", function.optString("name", ""));
                declaration.put("description", function.optString("description", ""));
                declaration.put("parameters", convertJsonSchemaToGemini(function.optJSONObject("parameters")));
                functionDeclarations.put(declaration);
            } catch (Exception ignored) {
            }
        }
        try {
            return new JSONArray().put(new JSONObject().put("functionDeclarations", functionDeclarations));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private JSONObject convertJsonSchemaToGemini(JSONObject schema) {
        JSONObject converted = new JSONObject();
        try {
            converted.put("type", "OBJECT");
            JSONObject properties = new JSONObject();
            JSONObject sourceProperties = schema == null ? null : schema.optJSONObject("properties");
            JSONArray propertyNames = sourceProperties == null ? null : sourceProperties.names();
            for (int i = 0; propertyNames != null && i < propertyNames.length(); i++) {
                String name = propertyNames.optString(i, "");
                JSONObject sourceProperty = sourceProperties.optJSONObject(name);
                JSONObject property = new JSONObject();
                property.put("type", geminiTypeName(sourceProperty == null ? "" : sourceProperty.optString("type", "")));
                property.put("description", sourceProperty == null ? "" : sourceProperty.optString("description", ""));
                if (sourceProperty != null) {
                    // Preserve schema details previously dropped in conversion.
                    JSONArray enumValues = sourceProperty.optJSONArray("enum");
                    if (enumValues != null) {
                        property.put("enum", enumValues);
                    }
                    JSONObject items = sourceProperty.optJSONObject("items");
                    if (items != null) {
                        JSONObject convertedItems = new JSONObject();
                        convertedItems.put("type", geminiTypeName(items.optString("type", "")));
                        if (items.optJSONObject("properties") != null) {
                            convertedItems = convertJsonSchemaToGemini(items);
                        }
                        property.put("items", convertedItems);
                    }
                }
                properties.put(name, property);
            }
            converted.put("properties", properties);
            JSONArray required = schema == null ? null : schema.optJSONArray("required");
            if (required != null) {
                converted.put("required", required);
            }
        } catch (Exception ignored) {
        }
        return converted;
    }

    private String geminiTypeName(String jsonSchemaType) {
        String normalized = jsonSchemaType == null ? "" : jsonSchemaType.trim().toLowerCase();
        if ("number".equals(normalized)) {
            return "NUMBER";
        }
        if ("integer".equals(normalized)) {
            return "INTEGER";
        }
        if ("boolean".equals(normalized)) {
            return "BOOLEAN";
        }
        if ("array".equals(normalized)) {
            return "ARRAY";
        }
        if ("object".equals(normalized)) {
            return "OBJECT";
        }
        return "STRING";
    }

    private String readStreamText(JSONObject jsonObject, String key) {
        if (jsonObject == null || key == null || !jsonObject.has(key) || jsonObject.isNull(key)) {
            return "";
        }
        return sanitizeStreamValue(jsonObject.opt(key));
    }

    private String sanitizeStreamValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        String text = String.valueOf(value);
        return "null".equalsIgnoreCase(text.trim()) ? "" : text;
    }

    private static void recordOpenAiUsage(JSONObject usage) {
        if (usage == null) return;
        long total = usage.optLong("total_tokens", 0);
        if (total <= 0) {
            total = usage.optLong("prompt_tokens", 0)
                    + usage.optLong("completion_tokens", 0);
        }
        TokenUsageStore.record(SketchApplication.getContext(), total);
    }

    /**
     * Forwards a provider-reported usage snapshot to the listener (item 17).
     * Keeps the pre-existing TokenUsageStore bookkeeping untouched; the
     * structured report is additive so the runtime can settle real usage.
     */
    /** OpenAI-family usage blocks (prompt/completion or input/output). */
    private static void emitOpenAiUsage(JSONObject usage, StreamListener listener) {
        if (usage == null) {
            return;
        }
        long input = usage.optLong("prompt_tokens", usage.optLong("input_tokens", 0));
        long output = usage.optLong("completion_tokens", usage.optLong("output_tokens", 0));
        JSONObject promptDetails = usage.optJSONObject("prompt_tokens_details");
        JSONObject completionDetails = usage.optJSONObject("completion_tokens_details");
        emitReportedUsage(listener, input, output,
                promptDetails == null ? 0 : promptDetails.optLong("cached_tokens", 0),
                0,
                completionDetails == null ? 0 : completionDetails.optLong("reasoning_tokens", 0));
    }

    private static void emitReportedUsage(StreamListener listener, long inputTokens,
                                          long outputTokens, long cachedInputTokens,
                                          long cacheWriteTokens, long reasoningTokens) {
        if (listener == null || (inputTokens <= 0 && outputTokens <= 0)) {
            return;
        }
        try {
            listener.onTokenUsage(pro.sketchware.chat.agentsdk.TokenUsage
                    .reportedDetailed(inputTokens, outputTokens, cachedInputTokens, reasoningTokens));
        } catch (Exception ignored) {
            // Usage reporting must never break the stream.
        }
    }

    private static void emitDebug(StreamListener listener, String message) {
        if (listener == null || message == null) {
            return;
        }
        String safeMessage = message.trim();
        if (safeMessage.isEmpty()) {
            return;
        }
        Log.d(TAG, safeMessage);
        listener.onDebug(safeMessage);
    }

    private static void emitProtocolPayload(StreamListener listener, String direction, String payload) {
        String safe = ChatFlowLogger.redact(payload == null ? "" : payload);
        emitDebug(listener, direction + " (" + safe.length() + " chars)\n" + safe);
        ChatFlowLogger.event("protocol", direction, safe);
    }

    static boolean isJsonResponse(Response response) {
        if (response == null) {
            return false;
        }
        String contentType = response.header("Content-Type", "").toLowerCase(java.util.Locale.US);
        return contentType.contains("application/json")
                && !contentType.contains("text/event-stream")
                && !contentType.contains("ndjson");
    }

    static boolean shouldParseAsJson(Response response, boolean requestedStreaming) {
        return response != null && StreamingCapabilityRegistry.shouldParseJsonBody(
                response.header("Content-Type", ""), requestedStreaming);
    }

    private static void requireResponseSource(Response response, String provider) throws IOException {
        if (response == null || response.body() == null) {
            throw new IOException(provider + " retornou uma resposta sem corpo.");
        }
    }

    private static String requestBodyText(Request request) {
        if (request == null || request.body() == null) return "";
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception error) {
            return "[body unavailable: " + error.getClass().getSimpleName() + "]";
        }
    }

    private String summarizeOpenAiChunk(JSONObject json, int chunkIndex) {
        JSONObject payload = null;
        JSONArray choices = json.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            payload = choices.optJSONObject(0).optJSONObject("delta");
        }
        boolean ollamaNativeMessage = false;
        if (payload == null) {
            payload = json.optJSONObject("message");
            ollamaNativeMessage = payload != null;
        }

        String content = "";
        String reasoning = "";
        if (payload != null) {
            content = readStreamText(payload, "content");
            reasoning = VoidPortExtractGrammar.readReasoningText(payload);
            if (reasoning.isEmpty() && ollamaNativeMessage) {
                reasoning = VoidPortExtractGrammar.readReasoningText(json);
            }
        } else if (json.has("content")) {
            content = readStreamText(json, "content");
            reasoning = VoidPortExtractGrammar.readReasoningText(json);
        }

        return "Chunk #" + chunkIndex
                + " -> contentChars=" + content.length()
                + ", reasoningChars=" + reasoning.length()
                + ", done=" + json.optBoolean("done", false)
                + (content.isEmpty() ? "" : ", content=\"" + previewForDebug(content) + "\"")
                + (reasoning.isEmpty() ? "" : ", thinking=\"" + previewForDebug(reasoning) + "\"");
    }

    private String previewForDebug(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() <= 72) {
            return compact;
        }
        return compact.substring(0, 72).trim() + "...";
    }

    private String buildHttpErrorMessage(String providerId, int statusCode, String errorBody) {
        String compactBody = errorBody == null ? "" : errorBody.trim();
        if (compactBody.length() > 400) {
            compactBody = compactBody.substring(0, 400).trim() + "...";
        }
        if (compactBody.isEmpty()) {
            return "API Error from " + providerId + ": HTTP " + statusCode;
        }
        return "API Error from " + providerId + ": HTTP " + statusCode + " - " + compactBody;
    }

    private ToolCallParseResult detectAndEmitToolCalls(
            String content,
            String reasoning,
            List<ToolCall> nativeCalls,
            JSONArray tools,
            StreamListener listener) {
        // v2 contract (Codex ResponseItem parity): when the caller declares
        // NATIVE_TOOL_CALLS_ONLY (the AgentRuntime gateway), the structured
        // envelope is the ONLY source of tool calls. Text passes through
        // untouched — nothing is mined from it and nothing text-derived is
        // re-emitted as onToolCall. Legacy chat listeners keep the
        // sanitizing detector for old integrations (explicit LEGACY path).
        if (listener instanceof EmissionTracker
                && ((EmissionTracker) listener).nativeToolCallsOnly) {
            // v2 contract (Codex ResponseItem parity): the structured envelope
            // IS the tool-call source. The result object also carries the calls
            // for callers that inspect it, but the runtime gateway collects ONLY
            // through StreamListener.onToolCall — so every native call MUST be
            // emitted here. Not emitting meant tool calls silently never reached
            // AgentRuntime in production (fix: native calls now reach the turn).
            emitToolCalls(nativeCalls, listener);
            return new ToolCallParseResult("native",
                    new ArrayList<>(nativeCalls), content, reasoning);
        }
        ToolCallParseResult result = toolCallDetector.detect(
                new ToolCallResponse(
                        content,
                        reasoning,
                        nativeCalls,
                        availableToolNames(tools)));
        emitToolCalls(result.getToolCalls(), listener);
        return result;
    }

    /** Emits provider-structured tool calls through the listener (native AND legacy paths). */
    static void emitToolCalls(List<ToolCall> calls, StreamListener listener) {
        if (calls == null || listener == null) {
            return;
        }
        for (ToolCall call : calls) {
            if (call != null && call.isValid()) {
                listener.onToolCall(
                        call.getName(),
                        call.getArguments(),
                        call.getId());
            }
        }
    }

    private List<String> availableToolNames(JSONArray tools) {
        List<String> names = new ArrayList<>();
        for (int i = 0; tools != null && i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            JSONObject function = tool == null ? null : tool.optJSONObject("function");
            String name = function == null ? "" : function.optString("name", "").trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private void appendOpenAiContentDelta(OpenAiStreamState state, String content, StreamListener listener) {
        if (content == null || content.isEmpty()) {
            return;
        }
        state.fullContent.append(content);
        listener.onContent(content);
    }

    private void appendAnthropicContentDelta(AnthropicStreamState state, String content, StreamListener listener) {
        if (content == null || content.isEmpty()) {
            return;
        }
        state.fullContent.append(content);
        listener.onContent(content);
    }

    private interface ResponseHandler {
        void handle(Call call, Response response) throws Exception;
    }

    private interface StreamingFallbackHandler {
        boolean handle(int statusCode, String errorBody);
    }
}
