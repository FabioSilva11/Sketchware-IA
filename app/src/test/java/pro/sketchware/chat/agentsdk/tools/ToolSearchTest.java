package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.tools.ToolSpec.Type;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * tool_search parity (Codex tools/tool_search.rs): 50 tools — 10 direct,
 * 40 deferred — are exposed; the deferred ones are ABSENT from the catalog,
 * discoverable only through tool_search, and activated once discovered so the
 * router permits them. Limit capping matches Codex (default 10, max 25).
 */
public class ToolSearchTest {

    private static final int DIRECT_TOOLS = 10;
    private static final int DEFERRED_TOOLS = 40;

    private AxionToolRegistry buildRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        for (int i = 0; i < DIRECT_TOOLS; i++) {
            final int n = i;
            registry.register(ToolRegistration.builder(ToolSpec.function(
                            ToolName.plain("direct_tool_" + n), "Direct description of tool " + n,
                            new JSONObject()))
                    .executor(ctx -> AgentToolResult.success("direct_" + n))
                    .exposure(ToolExposure.direct())
                    .build());
        }
        for (int i = 0; i < DEFERRED_TOOLS; i++) {
            final int n = i;
            registry.register(ToolRegistration.builder(ToolSpec.function(
                            ToolName.plain("deferred_tool_" + n), "Deferred description " + n,
                            new JSONObject()))
                    .executor(ctx -> AgentToolResult.success("deferred_" + n))
                    .exposure(ToolExposure.deferred())
                    .build());
        }
        // tool_search itself (auto-registered when deferred tools exist).
        try {
            registry.register(ToolRegistration.builder(ToolSpec.toolSearch(
                            "Searches for a tool by name or description.",
                            new JSONObject().put("type", "object")))
                    .executor(new ToolSearchTool(registry))
                    .build());
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
        return registry;
    }

    @Test
    public void catalogContainsOnlyDirectTools() {
        AxionToolRegistry registry = buildRegistry();
        List<ToolRegistration> visible = registry.modelVisibleTools();
        assertEquals(DIRECT_TOOLS + 1, visible.size()); // + tool_search itself
        for (ToolRegistration reg : visible) {
            assertFalse("deferred tool must be absent from catalog: " + reg.spec().qualifiedName(),
                    reg.spec().name().name().startsWith("deferred_"));
        }
        assertEquals(DEFERRED_TOOLS, registry.deferredTools().size());
    }

    @Test
    public void activatedDeferredToolsAppearInTheNextCatalogSnapshot() throws Exception {
        AxionToolRegistry registry = buildRegistry();
        ToolRegistration search = registry.get("tool_search");
        search.executor().execute(execute(search, "{\"query\":\"deferred_tool_3\"}"));

        assertTrue(registry.isDeferredActivated(ToolName.plain("deferred_tool_3")));
        List<String> names = new ArrayList<>();
        for (ToolRegistration reg : ToolCatalog.from(registry).registrations()) {
            names.add(reg.spec().qualifiedName());
        }
        assertTrue("an activated deferred tool must appear in the next catalog snapshot",
                names.contains("deferred_tool_3"));
        assertFalse("a not-yet-activated deferred tool stays out of the catalog",
                names.contains("deferred_tool_4"));
        assertTrue(names.contains("tool_search"));
    }

    @Test
    public void toolSearchMatchesByNameAndActivates() throws Exception {
        AxionToolRegistry registry = buildRegistry();
        ToolRegistration search = registry.get("tool_search");
        ToolExecutionContext ctx = execute(search, "{\"query\":\"deferred_tool_3\"}");
        AgentToolResult result = search.executor().execute(ctx);

        assertFalse(result.isError());
        JSONObject body = new JSONObject(result.output());
        JSONArray tools = body.getJSONArray("tools");
        assertEquals("should find one matching deferred tool", 1, tools.length());
        assertEquals("deferred_tool_3", tools.getJSONObject(0).getString("name"));

        // Activation: the tool is now callable via the router.
        assertTrue(registry.isDeferredActivated(ToolName.plain("deferred_tool_3")));
    }

    @Test
    public void toolSearchMatchesByDescription() throws Exception {
        AxionToolRegistry registry = buildRegistry();
        ToolRegistration search = registry.get("tool_search");
        ToolExecutionContext ctx = execute(search, "{\"query\":\"Deferred description 7\"}");
        AgentToolResult result = search.executor().execute(ctx);

        assertFalse(result.isError());
        JSONObject body = new JSONObject(result.output());
        assertTrue(body.getJSONArray("tools").length() >= 1);
    }

    @Test
    public void toolSearchDefaultLimitAndCap() throws Exception {
        AxionToolRegistry registry = buildRegistry();
        ToolRegistration search = registry.get("tool_search");

        // Query matching ALL deferred tools → default limit 10.
        ToolExecutionContext searchAll = execute(search, "{\"query\":\"deferred_tool\"}");
        AgentToolResult allResult = search.executor().execute(searchAll);
        JSONObject allBody = new JSONObject(allResult.output());
        assertEquals(10, allBody.getJSONArray("tools").length());

        // Explicit limit 25 (at the cap).
        ToolExecutionContext searchTwentyFive =
                execute(search, "{\"query\":\"deferred_tool\",\"limit\":25}");
        AgentToolResult twentyFiveResult = search.executor().execute(searchTwentyFive);
        assertEquals(25, new JSONObject(twentyFiveResult.output()).getJSONArray("tools").length());
    }

    @Test
    public void toolSearchNoMatchReturnsEmptyMessage() throws Exception {
        AxionToolRegistry registry = buildRegistry();
        ToolRegistration search = registry.get("tool_search");
        ToolExecutionContext ctx = execute(search, "{\"query\":\"nonexistent_xyz\"}");
        AgentToolResult result = search.executor().execute(ctx);
        assertFalse(result.isError());
        assertTrue(result.output().contains("No tools matched"));
    }

    @Test
    public void toolSearchRequiresQuery() throws Exception {
        AxionToolRegistry registry = buildRegistry();
        ToolRegistration search = registry.get("tool_search");
        ToolExecutionContext ctx = execute(search, "{}");
        AgentToolResult result = search.executor().execute(ctx);
        assertTrue(result.isError());
        assertTrue(result.output().contains("query"));
    }

    private static ToolExecutionContext execute(ToolRegistration reg, String argsJson) throws Exception {
        return new ToolExecutionContext(reg, "sc_search", "call", null,
                new JSONObject(argsJson), null, argsJson);
    }
}