package pro.sketchware.chat.agentsdk;

/**
 * Result of a {@link Guardrail}, mirroring openai-agents-js
 * {@code GuardrailFunctionOutput}: when {@code tripwireTriggered} is true the
 * {@link Runner} stops before consuming more tokens or executing tools.
 */
public final class GuardrailResult {

    private final boolean tripwireTriggered;
    private final String info;

    private GuardrailResult(boolean tripwireTriggered, String info) {
        this.tripwireTriggered = tripwireTriggered;
        this.info = info == null ? "" : info;
    }

    public static GuardrailResult allow() {
        return new GuardrailResult(false, "");
    }

    public static GuardrailResult allow(String info) {
        return new GuardrailResult(false, info);
    }

    public static GuardrailResult block(String info) {
        return new GuardrailResult(true, info);
    }

    public boolean isTripwireTriggered() {
        return tripwireTriggered;
    }

    public String info() {
        return info;
    }
}
