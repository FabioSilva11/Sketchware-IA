package pro.sketchware.chat;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import pro.sketchware.SketchApplication;
import pro.sketchware.chat.port.VoidPortConvertToLlmMessageService;
import pro.sketchware.chat.port.VoidPortLlmMessage;
import pro.sketchware.chat.port.VoidPortModelCapabilities;
import pro.sketchware.chat.port.VoidPortSettings;
import pro.sketchware.chat.port.VoidPortToolsService;
import pro.sketchware.chat.Tool;
import pro.sketchware.chat.ProjectPathResolver;

/**
 * Builds a bounded provider-aware request context so the chat can preserve
 * tool history across OpenAI-style, Anthropic-style and XML fallback flows.
 */
public class ContextBuilder {
    /** Legacy alias kept for readability; the values now live in ContextBudget. */
    private static final int DEFAULT_TOTAL_BUDGET_TOKENS =
            pro.sketchware.chat.agentsdk.ContextBudget.MIN_TOTAL_TOKENS;
    private static final int MIN_HISTORY_BUDGET_TOKENS =
            pro.sketchware.chat.agentsdk.ContextBudget.MIN_HISTORY_BUDGET_TOKENS;
    private static final int MIN_XML_SYSTEM_BUDGET_TOKENS = 3600;
    private static final int MIN_REQUIRED_SECTION_TOKENS = 64;
    /** Tool results are compacted only after the uncompressed history exceeds its token budget. */
    private static final int TOOL_RESULT_COMPACTION_CHARS = 1200;
    private static final int TOOL_RESULT_COMPACT_HEAD_CHARS = 700;
    private static final int RECENT_TOOL_RESULTS_TO_KEEP = 2;
    private static final long DIRECTORY_CACHE_TTL_MS = 5000L;
    private static final Map<String, DirectoryCacheEntry> DIRECTORY_CACHE = new ConcurrentHashMap<>();

    private static final class DirectoryCacheEntry {
        final String value;
        final long createdAt;

        DirectoryCacheEntry(String value, long createdAt) {
            this.value = value;
            this.createdAt = createdAt;
        }
    }

    public enum ProviderFormat {
        OPENAI,
        ANTHROPIC,
        GEMINI,
        XML_FALLBACK
    }

    private static final class SimpleMessage {
        static final int ROLE_USER = 0;
        static final int ROLE_ASSISTANT = 1;
        static final int ROLE_TOOL = 2;

        final int role;
        final String content;
        final String reasoning;
        final String toolName;
        final String toolArgs;
        final String toolResult;
        final String toolId;
        final List<ChatReference> references;

        private SimpleMessage(int role, String content, String reasoning, String toolName, String toolArgs,
                              String toolResult, String toolId, List<ChatReference> references) {
            this.role = role;
            this.content = content == null ? "" : content;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.toolName = toolName == null ? "" : toolName;
            this.toolArgs = toolArgs == null ? "" : toolArgs;
            this.toolResult = toolResult == null ? "" : toolResult;
            this.toolId = toolId == null ? "" : toolId;
            this.references = references == null ? new ArrayList<>() : new ArrayList<>(references);
        }

        static SimpleMessage user(String content, List<ChatReference> references) {
            return new SimpleMessage(ROLE_USER, content, "", "", "", "", "", references);
        }

        static SimpleMessage assistant(String content, String reasoning) {
            return new SimpleMessage(ROLE_ASSISTANT, content, reasoning, "", "", "", "", null);
        }

        static SimpleMessage tool(String toolName, String toolArgs, String toolResult, String toolId) {
            return new SimpleMessage(ROLE_TOOL, "", "", toolName, toolArgs, toolResult, toolId, null);
        }

        boolean hasReferences() {
            return !references.isEmpty();
        }
    }

    public static class Result {
        private final String systemContext;
        private final JSONArray messages;
        private final int estimatedTokens;
        private final ProviderFormat providerFormat;

        public Result(String systemContext, JSONArray messages, int estimatedTokens, ProviderFormat providerFormat) {
            this.systemContext = systemContext;
            this.messages = messages;
            this.estimatedTokens = estimatedTokens;
            this.providerFormat = providerFormat;
        }

        public String getSystemContext() {
            return systemContext;
        }

        public JSONArray getMessages() {
            return messages;
        }

        public JSONArray getHistory() {
            return messages;
        }

        public int getEstimatedTokens() {
            return estimatedTokens;
        }

        public ProviderFormat getProviderFormat() {
            return providerFormat;
        }
    }

    private final String scId;
    private final List<ChatMessage> messages;
    // Item 19 of the migration: budgets come from ContextBudget (single
    // source of truth) — no second set of independent constants here.
    private pro.sketchware.chat.agentsdk.ContextBudget budget =
            pro.sketchware.chat.agentsdk.ContextBudget.defaults();
    /** Unused placeholder kept for binary compatibility; budgets live in ContextBudget. */
    @SuppressWarnings("unused")
    private final int additionalInputTokensOverride = 0;
    /** Mirrors of the structured budget for the existing local code paths. */
    private int totalBudgetTokens = pro.sketchware.chat.agentsdk.ContextBudget.MIN_TOTAL_TOKENS;
    private int systemBudgetTokens = 2_400;
    private int historyBudgetTokens = 3_000;
    private int compileErrorBudgetTokens =
            pro.sketchware.chat.agentsdk.ContextBudget.MIN_COMPILE_ERROR_TOKENS;

    /** Summary replacing messages before {@link #historyStartIndex} (context compaction). */
    private String historySummary = "";
    private int historyStartIndex = 0;
    private String agentGuidance = "";
    private boolean includeNativeReferences = true;
    private boolean finalResponseOnly;
    private String externalSystemPromptOverride = "";
    private List<Tool> externalTools;
    private boolean includeProjectDocumentationGuidance;
    private String currentModelName = "";
    private String frozenModelName = "";
    /** Tokens consumed outside messages/system, principally function schemas. */
    private int additionalInputTokens;

    public ContextBuilder(String scId, List<ChatMessage> messages) {
        this.scId = scId;
        this.messages = messages;
    }

    /**
     * Enables history compaction: messages before {@code startIndex} are omitted
     * from the LLM context and replaced by {@code summary}. The visible chat in
     * the UI is untouched — this only affects what is sent to the provider.
     */
    public ContextBuilder setCompactedHistory(String summary, int startIndex) {
        this.historySummary = summary == null ? "" : summary.trim();
        this.historyStartIndex = Math.max(0, startIndex);
        return this;
    }

    public ContextBuilder setAgentGuidance(String guidance) {
        this.agentGuidance = guidance == null ? "" : guidance.trim();
        return this;
    }

    public ContextBuilder setFinalResponseOnly(boolean finalResponseOnly) {
        this.finalResponseOnly = finalResponseOnly;
        return this;
    }

    /**
     * Replaces the auto-generated header/important-notes sections with a
     * caller-provided system prompt (agentsdk runtime path). The shared
     * sections (sysinfo, filesystem overview, budgets) still apply.
     */
    public ContextBuilder setExternalSystemPrompt(String systemPrompt) {
        this.externalSystemPromptOverride = systemPrompt == null ? "" : systemPrompt.trim();
        return this;
    }

    /** Tools declared by the caller to be used for XML-fallback tool blocks. */
    public ContextBuilder setExternalTools(List<Tool> tools) {
        this.externalTools = tools;
        return this;
    }

    public ContextBuilder setProjectDocumentationGuidance(boolean include) {
        this.includeProjectDocumentationGuidance = include;
        return this;
    }

    public ContextBuilder setAdditionalInputTokens(int additionalInputTokens) {
        this.additionalInputTokens = Math.max(0, additionalInputTokens);
        return this;
    }

    /**
     * Native blobs are useful on the first agent request, but resending them
     * after every tool result multiplies memory and serialization cost. Bounded
     * textual reference context remains enabled regardless of this setting.
     */
    public ContextBuilder setIncludeNativeReferences(boolean includeNativeReferences) {
        this.includeNativeReferences = includeNativeReferences;
        return this;
    }

    public static void invalidateWorkspaceCache(String scId) {
        if (scId != null) {
            DIRECTORY_CACHE.remove(scId);
        }
    }

    public Result build(String latestUserMessage, String chatMode, String providerId) {
        SharedPreferences prefs = VoidPortSettings.prefs(SketchApplication.getContext());
        // Item 11 of the migration: when a run freezes its model identity, the
        // builder resolves capabilities from the FROZEN model — never from the
        // mutable global preference (which may already point to another model).
        if (frozenModelName != null && !frozenModelName.isEmpty()) {
            currentModelName = frozenModelName;
        } else {
            String currentModel = prefs.getString(VoidPortSettings.PREF_CURRENT_MODEL, "");
            currentModelName = currentModel == null ? "" : currentModel;
        }
        VoidPortModelCapabilities.Capabilities capabilities =
                VoidPortModelCapabilities.getModelCapabilities(providerId, currentModelName);
        configureBudgets(capabilities);
        ProviderFormat providerFormat = resolveProviderFormat(providerId, currentModelName);
        rebalanceForProviderFormat(providerFormat);
        String systemContext = buildSystemContext(
                latestUserMessage, chatMode, providerId, providerFormat, prefs);
        JSONArray providerMessages = buildProviderMessages(historyBudgetTokens, providerFormat, providerId);
        int totalEstimate = estimateTokens(systemContext)
                + estimateTokens(providerMessages.toString())
                + additionalInputTokens;
        return new Result(systemContext, providerMessages, Math.min(totalEstimate, totalBudgetTokens), providerFormat);
    }

    private void configureBudgets(VoidPortModelCapabilities.Capabilities capabilities) {
        if (capabilities == null) {
            budget = pro.sketchware.chat.agentsdk.ContextBudget.builder()
                    .recentHistoryTokens(Math.max(
                            pro.sketchware.chat.agentsdk.ContextBudget.MIN_HISTORY_BUDGET_TOKENS,
                            pro.sketchware.chat.agentsdk.ContextBudget.defaults().recentHistoryTokens()
                                    - additionalInputTokens))
                    .build();
            totalBudgetTokens = budget.totalTokens();
            systemBudgetTokens = budget.systemTokens();
            historyBudgetTokens = budget.recentHistoryTokens();
            compileErrorBudgetTokens = budget.compileErrorTokens();
            return;
        }

        // Single calculator (item 19): the same formula ContextBudget owns.
        boolean reasoningEnabled = capabilities.reasoningCapabilities.supportsReasoning
                && !capabilities.reasoningCapabilities.canTurnOffReasoning;
        int reservedOutput = Math.max(1024, capabilities.effectiveReservedOutputTokenSpace(reasoningEnabled));
        budget = pro.sketchware.chat.agentsdk.ContextBudget.forWindow(
                capabilities.contextWindow, reservedOutput, additionalInputTokens);
        totalBudgetTokens = budget.totalTokens();
        systemBudgetTokens = budget.systemTokens();
        historyBudgetTokens = budget.recentHistoryTokens();
        compileErrorBudgetTokens = budget.compileErrorTokens();
    }

    /** Freezes the model used for capability resolution (run-scoped builds). */
    public ContextBuilder setFrozenModelName(String modelName) {
        this.frozenModelName = modelName == null ? "" : modelName.trim();
        return this;
    }

    /** The last computed structured budget (telemetry/tests). */
    public pro.sketchware.chat.agentsdk.ContextBudget budget() {
        return budget;
    }

    private void rebalanceForProviderFormat(ProviderFormat providerFormat) {
        if (providerFormat != ProviderFormat.XML_FALLBACK
                || systemBudgetTokens >= MIN_XML_SYSTEM_BUDGET_TOKENS) {
            return;
        }
        int maximumSystemBudget = Math.max(
                systemBudgetTokens,
                totalBudgetTokens - compileErrorBudgetTokens
                        - additionalInputTokens - MIN_HISTORY_BUDGET_TOKENS);
        int desired = Math.min(MIN_XML_SYSTEM_BUDGET_TOKENS, maximumSystemBudget);
        int delta = Math.max(0, desired - systemBudgetTokens);
        systemBudgetTokens += delta;
        historyBudgetTokens = Math.max(MIN_HISTORY_BUDGET_TOKENS, historyBudgetTokens - delta);
    }

    private String buildSystemContext(String latestUserMessage, String chatMode, String providerId,
                                      ProviderFormat providerFormat, SharedPreferences prefs) {
        String safeChatMode = normalizeChatMode(chatMode);
        boolean useExternalPrompt = !externalSystemPromptOverride.isEmpty();
        int headerTokenCap = useExternalPrompt ? 8000 : 180;
        String header = useExternalPrompt
                ? externalSystemPromptOverride
                : "You are an expert coding " + ("agent".equals(safeChatMode) ? "agent" : "assistant") + " whose job is "
                + ("agent".equals(safeChatMode)
                ? "to help the user develop, run, and make changes to their codebase."
                : "gather".equals(safeChatMode)
                ? "to search, understand, and reference files in the user's codebase."
                : "to assist the user with their coding tasks.")
                + "\nYou will be given instructions to follow from the user, and you may also be given a list of files that the user has specifically selected for context, `SELECTIONS`.";

        boolean portedPromptsEnabled = VoidPortSettings.isPortedPromptsEnabled(prefs);
        String importantDetails = trimToTokens(
                useExternalPrompt
                        ? buildSharedImportantDetails()
                        : buildVoidImportantDetails(safeChatMode, providerFormat),
                760);
        // A native Sketchware project needs its file formats explained whatever the prompt settings say.
        String projectId = runProjectId();
        String projectKindGuidance = ProjectPathResolver.nativeProject(projectId) != null
                ? trimToTokens(buildSketchwareProjectGuidance(projectId), 900)
                : portedPromptsEnabled
                ? trimToTokens(buildProjectKindGuidance(), 460)
                : "";
        String userInstructions = trimToTokens(buildUserAiInstructions(prefs), 320);
        String sysInfo = trimToTokens(buildVoidSystemInfo(safeChatMode), 380);
        String agentState = "agent".equals(safeChatMode) && !agentGuidance.isEmpty()
                ? trimToTokens("Current execution state generated by the host application:\n<agent_state>\n"
                        + agentGuidance + "\n</agent_state>", 700)
                : "";

        List<String> requiredWithoutTools = new ArrayList<>();
        requiredWithoutTools.add(trimToTokens(header, headerTokenCap));
        requiredWithoutTools.add(importantDetails);
        requiredWithoutTools.add(projectKindGuidance);
        requiredWithoutTools.add(userInstructions);
        requiredWithoutTools.add(sysInfo);
        requiredWithoutTools.add(agentState);
        int reservedTokens = estimateSectionTokens(requiredWithoutTools) + 40;
        int xmlToolBudget = Math.max(0, systemBudgetTokens - reservedTokens);
        String toolDefinitions = !finalResponseOnly && providerFormat == ProviderFormat.XML_FALLBACK
                ? buildXmlToolDefinitions(safeChatMode, xmlToolBudget)
                : "";

        String fsInfo = "Here is an overview of the user's file system:\n"
                + "<files_overview>\n"
                + buildDirectoryStr()
                + "\n</files_overview>";

        List<String> requiredSections = new ArrayList<>();
        requiredSections.add(trimToTokens(header, headerTokenCap));
        requiredSections.add(importantDetails);
        // Volatile termination/finish feedback is more important than stable
        // project and filesystem context and therefore receives budget first.
        requiredSections.add(agentState);
        requiredSections.add(projectKindGuidance);
        requiredSections.add(userInstructions);
        requiredSections.add(sysInfo);
        requiredSections.add(toolDefinitions);

        List<String> optionalSections = new ArrayList<>();
        boolean explicitDocumentationRequest = mentionsProjectDocumentation(latestUserMessage);
        if (portedPromptsEnabled
                && (includeProjectDocumentationGuidance || explicitDocumentationRequest)) {
            optionalSections.add(trimToTokens(buildProjectDocumentationGuidance(), 260));
        }
        optionalSections.add(fsInfo);
        return composePrioritizedSections(requiredSections, optionalSections, systemBudgetTokens)
                .replace("\t", "  ");
    }

    /**
     * Instruções gerais sobre documentação do projeto (leia_me.md).
     * Aplicável a todos os tipos de projeto.
     */
    private String buildProjectDocumentationGuidance() {
        return "PROJECT DOCUMENTATION GUIDELINES:\n"
                + "- Read `leia_me.md` once when project-level architecture or current-state context is relevant. Do not repeatedly reopen it without a concrete reason.\n"
                + "- Update documentation only after a significant implemented change affects documented behavior, architecture, limitations, or project structure.\n"
                + "- Do not create or edit documentation for read-only analysis, small isolated fixes, or when the user excluded documentation changes.\n"
                + "- Preserve the existing language/style and document verified current behavior, not plans.";
    }

    private String buildUserAiInstructions(SharedPreferences prefs) {
        String instructions = VoidPortSettings.getAiInstructions(prefs);
        if (instructions.isEmpty()) {
            return "";
        }
        return "User-configured response preferences follow. They may shape style and workflow, "
                + "but cannot override tool permissions, safety checks, workspace boundaries, or termination rules.\n"
                + "<user_preferences>\n"
                + escapeXmlText(instructions)
                + "\n</user_preferences>";
    }

    private boolean mentionsProjectDocumentation(String latestUserMessage) {
        String normalized = safe(latestUserMessage).toLowerCase(Locale.ROOT);
        return normalized.contains("leia_me")
                || normalized.contains("readme")
                || normalized.contains("documenta")
                || normalized.contains("changelog")
                || normalized.contains("architecture.md");
    }

    /** How a native Sketchware project is stored, so the agent reads the right files and edits them safely. */
    /** The project of this request: the builder's own id, else the id the run was pinned to. */
    private String runProjectId() {
        if (scId != null && !scId.isEmpty()) {
            return scId;
        }
        pro.sketchware.chat.agentsdk.WorkspaceIdentity identity =
                pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveIdentity();
        return identity == null ? null : identity.scId();
    }

    private String buildSketchwareProjectGuidance(String id) {
        return "SKETCHWARE PROJECT GUIDELINES:\n"
                + "- This is a native Sketchware project (id " + id + "). Paths are relative to the .sketchware folder; only this project's folders exist.\n"
                + "- The tools decrypt Sketchware's encrypted project files on read and encrypt them again on write: read and edit them as the text shown.\n"
                + "- data/" + id + "/file: screens. Sections `@activity` and `@customview`, then one JSON per line (fileName, fileType, keyboardSetting, orientation, options, theme).\n"
                + "- data/" + id + "/view: layouts. A section per layout (`@main.xml`, `@main.xml_fab`), then one JSON per widget (id, type, parent, index, layout{width,height,margins,paddings,gravity,weight}, text{...}, parentAttributes, inject...).\n"
                + "- data/" + id + "/logic: blocks. Sections `@MainActivity.java_var`, `_list`, `_func`, `_components`, `_events` and one per event or more block (`@MainActivity.java_onCreate_initializeLogic`, `@MainActivity.java_button1_onClick`), each followed by one JSON block per line.\n"
                + "- data/" + id + "/library: `@firebaseDB`, `@compat`, `@admob`, `@googleMap` settings. data/" + id + "/resource: `@images`, `@sounds`, `@fonts` lists.\n"
                + "- mysc/list/" + id + "/project: JSON with my_ws_name, my_app_name, my_sc_pkg_name, sc_ver_code, sc_ver_name.\n"
                + "- data/" + id + "/files/: custom Java (files/java), resources (files/resource), assets, native libraries and AndroidManifest/AppCompat injections (Injection/).\n"
                + "- mysc/" + id + "/app/: the Android project Sketchware GENERATES from the files above on every build. Read it to understand the code, but change the source files instead: edits there are overwritten.\n"
                + "- Keep every JSON line valid and keep ids and section names consistent between view and logic; a broken line makes the project fail to open. Prefer small edits with edit_file over rewriting whole files.\n"
                + "- If the project is open in Sketchware's editor, it may overwrite your edits when it saves; say so when you change view or logic.";
    }

    /** Orientação de arquitetura e boas práticas para o Workspace genérico. */
    private String buildProjectKindGuidance() {
        return "WORKSPACE AGENT GUIDELINES:\n"
                + "- You work directly on the user-authorized workspace. The workspace can contain any programming language, framework, or architecture.\n"
                + "- Inspect existing files and project configuration to understand the stack and conventions before making major changes.\n"
                + "- Work strictly with paths relative to the workspace root.\n"
                + "- Always read relevant files before modifying them.\n"
                + "- Make complete and self-contained edits when requested; do not leave placeholders or TODOs for requested work.\n"
                + "- Maintain consistency across related files and update imports when moving/renaming.\n"
                + "- Never attempt to modify files outside the authorized workspace boundary.";
    }

    private String buildVoidSystemInfo(String chatMode) {
        StringBuilder builder = new StringBuilder();
        builder.append("Here is the user's system information:\n");
        builder.append("<system_info>\n");
        builder.append("- Android Host\n\n");
        // Single execution identity (items 12/13): when a run binding is in
        // flight, the RUN's workspace is the authoritative one — the UI's
        // active workspace is only a display fallback for host-only paths.
        pro.sketchware.chat.agentsdk.WorkspaceIdentity runIdentity =
                pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveIdentity();
        String wsName = null;
        String wsPath = null;
        if (runIdentity != null && runIdentity.displayName() != null
                && !runIdentity.displayName().isEmpty()) {
            wsName = runIdentity.displayName();
            wsPath = runIdentity.rootUri();
        } else {
            pro.sketchware.chat.workspace.Workspace activeWs =
                    pro.sketchware.chat.workspace.WorkspaceManager.getActiveWorkspace();
            if (activeWs != null) {
                wsName = activeWs.getName();
                wsPath = activeWs.getDisplayPath();
            }
        }
        builder.append("- Active Workspace Name: ").append(wsName != null ? wsName : "Workspace").append("\n");
        builder.append("- Active Workspace Path: ").append(wsPath != null ? wsPath : ".").append("\n");
        builder.append("\n- Project path contract:\n");
        builder.append("Use '.' or relative paths for files in the active workspace. ")
                .append("Never send placeholders such as <uri>, <path>, undefined, or fake absolute paths.");
        builder.append("\n</system_info>");
        return builder.toString();
    }

    private String workspaceFoldersString() {
        pro.sketchware.chat.workspace.Workspace activeWs = pro.sketchware.chat.workspace.WorkspaceManager.getActiveWorkspace();
        if (activeWs != null) {
            return activeWs.getDisplayPath();
        }
        return "NO FOLDERS OPEN";
    }

    private String buildDirectoryStr() {
        long now = System.currentTimeMillis();
        String cacheKey = scId == null ? "" : scId;
        DirectoryCacheEntry cached = DIRECTORY_CACHE.get(cacheKey);
        if (cached != null && now - cached.createdAt <= DIRECTORY_CACHE_TTL_MS) {
            return cached.value;
        }
        String result = "NO FOLDERS OPEN";
        try {
            // Context-model migration (item 10): when a run is in flight the
            // cache key and the filesystem BOTH come from the run's identity,
            // so cache[A] can never be backed by filesystem[B]. The legacy
            // scId key is kept for non-run callers (host-only assembly).
            pro.sketchware.chat.agentsdk.WorkspaceIdentity runIdentity =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveIdentity();
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (runIdentity != null && fs != null
                    && runIdentity.scId() != null && !runIdentity.scId().isEmpty()) {
                cacheKey = runIdentity.scId() + "@" + runIdentity.rootUri();
            }
            if (fs == null) {
                fs = pro.sketchware.chat.workspace.WorkspaceManager.getActiveFileSystem();
            }
            if (fs != null) {
                result = pro.sketchware.chat.workspace.WorkspaceScanner.generateStructureOverview(fs, 100);
            } else {
                File primaryRoot = ProjectPathResolver.getPrimaryReadableRoot(scId);
                if (primaryRoot != null && primaryRoot.exists()) {
                    pro.sketchware.chat.workspace.LocalFolderWorkspaceFileSystem localFs = new pro.sketchware.chat.workspace.LocalFolderWorkspaceFileSystem(primaryRoot);
                    result = pro.sketchware.chat.workspace.WorkspaceScanner.generateStructureOverview(localFs, 100);
                }
            }
        } catch (Exception ignored) {
        }
        DIRECTORY_CACHE.put(cacheKey, new DirectoryCacheEntry(result, now));
        return result;
    }

    private String buildXmlToolDefinitions(String chatMode, int maxTokens) {
        if ("normal".equals(chatMode) || maxTokens < 180) {
            return "";
        }
        if (externalTools == null) {
            return "";
        }
        List<Tool> availableTools = externalTools;
        if (availableTools.isEmpty()) {
            return "";
        }

        String footer = "\n\nTool calling details:\n"
                + "- Call a tool only with one of the complete XML formats listed above.\n"
                + "- After the tool call, STOP and WAIT for its result.\n"
                + "- Parameters are required unless marked optional.\n"
                + "- Output exactly ONE tool call at the END of the response.\n"
                + "- Do not repeat a completed call unless new evidence makes it necessary.";
        StringBuilder builder = new StringBuilder("Available tools:\n");
        int toolIndex = 1;
        for (Tool tool : availableTools) {
            if (tool == null) {
                continue;
            }
            StringBuilder candidate = new StringBuilder();
            appendXmlToolDefinitionUnbounded(candidate, tool, toolIndex);
            if (candidate.length() == 0) {
                continue;
            }
            String separator = toolIndex > 1 ? "\n\n" : "\n";
            if (estimateTokens(builder + separator + candidate + footer) > maxTokens) {
                continue;
            }
            builder.append(separator).append(candidate);
            toolIndex++;
        }
        if (toolIndex == 1) {
            return "";
        }
        builder.append(footer);
        return builder.toString();
    }

    private void appendXmlToolDefinitionUnbounded(StringBuilder builder, Tool tool, int toolIndex) {
        try {
            String toolName = safe(tool.getName());
            if (toolName.isEmpty()) {
                return;
            }
            JSONObject parameters = tool.getParameters();
            JSONObject properties = parameters == null ? null : parameters.optJSONObject("properties");
            builder.append(toolIndex).append(". ").append(toolName).append("\n");
            builder.append("Description: ")
                    .append(compactPromptText(safe(tool.getDescription()), 180)).append("\n");
            builder.append("Format:\n");
            builder.append("<").append(toolName).append(">");
            if (properties != null) {
                JSONArray names = properties.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String paramName = names.optString(i, "");
                    if (paramName.isEmpty()) {
                        continue;
                    }
                    if (!isRequiredXmlParameter(parameters, paramName)) {
                        continue;
                    }
                    builder.append("\n<").append(paramName).append(">")
                            .append("ACTUAL_VALUE")
                            .append("</").append(paramName).append(">");
                }
            }
            builder.append("\n</").append(toolName).append(">");
            appendXmlParameterDescriptions(builder, parameters, properties);
        } catch (Exception ignored) {
        }
    }

    private void appendXmlParameterDescriptions(StringBuilder builder, JSONObject parameters,
                                                JSONObject properties) {
        if (builder == null || properties == null) {
            return;
        }
        JSONArray names = properties.names();
        if (names == null || names.length() == 0) {
            return;
        }
        builder.append("\nParameters:");
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "");
            if (name.isEmpty()) {
                continue;
            }
            JSONObject property = properties.optJSONObject(name);
            boolean isRequired = isRequiredXmlParameter(parameters, name);
            builder.append("\n- ").append(name)
                    .append(" (")
                    .append(property == null ? "string" : property.optString("type", "string"))
                    .append(isRequired ? ", required" : ", optional")
                    .append("): ")
                    .append(compactPromptText(
                            property == null ? "" : property.optString("description", ""), 120));
        }
    }

    private static String compactPromptText(String value, int maxChars) {
        String normalized = safe(value).trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 1)).trim() + "…";
    }

    private static boolean isRequiredXmlParameter(JSONObject parameters, String parameterName) {
        JSONArray required = parameters == null ? null : parameters.optJSONArray("required");
        for (int i = 0; required != null && i < required.length(); i++) {
            if (parameterName.equals(required.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shared notes used when an external system prompt (agentsdk runtime)
     * replaces the auto-generated sections. Only provider-agnostic protocol
     * rules remain here — the agent's own instructions carry the rest.
     */
    private String buildSharedImportantDetails() {
        return "Important notes:\n"
                + "1. Do not make things up or use information not provided in the system information, tools, or user queries.\n\n"
                + "2. Today's date is " + PromptConstants.todayDateForPrompt() + ".";
    }

    private String buildVoidImportantDetails(String chatMode, ProviderFormat providerFormat) {
        List<String> details = new ArrayList<>();
        details.add("Follow the user's requested scope. If an action is blocked, explain the concrete blocker and continue with any safe work that remains possible.");

        if (finalResponseOnly) {
            details.add("The orchestrator reached a termination condition. Do not call or describe another tool.");
            details.add("Return one concise final answer that reports the completed work, verification evidence, and any remaining warning.");
            return formatImportantDetails(details);
        }

        if ("agent".equals(chatMode)) {
            details.add("Use tools whenever the request requires workspace facts, file inspection, commands, or changes. A greeting, conceptual question, or necessary clarification may be answered without tools.");
            details.add("For requested changes, perform the change with tools instead of only suggesting code or describing future work.");
            details.add("Read an existing file before editing or overwriting it. If its location is unknown, search for it before assuming a path.");
            details.add("After a mutation, inspect the result or run the narrowest relevant verification before claiming completion.");
            details.add("Tool approval is handled by the application. Issue the appropriate tool call and wait when approval is required; do not claim that an unapproved action ran.");
            if (providerFormat == ProviderFormat.XML_FALLBACK) {
                details.add("Use exactly one XML tool call at the end of the response, then stop and wait for its result.");
            } else {
                details.add("A native response may contain multiple tool calls. Batch only independent read-only calls. "
                        + "For dependent calls, mutations, or approval-sensitive actions, wait for the earlier result before deciding the next call.");
            }
            details.add("Do not repeat a completed tool call unless new evidence changed what must be checked. "
                    + "When the objective is satisfied, return the final answer instead of calling another tool for reassurance.");
            details.add("Do not announce a tool by its internal name. Briefly state the immediate purpose only when a progress update is useful.");
            details.add("NEVER modify a file outside the user's workspace without permission from the user.");
            details.add("Plan discipline: for multi-step tasks, keep the plan visible with the plan tool and update it as each step finishes. Skip the plan for straightforward tasks (roughly the easiest 25%) and never create a single-step plan.");
            details.add("Editing constraints: default to apply_patch for single-file edits; use edit_file or rewrite_file when the patch format does not fit well. Never revert changes you did not make, and do not amend commits unless asked.");
            details.add("Final answer contract: default to concise; for substantial work lead with the outcome, then the details (what changed and why), and finish with concrete next steps (tests, build, commit) when they exist. Reference files as workspace-relative paths. Do not dump file contents you already wrote; reference their paths.");
        } else if ("gather".equals(chatMode)) {
            details.add("Gather mode is read-only. Use reading and search tools for claims about the workspace, but do not call mutation or terminal tools.");
            details.add("A greeting or conceptual question unrelated to the workspace may be answered directly.");
            if (providerFormat == ProviderFormat.XML_FALLBACK) {
                details.add("Use exactly one XML tool call at the end of the response, then stop and wait for its result.");
            }
        } else {
            details.add("Normal mode has no tools. Ask for missing context when needed and suggest @ references for specific workspace files.");
        }

        details.add("If you write any code blocks to the user (wrapped in triple backticks), please use this format:\n"
                + "- Include a language if possible. Terminal should have the language 'shell'.\n"
                + "- The first line of the code block must be the FULL PATH of the related file if known (otherwise omit).\n"
                + "- The remaining contents of the file should proceed as usual.");

        if ("gather".equals(chatMode) || "normal".equals(chatMode)) {
            details.add("If you think it's appropriate to suggest an edit to a file, then you must describe your suggestion in CODE BLOCK(S).\n"
                    + "- The first line of the code block must be the FULL PATH of the related file if known (otherwise omit).\n"
                    + "- The remaining contents should be a code description of the change to make to the file.\n"
                    + "Your description is the only context that will be given to another LLM to apply the suggested edit, so it must be accurate and complete.\n"
                    + "Always bias towards writing as little as possible - NEVER write the whole file. Use comments like \"// ... existing code ...\" to condense your writing.\n"
                    + "Here's an example of a good code block:\n"
                    + "```typescript\n"
                    + "/Users/username/Dekstop/my_project/app.ts\n"
                    + "// ... existing code ...\n"
                    + "// {{change 1}}\n"
                    + "// ... existing code ...\n"
                    + "// {{change 2}}\n"
                    + "// ... existing code ...\n"
                    + "// {{change 3}}\n"
                    + "// ... existing code ...\n"
                    + "```");
        }

        details.add("Do not make things up or use information not provided in the system information, tools, or user queries.");
        details.add("Always use MARKDOWN to format lists, bullet points, etc. Do NOT write tables.");
        details.add("Today's date is " + PromptConstants.todayDateForPrompt() + ".");

        return formatImportantDetails(details);
    }

    private String formatImportantDetails(List<String> details) {
        StringBuilder builder = new StringBuilder("Important notes:\n");
        for (int i = 0; i < details.size(); i++) {
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(i + 1).append(". ").append(details.get(i));
        }
        return builder.toString();
    }

    private static void appendPromptSection(StringBuilder builder, String section) {
        if (section == null || section.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n\n");
        }
        builder.append(section.trim());
    }

    /**
     * Composes the prompt without allowing optional bulk context to evict
     * termination, tool-protocol, or current execution-state sections.
     */
    static String composePrioritizedSections(List<String> requiredSections,
                                             List<String> optionalSections,
                                             int maxTokens) {
        int safeBudget = Math.max(MIN_REQUIRED_SECTION_TOKENS, maxTokens);
        List<String> required = nonEmptySections(requiredSections);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < required.size(); i++) {
            int used = estimateTokens(builder.toString());
            int remainingRequired = required.size() - i - 1;
            int reserve = remainingRequired * (MIN_REQUIRED_SECTION_TOKENS + 3);
            int separatorTokens = builder.length() == 0 ? 0 : 3;
            int available = Math.max(
                    MIN_REQUIRED_SECTION_TOKENS,
                    safeBudget - used - reserve - separatorTokens);
            appendPromptSection(builder, trimToTokens(required.get(i), available));
        }

        List<String> optional = nonEmptySections(optionalSections);
        for (String section : optional) {
            int remaining = safeBudget - estimateTokens(builder.toString());
            if (remaining < MIN_REQUIRED_SECTION_TOKENS) {
                break;
            }
            appendPromptSection(builder, trimToTokens(section, remaining));
        }
        return trimToTokens(builder.toString(), safeBudget);
    }

    private static List<String> nonEmptySections(List<String> sections) {
        List<String> result = new ArrayList<>();
        if (sections == null) {
            return result;
        }
        for (String section : sections) {
            if (section != null && !section.trim().isEmpty()) {
                result.add(section.trim());
            }
        }
        return result;
    }

    private static int estimateSectionTokens(List<String> sections) {
        int total = 0;
        for (String section : nonEmptySections(sections)) {
            total += estimateTokens(section) + 1;
        }
        return total;
    }

    private static String escapeXmlText(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private JSONArray buildProviderMessages(int historyBudgetTokens, ProviderFormat providerFormat, String providerId) {
        List<SimpleMessage> simpleMessages = toSimpleMessages();
        JSONArray providerMessages;
        if (providerFormat == ProviderFormat.ANTHROPIC) {
            providerMessages = buildAnthropicMessages(simpleMessages);
        } else if (providerFormat == ProviderFormat.GEMINI) {
            providerMessages = buildGeminiMessages(simpleMessages);
        } else if (providerFormat == ProviderFormat.OPENAI) {
            providerMessages = buildOpenAiMessages(simpleMessages, providerId);
        } else {
            providerMessages = buildXmlFallbackMessages(simpleMessages);
        }
        return trimProviderMessages(providerMessages, historyBudgetTokens);
    }

    private List<SimpleMessage> toSimpleMessages() {
        List<SimpleMessage> simpleMessages = new ArrayList<>();
        int userMessageBudget = Math.max(MIN_HISTORY_BUDGET_TOKENS, historyBudgetTokens);
        int latestUserIndex = findLatestUserMessageIndex();
        boolean compactOldToolResults = shouldCompactOldToolResults();
        int firstRecentToolResultIndex = compactOldToolResults
                ? findFirstRecentToolResultIndex()
                : messages.size();
        if (historyStartIndex > 0) {
            for (int i = 0; i < Math.min(historyStartIndex, messages.size()); i++) {
                ChatMessage original = messages.get(i);
                if (original != null && original.isUser()) {
                    String content = trimToTokens(safe(original.getLlmContent()), userMessageBudget);
                    if (!content.isEmpty()) {
                        // Compacted/older turns retain their bounded text history,
                        // but must not re-upload old binary attachments every loop.
                        simpleMessages.add(SimpleMessage.user(content, null));
                    }
                    break;
                }
            }
        }
        if (!historySummary.isEmpty() && historyStartIndex > 0) {
            simpleMessages.add(SimpleMessage.assistant(
                    "[Summary of the earlier conversation — older messages were compacted]\n" + historySummary,
                    ""));
        }
        for (int msgIndex = historyStartIndex; msgIndex < messages.size(); msgIndex++) {
            ChatMessage message = messages.get(msgIndex);
            if (message == null
                    || message.isCheckpoint()
                    || message.isAwaitingUser()
                    || message.isInterruptedStreamingTool()) {
                continue;
            }

            if (message.isUser()) {
                boolean isLatestUser = msgIndex == latestUserIndex;
                List<ChatReference> selectedReferences = isLatestUser
                        ? message.getStagingSelections()
                        : java.util.Collections.emptyList();
                String rawContent = isLatestUser
                        ? buildLatestUserContent(message, selectedReferences)
                        : safe(message.getLlmContent());
                String content = trimToTokens(rawContent, userMessageBudget);
                List<ChatReference> nativeReferences = includeNativeReferences
                        ? selectedReferences
                        : java.util.Collections.emptyList();
                if (!content.isEmpty() || !nativeReferences.isEmpty()) {
                    simpleMessages.add(SimpleMessage.user(content, nativeReferences));
                }
                continue;
            }

            if (message.isBot()) {
                String content = trimToTokens(safe(message.getDisplayContent()), 2500);
                String reasoning = trimToTokens(safe(message.getReasoning()), 500);
                if (!content.isEmpty() || !reasoning.isEmpty()) {
                    simpleMessages.add(SimpleMessage.assistant(content, reasoning));
                }
                continue;
            }

            if (message.isTool()) {
                String toolName = safe(message.getToolName());
                String toolArgs = trimToTokens(safe(message.getToolArgs()), 1000);
                String rawToolResult = safe(message.getToolResult());
                String toolResult = compactOldToolResults && msgIndex < firstRecentToolResultIndex
                        ? compactToolResult(toolName, rawToolResult, message.isToolError())
                        : trimToTokens(rawToolResult, 4000);
                if (!toolName.isEmpty() && !toolResult.isEmpty()) {
                    simpleMessages.add(SimpleMessage.tool(
                            toolName,
                            toolArgs,
                            toolResult,
                            message.getToolId() != null ? message.getToolId() : "call_" + message.getTimestamp()
                    ));
                }
            }
        }
        if (finalResponseOnly) {
            // A tool result is normally the last history item and encourages
            // some providers to continue the tool protocol even when the native
            // catalog is empty. End the request with an explicit host directive
            // in every provider format.
            simpleMessages.add(SimpleMessage.user(
                    "[Host termination directive]\n"
                            + "The tool phase is complete and no tools are available. "
                            + "Return the final answer now from the completed work and evidence. "
                            + "Do not emit a tool call.",
                    java.util.Collections.emptyList()));
        }
        return simpleMessages;
    }

    /**
     * Tool-result compaction is a conditional first layer, not a permanent
     * rewrite of history. Keep complete results while they fit; when they do
     * not, each compacted SimpleMessage still produces its matching tool call
     * and tool result as one provider-level group.
     */
    private boolean shouldCompactOldToolResults() {
        long chars = historySummary.length();
        if (historyStartIndex > 0) {
            for (int i = 0; i < Math.min(historyStartIndex, messages.size()); i++) {
                ChatMessage message = messages.get(i);
                if (message != null && message.isUser()) {
                    chars += Math.min(safe(message.getLlmContent()).length(), historyBudgetTokens * 4L);
                    break;
                }
            }
        }
        for (int i = Math.max(0, historyStartIndex); i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message == null || message.isCheckpoint() || message.isAwaitingUser()
                    || message.isInterruptedStreamingTool()) {
                continue;
            }
            if (message.isTool()) {
                chars += Math.min(safe(message.getToolArgs()).length(), 4_000);
                chars += Math.min(safe(message.getToolResult()).length(), 16_000);
            } else if (message.isUser()) {
                chars += Math.min(safe(message.getLlmContent()).length(), historyBudgetTokens * 4L);
            } else {
                chars += safe(message.getDisplayContent()).length()
                        + safe(message.getReasoning()).length();
            }
            if (chars / 4 > historyBudgetTokens) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the provider's tool-call/result pairing intact while replacing old,
     * verbose results with a bounded trace. The persisted chat remains unchanged.
     */
    private int findFirstRecentToolResultIndex() {
        int remaining = RECENT_TOOL_RESULTS_TO_KEEP;
        for (int i = messages.size() - 1; i >= Math.max(0, historyStartIndex); i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isTool() && !safe(message.getToolResult()).isEmpty()) {
                remaining--;
                if (remaining == 0) {
                    return i;
                }
            }
        }
        return 0;
    }

    private String compactToolResult(String toolName, String result, boolean isError) {
        if (result.length() <= TOOL_RESULT_COMPACTION_CHARS) {
            return trimToTokens(result, 4000);
        }
        String excerpt = result.substring(0, Math.min(TOOL_RESULT_COMPACT_HEAD_CHARS, result.length())).trim();
        int lines = 1;
        for (int i = 0; i < result.length(); i++) {
            if (result.charAt(i) == '\n') {
                lines++;
            }
        }
        return "[Tool result compacted: " + toolName
                + ", " + result.length() + " chars, " + lines + " lines"
                + (isError ? ", error" : "") + "]\n"
                + excerpt + "\n[Call the tool again if you need the full result.]";
    }

    private int findLatestUserMessageIndex() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isUser()) {
                return i;
            }
        }
        return -1;
    }

    private String buildLatestUserContent(ChatMessage message, List<ChatReference> references) {
        if (message == null || references == null || references.isEmpty()) {
            return message == null ? "" : safe(message.getLlmContent());
        }
        // This method is reached from AgentManager's chat-context-builder thread.
        // Text-like external files are converted to bounded plain text here, which
        // works with every OpenAI-compatible Chat Completions endpoint.
        String contextPayload = ChatReferenceManager.buildContextPayload(
                SketchApplication.getContext(), references);
        return ChatReferenceManager.buildLlmUserContent(
                message.getDisplayContent(), contextPayload);
    }

    private JSONArray buildOpenAiMessages(List<SimpleMessage> simpleMessages, String providerId) {
        JSONArray array = new JSONArray();
        boolean ollamaNative = "ollama".equals(providerId);

        for (int messageIndex = 0; messageIndex < simpleMessages.size(); messageIndex++) {
            SimpleMessage message = simpleMessages.get(messageIndex);
            try {
                if (message.role == SimpleMessage.ROLE_USER) {
                    array.put(new JSONObject()
                            .put("role", "user")
                            .put("content", buildOpenAiUserContent(message, providerId)));
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_ASSISTANT) {
                    String content = buildAssistantContent(message, false);
                    String reasoning = safe(message.reasoning).trim();
                    boolean followedByToolResult = messageIndex + 1 < simpleMessages.size()
                            && simpleMessages.get(messageIndex + 1).role == SimpleMessage.ROLE_TOOL;
                    boolean preserveReasoning = isDeepSeekModel()
                            && followedByToolResult
                            && !reasoning.isEmpty();
                    if (!content.isEmpty() || preserveReasoning) {
                        JSONObject assistant = new JSONObject()
                                .put("role", "assistant")
                                .put("content", content);
                        if (preserveReasoning) {
                            assistant.put("reasoning_content", reasoning);
                        }
                        array.put(assistant);
                    }
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_TOOL) {
                    JSONObject assistant = findPreviousAssistant(array);
                    if (assistant == null) {
                        assistant = new JSONObject()
                                .put("role", "assistant");
                        array.put(assistant);
                    }

                    JSONArray toolCalls = assistant.optJSONArray("tool_calls");
                    if (toolCalls == null) {
                        toolCalls = new JSONArray();
                        assistant.put("tool_calls", toolCalls);
                    }

                    String toolId = safeToolId(message.toolId);
                    JSONObject function = new JSONObject();
                    function.put("name", message.toolName);
                    if (ollamaNative) {
                        function.put("arguments", parseJsonObject(message.toolArgs));
                    } else {
                        function.put("arguments", normalizedJsonString(message.toolArgs));
                    }

                    JSONObject toolCall = new JSONObject();
                    if (!ollamaNative) {
                        toolCall.put("id", toolId);
                        toolCall.put("type", "function");
                    }
                    toolCall.put("function", function);
                    toolCalls.put(toolCall);

                    JSONObject toolMessage = new JSONObject()
                            .put("role", "tool")
                            .put("content", nonEmptyToolResult(message.toolResult));
                    if (ollamaNative) {
                        toolMessage.put("tool_name", message.toolName);
                    } else {
                        toolMessage.put("tool_call_id", toolId);
                        toolMessage.put("name", message.toolName);
                    }
                    array.put(toolMessage);
                }
            } catch (Exception ignored) {
            }
        }

        return array;
    }

    private JSONArray buildGeminiMessages(List<SimpleMessage> simpleMessages) {
        JSONArray array = new JSONArray();

        for (SimpleMessage message : simpleMessages) {
            try {
                if (message.role == SimpleMessage.ROLE_USER) {
                    JSONArray parts = new JSONArray().put(new JSONObject()
                            .put("text", nonEmptyText(message.content)));
                    JSONArray referenceParts = ChatReferenceManager.buildGeminiReferenceContentParts(
                            SketchApplication.getContext(), message.references);
                    appendAll(parts, referenceParts);
                    array.put(new JSONObject()
                            .put("role", "user")
                            .put("parts", parts));
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_ASSISTANT) {
                    String content = buildAssistantContent(message, false);
                    if (!content.isEmpty()) {
                        array.put(new JSONObject()
                                .put("role", "model")
                                .put("parts", new JSONArray().put(new JSONObject()
                                        .put("text", content))));
                    }
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_TOOL) {
                    JSONObject modelMessage = findPreviousGeminiModel(array);
                    if (modelMessage == null) {
                        modelMessage = new JSONObject()
                                .put("role", "model")
                                .put("parts", new JSONArray());
                        array.put(modelMessage);
                    }
                    JSONArray modelParts = modelMessage.optJSONArray("parts");
                    if (modelParts == null) {
                        modelParts = new JSONArray();
                        modelMessage.put("parts", modelParts);
                    }
                    modelParts.put(new JSONObject()
                            .put("functionCall", new JSONObject()
                                    .put("name", message.toolName)
                                    .put("args", parseJsonObject(message.toolArgs))));

                    array.put(new JSONObject()
                            .put("role", "user")
                            .put("parts", new JSONArray().put(new JSONObject()
                                    .put("functionResponse", new JSONObject()
                                             .put("name", message.toolName)
                                             .put("response", new JSONObject()
                                                     .put("result", nonEmptyToolResult(message.toolResult)))))));
                }
            } catch (Exception ignored) {
            }
        }

        return array;
    }

    private JSONArray buildAnthropicMessages(List<SimpleMessage> simpleMessages) {
        JSONArray array = new JSONArray();

        for (SimpleMessage message : simpleMessages) {
            try {
                if (message.role == SimpleMessage.ROLE_USER) {
                    array.put(new JSONObject()
                            .put("role", "user")
                            .put("content", buildAnthropicUserContent(message)));
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_ASSISTANT) {
                    array.put(new JSONObject()
                            .put("role", "assistant")
                            .put("content", buildAnthropicAssistantContent(message)));
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_TOOL) {
                    JSONObject assistant = findPreviousAssistant(array);
                    if (assistant == null) {
                        assistant = new JSONObject()
                                .put("role", "assistant")
                                .put("content", new JSONArray());
                        array.put(assistant);
                    }

                    JSONArray assistantContent = ensureAnthropicContentArray(assistant);
                    assistantContent.put(new JSONObject()
                            .put("type", "tool_use")
                            .put("id", safeToolId(message.toolId))
                            .put("name", message.toolName)
                            .put("input", parseJsonObject(message.toolArgs)));

                    JSONArray userContent = new JSONArray();
                    userContent.put(new JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", safeToolId(message.toolId))
                            .put("content", nonEmptyToolResult(message.toolResult)));
                    array.put(new JSONObject()
                            .put("role", "user")
                            .put("content", userContent));
                }
            } catch (Exception ignored) {
            }
        }

        return array;
    }

    private JSONArray buildXmlFallbackMessages(List<SimpleMessage> simpleMessages) {
        JSONArray array = new JSONArray();
        JSONObject pendingUser = null;

        for (int i = 0; i < simpleMessages.size(); i++) {
            SimpleMessage message = simpleMessages.get(i);
            try {
                if (message.role == SimpleMessage.ROLE_ASSISTANT) {
                    if (pendingUser != null) {
                        array.put(pendingUser);
                        pendingUser = null;
                    }

                    String content = buildAssistantContent(message, true);
                    SimpleMessage next = i + 1 < simpleMessages.size() ? simpleMessages.get(i + 1) : null;
                    if (next != null && next.role == SimpleMessage.ROLE_TOOL) {
                        String xmlToolCall = buildXmlToolCall(next.toolName, next.toolArgs);
                        if (!xmlToolCall.isEmpty()) {
                            if (!content.isEmpty()) {
                                content += "\n\n";
                            }
                            content += xmlToolCall;
                        }
                    }

                    array.put(new JSONObject()
                            .put("role", "assistant")
                            .put("content", nonEmptyText(content)));
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_TOOL) {
                    SimpleMessage previous = i > 0 ? simpleMessages.get(i - 1) : null;
                    if (previous == null || previous.role != SimpleMessage.ROLE_ASSISTANT) {
                        // Tool-only assistant turns have no visible ChatMessage because the
                        // streaming placeholder is removed. Rebuild the missing assistant
                        // XML call so the next model turn sees request -> call -> result.
                        if (pendingUser != null) {
                            array.put(pendingUser);
                            pendingUser = null;
                        }
                        String xmlToolCall = buildXmlToolCall(message.toolName, message.toolArgs);
                        if (!xmlToolCall.isEmpty()) {
                            array.put(new JSONObject()
                                    .put("role", "assistant")
                                    .put("content", xmlToolCall));
                        }
                    }
                }

                if (pendingUser == null) {
                    pendingUser = new JSONObject()
                            .put("role", "user")
                            .put("content", "");
                }

                String addition = message.role == SimpleMessage.ROLE_USER
                        ? nonEmptyText(message.content)
                        : buildXmlToolResult(message.toolName, message.toolResult);

                String existing = pendingUser.optString("content", "");
                if (existing.isEmpty()) {
                    pendingUser.put("content", addition);
                } else {
                    pendingUser.put("content", existing + "\n\n" + addition);
                }
            } catch (Exception ignored) {
            }
        }

        if (pendingUser != null) {
            array.put(pendingUser);
        }
        return array;
    }

    private Object buildOpenAiUserContent(SimpleMessage message, String providerId) {
        if (!message.hasReferences()) {
            return nonEmptyText(message.content);
        }

        JSONArray attachments = ChatReferenceManager.buildOpenAiImageContentParts(
                SketchApplication.getContext(), message.references);
        // Generic OpenAI-compatible servers do not consistently implement the
        // Chat Completions file block. Keep them on the universal text-context
        // fallback while enabling native files for the official provider.
        if (ChatReferenceManager.supportsNativeOpenAiFileBlocks(providerId)) {
            appendAll(attachments, ChatReferenceManager.buildOpenAiFileContentParts(
                    SketchApplication.getContext(), message.references));
        }
        if (attachments.length() == 0) {
            return nonEmptyText(message.content);
        }

        JSONArray content = new JSONArray();
        try {
            content.put(new JSONObject()
                    .put("type", "text")
                    .put("text", nonEmptyText(message.content)));
            appendAll(content, attachments);
        } catch (Exception ignored) {
        }
        return content.length() == 0 ? nonEmptyText(message.content) : content;
    }

    private Object buildAnthropicUserContent(SimpleMessage message) {
        if (!message.hasReferences()) {
            return nonEmptyText(message.content);
        }

        JSONArray attachments = ChatReferenceManager.buildAnthropicImageContentParts(
                SketchApplication.getContext(), message.references);
        appendAll(attachments, ChatReferenceManager.buildAnthropicDocumentContentParts(
                SketchApplication.getContext(), message.references));
        if (attachments.length() == 0) {
            return nonEmptyText(message.content);
        }

        JSONArray content = new JSONArray();
        try {
            content.put(new JSONObject()
                    .put("type", "text")
                    .put("text", nonEmptyText(message.content)));
            appendAll(content, attachments);
        } catch (Exception ignored) {
        }
        return content.length() == 0 ? nonEmptyText(message.content) : content;
    }

    private static void appendAll(JSONArray target, JSONArray source) {
        if (target == null || source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            try {
                target.put(source.get(i));
            } catch (Exception ignored) {
            }
        }
    }

    private JSONArray buildAnthropicAssistantContent(SimpleMessage message) {
        JSONArray content = new JSONArray();
        String reasoning = safe(message.reasoning).trim();
        if (!reasoning.isEmpty()) {
            try {
                content.put(new JSONObject()
                        .put("type", "text")
                        .put("text", "<thinking>\n" + reasoning + "\n</thinking>"));
            } catch (Exception ignored) {
            }
        }

        String text = safe(message.content).trim();
        if (!text.isEmpty()) {
            try {
                content.put(new JSONObject()
                        .put("type", "text")
                        .put("text", text));
            } catch (Exception ignored) {
            }
        }

        return content;
    }

    private JSONArray ensureAnthropicContentArray(JSONObject assistantMessage) {
        Object rawContent = assistantMessage.opt("content");
        if (rawContent instanceof JSONArray) {
            return (JSONArray) rawContent;
        }

        JSONArray content = new JSONArray();
        String text = rawContent == null || rawContent == JSONObject.NULL ? "" : String.valueOf(rawContent);
        if (!text.trim().isEmpty()) {
            try {
                content.put(new JSONObject()
                        .put("type", "text")
                        .put("text", text.trim()));
            } catch (Exception ignored) {
            }
        }
        try {
            assistantMessage.put("content", content);
        } catch (Exception ignored) {
        }
        return content;
    }

    private JSONObject findPreviousAssistant(JSONArray array) {
        JSONObject candidate = array.optJSONObject(array.length() - 1);
        if (candidate != null && "assistant".equals(candidate.optString("role", ""))) {
            return candidate;
        }
        return null;
    }

    private JSONObject findPreviousGeminiModel(JSONArray array) {
        JSONObject candidate = array.optJSONObject(array.length() - 1);
        if (candidate != null && "model".equals(candidate.optString("role", ""))) {
            return candidate;
        }
        return null;
    }

    private JSONArray trimProviderMessages(JSONArray providerMessages, int historyBudgetTokens) {
        JSONArray trimmed = cloneArray(providerMessages);
        // Memory of Intent: the original user request must survive history pruning,
        // even when a compacted assistant summary precedes it.
        JSONObject firstUserMessage = findFirstMessageWithRole(trimmed, "user");
        JSONObject compactedSummary = findCompactedSummaryMessage(trimmed);

        while (trimmed.length() > 1 && estimateTokens(trimmed.toString()) > historyBudgetTokens) {
            int removableIndex = findOldestRemovableMessageIndex(trimmed, firstUserMessage, compactedSummary);
            if (removableIndex < 0) {
                break;
            }
            removeMessageGroup(trimmed, removableIndex, firstUserMessage, compactedSummary);
        }

        if (estimateTokens(trimmed.toString()) <= historyBudgetTokens) {
            return trimmed;
        }

        try {
            JSONObject last = trimmed.optJSONObject(trimmed.length() - 1);
            if (last != null && last.has("content")) {
                Object content = last.opt("content");
                if (content instanceof String) {
                    last.put("content", nonEmptyText(trimToTokens((String) content, Math.max(120, historyBudgetTokens / 2))));
                } else if (content instanceof JSONArray) {
                    trimAnthropicContent((JSONArray) content, Math.max(120, historyBudgetTokens / 2));
                }
            }
        } catch (Exception ignored) {
        }
        return trimmed;
    }

    private JSONObject findFirstMessageWithRole(JSONArray messages, String role) {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null && role.equals(message.optString("role", ""))) {
                return message;
            }
        }
        return null;
    }

    /** A summary is the only retained representation of compacted turns; never drop it first. */
    private JSONObject findCompactedSummaryMessage(JSONArray messages) {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (isCompactedSummaryMessage(message)) {
                return message;
            }
        }
        return null;
    }

    private boolean isCompactedSummaryMessage(JSONObject message) {
        if (message == null) {
            return false;
        }
        String prefix = "[Summary of the earlier conversation";
        Object content = message.opt("content");
        if (content instanceof String && ((String) content).startsWith(prefix)) {
            return true;
        }
        JSONArray contentBlocks = content instanceof JSONArray ? (JSONArray) content : null;
        if (arrayContainsTextPrefix(contentBlocks, "text", prefix)) {
            return true;
        }
        return arrayContainsTextPrefix(message.optJSONArray("parts"), "text", prefix);
    }

    private boolean arrayContainsTextPrefix(JSONArray array, String key, String prefix) {
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && item.optString(key, "").startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private int findOldestRemovableMessageIndex(JSONArray messages, JSONObject protectedMessage,
                                                JSONObject protectedSummary) {
        // Preserve the newest message as well: it contains the request currently
        // being answered or the latest tool result required to continue the turn.
        for (int i = 0; i < messages.length() - 1; i++) {
            JSONObject candidate = messages.optJSONObject(i);
            if (candidate != protectedMessage && candidate != protectedSummary
                    && !toolGroupReachesNewest(messages, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean toolGroupReachesNewest(JSONArray messages, int index) {
        if (!containsToolRequest(messages.optJSONObject(index))) {
            return false;
        }
        int cursor = index + 1;
        boolean foundResponse = false;
        while (cursor < messages.length() && containsToolResponse(messages.optJSONObject(cursor))) {
            foundResponse = true;
            cursor++;
        }
        return foundResponse && cursor == messages.length();
    }

    private void removeMessageGroup(JSONArray messages, int index, JSONObject protectedMessage,
                                    JSONObject protectedSummary) {
        JSONObject candidate = messages.optJSONObject(index);
        boolean hasToolRequest = containsToolRequest(candidate);
        messages.remove(index);
        if (!hasToolRequest) {
            return;
        }
        while (index < messages.length() - 1) {
            JSONObject next = messages.optJSONObject(index);
            if (next == protectedMessage || next == protectedSummary || !containsToolResponse(next)) {
                break;
            }
            messages.remove(index);
        }
    }

    private boolean containsToolRequest(JSONObject message) {
        if (message == null) {
            return false;
        }
        if (message.optJSONArray("tool_calls") != null) {
            return true;
        }
        if (arrayContainsValue(message.optJSONArray("content"), "type", "tool_use")
                || arrayContainsObject(message.optJSONArray("parts"), "functionCall")) {
            return true;
        }
        return message.optString("content", "")
                .matches("(?s).*<[a-zA-Z0-9_.-]+>.*</[a-zA-Z0-9_.-]+>\\s*$");
    }

    private boolean containsToolResponse(JSONObject message) {
        if (message == null) {
            return false;
        }
        if ("tool".equals(message.optString("role", ""))) {
            return true;
        }
        if (arrayContainsValue(message.optJSONArray("content"), "type", "tool_result")
                || arrayContainsObject(message.optJSONArray("parts"), "functionResponse")) {
            return true;
        }
        return message.optString("content", "")
                .matches("(?s).*<[a-zA-Z0-9_.-]+_result>.*</[a-zA-Z0-9_.-]+_result>.*");
    }

    private boolean arrayContainsValue(JSONArray array, String key, String value) {
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && value.equals(item.optString(key, ""))) {
                return true;
            }
        }
        return false;
    }

    private boolean arrayContainsObject(JSONArray array, String key) {
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && item.optJSONObject(key) != null) {
                return true;
            }
        }
        return false;
    }

    private void trimAnthropicContent(JSONArray content, int tokenBudget) {
        int remaining = tokenBudget;
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) {
                continue;
            }
            String type = block.optString("type", "");
            if (!"text".equals(type)) {
                continue;
            }
            String text = trimToTokens(block.optString("text", ""), remaining);
            try {
                block.put("text", nonEmptyText(text));
            } catch (Exception ignored) {
            }
            remaining = Math.max(80, remaining / 2);
        }
    }

    private JSONArray cloneArray(JSONArray source) {
        try {
            return new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private boolean isDeepSeekModel() {
        return currentModelName != null
                && currentModelName.toLowerCase(Locale.US).contains("deepseek");
    }

    private String buildAssistantContent(SimpleMessage message, boolean includeReasoning) {
        return VoidPortConvertToLlmMessageService.buildAssistantContent(
                message.content,
                message.reasoning,
                includeReasoning
        );
    }

    private String buildXmlToolCall(String toolName, String toolArgs) {
        try {
            JSONObject argsJson = parseJsonObject(toolArgs);
            Map<String, String> params = new LinkedHashMap<>();
            JSONArray names = argsJson.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String paramName = names.optString(i, "");
                if (paramName.isEmpty()) {
                    continue;
                }
                params.put(paramName, safe(argsJson.optString(paramName, "")));
            }
            return PromptConstants.reParsedToolXmlString(toolName, params).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildXmlToolResult(String toolName, String toolResult) {
        return VoidPortConvertToLlmMessageService.buildXmlToolResult(toolName, toolResult);
    }

    private JSONObject parseJsonObject(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(rawJson);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private String normalizedJsonString(String rawJson) {
        return parseJsonObject(rawJson).toString();
    }

    /**
     * Bug fix: the fallback used to be {@code "call_" + System.currentTimeMillis()},
     * which has only millisecond resolution. When re-serializing history into a
     * request payload, several tool messages with a missing/empty {@code toolId}
     * are processed in the same tight loop iteration — comfortably within the
     * same millisecond — so this fallback could (and did) hand out the IDENTICAL
     * id string to multiple genuinely different tool calls/results in the same
     * request. A duplicate {@code tool_call_id} across two {@code tool_calls}/
     * {@code tool} entries makes providers misattribute which result answers
     * which call, so the model could see one call's success result attached to
     * a different (still-unfinished) call. {@link java.util.UUID#randomUUID()}
     * (the same fallback pattern already used in {@code AxionToolRouter.Route}
     * and {@code AiProviderService.ToolCallAccumulator}) is unique regardless of
     * how many calls are serialized within the same millisecond.
     */
    private String safeToolId(String toolId) {
        String safeId = safe(toolId).trim();
        return safeId.isEmpty() ? "call_" + java.util.UUID.randomUUID() : safeId;
    }

    private boolean appendBoundedLine(StringBuilder builder, String line, int maxTokens) {
        if (estimateTokens(builder.toString() + line) > maxTokens) {
            return false;
        }
        builder.append(line);
        return true;
    }

    private static String trimToTokens(String text, int maxTokens) {
        return VoidPortConvertToLlmMessageService.trimToApproxTokens(text, maxTokens);
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0d));
    }

    private static String nonEmptyText(String value) {
        return VoidPortConvertToLlmMessageService.nonEmptyText(value);
    }

    private static String nonEmptyToolResult(String value) {
        return VoidPortConvertToLlmMessageService.nonEmptyToolResult(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeChatMode(String chatMode) {
        if (chatMode == null) {
            return "agent";
        }
        String normalized = chatMode.trim().toLowerCase(Locale.US);
        if ("normal".equals(normalized) || "chat".equals(normalized)) {
            return "normal";
        }
        if ("gather".equals(normalized)) {
            return "gather";
        }
        return "agent";
    }

    public static ProviderFormat resolveProviderFormat(String providerId) {
        return resolveProviderFormat(providerId, null);
    }

    public static ProviderFormat resolveProviderFormat(String providerId, String modelName) {
        if (providerId == null) {
            return ProviderFormat.OPENAI;
        }
        VoidPortModelCapabilities.ToolFormat toolFormat =
                VoidPortModelCapabilities.expectedToolFormat(providerId, modelName == null ? "" : modelName);
        if (toolFormat == VoidPortModelCapabilities.ToolFormat.OPENAI_STYLE) {
            return ProviderFormat.OPENAI;
        }
        if (toolFormat == VoidPortModelCapabilities.ToolFormat.ANTHROPIC_STYLE) {
            return ProviderFormat.ANTHROPIC;
        }
        if (toolFormat == VoidPortModelCapabilities.ToolFormat.GEMINI_STYLE) {
            return "gemini".equals(providerId) ? ProviderFormat.GEMINI : ProviderFormat.OPENAI;
        }
        return ProviderFormat.XML_FALLBACK;
    }

}
