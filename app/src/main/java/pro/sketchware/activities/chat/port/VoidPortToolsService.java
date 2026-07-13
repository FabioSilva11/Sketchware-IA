package pro.sketchware.activities.chat.port;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.activities.chat.DirectoryTreeService;
import pro.sketchware.R;
import pro.sketchware.SketchApplication;
import pro.sketchware.activities.chat.LanguageHelpers;
import pro.sketchware.activities.chat.PromptConstants;
import pro.sketchware.activities.chat.StringHelpers;
import pro.sketchware.util.ChatToolLog;
import pro.sketchware.util.ProjectPathResolver;
import pro.sketchware.util.SemanticFileSearcher;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;
import pro.sketchware.util.FileChangeTracker;

/**
 * Android port of browser/toolsService.ts
 * Provides all builtin tools from Void for use in Sketchware-IA chat.
 */
public final class VoidPortToolsService {

    /**
     * Max characters of file content returned per read_file page.
     * Was 500 000 — half a megabyte per call blew up the LLM context budget
     * (≈125k tokens in one tool result). 24 000 chars ≈ 6k tokens per page;
     * the tool already reports hasNextPage/pagination so the model can page.
     */
    private static final int MAX_FILE_CHARS_PAGE = 24000;
    private static final int MAX_CHILDREN_URIS_PAGE = 500;
    /**
     * How long {@code run_persistent_command} waits before returning partial
     * output while the command continues in the background.
     * Raised from 5 s → 15 s; long-running builds / gradle tasks need more time.
     */
    private static final int MAX_TERMINAL_BG_COMMAND_TIME_SECONDS = 15;
    /**
     * Default wait for a one-shot {@code run_command} before force-killing it
     * and returning a timeout result. The model can override per call via the
     * optional {@code timeout_seconds} argument (clamped to 5-300 s).
     * Raised 8 s → 30 s → 60 s; Gradle/aapt/d8 invocations routinely exceed 30 s.
     */
    private static final int MAX_TERMINAL_INACTIVE_TIME_SECONDS = 60;
    private static final int LINT_ERROR_TIMEOUT = 1000;

    private static final Map<String, Process> activeTerminals = new ConcurrentHashMap<>();
    private static final Map<String, StringBuilder> terminalOutputs = new ConcurrentHashMap<>();
    private static final Map<String, BufferedReader> terminalReaders = new ConcurrentHashMap<>();

    private VoidPortToolsService() {
    }

    public static List<String> getPersistentTerminalIds() {
        return new ArrayList<>(activeTerminals.keySet());
    }

    /**
     * Force-kills every process spawned by run_command / persistent terminals and
     * clears the tracking maps. Called when the user cancels the current agent
     * run — previously those processes kept running (and leaking) in the background.
     */
    /**
     * update_plan tool: lets the model maintain the step plan shown in the
     * plan tab (Codex-style). Input format: one step per line,
     * "pending|running|done: step title".
     */
    private static String updatePlan(String scId, Object planObj) {
        try {
            String plan = planObj == null ? "" : String.valueOf(planObj);
            List<pro.sketchware.activities.chat.ChatPlanManager.Task> tasks = new ArrayList<>();
            for (String line : plan.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int status = pro.sketchware.activities.chat.ChatPlanManager.STATUS_PENDING;
                String title = trimmed;
                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    String statusToken = trimmed.substring(0, colon).trim()
                            .toLowerCase(java.util.Locale.US)
                            .replaceAll("[^a-z_]", "");
                    title = trimmed.substring(colon + 1).trim();
                    if ("done".equals(statusToken) || "completed".equals(statusToken)) {
                        status = pro.sketchware.activities.chat.ChatPlanManager.STATUS_DONE;
                    } else if ("running".equals(statusToken) || "in_progress".equals(statusToken)) {
                        status = pro.sketchware.activities.chat.ChatPlanManager.STATUS_RUNNING;
                    } else if (!"pending".equals(statusToken)) {
                        // No recognised status prefix — treat the whole line as a title.
                        title = trimmed;
                    }
                }
                if (!title.isEmpty()) {
                    tasks.add(new pro.sketchware.activities.chat.ChatPlanManager.Task(title, "", status));
                }
            }
            if (tasks.isEmpty()) {
                return "Erro: plano vazio. Envie um passo por linha no formato 'pending|running|done: título'.";
            }
            pro.sketchware.activities.chat.ChatPlanManager.setModelPlan(scId, tasks);
            return "Plano atualizado com " + tasks.size() + " passo(s).";
        } catch (Exception e) {
            return "Erro ao atualizar plano: " + e.getMessage();
        }
    }

    public static void killAllTerminals() {
        for (Map.Entry<String, Process> entry : activeTerminals.entrySet()) {
            try {
                entry.getValue().destroyForcibly();
            } catch (Exception ignored) {
            }
        }
        activeTerminals.clear();
        terminalOutputs.clear();
        terminalReaders.clear();
    }

    // ============================================
    // VALIDATION HELPERS
    // ============================================

    private static boolean isFalsy(Object value) {
        return value == null || "null".equals(String.valueOf(value)) || "undefined".equals(String.valueOf(value));
    }

    private static String validateStr(String argName, Object value) throws Exception {
        if (value == null) {
            throw new Exception("Invalid LLM output: " + argName + " was null.");
        }
        if (!(value instanceof String)) {
            throw new Exception("Invalid LLM output format: " + argName + " must be a string, but its type is \"" + (value != null ? value.getClass().getSimpleName() : "null") + "\". Full value: " + String.valueOf(value));
        }
        return (String) value;
    }

    private static String validateOptionalStr(String argName, Object value) {
        if (isFalsy(value)) return null;
        try {
            return validateStr(argName, value);
        } catch (Exception e) {
            return null;
        }
    }

    private static int validatePageNum(Object pageNumberUnknown) {
        if (pageNumberUnknown == null) return 1;
        try {
            int parsed = Integer.parseInt(String.valueOf(pageNumberUnknown));
            if (parsed < 1) return 1;
            return parsed;
        } catch (Exception e) {
            return 1;
        }
    }

    private static Integer validateNumber(Object numStr, Integer defaultVal) {
        if (numStr == null) return defaultVal;
        if (numStr instanceof Number) return ((Number) numStr).intValue();
        try {
            return Integer.parseInt(String.valueOf(numStr));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static boolean validateBoolean(Object b, boolean defaultVal) {
        if (b instanceof Boolean) return (Boolean) b;
        if (b instanceof String) {
            if ("true".equals(b)) return true;
            if ("false".equals(b)) return false;
        }
        return defaultVal;
    }

    private static boolean checkIfIsFolder(String uriStr) {
        if (uriStr == null) return false;
        uriStr = uriStr.trim();
        return uriStr.endsWith("/") || uriStr.endsWith("\\");
    }

    // ============================================
    // TOOL CALL RESULTS
    // ============================================

    public static class ToolCallResult {
        public final String result;
        public final boolean hasNextPage;
        public final boolean hasPrevPage;
        public final int itemsRemaining;
        public final int totalFileLen;
        public final int totalNumLines;

        private ToolCallResult(String result) {
            this.result = result;
            this.hasNextPage = false;
            this.hasPrevPage = false;
            this.itemsRemaining = 0;
            this.totalFileLen = 0;
            this.totalNumLines = 0;
        }

        private ToolCallResult(String result, boolean hasNextPage) {
            this.result = result;
            this.hasNextPage = hasNextPage;
            this.hasPrevPage = false;
            this.itemsRemaining = 0;
            this.totalFileLen = 0;
            this.totalNumLines = 0;
        }

        private ToolCallResult(String result, boolean hasNextPage, boolean hasPrevPage, int itemsRemaining) {
            this.result = result;
            this.hasNextPage = hasNextPage;
            this.hasPrevPage = hasPrevPage;
            this.itemsRemaining = itemsRemaining;
            this.totalFileLen = 0;
            this.totalNumLines = 0;
        }

        private ToolCallResult(String result, int totalFileLen, int totalNumLines, boolean hasNextPage) {
            this.result = result;
            this.hasNextPage = hasNextPage;
            this.hasPrevPage = false;
            this.itemsRemaining = 0;
            this.totalFileLen = totalFileLen;
            this.totalNumLines = totalNumLines;
        }
    }

    // ============================================
    // FILE TOOLS
    // ============================================

    public static ToolCallResult readFile(String scId, Object uriObj, Object startLineObj, Object endLineObj, Object pageNumberObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            int pageNumber = validatePageNum(pageNumberObj);
            Integer startLine = validateNumber(startLineObj, null);
            Integer endLine = validateNumber(endLineObj, null);

            if (startLine != null && startLine < 1) startLine = null;
            if (endLine != null && endLine < 1) endLine = null;

            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uriStr);
            if (resolved == null) {
                return new ToolCallResult("File not found or outside project scope: " + uriStr);
            }

            String content = SketchwareFileDecryptor.decryptFile(scId, uriStr);
            if (content == null) {
                return new ToolCallResult("File not found or could not be decrypted: " + uriStr);
            }

            String selected = sliceLines(content, startLine, endLine);
            int totalFileLen = content.length();
            int totalNumLines = content.split("\n", -1).length;

            int fromIdx = MAX_FILE_CHARS_PAGE * (pageNumber - 1);
            int toIdx = MAX_FILE_CHARS_PAGE * pageNumber - 1;
            String fileContents;
            if (fromIdx >= selected.length()) {
                fileContents = "";
            } else {
                fileContents = selected.substring(fromIdx, Math.min(toIdx + 1, selected.length()));
            }
            boolean hasNextPage = (selected.length() - 1) - toIdx >= 1;

            JSONObject resultObj = new JSONObject();
            resultObj.put("fileContents", fileContents);
            resultObj.put("totalFileLen", totalFileLen);
            resultObj.put("totalNumLines", totalNumLines);
            resultObj.put("hasNextPage", hasNextPage);

            return new ToolCallResult(resultObj.toString(), totalFileLen, totalNumLines, hasNextPage);
        } catch (Exception e) {
            return new ToolCallResult("Error reading file: " + e.getMessage());
        }
    }

    public static ToolCallResult lsDir(String scId, Object uriObj, Object pageNumberObj) {
        try {
            String uriStr = validateOptionalStr("uri", uriObj);
            if (uriStr == null) {
                uriStr = "";
            }
            int pageNumber = validatePageNum(pageNumberObj);

            List<File> entries = new ArrayList<>();
            if (uriStr.trim().isEmpty()) {
                for (File root : ProjectPathResolver.getReadableRoots(scId)) {
                    if (root != null && root.exists()) {
                        entries.add(root);
                    }
                }
            } else {
                ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uriStr);
                if (resolved == null) {
                    return new ToolCallResult("[]");
                }

                File folder = resolved.getFile();
                if (!folder.exists()) {
                    return new ToolCallResult("Directory not found: " + uriStr);
                }
                if (!folder.isDirectory()) {
                    return new ToolCallResult("The path is a file, not a directory. Use read_file to view its contents: " + uriStr);
                }

                File[] files = folder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        entries.add(file);
                    }
                }
            }

            if (entries.isEmpty()) {
                return new ToolCallResult("[]");
            }

            int fromIdx = MAX_CHILDREN_URIS_PAGE * (pageNumber - 1);
            int toIdx = MAX_CHILDREN_URIS_PAGE * pageNumber - 1;

            JSONArray resultArray = new JSONArray();
            for (int i = fromIdx; i <= Math.min(toIdx, entries.size() - 1); i++) {
                File f = entries.get(i);
                JSONObject item = new JSONObject();
                item.put("uri", f.getAbsolutePath());
                item.put("name", f.getName());
                item.put("isDirectory", f.isDirectory());
                item.put("isSymbolicLink", false);
                resultArray.put(item);
            }

            boolean hasNextPage = (entries.size() - 1) - toIdx >= 1;
            boolean hasPrevPage = pageNumber > 1;
            int itemsRemaining = Math.max(0, entries.size() - (toIdx + 1));

            JSONObject resultObj = new JSONObject();
            resultObj.put("children", resultArray);
            resultObj.put("hasNextPage", hasNextPage);
            resultObj.put("hasPrevPage", hasPrevPage);
            resultObj.put("itemsRemaining", itemsRemaining);

            return new ToolCallResult(resultObj.toString(), hasNextPage, hasPrevPage, itemsRemaining);
        } catch (Exception e) {
            return new ToolCallResult("[]");
        }
    }

    public static ToolCallResult getDirTree(String scId, Object uriObj) {
        try {
            String uriStr = validateStr("uri", uriObj);

            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uriStr);
            if (resolved == null) {
                return new ToolCallResult("Directory not found: " + uriStr);
            }

            File folder = resolved.getFile();
            if (!folder.exists()) {
                return new ToolCallResult("Directory not found: " + uriStr);
            }
            if (!folder.isDirectory()) {
                return new ToolCallResult("The path is a file, not a directory. Use read_file instead: " + uriStr);
            }

            String tree = DirectoryTreeService.getDirectoryStrTool(folder);
            JSONObject resultObj = new JSONObject();
            resultObj.put("str", tree);
            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult("Error getting directory tree: " + e.getMessage());
        }
    }

    // ============================================
    // SEARCH TOOLS
    // ============================================

    public static ToolCallResult searchPathnamesOnly(String scId, Object queryObj, Object includePatternObj, Object pageNumberObj) {
        try {
            String queryStr = validateStr("query", queryObj);
            int pageNumber = validatePageNum(pageNumberObj);
            String includePattern = validateOptionalStr("include_pattern", includePatternObj);

            List<SemanticFileSearcher.SearchResult> results = SemanticFileSearcher.searchByFilename(queryStr, scId);
            results = filterSearchResults(results, includePattern, null);
            
            int fromIdx = MAX_CHILDREN_URIS_PAGE * (pageNumber - 1);
            int toIdx = MAX_CHILDREN_URIS_PAGE * pageNumber - 1;

            JSONArray urisArray = new JSONArray();
            for (int i = fromIdx; i <= Math.min(toIdx, results.size() - 1); i++) {
                urisArray.put(results.get(i).filePath);
            }

            boolean hasNextPage = (results.size() - 1) - toIdx >= 1;

            JSONObject resultObj = new JSONObject();
            resultObj.put("uris", urisArray);
            resultObj.put("hasNextPage", hasNextPage);

            return new ToolCallResult(resultObj.toString(), hasNextPage);
        } catch (Exception e) {
            return new ToolCallResult("{\"uris\":[],\"hasNextPage\":false}");
        }
    }

    public static ToolCallResult searchForFiles(String scId, Object queryObj, Object isRegexObj, Object searchInFolderObj, Object pageNumberObj) {
        try {
            String queryStr = validateStr("query", queryObj);
            boolean isRegex = validateBoolean(isRegexObj, false);
            int pageNumber = validatePageNum(pageNumberObj);
            String searchInFolder = validateOptionalStr("search_in_folder", searchInFolderObj);

            List<SemanticFileSearcher.SearchResult> results;
            if (isRegex) {
                results = SemanticFileSearcher.searchByContentRegex(queryStr, scId);
            } else {
                results = SemanticFileSearcher.searchByContent(queryStr, scId);
            }
            results = filterSearchResults(results, null, searchInFolder);

            int fromIdx = MAX_CHILDREN_URIS_PAGE * (pageNumber - 1);
            int toIdx = MAX_CHILDREN_URIS_PAGE * pageNumber - 1;

            JSONArray urisArray = new JSONArray();
            for (int i = fromIdx; i <= Math.min(toIdx, results.size() - 1); i++) {
                urisArray.put(results.get(i).filePath);
            }

            boolean hasNextPage = (results.size() - 1) - toIdx >= 1;

            JSONObject resultObj = new JSONObject();
            resultObj.put("uris", urisArray);
            resultObj.put("hasNextPage", hasNextPage);

            return new ToolCallResult(resultObj.toString(), hasNextPage);
        } catch (Exception e) {
            return new ToolCallResult("{\"uris\":[],\"hasNextPage\":false}");
        }
    }

    public static ToolCallResult searchInFile(String scId, Object uriObj, Object queryObj, Object isRegexObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            String query = validateStr("query", queryObj);
            boolean isRegex = validateBoolean(isRegexObj, false);

            String content = SketchwareFileDecryptor.decryptFile(scId, uriStr);
            if (content == null) {
                JSONArray linesArray = new JSONArray();
                JSONObject resultObj = new JSONObject();
                resultObj.put("lines", linesArray);
                return new ToolCallResult(resultObj.toString());
            }

            String[] lines = content.split("\n", -1);
            JSONArray linesArray = new JSONArray();
            Pattern regex = isRegex ? Pattern.compile(query) : null;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                boolean matches = isRegex ? (regex != null && regex.matcher(line).find()) : line.contains(query);
                if (matches) {
                    linesArray.put(i + 1);
                }
            }

            JSONObject resultObj = new JSONObject();
            resultObj.put("lines", linesArray);
            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult("{\"lines\":[]}");
        }
    }

    public static ToolCallResult readLintErrors(String scId, Object uriObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            
            JSONArray lintErrorsArray = new JSONArray();
            List<VoidPortMarkerCheckService.LintError> lintErrors = VoidPortMarkerCheckService.getLintErrors(scId, uriStr);
            for (VoidPortMarkerCheckService.LintError error : lintErrors) {
                JSONObject errorObj = new JSONObject();
                errorObj.put("code", error.code);
                errorObj.put("message", error.message);
                errorObj.put("startLineNumber", error.startLineNumber);
                errorObj.put("endLineNumber", error.endLineNumber);
                lintErrorsArray.put(errorObj);
            }

            JSONObject resultObj = new JSONObject();
            resultObj.put("lintErrors", lintErrorsArray);
            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult("{\"lintErrors\":[]}");
        }
    }

    // ============================================
    // EDIT TOOLS
    // ============================================

    public static ToolCallResult rewriteFile(String scId, Object uriObj, Object newContentObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            String newContent = validateStr("new_content", newContentObj);

            String oldContent = SketchwareFileDecryptor.decryptFile(scId, uriStr);
            boolean existedBefore = oldContent != null;
            if (oldContent == null) {
                oldContent = "";
            }

            if (!SketchwareFileEncryptor.encryptAndSaveFile(scId, uriStr, newContent)) {
                return new ToolCallResult("Cannot write to file: " + uriStr);
            }

            FileChangeTracker.trackChange(scId, uriStr, oldContent, newContent, existedBefore);

            // Get lint errors after write
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            List<VoidPortMarkerCheckService.LintError> lintErrors = VoidPortMarkerCheckService.getLintErrors(scId, uriStr);
            JSONArray lintErrorsArray = new JSONArray();
            for (VoidPortMarkerCheckService.LintError error : lintErrors) {
                JSONObject errorObj = new JSONObject();
                errorObj.put("code", error.code);
                errorObj.put("message", error.message);
                errorObj.put("startLineNumber", error.startLineNumber);
                errorObj.put("endLineNumber", error.endLineNumber);
                lintErrorsArray.put(errorObj);
            }

            JSONObject resultObj = new JSONObject();
            resultObj.put("lintErrors", lintErrorsArray);
            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult("Error rewriting file: " + e.getMessage());
        }
    }

    public static ToolCallResult editFile(String scId, Object uriObj, Object searchReplaceBlocksObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            String searchReplaceBlocks = validateStr("search_replace_blocks", searchReplaceBlocksObj);

            String content = SketchwareFileDecryptor.decryptFile(scId, uriStr);
            if (content == null) {
                return new ToolCallResult("File not found or could not be decrypted: " + uriStr);
            }

            SearchReplaceResult replaceResult = applySearchReplaceBlocks(content, searchReplaceBlocks);
            if (replaceResult.blockCount == 0) {
                return new ToolCallResult("Invalid SEARCH/REPLACE blocks: no valid blocks found.");
            }
            if (replaceResult.appliedCount != replaceResult.blockCount) {
                return new ToolCallResult("Could not apply edit_file: one or more ORIGINAL blocks did not match the file exactly.");
            }
            String newContent = replaceResult.content;

            if (!SketchwareFileEncryptor.encryptAndSaveFile(scId, uriStr, newContent)) {
                return new ToolCallResult("Cannot write to file: " + uriStr);
            }

            FileChangeTracker.trackChange(scId, uriStr, content, newContent);

            // Get lint errors after edit
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            List<VoidPortMarkerCheckService.LintError> lintErrors = VoidPortMarkerCheckService.getLintErrors(scId, uriStr);
            JSONArray lintErrorsArray = new JSONArray();
            for (VoidPortMarkerCheckService.LintError error : lintErrors) {
                JSONObject errorObj = new JSONObject();
                errorObj.put("code", error.code);
                errorObj.put("message", error.message);
                errorObj.put("startLineNumber", error.startLineNumber);
                errorObj.put("endLineNumber", error.endLineNumber);
                lintErrorsArray.put(errorObj);
            }

            JSONObject resultObj = new JSONObject();
            resultObj.put("lintErrors", lintErrorsArray);
            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult("Error editing file: " + e.getMessage());
        }
    }

    public static ToolCallResult createFileOrFolder(String scId, Object uriObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            boolean isFolder = checkIfIsFolder(uriStr);

            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForWrite(scId, uriStr);
            if (resolved == null) {
                return new ToolCallResult("Cannot create: " + uriStr);
            }

            File file = resolved.getFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (isFolder) {
                if (!file.exists()) {
                    file.mkdirs();
                }
            } else {
                if (!file.exists()) {
                    file.createNewFile();
                    // existedBefore=false: rejecting this change must delete the file.
                    FileChangeTracker.trackChange(scId, uriStr, "", "", false);
                }
            }

            return new ToolCallResult("{}");
        } catch (Exception e) {
            return new ToolCallResult("Error creating file/folder: " + e.getMessage());
        }
    }

    public static ToolCallResult deleteFileOrFolder(String scId, Object uriObj, Object isRecursiveObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            boolean isRecursive = validateBoolean(isRecursiveObj, false);

            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uriStr);
            if (resolved == null) {
                return new ToolCallResult("File/folder not found: " + uriStr);
            }

            File file = resolved.getFile();
            if (!file.exists()) {
                return new ToolCallResult("File/folder not found: " + uriStr);
            }

            if (file.isDirectory() && !isRecursive && file.list().length > 0) {
                return new ToolCallResult("Cannot delete non-empty directory without is_recursive=true");
            }

            String oldContent = "";
            boolean isFile = file.isFile();
            if (isFile) {
                oldContent = SketchwareFileDecryptor.decryptFile(scId, uriStr);
                if (oldContent == null) oldContent = "";
            }

            deleteRecursive(file);

            if (isFile) {
                FileChangeTracker.trackChange(scId, uriStr, oldContent, "");
            }

            return new ToolCallResult("{}");
        } catch (Exception e) {
            return new ToolCallResult("Error deleting file/folder: " + e.getMessage());
        }
    }

    // ============================================
    // TERMINAL TOOLS
    // ============================================

    public static ToolCallResult runCommand(String scId, Object commandObj, Object cwdObj, Object terminalIdObj) {
        return runCommand(scId, commandObj, cwdObj, terminalIdObj, null);
    }

    public static ToolCallResult runCommand(String scId, Object commandObj, Object cwdObj, Object terminalIdObj, Object timeoutObj) {
        try {
            String command = validateStr("command", commandObj);
            String cwd = validateOptionalStr("cwd", cwdObj);
            int timeoutSeconds = MAX_TERMINAL_INACTIVE_TIME_SECONDS;
            if (!isFalsy(timeoutObj)) {
                try {
                    timeoutSeconds = (int) Double.parseDouble(String.valueOf(timeoutObj));
                } catch (NumberFormatException ignored) {
                }
            }
            // Clamp: at least 5 s, at most 5 min (Gradle/aapt builds need headroom).
            timeoutSeconds = Math.max(5, Math.min(timeoutSeconds, 300));
            String terminalId = terminalIdObj != null ? String.valueOf(terminalIdObj) : java.util.UUID.randomUUID().toString();

            if (command.trim().isEmpty()) {
                return new ToolCallResult(SketchApplication.getContext().getString(R.string.chat_tool_empty_command));
            }

            if (commandLooksLikeFileMutation(command)) {
                return new ToolCallResult("Blocked: run_command cannot create, edit, overwrite, move or delete files. " +
                        "Use create_file_or_folder, delete_file_or_folder, edit_file or rewrite_file instead.");
            }

            File workingDir = resolveTerminalWorkingDir(scId, cwd);
            String shell = androidShellPath();

            if (!workingDir.exists()) {
                return new ToolCallResult(SketchApplication.getContext().getString(
                        R.string.chat_tool_working_directory_missing, workingDir.getAbsolutePath()));
            }

            ProcessBuilder pb = new ProcessBuilder(shell, "-c", command);
            configureAndroidProcess(pb, workingDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            activeTerminals.put(terminalId, process);
            terminalOutputs.put(terminalId, new StringBuilder());
            terminalReaders.put(terminalId, new BufferedReader(new InputStreamReader(process.getInputStream())));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                String output = drainTerminalOutput(terminalId, false);
                process.destroyForcibly();
                activeTerminals.remove(terminalId);
                terminalOutputs.remove(terminalId);
                terminalReaders.remove(terminalId);

                JSONObject resolveReason = new JSONObject();
                resolveReason.put("type", "timeout");
                JSONObject resultObj = new JSONObject();
                resultObj.put("result", trimTerminalOutput(output));
                resultObj.put("resolveReason", resolveReason);
                resultObj.put("terminal", androidTerminalInfo(shell, workingDir));
                return new ToolCallResult(resultObj.toString());
            }

            String output = drainTerminalOutput(terminalId, true);

            int exitCode = process.exitValue();
            activeTerminals.remove(terminalId);
            terminalOutputs.remove(terminalId);
            terminalReaders.remove(terminalId);
            
            String normalizedOutput = trimTerminalOutput(output);

            JSONObject resultObj = new JSONObject();
            JSONObject resolveReason = new JSONObject();
            resolveReason.put("type", "done");
            resolveReason.put("exitCode", exitCode);
            resultObj.put("result", normalizedOutput);
            resultObj.put("resolveReason", resolveReason);
            resultObj.put("terminal", androidTerminalInfo(shell, workingDir));

            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult(SketchApplication.getContext().getString(
                    R.string.chat_tool_command_failed, e.getMessage()));
        }
    }

    public static ToolCallResult openPersistentTerminal(String scId, Object cwdObj) {
        try {
            String cwd = validateOptionalStr("cwd", cwdObj);
            String terminalId = java.util.UUID.randomUUID().toString();

            File workingDir = resolveTerminalWorkingDir(scId, cwd);
            String shell = androidShellPath();

            ProcessBuilder pb = new ProcessBuilder(shell, "-i");
            configureAndroidProcess(pb, workingDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            activeTerminals.put(terminalId, process);
            terminalOutputs.put(terminalId, new StringBuilder());
            terminalReaders.put(terminalId, new BufferedReader(new InputStreamReader(process.getInputStream())));

            JSONObject resultObj = new JSONObject();
            resultObj.put("persistentTerminalId", terminalId);
            resultObj.put("terminal", androidTerminalInfo(shell, workingDir));
            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult("Erro ao abrir terminal persistente: " + e.getMessage());
        }
    }

    public static ToolCallResult runPersistentCommand(String scId, Object commandObj, Object persistentTerminalIdObj) {
        try {
            String command = validateStr("command", commandObj);
            String terminalId = validateStr("persistent_terminal_id", persistentTerminalIdObj);

            if (commandLooksLikeFileMutation(command)) {
                return new ToolCallResult("Blocked: run_persistent_command cannot create, edit, overwrite, move or delete files. " +
                        "Use create_file_or_folder, delete_file_or_folder, edit_file or rewrite_file instead.");
            }

            Process process = activeTerminals.get(terminalId);
            if (process == null) {
                return new ToolCallResult("Terminal não encontrado: " + terminalId);
            }

            java.io.OutputStream os = process.getOutputStream();
            os.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();

            // Wait for command to complete (with timeout)
            Thread.sleep(TimeUnit.SECONDS.toMillis(MAX_TERMINAL_BG_COMMAND_TIME_SECONDS));

            String result = trimTerminalOutput(drainTerminalOutput(terminalId, false));
            
            StringBuilder output = terminalOutputs.get(terminalId);
            if (output != null) {
                output.setLength(0);
            }

            JSONObject resultObj = new JSONObject();
            JSONObject resolveReason = new JSONObject();
            resolveReason.put("type", "timeout");
            resultObj.put("result", result);
            resultObj.put("resolveReason", resolveReason);
            resultObj.put("terminal", new JSONObject()
                    .put("platform", "android")
                    .put("persistentTerminalId", terminalId));
            return new ToolCallResult(resultObj.toString());
        } catch (Exception e) {
            return new ToolCallResult("Erro ao executar comando persistente: " + e.getMessage());
        }
    }

    public static ToolCallResult killPersistentTerminal(String scId, Object persistentTerminalIdObj) {
        try {
            String terminalId = validateStr("persistent_terminal_id", persistentTerminalIdObj);

            Process process = activeTerminals.remove(terminalId);
            terminalOutputs.remove(terminalId);
            terminalReaders.remove(terminalId);
            
            if (process != null) {
                process.destroyForcibly();
            }

            return new ToolCallResult("{}");
        } catch (Exception e) {
            return new ToolCallResult("Erro ao fechar terminal: " + e.getMessage());
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private static File resolveTerminalWorkingDir(String scId, String cwd) throws IOException {
        // Scope the terminal to the PROJECT folder, not the shared .sketchware root.
        File projectRoot = ProjectPathResolver.getTerminalWorkingRoot(scId);
        File workingDir;
        if (cwd != null && !cwd.trim().isEmpty()) {
            // Previously any cwd was accepted verbatim (new File(cwd)), letting a
            // command run ANYWHERE on the device — the "run_command left the
            // project folder" bug. Now the cwd is resolved relative to the
            // project root and must stay inside it.
            String trimmed = cwd.trim();
            File requested = new File(trimmed);
            if (!requested.isAbsolute()) {
                requested = new File(projectRoot, trimmed);
            }
            try {
                String canonical = requested.getCanonicalPath();
                String rootCanonical = projectRoot.getCanonicalPath();
                if (!canonical.equals(rootCanonical) && !canonical.startsWith(rootCanonical + File.separator)) {
                    ChatToolLog.w("terminal", "cwd outside project, forcing root. requested=\"" + cwd
                            + "\" root=" + rootCanonical);
                    requested = projectRoot;
                }
            } catch (IOException e) {
                requested = projectRoot;
            }
            workingDir = requested;
        } else {
            workingDir = projectRoot;
        }
        if (workingDir == null) {
            throw new IOException("Android terminal working directory could not be resolved.");
        }
        if (!workingDir.exists()) {
            throw new IOException("Android terminal working directory not found: " + workingDir.getAbsolutePath());
        }
        if (!workingDir.isDirectory()) {
            throw new IOException("Android terminal cwd is not a directory: " + workingDir.getAbsolutePath());
        }
        ChatToolLog.d("terminal", "cwd=" + workingDir.getAbsolutePath());
        return workingDir;
    }

    private static String androidShellPath() {
        File systemShell = new File("/system/bin/sh");
        if (systemShell.exists() && systemShell.canExecute()) {
            return systemShell.getAbsolutePath();
        }
        File vendorShell = new File("/vendor/bin/sh");
        if (vendorShell.exists() && vendorShell.canExecute()) {
            return vendorShell.getAbsolutePath();
        }
        return "sh";
    }

    private static void configureAndroidProcess(ProcessBuilder processBuilder, File workingDir) {
        processBuilder.directory(workingDir);
        Map<String, String> env = processBuilder.environment();
        env.put("PWD", workingDir.getAbsolutePath());
        env.put("HOME", workingDir.getAbsolutePath());
        env.put("TERM", "xterm-256color");
        env.put("ANDROID_TERMINAL", "1");
    }

    private static JSONObject androidTerminalInfo(String shell, File workingDir) throws Exception {
        JSONObject info = new JSONObject();
        info.put("platform", "android");
        info.put("shell", shell);
        info.put("cwd", workingDir == null ? "" : workingDir.getAbsolutePath());
        info.put("sdk", Build.VERSION.SDK_INT);
        info.put("device", (Build.MANUFACTURER + " " + Build.MODEL).trim());
        return info;
    }

    private static String sliceLines(String content, Integer startLine, Integer endLine) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (startLine == null && endLine == null) {
            return content;
        }
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int from = Math.max(1, startLine == null ? 1 : startLine);
        int to = endLine == null ? lines.length : Math.min(endLine, lines.length);
        if (to < from) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = from; i <= to; i++) {
            builder.append(lines[i - 1]);
            if (i < to) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static String trimTerminalOutput(String output) {
        String normalized = output == null || output.trim().isEmpty() ? "(sem saida)" : output.trim();
        return normalized.length() > 100000 ? normalized.substring(0, 100000) : normalized;
    }

    private static String drainTerminalOutput(String terminalId, boolean readUntilEnd) throws IOException {
        BufferedReader reader = terminalReaders.get(terminalId);
        StringBuilder output = terminalOutputs.get(terminalId);
        if (reader == null || output == null) {
            return "";
        }

        long start = System.currentTimeMillis();
        while (readUntilEnd || reader.ready() || (System.currentTimeMillis() - start < 200)) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line == null) break;
                output.append(line).append("\n");
                start = System.currentTimeMillis(); // Reset timer if we are getting data
            } else {
                if (readUntilEnd) {
                   // If we must read until end, wait a bit for more data
                   try { Thread.sleep(50); } catch (Exception ignored) {}
                } else {
                   break; 
                }
            }
            if (readUntilEnd && !activeTerminals.containsKey(terminalId)) break; // Process died
        }
        return output.toString();
    }

    private static List<SemanticFileSearcher.SearchResult> filterSearchResults(
            List<SemanticFileSearcher.SearchResult> results,
            String includePattern,
            String searchInFolder) {
        if ((includePattern == null || includePattern.trim().isEmpty())
                && (searchInFolder == null || searchInFolder.trim().isEmpty())) {
            return results;
        }

        List<SemanticFileSearcher.SearchResult> filtered = new ArrayList<>();
        String normalizedFolder = normalizePathFilter(searchInFolder);
        Pattern includeRegex = compileGlobPattern(includePattern);
        for (SemanticFileSearcher.SearchResult result : results) {
            String normalizedPath = normalizePathFilter(result.filePath);
            if (normalizedFolder != null && !normalizedPath.startsWith(normalizedFolder)) {
                continue;
            }
            if (includeRegex != null && !includeRegex.matcher(normalizedPath).find()) {
                continue;
            }
            filtered.add(result);
        }
        return filtered;
    }

    private static String normalizePathFilter(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim().toLowerCase();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static Pattern compileGlobPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return null;
        }
        String normalized = normalizePathFilter(pattern);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static final class SearchReplaceResult {
        final String content;
        final int blockCount;
        final int appliedCount;

        SearchReplaceResult(String content, int blockCount, int appliedCount) {
            this.content = content;
            this.blockCount = blockCount;
            this.appliedCount = appliedCount;
        }
    }

    private static SearchReplaceResult applySearchReplaceBlocks(String content, String searchReplaceBlocks) {
        int blockCount = 0;

        // Pattern handles variations in whitespace around markers
        Pattern pattern = Pattern.compile(
            "<<<<<<< ORIGINAL[\\s\\t]*\\r?\\n(.*?)\\r?\\n[\\s\\t]*=======[\\s\\t]*\\r?\\n(.*?)\\r?\\n[\\s\\t]*>>>>>>> UPDATED",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(searchReplaceBlocks);

        List<String[]> blocks = new ArrayList<>();
        while (matcher.find()) {
            blockCount++;
            blocks.add(new String[]{matcher.group(1), matcher.group(2)});
        }

        if (blockCount == 0) {
            return new SearchReplaceResult(content, 0, 0);
        }

        // Normalize the file content to LF so that blocks produced with CRLF
        // line-endings (common from Windows / some LLM outputs) still match.
        String normalizedContent = content.replace("\r\n", "\n").replace('\r', '\n');

        String result = normalizedContent;
        int appliedCount = 0;

        for (String[] block : blocks) {
            // Normalize the ORIGINAL block the same way before searching.
            String search = block[0].replace("\r\n", "\n").replace('\r', '\n');
            String replace = block[1].replace("\r\n", "\n").replace('\r', '\n');

            if (result.contains(search)) {
                result = result.replace(search, replace);
                appliedCount++;
            } else {
                // Fuzzy fallback: strip trailing whitespace from every line and retry.
                // This handles the common case where the LLM adds/removes trailing
                // spaces compared to the real file content.
                String searchTrimmed = trimLinesTrailing(search);
                String resultTrimmed = trimLinesTrailing(result);
                int idx = resultTrimmed.indexOf(searchTrimmed);
                if (idx >= 0) {
                    // Find the corresponding range in the un-trimmed result and splice.
                    String before = result.substring(0, idx);
                    String after = result.substring(idx + searchTrimmed.length());
                    result = before + replace + after;
                    appliedCount++;
                } else {
                    // At least one block could not be applied; abort to avoid partial edits.
                    return new SearchReplaceResult(content, blockCount, 0);
                }
            }
        }

        return new SearchReplaceResult(result, blockCount, appliedCount);
    }

    /**
     * Returns a copy of {@code text} where every line has its trailing
     * whitespace (spaces and tabs) stripped. Used as a fuzzy fallback in
     * {@link #applySearchReplaceBlocks} so that insignificant trailing-space
     * differences between the LLM output and the real file do not cause edits
     * to fail silently.
     */
    private static String trimLinesTrailing(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            // Strip trailing spaces/tabs
            int end = lines[i].length();
            while (end > 0 && (lines[i].charAt(end - 1) == ' ' || lines[i].charAt(end - 1) == '\t')) {
                end--;
            }
            sb.append(lines[i], 0, end);
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean commandLooksLikeFileMutation(String command) {
        if (command == null) return false;
        String c = command.trim();
        return c.contains(">") ||
                c.contains(">>") ||
                c.matches("(?s).*\\btee\\b.*") ||
                c.matches("(?s).*\\bsed\\s+-i\\b.*") ||
                c.matches("(?s).*\\brm\\b.*") ||
                c.matches("(?s).*\\bmv\\b.*") ||
                c.matches("(?s).*\\bcp\\b.*") ||
                c.matches("(?s).*\\btouch\\b.*") ||
                c.matches("(?s).*\\bmkdir\\b.*");
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    // ============================================
    // TOOL REGISTRY FOR MCP
    // ============================================

    public static JSONArray getAllToolsAsMCP() {
        JSONArray array = new JSONArray();
        if (useVoidToolDescriptions()) {
            array.put(createToolMCP("read_file",
                    "Returns full contents of a given file.",
                    new String[]{"uri"}, new String[]{"start_line", "end_line", "page_number"}));
            array.put(createToolMCP("ls_dir",
                    "Lists all files and folders in the given URI.",
                    new String[]{}, new String[]{"uri", "page_number"}));
            array.put(createToolMCP("get_dir_tree",
                    "This is a very effective way to learn about the user's codebase. Returns a tree diagram of all the files and folders in the given folder.",
                    new String[]{"uri"}, null));
            array.put(createToolMCP("search_pathnames_only",
                    "Returns all pathnames that match a given query (searches ONLY file names). You should use this when looking for a file with a specific name or path.",
                    new String[]{"query"}, new String[]{"include_pattern", "page_number"}));
            array.put(createToolMCP("search_for_files",
                    "Returns a list of file names whose content matches the given query. The query can be any substring or regex.",
                    new String[]{"query"}, new String[]{"search_in_folder", "is_regex", "page_number"}));
            array.put(createToolMCP("search_in_file",
                    "Returns an array of all the start line numbers where the content appears in the file.",
                    new String[]{"uri", "query"}, new String[]{"is_regex"}));
            array.put(createToolMCP("read_lint_errors",
                    "Use this tool to view all the lint errors on a file.",
                    new String[]{"uri"}, null));
            array.put(createToolMCP("create_file_or_folder",
                    "Create a file or folder at the given path. To create a folder, the path MUST end with a trailing slash.",
                    new String[]{"uri"}, null));
            array.put(createToolMCP("delete_file_or_folder",
                    "Delete a file or folder at the given path.",
                    new String[]{"uri"}, new String[]{"is_recursive"}));
            array.put(createToolMCP("edit_file",
                    "Edit the contents of a file. You must provide the file's URI as well as a SINGLE string of SEARCH/REPLACE block(s) that will be used to apply the edit.",
                    new String[]{"uri", "search_replace_blocks"}, null));
            array.put(createToolMCP("rewrite_file",
                    "Edits a file, deleting all the old contents and replacing them with your new contents. Use this tool if you want to edit a file you just created.",
                    new String[]{"uri", "new_content"}, null));
            array.put(createToolMCP("run_command",
                    "Runs a terminal command and waits for the result (default timeout 60s; set timeout_seconds for longer builds, max 300). You can use this tool to run any command: sed, grep, etc. Do not edit any files with this tool; use edit_file instead. When working with git and other tools that open an editor (e.g. git diff), you should pipe to cat to get all results and not get stuck in vim.",
                    new String[]{"command"}, new String[]{"cwd", "timeout_seconds"}));
            array.put(createToolMCP("run_persistent_command",
                    "Runs a terminal command in the persistent terminal that you created with open_persistent_terminal (results after 5 are returned, and command continues running in background). You can use this tool to run any command: sed, grep, etc. Do not edit any files with this tool; use edit_file instead. When working with git and other tools that open an editor (e.g. git diff), you should pipe to cat to get all results and not get stuck in vim.",
                    new String[]{"command", "persistent_terminal_id"}, null));
            array.put(createToolMCP("open_persistent_terminal",
                    "Use this tool when you want to run a terminal command indefinitely, like a dev server (eg `npm run dev`), a background listener, etc. Opens a new terminal in the user's environment which will not awaited for or killed.",
                    new String[]{}, new String[]{"cwd"}));
            array.put(createToolMCP("kill_persistent_terminal",
                    "Interrupts and closes a persistent terminal that you opened with open_persistent_terminal.",
                    new String[]{"persistent_terminal_id"}, null));
            return array;
        }

        // File tools
        array.put(createToolMCP("read_file",
            "Returns full contents of a given file.",
            new String[]{"uri"}, new String[]{"start_line", "end_line", "page_number"}));

        array.put(createToolMCP("ls_dir",
            "Lists all files and folders in the given URI.",
            new String[]{}, new String[]{"uri", "page_number"}));

        array.put(createToolMCP("get_dir_tree",
            "This is a very effective way to learn about the user's codebase. Returns a tree diagram of all the files and folders in the given folder.",
            new String[]{"uri"}, null));

        // Search tools
        array.put(createToolMCP("search_pathnames_only",
            "Returns all pathnames that match a given query (searches ONLY file names). You should use this when looking for a file with a specific name or path.",
            new String[]{"query"}, new String[]{"include_pattern", "page_number"}));

        array.put(createToolMCP("search_for_files",
            "Returns a list of file names whose content matches the given query. The query can be any substring or regex.",
            new String[]{"query"}, new String[]{"search_in_folder", "is_regex", "page_number"}));

        array.put(createToolMCP("search_in_file",
            "Returns an array of all the start line numbers where the content appears in the file.",
            new String[]{"uri", "query"}, new String[]{"is_regex"}));

        array.put(createToolMCP("read_lint_errors",
            "Use this tool to view all the lint errors on a file.",
            new String[]{"uri"}, null));

        // Edit tools
        array.put(createToolMCP("create_file_or_folder",
            "Create a file or folder at the given path. To create a folder, the path MUST end with a trailing slash.",
            new String[]{"uri"}, null));

        array.put(createToolMCP("delete_file_or_folder",
            "Delete a file or folder at the given path.",
            new String[]{"uri"}, new String[]{"is_recursive"}));

        array.put(createToolMCP("edit_file",
            "Edit the contents of a file. You must provide the file's URI as well as a SINGLE string of SEARCH/REPLACE block(s) that will be used to apply the edit.",
            new String[]{"uri", "search_replace_blocks"}, null));

        array.put(createToolMCP("rewrite_file",
            "Edits a file, deleting all the old contents and replacing them with your new contents. Use this tool if you want to edit a file you just created.",
            new String[]{"uri", "new_content"}, null));

        // Terminal tools
        array.put(createToolMCP("run_command",
            "Runs a terminal command and waits for the result (times out after 30s of inactivity). You can use this tool to run any command: sed, grep, etc. Do not edit any files with this tool; use edit_file instead. When working with git and other tools that open an editor (e.g. git diff), you should pipe to cat to get all results and not get stuck in vim.",
            new String[]{"command"}, new String[]{"cwd"}));

        return array;
    }

    private static JSONObject createToolMCP(String name, String description, String[] requiredParams, String[] optionalParams) {
        try {
            JSONObject toolObj = new JSONObject();
            JSONObject function = new JSONObject();
            
            function.put("name", name);
            function.put("description", description);
            
            JSONObject params = new JSONObject();
            params.put("type", "object");
            
            JSONObject properties = new JSONObject();
            for (String param : requiredParams) {
                JSONObject prop = new JSONObject();
                prop.put("type", "string");
                prop.put("description", toolParamDescription(name, param));
                properties.put(param, prop);
            }
            if (optionalParams != null) {
                for (String param : optionalParams) {
                    JSONObject prop = new JSONObject();
                    prop.put("type", "string");
                    prop.put("description", toolParamDescription(name, param));
                    properties.put(param, prop);
                }
            }
            
            params.put("properties", properties);
            
            JSONArray required = new JSONArray();
            for (String param : requiredParams) {
                required.put(param);
            }
            params.put("required", required);
            
            function.put("parameters", params);
            toolObj.put("type", "function");
            toolObj.put("function", function);
            
            return toolObj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static boolean useVoidToolDescriptions() {
        return true;
    }

    private static String toolParamDescription(String toolName, String paramName) {
        if ("uri".equals(paramName)) {
            if ("ls_dir".equals(toolName)) {
                return "Optional. The FULL path to the folder. Leave this as empty or \"\" to search all folders.";
            }
            if ("get_dir_tree".equals(toolName)) {
                return "The FULL path to the folder.";
            }
            if ("create_file_or_folder".equals(toolName) || "delete_file_or_folder".equals(toolName)) {
                return "The FULL path to the file or folder.";
            }
            return "The FULL path to the file.";
        }
        if ("start_line".equals(paramName)) {
            return "Optional. Do NOT fill this field in unless you were specifically given exact line numbers to search. Defaults to the beginning of the file.";
        }
        if ("end_line".equals(paramName)) {
            return "Optional. Do NOT fill this field in unless you were specifically given exact line numbers to search. Defaults to the end of the file.";
        }
        if ("page_number".equals(paramName)) {
            return "Optional. The page number of the result. Default is 1.";
        }
        if ("timeout_seconds".equals(paramName)) {
            return "Optional. Max seconds to wait for the command (default 60, max 300). Use higher values for builds.";
        }
        if ("plan".equals(paramName)) {
            return "The full plan, one step per line: 'pending|running|done: step title'.";
        }
        if ("query".equals(paramName)) {
            return "Your query for the search.";
        }
        if ("include_pattern".equals(paramName)) {
            return "Optional. Only fill this in if you need to limit your search because there were too many results.";
        }
        if ("search_in_folder".equals(paramName)) {
            return "Optional. Leave as blank by default. ONLY fill this in if your previous search with the same query was truncated. Searches descendants of this folder only.";
        }
        if ("is_regex".equals(paramName)) {
            return "Optional. Default is false. Whether the query is a regex.";
        }
        if ("is_recursive".equals(paramName)) {
            return "Optional. Return true to delete recursively.";
        }
        if ("search_replace_blocks".equals(paramName)) {
            return PromptConstants.SEARCH_REPLACE_BLOCKS_TOOL_DESCRIPTION;
        }
        if ("new_content".equals(paramName)) {
            return "The new contents of the file. Must be a string.";
        }
        if ("command".equals(paramName)) {
            return "The terminal command to run.";
        }
        if ("cwd".equals(paramName)) {
            return "Optional. The directory in which to run the command. Defaults to the first workspace folder.";
        }
        if ("persistent_terminal_id".equals(paramName)) {
            return "The ID of the terminal created using open_persistent_terminal.";
        }
        return "";
    }

    // ============================================
    // MAIN TOOL EXECUTOR
    // ============================================

    public static String executeTool(String scId, String toolName, JSONObject args) {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        pro.sketchware.util.ChatToolLog.d("tool", "▶ " + toolName + " sc=" + scId
                + " args=" + pro.sketchware.util.ChatToolLog.preview(args == null ? "{}" : args.toString(), 300));
        try {
            String out = executeToolInner(scId, toolName, args);
            long ms = android.os.SystemClock.elapsedRealtime() - startedAt;
            boolean looksError = out != null && (out.startsWith("Erro") || out.startsWith("Error")
                    || out.startsWith("Cannot") || out.startsWith("File not found") || out.startsWith("Blocked"));
            pro.sketchware.util.ChatToolLog.d("tool", (looksError ? "✖ " : "✔ ") + toolName
                    + " (" + ms + "ms) -> " + pro.sketchware.util.ChatToolLog.preview(out, 200));
            return out;
        } catch (Exception e) {
            pro.sketchware.util.ChatToolLog.e("tool", "crash in " + toolName, e);
            return "Erro ao executar ferramenta " + toolName + ": " + e.getMessage();
        }
    }

    private static String executeToolInner(String scId, String toolName, JSONObject args) {
        try {
            ToolCallResult result;

            switch (toolName) {
                case "read_file":
                    result = readFile(scId, 
                        args.opt("uri"),
                        args.opt("start_line") != null ? args.opt("start_line") : args.opt("startLine"),
                        args.opt("end_line") != null ? args.opt("end_line") : args.opt("endLine"),
                        args.opt("page_number") != null ? args.opt("page_number") : args.opt("pageNumber"));
                    break;
                    
                case "ls_dir":
                    result = lsDir(scId,
                        args.opt("uri"),
                        args.opt("page_number") != null ? args.opt("page_number") : args.opt("pageNumber"));
                    break;
                    
                case "get_dir_tree":
                    result = getDirTree(scId, args.opt("uri"));
                    break;
                    
                case "search_pathnames_only":
                    Object includePattern = args.opt("include_pattern") != null ? args.opt("include_pattern") : args.opt("includePattern");
                    if (includePattern == null) {
                        includePattern = args.opt("search_in_folder"); // Fallback
                    }
                    result = searchPathnamesOnly(scId,
                        args.opt("query"),
                        includePattern,
                        args.opt("page_number") != null ? args.opt("page_number") : args.opt("pageNumber"));
                    break;
                    
                case "search_for_files":
                    result = searchForFiles(scId,
                        args.opt("query"),
                        args.opt("is_regex") != null ? args.opt("is_regex") : args.opt("isRegex"),
                        args.opt("search_in_folder") != null ? args.opt("search_in_folder") : args.opt("searchInFolder"),
                        args.opt("page_number") != null ? args.opt("page_number") : args.opt("pageNumber"));
                    break;
                    
                case "search_in_file":
                    result = searchInFile(scId,
                        args.opt("uri"),
                        args.opt("query"),
                        args.opt("is_regex") != null ? args.opt("is_regex") : args.opt("isRegex"));
                    break;
                    
                case "read_lint_errors":
                    result = readLintErrors(scId, args.opt("uri"));
                    break;
                    
                case "rewrite_file":
                    result = rewriteFile(scId,
                        args.opt("uri"),
                        args.opt("new_content") != null ? args.opt("new_content") : args.opt("newContent"));
                    break;
                    
                case "edit_file":
                    result = editFile(scId,
                        args.opt("uri"),
                        args.opt("search_replace_blocks") != null ? args.opt("search_replace_blocks") : args.opt("searchReplaceBlocks"));
                    break;
                    
                case "create_file_or_folder":
                    result = createFileOrFolder(scId, 
                        args.opt("uri"));
                    break;
                    
                case "delete_file_or_folder":
                    result = deleteFileOrFolder(scId,
                        args.opt("uri"),
                        args.opt("is_recursive") != null ? args.opt("is_recursive") : args.opt("isRecursive"));
                    break;
                    
                case "run_command":
                    result = runCommand(scId,
                        args.opt("command"),
                        args.opt("cwd"),
                        args.opt("terminal_id") != null ? args.opt("terminal_id") : args.opt("terminalId"),
                        args.opt("timeout_seconds") != null ? args.opt("timeout_seconds") : args.opt("timeoutSeconds"));
                    break;
                    
                default:
                    if ("get_file".equals(toolName)) {
                        return SketchApplication.getContext().getString(R.string.chat_tool_get_file_alias_error);
                    }
                    return SketchApplication.getContext().getString(R.string.chat_tool_unknown_error, toolName);
            }
            
            String technicalResult = result.result;
            
            // If the result is an error message (doesn't look like JSON), return it as is
            if (technicalResult.startsWith("Error") || technicalResult.startsWith("Erro") || technicalResult.startsWith("Cannot") || technicalResult.startsWith("File not found")) {
                return technicalResult;
            }

            return getStringOfResult(toolName, args, result);
            
        } catch (Exception e) {
            return "Erro ao executar ferramenta " + toolName + ": " + e.getMessage();
        }
    }

    private static String getStringOfResult(String toolName, JSONObject args, ToolCallResult result) {
        try {
            JSONObject resObj = new JSONObject(result.result);
            
            switch (toolName) {
                case "read_file": {
                    String fsPath = args.optString("uri");
                    String fileContents = resObj.optString("fileContents");
                    boolean hasNextPage = resObj.optBoolean("hasNextPage");
                    int totalNumLines = resObj.optInt("totalNumLines");
                    int totalFileLen = resObj.optInt("totalFileLen");
                    
                    String nextPageStr = hasNextPage ? "\n\n(more on next page...)" : "";
                    String truncationInfo = hasNextPage ? 
                        String.format("\nMore info because truncated: this file has %d lines, or %d characters.", totalNumLines, totalFileLen) : "";
                    
                    return String.format("%s\n```\n%s\n```%s%s", fsPath, fileContents, nextPageStr, truncationInfo);
                }

                case "ls_dir": {
                    return stringifyDirectoryTree1Deep(args, resObj);
                }

                case "get_dir_tree": {
                    return resObj.optString("str");
                }

                case "search_pathnames_only":
                case "search_for_files": {
                    JSONArray uris = resObj.optJSONArray("uris");
                    StringBuilder sb = new StringBuilder();
                    if (uris != null) {
                        for (int i = 0; i < uris.length(); i++) {
                            sb.append(uris.optString(i)).append("\n");
                        }
                    }
                    if (resObj.optBoolean("hasNextPage")) {
                        sb.append("\n(more on next page...)");
                    }
                    return sb.toString().trim();
                }

                case "search_in_file": {
                    JSONArray lines = resObj.optJSONArray("lines");
                    if (lines == null || lines.length() == 0) return "No matches found.";
                    
                    String uri = args.optString("uri");
                    String content = SketchwareFileDecryptor.decryptFile("", uri); // scId ignored in decrypt if absolute
                    String[] allLines = content != null ? content.split("\n", -1) : new String[0];
                    
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < lines.length(); i++) {
                        int lineNum = lines.optInt(i);
                        String lineContent = (lineNum > 0 && lineNum <= allLines.length) ? allLines[lineNum - 1] : "";
                        sb.append(String.format("Line %d:\n```\n%s\n```\n\n", lineNum, lineContent));
                    }
                    return sb.toString().trim();
                }

                case "read_lint_errors": {
                    JSONArray errors = resObj.optJSONArray("lintErrors");
                    return stringifyLintErrors(errors);
                }

                case "create_file_or_folder":
                    return String.format("URI %s successfully created.", args.optString("uri"));

                case "delete_file_or_folder":
                    return String.format("URI %s successfully deleted.", args.optString("uri"));

                case "edit_file":
                case "rewrite_file": {
                    String uri = args.optString("uri");
                    JSONArray errors = resObj.optJSONArray("lintErrors");
                    String lintInfo = "";
                    
                    if (errors != null && errors.length() > 0) {
                        lintInfo = "\n\nLint errors found after change:\n" + stringifyLintErrors(errors) + 
                                 "\nIf this is related to a change made while calling this tool, you might want to fix the error.";
                    } else {
                        lintInfo = " No lint errors found.";
                    }
                    
                    return String.format("Change successfully made to %s.%s", uri, lintInfo);
                }

                case "run_command":
                case "run_persistent_command": {
                    String output = resObj.optString("result");
                    JSONObject resolveReason = resObj.optJSONObject("resolveReason");
                    String type = resolveReason != null ? resolveReason.optString("type") : "done";
                    
                    if ("done".equals(type)) {
                        int exitCode = resolveReason.optInt("exitCode", 0);
                        return String.format("%s\n(exit code %d)", output, exitCode);
                    } else if ("timeout".equals(type)) {
                        if ("run_persistent_command".equals(toolName)) {
                            String termId = args.optString("persistent_terminal_id");
                            return String.format("%s\nTerminal command is running in terminal %s. The given outputs are the results after %d seconds.", 
                                output, termId, MAX_TERMINAL_BG_COMMAND_TIME_SECONDS);
                        } else {
                            return String.format("%s\nTerminal command ran, but was automatically killed by Void after %ds of inactivity and did not finish successfully. To try with more time, open a persistent terminal and run the command there.", 
                                output, MAX_TERMINAL_INACTIVE_TIME_SECONDS);
                        }
                    }
                    return output;
                }

                case "open_persistent_terminal":
                    return String.format("Successfully created persistent terminal. persistentTerminalId=\"%s\"", resObj.optString("persistentTerminalId"));

                case "kill_persistent_terminal":
                    return String.format("Successfully closed terminal \"%s\".", args.optString("persistent_terminal_id"));

                default:
                    return result.result;
            }
        } catch (Exception e) {
            return result.result; // Fallback to raw result if parsing fails
        }
    }

    private static String stringifyDirectoryTree1Deep(JSONObject args, JSONObject result) {
        JSONArray children = result.optJSONArray("children");
        if (children == null) return "[]";
        
        StringBuilder sb = new StringBuilder();
        String uri = args.optString("uri", "");
        sb.append(uri.isEmpty() ? "Root directory:" : uri + ":").append("\n");
        
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            String name = child.optString("name");
            boolean isDir = child.optBoolean("isDirectory");
            sb.append(isDir ? "  / " : "    ").append(name).append("\n");
        }
        
        if (result.optBoolean("hasNextPage")) {
            int remaining = result.optInt("itemsRemaining", 0);
            sb.append("\n... and ").append(remaining).append(" more items (use page_number to see more)");
        }
        
        return sb.toString().trim();
    }

    private static String stringifyLintErrors(JSONArray lintErrors) {
        if (lintErrors == null || lintErrors.length() == 0) return "No lint errors found.";
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lintErrors.length(), 100); i++) {
            JSONObject err = lintErrors.optJSONObject(i);
            sb.append(String.format("Error %d:\nLines Affected: %d-%d\nError message:%s\n\n", 
                i + 1, 
                err.optInt("startLineNumber"), 
                err.optInt("endLineNumber"), 
                err.optString("message")));
        }
        return sb.toString().trim();
    }
}
