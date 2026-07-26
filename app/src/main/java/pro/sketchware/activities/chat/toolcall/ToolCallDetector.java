package pro.sketchware.activities.chat.toolcall;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface ToolCallDetector {
    DetectionResult detect(Response response);

    void registerParser(ToolCallParser parser);

    final class Response {
        private final String content;
        private final String reasoning;
        private final List<ToolCall> nativeToolCalls;
        private final JSONArray availableTools;

        public Response(String content, String reasoning, List<ToolCall> nativeToolCalls, JSONArray availableTools) {
            this.content = content == null ? "" : content;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.nativeToolCalls = nativeToolCalls == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(nativeToolCalls));
            this.availableTools = availableTools == null ? new JSONArray() : availableTools;
        }

        public String getContent() {
            return content;
        }

        public String getReasoning() {
            return reasoning;
        }

        public List<ToolCall> getNativeToolCalls() {
            return nativeToolCalls;
        }

        public JSONArray getAvailableTools() {
            return availableTools;
        }
    }

    final class DetectionResult {
        private final String protocol;
        private final String cleanedContent;
        private final String cleanedReasoning;
        private final List<ToolCall> toolCalls;

        public DetectionResult(String protocol, String cleanedContent, String cleanedReasoning, List<ToolCall> toolCalls) {
            this.protocol = protocol == null ? "" : protocol;
            this.cleanedContent = cleanedContent == null ? "" : cleanedContent;
            this.cleanedReasoning = cleanedReasoning == null ? "" : cleanedReasoning;
            this.toolCalls = toolCalls == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        }

        public static DetectionResult none(Response response) {
            return new DetectionResult(
                    "none",
                    response == null ? "" : response.getContent(),
                    response == null ? "" : response.getReasoning(),
                    Collections.emptyList()
            );
        }

        public String getProtocol() {
            return protocol;
        }

        public String getCleanedContent() {
            return cleanedContent;
        }

        public String getCleanedReasoning() {
            return cleanedReasoning;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }
}
