package pro.sketchware.chat.port;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pro.sketchware.chat.SslUtils;

/**
 * Android port of common/refreshModelService.ts for local model discovery.
 */
public final class VoidPortRefreshModelService {
    private static final long REFRESH_INTERVAL_MS = 5_000L;
    private static final OkHttpClient CLIENT = SslUtils.relaxedClientBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean autoRefreshStarted = false;

    private VoidPortRefreshModelService() {
    }

    public enum RefreshState {
        INIT,
        REFRESHING,
        FINISHED,
        ERROR
    }

    public static final class RefreshResult {
        public final String providerId;
        public final RefreshState state;
        public final List<String> models;
        public final String error;

        RefreshResult(String providerId, RefreshState state, List<String> models, String error) {
            this.providerId = providerId == null ? "" : providerId;
            this.state = state == null ? RefreshState.INIT : state;
            this.models = models == null ? new ArrayList<>() : models;
            this.error = error == null ? "" : error;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("providerId", providerId);
                obj.put("state", state.name().toLowerCase(java.util.Locale.US));
                obj.put("models", new JSONArray(models));
                obj.put("error", error);
            } catch (Exception ignored) {
            }
            return obj;
        }
    }

    public interface Listener {
        void onResult(RefreshResult result);
    }

    public static void startAutoRefresh(Context context) {
        if (context == null || autoRefreshStarted) {
            return;
        }
        autoRefreshStarted = true;
        Context appContext = context.getApplicationContext();
        MAIN.post(() -> scheduleAutoRefresh(appContext));
    }

    public static void refreshProviderAsync(Context context, String providerId, boolean enableProviderOnSuccess,
                                            Listener listener) {
        Context appContext = context == null ? null : context.getApplicationContext();
        new Thread(() -> {
            RefreshResult result = refreshProvider(appContext, providerId, enableProviderOnSuccess);
            if (listener != null) {
                MAIN.post(() -> listener.onResult(result));
            }
        }, "void-refresh-models-" + providerId).start();
    }

    public static RefreshResult refreshProvider(Context context, String providerId, boolean enableProviderOnSuccess) {
        if (context == null) {
            return new RefreshResult(providerId, RefreshState.ERROR, null, "Context unavailable.");
        }
        String normalizedProvider = normalizeProvider(providerId);
        if (normalizedProvider.isEmpty()) {
            return new RefreshResult(providerId, RefreshState.ERROR, null, "Provider not supported.");
        }
        try {
            SharedPreferences prefs = VoidPortSettings.prefs(context);
            List<String> models = fetchModelsForProvider(prefs, normalizedProvider);
            if (enableProviderOnSuccess && !models.isEmpty()) {
                prefs.edit().putString(VoidPortSettings.PREF_CURRENT_PROVIDER, normalizedProvider).apply();
            }
            return new RefreshResult(normalizedProvider, RefreshState.FINISHED, models, "");
        } catch (Exception e) {
            return new RefreshResult(normalizedProvider, RefreshState.ERROR, null, e.getMessage());
        }
    }

    public static JSONArray refreshAll(Context context, boolean enableProviderOnSuccess) {
        JSONArray array = new JSONArray();
        for (String provider : new String[]{"ollama", "vllm", "lm_studio"}) {
            array.put(refreshProvider(context, provider, enableProviderOnSuccess).toJson());
        }
        return array;
    }

    private static void scheduleAutoRefresh(Context context) {
        SharedPreferences prefs = VoidPortSettings.prefs(context);
        if (prefs.getBoolean(VoidPortSettings.PREF_AUTO_REFRESH_MODELS, true)) {
            new Thread(() -> {
                for (String provider : new String[]{"ollama", "vllm", "lm_studio"}) {
                    refreshProvider(context, provider, true);
                }
                MAIN.postDelayed(() -> scheduleAutoRefresh(context), REFRESH_INTERVAL_MS);
            }, "void-auto-refresh-models").start();
        } else {
            MAIN.postDelayed(() -> scheduleAutoRefresh(context), REFRESH_INTERVAL_MS);
        }
    }

    private static List<String> fetchOllamaModels(String baseUrl) throws Exception {
        JSONObject json = fetchJson(trimTrailingSlash(baseUrl) + "/api/tags");
        JSONArray models = json.optJSONArray("models");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; models != null && i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            String name = model == null ? "" : model.optString("name", "");
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private static List<String> fetchOpenAiCompatibleModels(String baseUrl) throws Exception {
        JSONObject json = fetchJson(modelListUrl(baseUrl));
        JSONArray data = json.optJSONArray("data");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject model = data.optJSONObject(i);
            String id = model == null ? "" : model.optString("id", "");
            if (!id.isEmpty()) {
                names.add(id);
            }
        }
        return new ArrayList<>(names);
    }

    private static List<String> fetchGeminiModels(SharedPreferences prefs) throws Exception {
        VoidPortLlmMessage.ProviderConfig config = VoidPortLlmMessage.resolveProviderConfig(prefs, "gemini");
        if (config == null || config.apiKey.isEmpty()) {
            throw new Exception("Chave da API Gemini não configurada");
        }
        String base = trimTrailingSlash(config.baseUrl);
        Request request = new Request.Builder()
                .url(base + "/models")
                .header("x-goog-api-key", config.apiKey.trim())
                .get()
                .build();
        JSONObject json = fetchJson(request);
        JSONArray models = json.optJSONArray("models");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; models != null && i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            JSONArray methods = model == null ? null : model.optJSONArray("supportedGenerationMethods");
            boolean supported = methods == null;
            for (int j = 0; methods != null && j < methods.length(); j++) {
                String method = methods.optString(j, "");
                if ("generateContent".equals(method) || "embedContent".equals(method)) {
                    supported = true;
                    break;
                }
            }
            if (!supported) continue;
            String name = model == null ? "" : model.optString("name", "");
            if (name.startsWith("models/")) name = name.substring("models/".length());
            if (!name.isEmpty()) names.add(name);
        }
        return new ArrayList<>(names);
    }

    private static List<String> fetchModelsForProvider(SharedPreferences prefs, String providerId) throws Exception {
        if ("ollama".equals(providerId)) {
            return fetchOllamaModels(endpoint(prefs, "local_provider_ollama_url", "http://127.0.0.1:11434"));
        }
        if ("gemini".equals(providerId)) {
            return fetchGeminiModels(prefs);
        }
        VoidPortLlmMessage.ProviderConfig config = VoidPortLlmMessage.resolveProviderConfig(prefs, providerId);
        if (config == null || config.baseUrl.isEmpty()) {
            throw new Exception("URL base não configurada para " + providerId);
        }
        Request.Builder request = new Request.Builder().url(modelListUrl(config.baseUrl)).get();
        if (!config.apiKey.isEmpty() && config.family == VoidPortLlmMessage.ProviderFamily.ANTHROPIC) {
            request.header("x-api-key", config.apiKey);
            request.header("anthropic-version", "2023-06-01");
        } else if (!config.apiKey.isEmpty()) {
            request.header("Authorization", "Bearer " + config.apiKey);
        }
        return fetchOpenAiCompatibleModels(request.build());
    }

    private static List<String> fetchOpenAiCompatibleModels(Request request) throws Exception {
        JSONObject json;
        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code() + " from models endpoint");
            json = new JSONObject(body);
        }
        JSONArray data = json.optJSONArray("data");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject model = data.optJSONObject(i);
            String id = model == null ? "" : model.optString("id", "");
            if (!id.isEmpty()) names.add(id);
        }
        return new ArrayList<>(names);
    }

    private static JSONObject fetchJson(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        return fetchJson(request);
    }

    private static JSONObject fetchJson(Request request) throws Exception {
        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                String detail = "";
                try {
                    JSONObject parsed = new JSONObject(body);
                    JSONObject error = parsed.optJSONObject("error");
                    detail = error == null ? "" : error.optString("message", "").trim();
                } catch (Exception ignored) {
                }
                throw new Exception("HTTP " + response.code()
                        + (detail.isEmpty() ? "" : ": " + detail));
            }
            return new JSONObject(body);
        }
    }

    private static String endpoint(SharedPreferences prefs, String key, String fallback) {
        return prefs.getString(key, fallback).trim();
    }

    private static String modelListUrl(String baseUrl) {
        String trimmed = trimTrailingSlash(baseUrl);
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed.substring(0, trimmed.length() - "/chat/completions".length()) + "/models";
        }
        return trimmed + "/models";
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String normalizeProvider(String providerId) {
        if (providerId == null) {
            return "";
        }
        return switch (providerId) {
            case "vLLM" -> "vllm";
            case "lmStudio" -> "lm_studio";
            default -> providerId;
        };
    }

}
