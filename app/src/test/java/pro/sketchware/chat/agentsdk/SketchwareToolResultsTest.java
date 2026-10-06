package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;
import pro.sketchware.chat.workspace.SketchwareProjectFileSystem;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * What the model gets back from the file tools on a native Sketchware project, through the same registry, router and
 * executors the chat uses: a refused or failed call is an error (not a "done" bubble), and the refusal explains what
 * to do instead.
 */
public class SketchwareToolResultsTest {
    private static final String VIEW = "@main.xml\n{\"id\":\"button1\",\"type\":3}\n";
    private static final String LIBRARY = "@compat\n{\"configurations\":{\"theme\":\"DayNight\"},\"useYn\":\"Y\"}\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File root;
    private AutoCloseable runBinding;
    private AxionToolRouter router;

    @Before
    public void setUp() throws IOException {
        root = folder.newFolder(".sketchware");
        write("data/601/view", SketchwareProjectFileSystem.encrypt(VIEW.getBytes(StandardCharsets.UTF_8)));
        write("data/601/library", SketchwareProjectFileSystem.encrypt(LIBRARY.getBytes(StandardCharsets.UTF_8)));
        write("data/601/files/java/Util.java", "class Util {}".getBytes(StandardCharsets.UTF_8));
        write("mysc/list/601/project", SketchwareProjectFileSystem.encrypt("{\"my_ws_name\":\"Loja\"}".getBytes(StandardCharsets.UTF_8)));
        write("mysc/601/app/src/main/java/MainActivity.java", "class MainActivity {}".getBytes(StandardCharsets.UTF_8));
        write("data/602/view", SketchwareProjectFileSystem.encrypt("@main.xml\nsecret\n".getBytes(StandardCharsets.UTF_8)));

        runBinding = RuntimeFileContext.install("run_601", new WorkspaceIdentity("601", "", "", "Loja", ""),
                SketchwareProjectFileSystem.forProject("601", root));
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerWorkspaceReadTools(registry);
        WorkspaceToolProvider.registerWorkspaceMutationTools(registry);
        router = new AxionToolRouter(registry, null, new EventStream(Runnable::run, 16));
        FileChangeTracker.clearChanges("601");
    }

    @After
    public void tearDown() throws Exception {
        runBinding.close();
        FileChangeTracker.clearChanges("601");
    }

    private void write(String path, byte[] content) throws IOException {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content);
    }

    private int calls;

    private AgentToolResult call(String tool, JSONObject args) {
        // Each call gets its own id: the router answers a repeated id with the first call's outcome
        return router.route(new AxionToolRouter.Route("c" + (++calls) + "_" + tool, tool, args.toString()), "601",
                RunContext.bare("601", "coordinator", null), null).result();
    }

    private String decrypted(String path) throws IOException {
        return new String(SketchwareProjectFileSystem.decryptOrRaw(Files.readAllBytes(new File(root, path).toPath())),
                StandardCharsets.UTF_8);
    }

    @Test
    public void writingGeneratedCodeIsAnErrorThatPointsToTheProjectFiles() throws Exception {
        AgentToolResult result = call("rewrite_file", new JSONObject()
                .put("uri", "mysc/601/app/src/main/java/MainActivity.java").put("new_content", "class X {}"));
        assertTrue(result.isError());
        assertTrue(result.output(), result.output().contains("data/601/view"));
        assertEquals("class MainActivity {}", new String(Files.readAllBytes(
                new File(root, "mysc/601/app/src/main/java/MainActivity.java").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void anEditReportsOnlyWhatHappened() throws Exception {
        AgentToolResult result = call("edit_file", new JSONObject().put("uri", "data/601/library")
                .put("search_replace_blocks", "<<<<<<< ORIGINAL\n\"theme\":\"DayNight\"\n=======\n"
                        + "\"theme\":\"DayNight.NoActionBar\"\n>>>>>>> UPDATED"));
        assertFalse(result.output(), result.isError());
        assertEquals("Change successfully made to data/601/library.", result.output());
        assertTrue(decrypted("data/601/library").contains("DayNight.NoActionBar"));
    }

    @Test
    public void anotherProjectAndProtectedFilesAreErrors() throws Exception {
        assertTrue(call("read_file", new JSONObject().put("uri", "data/602/view")).isError());
        assertTrue(call("delete_file_or_folder", new JSONObject().put("uri", "data/601/view")).isError());
        assertTrue(call("get_file_info", new JSONObject().put("uri", "data/602/view")).isError());
        assertEquals(VIEW, decrypted("data/601/view"));
    }

    @Test
    public void aMoveCannotDropAPlainFileOverAProjectFile() throws Exception {
        AgentToolResult result = call("move_file", new JSONObject()
                .put("source", "data/601/files/java/Util.java").put("destination", "data/601/view"));
        assertTrue(result.isError());
        assertEquals(VIEW, decrypted("data/601/view"));
        assertTrue(new File(root, "data/601/files/java/Util.java").exists());
    }

    @Test
    public void copyingAProjectFileGivesItsText() throws Exception {
        AgentToolResult result = call("copy_file", new JSONObject()
                .put("source", "data/601/view").put("destination", "data/601/files/view_backup.txt"));
        assertFalse(result.output(), result.isError());
        assertEquals("Copied data/601/view to data/601/files/view_backup.txt.", result.output());
        assertEquals(VIEW, new String(Files.readAllBytes(new File(root, "data/601/files/view_backup.txt").toPath()),
                StandardCharsets.UTF_8));
    }

    @Test
    public void editsWaitForTheEditorToSave() throws Exception {
        // An empty bak/<id> is what "Save & exit" leaves behind: edits go through
        new File(root, "bak/601").mkdirs();
        assertFalse(call("rewrite_file", new JSONObject()
                .put("uri", "data/601/library").put("new_content", LIBRARY)).isError());
        // Unsaved work in it means the editor will write the project back
        write("bak/601/view", VIEW.getBytes(StandardCharsets.UTF_8));
        AgentToolResult result = call("rewrite_file", new JSONObject()
                .put("uri", "data/601/library").put("new_content", "@compat\n{}\n"));
        assertTrue(result.isError());
        assertTrue(result.output(), result.output().contains("Save & exit"));
        assertEquals(LIBRARY, decrypted("data/601/library"));
    }
}
