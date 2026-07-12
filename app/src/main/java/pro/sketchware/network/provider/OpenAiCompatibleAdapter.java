package pro.sketchware.network.provider;

import okhttp3.Headers;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderFamily;

public final class OpenAiCompatibleAdapter extends BaseAiProviderAdapter {
    @Override
    public ProviderFamily family() {
        return ProviderFamily.OPENAI_COMPATIBLE;
    }

    @Override
    public Headers headers(ProviderConfig config) {
        Headers.Builder headers = jsonHeaders(config);
        if (config != null && !config.apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + config.apiKey);
        }
        return headers.build();
    }

    @Override
    public String streamingUrl(ProviderConfig config, String modelName) {
        return VoidPortLlmMessage.resolveRequestUrl(config, modelName);
    }
}
