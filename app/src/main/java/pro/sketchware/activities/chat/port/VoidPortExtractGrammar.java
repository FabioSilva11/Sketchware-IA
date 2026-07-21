package pro.sketchware.activities.chat.port;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.activities.chat.PromptConstants;

public final class VoidPortExtractGrammar {
    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";

    private VoidPortExtractGrammar() {
    }

    public static final class ReasoningExtraction {
        public final String fullText;
        public final String fullReasoning;

        public ReasoningExtraction(String fullText, String fullReasoning) {
            this.fullText = fullText == null ? "" : fullText;
            this.fullReasoning = fullReasoning == null ? "" : fullReasoning;
        }
    }

    public static final class ToolCallExtraction {
        public final String cleanedContent;
        public final String toolName;
        public final String toolArguments;
        public final String toolId;

        public ToolCallExtraction(String cleanedContent, String toolName, String toolArguments, String toolId) {
            this.cleanedContent = cleanedContent == null ? "" : cleanedContent;
            this.toolName = toolName == null ? "" : toolName;
            this.toolArguments = toolArguments == null ? "{}" : toolArguments;
            this.toolId = toolId == null ? "" : toolId;
        }
    }

    public static final class XmlToolStreamStep {
        public final String visibleDelta;
        public final String visibleText;
        public final ToolCallExtraction toolCall;

        XmlToolStreamStep(String visibleDelta, String visibleText, ToolCallExtraction toolCall) {
            this.visibleDelta = visibleDelta == null ? "" : visibleDelta;
            this.visibleText = visibleText == null ? "" : visibleText;
            this.toolCall = toolCall;
        }
    }

    public static final class XmlToolStreamParser {
        private final List<String> toolOpenTags = new ArrayList<>();
        private final Map<String, JSONObject> toolFunctions = new HashMap<>();
        private final StringBuilder trueFullText = new StringBuilder();
        private String visibleText = "";
        private String openToolTagBuffer = "";
        private String foundToolName = "";
        private int foundOpenIdx = -1;
        private final String toolId = "xml_call_" + UUID.randomUUID();
        private ToolCallExtraction latestToolCall;

        public XmlToolStreamParser(JSONArray tools) {
            for (int i = 0; tools != null && i < tools.length(); i++) {
                JSONObject tool = tools.optJSONObject(i);
                JSONObject function = tool == null ? null : tool.optJSONObject("function");
                String toolName = function == null ? "" : function.optString("name", "").trim();
                if (toolName.isEmpty()) {
                    continue;
                }
                toolOpenTags.add("<" + toolName + ">");
                toolFunctions.put(toolName, function);
            }
        }

        public boolean isEnabled() {
            return !toolOpenTags.isEmpty();
        }

        public XmlToolStreamStep accept(String newText) {
            String chunk = newText == null ? "" : newText;
            String previousVisible = visibleText;
            trueFullText.append(chunk);

            if (foundToolName.isEmpty()) {
                String newFullText = openToolTagBuffer + chunk;
                if (endsWithAnyToolPrefix(newFullText)) {
                    openToolTagBuffer += chunk;
                } else {
                    String candidate = visibleText + openToolTagBuffer + chunk;
                    openToolTagBuffer = "";
                    FoundToolTag found = findIndexOfAnyToolTag(candidate);
                    if (found != null) {
                        foundToolName = found.toolName;
                        String openTag = "<" + foundToolName + ">";
                        foundOpenIdx = trueFullText.indexOf(openTag);
                        visibleText = candidate.substring(0, found.idx);
                    } else {
                        visibleText = candidate;
                    }
                }
            }

            if (!foundToolName.isEmpty() && foundOpenIdx >= 0) {
                JSONObject function = toolFunctions.get(foundToolName);
                latestToolCall = parseXmlPrefixToToolCall(
                        foundToolName,
                        toolId,
                        trueFullText.substring(foundOpenIdx),
                        function,
                        visibleText
                );
            }

            String delta = "";
            if (visibleText.length() > previousVisible.length()
                    && visibleText.startsWith(previousVisible)) {
                delta = visibleText.substring(previousVisible.length());
            }
            return new XmlToolStreamStep(delta, visibleText, latestToolCall);
        }

        public String getVisibleText() {
            return visibleText;
        }

        public ToolCallExtraction getLatestToolCall() {
            return latestToolCall;
        }

        private boolean endsWithAnyToolPrefix(String text) {
            for (String tag : toolOpenTags) {
                if (endsWithAnyPrefixOf(text, tag)) {
                    return true;
                }
            }
            return false;
        }

        private FoundToolTag findIndexOfAnyToolTag(String text) {
            FoundToolTag best = null;
            for (String tag : toolOpenTags) {
                int idx = text.indexOf(tag);
                if (idx < 0) {
                    continue;
                }
                if (best == null || idx < best.idx) {
                    best = new FoundToolTag(idx, tag.substring(1, tag.length() - 1));
                }
            }
            return best;
        }
    }

    private static final class FoundToolTag {
        final int idx;
        final String toolName;

        FoundToolTag(int idx, String toolName) {
            this.idx = idx;
            this.toolName = toolName;
        }
    }

    public static String readReasoningText(JSONObject jsonObject) {
        String[] keys = new String[] {"reasoning_content", "reasoning", "thinking"};
        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            String value = readStreamText(jsonObject, key);
            if (!value.isEmpty()) {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    public static ReasoningExtraction extractThinkTaggedReasoning(String fullContent) {
        String text = fullContent == null ? "" : fullContent;
        int open = text.indexOf(THINK_OPEN);
        if (open < 0) {
            return new ReasoningExtraction(text, "");
        }

        int close = text.indexOf(THINK_CLOSE, open + THINK_OPEN.length());
        if (close < 0) {
            String visible = text.substring(0, open).trim();
            String reasoning = text.substring(open + THINK_OPEN.length()).trim();
            return new ReasoningExtraction(visible, reasoning);
        }

        String before = text.substring(0, open);
        String reasoning = text.substring(open + THINK_OPEN.length(), close);
        String after = text.substring(close + THINK_CLOSE.length());
        return new ReasoningExtraction((before + after).trim(), reasoning.trim());
    }

    public static ToolCallExtraction extractXmlToolCall(String fullContent, JSONArray tools) {
        if (fullContent == null || fullContent.trim().isEmpty() || tools == null || tools.length() == 0) {
            return null;
        }

        try {
            boolean sawIncompleteToolWrapper = false;
            for (int i = 0; i < tools.length(); i++) {
                JSONObject tool = tools.optJSONObject(i);
                JSONObject function = tool == null ? null : tool.optJSONObject("function");
                String toolName = function == null ? "" : function.optString("name", "").trim();
                if (toolName.isEmpty()) {
                    continue;
                }

                String openTag = "<" + toolName + ">";
                int start = fullContent.indexOf(openTag);
                if (start < 0) {
                    continue;
                }

                String closeTag = "</" + toolName + ">";
                int end = fullContent.indexOf(closeTag, start + openTag.length());
                if (end < 0) {
                    // Never execute a partially streamed or token-truncated XML call.
                    sawIncompleteToolWrapper = true;
                    continue;
                }
                String cleaned = (fullContent.substring(0, start)
                        + fullContent.substring(end + closeTag.length())).trim();
                return parseXmlPrefixToToolCall(
                        toolName,
                        "xml_call_" + UUID.randomUUID(),
                        fullContent.substring(start),
                        function,
                        cleaned
                );
            }

            // An incomplete known wrapper takes precedence over permissive legacy
            // fallbacks below. In particular, do not turn a half-streamed
            // <edit_file> containing SEARCH/REPLACE text into an executable call.
            if (sawIncompleteToolWrapper) {
                return null;
            }

            // Check for known incorrect tools like get_file
            if (fullContent.contains("<get_file>")) {
                int start = fullContent.indexOf("<get_file>");
                int end = fullContent.indexOf("</get_file>", start);
                if (end < 0) {
                    return null;
                }
                String cleaned = (fullContent.substring(0, start)
                        + fullContent.substring(end + 11)).trim();
                
                return new ToolCallExtraction(
                    cleaned,
                    "get_file",
                    "{}",
                    "error_call_" + UUID.randomUUID()
                );
            }

            // Fallback: naked SEARCH/REPLACE blocks without a tool XML wrapper
            if (containsSearchReplaceMarkers(fullContent)) {
                JSONObject params = new JSONObject();
                enrichEditFileParams(params, fullContent);
                if (params.has("uri") && params.has("search_replace_blocks")) {
                    int start = fullContent.indexOf(PromptConstants.ORIGINAL);
                    int end = fullContent.lastIndexOf(PromptConstants.FINAL);
                    String cleaned = fullContent.trim();
                    if (start >= 0 && end >= start) {
                        end += PromptConstants.FINAL.length();
                        cleaned = (fullContent.substring(0, start) + fullContent.substring(end)).trim();
                    }
                    return new ToolCallExtraction(
                            cleaned,
                            "edit_file",
                            params.toString(),
                            "naked_edit_" + UUID.randomUUID()
                    );
                }
            }

        } catch (Exception ignored) {
        }
        return null;
    }

    private static ToolCallExtraction parseXmlPrefixToToolCall(String toolName, String toolId, String xmlPrefix,
                                                               JSONObject function, String cleanedContent) {
        JSONObject params = new JSONObject();
        try {
            String openTag = "<" + toolName + ">";
            String closeTag = "</" + toolName + ">";
            int start = xmlPrefix == null ? -1 : xmlPrefix.indexOf(openTag);
            if (start < 0) {
                return null;
            }
            int end = xmlPrefix.indexOf(closeTag, start + openTag.length());
            if (end < 0) {
                return null;
            }
            String inner = xmlPrefix.substring(start + openTag.length(), end);

            JSONObject schema = function == null ? null : function.optJSONObject("parameters");
            JSONObject properties = schema == null ? null : schema.optJSONObject("properties");
            JSONArray names = properties == null ? null : properties.names();
            for (int j = 0; names != null && j < names.length(); j++) {
                String paramName = names.optString(j, "").trim();
                if (paramName.isEmpty()) {
                    continue;
                }
                String paramOpenTag = "<" + paramName + ">";
                String paramCloseTag = "</" + paramName + ">";
                int paramStart = inner.indexOf(paramOpenTag);
                int paramEnd = inner.indexOf(paramCloseTag);
                if (paramStart < 0 && paramEnd < 0) {
                    continue;
                }
                if (paramStart < 0 || paramEnd < paramStart + paramOpenTag.length()) {
                    return null;
                }
                String paramValue = readXmlTag(inner, paramName);
                if (!paramValue.isEmpty()) {
                    Object typedValue = coerceXmlValue(
                            paramValue,
                            properties.optJSONObject(paramName));
                    if (typedValue != null) {
                        params.put(paramName, typedValue);
                    }
                }
            }

            if ("edit_file".equals(toolName)) {
                enrichEditFileParams(params, inner);
            } else if (params.length() == 0 && !inner.trim().isEmpty() && names != null && names.length() > 0) {
                String firstParam = names.optString(0, "");
                if (!firstParam.isEmpty()) {
                    Object typedValue = coerceXmlValue(
                            inner.trim(),
                            properties.optJSONObject(firstParam));
                    if (typedValue != null) {
                        params.put(firstParam, typedValue);
                    }
                }
            }
            if (!hasAllRequiredParameters(schema, params)) {
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
        return new ToolCallExtraction(cleanedContent, toolName, params.toString(), toolId);
    }

    private static boolean hasAllRequiredParameters(JSONObject schema, JSONObject params) {
        JSONArray required = schema == null ? null : schema.optJSONArray("required");
        for (int i = 0; required != null && i < required.length(); i++) {
            String name = required.optString(i, "").trim();
            if (name.isEmpty() || !params.has(name) || params.isNull(name)) {
                return false;
            }
            Object value = params.opt(name);
            if (value instanceof String && ((String) value).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Object coerceXmlValue(String rawValue, JSONObject propertySchema) {
        String value = rawValue == null ? "" : rawValue.trim();
        String type = propertySchema == null ? "string" : propertySchema.optString("type", "string");
        try {
            if ("integer".equals(type)) {
                return Integer.parseInt(value);
            }
            if ("number".equals(type)) {
                return Double.parseDouble(value);
            }
            if ("boolean".equals(type)) {
                if ("true".equalsIgnoreCase(value)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(value)) {
                    return false;
                }
                return null;
            }
            return rawValue == null ? "" : rawValue;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Fills missing uri / search_replace_blocks when the model uses XML without explicit param tags.
     */
    static void enrichEditFileParams(JSONObject params, String inner) {
        if (params == null) {
            return;
        }
        try {
            String source = inner == null ? "" : inner;

            String uri = params.optString("uri", "").trim();
            if (uri.isEmpty()) {
                uri = findUriInText(source);
                if (!uri.isEmpty()) {
                    params.put("uri", uri);
                }
            }

            String blocks = params.optString("search_replace_blocks", "").trim();
            if (blocks.isEmpty()) {
                blocks = extractSearchReplaceBlocksFromText(source);
                if (blocks.isEmpty()) {
                    blocks = extractSearchReplaceBlocksFromText(stripEditFileWrapperText(source, uri));
                }
                if (!blocks.isEmpty()) {
                    params.put("search_replace_blocks", blocks);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean containsSearchReplaceMarkers(String text) {
        return text != null
                && text.contains(PromptConstants.ORIGINAL)
                && text.contains(PromptConstants.DIVIDER)
                && text.contains(PromptConstants.FINAL);
    }

    public static String extractSearchReplaceBlocksFromText(String text) {
        if (!containsSearchReplaceMarkers(text)) {
            return "";
        }
        int start = text.indexOf(PromptConstants.ORIGINAL);
        int end = text.lastIndexOf(PromptConstants.FINAL);
        if (end < start) {
            return text.substring(start).trim();
        }
        end += PromptConstants.FINAL.length();
        return text.substring(start, end).trim();
    }

    private static String stripEditFileWrapperText(String text, String uri) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String stripped = text;
        if (uri != null && !uri.isEmpty()) {
            stripped = stripped.replace(uri, "");
            stripped = stripped.replace("<uri>" + uri + "</uri>", "");
            stripped = stripped.replace("<uri>\n" + uri + "\n</uri>", "");
        }
        stripped = stripped.replaceAll("(?s)<uri>.*?</uri>", "");
        stripped = stripped.replaceAll("(?s)<search_replace_blocks>.*?</search_replace_blocks>", "");
        return stripped.trim();
    }

    private static final Pattern[] URI_PATTERNS = new Pattern[]{
            Pattern.compile("(/storage/emulated/0/[\\w./\\-]+)"),
            Pattern.compile("(/data/(?:user/0/)?[\\w./\\-]+)"),
            Pattern.compile("(/[\\w./\\-]+\\.(?:java|kt|kts|xml|json|gradle|properties|txt|smali))"),
    };

    public static String findUriInText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String taggedUri = readXmlTag(text, "uri");
        if (!taggedUri.isEmpty()) {
            return taggedUri;
        }
        String best = "";
        for (Pattern pattern : URI_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (candidate.length() > best.length()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    public static String readXmlTag(String xml, String tagName) {
        if (xml == null || tagName == null || tagName.trim().isEmpty()) {
            return "";
        }
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        int end = xml.indexOf(closeTag, start + openTag.length());
        if (start < 0 || end < 0 || end < start) {
            return "";
        }
        return trimBeforeAndAfterNewLines(xml.substring(start + openTag.length(), end));
    }

    private static boolean endsWithAnyPrefixOf(String text, String value) {
        if (text == null || value == null || text.isEmpty() || value.isEmpty()) {
            return false;
        }
        for (int i = value.length(); i >= 1; i--) {
            if (text.endsWith(value.substring(0, i))) {
                return true;
            }
        }
        return false;
    }

    private static String readStreamText(JSONObject jsonObject, String key) {
        if (jsonObject == null || key == null || !jsonObject.has(key) || jsonObject.isNull(key)) {
            return "";
        }
        return sanitizeStreamValue(jsonObject.opt(key));
    }

    private static String sanitizeStreamValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        String text = String.valueOf(value);
        return "null".equalsIgnoreCase(text.trim()) ? "" : text;
    }

    private static String trimBeforeAndAfterNewLines(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String s = value;
        int firstNewLine = s.indexOf('\n');
        if (firstNewLine != -1 && s.substring(0, firstNewLine).trim().isEmpty()) {
            s = s.substring(firstNewLine + 1);
        }
        int lastNewLine = s.lastIndexOf('\n');
        if (lastNewLine != -1 && s.substring(lastNewLine + 1).trim().isEmpty()) {
            s = s.substring(0, lastNewLine);
        }
        return s.trim();
    }
}
