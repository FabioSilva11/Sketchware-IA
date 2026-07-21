package pro.sketchware.activities.chat.port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class VoidPortExtractGrammarTest {

    @Test
    public void streamingXmlProducesOnlyOneCompleteCall() throws Exception {
        JSONArray tools = tools("get_dir_tree", new String[]{},
                property("uri", "string"));
        VoidPortExtractGrammar.XmlToolStreamParser parser =
                new VoidPortExtractGrammar.XmlToolStreamParser(tools);

        assertNull(parser.accept("before\n<get_dir_tree>").toolCall);
        assertNull(parser.accept("<uri>/").toolCall);
        assertNull(parser.accept("storage/emulated/0/project</uri>").toolCall);

        VoidPortExtractGrammar.XmlToolStreamStep completed =
                parser.accept("</get_dir_tree>");
        assertNotNull(completed.toolCall);
        assertEquals("get_dir_tree", completed.toolCall.toolName);
        assertEquals("/storage/emulated/0/project",
                new JSONObject(completed.toolCall.toolArguments).getString("uri"));
        assertEquals(completed.toolCall.toolId, parser.getLatestToolCall().toolId);
    }

    @Test
    public void incompleteOuterOrParameterTagIsRejected() throws Exception {
        JSONArray tools = tools("get_dir_tree", new String[]{},
                property("uri", "string"));

        assertNull(VoidPortExtractGrammar.extractXmlToolCall(
                "<get_dir_tree><uri>/project</uri>", tools));
        assertNull(VoidPortExtractGrammar.extractXmlToolCall(
                "<get_dir_tree><uri>/project</get_dir_tree>", tools));
    }

    @Test
    public void incompleteEditWrapperCannotFallThroughToNakedEdit() throws Exception {
        JSONArray tools = tools("edit_file", new String[]{"uri", "search_replace_blocks"},
                property("uri", "string"),
                property("search_replace_blocks", "string"));

        assertNull(VoidPortExtractGrammar.extractXmlToolCall(
                "<edit_file><uri>/project/A.java</uri>\n"
                        + "<<<<<<< ORIGINAL\nold\n=======\nnew\n>>>>>>> UPDATED",
                tools));
    }

    @Test
    public void missingRequiredParameterIsRejected() throws Exception {
        JSONArray tools = tools("read_file", new String[]{"uri"},
                property("uri", "string"));

        assertNull(VoidPortExtractGrammar.extractXmlToolCall(
                "<read_file></read_file>", tools));
    }

    @Test
    public void xmlValuesFollowDeclaredSchemaTypes() throws Exception {
        JSONArray tools = tools("search_for_files", new String[]{"query"},
                property("query", "string"),
                property("is_regex", "boolean"),
                property("page_number", "integer"));

        VoidPortExtractGrammar.ToolCallExtraction extraction =
                VoidPortExtractGrammar.extractXmlToolCall(
                        "<search_for_files>"
                                + "<query>AgentManager</query>"
                                + "<is_regex>true</is_regex>"
                                + "<page_number>2</page_number>"
                                + "</search_for_files>",
                        tools);

        assertNotNull(extraction);
        JSONObject args = new JSONObject(extraction.toolArguments);
        assertEquals("AgentManager", args.getString("query"));
        assertTrue(args.getBoolean("is_regex"));
        assertEquals(2, args.getInt("page_number"));
        assertFalse(args.get("page_number") instanceof String);
    }

    private static JSONArray tools(String name, String[] required, JSONObject... properties)
            throws Exception {
        JSONObject propertyMap = new JSONObject();
        for (JSONObject property : properties) {
            propertyMap.put(property.getString("name"), property.getJSONObject("schema"));
        }
        JSONArray requiredArray = new JSONArray();
        for (String value : required) {
            requiredArray.put(value);
        }
        JSONObject parameters = new JSONObject()
                .put("type", "object")
                .put("properties", propertyMap)
                .put("required", requiredArray)
                .put("additionalProperties", false);
        JSONObject function = new JSONObject()
                .put("name", name)
                .put("parameters", parameters);
        return new JSONArray().put(new JSONObject()
                .put("type", "function")
                .put("function", function));
    }

    private static JSONObject property(String name, String type) throws Exception {
        return new JSONObject()
                .put("name", name)
                .put("schema", new JSONObject().put("type", type));
    }
}
