package pro.sketchware.chat.toolcalling;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the DeepSeek V4 DSML tool-call protocol. */
public final class DsmlToolParser implements ToolCallParser {
    private static final String BAR = "[|\\uFF5C]";
    private static final String PREFIX = BAR + "DSML" + BAR;
    private static final Pattern TOOL_BLOCK = Pattern.compile(
            "(?is)<\\s*" + PREFIX + "tool_calls\\s*>(.*?)</\\s*" + PREFIX + "tool_calls\\s*>");
    private static final Pattern INVOKE_BLOCK = Pattern.compile(
            "(?is)<\\s*" + PREFIX + "invoke\\b([^>]*)>(.*?)</\\s*" + PREFIX + "invoke\\s*>");
    private static final Pattern PARAMETER_BLOCK = Pattern.compile(
            "(?is)<\\s*" + PREFIX + "parameter\\b([^>]*)>(.*?)</\\s*" + PREFIX + "parameter\\s*>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)\\b([A-Za-z_][A-Za-z0-9_.:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern END_TOKEN = Pattern.compile(
            "(?is)<\\s*" + BAR + "end[^>]*" + BAR + "\\s*>");

    @Override
    public String protocol() {
        return "dsml";
    }

    @Override
    public boolean recognizes(ToolCallResponse response) {
        if (response == null) {
            return false;
        }
        return containsDsml(response.getContent()) || containsDsml(response.getReasoning());
    }

    @Override
    public ToolCallParseResult parse(ToolCallResponse response) {
        ParsedText content = parseText(response == null ? "" : response.getContent(), 0);
        ParsedText reasoning = parseText(
                response == null ? "" : response.getReasoning(), content.calls.size());
        List<ToolCall> calls = new ArrayList<>(content.calls);
        calls.addAll(reasoning.calls);
        return new ToolCallParseResult(protocol(), calls, content.cleaned, reasoning.cleaned);
    }

    private boolean containsDsml(String text) {
        return text != null && Pattern.compile("(?is)<\\s*" + PREFIX + "(?:tool_calls|invoke|parameter)\\b")
                .matcher(text).find();
    }

    private ParsedText parseText(String text, int startingIndex) {
        String source = text == null ? "" : text;
        List<ToolCall> calls = new ArrayList<>();
        Matcher blockMatcher = TOOL_BLOCK.matcher(source);
        StringBuffer cleaned = new StringBuffer(source.length());
        int callIndex = Math.max(0, startingIndex);
        while (blockMatcher.find()) {
            Matcher invokeMatcher = INVOKE_BLOCK.matcher(blockMatcher.group(1));
            while (invokeMatcher.find()) {
                ToolCall call = parseInvoke(invokeMatcher.group(1), invokeMatcher.group(2), callIndex++);
                if (call != null) {
                    calls.add(call);
                }
            }
            blockMatcher.appendReplacement(cleaned, "");
        }
        blockMatcher.appendTail(cleaned);
        String remaining = END_TOKEN.matcher(cleaned.toString()).replaceAll("").trim();
        return new ParsedText(calls, remaining);
    }

    private ToolCall parseInvoke(String rawAttributes, String body, int index) {
        String name = attribute(rawAttributes, "name").trim();
        if (name.isEmpty()) {
            return null;
        }
        JSONObject arguments = new JSONObject();
        Matcher parameterMatcher = PARAMETER_BLOCK.matcher(body == null ? "" : body);
        try {
            while (parameterMatcher.find()) {
                String parameterName = attribute(parameterMatcher.group(1), "name").trim();
                if (parameterName.isEmpty()) {
                    return null;
                }
                String rawValue = decodeEntities(parameterMatcher.group(2));
                boolean stringValue = !"false".equalsIgnoreCase(
                        attribute(parameterMatcher.group(1), "string").trim());
                Object value;
                if (stringValue) {
                    value = stripBoundaryNewlines(rawValue);
                } else {
                    value = new JSONTokener(rawValue.trim()).nextValue();
                }
                arguments.put(parameterName, value);
            }
        } catch (Exception invalidParameter) {
            return null;
        }
        return new ToolCall(name, arguments.toString(), "dsml_" + index);
    }

    private String attribute(String attributes, String expectedName) {
        Matcher matcher = ATTRIBUTE.matcher(attributes == null ? "" : attributes);
        while (matcher.find()) {
            if (!expectedName.equalsIgnoreCase(matcher.group(1))) {
                continue;
            }
            for (int i = 2; i <= 4; i++) {
                String value = matcher.group(i);
                if (value != null) {
                    return value;
                }
            }
        }
        return "";
    }

    private String stripBoundaryNewlines(String value) {
        String result = value == null ? "" : value;
        if (result.startsWith("\r\n")) {
            result = result.substring(2);
        } else if (result.startsWith("\n")) {
            result = result.substring(1);
        }
        if (result.endsWith("\r\n")) {
            result = result.substring(0, result.length() - 2);
        } else if (result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String decodeEntities(String value) {
        return (value == null ? "" : value)
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static final class ParsedText {
        final List<ToolCall> calls;
        final String cleaned;

        ParsedText(List<ToolCall> calls, String cleaned) {
            this.calls = calls;
            this.cleaned = cleaned;
        }
    }
}
