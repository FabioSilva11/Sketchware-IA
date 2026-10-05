package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class OpenAiToolSchemaNormalizerTest {

    @Test
    public void enablesStrictModeAndMakesOptionalPropertiesNullableRequired() throws Exception {
        JSONObject properties = new JSONObject()
                .put("path", new JSONObject().put("type", "string"))
                .put("line", new JSONObject().put("type", "integer"));
        JSONObject parameters = new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", new JSONArray().put("path"));
        JSONArray source = new JSONArray().put(new JSONObject()
                .put("type", "function")
                .put("function", new JSONObject()
                        .put("name", "read_file")
                        .put("parameters", parameters)));

        JSONArray normalized = OpenAiToolSchemaNormalizer.forRequest(source, true);
        JSONObject function = normalized.getJSONObject(0).getJSONObject("function");
        JSONObject strictParameters = function.getJSONObject("parameters");

        assertTrue(function.getBoolean("strict"));
        assertFalse(strictParameters.getBoolean("additionalProperties"));
        assertEquals(2, strictParameters.getJSONArray("required").length());
        assertEquals("integer",
                strictParameters.getJSONObject("properties")
                        .getJSONObject("line").getJSONArray("type").getString(0));
        assertEquals("null",
                strictParameters.getJSONObject("properties")
                        .getJSONObject("line").getJSONArray("type").getString(1));
        assertFalse(source.getJSONObject(0).getJSONObject("function").has("strict"));
    }

    @Test
    public void leavesMcpAndUnsupportedSchemasNonStrict() throws Exception {
        JSONArray tools = new JSONArray()
                .put(tool("mcp_server_search", new JSONObject()
                        .put("type", "object").put("properties", new JSONObject())))
                .put(tool("custom", new JSONObject().put("anyOf", new JSONArray())));

        JSONArray normalized = OpenAiToolSchemaNormalizer.forRequest(tools, true);

        assertFalse(normalized.getJSONObject(0).getJSONObject("function").has("strict"));
        assertFalse(normalized.getJSONObject(1).getJSONObject("function").has("strict"));
    }

    private static JSONObject tool(String name, JSONObject parameters) throws Exception {
        return new JSONObject().put("type", "function")
                .put("function", new JSONObject()
                        .put("name", name)
                        .put("parameters", parameters));
    }
}
