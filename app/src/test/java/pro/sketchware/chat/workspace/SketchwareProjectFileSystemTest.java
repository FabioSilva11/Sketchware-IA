package pro.sketchware.chat.workspace;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

public class SketchwareProjectFileSystemTest {
    private static final String VIEW = "@main.xml\n{\"id\":\"button1\",\"type\":3,\"text\":{\"text\":\"Comprar agora\"}}\n";
    private static final String LIBRARY = "@compat\n{\"configurations\":{\"theme\":\"DayNight\"},\"useYn\":\"Y\"}\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File root;
    private SketchwareProjectFileSystem fs;

    @Before
    public void setUp() throws IOException {
        root = folder.newFolder(".sketchware");
        write("data/601/view", SketchwareProjectFileSystem.encrypt(VIEW.getBytes(StandardCharsets.UTF_8)));
        write("data/601/library", SketchwareProjectFileSystem.encrypt(LIBRARY.getBytes(StandardCharsets.UTF_8)));
        write("data/601/project_config", "{\"enable_viewbinding\":\"true\"}".getBytes(StandardCharsets.UTF_8));
        write("data/601/files/java/Util.java", "class Util {}".getBytes(StandardCharsets.UTF_8));
        write("mysc/list/601/project", SketchwareProjectFileSystem.encrypt("{\"my_ws_name\":\"Loja\"}".getBytes(StandardCharsets.UTF_8)));
        write("mysc/601/app/src/main/java/MainActivity.java", "class MainActivity {}".getBytes(StandardCharsets.UTF_8));
        write("data/602/view", SketchwareProjectFileSystem.encrypt("@main.xml\nsecret of 602\n".getBytes(StandardCharsets.UTF_8)));
        fs = SketchwareProjectFileSystem.forProject("601", root);
    }

    private void write(String path, byte[] content) throws IOException {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content);
    }

    private byte[] raw(String path) throws IOException {
        return Files.readAllBytes(new File(root, path).toPath());
    }

    @Test
    public void readsEncryptedProjectFilesAsText() {
        assertEquals(VIEW, fs.readText("data/601/view"));
        assertEquals("{\"my_ws_name\":\"Loja\"}", fs.readText("mysc/list/601/project"));
        // Plain files are returned as they are
        assertEquals("{\"enable_viewbinding\":\"true\"}", fs.readText("data/601/project_config"));
        assertEquals("class Util {}", fs.readText("data/601/files/java/Util.java"));
    }

    @Test
    public void acceptsThePathFormsModelsSend() {
        assertEquals(VIEW, fs.readText(".sketchware/data/601/view"));
        assertEquals(VIEW, fs.readText("/storage/emulated/0/.sketchware/data/601/view"));
        assertEquals("{\"my_ws_name\":\"Loja\"}", fs.readText("project"));
        assertEquals("class MainActivity {}", fs.readText("mysc/601/601/app/src/main/java/MainActivity.java"));
    }

    @Test
    public void writingAnEncryptedFileKeepsItEncrypted() throws IOException {
        String edited = LIBRARY.replace("DayNight", "DayNight.NoActionBar");
        fs.writeText("data/601/library", edited);
        byte[] stored = raw("data/601/library");
        assertTrue(SketchwareProjectFileSystem.isEncrypted(stored));
        assertArrayEquals(edited.getBytes(StandardCharsets.UTF_8), SketchwareProjectFileSystem.decryptOrRaw(stored));
        assertEquals(edited, fs.readText("data/601/library"));
    }

    @Test
    public void writingAPlainFileKeepsItPlain() throws IOException {
        fs.writeText("data/601/project_config", "{\"enable_viewbinding\":\"false\"}");
        assertEquals("{\"enable_viewbinding\":\"false\"}", new String(raw("data/601/project_config"), StandardCharsets.UTF_8));
        fs.writeText("data/601/files/java/Nota.java", "class Nota {}");
        assertEquals("class Nota {}", new String(raw("data/601/files/java/Nota.java"), StandardCharsets.UTF_8));
    }

    @Test
    public void newNativeFilesAreEncryptedLikeSketchwareWritesThem() throws IOException {
        fs.writeText("data/601/logic", "@MainActivity.java_var\n");
        assertTrue(SketchwareProjectFileSystem.isEncrypted(raw("data/601/logic")));
    }

    @Test
    public void otherProjectsDoNotExist() {
        assertThrows(SecurityException.class, () -> fs.readText("data/602/view"));
        assertThrows(SecurityException.class, () -> fs.writeText("data/602/view", "x"));
        assertThrows(SecurityException.class, () -> fs.readText("/storage/emulated/0/.sketchware/data/602/view"));
        assertThrows(SecurityException.class, () -> fs.readText("data/601/../602/view"));
        assertFalse(fs.exists("data/602/view"));
        assertFalse(fs.canRead("data/602"));
        List<String> dataEntries = names(fs.list("data"));
        assertEquals(List.of("601"), dataEntries);
    }

    @Test
    public void listsOnlyThisProjectsFolders() {
        assertEquals(List.of("data", "mysc"), names(fs.list("")));
        assertEquals(List.of("601", "list"), names(fs.list("mysc")));
        assertTrue(names(fs.list("data/601")).containsAll(List.of("files", "library", "view")));
    }

    @Test
    public void searchFindsTextInsideEncryptedFiles() {
        List<String> hits = new ArrayList<>();
        for (WorkspaceFileSystem.SearchResult result : fs.searchText("Comprar agora", 10)) {
            hits.add(result.getRelativePath());
        }
        assertEquals(List.of("data/601/view"), hits);
        assertTrue(fs.searchText("secret of 602", 10).isEmpty());
    }

    @Test
    public void generatedProjectAndAssetsAreReadOnly() throws IOException {
        // mysc/<id> is regenerated by every build: Java converted from view/logic, resource copies, build output
        assertFalse(fs.canWrite("mysc/601/app/src/main/java/MainActivity.java"));
        assertFalse(fs.canWrite("mysc/601/build.gradle"));
        SecurityException generated = assertThrows(SecurityException.class,
                () -> fs.writeText("mysc/601/app/src/main/java/MainActivity.java", "x"));
        assertTrue(generated.getMessage().contains("data/601/view"));
        assertThrows(SecurityException.class, () -> fs.delete("mysc/601/app/src/main/java/MainActivity.java"));
        assertEquals("class MainActivity {}", fs.readText("mysc/601/app/src/main/java/MainActivity.java"));
        // Images, sounds and fonts are registered through Sketchware's managers
        write("resources/images/601/logo.png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        assertTrue(fs.exists("resources/images/601/logo.png"));
        assertThrows(SecurityException.class, () -> fs.writeText("resources/images/601/logo.png", "x"));
    }

    @Test
    public void buildOutputIsLeftOutOfListingsAndSearches() throws IOException {
        write("mysc/601/bin/classes/Main.class", "Comprar agora".getBytes(StandardCharsets.UTF_8));
        write("mysc/601/gen/com/example/R.java", "class R { Comprar agora }".getBytes(StandardCharsets.UTF_8));
        assertFalse(names(fs.list("mysc/601")).contains("bin"));
        assertFalse(names(fs.list("mysc/601")).contains("gen"));
        assertTrue(names(fs.list("mysc/601")).contains("app"));
        for (WorkspaceFileSystem.SearchResult result : fs.searchText("Comprar agora", 10)) {
            assertFalse(result.getRelativePath(), result.getRelativePath().startsWith("mysc/601/"));
        }
        // Still readable when asked for by path (a build error can point there)
        assertEquals("class R { Comprar agora }", fs.readText("mysc/601/gen/com/example/R.java"));
    }

    @Test
    public void projectFilesCannotBeDeleted() {
        assertThrows(SecurityException.class, () -> fs.delete("data/601/view"));
        assertThrows(SecurityException.class, () -> fs.delete("mysc/list/601/project"));
        assertTrue(new File(root, "data/601/view").exists());
        assertTrue(fs.delete("data/601/files/java/Util.java"));
    }

    private static List<String> names(List<WorkspaceFileSystem.FileEntry> entries) {
        List<String> names = new ArrayList<>();
        for (WorkspaceFileSystem.FileEntry entry : entries) {
            names.add(entry.getName());
        }
        return names;
    }

    @Test
    public void projectFilesCannotChangeWhileTheEditorHasThemOpen() throws IOException {
        // The editor keeps unsaved work in bak/<id> and writes data/<id> on "Save & exit"
        write("bak/601/view", "@main.xml\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(fs.isOpenInEditor());
        SecurityException open = assertThrows(SecurityException.class, () -> fs.writeText("data/601/view", "x"));
        assertTrue(open.getMessage().contains("Save & exit"));
        // Custom Java and other files the editor doesn't rewrite stay editable
        fs.writeText("data/601/files/java/Util.java", "class Util { }");
        assertEquals(VIEW, fs.readText("data/601/view"));
    }
}
