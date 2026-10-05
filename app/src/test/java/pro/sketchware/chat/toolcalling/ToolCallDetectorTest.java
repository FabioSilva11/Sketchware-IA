package pro.sketchware.chat.toolcalling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

public class ToolCallDetectorTest {
    @Test
    public void nativeProtocolHasHighestPriority() throws Exception {
        ToolCall nativeCall = new ToolCall(
                "native_read",
                new JSONObject().put("path", "native.java").toString(),
                "native-1");
        ToolCallResponse response = new ToolCallResponse(
                "<function name=\"xml_read\"><path>xml.java</path></function>",
                "",
                Collections.singletonList(nativeCall));

        ToolCallParseResult result = detector().detect(response);

        assertEquals("native", result.getProtocol());
        assertEquals(1, result.getToolCalls().size());
        assertEquals("native_read", result.getToolCalls().get(0).getName());
        assertEquals("", result.getRemainingContent());
    }

    @Test
    public void mcpIsDetectedBeforeOtherTextProtocols() {
        String response = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"read\",\"arguments\":{\"path\":\"mcp.java\"}}}"
                + "\n<function name=\"other\"><path>xml.java</path></function>";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals("mcp", result.getProtocol());
        assertEquals("read", result.getToolCalls().get(0).getName());
        assertEquals("mcp.java", arguments(result).optString("path"));
        assertEquals("", result.getRemainingContent());
    }

    @Test
    public void parsesLegacyToolCallParameterSyntax() {
        String response = "Antes\n<tool_call>\n"
                + "<function>read</function>\n"
                + "<parameter=path>/storage/emulated/0/test.java</parameter>\n"
                + "</tool_call>\nDepois";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals("xml", result.getProtocol());
        assertEquals("read", result.getToolCalls().get(0).getName());
        assertEquals(
                "/storage/emulated/0/test.java",
                arguments(result).optString("path"));
        assertEquals("Antes\n\nDepois", result.getRemainingContent());
    }

    @Test
    public void parsesToolArgumentsWrapper() {
        String response = "<tool_call>\n"
                + "  <tool>read</tool>\n"
                + "  <arguments><path>arquivo.java</path></arguments>\n"
                + "</tool_call>";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals("read", result.getToolCalls().get(0).getName());
        assertEquals("arquivo.java", arguments(result).optString("path"));
        assertEquals("", result.getRemainingContent());
    }

    @Test
    public void parsesFunctionAttributesAndCdata() {
        String response = "<function name=\"rewrite_file\" id=\"call-9\" mode=\"safe\">"
                + "<path>arquivo.java</path>"
                + "<content><![CDATA[if (a < b && b > 0) {}]]></content>"
                + "</function>";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals("call-9", result.getToolCalls().get(0).getId());
        assertEquals("safe", arguments(result).optString("mode"));
        assertEquals("arquivo.java", arguments(result).optString("path"));
        assertEquals("if (a < b && b > 0) {}", arguments(result).optString("content"));
    }

    @Test
    public void preservesLegacyToolNameAsXmlElementProtocol() {
        ToolCallResponse response = new ToolCallResponse(
                "Antes <read_file><uri>src/Main.java</uri></read_file> Depois",
                "",
                Collections.emptyList(),
                Collections.singletonList("read_file"));

        ToolCallParseResult result = detector().detect(response);

        assertEquals("xml", result.getProtocol());
        assertEquals("read_file", result.getToolCalls().get(0).getName());
        assertEquals("src/Main.java", arguments(result).optString("uri"));
        assertEquals("Antes  Depois", result.getRemainingContent());
    }

    @Test
    public void extractsMultipleXmlCallsAndPreservesSurroundingText() {
        String response = "Vou analisar primeiro.\n\n"
                + "<function name=\"read\"><path>a.java</path></function>\n"
                + "<tool_call><tool>read</tool><arguments><path>b.java</path></arguments></tool_call>\n"
                + "<tool_call><function>read</function><parameter=path>c.java</parameter></tool_call>\n\n"
                + "Depois disso continuarei.";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals("xml", result.getProtocol());
        assertEquals(3, result.getToolCalls().size());
        assertEquals("a.java", arguments(result, 0).optString("path"));
        assertEquals("b.java", arguments(result, 1).optString("path"));
        assertEquals("c.java", arguments(result, 2).optString("path"));
        assertTrue(result.getRemainingContent().startsWith("Vou analisar primeiro."));
        assertTrue(result.getRemainingContent().endsWith("Depois disso continuarei."));
    }

    @Test
    public void recoversPartiallyMalformedXmlWithoutConsumingTrailingText() {
        String response = "Início\n"
                + "<tool_call><tool>read</tool><arguments><path>x.java</path></arguments>\n"
                + "Texto preservado";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals(1, result.getToolCalls().size());
        assertEquals("x.java", arguments(result).optString("path"));
        assertEquals("Início\n\nTexto preservado", result.getRemainingContent());
    }

    @Test
    public void invalidXmlNeverThrowsOrRemovesCommonText() {
        String response = "Use <tag comum sem fechamento e continue normalmente.";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertFalse(result.hasToolCalls());
        assertEquals(response, result.getRemainingContent());
    }

    @Test
    public void parsesMultipleJsonCallsInsideRegularText() {
        String response = "Preparando.\n"
                + "{\"tool\":\"read\",\"arguments\":{\"path\":\"a.java\"}}\n"
                + "{\"function\":{\"name\":\"read\",\"arguments\":{\"path\":\"b.java\"}}}\n"
                + "Concluído.";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals("json", result.getProtocol());
        assertEquals(2, result.getToolCalls().size());
        assertEquals("a.java", arguments(result, 0).optString("path"));
        assertEquals("b.java", arguments(result, 1).optString("path"));
        assertTrue(result.getRemainingContent().contains("Preparando."));
        assertTrue(result.getRemainingContent().contains("Concluído."));
    }

    @Test
    public void xmlIsDetectedBeforeJson() {
        String response = "{\"tool\":\"json_read\",\"arguments\":{\"path\":\"json.java\"}}\n"
                + "<function name=\"xml_read\"><path>xml.java</path></function>";

        ToolCallParseResult result = detector().detect(ToolCallResponse.text(response));

        assertEquals("xml", result.getProtocol());
        assertEquals("xml_read", result.getToolCalls().get(0).getName());
        assertEquals("", result.getRemainingContent());
    }

    @Test
    public void parsesDeepSeekDsmlWithMultipleInvocationsAndCleansProtocolText() {
        String response = "Antes\n"
                + "<｜DSML｜tool_calls>"
                + "<｜DSML｜invoke name=\"read_file\">"
                + "<｜DSML｜parameter name=\"uri\" string=\"true\">/tmp/a.html</｜DSML｜parameter>"
                + "</｜DSML｜invoke>"
                + "<｜DSML｜invoke name=\"search_files\">"
                + "<｜DSML｜parameter name=\"limit\" string=\"false\">10</｜DSML｜parameter>"
                + "</｜DSML｜invoke>"
                + "</｜DSML｜tool_calls>\nDepois";
        ToolCallResponse toolResponse = new ToolCallResponse(
                response, "", Collections.emptyList(),
                java.util.Arrays.asList("read_file", "search_files"));

        ToolCallParseResult result = detector().detect(toolResponse);

        assertEquals("dsml", result.getProtocol());
        assertEquals(2, result.getToolCalls().size());
        assertEquals("/tmp/a.html", arguments(result, 0).optString("uri"));
        assertEquals(10, arguments(result, 1).optInt("limit"));
        assertEquals("Antes\n\nDepois", result.getRemainingContent());
    }

    @Test
    public void nativeCallsRemainAuthoritativeWhileDsmlIsRemovedFromChat() {
        ToolCall nativeCall = new ToolCall("read_file", "{\"uri\":\"a.html\"}", "n1");
        String dsml = "<｜DSML｜tool_calls><｜DSML｜invoke name=\"read_file\">"
                + "<｜DSML｜parameter name=\"uri\" string=\"true\">a.html</｜DSML｜parameter>"
                + "</｜DSML｜invoke></｜DSML｜tool_calls>";
        ToolCallResponse response = new ToolCallResponse(
                dsml, "", Collections.singletonList(nativeCall),
                Collections.singletonList("read_file"));

        ToolCallParseResult result = detector().detect(response);

        assertEquals("native", result.getProtocol());
        assertEquals(1, result.getToolCalls().size());
        assertEquals("", result.getRemainingContent());
    }

    @Test
    public void recoversExactRepeatedNativeToolName() {
        ToolCallResponse response = new ToolCallResponse(
                "", "",
                Collections.singletonList(new ToolCall(
                        "read_fileread_fileread_file", "{\"uri\":\"a.html\"}", "n1")),
                Collections.singletonList("read_file"));

        ToolCallParseResult result = detector().detect(response);

        assertEquals("native", result.getProtocol());
        assertEquals("read_file", result.getToolCalls().get(0).getName());
    }

    @Test
    public void rejectsInvalidNativeArgumentsInsteadOfReplacingThemWithEmptyObject() {
        ToolCallResponse response = new ToolCallResponse(
                "texto preservado", "",
                Collections.singletonList(new ToolCall("read_file", "{invalid", "n1")),
                Collections.singletonList("read_file"));

        ToolCallParseResult result = detector().detect(response);

        assertFalse(result.hasToolCalls());
        assertEquals("texto preservado", result.getRemainingContent());
    }

    @Test
    public void futureParserCanBeRegisteredWithoutChangingDetector() {
        DefaultToolCallDetector detector = detector();
        detector.registerParser(new ToolCallParser() {
            @Override
            public String protocol() {
                return "future";
            }

            @Override
            public boolean recognizes(ToolCallResponse response) {
                return response.getContent().contains("FUTURE_CALL");
            }

            @Override
            public ToolCallParseResult parse(ToolCallResponse response) {
                return new ToolCallParseResult(
                        protocol(),
                        Collections.singletonList(new ToolCall("future_tool", "{}", "future-1")),
                        response.getContent().replace("FUTURE_CALL", "").trim(),
                        response.getReasoning());
            }
        });

        ToolCallParseResult result = detector.detect(
                ToolCallResponse.text("Antes FUTURE_CALL Depois"));

        assertEquals("future", result.getProtocol());
        assertEquals("future_tool", result.getToolCalls().get(0).getName());
        assertEquals("Antes  Depois", result.getRemainingContent());
    }

    private DefaultToolCallDetector detector() {
        return new DefaultToolCallDetector();
    }

    private JSONObject arguments(ToolCallParseResult result) {
        return arguments(result, 0);
    }

    private JSONObject arguments(ToolCallParseResult result, int index) {
        try {
            return new JSONObject(result.getToolCalls().get(index).getArguments());
        } catch (Exception error) {
            throw new AssertionError("Invalid tool arguments JSON", error);
        }
    }
}
