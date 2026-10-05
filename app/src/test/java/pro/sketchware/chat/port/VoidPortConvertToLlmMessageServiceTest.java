package pro.sketchware.chat.port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoidPortConvertToLlmMessageServiceTest {

    @Test
    public void emptyAssistantContentDoesNotCreateVisibleProtocolText() {
        assertEquals("", VoidPortConvertToLlmMessageService.buildAssistantContent(
                "", "", false));
    }

    @Test
    public void recognizesLegacyMarkerAndItsStreamingPrefix() {
        assertTrue(VoidPortConvertToLlmMessageService.isProtocolEmptyMessage(
                "  (empty message) "));
        assertTrue(VoidPortConvertToLlmMessageService.isProtocolEmptyMessagePrefix(
                "(empty mes"));
        assertFalse(VoidPortConvertToLlmMessageService.isProtocolEmptyMessagePrefix(
                "real answer"));
    }

    @Test
    public void emptyToolResultUsesStructuredValue() {
        assertEquals(
                "{\"result\":null}",
                VoidPortConvertToLlmMessageService.nonEmptyToolResult(""));
    }

    /**
     * Regression test for the exact bug reported: two sibling tool messages
     * created in the SAME millisecond (e.g. two bubbles from a single turn's
     * parallel tool calls) with a missing/empty toolId used to collide on
     * {@code "call_" + timestamp}, handing the model a duplicate
     * tool_call_id for two genuinely different calls — which could make the
     * model see one call's cached success result attributed to a different,
     * still-unfinished call. stableToolId must never hand out the same
     * fallback id to two distinct messages, even when created at the exact
     * same millisecond.
     */
    @Test
    public void stableToolId_neverCollidesForDifferentMessagesAtSameTimestamp() {
        long sameTimestamp = 1_700_000_000_000L;
        pro.sketchware.chat.ChatMessage a =
                new pro.sketchware.chat.ChatMessage("read_file", "{}", sameTimestamp, "");
        pro.sketchware.chat.ChatMessage b =
                new pro.sketchware.chat.ChatMessage("edit_file", "{}", sameTimestamp, null);

        String idA = VoidPortConvertToLlmMessageService.stableToolId(a);
        String idB = VoidPortConvertToLlmMessageService.stableToolId(b);

        assertFalse("two distinct messages must never receive the same fallback tool id",
                idA.equals(idB));
    }

    /**
     * stableToolId must be genuinely stable: calling it again on the same
     * message (as happens every turn, since the whole history is
     * re-serialized each time) must return the SAME id it returned before,
     * not a fresh random one.
     */
    @Test
    public void stableToolId_isStableAcrossRepeatedCallsOnTheSameMessage() {
        pro.sketchware.chat.ChatMessage message =
                new pro.sketchware.chat.ChatMessage("ls_dir", "{}", 1_700_000_000_000L, "");

        String first = VoidPortConvertToLlmMessageService.stableToolId(message);
        String second = VoidPortConvertToLlmMessageService.stableToolId(message);

        assertEquals(first, second);
        assertEquals("the generated id must be persisted back onto the message",
                first, message.getToolId());
    }

    @Test
    public void stableToolId_keepsRealProviderIdUnchanged() {
        pro.sketchware.chat.ChatMessage message =
                new pro.sketchware.chat.ChatMessage("read_file", "{}", 1_700_000_000_000L, "call_abc123");

        assertEquals("call_abc123", VoidPortConvertToLlmMessageService.stableToolId(message));
    }
}
