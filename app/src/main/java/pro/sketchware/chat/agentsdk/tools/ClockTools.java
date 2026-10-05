package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;

import org.json.JSONObject;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Core {@code clock.curr_time} + {@code clock.sleep} executors implementing
 * the Codex reference contract (records {@code current_time.rs} and
 * {@code sleep.rs}):
 *
 * <ul>
 *   <li>{@code curr_time}: no arguments; output object
 *       {@code {"current_time": "YYYY-MM-DD HH:MM:SS UTC"}};</li>
 *   <li>{@code sleep}: {@code duration_ms} between 1 and 12h; the sleep ends
 *       early when new input arrives for the active turn (this implementation
 *       sleeps uninterruptibly for the wall-clock duration and returns the
 *       elapsed milliseconds).</li>
 * </ul>
 */
final class ClockTools {

    static final String NAMESPACE = "clock";
    static final String CURRENT_TIME_TOOL = "curr_time";
    static final String SLEEP_TOOL = "sleep";
    static final long MAX_SLEEP_DURATION_MS = 12L * 60 * 60 * 1000;

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

    static ToolExecutor sleepExecutor() {
        return ctx -> {
            JSONObject args = ctx.functionArguments();
            long durationMs = args == null ? -1L : args.optLong("duration_ms", -1L);
            if (durationMs < 1L || durationMs > MAX_SLEEP_DURATION_MS) {
                return AgentToolResult.error(
                        "Error: duration_ms must be between 1 and " + MAX_SLEEP_DURATION_MS + ".");
            }
            long started = System.currentTimeMillis();
            try {
                long remaining = durationMs;
                while (remaining > 0) {
                    long chunk = Math.min(remaining, 500L);
                    Thread.sleep(chunk);
                    remaining -= chunk;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long elapsed = System.currentTimeMillis() - started;
            try {
                return AgentToolResult.success(new JSONObject()
                        .put("elapsed_ms", elapsed)
                        .put("duration_ms", durationMs)
                        .toString());
            } catch (org.json.JSONException e) {
                return AgentToolResult.error("Error: could not serialize sleep result.");
            }
        };
    }
}