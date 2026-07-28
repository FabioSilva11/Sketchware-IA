package pro.sketchware.activities.chat.port;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class VoidPortModelCapabilities {
    public enum SystemMessageSupport {
        NONE,
        SYSTEM_ROLE,
        DEVELOPER_ROLE,
        SEPARATED
    }

    public enum ToolFormat {
        OPENAI_STYLE,
        ANTHROPIC_STYLE,
        GEMINI_STYLE,
        XML_FALLBACK
    }

    public enum ReasoningSliderType {
        NONE,
        BUDGET,
        EFFORT
    }

    public static final class ReasoningCapabilities {
        public final boolean supportsReasoning;
        public final boolean canTurnOffReasoning;
        public final boolean canOutputReasoning;
        public final int reasoningReservedOutputTokenSpace;
        public final ReasoningSliderType sliderType;
        public final int budgetMin;
        public final int budgetMax;
        public final int budgetDefault;
        public final String[] effortValues;
        public final String effortDefault;
        public final String thinkOpenTag;
        public final String thinkCloseTag;

        private ReasoningCapabilities(boolean supportsReasoning,
                                      boolean canTurnOffReasoning,
                                      boolean canOutputReasoning,
                                      int reasoningReservedOutputTokenSpace,
                                      ReasoningSliderType sliderType,
                                      int budgetMin,
                                      int budgetMax,
                                      int budgetDefault,
                                      String[] effortValues,
                                      String effortDefault,
                                      String thinkOpenTag,
                                      String thinkCloseTag) {
            this.supportsReasoning = supportsReasoning;
            this.canTurnOffReasoning = canTurnOffReasoning;
            this.canOutputReasoning = canOutputReasoning;
            this.reasoningReservedOutputTokenSpace = reasoningReservedOutputTokenSpace;
            this.sliderType = sliderType;
            this.budgetMin = budgetMin;
            this.budgetMax = budgetMax;
            this.budgetDefault = budgetDefault;
            this.effortValues = effortValues == null ? new String[0] : effortValues;
            this.effortDefault = effortDefault == null ? "" : effortDefault;
            this.thinkOpenTag = thinkOpenTag == null ? "" : thinkOpenTag;
            this.thinkCloseTag = thinkCloseTag == null ? "" : thinkCloseTag;
        }

        static ReasoningCapabilities none() {
            return new ReasoningCapabilities(false, false, false, 0, ReasoningSliderType.NONE, 0, 0, 0, null, "", "", "");
        }

        static ReasoningCapabilities thinkTags(boolean canTurnOffReasoning, boolean canOutputReasoning, int reservedOutput) {
            return new ReasoningCapabilities(true, canTurnOffReasoning, canOutputReasoning, reservedOutput,
                    ReasoningSliderType.NONE, 0, 0, 0, null, "", "<think>", "</think>");
        }

        static ReasoningCapabilities budget(boolean canTurnOffReasoning, int reservedOutput, int min, int max, int defaultValue) {
            return new ReasoningCapabilities(true, canTurnOffReasoning, true, reservedOutput,
                    ReasoningSliderType.BUDGET, min, max, defaultValue, null, "", "", "");
        }

        static ReasoningCapabilities effort(boolean canTurnOffReasoning, int reservedOutput, String defaultValue, String... values) {
            return new ReasoningCapabilities(true, canTurnOffReasoning, true, reservedOutput,
                    ReasoningSliderType.EFFORT, 0, 0, 0, values, defaultValue, "", "");
        }
    }

    public static final class Capabilities {
        public final String modelName;
        public final String recognizedModelName;
        public final boolean unrecognizedModel;
        public final int contextWindow;
        public final int reservedOutputTokenSpace;
        public final SystemMessageSupport systemMessageSupport;
        public final ToolFormat toolFormat;
        public final boolean supportsFim;
        public final ReasoningCapabilities reasoningCapabilities;

        private Capabilities(String modelName,
                             String recognizedModelName,
                             boolean unrecognizedModel,
                             int contextWindow,
                             int reservedOutputTokenSpace,
                             SystemMessageSupport systemMessageSupport,
                             ToolFormat toolFormat,
                             boolean supportsFim,
                             ReasoningCapabilities reasoningCapabilities) {
            this.modelName = modelName == null ? "" : modelName;
            this.recognizedModelName = recognizedModelName == null ? "" : recognizedModelName;
            this.unrecognizedModel = unrecognizedModel;
            this.contextWindow = contextWindow;
            this.reservedOutputTokenSpace = reservedOutputTokenSpace;
            this.systemMessageSupport = systemMessageSupport == null ? SystemMessageSupport.NONE : systemMessageSupport;
            this.toolFormat = toolFormat == null ? ToolFormat.XML_FALLBACK : toolFormat;
            this.supportsFim = supportsFim;
            this.reasoningCapabilities = reasoningCapabilities == null ? ReasoningCapabilities.none() : reasoningCapabilities;
        }

        public int effectiveReservedOutputTokenSpace(boolean reasoningEnabled) {
            if (reasoningEnabled && reasoningCapabilities.supportsReasoning
                    && reasoningCapabilities.reasoningReservedOutputTokenSpace > 0) {
                return reasoningCapabilities.reasoningReservedOutputTokenSpace;
            }
            return reservedOutputTokenSpace > 0 ? reservedOutputTokenSpace : 4096;
        }
    }

    private static final Map<String, Capabilities> EXACT = new HashMap<>();

    static {
        add("openai", "gpt-4.1", 1_047_576, 32_768, SystemMessageSupport.DEVELOPER_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openai", "gpt-4.1-mini", 1_047_576, 32_768, SystemMessageSupport.DEVELOPER_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openai", "gpt-4.1-nano", 1_047_576, 32_768, SystemMessageSupport.DEVELOPER_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openai", "o3", 200_000, 100_000, SystemMessageSupport.DEVELOPER_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.effort(true, 100_000, "medium", "low", "medium", "high"));
        add("openai", "o4-mini", 200_000, 100_000, SystemMessageSupport.DEVELOPER_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.effort(true, 100_000, "medium", "low", "medium", "high"));
        add("openai", "gpt-4o", 128_000, 16_384, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openai", "gpt-4o-mini", 128_000, 16_384, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());

        add("anthropic", "claude-3-7-sonnet-latest", 200_000, 8_192, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.budget(true, 8_192, 1_024, 8_192, 1_024));
        add("anthropic", "claude-3-7-sonnet-20250219", 200_000, 8_192, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.budget(true, 8_192, 1_024, 8_192, 1_024));
        add("anthropic", "claude-sonnet-4-20250514", 200_000, 8_192, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.none());
        add("anthropic", "claude-opus-4-20250514", 200_000, 8_192, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.none());
        add("anthropic", "claude-3-5-sonnet-latest", 200_000, 8_192, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.none());
        add("anthropic", "claude-3-5-haiku-latest", 200_000, 8_192, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.none());
        add("anthropic", "claude-3-opus-latest", 200_000, 4_096, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.none());

        add("gemini", "gemini-2.5-pro-exp-03-25", 1_048_576, 65_536, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.GEMINI_STYLE, false, ReasoningCapabilities.budget(true, 65_536, 128, 32_768, 8_192));
        add("gemini", "gemini-2.5-pro-preview-05-06", 1_048_576, 65_536, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.GEMINI_STYLE, false, ReasoningCapabilities.budget(true, 65_536, 128, 32_768, 8_192));
        add("gemini", "gemini-2.5-flash-preview-04-17", 1_048_576, 65_536, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.GEMINI_STYLE, false, ReasoningCapabilities.budget(true, 65_536, 128, 24_576, 8_192));
        add("gemini", "gemini-2.0-flash", 1_048_576, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.GEMINI_STYLE, false, ReasoningCapabilities.none());
        add("gemini", "gemini-2.0-flash-lite", 1_048_576, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.GEMINI_STYLE, false, ReasoningCapabilities.none());

        add("deepseek", "deepseek-chat", 64_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("deepseek", "deepseek-reasoner", 64_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(false, true, 8_192));

        add("groq", "qwen-qwq-32b", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(false, true, 8_192));
        add("groq", "llama-3.3-70b-versatile", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("groq", "llama-3.1-8b-instant", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());

        add("mistral", "codestral-latest", 256_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, true, ReasoningCapabilities.none());
        add("mistral", "devstral-small-latest", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("mistral", "mistral-large-latest", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("mistral", "mistral-medium-latest", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("mistral", "ministral-3b-latest", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("mistral", "ministral-8b-latest", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());

        add("grok_xai", "grok-2", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("grok_xai", "grok-3", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("grok_xai", "grok-3-mini", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.effort(true, 8_192, "medium", "low", "medium", "high"));
        add("grok_xai", "grok-3-fast", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("grok_xai", "grok-3-mini-fast", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.effort(true, 8_192, "medium", "low", "medium", "high"));

        add("openrouter", "qwen/qwen3-235b-a22b", 40_960, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(false, true, 8_192));
        add("openrouter", "deepseek/deepseek-r1", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(false, true, 8_192));
        add("openrouter", "google/gemini-2.0-flash-exp:free", 1_048_576, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openrouter", "anthropic/claude-opus-4", 200_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openrouter", "anthropic/claude-sonnet-4", 200_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openrouter", "anthropic/claude-3.7-sonnet", 200_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());
        add("openrouter", "anthropic/claude-3.5-sonnet", 200_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.none());

        // Hugging Face Inference Providers exposes an OpenAI-compatible chat
        // endpoint. The router may select different backends, so these are
        // conservative capabilities for the documented starter models.
        add("huggingface", "openai/gpt-oss-120b:fastest", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(true, true, 8_192));
        add("huggingface", "deepseek-ai/DeepSeek-R1:fastest", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(false, true, 8_192));

        add("ollama", "qwen2.5-coder:7b", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, true, ReasoningCapabilities.none());
        add("ollama", "qwen2.5-coder:3b", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, true, ReasoningCapabilities.none());
        add("ollama", "qwen2.5-coder:1.5b", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, true, ReasoningCapabilities.none());
        add("ollama", "llama3.1", 128_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, false, ReasoningCapabilities.none());
        add("ollama", "qwen2.5-coder", 128_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, false, ReasoningCapabilities.none());
        add("ollama", "qwq", 128_000, 32_000, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, false, ReasoningCapabilities.thinkTags(false, false, 32_000));
        add("ollama", "deepseek-r1", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, false, ReasoningCapabilities.thinkTags(false, false, 8_192));
        add("ollama", "devstral:latest", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, false, ReasoningCapabilities.none());
        add("ollama", "qwen3.5:397b-cloud", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.XML_FALLBACK, false, ReasoningCapabilities.thinkTags(true, true, 8_192));
        add("ollama", "gpt-oss:20b-cloud", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(true, true, 8_192));
        add("ollama", "gpt-oss:120b-cloud", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(true, true, 8_192));
    }

    private VoidPortModelCapabilities() {
    }

    public static Capabilities getModelCapabilities(String providerId, String modelName) {
        String provider = providerId == null ? "" : providerId;
        String model = modelName == null ? "" : modelName;
        String key = key(provider, model);
        Capabilities exact = EXACT.get(key);
        if (exact != null) {
            return exactForRequestedName(exact, model);
        }

        Capabilities fallback = fallbackCapabilities(provider, model);
        if (fallback != null) {
            return fallback;
        }

        return new Capabilities(model, "", true, 16_384, 4_096,
                defaultSystemMessageSupport(provider), defaultToolFormat(provider),
                false, ReasoningCapabilities.none());
    }

    public static ToolFormat expectedToolFormat(String providerId, String modelName) {
        return getModelCapabilities(providerId, modelName).toolFormat;
    }

    public static boolean supportsFim(String providerId, String modelName) {
        return getModelCapabilities(providerId, modelName).supportsFim;
    }

    public static boolean supportsReasoning(String providerId, String modelName) {
        return getModelCapabilities(providerId, modelName).reasoningCapabilities.supportsReasoning;
    }

    private static void add(String providerId,
                            String modelName,
                            int contextWindow,
                            int reservedOutput,
                            SystemMessageSupport systemMessageSupport,
                            ToolFormat toolFormat,
                            boolean supportsFim,
                            ReasoningCapabilities reasoningCapabilities) {
        EXACT.put(key(providerId, modelName), new Capabilities(
                modelName,
                modelName,
                false,
                contextWindow,
                reservedOutput,
                systemMessageSupport,
                toolFormat,
                supportsFim,
                reasoningCapabilities
        ));
    }

    private static Capabilities exactForRequestedName(Capabilities exact, String requestedName) {
        return new Capabilities(
                requestedName,
                exact.recognizedModelName,
                false,
                exact.contextWindow,
                exact.reservedOutputTokenSpace,
                exact.systemMessageSupport,
                exact.toolFormat,
                exact.supportsFim,
                exact.reasoningCapabilities
        );
    }

    private static Capabilities fallbackCapabilities(String providerId, String modelName) {
        String lower = modelName == null ? "" : modelName.toLowerCase(Locale.US);
        ToolFormat toolFormat = defaultToolFormat(providerId);

        if (lower.contains("gemini") && (lower.contains("2.5") || lower.contains("2-5"))) {
            return recognized(modelName, "gemini-2.5", 1_048_576, 65_536, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.GEMINI_STYLE, false, ReasoningCapabilities.budget(true, 65_536, 128, 32_768, 8_192));
        }
        if (lower.contains("claude-3-5") || lower.contains("claude-3.5")) {
            return recognized(modelName, "claude-3-5-sonnet", 200_000, 8_192, SystemMessageSupport.SEPARATED, ToolFormat.ANTHROPIC_STYLE, false, ReasoningCapabilities.none());
        }
        if (lower.contains("claude")) {
            ToolFormat format = "anthropic".equals(providerId) ? ToolFormat.ANTHROPIC_STYLE : ToolFormat.OPENAI_STYLE;
            SystemMessageSupport support = "anthropic".equals(providerId) ? SystemMessageSupport.SEPARATED : SystemMessageSupport.SYSTEM_ROLE;
            return recognized(modelName, "claude", 200_000, 8_192, support, format, false, ReasoningCapabilities.none());
        }
        if (lower.contains("deepseek-r1") || lower.contains("deepseek-reasoner")) {
            return recognized(modelName, "deepseek-r1", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.thinkTags(false, true, 8_192));
        }
        if (lower.contains("deepseek")) {
            return recognized(modelName, "deepseek-coder", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("llama3.3") || lower.contains("llama-3.3")) {
            return recognized(modelName, "llama3.3", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("llama3.2") || lower.contains("llama-3.2")) {
            return recognized(modelName, "llama3.2", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("llama3.1") || lower.contains("llama-3.1")) {
            return recognized(modelName, "llama3.1", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("llama3") || lower.contains("llama-3")) {
            return recognized(modelName, "llama3", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("llama") || lower.contains("scout") || lower.contains("maverick")) {
            return recognized(modelName, "llama4-scout", 10_000_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("qwen") && lower.contains("2.5") && lower.contains("coder")) {
            return recognized(modelName, "qwen2.5coder", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, true, ReasoningCapabilities.none());
        }
        if (lower.contains("qwen") && (lower.contains("3.5") || lower.contains("3-5"))) {
            return recognized(modelName, "qwen3.5", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.thinkTags(true, true, 8_192));
        }
        if (lower.contains("qwen") && lower.contains("3")) {
            return recognized(modelName, "qwen3", 32_768, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.thinkTags(true, true, 8_192));
        }
        if (lower.contains("qwen") || lower.contains("qwq")) {
            return recognized(modelName, "qwq", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.thinkTags(false, true, 8_192));
        }
        if (lower.contains("phi4")) {
            return recognized(modelName, "phi4", 16_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.thinkTags(true, true, 4_096));
        }
        if (lower.contains("codestral")) {
            return recognized(modelName, "codestral", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, true, ReasoningCapabilities.none());
        }
        if (lower.contains("devstral")) {
            return recognized(modelName, "devstral", 131_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("gemma")) {
            return recognized(modelName, "gemma", 32_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("starcoder2")) {
            return recognized(modelName, "starcoder2", 128_000, 8_192, SystemMessageSupport.NONE, toolFormat, true, ReasoningCapabilities.none());
        }
        if (lower.contains("openhands")) {
            return recognized(modelName, "openhands-lm-32b", 128_000, 4_096, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (lower.contains("quasar") || lower.contains("quaser")) {
            return recognized(modelName, "quasar", 1_000_000, 32_000, SystemMessageSupport.SYSTEM_ROLE, toolFormat, false, ReasoningCapabilities.none());
        }
        if (isGpt5Family(lower)) {
            // Conservative floor for current and future GPT-5 aliases. Do not
            // enable optional reasoning parameters solely from a model name.
            return recognized(modelName, "gpt-5-family", 128_000, 16_384,
                    SystemMessageSupport.SYSTEM_ROLE, toolFormat, false,
                    ReasoningCapabilities.none());
        }
        if (lower.contains("gpt-oss")) {
            return recognized(modelName, "gpt-oss", 128_000, 8_192, SystemMessageSupport.SYSTEM_ROLE, ToolFormat.OPENAI_STYLE, false, ReasoningCapabilities.thinkTags(true, true, 8_192));
        }
        if (lower.contains("gpt") && lower.contains("mini") && (lower.contains("4.1") || lower.contains("4-1"))) {
            return exactForRequestedName(EXACT.get(key("openai", "gpt-4.1-mini")), modelName);
        }
        if (lower.contains("gpt") && lower.contains("nano") && (lower.contains("4.1") || lower.contains("4-1"))) {
            return exactForRequestedName(EXACT.get(key("openai", "gpt-4.1-nano")), modelName);
        }
        if (lower.contains("gpt") && (lower.contains("4.1") || lower.contains("4-1"))) {
            return exactForRequestedName(EXACT.get(key("openai", "gpt-4.1")), modelName);
        }
        if (lower.contains("4o") && lower.contains("mini")) {
            return exactForRequestedName(EXACT.get(key("openai", "gpt-4o-mini")), modelName);
        }
        if (lower.contains("4o")) {
            return exactForRequestedName(EXACT.get(key("openai", "gpt-4o")), modelName);
        }
        if (lower.contains("o3")) {
            return exactForRequestedName(EXACT.get(key("openai", "o3")), modelName);
        }
        if (lower.contains("o4") && lower.contains("mini")) {
            return exactForRequestedName(EXACT.get(key("openai", "o4-mini")), modelName);
        }
        return null;
    }

    private static ToolFormat defaultToolFormat(String providerId) {
        String provider = providerId == null ? "" : providerId.toLowerCase(Locale.US);
        if ("anthropic".equals(provider)) {
            return ToolFormat.ANTHROPIC_STYLE;
        }
        if ("gemini".equals(provider)) {
            return ToolFormat.GEMINI_STYLE;
        }
        if ("ollama".equals(provider) || "vllm".equals(provider)
                || "lm_studio".equals(provider)) {
            return ToolFormat.XML_FALLBACK;
        }
        // OpenAI-compatible and LiteLLM endpoints advertise native tool support
        // in ProviderConfig. Unknown model names must not silently downgrade to XML.
        return ToolFormat.OPENAI_STYLE;
    }

    private static SystemMessageSupport defaultSystemMessageSupport(String providerId) {
        String provider = providerId == null ? "" : providerId.toLowerCase(Locale.US);
        if ("anthropic".equals(provider)) {
            return SystemMessageSupport.SEPARATED;
        }
        return SystemMessageSupport.SYSTEM_ROLE;
    }

    private static boolean isGpt5Family(String lowerModelName) {
        if (lowerModelName == null || lowerModelName.isEmpty()) {
            return false;
        }
        return containsVersionFamily(lowerModelName, "gpt-5")
                || containsVersionFamily(lowerModelName, "gpt5");
    }

    private static boolean containsVersionFamily(String value, String marker) {
        int from = 0;
        while (from < value.length()) {
            int index = value.indexOf(marker, from);
            if (index < 0) {
                return false;
            }
            int after = index + marker.length();
            if (after >= value.length() || !Character.isDigit(value.charAt(after))) {
                return true;
            }
            from = after;
        }
        return false;
    }

    private static Capabilities recognized(String modelName,
                                           String recognizedModelName,
                                           int contextWindow,
                                           int reservedOutput,
                                           SystemMessageSupport systemMessageSupport,
                                           ToolFormat toolFormat,
                                           boolean supportsFim,
                                           ReasoningCapabilities reasoningCapabilities) {
        return new Capabilities(modelName, recognizedModelName, false, contextWindow, reservedOutput,
                systemMessageSupport, toolFormat, supportsFim, reasoningCapabilities);
    }

    private static String key(String providerId, String modelName) {
        return (providerId == null ? "" : providerId.toLowerCase(Locale.US))
                + "/"
                + (modelName == null ? "" : modelName.toLowerCase(Locale.US));
    }
}
