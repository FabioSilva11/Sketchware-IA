package pro.sketchware.chat.toolcalling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ToolCallResponse {
    private final String content;
    private final String reasoning;
    private final List<ToolCall> nativeToolCalls;
    private final List<String> availableToolNames;

    public ToolCallResponse(String content, String reasoning, List<ToolCall> nativeToolCalls) {
        this(content, reasoning, nativeToolCalls, Collections.emptyList());
    }

    public ToolCallResponse(String content, String reasoning, List<ToolCall> nativeToolCalls,
                            List<String> availableToolNames) {
        this.content = content == null ? "" : content;
        this.reasoning = reasoning == null ? "" : reasoning;
        this.nativeToolCalls = nativeToolCalls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(nativeToolCalls));
        this.availableToolNames = availableToolNames == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(availableToolNames));
    }

    public static ToolCallResponse text(String content) {
        return new ToolCallResponse(content, "", Collections.emptyList());
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

    public List<String> getAvailableToolNames() {
        return availableToolNames;
    }
}
