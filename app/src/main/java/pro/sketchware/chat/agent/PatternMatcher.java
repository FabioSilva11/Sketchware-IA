package pro.sketchware.chat.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.chat.ChatReference;

/**
 * Lightweight intent classifier used only for hints, UI and conservative
 * completion checks. It is deliberately NOT the authority that decides which
 * semantic action the model must take. Safety and mutation permissions are
 * enforced by the host/tool layer.
 */
public final class PatternMatcher {

    public static final String PROJECT_DISCOVERY_REQUIREMENT = "project_discovery";
    public static final String WORKSPACE_MUTATION_REQUIREMENT = "workspace_mutation";

    public enum RequestType {
        CHAT,
        READ_FILE,
        SEARCH,
        EDIT_FILE,
        CREATE_FILE,
        DELETE_FILE,
        RUN_COMMAND,
        ANALYZE_CODE,
        FIX_BUG,
        REFACTOR,
        GENERAL_CODING,
        UNKNOWN
    }

    public static final class Result {
        private final RequestType primaryType;
        private final List<RequestType> secondaryTypes;
        private final List<String> requiredTools;
        private final List<String> optionalTools;
        private final List<String> extractedFilePaths;
        private final boolean requiresProjectExploration;
        private final boolean allowMutations;
        private final int confidenceScore;

        private Result(@NonNull Builder builder) {
            primaryType = builder.primaryType;
            secondaryTypes = Collections.unmodifiableList(new ArrayList<>(builder.secondaryTypes));
            requiredTools = Collections.unmodifiableList(new ArrayList<>(builder.requiredTools));
            optionalTools = Collections.unmodifiableList(new ArrayList<>(builder.optionalTools));
            extractedFilePaths = Collections.unmodifiableList(new ArrayList<>(builder.extractedFilePaths));
            requiresProjectExploration = builder.requiresProjectExploration;
            allowMutations = builder.allowMutations;
            confidenceScore = builder.confidenceScore;
        }

        @NonNull public RequestType getPrimaryType() { return primaryType; }
        @NonNull public List<RequestType> getSecondaryTypes() { return secondaryTypes; }
        @NonNull public List<String> getRequiredTools() { return requiredTools; }
        @NonNull public List<String> getOptionalTools() { return optionalTools; }
        @NonNull public List<String> getExtractedFilePaths() { return extractedFilePaths; }
        public boolean requiresProjectExploration() { return requiresProjectExploration; }
        public boolean allowsMutations() { return allowMutations; }
        public boolean isReadOnly() { return !allowMutations; }
        public int getConfidenceScore() { return confidenceScore; }
        public boolean hasRequiredTools() { return !requiredTools.isEmpty(); }
        public boolean isChatOnly() {
            return primaryType == RequestType.CHAT || primaryType == RequestType.UNKNOWN;
        }

        @NonNull static Builder builder() { return new Builder(); }
    }

    static final class Builder {
        private RequestType primaryType = RequestType.UNKNOWN;
        private final List<RequestType> secondaryTypes = new ArrayList<>();
        private final List<String> requiredTools = new ArrayList<>();
        private final List<String> optionalTools = new ArrayList<>();
        private final List<String> extractedFilePaths = new ArrayList<>();
        private boolean requiresProjectExploration;
        private boolean allowMutations = true;
        private int confidenceScore;

        Builder primaryType(RequestType value) { primaryType = value; return this; }
        Builder addSecondaryType(RequestType value) {
            if (value != null && !secondaryTypes.contains(value)) secondaryTypes.add(value);
            return this;
        }
        Builder addRequiredTool(String value) {
            if (value != null && !value.isEmpty() && !requiredTools.contains(value)) requiredTools.add(value);
            return this;
        }
        Builder addOptionalTool(String value) {
            if (value != null && !value.isEmpty() && !optionalTools.contains(value)) optionalTools.add(value);
            return this;
        }
        Builder addExtractedFilePath(String value) {
            if (value != null && !value.isEmpty() && !extractedFilePaths.contains(value)) extractedFilePaths.add(value);
            return this;
        }
        Builder requiresProjectExploration(boolean value) { requiresProjectExploration = value; return this; }
        Builder allowMutations(boolean value) { allowMutations = value; return this; }
        Builder confidenceScore(int value) { confidenceScore = Math.max(0, Math.min(100, value)); return this; }
        Result build() { return new Result(this); }
    }

    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^\\s*(hi|hello|hey|good morning|good afternoon|good evening|howdy|what's up|sup|oi|ola|bom dia|boa tarde|boa noite|e ai)\\s*[!.?]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?:[a-zA-Z]:[\\\\/])?(?:[a-zA-Z0-9_. -]+[\\\\/])*[a-zA-Z0-9_.-]+\\.(java|kt|xml|json|gradle|properties|txt|md|js|ts|css|html)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GLOBAL_READ_ONLY_PATTERN = Pattern.compile(
            "(?i)(\\bsem (?:alterar|modificar|editar|mudar) (?:nada|o projeto|os arquivos|arquivos|o codigo|codigo)\\b|"
                    + "\\bnao (?:altere|modifique|edite|mude) (?:nada|o projeto|os arquivos|arquivos|o codigo|codigo)\\b|"
                    + "\\bnenhuma alteracao\\b|\\bsomente analise\\b|\\bapenas analise\\b|\\bso analise\\b|"
                    + "\\bsomente leia\\b|\\bapenas leia\\b|\\bread[- ]?only\\b|"
                    + "\\bdo not (?:modify|edit|change) (?:anything|the project|the files|the code)\\b|"
                    + "\\bdon't (?:modify|edit|change) (?:anything|the project|the files|the code)\\b|"
                    + "\\bwithout (?:modifying|editing|changing) (?:anything|the project|the files|the code)\\b)");
    private static final Pattern DELETE_FILE_PATTERN = Pattern.compile(
            "(?i)\\b(delete|remove|erase|apague|exclua|remova|deletar)\\b.*\\b(file|arquivo|class|classe|component|componente|\\w+\\.(java|kt|xml|json|js|ts|css|html|md))\\b");
    private static final Pattern CREATE_FILE_PATTERN = Pattern.compile(
            "(?i)\\b(create|add|generate|write|crie|adicione|gere|escreva)\\b.*\\b(file|arquivo|class|classe|component|componente|module|modulo|\\w+\\.(java|kt|xml|json|js|ts|css|html|md))\\b");
    private static final Pattern EDIT_FILE_PATTERN = Pattern.compile(
            "(?i)\\b(edit|modify|change|update|alter|correct|adjust|revise|edite|modifique|mude|altere|atualize|corrija|ajuste)\\b.*\\b(file|arquivo|code|codigo|class|classe|method|metodo|function|funcao|line|linha|\\w+\\.(java|kt|xml|json|js|ts|css|html|md))\\b");
    private static final Pattern SEARCH_PATTERN = Pattern.compile(
            "(?i)\\b(find|search|locate|look for|where is|which file|grep|encontre|ache|procure|buscar|localize|onde esta|qual arquivo)\\b");
    private static final Pattern READ_PATTERN = Pattern.compile(
            "(?i)\\b(show|display|read|view|open|mostre|exiba|leia|veja|confira|abra)\\b.*\\b(file|arquivo|code|codigo|content|conteudo|class|classe)\\b");
    private static final Pattern ANALYZE_PATTERN = Pattern.compile(
            "(?i)\\b(analyze|analyse|explain|review|inspect|understand|analise|explique|revise|inspecione|entenda)\\b");
    private static final Pattern FIX_PATTERN = Pattern.compile(
            "(?i)\\b(fix|repair|solve|resolve|debug|corrija|conserte|resolva)\\b|\\b(error|bug|issue|problem|crash|exception|erro|falha|problema|excecao)\\b");
    private static final Pattern REFACTOR_PATTERN = Pattern.compile(
            "(?i)\\b(refactor|restructure|reorganize|optimize|clean up|refatore|reestruture|reorganize|otimize|simplifique)\\b");
    private static final Pattern RUN_PATTERN = Pattern.compile(
            "(?i)\\b(run|execute|compile|build|test|debug|install|rode|execute|compile|compilar|teste|testar|instale|instalar)\\b");
    private static final Pattern IMPLEMENT_PATTERN = Pattern.compile(
            "(?i)\\b(implement|integrate|migrate|port|develop|create|make|implemente|integre|migre|porte|desenvolva|faca|crie|monte|construa)\\b");

    private PatternMatcher() {}

    @NonNull
    public static Result analyze(@NonNull String userMessage,
                                 @Nullable String contextPayload,
                                 @Nullable List<ChatReference> selections) {
        String raw = userMessage == null ? "" : userMessage.trim();
        if (raw.isEmpty()) {
            return Result.builder().primaryType(RequestType.UNKNOWN).confidenceScore(0).build();
        }
        String message = normalizeForMatching(raw);
        boolean globalReadOnly = GLOBAL_READ_ONLY_PATTERN.matcher(message).find();
        boolean hasPath = FILE_PATH_PATTERN.matcher(raw).find();
        boolean hasSelection = selections != null && !selections.isEmpty();
        boolean hasWorkspaceContext = hasPath || hasSelection
                || (contextPayload != null && !contextPayload.trim().isEmpty())
                || message.matches("(?s).*\\b(project|workspace|repository|repo|codebase|app|projeto|repositorio|codigo fonte|neste codigo|nesse codigo)\\b.*");

        Builder builder = Result.builder().allowMutations(!globalReadOnly);
        RequestType type = RequestType.UNKNOWN;
        int confidence = 50;

        if (GREETING_PATTERN.matcher(message).matches() && message.length() < 60) {
            type = RequestType.CHAT;
            confidence = 95;
        } else if (globalReadOnly && (ANALYZE_PATTERN.matcher(message).find() || FIX_PATTERN.matcher(message).find())) {
            type = RequestType.ANALYZE_CODE;
            confidence = 95;
            if (hasWorkspaceContext) {
                builder.requiresProjectExploration(true).addOptionalTool("read_file").addOptionalTool("search_for_files");
            }
        } else if (!globalReadOnly && DELETE_FILE_PATTERN.matcher(message).find()) {
            type = RequestType.DELETE_FILE;
            confidence = 90;
            builder.addRequiredTool("search_pathnames_only").addRequiredTool("delete_file_or_folder");
        } else if (!globalReadOnly && CREATE_FILE_PATTERN.matcher(message).find()) {
            type = RequestType.CREATE_FILE;
            confidence = 88;
            builder.addRequiredTool("create_file_or_folder").addRequiredTool("rewrite_file");
        } else if (!globalReadOnly && EDIT_FILE_PATTERN.matcher(message).find()) {
            type = RequestType.EDIT_FILE;
            confidence = 88;
            builder.addRequiredTool("read_file").addRequiredTool("edit_file");
        } else if (SEARCH_PATTERN.matcher(message).find()) {
            type = RequestType.SEARCH;
            confidence = 82;
            builder.addRequiredTool("search_for_files").addOptionalTool("search_pathnames_only").requiresProjectExploration(true);
        } else if (READ_PATTERN.matcher(message).find()) {
            type = RequestType.READ_FILE;
            confidence = 82;
            builder.addRequiredTool("read_file");
        } else if (ANALYZE_PATTERN.matcher(message).find()) {
            type = hasWorkspaceContext ? RequestType.ANALYZE_CODE : RequestType.CHAT;
            confidence = 78;
            if (hasWorkspaceContext) builder.requiresProjectExploration(true).addOptionalTool("read_file").addOptionalTool("get_dir_tree");
        } else if (RUN_PATTERN.matcher(message).find()) {
            type = RequestType.RUN_COMMAND;
            confidence = 80;
            // Execution intent must win over generic words such as "erro" in
            // "compile e mostre os erros". No run_command requirement is added:
            // the Android chat registry intentionally exposes no generic shell.
        } else if (FIX_PATTERN.matcher(message).find()) {
            type = RequestType.FIX_BUG;
            confidence = 78;
            // Hint only. A generic word such as "erro" must never force mutation.
            if (hasWorkspaceContext) builder.requiresProjectExploration(true).addOptionalTool("search_for_files").addOptionalTool("read_file").addOptionalTool("edit_file");
        } else if (REFACTOR_PATTERN.matcher(message).find()) {
            type = RequestType.REFACTOR;
            confidence = 75;
            if (hasWorkspaceContext) builder.requiresProjectExploration(true).addOptionalTool("read_file").addOptionalTool("edit_file");
        } else if (IMPLEMENT_PATTERN.matcher(message).find() && hasWorkspaceContext) {
            type = RequestType.GENERAL_CODING;
            confidence = 72;
            builder.requiresProjectExploration(true).addOptionalTool("get_dir_tree").addOptionalTool("read_file");
            if (!globalReadOnly) builder.addOptionalTool("edit_file");
        } else if (message.matches("(?s).*\\b(code|coding|program|function|class|method|variable|codigo|classe|metodo|funcao)\\b.*")) {
            type = RequestType.GENERAL_CODING;
            confidence = 62;
            if (hasWorkspaceContext) builder.requiresProjectExploration(true);
        } else {
            type = RequestType.CHAT;
            confidence = 55;
        }

        Matcher matcher = FILE_PATH_PATTERN.matcher(raw);
        while (matcher.find()) builder.addExtractedFilePath(matcher.group());
        if (selections != null) {
            for (ChatReference ref : selections) {
                if (ref == null) continue;
                if ((ref.getType() == ChatReference.TYPE_FILE || ref.getType() == ChatReference.TYPE_CODE_SELECTION)
                        && ref.getPath() != null && !ref.getPath().trim().isEmpty()) {
                    builder.addExtractedFilePath(ref.getPath().trim());
                }
            }
        }

        return builder.primaryType(type).confidenceScore(confidence).build();
    }

    static boolean isProjectDiscoveryTool(@Nullable String toolName) {
        return "ls_dir".equals(toolName) || "get_dir_tree".equals(toolName)
                || "search_pathnames_only".equals(toolName) || "search_for_files".equals(toolName)
                || "search_in_file".equals(toolName) || "read_file".equals(toolName);
    }

    static boolean isWorkspaceMutationTool(@Nullable String toolName) {
        return "edit_file".equals(toolName) || "rewrite_file".equals(toolName)
                || "create_file_or_folder".equals(toolName) || "delete_file_or_folder".equals(toolName);
    }

    @NonNull
    private static String normalizeForMatching(@NonNull String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
