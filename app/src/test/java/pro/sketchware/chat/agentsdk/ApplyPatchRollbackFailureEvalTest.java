package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Hardening evals: a patch that fails mid-apply must never fake a clean
 * revert. Rollbacks are verified against the real filesystem state, an
 * incomplete rollback surfaces an explicit partial-state error (no fake
 * commits, no FileChanged), and the tracker resolves the filesystem of the
 * CHANGE's project — never a different workspace.
 */
public class ApplyPatchRollbackFailureEvalTest {

    private FakeWorkspaceFileSystem fs;
    private EventStream events;
    private List<AgentEvent> received;
    private static final String SC = "sc_rollback";

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
    public void eval_patchFailsAndRollbackCompletes_zeroTracesOfCommit() throws Exception {
        fs.writeText("src/A.java", "original A\n");
        fs.writeText("src/C.java", "original C\n");
        fs.failDeletes = true; // DELETE op fails mid-apply
        ApplyPatchTool tool = new ApplyPatchTool(SC, events, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                        + "*** Update File: src/A.java\n@@\n-original A\n+patched A\n"
                        + "*** Delete File: src/C.java\n"
                        + "*** End Patch");

        assertTrue(result.isError());
        assertTrue("clean-rollback message must be explicit",
                result.output().contains("fully rolled back"));
        assertEquals("original A\n", fs.readText("src/A.java"));
        assertEquals("original C\n", fs.readText("src/C.java"));
        assertTrue("no tracker entries for a fully rolled back patch",
                FileChangeTracker.getAllRecentChanges(SC).isEmpty());
        assertEquals("no FileChanged events for a fully rolled back patch",
                0, fileChangedCount());
    }

    @Test
    public void eval_rollbackOfCreatedFileFails_honestPartialStateError() throws Exception {
        fs.writeText("src/C.java", "original C\n");
        // DELETE C fails mid-apply (triggering the rollback) and the rollback
        // of the created file fails too: deletion is globally refused.
        fs.failDeletes = true;
        ApplyPatchTool tool = new ApplyPatchTool(SC, events, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                        + "*** Add File: src/New.java\n+class New {}\n"
                        + "*** Delete File: src/C.java\n"
                        + "*** End Patch");

        assertTrue("the patch must fail", result.isError());
        assertTrue("the error must admit the partial state, not claim a clean revert",
                result.output().contains("rollback was incomplete"));
        assertTrue("the error must name the stuck file",
                result.output().contains("src/New.java"));
        assertTrue("the created file remains (rollback could not remove it)",
                fs.exists("src/New.java"));
        assertEquals("original C\n", fs.readText("src/C.java"));
        assertTrue("an incomplete rollback must not fake tracker commits",
                FileChangeTracker.getAllRecentChanges(SC).isEmpty());
        assertEquals("an incomplete rollback must not emit commit events",
                0, fileChangedCount());
    }

    @Test
    public void eval_rollbackOfUpdatedFileFails_partialStateDetected() throws Exception {
        fs.writeText("src/A.java", "original A\n");
        fs.writeText("src/B.java", "original B\n");
        // Restore of A will "write" but the filesystem loses the content:
        // the verified restore must detect the divergence.
        fs.failDeletesFor.add("src/B.java"); // DELETE op fails mid-apply
        fs.loseContentOnWriteFor.add("src/A.java"); // rollback of A silently diverges
        ApplyPatchTool tool = new ApplyPatchTool(SC, events, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                        + "*** Update File: src/A.java\n@@\n-original A\n+patched A\n"
                        + "*** Delete File: src/B.java\n"
                        + "*** End Patch");

        assertTrue(result.isError());
        assertTrue("the divergence must be detected and reported",
                result.output().contains("rollback was incomplete"));
        assertTrue(result.output().contains("src/A.java"));
        assertEquals("no commit events for a divergent rollback",
                0, fileChangedCount());
    }

    @Test
    public void eval_revert_resolvesTheFilesystemOfTheChange_notTheActiveWorkspace() throws Exception {
        // Workspace A: mutated by the agent.
        FakeWorkspaceFileSystem fsA = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsA, fakeWorkspace());
        fsA.writeText("src/File.java", "original A\n");
        ApplyPatchTool toolA = new ApplyPatchTool(SC, null, fsA);
        AgentToolResult r = toolA.apply(null,
                "*** Begin Patch\n*** Update File: src/File.java\n@@\n-original A\n+patched A\n*** End Patch");
        assertFalse(r.isError());
        assertEquals("patched A\n", fsA.readText("src/File.java"));

        // Switch the active workspace to B (a DIFFERENT backend).
        FakeWorkspaceFileSystem fsB = new FakeWorkspaceFileSystem();
        fsB.writeText("src/File.java", "original B\n");
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsB, fakeWorkspace());
        int writesB = fsB.writeTextCalls.size(); // seeded only

        // Revert the change of project A while B is active.
        assertTrue("the revert must succeed through A's bound filesystem",
                FileChangeTracker.rejectChange(SC, "src/File.java"));
        assertEquals("A must be restored", "original A\n", fsA.readText("src/File.java"));
        assertEquals("B must never be touched", "original B\n", fsB.readText("src/File.java"));
        assertEquals("B must record no revert write", writesB, fsB.writeTextCalls.size());

        // Accept of project A's entry with B active: review-only, B untouched.
        FileChangeTracker.clearChanges(SC);
        fsA.writeText("src/Other.java", "final\n");
        int writesA = fsA.writeTextCalls.size();
        FileChangeTracker.trackChange(SC, "src/Other.java", "before\n", "final\n");
        assertTrue(FileChangeTracker.acceptChange(SC, "src/Other.java"));
        assertTrue(FileChangeTracker.getAllRecentChanges(SC).isEmpty());
        assertEquals("accept must not write anywhere", writesA, fsA.writeTextCalls.size());
        assertEquals("accept must not touch B", writesB, fsB.writeTextCalls.size());
    }

    // ------------------------------------------------------------------
    // fail-closed revert: process death / binding loss
    // ------------------------------------------------------------------

    @Test
    public void eval_processDeath_bindingLost_wrongActiveWorkspace_revertIsRefused()
            throws Exception {
        // Workspace A is mutated and tracked.
        FakeWorkspaceFileSystem fsA = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsA, fakeWorkspace("ws-a"));
        fsA.writeText("src/File.java", "original A\n");
        ApplyPatchTool toolA = new ApplyPatchTool(SC, null, fsA);
        AgentToolResult r = toolA.apply(null,
                "*** Begin Patch\n*** Update File: src/File.java\n@@\n-original A\n+patched A\n*** End Patch");
        assertFalse(r.isError());

        // Process death: the in-memory scId→filesystem binding is gone.
        FileChangeTracker.clearFileSystemBindings();

        // A DIFFERENT workspace (B) becomes active.
        FakeWorkspaceFileSystem fsB = new FakeWorkspaceFileSystem();
        fsB.writeText("src/File.java", "original B\n");
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsB, fakeWorkspace("ws-b"));

        // The revert must be refused: A is not bound and B is not provably A.
        assertFalse("revert into a different workspace must fail closed",
                FileChangeTracker.rejectChange(SC, "src/File.java"));
        assertEquals("A must keep the applied content", "patched A\n", fsA.readText("src/File.java"));
        assertEquals("B must never be touched", "original B\n", fsB.readText("src/File.java"));
        assertTrue("the refused entry stays tracked for a later legitimate revert",
                FileChangeTracker.getAllRecentChanges(SC).containsKey("src/File.java"));
    }

    @Test
    public void eval_processDeath_bindingLost_correctWorkspaceReopened_revertSucceeds()
            throws Exception {
        FakeWorkspaceFileSystem fsA = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsA, fakeWorkspace("ws-a"));
        fsA.writeText("src/File.java", "original A\n");
        ApplyPatchTool toolA = new ApplyPatchTool(SC, null, fsA);
        toolA.apply(null,
                "*** Begin Patch\n*** Update File: src/File.java\n@@\n-original A\n+patched A\n*** End Patch");

        // Process death removes the binding…
        FileChangeTracker.clearFileSystemBindings();

        // …and the user reopens Workspace A — a workspace whose identity IS
        // the change's scId (identity path, no path inference).
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsA, fakeWorkspace(SC));

        assertTrue("reverting with the correct workspace reopened must work",
                FileChangeTracker.rejectChange(SC, "src/File.java"));
        assertEquals("original A\n", fsA.readText("src/File.java"));
    }

    @Test
    public void eval_safWorkspaces_bindingLost_wrongSafTreeActive_revertIsRefused()
            throws Exception {
        // SAF workspace A (content:// identity, no path inference possible).
        FakeWorkspaceFileSystem fsA = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsA,
                safWorkspace("ws-saf-a", "content://workspaceA/tree"));
        fsA.writeText("src/File.java", "original A\n");
        ApplyPatchTool toolA = new ApplyPatchTool(SC, null, fsA);
        toolA.apply(null,
                "*** Begin Patch\n*** Update File: src/File.java\n@@\n-original A\n+patched A\n*** End Patch");

        // Binding lost; a DIFFERENT SAF tree (B) becomes active.
        FileChangeTracker.clearFileSystemBindings();
        FakeWorkspaceFileSystem fsB = new FakeWorkspaceFileSystem();
        fsB.writeText("src/File.java", "original B\n");
        int writesB = fsB.writeTextCalls.size(); // seed only
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsB,
                safWorkspace("ws-saf-b", "content://workspaceB/tree"));

        assertFalse("revert across SAF trees must fail closed",
                FileChangeTracker.rejectChange(SC, "src/File.java"));
        assertEquals("patched A\n", fsA.readText("src/File.java"));
        assertEquals("original B\n", fsB.readText("src/File.java"));
        assertEquals("B must record no revert mutation", writesB, fsB.writeTextCalls.size());

        // Reopening the correct SAF workspace A (identity == scId) makes the
        // revert legitimate — resolved by identity, never by path.
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsA,
                safWorkspace(SC, "content://workspaceA/tree"));
        assertTrue(FileChangeTracker.rejectChange(SC, "src/File.java"));
        assertEquals("original A\n", fsA.readText("src/File.java"));
    }

    @Test
    public void eval_acceptWithDifferentWorkspaceActive_neverTouchesAnyFilesystem()
            throws Exception {
        FakeWorkspaceFileSystem fsA = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsA, fakeWorkspace("ws-a"));
        fsA.writeText("src/F.java", "final\n");
        FileChangeTracker.trackChange(SC, "src/F.java", "before\n", "final\n");

        // B becomes active; accept must stay review-only regardless.
        FakeWorkspaceFileSystem fsB = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fsB, fakeWorkspace("ws-b"));

        int writesB = fsB.writeTextCalls.size();
        assertTrue(FileChangeTracker.acceptChange(SC, "src/F.java"));
        assertTrue(FileChangeTracker.getAllRecentChanges(SC).isEmpty());
        assertEquals("accept must not write into B", writesB, fsB.writeTextCalls.size());
        assertEquals("accept must not write into A", 1, fsA.writeTextCalls.size()); // seed only
    }

    // ------------------------------------------------------------------

    private int fileChangedCount() {
        int count = 0;
        for (AgentEvent event : received) {
            if (event instanceof AgentEvent.FileChanged) {
                count++;
            }
        }
        return count;
    }

    private static Workspace fakeWorkspace() {
        return fakeWorkspace("ws-rollback");
    }

    private static Workspace fakeWorkspace(String id) {
        return new Workspace(id, id, "", "", false,
                Workspace.PermissionState.GRANTED, 0L, "");
    }

    /** A SAF-style workspace: content:// identity, no local path. */
    private static Workspace safWorkspace(String id, String treeUri) {
        return new Workspace(id, id, treeUri, "", false,
                Workspace.PermissionState.GRANTED, 0L, "");
    }
}
