package pro.sketchware.chat.toolcalling;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Bounded JSON-Schema validation for tool-call arguments.
 *
 * This intentionally validates the subset used by Axion/MCP tool schemas:
 * object/array/scalars, required, properties, additionalProperties=false,
 * enum, min/max length/items and nested schemas. Invalid payloads are rejected
 * before execution instead of being silently coerced to {}.
 */
public final class ToolArgumentsValidator {
    private static final int MAX_DEPTH = 24;

    public static final class Result {
        private final boolean valid;
        private final JSONObject arguments;
        private final String error;

        private Result(boolean valid, JSONObject arguments, String error) {
            this.valid = valid;
            this.arguments = arguments == null ? new JSONObject() : arguments;
            this.error = error == null ? "" : error;
        }

        public boolean isValid() { return valid; }
        public JSONObject getArguments() { return arguments; }
        public String getError() { return error; }
    }

    private ToolArgumentsValidator() {}

    public static Result validate(String rawArguments, JSONObject schema) {
        final JSONObject arguments;
        try {
            String raw = rawArguments == null ? "" : rawArguments.trim();
            if (raw.isEmpty() || "null".equalsIgnoreCase(raw)) {
                arguments = new JSONObject();
            } else {
                Object parsed = new org.json.JSONTokener(raw).nextValue();
                if (!(parsed instanceof JSONObject)) {
                    return new Result(false, null, "Os argumentos da ferramenta devem ser um objeto JSON.");
                }
                arguments = (JSONObject) parsed;
            }
        } catch (Exception invalidJson) {
            return new Result(false, null,
                    "Os argumentos da ferramenta não formam um objeto JSON válido.");
        }

        if (schema == null) return new Result(true, arguments, "");
        String error = validateValue(arguments, schema, "$", 0);
        if (error != null) {
            return new Result(false, arguments, error);
        }
        removeOptionalNulls(arguments, schema, 0);
        return new Result(true, arguments, "");
    }

    /** Strict OpenAI schemas encode optional fields as required nullable values. */
    private static void removeOptionalNulls(JSONObject object, JSONObject schema, int depth) {
        if (object == null || schema == null || depth > MAX_DEPTH) {
            return;
        }
        Set<String> required = new HashSet<>();
        JSONArray requiredArray = schema.optJSONArray("required");
        for (int i = 0; requiredArray != null && i < requiredArray.length(); i++) {
            required.add(requiredArray.optString(i, ""));
        }
        JSONObject properties = schema.optJSONObject("properties");
        JSONArray names = object.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String key = names.optString(i, "");
            JSONObject childSchema = properties == null ? null : properties.optJSONObject(key);
            if (object.isNull(key) && !required.contains(key)) {
                object.remove(key);
                continue;
            }
            Object child = object.opt(key);
            if (child instanceof JSONObject && childSchema != null) {
                removeOptionalNulls((JSONObject) child, childSchema, depth + 1);
            }
        }
    }

    private static String validateValue(Object value, JSONObject schema, String path, int depth) {
        if (schema == null) return null;
        if (depth > MAX_DEPTH) return "Schema/argumentos excedem a profundidade máxima permitida em " + path + ".";

        String expectedType = schema.optString("type", "").trim();
        if (!expectedType.isEmpty() && !matchesType(value, expectedType)) {
            return "Tipo inválido em '" + path + "': esperado " + expectedType + ".";
        }

        JSONArray enumValues = schema.optJSONArray("enum");
        if (enumValues != null && !containsJsonValue(enumValues, value)) {
            return "Valor inválido em '" + path + "': não pertence ao enum permitido.";
        }

        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONArray required = schema.optJSONArray("required");
            for (int i = 0; required != null && i < required.length(); i++) {
                String key = required.optString(i, "").trim();
                if (!key.isEmpty() && (!object.has(key) || object.isNull(key))) {
                    return "Parâmetro obrigatório ausente: '" + key + "'.";
                }
            }

            JSONObject properties = schema.optJSONObject("properties");
            boolean additionalAllowed = !schema.has("additionalProperties")
                    || schema.optBoolean("additionalProperties", true);
            Set<String> known = new HashSet<>();
            JSONArray propertyNames = properties == null ? null : properties.names();
            for (int i = 0; propertyNames != null && i < propertyNames.length(); i++) {
                known.add(propertyNames.optString(i, ""));
            }

            JSONArray names = object.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String key = names.optString(i, "");
                JSONObject childSchema = properties == null ? null : properties.optJSONObject(key);
                if (childSchema == null) {
                    if (!additionalAllowed) {
                        return "Parâmetro desconhecido não permitido: '" + key + "'.";
                    }
                    continue;
                }
                if (object.isNull(key)) continue;
                String error = validateValue(object.opt(key), childSchema, path + "." + key, depth + 1);
                if (error != null) return error;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (schema.has("minItems") && array.length() < schema.optInt("minItems", 0)) {
                return "Array em '" + path + "' possui menos itens que o permitido.";
            }
            if (schema.has("maxItems") && array.length() > schema.optInt("maxItems", Integer.MAX_VALUE)) {
                return "Array em '" + path + "' possui mais itens que o permitido.";
            }
            JSONObject itemSchema = schema.optJSONObject("items");
            if (itemSchema != null) {
                for (int i = 0; i < array.length(); i++) {
                    Object child = array.opt(i);
                    if (child == JSONObject.NULL) continue;
                    String error = validateValue(child, itemSchema, path + "[" + i + "]", depth + 1);
                    if (error != null) return error;
                }
            }
        } else if (value instanceof String) {
            String text = (String) value;
            if (schema.has("minLength") && text.length() < schema.optInt("minLength", 0)) {
                return "Texto em '" + path + "' é menor que minLength.";
            }
            if (schema.has("maxLength") && text.length() > schema.optInt("maxLength", Integer.MAX_VALUE)) {
                return "Texto em '" + path + "' excede maxLength.";
            }
        }

        return null;
    }

    private static boolean containsJsonValue(JSONArray values, Object value) {
        for (int i = 0; i < values.length(); i++) {
            if (jsonValuesEqual(value, values.opt(i))) return true;
        }
        return false;
    }

    private static boolean jsonValuesEqual(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        if (left == JSONObject.NULL || right == JSONObject.NULL) {
            return left == JSONObject.NULL && right == JSONObject.NULL;
        }
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
        }
        if ((left instanceof JSONObject && right instanceof JSONObject)
                || (left instanceof JSONArray && right instanceof JSONArray)) {
            return left.toString().equals(right.toString());
        }
        return left.equals(right);
    }

    private static boolean matchesType(Object value, String expectedType) {
        if (expectedType == null || expectedType.isEmpty()) return true;
        if (value == null || value == JSONObject.NULL) return "null".equals(expectedType);
        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof JSONArray;
            case "object" -> value instanceof JSONObject;
            case "null" -> value == JSONObject.NULL;
            default -> true;
        };
    }
}
