package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.EventStream;
import pro.sketchware.chat.agentsdk.RunContext;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter.Routed;

import org.junit.Test;

/**
 * Namespace parity (Codex ToolName::from_str): namespaced tools keep distinct
 * identities, register under the namespace declaration, resolve through the
 * router by qualified name, and are grouped under a single NAMESPACE entry by
 * the serializer.
 */
public class NamespaceToolTest {

    private AxionToolRegistry registry;
    private AxionToolRouter router;

    private void build(String namespace, String... children) {
        registry = new AxionToolRegistry();
        registry.register(ToolRegistration.builder(ToolSpec.namespace(
                        ToolName.plain(namespace), "Tools in " + namespace,
                        java.util.Collections.emptyList()))
                .source("core")
                .build());
        for (String child : children) {
            registry.register(ToolRegistration.builder(ToolSpec.function(
                            ToolName.namespaced(namespace, child), "desc " + child,
                            new org.json.JSONObject()))
                    .executor(ctx -> AgentToolResult.success(child))
                    .source("core")
                    .build());
        }
        router = new AxionToolRouter(registry, null, new EventStream(r -> r.run(), 8));
    }

    @Test
    public void namespacedToolHasStableQualifiedName() {
        ToolName name = ToolName.namespaced("clock", "curr_time");
        assertEquals("clock", name.namespace());
        assertEquals("curr_time", name.name());
        assertEquals("clock.curr_time", name.qualifiedName());
        assertEquals(ToolName.parse("clock.curr_time"), name);
    }

    @Test
    public void namespacedToolsRegisterUnderNamespaceDeclaration() {
        build("clock", "curr_time", "sleep");
        assertEquals(3, registry.size());
        assertNotNull(registry.get("clock"));
        assertNotNull(registry.get(ToolName.namespaced("clock", "curr_time")));
        assertNotNull(registry.get("clock.sleep"));
        assertEquals(1, registry.namespaceTools().size());
        assertEquals(2, registry.namespaceChildTools("clock").size());
    }

    @Test
    public void routerResolvesNamespacedCallAndReturnsChildOutput() {
        build("clock", "curr_time", "sleep");
        Routed routed = router.route(new AxionToolRouter.Route("c1", "clock.curr_time", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(!routed.result().isError());
        assertEquals("curr_time", routed.result().output());
    }

    @Test
    public void namespaceChildPlainNameFallsBackToNamespaceResolution() {
        // Codex providers may hand in the plain child name; the router does a
        // last-chance resolution against the namespace children.
        build("clock", "curr_time", "sleep");
        Routed routedReduced = router.route(new AxionToolRouter.Route("c2", "curr_time", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(!routedReduced.result().isError());
        assertEquals("curr_time", routedReduced.result().output());
    }

    @Test
    public void serializerGroupsChildrenUnderNamespaceEntry() throws Exception {
        build("clock", "curr_time", "sleep");
        org.json.JSONArray catalog = ToolSpecSerializer.toCatalog(registry.modelVisibleTools());
        assertEquals(1, catalog.length());
        org.json.JSONObject entry = catalog.getJSONObject(0);
        assertEquals("namespace", entry.getString("type"));
        assertEquals("clock", entry.getString("name"));
        assertEquals(2, entry.getJSONArray("tools").length());
    }

    @Test
    public void unknownNamespaceDoesNotResolve() {
        build("clock", "curr_time");
        Routed routed = router.route(new AxionToolRouter.Route("c3", "clock.nope", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(routed.result().isError());
        assertTrue(routed.result().output().contains("unknown tool"));

        // Plain name of a different namespace never leaks across namespaces.
        Routed routedOther = router.route(new AxionToolRouter.Route("c4", "sleep", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(routedOther.result().isError());
    }

    @Test
    public void twoServersSameChildNameRemainDistinct() {
        build("mcp__server_a", "search");
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("mcp__server_b", "search"), "other",
                        new org.json.JSONObject()))
                .executor(ctx -> AgentToolResult.success("server_b search"))
                .build());
        registry.register(ToolRegistration.builder(ToolSpec.namespace(
                        ToolName.plain("mcp__server_b"), "other namespace",
                        java.util.Collections.emptyList())).build());

        Routed a = router.route(new AxionToolRouter.Route("c_a", "mcp__server_a.search", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        Routed b = router.route(new AxionToolRouter.Route("c_b", "mcp__server_b.search", "{}"),
                "sc", RunContext.bare("sc", "b", null), null);
        assertEquals("search", a.result().output());
        assertEquals("server_b search", b.result().output());
    }
}