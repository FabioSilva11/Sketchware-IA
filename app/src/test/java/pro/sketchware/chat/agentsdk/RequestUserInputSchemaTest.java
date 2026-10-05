package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.agentsdk.schema.ToolSchemaNormalizer;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.FunctionToolSpec;
import pro.sketchware.chat.agentsdk.tools.ToolCatalog;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the {@code request_user_input} schema structure produced by the core
 * registry ({@link WorkspaceToolProvider}), verifying that the fix for the
 * HTTP 400 bug is correct: every {@code items} is a single schema object,
 * never an array.
 */
public class RequestUserInputSchemaTest {

    /** The registered request_user_input FUNCTION spec, as the router sees it. */
    private JSONObject requestUserInputSchema() {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null, null);
        ToolRegistration registration = ToolCatalog.from(registry).get("request_user_input");
        assertNotNull("request_user_input must be registered in the core registry", registration);
        assertTrue("request_user_input must be a FUNCTION spec",
                registration.spec() instanceof FunctionToolSpec);
        return ((FunctionToolSpec) registration.spec()).parameters();
    }

    @Test
    public void requestUserInputSchema_isValid() {
        JSONObject schema = requestUserInputSchema();

        // The schema must be valid according to our normalizer
        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("request_user_input", schema);

        assertTrue("request_user_input schema must be valid: " +
                        (result.isValid() ? "OK" : result.getFullErrorMessage()),
                result.isValid());
    }

    @Test
    public void requestUserInputSchema_rootIsObject() {
        JSONObject schema = requestUserInputSchema();

        assertEquals("Root type must be object", "object", schema.optString("type"));
    }

    @Test
    public void requestUserInputSchema_hasRequiredProperties() {
        JSONObject schema = requestUserInputSchema();

        JSONObject properties = schema.optJSONObject("properties");
        assertNotNull("Schema must have properties", properties);
        assertTrue("Must have questions property", properties.has("questions"));
    }

    @Test
    public void requestUserInputSchema_questionsIsArray() {
        JSONObject schema = requestUserInputSchema();

        JSONObject questions = schema
                .optJSONObject("properties")
                .optJSONObject("questions");

        assertNotNull("questions property must exist", questions);
        assertEquals("questions must be array type", "array", questions.optString("type"));
    }

    @Test
    public void requestUserInputSchema_questionIsString() {
        JSONObject schema = requestUserInputSchema();

        JSONObject question = schema
                .optJSONObject("properties")
                .optJSONObject("questions")
                .optJSONObject("items")
                .optJSONObject("properties")
                .optJSONObject("question");

        assertNotNull("question property must exist", question);
        assertEquals("question must be string type", "string", question.optString("type"));
    }

    @Test
    public void requestUserInputSchema_optionsIsArray() {
        JSONObject schema = requestUserInputSchema();

        JSONObject options = schema
                .optJSONObject("properties")
                .optJSONObject("questions")
                .optJSONObject("items")
                .optJSONObject("properties")
                .optJSONObject("options");

        assertNotNull("options property must exist", options);
        assertEquals("options must be array type", "array", options.optString("type"));
    }

    @Test
    public void requestUserInputSchema_optionsItems_isObject_notArray() {
        // CRITICAL: This is the guard that would fail with the old buggy code
        JSONObject schema = requestUserInputSchema();

        JSONObject options = schema
                .optJSONObject("properties")
                .optJSONObject("questions")
                .optJSONObject("items")
                .optJSONObject("properties")
                .optJSONObject("options");

        Object items = options.opt("items");
        assertNotNull("options.items must exist", items);

        assertTrue("options.items must be a JSONObject, not a JSONArray",
                items instanceof JSONObject);
        assertFalse("options.items must NOT be a JSONArray (this was the bug)",
                items instanceof JSONArray);
    }

    @Test
    public void requestUserInputSchema_optionsItemsSchema_isObject() {
        JSONObject schema = requestUserInputSchema();

        JSONObject items = schema
                .optJSONObject("properties")
                .optJSONObject("questions")
                .optJSONObject("items")
                .optJSONObject("properties")
                .optJSONObject("options")
                .optJSONObject("items");

        assertNotNull("options.items must be a schema object", items);
        assertEquals("options.items must be object type", "object", items.optString("type"));
    }

    @Test
    public void requestUserInputSchema_optionHasLabelAndDescription() {
        JSONObject schema = requestUserInputSchema();

        JSONObject itemProperties = schema
                .optJSONObject("properties")
                .optJSONObject("questions")
                .optJSONObject("items")
                .optJSONObject("properties")
                .optJSONObject("options")
                .optJSONObject("items")
                .optJSONObject("properties");

        assertNotNull("option schema must have properties", itemProperties);
        assertTrue("option must have label", itemProperties.has("label"));
        assertTrue("option must have description", itemProperties.has("description"));

        JSONObject label = itemProperties.optJSONObject("label");
        assertEquals("label must be string", "string", label.optString("type"));

        JSONObject description = itemProperties.optJSONObject("description");
        assertEquals("description must be string", "string", description.optString("type"));
    }

    @Test
    public void requestUserInputSchema_optionLabelIsRequired() {
        JSONObject schema = requestUserInputSchema();

        JSONObject items = schema
                .optJSONObject("properties")
                .optJSONObject("questions")
                .optJSONObject("items")
                .optJSONObject("properties")
                .optJSONObject("options")
                .optJSONObject("items");

        JSONArray required = items.optJSONArray("required");
        assertNotNull("options items must have required array", required);
        assertTrue("option labels must be required",
                contains(required, "label"));
        assertTrue("option descriptions must be required",
                contains(required, "description"));
    }

    @Test
    public void requestUserInputSchema_questionIdHeaderAndOptionsAreRequired() {
        JSONObject schema = requestUserInputSchema();

        JSONObject items = schema
                .optJSONObject("properties")
                .optJSONObject("questions")
                .optJSONObject("items");

        JSONArray required = items.optJSONArray("required");
        assertNotNull("questions items must have required array", required);
        assertTrue("id must be required", contains(required, "id"));
        assertTrue("header must be required", contains(required, "header"));
        assertTrue("question must be required", contains(required, "question"));
        assertTrue("options must be required", contains(required, "options"));
    }

    @Test
    public void requestUserInputSchema_questionsIsRequired() {
        JSONObject schema = requestUserInputSchema();

        JSONArray required = schema.optJSONArray("required");
        assertNotNull("Schema must have required array", required);
        assertEquals(1, required.length());
        assertEquals("questions", required.optString(0));
    }

    @Test
    public void requestUserInputSchema_noAdditionalProperties() {
        JSONObject schema = requestUserInputSchema();

        assertFalse("additionalProperties should be false",
                schema.optBoolean("additionalProperties", true));
    }

    @Test
    public void requestUserInputSchema_structure_matchesExpectation() throws Exception {
        // Final comprehensive test: the wire structure is
        // {
        //   "type": "object",
        //   "properties": {
        //     "questions": {
        //       "type": "array",
        //       "items": {
        //         "type": "object",
        //         "properties": {
        //           "id": { "type": "string" },
        //           "header": { "type": "string" },
        //           "question": { "type": "string" },
        //           "options": {
        //             "type": "array",
        //             "items": {
        //               "type": "object",
        //               "properties": {
        //                 "label": { "type": "string" },
        //                 "description": { "type": "string" }
        //               },
        //               "required": ["description", "label"]
        //             }
        //           }
        //         },
        //         "required": ["header", "id", "options", "question"]
        //       }
        //     }
        //   },
        //   "required": ["questions"]
        // }

        JSONObject schema = requestUserInputSchema();

        // Root level
        assertEquals("object", schema.optString("type"));
        assertFalse(schema.optBoolean("additionalProperties"));
        assertEquals(1, schema.optJSONObject("properties").length()); // questions

        // questions
        JSONObject questions = schema.getJSONObject("properties").getJSONObject("questions");
        assertEquals("array", questions.optString("type"));

        // questions.items (MUST be object, not array)
        JSONObject items = questions.getJSONObject("items");
        assertNotNull("items must be object", items);
        assertEquals("object", items.optString("type"));

        // questions.items.properties
        JSONObject itemProps = items.getJSONObject("properties");
        assertEquals(4, itemProps.length()); // id, header, question, options
        assertEquals("string", itemProps.getJSONObject("id").optString("type"));
        assertEquals("string", itemProps.getJSONObject("header").optString("type"));
        assertEquals("string", itemProps.getJSONObject("question").optString("type"));

        // options
        JSONObject options = itemProps.getJSONObject("options");
        assertEquals("array", options.optString("type"));

        // options.items (MUST be object, not array)
        JSONObject optionItems = options.getJSONObject("items");
        assertEquals("object", optionItems.optString("type"));

        // options.items.properties
        JSONObject optionProps = optionItems.getJSONObject("properties");
        assertEquals(2, optionProps.length()); // label, description
        assertEquals("string", optionProps.getJSONObject("label").optString("type"));
        assertEquals("string", optionProps.getJSONObject("description").optString("type"));

        // option required
        JSONArray optionRequired = optionItems.getJSONArray("required");
        assertEquals(2, optionRequired.length());
        assertTrue(contains(optionRequired, "label"));
        assertTrue(contains(optionRequired, "description"));

        // question required
        JSONArray required = items.getJSONArray("required");
        assertEquals(4, required.length());
        assertTrue(contains(required, "id"));
        assertTrue(contains(required, "header"));
        assertTrue(contains(required, "question"));
        assertTrue(contains(required, "options"));

        // Root required
        assertEquals(1, schema.getJSONArray("required").length());
        assertEquals("questions", schema.getJSONArray("required").optString(0));
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) {
                return true;
            }
        }
        return false;
    }
}