package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/**
 * Output-schema boundary contract (provider integration fix):
 *
 * <ul>
 *   <li><b>internal model preserved</b> — {@code ToolSpec.outputSchema()} keeps
 *       working and {@code FunctionToolSpec} still stores it;</li>
 *   <li><b>FUNCTION_ONLY wire stripped</b> — the payload serialized for the
 *       function-only / OpenAI-compatible transport NEVER contains
 *       {@code output_schema};</li>
 *   <li><b>other tools intact</b> — removing the field from the wire
 *       representation does not break apply_patch, exec_command, write_stdin,
 *       update_plan, request_user_input, new_context, workspace read tools or
 *       MCP tools.</li>
 * </ul>
 */
public class ToolOutputSchemaBoundaryTest {

    private static AxionToolRegistry coreRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        return registry;
    }

    private static JSONArray functionOnly(List<ToolRegistration> registrations) {
        return ToolSpecSerializer.toProviderPayload(
                registrations, ProviderToolCapabilities.FUNCTION_ONLY).payload();
    }

    private static JSONObject functionByName(JSONArray payload, String qualifiedName) {
        for (int i = 0; i < payload.length(); i++) {
            JSONObject entry = payload.optJSONObject(i);
            JSONObject function = entry == null ? null : entry.optJSONObject("function");
            if (function != null && qualifiedName.equals(function.optString("name"))) {
                return function;
            }
        }
        return null;
    }

    private static boolean wireContains(JSONArray payload, String needle) {
        return payload.toString().contains(needle);
    }

    // ------------------------------------------------------------------
    // Case A — internal schema preserved
    // ------------------------------------------------------------------

    @Test
    public void outputSchemaStaysOnTheInternalSpec() {
        ToolRegistration ctx = ToolCatalog.from(coreRegistry()).get("get_context_remaining");
        assertNotNull(ctx);
        assertNotNull("outputSchema must remain on the spec", ctx.spec().outputSchema());
        assertEquals("object", ctx.spec().outputSchema().optString("type"));
    }

    @Test
    public void functionToolSpecStillStoresOutputSchema() throws Exception {
        JSONObject outputSchema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject().put("ok", new JSONObject().put("type", "boolean")));
        FunctionToolSpec spec = ToolSpec.function(
                ToolName.plain("custom"), "custom tool",
                new JSONObject().put("type", "object"), outputSchema, false);
        assertNotNull(spec.outputSchema());
        assertEquals("object", spec.outputSchema().optString("type"));
    }

    @Test
    public void modelCatalogStillCarriesOutputSchema() {
        // The model-facing catalog (NOT the provider payload) keeps the Codex
        // output_schema contract for tools that declare one.
        JSONArray catalog = ToolSpecSerializer.toCatalog(
                coreRegistry().modelVisibleTools());
        JSONObject fn = functionByName(catalog, "get_context_remaining");
        assertNotNull("get_context_remaining must be in the model catalog", fn);
        assertTrue("model catalog keeps output_schema",
                fn.optJSONObject("output_schema") != null);
    }

    // ------------------------------------------------------------------
    // Case B — FUNCTION_ONLY never carries output_schema
    // ------------------------------------------------------------------

    @Test
    public void functionOnlyPayloadNeverContainsOutputSchema() {
        JSONArray payload = functionOnly(coreRegistry().modelVisibleTools());
        assertTrue(payload.length() > 0);
        assertFalse("FUNCTION_ONLY wire must never serialize output_schema",
                wireContains(payload, "output_schema"));
    }

    @Test
    public void nullCapabilitiesDefaultToFunctionOnlyWithoutOutputSchema() {
        // toProviderPayload(..., null) falls back to the FUNCTION_ONLY profile.
        JSONArray payload = ToolSpecSerializer.toProviderPayload(
                ToolCatalog.from(coreRegistry()), null).payload();
        assertFalse(wireContains(payload, "output_schema"));
    }

    // ------------------------------------------------------------------
    // Case C — get_context_remaining
    // ------------------------------------------------------------------

    @Test
    public void contextRemainingWireShape() {
        JSONArray payload = functionOnly(coreRegistry().modelVisibleTools());
        JSONObject function = functionByName(payload, "get_context_remaining");
        assertNotNull(function);
    }

    @Test
    public void contextRemainingFinalPayloadMatchesProviderContract() {
        JSONArray payload = functionOnly(coreRegistry().modelVisibleTools());

        JSONObject entry = null;
        JSONObject function = null;
        for (int i = 0; i < payload.length(); i++) {
            JSONObject candidate = payload.optJSONObject(i);
            JSONObject fn = candidate == null ? null : candidate.optJSONObject("function");
            if (fn != null && "get_context_remaining".equals(fn.optString("name"))) {
                entry = candidate;
                function = fn;
                break;
            }
        }

        assertNotNull("get_context_remaining must be present", entry);
        assertEquals("function", entry.optString("type"));
        assertNotNull(function);
        assertEquals("get_context_remaining", function.optString("name"));
        assertEquals(false, function.optBoolean("strict", true));

        JSONObject parameters = function.optJSONObject("parameters");
        assertNotNull("parameters must be present", parameters);
        assertEquals("object", parameters.optString("type"));
        assertEquals(false, parameters.optBoolean("additionalProperties", true));

        assertFalse("output_schema must be absent from the final payload",
                function.has("output_schema"));
        assertFalse(function.optString("description", "").isEmpty());
    }

    // ------------------------------------------------------------------
    // Case D — other tools survive the wire change
    // ------------------------------------------------------------------

    @Test
    public void coreToolsetSerializesWithoutOutputSchema() {
        JSONArray payload = functionOnly(coreRegistry().modelVisibleTools());
        for (String tool : new String[]{"apply_patch",
                "update_plan", "request_user_input", "get_context_remaining", "new_context",
                "clock.curr_time", "clock.sleep"}) {
            JSONObject function = functionByName(payload, tool);
            assertNotNull("FUNCTION_ONLY payload must contain " + tool, function);
            assertNotNull("parameters must be present for " + tool,
                    function.optJSONObject("parameters"));
            assertFalse("output_schema absent for " + tool, function.has("output_schema"));
        }
    }

    @Test
    public void applyPatchStaysFreeformOnNativeWireAndFunctionOnFunctionOnly() {
        // Native wire: FREEFORM preserved (never an artificial JSON envelope).
        JSONArray nativeWire = ToolSpecSerializer.toProviderPayload(
                ToolCatalog.from(coreRegistry()), ProviderToolCapabilities.NATIVE_ALL).payload();
        JSONObject ff = null;
        for (int i = 0; i < nativeWire.length(); i++) {
            JSONObject entry = nativeWire.optJSONObject(i);
            if (entry != null && "freeform".equals(entry.optString("type"))
                    && "apply_patch".equals(entry.optJSONObject("freeform") == null
                    ? null : entry.optJSONObject("freeform").optString("name"))) {
                ff = entry.optJSONObject("freeform");
                break;
            }
        }
        assertNotNull("apply_patch stays FREEFORM on the native wire", ff);
        assertNotNull("freeform carries its raw grammar definition",
                ff.optJSONObject("format"));
        assertFalse("freeform never wraps the patch in JSON",
                nativeWire.toString().contains("apply_patch-envelope"));

        // FUNCTION_ONLY wire: explicit function fallback, still no output_schema.
        JSONArray payload = functionOnly(coreRegistry().modelVisibleTools());
        JSONObject function = functionByName(payload, "apply_patch");
        assertNotNull("apply_patch reached the function-only transport via the declared fallback",
                function);
        assertEquals("object", function.optJSONObject("parameters").optString("type"));
        assertFalse(function.has("output_schema"));
    }

    @Test
    public void workspaceReadToolsSerializeOnFunctionOnly() {
        AxionToolRegistry registry = coreRegistry();
        WorkspaceToolProvider.registerWorkspaceReadTools(registry);
        JSONArray payload = functionOnly(registry.modelVisibleTools());
        assertTrue("workspace read tools must be present",
                wireContains(payload, "read_file"));
        assertTrue(wireContains(payload, "ls_dir"));
        assertTrue(wireContains(payload, "search_in_file"));
        assertFalse(wireContains(payload, "output_schema"));
    }

    @Test
    public void mcpToolsSerializeOnFunctionOnly() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        // MCP servers surface their tools as plain FUNCTION registrations whose
        // names live under the mcp_ namespace.
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("mcp__server_a_call_tool"),
                        "Call an MCP tool exposed by server_a.",
                        new JSONObject()
                                .put("type", "object")
                                .put("properties", new JSONObject()
                                        .put("tool_name", new JSONObject().put("type", "string"))
                                        .put("arguments", new JSONObject().put("type", "object")))
                                .put("required", new JSONArray().put("tool_name"))))
                .source("mcp:server_a")
                .build());
        JSONArray payload = functionOnly(registry.modelVisibleTools());
        JSONObject function = functionByName(payload, "mcp__server_a_call_tool");
        assertNotNull("MCP tool must serialize on FUNCTION_ONLY", function);
        assertNotNull(function.optJSONObject("parameters"));
        assertFalse(function.has("output_schema"));
    }

    @Test
    public void nativeAllWireMayKeepOutputSchema() {
        // NATIVE_ALL declares the transport can carry output_schema: the field
        // is preserved on the wire for tools that declare it, proving the
        // internal contract was not deleted — only stripped on FUNCTION_ONLY.
        JSONArray payload = ToolSpecSerializer.toProviderPayload(
                ToolCatalog.from(coreRegistry()), ProviderToolCapabilities.NATIVE_ALL).payload();
        JSONObject function = functionByName(payload, "get_context_remaining");
        assertNotNull(function);
        assertNotNull("NATIVE wire may carry output_schema",
                function.optJSONObject("output_schema"));
    }
}