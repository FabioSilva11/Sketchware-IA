package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.workspace.LocalFolderWorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/** Level-1/2 tests for the apply_patch tool over a real temp workspace. */
public class ApplyPatchToolTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private WorkspaceFileSystem fs;
    private ApplyPatchTool tool;

    @Before
    public void setUp() throws IOException {
        fs = new LocalFolderWorkspaceFileSystem(temp.newFolder("ws"));
        tool = new ApplyPatchTool("sc_test", null, fs);
    }

    @Test
    public void addFile_createsNewFile() {
        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                + "*** Add File: lib/novo.kt\n"
                + "+fun created() {}\n"
                + "*** End Patch\n");
        assertFalse(result.isError());
        assertEquals("fun created() {}\n", fs.readText("lib/novo.kt"));
    }

    @Test
    public void updateFile_appliesContextHunk() throws IOException {
        fs.writeText("app.kt", "class A {\n    val x = 1\n}\n");
        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                + "*** Update File: app.kt\n"
                + " class A {\n"
                + "-    val x = 1\n"
                + "+    val x = 2\n"
                + " }\n"
                + "*** End Patch\n");
        assertFalse(result.isError());
        assertEquals("class A {\n    val x = 2\n}\n", fs.readText("app.kt"));
    }

    @Test
    public void updateFile_failsCleanlyWhenContextMissing() throws IOException {
        fs.writeText("app.kt", "class A {}\n");
        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                + "*** Update File: app.kt\n"
                + "-nonexistent line\n"
                + "+replacement\n"
                + "*** End Patch\n");
        assertTrue(result.isError());
        assertEquals("class A {}\n", fs.readText("app.kt")); // untouched
    }

    @Test
    public void deleteFile_removesFile() throws IOException {
        fs.writeText("obsolete.txt", "bye\n");
        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                + "*** Delete File: obsolete.txt\n"
                + "*** End Patch\n");
        assertFalse(result.isError());
        assertFalse(fs.exists("obsolete.txt"));
    }

    @Test
    public void pathTraversal_isRejected() {
        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                + "*** Add File: ../escape.kt\n"
                + "+evil\n"
                + "*** End Patch\n");
        assertTrue(result.isError());
    }

    @Test
    public void absolutePath_isRejected() {
        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                + "*** Add File: /etc/passwd\n"
                + "+evil\n"
                + "*** End Patch\n");
        assertTrue(result.isError());
    }

    @Test
    public void addFile_onExistingFile_isRejected() throws IOException {
        fs.writeText("exists.kt", "original\n");
        AgentToolResult result = tool.apply(null,
                "*** Begin Patch\n"
                + "*** Add File: exists.kt\n"
                + "+clobber\n"
                + "*** End Patch\n");
        assertTrue(result.isError());
        assertEquals("original\n", fs.readText("exists.kt"));
    }

    @Test
    public void malformedPatch_isRejectedWithoutWrites() {
        AgentToolResult result = tool.apply(null, "no markers");
        assertTrue(result.isError());
    }
}