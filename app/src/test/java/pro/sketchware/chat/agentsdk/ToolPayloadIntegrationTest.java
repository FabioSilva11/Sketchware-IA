package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.agentsdk.schema.ToolSchemaNormalizer;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.ProviderCatalogPayload;
import pro.sketchware.chat.agentsdk.tools.ProviderToolCapabilities;
import pro.sketchware.chat.agentsdk.tools.ToolCatalog;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.ToolSpecSerializer;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration test validating the complete tool payload that would be sent to
 * an LLM provider. The payload now comes from the single canonical path —
 * {@link AxionToolRegistry} → {@link ToolCatalog} → {@link ToolSpecSerializer}
 * — and this test ensures no entry produces the invalid {@code "items": [...]}
 * pattern that caused HTTP 400.
 */
public class ToolPayloadIntegrationTest {

    private static final String INVALID_ITEMS_PATTERN = "\"items\":[{";
    private static final String VALID_ITEMS_PATTERN = "\"items\":{";

    /** The core Codex-parity registry (apply_patch, request_user_input, ...). */
    private AxionToolRegistry coreRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null, null);
        return registry;
    }

    /** FUNCTION_ONLY wire — what every current provider transport receives. */
    private JSONArray functionOnlyPayload() {
        return ToolSpecSerializer.toProviderPayload(
                ToolCatalog.from(coreRegistry()), ProviderToolCapabilities.FUNCTION_ONLY).payload();
    }

    /** NATIVE per-kind wire — freeform/namespace/tool_search preserved. */
    private JSONArray nativePayload() {
        return ToolSpecSerializer.toProviderPayload(
                ToolCatalog.from(coreRegistry()), ProviderToolCapabilities.NATIVE_ALL).payload();
    }

    /** The FUNCTION entry with the given qualified name, or {@code null}. */
    private JSONObject functionEntry(String qualifiedName) {
        JSONArray payload = functionOnlyPayload();
        for (int i = 0; i < payload.length(); i++) {
            JSONObject function = payload.optJSONObject(i).optJSONObject("function");
            if (function != null && qualifiedName.equals(function.optString("name"))) {
                return function;
            }
        }
        return null;
    }

    @Test
    public void defaultToolset_allSchemasValid() {
        JSONArray payload = functionOnlyPayload();
        assertFalse("The core registry must expose tools", payload.length() == 0);

        for (int i = 0; i < payload.length(); i++) {
            JSONObject entry = payload.optJSONObject(i);
            if (entry == null || !"function".equals(entry.optString("type"))) {
                continue;
            }
            JSONObject function = entry.optJSONObject("function");
            ToolSchemaNormalizer.ValidationResult result =
                    ToolSchemaNormalizer.normalize(function.optString("name"),
                            function.optJSONObject("parameters"));
            assertTrue("Tool " + function.optString("name") + " must have valid schema: " +
                            (result.isValid() ? "OK" : result.getFullErrorMessage()),
                    result.isValid());
        }
    }

    @Test
    public void requestUserInputTool_payloadStructure() {
        JSONObject function = functionEntry("request_user_input");
        assertNotNull("request_user_input must be in the FUNCTION_ONLY payload", function);

        JSONObject parameters = function.optJSONObject("parameters");
        assertEquals("object", parameters.optString("type"));

        JSONObject questions = parameters
                .optJSONObject("properties")
                .optJSONObject("questions");
        assertNotNull("questions property must exist", questions);
        assertEquals("array", questions.optString("type"));

        // CRITICAL: items must be a JSONObject, not a JSONArray
        Object items = questions.opt("items");
        assertTrue("items must be a JSONObject, not a JSONArray",
                items instanceof JSONObject);
        assertEquals("object", ((JSONObject) items).optString("type"));

        // Verify the invalid pattern is NOT present anywhere in the payload
        String json = functionOnlyPayload().toString();
        assertFalse("Payload must NOT contain the items-array pattern",
                json.contains(INVALID_ITEMS_PATTERN));
    }

    @Test
    public void applyPatchTool_isFreeformOnNativeWire() {
        JSONArray payload = nativePayload();
        JSONObject freeform = null;
        for (int i = 0; i < payload.length(); i++) {
            JSONObject entry = payload.optJSONObject(i);
            JSONObject ff = entry == null || !"freeform".equals(entry.optString("type"))
                    ? null : entry.optJSONObject("freeform");
            if (ff != null && "apply_patch".equals(ff.optString("name"))) {
                freeform = ff;
                break;
            }
        }
        assertNotNull("apply_patch must stay FREEFORM on the native wire", freeform);
        JSONObject format = freeform.optJSONObject("format");
        assertNotNull("freeform must carry a format", format);
        assertEquals("grammar", format.optString("type"));
        assertEquals("lark", format.optString("syntax"));
        assertTrue("format must describe the patch grammar",
                format.optString("definition", "").contains("Begin Patch"));
    }

    @Test
    public void applyPatchTool_downgradesOnlyWhenDeclaredHolds() {
        // FUNCTION_ONLY declares the freeform->function fallback: the tool must
        // appear as a function envelope AND the downgrade must be recorded.
        ProviderCatalogPayload catalogPayload = ToolSpecSerializer.toProviderPayload(
                ToolCatalog.from(coreRegistry()), ProviderToolCapabilities.FUNCTION_ONLY);
        assertTrue("the freeform downgrade must be recorded",
                catalogPayload.freeformFellBack());
        assertNotNull("apply_patch must still reach the function-only transport",
                functionEntry("apply_patch"));
    }

    @Test
    public void contextRemainingTool_payloadStructure() {
        JSONObject function = functionEntry("get_context_remaining");
        assertNotNull("get_context_remaining must be in the FUNCTION_ONLY payload", function);

        JSONObject parameters = function.optJSONObject("parameters");
        assertEquals("object", parameters.optString("type"));
        assertFalse("Should not allow additional properties",
                parameters.optBoolean("additionalProperties", true));

        // The provider-bound FUNCTION_ONLY wire must NOT carry output_schema —
        // it is rejected by current transports. The internal spec keeps it.
        assertFalse("output_schema must not be sent on the FUNCTION_ONLY wire",
                function.has("output_schema"));
    }

    @Test
    public void contextRemainingTool_internalOutputSchemaPreserved() {
        ToolRegistration ctx = ToolCatalog.from(coreRegistry()).get("get_context_remaining");
        assertNotNull("get_context_remaining must be registered", ctx);
        assertNotNull("outputSchema must stay available on the internal spec",
                ctx.spec().outputSchema());
        assertEquals("object", ctx.spec().outputSchema().optString("type"));
        assertNotNull(ctx.spec().outputSchema().optJSONObject("properties")
                .opt("tokens_left"));
    }

    @Test
    public void fullToolsArray_canBeSerialized() {
        JSONArray payload = functionOnlyPayload();

        String json = payload.toString();
        assertNotNull("Tools array must be serializable", json);

        // Verify no invalid patterns anywhere
        assertFalse("Must NOT contain the buggy items array pattern",
                json.contains(INVALID_ITEMS_PATTERN));

        // Expected tools are present (function-only transport)
        assertTrue("Must contain apply_patch", json.contains("apply_patch"));
        assertTrue("Must contain request_user_input", json.contains("request_user_input"));
        assertTrue("Must contain get_context_remaining", json.contains("get_context_remaining"));
    }

    @Test
    public void fullPayload_asItWouldBeSentToProvider() throws Exception {
        // This test simulates the exact payload structure sent to a provider
        // like custom_mocklocal (the one that was failing with HTTP 400).
        JSONObject request = new JSONObject()
                .put("model", "gpt-oss-120b-medium")
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", "You are a helpful assistant."))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", "Hello")));

        request.put("tools", functionOnlyPayload());

        String json = request.toString(2); // Pretty print
        assertNotNull("Request must be serializable", json);

        // Verify critical constraints
        assertFalse("Must NOT have invalid items array pattern",
                json.contains(INVALID_ITEMS_PATTERN));
        assertTrue("Must have valid items object pattern",
                json.replaceAll("\\s+", "").contains(VALID_ITEMS_PATTERN));
        assertTrue("Must contain request_user_input",
                json.contains("request_user_input"));
    }
}