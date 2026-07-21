package pro.sketchware.activities.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @Test
    public void userMessageUsesDynamicHistoryBudgetAndKeepsAllReferences() throws Exception {
        String longMessage = repeat("long-user-input", 1500);
        ChatMessage message = new ChatMessage(longMessage, ChatMessage.TYPE_USER, 1L);
        message.setStagingSelections(Arrays.asList(
                ChatReference.file("MainActivity.java", "/project/MainActivity.java"),
                ChatReference.folder("app", "/project/app")
        ));

        ContextBuilder builder = new ContextBuilder(
                "test", new ArrayList<>(Arrays.asList(message)), new ToolManager());
        Method toSimpleMessages = ContextBuilder.class.getDeclaredMethod("toSimpleMessages");
        toSimpleMessages.setAccessible(true);
        List<?> simpleMessages = (List<?>) toSimpleMessages.invoke(builder);

        Object simpleMessage = simpleMessages.get(0);
        java.lang.reflect.Field content = simpleMessage.getClass().getDeclaredField("content");
        content.setAccessible(true);
        java.lang.reflect.Field references = simpleMessage.getClass().getDeclaredField("references");
        references.setAccessible(true);

        assertTrue(((String) content.get(simpleMessage)).length() > 16_000);
        assertEquals(2, ((List<?>) references.get(simpleMessage)).size());
    }

    @Test
    public void xmlHistoryRebuildsToolOnlyAssistantTurn() throws Exception {
        ChatMessage user = new ChatMessage("Inspect the project", ChatMessage.TYPE_USER, 1L);
        ChatMessage tool = new ChatMessage("get_dir_tree", "{\"uri\":\".\"}", 2L, "call_1");
        tool.setToolResult("project\n  app");

        ContextBuilder builder = new ContextBuilder(
                "test", new ArrayList<>(Arrays.asList(user, tool)), new ToolManager());
        Method toSimpleMessages = ContextBuilder.class.getDeclaredMethod("toSimpleMessages");
        toSimpleMessages.setAccessible(true);
        List<?> simpleMessages = (List<?>) toSimpleMessages.invoke(builder);
        Method buildXml = ContextBuilder.class.getDeclaredMethod(
                "buildXmlFallbackMessages", List.class);
        buildXml.setAccessible(true);

        JSONArray result = (JSONArray) buildXml.invoke(builder, simpleMessages);

        assertEquals(3, result.length());
        assertEquals("user", result.getJSONObject(0).getString("role"));
        assertEquals("assistant", result.getJSONObject(1).getString("role"));
        assertTrue(result.getJSONObject(1).getString("content").contains("<get_dir_tree>"));
        assertEquals("user", result.getJSONObject(2).getString("role"));
        assertTrue(result.getJSONObject(2).getString("content").contains("project"));
    }

    @Test
    public void xmlFormatExampleOmitsOptionalParametersButStillDocumentsThem() throws Exception {
        JSONObject properties = new JSONObject()
                .put("query", new JSONObject()
                        .put("type", "string")
                        .put("description", "Search query"))
                .put("page_number", new JSONObject()
                        .put("type", "integer")
                        .put("description", "Optional page"));
        JSONObject parameters = new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", new JSONArray().put("query"));
        JSONObject function = new JSONObject()
                .put("name", "search_pathnames_only")
                .put("description", "Search paths")
                .put("parameters", parameters);

        ContextBuilder builder = new ContextBuilder("test", new ArrayList<>(), new ToolManager());
        Method appendDefinition = ContextBuilder.class.getDeclaredMethod(
                "appendXmlFunctionDefinitionUnbounded",
                StringBuilder.class, JSONObject.class, int.class);
        appendDefinition.setAccessible(true);
        StringBuilder prompt = new StringBuilder();
        appendDefinition.invoke(builder, prompt, function, 1);
        String value = prompt.toString();

        assertTrue(value.contains("<query>ACTUAL_VALUE</query>"));
        assertFalse(value.contains("<page_number>ACTUAL_VALUE</page_number>"));
        assertTrue(value.contains("page_number (integer, optional)"));
    }

    @Test
    public void openAiCompatibleReceivesReferenceFileAsPlainTextNotFileBlock() throws Exception {
        File referenceFile = File.createTempFile("chat-reference-", ".txt");
        try {
            Files.write(referenceFile.toPath(),
                    "REFERENCE_FILE_BODY".getBytes(StandardCharsets.UTF_8));
            ChatMessage user = new ChatMessage("Use this reference", ChatMessage.TYPE_USER, 1L);
            user.setStagingSelections(Arrays.asList(ChatReference.file(
                    "reference.txt", referenceFile.getAbsolutePath())));

            ContextBuilder builder = new ContextBuilder(
                    "test", new ArrayList<>(Arrays.asList(user)), new ToolManager())
                    .setIncludeNativeReferences(false);
            Method toSimpleMessages = ContextBuilder.class.getDeclaredMethod("toSimpleMessages");
            toSimpleMessages.setAccessible(true);
            List<?> simpleMessages = (List<?>) toSimpleMessages.invoke(builder);
            Method buildOpenAi = ContextBuilder.class.getDeclaredMethod(
                    "buildOpenAiMessages", List.class, String.class);
            buildOpenAi.setAccessible(true);

            JSONArray result = (JSONArray) buildOpenAi.invoke(
                    builder, simpleMessages, "openai_compatible");
            Object content = result.getJSONObject(0).get("content");

            assertTrue(content instanceof String);
            assertTrue(((String) content).contains("REFERENCE_FILE_BODY"));
            assertFalse(result.toString().contains("\"type\":\"file\""));
            assertTrue(ChatReferenceManager.supportsNativeOpenAiFileBlocks("openai_compatible"));
            assertTrue(ChatReferenceManager.supportsNativeOpenAiFileBlocks("litellm"));
            assertTrue(ChatReferenceManager.supportsNativeOpenAiFileBlocks("openai"));
        } finally {
            Files.deleteIfExists(referenceFile.toPath());
        }
    }

    @Test
    public void onlyLatestUserTurnKeepsNativeReferenceObjects() throws Exception {
        ChatMessage older = new ChatMessage("older", ChatMessage.TYPE_USER, 1L);
        older.setStagingSelections(Arrays.asList(
                ChatReference.file("old.txt", "/project/old.txt")));
        ChatMessage latest = new ChatMessage("latest", ChatMessage.TYPE_USER, 2L);
        latest.setStagingSelections(Arrays.asList(
                ChatReference.file("latest.txt", "/project/latest.txt")));

        ContextBuilder builder = new ContextBuilder(
                "test", new ArrayList<>(Arrays.asList(older, latest)), new ToolManager());
        Method toSimpleMessages = ContextBuilder.class.getDeclaredMethod("toSimpleMessages");
        toSimpleMessages.setAccessible(true);
        List<?> simpleMessages = (List<?>) toSimpleMessages.invoke(builder);
        java.lang.reflect.Field references = simpleMessages.get(0).getClass()
                .getDeclaredField("references");
        references.setAccessible(true);

        assertEquals(0, ((List<?>) references.get(simpleMessages.get(0))).size());
        assertEquals(1, ((List<?>) references.get(simpleMessages.get(1))).size());
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
