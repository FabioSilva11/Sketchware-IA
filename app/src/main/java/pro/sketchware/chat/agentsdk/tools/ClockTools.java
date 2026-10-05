package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;

import org.json.JSONObject;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Core {@code clock.curr_time} executor implementing the Codex reference contract ({@code current_time.rs}): no
 * arguments; output object {@code {"current_time": "YYYY-MM-DD HH:MM:SS UTC"}}. Codex's {@code clock.sleep} is not
 * offered: without a shell the chat has nothing to wait for, and nothing could cut a sleep short.
 */
final class ClockTools {

    static final String NAMESPACE = "clock";
    static final String CURRENT_TIME_TOOL = "curr_time";

    private ClockTools() {
    }

    static ToolExecutor currentTimeExecutor() {
        return ctx -> {
            try {
                String now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneOffset.UTC)
                        .format(ZonedDateTime.now(ZoneOffset.UTC));
                return AgentToolResult.success(new JSONObject()
                        .put("current_time", now + " UTC")
                        .toString());
            } catch (Exception e) {
                return AgentToolResult.error("Error: could not read the current time.");
            }
        };
    }
}
