package mod.hey.studios.project.backup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import pro.sketchware.workspace.RecentProjectsRegistry;
import pro.sketchware.workspace.WorkspacePathManager;
import pro.sketchware.core.MapValueUtils;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.project.AndroidStudioProjectSettingsDialog;
import mod.hey.studios.project.custom_blocks.CustomBlocksManager;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BackupFactory {
    public static final String EXTENSION = "swb";
    public static final String ANDROID_STUDIO_EXTENSION = "zip";
    public static final String DEF_PATH = ".sketchware/backups/";
    private static final String STUDIO_ARCHIVE_SUBDIR = "studio_project";

    private static final String[] resSubfolders = {
            "fonts", "icons", "images", "sounds"
    };

    final String sc_id;
    private final File storageRoot;
    File outPath;
    boolean backupLocalLibs;
    boolean backupCustomBlocks;
    String error = "";
    boolean restoreSuccess = true;

    /**
     * @param sc_id For backing up, the target project's ID,
     *              for restoring, the new project ID
     */
    public BackupFactory(String sc_id) {
        this(sc_id, null);
    }

    public BackupFactory(String sc_id, File storageRoot) {
        this.sc_id = sc_id;
        this.storageRoot = storageRoot;
    }

    private File resolveStorageRoot() {
        return storageRoot != null ? storageRoot : Environment.getExternalStorageDirectory();
    }

    public static String getBackupDir() {
        return new File(Environment.getExternalStorageDirectory(), ConfigActivity.getBackupPath())
                .getAbsolutePath();
    }

    private File getBackupDirFile() {
        if (storageRoot != null) {
            return new File(storageRoot, DEF_PATH);
        }
        return new File(Environment.getExternalStorageDirectory(), ConfigActivity.getBackupPath());
    }

    private File getAllLocalLibsDir() {
        return new File(resolveStorageRoot(), ".sketchware/libs/local_libs");
    }

    private static HashMap<String, Object> decryptProjectBytes(byte[] encrypted) {
        try {
            if (encrypted == null || encrypted.length == 0) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = "sketchwaresecure".getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] decrypted = cipher.doFinal(encrypted);
            String decryptedString = new String(decrypted, StandardCharsets.UTF_8);

            return new Gson().fromJson(decryptedString.trim(), Helper.TYPE_MAP);
        } catch (Exception e) {
            return null;
        }
    }

    public static HashMap<String, Object> readEncryptedProjectFile(File file) {
        return getProject(file);
    }

    private static HashMap<String, Object> getProject(File file) {
        try {
            byte[] encrypted;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                encrypted = new byte[(int) raf.length()];
                raf.readFully(encrypted);
            }
            return decryptProjectBytes(encrypted);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean writeEncryptedProjectFile(File file, String string) {
        return writeEncrypted(file, string);
    }

    private static boolean writeEncrypted(File file, String string) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        String path = file.getAbsolutePath();

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = "sketchwaresecure".getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] encrypted = cipher.doFinal((string.trim()).getBytes(StandardCharsets.UTF_8));
            try (RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
                raf.setLength(0);
                raf.write(encrypted);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getNewScId() {
        File myscList = new File(Environment.getExternalStorageDirectory(),
                ".sketchware/mysc/list/");

        ArrayList<String> list = new ArrayList<>();
        FileUtil.listDir(myscList.getAbsolutePath(), list);
        //noinspection Java8ListSort

        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);

        int id = list.isEmpty() ? 600 : Integer.parseInt(new File(list.get(list.size() - 1)).getName());
        return String.valueOf(id + 1);
    }

    /************************ UTILITIES ************************/

    public static boolean unzip(File zipFile, File destinationDir) {
        int DEFAULT_BUFFER = 2048;
        try (ZipFile zip = new ZipFile(zipFile)) {
            destinationDir.mkdirs();
            String destinationPath = destinationDir.getCanonicalPath() + File.separator;
            Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry entry = zipFileEntries.nextElement();
                String entryName = entry.getName();
                File destFile = new File(destinationDir, entryName);
                // Backups can come from anyone (opened from other apps, the store): an entry

                // like "../x" must not write outside the folder

                String destFilePath = destFile.getCanonicalPath();
                if (!destFilePath.equals(destinationDir.getCanonicalPath()) && !destFilePath.startsWith(destinationPath)) {
                    return false;
                }
                File destinationParent = destFile.getParentFile();
                if (destinationParent != null && !destinationParent.exists()) {
                    destinationParent.mkdirs();
                }
                if (!entry.isDirectory()) {
                    try (BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry))) {
                        int currentByte;
                        byte[] data = new byte[DEFAULT_BUFFER];
                        try (FileOutputStream fos = new FileOutputStream(destFile)) {
                            try (BufferedOutputStream dest = new BufferedOutputStream(fos, DEFAULT_BUFFER)) {
                                while ((currentByte = is.read(data, 0, DEFAULT_BUFFER)) != -1 /*EOF*/) {
                                    dest.write(data, 0, currentByte);
                                }
                                dest.flush();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static void zipFolder(File srcFolder, File destZipFile) throws Exception {
        try (FileOutputStream fileWriter = new FileOutputStream(destZipFile)) {
            try (ZipOutputStream zip = new ZipOutputStream(fileWriter)) {
                addFolderToZip(srcFolder, srcFolder, zip);
                zip.flush();
            }
        }
    }

    private static void addFileToZip(File rootPath, File srcFile, ZipOutputStream zip) throws Exception {

        if (srcFile.isDirectory()) {
            addFolderToZip(rootPath, srcFile, zip);
        } else {
            byte[] buf = new byte[4096];
            int len;
            try (FileInputStream in = new FileInputStream(srcFile)) {
                String name = rootPath.toPath().relativize(srcFile.toPath()).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    private static void addFolderToZip(File rootPath, File srcFolder, ZipOutputStream zip) throws Exception {
        File[] srcFolderFiles = srcFolder.listFiles();
        if (srcFolderFiles != null) {
            for (File fileName : srcFolderFiles) {
                addFileToZip(rootPath, fileName, zip);
            }
        }
    }

    //6.3.0 fix1

    public static void createNomediaFileIn(File dir) {
        try {
            writeTextUtf8(new File(dir, ".nomedia"), "");
        } catch (IOException ignored) {
        }
    }

    //6.3.0 fix1

    public static void copySafe(File source, File destination) {
        if (!source.exists()) {
            destination.mkdirs();
            createNomediaFileIn(destination);
        } else {
            copy(source, destination);
        }
    }

    public static void copy(File source, File destination) {
        if (source.isDirectory()) {
            if (!destination.exists()) destination.mkdirs();

            String[] files = source.list();
            if (files != null) {

                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);

                    copy(srcFile, destFile);
                }
            }
        } else {
            //skip .nomedia files

            if (source.getName().equals(".nomedia")) return;

            File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean zipContainsFile(String zipPath, String fileName) {
        String normalizedTarget = fileName.replace('\\', '/');
        if (normalizedTarget.endsWith("/")) {
            normalizedTarget = normalizedTarget.substring(0, normalizedTarget.length() - 1);
        }

        try (ZipInputStream zp = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry en;
            while ((en = zp.getNextEntry()) != null) {
                String name = en.getName().replace('\\', '/');
                if (name.equals(normalizedTarget) || name.startsWith(normalizedTarget + "/")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    public static boolean isAndroidStudioArchive(File archiveFile) {
        if (archiveFile == null || !archiveFile.isFile()) {
            return false;
        }
        try (ZipFile zip = new ZipFile(archiveFile)) {
            ZipEntry rootProjectEntry = null;
            boolean hasGradleMarker = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (name.equals(STUDIO_ARCHIVE_SUBDIR) || name.startsWith(STUDIO_ARCHIVE_SUBDIR + "/")) {
                    return true;
                }
                if ("project".equals(name) && !entry.isDirectory()) {
                    rootProjectEntry = entry;
                }
                if (isGradleMarkerEntry(name)) {
                    hasGradleMarker = true;
                }
            }
            if (rootProjectEntry != null) {
                byte[] bytes = readZipEntryBytes(zip, rootProjectEntry, 512 * 1024);
                HashMap<String, Object> metadata = decryptProjectBytes(bytes);
                if (metadata != null) {
                    return RecentProjectsRegistry.isAndroidStudioProject(metadata);
                }
            }
            return hasGradleMarker;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isGradleMarkerEntry(String normalizedEntryName) {
        return normalizedEntryName.equals("settings.gradle")
                || normalizedEntryName.equals("settings.gradle.kts")
                || normalizedEntryName.equals("build.gradle")
                || normalizedEntryName.equals("build.gradle.kts")
                || normalizedEntryName.equals("app/build.gradle")
                || normalizedEntryName.equals("app/build.gradle.kts")
                || normalizedEntryName.endsWith("/settings.gradle")
                || normalizedEntryName.endsWith("/settings.gradle.kts")
                || normalizedEntryName.endsWith("/build.gradle")
                || normalizedEntryName.endsWith("/build.gradle.kts")
                || normalizedEntryName.endsWith("/app/build.gradle")
                || normalizedEntryName.endsWith("/app/build.gradle.kts");
    }

    private static byte[] readZipEntryBytes(ZipFile zip, ZipEntry entry, int maxBytes) throws IOException {
        try (InputStream in = zip.getInputStream(entry);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buf)) > 0) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    /************************ BACKUP ************************/

    public void backup(Context context, String project_name) {
        HashMap<String, Object> currentMetadata = readCurrentProjectMetadata();
        if ((currentMetadata != null && RecentProjectsRegistry.isAndroidStudioProject(currentMetadata))
                || (!getProjectPath().exists() && looksLikeAndroidStudioProject(getProjectDirectory()))) {
            backupAndroidStudioProject(project_name);
            return;
        }

        String safeInputName = (project_name == null || project_name.trim().isEmpty()) ? sc_id : project_name;
        String projectNameOnly = sanitizeFileName(safeInputName.replace("_d", "").replace(File.separator, ""));
        String finalFileName = buildBackupFileName(projectNameOnly, currentMetadata, context != null);
        createBackupsFolder();

        File backupDir = getBackupDirFile();
        File projectOutputFolder = new File(backupDir, projectNameOnly);
        File outFolder = new File(backupDir, projectNameOnly + "_" + sc_id + "_temp");
        File outZip = createUniqueArchiveFile(projectOutputFolder, finalFileName, EXTENSION);

        if (outFolder.exists()) {
            deleteRecursivelyQuiet(outFolder);
        }

        outFolder.mkdirs();
        projectOutputFolder.mkdirs();

        // Copy data
        File dataF = new File(outFolder, "data");
        dataF.mkdirs();
        copySafe(getDataDir(), dataF);

        // Copy res
        File resF = new File(outFolder, "resources");
        resF.mkdirs();

        for (String subfolder : resSubfolders) {
            File resSubf = new File(resF, subfolder);
            resSubf.mkdirs();
            copySafe(getResDir(subfolder), resSubf);

            if (!subfolder.equals("icons")) {
                createNomediaFileIn(resSubf);
            }
        }

        // Copy project
        File projectF = new File(outFolder, "project");
        copy(getProjectPath(), projectF);

        // Find local libs used and include them in the backup
        if (backupLocalLibs) {
            copyUsedLocalLibsToBackup(outFolder);
        }

        // Find custom blocks used and include them in the backup
        if (backupCustomBlocks && context != null) {
            CustomBlocksManager cbm = new CustomBlocksManager(context, sc_id);

            Set<ExtraBlockInfo> blocks = new HashSet<>();
            Set<String> block_names = new HashSet<>();
            for (BlockBean bean : cbm.getUsedBlocks()) {
                if (!block_names.contains(bean.opCode)) {
                    block_names.add(bean.opCode);
                    if (cbm.contains(bean.opCode)) {
                        blocks.add(cbm.getExtraBlockInfo(bean.opCode));
                    } else {
                        var block = BlockLoader.getBlockInfo(bean.opCode);
                        blocks.add(block);
                    }
                }
            }

            String json = new Gson().toJson(blocks);

            File customBlocksF = new File(dataF, "custom_blocks");
            FileUtil.writeFile(customBlocksF.getAbsolutePath(), json);
        }

        // Zip final folder
        try {
            zipFolder(outFolder, outZip);
        } catch (Exception e) {
            error = formatError(e);
            outPath = null;
            deleteRecursivelyQuiet(outFolder);
            return;
        }

        // Delete the temporary folder
        deleteRecursivelyQuiet(outFolder);

        // Put outZip to global variable
        outPath = outZip;
    }

    private String getFormattedDateFrom(String format) {
        return new SimpleDateFormat(format, Locale.ENGLISH).format(Calendar.getInstance().getTime());
    }

    public File getOutFile() {
        return outPath;
    }

    public void backupAndroidStudioProject(String project_name) {
        File sourceFolder = getProjectDirectory();
        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            error = "Android Studio project folder not found";
            outPath = null;
            return;
        }

        HashMap<String, Object> metadata = readAndroidStudioProjectMetadata(sourceFolder, new File(sourceFolder, "project"));
        metadata.put("sc_id", sc_id);
        metadata.put(RecentProjectsRegistry.PROJECT_KIND_KEY, RecentProjectsRegistry.PROJECT_KIND_ANDROID_STUDIO);
        metadata.put("proj_type", 2);

        String fallbackName = metadataString(metadata, "my_ws_name");
        String chosenName = (project_name == null || project_name.trim().isEmpty()) ? fallbackName : project_name;
        String projectNameOnly = sanitizeFileName(chosenName);
        String finalFileName = buildBackupFileName(projectNameOnly, metadata, false);

        File backupDir = getBackupDirFile();
        File outputFolder = new File(backupDir, projectNameOnly);
        File outSwb = createUniqueArchiveFile(outputFolder, finalFileName, EXTENSION);
        File tempFolder = new File(backupDir, projectNameOnly + "_" + sc_id + "_studio_temp");

        createBackupsFolder();
        outputFolder.mkdirs();
        if (tempFolder.exists()) {
            deleteRecursivelyQuiet(tempFolder);
        }
        tempFolder.mkdirs();

        try {
            File rootProjectMetadata = new File(tempFolder, "project");
            if (!writeEncrypted(rootProjectMetadata, new Gson().toJson(metadata))) {
                throw new IOException("Couldn't write encrypted Android Studio project metadata");
            }

            File studioTarget = new File(tempFolder, STUDIO_ARCHIVE_SUBDIR);
            studioTarget.mkdirs();
            copyAndroidStudioSourceTree(sourceFolder, studioTarget, 0);

            File dataDir = getDataDir();
            if (dataDir.isDirectory()) {
                File dataTarget = new File(tempFolder, "data");
                dataTarget.mkdirs();
                copy(dataDir, dataTarget);
            }

            File resRootTarget = new File(tempFolder, "resources");
            for (String subfolder : resSubfolders) {
                File resSource = getResDir(subfolder);
                if (resSource.isDirectory()) {
                    File resSubTarget = new File(resRootTarget, subfolder);
                    resSubTarget.mkdirs();
                    copy(resSource, resSubTarget);
                }
            }

            if (backupLocalLibs) {
                copyUsedLocalLibsToBackup(tempFolder);
            }

            zipFolder(tempFolder, outSwb);
            outPath = outSwb;
        } catch (Exception e) {
            error = formatError(e);
            outPath = null;
        } finally {
            deleteRecursivelyQuiet(tempFolder);
        }
    }

    public void setBackupLocalLibs(boolean b) {
        backupLocalLibs = b;
    }

    public void setBackupCustomBlocks(boolean b) {
        backupCustomBlocks = b;
    }

    /************************ RESTORE ************************/

    public void restore(File swbPath) {
        if (isAndroidStudioArchive(swbPath)) {
            restoreAndroidStudioProject(swbPath);
            return;
        }

        String name = swbPath.getName();
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }

        restore(swbPath, sanitizeFileName(name));
    }

    public void restoreAndroidStudioProject(File zipPath) {
        createBackupsFolder();

        File backupDir = getBackupDirFile();
        String baseName = sanitizeFileName(stripExtension(zipPath.getName()));
        File tempFolder = new File(backupDir, baseName + "_" + sc_id + "_restore");
        if (tempFolder.exists()) {
            deleteRecursivelyQuiet(tempFolder);
        }

        File targetFolder = getProjectDirectory();
        if (targetFolder.exists()) {
            error = "project id already exists";
            restoreSuccess = false;
            return;
        }

        if (!unzipSafely(zipPath, tempFolder)) {
            error = "couldn't unzip the Android Studio backup";
            restoreSuccess = false;
            deleteRecursivelyQuiet(tempFolder);
            return;
        }

        try {
            File bundledStudioDir = new File(tempFolder, STUDIO_ARCHIVE_SUBDIR);
            File topLevelProjectFile = new File(tempFolder, "project");
            File sourceFolder;
            if (bundledStudioDir.isDirectory() && looksLikeAndroidStudioProject(bundledStudioDir)) {
                sourceFolder = bundledStudioDir;
            } else {
                sourceFolder = findAndroidStudioProjectRoot(tempFolder);
            }

            if (sourceFolder == null) {
                error = "couldn't find an Android Studio project in the backup";
                restoreSuccess = false;
                return;
            }

            File metadataSource = topLevelProjectFile.isFile() ? topLevelProjectFile : new File(sourceFolder, "project");
            HashMap<String, Object> metadata = readAndroidStudioProjectMetadata(sourceFolder, metadataSource);
            metadata.put("sc_id", sc_id);
            metadata.put(RecentProjectsRegistry.PROJECT_KIND_KEY, RecentProjectsRegistry.PROJECT_KIND_ANDROID_STUDIO);
            metadata.put("proj_type", 2);
            metadata.put("studio_path", targetFolder.getAbsolutePath());

            File parent = targetFolder.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            copyAndroidStudioSourceTree(sourceFolder, targetFolder, 0);

            if (!writeEncrypted(new File(targetFolder, "project"), new Gson().toJson(metadata))) {
                error = "couldn't write to the Android Studio project metadata file";
                restoreSuccess = false;
                return;
            }

            if (backupLocalLibs) {
                copyLocalLibsFromBackup(tempFolder);
            }

            File bundledData = new File(tempFolder, "data");
            if (bundledData.isDirectory()) {
                copy(bundledData, getDataDir());
                normalizeRestoredLocalLibraries(true);
                if (storageRoot == null && new File(getDataDir(), "project_config").isFile()) {
                    try {
                        AndroidStudioProjectSettingsDialog.applyStoredSettingsToGradle(sc_id, targetFolder);
                    } catch (Throwable ignored) {
                    }
                }
            }

            File bundledRes = new File(tempFolder, "resources");
            if (bundledRes.isDirectory()) {
                for (String subfolder : resSubfolders) {
                    File subSource = new File(bundledRes, subfolder);
                    if (subSource.isDirectory()) {
                        copy(subSource, getResDir(subfolder));
                    }
                }
            }

            recoverStudioCustomIconIfNeeded(metadata, targetFolder);
            restoreSuccess = true;
        } finally {
            deleteRecursivelyQuiet(tempFolder);
        }
    }

    private void restore(File swbPath, String name) {
        createBackupsFolder();

        File outFolder = createUniqueTempFolder(getBackupDirFile(), name + "_" + sc_id + "_restore");

        if (!unzip(swbPath, outFolder)) {
            error = "couldn't unzip the backup";
            restoreSuccess = false;
            deleteRecursivelyQuiet(outFolder);
            return;
        }

        try {
            File project = new File(outFolder, "project");
            File data = new File(outFolder, "data");
            File res = new File(outFolder, "resources");

            HashMap<String, Object> map = getProject(project);

            if (map == null) {
                error = "couldn't read the project file";
                restoreSuccess = false;
                return;
            }

            if (RecentProjectsRegistry.isAndroidStudioProject(map)) {
                deleteRecursivelyQuiet(outFolder);
                restoreAndroidStudioProject(swbPath);
                return;
            }

            map.put("sc_id", sc_id);
            if (metadataString(map, RecentProjectsRegistry.PROJECT_KIND_KEY).isEmpty()) {
                map.put(RecentProjectsRegistry.PROJECT_KIND_KEY, RecentProjectsRegistry.PROJECT_KIND_SKETCHWARE);
            }

            if (!writeEncrypted(project, new Gson().toJson(map))) {
                error = "couldn't write to the project file";
                restoreSuccess = false;
                return;
            }

            if (data.exists()) {
                copy(data, getDataDir());
            }

            for (String subfolder : resSubfolders) {
                File subf = new File(res, subfolder);
                copySafe(subf, getResDir(subfolder));
            }

            File projectParent = getProjectPath().getParentFile();
            if (projectParent != null) {
                projectParent.mkdirs();
            }

            copy(project, getProjectPath());

            if (backupLocalLibs) {
                copyLocalLibsFromBackup(outFolder);
            }
            normalizeRestoredLocalLibraries(false);

            restoreSuccess = true;
        } finally {
            deleteRecursivelyQuiet(outFolder);
        }
    }

    public String getError() {
        return error;
    }

    public boolean isRestoreSuccess() {
        return restoreSuccess;
    }

    /************************ SW METHODS ************************/

    private void createBackupsFolder() {
        getBackupDirFile().mkdirs();
    }

    private HashMap<String, Object> readCurrentProjectMetadata() {
        File nativeProject = getProjectPath();
        if (nativeProject.isFile()) {
            HashMap<String, Object> map = getProject(nativeProject);
            if (map != null) {
                if (metadataString(map, RecentProjectsRegistry.PROJECT_KIND_KEY).isEmpty()) {
                    map.put(RecentProjectsRegistry.PROJECT_KIND_KEY, RecentProjectsRegistry.PROJECT_KIND_SKETCHWARE);
                }
                return map;
            }
        }
        if (storageRoot == null) {
            try {
                return RecentProjectsRegistry.b(sc_id);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String buildBackupFileName(String projectNameOnly, HashMap<String, Object> metadata, boolean notifyOnCustomFormatError) {
        String versionName = metadata != null ? metadataString(metadata, "sc_ver_name") : "";
        String versionCode = metadata != null ? metadataString(metadata, "sc_ver_code") : "";
        String pkgName = metadata != null ? metadataString(metadata, "my_sc_pkg_name") : "";
        if (versionName.isEmpty()) versionName = "1.0";
        if (versionCode.isEmpty()) versionCode = "1";
        if (pkgName.isEmpty()) pkgName = "com.my.project";

        if (storageRoot == null) {
            try {
                String customFileName = ConfigActivity.getBackupFileName();
                if (customFileName != null && !customFileName.trim().isEmpty()) {
                    String parsed = customFileName
                            .replace("$projectName", projectNameOnly)
                            .replace("$versionCode", versionCode)
                            .replace("$versionName", versionName)
                            .replace("$pkgName", pkgName)
                            .replace("$timeInMs", String.valueOf(Calendar.getInstance(Locale.ENGLISH).getTimeInMillis()));
                    Matcher matcher = Pattern.compile("\\$time\\((.*?)\\)").matcher(customFileName);
                    while (matcher.find()) {
                        parsed = parsed.replaceFirst(Pattern.quote(Objects.requireNonNull(matcher.group(0))), getFormattedDateFrom(matcher.group(1)));
                    }
                    String sanitized = sanitizeFileName(parsed);
                    if (!sanitized.isEmpty()) {
                        return sanitized;
                    }
                }
            } catch (Throwable ignored) {
                if (notifyOnCustomFormatError) {
                    try {
                        SketchwareUtil.toastError("Failed To Parse Custom Filename For Backup. Using default");
                    } catch (Throwable ignoredToast) {
                    }
                }
            }
        }

        return sanitizeFileName(projectNameOnly + " v" + versionName + " (" + pkgName + ", " + versionCode + ") " + getFormattedDateFrom("yyyy-M-dd'T'HHmmss"));
    }

    private File createUniqueArchiveFile(File outputFolder, String baseName, String extension) {
        String safeBase = sanitizeFileName(baseName);
        File archive = new File(outputFolder, safeBase + "." + extension);
        int index = 2;
        while (archive.exists()) {
            archive = new File(outputFolder, safeBase + "-" + index + "." + extension);
            index++;
        }
        return archive;
    }

    private File createUniqueTempFolder(File parentFolder, String baseName) {
        String safeBase = sanitizeFileName(baseName);
        File folder = new File(parentFolder, safeBase);
        int index = 2;
        while (folder.exists()) {
            folder = new File(parentFolder, safeBase + "_" + index);
            index++;
        }
        return folder;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void copyAndroidStudioSourceTree(File source, File destination, int depth) {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            boolean isModuleOrRootDir = depth == 0
                    || new File(source, "build.gradle").isFile()
                    || new File(source, "build.gradle.kts").isFile();
            for (File child : children) {
                String name = child.getName();
                if (child.isDirectory()) {
                    if (shouldSkipAndroidStudioDirectory(name, depth, isModuleOrRootDir)) {
                        continue;
                    }
                } else if (shouldSkipAndroidStudioFile(name, depth)) {
                    continue;
                }
                copyAndroidStudioSourceTree(child, new File(destination, name), depth + 1);
            }
        } else {
            copy(source, destination);
        }
    }

    private static boolean shouldSkipAndroidStudioDirectory(String name, int depth, boolean isModuleOrRootDir) {
        if (".git".equals(name) || ".gradle".equals(name) || ".idea".equals(name) || ".cxx".equals(name)) {
            return true;
        }
        if (depth == 0 && ("bin".equals(name) || "gen".equals(name) || "build".equals(name))) {
            return true;
        }
        return isModuleOrRootDir && "build".equals(name);
    }

    private static boolean shouldSkipAndroidStudioFile(String name, int depth) {
        if (".nomedia".equals(name)) {
            return true;
        }
        return depth == 0 && ("project".equals(name) || "local.properties".equals(name));
    }

    private void copyUsedLocalLibsToBackup(File backupRoot) {
        File localLibs = getLocalLibsPath();
        if (!localLibs.isFile()) {
            return;
        }
        try {
            JSONArray ja = new JSONArray(readTextUtf8(localLibs));
            File libsF = new File(backupRoot, "local_libs");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                File libFolder = resolveLocalLibraryDirectory(jo);
                if (libFolder != null && libFolder.isDirectory()) {
                    libsF.mkdirs();
                    copy(libFolder, new File(libsF, libFolder.getName()));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private File resolveLocalLibraryDirectory(JSONObject jo) {
        for (String pathKey : new String[]{"dexPath", "jarPath", "resPath", "assetsPath", "manifestPath", "pgRulesPath"}) {
            String rawPath = jo.optString(pathKey, "");
            if (!rawPath.isEmpty()) {
                File candidate = new File(rawPath).getParentFile();
                if (candidate != null && candidate.isDirectory()) {
                    return candidate;
                }
                if (candidate != null) {
                    File inLocalLibs = new File(getAllLocalLibsDir(), candidate.getName());
                    if (inLocalLibs.isDirectory()) {
                        return inLocalLibs;
                    }
                }
            }
        }
        String name = jo.optString("name", "").trim();
        if (!name.isEmpty()) {
            File byName = new File(getAllLocalLibsDir(), name);
            if (byName.isDirectory()) {
                return byName;
            }
        }
        return null;
    }

    private void copyLocalLibsFromBackup(File extractedBackupRoot) {
        File localLibs = new File(extractedBackupRoot, "local_libs");
        if (!localLibs.isDirectory()) {
            return;
        }
        File[] content = localLibs.listFiles();
        if (content == null) {
            return;
        }
        for (File localLib : content) {
            if (!localLib.isDirectory()) {
                continue;
            }
            File targetLibDir = new File(getAllLocalLibsDir(), localLib.getName());
            if (!targetLibDir.exists()) {
                targetLibDir.mkdirs();
                copy(localLib, targetLibDir);
            }
        }
    }

    private void normalizeRestoredLocalLibraries(boolean pruneMissing) {
        File localLibFile = getLocalLibsPath();
        if (!localLibFile.isFile()) {
            return;
        }
        try {
            String raw = readTextUtf8(localLibFile).trim();
            if (raw.isEmpty()) {
                return;
            }
            JSONArray sourceArray = new JSONArray(raw);
            JSONArray normalizedArray = new JSONArray();
            File allLocalLibs = getAllLocalLibsDir();
            for (int i = 0; i < sourceArray.length(); i++) {
                JSONObject item = sourceArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) {
                    for (String key : new String[]{"dexPath", "jarPath", "resPath", "assetsPath"}) {
                        String p = item.optString(key, "");
                        if (!p.isEmpty() && new File(p).getParentFile() != null) {
                            name = new File(p).getParentFile().getName();
                            break;
                        }
                    }
                }
                if (name.isEmpty()) {
                    continue;
                }
                File libDir = new File(allLocalLibs, name);
                if (!libDir.isDirectory() && pruneMissing) {
                    continue;
                }
                rebindLocalLibPath(item, "resPath", new File(libDir, "res"), libDir.isDirectory());
                rebindLocalLibPath(item, "jarPath", new File(libDir, "classes.jar"), libDir.isDirectory());
                rebindLocalLibPath(item, "dexPath", new File(libDir, "classes.dex"), libDir.isDirectory());
                rebindLocalLibPath(item, "manifestPath", new File(libDir, "AndroidManifest.xml"), libDir.isDirectory());
                rebindLocalLibPath(item, "pgRulesPath", new File(libDir, "proguard.txt"), libDir.isDirectory());
                rebindLocalLibPath(item, "assetsPath", new File(libDir, "assets"), libDir.isDirectory());
                normalizedArray.put(item);
            }
            writeTextUtf8(localLibFile, normalizedArray.toString());
        } catch (Exception ignored) {
        }
    }

    private static void rebindLocalLibPath(JSONObject item, String key, File expectedTarget, boolean verifyExists) {
        if (!item.has(key)) {
            if (verifyExists && expectedTarget.exists()) {
                try {
                    item.put(key, expectedTarget.getAbsolutePath());
                } catch (Exception ignored) {
                }
            }
            return;
        }
        try {
            if (!verifyExists || expectedTarget.exists()) {
                item.put(key, expectedTarget.getAbsolutePath());
            } else {
                item.remove(key);
            }
        } catch (Exception ignored) {
        }
    }

    private void recoverStudioCustomIconIfNeeded(HashMap<String, Object> metadata, File studioFolder) {
        if (!Boolean.TRUE.equals(metadata.get("custom_icon"))) {
            return;
        }
        File iconTarget = new File(getResDir("icons"), "icon.png");
        if (iconTarget.isFile() && iconTarget.length() > 0) {
            return;
        }
        String[] mipmapDirs = {"mipmap-xxxhdpi", "mipmap-xxhdpi", "mipmap-xhdpi", "mipmap-hdpi", "mipmap-mdpi"};
        File resDir = new File(studioFolder, "app" + File.separator + "src" + File.separator + "main" + File.separator + "res");
        for (String density : mipmapDirs) {
            File candidate = new File(new File(resDir, density), "ic_launcher.png");
            if (candidate.isFile() && candidate.length() > 0) {
                copy(candidate, iconTarget);
                return;
            }
        }
    }

    private static void deleteRecursivelyQuiet(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursivelyQuiet(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String readTextUtf8(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void writeTextUtf8(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String formatError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(sw));
        String trace = sw.toString().trim();
        return trace.isEmpty() ? String.valueOf(throwable.getMessage()) : trace;
    }

    private String sanitizeFileName(String name) {
        String sanitized = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "ProjectBackup" : sanitized;
    }

    private static boolean unzipSafely(File zipFile, File destinationDir) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            destinationDir.mkdirs();
            String destinationPath = destinationDir.getCanonicalPath() + File.separator;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[4096];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File destFile = new File(destinationDir, entry.getName());
                String destPath = destFile.getCanonicalPath();
                if (!destPath.equals(destinationDir.getCanonicalPath()) && !destPath.startsWith(destinationPath)) {
                    return false;
                }
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                    continue;
                }
                File parent = destFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (InputStream input = zip.getInputStream(entry);
                     OutputStream output = new FileOutputStream(destFile)) {
                    int length;
                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static File findAndroidStudioProjectRoot(File directory) {
        if (looksLikeAndroidStudioProject(directory)) {
            return directory;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return null;
        }
        File onlyDirectory = null;
        for (File child : children) {
            if (child.isDirectory()) {
                File nested = findAndroidStudioProjectRoot(child);
                if (nested != null) {
                    if (onlyDirectory != null) {
                        return nested;
                    }
                    onlyDirectory = nested;
                }
            }
        }
        return onlyDirectory;
    }

    private static boolean looksLikeAndroidStudioProject(File directory) {
        return new File(directory, "app" + File.separator + "build.gradle").exists()
                || new File(directory, "app" + File.separator + "build.gradle.kts").exists()
                || new File(directory, "settings.gradle").exists()
                || new File(directory, "settings.gradle.kts").exists()
                || new File(directory, "build.gradle").exists()
                || new File(directory, "build.gradle.kts").exists();
    }

    private static HashMap<String, Object> readAndroidStudioProjectMetadata(File sourceFolder, File metadataFile) {
        HashMap<String, Object> metadata = (metadataFile != null && metadataFile.exists()) ? getProject(metadataFile) : null;
        if (metadata == null) {
            File fallbackFile = new File(sourceFolder, "project");
            metadata = fallbackFile.exists() ? getProject(fallbackFile) : null;
        }
        if (metadata == null) {
            metadata = new HashMap<>();
        }

        putIfMissing(metadata, "my_ws_name", detectProjectName(sourceFolder));
        putIfMissing(metadata, "my_app_name", detectAppName(sourceFolder, metadataString(metadata, "my_ws_name")));
        putIfMissing(metadata, "my_sc_pkg_name", detectPackageName(sourceFolder));
        putIfMissing(metadata, "sc_ver_code", detectGradleValue(sourceFolder, "versionCode", "1"));
        putIfMissing(metadata, "sc_ver_name", detectGradleString(sourceFolder, "versionName", "1.0"));
        putIfMissing(metadata, "sketchware_ver", 61);
        putIfMissing(metadata, "custom_icon", false);
        putIfMissing(metadata, "isIconAdaptive", false);
        normalizeAndroidStudioMetadata(metadata);
        return metadata;
    }

    private static void putIfMissing(HashMap<String, Object> metadata, String key, Object value) {
        if (metadataString(metadata, key).isEmpty()) {
            metadata.put(key, value);
        }
    }

    private static void normalizeAndroidStudioMetadata(HashMap<String, Object> metadata) {
        normalizeString(metadata, "sc_id");
        normalizeString(metadata, "my_ws_name");
        normalizeString(metadata, "my_app_name");
        normalizeString(metadata, "my_sc_pkg_name");
        normalizeString(metadata, "sc_ver_code");
        normalizeString(metadata, "sc_ver_name");
        normalizeInt(metadata, "sketchware_ver", 61);
        normalizeBoolean(metadata, "custom_icon", false);
        normalizeBoolean(metadata, "isIconAdaptive", false);
    }

    private static void normalizeString(HashMap<String, Object> metadata, String key) {
        if (metadata.containsKey(key)) {
            metadata.put(key, metadataString(metadata, key));
        }
    }

    private static void normalizeInt(HashMap<String, Object> metadata, String key, int fallback) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            metadata.put(key, ((Number) value).intValue());
            return;
        }
        if (value instanceof String) {
            try {
                metadata.put(key, Integer.parseInt(((String) value).trim()));
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        metadata.put(key, fallback);
    }

    private static void normalizeBoolean(HashMap<String, Object> metadata, String key, boolean fallback) {
        Object value = metadata.get(key);
        if (value instanceof Boolean) {
            return;
        }
        if (value instanceof Number) {
            metadata.put(key, ((Number) value).intValue() != 0);
            return;
        }
        if (value instanceof String) {
            String normalized = ((String) value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized)) {
                metadata.put(key, true);
                return;
            }
            if ("false".equals(normalized) || "0".equals(normalized) || normalized.isEmpty()) {
                metadata.put(key, false);
                return;
            }
        }
        metadata.put(key, fallback);
    }

    private static String metadataString(HashMap<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            double doubleValue = ((Number) value).doubleValue();
            if (doubleValue == Math.rint(doubleValue)) {
                return String.valueOf(((Number) value).longValue());
            }
        }
        return String.valueOf(value);
    }

    private static String detectProjectName(File sourceFolder) {
        String settings = readFileIfPresent(new File(sourceFolder, "settings.gradle"));
        if (settings.isEmpty()) {
            settings = readFileIfPresent(new File(sourceFolder, "settings.gradle.kts"));
        }
        String name = firstMatch(settings, "rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']", "");
        return name.isEmpty() ? sourceFolder.getName() : name;
    }

    private static String detectAppName(File sourceFolder, String fallback) {
        String strings = readFileIfPresent(new File(sourceFolder, "app" + File.separator + "src" + File.separator + "main" + File.separator + "res" + File.separator + "values" + File.separator + "strings.xml"));
        String appName = firstMatch(strings, "(?s)<string\\s+name=\"app_name\"\\s*>\\s*(.*?)\\s*</string>", "");
        return appName.isEmpty() ? fallback : unescapeXml(appName);
    }

    private static String detectPackageName(File sourceFolder) {
        String appBuild = readAppBuildFile(sourceFolder);
        String packageName = detectGradleString(appBuild, "applicationId", "");
        if (!packageName.isEmpty()) {
            return packageName;
        }
        String manifest = readFileIfPresent(new File(sourceFolder, "app" + File.separator + "src" + File.separator + "main" + File.separator + "AndroidManifest.xml"));
        packageName = firstMatch(manifest, "\\bpackage\\s*=\\s*\"([^\"]+)\"", "");
        return packageName.isEmpty() ? "com.my.restored" : packageName;
    }

    private static String detectGradleValue(File sourceFolder, String key, String fallback) {
        return firstMatch(readAppBuildFile(sourceFolder), "\\b" + Pattern.quote(key) + "\\b\\s*(?:=\\s*)?(\\d+)", fallback);
    }

    private static String detectGradleString(File sourceFolder, String key, String fallback) {
        return detectGradleString(readAppBuildFile(sourceFolder), key, fallback);
    }

    private static String detectGradleString(String content, String key, String fallback) {
        return firstMatch(content, "\\b" + Pattern.quote(key) + "\\b\\s*(?:=\\s*)?[\"']([^\"']+)[\"']", fallback);
    }

    private static String readAppBuildFile(File sourceFolder) {
        String content = readFileIfPresent(new File(sourceFolder, "app" + File.separator + "build.gradle"));
        return content.isEmpty() ? readFileIfPresent(new File(sourceFolder, "app" + File.separator + "build.gradle.kts")) : content;
    }

    private static String readFileIfPresent(File file) {
        if (!file.exists() || !file.isFile()) {
            return "";
        }
        try {
            return readTextUtf8(file);
        } catch (IOException e) {
            return "";
        }
    }

    private static String firstMatch(String content, String regex, String fallback) {
        if (content == null || content.isEmpty()) {
            return fallback;
        }
        Matcher matcher = Pattern.compile(regex).matcher(content);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private static String unescapeXml(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private File getProjectDirectory() {
        return new File(new File(resolveStorageRoot(), WorkspacePathManager.c), sc_id);
    }

    private File getDataDir() {
        return new File(resolveStorageRoot(), ".sketchware/data/" + sc_id);
    }

    private File getResDir(String subfolder) {
        return new File(resolveStorageRoot(), ".sketchware/resources/" + subfolder + "/" + sc_id);
    }

    private File getProjectPath() {
        return new File(getProjectDirectory(), "project");
    }

    private File getLocalLibsPath() {
        return new File(resolveStorageRoot(), ".sketchware/data/" + sc_id + "/local_library");
    }
}
