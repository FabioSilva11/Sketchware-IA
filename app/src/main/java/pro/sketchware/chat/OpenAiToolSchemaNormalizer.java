package pro.sketchware.chat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Builds a request-only strict copy of Axion's built-in OpenAI tool schemas. */
public final class OpenAiToolSchemaNormalizer {
    private OpenAiToolSchemaNormalizer() {}

    public static JSONArray forRequest(JSONArray tools, boolean enableStrict) {
        if (tools == null) {
            return new JSONArray();
        }
        final JSONArray copy;
        try {
            copy = new JSONArray(tools.toString());
        } catch (Exception invalidTools) {
            return tools;
        }
        if (!enableStrict) {
            return copy;
        }

        for (int i = 0; i < copy.length(); i++) {
            JSONObject tool = copy.optJSONObject(i);
            JSONObject function = tool == null ? null : tool.optJSONObject("function");
            if (function == null) {
                continue;
            }
            String name = function.optString("name", "");
            // MCP schemas are controlled by external servers and may use JSON
            // Schema features outside OpenAI strict mode's supported subset.
            if (name.startsWith("mcp_")) {
                continue;
            }
            JSONObject parameters = function.optJSONObject("parameters");
            if (parameters != null && normalizeSchema(parameters, false, 0)) {
                try {
                    function.put("strict", true);
                } catch (Exception ignored) {
                }
            }
        }
        return copy;
    }

    private static boolean normalizeSchema(JSONObject schema, boolean nullable, int depth) {
        if (schema == null || depth > 20 || hasUnsupportedComposition(schema)) {
            return false;
        }
        Object rawType = schema.opt("type");
        if (!(rawType instanceof String)) {
            return false;
        }
        String primaryType = ((String) rawType).trim();
        if (!isSupportedType(primaryType)) {
            return false;
        }

        if ("object".equals(primaryType)) {
            JSONObject properties = schema.optJSONObject("properties");
            if (properties == null) {
                properties = new JSONObject();
                try {
                    schema.put("properties", properties);
                } catch (Exception ignored) {
                    return false;
                }
            }
            Set<String> originallyRequired = stringSet(schema.optJSONArray("required"));
            JSONArray allRequired = new JSONArray();
            JSONArray names = properties.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String name = names.optString(i, "");
                JSONObject child = properties.optJSONObject(name);
                if (name.isEmpty() || child == null
                        || !normalizeSchema(child, !originallyRequired.contains(name), depth + 1)) {
                    return false;
                }
                allRequired.put(name);
            }
            try {
                schema.put("required", allRequired);
                schema.put("additionalProperties", false);
            } catch (Exception ignored) {
                return false;
            }
        } else if ("array".equals(primaryType)) {
            JSONObject items = schema.optJSONObject("items");
            if (items == null || !normalizeSchema(items, false, depth + 1)) {
                return false;
            }
        }

        if (nullable) {
            try {
                schema.put("type", new JSONArray().put(primaryType).put("null"));
                JSONArray enumValues = schema.optJSONArray("enum");
                if (enumValues != null && !containsNull(enumValues)) {
                    enumValues.put(JSONObject.NULL);
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnsupportedComposition(JSONObject schema) {
        return schema.has("$ref")
                || schema.has("oneOf")
                || schema.has("anyOf")
                || schema.has("allOf")
                || schema.has("patternProperties");
    }

    private static boolean isSupportedType(String type) {
        return "object".equals(type)
                || "array".equals(type)
                || "string".equals(type)
                || "integer".equals(type)
                || "number".equals(type)
                || "boolean".equals(type);
    }

    private static Set<String> stringSet(JSONArray values) {
        Set<String> result = new HashSet<>();
        for (int i = 0; values != null && i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static boolean containsNull(JSONArray values) {
        for (int i = 0; values != null && i < values.length(); i++) {
            if (values.isNull(i)) {
                return true;
            }
        }
        return false;
    }
}
