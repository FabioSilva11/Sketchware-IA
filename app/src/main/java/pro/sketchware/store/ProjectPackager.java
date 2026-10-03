package pro.sketchware.store;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.store.core.Records;

/**
 * Turns a local project into the package the store shares, and a downloaded package back into a
 * project. Sketchware projects use the app's backup format (.swb, with local libraries and custom
 * blocks); Android Studio projects are zipped without build outputs. APKs are never shared.
 */
public final class ProjectPackager {

    /** Folders and files of an Android Studio project that are generated, local or binaries. */
    private static final Set<String> SKIPPED_NAMES = new HashSet<>(Arrays.asList(
            "build", ".gradle", ".idea", ".cxx", ".externalNativeBuild", "local.properties", "captures", ".kotlin"));
    private static final Set<String> SKIPPED_EXTENSIONS = new HashSet<>(Arrays.asList("apk", "aab", "apks", "ap_"));

    private ProjectPackager() {
    }

    public static final class LocalProject {
        public final String scId;
        public final String name;
        public final String appName;
        public final String packageName;
        public final String versionName;
        public final long versionCode;
        public final String kind;
        public final File icon;

        LocalProject(HashMap<String, Object> map) {
            scId = yB.c(map, "sc_id");
            name = yB.c(map, "my_ws_name");
            appName = yB.c(map, "my_app_name");
            packageName = yB.c(map, "my_sc_pkg_name");
            versionName = emptyTo(yB.c(map, "sc_ver_name"), "1.0");
            long code;
            try {
                code = Long.parseLong(yB.c(map, "sc_ver_code"));
            } catch (NumberFormatException e) {
                code = 1;
            }
            versionCode = code;
            kind = lC.isAndroidStudioProject(map) ? Records.KIND_ANDROID_STUDIO : Records.KIND_SKETCHWARE;
            icon = findIcon(scId, kind);
        }

        public String title() {
            return appName.isEmpty() ? name : appName;
        }
    }

    public static List<LocalProject> localProjects() {
        List<LocalProject> projects = new ArrayList<>();
        for (HashMap<String, Object> map : lC.a()) {
            projects.add(new LocalProject(map));
        }
        return projects;
    }

    public static LocalProject localProject(String scId) {
        HashMap<String, Object> map = lC.b(scId);
        return map == null ? null : new LocalProject(map);
    }

    private static File findIcon(String scId, String kind) {
        if (Records.KIND_SKETCHWARE.equals(kind)) {
            File icon = new File(wq.e() + File.separator + scId, "icon.png");
            return icon.isFile() ? icon : null;
        }
        File res = new File(wq.getAndroidStudioProjectPath(scId), "app/src/main/res");
        for (String density : new String[]{"xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi"}) {
            for (String name : new String[]{"ic_launcher.png", "ic_launcher.webp", "ic_launcher_round.png"}) {
                File icon = new File(res, "mipmap-" + density + File.separator + name);
                if (icon.isFile()) {
                    return icon;
                }
            }
        }
        return null;
    }

    /** Packs a local project into {@code outputDir}. Runs on a background thread. */
    public static File pack(Context context, LocalProject project, File outputDir) throws IOException {
        outputDir.mkdirs();
        String baseName = sanitize(project.title()) + "-" + project.versionCode;
        if (Records.KIND_SKETCHWARE.equals(project.kind)) {
            BackupFactory backup = new BackupFactory(project.scId);
            backup.setBackupLocalLibs(true);
            backup.setBackupCustomBlocks(true);
            backup.backup(context, sanitize(project.name.isEmpty() ? project.title() : project.name));
            File out = backup.getOutFile();
            if (out == null || !out.isFile()) {
                throw new IOException("Couldn't pack the project: " + backup.getError());
            }
            File target = new File(outputDir, baseName + "." + BackupFactory.EXTENSION);
            if (!out.renameTo(target)) {
                BackupFactory.copy(out, target);
                out.delete();
            }
            return target;
        }
        File source = new File(wq.getAndroidStudioProjectPath(project.scId));
        if (!source.isDirectory()) {
            throw new IOException("The Android Studio project folder is missing");
        }
        File target = new File(outputDir, baseName + "." + BackupFactory.ANDROID_STUDIO_EXTENSION);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target))) {
            addToZip(zip, source, sanitize(project.title()));
        }
        return target;
    }

    private static void addToZip(ZipOutputStream zip, File file, String path) throws IOException {
        String name = file.getName();
        if (SKIPPED_NAMES.contains(name) || SKIPPED_EXTENSIONS.contains(extension(name))) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(zip, child, path + "/" + child.getName());
                }
            }
            return;
        }
        zip.putNextEntry(new ZipEntry(path));
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    /**
     * Imports a downloaded package as a new project and returns its id. The package must be the
     * kind its listing says.
     */
    public static String importPackage(File archive, String kind) throws IOException {
        boolean sketchware = Records.KIND_SKETCHWARE.equals(kind);
        checkArchive(archive, sketchware);
        String scId = lC.b();
        BackupFactory restore = new BackupFactory(scId);
        if (sketchware) {
            restore.setBackupLocalLibs(true);
            restore.restore(archive);
        } else {
            restore.restoreAndroidStudioProject(archive);
        }
        if (!restore.isRestoreSuccess()) {
            throw new IOException("Couldn't import the project: " + restore.getError());
        }
        if (!sketchware) {
            deleteBinaries(new File(wq.getAndroidStudioProjectPath(scId)));
        }
        StoreRuntime.projectsChanged = true;
        return scId;
    }

    /** Rejects packages that aren't what their listing says, or that try to ship an APK. */
    private static void checkArchive(File archive, boolean sketchware) throws IOException {
        boolean hasProject = false;
        boolean hasGradle = false;
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName().replace('\\', '/');
                if (name.contains("../") || name.startsWith("/")) {
                    throw new IOException("The package has unsafe paths");
                }
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (name.equals("project")) {
                    hasProject = true;
                }
                if (fileName.startsWith("settings.gradle") || fileName.startsWith("build.gradle")) {
                    hasGradle = true;
                }
            }
        }
        if (sketchware && !hasProject) {
            throw new IOException("This isn't a Sketchware project package");
        }
        if (!sketchware && !hasGradle) {
            throw new IOException("This isn't an Android Studio project package");
        }
    }

    private static void deleteBinaries(File folder) {
        File[] children = folder.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                deleteBinaries(child);
            } else if (SKIPPED_EXTENSIONS.contains(extension(child.getName()))) {
                child.delete();
            }
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String name) {
        String clean = name == null ? "" : name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return clean.isEmpty() ? "project" : clean;
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
