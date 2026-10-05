package pro.sketchware.chat.toolcalling;

import org.json.JSONObject;

import java.util.UUID;

public final class ToolCall {
    private final String name;
    private final String arguments;
    private final String id;

    public ToolCall(String name, String arguments, String id) {
        this.name = name == null ? "" : name.trim();
        this.arguments = normalizeArguments(arguments);
        this.id = id == null || id.trim().isEmpty()
                ? "tool_" + UUID.randomUUID()
                : id.trim();
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

    public boolean isValid() {
        return !name.isEmpty() && hasValidArguments(arguments);
    }

    public static boolean hasValidArguments(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return true;
        }
        try {
            new JSONObject(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeArguments(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return "{}";
        }
        try {
            return new JSONObject(value).toString();
        } catch (Exception ignored) {
            // Preserve the original payload for diagnostics. isValid() will reject it.
            return value;
        }
    }
}
