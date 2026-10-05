package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class OpenAiResponseEnvelopeParserTest {
    @Test
    public void parsesChatCompletionsToolCall() throws Exception {
        JSONObject function = new JSONObject()
                .put("name", "read_file")
                .put("arguments", "{\"uri\":\"A.java\"}");
        JSONObject message = new JSONObject()
                .put("content", "checking")
                .put("tool_calls", new JSONArray().put(
                        new JSONObject().put("id", "call_1").put("function", function)));
        JSONObject root = new JSONObject().put("choices", new JSONArray().put(
                new JSONObject().put("finish_reason", "tool_calls").put("message", message)));

        OpenAiResponseEnvelopeParser.ParsedResponse parsed =
                OpenAiResponseEnvelopeParser.parse(root);

        assertTrue(parsed.recognized);
        assertEquals("chat_completions", parsed.envelope);
        assertEquals("checking", parsed.content);
        assertEquals("read_file", parsed.toolCalls.get(0).getName());
    }

    @Test
    public void parsesResponsesApiTextReasoningAndFunctionCall() throws Exception {
        JSONArray output = new JSONArray()
                .put(new JSONObject().put("type", "reasoning")
                        .put("summary", new JSONArray().put(
                                new JSONObject().put("type", "summary_text").put("text", "plan"))))
                .put(new JSONObject().put("type", "message")
                        .put("content", new JSONArray().put(
                                new JSONObject().put("type", "output_text").put("text", "done"))))
                .put(new JSONObject().put("type", "function_call")
                        .put("call_id", "call_2")
                        .put("name", "read_file")
                        .put("arguments", "{\"uri\":\"B.java\"}"));
        JSONObject root = new JSONObject().put("status", "completed").put("output", output);

        OpenAiResponseEnvelopeParser.ParsedResponse parsed =
                OpenAiResponseEnvelopeParser.parse(root);

        assertEquals("responses", parsed.envelope);
        assertEquals("done", parsed.content);
        assertEquals("plan", parsed.reasoning);
        assertEquals(1, parsed.toolCalls.size());
    }

    @Test
    public void parsesShallowGatewayWrapper() throws Exception {
        JSONObject root = new JSONObject().put("data", new JSONObject().put("response",
                new JSONObject().put("output_text", "wrapped")));
        OpenAiResponseEnvelopeParser.ParsedResponse parsed =
                OpenAiResponseEnvelopeParser.parse(root);
        assertTrue(parsed.recognized);
        assertEquals("data.response.responses", parsed.envelope);
        assertEquals("wrapped", parsed.content);
    }

    @Test
    public void unknownEnvelopeIsNotReportedAsEmptyKnownResponse() throws Exception {
        OpenAiResponseEnvelopeParser.ParsedResponse parsed =
                OpenAiResponseEnvelopeParser.parse(new JSONObject().put("wallet", 10));
        assertFalse(parsed.recognized);
        assertEquals("unknown", parsed.envelope);
    }
}
