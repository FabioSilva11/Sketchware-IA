package a.a.a;

import java.io.File;
import java.util.Objects;

import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.util.library.BuiltInLibraryUtils;

/**
 * An object representing a built-in library, e.g. the MDC library (nicknamed material-1.0.0)
 */
public class Jp {
    private final String name;
    private final String packageName;
    private final boolean hasResources;

    public Jp(String libraryName) {
        name = libraryName;
        hasResources = BuiltInLibraryUtils.hasResources(libraryName);
        packageName = hasResources ? BuiltInLibraryUtils.getPackageName(libraryName) : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Jp jp = (Jp) o;
        return name.equals(jp.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * @return The library's name inside libs.zip or dexs.zip, e.g. material-1.0.0
     */
    public String getName() {
        return name;
    }

    /**
     * @return The library's base package name, e.g. com.google.android.material
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * @return <code>true</code> if the library has resources that need constants in an R class,
     * <code>false</code> otherwise
     */
    public boolean hasResources() {
        return hasResources;
    }

    /**
     * @return <code>true</code> if the library has assets that need to be put into the APK,
     * <code>false</code> otherwise
     */
    public boolean hasAssets() {
        // Checked when asked rather than on construction, as libraries get extracted after
        // the project's library list is built. E.g. CodeView's scripts, OkHttp's public suffix list.
        return new File(BuiltInLibraries.getLibraryPath(name), "assets").isDirectory();
    }
}
