package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.workspace.WorkspaceFileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Assembles the prompt fragments of one run from its {@link RunContext}
 * (item 12 of the context-model migration). Replaces the legacy
 * {@code String systemContext} flattening: each fragment is structured,
 * carries provenance and is rendered in a fixed, tested order.
 *
 * <pre>
 * RunContext
 *     ↓
 * ContextAssembly (structured fragments with provenance)
 *     ↓
 * Prompt (single system prompt string)
 * </pre>
 *
 * <p>Fragment sources, in render order:</p>
 * <ol>
 *   <li>{@code AGENT_INSTRUCTIONS} — the {@link Agent}'s own role prompt;</li>
 *   <li>{@code PROJECT_INSTRUCTIONS} — the AGENTS.md hierarchy read from
 *       the run's filesystem;</li>
 *   <li>{@code WORKSPACE_STATE} — the discovered {@link ProjectSnapshot}
 *       (UNKNOWN-aware, anti-hallucination);</li>
 *   <li>{@code TASK_MEMORY} — the structured {@link TaskMemory} that
 *       survives compaction.</li>
 * </ol>
 */
public final class RunContextAssembler {

    /** Provenance of a fragment (item 12: know where each instruction came from). */
    public enum FragmentSource {
        AGENT_INSTRUCTIONS,
        TOOL_USAGE_POLICY,
        PROJECT_INSTRUCTIONS,
        WORKSPACE_STATE,
        TASK_MEMORY
    }

    /** One ordered prompt piece with its origin. */
    public static final class Fragment {
        private final FragmentSource source;
        private final String content;

        Fragment(FragmentSource source, String content) {
            this.source = source;
            this.content = content == null ? "" : content;
        }

        public FragmentSource source() {
            return source;
        }

        public String content() {
            return content;
        }
    }

    /** The structured result of one assembly pass. */
    public static final class Assembly {
        private final List<Fragment> fragments;

        Assembly(List<Fragment> fragments) {
            this.fragments = Collections.unmodifiableList(fragments);
        }

        public List<Fragment> fragments() {
            return fragments;
        }

        /** Renders the final system prompt (fragment order fixed by contract). */
        public String renderPrompt() {
            StringBuilder sb = new StringBuilder();
            for (Fragment fragment : fragments) {
                if (fragment.content.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(fragment.content.trim());
            }
            return sb.toString();
        }
    }

    private RunContextAssembler() {
    }

    /**
     * Assembles the fragments of one run. Never hits the global active
     * workspace: everything derives from {@code context}. Test overload for
     * JUnit runs without an Android host.
     */
    public static Assembly assemble(Agent agent, RunContext context) {
        return assemble(agent, context, context != null ? context.projectInstructions() : "",
                context != null ? context.snapshot() : null);
    }

    /**
     * Host overload: the resolved instructions and snapshot may be provided
     * by the caller (the runtime pre-resolves them via the factory) while the
     * agent supplies its own instructions.
     */
    public static Assembly assemble(Agent agent, String instructions, ProjectSnapshot snapshot) {
        return assemble(agent, null, instructions, snapshot);
    }

    private static Assembly assemble(Agent agent, RunContext context,
                                     String instructions, ProjectSnapshot snapshot) {
        List<Fragment> fragments = new ArrayList<>();
        if (agent != null) {
            fragments.add(new Fragment(FragmentSource.AGENT_INSTRUCTIONS,
                    agent.instructions() == null ? "" : agent.instructions().trim()));
        }
        // Requirement 9: the tool usage policy is a permanent,
        // structural fragment of every run's prompt — not a string only the
        // UI happens to show. It is rendered from ToolSelectionPolicy so this
        // is the single source of truth for the instruction text.
        fragments.add(new Fragment(FragmentSource.TOOL_USAGE_POLICY,
                pro.sketchware.chat.agentsdk.tools.ToolSelectionPolicy.TOOL_USAGE_POLICY_PROMPT));
        if (instructions != null && !instructions.trim().isEmpty()) {
            fragments.add(new Fragment(FragmentSource.PROJECT_INSTRUCTIONS,
                    "<project_instructions>\n" + instructions.trim() + "\n</project_instructions>"));
        }
        if (snapshot != null) {
            fragments.add(new Fragment(FragmentSource.WORKSPACE_STATE,
                    snapshot.renderPromptBlock()));
        }
        if (context != null && context.taskMemory() != null) {
            fragments.add(new Fragment(FragmentSource.TASK_MEMORY,
                    context.taskMemory().renderPromptBlock()));
        }
        return new Assembly(fragments);
    }
}
