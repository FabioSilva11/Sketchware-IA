package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.ApprovalHandler;
import pro.sketchware.chat.agentsdk.PermissionDecision;
import pro.sketchware.chat.agentsdk.PermissionRequest;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Core {@code request_user_input} executor implementing the Codex structured
 * contract (reference {@code request_user_input_spec.rs}): exactly
 *
 * <pre>
 * {"questions": [{"id","header","question",
 *                 "options":[{"label","description"}]}]}   // "questions" required
 * </pre>
 *
 * One question is preferred; a maximum of three is enforced. Every option
 * needs a label and a description pseudo-required pair (label required,
 * description required in Codex; we tolerate a missing description at parse
 * time and substitute an empty string, keeping the wire strict).
 *
 * <p>The question is routed through the same {@link ApprovalHandler}
 * human-in-the-loop boundary used by the legacy tool so answers reach the
 * model as the tool result. Fail-closed: no handler means an error asking the
 * model to proceed with the best safe default.</p>
 */
final class RequestUserInputExecutor implements ToolExecutor {

    static final int MAX_QUESTIONS = 3;
    static final int MAX_OPTIONS = 4;

    private final ApprovalHandler handler;

    RequestUserInputExecutor(ApprovalHandler handler) {
        this.handler = handler;
    }

    @Override
    public AgentToolResult execute(ToolExecutionContext ctx) {
        JSONObject args = ctx.functionArguments();
        JSONArray questions = args == null ? null : args.optJSONArray("questions");
        if (questions == null) {
            return AgentToolResult.error(
                    "Error: invalid request_user_input arguments; the required 'questions' "
                            + "array is missing (each item needs id, header, question and options).");
        }
        if (questions.length() == 0) {
            return AgentToolResult.error("Error: 'questions' must contain at least one question.");
        }
        if (questions.length() > MAX_QUESTIONS) {
            return AgentToolResult.error(
                    "Error: at most " + MAX_QUESTIONS + " questions are supported per call.");
        }

        JSONArray normalized = new JSONArray();
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.optJSONObject(i);
            if (q == null) {
                return AgentToolResult.error("Error: question " + i + " is not an object.");
            }
            String id = q.optString("id", "").trim();
            String header = q.optString("header", "").trim();
            String question = q.optString("question", "").trim();
            if (id.isEmpty() || header.isEmpty() || question.isEmpty()) {
                return AgentToolResult.error(
                        "Error: every question needs non-empty 'id', 'header' and 'question'.");
            }
            JSONArray rawOptions = q.optJSONArray("options");
            if (rawOptions == null || rawOptions.length() == 0) {
                return AgentToolResult.error(
                        "Error: question '" + id + "' needs non-empty 'options'.");
            }
            JSONArray cleanOptions = new JSONArray();
            for (int j = 0; j < rawOptions.length() && j < MAX_OPTIONS; j++) {
                JSONObject opt = rawOptions.optJSONObject(j);
                if (opt == null) {
                    return AgentToolResult.error(
                            "Error: option " + j + " of question '" + id + "' is not an object.");
                }
                String label = opt.optString("label", "").trim();
                if (label.isEmpty()) {
                    return AgentToolResult.error(
                            "Error: every option of question '" + id + "' needs a 'label'.");
                }
                try {
                    cleanOptions.put(new JSONObject()
                            .put("label", label)
                            .put("description", opt.optString("description", "").trim()));
                } catch (org.json.JSONException e) {
                    return AgentToolResult.error("Error: could not serialize option payload.");
                }
            }
            try {
                normalized.put(new JSONObject()
                        .put("id", id)
                        .put("header", header)
                        .put("question", question)
                        .put("options", cleanOptions));
            } catch (org.json.JSONException e) {
                return AgentToolResult.error("Error: could not serialize question payload.");
            }
        }

        if (handler == null) {
            return AgentToolResult.error(
                    "Error: no user input channel is available in this run. Proceed with the best "
                            + "safe default and state the assumption in your final answer.");
        }

        try {
            String question = normalized.optJSONObject(0).optString("question");
            JSONObject payload = new JSONObject().put("questions", normalized);
            PermissionRequest request = new PermissionRequest(
                    "user_input_" + System.currentTimeMillis(),
                    "request_user_input",
                    null,
                    payload.toString());
            PermissionDecision decision = handler.awaitDecision(request);
            if (decision == PermissionDecision.DENY) {
                return AgentToolResult.success(
                        "The user dismissed the question(s). Proceed with the best safe default "
                                + "and state the assumption in your final answer.");
            }
            String answer = handler.lastResponseText();
            if (answer == null || answer.trim().isEmpty()) {
                return AgentToolResult.success(
                        "The user approved without providing answers. Proceed with the best safe "
                                + "default and state the assumption in your final answer.");
            }
            return AgentToolResult.success("User answer: " + answer.trim());
        } catch (org.json.JSONException e) {
            return AgentToolResult.error("Error: could not build the question payload.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentToolResult.error("Error: the question was interrupted.");
        } catch (java.util.concurrent.TimeoutException e) {
            return AgentToolResult.success(
                    "The question timed out without an answer. Proceed with the best safe default "
                            + "and state the assumption in your final answer.");
        }
    }

    /** Field accessor used by tests to assert the first question text. */
    String questionText(JSONObject args) {
        JSONArray questions = args == null ? null : args.optJSONArray("questions");
        if (questions == null || questions.length() == 0) {
            return "";
        }
        JSONObject q = questions.optJSONObject(0);
        return q == null ? "" : q.optString("question", "");
    }
}