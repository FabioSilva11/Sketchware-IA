package pro.sketchware.activities.chat.toolcall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ToolCallParsing {
    private ToolCallParsing() {
    }

    static List<ToolCall> validCalls(List<ToolCall> calls) {
        List<ToolCall> result = new ArrayList<>();
        if (calls == null) {
            return result;
        }
        for (ToolCall call : calls) {
            if (call != null && call.isValid()) {
                result.add(call);
            }
        }
        return result;
    }

    static String newId(String prefix) {
        String safePrefix = prefix == null || prefix.trim().isEmpty() ? "tool_call" : prefix.trim();
        return safePrefix + "_" + UUID.randomUUID();
    }

    static String compactTextAfterRemoval(String source, List<Range> ranges) {
        if (source == null || source.isEmpty() || ranges == null || ranges.isEmpty()) {
            return source == null ? "" : source;
        }
        List<Range> sorted = new ArrayList<>(ranges);
        sorted.sort((left, right) -> Integer.compare(left.start, right.start));
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        for (Range range : sorted) {
            int start = Math.max(0, Math.min(range.start, source.length()));
            int end = Math.max(start, Math.min(range.end, source.length()));
            if (start < cursor) {
                continue;
            }
            builder.append(source, cursor, start);
            cursor = end;
        }
        if (cursor < source.length()) {
            builder.append(source.substring(cursor));
        }
        return builder.toString().replaceAll("(?m)[ \t]+$", "").replaceAll("\n{3,}", "\n\n").trim();
    }

    static List<JsonCandidate> findJsonCandidates(String text) {
        List<JsonCandidate> candidates = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return candidates;
        }
        for (int i = 0; i < text.length(); i++) {
            char open = text.charAt(i);
            if (open != '{' && open != '[') {
                continue;
            }
            int end = findBalancedJsonEnd(text, i, open, open == '{' ? '}' : ']');
            if (end <= i) {
                continue;
            }
            String raw = text.substring(i, end + 1);
            try {
                Object json = open == '{' ? new JSONObject(raw) : new JSONArray(raw);
                candidates.add(new JsonCandidate(json, i, end + 1));
                i = end;
            } catch (Exception ignored) {
            }
        }
        return candidates;
    }

    static int findBalancedJsonEnd(String text, int start, char open, char close) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static List<ToolCall> extractGenericJsonToolCalls(Object json, boolean mcpOnly) {
        List<ToolCall> result = new ArrayList<>();
        collectGenericJsonToolCalls(json, mcpOnly, result);
        return result;
    }

    private static void collectGenericJsonToolCalls(Object value, boolean mcpOnly, List<ToolCall> result) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                collectGenericJsonToolCalls(array.opt(i), mcpOnly, result);
            }
            return;
        }
        if (!(value instanceof JSONObject)) {
            return;
        }
        JSONObject object = (JSONObject) value;
        ToolCall call = parseKnownJsonToolCall(object, mcpOnly);
        if (call != null && call.isValid()) {
            result.add(call);
            return;
        }

        JSONArray toolCalls = object.optJSONArray("tool_calls");
        if (toolCalls == null) {
            toolCalls = object.optJSONArray("toolCalls");
        }
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.length(); i++) {
                collectGenericJsonToolCalls(toolCalls.opt(i), mcpOnly, result);
            }
        }

        JSONArray content = object.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                collectGenericJsonToolCalls(content.opt(i), mcpOnly, result);
            }
        }

        JSONArray parts = object.optJSONArray("parts");
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                collectGenericJsonToolCalls(parts.opt(i), mcpOnly, result);
            }
        }
    }

    private static ToolCall parseKnownJsonToolCall(JSONObject object, boolean mcpOnly) {
        if (object == null) {
            return null;
        }

        JSONObject function = object.optJSONObject("function");
        if (function != null) {
            String name = function.optString("name", "").trim();
            Object args = function.has("arguments") ? function.opt("arguments") : function.opt("args");
            return acceptedCall(name, args, object.optString("id", ""), mcpOnly);
        }

        JSONObject functionCall = object.optJSONObject("functionCall");
        if (functionCall != null) {
            String name = functionCall.optString("name", "").trim();
            Object args = functionCall.has("args") ? functionCall.opt("args") : functionCall.opt("arguments");
            return acceptedCall(name, args, functionCall.optString("id", object.optString("id", "")), mcpOnly);
        }

        if ("tool_use".equalsIgnoreCase(object.optString("type", ""))) {
            String name = object.optString("name", "").trim();
            Object args = object.has("input") ? object.opt("input") : object.opt("arguments");
            return acceptedCall(name, args, object.optString("id", ""), mcpOnly);
        }

        if ("tools/call".equalsIgnoreCase(object.optString("method", ""))) {
            JSONObject params = object.optJSONObject("params");
            if (params == null) {
                params = object;
            }
            String name = params.optString("name", params.optString("tool", params.optString("tool_name", ""))).trim();
            String server = params.optString("server", params.optString("server_name", "")).trim();
            if (!server.isEmpty() && !name.startsWith("mcp_")) {
                name = mcpToolName(server, name);
            }
            Object args = params.has("arguments") ? params.opt("arguments") : params.opt("args");
            return acceptedCall(name, args, object.optString("id", ""), true);
        }

        JSONObject mcp = object.optJSONObject("mcp");
        if (mcp != null) {
            String name = mcp.optString("name", mcp.optString("tool", mcp.optString("tool_name", ""))).trim();
            String server = mcp.optString("server", mcp.optString("server_name", "")).trim();
            if (!server.isEmpty() && !name.startsWith("mcp_")) {
                name = mcpToolName(server, name);
            }
            Object args = mcp.has("arguments") ? mcp.opt("arguments") : mcp.opt("args");
            return acceptedCall(name, args, mcp.optString("id", object.optString("id", "")), true);
        }

        String name = object.optString("tool", object.optString("tool_name", object.optString("name", ""))).trim();
        if (!name.isEmpty() && (object.has("arguments") || object.has("args") || object.has("input"))) {
            Object args = object.has("arguments") ? object.opt("arguments") : object.has("args") ? object.opt("args") : object.opt("input");
            return acceptedCall(name, args, object.optString("id", ""), mcpOnly);
        }

        return null;
    }

    private static ToolCall acceptedCall(String name, Object args, String id, boolean mcpOnly) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty()) {
            return null;
        }
        if (mcpOnly && !isMcpToolName(safeName)) {
            return null;
        }
        return new ToolCall(safeName, ToolCall.normalizeArguments(args), id);
    }

    static boolean isMcpToolName(String name) {
        String safe = name == null ? "" : name.trim();
        return safe.startsWith("mcp_") || safe.startsWith("github_");
    }

    static String mcpToolName(String serverName, String toolName) {
        String safeServer = slug(serverName);
        String safeTool = slug(toolName);
        if (safeServer.isEmpty()) {
            return safeTool;
        }
        return "mcp_" + safeServer + "_" + safeTool;
    }

    static String slug(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    static final class Range {
        final int start;
        final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    static final class JsonCandidate {
        final Object json;
        final int start;
        final int end;

        JsonCandidate(Object json, int start, int end) {
            this.json = json;
            this.start = start;
            this.end = end;
        }
    }
}
