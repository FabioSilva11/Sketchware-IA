package pro.sketchware.activities.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import pro.sketchware.ia.tools.ToolManager;

public class ContextBuilderTest {

    @Test
    public void pruningPreservesFirstUserAfterAssistantSummary() throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(message("assistant", repeat("summary", 300)));
        messages.put(message("user", "ORIGINAL REQUEST"));
        messages.put(message("assistant", repeat("middle", 300)));
        messages.put(message("user", "latest request"));

        ContextBuilder builder = new ContextBuilder("test", new ArrayList<>(), new ToolManager());
        Method trim = ContextBuilder.class.getDeclaredMethod(
                "trimProviderMessages", JSONArray.class, int.class);
        trim.setAccessible(true);
        JSONArray result = (JSONArray) trim.invoke(builder, messages, 80);

        boolean foundOriginal = false;
        for (int i = 0; i < result.length(); i++) {
            if ("ORIGINAL REQUEST".equals(result.getJSONObject(i).optString("content"))) {
                foundOriginal = true;
            }
        }
        assertEquals(true, foundOriginal);
    }

    @Test
    public void pruningDoesNotLeaveOrphanOpenAiToolResult() throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(message("user", "ORIGINAL REQUEST"));
        messages.put(new JSONObject()
                .put("role", "assistant")
                .put("content", repeat("tool request", 200))
                .put("tool_calls", new JSONArray().put(new JSONObject().put("id", "call_1"))));
        messages.put(new JSONObject()
                .put("role", "tool")
                .put("tool_call_id", "call_1")
                .put("content", repeat("tool result", 200)));
        messages.put(message("assistant", repeat("old answer", 200)));
        messages.put(message("user", "latest request"));

        JSONArray result = trim(messages, 100);
        for (int i = 0; i < result.length(); i++) {
            assertFalse("tool".equals(result.getJSONObject(i).optString("role")));
        }
    }

    @Test
    public void toolCountRuleDependsOnProviderFormat() throws Exception {
        ContextBuilder builder = new ContextBuilder("test", new ArrayList<>(), new ToolManager());
        Method details = ContextBuilder.class.getDeclaredMethod(
                "buildVoidImportantDetails", String.class, ContextBuilder.ProviderFormat.class);
        details.setAccessible(true);

        String nativePrompt = (String) details.invoke(
                builder, "agent", ContextBuilder.ProviderFormat.OPENAI);
        String xmlPrompt = (String) details.invoke(
                builder, "agent", ContextBuilder.ProviderFormat.XML_FALLBACK);

        assertTrue(nativePrompt.contains("multiple independent tools"));
        assertFalse(nativePrompt.contains("exactly one XML tool call"));
        assertTrue(xmlPrompt.contains("exactly one XML tool call"));
    }

    private static JSONArray trim(JSONArray messages, int budget) throws Exception {
        ContextBuilder builder = new ContextBuilder("test", new ArrayList<>(), new ToolManager());
        Method trim = ContextBuilder.class.getDeclaredMethod(
                "trimProviderMessages", JSONArray.class, int.class);
        trim.setAccessible(true);
        return (JSONArray) trim.invoke(builder, messages, budget);
    }

    private static JSONObject message(String role, String content) throws Exception {
        return new JSONObject().put("role", role).put("content", content);
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value).append(' ');
        }
        return builder.toString();
    }
}
