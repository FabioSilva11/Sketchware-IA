package pro.sketchware.chat.port;

import java.util.Locale;

/**
 * Conservative, transport-agnostic image-input classifier.
 *
 * <p>Only return {@code true} when the model family is known to accept image
 * content in a chat request. A gateway can expose arbitrary aliases, so custom
 * model metadata in {@link VoidPortSettings} always takes precedence.</p>
 */
public final class ModelImageCapabilities {
    private ModelImageCapabilities() {
    }

    public static boolean supportsImageInput(String providerId, String modelId) {
        String provider = normalize(providerId);
        String model = normalize(modelId);
        if (model.isEmpty() || isTextOnlyOrUnsupportedChatModel(model)) {
            return false;
        }

        // Native providers whose chat models accept images.
        if ("anthropic".equals(provider)) {
            return model.contains("claude");
        }
        if ("gemini".equals(provider)) {
            return model.contains("gemini") && !model.contains("live");
        }
        if ("openai".equals(provider)) {
            return isOpenAiVisionModel(model);
        }
        if ("deepseek".equals(provider)) {
            return model.contains("deepseek-vl") || model.contains("deepseek-vision");
        }
        if ("groq".equals(provider)) {
            return model.contains("llama-4") || model.contains("llama-3.2")
                    || model.contains("vision") || model.contains("-vl");
        }
        if ("mistral".equals(provider)) {
            return model.contains("pixtral") || model.contains("mistral-small")
                    || model.contains("mistral-medium") || model.contains("mistral-large");
        }
        if ("grok_xai".equals(provider)) {
            return model.contains("grok-2-vision") || model.contains("grok-4")
                    || model.contains("grok-vision");
        }
        if ("minimax".equals(provider)) {
            return model.contains("-vl") || model.contains("vision");
        }

        // Routers and OpenAI-compatible gateways use the model identifier; do
        // not infer vision solely from the gateway name.
        return isKnownVisionModelName(model);
    }

    private static boolean isKnownVisionModelName(String model) {
        return isOpenAiVisionModel(model)
                || model.contains("claude")
                || (model.contains("gemini") && !model.contains("live"))
                || model.contains("vision")
                || model.contains("-vl")
                || model.contains("_vl")
                || model.contains("qwen-vl")
                || model.contains("qvq")
                || model.contains("pixtral")
                || model.contains("llava")
                || model.contains("minicpm-v")
                || model.contains("glm-4v")
                || model.contains("llama-4")
                || model.contains("llama-3.2");
    }

    private static boolean isOpenAiVisionModel(String model) {
        return model.contains("gpt-4o")
                || model.contains("gpt-4.1")
                || model.contains("gpt-5")
                || model.contains("gpt5")
                || model.equals("o1") || model.startsWith("o1-")
                || model.equals("o3") || model.startsWith("o3-")
                || model.equals("o4") || model.startsWith("o4-");
    }

    private static boolean isTextOnlyOrUnsupportedChatModel(String model) {
        return model.contains("embedding") || model.contains("rerank")
                || model.contains("moderation") || model.contains("whisper")
                || model.contains("transcribe") || model.contains("tts")
                || model.contains("audio") || model.contains("realtime")
                || model.contains("gpt-oss") || model.contains("codestral")
                || model.contains("devstral") || model.contains("deepseek-chat")
                || model.contains("deepseek-reasoner") || model.contains("qwq")
                || model.contains("coder");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
