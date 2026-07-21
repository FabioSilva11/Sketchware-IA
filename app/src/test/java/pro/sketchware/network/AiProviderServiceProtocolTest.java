package pro.sketchware.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import pro.sketchware.activities.chat.ContextBuilder;

public class AiProviderServiceProtocolTest {

    @Test
    public void xmlExtractionIsEnabledOnlyForXmlFallbackRequests() {
        assertTrue(AiProviderService.shouldExtractXmlToolCall(result(
                ContextBuilder.ProviderFormat.XML_FALLBACK), false));
        assertFalse(AiProviderService.shouldExtractXmlToolCall(result(
                ContextBuilder.ProviderFormat.XML_FALLBACK), true));
        assertFalse(AiProviderService.shouldExtractXmlToolCall(result(
                ContextBuilder.ProviderFormat.OPENAI), false));
        assertFalse(AiProviderService.shouldExtractXmlToolCall(result(
                ContextBuilder.ProviderFormat.ANTHROPIC), false));
        assertFalse(AiProviderService.shouldExtractXmlToolCall(result(
                ContextBuilder.ProviderFormat.GEMINI), false));
        assertFalse(AiProviderService.shouldExtractXmlToolCall(null, false));
    }

    private static ContextBuilder.Result result(ContextBuilder.ProviderFormat format) {
        return new ContextBuilder.Result("", new JSONArray(), 0, format);
    }
}
