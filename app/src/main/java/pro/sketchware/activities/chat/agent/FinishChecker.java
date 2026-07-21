package pro.sketchware.activities.chat.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Validates completion using only state from the current agent run. Historical
 * tool calls from older user turns must never satisfy the current request.
 */
public final class FinishChecker {

    private FinishChecker() {
    }

    public static final class ValidationResult {
        private final boolean canFinish;
        private final String reason;
        private final String feedbackPrompt;
        private final List<String> missingActions;

        private ValidationResult(boolean canFinish, String reason, String feedbackPrompt,
                                 @Nullable List<String> missingActions) {
            this.canFinish = canFinish;
            this.reason = reason;
            this.feedbackPrompt = feedbackPrompt;
            this.missingActions = missingActions == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(missingActions));
        }

        @NonNull
        public static ValidationResult allowed() {
            return new ValidationResult(true, "Completion requirements satisfied", "", null);
        }

        @NonNull
        public static ValidationResult cannotFinish(@NonNull String reason,
                                                    @NonNull String feedbackPrompt,
                                                    @Nullable List<String> missingActions) {
            return new ValidationResult(false, reason, feedbackPrompt, missingActions);
        }

        public boolean canFinish() {
            return canFinish;
        }

        @NonNull
        public String getReason() {
            return reason;
        }

        @NonNull
        public String getFeedbackPrompt() {
            return feedbackPrompt;
        }

        @NonNull
        public List<String> getMissingActions() {
            return missingActions;
        }
    }

    @NonNull
    public static ValidationResult validate(@Nullable AgentMemory memory,
                                           @Nullable PatternMatcher.Result pattern,
                                           @Nullable TaskPlanner.Plan plan,
                                           @NonNull List<ToolSequenceValidator.ToolUsage> currentRunTools,
                                           @Nullable String lastAssistantResponse,
                                           @NonNull String chatMode) {
        if (!"agent".equalsIgnoreCase(chatMode)) {
            return ValidationResult.allowed();
        }

        PatternMatcher.Result effectivePattern = pattern;
        if (effectivePattern == null && memory != null) {
            effectivePattern = PatternMatcher.analyze(
                    memory.getOriginalUserMessage(), null, memory.getOriginalSelections());
        }
        if (effectivePattern == null || effectivePattern.isChatOnly()) {
            return ValidationResult.allowed();
        }

        List<String> incompleteSteps = incompleteCriticalSteps(plan);
        if (!incompleteSteps.isEmpty()) {
            return ValidationResult.cannotFinish(
                    "Critical plan steps remain",
                    listFeedback("The task is not complete. Continue with these steps:", incompleteSteps),
                    incompleteSteps);
        }

        List<String> unusedTools = unusedRequiredTools(
                effectivePattern.getRequiredTools(), currentRunTools);
        if (!unusedTools.isEmpty()) {
            return ValidationResult.cannotFinish(
                    "Required workspace actions were not executed",
                    listFeedback("Use the required workspace tools before finishing:", unusedTools),
                    unusedTools);
        }

        if (effectivePattern.hasRequiredTools() && !hasSuccessfulTool(currentRunTools)) {
            return ValidationResult.cannotFinish(
                    "The response is text-only for an actionable request",
                    "The user requested workspace-dependent work. Inspect or change the workspace with the appropriate tools before providing a final answer.",
                    Collections.singletonList("Execute the requested workspace action"));
        }

        if (memory != null) {
            List<String> unaccessedFiles = unaccessedKeyFiles(memory.getKeyFiles(), currentRunTools);
            if (!unaccessedFiles.isEmpty()) {
                return ValidationResult.cannotFinish(
                        "Referenced files were not accessed in this run",
                        listFeedback("Access these referenced files before finishing:", unaccessedFiles),
                        unaccessedFiles);
            }
        }

        if (effectivePattern.hasRequiredTools()
                && !hasSuccessfulTool(currentRunTools)
                && describesFutureAction(lastAssistantResponse)) {
            return ValidationResult.cannotFinish(
                    "The assistant described future work without executing it",
                    "Do not describe what you will do. Use the available tools to do it, then report the verified result.",
                    Collections.singletonList("Execute instead of promising"));
        }

        return ValidationResult.allowed();
    }

    @NonNull
    private static List<String> incompleteCriticalSteps(@Nullable TaskPlanner.Plan plan) {
        if (plan == null) {
            return Collections.emptyList();
        }
        List<String> incomplete = new ArrayList<>();
        for (TaskPlanner.Step step : plan.getSteps()) {
            if (step.isCritical() && !step.isCompleted() && !step.isSkipped()) {
                incomplete.add(step.getDescription());
            }
        }
        return incomplete;
    }

    @NonNull
    private static List<String> unusedRequiredTools(
            @NonNull List<String> requiredTools,
            @NonNull List<ToolSequenceValidator.ToolUsage> usages) {
        List<String> missing = new ArrayList<>();
        for (String required : requiredTools) {
            if (!isRequirementSatisfied(required, usages)) {
                missing.add(describeRequirement(required));
            }
        }
        return missing;
    }

    @NonNull
    private static String describeRequirement(@NonNull String required) {
        if (PatternMatcher.PROJECT_DISCOVERY_REQUIREMENT.equals(required)) {
            return "Inspect the project with ls_dir, get_dir_tree, "
                    + "search_pathnames_only, or search_for_files";
        }
        return required;
    }

    private static boolean isRequirementSatisfied(
            @NonNull String required,
            @NonNull List<ToolSequenceValidator.ToolUsage> usages) {
        for (ToolSequenceValidator.ToolUsage usage : usages) {
            if (!usage.wasSuccessful()) {
                continue;
            }
            String used = usage.getToolName();
            if (required.equals(used)) {
                return true;
            }
            if (PatternMatcher.PROJECT_DISCOVERY_REQUIREMENT.equals(required)
                    && PatternMatcher.isProjectDiscoveryTool(used)) {
                return true;
            }
            if ("search_for_files".equals(required)
                    && ("search_pathnames_only".equals(used) || "get_dir_tree".equals(used))) {
                return true;
            }
            if ("search_pathnames_only".equals(required)
                    && "search_for_files".equals(used)) {
                return true;
            }
            if ("edit_file".equals(required) && "rewrite_file".equals(used)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSuccessfulTool(
            @NonNull List<ToolSequenceValidator.ToolUsage> usages) {
        for (ToolSequenceValidator.ToolUsage usage : usages) {
            if (usage.wasSuccessful()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static List<String> unaccessedKeyFiles(
            @NonNull List<String> keyFiles,
            @NonNull List<ToolSequenceValidator.ToolUsage> usages) {
        List<String> missing = new ArrayList<>();
        for (String keyFile : keyFiles) {
            String normalized = normalizePath(keyFile);
            boolean accessed = false;
            for (ToolSequenceValidator.ToolUsage usage : usages) {
                if (!usage.wasSuccessful() || !isFileAccessTool(usage.getToolName())) {
                    continue;
                }
                if (normalizePath(usage.getArgs()).contains(normalized)) {
                    accessed = true;
                    break;
                }
            }
            if (!accessed) {
                missing.add(keyFile);
            }
        }
        return missing;
    }

    private static boolean isFileAccessTool(@NonNull String toolName) {
        return "read_file".equals(toolName)
                || "edit_file".equals(toolName)
                || "rewrite_file".equals(toolName)
                || "delete_file_or_folder".equals(toolName);
    }

    private static boolean describesFutureAction(@Nullable String text) {
        if (text == null) {
            return false;
        }
        String value = text.toLowerCase(Locale.ROOT);
        return value.matches("(?s).*\\b(i will|i'll|let me|i am going to|vou fazer|vou editar|vou criar|deixe-me)\\b.*");
    }

    @NonNull
    private static String normalizePath(@NonNull String value) {
        return value.replace('\\', '/').toLowerCase(Locale.ROOT).trim();
    }

    @NonNull
    private static String listFeedback(@NonNull String header, @NonNull List<String> items) {
        StringBuilder builder = new StringBuilder(header);
        for (String item : items) {
            builder.append("\n- ").append(item);
        }
        return builder.toString();
    }
}
