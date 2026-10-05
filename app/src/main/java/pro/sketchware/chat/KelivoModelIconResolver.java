package pro.sketchware.chat;

import java.util.Locale;

import pro.sketchware.R;

public final class KelivoModelIconResolver {

    private KelivoModelIconResolver() {
    }

    public static int resolve(String providerId, String modelId) {
        int modelIcon = resolveName(modelId);
        if (modelIcon != 0) {
            return modelIcon;
        }
        return resolveName(providerId);
    }

    public static int resolveProvider(String providerId, String providerLabel) {
        // OpenAI-compatible endpoints, LiteLLM and custom providers aren't OpenAI: no brand logo
        if (providerId != null && (providerId.equals("openai_compatible") || providerId.equals("litellm")
                || providerId.startsWith("custom_"))) {
            return 0;
        }
        int providerIcon = resolveName(providerId);
        if (providerIcon != 0) {
            return providerIcon;
        }
        return resolveName(providerLabel);
    }

    private static int resolveName(String name) {
        if (!ChatMessage.hasVisibleText(name)) {
            return 0;
        }
        String key = name.toLowerCase(Locale.US);
        if (containsAny(key, "openai", "gpt") || key.matches(".*\\bo[0-9].*")) {
            return R.drawable.kelivo_icon_openai;
        }
        if (key.contains("gemini")) {
            return R.drawable.kelivo_icon_gemini_color;
        }
        if (key.contains("google")) {
            return R.drawable.kelivo_icon_google_color;
        }
        if (key.contains("claude")) {
            return R.drawable.kelivo_icon_claude_color;
        }
        if (key.contains("anthropic")) {
            return R.drawable.kelivo_icon_anthropic;
        }
        if (key.contains("deepseek")) {
            return R.drawable.kelivo_icon_deepseek_color;
        }
        if (containsAny(key, "qwen", "qwq", "qvq")) {
            return R.drawable.kelivo_icon_qwen_color;
        }
        if (key.contains("doubao")) {
            return R.drawable.kelivo_icon_doubao_color;
        }
        if (key.contains("grok")) {
            return R.drawable.kelivo_icon_grok;
        }
        if (key.contains("xai")) {
            return R.drawable.kelivo_icon_xai;
        }
        if (key.contains("openrouter")) {
            return R.drawable.kelivo_icon_openrouter;
        }
        if (containsAny(key, "zhipu", "glm")) {
            return R.drawable.kelivo_icon_zhipu_color;
        }
        if (key.contains("mistral")) {
            return R.drawable.kelivo_icon_mistral_color;
        }
        if (key.contains("metaso")) {
            return R.drawable.kelivo_icon_metaso_color;
        }
        if (containsAny(key, "llama", "meta")) {
            return R.drawable.kelivo_icon_meta_color;
        }
        if (containsAny(key, "hunyuan", "tencent")) {
            return R.drawable.kelivo_icon_hunyuan_color;
        }
        if (key.contains("gemma")) {
            return R.drawable.kelivo_icon_gemma_color;
        }
        if (key.contains("perplexity")) {
            return R.drawable.kelivo_icon_perplexity_color;
        }
        if (containsAny(key, "aliyun", "alibaba", "bailian")) {
            return R.drawable.kelivo_icon_alibabacloud_color;
        }
        if (key.contains("bytedance")) {
            return R.drawable.kelivo_icon_bytedance_color;
        }
        if (key.contains("silicon")) {
            return R.drawable.kelivo_icon_siliconflow_color;
        }
        if (containsAny(key, "sensenova", "sensetime")) {
            return R.drawable.kelivo_icon_sensenova_color;
        }
        if (key.contains("aihubmix")) {
            return R.drawable.kelivo_icon_aihubmix_color;
        }
        if (key.contains("ollama")) {
            return R.drawable.kelivo_icon_ollama;
        }
        if (key.contains("cloudflare")) {
            return R.drawable.kelivo_icon_cloudflare_color;
        }
        if (key.contains("minimax")) {
            return R.drawable.kelivo_icon_minimax_color;
        }
        if (containsAny(key, "kimi", "moonshot")) {
            return R.drawable.kelivo_icon_kimi_color;
        }
        if (key.contains("302")) {
            return R.drawable.kelivo_icon_302ai_color;
        }
        if (key.contains("step")) {
            return R.drawable.kelivo_icon_stepfun_color;
        }
        if (key.contains("internlm")) {
            return R.drawable.kelivo_icon_internlm_color;
        }
        if (containsAny(key, "cohere", "command-")) {
            return R.drawable.kelivo_icon_cohere_color;
        }
        if (key.contains("tensdaq")) {
            return R.drawable.kelivo_icon_tensdaq_color;
        }
        if (key.contains("iflow")) {
            return R.drawable.kelivo_icon_iflow_color;
        }
        if (key.contains("sora")) {
            return R.drawable.kelivo_icon_sora_color;
        }
        if (key.contains("tavily")) {
            return R.drawable.kelivo_icon_tavily_color;
        }
        if (key.contains("exa")) {
            return R.drawable.kelivo_icon_exa_color;
        }
        if (key.contains("brave")) {
            return R.drawable.kelivo_icon_brave_color;
        }
        if (key.contains("jina")) {
            return R.drawable.kelivo_icon_jina_color;
        }
        if (key.contains("searxng")) {
            return R.drawable.kelivo_icon_searxng_color;
        }
        if (key.contains("serper")) {
            return R.drawable.kelivo_icon_serper;
        }
        if (key.contains("bocha")) {
            return R.drawable.kelivo_icon_bocha_color;
        }
        if (key.contains("kat")) {
            return R.drawable.kelivo_icon_katkwaipilot_color;
        }
        if (key.contains("duckduckgo")) {
            return R.drawable.kelivo_icon_duckduckgo_color;
        }
        if (containsAny(key, "mimo", "xiaomi")) {
            return R.drawable.kelivo_icon_mimo;
        }
        if (key.contains("codex")) {
            return R.drawable.kelivo_icon_codex;
        }
        return 0;
    }

    public static String initial(String providerId, String modelId) {
        String source = ChatMessage.hasVisibleText(modelId) ? modelId : providerId;
        if (!ChatMessage.hasVisibleText(source)) {
            return "?";
        }
        return source.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
