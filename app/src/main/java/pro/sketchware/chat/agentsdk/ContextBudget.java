package pro.sketchware.chat.agentsdk;

import java.util.ArrayList;
import java.util.List;

/**
 * THE single source of truth for context budget calculation (item 19 of the
 * migration). Every consumer — {@code ContextBuilder} (provider envelopes),
 * the host loop's compaction trigger, telemetry — derives its numbers from
 * THIS class; there is no second set of independent constants.
 *
 * <p>Semantics: {@code totalTokens} is the overall envelope and each
 * category is a per-section cap. When the caps sum above the envelope,
 * contention is resolved by priority (system → recent turns → tool results →
 * relevant files → summary), mirroring
 * {@code ContextBuilder.composePrioritizedSections} — it is not a build
 * error, but {@link #envelopeViolations()} exposes any violation for tests
 * and telemetry.</p>
 *
 * <p>{@link #forWindow(int, int, int)} is the canonical model-aware
 * calculator used by {@code ContextBuilder.configureBudgets}.</p>
 */
public final class ContextBudget {

    /** Floors shared with every consumer (no private duplicates elsewhere). */
    public static final int MIN_TOTAL_TOKENS = 6_000;
    public static final int MIN_HISTORY_BUDGET_TOKENS = 1_000;
    public static final int MIN_COMPILE_ERROR_TOKENS = 500;
    public static final int MAX_TOTAL_TOKENS = 128_000;

    private final int totalTokens;
    private final int systemTokens;
    private final int relevantFilesTokens;
    private final int recentHistoryTokens;
    private final int toolResultTokens;
    private final int summaryTokens;
    private final int compileErrorTokens;

    private ContextBudget(Builder b) {
        this.totalTokens = b.totalTokens;
        this.systemTokens = b.systemTokens;
        this.relevantFilesTokens = b.relevantFilesTokens;
        this.recentHistoryTokens = b.recentHistoryTokens;
        this.toolResultTokens = b.toolResultTokens;
        this.summaryTokens = b.summaryTokens;
        this.compileErrorTokens = b.compileErrorTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }

    public int systemTokens() {
        return systemTokens;
    }

    public int relevantFilesTokens() {
        return relevantFilesTokens;
    }

    public int recentHistoryTokens() {
        return recentHistoryTokens;
    }

    public int toolResultTokens() {
        return toolResultTokens;
    }

    public int summaryTokens() {
        return summaryTokens;
    }

    public int compileErrorTokens() {
        return compileErrorTokens;
    }

    /** Sums every category; useful for invariant checks and telemetry. */
    public int allocatedTokens() {
        return systemTokens + relevantFilesTokens + recentHistoryTokens
                + toolResultTokens + summaryTokens + compileErrorTokens;
    }

    /**
     * Categories whose caps exceed the total envelope (empty when the budget
     * is coherent). Tests assert this stays empty for every budget the app
     * can build.
     */
    public List<String> envelopeViolations() {
        List<String> violations = new ArrayList<>();
        if (systemTokens > totalTokens) violations.add("system");
        if (recentHistoryTokens > totalTokens) violations.add("history");
        if (toolResultTokens > totalTokens) violations.add("toolResults");
        if (relevantFilesTokens > totalTokens) violations.add("relevantFiles");
        if (summaryTokens > totalTokens) violations.add("summary");
        if (compileErrorTokens > totalTokens) violations.add("compileErrors");
        return violations;
    }

    /** Default budget mirroring today's constants. */
    public static ContextBudget defaults() {
        return builder().build();
    }

    /**
     * Canonical model-aware calculator (single implementation of the old
     * {@code ContextBuilder.configureBudgets} formula):
     *
     * <pre>
     * total  = clamp(contextWindow − reservedOutput, 6000..128000)
     * system = clamp(total/4, 2400..16000)
     * compileError = clamp(system/6, 500..2000)
     * history = max(1000, total − system − compileError − additionalInput)
     * </pre>
     *
     * {@code toolResult}/{@code relevantFiles}/{@code summary} keep their
     * reference proportions of the 6k budget, scaled with the total.
     */
    public static ContextBudget forWindow(int contextWindow, int reservedOutputTokens,
                                          int additionalInputTokens) {
        int total = Math.max(MIN_TOTAL_TOKENS,
                Math.min(MAX_TOTAL_TOKENS, contextWindow - Math.max(0, reservedOutputTokens)));
        int system = Math.max(2_400, Math.min(16_000, total / 4));
        int compileError = Math.max(MIN_COMPILE_ERROR_TOKENS, Math.min(2_000, system / 6));
        int history = Math.max(MIN_HISTORY_BUDGET_TOKENS,
                total - system - compileError - Math.max(0, additionalInputTokens));
        double scale = total / 6_000.0;
        int relevantFiles = (int) Math.min(Integer.MAX_VALUE, 1_500 * scale);
        int toolResults = (int) Math.min(Integer.MAX_VALUE, 1_000 * scale);
        int summary = (int) Math.min(Integer.MAX_VALUE, 500 * scale);
        return builder()
                .totalTokens(total)
                .systemTokens(system)
                .compileErrorTokens(compileError)
                .recentHistoryTokens(history)
                .relevantFilesTokens(relevantFiles)
                .toolResultTokens(toolResults)
                .summaryTokens(summary)
                .build();
    }

    /**
     * Scales every category proportionally to {@code maxModelTokens} while
     * keeping the current category ratios at the reference budget (6k total).
     */
    public static ContextBudget forModel(int maxModelTokens) {
        ContextBudget reference = defaults();
        if (maxModelTokens <= 0) {
            return reference;
        }
        int ceiling = Math.min(maxModelTokens, 128_000);
        if (ceiling <= reference.totalTokens) {
            return reference;
        }
        double scale = (double) ceiling / reference.totalTokens();
        // Cap growth so a big-context model never drowns the prompt in stale history.
        double bounded = Math.min(scale, 8.0);
        return builder()
                .totalTokens((int) (reference.totalTokens * bounded))
                .systemTokens((int) (reference.systemTokens * bounded))
                .relevantFilesTokens((int) (reference.relevantFilesTokens * bounded))
                .recentHistoryTokens((int) (reference.recentHistoryTokens * bounded))
                .toolResultTokens((int) (reference.toolResultTokens * bounded))
                .summaryTokens((int) (reference.summaryTokens * bounded))
                .compileErrorTokens((int) (reference.compileErrorTokens * bounded))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int totalTokens = 6_000;
        private int systemTokens = 2_400;
        private int relevantFilesTokens = 1_500;
        private int recentHistoryTokens = 3_000;
        private int toolResultTokens = 1_000;
        private int summaryTokens = 500;
        private int compileErrorTokens = 500;

        public Builder totalTokens(int v) {
            this.totalTokens = v;
            return this;
        }

        public Builder systemTokens(int v) {
            this.systemTokens = v;
            return this;
        }

        public Builder relevantFilesTokens(int v) {
            this.relevantFilesTokens = v;
            return this;
        }

        public Builder recentHistoryTokens(int v) {
            this.recentHistoryTokens = v;
            return this;
        }

        public Builder toolResultTokens(int v) {
            this.toolResultTokens = v;
            return this;
        }

        public Builder summaryTokens(int v) {
            this.summaryTokens = v;
            return this;
        }

        public Builder compileErrorTokens(int v) {
            this.compileErrorTokens = v;
            return this;
        }

        public ContextBudget build() {
            if (totalTokens <= 0 || systemTokens < 0 || relevantFilesTokens < 0
                    || recentHistoryTokens < 0 || toolResultTokens < 0 || summaryTokens < 0
                    || compileErrorTokens < 0) {
                throw new IllegalArgumentException(
                        "Budgets must be positive (total) and non-negative (categories)");
            }
            return new ContextBudget(this);
        }
    }
}
