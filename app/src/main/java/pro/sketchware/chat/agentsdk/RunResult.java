package pro.sketchware.chat.agentsdk;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of an {@link AgentRuntime} run, mirroring
 * {@code RunnerResult/RunResult} from openai-agents-js.
 */
public final class RunResult {

    enum Status { SUCCESS, MAX_TURNS, GUARDRAIL_BLOCKED, FAILED }

    private final Status status;
    private final String output;
    private final String failureReason;
    private final GuardrailResult guardrail;
    private final List<String> handoffTrail;
    private final RunContext context;

    private RunResult(Status status, String output, String failureReason,
                      GuardrailResult guardrail, List<String> handoffTrail,
                      RunContext context) {
        this.status = status;
        this.output = output == null ? "" : output;
        this.failureReason = failureReason == null ? "" : failureReason;
        this.guardrail = guardrail;
        this.handoffTrail = handoffTrail == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(handoffTrail);
        this.context = context;
    }

    static RunResult success(String output, RunContext context) {
        return new RunResult(Status.SUCCESS, output, "", null, context.handoffTrail(), context);
    }

    static RunResult maxTurnsReached(RunContext context, String partialOutput) {
        return new RunResult(Status.MAX_TURNS, partialOutput,
                "Max turns reached.", null, context.handoffTrail(), context);
    }

    static RunResult blockedByGuardrail(GuardrailResult result) {
        return new RunResult(Status.GUARDRAIL_BLOCKED, "", "",
                result, Collections.emptyList(), null);
    }

    static RunResult failure(String reason) {
        return new RunResult(Status.FAILED, "", reason, null, Collections.emptyList(), null);
    }

    /**
     * The {@link RunContext} of the run, when one was created (guardrail
     * blocks and pre-context failures return {@code null}). Hosts use it to
     * persist the {@link TaskMemory} after the run ends.
     */
    public RunContext context() {
        return context;
    }

    public boolean isSuccessful() {
        return status == Status.SUCCESS;
    }

    public boolean isBlockedByGuardrail() {
        return status == Status.GUARDRAIL_BLOCKED;
    }

    public boolean isMaxTurnsReached() {
        return status == Status.MAX_TURNS;
    }

    /** Final assistant text (also populated for partial max-turn runs). */
    public String getOutput() {
        return output;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public GuardrailResult getGuardrailResult() {
        return guardrail;
    }

    /** Ordered trail of handoffs that happened during the run. */
    public List<String> getHandoffTrail() {
        return handoffTrail;
    }
}
