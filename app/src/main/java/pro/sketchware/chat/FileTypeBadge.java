package pro.sketchware.chat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a file extension to a short label + accent color, used as a compact
 * "file type icon" badge (e.g. in the diff file list) instead of drawing a
 * bespoke vector icon per language. Colors loosely follow the conventions
 * most editors/GitHub already use for these languages, so they read as
 * familiar rather than arbitrary.
 */
final class FileTypeBadge {

    static final class Info {
        final String label;
        final int color;

        Info(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final Info DEFAULT = new Info("•", 0xFF9E9E9E);

    private static final Map<String, Info> BY_EXTENSION = build();

    private FileTypeBadge() {
    }

    /** Resolves the badge for a file path/name; falls back to a neutral dot for unknown types. */
    static Info forFileName(String fileNameOrPath) {
        if (fileNameOrPath == null || fileNameOrPath.isEmpty()) {
            return DEFAULT;
        }
        String name = fileNameOrPath;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return DEFAULT;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        Info info = BY_EXTENSION.get(ext);
        return info != null ? info : DEFAULT;
    }

    private static Map<String, Info> build() {
        Map<String, Info> m = new HashMap<>();
        // Web
        put(m, new Info("HTML", 0xFFE44D26), "html", "htm");
        put(m, new Info("CSS", 0xFF2965F1), "css");
        put(m, new Info("SCSS", 0xFFCC6699), "scss", "sass");
        put(m, new Info("JS", 0xFFF0DB4F), "js", "mjs", "cjs");
        put(m, new Info("JSX", 0xFF61DAFB), "jsx");
        put(m, new Info("TS", 0xFF3178C6), "ts");
        put(m, new Info("TSX", 0xFF3178C6), "tsx");
        put(m, new Info("VUE", 0xFF42B883), "vue");
        // Backend / general purpose
        put(m, new Info("PY", 0xFF3776AB), "py", "pyw");
        put(m, new Info("JAVA", 0xFFEA2D2E), "java");
        put(m, new Info("KT", 0xFFA97BFF), "kt", "kts");
        put(m, new Info("C", 0xFF5C6BC0), "c", "h");
        put(m, new Info("C++", 0xFF00599C), "cpp", "cc", "cxx", "hpp");
        put(m, new Info("C#", 0xFF68217A), "cs");
        put(m, new Info("GO", 0xFF00ADD8), "go");
        put(m, new Info("RS", 0xFFDEA584), "rs");
        put(m, new Info("RB", 0xFFCC342D), "rb");
        put(m, new Info("PHP", 0xFF777BB4), "php");
        put(m, new Info("SWIFT", 0xFFFA7343), "swift");
        put(m, new Info("SH", 0xFF4EAA25), "sh", "bash", "zsh");
        // Data / config
        put(m, new Info("JSON", 0xFF8BC34A), "json");
        put(m, new Info("XML", 0xFFFF6F00), "xml");
        put(m, new Info("YML", 0xFFCB171E), "yml", "yaml");
        put(m, new Info("SQL", 0xFF00758F), "sql");
        put(m, new Info("MD", 0xFF607D8B), "md", "markdown");
        put(m, new Info("TXT", 0xFF9E9E9E), "txt");
        put(m, new Info("GRADLE", 0xFF02303A), "gradle");
        put(m, new Info("PROPS", 0xFF757575), "properties", "toml", "ini", "env");
        return m;
    }

    private static void put(Map<String, Info> map, Info info, String... extensions) {
        for (String ext : extensions) {
            map.put(ext, info);
        }
    }
}
