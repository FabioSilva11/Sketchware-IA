package pro.sketchware.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
import pro.sketchware.SketchApplication;
import pro.sketchware.activities.chat.AiChatSettingsHelper;
import pro.sketchware.activities.chat.ContextBuilder;
import pro.sketchware.activities.chat.port.VoidPortExtractGrammar;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderFamily;

/**
 * Provider-aware AI service with OpenAI-compatible and Anthropic-specific
 * streaming paths plus retry and XML fallback support.
 */
public class AiProviderService {
    private static final String TAG = "AiProviderService";
    /** Matches Void chatThreadService {@code CHAT_RETRIES} / {@code RETRY_DELAY}. */
    private static final int MAX_PROVIDER_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2500L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private static AiProviderService instance;

    private final Context context;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private volatile Call currentStreamingCall;

    public interface StreamListener {
        void onContent(String delta);
        void onReasoning(String delta);
        void onToolCall(String name, String arguments, String id);
        void onFinalMessage(String fullContent, String fullReasoning);
        void onDebug(String message);
        void onError(String message, Throwable t);
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
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
        private final StringBuilder id = new StringBuilder();

        ToolCallAccumulator(int index) {
            this.index = index;
        }

        void appendId(String value) {
            appendIfPresent(id, value);
        }

        void appendName(String value) {
            appendIfPresent(name, value);
        }

        void appendArguments(String value) {
            appendIfPresent(arguments, value);
        }

        String getId() {
            String current = id.toString().trim();
            return current.isEmpty() ? "tool_" + index + "_" + UUID.randomUUID() : current;
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

        private void appendIfPresent(StringBuilder builder, String value) {
            if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
                return;
            }
            builder.append(value);
        }
    }

    private static final class AnthropicStreamState {
        final StringBuilder fullContent = new StringBuilder();
        final StringBuilder fullReasoning = new StringBuilder();
        final ToolCallAccumulator firstTool = new ToolCallAccumulator(0);
        int firstToolBlockIndex = -1;
        VoidPortExtractGrammar.XmlToolStreamParser xmlToolParser;
        String lastEmittedToolName = "";
        String lastEmittedToolArgs = "";
        String lastEmittedToolId = "";
    }

    private static final class OpenAiStreamState {
        final StringBuilder fullContent = new StringBuilder();
        final StringBuilder fullReasoning = new StringBuilder();
        final Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<>();
        VoidPortExtractGrammar.XmlToolStreamParser xmlToolParser;
        String lastEmittedToolName = "";
        String lastEmittedToolArgs = "";
        String lastEmittedToolId = "";
    }

    private AiProviderService() {
        this.context = SketchApplication.getContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized AiProviderService getInstance() {
        if (instance == null) {
            instance = new AiProviderService();
        }
        return instance;
    }

    public void sendStreamingMessage(ContextBuilder.Result requestContext, JSONArray tools, String chatMode, StreamListener listener) {
        SharedPreferences prefs = context.getSharedPreferences(AiChatSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE);
        AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
        String currentProvider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "groq");
        String currentModel = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "llama-3.1-8b-instant");

        ProviderConfig providerConfig = resolveProviderConfig(prefs, currentProvider);
        if (providerConfig == null) {
            listener.onError("Unsupported provider: " + currentProvider, null);
            return;
        }

        if (!AiChatSettingsHelper.isProviderConfigured(prefs, currentProvider)) {
            listener.onError("Provider not enabled or API key missing", null);
            return;
        }
        if (providerConfig.baseUrl.isEmpty()) {
            listener.onError("Provider endpoint is missing", null);
            return;
        }

        dispatchRequest(providerConfig, currentProvider, currentModel, requestContext, tools, chatMode, listener, 0);
    }

    public String sendTextMessage(String systemPrompt, String userPrompt) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences(AiChatSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE);
        AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
        String currentProvider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "groq");
        String currentModel = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "llama-3.1-8b-instant");

        ProviderConfig providerConfig = resolveProviderConfig(prefs, currentProvider);
        if (providerConfig == null) {
            throw new IOException("Unsupported provider: " + currentProvider);
        }
        if (!AiChatSettingsHelper.isProviderConfigured(prefs, currentProvider)) {
            throw new IOException("Provider not enabled or API key missing: " + currentProvider);
        }
        if (providerConfig.baseUrl.isEmpty()) {
            throw new IOException("Provider endpoint is missing: " + currentProvider);
        }

        Request request = providerConfig.family == ProviderFamily.ANTHROPIC
                ? buildAnthropicTextRequest(providerConfig, currentModel, systemPrompt, userPrompt)
                : providerConfig.family == ProviderFamily.GEMINI
                ? buildGeminiTextRequest(providerConfig, currentModel, systemPrompt, userPrompt)
                : buildOpenAiCompatibleTextRequest(providerConfig, currentProvider, currentModel, systemPrompt, userPrompt);

        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_PROVIDER_RETRIES; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    if (attempt < MAX_PROVIDER_RETRIES && shouldRetryForStatus(response.code(), attempt)) {
                        sleepBeforeBlockingRetry(attempt);
                        continue;
                    }
                    throw new IOException(buildHttpErrorMessage(currentProvider, response.code(), responseBody));
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
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_PROVIDER_RETRIES && shouldRetryForFailure(e, attempt)) {
                    sleepBeforeBlockingRetry(attempt);
                    continue;
                }
                throw e;
            } catch (Exception e) {
                throw new IOException("Error processing AI response", e);
            }
        }

        throw lastException != null ? lastException : new IOException("Unknown AI request error");
    }

    public void cancelCurrentStream() {
        Call call = currentStreamingCall;
        if (call != null) {
            call.cancel();
        }
        currentStreamingCall = null;
    }

    private void dispatchRequest(ProviderConfig providerConfig, String providerId, String modelName,
                                 ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                 StreamListener listener, int retryCount) {
        if (providerConfig.family == ProviderFamily.ANTHROPIC) {
            sendAnthropicStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode, listener, providerId, retryCount);
        } else if (providerConfig.family == ProviderFamily.GEMINI) {
            sendGeminiStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode, listener, providerId, retryCount);
        } else {
            sendOpenAiCompatibleStreamingRequest(providerConfig, modelName, requestContext, tools, chatMode, listener, providerId, retryCount);
        }
    }

    private void sendGeminiStreamingRequest(ProviderConfig providerConfig, String modelName,
                                            ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                            StreamListener listener, String providerId, int retryCount) {
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
            if (useNativeTools) {
                jsonBody.put("tools", convertToolsToGemini(tools));
            }

            HttpUrl url = HttpUrl.parse(providerConfig.baseUrl + "/models/" + modelName + ":streamGenerateContent")
                    .newBuilder()
                    .addQueryParameter("alt", "sse")
                    .addQueryParameter("key", providerConfig.apiKey)
                    .build();

            emitDebug(listener, "LLM request -> provider=" + providerId
                    + ", model=" + modelName
                    + ", endpoint=" + url);

            Request request = new Request.Builder()
                    .url(url)
                    .headers(buildGeminiHeaders(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();

            executeWithRetry(request, retryCount, providerId, listener, (call, response) -> {
                try (BufferedSource source = response.body().source()) {
                    readGeminiEventStream(source, requestContext, tools, listener);
                }
            });
        } catch (Exception e) {
            listener.onError("Request preparation error", e);
        }
    }

    private void sendOpenAiCompatibleStreamingRequest(ProviderConfig providerConfig, String modelName,
                                                      ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                                      StreamListener listener, String providerId, int retryCount) {
        try {
            JSONArray messages = new JSONArray();
            if (!TextUtils.isEmpty(requestContext.getSystemContext())) {
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", requestContext.getSystemContext()));
            }
            JSONArray history = requestContext.getMessages();
            for (int i = 0; i < history.length(); i++) {
                messages.put(history.get(i));
            }

            JSONObject jsonBody = new JSONObject();
            VoidPortLlmMessage.putModelIfNeeded(jsonBody, providerConfig, modelName);
            jsonBody.put("messages", messages);
            jsonBody.put("stream", true);
            if ("ollama".equals(providerId)) {
                // Ollama enables thinking by default for supported models.
                // Disable it in chat so the UI gets only the final answer content.
                jsonBody.put("think", false);
            }

            boolean useNativeTools = requestContext.getProviderFormat() == ContextBuilder.ProviderFormat.OPENAI
                    && VoidPortLlmMessage.shouldUseNativeTools(providerId, modelName, providerConfig)
                    && tools != null
                    && tools.length() > 0
                    && !"normal".equals(chatMode);
            if (useNativeTools) {
                jsonBody.put("tools", tools);
                if (!"ollama".equals(providerId)) {
                    jsonBody.put("tool_choice", "auto");
                }
            }

            String requestUrl = VoidPortLlmMessage.resolveRequestUrl(providerConfig, modelName);
            emitDebug(listener, "LLM request -> provider=" + providerId
                    + ", model=" + modelName
                    + ", endpoint=" + requestUrl);

            Request request = new Request.Builder()
                    .url(requestUrl)
                    .headers(buildOpenAiHeaders(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();

            executeWithRetry(request, retryCount, providerId, listener, (call, response) -> {
                String contentType = response.header("Content-Type", "");
                emitDebug(listener, "LLM response <- contentType=" + (contentType.isEmpty() ? "unknown" : contentType));
                if ("ollama".equals(providerId)) {
                    emitDebug(listener, "Response mode: stream (Ollama provider override)");
                    try (BufferedSource source = response.body().source()) {
                        readOpenAiEventStream(source, requestContext, tools, listener);
                    }
                    return;
                }
                if (contentType.contains("application/json")) {
                    emitDebug(listener, "Response mode: JSON");
                    String body = response.body() != null ? response.body().string() : "";
                    handleOpenAiJsonResponse(body, requestContext, tools, listener);
                    return;
                }
                try (BufferedSource source = response.body().source()) {
                    readOpenAiEventStream(source, requestContext, tools, listener);
                }
            });
        } catch (Exception e) {
            listener.onError("Request preparation error", e);
        }
    }

    private void sendAnthropicStreamingRequest(ProviderConfig providerConfig, String modelName,
                                               ContextBuilder.Result requestContext, JSONArray tools, String chatMode,
                                               StreamListener listener, String providerId, int retryCount) {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelName);
            jsonBody.put("messages", requestContext.getMessages());
            jsonBody.put("stream", true);
            jsonBody.put("max_tokens", VoidPortLlmMessage.maxOutputTokens(providerId, modelName));
            if (!TextUtils.isEmpty(requestContext.getSystemContext())) {
                jsonBody.put("system", requestContext.getSystemContext());
            }

            boolean useNativeTools = requestContext.getProviderFormat() == ContextBuilder.ProviderFormat.ANTHROPIC
                    && tools != null
                    && tools.length() > 0
                    && !"normal".equals(chatMode);
            if (useNativeTools) {
                jsonBody.put("tools", convertToolsToAnthropic(tools));
                jsonBody.put("tool_choice", new JSONObject().put("type", "auto"));
            }

            Request request = new Request.Builder()
                    .url(providerConfig.baseUrl)
                    .headers(buildAnthropicHeaders(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();

            executeWithRetry(request, retryCount, providerId, listener, (call, response) -> {
                try (BufferedSource source = response.body().source()) {
                    readAnthropicEventStream(source, tools, listener);
                }
            });
        } catch (Exception e) {
            listener.onError("Request preparation error", e);
        }
    }

    private void executeWithRetry(Request request, int retryCount, String providerId, StreamListener listener,
                                  ResponseHandler responseHandler) {
        final long requestStartedAt = SystemClock.elapsedRealtime();
        Call call = clientForProvider(providerId).newCall(request);
        currentStreamingCall = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call failedCall, IOException e) {
                if (currentStreamingCall == failedCall) {
                    currentStreamingCall = null;
                }
                if (failedCall.isCanceled()) {
                    listener.onError("cancelled", e);
                    return;
                }
                if (shouldRetryForFailure(e, retryCount)) {
                    scheduleRetry(request, retryCount, providerId, listener, responseHandler, -1);
                    return;
                }
                listener.onError(e.getMessage(), e);
            }

            @Override
            public void onResponse(Call respondedCall, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    if (currentStreamingCall == respondedCall) {
                        currentStreamingCall = null;
                    }
                    if (shouldRetryForStatus(response.code(), retryCount)) {
                        // For HTTP 429 (rate limit), honour the Retry-After header when present
                        // instead of retrying immediately. The header value is in seconds.
                        long retryAfterMs = -1;
                        if (response.code() == 429) {
                            String retryAfterHeader = response.header("Retry-After");
                            if (retryAfterHeader != null && !retryAfterHeader.trim().isEmpty()) {
                                try {
                                    long retryAfterSeconds = Long.parseLong(retryAfterHeader.trim());
                                    // Cap at 60 s to avoid stalling forever on absurdly large values.
                                    retryAfterMs = Math.min(retryAfterSeconds * 1000L, 60_000L);
                                } catch (NumberFormatException ignored) {
                                    // Non-numeric value (e.g. HTTP-date); use default backoff.
                                }
                            }
                            emitDebug(listener, "HTTP 429 rate-limit from " + providerId
                                    + (retryAfterMs > 0 ? ", Retry-After=" + (retryAfterMs / 1000) + "s" : ", using default backoff"));
                        }
                        scheduleRetry(request, retryCount, providerId, listener, responseHandler, retryAfterMs);
                        return;
                    }
                    listener.onError(buildHttpErrorMessage(providerId, response.code(), errorBody), null);
                    return;
                }

                emitDebug(listener, "HTTP " + response.code()
                        + " em " + (SystemClock.elapsedRealtime() - requestStartedAt) + "ms");

                try (Response safeResponse = response) {
                    responseHandler.handle(respondedCall, safeResponse);
                } catch (Exception e) {
                    if (shouldRetryForFailure(e, retryCount)) {
                        scheduleRetry(request, retryCount, providerId, listener, responseHandler, -1);
                        return;
                    }
                    listener.onError("Stream reading error", e);
                    return;
                } finally {
                    if (currentStreamingCall == respondedCall) {
                        currentStreamingCall = null;
                    }
                }
            }
        });
    }

    /**
     * Schedules a retry with back-off.
     *
     * @param retryAfterMs explicit delay in milliseconds from a {@code Retry-After} header,
     *                     or {@code -1} to use the default exponential back-off.
     */
    private void scheduleRetry(Request request, int retryCount, String providerId, StreamListener listener,
                               ResponseHandler responseHandler, long retryAfterMs) {
        if (retryCount >= MAX_PROVIDER_RETRIES) {
            listener.onError("Request failed after retries for provider: " + providerId, null);
            return;
        }
        long delayMs = retryAfterMs > 0 ? retryAfterMs : RETRY_DELAY_MS * (retryCount + 1L);
        mainHandler.postDelayed(() -> executeWithRetry(request, retryCount + 1, providerId, listener, responseHandler),
                delayMs);
    }

    // Keep the old two-arg overload so call-sites that pass no retryAfterMs still compile.
    private void scheduleRetry(Request request, int retryCount, String providerId, StreamListener listener,
                               ResponseHandler responseHandler) {
        scheduleRetry(request, retryCount, providerId, listener, responseHandler, -1);
    }

    private OkHttpClient clientForProvider(String providerId) {
        SharedPreferences prefs = context.getSharedPreferences(AiChatSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE);
        JSONObject custom = pro.sketchware.activities.chat.port.VoidPortSettings.getProviderConfigObject(prefs, providerId);
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
        configureXmlParser(state, tools);
        StreamPerf perf = new StreamPerf();
        String line;
        int chunkCount = 0;
        boolean loggedFormat = false;
        while ((line = source.readUtf8Line()) != null) {
            String trimmedLine = line == null ? "" : line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            String data;
            if (trimmedLine.startsWith("data:")) {
                data = trimmedLine.substring(5).trim();
                if (!loggedFormat) {
                    emitDebug(listener, "Stream mode: SSE");
                    loggedFormat = true;
                }
            } else if (trimmedLine.startsWith("event:") || trimmedLine.startsWith(":")) {
                continue;
            } else {
                data = trimmedLine;
                if (!loggedFormat) {
                    emitDebug(listener, "Stream mode: NDJSON");
                    loggedFormat = true;
                }
            }
            if (data.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(data)) {
                break;
            }

            chunkCount++;
            perf.onChunk(listener);
            perf.onProgress(listener, chunkCount);
            try {
                JSONObject chunk = new JSONObject(data);
                if (chunkCount <= 4) {
                    emitDebug(listener, summarizeOpenAiChunk(chunk, chunkCount));
                }
                handleOpenAiChunk(chunk, state, listener);
            } catch (Exception e) {
                emitDebug(listener, "Chunk parse error #" + chunkCount + ": " + previewForDebug(data));
                Log.e(TAG, "Error parsing stream chunk: " + data, e);
            }
        }

        perf.finish(listener, state.fullContent.length(), state.fullReasoning.length());
        emitDebug(listener, "Stream finished: chunks=" + chunkCount
                + ", contentChars=" + state.fullContent.length()
                + ", reasoningChars=" + state.fullReasoning.length());
        completeOpenAiRequest(state, requestContext, tools, listener);
    }

    private void handleOpenAiJsonResponse(String body, ContextBuilder.Result requestContext,
                                          JSONArray tools, StreamListener listener) {
        OpenAiStreamState state = new OpenAiStreamState();
        configureXmlParser(state, tools);
        try {
            JSONObject json = new JSONObject(body);
            JSONArray choices = json.optJSONArray("choices");
            JSONObject firstChoice = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
            JSONObject message = firstChoice != null ? firstChoice.optJSONObject("message") : json.optJSONObject("message");
            
            if (message != null) {
                String content = sanitizeStreamValue(message.opt("content"));
                if (!content.isEmpty()) {
                    appendOpenAiContentDelta(state, content, listener);
                }

                // Check for reasoning/thinking inside message (DeepSeek style) or at top level (some Ollama proxies)
                String reasoning = VoidPortExtractGrammar.readReasoningText(message);
                if (reasoning.isEmpty()) {
                    reasoning = VoidPortExtractGrammar.readReasoningText(json);
                }
                
                if (!reasoning.isEmpty()) {
                    state.fullReasoning.append(reasoning);
                    listener.onReasoning(reasoning);
                }

                JSONArray toolCalls = message.optJSONArray("tool_calls");
                appendOpenAiToolCalls(toolCalls, state);
            } else if (json.has("content")) {
                // Fallback for simple content field at top level
                String content = sanitizeStreamValue(json.opt("content"));
                if (!content.isEmpty()) {
                    appendOpenAiContentDelta(state, content, listener);
                }
                String reasoning = VoidPortExtractGrammar.readReasoningText(json);
                if (!reasoning.isEmpty()) {
                    state.fullReasoning.append(reasoning);
                    listener.onReasoning(reasoning);
                }
            }
        } catch (Exception e) {
            listener.onError("Failed to parse response body", e);
            return;
        }

        emitDebug(listener, "JSON response parsed: contentChars=" + state.fullContent.length()
                + ", reasoningChars=" + state.fullReasoning.length());
        completeOpenAiRequest(state, requestContext, tools, listener);
    }

    private void handleOpenAiChunk(JSONObject json, OpenAiStreamState state, StreamListener listener) {
        JSONArray choices = json.optJSONArray("choices");
        JSONObject delta = null;
        if (choices != null && choices.length() > 0) {
            delta = choices.optJSONObject(0).optJSONObject("delta");
        }
        
        // If no delta (OpenAI style), check for message (Ollama native style)
        if (delta == null) {
            delta = json.optJSONObject("message");
        }

        if (delta != null) {
            String content = readStreamText(delta, "content");
            if (!content.isEmpty()) {
                appendOpenAiContentDelta(state, content, listener);
            }

            String reasoning = VoidPortExtractGrammar.readReasoningText(delta);
            if (reasoning.isEmpty() && delta == json.optJSONObject("message")) {
                // If it's Ollama native, thinking might be at top level of chunk
                reasoning = VoidPortExtractGrammar.readReasoningText(json);
            }
            if (!reasoning.isEmpty()) {
                state.fullReasoning.append(reasoning);
                listener.onReasoning(reasoning);
            }

            appendOpenAiToolCalls(delta.optJSONArray("tool_calls"), state);
        } else if (json.has("content")) {
            // Very simple fallback
            String content = readStreamText(json, "content");
            if (!content.isEmpty()) {
                appendOpenAiContentDelta(state, content, listener);
            }
            String reasoning = VoidPortExtractGrammar.readReasoningText(json);
            if (!reasoning.isEmpty()) {
                state.fullReasoning.append(reasoning);
                listener.onReasoning(reasoning);
            }
        }
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

            int index = toolCall.optInt("index", i);
            if (index != 0) {
                continue;
            }

            ToolCallAccumulator accumulator = state.toolCalls.get(0);
            if (accumulator == null) {
                accumulator = new ToolCallAccumulator(0);
                state.toolCalls.put(0, accumulator);
            }

            accumulator.appendId(sanitizeStreamValue(toolCall.opt("id")));
            JSONObject function = toolCall.optJSONObject("function");
            if (function != null) {
                accumulator.appendName(sanitizeStreamValue(function.opt("name")));
                accumulator.appendArguments(sanitizeStreamValue(function.opt("arguments")));
            }
        }

        // Tool arguments arrive in chunks on most OpenAI-compatible streams.
        // Emit only after the stream finishes so AgentManager receives one complete JSON payload.
    }

    private ToolCallAccumulator firstReadyOpenAiTool(OpenAiStreamState state) {
        ToolCallAccumulator accumulator = state.toolCalls.get(0);
        if (accumulator != null && (accumulator.isReady() || accumulator.hasAnyPayload())) {
            return accumulator;
        }
        return null;
    }

    private void completeOpenAiRequest(OpenAiStreamState state, ContextBuilder.Result requestContext,
                                       JSONArray tools, StreamListener listener) {
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
        ToolCallAccumulator firstTool = firstReadyOpenAiTool(state);
        boolean hasNativeTool = firstTool != null && firstTool.isReady();
        boolean hasXmlTool = false;

        if (hasNativeTool) {
            maybeEmitToolCall(firstTool.getName(), firstTool.getArguments(), firstTool.getId(), state, listener);
        } else {
            VoidPortExtractGrammar.ToolCallExtraction extraction = state.xmlToolParser == null
                    ? null
                    : state.xmlToolParser.getLatestToolCall();
            if (extraction != null) {
                finalContent = trimEnd(state.xmlToolParser.getVisibleText());
                state.fullContent.setLength(0);
                state.fullContent.append(finalContent);
            }
            if (extraction == null) {
                extraction = VoidPortExtractGrammar.extractXmlToolCall(finalContent, tools);
            }
            boolean extractedFromReasoning = false;
            if (extraction == null && finalContent.trim().isEmpty() && !finalReasoning.trim().isEmpty()) {
                extraction = VoidPortExtractGrammar.extractXmlToolCall(finalReasoning, tools);
                extractedFromReasoning = extraction != null;
            }
            if (extraction != null) {
                hasXmlTool = true;
                if (extractedFromReasoning) {
                    state.fullReasoning.setLength(0);
                    state.fullReasoning.append(extraction.cleanedContent);
                } else {
                    finalContent = extraction.cleanedContent;
                }
                maybeEmitToolCall(extraction.toolName, extraction.toolArguments, extraction.toolId, state, listener);
            }
        }

        if (finalContent.trim().isEmpty() && state.fullReasoning.toString().trim().isEmpty()
                && !hasNativeTool && !hasXmlTool) {
            emitDebug(listener, "Final assistant payload was empty");
            listener.onError("Void-style provider response was empty.", null);
            return;
        }
        emitDebug(listener, "Final assistant payload: contentChars=" + finalContent.length()
                + ", reasoningChars=" + state.fullReasoning.length()
                + ", hasToolCall=" + (hasNativeTool || hasXmlTool));
        listener.onFinalMessage(finalContent, state.fullReasoning.toString());
    }

    private void readGeminiEventStream(BufferedSource source, ContextBuilder.Result requestContext,
                                       JSONArray tools, StreamListener listener) throws IOException {
        OpenAiStreamState state = new OpenAiStreamState();
        configureXmlParser(state, tools);
        StreamPerf perf = new StreamPerf();
        String line;
        int chunkCount = 0;
        while ((line = source.readUtf8Line()) != null) {
            String trimmedLine = line == null ? "" : line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("event:") || trimmedLine.startsWith(":")) {
                continue;
            }
            String data = trimmedLine.startsWith("data:")
                    ? trimmedLine.substring(5).trim()
                    : trimmedLine;
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }
            chunkCount++;
            perf.onChunk(listener);
            perf.onProgress(listener, chunkCount);
            try {
                JSONObject chunk = new JSONObject(data);
                handleGeminiChunk(chunk, state, listener);
            } catch (Exception e) {
                emitDebug(listener, "Gemini chunk parse error #" + chunkCount + ": " + previewForDebug(data));
                Log.e(TAG, "Error parsing Gemini stream chunk: " + data, e);
            }
        }
        perf.finish(listener, state.fullContent.length(), state.fullReasoning.length());
        emitDebug(listener, "Gemini stream finished: chunks=" + chunkCount
                + ", contentChars=" + state.fullContent.length()
                + ", reasoningChars=" + state.fullReasoning.length());
        completeOpenAiRequest(state, requestContext, tools, listener);
    }

    private void handleGeminiChunk(JSONObject chunk, OpenAiStreamState state, StreamListener listener) {
        JSONArray candidates = chunk.optJSONArray("candidates");
        for (int i = 0; candidates != null && i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
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
                    ToolCallAccumulator accumulator = state.toolCalls.get(0);
                    if (accumulator == null) {
                        accumulator = new ToolCallAccumulator(0);
                        state.toolCalls.put(0, accumulator);
                    }
                    if (accumulator.hasAnyPayload()) {
                        continue;
                    }
                    accumulator.appendName(functionCall.optString("name", ""));
                    accumulator.appendArguments(functionCall.optJSONObject("args") == null
                            ? "{}"
                            : functionCall.optJSONObject("args").toString());
                    accumulator.appendId(functionCall.optString("id", ""));
                }
            }
        }
    }

    private Request buildOpenAiCompatibleTextRequest(ProviderConfig providerConfig, String providerId,
                                                     String modelName, String systemPrompt, String userPrompt) throws IOException {
        try {
            JSONArray messages = new JSONArray();
            if (!TextUtils.isEmpty(systemPrompt)) {
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", systemPrompt));
            }
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", userPrompt == null ? "" : userPrompt));

            JSONObject jsonBody = new JSONObject();
            VoidPortLlmMessage.putModelIfNeeded(jsonBody, providerConfig, modelName);
            jsonBody.put("messages", messages);
            jsonBody.put("stream", false);
            if ("ollama".equals(providerId)) {
                jsonBody.put("think", false);
            }

            return new Request.Builder()
                    .url(VoidPortLlmMessage.resolveRequestUrl(providerConfig, modelName))
                    .headers(buildOpenAiHeaders(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();
        } catch (Exception e) {
            throw new IOException("Request preparation error", e);
        }
    }

    private Request buildAnthropicTextRequest(ProviderConfig providerConfig, String modelName,
                                              String systemPrompt, String userPrompt) throws IOException {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelName);
            jsonBody.put("max_tokens", VoidPortLlmMessage.maxOutputTokens("anthropic", modelName));
            if (!TextUtils.isEmpty(systemPrompt)) {
                jsonBody.put("system", systemPrompt);
            }
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", userPrompt == null ? "" : userPrompt));
            jsonBody.put("messages", messages);

            return new Request.Builder()
                    .url(providerConfig.baseUrl)
                    .headers(buildAnthropicHeaders(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();
        } catch (Exception e) {
            throw new IOException("Request preparation error", e);
        }
    }

    private Request buildGeminiTextRequest(ProviderConfig providerConfig, String modelName,
                                           String systemPrompt, String userPrompt) throws IOException {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("contents", new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject()
                            .put("text", userPrompt == null ? "" : userPrompt)))));
            if (!TextUtils.isEmpty(systemPrompt)) {
                jsonBody.put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("text", systemPrompt))));
            }

            HttpUrl url = HttpUrl.parse(providerConfig.baseUrl + "/models/" + modelName + ":generateContent")
                    .newBuilder()
                    .addQueryParameter("key", providerConfig.apiKey)
                    .build();

            return new Request.Builder()
                    .url(url)
                    .headers(buildGeminiHeaders(providerConfig))
                    .post(RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE))
                    .build();
        } catch (Exception e) {
            throw new IOException("Request preparation error", e);
        }
    }

    private String parseOpenAiCompatibleTextResponse(String body) throws IOException {
        try {
            JSONObject json = new JSONObject(body);
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

    private void sleepBeforeBlockingRetry(int retryCount) {
        try {
            Thread.sleep(RETRY_DELAY_MS * (retryCount + 1L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void readAnthropicEventStream(BufferedSource source, JSONArray tools,
                                          StreamListener listener) throws IOException {
        AnthropicStreamState state = new AnthropicStreamState();
        configureXmlParser(state, tools);
        String currentEvent = "";
        StringBuilder dataBuffer = new StringBuilder();
        String line;

        while ((line = source.readUtf8Line()) != null) {
            if (line.startsWith("event:")) {
                currentEvent = line.substring(6).trim();
                continue;
            }
            if (line.startsWith("data:")) {
                if (dataBuffer.length() > 0) {
                    dataBuffer.append('\n');
                }
                dataBuffer.append(line.substring(5).trim());
                continue;
            }
            if (line.isEmpty()) {
                dispatchAnthropicEvent(currentEvent, dataBuffer.toString(), state, listener);
                currentEvent = "";
                dataBuffer.setLength(0);
            }
        }

        if (dataBuffer.length() > 0) {
            dispatchAnthropicEvent(currentEvent, dataBuffer.toString(), state, listener);
        }

        boolean hasNativeTool = !state.firstTool.getName().isEmpty();
        boolean hasXmlTool = false;

        if (hasNativeTool) {
            maybeEmitAnthropicToolCall(state.firstTool.getName(), state.firstTool.getArguments(), state.firstTool.getId(), state, listener);
        } else {
            String finalContent = state.fullContent.toString();
            String finalReasoning = state.fullReasoning.toString();
            VoidPortExtractGrammar.ToolCallExtraction extraction = state.xmlToolParser == null
                    ? null
                    : state.xmlToolParser.getLatestToolCall();
            if (extraction != null) {
                finalContent = trimEnd(state.xmlToolParser.getVisibleText());
                state.fullContent.setLength(0);
                state.fullContent.append(finalContent);
            }
            if (extraction == null) {
                extraction = VoidPortExtractGrammar.extractXmlToolCall(finalContent, tools);
            }
            boolean extractedFromReasoning = false;
            if (extraction == null && finalContent.trim().isEmpty() && !finalReasoning.trim().isEmpty()) {
                extraction = VoidPortExtractGrammar.extractXmlToolCall(finalReasoning, tools);
                extractedFromReasoning = extraction != null;
            }
            if (extraction != null) {
                hasXmlTool = true;
                if (extractedFromReasoning) {
                    state.fullReasoning.setLength(0);
                    state.fullReasoning.append(extraction.cleanedContent);
                } else {
                    state.fullContent.setLength(0);
                    state.fullContent.append(extraction.cleanedContent);
                }
                maybeEmitAnthropicToolCall(extraction.toolName, extraction.toolArguments, extraction.toolId, state, listener);
            }
        }

        if (state.fullContent.toString().trim().isEmpty()
                && state.fullReasoning.toString().trim().isEmpty()
                && !hasNativeTool
                && !hasXmlTool) {
            listener.onError("Anthropic response was empty.", null);
            return;
        }
        listener.onFinalMessage(state.fullContent.toString(), state.fullReasoning.toString());
    }

    private void dispatchAnthropicEvent(String eventName, String data,
                                        AnthropicStreamState state, StreamListener listener) {
        if (data == null || data.trim().isEmpty() || "[DONE]".equals(data.trim())) {
            return;
        }
        try {
            JSONObject json = new JSONObject(data);
            String type = json.optString("type", eventName == null ? "" : eventName);

            if ("error".equals(type)) {
                JSONObject error = json.optJSONObject("error");
                throw new IOException(error == null ? "Anthropic stream error" : error.toString());
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
                    if (!state.firstTool.hasAnyPayload()) {
                        state.firstToolBlockIndex = blockIndex;
                        state.firstTool.appendId(block.optString("id", ""));
                        state.firstTool.appendName(block.optString("name", ""));
                    }
                }
                return;
            }

            if ("content_block_delta".equals(type)) {
                int blockIndex = json.optInt("index", state.firstToolBlockIndex);
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
                    if (state.firstToolBlockIndex < 0 || blockIndex == state.firstToolBlockIndex) {
                        state.firstTool.appendArguments(delta.optString("partial_json", ""));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Headers buildOpenAiHeaders(ProviderConfig providerConfig) {
        Headers.Builder headers = new Headers.Builder();
        headers.add("Content-Type", "application/json");
        if (!providerConfig.apiKey.isEmpty()) {
            headers.add("Authorization", "Bearer " + providerConfig.apiKey);
        }
        addExtraHeaders(headers, providerConfig.extraHeaders);
        return headers.build();
    }

    private Headers buildAnthropicHeaders(ProviderConfig providerConfig) {
        Headers.Builder headers = new Headers.Builder();
        headers.add("Content-Type", "application/json");
        headers.add("x-api-key", providerConfig.apiKey);
        headers.add("anthropic-version", "2023-06-01");
        addExtraHeaders(headers, providerConfig.extraHeaders);
        return headers.build();
    }

    private Headers buildGeminiHeaders(ProviderConfig providerConfig) {
        Headers.Builder headers = new Headers.Builder();
        headers.add("Content-Type", "application/json");
        addExtraHeaders(headers, providerConfig.extraHeaders);
        return headers.build();
    }

    private void addExtraHeaders(Headers.Builder headers, JSONObject extraHeaders) {
        JSONArray headerNames = extraHeaders == null ? null : extraHeaders.names();
        for (int i = 0; headerNames != null && i < headerNames.length(); i++) {
            String name = headerNames.optString(i, "");
            if (name.isEmpty()) {
                continue;
            }
            headers.add(name, extraHeaders.optString(name, ""));
        }
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

    private ProviderConfig resolveProviderConfig(SharedPreferences prefs, String providerId) {
        return VoidPortLlmMessage.resolveProviderConfig(prefs, providerId);
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

    private boolean shouldRetryForStatus(int statusCode, int retryCount) {
        return retryCount < MAX_PROVIDER_RETRIES
                && (statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500);
    }

    private boolean shouldRetryForFailure(Throwable throwable, int retryCount) {
        return retryCount < MAX_PROVIDER_RETRIES && throwable instanceof IOException;
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

    private void configureXmlParser(OpenAiStreamState state, JSONArray tools) {
        if (state == null || tools == null || tools.length() == 0) {
            return;
        }
        VoidPortExtractGrammar.XmlToolStreamParser parser = new VoidPortExtractGrammar.XmlToolStreamParser(tools);
        if (parser.isEnabled()) {
            state.xmlToolParser = parser;
        }
    }

    private void configureXmlParser(AnthropicStreamState state, JSONArray tools) {
        if (state == null || tools == null || tools.length() == 0) {
            return;
        }
        VoidPortExtractGrammar.XmlToolStreamParser parser = new VoidPortExtractGrammar.XmlToolStreamParser(tools);
        if (parser.isEnabled()) {
            state.xmlToolParser = parser;
        }
    }

    private void appendOpenAiContentDelta(OpenAiStreamState state, String content, StreamListener listener) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (state.xmlToolParser == null) {
            state.fullContent.append(content);
            listener.onContent(content);
            return;
        }
        VoidPortExtractGrammar.XmlToolStreamStep step = state.xmlToolParser.accept(content);
        state.fullContent.setLength(0);
        state.fullContent.append(step.visibleText);
        if (!step.visibleDelta.isEmpty()) {
            listener.onContent(step.visibleDelta);
        }
        if (step.toolCall != null) {
            maybeEmitToolCall(step.toolCall.toolName, step.toolCall.toolArguments, step.toolCall.toolId, state, listener);
        }
    }

    private void appendAnthropicContentDelta(AnthropicStreamState state, String content, StreamListener listener) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (state.xmlToolParser == null) {
            state.fullContent.append(content);
            listener.onContent(content);
            return;
        }
        VoidPortExtractGrammar.XmlToolStreamStep step = state.xmlToolParser.accept(content);
        state.fullContent.setLength(0);
        state.fullContent.append(step.visibleText);
        if (!step.visibleDelta.isEmpty()) {
            listener.onContent(step.visibleDelta);
        }
        if (step.toolCall != null) {
            maybeEmitAnthropicToolCall(step.toolCall.toolName, step.toolCall.toolArguments, step.toolCall.toolId, state, listener);
        }
    }

    private String trimEnd(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private void maybeEmitToolCall(String name, String arguments, String id, OpenAiStreamState state, StreamListener listener) {
        String safeName = name == null ? "" : name.trim();
        String safeArguments = arguments == null || arguments.trim().isEmpty() ? "{}" : arguments.trim();
        String safeId = id == null || id.trim().isEmpty() ? "tool_" + UUID.randomUUID() : id.trim();
        if (safeName.isEmpty()) {
            return;
        }
        if (safeName.equals(state.lastEmittedToolName)
                && safeArguments.equals(state.lastEmittedToolArgs)
                && safeId.equals(state.lastEmittedToolId)) {
            return;
        }
        state.lastEmittedToolName = safeName;
        state.lastEmittedToolArgs = safeArguments;
        state.lastEmittedToolId = safeId;
        listener.onToolCall(safeName, safeArguments, safeId);
    }

    private void maybeEmitAnthropicToolCall(String name, String arguments, String id,
                                            AnthropicStreamState state, StreamListener listener) {
        String safeName = name == null ? "" : name.trim();
        String safeArguments = arguments == null || arguments.trim().isEmpty() ? "{}" : arguments.trim();
        String safeId = id == null || id.trim().isEmpty() ? "tool_" + UUID.randomUUID() : id.trim();
        if (safeName.isEmpty()) {
            return;
        }
        if (safeName.equals(state.lastEmittedToolName)
                && safeArguments.equals(state.lastEmittedToolArgs)
                && safeId.equals(state.lastEmittedToolId)) {
            return;
        }
        state.lastEmittedToolName = safeName;
        state.lastEmittedToolArgs = safeArguments;
        state.lastEmittedToolId = safeId;
        listener.onToolCall(safeName, safeArguments, safeId);
    }

    private interface ResponseHandler {
        void handle(Call call, Response response) throws Exception;
    }
}
