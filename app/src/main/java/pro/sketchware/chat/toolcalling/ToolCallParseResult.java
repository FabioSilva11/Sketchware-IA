package pro.sketchware.chat.toolcalling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ToolCallParseResult {
    private final String protocol;
    private final List<ToolCall> toolCalls;
    private final String remainingContent;
    private final String remainingReasoning;

    public ToolCallParseResult(String protocol, List<ToolCall> toolCalls,
                               String remainingContent, String remainingReasoning) {
        this.protocol = protocol == null ? "" : protocol;
        this.toolCalls = toolCalls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.remainingContent = remainingContent == null ? "" : remainingContent;
        this.remainingReasoning = remainingReasoning == null ? "" : remainingReasoning;
    }

    public static ToolCallParseResult none(ToolCallResponse response) {
        return new ToolCallParseResult(
                "",
                Collections.emptyList(),
                response == null ? "" : response.getContent(),
                response == null ? "" : response.getReasoning()
        );
    }

    public String getProtocol() {
        return protocol;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getRemainingContent() {
        return remainingContent;
    }

    public String getRemainingReasoning() {
        return remainingReasoning;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
