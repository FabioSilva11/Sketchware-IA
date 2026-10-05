package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.agentsdk.tools.ApplyPatchExecutor;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;
import pro.sketchware.chat.workspace.LocalFolderWorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-path tests of the new permission model (item 13): the factory
 * defaults to WORKSPACE + ON_REQUEST, reads auto-run, risky tools fail closed
 * without a handler, and {@code AgentRuntime#updatePermissionConfig} swaps the
 * enforcement mid-life — full access then executes without approval.
 */
public class PermissionModelProductionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static final String ORIGINAL =
            "package demo;\n\n"
            + "public class Counter {\n"
            + "    private final long count;\n\n"
            + "    public Counter(long count) {\n"
            + "        this.count = count;\n"
            + "    }\n"
            + "}\n";

    private static final String PATCHED =
            "package demo;\n\n"
            + "public class Counter {\n"
            + "    private final long count;\n\n"
            + "    public Counter(long count) {\n"
            + "        this.count = Math.max(0, count);\n"
            + "    }\n"
            + "}\n";

    private static final String PATCH =
            "*** Begin Patch\n"
            + "*** Update File: src/demo/Counter.kt\n"
            + "@@ public Counter\n"
            + "-        this.count = count;\n"
            + "+        this.count = Math.max(0, count);\n"
            + "*** End Patch\n";

    private WorkspaceFileSystem fs;
    private String workspacePath;

    @Before
    public void setUp() throws IOException {
        File root = temp.newFolder("workspace");
        workspacePath = root.getAbsolutePath();
        fs = new LocalFolderWorkspaceFileSystem(root);
        fs.writeText("src/demo/Counter.kt", ORIGINAL);
    }

    private Agent scriptedAgent() {
        return Agent.Builder.forName("coordinator", "You fix workspace bugs.").build();
    }

    private AxionToolRegistry registry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration read = ToolRegistration.function(
                        "read_file", "Read a file.", new org.json.JSONObject())
                .executor(context -> AgentToolResult.success("class Counter { ... }"))
                .source("test")
                .capability(ToolCapability.READ)
                .build();
        ToolRegistration patch = ToolRegistration.freeform(
                        "apply_patch",
                        "Edit files. FREEFORM.",
                        WorkspaceToolProvider.APPLY_PATCH_GRAMMAR,
                        new ApplyPatchExecutor((context, stream, scId) -> new ApplyPatchTool(
                                scId == null || scId.isEmpty() ? context.scId() : scId,
                                stream, fs)))
                .source("core")
                .capabilities(ToolCapability.WORKSPACE_WRITE, ToolCapability.DESTRUCTIVE)
                .build();
        registry.register(read);
        registry.register(patch);
        return registry;
    }

    /**
     * Runs one scripted turn over a runtime whose PermissionLayer uses the
     * SAME EventStream as the runtime (so enforcement events are observable).
     */
    private List<AgentEvent> runWith(PermissionConfig config, FakeAgentLlmGateway gateway) {
        EventStream stream = new EventStream(Runnable::run, 128);
        List<AgentEvent> received = new ArrayList<>();
        stream.subscribe(received::add);
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(stream)
                .toolRegistry(registry())
                .permissions(new PermissionLayer(config, null, stream))
                .maxTurns(8)
                .build();
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(
                "Fix something.", ChatMessage.TYPE_USER, System.currentTimeMillis()));
        RunResult result = runtime.run(scriptedAgent(), history, "sc_prod");
        assertNotNull(result);
        return received;
    }

    @Test
    public void factoryRunsWithTheSafeDefaultWorkspaceOnRequest() {
        AgentRuntime runtime = AgentRuntimeFactory.createForChatWithGateway(
                new FakeAgentLlmGateway(FakeAgentLlmGateway.ScriptedTurn.text("hello")));
        assertEquals(PermissionConfig.workspaceRequest(), runtime.permissionConfig());
        assertEquals(PermissionProfile.WORKSPACE, runtime.permissionConfig().profile());
        assertEquals(ApprovalPolicy.ON_REQUEST, runtime.permissionConfig().approvalPolicy());
    }

    @Test
    public void updatePermissionConfigSwapsEnforcementAtRuntime() {
        AgentRuntime runtime = AgentRuntimeFactory.createForChatWithGateway(
                new FakeAgentLlmGateway(FakeAgentLlmGateway.ScriptedTurn.text("hello")));
        runtime.updatePermissionConfig(PermissionConfig.readOnly());
        assertEquals(PermissionProfile.READ_ONLY, runtime.permissionConfig().profile());
        runtime.updatePermissionConfig(PermissionConfig.fullAccess());
        assertEquals(PermissionProfile.DANGER_FULL_ACCESS, runtime.permissionConfig().profile());
        assertEquals(ApprovalPolicy.NEVER, runtime.permissionConfig().approvalPolicy());
    }

    @Test
    public void defaultWorkspaceRequestRunsSafeReadsWithoutParking() {
        List<AgentEvent> events = runWith(
                PermissionConfig.workspaceRequest(),
                new FakeAgentLlmGateway(
                        FakeAgentLlmGateway.ScriptedTurn.toolCall("read_file", "{\"uri\":\"a\"}"),
                        FakeAgentLlmGateway.ScriptedTurn.text("Read the file.")));
        assertTrue(noApprovalOrDenial(events));
    }

    @Test
    public void defaultWorkspaceRequestWithNoHandlerFailsClosedForRiskyTools() throws IOException {
        List<AgentEvent> events = runWith(
                PermissionConfig.workspaceRequest(),
                new FakeAgentLlmGateway(
                        FakeAgentLlmGateway.ScriptedTurn.toolCall("apply_patch", PATCH),
                        FakeAgentLlmGateway.ScriptedTurn.text("I could not modify the file.")));
        assertEquals(ORIGINAL, new String(Files.readAllBytes(
                new File(workspacePath, "src/demo/Counter.kt").toPath()), StandardCharsets.UTF_8));
        assertTrue("a PROMPT without a handler must be denied",
                anyDenial(events, "apply_patch"));
    }

    @Test
    public void fullAccessRunsMutationsWithoutAnyApproval() throws IOException {
        EventStream eventStream = new EventStream(Runnable::run, 128);
        List<AgentEvent> events = new ArrayList<>();
        eventStream.subscribe(events::add);

        AgentRuntime runtime = new AgentRuntime.Builder(
                        new FakeAgentLlmGateway(
                                FakeAgentLlmGateway.ScriptedTurn.toolCall("apply_patch", PATCH),
                                FakeAgentLlmGateway.ScriptedTurn.text("Patched with full access.")))
                .events(eventStream)
                .toolRegistry(registry())
                .permissions(new PermissionLayer(PermissionConfig.fullAccess(), null, eventStream))
                .maxTurns(8)
                .build();

        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(
                "Fix it.", ChatMessage.TYPE_USER, System.currentTimeMillis()));
        RunResult result = runtime.run(scriptedAgent(), history, "sc_full");
        assertTrue(result.isSuccessful());
        assertEquals(PATCHED, new String(Files.readAllBytes(
                new File(workspacePath, "src/demo/Counter.kt").toPath()), StandardCharsets.UTF_8));
        assertTrue("full access must not emit ApprovalRequired", noApproval(events));
        assertTrue("full access must not emit PolicyDenied", noDenial(events));
    }

    private static boolean noApprovalOrDenial(List<AgentEvent> events) {
        return noApproval(events) && noDenial(events);
    }

    private static boolean noApproval(List<AgentEvent> events) {
        for (AgentEvent event : events) {
            if (event instanceof AgentEvent.ApprovalRequired) {
                return false;
            }
        }
        return true;
    }

    private static boolean noDenial(List<AgentEvent> events) {
        for (AgentEvent event : events) {
            if (event instanceof AgentEvent.PolicyDenied) {
                return false;
            }
        }
        return true;
    }

    private static boolean anyDenial(List<AgentEvent> events, String tool) {
        for (AgentEvent event : events) {
            if (event instanceof AgentEvent.PolicyDenied
                    && tool.equals(((AgentEvent.PolicyDenied) event).getTool())) {
                return true;
            }
        }
        return false;
    }
}