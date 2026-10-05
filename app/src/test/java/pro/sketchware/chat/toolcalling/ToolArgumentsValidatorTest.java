package pro.sketchware.chat.toolcalling;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ToolArgumentsValidatorTest {
    @Test
    public void rejectsMalformedJson() {
        assertFalse(ToolArgumentsValidator.validate("{bad", null).isValid());
    }

    @Test
    public void rejectsScalarAndArrayInsteadOfObject() {
        assertFalse(ToolArgumentsValidator.validate("42", null).isValid());
        assertFalse(ToolArgumentsValidator.validate("[]", null).isValid());
    }

    @Test
    public void rejectsMissingRequiredParameter() throws Exception {
        JSONObject schema = objectSchema().put("required", new JSONArray().put("uri"));
        assertFalse(ToolArgumentsValidator.validate("{}", schema).isValid());
    }

    @Test
    public void rejectsUnknownParameterWhenSchemaIsStrict() throws Exception {
        JSONObject schema = objectSchema().put("additionalProperties", false);
        assertFalse(ToolArgumentsValidator.validate(
                "{\"uri\":\"A.java\",\"invented\":true}", schema).isValid());
    }

    @Test
    public void validatesNestedArrayAndEnum() throws Exception {
        JSONObject child = new JSONObject()
                .put("type", "object")
                .put("additionalProperties", false)
                .put("required", new JSONArray().put("mode"))
                .put("properties", new JSONObject().put("mode",
                        new JSONObject().put("type", "string")
                                .put("enum", new JSONArray().put("read").put("write"))));
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("additionalProperties", false)
                .put("properties", new JSONObject().put("items",
                        new JSONObject().put("type", "array")
                                .put("minItems", 1).put("items", child)));

        assertTrue(ToolArgumentsValidator.validate(
                "{\"items\":[{\"mode\":\"read\"}]}", schema).isValid());
        assertFalse(ToolArgumentsValidator.validate(
                "{\"items\":[{\"mode\":\"delete\"}]}", schema).isValid());
        assertFalse(ToolArgumentsValidator.validate(
                "{\"items\":[]}", schema).isValid());
    }

    @Test
    public void acceptsArgumentsMatchingSchema() throws Exception {
        JSONObject schema = objectSchema().put("required", new JSONArray().put("uri"));
        assertTrue(ToolArgumentsValidator.validate("{\"uri\":\"A.java\"}", schema).isValid());
    }

    @Test
    public void removesNullablePlaceholderForOptionalProperty() throws Exception {
        JSONObject schema = objectSchema();
        ToolArgumentsValidator.Result result = ToolArgumentsValidator.validate(
                "{\"uri\":null}", schema);
        assertTrue(result.isValid());
        assertFalse(result.getArguments().has("uri"));
    }

    private static JSONObject objectSchema() throws Exception {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject().put("uri",
                        new JSONObject().put("type", "string")));
    }
}
