package pro.sketchware.activities.chat.port;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.ia.tools.Tool;

public final class VoidPortSettings {
    public static final String PREFS_NAME = "ia_settings";
    public static final String PREF_CURRENT_PROVIDER = "current_ai_provider";
    public static final String PREF_CURRENT_MODEL = "current_ai_model";
    public static final String PREF_CUSTOM_MODELS = "custom_models_json";
    public static final String PREF_PROVIDER_CONFIGS = "provider_configs_v1";
    public static final String PREF_GLOBAL_MAX_TOKENS = "global_chat_max_tokens";
    public static final String PREF_CHAT_MODE = "chat_mode";
    public static final String PREF_MCP_CONFIG = "mcp_config_json";

    public static final String PREF_AUTO_REFRESH_MODELS = "local_auto_detect_models";
    public static final String PREF_AI_INSTRUCTIONS = "void_ai_instructions";
    public static final String PREF_ENABLE_AUTOCOMPLETE = "feature_autocomplete_enabled";
    public static final String PREF_SYNC_APPLY_TO_CHAT = "feature_apply_same_as_chat_model";
    public static final String PREF_APPLY_MODE = "feature_apply_mode";
    public static final String PREF_SYNC_SCM_TO_CHAT = "feature_commit_same_as_chat_model";
    public static final String PREF_TOOLS_AUTO_APPROVE_EDITS = "feature_tools_auto_approve_edits";
    public static final String PREF_TOOLS_AUTO_APPROVE_TERMINAL = "feature_tools_auto_approve_terminal";
    public static final String PREF_TOOLS_AUTO_APPROVE_MCP = "feature_tools_auto_approve_mcp";
    public static final String PREF_INCLUDE_TOOL_LINT_ERRORS = "feature_tools_fix_lint";
    public static final String PREF_AUTO_ACCEPT_LLM_CHANGES = "feature_tools_auto_accept_changes";
    public static final String PREF_SHOW_INLINE_SUGGESTIONS = "feature_editor_show_suggestions_on_select";
    public static final String PREF_DISABLE_SYSTEM_MESSAGE = "feature_disable_void_system_message";
    public static final String PREF_PORT_SETTINGS_ENABLED = "feature_void_port_settings_enabled";
    public static final String PREF_PORT_PROMPTS_ENABLED = "feature_void_port_prompts_enabled";
    public static final String PREF_PORT_TOOL_POLICY_ENABLED = "feature_void_port_tool_policy_enabled";

    /** GitHub Personal Access Token used by {@link GitHubMcpService}. */
    public static final String PREF_GITHUB_TOKEN = "github_pat_token";

    public static final String DEFAULT_MCP_CONFIG = "{\n  \"mcpServers\": {}\n}";
    public static final String MCP_CONFIG_ONLY_NOTICE = "Android can call URL-based MCP servers through JSON-RPC HTTP; command/stdio servers need an Android-accessible URL endpoint.";
    public static final String APPLY_MODE_FAST = "Fast Apply";
    public static final String APPLY_MODE_BALANCED = "Balanced";
    public static final String APPLY_MODE_CAREFUL = "Careful";

    public static final String APPROVAL_EDITS = "edits";
    public static final String APPROVAL_TERMINAL = "terminal";
    public static final String APPROVAL_MCP_TOOLS = "MCP tools";

    private VoidPortSettings() {
    }

    public static final class ModelOption {
        public final String providerId;
        public final String providerLabel;
        public final String model;

        public ModelOption(String providerId, String providerLabel, String model) {
            this.providerId = providerId;
            this.providerLabel = providerLabel;
            this.model = model;
        }

        public String getDisplayLabel() {
            return providerLabel + " - " + model;
        }
    }

    public static final class ProviderGroup {
        public final String providerId;
        public final String voidProviderName;
        public final String label;
        public final boolean localProvider;
        public final List<String> models;

        public ProviderGroup(String providerId, String voidProviderName, String label, boolean localProvider, List<String> models) {
            this.providerId = providerId;
            this.voidProviderName = voidProviderName;
            this.label = label;
            this.localProvider = localProvider;
            this.models = models == null ? new ArrayList<>() : models;
        }
    }

    public static final class ProviderCardSpec {
        public final String providerId;
        public final String title;
        public final String description;
        public final String helpUrl;
        public final boolean custom;
        public final List<FieldSpec> fields = new ArrayList<>();

        public ProviderCardSpec(String title, String description, String helpUrl) {
            this(slugify(title), title, description, helpUrl, false);
        }

        public ProviderCardSpec(String providerId, String title, String description, String helpUrl, boolean custom) {
            this.providerId = providerId == null ? slugify(title) : providerId;
            this.title = title;
            this.description = description;
            this.helpUrl = helpUrl;
            this.custom = custom;
        }

        public ProviderCardSpec addField(String label, String prefKey, String defaultValue, boolean password, String enabledKey) {
            fields.add(new FieldSpec(label, prefKey, defaultValue, password, enabledKey));
            return this;
        }
    }

    public static final class FieldSpec {
        public final String label;
        public final String prefKey;
        public final String defaultValue;
        public final boolean password;
        public final String enabledKey;

        public FieldSpec(String label, String prefKey, String defaultValue, boolean password, String enabledKey) {
            this.label = label;
            this.prefKey = prefKey;
            this.defaultValue = defaultValue;
            this.password = password;
            this.enabledKey = enabledKey;
        }
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getChatMode(SharedPreferences prefs) {
        String mode = prefs.getString(PREF_CHAT_MODE, "agent");
        if ("normal".equals(mode) || "gather".equals(mode) || "agent".equals(mode)) {
            return mode;
        }
        return "agent";
    }

    public static void setChatMode(SharedPreferences prefs, String mode) {
        prefs.edit().putString(PREF_CHAT_MODE, normalizeChatMode(mode)).apply();
    }

    public static List<ModelOption> getVisibleModelOptions(SharedPreferences prefs) {
        List<ModelOption> options = new ArrayList<>();
        for (ProviderGroup group : getAllProviderGroups(prefs)) {
            if (!isProviderSupportedInChat(group.providerId) || !isProviderConfigured(prefs, group.providerId)) {
                continue;
            }
            for (String model : group.models) {
                if (!isModelHidden(prefs, group.providerId, model)) {
                    options.add(new ModelOption(group.providerId, group.label, model));
                }
            }
        }
        return options;
    }

    public static void ensureValidCurrentSelection(SharedPreferences prefs) {
        String provider = prefs.getString(PREF_CURRENT_PROVIDER, "");
        String model = prefs.getString(PREF_CURRENT_MODEL, "");
        if (isCurrentSelectionValid(prefs, provider, model)) {
            return;
        }

        List<ModelOption> options = getVisibleModelOptions(prefs);
        if (options.isEmpty()) {
            return;
        }

        ModelOption first = options.get(0);
        prefs.edit()
                .putString(PREF_CURRENT_PROVIDER, first.providerId)
                .putString(PREF_CURRENT_MODEL, first.model)
                .apply();
    }

    public static boolean isCurrentSelectionValid(SharedPreferences prefs, String providerId, String model) {
        if (providerId == null || model == null || providerId.trim().isEmpty() || model.trim().isEmpty()) {
            return false;
        }
        for (ModelOption option : getVisibleModelOptions(prefs)) {
            if (providerId.equals(option.providerId) && model.equals(option.model)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProviderSupportedInChat(String providerId) {
        return "anthropic".equals(providerId)
                || "openai".equals(providerId)
                || "gemini".equals(providerId)
                || "groq".equals(providerId)
                || "deepseek".equals(providerId)
                || "openrouter".equals(providerId)
                || "grok_xai".equals(providerId)
                || "mistral".equals(providerId)
                || "openai_compatible".equals(providerId)
                || "litellm".equals(providerId)
                || "ollama".equals(providerId)
                || "vllm".equals(providerId)
                || "lm_studio".equals(providerId)
                || (providerId != null && providerId.startsWith("custom_"));
    }

    public static boolean isProviderConfigured(SharedPreferences prefs, String providerId) {
        JSONObject custom = getProviderConfigObject(prefs, providerId);
        if (custom != null) {
            if (!custom.optBoolean("enabled", true)) {
                return false;
            }
            String type = providerType(custom);
            String baseUrl = custom.optString("baseUrl", "").trim();
            String apiKey = custom.optString("apiKey", "").trim();
            if ("openai".equals(type) || "anthropic".equals(type) || "gemini".equals(type)) {
                return !apiKey.isEmpty() && !baseUrl.isEmpty();
            }
            return !baseUrl.isEmpty();
        }
        return switch (providerId) {
            case "ollama" -> !getPreferenceValue(prefs, "local_provider_ollama_url", "http://127.0.0.1:11434").isEmpty();
            case "vllm" -> !getPreferenceValue(prefs, "local_provider_vllm_url", "http://localhost:8000").isEmpty();
            case "lm_studio" -> !getPreferenceValue(prefs, "local_provider_lm_studio_url", "http://localhost:1234").isEmpty();
            case "anthropic" -> !getPreferenceValue(prefs, "anthropic_api_key", "").isEmpty();
            case "openai" -> !getPreferenceValue(prefs, "openai_api_key", "").isEmpty();
            case "deepseek" -> !getPreferenceValue(prefs, "deepseek_api_key", "").isEmpty();
            case "openrouter" -> !getPreferenceValue(prefs, "openrouter_api_key", "").isEmpty();
            case "openai_compatible" -> !getPreferenceValue(prefs, "openai_compatible_base_url", "").isEmpty();
            case "gemini" -> !getPreferenceValue(prefs, "gemini_api_key", "").isEmpty();
            case "groq" -> !getPreferenceValue(prefs, "groq_api_key", "").isEmpty();
            case "grok_xai" -> !getPreferenceValue(prefs, "grok_xai_api_key", "").isEmpty();
            case "mistral" -> !getPreferenceValue(prefs, "mistral_api_key", "").isEmpty();
            case "litellm" -> !getPreferenceValue(prefs, "litellm_base_url", "").isEmpty();
            default -> false;
        };
    }

    public static boolean isLocalProvider(String providerId) {
        return "ollama".equals(providerId) || "vllm".equals(providerId) || "lm_studio".equals(providerId);
    }

    public static List<ProviderGroup> getAllProviderGroups(SharedPreferences prefs) {
        List<ProviderGroup> groups = getCatalogProviderGroups();
        for (ProviderGroup providerConfigGroup : getProviderConfigGroups(prefs)) {
            groups.add(providerConfigGroup);
        }
        for (ProviderGroup customGroup : getCustomProviderGroups(prefs)) {
            boolean merged = false;
            for (ProviderGroup group : groups) {
                if (!group.providerId.equals(customGroup.providerId)) {
                    continue;
                }
                for (String model : customGroup.models) {
                    if (!group.models.contains(model)) {
                        group.models.add(model);
                    }
                }
                merged = true;
                break;
            }
            if (!merged) {
                groups.add(customGroup);
            }
        }
        return groups;
    }

    public static List<ProviderGroup> getCatalogProviderGroups() {
        List<ProviderGroup> groups = new ArrayList<>();
        groups.add(new ProviderGroup("ollama", "ollama", "Ollama", true, new ArrayList<>()));
        groups.add(new ProviderGroup("vllm", "vLLM", "vLLM", true, new ArrayList<>()));
        groups.add(new ProviderGroup("lm_studio", "lmStudio", "LM Studio", true, new ArrayList<>()));
        groups.add(new ProviderGroup("anthropic", "anthropic", "Anthropic", false, new ArrayList<>(List.of(
                "claude-opus-4-0",
                "claude-sonnet-4-0",
                "claude-3-7-sonnet-latest",
                "claude-3-5-sonnet-latest",
                "claude-3-5-haiku-latest",
                "claude-3-opus-latest"
        ))));
        groups.add(new ProviderGroup("openai", "openAI", "OpenAI", false, new ArrayList<>(List.of(
                "gpt-4.1",
                "gpt-4.1-mini",
                "gpt-4.1-nano",
                "o3",
                "o4-mini"
        ))));
        groups.add(new ProviderGroup("deepseek", "deepseek", "DeepSeek", false, new ArrayList<>(List.of(
                "deepseek-chat",
                "deepseek-reasoner"
        ))));
        groups.add(new ProviderGroup("openrouter", "openRouter", "OpenRouter", false, new ArrayList<>(List.of(
                "anthropic/claude-opus-4",
                "anthropic/claude-sonnet-4",
                "qwen/qwen3-235b-a22b",
                "anthropic/claude-3.7-sonnet",
                "anthropic/claude-3.5-sonnet",
                "deepseek/deepseek-r1",
                "google/gemini-2.0-flash-exp:free"
        ))));
        groups.add(new ProviderGroup("openai_compatible", "openAICompatible", "OpenAI-Compatible", false, new ArrayList<>()));
        groups.add(new ProviderGroup("gemini", "gemini", "Gemini", false, new ArrayList<>(List.of(
                "gemini-2.5-pro-exp-03-25",
                "gemini-2.5-flash-preview-04-17",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-2.5-pro-preview-05-06"
        ))));
        groups.add(new ProviderGroup("groq", "groq", "Groq", false, new ArrayList<>(List.of(
                "qwen-qwq-32b",
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant"
        ))));
        groups.add(new ProviderGroup("grok_xai", "xAI", "Grok (xAI)", false, new ArrayList<>(List.of(
                "grok-2",
                "grok-3",
                "grok-3-mini",
                "grok-3-fast",
                "grok-3-mini-fast"
        ))));
        groups.add(new ProviderGroup("mistral", "mistral", "Mistral", false, new ArrayList<>(List.of(
                "codestral-latest",
                "devstral-small-latest",
                "mistral-large-latest",
                "mistral-medium-latest",
                "ministral-3b-latest",
                "ministral-8b-latest"
        ))));
        groups.add(new ProviderGroup("minimax", "minimax", "MiniMax", false, new ArrayList<>(List.of(
                "MiniMax-M2.7",
                "MiniMax-M2.7-highspeed",
                "MiniMax-M2.5",
                "MiniMax-M2.5-highspeed",
                "MiniMax-M2.1",
                "MiniMax-M2.1-highspeed",
                "MiniMax-M2",
                "M2-her"
        ))));
        groups.add(new ProviderGroup("litellm", "liteLLM", "LiteLLM", false, new ArrayList<>()));
        return groups;
    }

    public static List<ProviderCardSpec> getProviderCards() {
        List<ProviderCardSpec> providers = new ArrayList<>();
        providers.add(new ProviderCardSpec("openai", "OpenAI", "Get your API key here.", "https://platform.openai.com/api-keys", false)
                .addField("API Key", "openai_api_key", "", true, "openai_enabled"));
        providers.add(new ProviderCardSpec("anthropic", "Anthropic", "Get your API key here.", "https://console.anthropic.com/settings/keys", false)
                .addField("API Key", "anthropic_api_key", "", true, null));
        providers.add(new ProviderCardSpec("gemini", "Gemini", "Google AI Studio OpenAI-compatible endpoint.", "https://aistudio.google.com/apikey", false)
                .addField("API Key", "gemini_api_key", "", true, "gemini_enabled"));
        providers.add(new ProviderCardSpec("ollama", "Ollama", "Local Ollama server. Default: http://127.0.0.1:11434", "https://ollama.com", false)
                .addField("Base URL", "local_provider_ollama_url", "http://127.0.0.1:11434", false, null));
        providers.add(new ProviderCardSpec("openrouter", "OpenRouter", "Get your API key here. Rate limits depend on the selected model.", "https://openrouter.ai/keys", false)
                .addField("API Key", "openrouter_api_key", "", true, null));
        providers.add(new ProviderCardSpec("deepseek", "DeepSeek", "Get your API key here.", "https://platform.deepseek.com/api_keys", false)
                .addField("API Key", "deepseek_api_key", "", true, null));
        providers.add(new ProviderCardSpec("groq", "Groq", "Use Groq-hosted OpenAI-compatible models.", "https://console.groq.com/keys", false)
                .addField("API Key", "groq_api_key", "", true, "groq_enabled"));
        providers.add(new ProviderCardSpec("mistral", "Mistral", "Mistral API access.", "https://console.mistral.ai/api-keys/", false)
                .addField("API Key", "mistral_api_key", "", true, null));
        providers.add(new ProviderCardSpec("minimax", "MiniMax", "MiniMax text models through its OpenAI-compatible API.", "https://platform.minimax.io/user-center/basic-information/interface-key", false)
                .addField("API Key", "minimax_api_key", "", true, "minimax_enabled"));
        providers.add(new ProviderCardSpec("openai_compatible", "OpenAI-Compatible", "Use any provider that exposes an OpenAI-compatible endpoint.", null, false)
                .addField("Base URL", "openai_compatible_base_url", "https://my-endpoint.example/v1", false, null)
                .addField("API Key", "openai_compatible_api_key", "", true, null)
                .addField("Headers JSON", "openai_compatible_headers", "{}", false, null));
        providers.add(new ProviderCardSpec("litellm", "LiteLLM", "Point this to a LiteLLM proxy if you use one.", null, false)
                .addField("Base URL", "litellm_base_url", "http://localhost:4000", false, null));
        return providers;
    }

    public static List<ProviderCardSpec> getProviderCards(SharedPreferences prefs) {
        List<ProviderCardSpec> providers = getProviderCards();
        JSONArray configs = readProviderConfigs(prefs);
        for (int i = 0; i < configs.length(); i++) {
            JSONObject config = configs.optJSONObject(i);
            if (config == null) {
                continue;
            }
            String providerId = config.optString("id", "").trim();
            String name = config.optString("name", providerId).trim();
            if (providerId.isEmpty() || name.isEmpty()) {
                continue;
            }
            ProviderCardSpec spec = new ProviderCardSpec(providerId, name, "Custom provider", null, true)
                    .addField("API Key", providerPrefKey(providerId, "api_key"), "", true, providerPrefKey(providerId, "enabled"))
                    .addField("Base URL", providerPrefKey(providerId, "base_url"), defaultBaseForProviderType(providerType(config)), false, null)
                    .addField("Headers JSON", providerPrefKey(providerId, "headers"), "{}", false, null);
            providers.add(spec);
        }
        return providers;
    }

    public static JSONArray readProviderConfigs(SharedPreferences prefs) {
        return readJsonArrayPreference(prefs, PREF_PROVIDER_CONFIGS);
    }

    public static JSONObject getProviderConfigObject(SharedPreferences prefs, String providerId) {
        if (prefs == null || providerId == null || providerId.trim().isEmpty()) {
            return null;
        }
        JSONArray configs = readProviderConfigs(prefs);
        for (int i = 0; i < configs.length(); i++) {
            JSONObject config = configs.optJSONObject(i);
            if (config != null && providerId.equals(config.optString("id", ""))) {
                return config;
            }
        }
        return null;
    }

    public static JSONObject getProviderConfigForTitle(SharedPreferences prefs, String title) {
        if (title == null) {
            return null;
        }
        JSONArray configs = readProviderConfigs(prefs);
        for (int i = 0; i < configs.length(); i++) {
            JSONObject config = configs.optJSONObject(i);
            if (config != null && title.equals(config.optString("name", ""))) {
                return config;
            }
        }
        return null;
    }

    public static void saveProviderConfig(SharedPreferences prefs, JSONObject config) {
        if (prefs == null || config == null) {
            return;
        }
        String providerId = config.optString("id", "").trim();
        if (providerId.isEmpty()) {
            return;
        }
        JSONArray existing = readProviderConfigs(prefs);
        JSONArray next = new JSONArray();
        boolean updated = false;
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (providerId.equals(item.optString("id", ""))) {
                next.put(config);
                updated = true;
            } else {
                next.put(item);
            }
        }
        if (!updated) {
            next.put(config);
        }
        writeProviderConfigPrefs(prefs, config);
        prefs.edit().putString(PREF_PROVIDER_CONFIGS, next.toString()).apply();
    }

    public static void updateProviderConfigValue(SharedPreferences prefs, String providerId, String key, Object value) {
        JSONObject config = getProviderConfigObject(prefs, providerId);
        if (config == null || key == null || key.trim().isEmpty()) {
            return;
        }
        try {
            config.put(key, value);
            saveProviderConfig(prefs, config);
        } catch (Exception ignored) {
        }
    }

    public static void removeProviderConfig(SharedPreferences prefs, String providerId) {
        if (prefs == null || providerId == null || providerId.trim().isEmpty()) {
            return;
        }
        JSONArray existing = readProviderConfigs(prefs);
        JSONArray next = new JSONArray();
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item != null && !providerId.equals(item.optString("id", ""))) {
                next.put(item);
            }
        }
        prefs.edit()
                .putString(PREF_PROVIDER_CONFIGS, next.toString())
                .remove(providerPrefKey(providerId, "enabled"))
                .remove(providerPrefKey(providerId, "api_key"))
                .remove(providerPrefKey(providerId, "base_url"))
                .remove(providerPrefKey(providerId, "headers"))
                .remove(providerPrefKey(providerId, "api_path"))
                .apply();
    }

    public static String uniqueProviderId(SharedPreferences prefs, String seed) {
        String base = "custom_" + slugify(seed == null || seed.trim().isEmpty() ? "provider" : seed);
        if ("custom_".equals(base)) {
            base = "custom_provider";
        }
        String candidate = base;
        int suffix = 2;
        while (getProviderConfigObject(prefs, candidate) != null || isBuiltInProviderId(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    public static String providerPrefKey(String providerId, String key) {
        return "provider_config_" + slugify(providerId) + "_" + key;
    }

    public static String providerType(JSONObject config) {
        String raw = config == null ? "" : config.optString("providerType", config.optString("type", ""));
        String normalized = raw.trim().toLowerCase(Locale.US);
        if ("google".equals(normalized) || "gemini".equals(normalized)) {
            return "gemini";
        }
        if ("claude".equals(normalized) || "anthropic".equals(normalized)) {
            return "anthropic";
        }
        if ("openai-compatible".equals(normalized) || "openai_compatible".equals(normalized)) {
            return "openai_compatible";
        }
        return "openai";
    }

    public static String defaultBaseForProviderType(String type) {
        String normalized = type == null ? "openai" : type;
        return switch (normalized) {
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "minimax" -> "https://api.minimax.io/v1";
            case "openai_compatible" -> "";
            default -> "https://api.openai.com/v1";
        };
    }

    public static String defaultChatPathForProviderType(String type) {
        String normalized = type == null ? "openai" : type;
        return switch (normalized) {
            case "gemini", "anthropic" -> "";
            default -> "/chat/completions";
        };
    }

    private static List<ProviderGroup> getProviderConfigGroups(SharedPreferences prefs) {
        List<ProviderGroup> groups = new ArrayList<>();
        JSONArray configs = readProviderConfigs(prefs);
        for (int i = 0; i < configs.length(); i++) {
            JSONObject config = configs.optJSONObject(i);
            if (config == null) {
                continue;
            }
            String providerId = config.optString("id", "").trim();
            String label = config.optString("name", providerId).trim();
            if (providerId.isEmpty() || label.isEmpty()) {
                continue;
            }
            List<String> models = new ArrayList<>();
            JSONArray configModels = config.optJSONArray("models");
            for (int j = 0; configModels != null && j < configModels.length(); j++) {
                String model = configModels.optString(j, "").trim();
                if (!model.isEmpty() && !models.contains(model)) {
                    models.add(model);
                }
            }
            if (!models.isEmpty()) {
                groups.add(new ProviderGroup(providerId, toVoidProviderName(providerId), label, false, models));
            }
        }
        return groups;
    }

    private static void writeProviderConfigPrefs(SharedPreferences prefs, JSONObject config) {
        String providerId = config.optString("id", "").trim();
        if (providerId.isEmpty()) {
            return;
        }
        prefs.edit()
                .putBoolean(providerPrefKey(providerId, "enabled"), config.optBoolean("enabled", true))
                .putString(providerPrefKey(providerId, "api_key"), config.optString("apiKey", ""))
                .putString(providerPrefKey(providerId, "base_url"), config.optString("baseUrl", defaultBaseForProviderType(providerType(config))))
                .putString(providerPrefKey(providerId, "headers"), config.optString("headers", "{}"))
                .putString(providerPrefKey(providerId, "api_path"), config.optString("chatPath", defaultChatPathForProviderType(providerType(config))))
                .apply();
    }

    private static boolean isBuiltInProviderId(String providerId) {
        if (providerId == null) {
            return false;
        }
        for (ProviderCardSpec spec : getProviderCards()) {
            if (providerId.equals(spec.providerId)) {
                return true;
            }
        }
        return false;
    }

    public static List<ProviderGroup> getCustomProviderGroups(SharedPreferences prefs) {
        JSONArray array = readJsonArrayPreference(prefs, PREF_CUSTOM_MODELS);
        Map<String, ProviderGroup> groups = new LinkedHashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String providerId = item.optString("providerId", "custom");
            String providerLabel = item.optString("providerLabel", "Custom");
            String model = item.optString("model", "");
            if (model.isEmpty()) {
                continue;
            }
            ProviderGroup group = groups.get(providerId);
            if (group == null) {
                group = new ProviderGroup(providerId, toVoidProviderName(providerId), providerLabel, isLocalProvider(providerId), new ArrayList<>());
                groups.put(providerId, group);
            }
            if (!group.models.contains(model)) {
                group.models.add(model);
            }
        }
        return new ArrayList<>(groups.values());
    }

    public static String modelHiddenKey(String providerId, String model) {
        return "model_hidden_" + slugify(providerId) + "_" + slugify(model);
    }

    public static boolean isModelHidden(SharedPreferences prefs, String providerId, String model) {
        return prefs.getBoolean(modelHiddenKey(providerId, model), false);
    }

    public static void setModelHidden(SharedPreferences prefs, String providerId, String model, boolean hidden) {
        prefs.edit().putBoolean(modelHiddenKey(providerId, model), hidden).apply();
    }

    public static String getAiInstructions(SharedPreferences prefs) {
        return prefs.getString(PREF_AI_INSTRUCTIONS, "").trim();
    }

    public static boolean isPortedSettingsEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(PREF_PORT_SETTINGS_ENABLED, true);
    }

    public static boolean isPortedPromptsEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(PREF_PORT_PROMPTS_ENABLED, true);
    }

    public static boolean isPortedToolPolicyEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(PREF_PORT_TOOL_POLICY_ENABLED, true);
    }

    public static boolean shouldIncludeToolLintErrors(SharedPreferences prefs) {
        return prefs.getBoolean(PREF_INCLUDE_TOOL_LINT_ERRORS, true);
    }

    public static boolean shouldAutoAcceptLlmChanges(SharedPreferences prefs) {
        return prefs.getBoolean(PREF_AUTO_ACCEPT_LLM_CHANGES, false);
    }

    public static boolean requiresApproval(Context context, Tool tool) {
        if (tool == null || !tool.requiresApproval()) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        if (!isPortedToolPolicyEnabled(prefs)) {
            return true;
        }
        String approvalType = approvalTypeOfTool(tool.getName());
        if (approvalType == null) {
            return true;
        }
        return !isAutoApprovalEnabled(prefs, approvalType);
    }

    public static String approvalTypeOfTool(String toolName) {
        if (toolName == null) {
            return null;
        }
        return switch (toolName) {
            case "edit_project_file", "rewrite_project_file", "encrypt_sketchware_file",
                    "rewrite_file", "edit_file", "create_file_or_folder", "delete_file_or_folder" -> APPROVAL_EDITS;
            case "run_shell_command", "run_command", "open_persistent_terminal",
                    "run_persistent_command", "kill_persistent_terminal" -> APPROVAL_TERMINAL;
            default -> toolName.startsWith("mcp_") ? APPROVAL_MCP_TOOLS : null;
        };
    }

    public static boolean isAutoApprovalEnabled(SharedPreferences prefs, String approvalType) {
        if (APPROVAL_EDITS.equals(approvalType)) {
            return prefs.getBoolean(PREF_TOOLS_AUTO_APPROVE_EDITS, true);
        }
        if (APPROVAL_TERMINAL.equals(approvalType)) {
            return prefs.getBoolean(PREF_TOOLS_AUTO_APPROVE_TERMINAL, true);
        }
        if (APPROVAL_MCP_TOOLS.equals(approvalType)) {
            return prefs.getBoolean(PREF_TOOLS_AUTO_APPROVE_MCP, false);
        }
        return false;
    }

    public static String buildSystemPromptSettings(SharedPreferences prefs, String providerId, String chatMode) {
        if (!isPortedSettingsEnabled(prefs)) {
            return "";
        }

        String currentProvider = prefs.getString(PREF_CURRENT_PROVIDER, providerId == null ? "" : providerId);
        String currentModel = prefs.getString(PREF_CURRENT_MODEL, "");
        VoidPortModelCapabilities.Capabilities capabilities =
                VoidPortModelCapabilities.getModelCapabilities(currentProvider, currentModel);
        VoidPortModelCapabilities.ToolFormat effectiveToolFormat =
                VoidPortLlmMessage.prefersXmlToolProtocol(currentProvider)
                        ? VoidPortModelCapabilities.ToolFormat.XML_FALLBACK
                        : capabilities.toolFormat;
        String applyMode = prefs.getString(PREF_APPLY_MODE, APPLY_MODE_FAST);
        int mcpTotal = countMcpServers(prefs);
        int mcpEnabled = countEnabledMcpServers(prefs);

        StringBuilder builder = new StringBuilder();
        builder.append("- Void storage key: ").append(VoidPortStorageKeys.VOID_SETTINGS_STORAGE_KEY).append("\n");
        builder.append("- Chat mode: ").append(normalizeChatMode(chatMode)).append("\n");
        builder.append("- Chat model: ").append(currentProvider).append("/").append(currentModel).append("\n");
        builder.append("- Model capabilities: contextWindow=").append(capabilities.contextWindow)
                .append(", reservedOutput=").append(capabilities.reservedOutputTokenSpace)
                .append(", toolFormat=").append(effectiveToolFormat)
                .append(", supportsFIM=").append(capabilities.supportsFim)
                .append(", supportsReasoning=").append(capabilities.reasoningCapabilities.supportsReasoning)
                .append(", recognized=").append(capabilities.unrecognizedModel ? "false" : capabilities.recognizedModelName)
                .append("\n");
        builder.append("- Autocomplete enabled: ").append(prefs.getBoolean(PREF_ENABLE_AUTOCOMPLETE, false)).append("\n");
        builder.append("- Apply syncs to chat model: ").append(prefs.getBoolean(PREF_SYNC_APPLY_TO_CHAT, true)).append("\n");
        builder.append("- Apply mode: ").append(applyMode).append("\n");
        builder.append("- SCM/commit message syncs to chat model: ").append(prefs.getBoolean(PREF_SYNC_SCM_TO_CHAT, true)).append("\n");
        builder.append("- Inline suggestions on selection: ").append(prefs.getBoolean(PREF_SHOW_INLINE_SUGGESTIONS, true)).append("\n");
        builder.append("- Include lint errors after edits: ").append(shouldIncludeToolLintErrors(prefs)).append("\n");
        builder.append("- Auto-accept LLM changes: ").append(shouldAutoAcceptLlmChanges(prefs)).append("\n");
        builder.append("- Auto-approval: edits=").append(isAutoApprovalEnabled(prefs, APPROVAL_EDITS))
                .append(", terminal=").append(isAutoApprovalEnabled(prefs, APPROVAL_TERMINAL))
                .append(", MCP=").append(isAutoApprovalEnabled(prefs, APPROVAL_MCP_TOOLS)).append("\n");
        builder.append("- MCP config entries: ").append(mcpEnabled).append(" enabled / ").append(mcpTotal)
                .append(" total. ").append(MCP_CONFIG_ONLY_NOTICE).append("\n");

        String instructions = getAiInstructions(prefs);
        if (!instructions.isEmpty()) {
            builder.append("- User AI instructions from settings:\n").append(instructions).append("\n");
        }
        return builder.toString().trim();
    }

    public static JSONObject readMcpConfigObject(SharedPreferences prefs) {
        String raw = prefs.getString(PREF_MCP_CONFIG, DEFAULT_MCP_CONFIG);
        try {
            JSONObject config = new JSONObject(raw);
            if (config.optJSONObject("mcpServers") == null) {
                config.put("mcpServers", new JSONObject());
            }
            return config;
        } catch (Exception ignored) {
            try {
                return new JSONObject(DEFAULT_MCP_CONFIG);
            } catch (Exception impossible) {
                return new JSONObject();
            }
        }
    }

    public static int countMcpServers(SharedPreferences prefs) {
        JSONObject servers = readMcpServersObject(prefs);
        return servers.length();
    }

    public static int countEnabledMcpServers(SharedPreferences prefs) {
        JSONObject servers = readMcpServersObject(prefs);
        JSONArray names = servers.names();
        int count = 0;
        for (int i = 0; names != null && i < names.length(); i++) {
            JSONObject server = servers.optJSONObject(names.optString(i, ""));
            if (server != null && server.optBoolean("enabled", true)) {
                count++;
            }
        }
        return count;
    }

    private static JSONObject readMcpServersObject(SharedPreferences prefs) {
        JSONObject config = readMcpConfigObject(prefs);
        JSONObject servers = config.optJSONObject("mcpServers");
        return servers == null ? new JSONObject() : servers;
    }

    private static JSONArray readJsonArrayPreference(SharedPreferences prefs, String key) {
        String raw = prefs.getString(key, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String getPreferenceValue(SharedPreferences prefs, String key, String defaultValue) {
        return prefs.getString(key, defaultValue).trim();
    }

    private static String slugify(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_");
    }

    private static String normalizeChatMode(String chatMode) {
        if (chatMode == null) {
            return "agent";
        }
        String mode = chatMode.trim().toLowerCase(Locale.US);
        if ("normal".equals(mode) || "chat".equals(mode)) {
            return "normal";
        }
        if ("gather".equals(mode)) {
            return "gather";
        }
        return "agent";
    }

    private static String toVoidProviderName(String providerId) {
        return switch (providerId) {
            case "openai" -> "openAI";
            case "openrouter" -> "openRouter";
            case "openai_compatible" -> "openAICompatible";
            case "grok_xai" -> "xAI";
            case "lm_studio" -> "lmStudio";
            case "litellm" -> "liteLLM";
            case "vllm" -> "vLLM";
            default -> providerId;
        };
    }
}
