package pro.sketchware.chat.agentsdk;

/**
 * A guardrail check, mirroring openai-agents-js {@code Guardrail}. Input
 * guardrails run once before the first LLM call; output guardrails run after
 * each final assistant output. Returning a triggered tripwire stops the run.
 */
public interface Guardrail {

    /** Checks the user input before the run starts. */
    default GuardrailResult checkInput(String userInput) {
        return GuardrailResult.allow();
    }

    /** Checks the final assistant output before it is delivered. */
    default GuardrailResult checkOutput(String assistantOutput) {
        return GuardrailResult.allow();
    }
}
