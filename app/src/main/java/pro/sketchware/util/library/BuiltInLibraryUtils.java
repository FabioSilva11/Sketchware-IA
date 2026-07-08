package pro.sketchware.util.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.BuiltInLibraries.BuiltInLibrary;

public class BuiltInLibraryUtils {

    /** Splits a built-in library folder name into artifact + version (e.g. "material-1.13.0"). */
    private static final Pattern ARTIFACT_VERSION = Pattern.compile("^(.*?)-(\\d[\\w.\\-]*)$");

    private static volatile String cachedPromptSection;

    /**
     * Builds a prompt section listing every library already bundled in the build
     * environment, with its exact version and (when available) Java package.
     *
     * The model uses this to reuse the bundled versions instead of declaring a
     * different version of the same library — which would put two versions on the
     * classpath and cause "Duplicate class" / version-divergence build failures,
     * especially in Android Studio projects.
     */
    public static String buildAvailableLibrariesPromptSection() {
        String cached = cachedPromptSection;
        if (cached != null) {
            return cached;
        }

        List<String> lines = new ArrayList<>();
        for (BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            String name = library.getName();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            Matcher matcher = ARTIFACT_VERSION.matcher(name.trim());
            String entry;
            if (matcher.matches()) {
                String artifact = matcher.group(1);
                String version = matcher.group(2);
                entry = artifact + " : " + version;
            } else {
                entry = name.trim();
            }
            String pkg = library.getPackageName().orElse(null);
            if (pkg != null && !pkg.isEmpty()) {
                entry += "  (package " + pkg + ")";
            }
            lines.add(entry);
        }
        Collections.sort(lines);

        StringBuilder builder = new StringBuilder();
        builder.append("Libraries already available in the build environment (pre-bundled at these EXACT versions):\n");
        builder.append("<available_libraries>\n");
        for (String line : lines) {
            builder.append("- ").append(line).append('\n');
        }
        builder.append("</available_libraries>\n");
        builder.append("Rules for dependencies and imports:\n");
        builder.append("- These libraries are ALREADY on the compile classpath at the versions above. Import and use them directly.\n");
        builder.append("- Do NOT add a build.gradle dependency for any library listed above, and NEVER declare a different version of it — two versions of the same library on the classpath cause 'Duplicate class' errors and version-divergence build failures.\n");
        builder.append("- Only add a new dependency when the library is NOT in the list above.\n");
        builder.append("- When you do add a new dependency, prefer a version compatible with the AndroidX/Material versions listed above.");

        String result = builder.toString();
        cachedPromptSection = result;
        return result;
    }

    /**
     * Returns the known dependencies for a given built-in library.
     *
     * @apiNote This method won't return the dependencies' sub-dependencies!
     */
    public static String[] getKnownDependencies(String libraryName) {
        for (BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            if (library.getName().equals(libraryName)) {
                return library.getDependencyNames().toArray(new String[0]);
            }
        }

        throw new IllegalArgumentException("Unknown built-in library '" + libraryName + "'!");
    }

    /**
     * Returns the package name of a given built-in library.
     */
    public static String getPackageName(String libraryName) {
        for (BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            if (library.getName().equals(libraryName)) {
                return library.getPackageName().orElseThrow(IllegalStateException::new);
            }
        }

        throw new IllegalArgumentException("Unknown built-in library '" + libraryName + "'!");
    }

    /**
     * Returns whether a given built-in library has resources that need to be mapped to a R.java file
     * by a resource processor.
     */
    public static boolean hasResources(String libraryName) {
        for (BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            if (library.getName().equals(libraryName)) {
                return library.hasResources();
            }
        }

        throw new IllegalArgumentException("Unknown built-in library '" + libraryName + "'!");
    }
}
