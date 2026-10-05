package pro.sketchware.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.annotation.Nullable;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.io.File;

import pro.sketchware.R;
import pro.sketchware.chat.agent.AgentMemory;
import pro.sketchware.chat.agent.AgentRunGuard;
import pro.sketchware.chat.agent.MultiAgentOrchestrator;
import pro.sketchware.chat.agent.MultiAgentPolicy;
import pro.sketchware.chat.agent.PatternMatcher;
import pro.sketchware.chat.agent.RetryManager;
import pro.sketchware.chat.agent.TaskPlanner;
import pro.sketchware.chat.port.VoidPortDiffService;
import pro.sketchware.chat.port.VoidPortConvertToLlmMessageService;
import pro.sketchware.chat.port.VoidPortModelCapabilities;
import pro.sketchware.chat.port.VoidPortSettings;
import pro.sketchware.chat.Tool;
import pro.sketchware.chat.AiProviderService;
import pro.sketchware.chat.AiRequestHandle;
import pro.sketchware.chat.ProjectPathResolver;
import pro.sketchware.chat.toolcalling.ToolArgumentsValidator;

/**
 * Orchestrates the chat loop, approval flow, checkpoints, diff previews and
 * cancellation of the active stream/tool execution.
 */
public class AgentManager {

    private static final int MAX_PREVIEW_LINES = 48;
    private static final long STREAM_COALESCE_MS = 120L;
    /**
     * Retry na camada do AgentManager foi reduzido de 3 para 1 porque agora as camadas
     * inferiores (AiStreamingTransport e AiProviderService) já implementam retry
     * centralizado com AiRetryController (4 tentativas totais).
     * Manter múltiplas camadas de retry causava até 27 tentativas (3×3×3).
     */
    private static final int MAX_LLM_ATTEMPTS = 1;
    private static final long LLM_RETRY_DELAY_MS = 2500L;
    private static final int MAX_FINISH_REJECTIONS = 3;
    /** A truncated successful response may be continued, but never indefinitely. */
    private static final int MAX_OUTPUT_CONTINUATIONS = 2;
    /** Bounded maker-checker retries prevent reviewer feedback loops. */
    private static final int MAX_MULTI_AGENT_REVIEW_ROUNDS = 2;
    /**
     * Regex that strips any characters that are not valid in a tool name.
     * Protects against models (especially free/quantized ones) leaking internal
     * tokens into tool names, e.g. {@code edit_file<|channel|>commentary}.
     * Valid tool names contain only ASCII word chars, hyphens and dots.
     */
    private static final java.util.regex.Pattern TOOL_NAME_SANITIZER =
            java.util.regex.Pattern.compile("[^a-zA-Z0-9_\\-.]");

    private final Context context;
    private final String scId;
    private final List<ChatMessage> messages;
    private final AgentListener listener;
    private final AiProviderService aiService;
    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final Handler mainHandler;
    private final Handler streamCoalesceHandler;
    private final ChatCheckpointManager checkpointManager;
    private AiRequestHandle currentRequestHandle;

    private boolean running = false;
    private ChatMessage pendingToolMessage;
    private ChatMessage currentStreamingMessage;
    private int runVersion = 0;
    private int pendingToolLoopStep = -1;
    private final AgentRunGuard runGuard = new AgentRunGuard();
    
    /** Contexto imutável da operação atual de IA (modelo, provedor, requestId). */
    private AiOperationContext currentOperationContext;
    
    /** Abort the run after this many consecutive failing tool executions. */
    private static final int MAX_CONSECUTIVE_TOOL_FAILURES = 4;
    private int consecutiveToolFailures = 0;

    // ---- History compaction (context only; the visible chat is untouched) ----
    /** The character estimator used by the documented compaction strategy. */
    private static final int COMPACTION_CHARS_PER_TOKEN = 4;
    /** Summarization runs only once the effective history exceeds its token budget. */
    private static final int COMPACTION_TRIGGER_PERCENT = 75;
    /** Keep Android requests responsive even when a provider advertises a huge context window. */
    private static final int MAX_COMPACTION_TRIGGER_TOKENS = 16_000;
    private static final int DEFAULT_TOTAL_CONTEXT_TOKENS = 6_000;
    private static final int DEFAULT_SYSTEM_CONTEXT_TOKENS = 2_400;
    private static final int DEFAULT_HISTORY_CONTEXT_TOKENS = 3_000;
    private static final int DEFAULT_COMPILE_ERROR_TOKENS = 500;
    private static final int MAX_CONTEXT_TOKENS = 128_000;
    /** Recent messages always kept verbatim in the context. */
    private static final int COMPACT_KEEP_TAIL = 12;
    /** Max chars of transcript sent to the summarizer. */
    private static final int COMPACT_TRANSCRIPT_MAX_CHARS = 32_000;
    /** Leaves room for fresh messages when an existing summary is summarized again. */
    private static final int COMPACT_SUMMARY_MAX_CHARS = 12_000;
    private String historySummary = "";
    private int historyCompactedUntil = 0;
    /** Prevents the same unchanged history from immediately re-entering compaction. */
    private int lastCompactionMessageCount = -1;
    private boolean compactionInFlight = false;
    private boolean compactionFailed = false;

    /** Checkpoint message shared by every file mutation of the current run (turn-level rollback). */
    private ChatMessage currentRunCheckpointMessage;
    private ChatInteractionTrace interactionTrace;
    private AgentMemory agentMemory;
    private PatternMatcher.Result requestPattern;
    private TaskPlanner.Plan taskPlan;
    private String pendingAgentFeedback = "";
    private int finishValidationFailures = 0;
    private int outputContinuationCount = 0;
    private String multiAgentGuidance = "";
    private boolean multiAgentEnabledForRun = false;
    private String multiAgentModeForRun = MultiAgentPolicy.MODE_AUTO;
    private String multiAgentDecisionReason = "not_evaluated";
    private boolean multiAgentPrepared = false;
    private boolean multiAgentPreparationInFlight = false;
    private boolean multiAgentReviewInFlight = false;
    private int multiAgentReviewRounds = 0;
    /** Tools are removed only for the terminal response forced by the circuit breaker. */
    private boolean finalResponseOnly = false;
    private String finalResponseReason = "";
    private boolean finalResponseForcedByGuard = false;
    /** A stale mutation is discarded and must be regenerated after a fresh read. */
    private boolean awaitingRecoveredMutation = false;

    public interface AgentListener {
        void onMessageAdded(ChatMessage message);
        void onMessageUpdated(ChatMessage message);
        void onMessageRemoved(ChatMessage message, int index);
        void onStatusChanged(String status);
        void onDebug(String message);
        void onProcessingFinished();
        void onToolExecuted(String toolName, boolean isMutation);
        void onError(String error);

        /** Persists context-only compaction without changing the visible chat. */
        default void onCompactionStateChanged(String summary, int compactedUntil) {
        }

        /** Erro final estruturado para a interface, com detalhes técnicos opcionais. */
        default void onUserFacingError(UserFacingError error, @Nullable String requestId) {
            String message = error == null
                    ? "Não foi possível concluir a operação."
                    : error.getTitle() + ": " + error.getMessage();
            onError(message);
        }

        // ------------------------------------------------------------------
        // Structured event surface (items 8/9/37): the UI consumes typed
        // runtime events instead of inferring tool/mutation/completion state
        // from message text. Defaults keep legacy listeners compiling.
        // ------------------------------------------------------------------

        /** Streaming delta of assistant text (UI appends to the live message). */
        default void onAssistantDelta(@Nullable String delta) {
        }

        /** Complete assistant message for the turn (only when NOT streamed). */
        default void onAssistantMessage(@Nullable String content) {
        }

        /** A structured tool call started executing. */
        default void onToolCallStarted(@Nullable String toolName, @Nullable String callId) {
        }

        /** A structured tool call finished (success/failure + result text). */
        default void onToolCallCompleted(@Nullable String toolName, @Nullable String callId,
                                         boolean success, @Nullable String resultText) {
        }

        /** A file inside the run's workspace changed (diff/panels refresh). */
        default void onFileChanged(@Nullable String path, @Nullable String kind,
                                   @Nullable String toolName) {
        }

        /** The runtime parked a tool call waiting for the user's decision. */
        default void onApprovalRequired(@Nullable String requestId, @Nullable String toolName) {
        }

        /** A pending approval reached a terminal state (ALLOWED/DENIED/...). */
        default void onPermissionResolved(@Nullable String toolName, boolean allowed,
                                          @Nullable String stateName) {
        }
    }

    public AgentManager(Context context, String scId, List<ChatMessage> messages, AgentListener listener) {
        this.context = context.getApplicationContext();
        this.scId = scId;
        this.messages = messages;
        this.listener = listener;
        this.aiService = AiProviderService.getInstance();
        this.multiAgentOrchestrator = new MultiAgentOrchestrator(this.aiService);

        this.mainHandler = new Handler(Looper.getMainLooper());
        this.streamCoalesceHandler = new Handler(Looper.getMainLooper());
        this.checkpointManager = new ChatCheckpointManager(context);
    }

    /**
     * UI factory (item 1 of the migration): builds the AgentManager as a
     * UI CONTROLLER for an already-assembled v2
     * {@link pro.sketchware.chat.agentsdk.AgentRuntime}. The
     * runtime is the ONLY execution engine; this controller exposes the
     * preserved surface (approve/reject, cancel, checkpoint, compaction
     * restore) and bridges structured runtime events into the
     * {@link AgentListener} surface through the SAME
     * {@link pro.sketchware.chat.agentsdk.EventStream} the
     * runtime emits to.
     */
    public static AgentManager forUi(Context context, String scId, List<ChatMessage> messages,
                                     pro.sketchware.chat.agentsdk.AgentRuntime runtime,
                                     AgentListener listener) {
        return new AgentManager(context, scId, messages, listener, runtime);
    }

    private AgentManager(Context context, String scId, List<ChatMessage> messages,
                         AgentListener listener,
                         pro.sketchware.chat.agentsdk.AgentRuntime runtime) {
        this.context = context.getApplicationContext();
        this.scId = scId;
        this.messages = messages;
        this.listener = listener;
        this.aiService = AiProviderService.getInstance();
        this.multiAgentOrchestrator = new MultiAgentOrchestrator(this.aiService);

        this.mainHandler = new Handler(Looper.getMainLooper());
        this.streamCoalesceHandler = new Handler(Looper.getMainLooper());
        this.checkpointManager = new ChatCheckpointManager(context);
        this.uiRuntime = runtime;
        // The bridge owns the run presentation: it adds user/assistant/tool
        // messages to the SAME list the UI renders and translates runtime
        // events into {@link AgentListener} callbacks (items 1/8/9).
        this.uiHostBridge = new HostBridge(runtime, () -> uiAgentForRun(), listener, scId,
                null, null, null, this::removeMessage);
        this.uiHostBridge.attachMessages(messages);
    }

    /** The runtime-backed engine serving the chat screen (null on legacy construction). */
    private pro.sketchware.chat.agentsdk.AgentRuntime uiRuntime;
    private HostBridge uiHostBridge;
    private volatile pro.sketchware.chat.agentsdk.Agent uiAgentForRunCache;

    private pro.sketchware.chat.agentsdk.Agent uiAgentForRun() {
        return uiAgentForRunCache;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * The UI switches the permission mode (workspace+ask, read-only, full
     * access). Forwarded to the v2 runtime layer; takes effect on the next
     * tool check.
     */
    public void setPermissionConfig(pro.sketchware.chat.agentsdk.PermissionConfig config) {
        if (uiRuntime != null) {
            uiRuntime.updatePermissionConfig(config);
        }
    }

    @Nullable
    public String getCurrentOperationId() {
        return currentOperationContext == null ? null : currentOperationContext.getRequestId();
    }

    /** Restores the local context checkpoint for the conversation being opened. */
    public void restoreCompactionState(@Nullable String summary, int compactedUntil) {
        if (running) return;
        historySummary = limitCompactionSummary(summary);
        historyCompactedUntil = Math.max(0, Math.min(compactedUntil, messages.size()));
        lastCompactionMessageCount = historySummary.isEmpty() ? -1 : messages.size();
        compactionFailed = false;
    }

    public boolean hasCheckpoint() {
        return checkpointManager.hasCheckpoint(messages);
    }

    public ChatCheckpointManager.RollbackResult rollbackLastCheckpoint() {
        return checkpointManager.rollbackLatestCheckpoint(scId, messages);
    }

    // ------------------------------------------------------------------
    // Single execution identity (context-model migration, item 3): the
    // workspace of THIS run is pinned from the scId before the loop starts,
    // so every tool, ContextBuilder read and mutation goes to the run's
    // filesystem — never to whatever workspace the UI has active.
    // ------------------------------------------------------------------
    private volatile AutoCloseable runIdentityPin;

    private void beginRunIdentity() {
        endRunIdentity();
        try {
            pro.sketchware.chat.agentsdk.RunContextFactory.Resolved resolved =
                    pro.sketchware.chat.agentsdk.RunContextFactory.resolve(scId);
            runIdentityPin = pro.sketchware.chat.agentsdk.RuntimeFileContext.pin(
                    "host_" + scId, resolved.workspace(), resolved.filesystem());
        } catch (Exception e) {
            runIdentityPin = null; // legacy global fallback stays active
        }
    }

    private void endRunIdentity() {
        AutoCloseable pin = runIdentityPin;
        runIdentityPin = null;
        if (pin != null) {
            try {
                pin.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void processUserMessage(String userText) {
        processUserMessage(userText, null);
    }

    public void processUserMessage(String userText, String contextPayload) {
        processUserMessage(userText, contextPayload, null);
    }

    /**
     * Item 1 of the migration: the UI's user turn runs EXCLUSIVELY through
     * the v2 runtime. The legacy in-class agent loop (startAgentLoop →
     * queuedToolCalls → executeToolCall) is no longer an execution path —
     * this class keeps only controller responsibilities.
     */
    public void processUserMessage(String userText, String contextPayload, List<ChatReference> stagingSelections) {
        if (running) {
            ChatFlowLogger.event("agent", "message_ignored", "state=running");
            return;
        }
        if (uiRuntime == null || uiHostBridge == null) {
            throw new IllegalStateException(
                    "AgentManager without a v2 runtime: use AgentManager.forUi(...). "
                            + "The legacy loop is retired.");
        }

        String displayText = userText == null ? "" : userText.trim();
        ChatFlowLogger.event("agent", "turn_started", "chars=" + displayText.length()
                + ", references=" + (stagingSelections == null ? 0 : stagingSelections.size()));

        running = true;
        requestPattern = PatternMatcher.analyze(displayText, contextPayload, stagingSelections);
        captureOperationContextForRun();
        beginRunIdentity();

        uiAgentForRunCache = pro.sketchware.chat.agentsdk.Agent.Builder.forName("coordinator",
                "You are the workspace coordinator. Use the available tools to "
                        + "explore, read, and modify project files to complete the user's task.")
                .build();

        // The user message is built here (llmContent/references preserved) and
        // handed to the bridge; the runtime receives the WHOLE conversation as
        // history, so multi-turn context survives the v2 migration.
        ChatMessage userMsg = new ChatMessage(displayText, true, System.currentTimeMillis());
        userMsg.setContextPayload(contextPayload);
        userMsg.setStagingSelections(stagingSelections);
        userMsg.setLlmContent(ChatReferenceManager.buildLlmUserContent(displayText, contextPayload));
        messages.add(userMsg);

        final int version = ++runVersion;
        executorForRuns().execute(() -> {
            try {
                uiHostBridge.processUserMessage(userMsg);
                mainHandler.post(() -> {
                    if (version == runVersion) {
                        running = false;
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (version == runVersion) {
                        running = false;
                        listener.onError(e.getMessage() == null ? "Run failed" : e.getMessage());
                    }
                });
            }
        });
    }

    private java.util.concurrent.ExecutorService runExecutor;

    private synchronized java.util.concurrent.ExecutorService executorForRuns() {
        if (runExecutor == null) {
            runExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "axion-ui-run");
                t.setDaemon(true);
                return t;
            });
        }
        return runExecutor;
    }

    public void continueFromExistingMessage(@Nullable ChatMessage sourceMessage) {
        if (running) {
            return;
        }
        // Regenerate/continue runs through the SAME v2 runtime as a fresh
        // message: the existing conversation (already containing the source
        // message) is replayed as history, so the model continues from where
        // it stopped. No legacy loop is involved.
        String displayText = sourceMessage == null ? findLatestUserMessage() : sourceMessage.getDisplayContent();
        List<ChatReference> selections = sourceMessage == null ? null : sourceMessage.getStagingSelections();
        String contextPayload = sourceMessage == null ? null : sourceMessage.getContextPayload();
        initializeAgentExecution(displayText, contextPayload, selections);
        if (uiRuntime == null || uiHostBridge == null) {
            throw new IllegalStateException(
                    "AgentManager without a v2 runtime: use AgentManager.forUi(...). "
                            + "The legacy loop is retired.");
        }
        requestPattern = PatternMatcher.analyze(displayText, contextPayload, selections);
        captureOperationContextForRun();
        beginRunIdentity();
        beginInteractionTrace(++runVersion, displayText, selections);

        uiAgentForRunCache = pro.sketchware.chat.agentsdk.Agent.Builder.forName("coordinator",
                "You are the workspace coordinator. Use the available tools to "
                        + "explore, read, and modify project files to complete the user's task.")
                .build();

        running = true;
        final int version = runVersion;
        executorForRuns().execute(() -> {
            try {
                uiHostBridge.processHistory(messages);
                mainHandler.post(() -> {
                    if (version == runVersion) {
                        running = false;
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (version == runVersion) {
                        running = false;
                        listener.onError(e.getMessage() == null ? "Run failed" : e.getMessage());
                    }
                });
            }
        });
    }

    public boolean cancelCurrentRun() {
        if (!running) {
            return false;
        }

        // v2 runtime cancellation (item 33): the gateway cancels the provider
        // request, parked approvals resolve as CANCELLED and the run thread
        // unwinds; the run thread itself completes the UI presentation once
        // runtime.run returns. Nothing here may execute tools or continue the
        // run afterwards — there is no second executor left.
        runVersion++;
        if (uiHostBridge != null) {
            uiHostBridge.cancel();
        } else if (uiRuntime != null) {
            uiRuntime.cancel();
        }
        if (currentOperationContext != null) {
            SecureLogger.logCancellation(currentOperationContext.getRequestId(),
                    CancellationReason.USER_REQUESTED);
        }
        return true;
    }

    /**
     * Invalidates the complete workflow before clearing or switching a thread.
     * A reset must also abort an active run; otherwise its late callback can
     * append an old response to the newly opened conversation.
     */
    public void resetConversationState() {
        boolean wasActive = running;
        // v2 teardown: cancel parked approvals with the run.
        if (uiRuntime != null) {
            uiRuntime.cancel();
        }
        runVersion++;
        multiAgentOrchestrator.reset();
        mainHandler.removeCallbacksAndMessages(null);
        historySummary = "";
        historyCompactedUntil = 0;
        lastCompactionMessageCount = -1;
        compactionInFlight = false;
        compactionFailed = false;
        agentMemory = null;
        requestPattern = null;
        taskPlan = null;
        ChatPlanManager.clearExecutionPlan(scId);
        ChatPlanManager.clearModelPlan(scId);
        pendingAgentFeedback = "";
        finishValidationFailures = 0;
        outputContinuationCount = 0;
        runGuard.reset();
        finalResponseOnly = false;
        finalResponseReason = "";
        finalResponseForcedByGuard = false;
        awaitingRecoveredMutation = false;
        pendingToolLoopStep = -1;
        currentRunCheckpointMessage = null;
        interactionTrace = null;
        currentStreamingMessage = null;
        currentOperationContext = null;
        multiAgentOrchestrator.endOperation();
        multiAgentGuidance = "";
        multiAgentEnabledForRun = false;
        multiAgentPrepared = false;
        multiAgentPreparationInFlight = false;
        multiAgentReviewInFlight = false;
        multiAgentReviewRounds = 0;
        running = false;
        if (wasActive) {
            listener.onProcessingFinished();
        }
    }

    /**
     * Legacy-free host bridge: serves the same {@link AgentListener} surface
     * the retired loop served, but every turn is executed by the v2
     * {@link pro.sketchware.chat.agentsdk.AgentRuntime}. Process
     * calls block until the run finishes (the UI already calls them off the
     * main thread); UI callbacks are posted to the main looper.
     *
     * <p>Item 8 of the migration: the bridge SUBSCRIBES to the runtime's
     * {@link pro.sketchware.chat.agentsdk.EventStream} and
     * translates every typed event into UI state. The runtime is NOT a
     * black box that only returns a final string: streaming deltas, tool
     * activity, approvals, FileChanged and errors all flow to the UI through
     * this subscription, scoped to the events' {@code scId} (item 35) so a
     * stale run's events can never render into a newer conversation.</p>
     */
    public static final class HostBridge implements AutoCloseable {
        private final pro.sketchware.chat.agentsdk.AgentRuntime runtime;
        private final java.util.function.Supplier<pro.sketchware.chat.agentsdk.Agent> agentSupplier;
        private final AgentListener listener;
        private final String scId;
        private final android.content.Context appContext;
        private final java.util.concurrent.Executor uiExecutor;
        private final pro.sketchware.chat.agentsdk.EventStream eventStream;
        private final AutoCloseable eventSubscription;

        // ---- Run presentation state, driven ONLY by structured events ----
        // (items 8/9/35/37: the UI never infers tool/completion state from text;
        // every fact comes from the runtime's AgentEvent stream).
        /** The conversation list shared with the host UI (nullable in tests). */
        private java.util.List<ChatMessage> messages;
        /** Removes an empty placeholder from the conversation on fatal errors. */
        private final java.util.function.Consumer<ChatMessage> placeholderRemover;
        private ChatMessage liveAssistant;
        private final StringBuilder liveText = new StringBuilder();
        private final StringBuilder liveReasoning = new StringBuilder();
        private final java.util.Map<String, ChatMessage> toolBubbles =
                new java.util.LinkedHashMap<>();
        private String pendingApprovalRequestId = "";
        private volatile String lastOutput = "";
        private volatile String lastStatus = "";
        /** Single reducer: every run event derives the composer status. */
        private final pro.sketchware.chat.agentsdk.RunStatusReducer statusReducer =
                new pro.sketchware.chat.agentsdk.RunStatusReducer();

        /** Production constructor: UI callbacks are posted to the main looper. */
        public HostBridge(pro.sketchware.chat.agentsdk.AgentRuntime runtime,
                          java.util.function.Supplier<pro.sketchware.chat.agentsdk.Agent> agentSupplier,
                          AgentListener listener,
                          String scId) {
            this(runtime, agentSupplier, listener, scId, null, null, null, null);
        }

        /** Test/headless constructor: inject the callback executor. */
        public HostBridge(pro.sketchware.chat.agentsdk.AgentRuntime runtime,
                          java.util.function.Supplier<pro.sketchware.chat.agentsdk.Agent> agentSupplier,
                          AgentListener listener,
                          String scId,
                          java.util.concurrent.Executor uiExecutor) {
            this(runtime, agentSupplier, listener, scId, uiExecutor, null, null, null);
        }

        /** Full constructor: the bridge takes ownership of the stream subscription. */
        public HostBridge(pro.sketchware.chat.agentsdk.AgentRuntime runtime,
                          java.util.function.Supplier<pro.sketchware.chat.agentsdk.Agent> agentSupplier,
                          AgentListener listener,
                          String scId,
                          java.util.concurrent.Executor uiExecutor,
                          pro.sketchware.chat.agentsdk.EventStream eventStream,
                          android.content.Context appContext) {
            this(runtime, agentSupplier, listener, scId, uiExecutor, eventStream, appContext, null);
        }

        public HostBridge(pro.sketchware.chat.agentsdk.AgentRuntime runtime,
                          java.util.function.Supplier<pro.sketchware.chat.agentsdk.Agent> agentSupplier,
                          AgentListener listener,
                          String scId,
                          java.util.concurrent.Executor uiExecutor,
                          pro.sketchware.chat.agentsdk.EventStream eventStream,
                          android.content.Context appContext,
                          java.util.function.Consumer<ChatMessage> placeholderRemover) {
            this.runtime = runtime;
            this.agentSupplier = agentSupplier;
            this.listener = listener;
            this.scId = scId == null ? "" : scId;
            this.appContext = appContext;
            this.uiExecutor = uiExecutor != null
                    ? uiExecutor
                    : command -> new Handler(Looper.getMainLooper()).post(command);
            this.eventStream = eventStream != null
                    ? eventStream
                    : pro.sketchware.chat.agentsdk.AgentRuntimeEventAccess.eventStreamOf(runtime);
            this.placeholderRemover = placeholderRemover == null ? m -> { } : placeholderRemover;
            // Item 8/9/35: one subscription, run-scoped events, structured UI.
            this.eventSubscription = this.eventStream.subscribe(this::onAgentEvent);
        }

        /** Shares the host conversation list (user turn + tool bubbles + live
         *  assistant all land here; ChatActivity renders this same list). */
        public void attachMessages(java.util.List<ChatMessage> messages) {
            this.messages = messages;
        }

        /** Runs one user turn on the calling thread; UI updates go to main.
         *  Convenience overload for tests/headless hosts: the user message is
         *  created here and appended to the shared conversation list. */
        public pro.sketchware.chat.agentsdk.RunResult processUserMessage(String userText) {
            ChatMessage userMsg = new ChatMessage(
                    userText == null ? "" : userText, true, System.currentTimeMillis());
            if (messages != null) {
                synchronized (messages) {
                    messages.add(userMsg);
                }
            }
            return processUserMessage(userMsg);
        }

        /** Runs one user turn on the calling thread; UI updates go to main.
         *  The user message is prebuilt by the controller (llmContent,
         *  references); the WHOLE conversation is passed to the runtime as
         *  history so multi-turn context survives the v2 migration. */
        public pro.sketchware.chat.agentsdk.RunResult processUserMessage(ChatMessage userMsg) {
            beginRunPresentation();
            uiExecutor.execute(() -> {
                listener.onMessageAdded(userMsg);
                listener.onStatusChanged("");
            });
            lastStatus = "";
            // Headless/test hosts may not have attached a conversation list:
            // fall back to the single user message so the run still happens.
            java.util.List<ChatMessage> history = messages != null ? messages
                    : java.util.Collections.singletonList(userMsg);
            pro.sketchware.chat.agentsdk.RunResult result =
                    runtime.run(agentSupplier.get(), history, scId);
            finalizeRunPresentation(result);
            return result;
        }

        /** Resumable run over the host's history (multi-turn conversations). */
        public pro.sketchware.chat.agentsdk.RunResult processHistory(
                java.util.List<ChatMessage> history) {
            beginRunPresentation();
            pro.sketchware.chat.agentsdk.RunResult result =
                    runtime.run(agentSupplier.get(), history, scId);
            finalizeRunPresentation(result);
            return result;
        }

        /** Final assistant text of the most recent run (empty on failure). */
        public String lastAssistantText() {
            return lastOutput;
        }

        /** Latest structured status text derived from events. */
        public String lastStatus() {
            return lastStatus;
        }

        /** Resolves a pending approval by requestId (item 16). */
        public boolean resolveApproval(String requestId,
                                       pro.sketchware.chat.agentsdk.PermissionDecision decision) {
            return runtime.resolveApproval(requestId, decision);
        }

        /** Cancels a pending approval (user dismissed the dialog). */
        public boolean cancelApproval(String requestId) {
            return runtime.cancelApproval(requestId);
        }

        /** Cooperative cancellation of the run in flight. */
        public void cancel() {
            statusReducer.requestCancellation();
            postStatus(statusTextFor(statusReducer.state()));
            runtime.cancel();
        }

        /** Detaches the event subscription (screen destroyed). */
        @Override
        public void close() {
            try {
                eventSubscription.close();
            } catch (Exception ignored) {
            }
        }

        // ------------------------------------------------------------------
        // Presentation built from structured events (item 8). Handlers run on
        // the EventStream delivery thread; every listener call is marshalled to
        // the UI executor. The runtime remains the single source of truth for
        // streaming, tool lifecycle, approvals, file changes and completion.
        // ------------------------------------------------------------------

        private void beginRunPresentation() {
            synchronized (liveText) {
                liveText.setLength(0);
                liveReasoning.setLength(0);
            }
            liveAssistant = null;
            toolBubbles.clear();
            pendingApprovalRequestId = "";
            statusReducer.reset();
        }

        private void finalizeRunPresentation(pro.sketchware.chat.agentsdk.RunResult result) {
            lastOutput = result != null && result.isSuccessful() ? result.getOutput() : "";
            uiExecutor.execute(() -> {
                ChatMessage assistant = liveAssistant;
                liveAssistant = null;
                if (assistant != null) {
                    assistant.setStreaming(false);
                    String failure = result == null || result.isSuccessful()
                            ? "" : safeText(result.getFailureReason());
                    boolean cancelled = failure.toLowerCase(java.util.Locale.ROOT).contains("cancel");
                    if (cancelled) {
                        if (!assistant.hasDisplayContent()) {
                            assistant.setDisplayContent(stringOf(R.string.chat_tool_cancelled_message));
                        } else if (!assistant.getDisplayContent().contains(
                                stringOf(R.string.chat_cancelled_suffix))) {
                            assistant.setDisplayContent(assistant.getDisplayContent().trim()
                                    + "\n\n" + stringOf(R.string.chat_cancelled_suffix));
                        }
                        assistant.setStatus(stringOf(R.string.chat_tool_status_cancelled));
                    } else if (result != null && result.isSuccessful()
                            && !assistant.hasDisplayContent()
                            && ChatMessage.hasVisibleText(lastOutput)) {
                        // Non-streamed final answer: publish it exactly once here.
                        assistant.setDisplayContent(lastOutput);
                    }
                    if (assistant.hasDisplayContent()) {
                        assistant.setStatus("");
                        listener.onMessageUpdated(assistant);
                    } else if (!assistant.hasReasoningContent()) {
                        // The run ended with this "thinking" placeholder still
                        // empty (e.g. the last turn was a tool call with no
                        // trailing text, or a non-cancel failure with no
                        // output) — remove it instead of leaving an invisible
                        // empty bot message in the persisted history.
                        removeMessageIfPresent(assistant);
                    } else {
                        listener.onMessageUpdated(assistant);
                    }
                } else if (result != null && !result.isSuccessful()) {
                    String failure = safeText(result.getFailureReason());
                    if (!failure.toLowerCase(java.util.Locale.ROOT).contains("cancel")
                            && ChatMessage.hasVisibleText(failure)) {
                        listener.onError(failure);
                    }
                }
                listener.onProcessingFinished();
            });
        }

        private void onAgentEvent(pro.sketchware.chat.agentsdk.AgentEvent event) {
            // Item 35: reject events of OTHER runs (stale subscribers,
            // cancelled runs, retried requests) — the UI renders only events
            // whose scId belongs to this bridge conversation.
            if (event == null
                    || (scId != null && !scId.isEmpty()
                    && event.getScId() != null && !event.getScId().isEmpty()
                    && !scId.equals(event.getScId()))) {
                return;
            }
            // The reducer owns the composer status: every run event derives a
            // status (TurnStarted keeps THINKING; tools/approvals/cancels all
            // round-trip exactly once). This replaces the old scattered
            // postStatus(...) calls that could clear the indicator early.
            publishReducedStatus(event);
            // TurnStarted keeps the composer status at THINKING (see
            // publishReducedStatus above); it also now materializes the
            // transient "thinking" chat bubble below (item 29/31), which it
            // previously did not.
            // Item 29/31: show the transient "thinking" bubble as soon as a
            // turn starts, not only once the first assistant text delta
            // arrives. Previously, whenever a turn went straight from
            // TurnStarted into a tool call (no text before it — the common
            // case once tool-first behavior was encouraged), the assistant
            // bubble was never created at all, so the chat list never showed
            // any "Pensando…" indicator; only the composer status line did.
            if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.TurnStarted) {
                ensureLiveAssistant();
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.AssistantMessageDelta) {
                String delta = ((pro.sketchware.chat.agentsdk.AgentEvent.AssistantMessageDelta) event).getDelta();
                if (!ChatMessage.hasVisibleText(delta)) {
                    return;
                }
                ensureLiveAssistant();
                synchronized (liveText) {
                    liveText.append(delta);
                }
                ChatMessage assistant = liveAssistant;
                assistant.setStatus("");
                assistant.setDisplayContent(liveTextSnapshot());
                uiExecutor.execute(() -> listener.onMessageUpdated(assistant));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.AssistantMessage) {
                String content = ((pro.sketchware.chat.agentsdk.AgentEvent.AssistantMessage) event).getContent();
                if (!ChatMessage.hasVisibleText(content)) {
                    return;
                }
                // Item 7: if the same text was already delivered as deltas,
                // publishing it again would duplicate the message. Only a
                // non-streamed turn (no deltas) materializes the text here.
                if (content.equals(liveTextSnapshot())) {
                    return;
                }
                ensureLiveAssistant();
                ChatMessage assistant = liveAssistant;
                assistant.setStatus("");
                assistant.setDisplayContent(content);
                synchronized (liveText) {
                    liveText.setLength(0);
                    liveText.append(content);
                }
                uiExecutor.execute(() -> listener.onMessageUpdated(assistant));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.ToolCallStarted) {
                pro.sketchware.chat.agentsdk.AgentEvent.ToolCallStarted started =
                        (pro.sketchware.chat.agentsdk.AgentEvent.ToolCallStarted) event;
                String tool = started.getTool();
                String callId = started.getCall() == null ? "" : safeText(started.getCall().getId());
                String args = started.getCall() == null ? "{}" : safeText(started.getCall().getArguments());
                closeLiveAssistant();
                ChatMessage existing = toolBubbles.get(callId);
                if (existing == null) {
                    ChatMessage bubble = new ChatMessage(tool, args, System.currentTimeMillis(), callId);
                    bubble.setToolRunning(true);
                    bubble.setToolState("running_now");
                    bubble.setStatus(stringOf(R.string.chat_tool_status_running));
                    bubble.setDisplayContent(stringOf(R.string.chat_tool_running_message));
                    toolBubbles.put(callId, bubble);
                    if (messages != null) {
                        synchronized (messages) {
                            messages.add(bubble);
                        }
                    }
                    uiExecutor.execute(() -> listener.onMessageAdded(bubble));
                } else {
                    existing.setToolRunning(true);
                    existing.setToolState("running_now");
                    uiExecutor.execute(() -> listener.onMessageUpdated(existing));
                }
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.ToolCallCompleted) {
                pro.sketchware.chat.agentsdk.AgentEvent.ToolCallCompleted completed =
                        (pro.sketchware.chat.agentsdk.AgentEvent.ToolCallCompleted) event;
                String tool = completed.getTool();
                String callId = completed.getCall() == null ? "" : safeText(completed.getCall().getId());
                boolean success = completed.getResult() == null
                        || !completed.getResult().isError();
                String resultText = completed.getResult() == null
                        ? "" : safeText(completed.getResult().output());
                ChatMessage existing = toolBubbles.get(callId);
                final ChatMessage bubble;
                if (existing == null) {
                    // Completion without a visible start (deduped or approval path).
                    bubble = new ChatMessage(tool, "{}", System.currentTimeMillis(), callId);
                    toolBubbles.put(callId, bubble);
                    if (messages != null) {
                        synchronized (messages) {
                            messages.add(bubble);
                        }
                    }
                    uiExecutor.execute(() -> listener.onMessageAdded(bubble));
                } else {
                    bubble = existing;
                }
                bubble.setToolRunning(false);
                bubble.setToolError(!success);
                bubble.setToolState(success ? "success" : "error");
                bubble.setToolResult(resultText);
                bubble.setStatus(stringOf(success
                        ? R.string.chat_tool_status_done
                        : R.string.chat_tool_status_error));
                bubble.setDisplayContent(stringOf(success
                        ? R.string.chat_tool_done_message
                        : R.string.chat_tool_error_message));
                bubble.setExpanded(!success);
                uiExecutor.execute(() -> listener.onMessageUpdated(bubble));
                // A refused or failed write changed nothing
                boolean mutation = success && isMutationTool(tool);
                uiExecutor.execute(() -> listener.onToolExecuted(tool, mutation));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.FileChanged) {
                pro.sketchware.chat.agentsdk.AgentEvent.FileChanged changed =
                        (pro.sketchware.chat.agentsdk.AgentEvent.FileChanged) event;
                uiExecutor.execute(() -> listener.onFileChanged(changed.getPath(),
                        changed.getKind() == null ? "" : changed.getKind().name(),
                        changed.getTool()));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.ApprovalRequired) {
                pro.sketchware.chat.agentsdk.AgentEvent.ApprovalRequired required =
                        (pro.sketchware.chat.agentsdk.AgentEvent.ApprovalRequired) event;
                String requestId = required.getRequest() == null
                        ? "" : safeText(required.getRequest().getId());
                // Key the bubble by the tool CALL id (as ToolCallStarted/Completed do), so the approved call
                // updates this same bubble instead of adding a second one next to a stale "Approve" card.
                String callId = required.getRequest() == null || required.getRequest().getCall() == null
                        ? "" : safeText(required.getRequest().getCall().getId());
                String bubbleKey = callId.isEmpty() ? requestId : callId;
                pendingApprovalRequestId = bubbleKey;
                closeLiveAssistant();
                ChatMessage existing = toolBubbles.get(bubbleKey);
                final ChatMessage bubble;
                if (existing == null && required.getRequest() != null) {
                    String args = required.getRequest().getCall() == null
                            ? "{}" : safeText(required.getRequest().getCall().getArguments());
                    bubble = new ChatMessage(required.getTool(), args,
                            System.currentTimeMillis(), bubbleKey);
                    toolBubbles.put(bubbleKey, bubble);
                    if (messages != null) {
                        synchronized (messages) {
                            messages.add(bubble);
                        }
                    }
                    uiExecutor.execute(() -> listener.onMessageAdded(bubble));
                } else {
                    bubble = existing;
                }
                if (bubble != null) {
                    bubble.setRequiresApproval(true);
                    bubble.setToolState("tool_request");
                    bubble.setStatus(stringOf(R.string.chat_tool_status_waiting_approval));
                    bubble.setDisplayContent(ChatMessage.hasVisibleText(required.getTool())
                            ? stringOf(R.string.chat_tool_approval_message_named, required.getTool())
                            : stringOf(R.string.chat_tool_status_waiting_approval));
                    uiExecutor.execute(() -> listener.onMessageUpdated(bubble));
                }
                uiExecutor.execute(() -> listener.onApprovalRequired(requestId, required.getTool()));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.PermissionResolved) {
                pro.sketchware.chat.agentsdk.AgentEvent.PermissionResolved resolved =
                        (pro.sketchware.chat.agentsdk.AgentEvent.PermissionResolved) event;
                // The resolved request is the one the bridge parked; the event
                // itself carries tool/decision/state (no requestId payload).
                ChatMessage bubble = toolBubbles.get(pendingApprovalRequestId);
                pendingApprovalRequestId = "";
                boolean allowed = resolved.isAllowed()
                        || resolved.getState() == pro.sketchware.chat.agentsdk.ApprovalHandler.ApprovalState.ALLOWED;
                if (bubble != null && allowed) {
                    // Approved: the card stops asking and shows the call running until ToolCallCompleted
                    bubble.setRequiresApproval(false);
                    bubble.setApproved(true);
                    bubble.setToolRunning(true);
                    bubble.setToolState("running_now");
                    bubble.setStatus(stringOf(R.string.chat_tool_status_running));
                    bubble.setDisplayContent(stringOf(R.string.chat_tool_running_message));
                    uiExecutor.execute(() -> listener.onMessageUpdated(bubble));
                }
                if (bubble != null && !allowed) {
                    bubble.setRequiresApproval(false);
                    bubble.setToolRunning(false);
                    bubble.setRejected(true);
                    bubble.setToolState("rejected");
                    bubble.setToolResult(stringOf(R.string.chat_tool_cancelled_message));
                    bubble.setStatus(stringOf(R.string.chat_tool_status_cancelled));
                    bubble.setDisplayContent(stringOf(R.string.chat_tool_cancelled_message));
                    uiExecutor.execute(() -> listener.onMessageUpdated(bubble));
                }
                uiExecutor.execute(() -> listener.onPermissionResolved(resolved.getTool(),
                        resolved.isAllowed(),
                        resolved.getState() == null ? "" : resolved.getState().name()));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.PolicyDenied) {
                pro.sketchware.chat.agentsdk.AgentEvent.PolicyDenied denied =
                        (pro.sketchware.chat.agentsdk.AgentEvent.PolicyDenied) event;
                uiExecutor.execute(() -> listener.onDebug("[policy] " + denied.getTool()
                        + ": " + denied.getReason()));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.Error) {
                String message = ((pro.sketchware.chat.agentsdk.AgentEvent.Error) event).getMessage();
                uiExecutor.execute(() -> listener.onDebug("[error] " + message));
            } else if (event instanceof pro.sketchware.chat.agentsdk.AgentEvent.RunCompleted) {
                // Nothing here: finalizeRunPresentation closes the run on the
                // UI thread after runtime.run returns (single completion path).
            }
        }

        private void ensureLiveAssistant() {
            if (liveAssistant != null) {
                return;
            }
            ChatMessage assistant = new ChatMessage("", false, System.currentTimeMillis());
            assistant.setStreaming(true);
            assistant.setStatus("");
            liveAssistant = assistant;
            if (messages != null) {
                synchronized (messages) {
                    messages.add(assistant);
                }
            }
            uiExecutor.execute(() -> listener.onMessageAdded(assistant));
        }

        private void closeLiveAssistant() {
            ChatMessage assistant = liveAssistant;
            // Always clear the reference, even when there is nothing to close:
            // otherwise a later ensureLiveAssistant() call could resurrect (and
            // append new text to) a bubble that was already closed/removed and
            // is no longer part of `messages`, silently losing that text.
            liveAssistant = null;
            if (assistant == null || !assistant.isStreaming()) {
                return;
            }
            assistant.setStreaming(false);
            if (!assistant.hasDisplayContent() && !assistant.hasReasoningContent()) {
                // The "thinking" placeholder never received any real content
                // (e.g. the turn went straight into a tool call) — remove it
                // instead of leaving an invisible, empty bot message behind
                // that would also get persisted to history.
                removeMessageIfPresent(assistant);
                return;
            }
            uiExecutor.execute(() -> listener.onMessageUpdated(assistant));
        }

        /** Removes a message this bridge added earlier, notifying the UI. */
        private void removeMessageIfPresent(ChatMessage message) {
            if (message == null || messages == null) {
                return;
            }
            final int index;
            synchronized (messages) {
                index = messages.indexOf(message);
                if (index < 0) {
                    return;
                }
                messages.remove(index);
            }
            uiExecutor.execute(() -> listener.onMessageRemoved(message, index));
        }

        private String liveTextSnapshot() {
            synchronized (liveText) {
                return liveText.toString();
            }
        }

        private void postStatus(String status) {
            lastStatus = status == null ? "" : status;
            uiExecutor.execute(() -> listener.onStatusChanged(lastStatus));
        }

        /** Feeds one event into the reducer and publishes the resulting status. */
        private void publishReducedStatus(pro.sketchware.chat.agentsdk.AgentEvent event) {
            statusReducer.reduce(event);
            postStatus(statusTextFor(statusReducer.state()));
        }

        /** Maps a reduced state to the composer header text (never a chat bubble). */
        private String statusTextFor(pro.sketchware.chat.agentsdk.AgentUiState state) {
            if (state == null) {
                return "";
            }
            switch (state.getRunStatus()) {
                case WORKING:
                    return stringOf(R.string.chat_status_working);
                case THINKING:
                    return stringOf(R.string.chat_status_thinking);
                case WAITING_APPROVAL:
                    return stringOf(R.string.chat_tool_status_waiting_approval);
                case RUNNING_TOOL:
                    return statusForTool(state.getActiveTool());
                case CANCELLING:
                    return stringOf(R.string.chat_status_cancelling);
                case ERROR:
                    return stringOf(R.string.chat_tool_status_error);
                case READY:
                case COMPLETED:
                default:
                    return "";
            }
        }

        private String stringOf(int resId, Object... args) {
            if (appContext == null) {
                return "";
            }
            try {
                return args == null || args.length == 0
                        ? appContext.getString(resId)
                        : appContext.getString(resId, args);
            } catch (Exception e) {
                return "";
            }
        }

        private static String safeText(String value) {
            return value == null ? "" : value;
        }

        private static String statusForTool(String toolName) {
            String name = toolName == null ? "" : toolName.trim();
            if (READ_TOOLS.contains(name)) {
                return "Analisando os arquivos do projeto…";
            }
            if (isMutationTool(name)) {
                return "Aplicando alterações no projeto…";
            }
            return name.isEmpty() ? "Executando ferramenta…" : "Executando " + name + "…";
        }
    }

    public void release() {
        resetConversationState();
        multiAgentOrchestrator.shutdown();
        if (uiHostBridge != null) {
            uiHostBridge.close();
            uiHostBridge = null;
        }
        if (runExecutor != null) {
            runExecutor.shutdownNow();
            runExecutor = null;
        }
    }


    /**
     * Mantém o andamento da operação no mesmo item de resposta da conversa.
     * O status continua disponível para os painéis auxiliares, mas não cria um
     * segundo indicador visual fora da lista.
     */
    private void updateRunStatus(@Nullable String status) {
        String safeStatus = status == null ? "" : status.trim();
        listener.onStatusChanged(safeStatus);
    }

    private void prepareMultiAgentWorkflow(int version, int loopStep, int llmAttempt) {
        if (multiAgentPreparationInFlight) {
            return;
        }
        multiAgentPreparationInFlight = true;
        String objective = agentMemory == null
                ? findLatestUserMessage()
                : agentMemory.getOriginalUserMessage();
        String sharedContext = buildMultiAgentSharedContext();
        updateRunStatus(getString(R.string.chat_status_preparing_multi_agent));
        ChatFlowLogger.event("agent", "multi_agent_started",
                "reason=" + multiAgentDecisionReason + ", planner+architect->manager");
        emitTrace("Multiagente iniciado",
                "reason=" + multiAgentDecisionReason + ", planner+architect -> manager");
        multiAgentOrchestrator.prepareAsync(objective, sharedContext, preparation ->
                mainHandler.post(() -> {
                    if (!isActiveRun(version)) {
                        return;
                    }
                    multiAgentPreparationInFlight = false;
                    multiAgentPrepared = true;
                    multiAgentGuidance = preparation.toGuidance();
                    updateRunStatus(getString(R.string.chat_status_multi_agent_ready));
                    ChatFlowLogger.event("agent", "multi_agent_ready",
                            "degraded=" + preparation.isDegraded()
                                    + ", guidanceChars=" + multiAgentGuidance.length());
                    emitTrace("Multiagente preparado",
                            "plannerChars=" + preparation.getPlannerOutputChars()
                                    + ", architectChars=" + preparation.getArchitectOutputChars()
                                    + ", managerChars=" + preparation.getManagerOutputChars()
                                    + ", degraded=" + preparation.isDegraded());
                    // v2 path: the prepared guidance reaches the runtime through
                    // buildAgentGuidance() on the NEXT run of this conversation.
                }));
    }

    private String buildMultiAgentSharedContext() {
        StringBuilder contextBuilder = new StringBuilder();
        File root = ProjectPathResolver.getPrimaryReadableRoot(scId);
        contextBuilder.append("Workspace root: ")
                .append(root == null ? "" : root.getAbsolutePath());
        if (agentMemory != null) {
            contextBuilder.append("\n\n").append(agentMemory.buildContextInjection());
        }
        if (taskPlan != null) {
            contextBuilder.append("\n\n").append(taskPlan.buildPlanSummary());
        }
        if (ChatMessage.hasVisibleText(historySummary)) {
            contextBuilder.append("\n\n[Prior conversation summary]\n")
                    .append(truncateForTranscript(historySummary, 4_000));
        }
        return truncateForTranscript(contextBuilder.toString(), 12_000);
    }

    /**
     * True only when the effective history approaches the selected model's
     * actual context budget. The former fixed 32k-character trigger was about
     * 8k tokens for every model, including models with much larger windows, so
     * a verbose tool result could repeatedly start a summarization mid-run.
     */
    private boolean shouldCompactHistory() {
        // A successful compaction does not remove visible messages. Without a
        // progress marker, the continuation sees the exact same message count
        // and can immediately compact again until the Android heap is exhausted.
        if (messages.size() <= lastCompactionMessageCount) {
            return false;
        }
        int end = messages.size() - COMPACT_KEEP_TAIL;
        if (end - historyCompactedUntil < 8) {
            return false;
        }
        long effectiveChars = 0;
        int recentToolResults = 0;
        for (int i = messages.size() - 1; i >= historyCompactedUntil; i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isTool() && !safe(message.getToolResult()).isEmpty()) {
                recentToolResults++;
            }
        }
        for (int i = historyCompactedUntil; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (m == null) {
                continue;
            }
            if (m.isTool()) {
                boolean keepVerbatim = recentToolResults <= 2 && !safe(m.getToolResult()).isEmpty();
                effectiveChars += Math.min(safe(m.getToolArgs()).length(), 4_000);
                effectiveChars += keepVerbatim
                        ? Math.min(safe(m.getToolResult()).length(), 16_000)
                        : Math.min(safe(m.getToolResult()).length(), 1_200);
                if (!safe(m.getToolResult()).isEmpty()) {
                    recentToolResults--;
                }
            } else {
                effectiveChars += safe(m.getDisplayContent()).length()
                        + safe(m.getReasoning()).length();
            }
            if (effectiveChars / COMPACTION_CHARS_PER_TOKEN > historyCompactionTriggerTokens()) {
                return true;
            }
        }
        return false;
    }

    /** Mirrors ContextBuilder's provider-aware history allocation. */
    private int historyCompactionTriggerTokens() {
        SharedPreferences prefs = VoidPortSettings.prefs(context);
        String providerId = currentOperationContext != null
                ? currentOperationContext.getProviderId()
                : prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String modelName = currentOperationContext != null
                ? currentOperationContext.getModelName()
                : prefs.getString(VoidPortSettings.PREF_CURRENT_MODEL, "");
        VoidPortModelCapabilities.Capabilities capabilities =
                VoidPortModelCapabilities.getModelCapabilities(providerId, modelName);
        boolean reasoningEnabled = capabilities.reasoningCapabilities.supportsReasoning
                && !capabilities.reasoningCapabilities.canTurnOffReasoning;
        int reservedOutput = Math.max(1024,
                capabilities.effectiveReservedOutputTokenSpace(reasoningEnabled));
        int totalBudget = Math.max(DEFAULT_TOTAL_CONTEXT_TOKENS,
                Math.min(MAX_CONTEXT_TOKENS, capabilities.contextWindow - reservedOutput));
        int systemBudget = Math.max(DEFAULT_SYSTEM_CONTEXT_TOKENS,
                Math.min(16_000, totalBudget / 4));
        int compileErrorBudget = Math.max(DEFAULT_COMPILE_ERROR_TOKENS,
                Math.min(2_000, systemBudget / 6));
        int historyBudget = Math.max(DEFAULT_HISTORY_CONTEXT_TOKENS,
                totalBudget - systemBudget - compileErrorBudget);
        return Math.max(DEFAULT_HISTORY_CONTEXT_TOKENS,
                Math.min(MAX_COMPACTION_TRIGGER_TOKENS,
                        historyBudget * COMPACTION_TRIGGER_PERCENT / 100));
    }

    /**
     * Summarizes messages[historyCompactedUntil, size-KEEP_TAIL) on a background
     * thread and swaps them for a summary in the LLM context (UI untouched).
     * On any failure compaction is disabled for this session and the loop
     * continues with plain truncation as before.
     */
    private void compactHistoryAsync(int version, Runnable continuation) {
        compactionInFlight = true;
        lastCompactionMessageCount = messages.size();
        updateRunStatus(getString(R.string.chat_status_compacting_context));
        final int requestedEnd = Math.max(historyCompactedUntil, messages.size() - COMPACT_KEEP_TAIL);
        int compactedEnd = historyCompactedUntil;
        final StringBuilder transcript = new StringBuilder();
        if (!historySummary.isEmpty()) {
            transcript.append("[Resumo acumulado até aqui]\n").append(historySummary).append("\n\n");
        }
        for (int i = historyCompactedUntil; i < requestedEnd; i++) {
            ChatMessage m = messages.get(i);
            if (m == null || m.isCheckpoint()) {
                compactedEnd = i + 1;
                continue;
            }
            int transcriptLengthBeforeMessage = transcript.length();
            if (m.isUser()) {
                transcript.append("USUÁRIO: ")
                        .append(truncateForTranscript(safe(m.getDisplayContent()), 6000)).append('\n');
            } else if (m.isTool()) {
                transcript.append("FERRAMENTA ").append(safe(m.getToolName()))
                        .append(" args=").append(truncateForTranscript(safe(m.getToolArgs()), 400))
                        .append(" resultado=").append(truncateForTranscript(safe(m.getToolResult()), 1200))
                        .append('\n');
            } else {
                transcript.append("ASSISTENTE: ")
                        .append(truncateForTranscript(safe(m.getDisplayContent()), 6000)).append('\n');
            }
            if (transcript.length() > COMPACT_TRANSCRIPT_MAX_CHARS) {
                // Keep this whole message in the live window. Summarizing only a
                // prefix and then excluding the full message would lose context.
                transcript.setLength(transcriptLengthBeforeMessage);
                break;
            }
            compactedEnd = i + 1;
        }

        // Never mark messages as compacted unless they were actually provided to
        // the summarizer. This is especially important for long tool results.
        final int end = compactedEnd;
        if (end <= historyCompactedUntil) {
            compactionInFlight = false;
            emitTrace("Compactação sem progresso",
                    "mantendo janela atual e evitando nova tentativa para o mesmo histórico");
            // Post instead of calling recursively so the current stack and its
            // transcript can be released before the agent loop continues.
            mainHandler.post(continuation);
            return;
        }

        emitTrace("Compactação iniciada", "msgs=" + (end - historyCompactedUntil)
                + ", transcriptChars=" + transcript.length());

        new Thread(() -> {
            String summary = null;
            try {
                String systemPrompt = "Você é um sumarizador de contexto de um agente de programação. "
                        + "Resuma a conversa a seguir preservando: objetivo do usuário, decisões tomadas, "
                        + "arquivos criados/alterados (com caminhos), erros encontrados e estado atual da tarefa. "
                        + "Seja denso e factual; máximo ~600 palavras.";
                String userPrompt = truncateForTranscript(
                        transcript.toString(), COMPACT_TRANSCRIPT_MAX_CHARS);
                AiOperationContext frozenContext = currentOperationContext;
                summary = frozenContext == null
                        ? aiService.sendTextMessage(systemPrompt, userPrompt)
                        : aiService.sendTextMessage(
                                frozenContext.getProviderId(),
                                frozenContext.getModelName(),
                                systemPrompt,
                                userPrompt);
            } catch (Exception ignored) {
            }
            final String result = summary;
            mainHandler.post(() -> {
                compactionInFlight = false;
                if (result != null && !result.trim().isEmpty()) {
                    historySummary = limitCompactionSummary(result);
                    historyCompactedUntil = end;
                    listener.onCompactionStateChanged(historySummary, historyCompactedUntil);
                    emitTrace("Compactação concluída", "summaryChars=" + historySummary.length()
                            + ", compactadoAté=" + historyCompactedUntil);
                } else {
                    // Don't retry every turn if the summarizer is failing.
                    compactionFailed = true;
                    emitTrace("Compactação falhou", "seguindo com truncamento padrão");
                }
                if (isActiveRun(version)) {
                    continuation.run();
                }
            });
        }, "chat-history-compactor").start();
    }

    private static String limitCompactionSummary(@Nullable String summary) {
        String value = summary == null ? "" : summary.trim();
        if (value.length() <= COMPACT_SUMMARY_MAX_CHARS) {
            return value;
        }
        return value.substring(0, COMPACT_SUMMARY_MAX_CHARS)
                + "\n[Resumo truncado para proteger a memória do dispositivo]";
    }

    /**
     * Adds a new file snapshot to an existing turn checkpoint message.
     * Keeps the EARLIEST snapshot when the same file is touched twice in the
     * turn, so rollback restores the pre-turn content.
     */
    private boolean mergeSnapshotIntoCheckpoint(ChatMessage checkpointMsg,
                                                ChatCheckpointManager.CheckpointEntry entry) {
        try {
            JSONObject snapshots = new JSONObject(safe(checkpointMsg.getCheckpointSnapshotsJson()));
            if (snapshots.has(entry.filePath)) {
                return true; // earliest snapshot already stored
            }
            JSONObject snapshot = new JSONObject();
            snapshot.put("toolId", entry.toolId);
            snapshot.put("toolName", entry.toolName);
            snapshot.put("filePath", entry.filePath);
            snapshot.put("beforeContent", entry.beforeContent);
            snapshot.put("existedBefore", entry.existedBefore);
            snapshots.put(entry.filePath, snapshot);
            checkpointMsg.setCheckpointSnapshotsJson(snapshots.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Item 16: resolves the runtime's pending approval with ALLOW. The UI
     * no longer executes the tool itself — the parked run thread continues
     * through the {@code PermissionLayer}.
     */
    public void approveTool() {
        if (uiRuntime != null) {
            pro.sketchware.chat.agentsdk.AgentRuntime.PendingApproval pending =
                    uiRuntime.currentPendingApproval();
            if (pending != null) {
                uiRuntime.resolveApproval(pending.getRequestId(),
                        pro.sketchware.chat.agentsdk.PermissionDecision.ALLOW);
            }
        }
    }

    /**
     * Item 16: resolves the runtime's pending approval with DENY.
     */
    public void rejectTool() {
        if (uiRuntime != null) {
            pro.sketchware.chat.agentsdk.AgentRuntime.PendingApproval pending =
                    uiRuntime.currentPendingApproval();
            if (pending != null) {
                uiRuntime.resolveApproval(pending.getRequestId(),
                        pro.sketchware.chat.agentsdk.PermissionDecision.DENY);
            }
        }
    }

    private String truncateForTranscript(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }

    /** Runs the next queued tool call, or advances the agent loop when the queue drains. */
    /** Best-effort path extraction from a tool message for task memory. */
    @Nullable
    private static String argsPathOf(ChatMessage toolMsg) {
        try {
            String args = toolMsg.getToolArgs();
            if (args == null || args.isEmpty()) {
                return null;
            }
            org.json.JSONObject json = new org.json.JSONObject(args);
            String path = json.optString("uri", json.optString("path", ""));
            return path.isEmpty() ? null : path;
        } catch (Exception e) {
            return null;
        }
    }

    private void captureOperationContextForRun() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(context);
        AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
        String providerId = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "").trim();
        String modelName = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "").trim();
        String chatMode = AiChatSettingsHelper.getChatMode(prefs);
        if (providerId.isEmpty() || modelName.isEmpty()) {
            currentOperationContext = null;
            return;
        }
        currentOperationContext = AiOperationContext.builder()
                .providerId(providerId)
                .modelName(modelName)
                .chatMode(chatMode)
                .webSearchEnabled(VoidPortSettings.isChatWebSearchEnabled(prefs))
                .build();
        multiAgentOrchestrator.beginOperation(providerId, modelName);
        SecureLogger.d("AgentManager", "Operação iniciada: "
                + currentOperationContext.getRequestId() + " " + providerId + "/" + modelName);
    }

    private void initializeAgentExecution(String userText, String contextPayload,
                                          List<ChatReference> stagingSelections) {
        multiAgentOrchestrator.cancelActiveWorkflow();
        multiAgentGuidance = "";
        multiAgentModeForRun = MultiAgentPolicy.MODE_AUTO;
        multiAgentDecisionReason = "not_evaluated";
        multiAgentPrepared = false;
        multiAgentPreparationInFlight = false;
        multiAgentReviewInFlight = false;
        multiAgentReviewRounds = 0;
        String safeText = userText == null ? "" : userText.trim();
        if (safeText.isEmpty()) {
            agentMemory = null;
            requestPattern = null;
            taskPlan = null;
            ChatPlanManager.clearExecutionPlan(scId);
            multiAgentEnabledForRun = false;
            multiAgentDecisionReason = "empty_request";
            return;
        }
        requestPattern = PatternMatcher.analyze(safeText, contextPayload, stagingSelections);

        // Freeze the user's multi-agent preference together with the operation.
        // The policy also recognizes broad short fixes and explicit activation;
        // Auto may still escalate later if project inspection reveals complexity.
        SharedPreferences prefs = AiChatSettingsHelper.prefs(context);
        String chatMode = AiChatSettingsHelper.getChatMode(prefs);
        multiAgentModeForRun = AiChatSettingsHelper.getMultiAgentMode(prefs);
        MultiAgentPolicy.Decision multiAgentDecision = "agent".equalsIgnoreCase(chatMode)
                ? MultiAgentPolicy.decide(multiAgentModeForRun, requestPattern, safeText)
                : MultiAgentPolicy.decide(MultiAgentPolicy.MODE_OFF, requestPattern, safeText);
        multiAgentEnabledForRun = multiAgentDecision.isEnabled();
        multiAgentDecisionReason = "agent".equalsIgnoreCase(chatMode)
                ? multiAgentDecision.getReason()
                : "chat_mode_" + chatMode;
        ChatFlowLogger.event("agent", "multi_agent_decision",
                "enabled=" + multiAgentEnabledForRun
                        + ", mode=" + multiAgentModeForRun
                        + ", reason=" + multiAgentDecisionReason
                        + ", requestType=" + requestPattern.getPrimaryType());

        AgentMemory.Builder memoryBuilder = AgentMemory.builder(safeText)
                .originalSelections(stagingSelections)
                .addKeyFiles(requestPattern.getExtractedFilePaths());
        agentMemory = memoryBuilder.build();

        // The deterministic TaskPlanner is no longer authoritative. Complex
        // tasks may expose update_plan to let the model maintain the visible plan.
        taskPlan = null;
        ChatPlanManager.clearExecutionPlan(scId);
        ChatPlanManager.clearModelPlan(scId);
        pendingAgentFeedback = "";
        finishValidationFailures = 0;
        outputContinuationCount = 0;
    }

    private String buildAgentGuidance() {
        StringBuilder guidance = new StringBuilder();
        if (finalResponseOnly) {
            String terminalInstruction = "TERMINATION CONDITION:\n"
                    + (ChatMessage.hasVisibleText(finalResponseReason)
                            ? finalResponseReason
                            : "The required work is complete.")
                    + "\nThe tool phase is over. Do not emit, request, or describe another tool call. "
                    + "Return the final answer now using the completed results.";
            guidance.append(terminalInstruction);
            if (agentMemory != null) {
                guidance.append("\n\n").append(agentMemory.buildContextInjection());
            }
            if (taskPlan != null) {
                guidance.append("\n\n").append(taskPlan.buildPlanSummary());
            }
            // Keep the terminal requirement both at the beginning (survives
            // prompt trimming) and at the end (wins over stale history).
            guidance.append("\n\nFINAL RESPONSE REQUIRED: tools are disabled; answer now without a tool call.");
            pendingAgentFeedback = "";
            return guidance.toString();
        }

        boolean completionCandidate = pendingAgentFeedback.startsWith("[COMPLETION CANDIDATE]");
        // The preparation briefing describes how to begin the task. Once the
        // host has confirmed completion it is stale and must not compete with
        // the instruction to return the final answer.
        if (!completionCandidate && ChatMessage.hasVisibleText(multiAgentGuidance)) {
            guidance.append(multiAgentGuidance);
        }
        if (agentMemory != null) {
            if (guidance.length() > 0) {
                guidance.append("\n\n");
            }
            guidance.append(agentMemory.buildContextInjection());
        }
        if (taskPlan != null) {
            if (guidance.length() > 0) {
                guidance.append("\n\n");
            }
            guidance.append(taskPlan.buildPlanSummary());
        }
        // Put volatile feedback last so it remains the most recent instruction.
        if (ChatMessage.hasVisibleText(pendingAgentFeedback)) {
            if (guidance.length() > 0) {
                guidance.append("\n\n");
            }
            guidance.append("FINISH VALIDATION FEEDBACK:\n").append(pendingAgentFeedback);
            pendingAgentFeedback = "";
        }
        return guidance.toString();
    }

    private void beginInteractionTrace(int version, String userText, List<ChatReference> stagingSelections) {
        interactionTrace = new ChatInteractionTrace(version);
        runGuard.reset();
        finalResponseOnly = false;
        finalResponseReason = "";
        finalResponseForcedByGuard = false;
        awaitingRecoveredMutation = false;
        consecutiveToolFailures = 0;
        pendingAgentFeedback = "";
        finishValidationFailures = 0;
        outputContinuationCount = 0;
        currentRunCheckpointMessage = null;
        int textChars = userText == null ? 0 : userText.trim().length();
        int selectionCount = stagingSelections == null ? 0 : stagingSelections.size();
        int imageCount = stagingSelections == null ? 0 : ChatReferenceManager.getImageReferences(stagingSelections).size();
        emitTrace("Interação iniciada", "textChars=" + textChars + ", selections=" + selectionCount + ", images=" + imageCount);
        emitTrace("Decisão multiagente",
                "enabled=" + multiAgentEnabledForRun
                        + ", mode=" + multiAgentModeForRun
                        + ", reason=" + multiAgentDecisionReason
                        + (requestPattern == null ? "" : ", type=" + requestPattern.getPrimaryType()));
    }

    private void emitTrace(String event) {
        emitTrace(event, null);
    }

    private void emitTrace(String event, String detail) {
        if (interactionTrace == null) {
            return;
        }
        String line = interactionTrace.mark(event, detail);
        if (ChatMessage.hasVisibleText(line)) {
            listener.onDebug(line);
        }
    }

    private void emitTraceSummary(String label) {
        if (interactionTrace == null) {
            return;
        }
        String line = interactionTrace.summary(label);
        interactionTrace = null;
        if (ChatMessage.hasVisibleText(line)) {
            listener.onDebug(line);
        }
    }

    private void removeStreamingPlaceholderIfEmpty(ChatMessage botMsg) {
        if (botMsg == null) {
            return;
        }
        if (botMsg.hasDisplayContent() || botMsg.hasReasoningContent()) {
            return;
        }
        removeMessage(botMsg);
    }

    private void removeMessage(ChatMessage message) {
        int index = messages.indexOf(message);
        if (index < 0) {
            return;
        }
        messages.remove(index);
        listener.onMessageRemoved(message, index);
    }

    /** Enters a terminal model turn with the tool catalog removed. */
    private void activateFinalResponseOnly(String reason) {
        finalResponseOnly = true;
        finalResponseReason = reason == null ? "" : reason.trim();
    }

    private String sanitizeAssistantPayload(String payload) {
        String safePayload = payload == null ? "" : payload.trim();
        return VoidPortConvertToLlmMessageService.isProtocolEmptyMessage(safePayload)
                ? ""
                : safePayload;
    }

    private String buildTerminalFallback() {
        if (ChatMessage.hasVisibleText(finalResponseReason)
                && !finalResponseReason.contains("etapas obrigatórias")) {
            return "A execução foi encerrada de forma segura para evitar um loop. "
                    + "O melhor resultado verificado foi preservado. Motivo: "
                    + finalResponseReason;
        }
        return "Tarefa concluída e verificada. As ações e os resultados estão registrados acima.";
    }

    private String recoveredMutationFeedback() {
        return "A mutação anterior foi descartada porque usava conteúdo obsoleto. "
                + "O arquivo foi lido novamente. Gere um novo edit_file ou rewrite_file "
                + "a partir desse conteúdo atual; não reutilize o patch anterior.";
    }

    /** The chat's read and mutation tools, by the names they are registered under. */
    private static final java.util.Set<String> READ_TOOLS = new java.util.HashSet<>(java.util.Arrays.asList(
            "read_file", "ls_dir", "get_dir_tree", "search_pathnames_only", "search_for_files", "search_in_file",
            "get_file_info"));
    private static final java.util.Set<String> MUTATION_TOOLS = new java.util.HashSet<>(java.util.Arrays.asList(
            "edit_file", "rewrite_file", "create_file_or_folder", "delete_file_or_folder", "move_file",
            "rename_file", "copy_file", "apply_patch"));

    private static boolean isMutationTool(String toolName) {
        return MUTATION_TOOLS.contains(toolName);
    }

    static boolean isOutputTruncated(String finishReason) {
        String normalized = finishReason == null
                ? ""
                : finishReason.trim().toLowerCase(java.util.Locale.ROOT);
        return "length".equals(normalized)
                || "max_tokens".equals(normalized)
                || "max_output_tokens".equals(normalized);
    }

    static int estimateInputTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (text.length() + COMPACTION_CHARS_PER_TOKEN - 1)
                / COMPACTION_CHARS_PER_TOKEN);
    }

    private boolean isActiveRun(int version) {
        return version == runVersion;
    }

    private String findLatestUserMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isUser()) {
                return message.getLlmContent();
            }
        }
        return "";
    }

    private void syncExecutionPlan() {
        if (taskPlan == null) {
            ChatPlanManager.clearExecutionPlan(scId);
        } else {
            ChatPlanManager.setExecutionPlan(scId, taskPlan);
        }
    }

    private String consecutiveToolFailureMessage() {
        return "Erro: limite de falhas consecutivas de ferramentas atingido ("
                + consecutiveToolFailures + ").";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String getString(int resId) {
        return context.getString(resId);
    }

    private String getString(int resId, Object... args) {
        return context.getString(resId, args);
    }
}
