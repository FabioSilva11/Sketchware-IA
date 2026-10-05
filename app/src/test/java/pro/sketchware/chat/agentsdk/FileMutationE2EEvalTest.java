package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.FileChangeTrackerWorkspaceTest;
import pro.sketchware.chat.agentsdk.tools.ApplyPatchExecutor;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;
import pro.sketchware.chat.toolcalling.ToolCall;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Codex-shaped end-to-end eval over the REAL runtime loop: the model calls a
 * mutating tool, the PermissionLayer asks the scripted user, the tool mutates
 * the workspace filesystem ONCE, {@code FileChanged} proves the side effect
 * happened, and the on-disk content is final without any "accept diff" step.
 */
public class FileMutationE2EEvalTest {

    private FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem fs;
    private EventStream events;
    private List<AgentEvent> received;
    private RecordingApproval approvals;

    @Before
    public void setUp() {
        fs = new FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fs, fakeWorkspace());
        FileChangeTracker.clearChanges("sc_e2e");

        events = new EventStream(Runnable::run, 256);
        received = new ArrayList<>();
        events.subscribe(received::add);
        approvals = new RecordingApproval();
        approvals.decision = PermissionDecision.ALLOW;
    }

    @After
    public void tearDown() {
        FileChangeTracker.clearChanges("sc_e2e");
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(
                new FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem(), fakeWorkspace());
    }

    @Test
    public void eval_editTool_approvalThenSingleMutation_fileChangedEmitted_noSecondStep() throws Exception {
        fs.writeText("src/Config.java", "int timeout = 30;\n");
        FileChangeTracker.clearChanges("sc_e2e");
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                // Turn 1: the model edits the file through the registry tool.
                FakeAgentLlmGateway.ScriptedTurn.toolCall("apply_patch",
                        "*** Begin Patch\n"
                                + "*** Update File: src/Config.java\n"
                                + "@@\n"
                                + "-int timeout = 30;\n"
                                + "+int timeout = 60;\n"
                                + "*** End Patch"),
                // Turn 2: done — the mutation is already on disk.
                FakeAgentLlmGateway.ScriptedTurn.text("Timeout atualizado para 60."));

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(applyPatchRegistry())
                .permissions(new PermissionLayer(
                        new ToolPolicy.Builder()
                                .mutation(ToolPolicy.Rule.ASK_USER)
                                .unknown(ToolPolicy.Rule.DENY)
                                .build(),
                        approvals,
                        events))
                .build();
        Agent agent = Agent.Builder.forName("coder", "You edit files.").build();

        RunResult result = runtime.run(agent, "Aumente o timeout", "sc_e2e");

        assertTrue(result.isSuccessful());
        assertEquals("the user approved exactly once", 1, approvals.requests);

        // The filesystem already carries the final content.
        assertEquals("int timeout = 60;\n", fs.readText("src/Config.java"));

        // The change is already tracked as applied (diff = review surface).
        assertTrue(FileChangeTracker.getAllRecentChanges("sc_e2e")
                .containsKey("src/Config.java"));

        // Event order: the approval decision fully precedes the execution of
        // the mutation (Codex: approval before the tool runtime runs), and the
        // FileChanged announces the real side effect BEFORE the call is
        // declared complete — the file is final when Completed is emitted.
        int asked = firstIndexOf(AgentEvent.ApprovalRequired.class);
        int resolved = firstIndexOf(AgentEvent.PermissionResolved.class);
        int started = firstIndexOf(AgentEvent.ToolCallStarted.class);
        int changed = firstIndexOf(AgentEvent.FileChanged.class);
        int completed = firstIndexOf(AgentEvent.ToolCallCompleted.class);
        assertTrue(asked >= 0 && resolved > asked && started > resolved
                && changed > started && completed > changed);
        AgentEvent.FileChanged fileChanged =
                (AgentEvent.FileChanged) received.get(changed);
        assertEquals("src/Config.java", fileChanged.getPath());
        assertEquals(AgentEvent.FileChangeKind.MODIFIED, fileChanged.getKind());
        assertEquals("apply_patch", fileChanged.getTool());

        // The Codex acceptance criterion: no accept click is needed — the file
        // is final the moment the tool completed.
        assertTrue("accept must not be required to apply the change",
                FileChangeTracker.acceptChange("sc_e2e", "src/Config.java"));
        assertEquals("content is unchanged by the review action",
                "int timeout = 60;\n", fs.readText("src/Config.java"));
    }

    @Test
    public void eval_userDenies_noMutationNoFileChanged_noTrackedChange() throws Exception {
        fs.writeText("src/Config.java", "int timeout = 30;\n");
        approvals.decision = PermissionDecision.DENY;
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("apply_patch",
                        "*** Begin Patch\n"
                                + "*** Update File: src/Config.java\n"
                                + "@@\n"
                                + "-int timeout = 30;\n"
                                + "+int timeout = 60;\n"
                                + "*** End Patch"),
                FakeAgentLlmGateway.ScriptedTurn.text("Entendi, não alterei o arquivo."));

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(applyPatchRegistry())
                .permissions(new PermissionLayer(
                        new ToolPolicy.Builder()
                                .mutation(ToolPolicy.Rule.ASK_USER)
                                .unknown(ToolPolicy.Rule.DENY)
                                .build(),
                        approvals,
                        events))
                .build();
        Agent agent = Agent.Builder.forName("coder", "You edit files.").build();

        RunResult result = runtime.run(agent, "Aumente o timeout", "sc_e2e");

        assertTrue(result.isSuccessful());
        assertEquals("the file must keep the original content",
                "int timeout = 30;\n", fs.readText("src/Config.java"));
        assertEquals("no side effect, no FileChanged event",
                -1, firstIndexOf(AgentEvent.FileChanged.class));
        assertTrue("no applied change may appear in the diff review list",
                FileChangeTracker.getAllRecentChanges("sc_e2e").isEmpty());
    }

    @Test
    public void eval_applyPatch_mutatesOnce_andFilesChangedMatchThePatch() throws Exception {
        fs.writeText("src/App.java", "class App {}\n");
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("apply_patch",
                        "*** Begin Patch\n"
                                + "*** Update File: src/App.java\n"
                                + "@@\n"
                                + "-class App {}\n"
                                + "+class App {\n"
                                + "+    int ready = 1;\n"
                                + "+}\n"
                                + "*** End Patch"),
                FakeAgentLlmGateway.ScriptedTurn.text("Patch aplicado."));

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(applyPatchRegistry())
                .build();
        Agent agent = Agent.Builder.forName("coder", "You edit files.").build();

        RunResult result = runtime.run(agent, "Adicione o campo ready", "sc_e2e");

        assertTrue(result.isSuccessful());
        assertEquals("class App {\n    int ready = 1;\n}\n", fs.readText("src/App.java"));
        long fileChangedCount = received.stream()
                .filter(e -> e instanceof AgentEvent.FileChanged).count();
        assertEquals("one mutation, one FileChanged event", 1L, fileChangedCount);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A registry carrying ONLY apply_patch, bound to the injected filesystem. */
    private AxionToolRegistry applyPatchRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(ToolRegistration.freeform(
                        "apply_patch",
                        "The `apply_patch` tool can be used to edit files. This is a FREEFORM tool.",
                        WorkspaceToolProvider.APPLY_PATCH_GRAMMAR,
                        new ApplyPatchExecutor((context, stream, scId) -> new ApplyPatchTool(
                                scId == null || scId.isEmpty() ? context.scId() : scId,
                                stream, fs)))
                .source("core")
                .fileMutation(true)
                .destructive(true)
                .build());
        return registry;
    }

    private static Workspace fakeWorkspace() {
        return new Workspace("ws-e2e", "E2E", "/", "/", false,
                Workspace.PermissionState.GRANTED, 0L, "");
    }

    private int firstIndexOf(Class<? extends AgentEvent> type) {
        for (int i = 0; i < received.size(); i++) {
            if (type.isInstance(received.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** ApprovalHandler doubling as the human in the loop. */
    private static final class RecordingApproval implements ApprovalHandler {
        PermissionDecision decision = PermissionDecision.ALLOW;
        int requests;

        @Override
        public PermissionDecision onRequest(PermissionRequest request) {
            requests++;
            return decision;
        }
    }
}
