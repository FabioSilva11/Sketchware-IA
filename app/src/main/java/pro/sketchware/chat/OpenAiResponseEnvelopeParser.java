package pro.sketchware.chat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.chat.toolcalling.ToolCall;

/**
 * Normalizes the common OpenAI-compatible response envelopes used by hosted
 * gateways: Chat Completions, Responses API and shallow wrapper objects.
 */
final class OpenAiResponseEnvelopeParser {
    static final class ParsedResponse {
        final String content;
        final String reasoning;
        final List<ToolCall> toolCalls;
        final String finishReason;
        final String blockReason;
        final String envelope;
        final boolean recognized;
        final String providerError;

        ParsedResponse(String content, String reasoning, List<ToolCall> toolCalls,
                       String finishReason, String blockReason, String envelope,
                       boolean recognized, String providerError) {
            this.content = content == null ? "" : content;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
            this.finishReason = finishReason == null ? "" : finishReason;
            this.blockReason = blockReason == null ? "" : blockReason;
            this.envelope = envelope == null ? "" : envelope;
            this.recognized = recognized;
            this.providerError = providerError == null ? "" : providerError;
        }
    }

    private OpenAiResponseEnvelopeParser() {
    }

    static ParsedResponse parse(JSONObject root) {
        return parse(root, 0);
    }

    private static ParsedResponse parse(JSONObject root, int depth) {
        if (root == null) {
            return empty(false, "unknown");
        }

        String providerError = readProviderError(root);
        JSONArray choices = root.optJSONArray("choices");
        if (choices != null) {
            JSONObject first = choices.length() > 0 ? choices.optJSONObject(0) : null;
            JSONObject message = first == null ? null : first.optJSONObject("message");
            if (message == null && first != null) {
                message = first.optJSONObject("delta");
            }
            ParsedResponse parsed = parseMessage(message, "chat_completions");
            return new ParsedResponse(
                    parsed.content,
                    parsed.reasoning,
                    parsed.toolCalls,
                    first == null ? "" : scalar(first.opt("finish_reason")),
                    parsed.blockReason,
                    "chat_completions",
                    true,
                    providerError);
        }

        JSONObject message = root.optJSONObject("message");
        if (message != null) {
            ParsedResponse parsed = parseMessage(message, "message");
            return new ParsedResponse(parsed.content, parsed.reasoning, parsed.toolCalls,
                    scalar(root.opt("finish_reason")), parsed.blockReason,
                    "message", true, providerError);
        }

        JSONArray output = root.optJSONArray("output");
        if (output != null || root.has("output_text")) {
            return parseResponsesApi(root, output, providerError);
        }

        if (root.has("content") || root.has("reasoning_content") || root.has("tool_calls")
                || root.has("function_call")) {
            ParsedResponse parsed = parseMessage(root, "flat");
            return new ParsedResponse(parsed.content, parsed.reasoning, parsed.toolCalls,
                    scalar(root.opt("finish_reason")), parsed.blockReason,
                    "flat", true, providerError);
        }

        if (depth < 3) {
            for (String wrapper : new String[]{"data", "response", "result"}) {
                JSONObject nested = root.optJSONObject(wrapper);
                if (nested == null) {
                    continue;
                }
                ParsedResponse parsed = parse(nested, depth + 1);
                if (parsed.recognized) {
                    return new ParsedResponse(parsed.content, parsed.reasoning, parsed.toolCalls,
                            parsed.finishReason, parsed.blockReason,
                            wrapper + "." + parsed.envelope, true,
                            providerError.isEmpty() ? parsed.providerError : providerError);
                }
            }
        }

        return new ParsedResponse("", "", new ArrayList<>(), "", "",
                "unknown", false, providerError);
    }

    private static ParsedResponse parseMessage(JSONObject message, String envelope) {
        if (message == null) {
            return empty(true, envelope);
        }
        String content = readContent(message.opt("content"));
        String reasoning = firstNonEmpty(
                readContent(message.opt("reasoning_content")),
                readContent(message.opt("reasoning")),
                readContent(message.opt("thinking")));
        List<ToolCall> calls = new ArrayList<>();
        appendToolCalls(message.optJSONArray("tool_calls"), calls);
        appendFunctionCall(message.optJSONObject("function_call"), message.optString("id", ""), calls);
        String blockReason = firstNonEmpty(
                scalar(message.opt("blocked_reason")),
                scalar(message.opt("block_reason")),
                scalar(message.opt("refusal")));
        return new ParsedResponse(content, reasoning, calls, "", blockReason,
                envelope, true, "");
    }

    private static ParsedResponse parseResponsesApi(JSONObject root, JSONArray output,
                                                     String providerError) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        appendText(content, readContent(root.opt("output_text")));

        for (int i = 0; output != null && i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "");
            if ("function_call".equals(type)) {
                String id = firstNonEmpty(item.optString("call_id", ""), item.optString("id", ""));
                calls.add(new ToolCall(
                        item.optString("name", ""),
                        argumentsString(item.opt("arguments")),
                        id));
                continue;
            }
            if ("reasoning".equals(type)) {
                appendText(reasoning, readContent(item.opt("summary")));
                appendText(reasoning, readContent(item.opt("content")));
                continue;
            }
            if ("message".equals(type) || item.has("content")) {
                appendText(content, readContent(item.opt("content")));
            }
        }

        String status = firstNonEmpty(root.optString("status", ""), root.optString("finish_reason", ""));
        String blockReason = firstNonEmpty(
                scalar(root.opt("incomplete_details")),
                scalar(root.opt("refusal")));
        return new ParsedResponse(content.toString(), reasoning.toString(), calls,
                status, blockReason, "responses", true, providerError);
    }

    private static void appendToolCalls(JSONArray toolCalls, List<ToolCall> target) {
        for (int i = 0; toolCalls != null && i < toolCalls.length(); i++) {
            JSONObject call = toolCalls.optJSONObject(i);
            if (call == null) {
                continue;
            }
            JSONObject function = call.optJSONObject("function");
            if (function != null) {
                target.add(new ToolCall(
                        function.optString("name", ""),
                        argumentsString(function.opt("arguments")),
                        firstNonEmpty(call.optString("id", ""), call.optString("call_id", ""))));
            } else if ("function_call".equals(call.optString("type", ""))) {
                target.add(new ToolCall(
                        call.optString("name", ""),
                        argumentsString(call.opt("arguments")),
                        firstNonEmpty(call.optString("call_id", ""), call.optString("id", ""))));
            }
        }
    }

    private static void appendFunctionCall(JSONObject function, String id, List<ToolCall> target) {
        if (function == null) {
            return;
        }
        target.add(new ToolCall(
                function.optString("name", ""),
                argumentsString(function.opt("arguments")),
                id));
    }

    private static String readContent(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    String type = object.optString("type", "");
                    if ("output_text".equals(type) || "text".equals(type)
                            || "input_text".equals(type) || object.has("text")) {
                        appendText(builder, readContent(object.opt("text")));
                    } else if ("refusal".equals(type)) {
                        appendText(builder, readContent(object.opt("refusal")));
                    } else {
                        appendText(builder, readContent(object.opt("content")));
                    }
                } else {
                    appendText(builder, readContent(item));
                }
            }
            return builder.toString();
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has("text")) {
                return readContent(object.opt("text"));
            }
            if (object.has("content")) {
                return readContent(object.opt("content"));
            }
            if (object.has("message")) {
                return readContent(object.opt("message"));
            }
            return object.toString();
        }
        return String.valueOf(value);
    }

    private static String argumentsString(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "{}";
        }
        if (value instanceof JSONObject) {
            return value.toString();
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? "{}" : raw;
    }

    private static String readProviderError(JSONObject root) {
        Object error = root.opt("error");
        if (error == null || error == JSONObject.NULL) {
            return "";
        }
        if (error instanceof JSONObject) {
            JSONObject object = (JSONObject) error;
            return firstNonEmpty(
                    object.optString("message", ""),
                    object.optString("detail", ""),
                    object.toString());
        }
        return String.valueOf(error);
    }

    private static String scalar(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    private static void appendText(StringBuilder builder, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
        builder.append(value);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static ParsedResponse empty(boolean recognized, String envelope) {
        return new ParsedResponse("", "", new ArrayList<>(), "", "",
                envelope, recognized, "");
    }
}
