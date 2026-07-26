package pro.sketchware.activities.chat.toolcall;

public interface ToolCallParser {
    String protocol();

    boolean recognizes(ToolCallDetector.Response response);

    ToolCallDetector.DetectionResult parse(ToolCallDetector.Response response);
}
