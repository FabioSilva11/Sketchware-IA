package pro.sketchware.activities.chat.toolcall;

import java.util.ArrayList;
import java.util.List;

public final class DefaultToolCallDetector implements ToolCallDetector {
    private final List<ToolCallParser> parsers = new ArrayList<>();

    public DefaultToolCallDetector() {
        registerParser(new NativeToolParser());
        registerParser(new McpParser());
        registerParser(new XmlToolParser());
        registerParser(new JsonToolParser());
    }

    @Override
    public DetectionResult detect(Response response) {
        if (response == null) {
            return DetectionResult.none(null);
        }
        for (ToolCallParser parser : parsers) {
            try {
                if (!parser.recognizes(response)) {
                    continue;
                }
                DetectionResult result = parser.parse(response);
                if (result != null && result.hasToolCalls()) {
                    return result;
                }
            } catch (Exception ignored) {
                // Tool call detection is best-effort. Invalid XML/JSON or a buggy
                // future parser must never break a normal assistant response.
            }
        }
        return DetectionResult.none(response);
    }

    @Override
    public void registerParser(ToolCallParser parser) {
        if (parser != null) {
            parsers.add(parser);
        }
    }
}
