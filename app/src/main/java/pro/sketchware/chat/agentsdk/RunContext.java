package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.workspace.WorkspaceFileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single execution identity of one agent run (Codex {@code Session → Cwd}
 * parity): created once by the {@link RunContextFactory} at the start of the
 * run and threaded through every turn, tool call and mutation.
 *
 * <p>Every component that needs the workspace — prompt assembly, AGENTS.md
 * discovery, project snapshot, tools, mutations — resolves it from the
 * {@link RunContext}, never from the global active-workspace state. This is
 * what makes {@code scId = A} + {@code activeWorkspace = B} impossible for a
 * running agent: the filesystem used by a run is the one carried here.</p>
 *
 * <p>Provenance of each piece of state:</p>
 * <ul>
 *   <li>{@link #workspace()} / {@link #filesystem()} — workspace identity
 *       resolved deterministically from the {@code scId} (may be absent when
 *       the chat belongs to a legacy project without an open workspace);</li>
 *   <li>{@link #projectInstructions()} — operational rules (AGENTS.md
 *       hierarchy) read from THIS run's filesystem;</li>
 *   <li>{@link #snapshot()} — discovered workspace facts, with UNKNOWN for
 *       anything not verified;</li>
 *   <li>{@link #taskMemory()} — structured task/run memory that survives
 *       history compaction;</li>
 *   <li>{@link #contextTracker()} — per-run token accounting.</li>
 * </ul>
 */
public final class RunContext {

    private final String scId;
    private final WorkspaceIdentity workspace;
    private final WorkspaceFileSystem filesystem;
    private final String projectInstructions;
    private final ProjectSnapshot snapshot;
    private final TaskMemory taskMemory;
    private final ContextTracker contextTracker;
    private final long startedAt = System.currentTimeMillis();
    private final List<String> handoffTrail = new ArrayList<>();
    private String currentAgentName;
    private String cwd;
    private int llmCalls;
    private int toolCalls;

    /** Test/injected constructor: caller provides the resolved identity. */
    RunContext(String scId,
               WorkspaceIdentity workspace,
               WorkspaceFileSystem filesystem,
               String projectInstructions,
               ProjectSnapshot snapshot,
               TaskMemory taskMemory,
               ContextTracker contextTracker,
               String initialAgentName) {
        this.scId = scId == null ? "" : scId;
        this.workspace = workspace;
        this.filesystem = filesystem;
        this.projectInstructions = projectInstructions == null ? "" : projectInstructions;
        this.snapshot = snapshot;
        this.taskMemory = taskMemory;
        this.contextTracker = contextTracker;
        this.currentAgentName = initialAgentName;
        this.cwd = workspace != null ? workspace.cwd() : "";
    }

    /**
     * Legacy/test constructor: identity-only context without a resolved
     * workspace (evals, JVM tests). Production runs go through the
     * {@link RunContextFactory} overload above.
     */
    public static RunContext bare(String scId, String agentName, ContextTracker tracker) {
        return new RunContext(scId, null, null, "", null, null,
                tracker != null ? tracker : new ContextTracker(null), agentName);
    }

    // ------------------------------------------------------------------
    // identity
    // ------------------------------------------------------------------

    /** Conversation/project id this run belongs to. */
    public String scId() {
        return scId;
    }

    /** Resolved workspace identity, or {@code null} for legacy projects. */
    public WorkspaceIdentity workspace() {
        return workspace;
    }

    /**
     * The ONLY filesystem this run may touch. May be {@code null} when the
     * chat belongs to a project without an open workspace — in that case
     * tools fall back to the legacy {@code ProjectPathResolver} path, which
     * is the documented contract for non-workspace projects.
     */
    public WorkspaceFileSystem filesystem() {
        return filesystem;
    }

    /** Workspace-relative cwd of the run (Codex Cwd parity; root by default). */
    public String cwd() {
        return cwd;
    }

    /** Stable environment id: workspace id when known, scId otherwise. */
    public String environmentId() {
        return workspace != null && workspace.workspaceId() != null
                && !workspace.workspaceId().isEmpty()
                ? workspace.workspaceId()
                : scId;
    }

    // ------------------------------------------------------------------
    // context fragments
    // ------------------------------------------------------------------

    /** AGENTS.md instructions applicable to this run (empty when none). */
    public String projectInstructions() {
        return projectInstructions;
    }

    /** Structured workspace facts with UNKNOWN for undiscovered values. */
    public ProjectSnapshot snapshot() {
        return snapshot;
    }

    /** Structured task memory (objective, files, progress) of this run. */
    public TaskMemory taskMemory() {
        return taskMemory;
    }

    /** Per-run context accounting (never null). */
    public ContextTracker contextTracker() {
        return contextTracker;
    }

    // ------------------------------------------------------------------
    // run bookkeeping
    // ------------------------------------------------------------------

    long startedAt() {
        return startedAt;
    }

    String currentAgentName() {
        return currentAgentName;
    }

    void setCurrentAgentName(String name) {
        if (name != null && !name.isEmpty()) {
            this.currentAgentName = name;
        }
    }

    void setCwd(String cwd) {
        this.cwd = cwd == null ? "" : cwd;
    }

    void incrementLlmCalls() {
        llmCalls++;
    }

    void incrementToolCalls() {
        toolCalls++;
    }

    void recordHandoff(Agent from, Agent to, String reason) {
        handoffTrail.add(from.name() + " -> " + to.name()
                + (reason == null || reason.isEmpty() ? "" : " (" + reason + ")"));
    }

    /** Ordered trail of handoffs in this run (for trace/logs). */
    List<String> handoffTrail() {
        return Collections.unmodifiableList(handoffTrail);
    }

    int llmCalls() {
        return llmCalls;
    }

    int toolCalls() {
        return toolCalls;
    }
}
