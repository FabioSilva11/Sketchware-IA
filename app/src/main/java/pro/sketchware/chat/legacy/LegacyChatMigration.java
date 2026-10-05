package pro.sketchware.chat.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

import pro.sketchware.chat.ChatHistoryManager;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.ChatThread;
import pro.sketchware.chat.port.VoidPortSettings;

/**
 * One-time upgrade from the chat Sketchware shipped before the Axion engine: provider API keys saved as flat
 * preferences ({@code openai_api_key}, {@code groq_api_key}, ...) become provider configs, and conversations saved in
 * {@code void.chatThreadStorageII.json} are imported into the SQLite history.
 */
public final class LegacyChatMigration {
    private static final String TAG = "LegacyChatMigration";
    private static final String PREF_PROVIDERS_DONE = "legacy_providers_migrated_v1";
    private static final String PREF_HISTORY_DONE = "legacy_history_migrated_v1";

    /** Provider id, display name, legacy API key preference, legacy base URL preference (or null). */
    private static final String[][] LEGACY_PROVIDERS = {
            {"openai", "OpenAI", "openai_api_key", null},
            {"anthropic", "Anthropic", "anthropic_api_key", null},
            {"gemini", "Gemini", "gemini_api_key", null},
            {"ollama", "Ollama", "ollama_api_key", "local_provider_ollama_url"},
            {"openrouter", "OpenRouter", "openrouter_api_key", null},
            {"huggingface", "Hugging Face", "huggingface_api_key", null},
            {"opencode_zen", "OpenCode Zen", "opencode_zen_api_key", null},
            {"deepseek", "DeepSeek", "deepseek_api_key", null},
            {"groq", "Groq", "groq_api_key", null},
            {"grok_xai", "Grok (xAI)", "grok_xai_api_key", null},
            {"mistral", "Mistral", "mistral_api_key", null},
            {"minimax", "MiniMax", "minimax_api_key", null},
            {"openai_compatible", "OpenAI-Compatible", "openai_compatible_api_key", "openai_compatible_base_url"},
            {"litellm", "LiteLLM", "litellm_api_key", "litellm_base_url"},
    };

    /** Models each provider listed out of the box before, so a migrated provider has models to pick from. */
    private static final java.util.Map<String, String[]> LEGACY_MODELS = java.util.Map.ofEntries(
            java.util.Map.entry("anthropic", new String[]{"claude-opus-4-20250514", "claude-sonnet-4-20250514",
                    "claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"}),
            java.util.Map.entry("openai", new String[]{"gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "o3", "o4-mini"}),
            java.util.Map.entry("deepseek", new String[]{"deepseek-chat", "deepseek-reasoner"}),
            java.util.Map.entry("openrouter", new String[]{"anthropic/claude-opus-4", "anthropic/claude-sonnet-4",
                    "qwen/qwen3-235b-a22b", "deepseek/deepseek-r1", "google/gemini-2.0-flash-exp:free"}),
            java.util.Map.entry("huggingface", new String[]{"openai/gpt-oss-120b:fastest", "deepseek-ai/DeepSeek-R1:fastest"}),
            java.util.Map.entry("opencode_zen", new String[]{"gpt-5.6-sol", "claude-sonnet-5", "gemini-3.6-flash"}),
            java.util.Map.entry("gemini", new String[]{"gemini-2.5-pro-preview-05-06", "gemini-2.5-flash-preview-04-17",
                    "gemini-2.0-flash", "gemini-2.0-flash-lite"}),
            java.util.Map.entry("groq", new String[]{"qwen-qwq-32b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant"}),
            java.util.Map.entry("grok_xai", new String[]{"grok-3", "grok-3-mini", "grok-3-fast", "grok-3-mini-fast"}),
            java.util.Map.entry("mistral", new String[]{"codestral-latest", "devstral-small-latest", "mistral-large-latest",
                    "mistral-medium-latest"}),
            java.util.Map.entry("minimax", new String[]{"MiniMax-M2.7", "MiniMax-M2.7-highspeed", "MiniMax-M2.5",
                    "MiniMax-M2.1", "MiniMax-M2"})
    );

    private LegacyChatMigration() {
    }

    public static void run(Context context) {
        SharedPreferences prefs = VoidPortSettings.prefs(context);
        if (!prefs.getBoolean(PREF_PROVIDERS_DONE, false)) {
            try {
                migrateProviders(prefs);
            } catch (Exception e) {
                Log.w(TAG, "Provider migration failed", e);
            }
            prefs.edit().putBoolean(PREF_PROVIDERS_DONE, true).apply();
        }
        if (!prefs.getBoolean(PREF_HISTORY_DONE, false)) {
            try {
                migrateHistory(context);
                prefs.edit().putBoolean(PREF_HISTORY_DONE, true).apply();
            } catch (Exception e) {
                // Left unmarked: the next start tries again, the old file is untouched.
                Log.w(TAG, "History migration failed", e);
            }
        }
    }

    private static void migrateProviders(SharedPreferences prefs) throws Exception {
        for (String[] legacy : LEGACY_PROVIDERS) {
            String id = legacy[0];
            String apiKey = prefs.getString(legacy[2], "");
            String baseUrl = legacy[3] == null ? "" : prefs.getString(legacy[3], "");
            apiKey = apiKey == null ? "" : apiKey.trim();
            baseUrl = baseUrl == null ? "" : baseUrl.trim();
            if (apiKey.isEmpty() && !(("ollama".equals(id) || "litellm".equals(id)) && !baseUrl.isEmpty())) {
                continue;
            }
            JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, id);
            if (config != null && !config.optString("apiKey", "").trim().isEmpty()) {
                continue;
            }
            if (config == null) {
                config = VoidPortSettings.defaultProviderConfig(id, legacy[1]);
            }
            config.put("enabled", true);
            if (!apiKey.isEmpty()) {
                config.put("apiKey", apiKey);
            }
            if (!baseUrl.isEmpty()) {
                // The old Ollama setting was the server address; its API lives under /api.
                config.put("baseUrl", "ollama".equals(id) && !baseUrl.endsWith("/api") ? baseUrl.replaceAll("/+$", "") + "/api" : baseUrl);
            }
            org.json.JSONArray models = config.optJSONArray("models");
            if (models == null) {
                models = new org.json.JSONArray();
            }
            java.util.Set<String> known = new java.util.LinkedHashSet<>();
            for (int i = 0; i < models.length(); i++) {
                known.add(models.optString(i, ""));
            }
            String currentModel = id.equals(prefs.getString(VoidPortSettings.PREF_CURRENT_PROVIDER, ""))
                    ? prefs.getString(VoidPortSettings.PREF_CURRENT_MODEL, "") : "";
            if (currentModel != null && !currentModel.isEmpty() && known.add(currentModel)) {
                models.put(currentModel);
            }
            for (String model : LEGACY_MODELS.getOrDefault(id, new String[0])) {
                if (known.add(model)) {
                    models.put(model);
                }
            }
            config.put("models", models);
            VoidPortSettings.saveProviderConfig(prefs, config);
            Log.i(TAG, "Migrated provider " + id);
        }
    }

    private static void migrateHistory(Context context) {
        LegacyChatStorage legacy = new LegacyChatStorage(context);
        if (!legacy.exists()) {
            return;
        }
        ChatHistoryManager history = new ChatHistoryManager(context);
        int imported = 0;
        for (String scId : legacy.projectIds()) {
            for (ChatThread thread : legacy.getThreads(scId)) {
                List<ChatMessage> messages = legacy.loadHistory(scId, thread.id);
                if (messages == null || messages.isEmpty()) {
                    continue;
                }
                String threadId = history.createThread(scId);
                history.saveHistory(scId, threadId, messages);
                history.updateThreadSummary(scId, threadId, thread.title, thread.summary, thread.activeModel);
                if (thread.pinned) {
                    history.setThreadPinned(scId, threadId, true);
                }
                imported++;
            }
        }
        legacy.shutdown();
        File file = legacy.getFile();
        if (!file.renameTo(new File(file.getParentFile(), file.getName() + ".imported"))) {
            Log.w(TAG, "Couldn't rename " + file);
        }
        Log.i(TAG, "Imported " + imported + " conversations");
    }
}
