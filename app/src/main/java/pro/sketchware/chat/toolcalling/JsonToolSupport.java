package pro.sketchware.chat.toolcalling;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class JsonToolSupport {
    private JsonToolSupport() {
    }

    static List<ToolCall> extractGeneric(Object value) {
        List<ToolCall> calls = new ArrayList<>();
        extractGenericInto(value, calls);
        return calls;
    }

    static List<ToolCall> extractMcp(Object value) {
        List<ToolCall> calls = new ArrayList<>();
        extractMcpInto(value, calls);
        return calls;
    }

    private static void extractGenericInto(Object value, List<ToolCall> calls) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                extractGenericInto(array.opt(i), calls);
            }
            return;
        }
        if (!(value instanceof JSONObject)) {
            return;
        }
        JSONObject object = (JSONObject) value;

        JSONArray toolCalls = object.optJSONArray("tool_calls");
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject item = toolCalls.optJSONObject(i);
                if (item != null) {
                    addFunctionObject(item, item.optJSONObject("function"), calls);
                }
            }
            return;
        }

        JSONObject functionCall = object.optJSONObject("function_call");
        if (functionCall != null) {
            addFunctionObject(object, functionCall, calls);
            return;
        }

        JSONObject function = object.optJSONObject("function");
        if (function != null && !function.optString("name", "").trim().isEmpty()) {
            addFunctionObject(object, function, calls);
            return;
        }

        String type = object.optString("type", "");
        if ("tool_use".equals(type) || "function_call".equals(type)) {
            addCall(
                    object.optString("name", ""),
                    firstPresent(object, "input", "arguments", "args", "parameters"),
                    object.optString("id", ""),
                    calls
            );
            return;
        }

        String name = firstString(object, "tool", "tool_name", "function", "name");
        Object arguments = firstPresent(object, "arguments", "args", "parameters", "input");
        if (!name.isEmpty() && arguments != null) {
            addCall(name, arguments, object.optString("id", ""), calls);
        }
    }

    private static void extractMcpInto(Object value, List<ToolCall> calls) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                extractMcpInto(array.opt(i), calls);
            }
            return;
        }
        if (!(value instanceof JSONObject)) {
            return;
        }
        JSONObject object = (JSONObject) value;
        String method = object.optString("method", "").trim();
        if (!"tools/call".equals(method)
                && !"tool/call".equals(method)
                && !"call_tool".equals(method)) {
            return;
        }
        JSONObject params = object.optJSONObject("params");
        if (params == null) {
            return;
        }
        String name = firstString(params, "name", "tool", "tool_name");
        Object arguments = firstPresent(params, "arguments", "args", "parameters", "input");
        addCall(name, arguments, String.valueOf(object.opt("id")), calls);
    }

    private static void addFunctionObject(JSONObject owner, JSONObject function,
                                          List<ToolCall> calls) {
        if (function == null) {
            return;
        }
        addCall(
                function.optString("name", ""),
                firstPresent(function, "arguments", "args", "parameters", "input"),
                owner.optString("id", ""),
                calls
        );
    }

    private static void addCall(String name, Object arguments, String id,
                                List<ToolCall> calls) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty()) {
            return;
        }
        ToolCall call = new ToolCall(safeName, argumentsToJson(arguments), cleanId(id));
        if (call.isValid()) {
            calls.add(call);
        }
    }

    private static String argumentsToJson(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "{}";
        }
        if (value instanceof JSONObject) {
            return value.toString();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return "{}";
            }
            try {
                return new JSONObject(text).toString();
            } catch (Exception ignored) {
                return scalarArguments(text);
            }
        }
        return scalarArguments(value);
    }

    private static String scalarArguments(Object value) {
        try {
            return new JSONObject().put("value", value).toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static Object firstPresent(JSONObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.isNull(key)) {
                return object.opt(key);
            }
        }
        return null;
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return ((String) value).trim();
            }
        }
        return "";
    }

    private static String cleanId(String value) {
        String id = value == null ? "" : value.trim();
        return "null".equalsIgnoreCase(id) ? "" : id;
    }
}
