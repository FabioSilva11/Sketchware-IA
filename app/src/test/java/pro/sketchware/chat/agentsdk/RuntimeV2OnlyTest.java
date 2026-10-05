package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.AgentManager;
import pro.sketchware.chat.AiOperationContext;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.toolcalling.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * V2-only runtime tests: legacy-free API parity (async approvals consultable
 * by the host UI) and the bridge contract for the migrated host
 * (AgentManager-delegates-to-runtime migration path).
 */
public class RuntimeV2OnlyTest {

    // ------------------------------------------------------------------
    // Consultable pending approval (Codex ReviewDecision parity)
    // ------------------------------------------------------------------

    @Test
    public void pendingApproval_isConsultableAndResolvableByHost() throws Exception {
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("edit_file", "{\"uri\":\"a.txt\"}"),
                FakeAgentLlmGateway.ScriptedTurn.text("done"));

        CompletableFuture<PermissionDecision> decision = new CompletableFuture<>();
        PolicyDenialCollector events = new PolicyDenialCollector();
        PermissionLayer layer = new PermissionLayer(ToolPolicy.interactive(),
                new ApprovalHandler() {
                    @Override
                    public PermissionDecision onRequest(PermissionRequest request) {
                        try {
                            // Blocks the responder thread until the host
                            // (the test main path) completes the decision.
                            return decision.get(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            return PermissionDecision.DENY;
                        }
                    }
                }, events.stream);

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .permissions(layer)
                .toolRegistry(registryWithApprovalTool("edit_file", new java.util.concurrent.atomic.AtomicBoolean(false)))
                .maxTurns(4)
                .build();
        Agent agent = Agent.Builder.forName("a", "i").build();

        // Run on a thread; the runtime parks on the approval handler.
        Thread runThread = new Thread(() -> runtime.run(agent, "go", "sc1"));
        runThread.start();
        Thread.sleep(150);

        AgentRuntime.PendingApproval pending = runtime.currentPendingApproval();
        assertNotNull("host must be able to see the pending approval", pending);
        assertEquals("edit_file", pending.getTool());
        assertEquals("not yet decided", ApprovalHandler.ApprovalState.PENDING,
                pending.peekState());

        // Item 16: the host resolves EXPLICITLY by requestId.
        assertTrue(runtime.resolveApproval(pending.getRequestId(), PermissionDecision.ALLOW));
        runThread.join(5000);
        assertFalse(runThread.isAlive());

        assertFalse("denial must not be emitted for an allowed tool",
                events.policyDenied.stream().anyMatch(e -> e.getTool().equals("edit_file")));
        assertFalse("resolution event must exist", events.resolved.isEmpty());
    }

    @Test
    public void timedOutApproval_failsClosed() throws Exception {
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("edit_file", "{}"),
                FakeAgentLlmGateway.ScriptedTurn.text("done"));

        PolicyDenialCollector events = new PolicyDenialCollector();
        PermissionLayer layer = new PermissionLayer(ToolPolicy.interactive(),
                new ApprovalHandler() {
                    @Override
                    public PermissionDecision onRequest(PermissionRequest request) {
                        try {
                            Thread.sleep(400);
                        } catch (InterruptedException ignored) {
                        }
                        return PermissionDecision.DENY;
                    }

                    @Override
                    public long timeoutMs() {
                        return 100; // expires before the handler answers
                    }
                }, events.stream);

        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean(false);
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .permissions(layer)
                .toolRegistry(registryWithApprovalTool("edit_file", executed))
                .maxTurns(4)
                .build();
        RunResult result = runtime.run(
                Agent.Builder.forName("a", "i").build(), "go", "sc1");

        assertTrue(result.isSuccessful());
        assertFalse("tool must not have executed after timeout", executed.get());
        assertFalse("timeout must resolve as a recorded denial",
                events.resolved.isEmpty());
        assertFalse(events.resolved.get(0).isAllowed());
    }

    // ------------------------------------------------------------------
    // Host bridge: AgentManager delegates to the runtime (sync UI path)
    // ------------------------------------------------------------------

    @Test
    public void bridge_deliversMessagesAndBlocksForSyncHosts() throws Exception {
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("legacy-free answer"));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(new pro.sketchware.chat.agentsdk.tools.AxionToolRegistry())
                .build();

        List<ChatMessage> added = new ArrayList<>();
        List<ChatMessage> updated = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        BlockingQueue<Boolean> finished = new LinkedBlockingQueue<>(1);
        AgentManager.HostBridge bridge = new AgentManager.HostBridge(runtime,
                () -> Agent.Builder.forName("a", "i").build(),
                new AgentManager.AgentListener() {
                    @Override
                    public void onMessageAdded(ChatMessage message) {
                        added.add(message);
                    }

                    @Override
                    public void onMessageUpdated(ChatMessage message) {
                        updated.add(message);
                    }

                    @Override
                    public void onMessageRemoved(ChatMessage message, int index) {
                    }

                    @Override
                    public void onStatusChanged(String status) {
                        statuses.add(status);
                    }

                    @Override
                    public void onDebug(String message) {
                    }

                    @Override
                    public void onProcessingFinished() {
                        finished.offer(Boolean.TRUE);
                    }

                    @Override
                    public void onToolExecuted(String toolName, boolean isMutation) {
                    }

                    @Override
                    public void onError(String error) {
                    }
                }, "sc_bridge", Runnable::run);

        bridge.processUserMessage("hello bridge");

        assertTrue("run must finish and unblock the sync host",
                finished.poll(5, TimeUnit.SECONDS) == Boolean.TRUE);
        assertEquals("legacy-free answer", bridge.lastAssistantText());
        assertFalse(added.isEmpty());
        assertFalse("user message must be echoed to the UI", added.get(0).isBot());
        assertTrue("assistant final text must reach the UI",
                updated.stream().anyMatch(ChatMessage::isBot));
    }

    @Test
    public void bridge_showsThinkingBubble_whenTurnGoesStraightToToolCall_andRemovesEmptyOne() throws Exception {
        // Scripted turn 1: the model calls a tool with NO text before it (the
        // common "tool-first" case). Turn 2: a final text answer.
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("read_file", "{\"uri\":\"a.txt\"}"),
                FakeAgentLlmGateway.ScriptedTurn.text("here is the file"));

        pro.sketchware.chat.agentsdk.tools.AxionToolRegistry registry =
                new pro.sketchware.chat.agentsdk.tools.AxionToolRegistry();
        registry.register(pro.sketchware.chat.agentsdk.tools.ToolRegistration.function(
                        "read_file", "stub read", new JSONObject())
                .executor(context -> AgentToolResult.success("file contents"))
                .source("test")
                .capability(pro.sketchware.chat.agentsdk.ToolCapability.READ)
                .build());

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .permissions(new PermissionLayer(ToolPolicy.permissive(), request -> PermissionDecision.ALLOW,
                        new EventStream(Runnable::run, 16)))
                .toolRegistry(registry)
                .maxTurns(4)
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        List<ChatMessage> added = new ArrayList<>();
        List<ChatMessage> removed = new ArrayList<>();
        BlockingQueue<Boolean> finished = new LinkedBlockingQueue<>(1);
        AgentManager.HostBridge bridge = new AgentManager.HostBridge(runtime,
                () -> Agent.Builder.forName("a", "i").build(),
                new AgentManager.AgentListener() {
                    @Override
                    public void onMessageAdded(ChatMessage message) {
                        added.add(message);
                    }

                    @Override
                    public void onMessageUpdated(ChatMessage message) {
                    }

                    @Override
                    public void onMessageRemoved(ChatMessage message, int index) {
                        removed.add(message);
                    }

                    @Override
                    public void onStatusChanged(String status) {
                    }

                    @Override
                    public void onDebug(String message) {
                    }

                    @Override
                    public void onProcessingFinished() {
                        finished.offer(Boolean.TRUE);
                    }

                    @Override
                    public void onToolExecuted(String toolName, boolean isMutation) {
                    }

                    @Override
                    public void onError(String error) {
                    }
                }, "sc_thinking", Runnable::run);
        bridge.attachMessages(messages);

        bridge.processUserMessage("read the file please");

        assertTrue("run must finish", finished.poll(5, TimeUnit.SECONDS) == Boolean.TRUE);

        // The transient "thinking" bubble must have been created (item 29/31: it
        // must show up even though the turn went straight into a tool call, with
        // no assistant text delta before it)...
        assertTrue("a placeholder bot bubble must have been added before the tool ran",
                added.stream().anyMatch(m -> m.isBot() && !m.hasDisplayContent()));
        // ...and then removed once it turned out to be empty (never got real
        // content before the tool call closed it), instead of being left behind
        // as an invisible, persisted empty message.
        assertTrue("the empty placeholder must be removed rather than left in history",
                removed.stream().anyMatch(m -> m.isBot()));

        // The real final answer must still have reached the UI as its own bubble.
        assertEquals("here is the file", bridge.lastAssistantText());
        assertTrue("final answer bubble must be a bot message with real content",
                added.stream().anyMatch(m -> m.isBot() && "here is the file".equals(m.getDisplayContent())));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A registry carrying ONE approval-gated tool ({@code edit_file}). */
    private static pro.sketchware.chat.agentsdk.tools.AxionToolRegistry
            registryWithApprovalTool(String name, java.util.concurrent.atomic.AtomicBoolean executed) {
        pro.sketchware.chat.agentsdk.tools.AxionToolRegistry registry =
                new pro.sketchware.chat.agentsdk.tools.AxionToolRegistry();
        registry.register(pro.sketchware.chat.agentsdk.tools.ToolRegistration.function(
                        name, "stub", new JSONObject())
                .executor(context -> {
                    executed.set(true);
                    return AgentToolResult.success("ran");
                })
                .source("test")
                .fileMutation(true)
                .build());
        return registry;
    }

    /** Collector for denial/resolution events backed by a real sync EventStream. */
    private static final class PolicyDenialCollector {
        final List<AgentEvent.PolicyDenied> policyDenied = new ArrayList<>();
        final List<AgentEvent.PermissionResolved> resolved = new ArrayList<>();
        final EventStream stream = new EventStream(Runnable::run, 64);

        PolicyDenialCollector() {
            stream.subscribe(event -> {
                if (event instanceof AgentEvent.PolicyDenied) {
                    policyDenied.add((AgentEvent.PolicyDenied) event);
                } else if (event instanceof AgentEvent.PermissionResolved) {
                    resolved.add((AgentEvent.PermissionResolved) event);
                }
            });
        }
    }
}
