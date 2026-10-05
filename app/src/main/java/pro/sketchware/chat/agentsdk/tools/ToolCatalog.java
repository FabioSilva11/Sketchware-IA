package pro.sketchware.chat.agentsdk.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the model-facing tool catalog for one LLM turn. The
 * canonical source of the catalog is {@link AxionToolRegistry#modelVisibleTools()};
 * a catalog carries the exact registrations the provider must see and never
 * lets another path assemble a parallel model-visible tool list.
 *
 * <p>Serialization to the provider wire is delegated to
 * {@link ToolSpecSerializer} (per-kind shapes) or
 * {@link #toFunctionEnvelope()} (OpenAI-style boundary envelope); the catalog
 * itself stays shape-agnostic.</p>
 */
public final class ToolCatalog {

    private final List<ToolRegistration> registrations;

    /** Snapshot of the given registrations (defensive copy, insertion order). */
    public ToolCatalog(List<ToolRegistration> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            this.registrations = Collections.emptyList();
        } else {
            this.registrations = Collections.unmodifiableList(new ArrayList<>(registrations));
        }
    }

    /** Canonical catalog from a registry's model-visible tools. */
    public static ToolCatalog from(AxionToolRegistry registry) {
        return new ToolCatalog(registry == null
                ? Collections.emptyList()
                : registry.modelVisibleTools());
    }

    /** Canonical catalog from a registry's model-visible tools (code-mode flag). */
    public static ToolCatalog from(AxionToolRegistry registry, boolean codeMode) {
        return new ToolCatalog(registry == null
                ? Collections.emptyList()
                : registry.modelVisibleTools(codeMode));
    }

    public List<ToolRegistration> registrations() {
        return registrations;
    }

    public int size() {
        return registrations.size();
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    /** Whether the catalog contains a tool with the given fully-qualified name. */
    public boolean contains(String qualifiedName) {
        for (ToolRegistration reg : registrations) {
            if (reg.qualifiedName().equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    /** The registration under the given fully-qualified name, or {@code null}. */
    public ToolRegistration get(String qualifiedName) {
        for (ToolRegistration reg : registrations) {
            if (reg.qualifiedName().equals(qualifiedName)) {
                return reg;
            }
        }
        return null;
    }

    /** Serializes to the OpenAI {@code {"type":"function","function":{...}}} boundary envelope. */
    public org.json.JSONArray toFunctionEnvelope() {
        return ToolSpecSerializer.toFunctionEnvelope(registrations);
    }

    /** Serializes to the faithful per-kind catalog (freeform/namespace/tool_search preserved). */
    public org.json.JSONArray toCatalog() {
        return ToolSpecSerializer.toCatalog(registrations);
    }

    @Override
    public String toString() {
        return "ToolCatalog(" + registrations.size() + ")";
    }
}