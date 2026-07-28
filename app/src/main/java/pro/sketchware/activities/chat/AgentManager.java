package pro.sketchware.activities.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.annotation.Nullable;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.chat.agent.AgentMemory;
import pro.sketchware.activities.chat.agent.FinishChecker;
import pro.sketchware.activities.chat.agent.PatternMatcher;
import pro.sketchware.activities.chat.agent.RetryManager;
import pro.sketchware.activities.chat.agent.TaskPlanner;
import pro.sketchware.activities.chat.agent.ToolSequenceValidator;
import pro.sketchware.activities.chat.port.VoidToolWrapper;
import pro.sketchware.activities.chat.port.VoidPortDiffService;
import pro.sketchware.activities.chat.port.VoidPortMcpChannel;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.activities.chat.port.GitHubMcpService;
import pro.sketchware.activities.chat.toolcall.DefaultToolCallDetector;
import pro.sketchware.activities.chat.toolcall.ToolCall;
import pro.sketchware.activities.chat.toolcall.ToolCallDetector;
import pro.sketchware.ia.tools.Tool;
import pro.sketchware.ia.tools.ToolManager;
import pro.sketchware.network.AiProviderService;
import pro.sketchware.network.AiRequestHandle;
import pro.sketchware.util.SketchwareFileDecryptor;

/**
 * Orchestrates the chat loop, approval flow, checkpoints, diff previews and
 * cancellation of the active stream/tool execution.
 */
public class AgentManager {

    /** Matches Void {@code CHAT_RETRIES} / {@code RETRY_DELAY} in chatThreadService.ts */
    private static final int MAX_PREVIEW_LINES = 48;
    private static final long STREAM_COALESCE_MS = 120L;
    /**
     * Hard limit on agentic loop iterations to prevent infinite token consumption
     * when the model keeps requesting tools that fail or loop.
     */
    private static final int MAX_LOOP_STEPS = 40;
    private static final int MAX_LLM_ATTEMPTS = 3;
    private static final long LLM_RETRY_DELAY_MS = 2500L;
    private static final int MAX_FINISH_REJECTIONS = 3;
    /**
     * Regex that strips any characters that are not valid in a tool name.
     * Protects against models (especially free/quantized ones) leaking internal
     * tokens into tool names, e.g. {@code edit_file<|channel|>commentary}.
     * Valid tool names contain only ASCII word chars, hyphens and dots.
     */
    private static final java.util.regex.Pattern TOOL_NAME_SANITIZER =
            java.util.regex.Pattern.compile("[^a-zA-Z0-9_\\-.]");

    public enum State {
        IDLE,
        THINKING,
        AWAITING_APPROVAL,
        EXECUTING_TOOL,
        FINISHED,
        ERROR
    }

    private final Context context;
    private final String scId;
    private final List<ChatMessage> messages;
    private final AgentListener listener;
    private final AiProviderService aiService;
    private final ToolManager toolManager;
    private final ToolCallDetector toolCallDetector;
    private final Handler mainHandler;
    private final Handler streamCoalesceHandler;
    private final ChatCheckpointManager checkpointManager;
    private AiRequestHandle currentRequestHandle;

    private State currentState = State.IDLE;
    private ChatMessage pendingToolMessage;
    private ChatMessage currentStreamingMessage;
    private Thread currentToolThread;
    private int runVersion = 0;
    private int pendingToolLoopStep = -1;
    /**
     * Sliding window of the most recent tool-call signatures ("name:args").
     * Detects not only exact consecutive repeats (A A A) but also oscillating
     * cycles (A B A B, A B C A B C) that the old single-signature counter missed.
     */
    private final java.util.ArrayDeque<String> recentToolSignatures = new java.util.ArrayDeque<>();
    private static final int SIGNATURE_WINDOW = 9;
    /** Abort the run after this many consecutive failing tool executions. */
    private static final int MAX_CONSECUTIVE_TOOL_FAILURES = 4;
    private int consecutiveToolFailures = 0;
    /**
     * Tool calls returned by the LLM in the current turn that still await
     * execution. Modern models emit several (often parallel) tool calls per
     * turn; they are executed sequentially in the order received, and the
     * agent loop only advances once the queue drains.
     */
    private final java.util.ArrayDeque<String[]> queuedToolCalls = new java.util.ArrayDeque<>();
    private String queuedChatMode = "agent";

    // ---- History compaction (context only; the visible chat is untouched) ----
    /** Approx. chars that trigger the summary stage (~8k tokens). */
    private static final int COMPACT_THRESHOLD_CHARS = 32_000;
    /** Recent message groups kept verbatim after the summary stage. */
    private static final int COMPACT_KEEP_TAIL = 8;
    /** Max chars of transcript sent to the summarizer. */
    private static final int COMPACT_TRANSCRIPT_MAX_CHARS = 64_000;
    private String historySummary = "";
    private int historyCompactedUntil = 0;
    private boolean compactionInFlight = false;
    private boolean compactionFailed = false;

    /** Checkpoint message shared by every file mutation of the current run (turn-level rollback). */
    private ChatMessage currentRunCheckpointMessage;
    private ChatInteractionTrace interactionTrace;
    private ChatMessage pendingStreamMessage;
    private boolean streamUpdateScheduled;
    private String streamingToolName = "";
    private String streamingToolId = "";
    private String streamingMcpServerName;
    private AgentMemory agentMemory;
    private PatternMatcher.Result requestPattern;
    private TaskPlanner.Plan taskPlan;
    private final java.util.List<ToolSequenceValidator.ToolUsage> toolUsageHistory = new java.util.ArrayList<>();
    private String pendingAgentFeedback = "";
    private int finishValidationFailures = 0;

    public interface AgentListener {
        void onMessageAdded(ChatMessage message);
        void onMessageUpdated(ChatMessage message);
        void onMessageRemoved(ChatMessage message, int index);
        void onStatusChanged(String status);
        void onDebug(String message);
        void onProcessingFinished();
        void onToolExecuted(String toolName, boolean isMutation);
        void onError(String error);
    }

    public AgentManager(Context context, String scId, List<ChatMessage> messages, AgentListener listener) {
        this.context = context.getApplicationContext();
        this.scId = scId;
        this.messages = messages;
        this.listener = listener;
        this.aiService = AiProviderService.getInstance();
        
        this.toolManager = new ToolManager();
        VoidToolWrapper.registerAllVoidTools(this.toolManager);
        this.toolCallDetector = new DefaultToolCallDetector();

        this.mainHandler = new Handler(Looper.getMainLooper());
        this.streamCoalesceHandler = new Handler(Looper.getMainLooper());
        this.checkpointManager = new ChatCheckpointManager(context);
    }

    public State getCurrentState() {
        return currentState;
    }

    public boolean hasCheckpoint() {
        return checkpointManager.hasCheckpoint(scId);
    }

    public ChatCheckpointManager.RollbackResult rollbackLastCheckpoint() {
        return checkpointManager.rollbackLatestCheckpoint(scId, messages);
    }

    private void setState(State state) {
        this.currentState = state;
        String statusText = "";
        switch (state) {
            case THINKING:
                statusText = getString(R.string.chat_status_thinking);
                break;
            case AWAITING_APPROVAL:
                statusText = getString(R.string.chat_tool_status_waiting_approval);
                break;
            case EXECUTING_TOOL:
                statusText = getString(R.string.chat_tool_status_running);
                break;
            case IDLE:
                statusText = "";
                break;
        }
        listener.onStatusChanged(statusText);
    }

    public void processUserMessage(String userText) {
        processUserMessage(userText, null);
    }

    public void processUserMessage(String userText, String contextPayload) {
        processUserMessage(userText, contextPayload, null);
    }

    public void processUserMessage(String userText, String contextPayload, List<ChatReference> stagingSelections) {
        if (currentState != State.IDLE) {
            return;
        }

        String displayText = userText == null ? "" : userText.trim();
        ChatMessage userMsg = new ChatMessage(displayText, true, System.currentTimeMillis());
        userMsg.setContextPayload(contextPayload);
        userMsg.setStagingSelections(stagingSelections);
        userMsg.setLlmContent(ChatReferenceManager.buildLlmUserContent(displayText, contextPayload));
        messages.add(userMsg);
        listener.onMessageAdded(userMsg);

        int version = ++runVersion;
        initializeAgentExecution(displayText, contextPayload, stagingSelections);
        beginInteractionTrace(version, displayText, stagingSelections);
        startAgentLoop(version, 0);
    }

    public void continueFromExistingMessage(@Nullable ChatMessage sourceMessage) {
        if (currentState != State.IDLE) {
            return;
        }
        int version = ++runVersion;
        String displayText = sourceMessage == null ? findLatestUserMessage() : sourceMessage.getDisplayContent();
        List<ChatReference> selections = sourceMessage == null ? null : sourceMessage.getStagingSelections();
        String contextPayload = sourceMessage == null ? null : sourceMessage.getContextPayload();
        initializeAgentExecution(displayText, contextPayload, selections);
        beginInteractionTrace(version, displayText, selections);
        startAgentLoop(version, 0);
    }

    public boolean cancelCurrentRun() {
        if (currentState == State.IDLE) {
            return false;
        }

        runVersion++;
        AiRequestHandle requestHandle = currentRequestHandle;
        currentRequestHandle = null;
        if (requestHandle != null) {
            requestHandle.cancel();
        }
        toolManager.cancelActiveTool();
        queuedToolCalls.clear();
        // Kill any shell processes spawned by run_command / persistent terminals;
        // previously they kept running (and leaking) after the user cancelled.
        pro.sketchware.activities.chat.port.VoidPortToolsService.killAllTerminals();
        streamCoalesceHandler.removeCallbacksAndMessages(null);
        streamUpdateScheduled = false;
        pendingStreamMessage = null;

        Thread toolThread = currentToolThread;
        if (toolThread != null) {
            toolThread.interrupt();
        }
        currentToolThread = null;

        final String interruptedToolName = streamingToolName;
        final String interruptedMcpServer = streamingMcpServerName;
        final boolean hadPendingTool = pendingToolMessage != null;
        final ChatMessage streamingSnapshot = currentStreamingMessage;

        mainHandler.post(() -> {
            if (ChatMessage.hasVisibleText(interruptedToolName)) {
                ChatMessage interrupted = ChatMessage.interruptedStreamingTool(
                        interruptedToolName,
                        interruptedMcpServer,
                        System.currentTimeMillis()
                );
                messages.add(interrupted);
                listener.onMessageAdded(interrupted);
            } else if (pendingToolMessage != null) {
                pendingToolMessage.setToolRunning(false);
                pendingToolMessage.setToolError(true);
                if (currentState == State.AWAITING_APPROVAL) {
                    pendingToolMessage.setToolState("rejected");
                    pendingToolMessage.setRejected(true);
                    pendingToolMessage.setStatus(getString(R.string.chat_tool_status_cancelled));
                    pendingToolMessage.setDisplayContent(getString(R.string.chat_tool_cancelled_message));
                } else {
                    pendingToolMessage.setStatus(getString(R.string.chat_tool_status_cancelled));
                    pendingToolMessage.setDisplayContent(getString(R.string.chat_tool_cancelled_message));
                }
                pendingToolMessage.setToolResult(getString(R.string.chat_tool_cancelled_message));
                listener.onMessageUpdated(pendingToolMessage);
            } else if (streamingSnapshot != null) {
                if (!streamingSnapshot.hasDisplayContent()) {
                    streamingSnapshot.setDisplayContent(getString(R.string.chat_tool_cancelled_message));
                } else if (!streamingSnapshot.getDisplayContent().contains(getString(R.string.chat_cancelled_suffix))) {
                    streamingSnapshot.setDisplayContent(
                            streamingSnapshot.getDisplayContent().trim()
                                    + "\n\n"
                                    + getString(R.string.chat_cancelled_suffix));
                }
                streamingSnapshot.setStatus(getString(R.string.chat_tool_status_cancelled));
                listener.onMessageUpdated(streamingSnapshot);
            }

            if (!hadPendingTool && !ChatMessage.hasVisibleText(interruptedToolName)) {
                // Void adds a user checkpoint after abort when no tool approval is pending.
            }

            clearStreamingToolState();
            finishProcessing();
        });
        return true;
    }

    private void startAgentLoop(final int version, final int loopStep) {
        startAgentLoop(version, loopStep, 0);
    }

    private void startAgentLoop(final int version, final int loopStep, final int llmAttempt) {
        if (!isActiveRun(version)) {
            return;
        }

        // Guard against runaway loops that would consume tokens indefinitely.
        // When the model keeps requesting failing tools the loop can run forever;
        // cap it and surface a clear error rather than silently burning credits.
        if (loopStep >= MAX_LOOP_STEPS) {
            mainHandler.post(() -> {
                if (!isActiveRun(version)) return;
                listener.onError(getString(R.string.chat_tool_loop_detected)
                        + " (>= " + MAX_LOOP_STEPS + " tool steps)");
                finishProcessing();
            });
            return;
        }

        // Compact old history asynchronously before this turn if it grew too large.
        if (!compactionInFlight && !compactionFailed && shouldCompactHistory()) {
            compactHistoryAsync(version, () -> startAgentLoop(version, loopStep, llmAttempt));
            return;
        }

        setState(State.THINKING);
        emitTrace("Agent loop", "step=" + loopStep);

        // Context assembly walks the project file tree and decrypts files — heavy
        // work that must NOT run on the UI thread. Previously it ran synchronously
        // on every loop step, so a turn with several tool calls froze the UI.
        // Build it on a background thread, then resume streaming on the main thread.
        final java.util.List<ChatMessage> historySnapshot = new java.util.ArrayList<>(messages);
        final String latestUser = findLatestUserMessage();
        final String agentGuidance = buildAgentGuidance();
        new Thread(() -> {
            final SharedPreferences prefs = AiChatSettingsHelper.prefs(pro.sketchware.SketchApplication.getContext());
            final String chatMode = AiChatSettingsHelper.getChatMode(prefs);
            final String providerId = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");

            long contextStartedAt = SystemClock.elapsedRealtime();
            final ContextBuilder.Result contextResult = new ContextBuilder(scId, historySnapshot, toolManager)
                    .setCompactedHistory(historySummary, historyCompactedUntil)
                    .setAgentGuidance(agentGuidance)
                    .setIncludeNativeReferences(loopStep == 0)
                    .build(latestUser, chatMode, providerId);
            final long contextMs = SystemClock.elapsedRealtime() - contextStartedAt;
            final JSONArray tools = toolManager.getToolsAsMCP(chatMode);
            if ("agent".equalsIgnoreCase(chatMode)) {
                appendMcpTools(tools, VoidPortMcpChannel.getToolsAsMCP(prefs));
                String githubToken = prefs.getString(VoidPortSettings.PREF_GITHUB_TOKEN, "").trim();
                if (!githubToken.isEmpty()) {
                    appendMcpTools(tools, GitHubMcpService.getToolDefinitions());
                }
            }

            mainHandler.post(() -> {
                if (!isActiveRun(version)) {
                    return;
                }
                if ("agent".equalsIgnoreCase(chatMode)) {
                    // Surface a debug notice for stdio-only MCP servers (Android can't spawn them).
                    emitMcpStdioWarning(prefs);
                }
                emitTrace(
                        "Contexto montado",
                        "build=" + contextMs + "ms, msgs=" + historySnapshot.size()
                                + ", tools=" + (tools == null ? 0 : tools.length())
                                + ", mode=" + chatMode
                                + ", provider=" + providerId
                );
                final ChatMessage botMsg = createThinkingMessage();
                currentStreamingMessage = botMsg;
                clearStreamingToolState();

                emitTrace("Chamada LLM iniciada");
                currentRequestHandle = aiService.sendStreamingMessage(contextResult, tools, chatMode,
                new AiProviderService.StreamListener() {
                    private final StringBuilder contentAccumulator = new StringBuilder();
                    private final StringBuilder reasoningAccumulator = new StringBuilder();
                    /** Final tool calls emitted this turn: [name, args, id]. */
                    private final java.util.List<String[]> collectedToolCalls = new java.util.ArrayList<>();
                    /** Repeated streaming updates for one tool id replace the prior payload. */
                    private final java.util.Map<String, Integer> collectedToolCallIndexes =
                            new java.util.LinkedHashMap<>();

                    @Override
                    public void onContent(String delta) {
                        if (!isActiveRun(version) || !ChatMessage.hasVisibleText(delta)) {
                            return;
                        }
                        contentAccumulator.append(delta);
                        botMsg.setStatus("");
                        botMsg.setDisplayContent(contentAccumulator.toString());
                        scheduleStreamUpdate(version, botMsg);
                    }

                    @Override
                    public void onReasoning(String delta) {
                        if (!isActiveRun(version) || !ChatMessage.hasVisibleText(delta)) {
                            return;
                        }
                        reasoningAccumulator.append(delta);
                        botMsg.setReasoning(reasoningAccumulator.toString());
                        scheduleStreamUpdate(version, botMsg);
                    }

                    @Override
                    public void onToolCall(String name, String arguments, String id) {
                        if (!isActiveRun(version) || !ChatMessage.hasVisibleText(name)) {
                            return;
                        }
                        // Sanitize the tool name: strip any characters that are not valid
                        // in a tool name. Some free/quantized models (e.g. gpt-oss-20b:free)
                        // leak internal tokens into tool names, producing strings like
                        // "edit_file<|channel|>commentary" that the tool registry cannot
                        // recognise. The regex keeps only ASCII word chars, hyphens and dots.
                        String sanitized = TOOL_NAME_SANITIZER.matcher(name.trim()).replaceAll("");
                        if (sanitized.isEmpty()) {
                            return;
                        }
                        String safeArgs = ChatMessage.hasVisibleText(arguments) ? arguments : "{}";
                        String safeId = ChatMessage.hasVisibleText(id) ? id : "";
                        collectOrReplaceToolCall(
                                collectedToolCalls,
                                collectedToolCallIndexes,
                                sanitized,
                                safeArgs,
                                safeId);
                        streamingToolName = sanitized;
                        streamingMcpServerName = resolveMcpServerName(sanitized);
                        if (!safeId.isEmpty()) {
                            streamingToolId = safeId;
                        }
                    }

                    @Override
                    public void onDebug(String message) {
                        if (!isActiveRun(version) || !ChatMessage.hasVisibleText(message)) {
                            return;
                        }
                        mainHandler.post(() -> {
                            if (!isActiveRun(version)) {
                                return;
                            }
                            listener.onDebug(message);
                        });
                    }

                    @Override
                    public void onFinalMessage(String fullContent, String fullReasoning) {
                        if (!isActiveRun(version)) {
                            return;
                        }
                        mainHandler.post(() -> {
                            if (!isActiveRun(version)) {
                                return;
                            }
                            currentRequestHandle = null;

                            flushStreamUpdate(version);

                            ToolCallDetector.DetectionResult toolDetection = toolCallDetector.detect(
                                    new ToolCallDetector.Response(
                                            fullContent,
                                            fullReasoning,
                                            toToolCalls(collectedToolCalls),
                                            tools
                                    )
                            );

                            if (ChatMessage.hasVisibleText(toolDetection.getCleanedContent())) {
                                botMsg.setDisplayContent(toolDetection.getCleanedContent());
                            } else {
                                botMsg.setDisplayContent("");
                            }
                            if (ChatMessage.hasVisibleText(toolDetection.getCleanedReasoning())) {
                                botMsg.setReasoning(toolDetection.getCleanedReasoning());
                            } else {
                                botMsg.setReasoning("");
                            }
                            botMsg.setStatus("");

                            boolean hasAssistantPayload = botMsg.hasDisplayContent() || botMsg.hasReasoningContent();
                            if (toolDetection.hasToolCalls()) {
                                if (hasAssistantPayload) {
                                    listener.onMessageUpdated(botMsg);
                                } else {
                                    removeStreamingPlaceholderIfEmpty(botMsg);
                                }
                                currentStreamingMessage = null;
                                emitTrace("LLM pediu ferramentas", "protocol=" + toolDetection.getProtocol()
                                        + ", count=" + toolDetection.getToolCalls().size());
                                clearStreamingToolState();
                                queuedToolCalls.clear();
                                for (ToolCall call : toolDetection.getToolCalls()) {
                                    queuedToolCalls.add(call.toLegacyArray());
                                }
                                queuedChatMode = chatMode;
                                processNextQueuedToolCall(version, loopStep);
                                return;
                            }

                            clearStreamingToolState();
                            if (!hasAssistantPayload) {
                                removeStreamingPlaceholderIfEmpty(botMsg);
                            } else {
                                if (isInsufficientBalanceText(botMsg.getDisplayContent())) {
                                    botMsg.setDisplayContent(clearInsufficientBalanceMessage());
                                    listener.onMessageUpdated(botMsg);
                                    emitTrace("Saldo insuficiente", "mensagem normalizada");
                                    finishProcessing();
                                    return;
                                }
                                listener.onMessageUpdated(botMsg);
                            }
                            FinishChecker.ValidationResult finishResult = FinishChecker.validate(
                                    agentMemory,
                                    requestPattern,
                                    taskPlan,
                                    toolUsageHistory,
                                    botMsg.getDisplayContent(),
                                    chatMode
                            );
                            if (!finishResult.canFinish()
                                    && finishValidationFailures < MAX_FINISH_REJECTIONS
                                    && loopStep + 1 < MAX_LOOP_STEPS) {
                                finishValidationFailures++;
                                pendingAgentFeedback = finishResult.getFeedbackPrompt();
                                emitTrace("Finalizacao adiada", finishResult.getReason());
                                startAgentLoop(version, loopStep + 1);
                                return;
                            }
                            if (!finishResult.canFinish()) {
                                emitTrace("Finalizacao bloqueada", finishResult.getReason());
                                listener.onError("O agente nao concluiu as etapas obrigatorias: "
                                        + finishResult.getReason());
                            }
                            emitTraceSummary("resposta final sem ferramenta");
                            finishProcessing();
                        });
                    }

                    @Override
                    public void onError(String message, Throwable t) {
                        if (!isActiveRun(version) || "cancelled".equalsIgnoreCase(message)) {
                            return;
                        }
                        currentRequestHandle = null;
                        mainHandler.post(() -> {
                            if (!isActiveRun(version)) {
                                return;
                            }
                            if (llmAttempt + 1 < MAX_LLM_ATTEMPTS) {
                                removeMessage(botMsg);
                                currentStreamingMessage = null;
                                clearStreamingToolState();
                                emitTrace("Retry LLM", "attempt=" + (llmAttempt + 2));
                                setState(State.THINKING);
                                mainHandler.postDelayed(
                                        () -> startAgentLoop(version, loopStep, llmAttempt + 1),
                                        LLM_RETRY_DELAY_MS
                                );
                                return;
                            }
                            setState(State.ERROR);
                            removeStreamingPlaceholderIfEmpty(botMsg);
                            String clearMessage = normalizeLlmError(message);
                            emitTrace("Erro LLM", clearMessage);
                            listener.onError(clearMessage);
                            emitTraceSummary("erro");
                            finishProcessing();
                        });
                    }
                });
            });
        }, "chat-context-builder").start();
    }

    private ChatMessage createThinkingMessage() {
        ChatMessage botMsg = new ChatMessage("", false, System.currentTimeMillis());
        botMsg.setStatus("");
        botMsg.setStreaming(true);
        messages.add(botMsg);
        listener.onMessageAdded(botMsg);
        return botMsg;
    }

    /** True when the non-compacted history is large enough to justify a summarization pass. */
    private boolean shouldCompactHistory() {
        int end = messages.size() - COMPACT_KEEP_TAIL;
        if (end - historyCompactedUntil < 4) {
            return false;
        }
        long chars = 0;
        for (int i = historyCompactedUntil; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (m == null) {
                continue;
            }
            chars += safe(m.getDisplayContent()).length()
                    + safe(m.getToolResult()).length()
                    + safe(m.getToolArgs()).length();
            if (chars > COMPACT_THRESHOLD_CHARS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Summarizes messages[historyCompactedUntil, size-KEEP_TAIL) on a background
     * thread and swaps them for a summary in the LLM context (UI untouched).
     * On any failure compaction is disabled for this session and the loop
     * continues with plain truncation as before.
     */
    private void compactHistoryAsync(int version, Runnable continuation) {
        compactionInFlight = true;
        final int end = Math.max(historyCompactedUntil, messages.size() - COMPACT_KEEP_TAIL);
        final StringBuilder transcript = new StringBuilder();
        if (!historySummary.isEmpty()) {
            transcript.append("[Resumo acumulado até aqui]\n").append(historySummary).append("\n\n");
        }
        for (int i = historyCompactedUntil; i < end; i++) {
            ChatMessage m = messages.get(i);
            if (m == null || m.isCheckpoint()) {
                continue;
            }
            if (m.isUser()) {
                transcript.append("USUÁRIO: ").append(safe(m.getDisplayContent())).append('\n');
            } else if (m.isTool()) {
                transcript.append("FERRAMENTA ").append(safe(m.getToolName()))
                        .append(" args=").append(truncateForTranscript(safe(m.getToolArgs()), 400))
                        .append(" resultado=").append(truncateForTranscript(safe(m.getToolResult()), 1200))
                        .append('\n');
            } else {
                transcript.append("ASSISTENTE: ").append(safe(m.getDisplayContent())).append('\n');
            }
            if (transcript.length() > COMPACT_TRANSCRIPT_MAX_CHARS) {
                break;
            }
        }

        emitTrace("Compactação iniciada", "msgs=" + (end - historyCompactedUntil)
                + ", transcriptChars=" + transcript.length());

        new Thread(() -> {
            String summary = null;
            try {
                summary = aiService.sendTextMessage(
                        "Você é um sumarizador de contexto de um agente de programação. "
                                + "Resuma a conversa a seguir preservando: objetivo do usuário, decisões tomadas, "
                                + "preferências do usuário, arquivos criados/alterados (com caminhos), resultados relevantes "
                                + "de ferramentas, erros encontrados e estado atual da tarefa. Descarte detalhes repetitivos, "
                                + "saídas extensas e dados transitórios. Seja denso e factual; máximo ~400 palavras.",
                        truncateForTranscript(transcript.toString(), COMPACT_TRANSCRIPT_MAX_CHARS));
            } catch (Exception ignored) {
            }
            final String result = summary;
            mainHandler.post(() -> {
                compactionInFlight = false;
                if (result != null && !result.trim().isEmpty()) {
                    historySummary = result.trim();
                    historyCompactedUntil = end;
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

    private String truncateForTranscript(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }

    /** Runs the next queued tool call, or advances the agent loop when the queue drains. */
    private void processNextQueuedToolCall(int version, int loopStep) {
        if (!isActiveRun(version)) {
            return;
        }
        String[] next = queuedToolCalls.pollFirst();
        if (next == null) {
            startAgentLoop(version, loopStep + 1);
            return;
        }
        handleToolCall(next[0], next[1], next[2], version, loopStep, queuedChatMode);
    }

    private void handleToolCall(String name, String args, String id, int version, int loopStep, String chatMode) {
        ToolSequenceValidator.ValidationResult sequenceResult = ToolSequenceValidator.validate(
                name,
                args == null ? "{}" : args,
                toolUsageHistory,
                null
        );
        if (!sequenceResult.isValid()) {
            String guidance = sequenceResult.getSuggestion();
            addUnavailableToolMessage(name, args, id, chatMode, version, loopStep,
                    sequenceResult.getErrorMessage()
                            + (guidance == null || guidance.isEmpty() ? "" : " " + guidance));
            return;
        }
        // Anti-loop detection: sliding-window cycle detection (periods 1-3).
        String signature = name + ":" + args;
        recentToolSignatures.addLast(signature);
        while (recentToolSignatures.size() > SIGNATURE_WINDOW) {
            recentToolSignatures.removeFirst();
        }

        if (detectSignatureCycle()) {
            String advice = getString(R.string.chat_tool_loop_detected);
            if ("get_file".equals(name)) {
                advice += " " + getString(R.string.chat_tool_loop_use_read_file);
            } else if ("run_command".equals(name) || "run_persistent_command".equals(name)) {
                advice += " " + getString(R.string.chat_tool_loop_use_edit_tools);
            } else {
                advice += " " + getString(R.string.chat_tool_loop_try_different);
            }
            recentToolSignatures.clear();
            addUnavailableToolMessage(name, args, id, chatMode, version, loopStep, advice);
            return;
        }

        if ("get_file".equals(name)) {
            addUnavailableToolMessage(name, args, id, chatMode, version, loopStep,
                    "Erro: ferramenta 'get_file' não existe. Use 'read_file' para ler arquivos.");
            return;
        }

        Tool tool = toolManager.getTool(name);
        boolean mcpTool = tool == null && isMcpToolAvailable(name, chatMode);
        if ((!mcpTool && tool == null) || (!mcpTool && !toolManager.hasToolForChatMode(name, chatMode))) {
            addUnavailableToolMessage(name, args, id, chatMode, version, loopStep, null);
            return;
        }

        boolean needsApproval = mcpTool
                ? !VoidPortSettings.isAutoApprovalEnabled(
                        VoidPortSettings.prefs(context),
                        VoidPortSettings.APPROVAL_MCP_TOOLS)
                : VoidPortSettings.requiresApproval(context, tool);

        ChatMessage toolMsg = new ChatMessage(name, args, System.currentTimeMillis(), id);
        toolMsg.setToolState(needsApproval ? "tool_request" : "running_now");
        toolMsg.setRequiresApproval(needsApproval);
        toolMsg.setStatus(needsApproval
                ? getString(R.string.chat_tool_status_waiting_approval)
                : getString(R.string.chat_tool_status_running));
        toolMsg.setDisplayContent(needsApproval
                ? getString(R.string.chat_tool_approval_message_named, name)
                : getString(R.string.chat_tool_running_message));
        toolMsg.setMcpServerName(mcpTool ? resolveMcpServerName(name) : null);
        pendingToolMessage = toolMsg;
        pendingToolLoopStep = loopStep;

        final Tool previewTool = mcpTool ? null : tool;
        mainHandler.post(() -> {
            if (!isActiveRun(version)) {
                return;
            }

            messages.add(toolMsg);
            listener.onMessageAdded(toolMsg);
            emitTrace("Ferramenta na fila", "name=" + name + ", approval=" + needsApproval);

            if (needsApproval) {
                setState(State.AWAITING_APPROVAL);
                // Build the diff preview OFF the UI thread (the LCS diff is heavy)
                // and refresh the message when ready — the user is reviewing anyway.
                if (previewTool != null && previewTool.isDestructive()) {
                    new Thread(() -> {
                        prepareToolPreview(toolMsg, previewTool);
                        mainHandler.post(() -> {
                            if (isActiveRun(version)) {
                                listener.onMessageUpdated(toolMsg);
                            }
                        });
                    }, "chat-tool-preview").start();
                }
            } else {
                executeTool(toolMsg, version, loopStep);
            }
        });
    }

    private void addUnavailableToolMessage(String name, String args, String id, String chatMode, int version, int loopStep, String customError) {
        String safeName = name == null ? "" : name.trim();
        String mode = chatMode == null || chatMode.trim().isEmpty() ? "agent" : chatMode.trim();
        String availableTools = toolManager.getToolNamesForChatMode(mode);
        String result = (customError != null) ? customError : "Erro: ferramenta '" + safeName + "' nao esta disponivel no modo '" + mode + "'.";
        if (!availableTools.isEmpty()) {
            result += " Ferramentas disponiveis: " + availableTools + ".";
        }

        ChatMessage toolMsg = new ChatMessage(safeName, args, System.currentTimeMillis(), id);
        toolMsg.setToolRunning(false);
        toolMsg.setToolError(true);
        toolMsg.setToolState("error");
        toolMsg.setStatus(getString(R.string.chat_tool_status_error));
        toolMsg.setDisplayContent(getString(R.string.chat_tool_error_message));
        toolMsg.setToolResult(result);
        pendingToolMessage = null;

        mainHandler.post(() -> {
            if (!isActiveRun(version)) {
                return;
            }
            messages.add(toolMsg);
            listener.onMessageAdded(toolMsg);
            consecutiveToolFailures++;
            if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                listener.onError(consecutiveToolFailureMessage());
                finishProcessing();
                return;
            }
            processNextQueuedToolCall(version, loopStep);
        });
    }

    public void approveTool() {
        if (currentState != State.AWAITING_APPROVAL || pendingToolMessage == null) {
            return;
        }

        pendingToolMessage.setApproved(true);
        pendingToolMessage.setToolState("running_now");
        pendingToolMessage.setStatus(getString(R.string.chat_tool_status_approved));
        pendingToolMessage.setDisplayContent(getString(R.string.chat_tool_approved_message));
        listener.onMessageUpdated(pendingToolMessage);
        executeTool(pendingToolMessage, runVersion, pendingToolLoopStep);
    }

    public void rejectTool() {
        if (currentState != State.AWAITING_APPROVAL || pendingToolMessage == null) {
            return;
        }

        pendingToolMessage.setRejected(true);
        pendingToolMessage.setToolRunning(false);
        pendingToolMessage.setToolError(true);
        pendingToolMessage.setToolState("rejected");
        pendingToolMessage.setStatus(getString(R.string.chat_tool_status_rejected));
        pendingToolMessage.setDisplayContent(getString(R.string.chat_tool_rejected_message));
        pendingToolMessage.setToolResult(getString(R.string.chat_tool_rejected_message));
        listener.onMessageUpdated(pendingToolMessage);
        finishProcessing();
    }

    private void executeTool(final ChatMessage toolMsg, final int version, final int loopStep) {
        if (!isActiveRun(version)) {
            return;
        }

        setState(State.EXECUTING_TOOL);
        toolMsg.setStatus(getString(R.string.chat_tool_status_running));
        toolMsg.setDisplayContent(getString(R.string.chat_tool_running_message));
        listener.onMessageUpdated(toolMsg);

        emitTrace("Ferramenta iniciada", "name=" + toolMsg.getToolName());
        final long toolStartedAt = SystemClock.elapsedRealtime();
        currentToolThread = new Thread(() -> {
            ChatCheckpointManager.CheckpointEntry checkpointEntry = createCheckpointIfNeeded(toolMsg);
            if (checkpointEntry != null) {
                mainHandler.post(() -> {
                    if (!isActiveRun(version)) {
                        return;
                    }
                    // Turn-level (transactional) checkpoint: all files touched during
                    // the same run share ONE checkpoint message, so a rollback
                    // restores the whole turn instead of a single file.
                    if (currentRunCheckpointMessage != null
                            && mergeSnapshotIntoCheckpoint(currentRunCheckpointMessage, checkpointEntry)) {
                        listener.onMessageUpdated(currentRunCheckpointMessage);
                        return;
                    }
                    ChatMessage checkpointMsg = checkpointEntry.toChatMessage();
                    currentRunCheckpointMessage = checkpointMsg;
                    messages.add(checkpointMsg);
                    listener.onMessageAdded(checkpointMsg);
                });
            }

            pro.sketchware.ia.tools.ToolExecResult execResult = executeToolCall(toolMsg);
            final String result = execResult.output;
            boolean isError = !execResult.ok;
            final long toolDurationMs = SystemClock.elapsedRealtime() - toolStartedAt;

            mainHandler.post(() -> {
                currentToolThread = null;
                if (!isActiveRun(version)) {
                    return;
                }
                emitTrace(
                        "Ferramenta concluída",
                        "name=" + toolMsg.getToolName()
                                + ", ok=" + !isError
                                + ", duration=" + toolDurationMs + "ms"
                                + ", resultChars=" + (result == null ? 0 : result.length())
                );

                toolMsg.setToolRunning(false);
                toolMsg.setToolError(isError);
                toolMsg.setToolState(isError ? "error" : "success");
                toolMsg.setToolResult(result);
                toolMsg.setStatus(getString(isError
                        ? R.string.chat_tool_status_error
                        : R.string.chat_tool_status_done));
                toolMsg.setDisplayContent(getString(isError
                        ? R.string.chat_tool_error_message
                        : R.string.chat_tool_done_message));
                toolMsg.setExpanded(isError);
                listener.onMessageUpdated(toolMsg);

                toolUsageHistory.add(ToolSequenceValidator.createUsage(
                        toolMsg.getToolName(),
                        toolMsg.getToolArgs() == null ? "{}" : toolMsg.getToolArgs(),
                        !isError
                ));

                if (!isError) {
                    consecutiveToolFailures = 0;
                    String toolName = toolMsg.getToolName();
                    boolean isMutation = "rewrite_file".equals(toolName) ||
                            "edit_file".equals(toolName) ||
                            "create_file_or_folder".equals(toolName) ||
                            "delete_file_or_folder".equals(toolName);
                    listener.onToolExecuted(toolName, isMutation);
                    if (isMutation) {
                        ContextBuilder.invalidateWorkspaceCache(scId);
                    }
                    if (taskPlan != null) {
                        taskPlan.recordToolUsage(toolName);
                        if (agentMemory != null) {
                            agentMemory.setProgress(taskPlan.getCompletedSteps(), taskPlan.getTotalSteps());
                        }
                    }
                } else {
                    consecutiveToolFailures++;
                    RetryManager.RetryDecision retryDecision = RetryManager.shouldRetry(
                            toolMsg.getToolName(),
                            toolMsg.getToolArgs() == null ? "{}" : toolMsg.getToolArgs(),
                            result == null ? "" : result,
                            consecutiveToolFailures,
                            toolUsageHistory
                    );
                    if (retryDecision.shouldRetry()
                            && retryDecision.getAlternativeTool() != null
                            && retryDecision.getAlternativeArgs() != null
                            && toolManager.hasToolForChatMode(
                                    retryDecision.getAlternativeTool(), queuedChatMode)) {
                        queuedToolCalls.addFirst(new String[]{
                                retryDecision.getAlternativeTool(),
                                retryDecision.getAlternativeArgs(),
                                ""
                        });
                        emitTrace("Retry alternativo", retryDecision.getReason());
                    }
                    if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                        // Stop burning tokens: repeated tool failures indicate the
                        // model is stuck; surface the problem instead of looping.
                        emitTrace("Loop de falhas", "falhas consecutivas=" + consecutiveToolFailures);
                        listener.onError(consecutiveToolFailureMessage());
                        clearPendingToolState();
                        finishProcessing();
                        return;
                    }
                }

                clearPendingToolState();
                processNextQueuedToolCall(version, loopStep);
            });
        }, "chat-tool-worker");
        currentToolThread.start();
    }

    private pro.sketchware.ia.tools.ToolExecResult executeToolCall(ChatMessage toolMsg) {
        String toolName = toolMsg.getToolName();
        if (toolName != null && toolName.startsWith("mcp_")) {
            return pro.sketchware.ia.tools.ToolExecResult.fromLegacyString(VoidPortMcpChannel.callTool(
                    VoidPortSettings.prefs(context),
                    toolName,
                    parseToolArgs(toolMsg.getToolArgs())
            ));
        }
        // GitHub MCP tools — dispatched natively via GitHubMcpService
        if (toolName != null && toolName.startsWith(GitHubMcpService.TOOL_PREFIX)) {
            return pro.sketchware.ia.tools.ToolExecResult.fromLegacyString(GitHubMcpService.callTool(
                    VoidPortSettings.prefs(context),
                    toolName,
                    parseToolArgs(toolMsg.getToolArgs())
            ));
        }
        return toolManager.executeTool(scId, toolName, toolMsg.getToolArgs());
    }

    private void appendMcpTools(JSONArray target, JSONArray mcpTools) {
        if (target == null || mcpTools == null || mcpTools.length() == 0) {
            return;
        }
        for (int i = 0; i < mcpTools.length(); i++) {
            JSONObject tool = mcpTools.optJSONObject(i);
            if (tool != null) {
                target.put(tool);
            }
        }
    }

    private boolean isMcpToolAvailable(String name, String chatMode) {
        if (!"agent".equalsIgnoreCase(chatMode) || name == null || !name.startsWith("mcp_")) {
            return false;
        }
        JSONArray mcpTools = VoidPortMcpChannel.getToolsAsMCP(VoidPortSettings.prefs(context));
        for (int i = 0; i < mcpTools.length(); i++) {
            JSONObject tool = mcpTools.optJSONObject(i);
            JSONObject function = tool == null ? null : tool.optJSONObject("function");
            if (function != null && name.equals(function.optString("name", ""))) {
                return true;
            }
        }
        return false;
    }

    private void prepareToolPreview(ChatMessage toolMsg, Tool tool) {
        if (toolMsg == null || tool == null || !tool.isDestructive()) {
            return;
        }

        try {
            JSONObject args = parseToolArgs(toolMsg.getToolArgs());
            String filePath = normalizeToolPath(toolPathArg(args));
            String content = args.optString("new_content", "");
            if (content.isEmpty()) {
                content = args.optString("search_replace_blocks", "");
            }
            if (content.isEmpty()) {
                content = args.optString("content", "");
            }
            if (content.isEmpty()) {
                content = args.optString("code_edit", "");
            }
            if (filePath.isEmpty() || content.isEmpty()) {
                return;
            }

            boolean existedBefore = SketchwareFileDecryptor.fileExists(scId, filePath);
            String beforeContent = existedBefore ? safe(SketchwareFileDecryptor.decryptFile(scId, filePath)) : "";
            String preview = buildVoidPreview(filePath, beforeContent, content, existedBefore);
            toolMsg.setToolResult(preview);
        } catch (Exception ignored) {
        }
    }

    private String buildVoidPreview(String filePath, String beforeContent, String generatedContent, boolean existedBefore) {
        String cleanedContent = extractRegularCode(generatedContent);
        List<ExtractCodeFromResult.ExtractedSearchReplaceBlock> blocks =
                ExtractCodeFromResult.extractSearchReplaceBlocks(cleanedContent);
        if (!blocks.isEmpty()) {
            return buildSearchReplacePreview(filePath, cleanedContent, blocks);
        }
        return buildWholeFilePreview(filePath, beforeContent, cleanedContent, existedBefore);
    }

    private String buildSearchReplacePreview(String filePath, String content,
                                             List<ExtractCodeFromResult.ExtractedSearchReplaceBlock> blocks) {
        String language = LanguageHelpers.detectLanguage(filePath, content);
        StringBuilder builder = new StringBuilder();
        builder.append("VOID SEARCH/REPLACE PREVIEW\n");
        builder.append("File: ").append(filePath).append("\n");
        builder.append("Language: ").append(language).append("\n");
        builder.append("Actions: ")
                .append(ActionIds.VOID_ACCEPT_DIFF_ACTION_ID)
                .append(" / ")
                .append(ActionIds.VOID_REJECT_DIFF_ACTION_ID)
                .append("\n\n");

        int printed = 0;
        for (int i = 0; i < blocks.size() && printed < MAX_PREVIEW_LINES; i++) {
            ExtractCodeFromResult.ExtractedSearchReplaceBlock block = blocks.get(i);
            builder.append("Block ").append(i + 1).append(" - ").append(block.state).append("\n");
            builder.append(PromptConstants.TRIPLE_TICK.get(0)).append(language).append("\n");
            builder.append(PromptConstants.ORIGINAL).append("\n");
            printed = appendPreviewLines(builder, block.orig, printed);
            builder.append(PromptConstants.DIVIDER).append("\n");
            printed = appendPreviewLines(builder, block.fin, printed);
            builder.append(PromptConstants.FINAL).append("\n");
            builder.append(PromptConstants.TRIPLE_TICK.get(1)).append("\n\n");
        }
        if (printed >= MAX_PREVIEW_LINES) {
            builder.append("... preview truncated ...\n");
        }
        return builder.toString().trim();
    }

    private String buildWholeFilePreview(String filePath, String beforeContent, String afterContent, boolean existedBefore) {
        String safeBefore = safe(beforeContent);
        String safeAfter = safe(afterContent);
        String language = LanguageHelpers.detectLanguage(filePath, safeAfter);
        List<VoidPortDiffService.ComputedDiff> diffs =
                VoidPortDiffService.findDiffs(safeBefore, safeAfter);

        StringBuilder builder = new StringBuilder();
        builder.append("VOID DIFF PREVIEW\n");
        builder.append("File: ").append(filePath).append("\n");
        builder.append("Mode: ").append(existedBefore ? "update" : "create").append("\n");
        builder.append("Language: ").append(language).append("\n");
        builder.append("Actions: ")
                .append(ActionIds.VOID_ACCEPT_FILE_ACTION_ID)
                .append(" / ")
                .append(ActionIds.VOID_REJECT_FILE_ACTION_ID)
                .append("\n\n");

        if (diffs.isEmpty()) {
            builder.append("No content changes detected.");
            return builder.toString();
        }

        int printed = 0;
        for (int i = 0; i < diffs.size() && printed < MAX_PREVIEW_LINES; i++) {
            VoidPortDiffService.ComputedDiff diff = diffs.get(i);
            builder.append("Change ")
                    .append(i + 1)
                    .append(" - ")
                    .append(diff.type)
                    .append(" original lines ")
                    .append(formatLineRange(diff.originalStartLine, diff.originalEndLine))
                    .append(" -> new lines ")
                    .append(formatLineRange(diff.startLine, diff.endLine))
                    .append("\n");
            builder.append(PromptConstants.TRIPLE_TICK.get(0)).append(language).append("\n");
            builder.append(PromptConstants.ORIGINAL).append("\n");
            printed = appendPreviewLines(builder, diff.originalCode, printed);
            builder.append(PromptConstants.DIVIDER).append("\n");
            printed = appendPreviewLines(builder, diff.code, printed);
            builder.append(PromptConstants.FINAL).append("\n");
            builder.append(PromptConstants.TRIPLE_TICK.get(1)).append("\n\n");
        }

        if (printed >= MAX_PREVIEW_LINES) {
            builder.append("... preview truncated ...\n");
        }
        return builder.toString().trim();
    }

    private String formatLineRange(int startLine, int endLine) {
        if (startLine <= 0 || endLine < startLine) {
            return "none";
        }
        if (startLine == endLine) {
            return String.valueOf(startLine);
        }
        return startLine + "-" + endLine;
    }

    private String extractRegularCode(String content) {
        ExtractCodeFromResult.Extraction extraction =
                ExtractCodeFromResult.extractCodeFromRegular(content, content == null ? 0 : content.length());
        return extraction.fullText;
    }

    private int appendPreviewLines(StringBuilder builder, String content, int printed) {
        return appendLineRange(builder, splitLines(safe(content)), 0, splitLines(safe(content)).length, printed);
    }

    private int appendLineRange(StringBuilder builder, String[] lines, int start, int end, int printed) {
        for (int i = start; i < end && printed < MAX_PREVIEW_LINES; i++) {
            builder.append(lines[i]).append("\n");
            printed++;
        }
        return printed;
    }

    private String[] splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return new String[0];
        }
        return content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }

    private ChatCheckpointManager.CheckpointEntry createCheckpointIfNeeded(ChatMessage toolMsg) {
        Tool tool = toolManager.getTool(toolMsg.getToolName());
        if (tool == null || (!tool.isDestructive() && !tool.isFileMutation())) {
            return null;
        }

        try {
            JSONObject args = parseToolArgs(toolMsg.getToolArgs());
            String filePath = normalizeToolPath(toolPathArg(args));
            if (filePath.isEmpty()) {
                return null;
            }

            boolean existedBefore = SketchwareFileDecryptor.fileExists(scId, filePath);
            String beforeContent = existedBefore ? safe(SketchwareFileDecryptor.decryptFile(scId, filePath)) : "";
            return checkpointManager.createCheckpoint(
                    scId,
                    toolMsg.getToolId() != null ? toolMsg.getToolId() : "",
                    safe(toolMsg.getToolName()),
                    filePath,
                    beforeContent,
                    existedBefore
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private void clearPendingToolState() {
        pendingToolMessage = null;
        pendingToolLoopStep = -1;
    }

    private void scheduleStreamUpdate(int version, ChatMessage message) {
        if (!isActiveRun(version) || message == null) {
            return;
        }
        pendingStreamMessage = message;
        if (streamUpdateScheduled) {
            return;
        }
        streamUpdateScheduled = true;
        streamCoalesceHandler.postDelayed(() -> {
            streamUpdateScheduled = false;
            flushStreamUpdate(version);
        }, STREAM_COALESCE_MS);
    }

    private void flushStreamUpdate(int version) {
        if (!isActiveRun(version)) {
            return;
        }
        ChatMessage message = pendingStreamMessage;
        pendingStreamMessage = null;
        if (message != null) {
            listener.onMessageUpdated(message);
        }
    }

    private void clearStreamingToolState() {
        streamingToolName = "";
        streamingToolId = "";
        streamingMcpServerName = null;
    }

    @Nullable
    private String resolveMcpServerName(String toolName) {
        if (toolName == null || !toolName.startsWith("mcp_")) {
            return null;
        }
        SharedPreferences prefs = VoidPortSettings.prefs(context);
        return VoidPortMcpChannel.resolveServerNameForTool(prefs, toolName);
    }

    /**
     * Emits a one-time debug notice per run when stdio-only MCP servers are
     * configured. Android cannot spawn desktop stdio processes, so those servers
     * are silently skipped by {@link VoidPortMcpChannel}; surfacing the warning
     * here prevents confusing "tool not found" errors for the user.
     */
    private boolean mcpStdioWarningEmitted = false;

    private void emitMcpStdioWarning(SharedPreferences prefs) {
        if (mcpStdioWarningEmitted) {
            return;
        }
        java.util.List<VoidPortMcpChannel.ServerStatus> statuses = VoidPortMcpChannel.readServerStatuses(prefs);
        boolean hasStdio = false;
        for (VoidPortMcpChannel.ServerStatus s : statuses) {
            if ("stdio-config-only".equals(s.status)) {
                hasStdio = true;
                break;
            }
        }
        if (hasStdio) {
            mcpStdioWarningEmitted = true;
            listener.onDebug("[MCP] Aviso: um ou mais servidores MCP usam stdio/command e não podem ser iniciados pelo Android. " +
                    "Exponha-os como endpoint HTTP em mcpServers para usá-los aqui.");
        }
    }

    private void finishProcessing() {
        streamCoalesceHandler.removeCallbacksAndMessages(null);
        streamUpdateScheduled = false;
        pendingStreamMessage = null;
        queuedToolCalls.clear();
        clearPendingToolState();
        clearStreamingToolState();
        currentStreamingMessage = null;
        currentToolThread = null;
        setState(State.IDLE);
        if (interactionTrace != null) {
            emitTraceSummary("processamento concluído");
        }
        listener.onProcessingFinished();
    }

    private void initializeAgentExecution(String userText, String contextPayload,
                                          List<ChatReference> stagingSelections) {
        String safeText = userText == null ? "" : userText.trim();
        if (safeText.isEmpty()) {
            agentMemory = null;
            requestPattern = null;
            taskPlan = null;
            return;
        }
        requestPattern = PatternMatcher.analyze(safeText, contextPayload, stagingSelections);
        AgentMemory.Builder memoryBuilder = AgentMemory.builder(safeText)
                .originalSelections(stagingSelections)
                .addKeyFiles(requestPattern.getExtractedFilePaths());
        agentMemory = memoryBuilder.build();
        taskPlan = requestPattern.isChatOnly() || !requestPattern.hasRequiredTools()
                ? null
                : TaskPlanner.createPlan(requestPattern, safeText);
        if (taskPlan != null) {
            agentMemory.setProgress(0, taskPlan.getTotalSteps());
        }
        pendingAgentFeedback = "";
        toolUsageHistory.clear();
        finishValidationFailures = 0;
    }

    private String buildAgentGuidance() {
        StringBuilder guidance = new StringBuilder();
        if (agentMemory != null) {
            guidance.append(agentMemory.buildContextInjection());
        }
        if (taskPlan != null) {
            if (guidance.length() > 0) {
                guidance.append("\n\n");
            }
            guidance.append(taskPlan.buildPlanSummary());
        }
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
        mcpStdioWarningEmitted = false;
        recentToolSignatures.clear();
        consecutiveToolFailures = 0;
        queuedToolCalls.clear();
        toolUsageHistory.clear();
        pendingAgentFeedback = "";
        finishValidationFailures = 0;
        currentRunCheckpointMessage = null;
        int textChars = userText == null ? 0 : userText.trim().length();
        int selectionCount = stagingSelections == null ? 0 : stagingSelections.size();
        int imageCount = stagingSelections == null ? 0 : ChatReferenceManager.getImageReferences(stagingSelections).size();
        emitTrace("Interação iniciada", "textChars=" + textChars + ", selections=" + selectionCount + ", images=" + imageCount);
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

    /**
     * True when the last tool calls form a repeating cycle of period 1-3
     * (e.g. A A A, A B A B A B, A B C A B C) — three full repetitions required.
     */
    private boolean detectSignatureCycle() {
        String[] window = recentToolSignatures.toArray(new String[0]);
        for (int period = 1; period <= 3; period++) {
            int needed = period * 3;
            if (window.length < needed) {
                continue;
            }
            boolean cycle = true;
            for (int i = window.length - needed; i < window.length - period; i++) {
                if (!window[i].equals(window[i + period])) {
                    cycle = false;
                    break;
                }
            }
            if (cycle) {
                return true;
            }
        }
        return false;
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

    private JSONObject parseToolArgs(String toolArgs) {
        try {
            if (toolArgs == null || toolArgs.trim().isEmpty() || "null".equals(toolArgs.trim())) {
                return new JSONObject();
            }
            return new JSONObject(toolArgs);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private java.util.List<ToolCall> toToolCalls(java.util.List<String[]> legacyCalls) {
        java.util.List<ToolCall> result = new java.util.ArrayList<>();
        if (legacyCalls == null) {
            return result;
        }
        for (String[] legacyCall : legacyCalls) {
            ToolCall call = ToolCall.fromLegacyArray(legacyCall);
            if (call.isValid()) {
                result.add(call);
            }
        }
        return result;
    }

    static void collectOrReplaceToolCall(java.util.List<String[]> calls,
                                         java.util.Map<String, Integer> indexesById,
                                         String name,
                                         String args,
                                         String id) {
        String[] value = new String[]{name, args, id};
        if (id == null || id.trim().isEmpty()) {
            calls.add(value);
            return;
        }
        Integer existingIndex = indexesById.get(id);
        if (existingIndex != null && existingIndex >= 0 && existingIndex < calls.size()) {
            calls.set(existingIndex, value);
            return;
        }
        indexesById.put(id, calls.size());
        calls.add(value);
    }

    private String consecutiveToolFailureMessage() {
        return "Erro: limite de falhas consecutivas de ferramentas atingido ("
                + consecutiveToolFailures + ").";
    }

    private String normalizeLlmError(String message) {
        return isInsufficientBalanceText(message) ? clearInsufficientBalanceMessage() : message;
    }

    private boolean isInsufficientBalanceText(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String text = message.toLowerCase(java.util.Locale.US);
        return text.contains("insufficient_quota")
                || text.contains("insufficient balance")
                || text.contains("insufficient credits")
                || text.contains("not enough credits")
                || text.contains("no credit")
                || text.contains("credit balance")
                || text.contains("balance_not_enough")
                || text.contains("quota exceeded")
                || text.contains("billing hard limit")
                || text.contains("payment required")
                || text.contains("saldo insuficiente")
                || text.contains("creditos insuficientes")
                || text.contains("créditos insuficientes");
    }

    private String clearInsufficientBalanceMessage() {
        return "Saldo insuficiente na API selecionada. Recarregue créditos, troque a API key ou escolha outro modelo/provedor para continuar.";
    }

    private String toolPathArg(JSONObject args) {
        if (args == null) {
            return "";
        }
        String uri = args.optString("uri", "");
        if (!uri.trim().isEmpty()) {
            return uri;
        }
        return args.optString("file_path", "");
    }

    private String normalizeToolPath(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.trim().replace("\\", "/");
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
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
