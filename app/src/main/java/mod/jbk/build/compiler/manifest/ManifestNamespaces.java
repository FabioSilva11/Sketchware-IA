package mod.jbk.build.compiler.manifest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Declares on {@code <manifest>} the namespace prefixes a manifest uses but never binds.
 * <p>
 * A manifest edited by hand (custom manifest, attribute injections, activity/app components) often uses
 * {@code tools:replace}, {@code tools:node} or {@code tools:ignore} without {@code xmlns:tools}. Android Studio
 * adds the declaration for you; a namespace-aware parser (the library manifest merger, AAPT2) fails instead with
 * "Undefined Prefix: tools".
 */
public final class ManifestNamespaces {
    public static final Map<String, String> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put("android", "http://schemas.android.com/apk/res/android");
        KNOWN.put("tools", "http://schemas.android.com/tools");
        KNOWN.put("app", "http://schemas.android.com/apk/res-auto");
        KNOWN.put("dist", "http://schemas.android.com/apk/distribution");
    }

    private static final Pattern ROOT = Pattern.compile("<manifest(?=[\\s>/])");
    private static final Pattern PREFIXED_NAME = Pattern.compile("[<\\s]([A-Za-z_][\\w.-]*):[A-Za-z_][\\w.-]*(?=\\s*=|[\\s>/])");
    private static final Pattern DECLARATION = Pattern.compile("xmlns:([A-Za-z_][\\w.-]*)\\s*=");

    private ManifestNamespaces() {
    }

    /**
     * @return {@code xml} with the missing {@code xmlns:} declarations of known prefixes added to {@code <manifest>},
     * or {@code xml} itself when nothing is missing or there's no {@code <manifest>} element
     */
    public static String declareUsedPrefixes(String xml) {
        if (xml == null) {
            return null;
        }
        Matcher root = ROOT.matcher(xml);
        if (!root.find()) {
            return xml;
        }
        Set<String> missing = new LinkedHashSet<>();
        String markup = stripCommentsAndText(xml);
        Matcher used = PREFIXED_NAME.matcher(markup);
        Set<String> declared = declaredPrefixes(markup);
        while (used.find()) {
            String prefix = used.group(1);
            if (!"xmlns".equals(prefix) && !"xml".equals(prefix) && !declared.contains(prefix) && KNOWN.containsKey(prefix)) {
                missing.add(prefix);
            }
        }
        if (missing.isEmpty()) {
            return xml;
        }
        StringBuilder declarations = new StringBuilder();
        for (String prefix : missing) {
            declarations.append(" xmlns:").append(prefix).append("=\"").append(KNOWN.get(prefix)).append('"');
        }
        return xml.substring(0, root.end()) + declarations + xml.substring(root.end());
    }

    /**
     * @return The first prefix the manifest uses without declaring it and that isn't a known Android prefix, or null
     */
    public static String findUnknownUndeclaredPrefix(String xml) {
        if (xml == null) {
            return null;
        }
        String markup = stripCommentsAndText(xml);
        Set<String> declared = declaredPrefixes(markup);
        Matcher used = PREFIXED_NAME.matcher(markup);
        while (used.find()) {
            String prefix = used.group(1);
            if (!"xmlns".equals(prefix) && !"xml".equals(prefix) && !declared.contains(prefix) && !KNOWN.containsKey(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    private static Set<String> declaredPrefixes(String markup) {
        Set<String> declared = new LinkedHashSet<>();
        Matcher declaration = DECLARATION.matcher(markup);
        while (declaration.find()) {
            declared.add(declaration.group(1));
        }
        return declared;
    }

    /**
     * Keeps only tags: comments, CDATA and text between tags are dropped and attribute values are emptied, so a
     * value like {@code android:value="tools:x"} isn't mistaken for a prefixed name.
     */
    private static String stripCommentsAndText(String xml) {
        String withoutComments = xml.replaceAll("(?s)<!--.*?-->", " ").replaceAll("(?s)<!\\[CDATA\\[.*?]]>", " ");
        StringBuilder out = new StringBuilder(withoutComments.length());
        boolean inTag = false;
        char quote = 0;
        for (int i = 0; i < withoutComments.length(); i++) {
            char c = withoutComments.charAt(i);
            if (!inTag) {
                if (c == '<') {
                    inTag = true;
                    out.append(c);
                }
                continue;
            }
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                    out.append(c);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                inTag = false;
                out.append(c).append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
