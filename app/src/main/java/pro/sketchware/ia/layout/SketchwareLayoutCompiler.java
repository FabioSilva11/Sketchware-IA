package pro.sketchware.ia.layout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Compiles the structured AI contract into Android XML accepted by ViewBeanParser. */
public final class SketchwareLayoutCompiler {
    public String compile(String response) throws Exception {
        String clean = stripFences(response);
        if (clean.startsWith("<")) return clean;
        JSONObject document = new JSONObject(clean);
        JSONObject root = document.optJSONObject("root");
        if (root == null) throw new IllegalArgumentException("Missing root object");

        String rootType = nonEmpty(root.optString("type"), "LinearLayout");
        Node rootNode = new Node("root", rootType, root.optJSONObject("attributes"));
        JSONArray views = document.optJSONArray("views");
        Map<String, Node> nodes = new LinkedHashMap<>();
        nodes.put("root", rootNode);
        for (int i = 0; views != null && i < views.length(); i++) {
            JSONObject item = views.optJSONObject(i);
            if (item == null) continue;
            String id = nonEmpty(item.optString("id"), "view_" + (i + 1));
            nodes.put(id, new Node(id, nonEmpty(item.optString("type"), "TextView"), item.optJSONObject("attributes")));
        }
        for (int i = 0; views != null && i < views.length(); i++) {
            JSONObject item = views.optJSONObject(i);
            if (item == null) continue;
            Node node = nodes.get(nonEmpty(item.optString("id"), "view_" + (i + 1)));
            Node parent = nodes.get(nonEmpty(item.optString("parent"), "root"));
            (parent == null ? rootNode : parent).children.put(node.id, node);
        }
        StringBuilder xml = new StringBuilder();
        appendNode(xml, rootNode, 0, true);
        return xml.toString();
    }

    public static String contractPrompt() {
        return "Return one JSON object only with this schema: "
                + "{\"root\":{\"type\":\"LinearLayout\",\"attributes\":{}},"
                + "\"views\":[{\"type\":\"TextView\",\"id\":\"text_title\","
                + "\"parent\":\"root\",\"attributes\":{\"android:text\":\"Title\"}}]}. "
                + "Views are flat and parent references an earlier id or root. "
                + "Do not emit XML, markdown, comments or unknown fields.";
    }

    private void appendNode(StringBuilder out, Node node, int depth, boolean root) {
        String indent = "    ".repeat(Math.max(0, depth));
        out.append(indent).append('<').append(node.type);
        if (root) {
            out.append(" xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                    .append(" xmlns:app=\"http://schemas.android.com/apk/res-auto\"");
        } else {
            out.append(" android:id=\"@+id/").append(xml(node.id)).append("\"");
        }
        if (!node.attributes.has("android:layout_width")) {
            out.append(" android:layout_width=\"").append(root ? "match_parent" : "wrap_content").append("\"");
        }
        if (!node.attributes.has("android:layout_height")) {
            out.append(" android:layout_height=\"").append(root ? "match_parent" : "wrap_content").append("\"");
        }
        JSONArray names = node.attributes.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String name = names.optString(i);
            if (name.startsWith("xmlns") || "android:id".equals(name)) continue;
            out.append(' ').append(name).append("=\"").append(xml(node.attributes.optString(name))).append("\"");
        }
        if (node.children.isEmpty()) {
            out.append(" />\n");
            return;
        }
        out.append(">\n");
        for (Node child : node.children.values()) appendNode(out, child, depth + 1, false);
        out.append(indent).append("</").append(node.type).append(">\n");
    }

    private static String stripFences(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.startsWith("```")) {
            int firstNewline = clean.indexOf('\n');
            int lastFence = clean.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) clean = clean.substring(firstNewline + 1, lastFence).trim();
        }
        return clean;
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String xml(String value) {
        return (value == null ? "" : value).replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class Node {
        final String id;
        final String type;
        final JSONObject attributes;
        final Map<String, Node> children = new LinkedHashMap<>();
        Node(String id, String type, JSONObject attributes) {
            this.id = id; this.type = type;
            this.attributes = attributes == null ? new JSONObject() : attributes;
        }
    }
}
