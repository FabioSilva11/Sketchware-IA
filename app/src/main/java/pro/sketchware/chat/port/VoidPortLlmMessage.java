package pro.sketchware.chat.port;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;



public final class VoidPortLlmMessage {
    public enum ProviderFamily {
        OPENAI_COMPATIBLE,
        ANTHROPIC,
        GEMINI
    }

    public static final class ProviderConfig {
        public final String providerId;
        public final ProviderFamily family;
        public final String baseUrl;
        public final String apiKey;
        public final JSONObject extraHeaders;
        public final boolean supportsNativeTools;
        public final boolean includeModelInBody;

        public ProviderConfig(ProviderFamily family, String baseUrl, String apiKey, JSONObject extraHeaders, boolean supportsNativeTools) {
            this("", family, baseUrl, apiKey, extraHeaders, supportsNativeTools, true);
        }

        public ProviderConfig(ProviderFamily family, String baseUrl, String apiKey, JSONObject extraHeaders,
                              boolean supportsNativeTools, boolean includeModelInBody) {
            this("", family, baseUrl, apiKey, extraHeaders, supportsNativeTools, includeModelInBody);
        }

        public ProviderConfig(String providerId, ProviderFamily family, String baseUrl, String apiKey,
                              JSONObject extraHeaders, boolean supportsNativeTools) {
            this(providerId, family, baseUrl, apiKey, extraHeaders, supportsNativeTools, true);
        }

        public ProviderConfig(String providerId, ProviderFamily family, String baseUrl, String apiKey,
                              JSONObject extraHeaders, boolean supportsNativeTools, boolean includeModelInBody) {
            this.providerId = providerId == null ? "" : providerId.trim();
            this.family = family;
            this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
            this.apiKey = apiKey == null ? "" : apiKey.trim();
            this.extraHeaders = extraHeaders == null ? new JSONObject() : extraHeaders;
            this.supportsNativeTools = supportsNativeTools;
            this.includeModelInBody = includeModelInBody;
        }
    }

    private VoidPortLlmMessage() {
    }

    /**
     * Modern official OpenAI chat models use the developer role for application
     * rules. Other OpenAI-compatible providers retain system for compatibility.
     */
    public static String instructionRole(String providerId, String modelName) {
        if (!"openai".equalsIgnoreCase(providerId == null ? "" : providerId.trim())) {
            return "system";
        }
        String model = modelName == null ? "" : modelName.trim().toLowerCase(java.util.Locale.ROOT);
        if (model.startsWith("gpt-5")
                || model.startsWith("gpt-4.1")
                || model.startsWith("o1")
                || model.startsWith("o3")
                || model.startsWith("o4")) {
            return "developer";
        }
        return "system";
    }

    public static ProviderConfig resolveProviderConfig(SharedPreferences prefs, String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) return null;
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        if (config == null) {
            String displayName = providerId;
            for (VoidPortSettings.ProviderCardSpec spec : VoidPortSettings.getProviderCards()) {
                if (providerId.equals(spec.providerId)) {
                    displayName = spec.title;
                    break;
                }
            }
            config = VoidPortSettings.defaultProviderConfig(providerId, displayName);
        }
        return resolveCustomProviderConfig(prefs, config);
    }

    public static int maxOutputTokens(String providerId, String modelName) {
        return VoidPortProviderMaxTokens.resolve(providerId, modelName, VoidPortProviderMaxTokens.DEFAULT_MAX_TOKENS);
    }

    public static boolean shouldUseNativeTools(String providerId, String modelName, ProviderConfig providerConfig) {
        if (providerConfig == null || !providerConfig.supportsNativeTools) {
            return false;
        }
        VoidPortModelCapabilities.ToolFormat toolFormat =
                VoidPortModelCapabilities.expectedToolFormat(providerId, modelName);
        if (providerConfig.family == ProviderFamily.ANTHROPIC) {
            return toolFormat == VoidPortModelCapabilities.ToolFormat.ANTHROPIC_STYLE;
        }
        if (providerConfig.family == ProviderFamily.GEMINI) {
            return toolFormat == VoidPortModelCapabilities.ToolFormat.GEMINI_STYLE;
        }
        return toolFormat == VoidPortModelCapabilities.ToolFormat.OPENAI_STYLE
                || toolFormat == VoidPortModelCapabilities.ToolFormat.GEMINI_STYLE;
    }

    public static boolean prefersXmlToolProtocol(String providerId) {
        return false;
    }

    private static ProviderConfig resolveCustomProviderConfig(SharedPreferences prefs, JSONObject config) {
        String providerId = config.optString("id", "");
        String type = VoidPortSettings.providerType(config);
        String baseUrl = config.optString("baseUrl", "");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = VoidPortSettings.defaultBaseForProviderType(providerId);
        }
        String chatPath = config.optString("chatPath", VoidPortSettings.defaultChatPathForProviderType(type));
        String apiKey = activeApiKey(prefs, providerId, config.optString("apiKey", ""));
        JSONObject headers = readHeadersJson(config.optString("headers", "{}"));

        if ("gemini".equals(type)) {
            return new ProviderConfig(
                    providerId,
                    ProviderFamily.GEMINI,
                    trimTrailingSlash(baseUrl),
                    apiKey,
                    headers,
                    true
            );
        }
        if ("anthropic".equals(type)) {
            return new ProviderConfig(
                    providerId,
                    ProviderFamily.ANTHROPIC,
                    configuredCustomRequestUrl(baseUrl, chatPath, "/messages"),
                    apiKey,
                    headers,
                    true
            );
        }
        if ("ollama".equals(type)) {
            return new ProviderConfig(
                    providerId,
                    ProviderFamily.OPENAI_COMPATIBLE,
                    normalizeOllamaUrl(baseUrl),
                    apiKey,
                    headers,
                    true
            );
        }
        if ("vllm".equals(type) || "lm_studio".equals(type) || "litellm".equals(type)) {
            return new ProviderConfig(
                    providerId,
                    ProviderFamily.OPENAI_COMPATIBLE,
                    normalizeOpenAiLocalUrl(baseUrl),
                    apiKey,
                    headers,
                    true
            );
        }
        return new ProviderConfig(
                providerId,
                ProviderFamily.OPENAI_COMPATIBLE,
                configuredCustomRequestUrl(baseUrl, chatPath, "/chat/completions"),
                apiKey,
                headers,
                true
        );
    }

    private static String configuredBaseUrl(SharedPreferences prefs, String providerId, String defaultBaseUrl) {
        String override = prefs.getString("base_url_override_" + slugify(providerId), "");
        return trimTrailingSlash(override == null || override.trim().isEmpty() ? defaultBaseUrl : override);
    }

    private static String nonEmptyPreference(SharedPreferences prefs, String key, String fallback) {
        String value = prefs.getString(key, "");
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String configuredRequestUrl(SharedPreferences prefs, String providerId, String defaultBaseUrl, String defaultPath) {
        String path = prefs.getString("api_path_override_" + slugify(providerId), "");
        return configuredCustomRequestUrl(configuredBaseUrl(prefs, providerId, defaultBaseUrl), path, defaultPath);
    }

    private static String configuredCustomRequestUrl(String baseUrl, String configuredPath, String defaultPath) {
        String base = trimTrailingSlash(baseUrl);
        if (base.isEmpty()) {
            return "";
        }
        String path = configuredPath == null || configuredPath.trim().isEmpty()
                ? defaultPath
                : configuredPath.trim();
        if (path == null || path.isEmpty()) {
            return base;
        }
        if (base.endsWith(path)) {
            return base;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static String activeApiKey(SharedPreferences prefs, String providerId, String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim();
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        if (config != null && config.optBoolean("multiKeyEnabled", false)) {
            JSONArray keys = config.optJSONArray("apiKeys");
            for (int i = 0; keys != null && i < keys.length(); i++) {
                JSONObject item = keys.optJSONObject(i);
                if (item == null || !item.optBoolean("enabled", true)) {
                    continue;
                }
                String value = item.optString("key", "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        if (!prefs.getBoolean("multi_key_mode_" + slugify(providerId), false)) {
            return key;
        }
        for (String part : key.split("[\\n,;]")) {
            String candidate = part.trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return key;
    }

    public static JSONObject readHeadersJson(String raw) {
        try {
            return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    public static String resolveRequestUrl(ProviderConfig providerConfig, String modelName) {
        if (providerConfig == null) {
            return "";
        }
        String url = providerConfig.baseUrl;
        if (url.contains("{model}")) {
            url = url.replace("{model}", safePathSegment(modelName));
        }
        return url;
    }

    public static JSONObject putModelIfNeeded(JSONObject body, ProviderConfig providerConfig, String modelName) {
        if (body == null || providerConfig == null || !providerConfig.includeModelInBody) {
            return body;
        }
        try {
            body.put("model", modelName);
            if (providerConfig.family == ProviderFamily.OPENAI_COMPATIBLE && !body.has("max_tokens")) {
                body.put("max_tokens", VoidPortProviderMaxTokens.resolve(
                        providerConfig.providerId,
                        modelName,
                        VoidPortProviderMaxTokens.DEFAULT_MAX_TOKENS
                ));
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    public static String normalizeOpenAiLocalUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.endsWith("/api/chat")) {
            return trimmed;
        }
        if (trimmed.contains("/v1/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        return trimmed + "/v1/chat/completions";
    }

    public static String normalizeOllamaUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // Remote Ollama uses its native chat endpoint and accepts the same
        // message/tool structures handled by the streaming parser.
        if (trimmed.endsWith("/api/chat")) {
            return trimmed;
        }
        if (trimmed.endsWith("/api")) {
            return trimmed + "/chat";
        }
        if (trimmed.equals("https://ollama.com")) {
            return trimmed + "/api/chat";
        }
        // Já aponta para o endpoint OpenAI-compatible — usar diretamente.
        if (trimmed.contains("/v1/chat/completions")) {
            return trimmed;
        }
        // Base terminando em /v1 — completar com /chat/completions.
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        // Base terminando em /api — é o prefixo nativo; converter para OpenAI-compat.
        if (trimmed.endsWith("/api")) {
            return trimmed.substring(0, trimmed.length() - 4) + "/v1/chat/completions";
        }
        // Base terminando em / — completar com o path OpenAI-compat padrão.
        if (trimmed.endsWith("/")) {
            return trimmed + "v1/chat/completions";
        }
        // URL base limpa (ex: http://127.0.0.1:11434) — adicionar path OpenAI-compat.
        return trimmed + "/v1/chat/completions";
    }

    public static String normalizeChatCompletionsUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String slugify(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+", "_");
    }

    private static String normalizeAzureOpenAiUrl(String resource, String apiVersion) {
        String trimmedResource = resource == null ? "" : resource.trim();
        if (trimmedResource.isEmpty()) {
            return "";
        }
        String endpoint = trimmedResource.startsWith("http://") || trimmedResource.startsWith("https://")
                ? trimmedResource
                : "https://" + trimmedResource + ".openai.azure.com";
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        String version = apiVersion == null || apiVersion.trim().isEmpty()
                ? "2024-05-01-preview"
                : apiVersion.trim();
        return endpoint + "/openai/deployments/{model}/chat/completions?api-version=" + version;
    }

    private static JSONObject singleHeader(String name, String value) {
        JSONObject headers = new JSONObject();
        try {
            if (name != null && !name.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
                headers.put(name, value.trim());
            }
        } catch (Exception ignored) {
        }
        return headers;
    }

    private static String safePathSegment(String value) {
        String segment = value == null ? "" : value.trim();
        return segment.replace("/", "%2F").replace(" ", "%20");
    }
}
