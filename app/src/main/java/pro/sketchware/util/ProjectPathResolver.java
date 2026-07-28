package pro.sketchware.util;

import android.os.Environment;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import a.a.a.lC;
import a.a.a.wq;

/**
 * Resolves project-scoped paths for chat tools so they never escape the
 * currently selected Sketchware or Android Studio project.
 */
public final class ProjectPathResolver {

    private ProjectPathResolver() {
    }

    public static final class ResolvedPath {
        private final File file;
        private final String relativePath;

        public ResolvedPath(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }

        public File getFile() {
            return file;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }

    public static File getSketchwareRoot() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware");
    }

    public static File getAndroidStudioProjectRoot(String scId) {
        return new File(wq.getAndroidStudioProjectPath(scId));
    }

    public static File getDefaultWorkingRoot(String scId) {
        return isAndroidStudioProject(scId) ? getAndroidStudioProjectRoot(scId) : getSketchwareRoot();
    }

    /**
     * Working directory for terminal tools (run_command / persistent terminals).
     * For Sketchware projects this must be the PROJECT's own folder, not the
     * shared {@code .sketchware} root — otherwise commands run at the root of all
     * projects (the "tools escape to the Android root" bug). Falls back to the
     * next existing project folder, and only to the shared root as last resort.
     */
    public static File getTerminalWorkingRoot(String scId) {
        File primary = getPrimaryReadableRoot(scId);
        if (primary != null) {
            return primary;
        }
        // Fail inside a project-specific path instead of silently executing at
        // the shared .sketchware root when project discovery is unavailable.
        return isAndroidStudioProject(scId)
                ? getAndroidStudioProjectRoot(scId)
                : new File(getSketchwareRoot(), "data/" + scId);
    }

    public static List<File> getReadableRoots(String scId) {
        List<File> roots = new ArrayList<>();
        if (isAndroidStudioProject(scId)) {
            addReadableRoot(roots, getAndroidStudioProjectRoot(scId));
            return roots;
        }
        addReadableRoot(roots, new File(getSketchwareRoot(), "data/" + scId));
        addReadableRoot(roots, new File(getSketchwareRoot(), "mysc/" + scId));
        addReadableRoot(roots, new File(getSketchwareRoot(), "mysc/list/" + scId));
        return roots;
    }

    @Nullable
    public static File getPrimaryReadableRoot(String scId) {
        List<File> roots = getReadableRoots(scId);
        return roots.isEmpty() ? null : roots.get(0);
    }

    private static void addReadableRoot(List<File> roots, File root) {
        if (root != null && root.isDirectory()) {
            roots.add(root);
        }
    }

    public static List<File> getWritableRoots(String scId) {
        List<File> roots = new ArrayList<>();
        if (isAndroidStudioProject(scId)) {
            roots.add(getAndroidStudioProjectRoot(scId));
            return roots;
        }
        roots.add(new File(getSketchwareRoot(), "data/" + scId));
        roots.add(new File(getSketchwareRoot(), "mysc/list/" + scId));
        roots.add(new File(getSketchwareRoot(), "mysc/" + scId + "/app"));
        roots.add(new File(getSketchwareRoot(), "mysc/" + scId + "/bin"));
        roots.add(new File(getSketchwareRoot(), "mysc/" + scId + "/gen"));
        return roots;
    }

    public static boolean isAndroidStudioProject(String scId) {
        try {
            java.util.HashMap<String, Object> project = lC.b(scId);
            return project != null && lC.isAndroidStudioProject(project);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String toDisplayPath(String scId, File file) {
        if (file == null) {
            return "";
        }
        try {
            if (isAndroidStudioProject(scId)) {
                File root = getAndroidStudioProjectRoot(scId);
                String relative = root.toPath().relativize(file.toPath()).toString().replace(File.separator, "/");
                if (relative.isEmpty()) {
                    return wq.ANDROID_STUDIO_PROJECTS + "/" + scId;
                }
                return wq.ANDROID_STUDIO_PROJECTS + "/" + scId + "/" + relative;
            }
            return getSketchwareRoot().toPath().relativize(file.toPath()).toString().replace(File.separator, "/");
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    @Nullable
    public static ResolvedPath resolveForRead(String scId, String requestedPath) {
        return resolve(scId, requestedPath, true);
    }

    @Nullable
    public static ResolvedPath resolveForWrite(String scId, String requestedPath) {
        return resolve(scId, requestedPath, false);
    }

    private static ResolvedPath resolve(String scId, String requestedPath, boolean readOnlyScope) {
        if (scId == null || scId.trim().isEmpty() || requestedPath == null) {
            ChatToolLog.pathResolve(scId, String.valueOf(requestedPath), null, false);
            return null;
        }

        if (isPlaceholderPath(requestedPath)) {
            ChatToolLog.w("path", "placeholder path rejected: \"" + requestedPath + "\"");
            ChatToolLog.pathResolve(scId, requestedPath, null, false);
            return null;
        }

        if (readOnlyScope && isReadRootAlias(requestedPath)) {
            File primary = getPrimaryReadableRoot(scId);
            if (primary == null) {
                ChatToolLog.w("path", "project root alias could not be resolved for sc=" + scId);
                ChatToolLog.pathResolve(scId, requestedPath, null, false);
                return null;
            }
            String displayPath = toDisplayPath(scId, primary);
            ChatToolLog.pathResolve(scId, requestedPath, displayPath, true);
            return new ResolvedPath(primary, displayPath);
        }

        String normalizedPath = normalize(requestedPath);
        if (normalizedPath.isEmpty()) {
            ChatToolLog.w("path", "empty/traversal after normalize: \"" + requestedPath + "\"");
            ChatToolLog.pathResolve(scId, requestedPath, null, false);
            return null;
        }

        if (isAndroidStudioProject(scId)) {
            return resolveAndroidStudio(scId, normalizedPath, readOnlyScope);
        }

        String mappedRelativePath = mapToProjectScope(scId, normalizedPath);
        if (mappedRelativePath == null) {
            ChatToolLog.w("path", "OUT_OF_SCOPE (Sketchware project): requested=\"" + requestedPath
                    + "\" normalized=\"" + normalizedPath + "\" — expected data/" + scId
                    + " or mysc/list/" + scId + " prefix");
            ChatToolLog.pathResolve(scId, requestedPath, null, false);
            return null;
        }

        if (!readOnlyScope && !isWritableProjectPath(scId, mappedRelativePath)) {
            ChatToolLog.w("path", "not writable: mapped=\"" + mappedRelativePath + "\"");
            ChatToolLog.pathResolve(scId, requestedPath, mappedRelativePath, false);
            return null;
        }

        File root = getSketchwareRoot();
        File candidate = new File(root, mappedRelativePath.replace("/", File.separator));
        if (!isInsideAllowedRoots(scId, candidate, readOnlyScope)) {
            ChatToolLog.w("path", "candidate escaped allowed roots: " + candidate.getAbsolutePath());
            ChatToolLog.pathResolve(scId, requestedPath, mappedRelativePath, false);
            return null;
        }

        ChatToolLog.pathResolve(scId, requestedPath, mappedRelativePath, true);
        return new ResolvedPath(candidate, mappedRelativePath);
    }

    public static boolean isReadRootAlias(String requestedPath) {
        if (requestedPath == null) {
            return false;
        }
        String value = requestedPath.trim();
        return value.isEmpty() || ".".equals(value) || "/".equals(value) || "\\".equals(value);
    }

    public static boolean isPlaceholderPath(String requestedPath) {
        if (requestedPath == null) {
            return false;
        }
        String value = requestedPath.trim();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return "<uri".equals(lower)
                || "<uri>".equals(lower)
                || "<path".equals(lower)
                || "<path>".equals(lower)
                || "<file_path".equals(lower)
                || "<file_path>".equals(lower)
                || "undefined".equals(lower)
                || "null".equals(lower)
                || (value.startsWith("{{") && value.endsWith("}}"))
                || (value.startsWith("${") && value.endsWith("}"));
    }

    public static boolean hasParentTraversal(String requestedPath) {
        if (requestedPath == null) {
            return false;
        }
        String value = requestedPath.trim().replace("\\", "/");
        for (String segment : value.split("/", -1)) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String requestedPath) {
        String normalizedPath = requestedPath.trim().replace("\\", "/");
        normalizedPath = normalizedPath.replaceAll("/{2,}", "/");

        if (hasParentTraversal(normalizedPath)) {
            return "";
        }

        int androidStudioIndex = normalizedPath.indexOf(wq.ANDROID_STUDIO_PROJECTS + "/");
        if (androidStudioIndex >= 0) {
            normalizedPath = normalizedPath.substring(androidStudioIndex + (wq.ANDROID_STUDIO_PROJECTS + "/").length());
        }

        int sketchwareIndex = normalizedPath.indexOf(".sketchware/");
        if (sketchwareIndex >= 0) {
            normalizedPath = normalizedPath.substring(sketchwareIndex + ".sketchware/".length());
        }

        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        return normalizedPath;
    }

    @Nullable
    private static ResolvedPath resolveAndroidStudio(String scId, String normalizedPath, boolean readOnlyScope) {
        String mappedRelativePath = mapToAndroidStudioScope(scId, normalizedPath);
        if (mappedRelativePath == null) {
            return null;
        }

        if (!readOnlyScope && !isWritableAndroidStudioPath(mappedRelativePath)) {
            return null;
        }

        File root = getAndroidStudioProjectRoot(scId);
        File candidate = mappedRelativePath.isEmpty()
                ? root
                : new File(root, mappedRelativePath.replace("/", File.separator));
        if (!isInsideAllowedRoots(scId, candidate, readOnlyScope)) {
            ChatToolLog.w("path", "AndroidStudio candidate escaped root=" + root.getAbsolutePath()
                    + " candidate=" + candidate.getAbsolutePath());
            ChatToolLog.pathResolve(scId, normalizedPath, mappedRelativePath, false);
            return null;
        }

        ChatToolLog.pathResolve(scId, normalizedPath, mappedRelativePath, true);
        return new ResolvedPath(candidate, toDisplayPath(scId, candidate));
    }

    @Nullable
    private static String mapToAndroidStudioScope(String scId, String normalizedPath) {
        String path = normalizedPath;
        if (path.equals(".") || path.equals(scId) || path.equals(scId + "/")) {
            return "";
        }

        if (path.startsWith(scId + "/")) {
            path = path.substring(scId.length() + 1);
        }

        if (path.equals("project.json")) {
            return "project";
        }

        if (path.startsWith("data/") || path.startsWith("mysc/")) {
            return null;
        }

        return path;
    }

    @Nullable
    private static String mapToProjectScope(String scId, String normalizedPath) {
        // Some model/tool payloads repeat the Sketchware project id after the
        // mysc root (mysc/<id>/<id>/app/...).  That is still a project-local
        // path, so collapse only this exact duplicate before applying scope
        // checks; arbitrary traversal remains rejected by normalize().
        String duplicatedMyscPrefix = "mysc/" + scId + "/" + scId + "/";
        if (normalizedPath.startsWith(duplicatedMyscPrefix)) {
            normalizedPath = "mysc/" + scId + "/"
                    + normalizedPath.substring(duplicatedMyscPrefix.length());
        }
        if (normalizedPath.equals("project") || normalizedPath.equals("project.json")) {
            return "mysc/list/" + scId + "/project";
        }

        String scopedDataPrefix = "data/" + scId;
        String scopedListPrefix = "mysc/list/" + scId;
        String scopedMyscPrefix = "mysc/" + scId;

        if (normalizedPath.equals(scopedDataPrefix)
                || normalizedPath.startsWith(scopedDataPrefix + "/")
                || normalizedPath.equals(scopedListPrefix)
                || normalizedPath.startsWith(scopedListPrefix + "/")
                || normalizedPath.equals(scopedMyscPrefix)
                || normalizedPath.startsWith(scopedMyscPrefix + "/")) {
            return normalizedPath;
        }

        if (normalizedPath.startsWith("data/") || normalizedPath.startsWith("mysc/")) {
            return null;
        }

        return null;
    }

    private static boolean isWritableProjectPath(String scId, String mappedRelativePath) {
        String dataPrefix = "data/" + scId + "/";
        if (mappedRelativePath.startsWith(dataPrefix)) {
            return true;
        }
        if (mappedRelativePath.equals("mysc/list/" + scId + "/project")) {
            return true;
        }
        String myscPrefix = "mysc/" + scId + "/";
        return mappedRelativePath.startsWith(myscPrefix + "app/")
                || mappedRelativePath.startsWith(myscPrefix + "bin/")
                || mappedRelativePath.startsWith(myscPrefix + "gen/");
    }

    private static boolean isWritableAndroidStudioPath(String mappedRelativePath) {
        return !mappedRelativePath.isEmpty() && !mappedRelativePath.equals("project");
    }

    private static boolean isInsideAllowedRoots(String scId, File candidate, boolean readOnlyScope) {
        try {
            String candidatePath = candidate.getCanonicalPath();
            for (File root : readOnlyScope ? getReadableRoots(scId) : getWritableRoots(scId)) {
                String allowedPath = root.getCanonicalPath();
                if (candidatePath.equals(allowedPath) || candidatePath.startsWith(allowedPath + File.separator)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }
}
