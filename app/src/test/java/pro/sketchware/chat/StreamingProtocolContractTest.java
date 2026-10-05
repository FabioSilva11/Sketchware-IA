package pro.sketchware.chat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderConfig;
import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderFamily;
import pro.sketchware.chat.provider.GeminiProviderAdapter;

import org.json.JSONObject;
import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class StreamingProtocolContractTest {
    @Test
    public void geminiUsesOfficialSseEndpoint() {
        ProviderConfig config = new ProviderConfig(
                "gemini",
                ProviderFamily.GEMINI,
                "https://generativelanguage.googleapis.com/v1beta",
                "secret",
                new JSONObject(),
                true);

        String url = new GeminiProviderAdapter().streamingUrl(config, "gemini-test");

        assertTrue(url, url.contains("/models/gemini-test:streamGenerateContent"));
        assertTrue(url, url.contains("alt=sse"));
    }

    @Test
    public void onlyPlainJsonUsesNonStreamingFallback() {
        assertTrue(AiProviderService.isJsonResponse(response("application/json; charset=utf-8")));
        assertFalse(AiProviderService.isJsonResponse(response("text/event-stream")));
        assertFalse(AiProviderService.isJsonResponse(response("application/x-ndjson")));
        assertFalse(AiProviderService.isJsonResponse(response("")));
    }

    private static Response response(String contentType) {
        Response.Builder builder = new Response.Builder()
                .request(new Request.Builder().url("https://example.com/v1/chat/completions").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create("", MediaType.parse("application/octet-stream")));
        if (contentType != null && !contentType.isEmpty()) {
            builder.header("Content-Type", contentType);
        }
        return builder.build();
    }
}
