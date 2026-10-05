package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter;
import pro.sketchware.chat.agentsdk.tools.ToolCatalog;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.ToolSpec.Type;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production path (migration stage 2): AgentManager → AgentRuntimeFactory →
 * AxionToolRegistry → ToolCatalog → gateway → provider → tool call →
 * AxionToolRouter → executor.
 *
 * <p>The coordinator agent carries NO per-agent tools — the model-facing
 * catalog comes exclusively from the registry ({@code AgentRuntime} no longer
 * adapts agent tools at run time). Legacy tools with a newer contract
 * (run_command→exec_command, edit_file→apply_patch, ...) never surface, while
 * the remaining workspace read tools (read_file, ls_dir, search*, ...) reach
 * the catalog as first-class registry citizens.</p>
 */
public class AgentManagerProductionPathTest {

    private static final String COORDINATOR_INSTRUCTIONS =
            "You are the workspace coordinator. Use the available tools to "
                    + "explore, read, and modify project files to complete the user's task.";

    private static final List<String> REPLACED_LEGACY_NAMES = Arrays.asList(
            "run_command", "run_persistent_command", "open_persistent_terminal",
            "kill_persistent_terminal", "edit_file", "rewrite_file",
            "create_file_or_folder", "delete_file_or_folder");

    /** A fake gateway capturing the ToolCatalog overload call. */
    private static final class CaptureGateway implements AgentLlmGateway {
        final List<String> catalogNames = new ArrayList<>();
        final List<ChatMessage> history = new ArrayList<>();
        LlmTurnOutput next = new LlmTurnOutput("done", "", "stop", null);
        TurnProvider nextProvider;

        interface TurnProvider {
            LlmTurnOutput provide(String systemPrompt, ToolCatalog catalog,
                                  List<ChatMessage> messages,
                                  pro.sketchware.chat.AiOperationContext operationContext);
        }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt,
                                          ToolCatalog catalog,
                                          List<ChatMessage> messages,
                                          pro.sketchware.chat.AiOperationContext operationContext) {
            catalogNames.clear();
            for (ToolRegistration reg : catalog.registrations()) {
                catalogNames.add(reg.qualifiedName());
            }
            history.clear();
            history.addAll(messages);
            return nextProvider != null
                    ? nextProvider.provide(systemPrompt, catalog, messages, operationContext)
                    : next;
        }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt,
                                          org.json.JSONArray tools,
                                          List<ChatMessage> messages,
                                          pro.sketchware.chat.AiOperationContext operationContext) {
            throw new AssertionError("production path must use the ToolCatalog overload");
        }
    }

    private static final class ToolCallScript implements CaptureGateway.TurnProvider {
        private final AtomicInteger served = new AtomicInteger(0);
        private final String callName;
        private final String callArgs;

        ToolCallScript(String callName, String callArgs) {
            this.callName = callName;
            this.callArgs = callArgs;
        }

        @Override
        public LlmTurnOutput provide(String systemPrompt, ToolCatalog catalog,
                                     List<ChatMessage> messages,
                                     pro.sketchware.chat.AiOperationContext operationContext) {
            if (served.getAndIncrement() == 0) {
                return new LlmTurnOutput("", "", "tool_calls",
                        java.util.Collections.singletonList(
                                new pro.sketchware.chat.toolcalling.ToolCall(
                                        callName, callArgs, "call_" + callName)));
            }
            return new LlmTurnOutput("done", "", "stop", null);
        }
    }

    /** The production registry wiring: core Codex tools + workspace read tools. */
    private AxionToolRegistry productionRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        WorkspaceToolProvider.registerWorkspaceReadTools(registry);
        return registry;
    }

    private static Agent plainCoordinator() {
        return Agent.Builder.forName("coordinator", COORDINATOR_INSTRUCTIONS).build();
    }

    private static List<String> namesOf(List<ToolRegistration> registrations) {
        List<String> names = new ArrayList<>();
        for (ToolRegistration reg : registrations) {
            names.add(reg.qualifiedName());
        }
        return names;
    }

    @Test
    public void productionCatalogComesExclusivelyFromTheRegistry() {
        AxionToolRegistry registry = productionRegistry();
        ToolCatalog catalog = ToolCatalog.from(registry);
        List<String> names = namesOf(catalog.registrations());

        // Exact canonical equality: the catalog IS the registry's model-visible set.
        assertTrue("catalog must equal registry.modelVisibleTools()",
                names.equals(namesOf(registry.modelVisibleTools())));

        // New Codex-parity contracts are the only mutations/shell faces.
        assertTrue("core apply_patch must be present", names.contains("apply_patch"));
        // Sketchware exposes no shell: a command would run as the app, outside the project's workspace
        assertFalse("exec_command must not be exposed in Sketchware", names.contains("exec_command"));
        assertTrue("core update_plan must be present", names.contains("update_plan"));
        assertTrue("core request_user_input must be present", names.contains("request_user_input"));
        assertTrue("core get_context_remaining must be present", names.contains("get_context_remaining"));

        // Model-compatible legacy tools survive via the registry.
        assertTrue("read_file must survive as a registry citizen", names.contains("read_file"));
        assertTrue("ls_dir must survive as a registry citizen", names.contains("ls_dir"));

        // Replaced legacy names never surface next to their new contracts.
        for (String forbidden : REPLACED_LEGACY_NAMES) {
            assertFalse("legacy '" + forbidden + "' must NOT be model-facing", names.contains(forbidden));
        }
    }

    @Test
    public void agentManagerMountsACatalogFreeCoordinatorAndRunsAgainstTheRegistry() throws Exception {
        Agent coordinator = plainCoordinator();
        // The coordinator carries NO per-agent tools — the registry feeds the model.

        AxionToolRegistry registry = productionRegistry();
        CaptureGateway gateway = new CaptureGateway();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(registry)
                .maxTurns(1)
                .includeProjectInstructions(false)
                .build();

        RunResult result = runtime.run(coordinator, "hello", "sc-prod-2");
        assertTrue("registry run must complete: " + result.getOutput()
                + " | reason: " + result.getFailureReason(), result.isSuccessful());

        List<String> expected = namesOf(registry.modelVisibleTools());
        assertTrue("the gateway must have received the registry catalog",
                gateway.catalogNames.containsAll(expected) && gateway.catalogNames.size() == expected.size());
        assertFalse("run_command must never reach a provider", gateway.catalogNames.contains("run_command"));
    }

    @Test
    public void productionRouterExecutesCoreToolsThroughTheirBoundExecutors() {
        AxionToolRegistry registry = productionRegistry();
        EventStream events = new EventStream(r -> r.run(), 32);
        AxionToolRouter router = new AxionToolRouter(registry, null, events);
        RunContext context = RunContext.bare("sc-exec", "coordinator", null);

        // exec_command isn't registered in Sketchware: the router refuses it instead of running a shell.
        AxionToolRouter.Routed exec = router.route(new AxionToolRouter.Route(
                "c_exec", "exec_command", "{\"cmd\":\"echo hi\"}"), "sc-exec", context, null);
        assertNull("exec_command must not resolve in Sketchware", exec.registration());

        // update_plan executes its structured contract via ChatPlanManager.
        AxionToolRouter.Routed plan = router.route(new AxionToolRouter.Route(
                "c_plan", "update_plan",
                "{\"plan\":[{\"step\":\"Do the work\",\"status\":\"in_progress\"}]}"),
                "sc-exec", context, null);
        assertNotNull("update_plan must resolve to a registration", plan.registration());
        assertFalse("update_plan must execute cleanly: " + plan.result().output(), plan.result().isError());

        // request_user_input reaches its executor and fails closed without a channel.
        AxionToolRouter.Routed input = router.route(new AxionToolRouter.Route(
                "c_input", "request_user_input",
                "{\"questions\":[{\"id\":\"q1\",\"header\":\"Pick\",\"question\":\"Which?\","
                        + "\"options\":[{\"label\":\"A\"}]}]}"),
                "sc-exec", context, null);
        assertNotNull("request_user_input must resolve to a registration", input.registration());
        assertTrue("without a channel the executor must fail closed", input.result().isError());

        // apply_patch: FREEFORM raw-input executor bound to the run filesystem
        // (real mutations proven in ApplyPatchFreeformTest).
        ToolRegistration apply = registry.get("apply_patch");
        assertNotNull(apply);
        assertNotNull("apply_patch must have a bound FREEFORM executor", apply.executor());
        assertEquals("apply_patch must remain a FREEFORM spec", Type.FREEFORM, apply.spec().type());
    }

    @Test
    public void registriesAreSessionScopedAndIsolated() {
        AxionToolRegistry sessionA = productionRegistry();
        AxionToolRegistry sessionB = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(sessionB, null);

        assertTrue("session A keeps its compat tool",
                namesOf(ToolCatalog.from(sessionA).registrations()).contains("read_file"));
        assertFalse("a compat tool registered in session A must never leak into session B",
                namesOf(ToolCatalog.from(sessionB).registrations()).contains("read_file"));
    }

    @Test
    public void productionRuntimeRoutesATurnThroughTheRouterAndReturnsAToolResult() throws Exception {
        AxionToolRegistry registry = productionRegistry();
        CaptureGateway gateway = new CaptureGateway();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(registry)
                .maxTurns(2)
                .includeProjectInstructions(false)
                .build();

        gateway.nextProvider = new ToolCallScript("get_context_remaining", "{}");

        RunResult result = runtime.run(plainCoordinator(), "work", "sc-prod-4");
        assertTrue("registry run must complete: " + result.getOutput()
                + " | reason: " + result.getFailureReason(), result.isSuccessful());

        boolean sawToolResult = false;
        for (ChatMessage message : gateway.history) {
            if (message.getType() == ChatMessage.TYPE_TOOL) {
                sawToolResult = true;
            }
        }
        assertTrue("the structured call must be executed by the router (tool result in history)",
                sawToolResult);
        assertTrue(gateway.catalogNames.contains("get_context_remaining"));
        assertTrue(gateway.catalogNames.contains("read_file"));
        assertFalse("legacy run_command must not be provider-visible", gateway.catalogNames.contains("run_command"));
    }
}