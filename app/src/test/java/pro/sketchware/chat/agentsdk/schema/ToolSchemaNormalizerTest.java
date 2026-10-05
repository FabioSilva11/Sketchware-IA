package pro.sketchware.chat.agentsdk.schema;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link ToolSchemaNormalizer}, verifying that it catches invalid
 * schema structures before they cause HTTP 400 errors from the provider.
 *
 * <p>These tests ensure the normalizer detects the exact error pattern that
 * was causing the original bug: {@code "items": [...]}</p>
 */
public class ToolSchemaNormalizerTest {

    @Test
    public void validArraySchema_withObjectItems_passes() throws Exception {
        // Arrays appear nested inside tool-argument objects; the root itself
        // must always be "object" for a tool parameters schema.
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("items", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject()
                                        .put("type", "string"))));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertTrue("Valid array schema should pass", result.isValid());
        assertNotNull(result.getSchema());
    }

    @Test
    public void invalidArraySchema_withArrayItems_fails() throws Exception {
        // This is the EXACT bug pattern that was causing HTTP 400
        JSONArray itemsAsArray = new JSONArray()
                .put(new JSONObject().put("type", "string"));

        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("items", new JSONObject()
                                .put("type", "array")
                                .put("items", itemsAsArray)));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertFalse("Array with items as JSONArray must fail", result.isValid());
        assertNotNull("Error message must be provided", result.getErrorMessage());
        assertTrue("Error should mention items",
                result.getErrorMessage().toLowerCase().contains("items"));
    }

    @Test
    public void objectSchema_withoutType_butWithProperties_infersObject() throws Exception {
        JSONObject schema = new JSONObject()
                .put("properties", new JSONObject()
                        .put("name", new JSONObject().put("type", "string")));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertTrue("Should infer object type", result.isValid());
        assertEquals("object", result.getSchema().optString("type"));
    }

    @Test
    public void rootSchema_withNonObjectType_fails() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "string");

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertFalse("Root must be object type", result.isValid());
        assertTrue("Error should mention object type requirement",
                result.getErrorMessage().contains("object type"));
    }

    @Test
    public void requiredField_notInProperties_fails() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("name", new JSONObject().put("type", "string")))
                .put("required", new JSONArray().put("age"));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertFalse("Required field not in properties should fail", result.isValid());
        assertTrue("Error should mention required field",
                result.getErrorMessage().contains("age"));
    }

    @Test
    public void nestedArraySchema_withInvalidItems_fails() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("options", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONArray()
                                        .put(new JSONObject().put("type", "object")))));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertFalse("Nested array with invalid items should fail", result.isValid());
        assertTrue("Error path should point to nested items",
                result.getErrorPath().contains("properties.options.items"));
    }

    @Test
    public void arraySchema_withoutItems_addsPermissiveDefault() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("tags", new JSONObject()
                                .put("type", "array")));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertTrue("Array without items should be normalized", result.isValid());

        JSONObject tags = result.getSchema()
                .optJSONObject("properties")
                .optJSONObject("tags");
        assertNotNull("tags property should exist", tags);
        assertTrue("items should be added", tags.has("items"));
    }

    @Test
    public void compositionSchema_anyOf_validates() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("value", new JSONObject()
                                .put("anyOf", new JSONArray()
                                        .put(new JSONObject().put("type", "string"))
                                        .put(new JSONObject().put("type", "number")))));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertTrue("Composition schema should validate", result.isValid());
    }

    @Test
    public void compositionSchema_withInvalidMember_fails() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("value", new JSONObject()
                                .put("anyOf", new JSONArray()
                                        .put("not-a-schema"))));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertFalse("Composition with invalid member should fail", result.isValid());
    }

    @Test
    public void emptySchema_isValid() {
        JSONObject schema = new JSONObject();

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertTrue("Empty schema (no parameters) should be valid", result.isValid());
    }

    @Test
    public void nullSchema_isValid() {
        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", null);

        assertTrue("Null schema (no parameters) should be valid", result.isValid());
    }

    @Test
    public void additionalProperties_asBoolean_isValid() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("name", new JSONObject().put("type", "string")))
                .put("additionalProperties", false);

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertTrue("additionalProperties as boolean should be valid", result.isValid());
        assertFalse(result.getSchema().optBoolean("additionalProperties"));
    }

    @Test
    public void additionalProperties_asSchema_validates() throws Exception {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("name", new JSONObject().put("type", "string")))
                .put("additionalProperties", new JSONObject()
                        .put("type", "string"));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("test_tool", schema);

        assertTrue("additionalProperties as schema should be valid", result.isValid());
        JSONObject addProps = result.getSchema().optJSONObject("additionalProperties");
        assertNotNull("additionalProperties schema should exist", addProps);
        assertEquals("string", addProps.optString("type"));
    }

    @Test
    public void realWorldExample_requestUserInput_old_fails() throws Exception {
        // This is the ACTUAL buggy schema from the old RequestUserInputTool
        JSONArray optionsAsArray = new JSONArray()
                .put(new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("label", new JSONObject().put("type", "string"))
                                .put("description", new JSONObject().put("type", "string")))
                        .put("required", new JSONArray().put("label")));

        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("question", new JSONObject().put("type", "string"))
                        .put("options", new JSONObject()
                                .put("type", "array")
                                .put("items", optionsAsArray)))
                .put("required", new JSONArray().put("question"));

        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("request_user_input", schema);

        assertFalse("Old buggy schema must be rejected", result.isValid());
        assertTrue("Error should point to items",
                result.getErrorPath().contains("items"));
    }

    @Test
    public void realWorldExample_requestUserInput_new_passes() throws Exception {
        // This is the CORRECT schema using ToolJsonSchema
        Map<String, ToolJsonSchema> optionProps = ToolJsonSchema.properties()
                .put("label", ToolJsonSchema.string())
                .put("description", ToolJsonSchema.string())
                .build();

        ToolJsonSchema optionSchema = ToolJsonSchema.object(
                optionProps,
                java.util.Arrays.asList("label"),
                false
        );

        Map<String, ToolJsonSchema> mainProps = ToolJsonSchema.properties()
                .put("question", ToolJsonSchema.string())
                .put("options", ToolJsonSchema.array(optionSchema))
                .build();

        ToolJsonSchema schema = ToolJsonSchema.object(
                mainProps,
                java.util.Arrays.asList("question"),
                false
        );

        JSONObject json = schema.toJson();
        ToolSchemaNormalizer.ValidationResult result =
                ToolSchemaNormalizer.normalize("request_user_input", json);

        assertTrue("New correct schema must pass", result.isValid());

        // Verify structure
        JSONObject options = result.getSchema()
                .optJSONObject("properties")
                .optJSONObject("options");
        assertEquals("array", options.optString("type"));

        JSONObject items = options.optJSONObject("items");
        assertNotNull("items must be object", items);
        assertEquals("object", items.optString("type"));
    }
}
