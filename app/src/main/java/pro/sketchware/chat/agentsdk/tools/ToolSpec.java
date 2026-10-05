package pro.sketchware.chat.agentsdk.tools;

import org.json.JSONObject;

import java.util.List;

/**
 * Model-facing tool specification, ported from Codex {@code ToolSpec}. A spec
 * describes what the MODEL sees and must honour; it carries no execution
 * logic. Execution is attached at registration time via a {@link ToolExecutor}.
 *
 * <pre>
 * ToolSpec
 *   ├── FUNCTION    — OpenAI-style json-schema tool ({@code {"type":"object"}})
 *   ├── FREEFORM    — raw-input tool (apply_patch, code execution); NO json schema
 *   ├── NAMESPACE   — a group of child tools under one namespace (clock.*, mcp__server__.*)
 *   └── TOOL_SEARCH — the synthetic {@code tool_search} discovery tool
 * </pre>
 *
 * <p>A spec is immutable; the same spec may be registered for different
 * {@link ToolExposure}s without sharing state.</p>
 */
public abstract class ToolSpec {

    /** The four Codex tool kinds Axion serialises as distinct wire shapes. */
    public enum Type {
        FUNCTION,
        FREEFORM,
        NAMESPACE,
        TOOL_SEARCH
    }

    private final ToolName name;
    private final String description;

    ToolSpec(ToolName name, String description) {
        this.name = name;
        this.description = description == null ? "" : description;
    }

    /** The codex tool kind this spec serialises as. */
    public abstract Type type();

    /** The model-visible identity of the tool. */
    public ToolName name() {
        return name;
    }

    /** The fully-qualified model name ({@code "ns.name"} when namespaced). */
    public String qualifiedName() {
        return name.qualifiedName();
    }

    /** Human-readable description sent to the model. */
    public String description() {
        return description;
    }

    /**
     * JSON schema ({@code {"type":"object",...}} envelope) for function tools;
     * the wire grammar definition for freeform tools; {@code null} for
     * namespace/tool_search specs.
     */
    public abstract JSONObject parameters();

    /** Optional output schema; {@code null} when the tool declares none. */
    public abstract JSONObject outputSchema();

    /**
     * Whether this tool is loading-deferred: present in the catalog but only
     * actually loaded/connected when the model first calls it.
     */
    public abstract boolean deferLoading();

    /** Child tools of a NAMESPACE spec (empty otherwise). */
    public abstract List<ToolSpec> childTools();

    // ------------------------------------------------------------------
    // Static factories — the ONLY way to construct specs.
    // ------------------------------------------------------------------

    /** FUNCTION: an OpenAI-style schema tool. */
    public static FunctionToolSpec function(ToolName name, String description, JSONObject parameters,
                                            JSONObject outputSchema, boolean deferLoading) {
        return new FunctionToolSpec(name, description, parameters, outputSchema, deferLoading);
    }

    /** FUNCTION shorthand with no output schema and eager loading. */
    public static FunctionToolSpec function(ToolName name, String description, JSONObject parameters) {
        return function(name, description, parameters, null, false);
    }

    /** FREEFORM: a raw-input tool with a grammar/lark description, no JSON schema. */
    public static FreeformToolSpec freeform(ToolName name, String description, String formatType,
                                            String syntax, String definition, boolean deferLoading) {
        return new FreeformToolSpec(name, description, formatType, syntax, definition, deferLoading);
    }

    /** NAMESPACE: a group of child tools under one namespace prefix. */
    public static NamespaceToolSpec namespace(ToolName name, String description, List<ToolSpec> children) {
        return new NamespaceToolSpec(name, description, children);
    }

    /** TOOL_SEARCH: the synthetic discovery tool that surfaces deferred tools. */
    public static ToolSearchToolSpec toolSearch(String description, JSONObject parameters) {
        return new ToolSearchToolSpec(description, parameters);
    }

    @Override
    public String toString() {
        return type() + "(" + qualifiedName() + ")";
    }
}