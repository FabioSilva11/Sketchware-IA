package pro.sketchware.chat.agentsdk.tools;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/**
 * TOOL_SEARCH spec: the synthetic discovery tool that surfaces deferred
 * tools to the model. Ported from Codex {@code ToolSpec::ToolSearch}.
 *
 * <p>The model calls {@code tool_search} with a free-text {@code query}; the
 * executor activates the best-matching deferred tools and returns short
 * entries the model can then call by name. Only present in the catalog when
 * at least one deferred tool exists.</p>
 */
public final class ToolSearchToolSpec extends ToolSpec {

    private final JSONObject parameters;
    private final String execution;

    ToolSearchToolSpec(String description, JSONObject parameters) {
        this(description, parameters, "sync");
    }

    ToolSearchToolSpec(String description, JSONObject parameters, String execution) {
        super(ToolName.plain("tool_search"), description);
        if (parameters == null || parameters.length() == 0) {
            try {
                parameters = new JSONObject().put("type", "object");
            } catch (org.json.JSONException e) {
                parameters = new JSONObject();
            }
        }
        this.parameters = parameters;
        this.execution = execution == null || execution.trim().isEmpty() ? "sync" : execution.trim();
    }

    @Override
    public Type type() {
        return Type.TOOL_SEARCH;
    }

    @Override
    public JSONObject parameters() {
        return parameters;
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
        return Collections.emptyList();
    }

    /** How tool_search executes: {@code "sync"} (in-process) or {@code "client"}. */
    public String execution() {
        return execution;
    }
}