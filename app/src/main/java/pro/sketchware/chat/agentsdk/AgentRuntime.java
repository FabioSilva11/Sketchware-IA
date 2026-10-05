package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.AiChatSettingsHelper;
import pro.sketchware.chat.AiOperationContext;
import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter;
import pro.sketchware.chat.agentsdk.tools.ToolCatalog;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.skills.SkillContext;
import pro.sketchware.chat.skills.SkillFlow;
import pro.sketchware.chat.skills.SkillPromptBlocks;
import pro.sketchware.chat.toolcalling.ToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateful agent run loop (v2), porting the Codex session/turn cycle to the
 * Axion stack on top of the {@link Runner} concepts:
 *
 * <pre>Session → Turn → Model → Tool calls → Permission/Sandbox → Result → next Turn</pre>
 *
 * <p>Differences from {@link Runner}: emits typed {@link AgentEvent}s to an
 * {@link EventStream}, consults the {@link PermissionLayer} before every
 * tool (async human-in-the-loop instead of a blocking boolean callback),
 * keeps an {@link AgentSession} audit trail, and supports cooperative
 * cancellation. Legacy hosts keep using {@link Runner}; new hosts and the
 * migrating UI use this class.</p>
 */
public final class AgentRuntime {

    public static final int DEFAULT_MAX_TURNS = 16;

    private static final long DIRECTORY_CACHE_TTL_MS = 5000L; // parity with ContextBuilder

    private final AgentLlmGateway gateway;
    private final EventStream events;
    private final PermissionLayer permissions;
    private final List<Guardrail> inputGuardrails;
    private final int maxTurns;
    private final RunBudget budget;
    private final int maxOutputTokensPerTurn;
    private final boolean includeProjectInstructions;
    // The single canonical execution path: registry lookup → exposure →
    // permission → sandbox → executor (AxionToolRouter, Codex router.rs parity).
    private final AxionToolRouter router;
    private final AxionToolRegistry registry;
    // Removed: expectFileMutations (Codex alignment)
    // The runtime no longer assumes all chats require file mutations.
    private final AiOperationContext builderOperationContext;
    /** Único fluxo de Skills resolvido por execução (código-x). */
    private final SkillFlow skillFlow;
    private volatile SkillPromptBlocks lastSkillBlocks = SkillPromptBlocks.empty();
    private volatile boolean cancelRequested;

    private AgentRuntime(Builder builder) {
        this.gateway = builder.gateway;
        this.events = builder.events;
        this.permissions = builder.permissions;
        this.registry = builder.registry;
        this.router = new AxionToolRouter(builder.registry, permissions, events);
        this.inputGuardrails = Collections.unmodifiableList(new ArrayList<>(builder.inputGuardrails));
        this.maxTurns = Math.max(1, builder.maxTurns);
        this.budget = builder.budget;
        this.maxOutputTokensPerTurn = builder.maxOutputTokensPerTurn;
        this.includeProjectInstructions = builder.includeProjectInstructions;
        // Removed: expectFileMutations assignment (Codex alignment)
        this.builderOperationContext = builder.operationContext;
        this.skillFlow = builder.skillFlow;
    }

    /** Single-shot run: user input in, final assistant text out. */
    public RunResult run(Agent agent, String userInput, String scId) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(userInput, ChatMessage.TYPE_USER, System.currentTimeMillis()));
        return run(agent, history, scId);
    }

    /** Resumable run over caller-owned history (appended with this run's turns). */
    public RunResult run(Agent agent, List<ChatMessage> history, String scId) {
        return run(agent, history, scId, null);
    }

    /**
     * Resumable run with a FROZEN {@link AiOperationContext} (items 10/11):
     * when the host provides one, every turn uses exactly that provider/model
     * identity; when not, the runtime captures the host's current selection
     * once, here, and never reads mutable preferences again mid-run.
     */
    public RunResult run(Agent agent, List<ChatMessage> history, String scId,
                         AiOperationContext operationContext) {
        if (agent == null) {
            return RunResult.failure("No agent was provided.");
        }
        if (history == null || history.isEmpty()) {
            return RunResult.failure("No user input was provided.");
        }
        cancelRequested = false;
        // Item 10 of the migration: the provider/model/mode identity of this
        // run is FROZEN now. Host preference changes mid-run affect the NEXT
        // run, never this one.
        final AiOperationContext runContextIdentity = operationContext != null
                ? operationContext
                : builderOperationContext != null ? builderOperationContext : captureOperationIdentity();

        AgentSession session = new AgentSession(scId, agent.name(), history);
        session.setStatus(AgentSession.Status.RUNNING);
        lastSession = session;

        emit(new AgentEvent.RunStarted(scId));

        // Input guardrails run before the first LLM call (openai-agents semantics).
        String latestUserText = latestUserText(history);
        for (Guardrail guardrail : inputGuardrails) {
            GuardrailResult result = guardrail.checkInput(latestUserText);
            if (result.isTripwireTriggered()) {
                session.setStatus(AgentSession.Status.FAILED);
                emit(new AgentEvent.RunCompleted(scId, false, "Blocked by input guardrail"));
                return RunResult.blockedByGuardrail(result);
            }
        }

        // Items (skills): the skill context (catalog → selector → loader →
        // renderer) is resolved ONCE per run from the run's scId and the
        // latest user text. A failure anywhere yields empty blocks — the
        // runtime NEVER breaks a conversation because of skills.
        final SkillPromptBlocks skillBlocks = skillBlocksFor(scId, latestUserText);

        // ---- Single execution identity (item 2/3 of the migration) ----
        // The workspace of this run is resolved ONCE from the scId — never
        // from the global active workspace, which is a UI selection that may
        // point anywhere. Prompt, AGENTS.md, snapshot, tools and mutations
        // all derive from this RunContext.
        final RunContextFactory.Resolved resolved = RunContextFactory.resolve(scId);
        RunContext context = new RunContext(
                scId,
                resolved.workspace(),
                resolved.filesystem(),
                includeProjectInstructions
                        ? ProjectInstructions.load(resolved.filesystem(), cwdOf(resolved), PROJECT_INSTRUCTIONS_MAX_CHARS)
                        : "",
                includeProjectInstructions
                        ? ProjectDiscovery.discover(resolved.filesystem(), cwdOf(resolved))
                        : null,
                new TaskMemory(scId, environmentIdOf(resolved), latestUserText(history)),
                new ContextTracker(budget),
                agent.name());

        // Pin the run's filesystem for the whole run under THIS run's id
        // (item 14): tools, ContextBuilder and ApplyPatchTool read
        // RuntimeFileContext instead of the global active workspace while
        // this run is in flight — and concurrent runs never share bindings.
        // Identity: the frozen operationContext's requestId when available,
        // else a unique id (tests/headless hosts without preferences).
        final String runId = runContextIdentity != null
                ? "run_" + runContextIdentity.getRequestId()
                : "run_" + java.util.UUID.randomUUID();
        final AutoCloseable pinned = RuntimeFileContext.pin(
                runId, resolved.workspace(), resolved.filesystem());

        // Compaction-proof handoff (item 7 of the migration): the previous
        // run's durable task state (objective, relevant files, progress) is
        // restored from the task store, so a compacted history never erases
        // where the task stands.
        TaskMemoryStore.restoreInto(scId, context.taskMemory());

        Agent activeAgent = agent;
        int turns = 0;
        // Removed: recoveryNudges - Codex alignment: no artificial recovery
        router.resetForNewRun();
        try {
            while (turns++ < maxTurns) {
                if (cancelRequested) {
                    session.setStatus(AgentSession.Status.CANCELLED);
                    emit(new AgentEvent.RunCompleted(scId, false, "Run cancelled"));
                    return RunResult.failure("Run cancelled.");
                }

                final String activeAgentName = activeAgent.name();
                final int turnNumber = turns;
                emit(new AgentEvent.TurnStarted(scId, activeAgentName, turnNumber));

                // M3: reserve the worst-case cost of this turn before the request.
                RunBudget.Handle reservation = null;
                if (budget != null) {
                    try {
                        long estimate = estimatedInputTokens(history) + maxOutputTokensPerTurn;
                        budget.ensureActive(estimate);
                        reservation = budget.reserve(estimate);
                    } catch (RunBudget.BudgetExceededException e) {
                        String reason = e.getMessage();
                        emit(new AgentEvent.Error(scId, reason));
                        session.setStatus(AgentSession.Status.FAILED);
                        emit(new AgentEvent.RunCompleted(scId, false, reason));
                        return RunResult.failure(reason);
                    }
                }

                final LlmTurnOutput turn;
                try {
                    // Item 6: the runtime owns the streaming route for THIS
                    // turn. The listener is registered before the request and
                    // cleared in the finally — a previous run's listener can
                    // never contaminate this one.
                    gateway.setDeltaListener(delta -> {
                        if (delta != null && !delta.isEmpty()) {
                            emit(new AgentEvent.AssistantMessageDelta(scId, delta));
                        }
                    });
                    // Canonical catalog (single source of truth): the model
                    // sees exactly the registry's DIRECT tools, serialized by
                    // capability in the gateway. The catalog is never flattened
                    // by the runtime — freeform/namespace kinds stay faithful
                    // until the provider serializer.
                    turn = gateway.completeTurn(
                            resolveSystemPrompt(activeAgent, context, skillBlocks),
                            toolCatalogFor(),
                            snapshot(history),
                            runContextIdentity);
                } catch (Exception e) {
                    String reason = "LLM turn failed: " + e.getMessage();
                    emit(new AgentEvent.Error(scId, reason));
                    session.setStatus(AgentSession.Status.FAILED);
                    emit(new AgentEvent.RunCompleted(scId, false, reason));
                    return RunResult.failure(reason);
                } finally {
                    gateway.setDeltaListener(null);
                }
                // Item 17: settle the reservation with REAL usage whenever the
                // provider reported it; only an estimate when it did not.
                if (budget != null && reservation != null) {
                    try {
                        // Without a provider report the reservation itself is
                        // the best-known cost: settling the FULL hold (never
                        // more) keeps spent honest and can never exceed what
                        // was reserved.
                        TokenUsage usage = turn.usage() != null
                                ? turn.usage()
                                : TokenUsage.estimated(reservation.reserved(), 0);
                        budget.settle(reservation, usage);
                        context.contextTracker().recordTurnUsage(usage);
                    } catch (RunBudget.UncertainChargeException e) {
                        String reason = "Budget: " + e.getMessage();
                        emit(new AgentEvent.Error(scId, reason));
                        session.setStatus(AgentSession.Status.FAILED);
                        emit(new AgentEvent.RunCompleted(scId, false, reason));
                        return RunResult.failure(reason);
                    }
                }
                // Input as the model will see it on the NEXT turn: the
                // history now includes this turn's tool results.
                context.contextTracker().recordInputEstimate(estimatedInputTokens(history));
                context.incrementLlmCalls();

                // ---- Source of truth (Codex ResponseItem parity) ----------
                // structuredToolCalls = executable facts from the provider
                // envelope; assistantText = displayable message. The runtime
                // NEVER re-derives tool calls from the text.
                List<ToolCall> structuredCalls = turn.toolCalls() == null
                        ? Collections.emptyList()
                        : turn.toolCalls();
                String assistantText = turn.content() == null ? "" : turn.content().trim();

                if (structuredCalls.isEmpty()) {
                    if (assistantText.isEmpty()) {
                        String reason = "LLM returned an empty turn.";
                        emit(new AgentEvent.Error(scId, reason));
                        session.setStatus(AgentSession.Status.FAILED);
                        emit(new AgentEvent.RunCompleted(scId, false, reason));
                        return RunResult.failure(reason);
                    }
                    // Codex alignment: plain text responses are VALID completion.
                    // The runtime does NOT fabricate tool calls or insert artificial
                    // messages into the user history. Text-only responses are a
                    // legitimate model decision, not an error condition.
                    // 
                    // Removed: expectFileMutations recovery logic that inserted
                    // RECOVERY_NUDGE as a fake user message. This violated the
                    // principle that the runtime should react to model behavior,
                    // not force specific outcomes.
                    emit(new AgentEvent.AssistantMessage(scId, assistantText));
                    for (Guardrail guardrail : activeAgent.outputGuardrails()) {
                        GuardrailResult result = guardrail.checkOutput(assistantText);
                        if (result.isTripwireTriggered()) {
                            session.setStatus(AgentSession.Status.FAILED);
                            emit(new AgentEvent.RunCompleted(scId, false, "Blocked by output guardrail"));
                            return RunResult.blockedByGuardrail(result);
                        }
                    }
                    session.setStatus(AgentSession.Status.COMPLETED);
                    emit(new AgentEvent.RunCompleted(scId, true, ""));
                    return RunResult.success(assistantText, context);
                }

                // Assistant text is display-only when tools also arrived: it
                // stays OUT of the execution path (semantic separation).
                // Item 7 (no duplicate AssistantMessageDelta): when the text
                // was ALREADY delivered by streaming deltas, do NOT publish it
                // again at turn end — the UI has it all. A non-streamed turn
                // emits exactly one AssistantMessage so the UI still sees it.
                if (!assistantText.isEmpty() && !turn.textStreamed()) {
                    emit(new AgentEvent.AssistantMessage(scId, assistantText));
                }

                if (registry == null) {
                    // Defensive: unreachable — the Builder makes the registry
                    // mandatory. Kept as a guard for misconstructed runtimes.
                    return RunResult.failure("Runtime has no tool registry.");
                }
                Agent next = executeTools(activeAgent, session, context, scId, structuredCalls, history);
                if (next != null && next != activeAgent) {
                    context.recordHandoff(activeAgent, next, "");
                    activeAgent = next;
                }
                // With tools executed (or a handoff), loop for the next LLM turn.
            }
            session.setStatus(AgentSession.Status.FAILED);
            emit(new AgentEvent.RunCompleted(scId, false, "Max turns reached"));
            return RunResult.maxTurnsReached(context, "");
        } finally {
            sessionSnapshot = session;
            // Durable task memory: the NEXT run (and process restart) starts
            // from this state even when the history gets compacted.
            TaskMemoryStore.save(scId, context.taskMemory());
            try {
                pinned.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Cooperative cancellation: checked between turns and tool calls. */
    public void cancel() {
        cancelRequested = true;
        // Item 33: a cancelled run must not leave a PENDING approval parked;
        // cancelling them resolves the parked run thread promptly.
        if (permissions != null) {
            permissions.cancelPendingApprovals();
        }
        gateway.cancel();
    }

    private AgentSession lastSession;
    private AgentSession sessionSnapshot;

    /** Session of the most recent run on this runtime (audit/telemetry). */
    public AgentSession lastSession() {
        return sessionSnapshot != null ? sessionSnapshot : lastSession;
    }

    /** The run-scoped {@link EventStream} this runtime emits to (item 8/9). */
    public EventStream eventStream() {
        return events;
    }

    // ------------------------------------------------------------------
    // Host-visible pending approval (Codex ReviewDecision parity)
    // ------------------------------------------------------------------

    /**
     * A tool call currently parked waiting for a human decision. Host UIs
     * query it to render the confirmation dialog and can observe the final
     * decision once resolved.
     */
    public static final class PendingApproval {
        private final PermissionRequest request;
        private final ApprovalHandler.ApprovalRecord recordView;

        PendingApproval(PermissionRequest request) {
            this(request, null);
        }

        PendingApproval(PermissionRequest request, ApprovalHandler.ApprovalRecord recordView) {
            this.request = request;
            this.recordView = recordView;
        }

        public String getTool() {
            return request.getTool();
        }

        public PermissionRequest getRequest() {
            return request;
        }

        /** The request id to use with {@code resolveApproval}/{@code cancelApproval}. */
        public String getRequestId() {
            return request.getId();
        }

        /** Lifecycle state snapshot: PENDING while unresolved. */
        public ApprovalHandler.ApprovalState peekState() {
            return recordView != null ? recordView.getState() : ApprovalHandler.ApprovalState.PENDING;
        }
    }

    /** The approval awaiting the user, or {@code null} when nothing is parked. */
    public PendingApproval currentPendingApproval() {
        if (permissions == null) {
            return null;
        }
        PermissionRequest request = permissions.currentPendingRequest();
        return request == null ? null : new PendingApproval(request);
    }

    /**
     * Explicitly resolves a pending approval by its requestId (item 16):
     * the UI calls this after the user answers the dialog. Returns false
     * when no matching PENDING request exists (stale dialog / wrong run).
     */
    public boolean resolveApproval(String requestId, PermissionDecision decision) {
        if (permissions == null) {
            return false;
        }
        // The layer owns the resolution channel — works with every handler
        // implementation (anonymous, resolver-based, test doubles).
        return permissions.resolve(requestId, decision);
    }

    /** Cancels a pending approval (user dismissed the dialog). */
    public boolean cancelApproval(String requestId) {
        if (permissions == null) {
            return false;
        }
        return permissions.cancel(requestId);
    }

    /** The immutable permission config in force, or {@code null} (no layer). */
    public PermissionConfig permissionConfig() {
        return permissions == null ? null : permissions.permissionConfig();
    }

    /**
     * The UI switches the permission mode (workspace+ask, read-only, full
     * access). Takes effect on the next tool check of the current run.
     */
    public void updatePermissionConfig(PermissionConfig config) {
        if (permissions != null) {
            permissions.updatePermissionConfig(config);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Item 10: freezes THIS run's provider/model/mode from the host's
     * current selection, ONCE. The frozen identity is what every later turn
     * of the run uses — mid-run preference changes only affect the NEXT run.
     * Pure-JVM safe: unavailable preferences yield {@code null} (tests).
     */
    private static AiOperationContext captureOperationIdentity() {
        try {
            android.content.SharedPreferences prefs = AiChatSettingsHelper.prefs(
                    pro.sketchware.SketchApplication.getContext());
            AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
            String providerId = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "").trim();
            String modelName = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "").trim();
            if (providerId.isEmpty() || modelName.isEmpty()) {
                return null;
            }
            return AiOperationContext.builder()
                    .providerId(providerId)
                    .modelName(modelName)
                    .chatMode(AiChatSettingsHelper.getChatMode(prefs))
                    .build();
        } catch (Exception unavailable) {
            return null; // JVM tests / headless: no frozen identity available
        }
    }

    /**
     * Codex context assembly (item 12 of the migration): structured
     * fragments — agent instructions, AGENTS.md hierarchy, workspace
     * snapshot and task memory — rendered in a fixed order from THIS run's
     * {@link RunContext}. Never reads the global active workspace.
     *
     * <p>Skills are appended as a separate, optional block: the run's
     * {@link SkillPromptBlocks} are rendered after the base prompt whenever
     * they are non-empty (single injection point for the whole run).</p>
     */
    private String resolveSystemPrompt(Agent agent, RunContext context, SkillPromptBlocks skills) {
        String base = includeProjectInstructions
                ? RunContextAssembler.assemble(agent, context).renderPrompt()
                : agent.instructions();
        if (skills == null || skills.isEmpty()) {
            return base;
        }
        String skillBlock = skills.renderAll();
        if (skillBlock.isEmpty()) {
            return base;
        }
        return base + "\n\n" + skillBlock;
    }

    /**
     * Resolve os blocos de Skills desta execução. Nunca lança: qualquer falha
     * vira blocos vazios, preservando uma conversa funcional mesmo com
     * catálogo corrompido ou repositório indisponível.
     */
    private SkillPromptBlocks skillBlocksFor(String scId, String userText) {
        if (skillFlow == null) {
            lastSkillBlocks = SkillPromptBlocks.empty();
            return lastSkillBlocks;
        }
        try {
            SkillContext context = skillFlow.resolveFor(scId, userText);
            lastSkillBlocks = context != null && context.blocks() != null
                    ? context.blocks()
                    : SkillPromptBlocks.empty();
            return lastSkillBlocks;
        } catch (Exception e) {
            lastSkillBlocks = SkillPromptBlocks.empty();
            return lastSkillBlocks;
        }
    }

    /** Blocos de Skills da última execução (inspeção/host). */
    public SkillPromptBlocks lastSkillBlocks() {
        return lastSkillBlocks;
    }

    /** ~4 chars per token, same estimate used by AgentManager. */
    private static long estimatedInputTokens(List<ChatMessage> history) {
        long chars = 0;
        for (ChatMessage message : snapshot(history)) {
            String content = message.getLlmContent();
            if (content != null) {
                chars += content.length();
            }
        }
        return chars / 4 + 1;
    }

    /**
     * Registry-backed model catalog for this turn: exactly the registry's
     * DIRECT tools. The catalog has a single source
     * ({@link ToolCatalog#from(AxionToolRegistry)}) — no agent-level tool
     * arrays participate.
     */
    private synchronized ToolCatalog toolCatalogFor() {
        return ToolCatalog.from(registry);
    }

    /**
     * The single execution path (Codex {@code router.rs} parity): routes
     * every structured call through the canonical {@link AxionToolRouter}
     * and returns the handoff target (or {@code null}). The router resolves
     * registry lookup → exposure → permission → sandbox → executor; handoffs
     * are detected from the registration's own metadata
     * ({@link ToolRegistration#isHandoff()} /
     * {@link ToolRegistration#handoffTarget()}).
     */
    private Agent executeTools(Agent activeAgent, AgentSession session, RunContext context, String scId,
                               List<ToolCall> structuredCalls, List<ChatMessage> history) {
        Agent handoffTarget = null;
        for (ToolCall toolCall : structuredCalls) {
            if (cancelRequested) {
                session.setStatus(AgentSession.Status.CANCELLED);
                emit(new AgentEvent.RunCompleted(scId, false, "Run cancelled"));
                return null;
            }
            AxionToolRouter.Route route = new AxionToolRouter.Route(
                    toolCall.getId(), toolCall.getName(), toolCall.getArguments());
            AxionToolRouter.Routed routed = router.route(
                    route, scId, context, new AxionToolRouter.LoopHooks() {
                        @Override
                        public void onToolCallStarted(AxionToolRouter.Route c, ToolRegistration t) {
                            context.incrementToolCalls();
                            emit(new AgentEvent.ToolCallStarted(scId, t.qualifiedName(), toolCall));
                        }

                        @Override
                        public void onToolCallCompleted(AxionToolRouter.Route c, ToolRegistration t, AgentToolResult r) {
                            if (r != null) {
                                emit(new AgentEvent.ToolCallCompleted(scId,
                                        t != null ? t.qualifiedName() : c.toolName(), toolCall, r));
                            }
                        }
                    });
            if (routed.wasDeduplicated()) {
                continue;
            }
            AgentToolResult result = routed.result();
            if (result != null) {
                appendToolResult(history, toolCall, result.output());
            }
            ToolRegistration registration = routed.registration();
            if (registration != null && registration.isHandoff()) {
                // Handoff registrations transfer control to the named agent.
                Agent target = findHandoffTargetByName(activeAgent, registration.handoffTarget());
                if (target != null) {
                    handoffTarget = target;
                }
                break;
            }
        }
        return handoffTarget;
    }

    /** Upper bound for AGENTS.md injection (~600 tokens). */
    private static final int PROJECT_INSTRUCTIONS_MAX_CHARS = 2400;

    /** Workspace-relative cwd of the resolved run (root when absent). */
    private static String cwdOf(RunContextFactory.Resolved resolved) {
        return resolved != null
                && resolved.workspace() != null
                && resolved.workspace().cwd() != null
                ? resolved.workspace().cwd()
                : "";
    }

    /** Stable environment id of the resolved run (workspace id, else scId). */
    private static String environmentIdOf(RunContextFactory.Resolved resolved) {
        if (resolved != null && resolved.workspace() != null
                && !resolved.workspace().workspaceId().isEmpty()) {
            return resolved.workspace().workspaceId();
        }
        return "";
    }

    private void emit(AgentEvent event) {
        events.emit(event);
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // Removed: RECOVERY_NUDGE constant (Codex alignment)
    // ------------------------------------------------------------------
    // The runtime no longer inserts artificial user messages to force mutations.
    // Text-only responses are valid model decisions, not error conditions.

    private static Agent findHandoffTargetByName(Agent activeAgent, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            return null;
        }
        for (Agent candidate : activeAgent.handoffs()) {
            if (targetName.equals(candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Records a tool call's result into the shared conversation history.
     *
     * <p>{@code history} is the SAME {@code List<ChatMessage>} instance the
     * host UI (see {@code AgentManager.HostBridge}) renders directly and
     * already added a {@code TYPE_TOOL} bubble to on {@code
     * AgentEvent.ToolCallStarted}, keyed by {@code call.getId()}. This must
     * therefore UPDATE that same {@link ChatMessage} in place rather than
     * appending a second one for the same call id — appending a second
     * message here previously caused every tool execution to render as two
     * duplicate cards in the chat (one created by the UI on start, one
     * created here on completion).
     *
     * <p>Identity is strictly the tool call id ({@code call.getId()}), never
     * a heuristic based on tool name/arguments/timestamp: two distinct calls
     * with identical name+args (e.g. the model retrying the same search)
     * must still produce two separate messages/cards, while a single call's
     * start+completion must always collapse onto one.</p>
     */
    static void appendToolResult(List<ChatMessage> history, ToolCall call, String output) {
        boolean isError = output != null && output.startsWith("Error");
        synchronized (history) {
            ChatMessage existing = findToolMessageById(history, call.getId());
            if (existing != null) {
                existing.setToolResult(output);
                existing.setToolRunning(false);
                existing.setToolError(isError);
                return;
            }
            // Defensive fallback only: reached when nothing already created a
            // visual bubble for this call id (e.g. a headless/history-only
            // run with no HostBridge attached). Still keyed by the call id.
            ChatMessage toolMessage = new ChatMessage("", ChatMessage.TYPE_TOOL, System.currentTimeMillis());
            toolMessage.setToolName(call.getName());
            toolMessage.setToolArgs(call.getArguments());
            toolMessage.setToolId(call.getId());
            toolMessage.setToolRunning(false);
            toolMessage.setToolResult(output);
            toolMessage.setToolError(isError);
            history.add(toolMessage);
        }
    }

    /**
     * Finds the existing {@code TYPE_TOOL} message for a call id, searching
     * from the end since the matching bubble is always among the most
     * recently appended messages. Callers must hold the lock on {@code
     * history} (it is a shared, cross-thread mutable list).
     */
    static ChatMessage findToolMessageById(List<ChatMessage> history, String callId) {
        if (callId == null || callId.isEmpty() || history == null) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage candidate = history.get(i);
            if (candidate.isTool() && callId.equals(candidate.getToolId())) {
                return candidate;
            }
        }
        return null;
    }

    private static String latestUserText(List<ChatMessage> messages) {
        List<ChatMessage> copy = snapshot(messages);
        for (int i = copy.size() - 1; i >= 0; i--) {
            ChatMessage message = copy.get(i);
            if (message.getType() == ChatMessage.TYPE_USER) {
                return message.getMessage();
            }
        }
        return "";
    }

    /**
     * A stable copy of the shared history. The host adds and removes bubbles from its
     * event thread while holding the list's lock (as {@link #appendToolResult} does), so
     * iterating the live list here can throw ConcurrentModificationException mid-run.
     */
    static List<ChatMessage> snapshot(List<ChatMessage> history) {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /** Fluent builder for {@link AgentRuntime}. */
    public static final class Builder {
        private final AgentLlmGateway gateway;
        private EventStream events = new EventStream();
        private PermissionLayer permissions;
        private final List<Guardrail> inputGuardrails = new ArrayList<>();
        private int maxTurns = DEFAULT_MAX_TURNS;
        private AiOperationContext operationContext;
        private RunBudget budget;
        private int maxOutputTokensPerTurn = 2048;
        private boolean includeProjectInstructions = true;
        // Removed: expectFileMutations field (Codex alignment)
        private AxionToolRegistry registry;
        private SkillFlow skillFlow = SkillFlow.NOOP;

        public Builder(AgentLlmGateway gateway) {
            if (gateway == null) {
                throw new IllegalArgumentException("gateway is required");
            }
            this.gateway = gateway;
        }

        public Builder events(EventStream events) {
            this.events = events == null ? new EventStream() : events;
            return this;
        }

        public Builder permissions(PermissionLayer permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder inputGuardrails(Guardrail... guardrails) {
            for (Guardrail guardrail : guardrails) {
                if (guardrail != null) {
                    inputGuardrails.add(guardrail);
                }
            }
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        /** M3: per-run token budget (reserve/settle/block). */
        public Builder budget(RunBudget budget) {
            this.budget = budget;
            return this;
        }

        /** M3: worst-case output reservation per turn. */
        public Builder maxOutputTokensPerTurn(int maxOutputTokensPerTurn) {
            this.maxOutputTokensPerTurn = Math.max(16, maxOutputTokensPerTurn);
            return this;
        }

        /** M7: inject workspace AGENTS.md into the system prompt (default true). */
        public Builder includeProjectInstructions(boolean include) {
            this.includeProjectInstructions = include;
            return this;
        }

        /**
         * Fluxo oficial de Skills desta runtime (padrão: desligado). Resolvido
         * uma vez por execução; implementações devem nunca lançar (o runtime
         * já defende contra falhas). Em produção é construído por
         * {@code SkillManager.appFlow(context)}.
         */
        public Builder skills(SkillFlow flow) {
            this.skillFlow = flow == null ? SkillFlow.NOOP : flow;
            return this;
        }

        /**
         * Freezes the provider/model/mode of every run of this runtime
         * (items 10/11). Host preference changes mid-run do not leak in.
         */
        public Builder operationContext(AiOperationContext context) {
            this.operationContext = context;
            return this;
        }

        /**
         * The single model-facing tool source. MANDATORY: the runtime's
         * catalog is exactly {@link ToolCatalog#from(AxionToolRegistry)} and
         * every execution routes through {@link AxionToolRouter}; there is no
         * legacy path. Fails fast when no registry is wired.
         */
        public Builder toolRegistry(AxionToolRegistry registry) {
            this.registry = registry;
            return this;
        }

        public AgentRuntime build() {
            if (registry == null) {
                throw new IllegalArgumentException(
                        "A toolRegistry is required: the runtime's catalog and execution "
                                + "path have exactly one source (AxionToolRegistry → ToolCatalog → "
                                + "AxionToolRouter). Register the model-facing tools before building.");
            }
            return new AgentRuntime(this);
        }
    }
}
