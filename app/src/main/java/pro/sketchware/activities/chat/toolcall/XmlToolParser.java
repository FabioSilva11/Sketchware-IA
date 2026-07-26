package pro.sketchware.activities.chat.toolcall;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XmlToolParser implements ToolCallParser {
    private static final Pattern TOOL_CALL_OPEN = Pattern.compile("(?is)<\\s*tool_call\\b[^>]*>");
    private static final Pattern TOOL_CALL_CLOSE = Pattern.compile("(?is)<\\s*/\\s*tool_call\\s*>");
    private static final Pattern FUNCTION_OPEN = Pattern.compile("(?is)<\\s*function\\b[^>]*>");
    private static final Pattern FUNCTION_CLOSE = Pattern.compile("(?is)<\\s*/\\s*function\\s*>");
    private static final Pattern CHILD_TAG = Pattern.compile("(?is)<\\s*([A-Za-z_][A-Za-z0-9_.:-]*)([^>]*)>(.*?)<\\s*/\\s*\\1\\s*>");
    private static final Pattern PARAMETER_EQUALS_TAG = Pattern.compile("(?is)<\\s*parameter\\s*=\\s*([A-Za-z0-9_.:-]+)\\s*>(.*?)<\\s*/\\s*parameter\\s*>");
    private static final Pattern CDATA = Pattern.compile("(?is)<!\\[CDATA\\[(.*?)]]>");

    @Override
    public String protocol() {
        return "xml";
    }

    @Override
    public boolean recognizes(ToolCallDetector.Response response) {
        return response != null && !parseXml(response.getContent()).calls.isEmpty();
    }

    @Override
    public ToolCallDetector.DetectionResult parse(ToolCallDetector.Response response) {
        Parsed parsed = parseXml(response.getContent());
        return new ToolCallDetector.DetectionResult(
                protocol(),
                ToolCallParsing.compactTextAfterRemoval(response.getContent(), parsed.ranges),
                response.getReasoning(),
                parsed.calls
        );
    }

    private Parsed parseXml(String rawText) {
        Parsed parsed = new Parsed();
        String text = rawText == null ? "" : rawText;
        parseToolCallBlocks(text, parsed);
        parseFunctionBlocks(text, parsed);
        return parsed;
    }

    private void parseToolCallBlocks(String text, Parsed parsed) {
        Matcher openMatcher = TOOL_CALL_OPEN.matcher(text);
        int searchFrom = 0;
        while (searchFrom < text.length() && openMatcher.find(searchFrom)) {
            int openStart = openMatcher.start();
            int bodyStart = openMatcher.end();
            Matcher closeMatcher = TOOL_CALL_CLOSE.matcher(text);
            int blockEnd = text.length();
            int bodyEnd = text.length();
            if (closeMatcher.find(bodyStart)) {
                bodyEnd = closeMatcher.start();
                blockEnd = closeMatcher.end();
            }

            String body = text.substring(bodyStart, Math.max(bodyStart, bodyEnd));
            ToolCall call = parseToolCallBody(body, openMatcher.group());
            if (call != null && call.isValid()) {
                parsed.calls.add(call);
                parsed.ranges.add(new ToolCallParsing.Range(openStart, blockEnd));
            }
            searchFrom = Math.max(blockEnd, bodyStart + 1);
        }
    }

    private ToolCall parseToolCallBody(String body, String openTag) {
        try {
            String normalized = stripCdata(body);
            String name = firstNonEmpty(
                    readFirstTag(normalized, "function"),
                    readFirstTag(normalized, "tool"),
                    attr(openTag, "name"),
                    attr(openTag, "tool"),
                    attr(openTag, "function")
            );
            if (name.isEmpty()) {
                return null;
            }
            JSONObject args = new JSONObject();
            String arguments = readFirstTag(normalized, "arguments");
            if (!arguments.isEmpty()) {
                mergeArguments(args, arguments);
            }
            mergeParameterEqualsTags(args, normalized);
            mergeNamedParameterTags(args, normalized);
            mergeDirectChildTags(args, normalized, "function", "tool", "arguments");
            return new ToolCall(name, args.toString(), firstNonEmpty(attr(openTag, "id"), ToolCallParsing.newId("xml_call")));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void parseFunctionBlocks(String text, Parsed parsed) {
        Matcher openMatcher = FUNCTION_OPEN.matcher(text);
        int searchFrom = 0;
        while (searchFrom < text.length() && openMatcher.find(searchFrom)) {
            int openStart = openMatcher.start();
            int bodyStart = openMatcher.end();
            if (overlaps(parsed.ranges, openStart, bodyStart)) {
                searchFrom = bodyStart;
                continue;
            }
            String openTag = openMatcher.group();
            String name = attr(openTag, "name");
            if (name.isEmpty()) {
                searchFrom = bodyStart;
                continue;
            }

            Matcher closeMatcher = FUNCTION_CLOSE.matcher(text);
            int bodyEnd = text.length();
            int blockEnd = text.length();
            if (closeMatcher.find(bodyStart)) {
                bodyEnd = closeMatcher.start();
                blockEnd = closeMatcher.end();
            }
            if (overlaps(parsed.ranges, openStart, blockEnd)) {
                searchFrom = Math.max(blockEnd, bodyStart + 1);
                continue;
            }

            try {
                JSONObject args = new JSONObject();
                mergeDirectChildTags(args, stripCdata(text.substring(bodyStart, Math.max(bodyStart, bodyEnd))));
                ToolCall call = new ToolCall(name, args.toString(), firstNonEmpty(attr(openTag, "id"), ToolCallParsing.newId("xml_call")));
                if (call.isValid()) {
                    parsed.calls.add(call);
                    parsed.ranges.add(new ToolCallParsing.Range(openStart, blockEnd));
                }
            } catch (Exception ignored) {
            }
            searchFrom = Math.max(blockEnd, bodyStart + 1);
        }
    }

    private void mergeArguments(JSONObject args, String rawArguments) {
        String value = stripCdata(rawArguments).trim();
        if (value.startsWith("{")) {
            try {
                JSONObject json = new JSONObject(value);
                mergeObject(args, json);
                return;
            } catch (Exception ignored) {
            }
        }
        mergeDirectChildTags(args, value);
    }

    private void mergeParameterEqualsTags(JSONObject args, String text) {
        Matcher matcher = PARAMETER_EQUALS_TAG.matcher(text);
        while (matcher.find()) {
            put(args, matcher.group(1), decodeXml(stripCdata(matcher.group(2))));
        }
    }

    private void mergeNamedParameterTags(JSONObject args, String text) {
        Matcher matcher = CHILD_TAG.matcher(text);
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (!"parameter".equalsIgnoreCase(tag)) {
                continue;
            }
            String name = firstNonEmpty(attr(matcher.group(2), "name"), attr(matcher.group(2), "key"));
            if (!name.isEmpty()) {
                put(args, name, decodeXml(stripCdata(matcher.group(3))));
            }
        }
    }

    private void mergeDirectChildTags(JSONObject args, String text, String... ignoredTags) {
        Matcher matcher = CHILD_TAG.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (isIgnored(tag, ignoredTags) || "parameter".equalsIgnoreCase(tag)) {
                continue;
            }
            put(args, tag, decodeXml(stripCdata(matcher.group(3))));
        }
    }

    private void mergeObject(JSONObject target, JSONObject source) {
        if (target == null || source == null) {
            return;
        }
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            put(target, key, source.opt(key));
        }
    }

    private void put(JSONObject args, String key, Object value) {
        if (args == null || key == null || key.trim().isEmpty() || value == null) {
            return;
        }
        try {
            args.put(key.trim(), value);
        } catch (Exception ignored) {
        }
    }

    private String readFirstTag(String text, String tagName) {
        if (text == null || tagName == null || tagName.trim().isEmpty()) {
            return "";
        }
        Pattern pattern = Pattern.compile("(?is)<\\s*" + Pattern.quote(tagName)
                + "\\b[^>]*>(.*?)<\\s*/\\s*" + Pattern.quote(tagName) + "\\s*>");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? decodeXml(stripCdata(matcher.group(1))).trim() : "";
    }

    private String stripCdata(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = CDATA.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String decodeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private String attr(String tag, String name) {
        if (tag == null || name == null || name.trim().isEmpty()) {
            return "";
        }
        Pattern quoted = Pattern.compile("(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*(['\"])(.*?)\\1");
        Matcher quotedMatcher = quoted.matcher(tag);
        if (quotedMatcher.find()) {
            return decodeXml(quotedMatcher.group(2)).trim();
        }
        Pattern unquoted = Pattern.compile("(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*([^\\s>]+)");
        Matcher unquotedMatcher = unquoted.matcher(tag);
        return unquotedMatcher.find() ? decodeXml(unquotedMatcher.group(1)).trim() : "";
    }

    private boolean isIgnored(String value, String... ignored) {
        if (ignored == null) {
            return false;
        }
        for (String item : ignored) {
            if (item != null && item.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(List<ToolCallParsing.Range> ranges, int start, int end) {
        for (ToolCallParsing.Range range : ranges) {
            if (start < range.end && end > range.start) {
                return true;
            }
        }
        return false;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class Parsed {
        final List<ToolCall> calls = new ArrayList<>();
        final List<ToolCallParsing.Range> ranges = new ArrayList<>();
    }
}
