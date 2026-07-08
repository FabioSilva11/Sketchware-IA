package pro.sketchware.util;

import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reads project files while respecting the active project's scope.
 */
public class SketchwareFileDecryptor {
    private static final String ENCRYPTION_KEY = "sketchwaresecure";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public static String decryptFile(String scId, String filePath) {
        try {
            ProjectPathResolver.ResolvedPath resolvedPath = resolvePath(scId, filePath);
            if (resolvedPath == null) {
                return null;
            }

            File file = resolvedPath.getFile();
            if (!file.exists() || file.isDirectory()) {
                return null;
            }

            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            String rawText = new String(fileBytes, StandardCharsets.UTF_8);

            // Code / plain-text files (.java, .kt, .xml, .gradle, .json, ...): the
            // chat is a coding agent, so it must read the EXACT bytes on disk — no
            // AES decryption and no JSON reformatting. Reformatting broke diffs and
            // edits because the returned content no longer matched the file.
            if (isPlainTextFile(file)) {
                return rawText;
            }

            // Extensionless Sketchware internal metadata (project/file/logic/view/...)
            // may be AES-encrypted or plain JSON; keep the legacy handling for those.
            String trimmed = rawText.trim();
            try {
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    return new org.json.JSONObject(trimmed).toString(4);
                } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    return new org.json.JSONArray(trimmed).toString(4);
                }
                if (trimmed.startsWith("<?xml") || trimmed.startsWith("<")) {
                    return rawText;
                }
            } catch (Exception ignored) {
            }

            try {
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                byte[] key = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));

                byte[] decrypted = cipher.doFinal(fileBytes);
                String decryptedString = new String(decrypted, StandardCharsets.UTF_8);
                String decTrimmed = decryptedString.trim();

                try {
                    if (decTrimmed.startsWith("{")) {
                        return new org.json.JSONObject(decTrimmed).toString(4);
                    } else if (decTrimmed.startsWith("[")) {
                        return new org.json.JSONArray(decTrimmed).toString(4);
                    }
                } catch (Exception ignored) {
                }

                return decryptedString;
            } catch (Exception cryptoEx) {
                // Not actually encrypted — return the raw bytes as-is.
                return rawText;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean fileExists(String scId, String filePath) {
        ProjectPathResolver.ResolvedPath resolvedPath = resolvePath(scId, filePath);
        return resolvedPath != null && resolvedPath.getFile().exists();
    }

    private static ProjectPathResolver.ResolvedPath resolvePath(String scId, String filePath) {
        String normalizedPath = filePath;
        if (normalizedPath.endsWith(".json") || normalizedPath.endsWith(".xml")) {
            int extensionIndex = normalizedPath.lastIndexOf(".");
            if (extensionIndex > 0) {
                String pathWithoutExtension = normalizedPath.substring(0, extensionIndex);
                ProjectPathResolver.ResolvedPath withoutExtension = ProjectPathResolver.resolveForRead(scId, pathWithoutExtension);
                if (withoutExtension != null && withoutExtension.getFile().exists()) {
                    return withoutExtension;
                }
            }
        }
        return ProjectPathResolver.resolveForRead(scId, normalizedPath);
    }

    private static boolean isPlainTextFile(File file) {
        String fileName = file.getName();
        if (!fileName.contains(".")) {
            return false;
        }

        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return PLAIN_TEXT_EXTENSIONS.contains(ext);
    }

    /** Extensions treated as plain-text code — never AES-decrypted or reformatted. */
    static final java.util.Set<String> PLAIN_TEXT_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList(
            "xml", "json", "txt", "java", "kt", "kts", "gradle", "properties", "pro",
            "html", "md", "yml", "yaml", "js", "ts", "css", "cfg", "ini", "smali",
            "c", "cpp", "h", "hpp", "sh", "svg"));
}
