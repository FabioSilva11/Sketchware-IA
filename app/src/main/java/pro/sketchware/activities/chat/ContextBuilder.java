package pro.sketchware.activities.chat;

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
import pro.sketchware.activities.chat.port.VoidPortConvertToLlmMessageService;
import pro.sketchware.activities.chat.port.VoidPortLlmMessage;
import pro.sketchware.activities.chat.port.VoidPortMcpChannel;
import pro.sketchware.activities.chat.port.VoidPortModelCapabilities;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.activities.chat.port.VoidPortToolsService;
import pro.sketchware.ai.config.DeviceLanguage;
import pro.sketchware.ia.tools.Tool;
import pro.sketchware.ia.tools.ToolManager;
import pro.sketchware.util.ProjectPathResolver;

/**
 * Builds a bounded provider-aware request context so the chat can preserve
 * tool history across OpenAI-style, Anthropic-style and XML fallback flows.
 */
public class ContextBuilder {
    private static final int DEFAULT_TOTAL_BUDGET_TOKENS = 6000;
    private static final int DEFAULT_SYSTEM_BUDGET_TOKENS = 2400;
    private static final int DEFAULT_HISTORY_BUDGET_TOKENS = 3000;
    private static final int MAX_ANDROID_CONTEXT_BUDGET_TOKENS = 128000;
    private static final int DEFAULT_COMPILE_ERROR_TOKENS = 500;
    private static final long DIRECTORY_CACHE_TTL_MS = 5000L;
    private static final String EMPTY_MESSAGE = VoidPortConvertToLlmMessageService.EMPTY_MESSAGE;
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
    private final ToolManager toolManager;
    private int totalBudgetTokens = DEFAULT_TOTAL_BUDGET_TOKENS;
    private int systemBudgetTokens = DEFAULT_SYSTEM_BUDGET_TOKENS;
    private int historyBudgetTokens = 8000; // Increased to prevent losing older steps
    private int compileErrorBudgetTokens = DEFAULT_COMPILE_ERROR_TOKENS;

    /** Summary replacing messages before {@link #historyStartIndex} (context compaction). */
    private String historySummary = "";
    private int historyStartIndex = 0;
    private String agentGuidance = "";
    private boolean includeNativeReferences = true;

    public ContextBuilder(String scId, List<ChatMessage> messages, ToolManager toolManager) {
        this.scId = scId;
        this.messages = messages;
        this.toolManager = toolManager;
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
        String currentModel = prefs.getString(VoidPortSettings.PREF_CURRENT_MODEL, "");
        VoidPortModelCapabilities.Capabilities capabilities =
                VoidPortModelCapabilities.getModelCapabilities(providerId, currentModel);
        configureBudgets(capabilities);
        ProviderFormat providerFormat = resolveProviderFormat(providerId, currentModel);
        String systemContext = buildSystemContext(latestUserMessage, chatMode, providerId, providerFormat);
        JSONArray providerMessages = buildProviderMessages(historyBudgetTokens, providerFormat, providerId);
        int totalEstimate = estimateTokens(systemContext) + estimateTokens(providerMessages.toString());
        return new Result(systemContext, providerMessages, Math.min(totalEstimate, totalBudgetTokens), providerFormat);
    }

    private void configureBudgets(VoidPortModelCapabilities.Capabilities capabilities) {
        if (capabilities == null) {
            totalBudgetTokens = DEFAULT_TOTAL_BUDGET_TOKENS;
            systemBudgetTokens = DEFAULT_SYSTEM_BUDGET_TOKENS;
            historyBudgetTokens = DEFAULT_HISTORY_BUDGET_TOKENS;
            compileErrorBudgetTokens = DEFAULT_COMPILE_ERROR_TOKENS;
            return;
        }

        boolean reasoningEnabled = capabilities.reasoningCapabilities.supportsReasoning
                && !capabilities.reasoningCapabilities.canTurnOffReasoning;
        int reservedOutput = Math.max(1024, capabilities.effectiveReservedOutputTokenSpace(reasoningEnabled));
        int usableWindow = Math.max(DEFAULT_TOTAL_BUDGET_TOKENS, capabilities.contextWindow - reservedOutput);
        totalBudgetTokens = Math.max(DEFAULT_TOTAL_BUDGET_TOKENS,
                Math.min(MAX_ANDROID_CONTEXT_BUDGET_TOKENS, usableWindow));
        systemBudgetTokens = Math.max(DEFAULT_SYSTEM_BUDGET_TOKENS, Math.min(16000, totalBudgetTokens / 4));
        compileErrorBudgetTokens = Math.max(DEFAULT_COMPILE_ERROR_TOKENS, Math.min(2000, systemBudgetTokens / 6));
        historyBudgetTokens = Math.max(DEFAULT_HISTORY_BUDGET_TOKENS,
                totalBudgetTokens - systemBudgetTokens - compileErrorBudgetTokens);
    }

    private String buildSystemContext(String latestUserMessage, String chatMode, String providerId, ProviderFormat providerFormat) {
        String safeChatMode = normalizeChatMode(chatMode);
        String header = "You are an expert coding " + ("agent".equals(safeChatMode) ? "agent" : "assistant") + " whose job is "
                + ("agent".equals(safeChatMode)
                ? "to help the user develop, run, and make changes to their codebase."
                : "gather".equals(safeChatMode)
                ? "to search, understand, and reference files in the user's codebase."
                : "to assist the user with their coding tasks.")
                + "\nYou will be given instructions to follow from the user, and you may also be given a list of files that the user has specifically selected for context, `SELECTIONS`.\n"
                + "Please assist the user with their query.";

        String sysInfo = buildVoidSystemInfo(safeChatMode);
        String toolDefinitions = providerFormat == ProviderFormat.XML_FALLBACK
                ? buildXmlToolDefinitions(safeChatMode)
                : "";
        String importantDetails = buildVoidImportantDetails(safeChatMode, providerFormat);
        String fsInfo = "Here is an overview of the user's file system:\n"
                + "<files_overview>\n"
                + buildDirectoryStr()
                + "\n</files_overview>";

        StringBuilder full = new StringBuilder();
        appendPromptSection(full, header);
        appendPromptSection(full, sysInfo);
        appendPromptSection(full, toolDefinitions);
        appendPromptSection(full, importantDetails);
        if ("agent".equals(safeChatMode) && !agentGuidance.isEmpty()) {
            appendPromptSection(full, "Current agent objective and execution state:\n<agent_state>\n"
                    + agentGuidance + "\n</agent_state>");
        }
        appendPromptSection(full, fsInfo);
        return trimToTokens(full.toString().trim().replace("\t", "  "), systemBudgetTokens);
    }

    private String buildVoidSystemInfo(String chatMode) {
        StringBuilder builder = new StringBuilder();
        builder.append("Here is the user's system information:\n");
        builder.append("<system_info>\n");
        builder.append("- Android\n\n");
        File primaryRoot = ProjectPathResolver.getPrimaryReadableRoot(scId);
        builder.append("- Current project root:\n");
        builder.append(primaryRoot == null ? "NOT AVAILABLE" : primaryRoot.getAbsolutePath()).append("\n\n");
        builder.append("- The user's workspace contains these folders:\n");
        builder.append(workspaceFoldersString()).append("\n\n");
        builder.append("- Project path contract:\n");
        builder.append("Use '.' for the current project root. '/' is also treated as that project root, never as the Android device root. ")
                .append("Use paths returned by tools or listed above. Never send placeholders such as <uri>, <path>, undefined, or invented absolute paths.\n\n");
        builder.append("- Active file:\n");
        builder.append("NOT SUPPLIED\n\n");
        builder.append("- Open files:\n");
        builder.append("NO OPENED FILES");
        if ("agent".equals(chatMode)) {
            List<String> terminalIds = VoidPortToolsService.getPersistentTerminalIds();
            if (terminalIds != null && !terminalIds.isEmpty()) {
                builder.append("\n\n- Persistent terminal IDs available for you to run commands in: ")
                        .append(String.join(", ", terminalIds));
            }
        }
        builder.append("\n</system_info>");
        return builder.toString();
    }

    private String workspaceFoldersString() {
        List<String> folders = new ArrayList<>();
        try {
            for (File root : ProjectPathResolver.getReadableRoots(scId)) {
                if (root != null) {
                    folders.add(root.getAbsolutePath());
                }
            }
        } catch (Exception ignored) {
        }
        if (folders.isEmpty()) {
            return "NO FOLDERS OPEN";
        }
        return String.join("\n", folders);
    }

    private String buildDirectoryStr() {
        long now = System.currentTimeMillis();
        String cacheKey = scId == null ? "" : scId;
        DirectoryCacheEntry cached = DIRECTORY_CACHE.get(cacheKey);
        if (cached != null && now - cached.createdAt <= DIRECTORY_CACHE_TTL_MS) {
            return cached.value;
        }
        StringBuilder builder = new StringBuilder();
        try {
            for (File root : ProjectPathResolver.getReadableRoots(scId)) {
                if (root == null || !root.exists()) {
                    continue;
                }
                String tree = DirectoryTreeService.getDirectoryStrTool(root);
                if (tree == null || tree.trim().isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append(trimToTokens(tree, 1200));
            }
        } catch (Exception ignored) {
        }
        String result = builder.length() == 0 ? "NO FOLDERS OPEN" : builder.toString();
        DIRECTORY_CACHE.put(cacheKey, new DirectoryCacheEntry(result, now));
        return result;
    }

    private String buildXmlToolDefinitions(String chatMode) {
        if ("normal".equals(chatMode) || toolManager == null) {
            return "";
        }
        List<Tool> availableTools = toolManager.getToolsForChatMode(chatMode);
        if (availableTools.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Available tools:\n\n");
        int toolIndex = 1;
        for (Tool tool : availableTools) {
            if (tool == null) {
                continue;
            }
            if (toolIndex > 1) {
                builder.append("\n\n");
            }
            appendXmlToolDefinitionUnbounded(builder, tool, toolIndex);
            toolIndex++;
        }
        if ("agent".equals(chatMode)) {
            JSONArray mcpTools = VoidPortMcpChannel.getToolsAsMCP(VoidPortSettings.prefs(SketchApplication.getContext()));
            for (int i = 0; i < mcpTools.length(); i++) {
                JSONObject toolObject = mcpTools.optJSONObject(i);
                JSONObject function = toolObject == null ? null : toolObject.optJSONObject("function");
                if (function == null) {
                    continue;
                }
                if (toolIndex > 1) {
                    builder.append("\n\n");
                }
                appendXmlFunctionDefinitionUnbounded(builder, function, toolIndex);
                toolIndex++;
            }
        }
        builder.append("\n\nTool calling details:\n");
        builder.append("- To call a tool, write its name and parameters in one of the XML formats specified above.\n");
        builder.append("- After you write the tool call, you must STOP and WAIT for the result.\n");
        builder.append("- All parameters are REQUIRED unless noted otherwise.\n");
        builder.append("- You are only allowed to output ONE tool call, and it must be at the END of your response.\n");
        builder.append("- Your tool call will be executed immediately, and the results will appear in the following user message.");
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
            builder.append("Description: ").append(safe(tool.getDescription())).append("\n");
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

    private void appendXmlFunctionDefinitionUnbounded(StringBuilder builder, JSONObject function, int toolIndex) {
        try {
            String toolName = function.optString("name", "");
            if (toolName.isEmpty()) {
                return;
            }
            JSONObject parameters = function.optJSONObject("parameters");
            JSONObject properties = parameters == null ? null : parameters.optJSONObject("properties");
            builder.append(toolIndex).append(". ").append(toolName).append("\n");
            builder.append("Description: ").append(function.optString("description", "")).append("\n");
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
                    .append(property == null ? "" : property.optString("description", ""));
        }
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

    private String buildVoidImportantDetails(String chatMode, ProviderFormat providerFormat) {
        List<String> details = new ArrayList<>();
        details.add("Follow the user's requested scope. If an action is blocked, explain the concrete blocker and continue with any safe work that remains possible.");

        if ("agent".equals(chatMode)) {
            details.add("Use tools whenever the request requires workspace facts, file inspection, commands, or changes. A greeting, conceptual question, or necessary clarification may be answered without tools.");
            details.add("For requested changes, perform the change with tools instead of only suggesting code or describing future work.");
            details.add("Read an existing file before editing or overwriting it. If its location is unknown, search for it before assuming a path.");
            details.add("After a mutation, inspect the result or run the narrowest relevant verification before claiming completion.");
            details.add("Tool approval is handled by the application. Issue the appropriate tool call and wait when approval is required; do not claim that an unapproved action ran.");
            if (providerFormat == ProviderFormat.XML_FALLBACK) {
                details.add("Use exactly one XML tool call at the end of the response, then stop and wait for its result.");
            } else {
                details.add("You may request multiple independent tools in one response. The application executes them sequentially and returns every result before you continue.");
            }
            details.add("Do not announce a tool by its internal name. Briefly state the immediate purpose only when a progress update is useful.");
            details.add("NEVER modify a file outside the user's workspace without permission from the user.");
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
        details.add(DeviceLanguage.responseInstruction());
        details.add("Always use MARKDOWN to format lists, bullet points, etc. Do NOT write tables.");
        details.add("Today's date is " + PromptConstants.todayDateForPrompt() + ".");

        StringBuilder builder = new StringBuilder("Important notes:\n");
        for (int i = 0; i < details.size(); i++) {
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(i + 1).append(". ").append(details.get(i));
        }
        return builder.toString();
    }

    private void appendPromptSection(StringBuilder builder, String section) {
        if (section == null || section.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n\n");
        }
        builder.append(section.trim());
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
        int userMessageBudget = Math.max(DEFAULT_HISTORY_BUDGET_TOKENS, historyBudgetTokens);
        int latestUserIndex = findLatestUserMessageIndex();
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
                    "[Resumo da conversa anterior — mensagens antigas foram compactadas]\n" + historySummary,
                    ""));
        }
        int oldToolResultsToCompact = countOldToolResultsToCompact(historyStartIndex);
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
                // Preserve the protocol pair, but compact old tool output before
                // it enters the provider payload. Tool calls/results must remain
                // atomic for OpenAI, Anthropic and Gemini request formats.
                boolean compactToolResult = oldToolResultsToCompact > 0;
                if (compactToolResult) {
                    oldToolResultsToCompact--;
                }
                String toolResult = compactToolResult
                        ? compactToolResult(toolName, safe(message.getToolResult()))
                        : trimToTokens(safe(message.getToolResult()), 4000);
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
        return simpleMessages;
    }

    /**
     * First pipeline stage: preserve the two newest tool-result groups verbatim
     * and replace older results with small, deterministic summaries. Keeping the
     * tool call and its result in the history preserves API-valid atomic groups.
     */
    private int countOldToolResultsToCompact(int startIndex) {
        int total = 0;
        for (int i = startIndex; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message != null && message.isTool()) {
                total++;
            }
        }
        return Math.max(0, total - 2);
    }

    private String compactToolResult(String toolName, String result) {
        String normalized = safe(result).replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "[Tool result compacted: " + safe(toolName) + " returned no text]";
        }
        String preview = trimToTokens(normalized, 96);
        return "[Tool result compacted: " + safe(toolName)
                + "; originalChars=" + normalized.length()
                + "; preview=" + preview + "]";
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

        for (SimpleMessage message : simpleMessages) {
            try {
                if (message.role == SimpleMessage.ROLE_USER) {
                    array.put(new JSONObject()
                            .put("role", "user")
                            .put("content", buildOpenAiUserContent(message, providerId)));
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_ASSISTANT) {
                    array.put(new JSONObject()
                            .put("role", "assistant")
                            .put("content", nonEmptyText(buildAssistantContent(message, false))));
                    continue;
                }

                if (message.role == SimpleMessage.ROLE_TOOL) {
                    JSONObject assistant = findPreviousAssistant(array);
                    if (assistant == null) {
                        assistant = new JSONObject()
                                .put("role", "assistant")
                                .put("content", EMPTY_MESSAGE);
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
                            .put("content", nonEmptyText(message.toolResult));
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
                    array.put(new JSONObject()
                            .put("role", "model")
                            .put("parts", new JSONArray().put(new JSONObject()
                                    .put("text", nonEmptyText(buildAssistantContent(message, false))))));
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
                                                    .put("result", nonEmptyText(message.toolResult)))))));
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
                                .put("content", new JSONArray().put(new JSONObject()
                                        .put("type", "text")
                                        .put("text", EMPTY_MESSAGE)));
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
                            .put("content", nonEmptyText(message.toolResult)));
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

        JSONArray attachments = new JSONArray();
        if (ChatReferenceManager.supportsNativeOpenAiImageBlocks(providerId)) {
            attachments = ChatReferenceManager.buildOpenAiImageContentParts(
                    SketchApplication.getContext(), message.references);
        }
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

        if (content.length() == 0) {
            try {
                content.put(new JSONObject()
                        .put("type", "text")
                        .put("text", EMPTY_MESSAGE));
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
        try {
            content.put(new JSONObject()
                    .put("type", "text")
                    .put("text", nonEmptyText(text)));
        } catch (Exception ignored) {
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

        while (trimmed.length() > 1 && estimateTokens(trimmed.toString()) > historyBudgetTokens) {
            int removableIndex = findOldestRemovableMessageIndex(trimmed, firstUserMessage);
            if (removableIndex < 0) {
                break;
            }
            removeMessageGroup(trimmed, removableIndex, firstUserMessage);
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

    private int findOldestRemovableMessageIndex(JSONArray messages, JSONObject protectedMessage) {
        // Preserve the newest message as well: it contains the request currently
        // being answered or the latest tool result required to continue the turn.
        for (int i = 0; i < messages.length() - 1; i++) {
            JSONObject candidate = messages.optJSONObject(i);
            // A tool response is removed only with its preceding tool-call
            // message. Removing it independently leaves an invalid API history.
            if (candidate != protectedMessage && !containsToolResponse(candidate)
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

    private void removeMessageGroup(JSONArray messages, int index, JSONObject protectedMessage) {
        JSONObject candidate = messages.optJSONObject(index);
        boolean hasToolRequest = containsToolRequest(candidate);
        messages.remove(index);
        if (!hasToolRequest) {
            return;
        }
        while (index < messages.length()) {
            JSONObject next = messages.optJSONObject(index);
            if (next == protectedMessage || !containsToolResponse(next)) {
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

    private String safeToolId(String toolId) {
        String safeId = safe(toolId).trim();
        return safeId.isEmpty() ? "call_" + System.currentTimeMillis() : safeId;
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
