package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.workspace.WorkspaceFileSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic project discovery for the {@link ProjectSnapshot}: reads the
 * build files of THIS run's filesystem and fills only what it actually
 * verifies. Gradle projects expose modules/namespace/applicationId from the
 * real {@code settings.gradle}/{@code build.gradle}; everything else stays
 * {@link ProjectSnapshot#UNKNOWN} — discovery never invents values, and a
 * shallow tree scan is never treated as semantic analysis (the agent digs
 * deeper with its tools when a needed fact is UNKNOWN).
 */
public final class ProjectDiscovery {

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("namespace\\s*[=\\s]+[\"']([^\"']+)[\"']");
    private static final Pattern APPLICATION_ID_PATTERN =
            Pattern.compile("applicationId\\s*[=\\s]+[\"']([^\"']+)[\"']");
    private static final Pattern PACKAGE_ATTRIBUTE_PATTERN =
            Pattern.compile("package\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern MODULE_LINE_PATTERN =
            Pattern.compile("(?m)^\\s*include(?:Dir)?\\s*[=]?\\s*[\"']([^\"']+)[\"']");
    private static final int MAX_SNIPPET_CHARS = 64_000;

    private ProjectDiscovery() {
    }

    /** Discovers what the filesystem actually proves; the rest stays UNKNOWN. */
    public static ProjectSnapshot discover(WorkspaceFileSystem fs, String cwd) {
        ProjectSnapshot.Builder builder = new ProjectSnapshot.Builder()
                .root(fs == null ? ProjectSnapshot.UNKNOWN : ProjectSnapshot.UNKNOWN)
                .cwd(cwd == null || cwd.isEmpty() ? ProjectSnapshot.UNKNOWN : cwd)
                // Seeded UNKNOWN so the discovery loop knows these are still
                // unverified and overwrites them only with real matches.
                .applicationId(ProjectSnapshot.UNKNOWN)
                .namespace(ProjectSnapshot.UNKNOWN);

        if (fs == null) {
            return builder.build();
        }
        if (fs instanceof pro.sketchware.chat.workspace.SketchwareProjectFileSystem sketchware) {
            return discoverSketchware(sketchware, builder);
        }

        boolean gradle = fs.exists("build.gradle") || fs.exists("build.gradle.kts")
                || fs.exists("settings.gradle") || fs.exists("settings.gradle.kts");
        boolean androidManifest = false;
        List<String> modules = new ArrayList<>();

        if (gradle) {
            builder.buildSystem("Gradle");
            modules.addAll(discoverModules(fs));
            if (!modules.isEmpty()) {
                builder.modules(modules);
            }
            // applicationId/namespace come from the app module's build file;
            // only real matches are set — misspelled/absent values stay UNKNOWN.
            for (String candidate : appModuleBuildFiles(modules)) {
                String text = readFileSafe(fs, candidate);
                if (text == null) {
                    continue;
                }
                if (ProjectSnapshot.UNKNOWN.equals(builderApplicationId(builder))) {
                    Matcher m = APPLICATION_ID_PATTERN.matcher(text);
                    if (m.find()) {
                        builder.applicationId(m.group(1));
                    }
                }
                if (ProjectSnapshot.UNKNOWN.equals(builderNamespace(builder))) {
                    Matcher m = NAMESPACE_PATTERN.matcher(text);
                    if (m.find()) {
                        builder.namespace(m.group(1));
                    }
                }
            }
            androidManifest = fs.exists("app/src/main/AndroidManifest.xml");
            for (String module : modules) {
                if (fs.exists(module + "/src/main/AndroidManifest.xml")) {
                    androidManifest = true;
                    break;
                }
            }
            if (androidManifest) {
                String manifestPackage = readManifestPackage(fs, modules);
                if (manifestPackage != null
                        && ProjectSnapshot.UNKNOWN.equals(builderNamespace(builder))) {
                    builder.namespace(manifestPackage);
                }
            }
            if (!modules.isEmpty()) {
                builder.projectType("Android (Gradle)");
            }
            addSourceRoots(fs, builder);
        } else if (fs.exists("package.json")) {
            builder.buildSystem("npm");
            builder.projectType("Node.js");
            addSourceRoots(fs, builder);
        } else if (fs.exists("pom.xml")) {
            builder.buildSystem("Maven");
            builder.projectType("Java (Maven)");
            addSourceRoots(fs, builder);
        } else {
            builder.buildSystem(ProjectSnapshot.UNKNOWN);
            builder.projectType(ProjectSnapshot.UNKNOWN);
        }

        addRelevantFiles(fs, builder);
        return builder.build();
    }

    /**
     * A native Sketchware project: its source is the (decrypted) project files in data/&lt;id&gt;, and the package
     * name is the one saved in mysc/list/&lt;id&gt;/project. Only what the files prove is filled in.
     */
    private static ProjectSnapshot discoverSketchware(pro.sketchware.chat.workspace.SketchwareProjectFileSystem fs,
                                                      ProjectSnapshot.Builder builder) {
        String id = fs.getScId();
        builder.projectType("Sketchware (native project)")
                .root(".sketchware (data/" + id + ", mysc/" + id + ", mysc/list/" + id + ")")
                .buildSystem("Sketchware: generates the Gradle project mysc/" + id + " from data/" + id + " on every build");
        String project = readFileSafe(fs, "mysc/list/" + id + "/project");
        if (project != null) {
            try {
                String packageName = new org.json.JSONObject(project).optString("my_sc_pkg_name", "");
                if (!packageName.isEmpty()) {
                    builder.applicationId(packageName).namespace(packageName);
                }
            } catch (org.json.JSONException ignored) {
            }
        }
        List<String> sourceRoots = new ArrayList<>();
        for (String candidate : new String[]{"data/" + id, "data/" + id + "/files/java", "data/" + id + "/files/resource"}) {
            if (fs.isDirectory(candidate)) {
                sourceRoots.add(candidate);
            }
        }
        if (!sourceRoots.isEmpty()) {
            builder.sourceRoots(sourceRoots);
        }
        List<String> files = new ArrayList<>();
        for (String name : new String[]{"file", "view", "logic", "library", "resource"}) {
            if (fs.exists("data/" + id + "/" + name)) {
                files.add("data/" + id + "/" + name);
            }
        }
        if (fs.exists("mysc/list/" + id + "/project")) {
            files.add("mysc/list/" + id + "/project");
        }
        if (!files.isEmpty()) {
            builder.relevantFiles(files);
        }
        return builder.build();
    }

    /** Modules declared in settings.gradle(.kts) (single-module app = "app"). */
    private static List<String> discoverModules(WorkspaceFileSystem fs) {
        List<String> modules = new ArrayList<>();
        for (String settings : new String[]{"settings.gradle", "settings.gradle.kts"}) {
            String text = readFileSafe(fs, settings);
            if (text == null) {
                continue;
            }
            Matcher m = MODULE_LINE_PATTERN.matcher(text);
            while (m.find()) {
                String module = m.group(1).replace(":", "").replace("/", "");
                if (!module.isEmpty() && !modules.contains(module)) {
                    modules.add(module);
                }
            }
            if (!modules.isEmpty()) {
                break;
            }
        }
        if (modules.isEmpty() && (fs.exists("app/build.gradle") || fs.exists("app/build.gradle.kts"))) {
            modules.add("app");
        }
        return modules;
    }

    private static List<String> appModuleBuildFiles(List<String> modules) {
        List<String> candidates = new ArrayList<>();
        if (modules.isEmpty()) {
            candidates.add("build.gradle");
            candidates.add("build.gradle.kts");
        } else {
            for (String module : modules) {
                candidates.add(module + "/build.gradle");
                candidates.add(module + "/build.gradle.kts");
            }
        }
        return candidates;
    }

    private static String readManifestPackage(WorkspaceFileSystem fs, List<String> modules) {
        List<String> manifests = new ArrayList<>();
        if (modules.isEmpty()) {
            manifests.add("src/main/AndroidManifest.xml");
        } else {
            for (String module : modules) {
                manifests.add(module + "/src/main/AndroidManifest.xml");
            }
        }
        for (String manifest : manifests) {
            String text = readFileSafe(fs, manifest);
            if (text == null) {
                continue;
            }
            Matcher m = PACKAGE_ATTRIBUTE_PATTERN.matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /** Standard source roots that actually exist (never invented). */
    private static void addSourceRoots(WorkspaceFileSystem fs, ProjectSnapshot.Builder builder) {
        List<String> roots = new ArrayList<>();
        for (String candidate : new String[]{"src/main/java", "src/main/kotlin",
                "app/src/main/java", "app/src/main/kotlin", "src", "lib"}) {
            if (fs.isDirectory(candidate)) {
                roots.add(candidate);
            }
        }
        if (!roots.isEmpty()) {
            builder.sourceRoots(roots);
        }
    }

    /** Small, bounded anchor list so the model knows where to look first. */
    private static void addRelevantFiles(WorkspaceFileSystem fs, ProjectSnapshot.Builder builder) {
        List<String> files = new ArrayList<>();
        for (String candidate : new String[]{"settings.gradle", "settings.gradle.kts",
                "build.gradle", "build.gradle.kts", "package.json", "pom.xml",
                "app/src/main/AndroidManifest.xml", "README.md", "AGENTS.md"}) {
            if (fs.exists(candidate)) {
                files.add(candidate);
            }
        }
        if (!files.isEmpty()) {
            builder.relevantFiles(files);
        }
    }

    private static String readFileSafe(WorkspaceFileSystem fs, String path) {
        try {
            if (!fs.exists(path) || fs.isDirectory(path)) {
                return null;
            }
            String text = fs.readText(path);
            if (text != null && text.length() > MAX_SNIPPET_CHARS) {
                text = text.substring(0, MAX_SNIPPET_CHARS);
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    // Builder accessors used above (namespace/applicationId are write-only in
    // the builder, so discovery tracks what it already set locally).
    private static String builderApplicationId(ProjectSnapshot.Builder builder) {
        return builder.applicationIdValue;
    }

    private static String builderNamespace(ProjectSnapshot.Builder builder) {
        return builder.namespaceValue;
    }
}
