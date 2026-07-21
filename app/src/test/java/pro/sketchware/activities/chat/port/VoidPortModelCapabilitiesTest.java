package pro.sketchware.activities.chat.port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoidPortModelCapabilitiesTest {

    @Test
    public void unknownOpenAiCompatibleProvidersUseNativeTools() {
        assertEquals(VoidPortModelCapabilities.ToolFormat.OPENAI_STYLE,
                VoidPortModelCapabilities.expectedToolFormat(
                        "openai_compatible", "future-model-alias"));
        assertEquals(VoidPortModelCapabilities.ToolFormat.OPENAI_STYLE,
                VoidPortModelCapabilities.expectedToolFormat(
                        "litellm", "future-model-alias"));
    }

    @Test
    public void unknownLocalModelsRemainOnXmlFallback() {
        assertEquals(VoidPortModelCapabilities.ToolFormat.XML_FALLBACK,
                VoidPortModelCapabilities.expectedToolFormat("ollama", "local-custom"));
        assertEquals(VoidPortModelCapabilities.ToolFormat.XML_FALLBACK,
                VoidPortModelCapabilities.expectedToolFormat("vllm", "local-custom"));
        assertEquals(VoidPortModelCapabilities.ToolFormat.XML_FALLBACK,
                VoidPortModelCapabilities.expectedToolFormat("lm_studio", "local-custom"));
    }

    @Test
    public void gpt5AliasesReceiveConservativeRecognizedCapabilities() {
        VoidPortModelCapabilities.Capabilities capabilities =
                VoidPortModelCapabilities.getModelCapabilities(
                        "openai_compatible", "gpt-5.5-custom");

        assertFalse(capabilities.unrecognizedModel);
        assertEquals("gpt-5-family", capabilities.recognizedModelName);
        assertEquals(VoidPortModelCapabilities.ToolFormat.OPENAI_STYLE,
                capabilities.toolFormat);
        assertTrue(capabilities.contextWindow >= 128_000);
        assertTrue(capabilities.reservedOutputTokenSpace >= 16_384);
    }

    @Test
    public void localGpt5AliasStillUsesXmlFallback() {
        VoidPortModelCapabilities.Capabilities capabilities =
                VoidPortModelCapabilities.getModelCapabilities("ollama", "gpt-5-local");

        assertEquals(VoidPortModelCapabilities.ToolFormat.XML_FALLBACK,
                capabilities.toolFormat);
    }
}
