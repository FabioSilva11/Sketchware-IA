package pro.sketchware.chat.agentsdk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Structured task/run memory (item 7 of the context-model migration): the
 * minimum state an agent needs to CONTINUE working after history compaction
 * — objective, workspace identity, verified project facts, relevant files,
 * progress and applied changes — kept as typed fields, never re-flattened
 * into one giant string.
 *
 * <p>Host integrations outside this package use {@code _RUN} as the scId
 * (one live run per process in the legacy loop) and the public mutators;
 * see {@link TaskMemoryStore} for the durable storage contract.</p>
 *
 * <p>Separation of concerns (does not mix the four concepts):</p>
 * <ul>
 *   <li>Documentation (README.md) — human docs, never loaded here;</li>
 *   <li>Instructions ({@code AGENTS.md}) — operational rules, carried by
 *       {@link RunContext#projectInstructions()};</li>
 *   <li>Persistent project memory — future store, out of this class;</li>
 *   <li>Run state — THIS class, plus the conversation state held by the
 *       {@link AgentSession}.</li>
 * </ul>
 *
 * <p>This replaces the string-only {@code AgentMemory.buildContextInjection()}
 * for v2 runs: the structured memory is serialized with the session so a
 * compaction cannot erase where the task stands. {@code AgentMemory} remains
 * as the host's per-message view; {@link TaskMemory} is the runtime's
 * authoritative, persisted record.</p>
 */
public final class TaskMemory {

    private final String scId;
    private final String workspaceId;
    private final String objective;
    private final long startedAt = System.currentTimeMillis();
    private final Set<String> relevantFiles = new LinkedHashSet<>();
    private final Set<String> requirements = new LinkedHashSet<>();
    private final Set<String> appliedChanges = new LinkedHashSet<>();
    private volatile String phase = "";

    /** Public for host integrations outside this package (legacy loop). */
    public TaskMemory(String scId, String workspaceId, String objective) {
        this.scId = scId == null ? "" : scId;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
        this.objective = objective == null ? "" : objective.trim();
    }

    public String scId() {
        return scId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String objective() {
        return objective;
    }

    public List<String> relevantFiles() {
        return Collections.unmodifiableList(new ArrayList<>(relevantFiles));
    }

    public List<String> requirements() {
        return Collections.unmodifiableList(new ArrayList<>(requirements));
    }

    public List<String> appliedChanges() {
        return Collections.unmodifiableList(new ArrayList<>(appliedChanges));
    }

    public String phase() {
        return phase;
    }

    /** Creation time, used by the store to avoid stale restores. */
    long startedAt() {
        return startedAt;
    }

    // Mutated by the runtime thread only; sets are copy-on-read.

    public void setPhase(String phase) {
        this.phase = phase == null ? "" : phase.trim();
    }

    public void recordFile(String path) {
        if (path != null && !path.trim().isEmpty()) {
            relevantFiles.add(path.trim());
        }
    }

    public void recordRequirement(String requirement) {
        if (requirement != null && !requirement.trim().isEmpty()) {
            requirements.add(requirement.trim());
        }
    }

    public void recordAppliedChange(String description) {
        if (description != null && !description.trim().isEmpty()) {
            appliedChanges.add(description.trim());
        }
    }

    /** Merges restored state into this memory (restored wins for scalar fields). */
    void mergeFrom(TaskMemory other) {
        if (other == null) {
            return;
        }
        if (!other.phase.isEmpty()) {
            this.phase = other.phase;
        }
        if (!other.objective.isEmpty() && this.objective.isEmpty()) {
            // objective is final (constructor); keep the restored value
            // visible through requirements instead.
            this.requirements.add("[restored objective] " + other.objective);
        }
        relevantFiles.addAll(other.relevantFiles);
        requirements.addAll(other.requirements);
        appliedChanges.addAll(other.appliedChanges);
    }

    /** Compact prompt block: the durable state the model must not lose. */
    public String renderPromptBlock() {
        StringBuilder sb = new StringBuilder("<task_memory>\n");
        if (!objective.isEmpty()) {
            sb.append("objective: ").append(objective).append('\n');
        }
        if (!workspaceId.isEmpty()) {
            sb.append("workspace: ").append(workspaceId).append('\n');
        }
        if (!phase.isEmpty()) {
            sb.append("phase: ").append(phase).append('\n');
        }
        if (!requirements.isEmpty()) {
            sb.append("requirements:\n");
            int i = 1;
            for (String r : requirements) {
                sb.append("  ").append(i++).append(". ").append(r).append('\n');
            }
        }
        if (!relevantFiles.isEmpty()) {
            sb.append("relevant_files: ").append(String.join(", ", relevantFiles)).append('\n');
        }
        if (!appliedChanges.isEmpty()) {
            sb.append("applied_changes:\n");
            for (String c : appliedChanges) {
                sb.append("  - ").append(c).append('\n');
            }
        }
        sb.append("Keep this state accurate: it survives conversation compaction.");
        sb.append("\n</task_memory>");
        return sb.toString();
    }

    /** Persistence shape (survives process death via the session store). */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("scId", scId);
            json.put("workspaceId", workspaceId);
            json.put("objective", objective);
            json.put("phase", phase);
            json.put("relevantFiles", new JSONArray(relevantFiles));
            json.put("requirements", new JSONArray(requirements));
            json.put("appliedChanges", new JSONArray(appliedChanges));
        } catch (Exception ignored) {
        }
        return json;
    }

    /** Restores memory persisted by {@link #toJson()}. */
    public static TaskMemory fromJson(JSONObject json) {
        if (json == null) {
            return new TaskMemory("", "", "");
        }
        TaskMemory memory = new TaskMemory(
                json.optString("scId", ""),
                json.optString("workspaceId", ""),
                json.optString("objective", ""));
        memory.setPhase(json.optString("phase", ""));
        JSONArray files = json.optJSONArray("relevantFiles");
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                memory.recordFile(files.optString(i, ""));
            }
        }
        JSONArray reqs = json.optJSONArray("requirements");
        if (reqs != null) {
            for (int i = 0; i < reqs.length(); i++) {
                memory.recordRequirement(reqs.optString(i, ""));
            }
        }
        JSONArray changes = json.optJSONArray("appliedChanges");
        if (changes != null) {
            for (int i = 0; i < changes.length(); i++) {
                memory.recordAppliedChange(changes.optString(i, ""));
            }
        }
        return memory;
    }
}
