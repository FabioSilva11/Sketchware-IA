package pro.sketchware.chat.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a chat turn benefits from the specialist orchestration.
 *
 * <p>The policy intentionally combines explicit user intent with the structural
 * hints produced by {@link PatternMatcher}. The old decision only considered
 * very long prompts and a small keyword list, so ordinary requests such as
 * "corrija os erros do meu app" almost never reached the multi-agent path.</p>
 */
public final class MultiAgentPolicy {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_ALWAYS = "always";
    public static final String MODE_OFF = "off";

    private static final Pattern EXPLICIT_MULTI_AGENT = Pattern.compile(
            "(?s).*(multi[ -]?agent|multiagente|varios agentes|equipe de agentes|"
                    + "time de agentes|multiple agents|agent team).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLEX_SCOPE = Pattern.compile(
            "(?s).*(auditoria|audit|arquitetura|architecture|migracao|migration|"
                    + "refatoracao ampla|large refactor|varios subsistemas|multiple subsystems|"
                    + "varios arquivos|multiple files|exaustiv|exhaustive|minucios|comprehensive).*",
            Pattern.CASE_INSENSITIVE);

    private MultiAgentPolicy() {
    }

    public static final class Decision {
        private final boolean enabled;
        private final String reason;

        private Decision(boolean enabled, @NonNull String reason) {
            this.enabled = enabled;
            this.reason = reason;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @NonNull
        public String getReason() {
            return reason;
        }
    }

    /** Makes the initial decision before the implementer starts inspecting files. */
    @NonNull
    public static Decision decide(@Nullable String configuredMode,
                                  @Nullable PatternMatcher.Result pattern,
                                  @Nullable String userText) {
        String mode = normalizeMode(configuredMode);
        String text = normalizeText(userText);

        // A user-selected Off state is authoritative, even if a prompt mentions
        // multiple agents. They can switch it back from the chat mode menu.
        if (MODE_OFF.equals(mode)) {
            return disabled("disabled_by_user");
        }

        // Evaluate explicit intent before isChatOnly(). A short prompt such as
        // "use multiagente" used to be classified UNKNOWN and rejected first.
        if (EXPLICIT_MULTI_AGENT.matcher(text).matches()) {
            return enabled("explicit_request");
        }

        if (pattern == null || pattern.isChatOnly()) {
            return disabled("chat_only");
        }
        if (MODE_ALWAYS.equals(mode)) {
            return enabled("always_enabled");
        }

        int fileCount = pattern.getExtractedFilePaths().size();
        if (fileCount >= 2) {
            return enabled("multiple_files");
        }
        if (text.length() >= 450) {
            return enabled("long_complex_request");
        }
        if (COMPLEX_SCOPE.matcher(text).matches()) {
            return enabled("complex_scope");
        }

        PatternMatcher.RequestType type = pattern.getPrimaryType();
        if (type == PatternMatcher.RequestType.REFACTOR) {
            return enabled("refactor_request");
        }
        // A broad bug fix has no explicit file because discovering the affected
        // surface is part of the work. This is exactly where planner + architect
        // provide value. A named one-file edit remains on the faster main agent.
        if (type == PatternMatcher.RequestType.FIX_BUG && fileCount == 0) {
            return enabled("broad_bug_fix");
        }

        int toolHints = pattern.getRequiredTools().size() + pattern.getOptionalTools().size();
        if (pattern.requiresProjectExploration()
                && type == PatternMatcher.RequestType.GENERAL_CODING
                && (text.length() >= 80 || toolHints >= 3)) {
            return enabled("broad_implementation");
        }
        if (pattern.requiresProjectExploration()
                && type == PatternMatcher.RequestType.ANALYZE_CODE
                && (text.length() >= 120 || toolHints >= 3)) {
            return enabled("broad_analysis");
        }
        if (pattern.getSecondaryTypes().size() >= 2 || toolHints >= 4) {
            return enabled("multiple_operations");
        }
        return disabled("simple_request");
    }

    /**
     * Reconsiders an Auto decision after the main agent has explored the project.
     * This covers short requests whose real scope only becomes clear after reads.
     */
    @NonNull
    public static Decision reconsiderAfterInspection(
            @Nullable String configuredMode,
            @Nullable PatternMatcher.Result pattern,
            int loopStep,
            @Nullable List<ToolSequenceValidator.ToolUsage> usages) {
        if (!MODE_AUTO.equals(normalizeMode(configuredMode))) {
            return disabled("automatic_escalation_not_allowed");
        }
        if (pattern == null || pattern.isChatOnly()) {
            return disabled("chat_only");
        }
        int toolCount = usages == null ? 0 : usages.size();
        if (pattern.requiresProjectExploration() && toolCount >= 2) {
            return enabled("complexity_found_during_inspection");
        }
        if (loopStep >= 2 && toolCount >= 3) {
            return enabled("multiple_tool_rounds");
        }
        return disabled("not_complex_yet");
    }

    @NonNull
    public static String normalizeMode(@Nullable String mode) {
        if (mode == null) {
            return MODE_AUTO;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (MODE_ALWAYS.equals(normalized) || MODE_OFF.equals(normalized)) {
            return normalized;
        }
        return MODE_AUTO;
    }

    @NonNull
    private static String normalizeText(@Nullable String value) {
        String safe = value == null ? "" : value;
        return Normalizer.normalize(safe, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static Decision enabled(String reason) {
        return new Decision(true, reason);
    }

    private static Decision disabled(String reason) {
        return new Decision(false, reason);
    }
}
