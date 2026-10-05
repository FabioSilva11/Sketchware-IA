package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContextBuilderProtocolTest {

    @Test
    public void prioritizedPromptKeepsTerminalStateAheadOfOptionalBulkContext() {
        String largeRules = "RULES " + repeated("important ", 800);
        String terminalState = "TERMINATION CONDITION: return the final answer now.";
        String optionalTree = "FILES " + repeated("path/to/file ", 800);

        String prompt = ContextBuilder.composePrioritizedSections(
                Arrays.asList("IDENTITY", largeRules, terminalState),
                Arrays.asList(optionalTree),
                220);

        assertTrue(prompt.contains("IDENTITY"));
        assertTrue(prompt.contains("RULES"));
        assertTrue(prompt.contains("TERMINATION CONDITION"));
        assertTrue(prompt.length() <= 220 * 4);
    }

    @Test
    public void nativeToolGuidanceAllowsSafeBatchesWhileXmlRemainsSingleCall() throws Exception {
        ContextBuilder builder = new ContextBuilder("1", new ArrayList<>());
        Method method = ContextBuilder.class.getDeclaredMethod(
                "buildVoidImportantDetails", String.class, ContextBuilder.ProviderFormat.class);
        method.setAccessible(true);

        String nativePrompt = (String) method.invoke(
                builder, "agent", ContextBuilder.ProviderFormat.OPENAI);
        String xmlPrompt = (String) method.invoke(
                builder, "agent", ContextBuilder.ProviderFormat.XML_FALLBACK);

        assertTrue(nativePrompt.contains("multiple tool calls"));
        assertFalse(nativePrompt.contains("exactly one tool call per response"));
        assertTrue(xmlPrompt.contains("exactly one XML tool call"));
    }

    @Test
    public void openAiToolTurnOmitsArtificialAssistantContentAndPairsId() throws Exception {
        ContextBuilder builder = builderWithToolTurn();
        List<?> simpleMessages = simpleMessages(builder);
        Method method = ContextBuilder.class.getDeclaredMethod(
                "buildOpenAiMessages", List.class, String.class);
        method.setAccessible(true);

        JSONArray messages = (JSONArray) method.invoke(builder, simpleMessages, "openai");
        JSONObject assistant = messages.getJSONObject(1);
        JSONObject tool = messages.getJSONObject(2);

        assertEquals("assistant", assistant.getString("role"));
        assertFalse(assistant.has("content"));
        assertEquals("call_1",
                assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id"));
        assertEquals("call_1", tool.getString("tool_call_id"));
        assertEquals("tool output", tool.getString("content"));
    }

    @Test
    public void deepSeekToolTurnPreservesReasoningContentForContinuation() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("inspect", true, 1L));
        ChatMessage assistant = new ChatMessage("", false, 2L);
        assistant.setReasoning("reasoning trace");
        messages.add(assistant);
        ChatMessage tool = new ChatMessage(
                "read_file", "{\"uri\":\"A.java\"}", 3L, "call_1");
        tool.setToolResult("tool output");
        messages.add(tool);
        ContextBuilder builder = new ContextBuilder("1", messages);
        Field currentModel = ContextBuilder.class.getDeclaredField("currentModelName");
        currentModel.setAccessible(true);
        currentModel.set(builder, "axion_managed/opencode-deepseek-v4-flash");

        Method method = ContextBuilder.class.getDeclaredMethod(
                "buildOpenAiMessages", List.class, String.class);
        method.setAccessible(true);
        JSONArray built = (JSONArray) method.invoke(builder, simpleMessages(builder), "axion_managed");
        JSONObject builtAssistant = built.getJSONObject(1);

        assertEquals("reasoning trace", builtAssistant.getString("reasoning_content"));
        assertTrue(builtAssistant.has("tool_calls"));
    }

    @Test
    public void deepSeekFinalAssistantMessageDoesNotReplayReasoningContent() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("question", true, 1L));
        ChatMessage assistant = new ChatMessage("answer", false, 2L);
        assistant.setReasoning("private reasoning");
        messages.add(assistant);
        ContextBuilder builder = new ContextBuilder("1", messages);
        Field currentModel = ContextBuilder.class.getDeclaredField("currentModelName");
        currentModel.setAccessible(true);
        currentModel.set(builder, "deepseek-v4-flash");

        Method method = ContextBuilder.class.getDeclaredMethod(
                "buildOpenAiMessages", List.class, String.class);
        method.setAccessible(true);
        JSONArray built = (JSONArray) method.invoke(builder, simpleMessages(builder), "axion_managed");

        assertFalse(built.getJSONObject(1).has("reasoning_content"));
    }

    @Test
    public void anthropicToolTurnContainsOnlyToolUseBeforeResult() throws Exception {
        ContextBuilder builder = builderWithToolTurn();
        List<?> simpleMessages = simpleMessages(builder);
        Method method = ContextBuilder.class.getDeclaredMethod(
                "buildAnthropicMessages", List.class);
        method.setAccessible(true);

        JSONArray messages = (JSONArray) method.invoke(builder, simpleMessages);
        JSONArray assistantContent = messages.getJSONObject(1).getJSONArray("content");
        JSONArray userContent = messages.getJSONObject(2).getJSONArray("content");

        assertEquals(1, assistantContent.length());
        assertEquals("tool_use", assistantContent.getJSONObject(0).getString("type"));
        assertEquals("call_1", assistantContent.getJSONObject(0).getString("id"));
        assertEquals("tool_result", userContent.getJSONObject(0).getString("type"));
        assertEquals("call_1", userContent.getJSONObject(0).getString("tool_use_id"));
    }

    @Test
    public void terminalPhaseEndsHistoryWithExplicitUserDirective() throws Exception {
        ContextBuilder builder = builderWithToolTurn().setFinalResponseOnly(true);
        Method method = ContextBuilder.class.getDeclaredMethod(
                "buildOpenAiMessages", List.class, String.class);
        method.setAccessible(true);

        JSONArray messages = (JSONArray) method.invoke(builder, simpleMessages(builder), "openai");
        JSONObject last = messages.getJSONObject(messages.length() - 1);

        assertEquals("user", last.getString("role"));
        assertTrue(last.getString("content").contains("no tools are available"));
        assertTrue(last.getString("content").contains("Do not emit a tool call"));
    }

    private static ContextBuilder builderWithToolTurn() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("inspect", true, 1L));
        ChatMessage tool = new ChatMessage(
                "read_file", "{\"uri\":\"A.java\"}", 2L, "call_1");
        tool.setToolResult("tool output");
        messages.add(tool);
        return new ContextBuilder("1", messages);
    }

    private static List<?> simpleMessages(ContextBuilder builder) throws Exception {
        Method method = ContextBuilder.class.getDeclaredMethod("toSimpleMessages");
        method.setAccessible(true);
        return (List<?>) method.invoke(builder);
    }

    private static String repeated(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
