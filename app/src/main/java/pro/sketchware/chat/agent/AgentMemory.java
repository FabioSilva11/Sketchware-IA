package pro.sketchware.chat.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pro.sketchware.chat.ChatReference;

/**
 * Host-side, per-message view of the user's intent: the original request,
 * key files and requirements of the CURRENT run, injected into the system
 * prompt so the loop never loses the thread mid-turn. This addresses the
 * "Memory of Intent" problem inside one run.
 *
 * <p><b>Scope contract (honest):</b> this is RUN memory, not persistent
 * memory — it lives only while the run is in flight and is rebuilt from the
 * user message on every run. Durable task state (objective, relevant files,
 * progress, applied changes across runs AND history compaction) is carried
 * by {@code pro.sketchware.chat.agentsdk.TaskMemory} and
 * persisted by {@code TaskMemoryStore}. Documentation, instructions and
 * memory stay separate concepts: README.md is human documentation,
 * AGENTS.md is operational instruction, this class is neither — it is run
 * state.</p>
 */
public class AgentMemory {

    private final String originalUserMessage;
    private final String compactObjective;
    private final List<String> keyFiles;
    private final List<String> keyRequirements;
    private final List<ChatReference> originalSelections;
    private final long timestamp;

    @Nullable
    private String currentPhase;
    private int progressSteps;
    private int totalSteps;

    private AgentMemory(@NonNull Builder builder) {
        this.originalUserMessage = builder.originalUserMessage;
        this.compactObjective = builder.compactObjective;
        this.keyFiles = Collections.unmodifiableList(new ArrayList<>(builder.keyFiles));
        this.keyRequirements = Collections.unmodifiableList(new ArrayList<>(builder.keyRequirements));
        this.originalSelections = builder.originalSelections == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.originalSelections));
        this.timestamp = System.currentTimeMillis();
        this.currentPhase = null;
        this.progressSteps = 0;
        this.totalSteps = 0;
    }

    /**
     * Returns the original user message, exactly as typed.
     */
    @NonNull
    public String getOriginalUserMessage() {
        return originalUserMessage;
    }

    /**
     * Returns a compact, essential summary of the objective.
     * Used when full original message is too large for context.
     */
    @NonNull
    public String getCompactObjective() {
        return compactObjective;
    }

    /**
     * Returns list of files that are central to completing the objective.
     */
    @NonNull
    public List<String> getKeyFiles() {
        return keyFiles;
    }

    /**
     * Returns list of key requirements that MUST be satisfied.
     */
    @NonNull
    public List<String> getKeyRequirements() {
        return keyRequirements;
    }

    /**
     * Returns original staging selections (files, images, etc).
     */
    @NonNull
    public List<ChatReference> getOriginalSelections() {
        return originalSelections;
    }

    /**
     * Returns timestamp when memory was created.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns current phase of execution (e.g., "reading files", "analyzing", "editing").
     */
    @Nullable
    public String getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Sets current phase of execution.
     */
    public void setCurrentPhase(@Nullable String phase) {
        this.currentPhase = phase;
    }

    /**
     * Returns number of steps completed.
     */
    public int getProgressSteps() {
        return progressSteps;
    }

    /**
     * Returns total number of steps planned.
     */
    public int getTotalSteps() {
        return totalSteps;
    }

    /**
     * Updates progress tracking.
     */
    public void setProgress(int completed, int total) {
        this.progressSteps = Math.max(0, completed);
        this.totalSteps = Math.max(0, total);
    }

    /**
     * Increments progress by one step.
     */
    public void incrementProgress() {
        this.progressSteps++;
    }

    /**
     * Returns true if all steps are completed.
     */
    public boolean isProgressComplete() {
        return totalSteps > 0 && progressSteps >= totalSteps;
    }

    /**
     * Builds host-generated execution state for the developer/system prompt.
     *
     * <p>The original user message deliberately stays in the user-message
     * history. Copying it into a higher-priority system prompt would duplicate
     * tokens and could promote user text into application instructions.</p>
     */
    @NonNull
    public String buildContextInjection() {
        StringBuilder builder = new StringBuilder();
        builder.append("[Host-generated execution state]");

        if (!keyRequirements.isEmpty()) {
            builder.append("\n\n[Deterministic requirements]\n");
            for (int i = 0; i < keyRequirements.size(); i++) {
                builder.append((i + 1)).append(". ").append(keyRequirements.get(i)).append("\n");
            }
        }

        if (!keyFiles.isEmpty()) {
            builder.append("\n[Key Files]\n");
            for (String file : keyFiles) {
                builder.append("- ").append(file).append("\n");
            }
        }

        if (currentPhase != null && !currentPhase.trim().isEmpty()) {
            builder.append("\n[Current Phase]\n");
            builder.append(currentPhase);
        }

        if (totalSteps > 0) {
            builder.append("\n[Progress]\n");
            builder.append(progressSteps).append(" of ").append(totalSteps).append(" steps completed");
        }

        return builder.toString().trim();
    }

    /**
     * Creates a new Builder for constructing AgentMemory.
     */
    @NonNull
    public static Builder builder(@NonNull String originalUserMessage) {
        return new Builder(originalUserMessage);
    }

    /**
     * Builder for AgentMemory.
     */
    public static class Builder {
        private final String originalUserMessage;
        private String compactObjective;
        private final List<String> keyFiles = new ArrayList<>();
        private final List<String> keyRequirements = new ArrayList<>();
        @Nullable
        private List<ChatReference> originalSelections;

        private Builder(@NonNull String originalUserMessage) {
            if (originalUserMessage == null || originalUserMessage.trim().isEmpty()) {
                throw new IllegalArgumentException("originalUserMessage cannot be null or empty");
            }
            this.originalUserMessage = originalUserMessage.trim();
            // Default: use original message as compact objective
            this.compactObjective = extractCompactObjective(this.originalUserMessage);
        }

        /**
         * Sets a custom compact objective (optional).
         * If not set, will be auto-extracted from original message.
         */
        @NonNull
        public Builder compactObjective(@NonNull String objective) {
            if (objective != null && !objective.trim().isEmpty()) {
                this.compactObjective = objective.trim();
            }
            return this;
        }

        /**
         * Adds a key file path.
         */
        @NonNull
        public Builder addKeyFile(@NonNull String filePath) {
            if (filePath != null && !filePath.trim().isEmpty()) {
                this.keyFiles.add(filePath.trim());
            }
            return this;
        }

        /**
         * Adds multiple key files.
         */
        @NonNull
        public Builder addKeyFiles(@NonNull List<String> filePaths) {
            if (filePaths != null) {
                for (String path : filePaths) {
                    addKeyFile(path);
                }
            }
            return this;
        }

        /**
         * Adds a key requirement.
         */
        @NonNull
        public Builder addKeyRequirement(@NonNull String requirement) {
            if (requirement != null && !requirement.trim().isEmpty()) {
                this.keyRequirements.add(requirement.trim());
            }
            return this;
        }

        /**
         * Adds multiple key requirements.
         */
        @NonNull
        public Builder addKeyRequirements(@NonNull List<String> requirements) {
            if (requirements != null) {
                for (String req : requirements) {
                    addKeyRequirement(req);
                }
            }
            return this;
        }

        /**
         * Sets original staging selections.
         */
        @NonNull
        public Builder originalSelections(@Nullable List<ChatReference> selections) {
            this.originalSelections = selections;
            return this;
        }

        /**
         * Builds the AgentMemory instance.
         */
        @NonNull
        public AgentMemory build() {
            return new AgentMemory(this);
        }

        /**
         * Extracts a compact objective from the full message.
         * Keeps first sentence or up to 200 characters.
         */
        @NonNull
        private static String extractCompactObjective(@NonNull String fullMessage) {
            if (fullMessage == null || fullMessage.trim().isEmpty()) {
                return "";
            }

            String trimmed = fullMessage.trim();

            // Find first sentence
            int firstPeriod = trimmed.indexOf('.');
            int firstNewline = trimmed.indexOf('\n');
            int firstQuestion = trimmed.indexOf('?');
            int firstExclamation = trimmed.indexOf('!');

            int cutoff = -1;
            if (firstPeriod > 0) cutoff = firstPeriod + 1;
            if (firstNewline > 0 && (cutoff < 0 || firstNewline < cutoff)) cutoff = firstNewline;
            if (firstQuestion > 0 && (cutoff < 0 || firstQuestion < cutoff)) cutoff = firstQuestion + 1;
            if (firstExclamation > 0 && (cutoff < 0 || firstExclamation < cutoff)) cutoff = firstExclamation + 1;

            if (cutoff > 0 && cutoff < 200) {
                return trimmed.substring(0, cutoff).trim();
            }

            // No sentence boundary found or too long, truncate at 200 chars
            if (trimmed.length() <= 200) {
                return trimmed;
            }

            return trimmed.substring(0, 200).trim() + "...";
        }
    }
}
