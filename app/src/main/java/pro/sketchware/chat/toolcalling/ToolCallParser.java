package pro.sketchware.chat.toolcalling;

public interface ToolCallParser {
    String protocol();

    boolean recognizes(ToolCallResponse response);

    ToolCallParseResult parse(ToolCallResponse response);
}
