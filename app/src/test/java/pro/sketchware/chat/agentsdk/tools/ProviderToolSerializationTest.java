package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.tools.ToolSpec.Type;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Provider parity: WorkspaceToolProvider.registerCoreTools assembles the full
 * Codex core toolset into the registry, and ToolSpecSerializer produces a
 * catalog where every kind keeps its OWN wire shape (function/freeform/
 * namespace/tool_search) with the Codex contracts attached.
 */
public class ProviderToolSerializationTest {

    private AxionToolRegistry coreRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        return registry;
    }

    @Test
    public void providerRegistersFullCoreSet() {
        AxionToolRegistry registry = coreRegistry();
        assertTrue(registry.contains("apply_patch"));
        assertFalse(registry.contains("exec_command"));
        assertFalse(registry.contains("write_stdin"));
        assertTrue(registry.contains("update_plan"));
        assertTrue(registry.contains("request_user_input"));
        assertTrue(registry.contains("get_context_remaining"));
        // new_context did nothing and clock.sleep had nothing to wait for: neither is offered
        assertFalse(registry.contains("new_context"));
        assertTrue(registry.contains("clock.curr_time"));
        assertFalse(registry.contains("clock.sleep"));
        assertTrue(registry.contains("tool_search"));
    }

    @Test
    public void catalogPreservesPerKindWireShapes() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        JSONArray catalog = ToolSpecSerializer.toCatalog(registry.modelVisibleTools());
        boolean sawFunction = false;
        boolean sawFreeform = false;
        boolean sawToolSearch = false;
        boolean sawNamespace = false;
        for (int i = 0; i < catalog.length(); i++) {
            String type = catalog.getJSONObject(i).getString("type");
            sawFunction |= "function".equals(type);
            sawFreeform |= "freeform".equals(type);
            sawToolSearch |= "tool_search".equals(type);
            sawNamespace |= "namespace".equals(type);
        }
        assertTrue("function tools present", sawFunction);
        assertTrue("freeform apply_patch present", sawFreeform);
        assertTrue("tool_search present", sawToolSearch);
        assertTrue("clock namespace present", sawNamespace);
    }

    @Test
    public void applyPatchCatalogEntryIsFreeform() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        JSONArray catalog = ToolSpecSerializer.toCatalog(registry.modelVisibleTools());
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject entry = catalog.getJSONObject(i);
            if ("freeform".equals(entry.getString("type"))
                    && "apply_patch".equals(entry.optJSONObject("freeform")
                    .optString("name"))) {
                assertEquals("lark", entry.getJSONObject("freeform")
                        .getJSONObject("format").getString("syntax"));
                assertEquals(WorkspaceToolProvider.APPLY_PATCH_GRAMMAR,
                        entry.getJSONObject("freeform").getJSONObject("format")
                                .getString("definition"));
                return;
            }
        }
        org.junit.Assert.fail("apply_patch freeform entry missing");
    }

    @Test
    public void getContextRemainingCarriesOutputSchema() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        ToolRegistration ctx = registry.get("get_context_remaining");
        assertNotNull(ctx);
        JSONObject output = ctx.spec().outputSchema();
        assertNotNull("output_schema required by Codex contract", output);
        assertEquals("object", output.optString("type"));
        assertNotNull(output.optJSONObject("properties").opt("tokens_left"));
        assertEquals("boolean", output.optJSONObject("properties")
                .getJSONObject("budget_enforced").optString("type"));
    }

    @Test
    public void clockNamespaceGroupsTheTimeTool() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        JSONArray catalog = ToolSpecSerializer.toCatalog(registry.modelVisibleTools());
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject entry = catalog.getJSONObject(i);
            if ("namespace".equals(entry.getString("type"))
                    && "clock".equals(entry.getString("name"))) {
                JSONArray tools = entry.getJSONArray("tools");
                // clock.sleep isn't offered: the chat has nothing to wait for
                assertEquals(1, tools.length());
                return;
            }
        }
        org.junit.Assert.fail("clock namespace entry missing");
    }
}