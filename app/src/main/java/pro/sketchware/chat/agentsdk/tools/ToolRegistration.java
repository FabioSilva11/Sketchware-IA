package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.ToolCapability;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A registered tool: the immutable binding of a {@link ToolSpec} (wire
 * declaration), a {@link ToolExposure} (model visibility), an optional
 * {@link ToolExecutor} (implementation) and policy metadata. This is the unit
 * the {@link AxionToolRegistry} stores and the {@link AxionToolRouter}
 * executes.
 *
 * <p>Each registration is the single source of truth for a tool: the spec
 * feeds the model catalog, the policy flags feed the {@code PermissionLayer}
 * classification, and the executor performs the work.</p>
 */
public final class ToolRegistration {

    private final ToolSpec spec;
    private final ToolExposure exposure;
    private final ToolExecutor executor;
    private final String source;
    private final boolean requiresApproval;
    private final boolean isFileMutation;
    private final boolean isDestructive;
    private final SandboxConstraint sandbox;
    /** Optional cooperative-handoff target (@{@code Agent} name) or {@code null}. */
    private final String handoffTarget;

    /**
     * Declared {@link ToolCapability}s (codex tool metadata parity). Declared
     * metadata is the single source of truth for the {@code PermissionEvaluator};
     * names are only a defensive fallback when nothing was declared.
     */
    private final Set<ToolCapability> capabilities;

    private ToolRegistration(Builder builder) {
        this.spec = builder.spec;
        this.exposure = builder.exposure == null ? ToolExposure.direct() : builder.exposure;
        this.executor = builder.executor;
        this.source = builder.source == null ? "" : builder.source;
        this.requiresApproval = builder.requiresApproval;
        this.isFileMutation = builder.isFileMutation;
        this.isDestructive = builder.isDestructive;
        this.sandbox = builder.sandbox;
        this.handoffTarget = builder.handoffTarget;
        this.capabilities = EnumSet.copyOf(builder.capabilities);
    }

    public ToolSpec spec() {
        return spec;
    }

    public ToolExposure exposure() {
        return exposure;
    }

    /** The implementation; {@code null} for declarations (pure namespaces). */
    public ToolExecutor executor() {
        return executor;
    }

    /** Provenance tag, e.g. {@code "core"}, {@code "legacy"}, {@code "mcp:<server>"}. */
    public String source() {
        return source;
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }

    public boolean isFileMutation() {
        return isFileMutation;
    }

    public boolean isDestructive() {
        return isDestructive;
    }

    /** Optional sandbox pre-check consulted by the router before execution. */
    public SandboxConstraint sandbox() {
        return sandbox;
    }

    /**
     * The {@code Agent} name this registration hands off to, or {@code null}
     * for ordinary tools. The runtime switches the active agent after a
     * successful execution of a handoff registration (Codex parity: handoffs
     * are tool metadata, never a separate execution path).
     */
    public String handoffTarget() {
        return handoffTarget;
    }

    /** Whether this registration is a cooperative handoff to another agent. */
    public boolean isHandoff() {
        return handoffTarget != null && !handoffTarget.trim().isEmpty();
    }

    /**
     * Declared capabilities (unmodifiable; empty when not declared — the
     * evaluator then falls back to defensive inference).
     */
    public Set<ToolCapability> capabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    /** Convenience: the fully-qualified model name. */
    public String qualifiedName() {
        return spec.qualifiedName();
    }

    /** Sandbox pre-execution or a pre-execution gate (Codex sandbox parity). */
    public interface SandboxConstraint {
        /**
         * Return an error result to block execution, or {@code null} to
         * allow it.
         */
        AgentToolResult preExecute(String callId, String scId);
    }

    public static Builder builder(ToolSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }
        return new Builder(spec);
    }

    // ------------------------------------------------------------------
    // Convenience factories
    // ------------------------------------------------------------------

    /** Builds a FUNCTION registration for any plain function spec. */
    public static Builder function(String name, String description, org.json.JSONObject parameters) {
        return builder(ToolSpec.function(ToolName.plain(name), description, parameters));
    }

    /** Builds a freeform executor registration (FREEFORM spec). */
    public static Builder freeform(String name, String description, String grammarDefinition,
                                   ToolExecutor executor) {
        return builder(ToolSpec.freeform(
                ToolName.plain(name), description, "grammar", "lark", grammarDefinition, false))
                .executor(executor);
    }

    public static final class Builder {
        private final ToolSpec spec;
        private ToolExposure exposure = ToolExposure.direct();
        private ToolExecutor executor;
        private String source;
        private boolean requiresApproval;
        private boolean isFileMutation;
        private boolean isDestructive;
        private SandboxConstraint sandbox;
        private String handoffTarget;

        private final Set<ToolCapability> capabilities = EnumSet.noneOf(ToolCapability.class);

        private Builder(ToolSpec spec) {
            this.spec = spec;
        }

        public Builder exposure(ToolExposure exposure) {
            this.exposure = exposure;
            return this;
        }

        public Builder executor(ToolExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        public Builder fileMutation(boolean isFileMutation) {
            this.isFileMutation = isFileMutation;
            return this;
        }

        public Builder destructive(boolean isDestructive) {
            this.isDestructive = isDestructive;
            return this;
        }

        public Builder sandbox(SandboxConstraint sandbox) {
            this.sandbox = sandbox;
            return this;
        }

        /** Marks this registration as a handoff to the named {@code Agent}. */
        public Builder handoffTarget(String targetAgentName) {
            this.handoffTarget = targetAgentName;
            return this;
        }

        /** Declares one capability for this tool (see {@link ToolCapability}). */
        public Builder capability(ToolCapability capability) {
            if (capability != null) {
                this.capabilities.add(capability);
            }
            return this;
        }

        /** Declares several capabilities for this tool. */
        public Builder capabilities(ToolCapability... capabilities) {
            if (capabilities != null) {
                Collections.addAll(this.capabilities, capabilities);
            }
            return this;
        }

        public ToolRegistration build() {
            return new ToolRegistration(this);
        }
    }
}