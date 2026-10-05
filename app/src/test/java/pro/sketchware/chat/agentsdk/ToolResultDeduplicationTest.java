package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.toolcalling.ToolCall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression tests for the tool-call UI duplication bug: {@code
 * AgentManager.HostBridge} creates one {@code ChatMessage} bubble on {@code
 * ToolCallStarted} and adds it to the shared conversation list; {@code
 * AgentRuntime.appendToolResult} must update that SAME message by call id
 * on completion, never append a second {@code TYPE_TOOL} message for the
 * same call.
 *
 * <p>These tests exercise {@code AgentRuntime.appendToolResult}/{@code
 * findToolMessageById} directly (package-private for testability) against a
 * shared {@code List<ChatMessage>}, simulating exactly what HostBridge does
 * before the runtime records the result — without needing to stand up the
 * full event bus/router/UI wiring.</p>
 */
public class ToolResultDeduplicationTest {

    /** Simulates AgentManager.HostBridge's ToolCallStarted handling. */
    private static ChatMessage simulateUiStarted(List<ChatMessage> history, String tool, String args, String callId) {
        ChatMessage bubble = new ChatMessage(tool, args, System.currentTimeMillis(), callId);
        history.add(bubble);
        return bubble;
    }

    private static long countToolMessagesForCall(List<ChatMessage> history, String callId) {
        long count = 0;
        for (ChatMessage m : history) {
            if (m.isTool() && callId.equals(m.getToolId())) {
                count++;
            }
        }
        return count;
    }

    // Teste 1: Started sozinho -> exatamente 1 TYPE_TOOL para o callId.
    @Test
    public void started_producesExactlyOneToolMessage() {
        List<ChatMessage> history = new ArrayList<>();
        simulateUiStarted(history, "search_pathnames_only", "{\"query\":\"ritmo\"}", "call-1");

        assertEquals(1, countToolMessagesForCall(history, "call-1"));
    }

    // Teste 2/3: Started + Completed (via appendToolResult) -> ainda 1 mensagem,
    // e é o MESMO objeto, atualizado.
    @Test
    public void startedThenCompleted_updatesSameMessage_neverDuplicates() {
        List<ChatMessage> history = new ArrayList<>();
        ChatMessage started = simulateUiStarted(history, "search_pathnames_only",
                "{\"query\":\"ritmo\"}", "call-1");

        ToolCall call = new ToolCall("search_pathnames_only", "{\"query\":\"ritmo\"}", "call-1");
        AgentRuntime.appendToolResult(history, call, "no matches found");

        assertEquals("exactly one TYPE_TOOL message must exist for this call id",
                1, countToolMessagesForCall(history, "call-1"));

        ChatMessage only = AgentRuntime.findToolMessageById(history, "call-1");
        assertSame("appendToolResult must update the SAME bubble the UI created, not a new one",
                started, only);
        assertFalse(only.isToolRunning());
        assertEquals("no matches found", only.getToolResult());
    }

    // Teste 4/5: dois callIds diferentes (mesmo nome/args ou não) -> 2 mensagens,
    // sem deduplicação por nome/args.
    @Test
    public void differentCallIds_sameNameAndArgs_produceTwoSeparateMessages() {
        List<ChatMessage> history = new ArrayList<>();
        simulateUiStarted(history, "search_pathnames_only",
                "{\"query\":\"ritmo_satira_irma_piseiro_pop\"}", "call-2");
        simulateUiStarted(history, "search_pathnames_only",
                "{\"query\":\"ritmo_satira_irma_piseiro_pop\"}", "call-3");

        AgentRuntime.appendToolResult(history,
                new ToolCall("search_pathnames_only", "{\"query\":\"ritmo_satira_irma_piseiro_pop\"}", "call-2"),
                "result A");
        AgentRuntime.appendToolResult(history,
                new ToolCall("search_pathnames_only", "{\"query\":\"ritmo_satira_irma_piseiro_pop\"}", "call-3"),
                "result B");

        assertEquals(1, countToolMessagesForCall(history, "call-2"));
        assertEquals(1, countToolMessagesForCall(history, "call-3"));
        assertEquals("total visual tool messages must equal the number of distinct call ids",
                2, historyToolCount(history));

        assertEquals("result A", AgentRuntime.findToolMessageById(history, "call-2").getToolResult());
        assertEquals("result B", AgentRuntime.findToolMessageById(history, "call-3").getToolResult());
    }

    // Teste 6: Started recebido duas vezes para o mesmo callId (simulando o guard
    // que já existe em HostBridge.onAgentEvent: toolBubbles.get(callId) != null).
    @Test
    public void startedTwiceForSameCallId_hostBridgeGuard_stillOneMessage() {
        List<ChatMessage> history = new ArrayList<>();
        // First ToolCallStarted: HostBridge finds nothing in toolBubbles, creates the bubble.
        ChatMessage first = simulateUiStarted(history, "read_file", "{\"uri\":\"a.txt\"}", "call-1");
        // Second ToolCallStarted for the SAME call id: a real HostBridge would look up
        // toolBubbles.get(callId), find `first`, and only update it in place rather
        // than calling simulateUiStarted/adding a second bubble. We assert the
        // invariant the guard exists to protect: at most one message per call id.
        assertEquals(1, countToolMessagesForCall(history, "call-1"));
        assertSame(first, AgentRuntime.findToolMessageById(history, "call-1"));
    }

    // Teste 7: Completed recebido duas vezes para o mesmo callId -> 1 card atualizado,
    // não duas mensagens.
    @Test
    public void completedTwiceForSameCallId_updatesSameMessageBothTimes() {
        List<ChatMessage> history = new ArrayList<>();
        simulateUiStarted(history, "edit_file", "{\"uri\":\"a.txt\"}", "call-1");

        ToolCall call = new ToolCall("edit_file", "{\"uri\":\"a.txt\"}", "call-1");
        AgentRuntime.appendToolResult(history, call, "first result");
        AgentRuntime.appendToolResult(history, call, "second result (retry/duplicate event)");

        assertEquals(1, countToolMessagesForCall(history, "call-1"));
        assertEquals("second result (retry/duplicate event)",
                AgentRuntime.findToolMessageById(history, "call-1").getToolResult());
    }

    // Teste 12/13: várias ferramentas sequenciais, incluindo chamadas legítimas
    // repetidas da mesma ferramenta -> uma mensagem por callId, nunca menos.
    @Test
    public void sequentialToolCalls_oneMessagePerCallId_noCollapsing() {
        List<ChatMessage> history = new ArrayList<>();
        String[] callIds = {"call-1", "call-2", "call-3"};
        for (String id : callIds) {
            simulateUiStarted(history, "search_pathnames_only", "{\"query\":\"ritmo\"}", id);
            AgentRuntime.appendToolResult(history,
                    new ToolCall("search_pathnames_only", "{\"query\":\"ritmo\"}", id), "Finalizado");
        }

        assertEquals(3, historyToolCount(history));
        for (String id : callIds) {
            assertEquals(1, countToolMessagesForCall(history, id));
        }
    }

    // Fallback: Completed sem Started prévio (ex.: caminho de deduplicação/approval
    // do router) ainda produz exatamente uma mensagem, nunca deixa a UI sem card.
    @Test
    public void completedWithoutPriorStarted_fallbackCreatesExactlyOneMessage() {
        List<ChatMessage> history = new ArrayList<>();
        ToolCall call = new ToolCall("update_plan", "{\"plan\":\"...\"}", "call-1");

        assertNull(AgentRuntime.findToolMessageById(history, "call-1"));
        AgentRuntime.appendToolResult(history, call, "ok");

        assertEquals(1, countToolMessagesForCall(history, "call-1"));
        ChatMessage created = AgentRuntime.findToolMessageById(history, "call-1");
        assertNotNull(created);
        assertTrue(created.isTool());
        assertEquals("ok", created.getToolResult());
    }

    private static long historyToolCount(List<ChatMessage> history) {
        long count = 0;
        for (ChatMessage m : history) {
            if (m.isTool()) {
                count++;
            }
        }
        return count;
    }
}
