package pro.sketchware.chat.toolcalling;

public interface ToolCallDetector {
    ToolCallParseResult detect(ToolCallResponse response);

    void registerParser(ToolCallParser parser);
}
