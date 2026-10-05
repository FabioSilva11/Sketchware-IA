package pro.sketchware.chat.toolcalling;

import java.util.ArrayList;
import java.util.List;

public final class JsonToolParser implements ToolCallParser {
    @Override
    public String protocol() {
        return "json";
    }

    @Override
    public boolean recognizes(ToolCallResponse response) {
        if (response == null) {
            return false;
        }
        return hasCalls(response.getContent()) || hasCalls(response.getReasoning());
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

    private boolean hasCalls(String text) {
        for (JsonBlockScanner.Block block : JsonBlockScanner.scan(text)) {
            if (!JsonToolSupport.extractGeneric(block.value).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private ParsedText parseText(String text) {
        List<ToolCall> calls = new ArrayList<>();
        List<JsonBlockScanner.Block> matched = new ArrayList<>();
        for (JsonBlockScanner.Block block : JsonBlockScanner.scan(text)) {
            List<ToolCall> blockCalls = JsonToolSupport.extractGeneric(block.value);
            if (!blockCalls.isEmpty()) {
                calls.addAll(blockCalls);
                matched.add(block);
            }
        }
        return new ParsedText(calls, JsonBlockScanner.remove(text, matched));
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
