package pro.sketchware.activities.chat.toolcall;

import java.util.ArrayList;
import java.util.List;

public final class McpParser implements ToolCallParser {
    @Override
    public String protocol() {
        return "mcp";
    }

    @Override
    public boolean recognizes(ToolCallDetector.Response response) {
        return response != null && !parseCandidates(response).calls.isEmpty();
    }

    @Override
    public ToolCallDetector.DetectionResult parse(ToolCallDetector.Response response) {
        Parsed parsed = parseCandidates(response);
        return new ToolCallDetector.DetectionResult(
                protocol(),
                ToolCallParsing.compactTextAfterRemoval(response.getContent(), parsed.ranges),
                response.getReasoning(),
                parsed.calls
        );
    }

    private Parsed parseCandidates(ToolCallDetector.Response response) {
        Parsed parsed = new Parsed();
        for (ToolCallParsing.JsonCandidate candidate : ToolCallParsing.findJsonCandidates(response.getContent())) {
            List<ToolCall> calls = ToolCallParsing.extractGenericJsonToolCalls(candidate.json, true);
            if (!calls.isEmpty()) {
                parsed.calls.addAll(calls);
                parsed.ranges.add(new ToolCallParsing.Range(candidate.start, candidate.end));
            }
        }
        return parsed;
    }

    private static final class Parsed {
        final List<ToolCall> calls = new ArrayList<>();
        final List<ToolCallParsing.Range> ranges = new ArrayList<>();
    }
}
