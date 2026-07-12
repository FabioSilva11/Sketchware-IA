package pro.sketchware.network.provider;

import okhttp3.Headers;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderFamily;

/** Protocol-specific endpoint and authentication behavior. */
public interface AiProviderAdapter {
    ProviderFamily family();
    Headers headers(ProviderConfig config);
    String streamingUrl(ProviderConfig config, String modelName);
}
