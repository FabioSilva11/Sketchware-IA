package pro.sketchware.chat.port;

import android.content.Context;
import android.content.SharedPreferences;

import pro.sketchware.chat.SecurePrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.chat.Tool;
import pro.sketchware.chat.agent.MultiAgentPolicy;

public final class VoidPortSettings {
    public static final String PREF_GITHUB_TOKEN = "github_token";

    public static final String PREFS_NAME = "ia_settings";
    public static final String PREF_CURRENT_PROVIDER = "current_ai_provider";
    public static final String PREF_CURRENT_MODEL = "current_ai_model";
    public static final String PREF_CUSTOM_MODELS = "custom_models_json";
    public static final String PREF_PROVIDER_CONFIGS = "provider_configs_v1";
    public static final String PREF_CHAT_MODE = "chat_mode";

    /** Persistent permission mode for the chat agent ("ask"/"read"/"full"). */
    public static final String PREF_PERMISSION_MODE = "permission_mode";
    public static final String PERMISSION_MODE_ASK = "ask";
    public static final String PERMISSION_MODE_READ = "read";
    public static final String PERMISSION_MODE_FULL = "full";
    public static final String PREF_MULTI_AGENT_MODE = "multi_agent_mode";
    public static final String PREF_MCP_CONFIG = "mcp_config_json";
    public static final String PREF_CHAT_WEB_SEARCH = "chat_web_search_enabled";

    public static final String PREF_AUTO_REFRESH_MODELS = "local_auto_detect_models";
    public static final String PREF_AI_INSTRUCTIONS = "void_ai_instructions";
    public static final String PREF_ENABLE_AUTOCOMPLETE = "feature_autocomplete_enabled";
    public static final String PREF_SYNC_APPLY_TO_CHAT = "feature_apply_same_as_chat_model";
    public static final String PREF_APPLY_MODE = "feature_apply_mode";
    public static final String PREF_SYNC_SCM_TO_CHAT = "feature_commit_same_as_chat_model";
    public static final String PREF_TOOLS_AUTO_APPROVE_EDITS = "feature_tools_auto_approve_edits";
    public static final String PREF_TOOLS_AUTO_APPROVE_MCP = "feature_tools_auto_approve_mcp";
    public static final String PREF_INCLUDE_TOOL_LINT_ERRORS = "feature_tools_fix_lint";
    public static final String PREF_AUTO_ACCEPT_LLM_CHANGES = "feature_tools_auto_accept_changes";
    public static final String PREF_SHOW_INLINE_SUGGESTIONS = "feature_editor_show_suggestions_on_select";
    public static final String PREF_PORT_PROMPTS_ENABLED = "feature_void_port_prompts_enabled";
    public static final String PREF_PORT_TOOL_POLICY_ENABLED = "feature_void_port_tool_policy_enabled";

    public static final String DEFAULT_MCP_CONFIG = "{\n  \"mcpServers\": {}\n}";
    public static final String MCP_CONFIG_ONLY_NOTICE = "Android can call URL-based MCP servers through JSON-RPC HTTP; command/stdio servers need an Android-accessible URL endpoint.";
    public static final String APPLY_MODE_FAST = "Fast Apply";
    public static final String APPLY_MODE_BALANCED = "Balanced";
    public static final String APPLY_MODE_CAREFUL = "Careful";

    public static final String APPROVAL_EDITS = "edits";
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
        // Hardened view: API keys and MCP headers are encrypted at rest with the
        // Android Keystore; legacy plaintext values are migrated on first access.
        return SecurePrefs.of(context);
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

    /**
     * Persistent permission mode for the chat agent. "ask" (WORKSPACE +
     * ON_REQUEST) is the default; "read" and "full" mirror the UI selector.
     * Full access is never a default — it is only set after explicit user
     * confirmation.
     */
    public static String getPermissionMode(SharedPreferences prefs) {
        if (prefs == null) {
            return PERMISSION_MODE_ASK;
        }
        String mode = prefs.getString(PREF_PERMISSION_MODE, PERMISSION_MODE_ASK);
        if (PERMISSION_MODE_ASK.equals(mode)
                || PERMISSION_MODE_READ.equals(mode)
                || PERMISSION_MODE_FULL.equals(mode)) {
            return mode;
        }
        return PERMISSION_MODE_ASK;
    }

    public static void setPermissionMode(SharedPreferences prefs, String mode) {
        if (prefs == null) {
            return;
        }
        prefs.edit().putString(PREF_PERMISSION_MODE, getPermissionMode0(mode)).apply();
    }

    private static String getPermissionMode0(String mode) {
        if (PERMISSION_MODE_READ.equals(mode) || PERMISSION_MODE_FULL.equals(mode)) {
            return mode;
        }
        return PERMISSION_MODE_ASK;
    }

    /** Maps a persisted permission mode to the runtime's immutable config. */
    public static pro.sketchware.chat.agentsdk.PermissionConfig permissionConfigForMode(String mode) {
        if (PERMISSION_MODE_READ.equals(mode)) {
            return pro.sketchware.chat.agentsdk.PermissionConfig.readOnly();
        }
        if (PERMISSION_MODE_FULL.equals(mode)) {
            return pro.sketchware.chat.agentsdk.PermissionConfig.fullAccess();
        }
        return pro.sketchware.chat.agentsdk.PermissionConfig.workspaceRequest();
    }

    public static String getMultiAgentMode(SharedPreferences prefs) {
        return MultiAgentPolicy.normalizeMode(
                prefs.getString(PREF_MULTI_AGENT_MODE, MultiAgentPolicy.MODE_AUTO));
    }

    public static void setMultiAgentMode(SharedPreferences prefs, String mode) {
        prefs.edit()
                .putString(PREF_MULTI_AGENT_MODE, MultiAgentPolicy.normalizeMode(mode))
                .apply();
    }

    public static List<ModelOption> getVisibleModelOptions(SharedPreferences prefs) {
        List<ModelOption> options = new ArrayList<>();
        // Kelivo keeps models inside the owning ProviderConfig. There is no
        // second discovered-model catalog to merge.
        JSONArray configs = readProviderConfigs(prefs);
        for (int i = 0; i < configs.length(); i++) {
            JSONObject config = configs.optJSONObject(i);
            if (config == null) continue;
            String pid = config.optString("id", "");
            String name = config.optString("name", pid);
            if (pid.isEmpty() || "kelivoin".equals(pid) || !isProviderConfigured(prefs, pid)) continue;
            JSONArray models = config.optJSONArray("models");
            if (models != null) {
                for (int j = 0; j < models.length(); j++) {
                    String model = models.optString(j, "").trim();
                    if (!model.isEmpty()) {
                        options.add(new ModelOption(pid, name, model));
                    }
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
            if (provider != null && !provider.trim().isEmpty() && model != null && !model.trim().isEmpty()) {
                return;
            }
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
                || "siliconflow".equals(providerId)
                || "tensdaq".equals(providerId)
                || "aihubmix".equals(providerId)
                || "suixiang".equals(providerId)
                || "aliyun".equals(providerId)
                || "zhipu".equals(providerId)
                || "bytedance".equals(providerId)
                || "grok_xai".equals(providerId)
                || "mistral".equals(providerId)
                || "minimax".equals(providerId)
                || "huggingface".equals(providerId)
                || "opencode_zen".equals(providerId)
                || "openai_compatible".equals(providerId)
                || "litellm".equals(providerId)
                || "ollama".equals(providerId)
                || "vllm".equals(providerId)
                || "lm_studio".equals(providerId)
                || (providerId != null && providerId.startsWith("custom_"));
    }

    public static boolean isProviderConfigured(SharedPreferences prefs, String providerId) {
        if (!isProviderAllowedByPlan(prefs, providerId)) {
            return false;
        }
        JSONObject custom = getProviderConfigObject(prefs, providerId);
        if (custom != null) {
            if (!custom.optBoolean("enabled", true)) {
                return false;
            }
            String baseUrl = custom.optString("baseUrl", "").trim();
            return !baseUrl.isEmpty()
                    && (!providerRequiresApiKey(providerId, custom) || hasUsableApiKey(custom));
        }
        return false;
    }

    public static boolean hasUsableApiKey(JSONObject config) {
        if (config == null) return false;
        if (!config.optString("apiKey", "").trim().isEmpty()) return true;
        if (!config.optBoolean("multiKeyEnabled", false)) return false;
        JSONArray keys = config.optJSONArray("apiKeys");
        for (int i = 0; keys != null && i < keys.length(); i++) {
            JSONObject item = keys.optJSONObject(i);
            if (item != null
                    && item.optBoolean("enabled", true)
                    && !item.optString("key", "").trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean providerRequiresApiKey(String providerId, JSONObject config) {
        String id = providerId == null ? "" : providerId.trim().toLowerCase(Locale.US);
        return !("ollama".equals(id) || "vllm".equals(id) || "lm_studio".equals(id));
    }

    public static boolean isProviderAllowedByPlan(SharedPreferences prefs, String providerId) {
        return true;
    }

    public static boolean isLocalProvider(String providerId) {
        return "vllm".equals(providerId) || "lm_studio".equals(providerId);
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
        List<ProviderGroup> allowed = new ArrayList<>();
        for (ProviderGroup group : groups) {
            if (isProviderAllowedByPlan(prefs, group.providerId)) {
                allowed.add(group);
            }
        }
        return allowed;
    }

    public static List<ProviderGroup> getCatalogProviderGroups() {
        List<ProviderGroup> groups = new ArrayList<>();
        groups.add(new ProviderGroup("ollama", "ollama", "Ollama Cloud", false, new ArrayList<>()));
        groups.add(new ProviderGroup("vllm", "vLLM", "vLLM", true, new ArrayList<>()));
        groups.add(new ProviderGroup("lm_studio", "lmStudio", "LM Studio", true, new ArrayList<>()));
        groups.add(new ProviderGroup("anthropic", "anthropic", "Anthropic", false, new ArrayList<>()));
        groups.add(new ProviderGroup("openai", "openAI", "OpenAI", false, new ArrayList<>()));
        groups.add(new ProviderGroup("deepseek", "deepseek", "DeepSeek", false, new ArrayList<>()));
        groups.add(new ProviderGroup("openrouter", "openRouter", "OpenRouter", false, new ArrayList<>()));
        groups.add(new ProviderGroup("openai_compatible", "openAICompatible", "OpenAI-Compatible", false, new ArrayList<>()));
        groups.add(new ProviderGroup("gemini", "gemini", "Gemini", false, new ArrayList<>()));
        groups.add(new ProviderGroup("groq", "groq", "Groq", false, new ArrayList<>()));
        groups.add(new ProviderGroup("grok_xai", "xAI", "Grok (xAI)", false, new ArrayList<>()));
        groups.add(new ProviderGroup("mistral", "mistral", "Mistral", false, new ArrayList<>()));
        groups.add(new ProviderGroup("minimax", "minimax", "MiniMax", false, new ArrayList<>()));
        groups.add(new ProviderGroup("litellm", "liteLLM", "LiteLLM", false, new ArrayList<>()));
        groups.add(new ProviderGroup("huggingface", "huggingFace", "Hugging Face", false, new ArrayList<>()));
        return groups;
    }

    public static List<ProviderCardSpec> getProviderCards() {
        List<ProviderCardSpec> providers = new ArrayList<>();
        // Keep the built-in catalog and order aligned with Kelivo v1.2.3.
        providers.add(provider("openai", "OpenAI", "https://platform.openai.com/api-keys"));
        providers.add(provider("siliconflow", "SiliconFlow", "https://cloud.siliconflow.cn/account/ak"));
        providers.add(provider("gemini", "Gemini", "https://aistudio.google.com/apikey"));
        providers.add(provider("openrouter", "OpenRouter", "https://openrouter.ai/keys"));
        providers.add(provider("tensdaq", "Tensdaq", null));
        providers.add(provider("deepseek", "DeepSeek", "https://platform.deepseek.com/api_keys"));
        providers.add(provider("aihubmix", "AIhubmix", "https://aihubmix.com"));
        providers.add(provider("suixiang", "随想AI中转站", "https://sui-xiang.com"));
        providers.add(provider("aliyun", "Aliyun", "https://bailian.console.aliyun.com"));
        providers.add(provider("zhipu", "Zhipu AI", "https://open.bigmodel.cn"));
        providers.add(provider("anthropic", "Claude", "https://console.anthropic.com/settings/keys"));
        providers.add(provider("grok_xai", "Grok", "https://console.x.ai"));
        providers.add(provider("bytedance", "ByteDance", "https://console.volcengine.com/ark"));
        // Providers Sketchware offered before the Axion engine; kept so existing setups stay visible
        providers.add(provider("opencode_zen", "OpenCode Zen", "https://opencode.ai/zen"));
        providers.add(provider("groq", "Groq", "https://console.groq.com/keys"));
        providers.add(provider("mistral", "Mistral", "https://console.mistral.ai/api-keys/"));
        providers.add(provider("minimax", "MiniMax", "https://platform.minimax.io/user-center/basic-information/interface-key"));
        providers.add(provider("huggingface", "Hugging Face", "https://huggingface.co/settings/tokens"));
        providers.add(provider("ollama", "Ollama", "https://ollama.com"));
        providers.add(provider("openai_compatible", "OpenAI-Compatible", null));
        providers.add(provider("litellm", "LiteLLM", null));
        return providers;
    }

    private static ProviderCardSpec provider(String id, String name, String helpUrl) {
        return new ProviderCardSpec(id, name, "", helpUrl, false)
                .addField("API Key", providerPrefKey(id, "api_key"), "", true, providerPrefKey(id, "enabled"))
                .addField("Base URL", providerPrefKey(id, "base_url"), defaultBaseForProviderType(id), false, null);
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
            if (providerId.isEmpty() || "kelivoin".equals(providerId) || name.isEmpty()) {
                continue;
            }
            if (isBuiltInProviderId(providerId)) {
                continue;
            }
            ProviderCardSpec spec = new ProviderCardSpec(providerId, name, "Custom provider", null, true)
                    .addField("API Key", providerPrefKey(providerId, "api_key"), "", true, providerPrefKey(providerId, "enabled"))
                    .addField("Base URL", providerPrefKey(providerId, "base_url"), defaultBaseForProviderType(providerType(config)), false, null)
                    .addField("Headers JSON", providerPrefKey(providerId, "headers"), "{}", false, null);
            providers.add(spec);
        }
        List<ProviderCardSpec> allowed = new ArrayList<>();
        for (ProviderCardSpec provider : providers) {
            if (isProviderAllowedByPlan(prefs, provider.providerId)) {
                allowed.add(provider);
            }
        }
        return allowed;
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

    /**
     * Returns a user-declared image-input capability for a custom/discovered
     * model, or {@code null} when the provider has not supplied that metadata.
     */
    public static Boolean getCustomModelImageInputOverride(SharedPreferences prefs,
                                                            String providerId,
                                                            String modelId) {
        if (prefs == null || providerId == null || modelId == null) {
            return null;
        }
        JSONArray models = readJsonArrayPreference(prefs, PREF_CUSTOM_MODELS);
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null
                    || !providerId.equals(model.optString("providerId", ""))
                    || !modelId.equals(model.optString("model", ""))
                    || !model.has("supportsImageInput")) {
                continue;
            }
            return model.optBoolean("supportsImageInput");
        }
        return null;
    }

    public static boolean supportsImageInput(SharedPreferences prefs, String providerId, String modelId) {
        Boolean override = getCustomModelImageInputOverride(prefs, providerId, modelId);
        return override != null
                ? override
                : ModelImageCapabilities.supportsImageInput(providerId, modelId);
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
        prefs.edit().putString(PREF_PROVIDER_CONFIGS, next.toString()).apply();
    }

    /** Kelivo-style provider-owned model list. */
    public static List<String> getProviderModels(SharedPreferences prefs, String providerId) {
        List<String> result = new ArrayList<>();
        JSONObject config = getProviderConfigObject(prefs, providerId);
        JSONArray models = config == null ? null : config.optJSONArray("models");
        for (int i = 0; models != null && i < models.length(); i++) {
            String model = models.optString(i, "").trim();
            if (!model.isEmpty() && !result.contains(model)) result.add(model);
        }
        return result;
    }

    public static void setProviderModels(SharedPreferences prefs, String providerId, List<String> modelIds) {
        if (prefs == null || providerId == null || providerId.trim().isEmpty()) return;
        JSONObject config = getProviderConfigObject(prefs, providerId);
        if (config == null) {
            config = defaultProviderConfig(providerId, providerId);
        }
        try {
            config.put("id", providerId);
            if (!config.has("name")) config.put("name", providerId);
            if (!config.has("providerType")) config.put("providerType", providerTypeForId(providerId));
            JSONArray models = new JSONArray();
            if (modelIds != null) {
                for (String model : modelIds) {
                    if (model != null && !model.trim().isEmpty()) models.put(model.trim());
                }
            }
            config.put("models", models);
            saveProviderConfig(prefs, config);
        } catch (Exception ignored) {
        }
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
        if ("ollama".equals(normalized) || "vllm".equals(normalized)
                || "lm_studio".equals(normalized) || "litellm".equals(normalized)) {
            return normalized;
        }
        return "openai";
    }

    public static String defaultBaseForProviderType(String type) {
        String normalized = type == null ? "openai" : type.trim().toLowerCase(Locale.US);
        return switch (normalized) {
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "siliconflow" -> "https://api.siliconflow.cn/v1";
            case "tensdaq" -> "https://tensdaq-api.x-aio.com/v1";
            case "aihubmix" -> "https://aihubmix.com/v1";
            case "suixiang" -> "https://sui-xiang.com/v1";
            case "aliyun" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "zhipu" -> "https://open.bigmodel.cn/api/paas/v4";
            case "bytedance" -> "https://ark.cn-beijing.volces.com/api/v3";
            case "groq" -> "https://api.groq.com/openai/v1";
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "openrouter" -> "https://openrouter.ai/api/v1";
            case "grok_xai" -> "https://api.x.ai/v1";
            case "mistral" -> "https://api.mistral.ai/v1";
            case "minimax" -> "https://api.minimax.io/v1";
            case "huggingface" -> "https://router.huggingface.co/v1";
            case "opencode_zen" -> "https://opencode.ai/zen/v1";
            case "ollama" -> "https://ollama.com/api";
            case "vllm" -> "http://127.0.0.1:8000/v1";
            case "lm_studio" -> "http://127.0.0.1:1234/v1";
            case "litellm" -> "http://127.0.0.1:4000/v1";
            // A useful default keeps new/custom providers usable for beginners.
            // Users can still replace this with any other OpenAI-compatible API.
            case "openai_compatible" -> "https://openrouter.ai/api/v1";
            default -> "https://api.openai.com/v1";
        };
    }

    public static boolean defaultEnabledForProvider(String providerId) {
        return false;
    }

    public static String providerTypeForId(String providerId) {
        if (providerId == null) return "openai";
        String id = providerId.trim().toLowerCase(Locale.US);
        if (id.contains("gemini") || id.contains("google")) return "gemini";
        if (id.contains("claude") || id.contains("anthropic")) return "anthropic";
        return "openai";
    }

    public static JSONObject defaultProviderConfig(String providerId, String displayName) {
        JSONObject config = new JSONObject();
        try {
            String id = providerId == null ? "" : providerId.trim();
            String type = providerTypeForId(id);
            config.put("id", id);
            config.put("enabled", defaultEnabledForProvider(id));
            config.put("name", displayName == null || displayName.trim().isEmpty() ? id : displayName.trim());
            config.put("apiKey", "");
            config.put("baseUrl", defaultBaseForProviderType(id));
            config.put("providerType", type);
            config.put("chatPath", "openai".equals(type) ? "/chat/completions" : "");
            config.put("useResponseApi", false);
            config.put("vertexAI", false);
            config.put("models", new JSONArray());
            config.put("modelOverrides", new JSONObject());
            config.put("customHeaders", new JSONArray());
            config.put("customBody", new JSONArray());
            config.put("proxyEnabled", false);
            config.put("proxyType", "http");
            config.put("proxyHost", "");
            config.put("proxyPort", "8080");
            config.put("proxyUsername", "");
            config.put("proxyPassword", "");
            config.put("multiKeyEnabled", false);
            config.put("apiKeys", new JSONArray());
            config.put("group", "other");
            if ("siliconflow".equals(id)) {
                config.put("models", new JSONArray().put("THUDM/GLM-4-9B-0414").put("Qwen/Qwen3-8B"));
            }
        } catch (Exception ignored) {
        }
        return config;
    }

    public static JSONObject getOrCreateProviderConfig(SharedPreferences prefs, String providerId, String displayName) {
        JSONObject existing = getProviderConfigObject(prefs, providerId);
        if (existing != null) return existing;
        JSONObject created = defaultProviderConfig(providerId, displayName);
        saveProviderConfig(prefs, created);
        return created;
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
            if (providerId.isEmpty() || "kelivoin".equals(providerId) || label.isEmpty()) {
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
                .putString(providerPrefKey(providerId, "base_url"), nonEmptyOrDefault(
                        config.optString("baseUrl", ""), defaultBaseForProviderType(providerType(config))))
                .putString(providerPrefKey(providerId, "headers"), config.optString("headers", "{}"))
                .putString(providerPrefKey(providerId, "api_path"), config.optString("chatPath", defaultChatPathForProviderType(providerType(config))))
                .apply();
    }

    private static String nonEmptyOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
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
            case "edit_project_file", "rewrite_project_file",
                    "rewrite_file", "edit_file", "create_file_or_folder", "delete_file_or_folder" -> APPROVAL_EDITS;
            default -> toolName.startsWith("mcp_") ? APPROVAL_MCP_TOOLS : null;
        };
    }

    public static boolean isAutoApprovalEnabled(SharedPreferences prefs, String approvalType) {
        if (APPROVAL_EDITS.equals(approvalType)) {
            return prefs.getBoolean(PREF_TOOLS_AUTO_APPROVE_EDITS, true);
        }
        if (APPROVAL_MCP_TOOLS.equals(approvalType)) {
            return prefs.getBoolean(PREF_TOOLS_AUTO_APPROVE_MCP, false);
        }
        return false;
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

    public static boolean isChatWebSearchEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_CHAT_WEB_SEARCH, false);
    }

    public static void setChatWebSearchEnabled(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(PREF_CHAT_WEB_SEARCH, enabled).apply();
        }
    }

    public static boolean setMcpServerEnabled(SharedPreferences prefs, String serverName,
                                              boolean enabled) {
        if (prefs == null || serverName == null || serverName.trim().isEmpty()) {
            return false;
        }
        try {
            JSONObject config = readMcpConfigObject(prefs);
            JSONObject servers = config.optJSONObject("mcpServers");
            JSONObject server = servers == null ? null : servers.optJSONObject(serverName);
            if (server == null) {
                return false;
            }
            server.put("enabled", enabled);
            prefs.edit().putString(PREF_MCP_CONFIG, config.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean saveMcpConfig(SharedPreferences prefs, String rawJson) {
        if (prefs == null || rawJson == null) {
            return false;
        }
        try {
            JSONObject config = new JSONObject(rawJson);
            if (config.optJSONObject("mcpServers") == null) {
                return false;
            }
            prefs.edit().putString(PREF_MCP_CONFIG, config.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
