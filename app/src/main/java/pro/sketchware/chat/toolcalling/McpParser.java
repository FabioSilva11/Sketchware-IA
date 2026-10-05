package pro.sketchware.chat.toolcalling;

import java.util.ArrayList;
import java.util.List;

public final class McpParser implements ToolCallParser {
    @Override
    public String protocol() {
        return "mcp";
    }

    @Override
    public boolean recognizes(ToolCallResponse response) {
        return !extract(response).calls.isEmpty();
    }

    @Override
    public ToolCallParseResult parse(ToolCallResponse response) {
        ParsedText content = parseText(response.getContent());
        ParsedText reasoning = parseText(response.getReasoning());
        List<ToolCall> calls = new ArrayList<>(content.calls);
        calls.addAll(reasoning.calls);
        return new ToolCallParseResult(
                protocol(),
                calls,
                content.cleaned,
                reasoning.cleaned
        );
    }

    private ParsedResponse extract(ToolCallResponse response) {
        ParsedText content = parseText(response == null ? "" : response.getContent());
        ParsedText reasoning = parseText(response == null ? "" : response.getReasoning());
        List<ToolCall> calls = new ArrayList<>(content.calls);
        calls.addAll(reasoning.calls);
        return new ParsedResponse(calls);
    }

    private ParsedText parseText(String text) {
        List<ToolCall> calls = new ArrayList<>();
        List<JsonBlockScanner.Block> matched = new ArrayList<>();
        for (JsonBlockScanner.Block block : JsonBlockScanner.scan(text)) {
            List<ToolCall> blockCalls = JsonToolSupport.extractMcp(block.value);
            if (!blockCalls.isEmpty()) {
                calls.addAll(blockCalls);
                matched.add(block);
            }
        }
        return new ParsedText(calls, JsonBlockScanner.remove(text, matched));
    }

    private static final class ParsedResponse {
        final List<ToolCall> calls;

        ParsedResponse(List<ToolCall> calls) {
            this.calls = calls;
        }
    }

    private static final class ParsedText {
        final List<ToolCall> calls;
        final String cleaned;

        ParsedText(List<ToolCall> calls, String cleaned) {
            this.calls = calls;
            this.cleaned = cleaned;
        }
    }
}
