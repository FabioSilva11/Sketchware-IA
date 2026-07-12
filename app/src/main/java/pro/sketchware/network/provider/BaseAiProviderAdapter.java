package pro.sketchware.network.provider;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Headers;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderConfig;

abstract class BaseAiProviderAdapter implements AiProviderAdapter {

    protected Headers.Builder jsonHeaders(ProviderConfig config) {
        Headers.Builder headers = new Headers.Builder().add("Content-Type", "application/json");
        JSONObject extraHeaders = config == null ? null : config.extraHeaders;
        JSONArray names = extraHeaders == null ? null : extraHeaders.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String name = names.optString(i, "").trim();
            if (!name.isEmpty()) {
                headers.set(name, extraHeaders.optString(name, ""));
            }
        }
        return headers;
    }
}
