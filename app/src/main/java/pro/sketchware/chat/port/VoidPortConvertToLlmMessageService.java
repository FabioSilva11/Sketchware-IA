package pro.sketchware.chat.port;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.chat.ChatMessage;

/**
 * Android port of browser/convertToLLMMessageService.ts primitives.
 */
public final class VoidPortConvertToLlmMessageService {
    /** Marker emitted by older builds; retained only for history cleanup. */
    public static final String EMPTY_MESSAGE = "(empty message)";

    private VoidPortConvertToLlmMessageService() {
    }

    public static final class SimpleMessage {
        public final String role;
        public final String content;
        public final String reasoning;
        public final String toolName;
        public final String toolArgs;
        public final String toolResult;
        public final String toolId;

        SimpleMessage(String role,
                      String content,
                      String reasoning,
                      String toolName,
                      String toolArgs,
                      String toolResult,
                      String toolId) {
            this.role = safe(role);
            this.content = safe(content);
            this.reasoning = safe(reasoning);
            this.toolName = safe(toolName);
            this.toolArgs = safe(toolArgs);
            this.toolResult = safe(toolResult);
            this.toolId = safe(toolId);
        }
    }

    public static List<SimpleMessage> toSimpleMessages(List<ChatMessage> messages) {
        List<SimpleMessage> simpleMessages = new ArrayList<>();
        if (messages == null) {
            return simpleMessages;
        }
        for (ChatMessage message : messages) {
            if (message == null
                    || message.isCheckpoint()
                    || message.isAwaitingUser()
                    || message.isInterruptedStreamingTool()) {
                continue;
            }
            if (message.isUser()) {
                simpleMessages.add(new SimpleMessage(
                        "user",
                        safe(message.getLlmContent()),
                        "",
                        "",
                        "",
                        "",
                        ""
                ));
            } else if (message.isBot()) {
                simpleMessages.add(new SimpleMessage(
                        "assistant",
                        safe(message.getDisplayContent()),
                        safe(message.getReasoning()),
                        "",
                        "",
                        "",
                        ""
                ));
            } else if (message.isTool()) {
                simpleMessages.add(new SimpleMessage(
                        "tool",
                        safe(message.getToolResult()),
                        "",
                        safe(message.getToolName()),
                        safe(message.getToolArgs()),
                        safe(message.getToolResult()),
                        stableToolId(message)
                ));
            }
        }
        return simpleMessages;
    }

    public static String nonEmptyText(String value) {
        String safeValue = safe(value).trim();
        return safeValue;
    }

    public static String nonEmptyToolResult(String value) {
        String safeValue = safe(value).trim();
        return safeValue.isEmpty() ? "{\"result\":null}" : safeValue;
    }

    public static boolean isProtocolEmptyMessage(String value) {
        return EMPTY_MESSAGE.equalsIgnoreCase(safe(value).trim());
    }

    public static boolean isProtocolEmptyMessagePrefix(String value) {
        String normalized = safe(value).trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.isEmpty()
                && EMPTY_MESSAGE.toLowerCase(java.util.Locale.ROOT).startsWith(normalized);
    }

    public static String trimToApproxTokens(String text, int maxTokens) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        int maxChars = Math.max(0, maxTokens * 4);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 32)).trim()
                + "\n...[trimmed for token budget]";
    }

    public static String buildAssistantContent(String content, String reasoning, boolean includeReasoning) {
        String safeContent = safe(content).trim();
        String safeReasoning = safe(reasoning).trim();
        if (includeReasoning && !safeReasoning.isEmpty()) {
            if (safeContent.isEmpty()) {
                return "<reasoning>\n" + safeReasoning + "\n</reasoning>";
            }
            return "<reasoning>\n" + safeReasoning + "\n</reasoning>\n\n" + safeContent;
        }
        if (!safeContent.isEmpty()) {
            return safeContent;
        }
        if (!safeReasoning.isEmpty()) {
            return safeReasoning;
        }
        return "";
    }

    public static String buildXmlToolResult(String toolName, String toolResult) {
        String safeName = safe(toolName).trim();
        if (safeName.isEmpty()) {
            safeName = "tool";
        }
        return "<" + safeName + "_result>\n"
                + nonEmptyToolResult(toolResult)
                + "\n</" + safeName + "_result>";
    }

    /**
     * Bug fix: both fallbacks here used to be timestamp-based ({@code
     * System.currentTimeMillis()} and {@code message.getTimestamp()}), which
     * only has millisecond resolution. This method runs once per TYPE_TOOL
     * message on every re-serialization of the whole conversation history
     * (see the caller above), so any two sibling tool messages created in the
     * same millisecond — e.g. two bubbles from a single turn's tool calls,
     * created back-to-back in the same loop — got the IDENTICAL id whenever
     * {@code toolId} was empty, and (being derived from the message's own
     * stored timestamp) kept colliding on every subsequent turn, not just
     * once. A duplicate id across two tool_call/tool-result entries in the
     * same request makes providers misattribute which result answers which
     * call, so the model could see one call's cached success attached to a
     * different call that never actually ran. Use a real unique id instead.
     */
    public static String stableToolId(ChatMessage message) {
        if (message == null) {
            return "call_" + java.util.UUID.randomUUID();
        }
        String toolId = safe(message.getToolId()).trim();
        if (!toolId.isEmpty()) {
            return toolId;
        }
        // Genuinely stable: generate once and persist it onto the message,
        // so every later re-serialization of this same message (every
        // subsequent turn resends the whole history) returns the SAME id,
        // instead of a fresh random one each call.
        String generated = "call_" + java.util.UUID.randomUUID();
        message.setToolId(generated);
        return generated;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
