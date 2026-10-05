package pro.sketchware.chat;


import pro.sketchware.R;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.chat.port.VoidPortScmService;
import pro.sketchware.chat.agent.TaskPlanner;

public final class ChatPlanManager {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_FAILED = 3;

    private ChatPlanManager() {
    }

    public static final class Task {
        public final String title;
        public final String detail;
        public final int status;

        public Task(String title, String detail, int status) {
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
            this.status = status;
        }
    }

    /**
     * Plans provided by the model itself via the {@code update_plan} tool,
     * keyed by project id. When present they replace the heuristic plan below,
     * mirroring how Codex keeps an explicit, model-maintained task plan.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, List<Task>> MODEL_PLANS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, TaskPlanner.Plan> EXECUTION_PLANS =
            new java.util.concurrent.ConcurrentHashMap<>();


    public static void setExecutionPlan(String scId, TaskPlanner.Plan plan) {
        if (scId == null) {
            return;
        }
        if (plan == null) {
            EXECUTION_PLANS.remove(scId);
        } else {
            EXECUTION_PLANS.put(scId, plan);
        }
    }

    public static void clearExecutionPlan(String scId) {
        if (scId != null) {
            EXECUTION_PLANS.remove(scId);
        }
    }

    public static void setModelPlan(String scId, List<Task> tasks) {
        if (scId == null) {
            return;
        }
        if (tasks == null || tasks.isEmpty()) {
            MODEL_PLANS.remove(scId);
        } else {
            MODEL_PLANS.put(scId, new ArrayList<>(tasks));
        }
    }

    public static void clearModelPlan(String scId) {
        if (scId != null) {
            MODEL_PLANS.remove(scId);
        }
    }

    public static List<Task> buildPlan(Context context, String scId, List<ChatMessage> messages, boolean processing, String statusText) {
        List<Task> modelPlan = scId == null ? null : MODEL_PLANS.get(scId);
        if (modelPlan != null && !modelPlan.isEmpty()) {
            return new ArrayList<>(modelPlan);
        }
        TaskPlanner.Plan executionPlan = scId == null ? null : EXECUTION_PLANS.get(scId);
        if (executionPlan != null) {
            return buildExecutionPlan(context, executionPlan, messages, processing, statusText);
        }
        List<Task> tasks = new ArrayList<>();
        int latestUserIndex = latestUserIndex(messages);
        if (latestUserIndex < 0) {
            tasks.add(new Task(context.getString(R.string.chat_plan_request_title), context.getString(R.string.chat_plan_start_message), STATUS_PENDING));
            tasks.add(new Task(context.getString(R.string.chat_plan_context_title), context.getString(R.string.chat_plan_context_message), STATUS_PENDING));
            return tasks;
        }

        int toolCount = 0;
        int runningTools = 0;
        int errorTools = 0;
        boolean hasAssistantAfterUser = false;
        for (int i = latestUserIndex + 1; messages != null && i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message == null) {
                continue;
            }
            if (message.isTool()) {
                toolCount++;
                if (message.isToolRunning()) {
                    runningTools++;
                }
                if (message.isToolError() || message.isRejected()) {
                    errorTools++;
                }
            }
            if (message.isBot() && (message.hasMessageContent() || message.hasReasoningContent())) {
                hasAssistantAfterUser = true;
            }
        }

        int changedFiles = VoidPortScmService.changedFileCount(scId);
        String safeStatus = statusText == null ? "" : statusText.trim();
        tasks.add(new Task(context.getString(R.string.chat_plan_request_title), compactUserText(context, messages.get(latestUserIndex)), STATUS_DONE));
        if (toolCount > 0 || processing) {
            tasks.add(new Task(context.getString(R.string.chat_plan_execution_title), toolCount > 0
                    ? ChatToolActivitySummary.summarize(messages).compactLabel(context)
                    : (safeStatus.isEmpty() ? context.getString(R.string.chat_plan_executing) : safeStatus),
                    runningTools > 0 || processing ? STATUS_RUNNING
                            : (errorTools > 0 ? STATUS_FAILED : STATUS_DONE)));
        }
        if (changedFiles > 0) {
            tasks.add(new Task(context.getString(R.string.chat_plan_changes_title), context.getString(R.string.chat_plan_changed_files, changedFiles), STATUS_DONE));
        }
        tasks.add(new Task(context.getString(R.string.chat_plan_finalization_title), errorTools > 0
                ? context.getString(R.string.chat_plan_tools_errors, errorTools)
                : (hasAssistantAfterUser ? context.getString(R.string.chat_plan_response_generated) : (safeStatus.isEmpty() ? context.getString(R.string.chat_plan_waiting_completion) : safeStatus)),
                processing ? STATUS_RUNNING : (hasAssistantAfterUser ? STATUS_DONE : STATUS_PENDING)));
        return tasks;
    }

    private static List<Task> buildExecutionPlan(Context context, TaskPlanner.Plan plan,
                                                     List<ChatMessage> messages,
                                                     boolean processing,
                                                     String statusText) {
        List<Task> tasks = new ArrayList<>();
        int latestUser = latestUserIndex(messages);
        tasks.add(new Task(context.getString(R.string.chat_plan_request_title),
                latestUser >= 0 ? compactUserText(context, messages.get(latestUser)) : plan.getObjective(),
                STATUS_DONE));

        for (TaskPlanner.Step step : plan.getSteps()) {
            String detail = step.getExpectedTools().isEmpty()
                    ? step.getDescription()
                    : step.getDescription() + " • " + context.getString(R.string.chat_plan_tools_suffix,
                            String.join(", ", step.getExpectedTools()));
            if (step.isCritical()) {
                detail += " • " + context.getString(R.string.chat_plan_required_step);
            }
            tasks.add(new Task(step.getDescription(), detail, mapStatus(step.getStatus())));
        }

        int remainingCritical = Math.max(0,
                plan.getCriticalSteps() - plan.getCompletedCriticalSteps());
        String safeStatus = statusText == null ? "" : statusText.trim();
        int finalStatus = plan.isComplete()
                ? (processing ? STATUS_RUNNING : STATUS_DONE)
                : STATUS_PENDING;
        String finalDetail;
        if (plan.isComplete()) {
            finalDetail = processing ? context.getString(R.string.chat_plan_complete_preparing)
                    : context.getString(R.string.chat_plan_all_required_complete);
        } else if (remainingCritical > 0) {
            finalDetail = context.getString(R.string.chat_plan_remaining_required, remainingCritical);
        } else {
            finalDetail = safeStatus.isEmpty() ? context.getString(R.string.chat_plan_waiting_completion_period) : safeStatus;
        }
        tasks.add(new Task(context.getString(R.string.chat_plan_finalization_title), finalDetail, finalStatus));
        return tasks;
    }

    private static int mapStatus(TaskPlanner.StepStatus status) {
        if (status == null) {
            return STATUS_PENDING;
        }
        return switch (status) {
            case COMPLETED, SKIPPED -> STATUS_DONE;
            case IN_PROGRESS -> STATUS_RUNNING;
            case FAILED -> STATUS_FAILED;
            default -> STATUS_PENDING;
        };
    }

    private static int latestUserIndex(List<ChatMessage> messages) {
        if (messages == null) {
            return -1;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isUser()) {
                return i;
            }
        }
        return -1;
    }

    private static String compactUserText(Context context, ChatMessage message) {
        String text = message == null ? "" : message.getMessage();
        if (!ChatMessage.hasVisibleText(text)) {
            return context.getString(R.string.chat_plan_attachment_message);
        }
        String trimmed = text.trim().replace('\n', ' ');
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }
}
