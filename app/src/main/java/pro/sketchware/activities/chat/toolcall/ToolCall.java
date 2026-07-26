package pro.sketchware.activities.chat.toolcall;

import org.json.JSONObject;

import java.util.UUID;

public final class ToolCall {
    private final String name;
    private final String arguments;
    private final String id;

    public ToolCall(String name, String arguments, String id) {
        this.name = sanitizeName(name);
        this.arguments = normalizeArguments(arguments);
        this.id = id == null || id.trim().isEmpty() ? "tool_" + UUID.randomUUID() : id.trim();
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }

    public String getId() {
        return id;
    }

    public String[] toLegacyArray() {
        return new String[]{name, arguments, id};
    }

    public static ToolCall fromLegacyArray(String[] value) {
        if (value == null) {
            return new ToolCall("", "{}", "");
        }
        String name = value.length > 0 ? value[0] : "";
        String args = value.length > 1 ? value[1] : "{}";
        String id = value.length > 2 ? value[2] : "";
        return new ToolCall(name, args, id);
    }

    public boolean isValid() {
        return !name.isEmpty();
    }

    private static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("[^A-Za-z0-9_.-]", "");
    }

    public static String normalizeArguments(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return "{}";
        }
        if (raw instanceof JSONObject) {
            return ((JSONObject) raw).toString();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return "{}";
        }
        try {
            return new JSONObject(text).toString();
        } catch (Exception ignored) {
            JSONObject wrapper = new JSONObject();
            try {
                wrapper.put("value", text);
                return wrapper.toString();
            } catch (Exception ignoredAgain) {
                return "{}";
            }
        }
    }
}
