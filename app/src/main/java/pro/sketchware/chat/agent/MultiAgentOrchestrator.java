package pro.sketchware.chat.agent;

import androidx.annotation.NonNull;

import pro.sketchware.chat.AiProviderService;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process multi-agent workflow for the Android chat.
 *
 * <p>The workflow follows the Agent Framework orchestration concepts without
 * coupling the app to a server-side SDK:
 * <ol>
 *     <li>Planner and architect run concurrently with isolated sessions.</li>
 *     <li>A manager aggregates their outputs into one executor briefing.</li>
 *     <li>The existing AgentManager remains the only tool-capable implementer.</li>
 *     <li>A reviewer performs a bounded maker-checker pass before completion.</li>
 * </ol>
 *
 * <p>Specialists are advisory and never receive tools. This preserves a single
 * mutation authority, keeps approvals/checkpoints effective, and prevents two
 * agents from racing to edit the same project.
 */
public final class MultiAgentOrchestrator {

    private static final int MAX_SESSION_EXCHANGES = 4;
    private static final int MAX_SESSION_CONTEXT_CHARS = 8_000;
    private static final int MAX_SHARED_CONTEXT_CHARS = 12_000;
    private static final int MAX_SPECIALIST_OUTPUT_CHARS = 6_000;
    private static final int MAX_REVIEW_INPUT_CHARS = 14_000;
    private static final long AGENT_TIMEOUT_MS = 55_000L;

    public interface PreparationCallback {
        void onPrepared(@NonNull Preparation preparation);
    }

    public interface ReviewCallback {
        void onReviewed(@NonNull ReviewDecision decision);
    }

    public static final class Preparation {
        private final String managerBriefing;
        private final String plannerOutput;
        private final String architectOutput;
        private final boolean degraded;

        private Preparation(String managerBriefing, String plannerOutput,
                            String architectOutput, boolean degraded) {
            this.managerBriefing = safe(managerBriefing);
            this.plannerOutput = safe(plannerOutput);
            this.architectOutput = safe(architectOutput);
            this.degraded = degraded;
        }

        @NonNull
        public String getManagerBriefing() {
            return managerBriefing;
        }

        public boolean isDegraded() {
            return degraded;
        }

        public int getPlannerOutputChars() {
            return plannerOutput.length();
        }

        public int getArchitectOutputChars() {
            return architectOutput.length();
        }

        public int getManagerOutputChars() {
            return managerBriefing.length();
        }

        @NonNull
        public String toGuidance() {
            if (managerBriefing.isEmpty() && plannerOutput.isEmpty() && architectOutput.isEmpty()) {
                return "";
            }
            StringBuilder guidance = new StringBuilder();
            guidance.append("[MULTI-AGENT ORCHESTRATION BRIEFING]\n")
                    .append("Advisory context only. Verify every claim against workspace tools before acting.\n");
            if (!managerBriefing.isEmpty()) {
                guidance.append("\n[Manager synthesis]\n").append(managerBriefing);
            } else {
                if (!plannerOutput.isEmpty()) {
                    guidance.append("\n[Planner]\n").append(plannerOutput);
                }
                if (!architectOutput.isEmpty()) {
                    guidance.append("\n[Architect]\n").append(architectOutput);
                }
            }
            return bounded(guidance.toString(), MAX_SHARED_CONTEXT_CHARS);
        }
    }

    public static final class ReviewDecision {
        private final boolean approved;
        private final String reason;
        private final String feedback;
        private final boolean degraded;

        private ReviewDecision(boolean approved, String reason, String feedback, boolean degraded) {
            this.approved = approved;
            this.reason = safe(reason);
            this.feedback = safe(feedback);
            this.degraded = degraded;
        }

        public boolean isApproved() {
            return approved;
        }

        @NonNull
        public String getReason() {
            return reason;
        }

        @NonNull
        public String getFeedback() {
            return feedback;
        }

        public boolean isDegraded() {
            return degraded;
        }

        @NonNull
        public static ReviewDecision approved(String reason) {
            return new ReviewDecision(true, reason, "", false);
        }

        @NonNull
        public static ReviewDecision degradedApproval(String reason) {
            return new ReviewDecision(true, reason, "", true);
        }
    }

    private enum Role {
        PLANNER,
        ARCHITECT,
        MANAGER,
        REVIEWER
    }

    private static final class Exchange {
        final String input;
        final String output;

        Exchange(String input, String output) {
            this.input = bounded(input, 1_500);
            this.output = bounded(output, 3_500);
        }
    }

    /**
     * Agent sessions are deliberately isolated. Cross-agent communication only
     * happens through the manager's shared workflow context.
     */
    private static final class AgentSessionState {
        final ArrayDeque<Exchange> exchanges = new ArrayDeque<>();

        synchronized String context() {
            StringBuilder result = new StringBuilder();
            for (Exchange exchange : exchanges) {
                result.append("Previous input:\n").append(exchange.input)
                        .append("\nPrevious output:\n").append(exchange.output)
                        .append("\n\n");
            }
            return bounded(result.toString(), MAX_SESSION_CONTEXT_CHARS);
        }

        synchronized void record(String input, String output) {
            exchanges.addLast(new Exchange(input, output));
            while (exchanges.size() > MAX_SESSION_EXCHANGES) {
                exchanges.removeFirst();
            }
        }

        synchronized void clear() {
            exchanges.clear();
        }
    }

    private final AiProviderService aiService;
    /** Workflow coordinators must not occupy the same pool as awaited roles. */
    private final ExecutorService workflowExecutor;
    private final ExecutorService agentExecutor;
    private final Map<Role, AgentSessionState> sessions = new EnumMap<>(Role.class);
    private final AtomicInteger generation = new AtomicInteger();
    private volatile String operationProviderId = "";
    private volatile String operationModelName = "";

    public MultiAgentOrchestrator(@NonNull AiProviderService aiService) {
        this.aiService = aiService;
        AtomicInteger roleThreadNumber = new AtomicInteger();
        ThreadFactory roleFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "axion-agent-role-" + roleThreadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        AtomicInteger workflowThreadNumber = new AtomicInteger();
        ThreadFactory workflowFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "axion-agent-workflow-" + workflowThreadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        agentExecutor = Executors.newFixedThreadPool(4, roleFactory);
        workflowExecutor = Executors.newFixedThreadPool(2, workflowFactory);
        for (Role role : Role.values()) {
            sessions.put(role, new AgentSessionState());
        }
    }

    /** Fixa o mesmo provedor e modelo para todos os especialistas desta operação. */
    public void beginOperation(@NonNull String providerId, @NonNull String modelName) {
        operationProviderId = providerId.trim();
        operationModelName = modelName.trim();
    }

    /** Limpa a seleção congelada ao concluir ou cancelar a operação. */
    public void endOperation() {
        operationProviderId = "";
        operationModelName = "";
    }

    /**
     * Runs independent specialists in parallel and then fans their results into
     * the manager. Failures are isolated: one healthy specialist is enough to
     * produce a useful briefing.
     */
    public void prepareAsync(@NonNull String objective,
                             @NonNull String sharedContext,
                             @NonNull PreparationCallback callback) {
        final int requestGeneration = generation.get();
        final String boundedObjective = bounded(objective, 6_000);
        final String boundedContext = bounded(sharedContext, MAX_SHARED_CONTEXT_CHARS);

        workflowExecutor.execute(() -> {
            Future<String> plannerFuture = agentExecutor.submit(() -> callRole(
                    Role.PLANNER,
                    plannerInstructions(),
                    specialistInput(boundedObjective, boundedContext),
                    requestGeneration));
            Future<String> architectFuture = agentExecutor.submit(() -> callRole(
                    Role.ARCHITECT,
                    architectInstructions(),
                    specialistInput(boundedObjective, boundedContext),
                    requestGeneration));

            long deadline = System.currentTimeMillis() + AGENT_TIMEOUT_MS;
            String planner = await(plannerFuture, deadline);
            String architect = await(architectFuture, deadline);
            if (requestGeneration != generation.get()) {
                plannerFuture.cancel(true);
                architectFuture.cancel(true);
                return;
            }
            boolean degraded = planner.isEmpty() || architect.isEmpty();

            String manager = "";
            if (!planner.isEmpty() || !architect.isEmpty()) {
                String aggregationInput = "USER OBJECTIVE:\n" + boundedObjective
                        + "\n\nPLANNER OUTPUT:\n" + emptyLabel(planner)
                        + "\n\nARCHITECT OUTPUT:\n" + emptyLabel(architect);
                manager = callRoleSafely(
                        Role.MANAGER, managerInstructions(), aggregationInput, requestGeneration);
            }

            if (requestGeneration != generation.get()) {
                return;
            }
            callback.onPrepared(new Preparation(
                    bounded(manager, MAX_SPECIALIST_OUTPUT_CHARS),
                    bounded(planner, MAX_SPECIALIST_OUTPUT_CHARS),
                    bounded(architect, MAX_SPECIALIST_OUTPUT_CHARS),
                    degraded || manager.isEmpty()));
        });
    }

    /**
     * Runs the checker side of a bounded maker-checker workflow.
     */
    public void reviewAsync(@NonNull String objective,
                            @NonNull String managerBriefing,
                            @NonNull String executionEvidence,
                            @NonNull String finalResponse,
                            @NonNull ReviewCallback callback) {
        final int requestGeneration = generation.get();
        workflowExecutor.execute(() -> {
            if (requestGeneration != generation.get()) {
                return;
            }
            String input = "USER OBJECTIVE:\n" + bounded(objective, 5_000)
                    + "\n\nMANAGER BRIEFING:\n" + bounded(managerBriefing, 5_000)
                    + "\n\nEXECUTION EVIDENCE:\n" + bounded(executionEvidence, 5_000)
                    + "\n\nIMPLEMENTER FINAL RESPONSE:\n" + bounded(finalResponse, 8_000);
            input = bounded(input, MAX_REVIEW_INPUT_CHARS);

            String raw = callRoleSafely(
                    Role.REVIEWER, reviewerInstructions(), input, requestGeneration);
            ReviewDecision decision = parseReviewDecision(raw);
            if (requestGeneration == generation.get()) {
                callback.onReviewed(decision);
            }
        });
    }

    /** Invalidates callbacks and clears every isolated agent session. */
    public void reset() {
        generation.incrementAndGet();
        for (AgentSessionState session : sessions.values()) {
            session.clear();
        }
    }

    /** Invalidates the active workflow while preserving session memory. */
    public void cancelActiveWorkflow() {
        generation.incrementAndGet();
    }

    /** Releases worker threads when the owning chat activity is destroyed. */
    public void shutdown() {
        reset();
        workflowExecutor.shutdownNow();
        agentExecutor.shutdownNow();
    }

    private String callRole(Role role, String instructions, String input,
                            int requestGeneration) throws Exception {
        AgentSessionState session = sessions.get(role);
        String prior = session == null ? "" : session.context();
        String sessionInput = prior.isEmpty()
                ? bounded(input, MAX_SHARED_CONTEXT_CHARS)
                : "[ISOLATED ROLE SESSION]\n" + bounded(prior, 4_000)
                + "\n[CURRENT WORKFLOW INPUT]\n" + bounded(input, 8_000);
        String frozenProvider = operationProviderId;
        String frozenModel = operationModelName;
        String output = frozenProvider.isEmpty() || frozenModel.isEmpty()
                ? aiService.sendTextMessage(
                        instructions,
                        bounded(sessionInput, MAX_SHARED_CONTEXT_CHARS),
                        AGENT_TIMEOUT_MS)
                : aiService.sendTextMessage(
                        frozenProvider,
                        frozenModel,
                        instructions,
                        bounded(sessionInput, MAX_SHARED_CONTEXT_CHARS),
                        AGENT_TIMEOUT_MS);
        output = bounded(output, MAX_SPECIALIST_OUTPUT_CHARS);
        if (session != null && !output.isEmpty()
                && requestGeneration == generation.get()) {
            session.record(input, output);
        }
        return output;
    }

    private String callRoleSafely(Role role, String instructions, String input,
                                  int requestGeneration) {
        Future<String> future;
        try {
            future = agentExecutor.submit((Callable<String>) () ->
                    callRole(role, instructions, input, requestGeneration));
        } catch (RuntimeException rejected) {
            return "";
        }
        try {
            return safe(future.get(AGENT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (Exception ignored) {
            future.cancel(true);
            return "";
        }
    }

    private static String await(Future<String> future, long deadline) {
        long remaining = Math.max(1L, deadline - System.currentTimeMillis());
        try {
            return safe(future.get(remaining, TimeUnit.MILLISECONDS));
        } catch (TimeoutException timeout) {
            future.cancel(true);
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    @NonNull
    static ReviewDecision parseReviewDecision(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ReviewDecision.degradedApproval(
                    "Reviewer unavailable; local deterministic validation remains authoritative.");
        }
        String json = raw.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            JSONObject object = new JSONObject(json);
            boolean approved = object.optBoolean("approved", true);
            String reason = bounded(object.optString("reason", ""), 1_000);
            String feedback = bounded(object.optString("feedback", ""), 3_000);
            if (!approved && feedback.trim().isEmpty()) {
                feedback = "Re-check the objective, execution evidence, and missing validation before finishing.";
            }
            return new ReviewDecision(approved, reason, feedback, false);
        } catch (Exception ignored) {
            return ReviewDecision.degradedApproval(
                    "Reviewer returned malformed output; local deterministic validation remains authoritative.");
        }
    }

    private static String specialistInput(String objective, String sharedContext) {
        return "USER OBJECTIVE:\n" + objective
                + "\n\nSHARED WORKFLOW CONTEXT:\n" + sharedContext;
    }

    private static String plannerInstructions() {
        return "You are the Planner specialist in a coding-agent workflow. "
                + "Decompose the objective into concrete, ordered steps and acceptance checks. "
                + "Identify dependencies, risky assumptions, and the minimum evidence required to claim completion. "
                + "Do not claim that files were inspected or commands were run. You have no tools. "
                + "Return concise factual guidance for a separate tool-capable implementer.";
    }

    private static String architectInstructions() {
        return "You are the Architecture and Reliability specialist in a coding-agent workflow. "
                + "Independently analyze likely integration points, failure modes, state-management hazards, "
                + "security boundaries, concurrency risks, and validation needs. "
                + "Use only the supplied context and label uncertainty. You have no tools and must not fabricate "
                + "workspace facts. Return actionable advice to a manager agent.";
    }

    private static String managerInstructions() {
        return "You are the central manager of a Magentic-style coding workflow. "
                + "Aggregate the independent specialist outputs into a single execution briefing. "
                + "Resolve contradictions, prioritize correctness, preserve the user's exact objective, "
                + "and specify completion criteria. Delegate all workspace inspection and mutations to the "
                + "tool-capable implementer. Never present specialist assumptions as verified facts. "
                + "Be compact and operational.";
    }

    private static String reviewerInstructions() {
        return "You are the checker in a bounded maker-checker coding workflow. "
                + "Evaluate whether the implementer's final response and execution evidence satisfy the user objective "
                + "and manager briefing. Reject only for a concrete correctness, safety, completeness, or verification gap; "
                + "do not reject for style. Tool evidence is authoritative. Return JSON only with keys "
                + "{\"approved\":true|false,\"reason\":\"short reason\",\"feedback\":\"specific corrective action\"}.";
    }

    private static String emptyLabel(String value) {
        return value == null || value.trim().isEmpty() ? "(agent unavailable)" : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String bounded(String value, int maxChars) {
        String safe = safe(value);
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxChars))
                + "\n...[multi-agent context compacted]";
    }
}
