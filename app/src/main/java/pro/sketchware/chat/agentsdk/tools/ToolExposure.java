package pro.sketchware.chat.agentsdk.tools;

/**
 * Tool exposure to the model, ported from Codex {@code ToolExposure}: a tool
 * is either exposed directly in the catalog, deferred (only discoverable
 * through {@code tool_search}), or hidden (an internal executor that never
 * reaches the model).
 *
 * <p>The optional {@code codeModeOnly} / {@code modelOnly} flags mirror the
 * Codex exposure axes: code-mode tools are catalog-only in code mode, and
 * model-only tools are meant for the model rather than a surface-limited
 * host chat mode.</p>
 */
public final class ToolExposure {

    /** The three exposure states a tool can be in. */
    public enum Kind {
        /** Present in the model catalog; callable directly. */
        DIRECT,
        /** Absent from the catalog; discoverable and activated via tool_search. */
        DEFERRED,
        /** Never model-visible; an internal executor only. */
        HIDDEN
    }

    private final Kind kind;
    private final boolean codeModeOnly;
    private final boolean modelOnly;

    private ToolExposure(Kind kind, boolean codeModeOnly, boolean modelOnly) {
        this.kind = kind == null ? Kind.DIRECT : kind;
        this.codeModeOnly = codeModeOnly;
        this.modelOnly = modelOnly;
    }

    /** Directly visible and callable by the model. */
    public static ToolExposure direct() {
        return new ToolExposure(Kind.DIRECT, false, false);
    }

    /** Direct exposure restricted to code mode. */
    public static ToolExposure directCodeModeOnly() {
        return new ToolExposure(Kind.DIRECT, true, false);
    }

    /** Direct exposure meant for the model only (not a surface-limited host). */
    public static ToolExposure directModelOnly() {
        return new ToolExposure(Kind.DIRECT, false, true);
    }

    /** Deferred: hidden from the catalog until {@code tool_search} activates it. */
    public static ToolExposure deferred() {
        return new ToolExposure(Kind.DEFERRED, false, false);
    }

    /** Hidden: never model-visible; an internal executor only. */
    public static ToolExposure hidden() {
        return new ToolExposure(Kind.HIDDEN, false, false);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isDirect() {
        return kind == Kind.DIRECT;
    }

    public boolean isDeferred() {
        return kind == Kind.DEFERRED;
    }

    public boolean isHidden() {
        return kind == Kind.HIDDEN;
    }

    /** Whether this exposure is restricted to code mode. */
    public boolean isCodeModeOnly() {
        return codeModeOnly;
    }

    /** Whether this exposure is model-only (not a surface-limited host chat). */
    public boolean isModelOnly() {
        return modelOnly;
    }

    /** Whether a model without the code-mode flag can see this tool. */
    public boolean isModelVisible() {
        return kind == Kind.DIRECT && !codeModeOnly;
    }

    /** Whether a code-mode model can see this tool. */
    public boolean isCodeModeVisible() {
        return kind == Kind.DIRECT;
    }

    @Override
    public String toString() {
        return kind + (codeModeOnly ? "(codeModeOnly)" : "") + (modelOnly ? "(modelOnly)" : "");
    }
}