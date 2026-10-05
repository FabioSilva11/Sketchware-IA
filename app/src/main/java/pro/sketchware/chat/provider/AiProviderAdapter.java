package pro.sketchware.chat.provider;
import pro.sketchware.R;

import okhttp3.Headers;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderFamily;

/** Protocol-specific endpoint and authentication behavior. */
public interface AiProviderAdapter {
    ProviderFamily family();
    Headers headers(ProviderConfig config);
    String streamingUrl(ProviderConfig config, String modelName);

    /** Endpoint equivalente sem streaming. Por padrao e o mesmo endpoint. */
    default String nonStreamingUrl(ProviderConfig config, String modelName) {
        return streamingUrl(config, modelName);
    }
}
