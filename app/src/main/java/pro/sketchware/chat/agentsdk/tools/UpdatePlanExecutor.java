package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.ChatPlanManager;
import pro.sketchware.chat.agentsdk.AgentToolResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Core {@code update_plan} executor implementing the Codex structured contract
 * (reference {@code plan_spec.rs}): exactly
 *
 * <pre>
 * {
 *   "explanation"?: string,
 *   "plan": [{"step": string, "status": "pending"|"in_progress"|"completed"}]
 * }       // "plan" required
 * </pre>
 *
 * converted into {@link ChatPlanManager} tasks so the UI surfaces the same
 * model-maintained plan that Codex keeps. At most one step may be in_progress
 * (Codex enforces the same invariant).
 */
final class UpdatePlanExecutor implements ToolExecutor {

    @Override
    public AgentToolResult execute(ToolExecutionContext ctx) {
        JSONObject args = ctx.functionArguments();
        JSONArray planArr = args == null ? null : args.optJSONArray("plan");
        if (planArr == null) {
            return AgentToolResult.error(
                    "Error: invalid update_plan arguments; the required 'plan' array is missing "
                            + "(each item needs a 'step' string and a 'status' of "
                            + "pending|in_progress|completed).");
        }
        if (planArr.length() == 0) {
            return AgentToolResult.error("Error: 'plan' must contain at least one step.");
        }

        String explanation = args != null ? args.optString("explanation", "") : "";
        List<ChatPlanManager.Task> tasks = new ArrayList<>();
        int inProgress = 0;
        for (int i = 0; i < planArr.length(); i++) {
            JSONObject item = planArr.optJSONObject(i);
            if (item == null) {
                return AgentToolResult.error("Error: plan item " + i + " is not an object.");
            }
            String step = item.optString("step", "").trim();
            String rawStatus = item.optString("status", "").trim();
            if (step.isEmpty()) {
                return AgentToolResult.error("Error: plan item " + i + " has no 'step' text.");
            }
            ToolStatus status = parseStatus(rawStatus);
            if (status == ToolStatus.INVALID) {
                return AgentToolResult.error(
                        "Error: plan item " + i + " has unknown 'status' '" + rawStatus
                                + "' (expected pending|in_progress|completed).");
            }
            if (status == ToolStatus.IN_PROGRESS) {
                inProgress++;
                if (inProgress > 1) {
                    return AgentToolResult.error(
                            "Error: at most one plan step can be in_progress at a time.");
                }
            }
            tasks.add(new ChatPlanManager.Task(step, "", mapStatus(status)));
        }

        ChatPlanManager.setModelPlan(ctx.scId(), tasks);
        try {
            return AgentToolResult.success(new JSONObject()
                    .put("message", "Plan updated with " + tasks.size() + " step(s).")
                    .toString());
        } catch (org.json.JSONException e) {
            return AgentToolResult.error("Error: could not serialize plan response.");
        }
    }

    private enum ToolStatus {
        PENDING, IN_PROGRESS, COMPLETED, INVALID
    }

    private static ToolStatus parseStatus(String raw) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "pending":
                return ToolStatus.PENDING;
            case "in_progress":
                return ToolStatus.IN_PROGRESS;
            case "completed":
                return ToolStatus.COMPLETED;
            default:
                return ToolStatus.INVALID;
        }
    }

    /** Maps the Codex plan status enum to internal task status. */
    static int mapStatus(ToolStatus status) {
        switch (status) {
            case IN_PROGRESS:
                return ChatPlanManager.STATUS_RUNNING;
            case COMPLETED:
                return ChatPlanManager.STATUS_DONE;
            default:
                return ChatPlanManager.STATUS_PENDING;
        }
    }
}