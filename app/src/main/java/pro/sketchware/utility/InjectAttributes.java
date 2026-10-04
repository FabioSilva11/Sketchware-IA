package pro.sketchware.utility;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and edits single attributes inside a widget's {@code inject} text without touching the rest
 * of what the user wrote there.
 */
public final class InjectAttributes {

    private static final Pattern ATTRIBUTE = Pattern.compile("([A-Za-z_][\\w:.\\-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    private InjectAttributes() {
    }

    /** Attributes in the order they were written, values without quotes. */
    @NonNull
    public static Map<String, String> parse(@Nullable String inject) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (inject == null) return attributes;
        Matcher matcher = ATTRIBUTE.matcher(inject);
        while (matcher.find()) {
            String quoted = matcher.group(2);
            attributes.put(matcher.group(1), quoted.substring(1, quoted.length() - 1));
        }
        return attributes;
    }

    @Nullable
    public static String get(@NonNull ViewBean bean, @NonNull String name) {
        return parse(bean.inject).get(name);
    }

    /**
     * Sets {@code name} to {@code value}, or removes it when the value is null or empty.
     */
    public static void set(@NonNull ViewBean bean, @NonNull String name, @Nullable String value) {
        bean.inject = set(bean.inject == null ? "" : bean.inject, name, value);
    }

    @NonNull
    public static String set(@NonNull String inject, @NonNull String name, @Nullable String value) {
        Pattern existing = Pattern.compile("(^|\\s)" + Pattern.quote(name) + "\\s*=\\s*(\"[^\"]*\"|'[^']*')");
        Matcher matcher = existing.matcher(inject);
        boolean remove = value == null || value.isEmpty();
        String replacement = remove ? "" : name + "=\"" + escape(value) + "\"";
        if (matcher.find()) {
            String result = inject.substring(0, matcher.start()) + matcher.group(1) + replacement + inject.substring(matcher.end());
            // A removed attribute leaves an empty line behind; drop it.
            return result.replaceAll("\n[ \t]*\n", "\n").trim();
        }
        if (remove) return inject;
        String trimmed = inject.trim();
        return trimmed.isEmpty() ? replacement : trimmed + "\n" + replacement;
    }

    /**
     * Normalizes inject text for the XML generator: one {@code name="value"} per line, keeping spaces
     * inside values (they used to be stripped, so "Send message" became "Sendmessage").
     */
    @NonNull
    public static String normalize(@NonNull String inject) {
        Matcher matcher = ATTRIBUTE.matcher(inject);
        StringBuilder out = new StringBuilder();
        int matched = 0;
        while (matcher.find()) {
            if (out.length() > 0) out.append('\n');
            out.append(matcher.group(1)).append('=').append(matcher.group(2));
            matched++;
        }
        if (matched == 0) {
            return inject.trim();
        }
        return out.toString();
    }

    @NonNull
    public static String escape(@NonNull String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
