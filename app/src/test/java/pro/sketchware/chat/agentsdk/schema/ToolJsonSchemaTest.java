package pro.sketchware.chat.agentsdk.schema;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link ToolJsonSchema}, ensuring that the typed schema construction
 * prevents the {@code "items": [...]} bug that causes HTTP 400 errors.
 *
 * <p>These tests verify Codex parity with the schema construction patterns
 * from {@code codex-rs/tools/src/json_schema_tests.rs}.</p>
 */
public class ToolJsonSchemaTest {

    @Test
    public void primitiveTypes_generateCorrectJson() {
        JSONObject stringSchema = ToolJsonSchema.string().toJson();
        assertEquals("string", stringSchema.optString("type"));

        JSONObject numberSchema = ToolJsonSchema.number().toJson();
        assertEquals("number", numberSchema.optString("type"));

        JSONObject integerSchema = ToolJsonSchema.integer().toJson();
        assertEquals("integer", integerSchema.optString("type"));

        JSONObject boolSchema = ToolJsonSchema.bool().toJson();
        assertEquals("boolean", boolSchema.optString("type"));

        JSONObject nullSchema = ToolJsonSchema.nullSchema().toJson();
        assertEquals("null", nullSchema.optString("type"));
    }

    @Test
    public void primitiveTypes_withDescription_includesDescription() {
        JSONObject schema = ToolJsonSchema.string("A test string").toJson();
        assertEquals("string", schema.optString("type"));
        assertEquals("A test string", schema.optString("description"));
    }

    @Test
    public void arraySchema_itemsIsObject_notArray() {
        // CRITICAL TEST: items must be a single schema object, not an array
        ToolJsonSchema schema = ToolJsonSchema.array(ToolJsonSchema.string());
        JSONObject json = schema.toJson();

        assertEquals("array", json.optString("type"));
        assertTrue("items must be a JSONObject", json.opt("items") instanceof JSONObject);
        assertFalse("items must NOT be a JSONArray", json.opt("items") instanceof JSONArray);

        JSONObject items = json.optJSONObject("items");
        assertNotNull("items must exist", items);
        assertEquals("string", items.optString("type"));
    }

    @Test
    public void arrayOfObjects_itemsIsObjectSchema() {
        Map<String, ToolJsonSchema> props = ToolJsonSchema.properties()
                .put("label", ToolJsonSchema.string())
                .put("value", ToolJsonSchema.integer())
                .build();

        ToolJsonSchema objectSchema = ToolJsonSchema.object(props, Arrays.asList("label"));
        ToolJsonSchema arraySchema = ToolJsonSchema.array(objectSchema);

        JSONObject json = arraySchema.toJson();
        assertEquals("array", json.optString("type"));

        // items is a single object schema, not an array
        JSONObject items = json.optJSONObject("items");
        assertNotNull("items must exist as JSONObject", items);
        assertEquals("object", items.optString("type"));

        JSONObject properties = items.optJSONObject("properties");
        assertNotNull("object items must have properties", properties);
        assertTrue("must have label property", properties.has("label"));
        assertTrue("must have value property", properties.has("value"));
    }

    @Test
    public void objectSchema_withPropertiesAndRequired() {
        Map<String, ToolJsonSchema> props = ToolJsonSchema.properties()
                .put("name", ToolJsonSchema.string("User name"))
                .put("age", ToolJsonSchema.integer("User age"))
                .build();

        ToolJsonSchema schema = ToolJsonSchema.object(
                props,
                Arrays.asList("name"),
                false
        );

        JSONObject json = schema.toJson();
        assertEquals("object", json.optString("type"));
        assertFalse("additionalProperties should be false", json.optBoolean("additionalProperties"));

        JSONObject properties = json.optJSONObject("properties");
        assertNotNull("properties must exist", properties);
        assertEquals(2, properties.length());

        JSONArray required = json.optJSONArray("required");
        assertNotNull("required must exist", required);
        assertEquals(1, required.length());
        assertEquals("name", required.optString(0));
    }

    @Test
    public void stringEnum_generatesEnumArray() {
        ToolJsonSchema schema = ToolJsonSchema.stringEnum("option1", "option2", "option3");
        JSONObject json = schema.toJson();

        assertEquals("string", json.optString("type"));
        JSONArray enumArray = json.optJSONArray("enum");
        assertNotNull("enum array must exist", enumArray);
        assertEquals(3, enumArray.length());
        assertEquals("option1", enumArray.optString(0));
        assertEquals("option2", enumArray.optString(1));
        assertEquals("option3", enumArray.optString(2));
    }

    @Test
    public void anyOf_composition() {
        ToolJsonSchema schema = ToolJsonSchema.anyOf(
                ToolJsonSchema.string(),
                ToolJsonSchema.integer()
        );
        JSONObject json = schema.toJson();

        JSONArray anyOf = json.optJSONArray("anyOf");
        assertNotNull("anyOf array must exist", anyOf);
        assertEquals(2, anyOf.length());

        JSONObject first = anyOf.optJSONObject(0);
        assertEquals("string", first.optString("type"));

        JSONObject second = anyOf.optJSONObject(1);
        assertEquals("integer", second.optString("type"));
    }

    @Test
    public void nestedArrayOfArrays_itemsIsAlwaysObject() {
        // array of arrays: items at each level is an object schema
        ToolJsonSchema innerArray = ToolJsonSchema.array(ToolJsonSchema.string());
        ToolJsonSchema outerArray = ToolJsonSchema.array(innerArray);

        JSONObject json = outerArray.toJson();
        assertEquals("array", json.optString("type"));

        JSONObject outerItems = json.optJSONObject("items");
        assertNotNull("outer items must be object", outerItems);
        assertEquals("array", outerItems.optString("type"));

        JSONObject innerItems = outerItems.optJSONObject("items");
        assertNotNull("inner items must be object", innerItems);
        assertEquals("string", innerItems.optString("type"));
    }

    @Test
    public void complexNestedObject_maintainsStructure() {
        Map<String, ToolJsonSchema> addressProps = ToolJsonSchema.properties()
                .put("street", ToolJsonSchema.string())
                .put("city", ToolJsonSchema.string())
                .build();

        Map<String, ToolJsonSchema> personProps = ToolJsonSchema.properties()
                .put("name", ToolJsonSchema.string())
                .put("addresses", ToolJsonSchema.array(
                        ToolJsonSchema.object(addressProps)))
                .build();

        ToolJsonSchema schema = ToolJsonSchema.object(personProps);
        JSONObject json = schema.toJson();

        JSONObject properties = json.optJSONObject("properties");
        JSONObject addresses = properties.optJSONObject("addresses");
        assertEquals("array", addresses.optString("type"));

        JSONObject addressItems = addresses.optJSONObject("items");
        assertNotNull("address items must be object", addressItems);
        assertEquals("object", addressItems.optString("type"));

        JSONObject addressProperties = addressItems.optJSONObject("properties");
        assertTrue("must have street", addressProperties.has("street"));
        assertTrue("must have city", addressProperties.has("city"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void arraySchema_withNullItems_throwsException() {
        ToolJsonSchema.array(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void enumSchema_withEmptyValues_throwsException() {
        ToolJsonSchema.stringEnum();
    }
}
