package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.AgentManager;
import pro.sketchware.chat.ChatMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Real end-to-end chat assembly (stage item 1 — no wiring duplicated in a
 * test): {@code ChatActivity → AgentManager → AgentRuntime} runs through the
 * PRODUCTION factory ({@link AgentRuntimeFactory#createForChatWithGateway})
 * and the production {@link AgentManager.HostBridge} that
 * {@code AgentManager.forUi} mounts (same constructor, same subscription,
 * same {@code processUserMessage} round trip). Only the LLM is fake.
 *
 * <p>Proven end to end:</p>
 * <ul>
 *   <li>the factory's own {@code AxionToolRegistry → ToolCatalog} is what the
 *       gateway receives (core tools present, replaced legacy names absent);</li>
 *   <li>the factory's default permission layer ({@link PermissionConfig}
 *       WORKSPACE + ON_REQUEST) evaluates {@code get_context_remaining} as a
 *       safe READ tool and lets it run without parking;</li>
 *   <li>the call executes through the {@link AxionToolRouter} and its result
 *       lands in the shared conversation;</li>
 *   <li>a second host turn runs on the accumulated history.</li>
 * </ul>
 */
public class ChatRuntimeAssemblyE2ETest {

    private static final String COORDINATOR_INSTRUCTIONS =
            "You are the workspace coordinator. Use the available tools to "
                    + "explore, read, and modify project files to complete the user's task.";

    /** Resolves any tool call the runtime parks (kept for approval flows). */
    private static final class AutoApprover implements AgentManager.AgentListener {
        final List<String> approvedTools = Collections.synchronizedList(new ArrayList<>());
        final List<String> executedTools = Collections.synchronizedList(new ArrayList<>());
        final AgentRuntime runtime;
        final List<String> runErrors = Collections.synchronizedList(new ArrayList<>());
        final List<String> processingFailures = Collections.synchronizedList(new ArrayList<>());

        AutoApprover(AgentRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public void onMessageAdded(ChatMessage message) {
        }

        @Override
        public void onMessageUpdated(ChatMessage message) {
        }

        @Override
        public void onMessageRemoved(ChatMessage message, int index) {
        }

        @Override
        public void onStatusChanged(String status) {
        }

        @Override
        public void onDebug(String message) {
        }

        @Override
        public void onProcessingFinished() {
        }

        @Override
        public void onToolExecuted(String toolName, boolean isMutation) {
            executedTools.add(toolName);
        }

        @Override
        public void onError(String error) {
            runErrors.add(error == null ? "" : error);
        }

        @Override
        public void onApprovalRequired(String requestId, String toolName) {
            approvedTools.add(toolName == null ? "" : toolName);
            boolean resolved = runtime.resolveApproval(
                    requestId, PermissionDecision.ALLOW);
            if (!resolved) {
                processingFailures.add("approval not resolvable: " + toolName);
            }
        }

        @Override
        public void onUserFacingError(
                pro.sketchware.chat.UserFacingError error,
                String requestId) {
            runErrors.add(error == null ? "user-facing error" : error.getTitle());
        }
    }

    @Test
    public void chatAssemblyRunsRealExecutionThroughTheFactory() throws Exception {
        Agent coordinator = Agent.Builder.forName("coordinator", COORDINATOR_INSTRUCTIONS).build();
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("get_context_remaining", "{}"),
                FakeAgentLlmGateway.ScriptedTurn.text("done first"),
                FakeAgentLlmGateway.ScriptedTurn.text("done second"));

        AgentRuntime runtime = AgentRuntimeFactory.createForChatWithGateway(gateway);
        AutoApprover listener = new AutoApprover(runtime);

        // The exact HostBridge wiring AgentManager.forUi mounts (main looper
        // replaced by a synchronous executor for the headless JVM).
        List<ChatMessage> conversation = new ArrayList<>();
        AgentManager.HostBridge bridge = new AgentManager.HostBridge(
                runtime, () -> coordinator, listener, "sc-e2e-1", Runnable::run);
        bridge.attachMessages(conversation);

        RunResult first = bridge.processUserMessage("Check how much context we have left.");
        assertTrue("first run must complete: " + safe(first.getFailureReason()),
                first.isSuccessful());
        assertEquals("done first", bridge.lastAssistantText());
        assertEquals("two LLM turns consumed (tool call then answer)",
                2, gateway.turnsConsumed());

        // The provider really received the registry catalog — core Codex tools
        // present, replaced legacy names genuinely absent.
        assertTrue("get_context_remaining must be exposed",
                gateway.wasToolExposed("get_context_remaining"));
        assertFalse("exec_command must not be exposed in Sketchware", gateway.wasToolExposed("exec_command"));
        assertTrue("apply_patch must be exposed", gateway.wasToolExposed("apply_patch"));
        assertTrue("clock.curr_time must be exposed", gateway.wasToolExposed("clock.curr_time"));
        assertFalse("run_command must never reach a provider",
                gateway.wasToolExposed("run_command"));

        // The default WORKSPACE + ON_REQUEST layer classifies
        // get_context_remaining as a safe READ tool: it runs straight through
        // without parking (zero approval prompts), unlike a SHELL/WRITE tool
        // which would wait for the host resolver.
        assertTrue("safe read tool must not require approval",
                listener.approvedTools.isEmpty());
        assertTrue("execution reported to the UI", listener.executedTools.contains("get_context_remaining"));
        assertTrue("no resolution failures", listener.processingFailures.isEmpty());

        // The tool really executed through the router: result landed as a tool
        // message in the shared conversation.
        ChatMessage toolMessage = toolResultIn(conversation);
        assertNotNull("get_context_remaining result must be in the conversation", toolMessage);
        assertFalse("tool result must carry output", toolMessage.getToolResult() == null
                || toolMessage.getToolResult().trim().isEmpty());

        // A second host turn runs on the accumulated multi-turn history.
        RunResult second = bridge.processUserMessage("Keep going.");
        assertTrue("second run must complete: " + safe(second.getFailureReason()),
                second.isSuccessful());
        assertEquals("done second", bridge.lastAssistantText());
        assertEquals("third LLM turn consumed", 3, gateway.turnsConsumed());

        bridge.close();
    }

    private static ChatMessage toolResultIn(List<ChatMessage> conversation) {
        ChatMessage withOutput = null;
        synchronized (conversation) {
            for (ChatMessage message : conversation) {
                if (message != null && message.getType() == ChatMessage.TYPE_TOOL
                        && "get_context_remaining".equals(message.getToolName())) {
                    if (withOutput == null) {
                        withOutput = message;
                    }
                    String output = message.getToolResult();
                    if (output != null && !output.trim().isEmpty()) {
                        return message;
                    }
                }
            }
        }
        // Prefer the message that actually carries the result output; the
        // approval bubble may appear first and has no output attached.
        return withOutput;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}