package pro.sketchware.chat.provider;
import pro.sketchware.R;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderFamily;

public final class GeminiProviderAdapter extends BaseAiProviderAdapter {
    @Override
    public ProviderFamily family() {
        return ProviderFamily.GEMINI;
    }

    @Override
    public Headers headers(ProviderConfig config) {
        Headers.Builder headers = jsonHeaders(config);
        if (config != null && !config.apiKey.isEmpty()) {
            headers.set("x-goog-api-key", config.apiKey);
        }
        return headers.build();
    }

    @Override
    public String streamingUrl(ProviderConfig config, String modelName) {
        if (config == null) {
            return "";
        }
        HttpUrl base = HttpUrl.parse(config.baseUrl);
        if (base == null) {
            return "";
        }
        return base.newBuilder()
                .addPathSegment("models")
                .addPathSegment(modelName + ":streamGenerateContent")
                .addQueryParameter("alt", "sse")
                .build()
                .toString();
    }

    @Override
    public String nonStreamingUrl(ProviderConfig config, String modelName) {
        if (config == null) {
            return "";
        }
        HttpUrl base = HttpUrl.parse(config.baseUrl);
        if (base == null) {
            return "";
        }
        return base.newBuilder()
                .addPathSegment("models")
                .addPathSegment(modelName + ":generateContent")
                .build()
                .toString();
    }
}
