package pro.sketchware.chat.agentsdk.schema;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Schema normalizer inspired by Codex {@code parse_tool_input_schema} and
 * {@code sanitize_json_schema}.
 *
 * <p>This layer sits between {@code AgentTool.parameters()} and the provider
 * adapter, validating and normalizing tool schemas before they're sent to
 * the LLM API. The goal is to catch schema errors BEFORE the HTTP request,
 * providing clear local error messages instead of opaque HTTP 400 responses.</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Validate that root schema is an object type</li>
 *   <li>Recursively validate properties, items, additionalProperties</li>
 *   <li>Detect the invalid {@code "items": [...]} array pattern</li>
 *   <li>Infer missing {@code "type": "object"} when properties exist</li>
 *   <li>Validate required arrays match property names</li>
 *   <li>Fail fast with clear error messages for irrecoverable structures</li>
 * </ul>
 */
public class ToolSchemaNormalizer {

    /**
     * Validation result containing either a normalized schema or an error.
     */
    public static class ValidationResult {
        private final JSONObject normalizedSchema;
        private final String errorMessage;
        private final String errorPath;

        private ValidationResult(JSONObject normalizedSchema, String errorMessage, String errorPath) {
            this.normalizedSchema = normalizedSchema;
            this.errorMessage = errorMessage;
            this.errorPath = errorPath;
        }

        public static ValidationResult success(JSONObject schema) {
            return new ValidationResult(schema, null, null);
        }

        public static ValidationResult error(String path, String message) {
            return new ValidationResult(null, message, path);
        }

        public boolean isValid() {
            return normalizedSchema != null;
        }

        public JSONObject getSchema() {
            return normalizedSchema;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorPath() {
            return errorPath;
        }

        public String getFullErrorMessage() {
            if (isValid()) {
                return null;
            }
            return String.format("Invalid schema at %s: %s", errorPath, errorMessage);
        }
    }

    /**
     * Normalizes and validates a tool parameter schema.
     *
     * @param toolName the name of the tool (for error reporting)
     * @param schema the raw schema from {@code AgentTool.parameters()}
     * @return validation result with normalized schema or error details
     */
    public static ValidationResult normalize(String toolName, JSONObject schema) {
        if (schema == null || schema.length() == 0) {
            // Empty schema is valid (no parameters)
            return ValidationResult.success(new JSONObject());
        }

        try {
            // Tool parameters must be object type at root
            String rootType = schema.optString("type", "");
            if (rootType.isEmpty() && schema.has("properties")) {
                // Infer object type when properties exist
                schema = new JSONObject(schema.toString()); // clone
                schema.put("type", "object");
                rootType = "object";
            }

            if (!"object".equals(rootType)) {
                return ValidationResult.error(
                        "root",
                        "tool parameters must be object type, got: " + rootType);
            }

            // Recursively validate the schema structure
            ValidationResult result = validateSchema(schema, "root");
            if (!result.isValid()) {
                return result;
            }

            return ValidationResult.success(result.getSchema());
        } catch (JSONException e) {
            return ValidationResult.error("root", "JSON structure error: " + e.getMessage());
        }
    }

    /**
     * Recursively validates a schema node.
     */
    private static ValidationResult validateSchema(JSONObject schema, String path) {
        try {
            // Clone to avoid modifying the original
            JSONObject normalized = new JSONObject(schema.toString());

            String type = normalized.optString("type", "");

            // Infer object type from properties
            if (type.isEmpty() && normalized.has("properties")) {
                normalized.put("type", "object");
                type = "object";
            }

            // Validate based on type
            switch (type) {
                case "object":
                    return validateObjectSchema(normalized, path);
                case "array":
                    return validateArraySchema(normalized, path);
                case "string":
                case "number":
                case "integer":
                case "boolean":
                case "null":
                    // Primitive types are valid as-is
                    return ValidationResult.success(normalized);
                case "":
                    // No type, check for composition keywords
                    if (normalized.has("anyOf") || normalized.has("oneOf") || normalized.has("allOf")) {
                        return validateCompositionSchema(normalized, path);
                    }
                    // No type and no composition, this is ambiguous
                    return ValidationResult.error(path, "schema has no type and no composition keywords");
                default:
                    return ValidationResult.error(path, "unknown type: " + type);
            }
        } catch (JSONException e) {
            return ValidationResult.error(path, "JSON error: " + e.getMessage());
        }
    }

    /**
     * Validates an object schema with properties and required fields.
     */
    private static ValidationResult validateObjectSchema(JSONObject schema, String path) {
        try {
            JSONObject normalized = new JSONObject(schema.toString());

            // Validate properties recursively
            if (schema.has("properties")) {
                JSONObject properties = schema.getJSONObject("properties");
                JSONObject normalizedProps = new JSONObject();

                Iterator<String> keys = properties.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = properties.get(key);

                    if (!(value instanceof JSONObject)) {
                        return ValidationResult.error(
                                path + ".properties." + key,
                                "property schema must be an object, got: " + value.getClass().getSimpleName());
                    }

                    ValidationResult propResult = validateSchema(
                            (JSONObject) value,
                            path + ".properties." + key);
                    if (!propResult.isValid()) {
                        return propResult;
                    }

                    normalizedProps.put(key, propResult.getSchema());
                }

                normalized.put("properties", normalizedProps);
            }

            // Validate required array
            if (schema.has("required")) {
                Object required = schema.get("required");
                if (!(required instanceof JSONArray)) {
                    return ValidationResult.error(
                            path + ".required",
                            "required must be an array, got: " + required.getClass().getSimpleName());
                }

                JSONArray requiredArray = (JSONArray) required;
                if (schema.has("properties")) {
                    JSONObject properties = schema.getJSONObject("properties");
                    for (int i = 0; i < requiredArray.length(); i++) {
                        String requiredField = requiredArray.optString(i);
                        if (requiredField.isEmpty()) {
                            return ValidationResult.error(
                                    path + ".required[" + i + "]",
                                    "required field name cannot be empty");
                        }
                        if (!properties.has(requiredField)) {
                            return ValidationResult.error(
                                    path + ".required[" + i + "]",
                                    "required field '" + requiredField + "' not found in properties");
                        }
                    }
                }
            }

            // Validate additionalProperties if it's a schema
            if (schema.has("additionalProperties")) {
                Object additionalProps = schema.get("additionalProperties");
                if (additionalProps instanceof JSONObject) {
                    ValidationResult addPropsResult = validateSchema(
                            (JSONObject) additionalProps,
                            path + ".additionalProperties");
                    if (!addPropsResult.isValid()) {
                        return addPropsResult;
                    }
                    normalized.put("additionalProperties", addPropsResult.getSchema());
                } else if (!(additionalProps instanceof Boolean)) {
                    return ValidationResult.error(
                            path + ".additionalProperties",
                            "must be boolean or schema object, got: " + additionalProps.getClass().getSimpleName());
                }
            }

            return ValidationResult.success(normalized);
        } catch (JSONException e) {
            return ValidationResult.error(path, "JSON error: " + e.getMessage());
        }
    }

    /**
     * Validates an array schema. This is where we catch the critical
     * {@code "items": [...]} bug.
     */
    private static ValidationResult validateArraySchema(JSONObject schema, String path) {
        try {
            JSONObject normalized = new JSONObject(schema.toString());

            if (!schema.has("items")) {
                // Array without items - provide a permissive default
                normalized.put("items", new JSONObject().put("type", "string"));
                return ValidationResult.success(normalized);
            }

            Object items = schema.get("items");

            // CRITICAL CHECK: items must be a schema object, NOT an array
            if (items instanceof JSONArray) {
                return ValidationResult.error(
                        path + ".items",
                        "items must be a schema object, not an array. " +
                        "Use a single schema to describe all array elements.");
            }

            if (!(items instanceof JSONObject)) {
                return ValidationResult.error(
                        path + ".items",
                        "items must be a schema object, got: " + items.getClass().getSimpleName());
            }

            // Recursively validate the items schema
            ValidationResult itemsResult = validateSchema((JSONObject) items, path + ".items");
            if (!itemsResult.isValid()) {
                return itemsResult;
            }

            normalized.put("items", itemsResult.getSchema());
            return ValidationResult.success(normalized);
        } catch (JSONException e) {
            return ValidationResult.error(path, "JSON error: " + e.getMessage());
        }
    }

    /**
     * Validates composition schemas (anyOf, oneOf, allOf).
     */
    private static ValidationResult validateCompositionSchema(JSONObject schema, String path) {
        try {
            JSONObject normalized = new JSONObject(schema.toString());

            for (String keyword : new String[]{"anyOf", "oneOf", "allOf"}) {
                if (schema.has(keyword)) {
                    Object composition = schema.get(keyword);
                    if (!(composition instanceof JSONArray)) {
                        return ValidationResult.error(
                                path + "." + keyword,
                                keyword + " must be an array, got: " + composition.getClass().getSimpleName());
                    }

                    JSONArray compositionArray = (JSONArray) composition;
                    if (compositionArray.length() == 0) {
                        return ValidationResult.error(
                                path + "." + keyword,
                                keyword + " must have at least one schema");
                    }

                    JSONArray normalizedArray = new JSONArray();
                    for (int i = 0; i < compositionArray.length(); i++) {
                        Object item = compositionArray.get(i);
                        if (!(item instanceof JSONObject)) {
                            return ValidationResult.error(
                                    path + "." + keyword + "[" + i + "]",
                                    "must be a schema object, got: " + item.getClass().getSimpleName());
                        }

                        ValidationResult itemResult = validateSchema(
                                (JSONObject) item,
                                path + "." + keyword + "[" + i + "]");
                        if (!itemResult.isValid()) {
                            return itemResult;
                        }

                        normalizedArray.put(itemResult.getSchema());
                    }

                    normalized.put(keyword, normalizedArray);
                }
            }

            return ValidationResult.success(normalized);
        } catch (JSONException e) {
            return ValidationResult.error(path, "JSON error: " + e.getMessage());
        }
    }
}
