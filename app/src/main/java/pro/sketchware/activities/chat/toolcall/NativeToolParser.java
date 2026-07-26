package pro.sketchware.activities.chat.toolcall;

import java.util.List;

public final class NativeToolParser implements ToolCallParser {
    @Override
    public String protocol() {
        return "native";
    }

    @Override
    public boolean recognizes(ToolCallDetector.Response response) {
        return response != null && !ToolCallParsing.validCalls(response.getNativeToolCalls()).isEmpty();
    }

    @Override
    public ToolCallDetector.DetectionResult parse(ToolCallDetector.Response response) {
        List<ToolCall> calls = ToolCallParsing.validCalls(response.getNativeToolCalls());
        return new ToolCallDetector.DetectionResult(
                protocol(),
                response.getContent(),
                response.getReasoning(),
                calls
        );
    }
}
