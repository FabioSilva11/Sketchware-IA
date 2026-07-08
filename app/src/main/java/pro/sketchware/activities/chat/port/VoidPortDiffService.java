package pro.sketchware.activities.chat.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Android port of Void's browser/helpers/findDiffs.ts.
 */
public final class VoidPortDiffService {
    private static final int MAX_LCS_LINES = 1200;

    public static final class ComputedDiff {
        public final String type;
        public final int startLine;
        public final int endLine;
        public final int originalStartLine;
        public final int originalEndLine;
        public final String originalCode;
        public final String code;

        ComputedDiff(String type,
                     int startLine,
                     int endLine,
                     int originalStartLine,
                     int originalEndLine,
                     String originalCode,
                     String code) {
            this.type = type;
            this.startLine = startLine;
            this.endLine = endLine;
            this.originalStartLine = originalStartLine;
            this.originalEndLine = originalEndLine;
            this.originalCode = originalCode == null ? "" : originalCode;
            this.code = code == null ? "" : code;
        }

        public int addedLines() {
            return countVisibleLines(code);
        }

        public int removedLines() {
            return countVisibleLines(originalCode);
        }
    }

    private enum OpType {
        EQUAL,
        ADD,
        REMOVE
    }

    private static final class Op {
        final OpType type;
        final String line;

        Op(OpType type, String line) {
            this.type = type;
            this.line = line == null ? "" : line;
        }
    }

    private VoidPortDiffService() {
    }

    public static List<ComputedDiff> findDiffs(String oldStr, String newStr) {
        String oldText = normalize(oldStr);
        String newText = normalize(newStr);
        if (oldText.equals(newText)) {
            return Collections.emptyList();
        }

        String[] oldLines = splitLines(oldText);
        String[] newLines = splitLines(newText);

        // Trim the common prefix/suffix before running the O(n·m) LCS. Most
        // edits touch a small region, so this lets even huge files fall inside
        // MAX_LCS_LINES and produce precise hunks instead of one whole-file diff.
        int prefix = 0;
        while (prefix < oldLines.length && prefix < newLines.length
                && oldLines[prefix].equals(newLines[prefix])) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < oldLines.length - prefix && suffix < newLines.length - prefix
                && oldLines[oldLines.length - 1 - suffix].equals(newLines[newLines.length - 1 - suffix])) {
            suffix++;
        }

        String[] oldCore = java.util.Arrays.copyOfRange(oldLines, prefix, oldLines.length - suffix);
        String[] newCore = java.util.Arrays.copyOfRange(newLines, prefix, newLines.length - suffix);

        if (oldCore.length > MAX_LCS_LINES || newCore.length > MAX_LCS_LINES) {
            // Still too large: one hunk covering only the differing region.
            return Collections.singletonList(new ComputedDiff(
                    "edit",
                    prefix + 1,
                    Math.max(prefix + 1, newLines.length - suffix),
                    prefix + 1,
                    Math.max(prefix + 1, oldLines.length - suffix),
                    joinArray(oldCore),
                    joinArray(newCore)
            ));
        }

        return collapseOperations(toOperations(oldCore, newCore), prefix + 1, prefix + 1);
    }

    public static DiffStats stats(String oldStr, String newStr) {
        return statsOf(findDiffs(oldStr, newStr));
    }

    /** Derives stats from an already computed diff list (avoids re-running the LCS). */
    public static DiffStats statsOf(List<ComputedDiff> diffs) {
        int added = 0;
        int removed = 0;
        for (ComputedDiff diff : diffs) {
            added += diff.addedLines();
            removed += diff.removedLines();
        }
        return new DiffStats(added, removed);
    }

    public static String toUnifiedDiff(String filePath, String oldStr, String newStr, int maxChars) {
        List<ComputedDiff> diffs = findDiffs(oldStr, newStr);
        if (diffs.isEmpty()) {
            return "No changes";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("--- ").append(filePath).append(" (before)\n");
        builder.append("+++ ").append(filePath).append(" (after)\n");
        for (ComputedDiff diff : diffs) {
            builder.append("@@ -")
                    .append(diff.originalStartLine)
                    .append(",")
                    .append(Math.max(0, diff.originalEndLine - diff.originalStartLine + 1))
                    .append(" +")
                    .append(diff.startLine)
                    .append(",")
                    .append(Math.max(0, diff.endLine - diff.startLine + 1))
                    .append(" @@ ")
                    .append(diff.type)
                    .append("\n");
            appendPrefixedLines(builder, "-", diff.originalCode, maxChars);
            appendPrefixedLines(builder, "+", diff.code, maxChars);
            if (maxChars > 0 && builder.length() >= maxChars) {
                builder.setLength(maxChars);
                builder.append("\n... diff truncated ...");
                break;
            }
        }
        return builder.toString().trim();
    }

    private static List<Op> toOperations(String[] oldLines, String[] newLines) {
        int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
        for (int i = oldLines.length - 1; i >= 0; i--) {
            for (int j = newLines.length - 1; j >= 0; j--) {
                if (oldLines[i].equals(newLines[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<Op> ops = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < oldLines.length && j < newLines.length) {
            if (oldLines[i].equals(newLines[j])) {
                ops.add(new Op(OpType.EQUAL, oldLines[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                ops.add(new Op(OpType.REMOVE, oldLines[i]));
                i++;
            } else {
                ops.add(new Op(OpType.ADD, newLines[j]));
                j++;
            }
        }
        while (i < oldLines.length) {
            ops.add(new Op(OpType.REMOVE, oldLines[i++]));
        }
        while (j < newLines.length) {
            ops.add(new Op(OpType.ADD, newLines[j++]));
        }
        ops.add(new Op(OpType.EQUAL, ""));
        return ops;
    }

    private static List<ComputedDiff> collapseOperations(List<Op> operations, int startOldLine, int startNewLine) {
        List<ComputedDiff> diffs = new ArrayList<>();
        int oldLineNum = startOldLine;
        int newLineNum = startNewLine;
        int streakNewStart = -1;
        int streakOldStart = -1;
        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();

        for (Op op : operations) {
            if (op.type == OpType.EQUAL) {
                if (streakNewStart > 0) {
                    int newEnd = newLineNum - 1;
                    int oldEnd = oldLineNum - 1;
                    String type = "edit";
                    if (added.isEmpty()) {
                        type = "deletion";
                        newEnd = streakNewStart - 1;
                    } else if (removed.isEmpty()) {
                        type = "insertion";
                        oldEnd = streakOldStart - 1;
                    }
                    diffs.add(new ComputedDiff(
                            type,
                            streakNewStart,
                            newEnd,
                            streakOldStart,
                            oldEnd,
                            joinLines(removed),
                            joinLines(added)
                    ));
                    streakNewStart = -1;
                    streakOldStart = -1;
                    removed.clear();
                    added.clear();
                }
                oldLineNum++;
                newLineNum++;
            } else if (op.type == OpType.REMOVE) {
                if (streakNewStart < 0) {
                    streakNewStart = newLineNum;
                    streakOldStart = oldLineNum;
                }
                removed.add(op.line);
                oldLineNum++;
            } else {
                if (streakNewStart < 0) {
                    streakNewStart = newLineNum;
                    streakOldStart = oldLineNum;
                }
                added.add(op.line);
                newLineNum++;
            }
        }
        return diffs;
    }

    private static String normalize(String value) {
        if (value == null) return "\n";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    private static String[] splitLines(String value) {
        if (value == null || value.isEmpty()) {
            return new String[0];
        }
        String[] lines = value.split("\n", -1);
        if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            String[] withoutTrailingEmpty = new String[lines.length - 1];
            System.arraycopy(lines, 0, withoutTrailingEmpty, 0, withoutTrailingEmpty.length);
            return withoutTrailingEmpty;
        }
        return lines;
    }

    private static String joinArray(String[] lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private static void appendPrefixedLines(StringBuilder builder, String prefix, String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] lines = splitLines(text);
        for (String line : lines) {
            if (maxChars > 0 && builder.length() >= maxChars) {
                return;
            }
            builder.append(prefix).append(" ").append(line).append("\n");
        }
    }

    private static int countVisibleLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return splitLines(text).length;
    }

    public static final class DiffStats {
        public final int added;
        public final int removed;

        DiffStats(int added, int removed) {
            this.added = added;
            this.removed = removed;
        }
    }
}
