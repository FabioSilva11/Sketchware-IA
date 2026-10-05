package pro.sketchware.chat.agentsdk.tools;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/**
 * FREEFORM tool spec: a tool whose input is NOT json-schema-wrapped — the
 * raw content itself (e.g. {@code apply_patch} receives the patch document
 * directly, never {@code {"patch": "..."}}). Ported from Codex
 * {@code FreeformTool}.
 *
 * <p>Freeform tools carry a language-grammar description
 * ({@code format.type/syntax/definition}) so constrained or grammar-grounded
 * providers can validate the raw input. Their execution receives the raw
 * string; there is no JSON argument parsing.</p>
 */
public final class FreeformToolSpec extends ToolSpec {

    private final String formatType;
    private final String syntax;
    private final String definition;
    private final boolean deferLoading;

    FreeformToolSpec(ToolName name, String description, String formatType,
                     String syntax, String definition, boolean deferLoading) {
        super(name, description);
        this.formatType = formatType == null || formatType.trim().isEmpty() ? "grammar" : formatType.trim();
        this.syntax = syntax;
        this.definition = definition;
        this.deferLoading = deferLoading;
    }

    @Override
    public Type type() {
        return Type.FREEFORM;
    }

    /** Freeform tools have no OpenAPI-style parameters; returns {@code null}. */
    @Override
    public JSONObject parameters() {
        return null;
    }

    @Override
    public JSONObject outputSchema() {
        return null;
    }

    @Override
    public boolean deferLoading() {
        return deferLoading;
    }

    @Override
    public List<ToolSpec> childTools() {
        return Collections.emptyList();
    }

    /** The format kind (Codex uses {@code "grammar"} for lark definitions). */
    public String formatType() {
        return formatType;
    }

    /** The grammar language ({@code "lark"} for Codex grammar definitions). */
    public String syntax() {
        return syntax;
    }

    /** The raw input wire definition (the grammar body for apply_patch). */
    public String definition() {
        return definition;
    }
}