package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.FileChangeTrackerWorkspaceTest;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.port.VoidPortToolsService;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * One approval, one real mutation: file tools mutate through the active
 * {@link pro.sketchware.chat.workspace.WorkspaceFileSystem}, the
 * PermissionLayer classifies them from their metadata, the tracker records
 * what was already applied, and the tool result reflects the real filesystem
 * outcome (no fake successes on failed deletes).
 */
public class FileMutationFlowTest {

    private FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem fs;
    private AutoCloseable runBinding;

    @Before
    public void setUp() {
        fs = new FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fs, fakeWorkspace());
        // Tools resolve the RUN's filesystem (fail-closed without one), as AgentRuntime pins it for a run
        runBinding = RuntimeFileContext.install("run_mutation",
                WorkspaceIdentity.of("sc_mutation", fakeWorkspace()), fs);
        FileChangeTracker.clearChanges("sc_mutation");
    }

    @After
    public void tearDown() throws Exception {
        runBinding.close();
        FileChangeTracker.clearChanges("sc_mutation");
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(
                new FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem(), fakeWorkspace());
    }

    // ------------------------------------------------------------------
    // delete_file_or_folder through the workspace filesystem
    // ------------------------------------------------------------------

    @Test
    public void delete_viaWorkspace_removesTheFileAndReportsSuccess() throws Exception {
        fs.writeText("src/old.txt", "obsolete");
        String output = executeVoidTool("delete_file_or_folder",
                new JSONObject().put("uri", "src/old.txt"));

        assertFalse("delete must succeed for an existing file: " + output,
                output.contains("Error"));
        assertFalse("the file must be gone from the workspace filesystem",
                fs.exists("src/old.txt"));
        assertTrue("delete must run through the workspace",
                fs.deleteCalls.contains("src/old.txt"));
    }

    @Test
    public void delete_whenFilesystemSaysNo_returnsError_neverSuccess() throws Exception {
        fs.writeText("src/stuck.txt", "content");
        fs.failDeletes = true;

        String output = executeVoidTool("delete_file_or_folder",
                new JSONObject().put("uri", "src/stuck.txt"));

        assertTrue("a failed delete must be an error result", output.contains("Error"));
        assertTrue("the file must still exist after a failed delete", fs.exists("src/stuck.txt"));
        assertFalse("failed delete must not be tracked as an applied change",
                FileChangeTracker.getAllRecentChanges("sc_mutation").containsKey("src/stuck.txt"));
    }

    @Test
    public void delete_missingFile_reportsNotFound() throws Exception {
        String output = executeVoidTool("delete_file_or_folder",
                new JSONObject().put("uri", "src/nope.txt"));

        assertTrue(output.contains("Error"));
    }

    @Test
    public void delete_rejectsParentTraversal() throws Exception {
        String output = executeVoidTool("delete_file_or_folder",
                new JSONObject().put("uri", "../outside.txt"));

        assertTrue(output.contains("Error"));
        assertTrue(fs.deleteCalls.isEmpty());
    }

    @Test
    public void delete_tracksAuditEntryForDeletedFile() throws Exception {
        fs.writeText("src/trackme.txt", "to be deleted");
        executeVoidTool("delete_file_or_folder",
                new JSONObject().put("uri", "src/trackme.txt"));

        FileChangeTracker.FileChange change = FileChangeTracker
                .getAllRecentChanges("sc_mutation").get("src/trackme.txt");
        assertNotNull("the audit trail must record the deletion", change);
        assertEquals("to be deleted", change.beforeContent);
    }

    // ------------------------------------------------------------------
    // apply_patch: validated delete + rollback after partial failure
    // ------------------------------------------------------------------

    @Test
    public void applyPatch_delete_validatesTheFilesystemResult() throws Exception {
        fs.writeText("src/Target.kt", "val a = 1\n");
        ApplyPatchTool tool = new ApplyPatchTool("sc_mutation", null, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n*** Delete File: src/Target.kt\n*** End Patch");

        assertTrue(result.output().contains("Deleted src/Target.kt"));
        assertFalse("delete must be real before it is reported", fs.exists("src/Target.kt"));
    }

    @Test
    public void applyPatch_delete_failedFilesystemNeverReportsSuccess() throws Exception {
        fs.writeText("src/Target.kt", "val a = 1\n");
        fs.failDeletes = true;
        ApplyPatchTool tool = new ApplyPatchTool("sc_mutation", null, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n*** Delete File: src/Target.kt\n*** End Patch");

        assertTrue("the tool must not claim success when the delete failed",
                result.isError());
        assertTrue("the file must still exist after the failed delete",
                fs.exists("src/Target.kt"));
    }

    @Test
    public void applyPatch_rollsBackEarlierWrites_whenALaterDeleteFails() throws Exception {
        fs.writeText("src/B.kt", "val b = 2\n");
        fs.writeText("src/C.kt", "val c = 3\n"); // passes validation, fails on apply
        fs.failDeletes = true;
        ApplyPatchTool tool = new ApplyPatchTool("sc_mutation", null, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                        + "*** Update File: src/B.kt\n"
                        + "@@\n"
                        + "-val b = 2\n"
                        + "+val b = 42\n"
                        + "*** Delete File: src/C.kt\n"
                        + "*** End Patch");

        assertTrue(result.isError());
        assertEquals("earlier writes must be rolled back to the pre-patch content",
                "val b = 2\n", fs.readText("src/B.kt"));
        assertFalse("the rolled-back error must be honest about the failure",
                result.output().contains("Deleted"));
    }

    @Test
    public void applyPatch_addUpdateDelete_appliesEverything() throws Exception {
        fs.writeText("src/Update.java", "int a = 1;\nint b = 2;\n");
        fs.writeText("src/Delete.java", "obsolete\n");
        ApplyPatchTool tool = new ApplyPatchTool("sc_mutation", null, fs);

        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                        + "*** Add File: src/New.java\n"
                        + "+class New {}\n"
                        + "*** Update File: src/Update.java\n"
                        + "@@\n"
                        + "-int a = 1;\n"
                        + "+int a = 10;\n"
                        + "*** Delete File: src/Delete.java\n"
                        + "*** End Patch");

        assertFalse(result.isError());
        assertEquals("class New {}\n", fs.readText("src/New.java"));
        assertEquals("int a = 10;\nint b = 2;\n", fs.readText("src/Update.java"));
        assertFalse(fs.exists("src/Delete.java"));
    }

    // ------------------------------------------------------------------
    // registry metadata preservation + PermissionLayer routing
    // ------------------------------------------------------------------

    @Test
    public void registryMetadata_preservesMutationDestructiveAndApprovalFlags() {
        ToolRegistration delete = ToolRegistration.function(
                        "delete_file_or_folder", "Delete a file.", new JSONObject())
                .requiresApproval(true)
                .fileMutation(true)
                .destructive(true)
                .build();
        ToolRegistration read = ToolRegistration.function(
                        "read_file", "Reads a file.", new JSONObject())
                .fileMutation(false)
                .destructive(false)
                .build();

        assertTrue(delete.isFileMutation());
        assertTrue(delete.isDestructive());
        assertTrue(delete.requiresApproval());
        assertFalse(read.isFileMutation());
        assertFalse(read.isDestructive());
    }

    @Test
    public void permissionLayer_classifiesMutatingToolsFromMetadata_notUnknown() {
        // A mutation-only tool (non-destructive) must follow the mutation rule.
        ToolRegistration edit = registration("edit_file", false, false, true);
        ToolRegistration delete = registration("delete_file_or_folder", true, true, true);
        ToolRegistration patch = registration("apply_patch", true, true, true);
        ToolRegistration read = registration("read_file", false, false, false);

        PermissionLayer layer = new PermissionLayer(
                new ToolPolicy.Builder()
                        .mutation(ToolPolicy.Rule.ALLOW)
                        .destructive(ToolPolicy.Rule.ASK_USER)
                        .network(ToolPolicy.Rule.ALLOW)
                        .unknown(ToolPolicy.Rule.DENY)
                        .build(),
                null, null);

        assertEquals(ToolPolicy.Rule.ALLOW, layer.ruleForPublicForTest(edit));
        assertEquals("destructive metadata must win over mutation",
                ToolPolicy.Rule.ASK_USER, layer.ruleForPublicForTest(delete));
        assertEquals(ToolPolicy.Rule.ASK_USER, layer.ruleForPublicForTest(patch));
        assertEquals("read-only tools must not be classified by the name fallback",
                ToolPolicy.Rule.DENY, layer.ruleForPublicForTest(read));
    }

    // ------------------------------------------------------------------
    // create_file_or_folder through the workspace filesystem
    // ------------------------------------------------------------------

    @Test
    public void createFile_viaWorkspace_createsAndTracksTheFile() throws Exception {
        String output = executeVoidTool("create_file_or_folder",
                new JSONObject().put("uri", "src/brand-new.txt"));

        assertFalse("creation must succeed: " + output, output.contains("Error"));
        assertTrue("the file must exist in the workspace filesystem",
                fs.exists("src/brand-new.txt"));
        FileChangeTracker.FileChange change = FileChangeTracker
                .getAllRecentChanges("sc_mutation").get("src/brand-new.txt");
        assertNotNull("creation must appear in the diff review", change);
        assertFalse("a created file did not exist before", change.existedBefore);
    }

    @Test
    public void createFolder_viaWorkspace_createsTheDirectory() throws Exception {
        String output = executeVoidTool("create_file_or_folder",
                new JSONObject().put("uri", "src/newpkg/"));

        assertFalse("folder creation must succeed: " + output, output.contains("Error"));
        assertTrue("the folder must exist", fs.isDirectory("src/newpkg"));
        // Folders are not tracked: the diff review is for file contents.
        assertTrue(FileChangeTracker.getAllRecentChanges("sc_mutation").isEmpty());
    }

    @Test
    public void createFile_whenFilesystemRefuses_reportsErrorAndTracksNothing() throws Exception {
        fs.failCreates = true;

        String output = executeVoidTool("create_file_or_folder",
                new JSONObject().put("uri", "src/doomed.txt"));

        assertTrue("a refused creation must be an error", output.contains("Error"));
        assertFalse(fs.exists("src/doomed.txt"));
        assertTrue("failed creation must not enter the diff review",
                FileChangeTracker.getAllRecentChanges("sc_mutation").isEmpty());
    }

    @Test
    public void create_rejectsTraversalAndAbsolutePaths() throws Exception {
        assertTrue(executeVoidTool("create_file_or_folder",
                new JSONObject().put("uri", "../escape.txt")).contains("Error"));
        assertTrue(executeVoidTool("create_file_or_folder",
                new JSONObject().put("uri", "/abs/path.txt")).contains("Error"));
        assertTrue(executeVoidTool("create_file_or_folder",
                new JSONObject().put("uri", "C:\\tmp\\x.txt")).contains("Error"));
        assertFalse(fs.exists("escape.txt"));
    }

    @Test
    public void createFolder_existingDirectory_isIdempotentSuccess() throws Exception {
        fs.createDirectory("src/already");

        String output = executeVoidTool("create_file_or_folder",
                new JSONObject().put("uri", "src/already/"));

        assertFalse(output.contains("Error"));
        assertTrue(fs.isDirectory("src/already"));
    }

    @Test
    public void permissionLayer_nameFallbackCoversMetadataLessAdapters() {
        ToolRegistration metadataLess = registration("delete_file_or_folder", false, false, false);

        PermissionLayer layer = new PermissionLayer(
                new ToolPolicy.Builder()
                        .mutation(ToolPolicy.Rule.ALLOW)
                        .destructive(ToolPolicy.Rule.ASK_USER)
                        .unknown(ToolPolicy.Rule.DENY)
                        .build(),
                null, null);

        assertEquals("a metadata-less mutating registration must not fall through to unknown",
                ToolPolicy.Rule.ASK_USER, layer.ruleForPublicForTest(metadataLess));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Workspace fakeWorkspace() {
        return new Workspace("ws-mutation", "Mutation", "/", "/", false,
                Workspace.PermissionState.GRANTED, 0L, "");
    }

    /** Runs a Void registry tool end-to-end through VoidPortToolsService. */
    private static String executeVoidTool(String name, JSONObject args) {
        return VoidPortToolsService.executeTool("sc_mutation", name, args);
    }

    /** A FUNCTION registration carrying the metadata flags. */
    private static ToolRegistration registration(String name, boolean approval,
                                                 boolean destructive, boolean mutation) {
        return ToolRegistration.function(name, name, new JSONObject())
                .requiresApproval(approval)
                .fileMutation(mutation)
                .destructive(destructive)
                .build();
    }
}
