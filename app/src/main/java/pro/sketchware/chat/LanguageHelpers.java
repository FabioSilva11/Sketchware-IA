package pro.sketchware.chat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class LanguageHelpers {
    private LanguageHelpers() {
    }

    public static final Map<String, String> MARKDOWN_LANG_TO_VSCODE_LANG;

    static {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("html", "html");
        map.put("css", "css");
        map.put("scss", "scss");
        map.put("sass", "scss");
        map.put("less", "less");
        map.put("javascript", "typescript");
        map.put("js", "typescript");
        map.put("jsx", "typescriptreact");
        map.put("typescript", "typescript");
        map.put("ts", "typescript");
        map.put("tsx", "typescriptreact");
        map.put("json", "json");
        map.put("jsonc", "json");
        map.put("python", "python");
        map.put("py", "python");
        map.put("java", "java");
        map.put("cpp", "cpp");
        map.put("c++", "cpp");
        map.put("c", "c");
        map.put("csharp", "csharp");
        map.put("cs", "csharp");
        map.put("c#", "csharp");
        map.put("go", "go");
        map.put("golang", "go");
        map.put("rust", "rust");
        map.put("rs", "rust");
        map.put("ruby", "ruby");
        map.put("rb", "ruby");
        map.put("php", "php");
        map.put("shell", "shellscript");
        map.put("bash", "shellscript");
        map.put("sh", "shellscript");
        map.put("zsh", "shellscript");
        map.put("markdown", "markdown");
        map.put("md", "markdown");
        map.put("xml", "xml");
        map.put("svg", "xml");
        map.put("yaml", "yaml");
        map.put("yml", "yaml");
        map.put("ini", "ini");
        map.put("toml", "ini");
        map.put("sql", "sql");
        map.put("mysql", "sql");
        map.put("postgresql", "sql");
        map.put("graphql", "graphql");
        map.put("gql", "graphql");
        map.put("dockerfile", "dockerfile");
        map.put("docker", "dockerfile");
        map.put("makefile", "makefile");
        map.put("plaintext", "plaintext");
        map.put("text", "plaintext");
        MARKDOWN_LANG_TO_VSCODE_LANG = Collections.unmodifiableMap(map);
    }

    public static String convertToVscodeLang(String markdownLang) {
        if (markdownLang == null || markdownLang.isEmpty()) {
            return "plaintext";
        }
        String normalized = markdownLang.toLowerCase(Locale.ROOT);
        String mapped = MARKDOWN_LANG_TO_VSCODE_LANG.get(normalized);
        return mapped != null ? mapped : markdownLang;
    }

    public static String detectLanguage(String path, String fileContents) {
        if (path == null || path.isEmpty()) {
            return "plaintext";
        }

        String name = path;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        if ("dockerfile".equals(lowerName)) {
            return "dockerfile";
        }
        if ("makefile".equals(lowerName)) {
            return "makefile";
        }

        int dot = lowerName.lastIndexOf('.');
        if (dot < 0 || dot == lowerName.length() - 1) {
            return "plaintext";
        }
        return convertToVscodeLang(lowerName.substring(dot + 1));
    }
}
