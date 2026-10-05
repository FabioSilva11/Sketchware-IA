package pro.sketchware.chat.agentsdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An agent definition, mirroring openai-agents-js {@code Agent}: a name, a
 * system instruction and a list of agents it can hand off to.
 *
 * <p>Agents are inert configuration; the {@link Runner} executes them.</p>
 *
 * <p>Tools are NOT part of an agent anymore: the model-facing catalog comes
 * exclusively from the {@code AxionToolRegistry} wired into the
 * {@link AgentRuntime} (registry → {@code ToolCatalog} → {@code AxionToolRouter}).
 * Handoffs follow Codex parity — they are registry tool registrations
 * ({@code transfer_to_<agent>}) produced from this agent's {@link #handoffs()},
 * never a separate execution path.</p>
 */
public class Agent {

    private final String name;
    private final String instructions;
    private final List<Agent> handoffs;
    private final List<Guardrail> outputGuardrails;

    private Agent(Builder builder) {
        this.name = builder.name;
        this.instructions = builder.instructions;
        this.handoffs = Collections.unmodifiableList(new ArrayList<>(builder.handoffs));
        this.outputGuardrails = Collections.unmodifiableList(new ArrayList<>(builder.outputGuardrails));
    }

    public String name() {
        return name;
    }

    public String instructions() {
        return instructions;
    }

    /** Agents this agent may transfer control to. */
    public List<Agent> handoffs() {
        return handoffs;
    }

    public List<Guardrail> outputGuardrails() {
        return outputGuardrails;
    }

    public Builder toBuilder() {
        return new Builder(name, instructions)
                .handoffs(handoffs.toArray(new Agent[0]))
                .outputGuardrails(outputGuardrails.toArray(new Guardrail[0]));
    }

    public static final class Builder {
        private final String name;
        private String instructions = "";
        private final List<Agent> handoffs = new ArrayList<>();
        private final List<Guardrail> outputGuardrails = new ArrayList<>();

        private Builder(String name, String instructions) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Agent name is required");
            }
            this.name = name.trim();
            this.instructions = instructions == null ? "" : instructions.trim();
        }

        public static Builder forName(String name, String instructions) {
            return new Builder(name, instructions);
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions == null ? "" : instructions.trim();
            return this;
        }

        public Builder handoffs(Agent... agents) {
            for (Agent agent : agents) {
                if (agent != null) {
                    this.handoffs.add(agent);
                }
            }
            return this;
        }

        public Builder outputGuardrails(Guardrail... guardrails) {
            for (Guardrail guardrail : guardrails) {
                if (guardrail != null) {
                    this.outputGuardrails.add(guardrail);
                }
            }
            return this;
        }

        public Agent build() {
            return new Agent(this);
        }
    }
}