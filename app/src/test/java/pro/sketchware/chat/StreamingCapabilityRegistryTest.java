package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.port.VoidPortLlmMessage.ProviderFamily;

import org.junit.After;
import org.junit.Test;

public class StreamingCapabilityRegistryTest {
    @After
    public void clear() {
        StreamingCapabilityRegistry.clearForTests();
    }

    @Test
    public void defaultsToStreamingAndLearnsUnsupported() {
        String key = StreamingCapabilityRegistry.key("openai", "https://api.example/v1", "model-x");
        assertEquals(StreamingCapabilityRegistry.Capability.UNKNOWN,
                StreamingCapabilityRegistry.get(key));
        assertTrue(StreamingCapabilityRegistry.shouldUseStreaming(key));

        StreamingCapabilityRegistry.markUnsupported(key);
        assertEquals(StreamingCapabilityRegistry.Capability.UNSUPPORTED,
                StreamingCapabilityRegistry.get(key));
        assertFalse(StreamingCapabilityRegistry.shouldUseStreaming(key));

        StreamingCapabilityRegistry.markSupported(key);
        assertEquals(StreamingCapabilityRegistry.Capability.SUPPORTED,
                StreamingCapabilityRegistry.get(key));
        assertTrue(StreamingCapabilityRegistry.shouldUseStreaming(key));
    }

    @Test
    public void fallsBackOnlyForStreamingCapabilityErrors() {
        assertTrue(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.OPENAI_COMPATIBLE, 400,
                "Unsupported parameter: stream"));
        assertTrue(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.OPENAI_COMPATIBLE, 422,
                "stream is not supported for this model"));
        assertTrue(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.GEMINI, 404, "not found"));

        assertTrue(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.OPENAI_COMPATIBLE, 400,
                "{\"error\":{\"code\":\"stream_not_supported\",\"message\":\"O gateway gerenciado requer stream=false.\"}}"));
        assertTrue(StreamingCapabilityRegistry.isExplicitStreamingDisabledError(
                400,
                "{\"error\":{\"code\":\"stream_not_supported\",\"message\":\"O gateway gerenciado requer stream=false.\"}}"));

        assertFalse(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.OPENAI_COMPATIBLE, 400,
                "invalid max_tokens"));
        assertFalse(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.OPENAI_COMPATIBLE, 401,
                "unsupported stream"));
        assertFalse(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.OPENAI_COMPATIBLE, 429,
                "stream unavailable"));
        assertFalse(StreamingCapabilityRegistry.shouldAttemptFallback(
                ProviderFamily.OPENAI_COMPATIBLE, 503,
                "stream not available"));
    }
    @Test
    public void streamFalsePersistsUntilModelChangesThenResetsToTrue() {
        String modelA = StreamingCapabilityRegistry.key(
                "axion-managed", "https://gateway.example/v1", "model-a");
        String modelB = StreamingCapabilityRegistry.key(
                "axion-managed", "https://gateway.example/v1", "model-b");

        assertTrue(StreamingCapabilityRegistry.shouldUseStreamingForSelection(modelA));
        StreamingCapabilityRegistry.markUnsupported(modelA);
        assertFalse(StreamingCapabilityRegistry.shouldUseStreamingForSelection(modelA));
        assertFalse(StreamingCapabilityRegistry.shouldUseStreamingForSelection(modelA));

        // Trocar de modelo volta a tentar streaming.
        assertTrue(StreamingCapabilityRegistry.shouldUseStreamingForSelection(modelB));

        // Voltar ao modelo anterior tambem inicia uma nova tentativa em true.
        assertTrue(StreamingCapabilityRegistry.shouldUseStreamingForSelection(modelA));
    }

    @Test
    public void selectsJsonParserSafely() {
        assertTrue(StreamingCapabilityRegistry.shouldParseJsonBody(
                "application/json; charset=utf-8", true));
        assertTrue(StreamingCapabilityRegistry.shouldParseJsonBody(
                "application/problem+json", true));
        assertFalse(StreamingCapabilityRegistry.shouldParseJsonBody(
                "text/event-stream", false));
        assertFalse(StreamingCapabilityRegistry.shouldParseJsonBody(
                "application/x-ndjson", false));
        assertTrue(StreamingCapabilityRegistry.shouldParseJsonBody("", false));
        assertFalse(StreamingCapabilityRegistry.shouldParseJsonBody("", true));
    }

}
