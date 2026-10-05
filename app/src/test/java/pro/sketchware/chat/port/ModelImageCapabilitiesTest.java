package pro.sketchware.chat.port;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModelImageCapabilitiesTest {
    @Test
    public void classifiesOfficialAndKnownVisionModels() {
        assertTrue(ModelImageCapabilities.supportsImageInput("openai", "gpt-5.6-sol"));
        assertTrue(ModelImageCapabilities.supportsImageInput("openai", "gpt-5-sol"));
        assertTrue(ModelImageCapabilities.supportsImageInput("anthropic", "claude-sonnet-4-0"));
        assertTrue(ModelImageCapabilities.supportsImageInput("gemini", "gemini-2.5-pro"));
        assertTrue(ModelImageCapabilities.supportsImageInput("groq", "meta-llama/llama-4-scout-17b-16e-instruct"));
        assertTrue(ModelImageCapabilities.supportsImageInput("mistral", "mistral-small-latest"));
        assertTrue(ModelImageCapabilities.supportsImageInput("openrouter", "google/gemini-2.5-flash"));
    }

    @Test
    public void rejectsKnownTextOnlyModels() {
        assertFalse(ModelImageCapabilities.supportsImageInput("openai", "text-embedding-3-large"));
        assertFalse(ModelImageCapabilities.supportsImageInput("ollama", "gpt-oss:120b"));
        assertFalse(ModelImageCapabilities.supportsImageInput("deepseek", "deepseek-chat"));
        assertFalse(ModelImageCapabilities.supportsImageInput("groq", "llama-3.3-70b-versatile"));
        assertFalse(ModelImageCapabilities.supportsImageInput("mistral", "codestral-latest"));
    }
}
