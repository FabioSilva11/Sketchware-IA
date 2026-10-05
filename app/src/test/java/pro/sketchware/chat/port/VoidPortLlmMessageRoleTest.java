package pro.sketchware.chat.port;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VoidPortLlmMessageRoleTest {

    @Test
    public void usesDeveloperOnlyForModernOfficialOpenAiModels() {
        assertEquals("developer", VoidPortLlmMessage.instructionRole("openai", "gpt-5.6"));
        assertEquals("developer", VoidPortLlmMessage.instructionRole("openai", "gpt-4.1"));
        assertEquals("system", VoidPortLlmMessage.instructionRole("openai", "gpt-4o"));
        assertEquals("system", VoidPortLlmMessage.instructionRole("openai_compatible", "gpt-5.6"));
        assertEquals("system", VoidPortLlmMessage.instructionRole("groq", "openai/gpt-oss-120b"));
    }
}
