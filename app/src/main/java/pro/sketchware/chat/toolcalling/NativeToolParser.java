package pro.sketchware.chat.toolcalling;

public final class NativeToolParser implements ToolCallParser {
    @Override
    public String protocol() {
        return "native";
    }

    @Override
    public boolean recognizes(ToolCallResponse response) {
        return response != null && !response.getNativeToolCalls().isEmpty();
    }

    @Override
    public ToolCallParseResult parse(ToolCallResponse response) {
        return new ToolCallParseResult(
                protocol(),
                response.getNativeToolCalls(),
                response.getContent(),
                response.getReasoning()
        );
    }
}
