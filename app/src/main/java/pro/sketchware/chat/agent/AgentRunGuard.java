package pro.sketchware.chat.agent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Progress-aware circuit breaker for one agent run.
 *
 * <p>Long tasks may use many tools, so the guard does not stop merely because a
 * read/search tool was used more than once. It stops only after a generous run
 * budget is exhausted or the same successful calls/results repeat without new
 * evidence. Workspace mutations reset the stagnation and completion windows.</p>
 */
public final class AgentRunGuard {
    static final int DEFAULT_MAX_MODEL_TURNS = 24;
    static final int DEFAULT_MAX_TOOL_CALLS = 80;
    static final int DEFAULT_MAX_IDENTICAL_SUCCESSFUL_CALLS = 3;
    static final int DEFAULT_MAX_OBSERVATIONS_WITHOUT_PROGRESS = 8;
    static final int DEFAULT_MAX_COMPLETION_CANDIDATES = 3;

    public enum Outcome {
        ALLOW,
        FORCE_FINAL_RESPONSE,
        STOP
    }

    public static final class Decision {
        private final Outcome outcome;
        private final String reason;

        private Decision(Outcome outcome, String reason) {
            this.outcome = outcome;
            this.reason = reason == null ? "" : reason;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public String getReason() {
            return reason;
        }

        public boolean shouldContinue() {
            return outcome == Outcome.ALLOW;
        }
    }

    private final int maxModelTurns;
    private final int maxToolCalls;
    private final int maxIdenticalSuccessfulCalls;
    private final int maxObservationsWithoutProgress;
    private final Map<String, Integer> successfulCallsBySignature = new HashMap<>();
    private final Set<String> observationFingerprints = new HashSet<>();

    private int modelTurns;
    private int toolCalls;
    private int successfulToolCalls;
    private int observationsWithoutProgress;
    private int completionCandidates;

    public AgentRunGuard() {
        this(DEFAULT_MAX_MODEL_TURNS,
                DEFAULT_MAX_TOOL_CALLS,
                DEFAULT_MAX_IDENTICAL_SUCCESSFUL_CALLS,
                DEFAULT_MAX_OBSERVATIONS_WITHOUT_PROGRESS);
    }

    /** Kept package-private for focused tests and source compatibility. */
    AgentRunGuard(int maxToolCalls, int maxObservationsWithoutProgress) {
        this(DEFAULT_MAX_MODEL_TURNS,
                maxToolCalls,
                DEFAULT_MAX_IDENTICAL_SUCCESSFUL_CALLS,
                maxObservationsWithoutProgress);
    }

    AgentRunGuard(int maxModelTurns, int maxToolCalls,
                  int maxIdenticalSuccessfulCalls,
                  int maxObservationsWithoutProgress) {
        this.maxModelTurns = Math.max(1, maxModelTurns);
        this.maxToolCalls = Math.max(1, maxToolCalls);
        this.maxIdenticalSuccessfulCalls = Math.max(1, maxIdenticalSuccessfulCalls);
        this.maxObservationsWithoutProgress = Math.max(1, maxObservationsWithoutProgress);
    }

    /** Called before creating a new provider request for the given logical loop step. */
    public Decision beforeModelTurn(int loopStep, boolean finalResponseOnly) {
        modelTurns = Math.max(modelTurns, Math.max(0, loopStep) + 1);
        if (finalResponseOnly) {
            return allow();
        }
        if (loopStep >= maxModelTurns) {
            return forceFinal("O limite seguro de rodadas do agente foi atingido. "
                    + "Resuma o progresso e informe qualquer trabalho restante sem usar novas ferramentas.");
        }
        return allow();
    }

    public Decision beforeToolCall(String toolName, String args, boolean finalResponseOnly) {
        if (finalResponseOnly) {
            return forceFinal("A condição de término já foi atingida; novas ferramentas foram bloqueadas.");
        }
        if (toolCalls >= maxToolCalls) {
            return forceFinal("O orçamento seguro de ferramentas foi atingido. "
                    + "Finalize com as evidências já coletadas e descreva o que ainda falta.");
        }
        if (observationsWithoutProgress >= maxObservationsWithoutProgress) {
            return forceFinal("As últimas ferramentas repetiram resultados sem produzir nova evidência. "
                    + "Não repita as chamadas; finalize e explique o bloqueio concreto.");
        }

        String signature = callSignature(toolName, args);
        int successfulIdenticalCalls = successfulCallsBySignature.getOrDefault(signature, 0);
        if (successfulIdenticalCalls >= maxIdenticalSuccessfulCalls) {
            return forceFinal("A mesma ferramenta com os mesmos argumentos já foi concluída "
                    + successfulIdenticalCalls + " vezes sem progresso adicional. Não a repita.");
        }

        toolCalls++;
        return allow();
    }

    /**
     * Bounds the case where deterministic completion is repeatedly confirmed but
     * the model keeps requesting more read/search tools. A mutation resets this
     * counter because it represents real new work that may need validation.
     */
    public Decision afterCompletionCandidate(boolean deterministicPlanComplete) {
        if (!deterministicPlanComplete) {
            completionCandidates = 0;
            return allow();
        }
        completionCandidates++;
        if (completionCandidates >= DEFAULT_MAX_COMPLETION_CANDIDATES) {
            return forceFinal("As etapas obrigatórias já foram concluídas e confirmadas "
                    + completionCandidates + " vezes sem uma nova alteração. "
                    + "Finalize com as evidências já coletadas e não solicite outras ferramentas.");
        }
        return allow();
    }

    public void onToolCompleted(String toolName, String args, String result, boolean successful) {
        if (!successful) {
            return;
        }
        successfulToolCalls++;

        if (isWorkspaceMutation(toolName)) {
            markProgress();
            return;
        }

        String signature = callSignature(toolName, args);
        successfulCallsBySignature.put(
                signature,
                successfulCallsBySignature.getOrDefault(signature, 0) + 1);

        String fingerprint = observationFingerprint(toolName, result);
        if (observationFingerprints.add(fingerprint)) {
            observationsWithoutProgress = 0;
        } else {
            observationsWithoutProgress++;
        }
    }

    /** Compatibility overload for older callers. New code should supply args/result. */
    public void onToolCompleted(String toolName, boolean successful) {
        onToolCompleted(toolName, "{}", "", successful);
    }

    public boolean hasSuccessfulToolCall() {
        return successfulToolCalls > 0;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public int getModelTurns() {
        return modelTurns;
    }

    public int getObservationsWithoutProgress() {
        return observationsWithoutProgress;
    }

    public void reset() {
        modelTurns = 0;
        toolCalls = 0;
        successfulToolCalls = 0;
        observationsWithoutProgress = 0;
        completionCandidates = 0;
        successfulCallsBySignature.clear();
        observationFingerprints.clear();
    }

    private void markProgress() {
        observationsWithoutProgress = 0;
        completionCandidates = 0;
        successfulCallsBySignature.clear();
        observationFingerprints.clear();
    }

    private static boolean isWorkspaceMutation(String toolName) {
        String normalized = safe(toolName).trim().toLowerCase(Locale.ROOT);
        return "edit_file".equals(normalized)
                || "rewrite_file".equals(normalized)
                || "create_file_or_folder".equals(normalized)
                || "delete_file_or_folder".equals(normalized);
    }

    private static String callSignature(String toolName, String args) {
        return safe(toolName).trim().toLowerCase(Locale.ROOT)
                + "\n" + safe(args).trim().replaceAll("\\s+", " ");
    }

    private static String observationFingerprint(String toolName, String result) {
        String normalizedResult = safe(result).trim().replace("\r\n", "\n");
        return safe(toolName).trim().toLowerCase(Locale.ROOT)
                + ':' + normalizedResult.length() + ':' + normalizedResult.hashCode();
    }

    private static Decision allow() {
        return new Decision(Outcome.ALLOW, "");
    }

    private static Decision forceFinal(String reason) {
        return new Decision(Outcome.FORCE_FINAL_RESPONSE, reason);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
