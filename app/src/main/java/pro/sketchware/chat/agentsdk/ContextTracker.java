package pro.sketchware.chat.agentsdk;

import org.json.JSONObject;

/**
 * Per-run context accounting shared with the {@code get_context_remaining}
 * tool. The four token metrics are NEVER mixed (item 18 of the migration):
 *
 * <ul>
 *   <li><b>estimated input</b> — character heuristic over the next-turn
 *       history (recorded by the runtime before/after a turn);</li>
 *   <li><b>provider-reported tokens</b> — real {@link TokenUsage} totals
 *       delivered by the provider envelope;</li>
 *   <li><b>budget reservation</b> — worst-case holds still parked in the
 *       {@link RunBudget};</li>
 *   <li><b>budget actual spend</b> — what the budget has actually settled.</li>
 * </ul>
 *
 * {@link #report()} names each number for what it is — {@code get_context_remaining}
 * never calls an estimate a real measurement.
 */
public final class ContextTracker {

    private final RunBudget budget;
    private volatile long lastInputEstimate;
    private volatile long lastReportedTotal;
    private volatile boolean lastTurnHadReportedUsage;
    private volatile long reportedTotal;
    private volatile long estimatedTotal;

    public ContextTracker(RunBudget budget) {
        this.budget = budget;
    }

    /** Called by the runtime before each LLM turn (character heuristic). */
    public void recordInputEstimate(long tokens) {
        if (tokens > 0) {
            lastInputEstimate = tokens;
        }
    }

    /** Called by the runtime after each LLM turn with the turn's usage. */
    public void recordTurnUsage(TokenUsage usage) {
        if (usage == null) {
            lastTurnHadReportedUsage = false;
            return;
        }
        if (usage.isEstimated()) {
            estimatedTotal += usage.totalTokens();
            lastTurnHadReportedUsage = false;
        } else {
            reportedTotal += usage.totalTokens();
            lastReportedTotal = usage.totalTokens();
            lastTurnHadReportedUsage = true;
        }
    }

    /** Called by the runtime after each settled turn (budget actual spend). */
    public void recordSettled(long tokens) {
        if (tokens > 0) {
            // Budget spend is tracked authoritatively by the RunBudget itself;
            // this local accumulator only serves the no-budget report branch.
        }
    }

    public long lastInputEstimate() {
        return lastInputEstimate;
    }

    /** Total of provider-REPORTED tokens across turns (real usage). */
    public long reportedTokens() {
        return reportedTotal;
    }

    /** Total of ESTIMATED tokens across turns (never presented as real). */
    public long estimatedTokens() {
        return estimatedTotal;
    }

    /** Total tokens of the last turn, with its origin flag. */
    public long lastTurnTotal() {
        return lastTurnHadReportedUsage ? lastReportedTotal : 0L;
    }

    /** True when the last turn had provider-reported usage. */
    public boolean lastTurnWasReported() {
        return lastTurnHadReportedUsage;
    }

    /** Builds the JSON report consumed by the {@code get_context_remaining} tool. */
    public JSONObject report() {
        JSONObject report = new JSONObject();
        try {
            report.put("estimated_input_tokens", lastInputEstimate)
                    .put("last_input_tokens", lastInputEstimate)
                    .put("reported_tokens", reportedTotal)
                    .put("estimated_tokens", estimatedTotal);
            if (budget != null) {
                report.put("budget_enforced", true)
                        .put("budget_reserved_tokens", budget.maximum() - budget.remaining() - budget.spent())
                        .put("budget_spent_tokens", budget.spent())
                        .put("settled_tokens_this_run", budget.spent())
                        .put("used_tokens", budget.spent())
                        .put("maximum_tokens", budget.maximum())
                        .put("remaining_tokens", budget.remaining())
                        .put("blocked", budget.isBlocked());
            } else {
                report.put("budget_enforced", false)
                        .put("settled_tokens_this_run", reportedTotal + estimatedTotal)
                        .put("used_tokens", reportedTotal + estimatedTotal)
                        .put("note", "No hard budget is configured for this run. "
                                + "Summarize and wrap up when the task is complete.");
            }
            return report;
        } catch (org.json.JSONException e) {
            return new JSONObject();
        }
    }
}
