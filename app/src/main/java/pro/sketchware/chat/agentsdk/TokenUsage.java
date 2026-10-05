package pro.sketchware.chat.agentsdk;

/**
 * Structured token usage of one LLM turn (Codex {@code TokenUsage} parity).
 *
 * <p>Two strictly distinct origins:</p>
 * <ul>
 *   <li><b>provider-reported</b> ({@code estimated == false}): the counts come
 *       from the provider envelope ({@code usage} / {@code usageMetadata});
 *       this is REAL usage and is the only input that settles the
 *       {@link RunBudget} as truth.</li>
 *   <li><b>estimated</b> ({@code estimated == true}): the provider did not
 *       report usage and the runtime fell back to its character heuristic.
 *       An estimate is NEVER called real usage.</li>
 * </ul>
 *
 * <p>Pure JVM, immutable, unit-testable without Android or network.</p>
 */
public final class TokenUsage {

    private final long inputTokens;
    private final long outputTokens;
    private final long cachedInputTokens;
    private final long reasoningTokens;
    private final boolean estimated;

    private TokenUsage(Builder builder) {
        this.inputTokens = Math.max(0, builder.inputTokens);
        this.outputTokens = Math.max(0, builder.outputTokens);
        this.cachedInputTokens = Math.max(0, builder.cachedInputTokens);
        this.reasoningTokens = Math.max(0, builder.reasoningTokens);
        this.estimated = builder.estimated;
    }

    /** Prompt tokens, including (not on top of) cached input when reported together. */
    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
    }

    public long cachedInputTokens() {
        return cachedInputTokens;
    }

    public long reasoningTokens() {
        return reasoningTokens;
    }

    /** True when the values are a local estimate, not a provider report. */
    public boolean isEstimated() {
        return estimated;
    }

    /** Total billed tokens: input + output (cached input already inside input). */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    /** Provider-reported usage. */
    public static TokenUsage reported(long inputTokens, long outputTokens) {
        return builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
    }

    /** Provider-reported usage with cache/reasoning detail. */
    public static TokenUsage reportedDetailed(long inputTokens, long outputTokens,
                                              long cachedInputTokens, long reasoningTokens) {
        return builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .cachedInputTokens(cachedInputTokens)
                .reasoningTokens(reasoningTokens)
                .build();
    }

    /** Local character-heuristic estimate — never presented as real usage. */
    public static TokenUsage estimated(long inputTokens, long outputTokens) {
        return builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimated(true)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long inputTokens;
        private long outputTokens;
        private long cachedInputTokens;
        private long reasoningTokens;
        private boolean estimated;

        public Builder inputTokens(long v) {
            this.inputTokens = v;
            return this;
        }

        public Builder outputTokens(long v) {
            this.outputTokens = v;
            return this;
        }

        public Builder cachedInputTokens(long v) {
            this.cachedInputTokens = v;
            return this;
        }

        public Builder reasoningTokens(long v) {
            this.reasoningTokens = v;
            return this;
        }

        public Builder estimated(boolean v) {
            this.estimated = v;
            return this;
        }

        public TokenUsage build() {
            return new TokenUsage(this);
        }
    }

    @Override
    public String toString() {
        return "TokenUsage{input=" + inputTokens + ", output=" + outputTokens
                + ", cached=" + cachedInputTokens + ", reasoning=" + reasoningTokens
                + ", estimated=" + estimated + "}";
    }
}
