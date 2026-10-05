package pro.sketchware.chat.agentsdk.schema;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed representation of JSON Schema for agent tools, inspired by
 * {@code codex-rs/tools/src/json_schema/types.rs}.
 *
 * <p>This prevents the common error of representing {@code items} as an array
 * instead of a single schema object. The Codex implementation enforces this
 * constraint at the type level; we replicate the same concept here.</p>
 *
 * <p>Key constraint: {@code array(itemsSchema)} takes exactly ONE schema,
 * never a list of schemas. This makes it impossible to generate the invalid
 * {@code "items": [{"type": "object"}]} structure that causes HTTP 400.</p>
 */
public abstract class ToolJsonSchema {

    private ToolJsonSchema() {
        // Sealed type: only the inner classes can extend this
    }

    /**
     * Converts this typed schema to the JSON representation expected by LLM providers.
     */
    public abstract JSONObject toJson();

    // ========================================================================
    // PRIMITIVE TYPES
    // ========================================================================

    public static ToolJsonSchema string() {
        return new StringSchema(null);
    }

    public static ToolJsonSchema string(String description) {
        return new StringSchema(description);
    }

    public static ToolJsonSchema number() {
        return new NumberSchema(null);
    }

    public static ToolJsonSchema number(String description) {
        return new NumberSchema(description);
    }

    public static ToolJsonSchema integer() {
        return new IntegerSchema(null);
    }

    public static ToolJsonSchema integer(String description) {
        return new IntegerSchema(description);
    }

    public static ToolJsonSchema bool() {
        return new BooleanSchema(null);
    }

    public static ToolJsonSchema bool(String description) {
        return new BooleanSchema(description);
    }

    public static ToolJsonSchema nullSchema() {
        return new NullSchema();
    }

    // ========================================================================
    // COMPLEX TYPES
    // ========================================================================

    /**
     * Array schema with a SINGLE item schema.
     * Codex equivalent: {@code JsonSchema::array(items_schema)}.
     *
     * <p><b>Critical:</b> This takes exactly ONE schema, not a list.
     * The result is {@code "items": {...}}, never {@code "items": [...]}</p>
     */
    public static ToolJsonSchema array(ToolJsonSchema itemsSchema) {
        return new ArraySchema(itemsSchema, null);
    }

    public static ToolJsonSchema array(ToolJsonSchema itemsSchema, String description) {
        return new ArraySchema(itemsSchema, description);
    }

    /**
     * Object schema with properties.
     * Codex equivalent: {@code JsonSchema::object(properties, required, additional_properties)}.
     */
    public static ToolJsonSchema object(Map<String, ToolJsonSchema> properties) {
        return new ObjectSchema(properties, Collections.emptyList(), false, null);
    }

    public static ToolJsonSchema object(Map<String, ToolJsonSchema> properties,
                                        List<String> required) {
        return new ObjectSchema(properties, required, false, null);
    }

    public static ToolJsonSchema object(Map<String, ToolJsonSchema> properties,
                                        List<String> required,
                                        boolean additionalProperties) {
        return new ObjectSchema(properties, required, additionalProperties, null);
    }

    public static ToolJsonSchema object(Map<String, ToolJsonSchema> properties,
                                        List<String> required,
                                        boolean additionalProperties,
                                        String description) {
        return new ObjectSchema(properties, required, additionalProperties, description);
    }

    /**
     * String enum schema.
     */
    public static ToolJsonSchema stringEnum(String... values) {
        return new EnumSchema(Arrays.asList(values), null);
    }

    public static ToolJsonSchema stringEnum(List<String> values, String description) {
        return new EnumSchema(values, description);
    }

    // ========================================================================
    // COMPOSITION TYPES
    // ========================================================================

    public static ToolJsonSchema anyOf(ToolJsonSchema... schemas) {
        return new AnyOfSchema(Arrays.asList(schemas));
    }

    public static ToolJsonSchema anyOf(List<ToolJsonSchema> schemas) {
        return new AnyOfSchema(schemas);
    }

    public static ToolJsonSchema oneOf(ToolJsonSchema... schemas) {
        return new OneOfSchema(Arrays.asList(schemas));
    }

    public static ToolJsonSchema oneOf(List<ToolJsonSchema> schemas) {
        return new OneOfSchema(schemas);
    }

    public static ToolJsonSchema allOf(ToolJsonSchema... schemas) {
        return new AllOfSchema(Arrays.asList(schemas));
    }

    public static ToolJsonSchema allOf(List<ToolJsonSchema> schemas) {
        return new AllOfSchema(schemas);
    }

    // ========================================================================
    // INNER CLASSES (sealed type pattern)
    // ========================================================================

    private static class StringSchema extends ToolJsonSchema {
        private final String description;

        StringSchema(String description) {
            this.description = description;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "string");
                if (description != null && !description.isEmpty()) {
                    obj.put("description", description);
                }
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class NumberSchema extends ToolJsonSchema {
        private final String description;

        NumberSchema(String description) {
            this.description = description;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "number");
                if (description != null && !description.isEmpty()) {
                    obj.put("description", description);
                }
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class IntegerSchema extends ToolJsonSchema {
        private final String description;

        IntegerSchema(String description) {
            this.description = description;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "integer");
                if (description != null && !description.isEmpty()) {
                    obj.put("description", description);
                }
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class BooleanSchema extends ToolJsonSchema {
        private final String description;

        BooleanSchema(String description) {
            this.description = description;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "boolean");
                if (description != null && !description.isEmpty()) {
                    obj.put("description", description);
                }
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class NullSchema extends ToolJsonSchema {
        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "null");
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    /**
     * Array schema. The {@code items} field is ALWAYS a single schema object,
     * never an array of schemas. This is the core fix for the HTTP 400 bug.
     */
    private static class ArraySchema extends ToolJsonSchema {
        private final ToolJsonSchema items;
        private final String description;

        ArraySchema(ToolJsonSchema items, String description) {
            if (items == null) {
                throw new IllegalArgumentException("Array schema must have an items schema");
            }
            this.items = items;
            this.description = description;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "array");
                // CRITICAL: items is a single schema object, not an array
                obj.put("items", items.toJson());
                if (description != null && !description.isEmpty()) {
                    obj.put("description", description);
                }
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class ObjectSchema extends ToolJsonSchema {
        private final Map<String, ToolJsonSchema> properties;
        private final List<String> required;
        private final boolean additionalProperties;
        private final String description;

        ObjectSchema(Map<String, ToolJsonSchema> properties,
                     List<String> required,
                     boolean additionalProperties,
                     String description) {
            this.properties = properties != null ? properties : Collections.emptyMap();
            this.required = required != null ? required : Collections.emptyList();
            this.additionalProperties = additionalProperties;
            this.description = description;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "object");

                if (!properties.isEmpty()) {
                    JSONObject propsObj = new JSONObject();
                    for (Map.Entry<String, ToolJsonSchema> entry : properties.entrySet()) {
                        propsObj.put(entry.getKey(), entry.getValue().toJson());
                    }
                    obj.put("properties", propsObj);
                }

                if (!required.isEmpty()) {
                    JSONArray reqArray = new JSONArray();
                    for (String r : required) {
                        reqArray.put(r);
                    }
                    obj.put("required", reqArray);
                }

                obj.put("additionalProperties", additionalProperties);

                if (description != null && !description.isEmpty()) {
                    obj.put("description", description);
                }
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class EnumSchema extends ToolJsonSchema {
        private final List<String> values;
        private final String description;

        EnumSchema(List<String> values, String description) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("Enum schema must have at least one value");
            }
            this.values = values;
            this.description = description;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", "string");
                JSONArray enumArray = new JSONArray();
                for (String v : values) {
                    enumArray.put(v);
                }
                obj.put("enum", enumArray);
                if (description != null && !description.isEmpty()) {
                    obj.put("description", description);
                }
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class AnyOfSchema extends ToolJsonSchema {
        private final List<ToolJsonSchema> schemas;

        AnyOfSchema(List<ToolJsonSchema> schemas) {
            if (schemas == null || schemas.isEmpty()) {
                throw new IllegalArgumentException("anyOf must have at least one schema");
            }
            this.schemas = schemas;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                JSONArray array = new JSONArray();
                for (ToolJsonSchema s : schemas) {
                    array.put(s.toJson());
                }
                obj.put("anyOf", array);
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class OneOfSchema extends ToolJsonSchema {
        private final List<ToolJsonSchema> schemas;

        OneOfSchema(List<ToolJsonSchema> schemas) {
            if (schemas == null || schemas.isEmpty()) {
                throw new IllegalArgumentException("oneOf must have at least one schema");
            }
            this.schemas = schemas;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                JSONArray array = new JSONArray();
                for (ToolJsonSchema s : schemas) {
                    array.put(s.toJson());
                }
                obj.put("oneOf", array);
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    private static class AllOfSchema extends ToolJsonSchema {
        private final List<ToolJsonSchema> schemas;

        AllOfSchema(List<ToolJsonSchema> schemas) {
            if (schemas == null || schemas.isEmpty()) {
                throw new IllegalArgumentException("allOf must have at least one schema");
            }
            this.schemas = schemas;
        }

        @Override
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                JSONArray array = new JSONArray();
                for (ToolJsonSchema s : schemas) {
                    array.put(s.toJson());
                }
                obj.put("allOf", array);
            } catch (org.json.JSONException ignored) {
            }
            return obj;
        }
    }

    // ========================================================================
    // BUILDER HELPER FOR OBJECT PROPERTIES
    // ========================================================================

    /**
     * Convenience builder for object properties (map creation).
     */
    public static class PropertiesBuilder {
        private final Map<String, ToolJsonSchema> properties = new LinkedHashMap<>();

        public PropertiesBuilder put(String name, ToolJsonSchema schema) {
            properties.put(name, schema);
            return this;
        }

        public Map<String, ToolJsonSchema> build() {
            return properties;
        }
    }

    public static PropertiesBuilder properties() {
        return new PropertiesBuilder();
    }
}
