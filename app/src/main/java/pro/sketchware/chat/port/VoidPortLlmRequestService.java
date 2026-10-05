package pro.sketchware.chat.port;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import pro.sketchware.chat.AiChatSettingsHelper;
import pro.sketchware.chat.SecurePrefs;
import pro.sketchware.chat.SslUtils;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderFamily;

/**
 * Small synchronous LLM client for ported non-chat features such as FIM
 * autocomplete, AI regex generation and local model utilities.
 */
public final class VoidPortLlmRequestService {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final OkHttpClient CLIENT = SslUtils.relaxedClientBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private VoidPortLlmRequestService() {
    }

    public static final class TextResult {
        public final String providerId;
        public final String modelName;
        public final String text;

        TextResult(String providerId, String modelName, String text) {
            this.providerId = providerId == null ? "" : providerId;
            this.modelName = modelName == null ? "" : modelName;
            this.text = text == null ? "" : text.trim();
        }
    }

    public static TextResult completeText(Context context, String systemPrompt, String userPrompt,
                                          int maxTokens, double temperature, List<String> stopTokens) throws Exception {
        if (context == null) {
            throw new Exception("Context unavailable.");
        }
        SharedPreferences prefs = SecurePrefs.of(context);
        String providerId = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "groq");
        String modelName = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "llama-3.1-8b-instant");
        ProviderConfig providerConfig = VoidPortLlmMessage.resolveProviderConfig(prefs, providerId);
        if (providerConfig == null) {
            throw new Exception("Unsupported provider: " + providerId);
        }
        if (!AiChatSettingsHelper.isProviderConfigured(prefs, providerId)) {
            throw new Exception("Provider not enabled or API key missing: " + providerId);
        }
        if (providerConfig.baseUrl.isEmpty()) {
            throw new Exception("Provider endpoint is missing: " + providerId);
        }

        String text = providerConfig.family == ProviderFamily.ANTHROPIC
                ? completeAnthropic(providerConfig, modelName, systemPrompt, userPrompt, maxTokens, temperature, stopTokens)
                : completeOpenAiCompatible(providerConfig, modelName, systemPrompt, userPrompt, maxTokens, temperature, stopTokens);
        return new TextResult(providerId, modelName, text);
    }

    private static String completeOpenAiCompatible(ProviderConfig providerConfig, String modelName,
                                                   String systemPrompt, String userPrompt, int maxTokens,
                                                   double temperature, List<String> stopTokens) throws Exception {
        JSONArray messages = new JSONArray();
        if (!safe(systemPrompt).isEmpty()) {
            messages.put(new JSONObject()
                    .put("role", VoidPortLlmMessage.instructionRole(
                            providerConfig.providerId, modelName))
                    .put("content", systemPrompt));
        }
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", safe(userPrompt)));

        JSONObject body = new JSONObject();
        VoidPortLlmMessage.putModelIfNeeded(body, providerConfig, modelName);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", temperature);
        body.put("max_tokens", Math.max(1, maxTokens));
        JSONArray stops = stopArray(stopTokens);
        if (stops.length() > 0) {
            body.put("stop", stops);
        }

        Request request = new Request.Builder()
                .url(VoidPortLlmMessage.resolveRequestUrl(providerConfig, modelName))
                .headers(buildOpenAiHeaders(providerConfig))
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("LLM request failed (" + response.code() + "): " + responseBody);
            }
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            JSONObject choice = choices == null || choices.length() == 0 ? null : choices.optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            String content = message == null ? "" : message.optString("content", "");
            if (content.isEmpty() && choice != null) {
                content = choice.optString("text", "");
            }
            return content.trim();
        }
    }

    private static String completeAnthropic(ProviderConfig providerConfig, String modelName,
                                            String systemPrompt, String userPrompt, int maxTokens,
                                            double temperature, List<String> stopTokens) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("max_tokens", Math.max(1, maxTokens));
        body.put("temperature", temperature);
        if (!safe(systemPrompt).isEmpty()) {
            body.put("system", systemPrompt);
        }
        body.put("messages", new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("content", safe(userPrompt))));
        JSONArray stops = stopArray(stopTokens);
        if (stops.length() > 0) {
            body.put("stop_sequences", stops);
        }

        Request request = new Request.Builder()
                .url(providerConfig.baseUrl)
                .headers(buildAnthropicHeaders(providerConfig))
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("LLM request failed (" + response.code() + "): " + responseBody);
            }
            JSONObject json = new JSONObject(responseBody);
            JSONArray content = json.optJSONArray("content");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; content != null && i < content.length(); i++) {
                JSONObject item = content.optJSONObject(i);
                if (item != null && "text".equals(item.optString("type", ""))) {
                    builder.append(item.optString("text", ""));
                }
            }
            return builder.toString().trim();
        }
    }

    private static Headers buildOpenAiHeaders(ProviderConfig providerConfig) {
        Headers.Builder headers = new Headers.Builder()
                .add("Content-Type", "application/json");
        if (!providerConfig.apiKey.isEmpty()) {
            headers.add("Authorization", "Bearer " + providerConfig.apiKey);
        }
        addExtraHeaders(headers, providerConfig.extraHeaders);
        return headers.build();
    }

    private static Headers buildAnthropicHeaders(ProviderConfig providerConfig) {
        Headers.Builder headers = new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("anthropic-version", "2023-06-01");
        if (!providerConfig.apiKey.isEmpty()) {
            headers.add("x-api-key", providerConfig.apiKey);
        }
        addExtraHeaders(headers, providerConfig.extraHeaders);
        return headers.build();
    }

    private static void addExtraHeaders(Headers.Builder headers, JSONObject extraHeaders) {
        JSONArray names = extraHeaders == null ? null : extraHeaders.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String key = names.optString(i, "");
            String value = extraHeaders.optString(key, "");
            if (!key.isEmpty() && !value.isEmpty()) {
                headers.set(key, value);
            }
        }
    }

    private static JSONArray stopArray(List<String> stopTokens) {
        JSONArray array = new JSONArray();
        if (stopTokens == null) {
            return array;
        }
        for (String stop : stopTokens) {
            if (stop != null && !stop.isEmpty()) {
                array.put(stop);
            }
        }
        return array;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
