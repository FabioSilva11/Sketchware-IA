package pro.sketchware.chat.agentsdk.tools;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NAMESPACE tool spec: a group of child tools declared under one namespace
 * prefix (e.g. {@code clock} with {@code current_time} and {@code sleep}, or
 * an MCP server name). Ported from Codex {@code ToolSpec::Namespace}.
 *
 * <p>A namespace exposes a top-level entry the model expands; child tools
 * keep their own {@link ToolName}s ({@code namespace.child}) and are
 * callable directly. Children may be FUNCTION or FREEFORM specs.</p>
 */
public final class NamespaceToolSpec extends ToolSpec {

    private final List<ToolSpec> children;

    NamespaceToolSpec(ToolName name, String description, List<ToolSpec> children) {
        super(name, description);
        this.children = children == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(children));
    }

    @Override
    public Type type() {
        return Type.NAMESPACE;
    }

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
        return false;
    }

    @Override
    public List<ToolSpec> childTools() {
        return children;
    }

    /** Child specs indexed by their plain name (order preserved). */
    public Map<String, ToolSpec> childrenByName() {
        Map<String, ToolSpec> byName = new LinkedHashMap<>();
        for (ToolSpec child : children) {
            byName.put(child.name().name(), child);
        }
        return byName;
    }
}