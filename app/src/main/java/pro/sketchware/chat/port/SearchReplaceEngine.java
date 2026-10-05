package pro.sketchware.chat.port;

import pro.sketchware.chat.PromptConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies model-generated SEARCH/REPLACE blocks without partial writes.
 *
 * <p>Exact matches are preferred. A line-aware fallback tolerates line-ending,
 * trailing-space, and uniform indentation differences, but only when the match
 * is unique. Ambiguous or stale blocks fail atomically.</p>
 */
final class SearchReplaceEngine {

    private static final Pattern BLOCK_PATTERN = Pattern.compile(
            "<<<<<<< ORIGINAL[\\s\\t]*\\r?\\n(.*?)\\r?\\n[\\s\\t]*=======[\\s\\t]*\\r?\\n"
                    + "(.*?)\\r?\\n[\\s\\t]*>>>>>>> UPDATED",
            Pattern.DOTALL);

    static final class Result {
        final String content;
        final int blockCount;
        final int appliedCount;
        final int failedBlock;
        final String failureReason;

        private Result(String content, int blockCount, int appliedCount,
                       int failedBlock, String failureReason) {
            this.content = content;
            this.blockCount = blockCount;
            this.appliedCount = appliedCount;
            this.failedBlock = failedBlock;
            this.failureReason = failureReason == null ? "" : failureReason;
        }

        boolean succeeded() {
            return blockCount > 0 && appliedCount == blockCount;
        }
    }

    private enum MatchMode {
        EXACT,
        TRAILING_WHITESPACE,
        INDENTATION
    }

    private static final class Match {
        final int start;
        final int end;
        final MatchMode mode;
        final String actualBaseIndent;
        final String searchBaseIndent;

        Match(int start, int end, MatchMode mode,
              String actualBaseIndent, String searchBaseIndent) {
            this.start = start;
            this.end = end;
            this.mode = mode;
            this.actualBaseIndent = actualBaseIndent;
            this.searchBaseIndent = searchBaseIndent;
        }
    }

    private static final class Line {
        final String text;
        final int start;
        final int end;

        Line(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private SearchReplaceEngine() {
    }

    static Result apply(String content, String searchReplaceBlocks) {
        String original = content == null ? "" : content;
        String lineEnding = preferredLineEnding(original);
        String normalizedOriginal = normalizeLineEndings(original);
        List<String[]> blocks = parseBlocks(searchReplaceBlocks);
        if (blocks.isEmpty()) {
            return new Result(original, 0, 0, 0,
                    "No valid " + PromptConstants.ORIGINAL + "/"
                            + PromptConstants.FINAL + " block was found.");
        }

        String working = normalizedOriginal;
        for (int i = 0; i < blocks.size(); i++) {
            String search = normalizeLineEndings(blocks.get(i)[0]);
            String replacement = normalizeLineEndings(blocks.get(i)[1]);
            if (search.isEmpty()) {
                return failure(original, blocks.size(), i + 1,
                        "The ORIGINAL section is empty.");
            }

            Match match = findUniqueMatch(working, search, false);
            if (match == null) {
                match = findUniqueMatch(working, search, true);
            }
            if (match == null) {
                return failure(original, blocks.size(), i + 1,
                        "The ORIGINAL text is stale, absent, or not unique in the current file.");
            }

            if (match.mode == MatchMode.INDENTATION) {
                replacement = reindentReplacement(
                        replacement, match.searchBaseIndent, match.actualBaseIndent);
            }
            working = working.substring(0, match.start)
                    + replacement
                    + working.substring(match.end);
        }

        return new Result(restoreLineEndings(working, lineEnding),
                blocks.size(), blocks.size(), 0, "");
    }

    private static Result failure(String original, int blockCount,
                                  int failedBlock, String reason) {
        // Always return the original content so callers cannot accidentally
        // persist a subset of a multi-block edit.
        return new Result(original, blockCount, 0, failedBlock, reason);
    }

    private static List<String[]> parseBlocks(String input) {
        List<String[]> blocks = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return blocks;
        }
        Matcher matcher = BLOCK_PATTERN.matcher(input);
        while (matcher.find()) {
            blocks.add(new String[]{matcher.group(1), matcher.group(2)});
        }
        return blocks;
    }

    /**
     * Returns a unique exact match first. When {@code allowIndentationFallback}
     * is true, uses line windows with safe whitespace normalization.
     */
    private static Match findUniqueMatch(String content, String search,
                                         boolean allowIndentationFallback) {
        if (!allowIndentationFallback) {
            int first = content.indexOf(search);
            if (first >= 0 && content.indexOf(search, first + 1) < 0) {
                return new Match(first, first + search.length(), MatchMode.EXACT, "", "");
            }
            if (first >= 0) {
                return null;
            }
        }

        List<Line> contentLines = splitLines(content);
        String[] searchLines = search.split("\\n", -1);
        if (searchLines.length > contentLines.size()) {
            return null;
        }

        Match unique = null;
        for (int startLine = 0;
             startLine + searchLines.length <= contentLines.size();
             startLine++) {
            MatchMode mode = compareWindow(
                    contentLines, startLine, searchLines, allowIndentationFallback);
            if (mode == null) {
                continue;
            }
            if (unique != null) {
                return null;
            }
            Line first = contentLines.get(startLine);
            Line last = contentLines.get(startLine + searchLines.length - 1);
            String actualIndent = baseIndent(contentLines, startLine, searchLines.length);
            String searchIndent = baseIndent(searchLines);
            unique = new Match(first.start, last.end, mode, actualIndent, searchIndent);
        }
        return unique;
    }

    private static MatchMode compareWindow(List<Line> contentLines, int start,
                                           String[] searchLines,
                                           boolean allowIndentationFallback) {
        boolean exactLeadingWhitespace = true;
        for (int i = 0; i < searchLines.length; i++) {
            String actual = trimTrailing(contentLines.get(start + i).text);
            String expected = trimTrailing(searchLines[i]);
            if (!actual.equals(expected)) {
                exactLeadingWhitespace = false;
                break;
            }
        }
        if (exactLeadingWhitespace) {
            return MatchMode.TRAILING_WHITESPACE;
        }
        if (!allowIndentationFallback) {
            return null;
        }

        for (int i = 0; i < searchLines.length; i++) {
            if (!contentLines.get(start + i).text.trim().equals(searchLines[i].trim())) {
                return null;
            }
        }
        return MatchMode.INDENTATION;
    }

    private static List<Line> splitLines(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                lines.add(new Line(text.substring(start, i), start, i));
                start = i + 1;
            }
        }
        return lines;
    }

    private static String baseIndent(List<Line> lines, int start, int length) {
        for (int i = 0; i < length; i++) {
            String text = lines.get(start + i).text;
            if (!text.trim().isEmpty()) {
                return leadingWhitespace(text);
            }
        }
        return "";
    }

    private static String baseIndent(String[] lines) {
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                return leadingWhitespace(line);
            }
        }
        return "";
    }

    private static String reindentReplacement(String replacement,
                                              String searchIndent,
                                              String actualIndent) {
        if (searchIndent.equals(actualIndent) || replacement.isEmpty()) {
            return replacement;
        }
        String[] lines = replacement.split("\\n", -1);
        StringBuilder adjusted = new StringBuilder(replacement.length()
                + Math.max(0, actualIndent.length() - searchIndent.length()) * lines.length);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.trim().isEmpty()) {
                if (!searchIndent.isEmpty() && line.startsWith(searchIndent)) {
                    line = line.substring(searchIndent.length());
                }
                line = actualIndent + line;
            }
            adjusted.append(line);
            if (i + 1 < lines.length) {
                adjusted.append('\n');
            }
        }
        return adjusted.toString();
    }

    private static String normalizeLineEndings(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String preferredLineEnding(String text) {
        if (text.contains("\r\n")) {
            return "\r\n";
        }
        return text.indexOf('\r') >= 0 ? "\r" : "\n";
    }

    private static String restoreLineEndings(String text, String lineEnding) {
        return "\n".equals(lineEnding) ? text : text.replace("\n", lineEnding);
    }

    private static String trimTrailing(String text) {
        int end = text.length();
        while (end > 0) {
            char c = text.charAt(end - 1);
            if (c != ' ' && c != '\t') {
                break;
            }
            end--;
        }
        return text.substring(0, end);
    }

    private static String leadingWhitespace(String text) {
        int end = 0;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c != ' ' && c != '\t') {
                break;
            }
            end++;
        }
        return text.substring(0, end);
    }
}
