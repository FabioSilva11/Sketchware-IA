package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspaceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The diff is a review surface, not a commit buffer: the tool's mutation is
 * already on disk when {@code trackChange} runs. Accept must therefore never
 * rewrite the file, and revert must restore through the SAME
 * {@link WorkspaceFileSystem} that performed the original mutation.
 */
public class FileChangeTrackerWorkspaceTest {

    private FakeWorkspaceFileSystem fs;

    @Before
    public void setUp() {
        fs = new FakeWorkspaceFileSystem();
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(fs, fakeWorkspace());
        FileChangeTracker.clearChanges("sc_tracker");
    }

    @After
    public void tearDown() {
        FileChangeTracker.clearChanges("sc_tracker");
        WorkspaceManager.INSTANCE.setCustomFileSystemForTesting(
                new FakeWorkspaceFileSystem(), fakeWorkspace());
    }

    @Test
    public void accept_doesNotRewriteTheFile_changeIsAlreadyApplied() {
        fs.writeText("src/App.kt", "fun main() {\n    println(\"v2\")\n}\n");
        FileChangeTracker.trackChange("sc_tracker", "src/App.kt",
                "fun main() {\n    println(\"v1\")\n}\n",
                "fun main() {\n    println(\"v2\")\n}\n");

        assertTrue(FileChangeTracker.acceptChange("sc_tracker", "src/App.kt"));

        // Accept is a review action: the applied content stays untouched.
        assertEquals("fun main() {\n    println(\"v2\")\n}\n", fs.readText("src/App.kt"));
        assertTrue("accept must clear the review entry",
                FileChangeTracker.getAllRecentChanges("sc_tracker").isEmpty());
    }

    @Test
    public void accept_withoutTrackedChange_returnsFalse() {
        assertFalse(FileChangeTracker.acceptChange("sc_tracker", "src/Missing.kt"));
    }

    @Test
    public void accept_performsNoFilesystemOperationAtAll() {
        fs.writeText("src/A.java", "final content\n");
        FileChangeTracker.trackChange("sc_tracker", "src/A.java", "original\n", "final content\n");
        int writesBefore = fs.writeTextCalls.size();
        int deletesBefore = fs.deleteCalls.size();

        assertTrue(FileChangeTracker.acceptChange("sc_tracker", "src/A.java"));

        assertEquals("accept must never write", writesBefore, fs.writeTextCalls.size());
        assertEquals("accept must never delete", deletesBefore, fs.deleteCalls.size());
    }

    @Test
    public void revert_restoresPreviousContent_throughTheActiveWorkspace() {
        String original = "class A\n";
        String modified = "class A\n// agent edit\n";
        fs.writeText("src/A.java", modified);
        FileChangeTracker.trackChange("sc_tracker", "src/A.java", original, modified);

        assertTrue(FileChangeTracker.rejectChange("sc_tracker", "src/A.java"));

        assertEquals("revert must restore the original bytes",
                original, fs.readText("src/A.java"));
        assertTrue("revert restores through the active workspace",
                fs.writeTextCalls.contains("src/A.java"));
        assertTrue(FileChangeTracker.getAllRecentChanges("sc_tracker").isEmpty());
    }

    @Test
    public void revert_deletion_removesTheFileThroughTheWorkspace() {
        // Real flow: the tool applied the mutation (file exists) and tracked it.
        fs.writeText("src/created.txt", "new content");
        FileChangeTracker.trackChange("sc_tracker", "src/created.txt", "", "new content", false);

        assertTrue(FileChangeTracker.rejectChange("sc_tracker", "src/created.txt"));

        assertFalse("a file created by the agent must be removed on revert",
                fs.exists("src/created.txt"));
        assertTrue("removal must go through the workspace delete",
                fs.deleteCalls.contains("src/created.txt"));
    }

    @Test
    public void revert_whenWorkspaceDeleteFails_reportsFailure() {
        fs.writeText("src/created.txt", "content");
        FileChangeTracker.trackChange("sc_tracker", "src/created.txt", "", "content", false);
        fs.failDeletes = true;

        assertFalse("a failed revert must never be reported as success",
                FileChangeTracker.rejectChange("sc_tracker", "src/created.txt"));
        // The entry stays tracked so the user can retry the revert.
        assertTrue(FileChangeTracker.getAllRecentChanges("sc_tracker").containsKey("src/created.txt"));
    }        @Test
        public void revert_whenWorkspaceWriteFails_reportsFailure() {
        fs.writeText("src/A.java", "class A\n// agent edit\n");
        FileChangeTracker.trackChange("sc_tracker", "src/A.java", "class A\n", "class A\n// agent edit\n");
        fs.failWrites = true;

        assertFalse(FileChangeTracker.rejectChange("sc_tracker", "src/A.java"));
        assertTrue(FileChangeTracker.getAllRecentChanges("sc_tracker").containsKey("src/A.java"));
    }

    // ------------------------------------------------------------------

    private static Workspace fakeWorkspace() {
        return new Workspace("ws-test", "Test", "/", "/", false,
                Workspace.PermissionState.GRANTED, 0L, "");
    }

    /** In-memory WorkspaceFileSystem that records which mutations were asked of it. */
    public static final class FakeWorkspaceFileSystem implements WorkspaceFileSystem {
        public final Map<String, String> files = new HashMap<>();
        public final java.util.Set<String> directories = new java.util.HashSet<>();
        public final List<String> writeTextCalls = new ArrayList<>();
        public final List<String> deleteCalls = new ArrayList<>();
        public final List<String> missingPaths = new ArrayList<>();
        public boolean failDeletes = false;
        public boolean failWrites = false;
        public boolean failCreates = false;
        /** Deletion is refused for these specific paths (rollback hardening tests). */
        public final java.util.Set<String> failDeletesFor = new java.util.HashSet<>();
        /** Writes to these paths silently lose the content (verifies read-back checks). */
        public final java.util.Set<String> loseContentOnWriteFor = new java.util.HashSet<>();

        private String key(String relativePath) {
            return pro.sketchware.chat.workspace.WorkspacePath
                    .normalize(relativePath);
        }

        @Override
        public String readText(String relativePath) {
            String key = key(relativePath);
            if (missingPaths.contains(key) || !files.containsKey(key)) {
                throw new RuntimeException("no such file: " + key);
            }
            return files.get(key);
        }

        @Override
        public byte[] readBytes(String relativePath) {
            return readText(relativePath).getBytes();
        }

        @Override
        public void writeText(String relativePath, String content) {
            String key = key(relativePath);
            if (failWrites) {
                throw new RuntimeException("workspace write failed");
            }
            missingPaths.remove(key);
            writeTextCalls.add(key);
            files.put(key, loseContentOnWriteFor.contains(key) ? "" : content);
        }

        @Override
        public void writeBytes(String relativePath, byte[] data) {
            writeText(relativePath, new String(data));
        }

        @Override
        public boolean createFile(String relativePath) {
            String key = key(relativePath);
            if (failCreates) return false;
            if (files.containsKey(key)) return true;
            files.put(key, "");
            return true;
        }

        @Override
        public boolean createDirectory(String relativePath) {
            if (failCreates) return false;
            directories.add(key(relativePath));
            return true;
        }

        @Override
        public boolean delete(String relativePath) {
            String key = key(relativePath);
            deleteCalls.add(key);
            if (failDeletes || failDeletesFor.contains(key) || missingPaths.contains(key)) {
                return false;
            }
            if (directories.remove(key)) return true;
            return files.remove(key) != null;
        }

        @Override
        public boolean rename(String relativePath, String newName) {
            return false;
        }

        @Override
        public boolean move(String sourceRelativePath, String destinationRelativePath) {
            return false;
        }

        @Override
        public boolean copy(String sourceRelativePath, String destinationRelativePath) {
            return false;
        }

        @Override
        public boolean exists(String relativePath) {
            String key = key(relativePath);
            return !missingPaths.contains(key)
                    && (files.containsKey(key) || directories.contains(key));
        }

        @Override
        public boolean isDirectory(String relativePath) {
            return directories.contains(key(relativePath));
        }

        @Override
        public List<FileEntry> list(String relativePath) {
            return new ArrayList<>();
        }

        @Override
        public List<String> searchFiles(String query, String includePattern, int maxResults) {
            return new ArrayList<>();
        }

        @Override
        public List<SearchResult> searchText(String query, int maxResults) {
            return new ArrayList<>();
        }

        @Override
        public FileMetadata getMetadata(String relativePath) {
            return null;
        }

        @Override
        public InputStream openInputStream(String relativePath) {
            return null;
        }

        @Override
        public OutputStream openOutputStream(String relativePath) {
            return null;
        }
    }
}
