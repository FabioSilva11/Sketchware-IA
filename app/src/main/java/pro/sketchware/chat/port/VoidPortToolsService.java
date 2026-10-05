package pro.sketchware.chat.port;

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
import java.util.regex.Pattern;

import pro.sketchware.chat.DirectoryTreeService;
import pro.sketchware.R;
import pro.sketchware.SketchApplication;
import pro.sketchware.chat.LanguageHelpers;
import pro.sketchware.chat.PromptConstants;
import pro.sketchware.chat.StringHelpers;
import pro.sketchware.chat.ChatToolLog;
import pro.sketchware.chat.ProjectPathResolver;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspaceManager;
import pro.sketchware.chat.workspace.WorkspacePath;
import pro.sketchware.chat.SemanticFileSearcher;
import pro.sketchware.chat.FileChangeTracker;

/**
 * Android port of browser/toolsService.ts
 * Provides all builtin tools from Void for use in Axion chat.
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
    private static final int LINT_ERROR_TIMEOUT = 1000;
    private VoidPortToolsService() {
    }

    /**
     * update_plan tool: lets the model maintain the step plan shown in the
     * plan tab (Codex-style). Input format: one step per line,
     * "pending|running|done: step title".
     */
    private static String updatePlan(String scId, Object planObj) {
        try {
            String plan = planObj == null ? "" : String.valueOf(planObj);
            List<pro.sketchware.chat.ChatPlanManager.Task> tasks = new ArrayList<>();
            for (String line : plan.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int status = pro.sketchware.chat.ChatPlanManager.STATUS_PENDING;
                String title = trimmed;
                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    String statusToken = trimmed.substring(0, colon).trim()
                            .toLowerCase(java.util.Locale.US)
                            .replaceAll("[^a-z_]", "");
                    title = trimmed.substring(colon + 1).trim();
                    if ("done".equals(statusToken) || "completed".equals(statusToken)) {
                        status = pro.sketchware.chat.ChatPlanManager.STATUS_DONE;
                    } else if ("running".equals(statusToken) || "in_progress".equals(statusToken)) {
                        status = pro.sketchware.chat.ChatPlanManager.STATUS_RUNNING;
                    } else if (!"pending".equals(statusToken)) {
                        // No recognised status prefix — treat the whole line as a title.
                        title = trimmed;
                    }
                }
                if (!title.isEmpty()) {
                    tasks.add(new pro.sketchware.chat.ChatPlanManager.Task(title, "", status));
                }
            }
            if (tasks.isEmpty()) {
                return "Erro: plano vazio. Envie um passo por linha no formato 'pending|running|done: título'.";
            }
            pro.sketchware.chat.ChatPlanManager.setModelPlan(scId, tasks);
            return "Plano atualizado com " + tasks.size() + " passo(s).";
        } catch (Exception e) {
            return "Erro ao atualizar plano: " + e.getMessage();
        }
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
            if (resolved == null || !resolved.isAuthorized()) {
                return new ToolCallResult("File not found or outside project scope: " + uriStr);
            }

            String content = readFileDirect(scId, uriStr);
            if (content == null) {
                return new ToolCallResult("File not found or could not be read: " + uriStr);
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

            WorkspaceFileSystem runFs = pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (runFs != null) {
                return lsDirThroughWorkspace(runFs, uriStr, pageNumber);
            }

            List<File> entries = new ArrayList<>();
            if (uriStr.trim().isEmpty()) {
                for (File root : ProjectPathResolver.getReadableRoots(scId)) {
                    if (root != null && root.exists()) {
                        entries.add(root);
                    }
                }
            } else {
                if (ProjectPathResolver.isPlaceholderPath(uriStr)) {
                    return new ToolCallResult(
                            "Error: invalid directory path placeholder: " + uriStr);
                }
            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uriStr);
            if (resolved == null || !resolved.isAuthorized()) {
                return new ToolCallResult(
                        "Error: directory path is invalid, out of scope, or unavailable: " + uriStr);
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
            return new ToolCallResult("Error listing directory: " + e.getMessage());
        }
    }

    public static ToolCallResult getDirTree(String scId, Object uriObj) {
        try {
            String uriStr = isFalsy(uriObj) ? "." : validateStr("uri", uriObj);
            uriStr = uriStr.trim().isEmpty() ? "." : uriStr;
            if (ProjectPathResolver.isPlaceholderPath(uriStr)) {
                return new ToolCallResult("Error: invalid folder path placeholder: " + uriStr);
            }

            WorkspaceFileSystem runFs = pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (runFs != null) {
                String path = ProjectPathResolver.isReadRootAlias(uriStr) ? "" : uriStr;
                if (!runFs.exists(path)) {
                    return new ToolCallResult("Directory not found: " + uriStr);
                }
                if (!runFs.isDirectory(path)) {
                    return new ToolCallResult("The path is a file, not a directory. Use read_file instead: " + uriStr);
                }
                JSONObject resultObj = new JSONObject();
                resultObj.put("str", workspaceTree(runFs, path));
                return new ToolCallResult(resultObj.toString());
            }

            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uriStr);
            if (resolved == null || !resolved.isAuthorized()) {
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

            String content = readFileDirect(scId, uriStr);
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

    // ============================================
    // EDIT TOOLS
    // ============================================

    public static ToolCallResult rewriteFile(String scId, Object uriObj, Object newContentObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            String newContent = validateStr("new_content", newContentObj);

            String oldContent = readFileDirect(scId, uriStr);
            boolean existedBefore = oldContent != null;
            if (oldContent == null) {
                oldContent = "";
            }

            String refused = writeFileDirect(scId, uriStr, newContent);
            if (refused != null) {
                return new ToolCallResult("Cannot write to " + uriStr + ": " + refused);
            }

            FileChangeTracker.trackChange(scId, uriStr, oldContent, newContent, existedBefore);
            return new ToolCallResult("{}");
        } catch (Exception e) {
            return new ToolCallResult("Error rewriting file: " + e.getMessage());
        }
    }

    public static ToolCallResult editFile(String scId, Object uriObj, Object searchReplaceBlocksObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            String searchReplaceBlocks = validateStr("search_replace_blocks", searchReplaceBlocksObj);

            String content = readFileDirect(scId, uriStr);
            if (content == null) {
                return new ToolCallResult("File not found or could not be read: " + uriStr);
            }

            SearchReplaceEngine.Result replaceResult =
                    SearchReplaceEngine.apply(content, searchReplaceBlocks);
            if (replaceResult.blockCount == 0) {
                return new ToolCallResult(
                        "Invalid SEARCH/REPLACE blocks: no valid blocks found. "
                                + "Use the exact marker format from the tool schema.");
            }
            if (!replaceResult.succeeded()) {
                return new ToolCallResult(
                        "Could not apply edit_file safely: block "
                                + replaceResult.failedBlock + "/" + replaceResult.blockCount
                                + " was not applied. " + replaceResult.failureReason
                                + " No changes were written. Call read_file again, then retry "
                                + "with a small unique ORIGINAL block from the current content.");
            }
            String newContent = replaceResult.content;

            String refused = writeFileDirect(scId, uriStr, newContent);
            if (refused != null) {
                return new ToolCallResult("Cannot write to " + uriStr + ": " + refused);
            }

            FileChangeTracker.trackChange(scId, uriStr, content, newContent);
            return new ToolCallResult("{}");
        } catch (Exception e) {
            return new ToolCallResult("Error editing file: " + e.getMessage());
        }
    }

    public static ToolCallResult createFileOrFolder(String scId, Object uriObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            boolean isFolder = checkIfIsFolder(uriStr);

            // Single source of truth: creations go through the active
            // WorkspaceFileSystem (SAF or local folder), never through
            // java.io.File. ProjectPathResolver below is only a fallback for
            // sessions with no workspace open.
            WorkspaceFileSystem ws = pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (ws != null) {
                return createThroughWorkspace(scId, ws, uriStr, isFolder);
            }

            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForWrite(scId, uriStr);
            if (resolved == null || !resolved.isAuthorized()) {
                return new ToolCallResult("Error: cannot create outside the workspace: " + uriStr);
            }

            File file = resolved.getFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (isFolder) {
                if (!file.exists() && !file.mkdirs()) {
                    return new ToolCallResult("Error: failed to create folder: " + uriStr);
                }
            } else {
                if (!file.exists() && !file.createNewFile()) {
                    return new ToolCallResult("Error: failed to create file: " + uriStr);
                }
                // existedBefore=false: rejecting this change must delete the file.
                FileChangeTracker.trackChange(scId, uriStr, "", "", false);
            }

            return new ToolCallResult("{}");
        } catch (Exception e) {
            return new ToolCallResult("Error creating file/folder: " + e.getMessage());
        }
    }

    /**
     * Workspace-first creation: resolves through the active
     * {@link WorkspaceFileSystem}, validates the boolean result and verifies
     * the entry really exists before reporting success. Returns error (never
     * silent success) when the filesystem refuses the operation.
     */
    private static ToolCallResult createThroughWorkspace(String scId, WorkspaceFileSystem ws,
                                                         String uriStr, boolean isFolder) {
        // The workspace filesystem only serves relative paths: absolute paths,
        // drive letters and backslashes never name a workspace entry.
        if (uriStr.startsWith("/") || uriStr.contains(":") || uriStr.contains("\\")) {
            return new ToolCallResult("Error: refusing to create an absolute or unsafe path: " + uriStr);
        }
        if (WorkspacePath.hasParentTraversal(uriStr)) {
            return new ToolCallResult("Error: unsafe path: " + uriStr);
        }
        String path = WorkspacePath.normalize(uriStr);
        if (path.isEmpty()) {
            return new ToolCallResult("Error: refusing to create the workspace root.");
        }
        if (ws.exists(path)) {
            // Already exists: idempotent success, nothing was mutated.
            return new ToolCallResult("{}");
        }

        boolean created;
        if (isFolder) {
            created = ws.createDirectory(path);
        } else {
            created = ws.createFile(path);
        }
        if (!created || !ws.exists(path)) {
            return new ToolCallResult("Error: failed to create "
                    + (isFolder ? "folder" : "file") + ": " + uriStr
                    + " (the filesystem did not create the entry).");
        }

        if (!isFolder) {
            // existedBefore=false: reverting this change must delete the file.
            FileChangeTracker.trackChange(scId, uriStr, "", "", false);
        }
        return new ToolCallResult("{}");
    }

    public static ToolCallResult deleteFileOrFolder(String scId, Object uriObj, Object isRecursiveObj) {
        try {
            String uriStr = validateStr("uri", uriObj);
            boolean isRecursive = validateBoolean(isRecursiveObj, false);

            // Single source of truth: mutations go through the active WorkspaceFileSystem
            // (SAF or local folder), never straight through java.io.File. The legacy
            // ProjectPathResolver path below is only a fallback for sessions with no
            // workspace open.
            WorkspaceFileSystem ws = pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (ws != null) {
                return deleteThroughWorkspace(scId, ws, uriStr, isRecursive);
            }

            // Root aliases are useful for project discovery, but must never be accepted by
            // a destructive tool. In particular, "/" resolves to the active project root
            // for read-only tools such as get_dir_tree.
            if (isUnsafeMutationRoot(uriStr)) {
                return new ToolCallResult("Error: refusing to delete the active project root: " + uriStr);
            }

            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForWrite(scId, uriStr);
            if (resolved == null || !resolved.isAuthorized()) {
                return new ToolCallResult("File/folder not found or outside the workspace: " + uriStr);
            }

            File file = resolved.getFile();
            if (isProtectedMutationRoot(file, ProjectPathResolver.getWritableRoots(scId))) {
                return new ToolCallResult("Error: refusing to delete the active project root: " + uriStr);
            }
            if (!file.exists()) {
                return new ToolCallResult("File/folder not found: " + uriStr);
            }

            if (file.isDirectory() && !isRecursive && file.list().length > 0) {
                return new ToolCallResult("Cannot delete non-empty directory without is_recursive=true");
            }

            String oldContent = "";
            boolean isFile = file.isFile();
            if (isFile) {
                oldContent = readFileDirect(scId, uriStr);
                if (oldContent == null) oldContent = "";
            }

            boolean deleted = deleteRecursive(file);
            if (!deleted || file.exists()) {
                return new ToolCallResult("Error: failed to delete " + uriStr
                        + " (the filesystem did not remove the entry).");
            }

            if (isFile) {
                FileChangeTracker.trackChange(scId, uriStr, oldContent, "");
            }

            return new ToolCallResult("{}");
        } catch (Exception e) {
            return new ToolCallResult("Error deleting file/folder: " + e.getMessage());
        }
    }

    /**
     * Workspace-first delete: resolves through the active {@link WorkspaceFileSystem},
     * validates the boolean result and re-checks existence so a failed delete is never
     * reported as success. Returns {@code null} when the path is out of scope so the
     * caller can fall back to the legacy resolver.
     */
    private static ToolCallResult deleteThroughWorkspace(String scId, WorkspaceFileSystem ws,
                                                         String uriStr, boolean isRecursive) {
        // The workspace filesystem only serves relative paths: absolute paths,
        // drive letters and backslashes never name a workspace entry.
        if (uriStr.startsWith("/") || uriStr.contains(":") || uriStr.contains("\\")) {
            return new ToolCallResult("Error: refusing to delete an absolute or unsafe path: " + uriStr);
        }
        if (WorkspacePath.hasParentTraversal(uriStr)) {
            return new ToolCallResult("Error: unsafe path: " + uriStr);
        }
        String path = WorkspacePath.normalize(uriStr);
        if (path.isEmpty()) {
            return new ToolCallResult("Error: refusing to delete the workspace root.");
        }
        if (!ws.exists(path)) {
            return new ToolCallResult("Error: file/folder not found: " + uriStr);
        }
        if (ws.isDirectory(path) && !isRecursive && !ws.list(path).isEmpty()) {
            return new ToolCallResult("Error: cannot delete non-empty directory without is_recursive=true");
        }

        String oldContent = "";
        boolean isFile = !ws.isDirectory(path);
        if (isFile) {
            try {
                oldContent = ws.readText(path);
            } catch (Exception readError) {
                oldContent = "";
            }
        }

        boolean deleted = ws.delete(path);
        if (!deleted || ws.exists(path)) {
            return new ToolCallResult("Error: failed to delete " + uriStr
                    + " (the filesystem did not remove the entry).");
        }

        if (isFile) {
            FileChangeTracker.trackChange(scId, uriStr, oldContent, "");
        }
        return new ToolCallResult("{}");
    }

    public static ToolCallResult readFiles(String scId, Object urisObj) {
        try {
            JSONArray array;
            if (urisObj instanceof JSONArray) {
                array = (JSONArray) urisObj;
            } else if (urisObj instanceof String) {
                array = new JSONArray((String) urisObj);
            } else {
                return new ToolCallResult("Invalid parameter 'uris': array expected");
            }
            JSONObject result = new JSONObject();
            for (int i = 0; i < array.length(); i++) {
                String uri = array.optString(i, "").trim();
                if (uri.isEmpty()) continue;
                String content = readFileDirect(scId, uri);
                result.put(uri, content != null ? content : "Error: file not found or unreadable");
            }
            return new ToolCallResult(result.toString());
        } catch (Exception e) {
            return new ToolCallResult("Error in read_files: " + e.getMessage());
        }
    }

    /** Success payload of move/rename/copy: the result text is built from it in getStringOfResult. */
    private static ToolCallResult fromTo(String from, String to) throws org.json.JSONException {
        return new ToolCallResult(new JSONObject().put("from", from).put("to", to).toString());
    }

    public static ToolCallResult moveFile(String scId, String source, String destination) {
        try {
            if (source.isEmpty() || destination.isEmpty()) {
                return new ToolCallResult("Error: source and destination are required");
            }
            if (pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(source)
                    || pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(destination)) {
                return new ToolCallResult("Security error: path traversal blocked");
            }
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (fs != null) {
                boolean ok = fs.move(source, destination);
                return ok ? fromTo(source, destination) : new ToolCallResult("Error: could not move " + source + " to " + destination + " (does the source exist?)");
            }
            ProjectPathResolver.ResolvedPath src = ProjectPathResolver.resolveForRead(scId, source);
            ProjectPathResolver.ResolvedPath dst = ProjectPathResolver.resolveForWrite(scId, destination);
            if (src == null || dst == null || !src.isAuthorized() || !dst.isAuthorized() || !src.getFile().exists()) {
                return new ToolCallResult("Source file not found or outside the workspace: " + source);
            }
            dst.getFile().getParentFile().mkdirs();
            boolean ok = src.getFile().renameTo(dst.getFile());
            return ok ? fromTo(source, destination) : new ToolCallResult("Error: could not move " + source + " to " + destination);
        } catch (Exception e) {
            return new ToolCallResult("Error moving file: " + e.getMessage());
        }
    }

    public static ToolCallResult renameFile(String scId, String uri, String newName) {
        try {
            if (uri.isEmpty() || newName.isEmpty()) {
                return new ToolCallResult("Error: uri and new_name are required");
            }
            if (pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(uri)
                    || pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(newName)) {
                return new ToolCallResult("Security error: path traversal blocked");
            }
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (fs != null) {
                boolean ok = fs.rename(uri, newName);
                return ok ? fromTo(uri, newName) : new ToolCallResult("Error: could not rename " + uri + " to " + newName + " (does it exist?)");
            }
            ProjectPathResolver.ResolvedPath src = ProjectPathResolver.resolveForRead(scId, uri);
            if (src == null || !src.getFile().exists()) {
                return new ToolCallResult("File not found: " + uri);
            }
            File target = new File(src.getFile().getParentFile(), newName);
            boolean ok = src.getFile().renameTo(target);
            return ok ? fromTo(uri, newName) : new ToolCallResult("Error: could not rename " + uri + " to " + newName);
        } catch (Exception e) {
            return new ToolCallResult("Error renaming file: " + e.getMessage());
        }
    }

    public static ToolCallResult copyFile(String scId, String source, String destination) {
        try {
            if (source.isEmpty() || destination.isEmpty()) {
                return new ToolCallResult("Error: source and destination are required");
            }
            if (pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(source)
                    || pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(destination)) {
                return new ToolCallResult("Security error: path traversal blocked");
            }
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (fs != null) {
                boolean ok = fs.copy(source, destination);
                return ok ? fromTo(source, destination) : new ToolCallResult("Error: could not copy " + source + " to " + destination + " (does the source exist?)");
            }
            String content = readFileDirect(scId, source);
            if (content == null) {
                return new ToolCallResult("Source file not found: " + source);
            }
            String refused = writeFileDirect(scId, destination, content);
            return refused == null ? fromTo(source, destination)
                    : new ToolCallResult("Cannot copy to " + destination + ": " + refused);
        } catch (Exception e) {
            return new ToolCallResult("Error copying file: " + e.getMessage());
        }
    }

    public static ToolCallResult getFileInfo(String scId, String uri) {
        try {
            if (pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(uri)) {
                return new ToolCallResult("Security error: path traversal blocked");
            }
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (fs != null) {
                pro.sketchware.chat.workspace.WorkspaceFileSystem.FileMetadata meta = fs.getMetadata(uri);
                if (meta != null) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", meta.getName());
                    obj.put("relativePath", meta.getRelativePath());
                    obj.put("isDirectory", meta.isDirectory());
                    obj.put("size", meta.getSize());
                    obj.put("lastModified", meta.getLastModified());
                    return new ToolCallResult(obj.toString());
                }
                // The run's workspace is the whole scope: no fallback that could reach another project
                return new ToolCallResult("File not found or outside the project: " + uri);
            }
            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uri);
            if (resolved == null || !resolved.getFile().exists()) {
                return new ToolCallResult("File not found: " + uri);
            }
            File f = resolved.getFile();
            JSONObject obj = new JSONObject();
            obj.put("name", f.getName());
            obj.put("isDirectory", f.isDirectory());
            obj.put("size", f.length());
            obj.put("lastModified", f.lastModified());
            return new ToolCallResult(obj.toString());
        } catch (Exception e) {
            return new ToolCallResult("Error getting file info: " + e.getMessage());
        }
    }

    static boolean isUnsafeMutationRoot(String uri) {
        return ProjectPathResolver.isReadRootAlias(uri)
                || ProjectPathResolver.hasParentTraversal(uri);
    }

    static boolean isProtectedMutationRoot(File candidate, List<File> writableRoots) {
        if (candidate == null || writableRoots == null) {
            return false;
        }
        try {
            String candidatePath = candidate.getCanonicalPath();
            for (File root : writableRoots) {
                if (root != null && candidatePath.equals(root.getCanonicalPath())) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return true;
        }
        return false;
    }

    // ============================================
    // HELPER METHODS
    // ============================================

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

    /**
     * Deletes recursively and reports whether EVERY required deletion
     * succeeded. Never treated as success by callers without checking.
     */
    private static boolean deleteRecursive(File file) {
        boolean allDeleted = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    allDeleted &= deleteRecursive(child);
                }
            }
        }
        return file.delete() && allDeleted;
    }

    // ============================================
    // TOOL REGISTRY FOR MCP
    // ============================================

    public static JSONArray getAllToolsAsMCP() {
        JSONArray array = new JSONArray();
        // File tools
        array.put(createToolMCP("read_file",
            "Returns full contents of a given file.",
            new String[]{"uri"}, new String[]{"start_line", "end_line", "page_number"}));

        array.put(createToolMCP("ls_dir",
            "Lists all files and folders in the given URI.",
            new String[]{}, new String[]{"uri", "page_number"}));

        array.put(createToolMCP("get_dir_tree",
            "This is a very effective way to learn about the user's codebase. Returns a tree diagram of all the files and folders in the given folder.",
            new String[]{}, new String[]{"uri"}));

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

        // Edit tools
        array.put(createToolMCP("create_file_or_folder",
            "Create a file or folder at the given path. To create a folder, the path MUST end with a trailing slash.",
            new String[]{"uri"}, null));

        array.put(createToolMCP("delete_file_or_folder",
            "Delete a file or folder at the given path.",
            new String[]{"uri"}, new String[]{"is_recursive"}));

        array.put(createToolMCP("edit_file",
            "Atomically edit a file using unique SEARCH/REPLACE blocks copied from a fresh read_file result. If an edit fails, read the file again before retrying.",
            new String[]{"uri", "search_replace_blocks"}, null));

        array.put(createToolMCP("rewrite_file",
            "Edits a file, deleting all the old contents and replacing them with your new contents. Use this tool if you want to edit a file you just created.",
            new String[]{"uri", "new_content"}, null));

        array.put(createToolMCP("move_file",
            "Moves a file or folder from one path to another within the workspace.",
            new String[]{"source", "destination"}, null));

        array.put(createToolMCP("rename_file",
            "Renames a file or folder in place, keeping it in the same directory.",
            new String[]{"uri", "new_name"}, null));

        array.put(createToolMCP("copy_file",
            "Copies a file or folder from one path to another within the workspace.",
            new String[]{"source", "destination"}, null));

        array.put(createToolMCP("get_file_info",
            "Returns metadata about a file or folder (size, type, last modified) without reading its full contents.",
            new String[]{"uri"}, null));

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
            params.put("additionalProperties", false);
            
            JSONObject properties = new JSONObject();
            for (String param : requiredParams) {
                JSONObject prop = new JSONObject();
                prop.put("type", toolParamType(param));
                prop.put("description", toolParamDescription(name, param));
                properties.put(param, prop);
            }
            if (optionalParams != null) {
                for (String param : optionalParams) {
                    JSONObject prop = new JSONObject();
                    prop.put("type", toolParamType(param));
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

    private static String toolParamDescription(String toolName, String paramName) {
        if ("uri".equals(paramName)) {
            if ("ls_dir".equals(toolName)) {
                return "Optional. The FULL path to the folder. Leave this as empty or \"\" to search all folders.";
            }
            if ("get_dir_tree".equals(toolName)) {
                return "Optional. Folder path inside the current project. Defaults to '.' (the current project root). Use '/' only as an alias for that project root, never for the device root.";
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
        if ("source".equals(paramName)) {
            return "The FULL path of the file or folder to " + ("copy_file".equals(toolName) ? "copy." : "move.");
        }
        if ("destination".equals(paramName)) {
            return "The FULL path it should end up at.";
        }
        if ("new_name".equals(paramName)) {
            return "The new name only, without a folder.";
        }
        return "";
    }

    private static String toolParamType(String paramName) {
        if ("start_line".equals(paramName) || "end_line".equals(paramName)
                || "page_number".equals(paramName)) {
            return "integer";
        }
        if ("is_regex".equals(paramName) || "is_recursive".equals(paramName)) {
            return "boolean";
        }
        return "string";
    }

    // ============================================
    // MAIN TOOL EXECUTOR
    // ============================================

    /** What a tool call gives the model, and whether it failed (the chat shows failed calls as errors). */
    public static final class ToolOutcome {
        public final String text;
        public final boolean failed;

        private ToolOutcome(String text, boolean failed) {
            this.text = text == null ? "" : text;
            this.failed = failed;
        }

        static ToolOutcome ok(String text) {
            return new ToolOutcome(text, false);
        }

        static ToolOutcome failure(String text) {
            return new ToolOutcome(text, true);
        }
    }

    public static String executeTool(String scId, String toolName, JSONObject args) {
        return runTool(scId, toolName, args).text;
    }

    public static ToolOutcome runTool(String scId, String toolName, JSONObject args) {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        ChatToolLog.d("tool", "▶ " + toolName + " sc=" + scId
                + " args=" + ChatToolLog.preview(args == null ? "{}" : args.toString(), 300));
        try {
            ToolOutcome outcome = executeToolInner(scId, toolName, args == null ? new JSONObject() : args);
            long ms = android.os.SystemClock.elapsedRealtime() - startedAt;
            ChatToolLog.d("tool", (outcome.failed ? "✖ " : "✔ ") + toolName
                    + " (" + ms + "ms) -> " + ChatToolLog.preview(outcome.text, 200));
            return outcome;
        } catch (Exception e) {
            ChatToolLog.e("tool", "crash in " + toolName, e);
            return ToolOutcome.failure("Erro ao executar ferramenta " + toolName + ": " + e.getMessage());
        }
    }

    /**
     * Every successful {@link ToolCallResult} carries a JSON payload; failures carry the message the model reads.
     */
    private static boolean isJsonPayload(String result) {
        String trimmed = result == null ? "" : result.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static ToolOutcome executeToolInner(String scId, String toolName, JSONObject args) {
        try {
            ToolCallResult result;

            switch (toolName) {
                case "read_file":
                    result = readFile(scId, 
                        args.opt("uri") != null ? args.opt("uri") : args.opt("path"),
                        args.opt("start_line") != null ? args.opt("start_line") : args.opt("startLine"),
                        args.opt("end_line") != null ? args.opt("end_line") : args.opt("endLine"),
                        args.opt("page_number") != null ? args.opt("page_number") : args.opt("pageNumber"));
                    break;

                case "read_files":
                    result = readFiles(scId, args.opt("uris") != null ? args.opt("uris") : args.opt("paths"));
                    break;
                    
                case "list_directory":
                case "ls_dir":
                    result = lsDir(scId,
                        args.opt("uri") != null ? args.opt("uri") : args.opt("path"),
                        args.opt("page_number") != null ? args.opt("page_number") : args.opt("pageNumber"));
                    break;
                    
                case "get_workspace_tree":
                case "get_dir_tree":
                    result = getDirTree(scId, args.opt("uri") != null ? args.opt("uri") : args.opt("path"));
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
                    
                case "search_files":
                case "search_for_files":
                    result = searchForFiles(scId,
                        args.opt("query"),
                        args.opt("is_regex") != null ? args.opt("is_regex") : args.opt("isRegex"),
                        args.opt("search_in_folder") != null ? args.opt("search_in_folder") : args.opt("searchInFolder"),
                        args.opt("page_number") != null ? args.opt("page_number") : args.opt("pageNumber"));
                    break;
                    
                case "search_text":
                case "search_in_file":
                    result = searchInFile(scId,
                        args.opt("uri") != null ? args.opt("uri") : args.opt("path"),
                        args.opt("query"),
                        args.opt("is_regex") != null ? args.opt("is_regex") : args.opt("isRegex"));
                    break;
                    
                case "write_file":
                case "rewrite_file":
                    result = rewriteFile(scId,
                        args.opt("uri") != null ? args.opt("uri") : (args.opt("path") != null ? args.opt("path") : args.opt("file_path")),
                        args.opt("new_content") != null ? args.opt("new_content") : (args.opt("newContent") != null ? args.opt("newContent") : args.opt("content")));
                    break;
                    
                case "patch_file":
                case "edit_file":
                    result = editFile(scId,
                        args.opt("uri") != null ? args.opt("uri") : (args.opt("path") != null ? args.opt("path") : args.opt("file_path")),
                        args.opt("search_replace_blocks") != null ? args.opt("search_replace_blocks") : args.opt("searchReplaceBlocks"));
                    break;
                    
                case "create_file":
                case "create_directory":
                case "create_file_or_folder":
                    String createTarget = args.optString("uri", args.optString("path", args.optString("file_path", "")));
                    if ("create_directory".equals(toolName) && !createTarget.endsWith("/")) {
                        createTarget = createTarget + "/";
                    }
                    result = createFileOrFolder(scId, createTarget);
                    break;
                    
                case "delete_file":
                case "delete_directory":
                case "delete_file_or_folder":
                    result = deleteFileOrFolder(scId,
                        args.opt("uri") != null ? args.opt("uri") : args.opt("path"),
                        args.opt("is_recursive") != null ? args.opt("is_recursive") : args.opt("isRecursive"));
                    break;

                case "move_file":
                    result = moveFile(scId,
                        args.optString("source", args.optString("source_path", args.optString("from", ""))),
                        args.optString("destination", args.optString("destination_path", args.optString("to", ""))));
                    break;

                case "rename_file":
                    result = renameFile(scId,
                        args.optString("uri", args.optString("path", "")),
                        args.optString("new_name", args.optString("newName", "")));
                    break;

                case "copy_file":
                    result = copyFile(scId,
                        args.optString("source", args.optString("source_path", args.optString("from", ""))),
                        args.optString("destination", args.optString("destination_path", args.optString("to", ""))));
                    break;

                case "get_file_info":
                    result = getFileInfo(scId, args.optString("uri", args.optString("path", "")));
                    break;

                case "update_plan": {
                    String plan = updatePlan(scId, args.opt("plan"));
                    return plan.startsWith("Erro") ? ToolOutcome.failure(plan) : ToolOutcome.ok(plan);
                }

                default:
                    if ("get_file".equals(toolName)) {
                        return ToolOutcome.failure(SketchApplication.getContext().getString(R.string.chat_tool_get_file_alias_error));
                    }
                    return ToolOutcome.failure(SketchApplication.getContext().getString(R.string.chat_tool_unknown_error, toolName));
            }

            if (!isJsonPayload(result.result)) {
                return ToolOutcome.failure(result.result);
            }
            return ToolOutcome.ok(getStringOfResult(toolName, args, result));

        } catch (Exception e) {
            return ToolOutcome.failure("Erro ao executar ferramenta " + toolName + ": " + e.getMessage());
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
                    String found = sb.toString().trim();
                    return found.isEmpty() ? "No matching files found." : found;
                }

                case "search_in_file": {
                    JSONArray lines = resObj.optJSONArray("lines");
                    if (lines == null || lines.length() == 0) return "No matches found.";
                    
                    String uri = args.optString("uri");
                    String content = readFileDirect("", uri); // scId ignored if absolute
                    String[] allLines = content != null ? content.split("\n", -1) : new String[0];
                    
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < lines.length(); i++) {
                        int lineNum = lines.optInt(i);
                        String lineContent = (lineNum > 0 && lineNum <= allLines.length) ? allLines[lineNum - 1] : "";
                        sb.append(String.format("Line %d:\n```\n%s\n```\n\n", lineNum, lineContent));
                    }
                    return sb.toString().trim();
                }

                case "create_file_or_folder":
                    return String.format("URI %s successfully created.", args.optString("uri"));

                case "delete_file_or_folder":
                    return String.format("URI %s successfully deleted.", args.optString("uri"));

                case "edit_file":
                case "rewrite_file":
                    // No linter runs on the phone: errors only show up when the project is compiled
                    return String.format("Change successfully made to %s.", args.optString("uri"));

                case "move_file":
                    return String.format("Moved %s to %s.", resObj.optString("from"), resObj.optString("to"));

                case "rename_file":
                    return String.format("Renamed %s to %s.", resObj.optString("from"), resObj.optString("to"));

                case "copy_file":
                    return String.format("Copied %s to %s.", resObj.optString("from"), resObj.optString("to"));

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

    /** ls_dir over the run's workspace: paths stay relative to the workspace, never absolute device paths. */
    private static ToolCallResult lsDirThroughWorkspace(WorkspaceFileSystem fs, String uriStr, int pageNumber) throws Exception {
        String path = uriStr == null || ProjectPathResolver.isReadRootAlias(uriStr.trim()) ? "" : uriStr.trim();
        if (!path.isEmpty() && ProjectPathResolver.isPlaceholderPath(path)) {
            return new ToolCallResult("Error: invalid directory path placeholder: " + uriStr);
        }
        if (!fs.exists(path)) {
            return new ToolCallResult("Directory not found: " + uriStr);
        }
        if (!fs.isDirectory(path)) {
            return new ToolCallResult("The path is a file, not a directory. Use read_file to view its contents: " + uriStr);
        }
        List<WorkspaceFileSystem.FileEntry> entries = fs.list(path);
        int fromIdx = MAX_CHILDREN_URIS_PAGE * (pageNumber - 1);
        int toIdx = MAX_CHILDREN_URIS_PAGE * pageNumber - 1;
        JSONArray resultArray = new JSONArray();
        for (int i = fromIdx; i <= Math.min(toIdx, entries.size() - 1); i++) {
            WorkspaceFileSystem.FileEntry entry = entries.get(i);
            JSONObject item = new JSONObject();
            item.put("uri", entry.getRelativePath());
            item.put("name", entry.getName());
            item.put("isDirectory", entry.isDirectory());
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
    }

    /** Indented tree of a workspace folder, bounded like the java.io.File version. */
    private static String workspaceTree(WorkspaceFileSystem fs, String path) {
        StringBuilder out = new StringBuilder(path.isEmpty() ? "." : path).append('\n');
        int[] count = {0};
        appendWorkspaceTree(fs, path, "  ", 0, out, count);
        return out.toString();
    }

    private static void appendWorkspaceTree(WorkspaceFileSystem fs, String path, String indent, int depth,
                                            StringBuilder out, int[] count) {
        if (depth > 6) {
            return;
        }
        for (WorkspaceFileSystem.FileEntry entry : fs.list(path)) {
            if (pro.sketchware.chat.workspace.WorkspaceIgnoreRules.isDefaultIgnored(entry.getName())) {
                continue;
            }
            if (++count[0] > 400) {
                out.append(indent).append("... (more entries omitted)\n");
                return;
            }
            out.append(indent).append(entry.getName()).append(entry.isDirectory() ? "/" : "").append('\n');
            if (entry.isDirectory()) {
                appendWorkspaceTree(fs, entry.getRelativePath(), indent + "  ", depth + 1, out, count);
            }
        }
    }

    private static String readFileDirect(String scId, String uriStr) {
        try {
            if (pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(uriStr)) {
                return null;
            }
            String norm = pro.sketchware.chat.workspace.WorkspacePath.normalize(uriStr);
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (fs != null && fs.exists(norm) && !fs.isDirectory(norm)) {
                return fs.readText(norm);
            }
            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, uriStr);
            if (resolved == null || !resolved.isAuthorized()) return null;
            File file = resolved.getFile();
            if (!file.exists() || file.isDirectory()) return null;
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes through the run's workspace. Returns null once written, otherwise why it wasn't: the workspace's own
     * refusal (a generated Sketchware folder, a project open in the editor...) tells the model what to do instead.
     */
    private static String writeFileDirect(String scId, String uriStr, String content) {
        try {
            if (pro.sketchware.chat.workspace.WorkspacePath.hasParentTraversal(uriStr)) {
                return "the path leaves the workspace";
            }
            String norm = pro.sketchware.chat.workspace.WorkspacePath.normalize(uriStr);
            pro.sketchware.chat.workspace.WorkspaceFileSystem fs =
                    pro.sketchware.chat.agentsdk.RuntimeFileContext.effectiveFileSystem();
            if (fs != null) {
                fs.writeText(norm, content);
                return null;
            }
            ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForWrite(scId, uriStr);
            if (resolved == null || !resolved.isAuthorized()) return "the path is outside the project";
            File file = resolved.getFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            java.nio.file.Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }
}
