package pro.sketchware.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import pro.sketchware.chat.agentsdk.RuntimeFileContext;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspaceIgnoreRules;

/**
 * File name and content search for the chat tools. Searches go through the run's workspace (or the project's own
 * workspace outside a run), so a native Sketchware project's encrypted files are searched by their real content and
 * results are workspace-relative paths the other tools accept.
 */
public class SemanticFileSearcher {

    private static final int MAX_RESULTS = 50;
    private static final int MAX_FILE_CHARS = 4_000_000;
    private static final int SNIPPET_CONTEXT = 40;

    public static class SearchResult {
        public String filePath;
        public String snippet;
        public double relevance;

        public SearchResult(String filePath, String snippet, double relevance) {
            this.filePath = filePath;
            this.snippet = snippet;
            this.relevance = relevance;
        }
    }

    public static List<SearchResult> searchRelevantFiles(String query, String scId) {
        return searchByFilename(query, scId);
    }

    public static List<SearchResult> searchByFilename(String query, String scId) {
        List<SearchResult> results = new ArrayList<>();
        WorkspaceFileSystem fs = fileSystem(scId);
        if (fs == null || query == null) {
            return results;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        walk(fs, "", path -> {
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.toLowerCase(Locale.ROOT).contains(lower) || path.toLowerCase(Locale.ROOT).contains(lower)) {
                results.add(new SearchResult(path, name, name.toLowerCase(Locale.ROOT).contains(lower) ? 1.0 : 0.5));
            }
            return results.size() < MAX_RESULTS;
        });
        return results;
    }

    public static List<SearchResult> searchByContent(String query, String scId) {
        List<SearchResult> results = new ArrayList<>();
        WorkspaceFileSystem fs = fileSystem(scId);
        if (fs == null || query == null || query.isEmpty()) {
            return results;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        walk(fs, "", path -> {
            String content = readText(fs, path);
            if (content != null) {
                int index = content.toLowerCase(Locale.ROOT).indexOf(lower);
                if (index >= 0) {
                    results.add(new SearchResult(path, snippet(content, index, query.length()), 1.0));
                }
            }
            return results.size() < MAX_RESULTS;
        });
        return results;
    }

    public static List<SearchResult> searchByContentRegex(String regex, String scId) {
        List<SearchResult> results = new ArrayList<>();
        WorkspaceFileSystem fs = fileSystem(scId);
        if (fs == null || regex == null || regex.isEmpty()) {
            return results;
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex, Pattern.MULTILINE);
        } catch (PatternSyntaxException e) {
            return results;
        }
        walk(fs, "", path -> {
            String content = readText(fs, path);
            if (content != null) {
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    results.add(new SearchResult(path, snippet(content, matcher.start(), matcher.end() - matcher.start()), 1.0));
                }
            }
            return results.size() < MAX_RESULTS;
        });
        return results;
    }

    /** The run's workspace, else the project's own (outside a run, e.g. from the UI). */
    private static WorkspaceFileSystem fileSystem(String scId) {
        WorkspaceFileSystem fs = RuntimeFileContext.effectiveFileSystem();
        return fs != null ? fs : SketchwareWorkspace.fileSystemOf(scId);
    }

    private interface Visitor {
        /** @return false to stop walking */
        boolean visit(String path);
    }

    private static boolean walk(WorkspaceFileSystem fs, String dir, Visitor visitor) {
        List<WorkspaceFileSystem.FileEntry> entries;
        try {
            entries = fs.list(dir);
        } catch (Exception e) {
            return true;
        }
        for (WorkspaceFileSystem.FileEntry entry : entries) {
            if (WorkspaceIgnoreRules.isDefaultIgnored(entry.getName())) {
                continue;
            }
            if (entry.isDirectory()) {
                if (!walk(fs, entry.getRelativePath(), visitor)) {
                    return false;
                }
            } else if (!visitor.visit(entry.getRelativePath())) {
                return false;
            }
        }
        return true;
    }

    private static String readText(WorkspaceFileSystem fs, String path) {
        if (isBinary(path)) {
            return null;
        }
        try {
            WorkspaceFileSystem.FileMetadata metadata = fs.getMetadata(path);
            if (metadata != null && metadata.getSize() > MAX_FILE_CHARS) {
                return null;
            }
            return fs.readText(path);
        } catch (Exception e) {
            return null;
        }
    }

    private static String snippet(String content, int index, int length) {
        int start = Math.max(0, index - SNIPPET_CONTEXT);
        int end = Math.min(content.length(), index + length + SNIPPET_CONTEXT);
        return content.substring(start, end).replace('\n', ' ').replace('\r', ' ');
    }

    private static boolean isBinary(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot < name.lastIndexOf('/')) {
            return false;
        }
        switch (name.substring(dot + 1)) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "mp3", "wav", "ogg", "m4a", "mp4", "ttf", "otf",
                 "apk", "aab", "dex", "jar", "zip", "so", "class", "keystore", "jks", "flat", "arsc", "aar" -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
