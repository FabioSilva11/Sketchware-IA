package pro.sketchware.chat.toolcalling;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

public final class XmlToolParser implements ToolCallParser {
    private static final Pattern ROOT_OPEN = Pattern.compile(
            "(?is)<\\s*(tool_call|function)\\b[^>]*>");
    private static final Pattern LEGACY_PARAMETER = Pattern.compile(
            "(?is)<\\s*parameter\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([\\w.-]+))\\s*>(.*?)</\\s*parameter\\s*>");
    private static final Pattern CHILD = Pattern.compile(
            "(?is)<\\s*([A-Za-z_][\\w.-]*)\\b[^>]*>(.*?)</\\s*\\1\\s*>");
    private static final Pattern NAME_ATTRIBUTE = Pattern.compile(
            "(?is)\\b(?:name|tool|function)\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))");
    private static final Pattern ID_ATTRIBUTE = Pattern.compile(
            "(?is)\\bid\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))");

    @Override
    public String protocol() {
        return "xml";
    }

    @Override
    public boolean recognizes(ToolCallResponse response) {
        if (response == null) {
            return false;
        }
        return !parseText(response.getContent(), response.getAvailableToolNames()).calls.isEmpty()
                || !parseText(response.getReasoning(), response.getAvailableToolNames()).calls.isEmpty();
    }

    @Override
    public ToolCallParseResult parse(ToolCallResponse response) {
        ParsedText content = parseText(
                response.getContent(), response.getAvailableToolNames());
        ParsedText reasoning = parseText(
                response.getReasoning(), response.getAvailableToolNames());
        List<ToolCall> calls = new ArrayList<>(content.calls);
        calls.addAll(reasoning.calls);
        return new ToolCallParseResult(
                protocol(),
                calls,
                content.cleaned,
                reasoning.cleaned
        );
    }

    private ParsedText parseText(String text, List<String> availableToolNames) {
        String source = text == null ? "" : text;
        List<XmlBlock> blocks = findBlocks(source, availableToolNames);
        List<ToolCall> calls = new ArrayList<>();
        List<XmlBlock> matched = new ArrayList<>();
        for (XmlBlock block : blocks) {
            ToolCall call = parseBlock(block);
            if (call != null && call.isValid()) {
                calls.add(call);
                matched.add(block);
            }
        }
        return new ParsedText(calls, removeBlocks(source, matched));
    }

    private List<XmlBlock> findBlocks(String text, List<String> availableToolNames) {
        List<XmlBlock> blocks = new ArrayList<>();
        Matcher matcher = ROOT_OPEN.matcher(text);
        int cursor = 0;
        while (matcher.find(cursor)) {
            int start = matcher.start();
            int openEnd = matcher.end();
            String tag = matcher.group(1).toLowerCase(Locale.US);

            // A nested <function> belongs to the enclosing <tool_call>.
            if ("function".equals(tag)
                    && !attribute(matcher.group(), NAME_ATTRIBUTE).isEmpty()
                    && isInsideExistingBlock(start, blocks)) {
                cursor = openEnd;
                continue;
            }

            String closeTag = "</" + tag + ">";
            int close = indexOfIgnoreCase(text, closeTag, openEnd);
            boolean complete = close >= 0;
            int end = complete
                    ? close + closeTag.length()
                    : recoverBlockEnd(text, openEnd);
            if (end <= openEnd) {
                cursor = openEnd;
                continue;
            }
            blocks.add(new XmlBlock(start, end, text.substring(start, end), tag, complete));
            cursor = end;
        }
        for (String toolName : availableToolNames) {
            addDirectToolBlocks(text, toolName, blocks);
        }
        Collections.sort(blocks, Comparator.comparingInt(block -> block.start));
        return blocks;
    }

    private void addDirectToolBlocks(String text, String toolName, List<XmlBlock> blocks) {
        String safeName = toolName == null ? "" : toolName.trim();
        if (safeName.isEmpty()
                || "tool_call".equalsIgnoreCase(safeName)
                || "function".equalsIgnoreCase(safeName)) {
            return;
        }
        Pattern openingPattern = Pattern.compile(
                "(?is)<\\s*" + Pattern.quote(safeName) + "\\b[^>]*>");
        Matcher opening = openingPattern.matcher(text);
        int cursor = 0;
        while (opening.find(cursor)) {
            int start = opening.start();
            int openEnd = opening.end();
            if (isInsideExistingBlock(start, blocks)) {
                cursor = openEnd;
                continue;
            }
            String closeTag = "</" + safeName + ">";
            int close = indexOfIgnoreCase(text, closeTag, openEnd);
            boolean complete = close >= 0;
            int end = complete
                    ? close + closeTag.length()
                    : recoverBlockEnd(text, openEnd);
            if (end > openEnd) {
                blocks.add(new XmlBlock(
                        start,
                        end,
                        text.substring(start, end),
                        safeName,
                        complete));
            }
            cursor = Math.max(openEnd, end);
        }
    }

    private ToolCall parseBlock(XmlBlock block) {
        if (block.raw.toUpperCase(Locale.US).contains("<!DOCTYPE")) {
            return null;
        }
        try {
            ToolCall parsed = parseDom(block);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
            // Invalid XML falls through to the conservative recovery parser.
        }
        try {
            return parseRecovered(block);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ToolCall parseDom(XmlBlock block) throws Exception {
        if (!block.complete || block.raw.toUpperCase(Locale.US).contains("<!DOCTYPE")) {
            return null;
        }
        String repaired = LEGACY_PARAMETER.matcher(block.raw)
                .replaceAll("<parameter name=\"$1$2$3\">$4</parameter>");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(repaired)));
        Element root = document.getDocumentElement();
        JSONObject arguments = new JSONObject();
        String id = root.getAttribute("id");
        String name;

        if ("function".equalsIgnoreCase(root.getTagName())) {
            name = firstNonEmpty(
                    root.getAttribute("name"),
                    root.getAttribute("tool"),
                    root.getAttribute("function"));
            addAttributeArguments(root, arguments, "name", "tool", "function", "id");
            addChildArguments(root, arguments, "name", "tool", "function", "id");
        } else if ("tool_call".equalsIgnoreCase(root.getTagName())) {
            name = firstNonEmpty(
                    root.getAttribute("name"),
                    root.getAttribute("tool"),
                    root.getAttribute("function"));
            addAttributeArguments(root, arguments, "name", "tool", "function", "id");
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element child = (Element) node;
                String tag = child.getTagName();
                if ("function".equalsIgnoreCase(tag) || "tool".equalsIgnoreCase(tag)) {
                    name = firstNonEmpty(
                            name,
                            child.getAttribute("name"),
                            child.getTextContent());
                    addChildArguments(
                            child,
                            arguments,
                            "name", "tool", "function", "id");
                } else if ("arguments".equalsIgnoreCase(tag)
                        || "parameters".equalsIgnoreCase(tag)
                        || "args".equalsIgnoreCase(tag)) {
                    addArgumentsElement(child, arguments);
                } else if ("parameter".equalsIgnoreCase(tag)) {
                    String parameterName = firstNonEmpty(
                            child.getAttribute("name"),
                            child.getAttribute("key"));
                    if (!parameterName.isEmpty()) {
                        putArgument(arguments, parameterName, elementValue(child));
                    }
                } else if ("id".equalsIgnoreCase(tag)) {
                    id = firstNonEmpty(id, child.getTextContent());
                } else {
                    putArgument(arguments, tag, elementValue(child));
                }
            }
        } else {
            name = root.getTagName();
            addAttributeArguments(root, arguments, "id");
            addChildArguments(root, arguments, "id");
        }
        return createCall(name, arguments, id, block);
    }

    private ToolCall parseRecovered(XmlBlock block) {
        String raw = block.raw;
        int openEnd = raw.indexOf('>');
        String opening = openEnd >= 0 ? raw.substring(0, openEnd + 1) : raw;
        String name = attribute(opening, NAME_ATTRIBUTE);
        String id = attribute(opening, ID_ATTRIBUTE);
        JSONObject arguments = new JSONObject();

        Matcher legacy = LEGACY_PARAMETER.matcher(raw);
        while (legacy.find()) {
            String parameterName = firstNonEmpty(
                    legacy.group(1), legacy.group(2), legacy.group(3));
            putArgument(arguments, parameterName, scalar(xmlText(legacy.group(4))));
        }

        Matcher childMatcher = CHILD.matcher(raw);
        while (childMatcher.find()) {
            String tag = childMatcher.group(1);
            String inner = childMatcher.group(2);
            if ("function".equalsIgnoreCase(tag) || "tool".equalsIgnoreCase(tag)) {
                name = firstNonEmpty(name, xmlText(inner));
            } else if ("arguments".equalsIgnoreCase(tag)
                    || "parameters".equalsIgnoreCase(tag)
                    || "args".equalsIgnoreCase(tag)) {
                addRecoveredArguments(inner, arguments);
            } else if (!"tool_call".equalsIgnoreCase(tag)
                    && !"parameter".equalsIgnoreCase(tag)
                    && !"id".equalsIgnoreCase(tag)) {
                putArgument(arguments, tag, scalar(xmlText(inner)));
            }
        }

        if ("function".equals(block.tag) && name.isEmpty()) {
            name = attribute(opening, NAME_ATTRIBUTE);
        } else if (!"tool_call".equals(block.tag) && name.isEmpty()) {
            name = block.tag;
        }
        return createCall(name, arguments, id, block);
    }

    private void addArgumentsElement(Element element, JSONObject arguments) {
        List<Element> children = childElements(element);
        if (children.isEmpty()) {
            String text = element.getTextContent() == null
                    ? ""
                    : element.getTextContent().trim();
            try {
                JSONObject object = new JSONObject(text);
                copyObject(object, arguments);
            } catch (Exception ignored) {
                if (!text.isEmpty()) {
                    putArgument(arguments, "value", scalar(text));
                }
            }
            return;
        }
        for (Element child : children) {
            if ("parameter".equalsIgnoreCase(child.getTagName())) {
                String name = firstNonEmpty(
                        child.getAttribute("name"),
                        child.getAttribute("key"));
                putArgument(arguments, name, elementValue(child));
            } else {
                putArgument(arguments, child.getTagName(), elementValue(child));
            }
        }
    }

    private void addRecoveredArguments(String inner, JSONObject arguments) {
        String text = xmlText(inner);
        try {
            copyObject(new JSONObject(text), arguments);
            return;
        } catch (Exception ignored) {
        }
        Matcher children = CHILD.matcher(inner);
        while (children.find()) {
            putArgument(
                    arguments,
                    children.group(1),
                    scalar(xmlText(children.group(2))));
        }
    }

    private void addChildArguments(Element parent, JSONObject arguments,
                                   String... ignoredNames) {
        for (Element child : childElements(parent)) {
            if (!containsIgnoreCase(ignoredNames, child.getTagName())) {
                putArgument(arguments, child.getTagName(), elementValue(child));
            }
        }
    }

    private void addAttributeArguments(Element element, JSONObject arguments,
                                       String... ignoredNames) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (!containsIgnoreCase(ignoredNames, attribute.getNodeName())) {
                putArgument(arguments, attribute.getNodeName(), scalar(attribute.getNodeValue()));
            }
        }
    }

    private Object elementValue(Element element) {
        List<Element> children = childElements(element);
        if (children.isEmpty()) {
            return scalar(element.getTextContent());
        }
        JSONObject object = new JSONObject();
        for (Element child : children) {
            putArgument(object, child.getTagName(), elementValue(child));
        }
        return object;
    }

    private ToolCall createCall(String name, JSONObject arguments, String id, XmlBlock block) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty() || (!block.complete && arguments.length() == 0)) {
            return null;
        }
        return new ToolCall(
                safeName,
                arguments.toString(),
                id == null || id.trim().isEmpty()
                        ? "xml_call_" + UUID.randomUUID()
                        : id.trim()
        );
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                elements.add((Element) children.item(i));
            }
        }
        return elements;
    }

    private static void putArgument(JSONObject object, String name, Object value) {
        if (object == null || name == null || name.trim().isEmpty() || value == null) {
            return;
        }
        try {
            String key = name.trim();
            if (!object.has(key)) {
                object.put(key, value);
                return;
            }
            Object existing = object.opt(key);
            JSONArray values;
            if (existing instanceof JSONArray) {
                values = (JSONArray) existing;
            } else {
                values = new JSONArray();
                values.put(existing);
                object.put(key, values);
            }
            values.put(value);
        } catch (Exception ignored) {
        }
    }

    private static void copyObject(JSONObject source, JSONObject target) {
        JSONArray names = source.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String name = names.optString(i, "");
            putArgument(target, name, source.opt(name));
        }
    }

    private static Object scalar(String raw) {
        String value = raw == null ? "" : raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("null".equalsIgnoreCase(value)) {
            return JSONObject.NULL;
        }
        try {
            if (value.matches("-?\\d+")) {
                return Long.parseLong(value);
            }
            if (value.matches("-?(?:\\d+\\.\\d*|\\d*\\.\\d+)")) {
                return Double.parseDouble(value);
            }
        } catch (NumberFormatException ignored) {
        }
        return value;
    }

    private static String xmlText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("<![CDATA[") && text.endsWith("]]>")) {
            text = text.substring(9, text.length() - 3);
        }
        return text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .trim();
    }

    private static String attribute(String openingTag, Pattern pattern) {
        Matcher matcher = pattern.matcher(openingTag == null ? "" : openingTag);
        if (!matcher.find()) {
            return "";
        }
        return firstNonEmpty(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static int recoverBlockEnd(String text, int openEnd) {
        Matcher next = ROOT_OPEN.matcher(text);
        int limit = next.find(openEnd) ? next.start() : text.length();
        Pattern closing = Pattern.compile("(?is)</\\s*[A-Za-z_][\\w.-]*\\s*>");
        Matcher matcher = closing.matcher(text);
        matcher.region(openEnd, limit);
        int recovered = -1;
        while (matcher.find()) {
            recovered = matcher.end();
        }
        return recovered > openEnd ? recovered : limit;
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        return text.toLowerCase(Locale.US).indexOf(
                needle.toLowerCase(Locale.US), Math.max(0, from));
    }

    private static boolean isInsideExistingBlock(int position, List<XmlBlock> blocks) {
        for (XmlBlock block : blocks) {
            if (position > block.start && position < block.end) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String[] values, String candidate) {
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void setFeature(DocumentBuilderFactory factory,
                                   String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
        }
    }

    private static String removeBlocks(String text, List<XmlBlock> blocks) {
        if (blocks.isEmpty()) {
            return text;
        }
        List<XmlBlock> ordered = new ArrayList<>(blocks);
        Collections.sort(ordered, Comparator.comparingInt(block -> block.start));
        StringBuilder cleaned = new StringBuilder(text.length());
        int cursor = 0;
        for (XmlBlock block : ordered) {
            if (block.start < cursor) {
                continue;
            }
            cleaned.append(text, cursor, block.start);
            cursor = block.end;
        }
        cleaned.append(text, cursor, text.length());
        return cleaned.toString().trim();
    }

    private static final class XmlBlock {
        final int start;
        final int end;
        final String raw;
        final String tag;
        final boolean complete;

        XmlBlock(int start, int end, String raw, String tag, boolean complete) {
            this.start = start;
            this.end = end;
            this.raw = raw;
            this.tag = tag;
            this.complete = complete;
        }
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
