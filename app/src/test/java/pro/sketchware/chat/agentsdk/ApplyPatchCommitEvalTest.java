package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.ApplyPatchExecutor;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The ApplyPatchTool commits as one unit: tracker entries and FileChanged
 * events exist only for patches that ended fully applied on the workspace
 * filesystem, and the diff review page sees exactly what was committed.
 */
public class ApplyPatchCommitEvalTest {

    private FakeWorkspaceFileSystem fs;
    private EventStream events;
    private List<AgentEvent> received;
    private static final String SC = "sc_patch_commit";

    @Before
    public void setUp() {
        fs = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fs, fakeWorkspace());
        FileChangeTracker.clearChanges(SC);
        events = new EventStream(Runnable::run, 256);
        received = new ArrayList<>();
        events.subscribe(received::add);
    }

    @After
    public void tearDown() {
        FileChangeTracker.clearChanges(SC);
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(
                new FakeWorkspaceFileSystem(), fakeWorkspace());
    }

    @Test
    public void addUpdateDelete_tracksCoherentHistory_andEmitsCorrectKinds() throws Exception {
        fs.writeText("src/Upd.java", "int a = 1;\n");
        fs.writeText("src/Del.java", "obsolete\n");
        ApplyPatchTool tool = new ApplyPatchTool(SC, events, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                        + "*** Add File: src/New.java\n"
                        + "+class New {}\n"
                        + "*** Update File: src/Upd.java\n"
                        + "@@\n"
                        + "-int a = 1;\n"
                        + "+int a = 10;\n"
                        + "*** Delete File: src/Del.java\n"
                        + "*** End Patch");

        assertFalse(result.isError());

        // Tracker records carry the full before/after/existedBefore history.
        var changes = FileChangeTracker.getAllRecentChanges(SC);
        assertEquals(3, changes.size());
        FileChangeTracker.FileChange created = changes.get("src/New.java");
        assertEquals("", created.beforeContent);
        assertEquals("class New {}\n", created.afterContent);
        assertFalse(created.existedBefore);

        FileChangeTracker.FileChange modified = changes.get("src/Upd.java");
        assertEquals("int a = 1;\n", modified.beforeContent);
        assertEquals("int a = 10;\n", modified.afterContent);
        assertTrue(modified.existedBefore);

        FileChangeTracker.FileChange deleted = changes.get("src/Del.java");
        assertEquals("obsolete\n", deleted.beforeContent);
        assertEquals("", deleted.afterContent);
        assertTrue(deleted.existedBefore);

        // FileChanged events match the operation that produced them.
        List<AgentEvent.FileChanged> fileEvents = fileChangedEvents();
        assertEquals(3, fileEvents.size());
        assertTrue(fileEvents.stream().anyMatch(e ->
                "src/New.java".equals(e.getPath())
                        && e.getKind() == AgentEvent.FileChangeKind.CREATED));
        assertTrue(fileEvents.stream().anyMatch(e ->
                "src/Upd.java".equals(e.getPath())
                        && e.getKind() == AgentEvent.FileChangeKind.MODIFIED));
        assertTrue(fileEvents.stream().anyMatch(e ->
                "src/Del.java".equals(e.getPath())
                        && e.getKind() == AgentEvent.FileChangeKind.DELETED));
        assertEquals("apply_patch", fileEvents.get(0).getTool());
    }

    @Test
    public void updateRevert_restoresOriginal_addRevertRemoves_deleteRevertRestores() throws Exception {
        // UPDATE: original → patch → revert → original
        fs.writeText("src/A.java", "class A {}\n");
        ApplyPatchTool patchTool = new ApplyPatchTool(SC, events, fs);
        AgentToolResult r1 = patchTool.apply(null,
                "*** Begin Patch\n*** Update File: src/A.java\n@@\n-class A {}\n+class A {\n+    int x;\n+}\n*** End Patch");
        assertFalse(r1.isError());
        assertTrue(FileChangeTracker.rejectChange(SC, "src/A.java"));
        assertEquals("class A {}\n", fs.readText("src/A.java"));
        FileChangeTracker.clearChanges(SC);

        // ADD: nonexistent → patch → revert → nonexistent
        AgentToolResult r2 = patchTool.apply(null,
                "*** Begin Patch\n*** Add File: src/Added.java\n+class Added {}\n*** End Patch");
        assertFalse(r2.isError());
        assertTrue(fs.exists("src/Added.java"));
        assertTrue(FileChangeTracker.rejectChange(SC, "src/Added.java"));
        assertFalse("a created file must disappear on revert", fs.exists("src/Added.java"));
        FileChangeTracker.clearChanges(SC);

        // DELETE: exists → patch → revert → restored with the original content
        fs.writeText("src/Deleted.java", "keep me\n");
        AgentToolResult r3 = patchTool.apply(null,
                "*** Begin Patch\n*** Delete File: src/Deleted.java\n*** End Patch");
        assertFalse(r3.isError());
        assertFalse(fs.exists("src/Deleted.java"));
        assertTrue(FileChangeTracker.rejectChange(SC, "src/Deleted.java"));
        assertEquals("the reverted delete restores the previous content",
                "keep me\n", fs.readText("src/Deleted.java"));
    }

    @Test
    public void failedPatch_leavesNoTrackerEntriesAndNoFileChangedEvents() throws Exception {
        fs.writeText("src/A.java", "original A\n");
        fs.writeText("src/B.java", "original B\n");
        fs.writeText("src/C.java", "original C\n");
        fs.failDeletes = true; // DELETE op passes validation, fails mid-apply
        ApplyPatchTool tool = new ApplyPatchTool(SC, events, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                        + "*** Update File: src/A.java\n@@\n-original A\n+patched A\n"
                        + "*** Update File: src/B.java\n@@\n-original B\n+patched B\n"
                        + "*** Delete File: src/C.java\n"
                        + "*** End Patch");

        assertTrue("the patch must fail", result.isError());
        assertEquals("A must be rolled back to the original", "original A\n", fs.readText("src/A.java"));
        assertEquals("B must be rolled back to the original", "original B\n", fs.readText("src/B.java"));
        assertEquals("C must still exist untouched", "original C\n", fs.readText("src/C.java"));
        assertTrue("a failed patch must not enter the diff review",
                FileChangeTracker.getAllRecentChanges(SC).isEmpty());
        assertEquals("a failed patch must emit no FileChanged events",
                0, fileChangedEvents().size());
    }

    @Test
    public void appliedPatch_fileChangedStaysBeforeToolCallCompleted_noRuntimeDuplication() throws Exception {
        fs.writeText("src/One.java", "class One {}\n");
        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.toolCall("apply_patch",
                        "*** Begin Patch\n*** Update File: src/One.java\n@@\n-class One {}\n+class One { /* v2 */ }\n*** End Patch"),
                FakeAgentLlmGateway.ScriptedTurn.text("Patch aplicado."));

        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .events(events)
                .toolRegistry(patchOnlyRegistry()) // apply_patch bound to fs; runtime lends its stream
                .build();
        Agent agent = Agent.Builder.forName("coder", "You patch files.").build();

        RunResult result = runtime.run(agent, "Aplique o patch", SC);

        assertTrue(result.isSuccessful());
        assertEquals("class One { /* v2 */ }\n", fs.readText("src/One.java"));

        // Exactly one FileChanged per committed mutation: the runtime must
        // not add a second, heuristic one for apply_patch.
        List<AgentEvent.FileChanged> fileEvents = fileChangedEvents();
        assertEquals(1, fileEvents.size());
        assertEquals("src/One.java", fileEvents.get(0).getPath());

        // The file is final before ToolCallCompleted and the diff review sees it.
        assertTrue(indexOfEvent(AgentEvent.FileChanged.class)
                < indexOfEvent(AgentEvent.ToolCallCompleted.class));
        assertTrue(FileChangeTracker.getAllRecentChanges(SC).containsKey("src/One.java"));
        assertTrue("accept only confirms: content is untouched",
                FileChangeTracker.acceptChange(SC, "src/One.java"));
        assertEquals("class One { /* v2 */ }\n", fs.readText("src/One.java"));
    }

    // ------------------------------------------------------------------

    /** A registry carrying ONLY apply_patch, bound to the injected filesystem. */
    private AxionToolRegistry patchOnlyRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration reg = ToolRegistration.freeform(
                        "apply_patch",
                        "The `apply_patch` tool can be used to edit files. This is a FREEFORM tool.",
                        WorkspaceToolProvider.APPLY_PATCH_GRAMMAR,
                        new ApplyPatchExecutor((context, stream, scId) -> new ApplyPatchTool(
                                scId == null || scId.isEmpty() ? context.scId() : scId, stream, fs)))
                .source("core")
                .fileMutation(true)
                .destructive(true)
                .build();
        registry.register(reg);
        return registry;
    }

    private List<AgentEvent.FileChanged> fileChangedEvents() {
        List<AgentEvent.FileChanged> result = new ArrayList<>();
        for (AgentEvent event : received) {
            if (event instanceof AgentEvent.FileChanged) {
                result.add((AgentEvent.FileChanged) event);
            }
        }
        return result;
    }

    private int indexOfEvent(Class<? extends AgentEvent> type) {
        for (int i = 0; i < received.size(); i++) {
            if (type.isInstance(received.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static Workspace fakeWorkspace() {
        return new Workspace("ws-patch", "Patch", "/", "/", false,
                Workspace.PermissionState.GRANTED, 0L, "");
    }
}
