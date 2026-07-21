package pro.sketchware.activities.chat.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Pattern;

import pro.sketchware.activities.chat.ChatReference;

/**
 * PatternMatcher detects patterns in user messages and classifies the type of request.
 * This determines which tools are MANDATORY and which operations must be forced.
 *
 * The application decides when tools are required, NOT the LLM.
 * This prevents the agent from responding with text when tools should be used.
 *
 * Inspired by Cursor's forced tool calling and Void IDE's pattern recognition.
 */
public class PatternMatcher {

    /**
     * Abstract capability used when the agent only needs to discover the project shape.
     * It must never be sent to the model as a concrete tool name.
     */
    public static final String PROJECT_DISCOVERY_REQUIREMENT = "project_discovery";

    /**
     * Type of user request detected.
     */
    public enum RequestType {
        /** Simple greeting or chat */
        CHAT,
        /** Request to read/view file content */
        READ_FILE,
        /** Request to search/find files or content */
        SEARCH,
        /** Request to edit/modify existing file */
        EDIT_FILE,
        /** Request to create new file */
        CREATE_FILE,
        /** Request to delete file */
        DELETE_FILE,
        /** Request to run command or execute code */
        RUN_COMMAND,
        /** Request to analyze/explain code */
        ANALYZE_CODE,
        /** Request to fix bug or error */
        FIX_BUG,
        /** Request to refactor code */
        REFACTOR,
        /** General coding task (catch-all) */
        GENERAL_CODING,
        /** Unknown/unclear intent */
        UNKNOWN
    }

    /**
     * Result of pattern analysis.
     */
    public static class Result {
        private final RequestType primaryType;
        private final List<RequestType> secondaryTypes;
        private final List<String> requiredTools;
        private final List<String> optionalTools;
        private final List<String> extractedFilePaths;
        private final boolean requiresProjectExploration;
        private final int confidenceScore;

        private Result(@NonNull Builder builder) {
            this.primaryType = builder.primaryType;
            this.secondaryTypes = Collections.unmodifiableList(new ArrayList<>(builder.secondaryTypes));
            this.requiredTools = Collections.unmodifiableList(new ArrayList<>(builder.requiredTools));
            this.optionalTools = Collections.unmodifiableList(new ArrayList<>(builder.optionalTools));
            this.extractedFilePaths = Collections.unmodifiableList(new ArrayList<>(builder.extractedFilePaths));
            this.requiresProjectExploration = builder.requiresProjectExploration;
            this.confidenceScore = builder.confidenceScore;
        }

        @NonNull
        public RequestType getPrimaryType() {
            return primaryType;
        }

        @NonNull
        public List<RequestType> getSecondaryTypes() {
            return secondaryTypes;
        }

        @NonNull
        public List<String> getRequiredTools() {
            return requiredTools;
        }

        @NonNull
        public List<String> getOptionalTools() {
            return optionalTools;
        }

        @NonNull
        public List<String> getExtractedFilePaths() {
            return extractedFilePaths;
        }

        public boolean requiresProjectExploration() {
            return requiresProjectExploration;
        }

        public int getConfidenceScore() {
            return confidenceScore;
        }

        public boolean hasRequiredTools() {
            return !requiredTools.isEmpty();
        }

        public boolean isChatOnly() {
            return (primaryType == RequestType.CHAT || primaryType == RequestType.UNKNOWN)
                    && requiredTools.isEmpty();
        }

        @NonNull
        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            private RequestType primaryType = RequestType.UNKNOWN;
            private final List<RequestType> secondaryTypes = new ArrayList<>();
            private final List<String> requiredTools = new ArrayList<>();
            private final List<String> optionalTools = new ArrayList<>();
            private final List<String> extractedFilePaths = new ArrayList<>();
            private boolean requiresProjectExploration = false;
            private int confidenceScore = 0;

            Builder primaryType(RequestType type) {
                this.primaryType = type;
                return this;
            }

            Builder addSecondaryType(RequestType type) {
                if (!secondaryTypes.contains(type)) {
                    secondaryTypes.add(type);
                }
                return this;
            }

            Builder addRequiredTool(String tool) {
                if (!requiredTools.contains(tool)) {
                    requiredTools.add(tool);
                }
                return this;
            }

            Builder addOptionalTool(String tool) {
                if (!optionalTools.contains(tool)) {
                    optionalTools.add(tool);
                }
                return this;
            }

            Builder addExtractedFilePath(String path) {
                if (!extractedFilePaths.contains(path)) {
                    extractedFilePaths.add(path);
                }
                return this;
            }

            Builder requiresProjectExploration(boolean requires) {
                this.requiresProjectExploration = requires;
                return this;
            }

            Builder confidenceScore(int score) {
                this.confidenceScore = Math.max(0, Math.min(100, score));
                return this;
            }

            Result build() {
                return new Result(this);
            }
        }
    }

    // Regex patterns for detection
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^\\s*(hi|hello|hey|good morning|good afternoon|good evening|howdy|what's up|sup|oi|ola|bom dia|boa tarde|boa noite|e ai)\\s*[!.?]*\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern READ_FILE_PATTERN = Pattern.compile(
            "(?i)\\b(show|display|read|view|see|check|look at|open|cat|print|get|fetch|mostre|exiba|leia|ver|veja|confira|abra)\\b.*\\b(file|code|content|class|method|function|arquivo|codigo|conteudo|classe|metodo|funcao)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SEARCH_PATTERN = Pattern.compile(
            "(?i)\\b(find|search|locate|look for|where is|where's|which file|what file|grep|encontre|ache|procure|buscar|localize|onde esta|qual arquivo)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EDIT_FILE_PATTERN = Pattern.compile(
            "(?i)\\b(edit|modify|change|update|alter|correct|adjust|revise|edite|modifique|mude|altere|atualize|corrija|ajuste)\\b.*\\b(file|code|class|method|function|line|arquivo|codigo|classe|metodo|funcao|linha)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CREATE_FILE_PATTERN = Pattern.compile(
            "(?i)\\b(create|add|make|new|generate|write|crie|adicione|novo|nova|gere|escreva)\\b.*\\b(file|class|component|module|arquivo|classe|componente|modulo)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DELETE_FILE_PATTERN = Pattern.compile(
            "(?i)\\b(delete|remove|erase|drop|apague|exclua|remova|deletar)\\b.*\\b(file|class|component|arquivo|classe|componente)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RUN_COMMAND_PATTERN = Pattern.compile(
            "(?i)\\b(run|execute|start|launch|compile|build|test|debug|install|rode|execute|inicie|compile|compilar|teste|testar|instale|instalar)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ANALYZE_PATTERN = Pattern.compile(
            "(?i)\\b(analyze|explain|describe|tell me about|what does|how does|understand|review|analise|explique|descreva|entenda|revise)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FIX_BUG_PATTERN = Pattern.compile(
            "(?i)\\b(fix|repair|solve|resolve|debug|error|bug|issue|problem|crash|exception|corrija|conserte|resolva|erro|falha|problema|excecao)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REFACTOR_PATTERN = Pattern.compile(
            "(?i)\\b(refactor|restructure|reorganize|improve|optimize|clean up|simplify|refatore|reestruture|reorganize|melhore|otimize|simplifique)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IMPLEMENT_PATTERN = Pattern.compile(
            "(?i)\\b(implement|integrate|migrate|port|complete|continue|develop|implemente|integre|migre|porte|complete|continue|desenvolva|faca)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // File path extraction pattern
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?:[a-zA-Z]:[\\\\/])?(?:[a-zA-Z0-9_. -]+[\\\\/])*[a-zA-Z0-9_.-]+\\.(java|kt|xml|json|gradle|properties|txt|md)"
    );

    /**
     * Analyzes user message and context to detect patterns and determine required tools.
     */
    @NonNull
    public static Result analyze(@NonNull String userMessage,
                                  @Nullable String contextPayload,
                                  @Nullable List<ChatReference> selections) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Result.builder()
                    .primaryType(RequestType.UNKNOWN)
                    .confidenceScore(0)
                    .build();
        }

        String message = normalizeForMatching(userMessage);
        boolean hasWorkspaceContext = (contextPayload != null && !contextPayload.trim().isEmpty())
                || (selections != null && !selections.isEmpty())
                || FILE_PATH_PATTERN.matcher(userMessage).find()
                || message.matches("(?s).*\\b(project|workspace|repository|repo|codebase|app|projeto|repositorio|codigo fonte|neste codigo|nesse codigo)\\b.*");
        Result.Builder resultBuilder = Result.builder();
        int maxConfidence = 0;
        RequestType detectedType = RequestType.UNKNOWN;

        // Check patterns in order of priority

        // 1. Greeting (simple chat)
        if (GREETING_PATTERN.matcher(message).matches() && message.length() < 50) {
            detectedType = RequestType.CHAT;
            maxConfidence = 95;
        }

        // 2. Delete file (destructive, high priority)
        else if (DELETE_FILE_PATTERN.matcher(message).find()) {
            detectedType = RequestType.DELETE_FILE;
            maxConfidence = 85;
            resultBuilder.addRequiredTool("search_pathnames_only");
            resultBuilder.addRequiredTool("delete_file_or_folder");
        }

        // 3. Create file
        else if (CREATE_FILE_PATTERN.matcher(message).find()) {
            detectedType = RequestType.CREATE_FILE;
            maxConfidence = 80;
            resultBuilder.addRequiredTool("ls_dir");
            resultBuilder.addRequiredTool("create_file_or_folder");
            resultBuilder.addRequiredTool("rewrite_file");
        }

        // 4. Edit file
        else if (EDIT_FILE_PATTERN.matcher(message).find()
                && !FIX_BUG_PATTERN.matcher(message).find()) {
            detectedType = RequestType.EDIT_FILE;
            maxConfidence = 85;
            resultBuilder.addRequiredTool("read_file");  // MUST read before edit
            resultBuilder.addRequiredTool("edit_file");
            resultBuilder.addSecondaryType(RequestType.SEARCH);
        }

        // 5. Fix bug (usually requires read + edit)
        else if (FIX_BUG_PATTERN.matcher(message).find()) {
            detectedType = RequestType.FIX_BUG;
            maxConfidence = 80;
            resultBuilder.addRequiredTool("search_for_files");
            resultBuilder.addRequiredTool("read_file");
            resultBuilder.addRequiredTool("edit_file");
            resultBuilder.addSecondaryType(RequestType.SEARCH);
            resultBuilder.addSecondaryType(RequestType.EDIT_FILE);
            resultBuilder.requiresProjectExploration(true);
        }

        // 6. Refactor
        else if (REFACTOR_PATTERN.matcher(message).find()) {
            detectedType = RequestType.REFACTOR;
            maxConfidence = 75;
            resultBuilder.addRequiredTool("read_file");
            resultBuilder.addRequiredTool("edit_file");
            resultBuilder.addSecondaryType(RequestType.EDIT_FILE);
        }

        // 7. Run command
        else if (RUN_COMMAND_PATTERN.matcher(message).find()) {
            detectedType = RequestType.RUN_COMMAND;
            maxConfidence = 85;
            resultBuilder.addRequiredTool("run_command");
        }

        // 8. Search
        else if (SEARCH_PATTERN.matcher(message).find()) {
            detectedType = RequestType.SEARCH;
            maxConfidence = 80;
            resultBuilder.addRequiredTool("search_for_files");
            resultBuilder.addOptionalTool("search_pathnames_only");
            resultBuilder.requiresProjectExploration(true);
        }

        // 9. Read file
        else if (READ_FILE_PATTERN.matcher(message).find()) {
            detectedType = RequestType.READ_FILE;
            maxConfidence = 85;
            resultBuilder.addRequiredTool("read_file");
            resultBuilder.addSecondaryType(RequestType.SEARCH);
        }

        // 10. Analyze code
        else if (ANALYZE_PATTERN.matcher(message).find()) {
            if (hasWorkspaceContext) {
                detectedType = RequestType.ANALYZE_CODE;
                maxConfidence = 75;
                resultBuilder.addRequiredTool("read_file");
                resultBuilder.addOptionalTool("get_dir_tree");
            } else {
                detectedType = RequestType.CHAT;
                maxConfidence = 70;
            }
        }

        // 11. Broad implementation request tied to this workspace
        else if (hasWorkspaceContext && IMPLEMENT_PATTERN.matcher(message).find()) {
            detectedType = RequestType.GENERAL_CODING;
            maxConfidence = 70;
            resultBuilder.addRequiredTool(PROJECT_DISCOVERY_REQUIREMENT);
            resultBuilder.requiresProjectExploration(true);
        }

        // 12. General coding (catch-all for conceptual coding keywords)
        else if (message.matches("(?i).*\\b(code|coding|program|function|class|method|variable)\\b.*")) {
            detectedType = RequestType.GENERAL_CODING;
            maxConfidence = 60;
            resultBuilder.requiresProjectExploration(true);
        }

        // Extract file paths from message
        java.util.regex.Matcher pathMatcher = FILE_PATH_PATTERN.matcher(userMessage);
        while (pathMatcher.find()) {
            resultBuilder.addExtractedFilePath(pathMatcher.group());
        }

        // Extract file paths from selections
        if (selections != null) {
            for (ChatReference ref : selections) {
                if (ref != null
                        && (ref.getType() == ChatReference.TYPE_FILE
                        || ref.getType() == ChatReference.TYPE_CODE_SELECTION)
                        && ref.getPath() != null
                        && !ref.getPath().trim().isEmpty()) {
                    resultBuilder.addExtractedFilePath(ref.getPath());
                    // If user selected files, likely wants to work with them
                    if (maxConfidence < 90 && detectedType != RequestType.CHAT) {
                        maxConfidence += 10;
                    }
                }
            }
        }

        resultBuilder.primaryType(detectedType);
        resultBuilder.confidenceScore(maxConfidence);

        return resultBuilder.build();
    }

    /**
     * Returns whether a successful tool call can satisfy project discovery.
     */
    static boolean isProjectDiscoveryTool(@Nullable String toolName) {
        return "ls_dir".equals(toolName)
                || "get_dir_tree".equals(toolName)
                || "search_pathnames_only".equals(toolName)
                || "search_for_files".equals(toolName);
    }

    @NonNull
    private static String normalizeForMatching(@NonNull String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }
}
