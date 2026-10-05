package pro.sketchware.chat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AgentManagerTokenPolicyTest {
    @Test
    public void recognizesOpenAiAndAnthropicTokenLimits() {
        assertTrue(AgentManager.isOutputTruncated("length"));
        assertTrue(AgentManager.isOutputTruncated("max_tokens"));
        assertTrue(AgentManager.isOutputTruncated("max_output_tokens"));
        assertFalse(AgentManager.isOutputTruncated("stop"));
        assertFalse(AgentManager.isOutputTruncated("end_turn"));
    }

    @Test
    public void estimatesAdditionalToolSchemaTokens() {
        assertEquals(0, AgentManager.estimateInputTokens(""));
        assertEquals(1, AgentManager.estimateInputTokens("abcd"));
        assertEquals(2, AgentManager.estimateInputTokens("abcde"));
    }
}
