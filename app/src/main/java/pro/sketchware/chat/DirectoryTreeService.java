package pro.sketchware.chat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DirectoryTreeService {
    private static final int MAX_FILES_TOTAL = 1000;
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final int DEFAULT_MAX_ITEMS_PER_DIR = 3;

    private DirectoryTreeService() {
    }

    public static boolean shouldExcludeDirectory(String name) {
        if (name == null) {
            return false;
        }
        if (name.equals(".git")
                || name.equals("node_modules")
                || name.startsWith(".")
                || name.equals("dist")
                || name.equals("build")
                || name.equals("out")
                || name.equals("bin")
                || name.equals("coverage")
                || name.equals("__pycache__")
                || name.equals("env")
                || name.equals("venv")
                || name.equals("tmp")
                || name.equals("temp")
                || name.equals("artifacts")
                || name.equals("target")
                || name.equals("obj")
                || name.equals("vendor")
                || name.equals("logs")
                || name.equals("cache")
                || name.equals("resource")
                || name.equals("resources")) {
            return true;
        }

        return name.matches(".*\\bout\\b.*") || name.matches(".*\\bbuild\\b.*");
    }

    public static List<File> getAllFilesInDirectory(File directory, int maxResults) {
        if (directory == null || !directory.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> result = new ArrayList<>();
        visitAll(directory, maxResults, result);
        return result;
    }

    public static String getDirectoryStrTool(File root) {
        if (root == null || !root.exists()) {
            throw new IllegalArgumentException("The folder does not exist.");
        }
        TreeResult result = computeAndStringifyDirectoryTree(
                root,
                PromptConstants.MAX_DIRSTR_CHARS_TOTAL_TOOL,
                new Counter(),
                Integer.MAX_VALUE,
                0,
                Integer.MAX_VALUE);

        if (result.wasCutOff) {
            result = computeAndStringifyDirectoryTree(
                    root,
                    PromptConstants.MAX_DIRSTR_CHARS_TOTAL_TOOL,
                    new Counter(),
                    DEFAULT_MAX_DEPTH,
                    0,
                    DEFAULT_MAX_ITEMS_PER_DIR);
        }

        String content = result.content;
        if (content.length() > PromptConstants.MAX_DIRSTR_CHARS_TOTAL_TOOL) {
            content = content.substring(0, PromptConstants.MAX_DIRSTR_CHARS_TOTAL_TOOL);
        }
        String answer = "Directory of " + root.getAbsolutePath() + ":\n" + content;
        return result.wasCutOff ? answer + "\n...Result was truncated..." : answer;
    }

    private static boolean visitAll(File folder, int maxResults, List<File> result) {
        if (result.size() >= maxResults) {
            return false;
        }
        File[] children = folder.listFiles();
        if (children == null) {
            return true;
        }
        List<File> sorted = sortedChildren(children);

        for (File child : sorted) {
            if (!child.isDirectory()) {
                result.add(child);
                if (result.size() >= maxResults) {
                    return false;
                }
            }
        }

        for (File child : sorted) {
            if (child.isDirectory() && !shouldExcludeDirectory(child.getName())) {
                boolean shouldContinue = visitAll(child, maxResults, result);
                if (!shouldContinue) {
                    return false;
                }
            }
        }
        return true;
    }

    private static TreeResult computeAndStringifyDirectoryTree(File item, int maxChars, Counter fileCount,
                                                               int maxDepth, int currentDepth,
                                                               int maxItemsPerDir) {
        if (currentDepth > maxDepth || fileCount.count >= MAX_FILES_TOTAL || maxChars <= 0) {
            return new TreeResult("", true);
        }

        fileCount.count += 1;
        String nodeLine = item.getName() + (item.isDirectory() ? "/" : "") + "\n";
        if (nodeLine.length() > maxChars) {
            return new TreeResult("", true);
        }

        StringBuilder content = new StringBuilder(nodeLine);
        boolean wasCutOff = false;
        int remainingChars = maxChars - nodeLine.length();

        if (item.isDirectory() && !shouldExcludeDirectory(item.getName())) {
            File[] children = item.listFiles();
            if (children != null && children.length > 0) {
                TreeResult childrenResult = renderChildrenCombined(
                        sortedChildren(children),
                        remainingChars,
                        "",
                        fileCount,
                        maxDepth,
                        currentDepth,
                        maxItemsPerDir);
                content.append(childrenResult.content);
                wasCutOff = childrenResult.wasCutOff;
            }
        }

        return new TreeResult(content.toString(), wasCutOff);
    }

    private static TreeResult renderChildrenCombined(List<File> children, int maxChars, String parentPrefix,
                                                     Counter fileCount, int maxDepth, int currentDepth,
                                                     int maxItemsPerDir) {
        int effectiveMaxItemsPerDir = currentDepth == 0 ? Integer.MAX_VALUE : maxItemsPerDir;
        int nextDepth = currentDepth + 1;
        StringBuilder childrenContent = new StringBuilder();
        boolean childrenCutOff = false;
        int remainingChars = maxChars;

        if (nextDepth > maxDepth) {
            return new TreeResult("", true);
        }

        List<File> itemsToProcess = children;
        boolean hasMoreItems = false;
        if (children.size() > effectiveMaxItemsPerDir) {
            itemsToProcess = children.subList(0, effectiveMaxItemsPerDir);
            hasMoreItems = true;
        }

        for (int i = 0; i < itemsToProcess.size(); i++) {
            if (fileCount.count >= MAX_FILES_TOTAL) {
                childrenCutOff = true;
                break;
            }

            File child = itemsToProcess.get(i);
            boolean isLast = i == itemsToProcess.size() - 1 && !hasMoreItems;
            String branchSymbol = isLast ? "`-- " : "|-- ";
            String childLine = parentPrefix + branchSymbol + child.getName() + (child.isDirectory() ? "/" : "") + "\n";

            if (childLine.length() > remainingChars) {
                childrenCutOff = true;
                break;
            }

            childrenContent.append(childLine);
            remainingChars -= childLine.length();
            fileCount.count += 1;

            String nextLevelPrefix = parentPrefix + (isLast ? "    " : "|   ");
            if (child.isDirectory() && !shouldExcludeDirectory(child.getName())) {
                File[] childFiles = child.listFiles();
                if (childFiles != null && childFiles.length > 0) {
                    TreeResult grandChildrenResult = renderChildrenCombined(
                            sortedChildren(childFiles),
                            remainingChars,
                            nextLevelPrefix,
                            fileCount,
                            maxDepth,
                            nextDepth,
                            maxItemsPerDir);
                    if (!grandChildrenResult.content.isEmpty()) {
                        childrenContent.append(grandChildrenResult.content);
                        remainingChars -= grandChildrenResult.content.length();
                    }
                    if (grandChildrenResult.wasCutOff) {
                        childrenCutOff = true;
                    }
                }
            }
        }

        if (hasMoreItems) {
            int remainingCount = children.size() - itemsToProcess.size();
            String truncatedLine = parentPrefix + "`-- (" + remainingCount + " more items not shown...)\n";
            if (truncatedLine.length() <= remainingChars) {
                childrenContent.append(truncatedLine);
            }
            childrenCutOff = true;
        }

        return new TreeResult(childrenContent.toString(), childrenCutOff);
    }

    private static List<File> sortedChildren(File[] children) {
        List<File> files = new ArrayList<>(Arrays.asList(children));
        files.sort(Comparator
                .comparing((File file) -> !file.isDirectory())
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private static final class Counter {
        int count;
    }

    private static final class TreeResult {
        final String content;
        final boolean wasCutOff;

        TreeResult(String content, boolean wasCutOff) {
            this.content = content;
            this.wasCutOff = wasCutOff;
        }
    }
}
