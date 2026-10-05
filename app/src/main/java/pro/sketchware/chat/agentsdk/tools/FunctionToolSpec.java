package pro.sketchware.chat.agentsdk.tools;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/**
 * FUNCTION tool spec: an OpenAI-style json-schema tool, the default Codex
 * tool kind. Parameters are a JSON schema rooted at {@code {"type":"object"}}.
 */
public final class FunctionToolSpec extends ToolSpec {

    private final JSONObject parameters;
    private final JSONObject outputSchema;
    private final boolean deferLoading;

    FunctionToolSpec(ToolName name, String description, JSONObject parameters,
                     JSONObject outputSchema, boolean deferLoading) {
        super(name, description);
        if (parameters == null || parameters.length() == 0) {
            try {
                parameters = new JSONObject().put("type", "object");
            } catch (org.json.JSONException e) {
                parameters = new JSONObject();
            }
        }
        this.parameters = parameters;
        this.outputSchema = outputSchema;
        this.deferLoading = deferLoading;
    }

    @Override
    public Type type() {
        return Type.FUNCTION;
    }

    @Override
    public JSONObject parameters() {
        return parameters;
    }

    @Override
    public JSONObject outputSchema() {
        return outputSchema;
    }

    @Override
    public boolean deferLoading() {
        return deferLoading;
    }

    @Override
    public List<ToolSpec> childTools() {
        return Collections.emptyList();
    }
}