package pro.sketchware.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.toolcalling.ToolCall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression for the critical production bug: provider-structured (native)
 * tool calls were collected into ToolCallParseResult but NEVER emitted via
 * StreamListener.onToolCall. The AgentRuntime gateway collects tool calls ONLY
 * through onToolCall, so in NativeToolCallsOnly mode tools never executed.
 *
 * <p>This test drives the exact emission helper the native branch now uses
 * ({@link AiProviderService#emitToolCalls}).</p>
 */
public class AiProviderServiceNativeEmissionTest {

    private final List<String> emitted = new ArrayList<>();

    /** Captures (name, arguments, id) as "name|arguments|id". */
    private final AiProviderService.StreamListener recordingListener = new AiProviderService.StreamListener() {
        @Override
        public void onContent(String delta) {
        }

        @Override
        public void onReasoning(String delta) {
        }

        @Override
        public void onToolCall(String name, String arguments, String id) {
            emitted.add(name + "|" + arguments + "|" + id);
        }

        @Override
        public void onFinalMessage(String fullContent, String fullReasoning, String finishReason) {
        }

        @Override
        public void onDebug(String message) {
        }

        @Override
        public void onError(String message, Throwable t) {
        }
    };

    @Test
    public void nativeCallsAreEmittedThroughOnToolCall() {
        List<ToolCall> nativeCalls = new ArrayList<>();
        nativeCalls.add(new ToolCall("exec_command", "{\"cmd\":\"ls\"}", "call_1"));
        nativeCalls.add(new ToolCall("apply_patch", "{\"patch\":\"\"}", "call_2"));

        AiProviderService.emitToolCalls(nativeCalls, recordingListener);

        assertEquals("every native call is delivered via onToolCall", 2, emitted.size());
        assertTrue(emitted.get(0).startsWith("exec_command|"));
        assertTrue(emitted.get(0).endsWith("|call_1"));
        assertTrue(emitted.get(1).startsWith("apply_patch|"));
        assertTrue(emitted.get(1).endsWith("|call_2"));
    }

    @Test
    public void invalidAndNullCallsAreSkippedNotEmitted() {
        List<ToolCall> nativeCalls = new ArrayList<>();
        nativeCalls.add(null);
        nativeCalls.add(new ToolCall("", "{}", "call_empty_name"));
        nativeCalls.add(new ToolCall("bad_args", "{invalid json", "call_bad_args"));
        nativeCalls.add(new ToolCall("ok", "{}", "call_ok"));

        AiProviderService.emitToolCalls(nativeCalls, recordingListener);

        assertEquals("only the valid call is emitted", 1, emitted.size());
        assertTrue(emitted.get(0).startsWith("ok|"));
    }

    @Test
    public void nullListOrListenerIsSafe() {
        AiProviderService.emitToolCalls(null, recordingListener);
        AiProviderService.emitToolCalls(new ArrayList<ToolCall>(), null);
        assertEquals(0, emitted.size());
    }
}