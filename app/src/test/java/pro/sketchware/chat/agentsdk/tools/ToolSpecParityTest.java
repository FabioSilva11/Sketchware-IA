package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.tools.ToolSpec.Type;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire-shape parity: every spec kind serialises to its OWN model-facing
 * shape (Codex create_tools_json parity) — function/freeform/namespace and
 * the synthetic tool_search are never flattened into "type":"function".
 */
public class ToolSpecParityTest {

    private static final String APPLY_PATCH_GRAMMAR =
            WorkspaceToolProvider.APPLY_PATCH_GRAMMAR;

    private static ToolRegistration functionReg(String name) {
        return ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain(name), "desc " + name,
                        params("arg1")))
                .executor(ctx -> AgentToolResult.success("ok"))
                .source("test")
                .build();
    }

    private static ToolRegistration freeformReg(String name) {
        return ToolRegistration.builder(ToolSpec.freeform(
                        ToolName.plain(name), "freeform " + name,
                        "grammar", "lark", APPLY_PATCH_GRAMMAR, false))
                .executor(ctx -> AgentToolResult.success("raw"))
                .source("test")
                .build();
    }

    private static JSONObject params(String... required) {
        try {
            JSONArray req = new JSONArray();
            for (String r : required) {
                req.put(r);
            }
            return new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject()
                            .put("arg1", new JSONObject().put("type", "string")))
                    .put("required", req)
                    .put("additionalProperties", false);
        } catch (org.json.JSONException e) {
            return new JSONObject();
        }
    }

    @Test
    public void functionToolKeepsFunctionWireShape() throws Exception {
        ToolRegistration reg = functionReg("exec_command");
        JSONArray catalog = ToolSpecSerializer.toCatalog(List.of(reg));
        assertEquals(1, catalog.length());
        JSONObject entry = catalog.getJSONObject(0);
        assertEquals("function", entry.getString("type"));
        JSONObject fn = entry.getJSONObject("function");
        assertEquals("exec_command", fn.getString("name"));
        assertEquals("desc exec_command", fn.getString("description"));
        assertTrue(fn.has("parameters"));
        assertEquals(false, fn.getBoolean("strict"));
        assertFalse(fn.has("output_schema"));
    }

    @Test
    public void freeformToolKeepsFreeformWireShape() throws Exception {
        ToolRegistration reg = freeformReg("apply_patch");
        JSONArray catalog = ToolSpecSerializer.toCatalog(List.of(reg));
        assertEquals(1, catalog.length());
        JSONObject entry = catalog.getJSONObject(0);
        assertEquals("freeform", entry.getString("type"));
        JSONObject ff = entry.getJSONObject("freeform");
        assertEquals("apply_patch", ff.getString("name"));
        assertEquals("grammar", ff.getJSONObject("format").getString("type"));
        assertEquals("lark", ff.getJSONObject("format").getString("syntax"));
        assertEquals(APPLY_PATCH_GRAMMAR, ff.getJSONObject("format").getString("definition"));
    }

    @Test
    public void namespaceDeclarationGroupsChildren() throws Exception {
        ToolRegistration decl = ToolRegistration.builder(ToolSpec.namespace(
                        ToolName.plain("clock"), "Tools for reading and waiting on time.",
                        List.of(
                                ToolSpec.function(ToolName.namespaced("clock", "curr_time"),
                                        "Return the current time in UTC.", params()),
                                ToolSpec.function(ToolName.namespaced("clock", "sleep"),
                                        "Pause execution for a specified duration.", params("duration_ms")))))
                .source("core")
                .build();
        ToolRegistration decl2 = ToolRegistration.builder(ToolSpec.namespace(
                        ToolName.plain("mcp__server_a"), "MCP tools exposed by server_a.",
                        List.of(ToolSpec.function(
                                ToolName.namespaced("mcp__server_a", "search"), "search", params()))))
                .source("mcp:server_a")
                .build();

        List<ToolRegistration> catalogInput = List.of(
                ToolRegistration.builder(ToolSpec.function(
                                ToolName.namespaced("clock", "curr_time"),
                                "Return the current time in UTC.", params()))
                        .executor(ctx -> AgentToolResult.success("time")).build(),
                ToolRegistration.builder(ToolSpec.function(
                                ToolName.namespaced("clock", "sleep"),
                                "Pause execution.", params("duration_ms")))
                        .executor(ctx -> AgentToolResult.success("slept")).build(),
                ToolRegistration.builder(ToolSpec.function(
                                ToolName.namespaced("mcp__server_a", "search"), "search", params()))
                        .executor(ctx -> AgentToolResult.success("found")).build(),
                decl, decl2);

        JSONArray catalog = ToolSpecSerializer.toCatalog(catalogInput);
        assertEquals(2, catalog.length());
        assertEquals("namespace", catalog.getJSONObject(0).getString("type"));
    }

    @Test
    public void toolSearchKeepsToolSearchWireShape() throws Exception {
        ToolRegistration toolSearch = ToolRegistration.builder(ToolSpec.toolSearch(
                        "Searches for a tool by name or description.",
                        params("query")))
                .executor(ctx -> AgentToolResult.success("{\"tools\":[]}"))
                .source("core")
                .build();
        JSONArray catalog = ToolSpecSerializer.toCatalog(List.of(toolSearch));
        assertEquals(1, catalog.length());
        JSONObject entry = catalog.getJSONObject(0);
        assertEquals("tool_search", entry.getString("type"));
        assertEquals("tool_search", entry.getJSONObject("tool_search").getString("name"));
    }

    @Test
    public void catalogTypesHelperLabelsKinds() throws Exception {
        List<ToolRegistration> regs = new ArrayList<>();
        regs.add(functionReg("a_func"));
        regs.add(freeformReg("a_patch"));
        String types = ToolSpecSerializer.catalogTypes(ToolSpecSerializer.toCatalog(regs));
        assertTrue(types.contains("function:a_func"));
        assertTrue(types.contains("freeform:a_patch"));
    }

    @Test
    public void specFactoryFunctionsProduceCorrectKinds() {
        assertTrue(ToolSpec.function(ToolName.plain("f"), "d", params()) instanceof FunctionToolSpec);
        assertTrue(ToolSpec.freeform(ToolName.plain("g"), "d", "g", "lark", "def", false)
                instanceof FreeformToolSpec);
        assertTrue(ToolSpec.namespace(ToolName.plain("n"), "d", List.of())
                instanceof NamespaceToolSpec);
        assertTrue(ToolSpec.toolSearch("d", params()) instanceof ToolSearchToolSpec);
    }
}