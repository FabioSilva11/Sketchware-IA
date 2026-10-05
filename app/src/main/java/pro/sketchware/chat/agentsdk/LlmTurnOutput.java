package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.toolcalling.ToolCall;

import java.util.Collections;
import java.util.List;

/**
 * Immutable parsed output of one LLM turn (Codex turn-structure parity):
 * assistant message + structured tool calls + finish reason + token usage.
 *
 * <p>Two contracts enforced here:</p>
 * <ul>
 *   <li><b>textStreamed</b> — transport state: {@code content} was ALREADY
 *       delivered to the UI incrementally through the gateway's delta
 *       listener. The runtime then treats the final content as completion
 *       state only — never re-publishing it as a new
 *       {@code AssistantMessageDelta} (single-display rule).</li>
 *   <li><b>usage</b> — real token accounting: provider-reported
 *       {@link TokenUsage} when available, an explicitly
 *       {@code estimated()} usage when not. The {@link RunBudget} settles
 *       with this value.</li>
 * </ul>
 *
 * <p><b>Semantic contract (no fake assistant text).</b> The carrier fields
 * ({@link #content()}, {@link #reasoning()}) are ONLY ever assistant content.
 * Transport errors, provider failures and empty assistant payloads are NEVER
 * written into {@code content} as text (no {@code "Error: ..."} mining) —
 * they surface as an {@link Outcome} with a stable {@link #technicalCode()}
 * and a diagnostic {@link #errorMessage()}. {@link #outcome()} is the single
 * source of truth for how the runtime reacts.</p>
 */
public final class LlmTurnOutput {

    /**
     * Semantic outcome of a turn — the single source of truth for how the
     * runtime reacts. Distinct from the transport-level {@code finishReason}
     * string and from any display text.
     *
     * <ul>
     *   <li>{@link #COMPLETED} — the turn produced content and/or native
     *       tool calls. Normal path.</li>
     *   <li>{@link #EMPTY_ASSISTANT_PAYLOAD} — the provider finished
     *       successfully but delivered an empty assistant payload. This is a
     *       SEMANTIC condition, never a transport error. The runtime consults
     *       {@code AgentEmptyResponsePolicy} (bounded semantic retry, then a
     *       locally built summary) and never renders it as text.</li>
     *   <li>{@link #FAILED} — a real transport/envelope failure. Carries a
     *       {@link #technicalCode()} and {@link #errorMessage()} as
     *       diagnostics, but never becomes assistant content.</li>
     * </ul>
     */
    public enum Outcome {
        COMPLETED,
        EMPTY_ASSISTANT_PAYLOAD,
        FAILED
    }

    /** Stable technical code for an empty assistant payload (policy contract). */
    public static final String CODE_EMPTY_ASSISTANT_PAYLOAD = "EMPTY_ASSISTANT_PAYLOAD";

    private final String content;
    private final String reasoning;
    private final String finishReason;
    private final List<ToolCall> toolCalls;
    private final boolean textStreamed;
    private final TokenUsage usage;
    private final Outcome outcome;
    private final String technicalCode;
    private final String errorMessage;

    public LlmTurnOutput(String content, String reasoning, String finishReason,
                         List<ToolCall> toolCalls) {
        this(content, reasoning, finishReason, toolCalls, false);
    }

    public LlmTurnOutput(String content, String reasoning, String finishReason,
                         List<ToolCall> toolCalls, boolean textStreamed) {
        this(content, reasoning, finishReason, toolCalls, textStreamed, null);
    }

    public LlmTurnOutput(String content, String reasoning, String finishReason,
                         List<ToolCall> toolCalls, boolean textStreamed, TokenUsage usage) {
        this(content, reasoning, finishReason, toolCalls, textStreamed, usage,
                Outcome.COMPLETED, "", "");
    }

    public LlmTurnOutput(String content, String reasoning, String finishReason,
                         List<ToolCall> toolCalls, boolean textStreamed, TokenUsage usage,
                         Outcome outcome, String technicalCode, String errorMessage) {
        this.content = content == null ? "" : content;
        this.reasoning = reasoning == null ? "" : reasoning;
        this.finishReason = finishReason == null ? "" : finishReason;
        this.toolCalls = toolCalls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(toolCalls);
        this.textStreamed = textStreamed;
        this.usage = usage;
        this.outcome = outcome == null ? Outcome.COMPLETED : outcome;
        this.technicalCode = technicalCode == null ? "" : technicalCode;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /** A semantically empty successful provider payload (never an error/text). */
    public static LlmTurnOutput emptyAssistantPayload(String detail) {
        return new LlmTurnOutput("", "", "stop", Collections.emptyList(), false, null,
                Outcome.EMPTY_ASSISTANT_PAYLOAD, CODE_EMPTY_ASSISTANT_PAYLOAD,
                detail == null ? "" : detail);
    }

    /** A real failure with a stable technical code + diagnostic message. */
    public static LlmTurnOutput failure(String technicalCode, String message, TokenUsage usage) {
        return new LlmTurnOutput("", "", "error", Collections.emptyList(), false, usage,
                Outcome.FAILED,
                technicalCode == null ? "" : technicalCode,
                message == null ? "" : message);
    }

    public String content() {
        return content;
    }

    public String reasoning() {
        return reasoning;
    }

    public String finishReason() {
        return finishReason;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    /** True when {@link #content()} already reached the UI via deltas. */
    public boolean textStreamed() {
        return textStreamed;
    }

    /** Provider usage of this turn, or {@code null} when the gateway did not report it. */
    public TokenUsage usage() {
        return usage;
    }

    /** Semantic outcome of the turn — the runtime's single source of truth. */
    public Outcome outcome() {
        return outcome;
    }

    /** Stable technical code, e.g. {@value #CODE_EMPTY_ASSISTANT_PAYLOAD}. */
    public String technicalCode() {
        return technicalCode;
    }

    /** Diagnostic detail for {@link #technicalCode()} — never assistant content. */
    public String errorMessage() {
        return errorMessage;
    }

    public boolean isEmptyAssistantPayload() {
        return outcome == Outcome.EMPTY_ASSISTANT_PAYLOAD;
    }

    public boolean isFailed() {
        return outcome == Outcome.FAILED;
    }

    /** True when the turn produced real assistant content or native tool calls. */
    public boolean hasEvidence() {
        return !content.isEmpty() || !toolCalls.isEmpty();
    }
}
