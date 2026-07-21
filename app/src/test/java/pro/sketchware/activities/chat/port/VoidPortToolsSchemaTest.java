package pro.sketchware.activities.chat.port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

import pro.sketchware.ia.tools.ToolExecResult;

public class VoidPortToolsSchemaTest {

    @Test
    public void schemasAreClosedAndUseNativeJsonTypes() throws Exception {
        JSONObject readFile = function("read_file");
        JSONObject search = function("search_for_files");
        JSONObject delete = function("delete_file_or_folder");
        JSONObject runCommand = function("run_command");

        assertFalse(readFile.getJSONObject("parameters").getBoolean("additionalProperties"));
        assertEquals("integer", propertyType(readFile, "start_line"));
        assertEquals("integer", propertyType(readFile, "page_number"));
        assertEquals("boolean", propertyType(search, "is_regex"));
        assertEquals("boolean", propertyType(delete, "is_recursive"));
        assertEquals("integer", propertyType(runCommand, "timeout_seconds"));
    }

    @Test
    public void directoryTreeDefaultsToCurrentProjectRoot() throws Exception {
        JSONObject function = function("get_dir_tree");
        JSONObject parameters = function.getJSONObject("parameters");

        assertEquals(0, parameters.getJSONArray("required").length());
        assertTrue(parameters.getJSONObject("properties").has("uri"));
        assertEquals("string", propertyType(function, "uri"));
        assertTrue(parameters.getJSONObject("properties")
                .getJSONObject("uri")
                .getString("description")
                .contains("Defaults to '.'"));
    }

    @Test
    public void destructiveToolsRejectEveryProjectRootAlias() {
        assertTrue(VoidPortToolsService.isUnsafeMutationRoot(""));
        assertTrue(VoidPortToolsService.isUnsafeMutationRoot("."));
        assertTrue(VoidPortToolsService.isUnsafeMutationRoot("/"));
        assertTrue(VoidPortToolsService.isUnsafeMutationRoot("\\"));
        assertTrue(VoidPortToolsService.isUnsafeMutationRoot("sub/.."));
        assertFalse(VoidPortToolsService.isUnsafeMutationRoot("data/123/file.xml"));
    }

    @Test
    public void canonicalWritableRootCannotBeDeletedThroughEquivalentPath() {
        File root = new File("build/test-project-root").getAbsoluteFile();
        File equivalent = new File(root, "sub/..");

        assertTrue(VoidPortToolsService.isProtectedMutationRoot(
                equivalent, Collections.singletonList(root)));
        assertFalse(VoidPortToolsService.isProtectedMutationRoot(
                new File(root, "src/Main.java"), Collections.singletonList(root)));
    }

    @Test
    public void blockedRootDeletionIsReportedAsToolFailure() {
        VoidPortToolsService.ToolCallResult result =
                VoidPortToolsService.deleteFileOrFolder("123", "/", true);

        assertTrue(result.result.startsWith("Error:"));
        assertFalse(ToolExecResult.fromLegacyString(result.result).ok);
    }

    @Test
    public void placeholderDirectoryDoesNotCountAsSuccessfulDiscovery() {
        VoidPortToolsService.ToolCallResult result =
                VoidPortToolsService.lsDir("123", "<uri>", null);

        assertTrue(result.result.startsWith("Error:"));
        assertFalse(ToolExecResult.fromLegacyString(result.result).ok);
    }

    private static JSONObject function(String name) throws Exception {
        JSONArray tools = VoidPortToolsService.getAllToolsAsMCP();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject function = tools.getJSONObject(i).getJSONObject("function");
            if (name.equals(function.getString("name"))) {
                return function;
            }
        }
        throw new AssertionError("Missing tool: " + name);
    }

    private static String propertyType(JSONObject function, String property) throws Exception {
        return function.getJSONObject("parameters")
                .getJSONObject("properties")
                .getJSONObject(property)
                .getString("type");
    }
}
