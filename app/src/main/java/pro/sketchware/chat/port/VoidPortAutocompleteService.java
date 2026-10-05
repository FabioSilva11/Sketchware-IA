package pro.sketchware.chat.port;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.List;

/**
 * Android port of the useful parts of browser/autocompleteService.ts.
 */
public final class VoidPortAutocompleteService {
    private static final int MAX_CONTEXT_LINES = 25;
    private static final String LINE_BREAK = "\n";

    private VoidPortAutocompleteService() {
    }

    public enum PredictionType {
        SINGLE_LINE_FILL_MIDDLE,
        SINGLE_LINE_REDO_SUFFIX,
        MULTI_LINE_START_ON_NEXT_LINE,
        DO_NOT_PREDICT
    }

    public static final class CompletionResult {
        public final boolean generated;
        public final PredictionType predictionType;
        public final String insertText;
        public final String providerId;
        public final String modelName;
        public final String error;

        CompletionResult(boolean generated, PredictionType predictionType, String insertText,
                         String providerId, String modelName, String error) {
            this.generated = generated;
            this.predictionType = predictionType == null ? PredictionType.DO_NOT_PREDICT : predictionType;
            this.insertText = insertText == null ? "" : insertText;
            this.providerId = providerId == null ? "" : providerId;
            this.modelName = modelName == null ? "" : modelName;
            this.error = error == null ? "" : error;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("generated", generated);
                obj.put("predictionType", predictionType.name());
                obj.put("insertText", insertText);
                obj.put("providerId", providerId);
                obj.put("modelName", modelName);
                obj.put("error", error);
            } catch (Exception ignored) {
            }
            return obj;
        }
    }

    private static final class PrefixAndSuffixInfo {
        final String prefix;
        final String suffix;
        final String prefixToLeftOfCursor;
        final String suffixToRightOfCursor;

        PrefixAndSuffixInfo(String prefix, String suffix, String prefixToLeftOfCursor, String suffixToRightOfCursor) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.prefixToLeftOfCursor = prefixToLeftOfCursor;
            this.suffixToRightOfCursor = suffixToRightOfCursor;
        }
    }

    private static final class CompletionOptions {
        final boolean shouldGenerate;
        final PredictionType predictionType;
        final String llmPrefix;
        final String llmSuffix;
        final List<String> stopTokens;

        CompletionOptions(boolean shouldGenerate, PredictionType predictionType, String llmPrefix,
                          String llmSuffix, List<String> stopTokens) {
            this.shouldGenerate = shouldGenerate;
            this.predictionType = predictionType;
            this.llmPrefix = llmPrefix;
            this.llmSuffix = llmSuffix;
            this.stopTokens = stopTokens;
        }
    }

    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(VoidPortSettings.PREF_ENABLE_AUTOCOMPLETE, false);
    }

    public static String sanitizeCompletion(String value) {
        if (value == null) {
            return "";
        }
        String result = stripCodeFence(value);
        boolean leadingSpace = result.startsWith(" ");
        boolean trailingSpace = result.endsWith(" ");
        result = result.trim();
        return (leadingSpace ? " " : "") + result + (trailingSpace ? " " : "");
    }

    public static CompletionResult complete(Context context, String fullText, int cursorOffset,
                                            String language, String filePath,
                                            boolean justAcceptedAutocompletion) {
        try {
            SharedPreferences prefs = context == null ? null : VoidPortSettings.prefs(context);
            if (!isEnabled(prefs)) {
                return new CompletionResult(false, PredictionType.DO_NOT_PREDICT, "", "", "", "Autocomplete disabled");
            }

            PrefixAndSuffixInfo info = getPrefixAndSuffixInfo(fullText, cursorOffset);
            CompletionOptions options = getCompletionOptions(info, justAcceptedAutocompletion);
            if (!options.shouldGenerate) {
                return new CompletionResult(false, options.predictionType, "", "", "", "");
            }

            String systemPrompt = "You are Sketchware IA Autocomplete. Return only code that belongs between the prefix and suffix. Do not wrap the answer in Markdown.";
            String userPrompt = buildFimInstructionBlock(options.llmPrefix, options.llmSuffix)
                    + "\n\nLanguage: " + nonEmpty(language, "text")
                    + "\nFile: " + nonEmpty(filePath, "unknown")
                    + "\n\nOnly output the missing middle code.";
            VoidPortLlmRequestService.TextResult llmResult = VoidPortLlmRequestService.completeText(
                    context,
                    systemPrompt,
                    userPrompt,
                    options.predictionType == PredictionType.MULTI_LINE_START_ON_NEXT_LINE ? 384 : 160,
                    0.15,
                    options.stopTokens
            );

            String insertText = postprocessAutocompletion(
                    sanitizeCompletion(llmResult.text),
                    options.predictionType,
                    info,
                    options.llmPrefix
            );
            if (options.predictionType == PredictionType.MULTI_LINE_START_ON_NEXT_LINE && !insertText.startsWith(LINE_BREAK)) {
                insertText = LINE_BREAK + insertText;
            }
            return new CompletionResult(!insertText.trim().isEmpty(), options.predictionType, insertText,
                    llmResult.providerId, llmResult.modelName, "");
        } catch (Exception e) {
            return new CompletionResult(false, PredictionType.DO_NOT_PREDICT, "", "", "", e.getMessage());
        }
    }

    public static String buildFimInstructionBlock(String prefix, String suffix) {
        return "Autocomplete/FIM context\n"
                + "<prefix>\n"
                + safe(prefix)
                + "\n</prefix>\n"
                + "<suffix>\n"
                + safe(suffix)
                + "\n</suffix>\n"
                + "Return only the missing middle code.";
    }

    public static String buildPromptSummary(SharedPreferences prefs) {
        if (!isEnabled(prefs)) {
            return "Autocomplete: disabled";
        }
        return "Autocomplete: enabled; Sora editor and chat tools can request FIM-style middle-code completions.";
    }

    private static PrefixAndSuffixInfo getPrefixAndSuffixInfo(String rawText, int cursorOffset) {
        String text = normalizeNewlines(rawText);
        int offset = Math.max(0, Math.min(cursorOffset, text.length()));
        String prefix = text.substring(0, offset);
        String suffix = text.substring(offset);
        String prefixToLeft = lastLine(prefix);
        String suffixToRight = firstLine(suffix);
        return new PrefixAndSuffixInfo(prefix, suffix, prefixToLeft, suffixToRight);
    }

    private static CompletionOptions getCompletionOptions(PrefixAndSuffixInfo info, boolean justAcceptedAutocompletion) {
        String prefix = keepLastLines(info.prefix, MAX_CONTEXT_LINES);
        String suffix = keepFirstLines(info.suffix, MAX_CONTEXT_LINES);
        String prefixLeft = info.prefixToLeftOfCursor;
        String suffixRight = info.suffixToRightOfCursor;
        boolean isLineEmpty = prefixLeft.trim().isEmpty() && suffixRight.trim().isEmpty();
        boolean isLinePrefixEmpty = removeAllWhitespace(prefixLeft).isEmpty();
        boolean isLineSuffixEmpty = removeAllWhitespace(suffixRight).isEmpty();

        if (justAcceptedAutocompletion && isLineSuffixEmpty) {
            return new CompletionOptions(true, PredictionType.MULTI_LINE_START_ON_NEXT_LINE,
                    prefix + LINE_BREAK, suffix, List.of(LINE_BREAK + LINE_BREAK));
        }
        if (isLineEmpty) {
            return new CompletionOptions(true, PredictionType.SINGLE_LINE_FILL_MIDDLE,
                    prefix, suffix, List.of("\r\n", "\n"));
        }
        if (removeAllWhitespace(suffixRight).length() <= 3) {
            String suffixWithoutLine = removeFirstLine(suffix);
            return new CompletionOptions(true, PredictionType.SINGLE_LINE_REDO_SUFFIX,
                    prefix, suffixWithoutLine, List.of("\r\n", "\n"));
        }
        if (!isLinePrefixEmpty) {
            return new CompletionOptions(true, PredictionType.SINGLE_LINE_FILL_MIDDLE,
                    prefix, suffix, List.of("\r\n", "\n"));
        }
        return new CompletionOptions(false, PredictionType.DO_NOT_PREDICT, prefix, suffix, List.of());
    }

    private static String postprocessAutocompletion(String generated, PredictionType type,
                                                    PrefixAndSuffixInfo info, String llmPrefix) {
        String completion = generated == null ? "" : generated;
        if (completion.isEmpty()) {
            return "";
        }

        if ((type == PredictionType.SINGLE_LINE_FILL_MIDDLE || type == PredictionType.SINGLE_LINE_REDO_SUFFIX)
                && info.prefixToLeftOfCursor.trim().length() > 0) {
            int newline = completion.indexOf('\n');
            if (newline >= 0) {
                completion = completion.substring(0, newline);
            }
        }

        if (type == PredictionType.SINGLE_LINE_FILL_MIDDLE && !info.suffixToRightOfCursor.trim().isEmpty()) {
            String suffixTrimmed = info.suffixToRightOfCursor.trim();
            if (!suffixTrimmed.isEmpty()) {
                int match = completion.lastIndexOf(suffixTrimmed.charAt(0));
                if (match > 0 && "{}()[]<>`'\"".indexOf(completion.charAt(match)) >= 0) {
                    completion = completion.substring(0, match);
                }
            }
        }

        if ((info.prefixToLeftOfCursor.endsWith(" ") || info.prefixToLeftOfCursor.endsWith("\t"))
                && completion.startsWith(" ")) {
            completion = completion.replaceFirst("^[ \\t]+", "");
        }

        return getStringUpToUnbalancedClosingParenthesis(completion, llmPrefix);
    }

    private static String getStringUpToUnbalancedClosingParenthesis(String value, String prefix) {
        String opens = "([{";
        String closes = ")]}";
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        String base = prefix == null ? "" : prefix;
        int firstOpen = firstBracketIndex(base);
        if (firstOpen >= 0) {
            for (int i = firstOpen; i < base.length(); i++) {
                pushOrPopBracket(base.charAt(i), stack, opens, closes);
            }
        }
        String generated = value == null ? "" : value;
        for (int i = 0; i < generated.length(); i++) {
            char c = generated.charAt(i);
            if (opens.indexOf(c) >= 0) {
                stack.push(c);
            } else {
                int closeIdx = closes.indexOf(c);
                if (closeIdx >= 0) {
                    if (stack.isEmpty() || stack.pop() != opens.charAt(closeIdx)) {
                        return generated.substring(0, i);
                    }
                }
            }
        }
        return generated;
    }

    private static void pushOrPopBracket(char c, java.util.ArrayDeque<Character> stack, String opens, String closes) {
        int openIdx = opens.indexOf(c);
        if (openIdx >= 0) {
            stack.push(c);
            return;
        }
        int closeIdx = closes.indexOf(c);
        if (closeIdx >= 0) {
            if (!stack.isEmpty() && stack.peek() == opens.charAt(closeIdx)) {
                stack.pop();
            } else {
                stack.push(c);
            }
        }
    }

    private static int firstBracketIndex(String value) {
        int best = -1;
        for (char c : new char[]{'(', '[', '{'}) {
            int idx = value.indexOf(c);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static String normalizeNewlines(String value) {
        return safe(value).replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String keepLastLines(String value, int maxLines) {
        String[] lines = normalizeNewlines(value).split("\n", -1);
        int start = Math.max(0, lines.length - maxLines);
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (i > start) {
                builder.append(LINE_BREAK);
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static String keepFirstLines(String value, int maxLines) {
        String[] lines = normalizeNewlines(value).split("\n", -1);
        int end = Math.min(lines.length, maxLines);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                builder.append(LINE_BREAK);
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static String removeFirstLine(String suffix) {
        String text = normalizeNewlines(suffix);
        int firstBreak = text.indexOf('\n');
        if (firstBreak < 0) {
            return "";
        }
        return text.substring(firstBreak);
    }

    private static String lastLine(String value) {
        String text = normalizeNewlines(value);
        int idx = text.lastIndexOf('\n');
        return idx < 0 ? text : text.substring(idx + 1);
    }

    private static String firstLine(String value) {
        String text = normalizeNewlines(value);
        int idx = text.indexOf('\n');
        return idx < 0 ? text : text.substring(0, idx);
    }

    private static String removeAllWhitespace(String value) {
        return safe(value).replaceAll("\\s+", "");
    }

    private static String stripCodeFence(String value) {
        String text = value.trim();
        if (!text.startsWith("```")) {
            return value;
        }
        int firstNewLine = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstNewLine >= 0 && lastFence > firstNewLine) {
            return text.substring(firstNewLine + 1, lastFence);
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String nonEmpty(String value, String fallback) {
        String safeValue = safe(value).trim();
        return safeValue.isEmpty() ? fallback : safeValue;
    }
}

