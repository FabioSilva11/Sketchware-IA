package pro.sketchware.network.provider;

import okhttp3.Headers;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderFamily;

public final class AnthropicProviderAdapter extends BaseAiProviderAdapter {
    @Override
    public ProviderFamily family() {
        return ProviderFamily.ANTHROPIC;
    }

    @Override
    public Headers headers(ProviderConfig config) {
        Headers.Builder headers = jsonHeaders(config).set("anthropic-version", "2023-06-01");
        if (config != null && !config.apiKey.isEmpty()) {
            headers.set("x-api-key", config.apiKey);
        }
        return headers.build();
    }

    @Override
    public String streamingUrl(ProviderConfig config, String modelName) {
        return config == null ? "" : config.baseUrl;
    }
}
