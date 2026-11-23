package pro.sketchware.activities.game.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pro.sketchware.filemanagers.ProjectResourceManager;
import pro.sketchware.workspace.ProjectDataManagerRegistry;
import pro.sketchware.workspace.WorkspacePathManager;

/**
 * Persistence and Sketchware project resource lookup for {@link GameProject}.
 * Uses atomic temp-file writes and a {@code .bak} safety copy to prevent silent project loss.
 */
public final class GameProjectRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum AssetKind {
        IMAGE,
        SOUND,
        FONT
    }

    public static final class LoadResult {
        public final GameProject project;
        public final boolean recoveredFromBackup;
        public final boolean createdFresh;
        public final String warning;

        public LoadResult(GameProject project, boolean recoveredFromBackup, boolean createdFresh, String warning) {
            this.project = project;
            this.recoveredFromBackup = recoveredFromBackup;
            this.createdFresh = createdFresh;
            this.warning = warning;
        }
    }

    private GameProjectRepository() {
    }

    public static File getGameDir(String scId) {
        return new File(WorkspacePathManager.b(safeScId(scId)), "game");
    }

    public static File getGameProjectFile(String scId) {
        return new File(getGameDir(scId), "game_project.json");
    }

    public static File getBackupFile(File projectFile) {
        return new File(projectFile.getParentFile(), projectFile.getName() + ".bak");
    }

    public static GameProject loadOrCreate(String scId) {
        String safeId = safeScId(scId);
        return loadOrCreateFromFile(getGameProjectFile(safeId), safeId).project;
    }

    /**
     * Loads a {@link GameProject} from {@code file}, falling back to {@code file.bak} if the
     * primary JSON is corrupted, and quarantining unreadable files instead of silently erasing them.
     */
    public static LoadResult loadOrCreateFromFile(File file, String scId) {
        String safeId = safeScId(scId);
        File backup = getBackupFile(file);

        if (file.exists() && file.isFile() && file.length() > 0) {
            try {
                String json = readUtf8File(file);
                GameProject parsed = fromJsonOrNull(json, safeId);
                if (parsed != null) {
                    return new LoadResult(parsed, false, false, null);
                }
            } catch (Throwable ignored) {
            }

            // Primary file exists but could not be parsed: try backup before creating fresh
            if (backup.exists() && backup.isFile() && backup.length() > 0) {
                try {
                    String bakJson = readUtf8File(backup);
                    GameProject recovered = fromJsonOrNull(bakJson, safeId);
                    if (recovered != null) {
                        quarantineCorruptFile(file);
                        saveToFile(file, safeId, recovered);
                        return new LoadResult(recovered, true, false,
                                "Recovered game_project.json from backup.");
                    }
                } catch (Throwable ignored) {
                }
            }

            // Preserve corrupt file so user work is never silently destroyed
            quarantineCorruptFile(file);
            GameProject created = GameProject.createDefault(safeId);
            saveToFile(file, safeId, created);
            return new LoadResult(created, false, true,
                    "Project JSON was corrupted; original file backed up as .corrupt.");
        }

        // Primary file missing: check backup before creating a new empty project
        if (backup.exists() && backup.isFile() && backup.length() > 0) {
            try {
                String bakJson = readUtf8File(backup);
                GameProject recovered = fromJsonOrNull(bakJson, safeId);
                if (recovered != null) {
                    saveToFile(file, safeId, recovered);
                    return new LoadResult(recovered, true, false,
                            "Restored missing game_project.json from backup.");
                }
            } catch (Throwable ignored) {
            }
        }

        GameProject created = GameProject.createDefault(safeId);
        saveToFile(file, safeId, created);
        return new LoadResult(created, false, true, null);
    }

    public static void save(String scId, GameProject project) {
        if (project == null) {
            return;
        }
        String safeId = safeScId(scId);
        saveToFile(getGameProjectFile(safeId), safeId, project);
    }

    public static boolean saveToFile(File file, String scId, GameProject project) {
        if (project == null || file == null) {
            return false;
        }
        String safeId = safeScId(scId);
        project.ensureInitialized(safeId);
        project.scId = safeId;

        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        return writeUtf8FileAtomic(file, toJson(project), true);
    }

    public static String toJson(GameProject project) {
        if (project == null) {
            return "{}";
        }
        return GSON.toJson(project);
    }

    public static GameProject fromJsonOrNull(String json, String scId) {
        String safeId = safeScId(scId);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            GameProject parsed = GSON.fromJson(json, GameProject.class);
            if (parsed == null) {
                return null;
            }
            if (!safeId.isEmpty()) parsed.scId = safeId;
            parsed.ensureInitialized(safeId);
            return parsed;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static GameProject fromJson(String json, String scId) {
        GameProject parsed = fromJsonOrNull(json, scId);
        return parsed != null ? parsed : GameProject.createDefault(safeScId(scId));
    }

    public static ArrayList<String> getProjectImageNames(String scId) {
        return getProjectAssetNames(scId, AssetKind.IMAGE);
    }

    public static ArrayList<String> getProjectSoundNames(String scId) {
        return getProjectAssetNames(scId, AssetKind.SOUND);
    }

    public static ArrayList<String> getProjectFontNames(String scId) {
        return getProjectAssetNames(scId, AssetKind.FONT);
    }

    public static ArrayList<String> getProjectAssetNames(String scId, AssetKind kind) {
        String safeId = safeScId(scId);
        Set<String> names = new LinkedHashSet<>();
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null) {
                List<String> managed = switch (kind) {
                    case IMAGE -> mgr.m();
                    case SOUND -> mgr.p();
                    case FONT -> mgr.k();
                };
                if (managed != null) {
                    for (String name : managed) {
                        addNormalizedName(names, name);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        collectDirectoryNames(getAssetDirectory(safeId, kind), names);
        return new ArrayList<>(names);
    }

    public static File getImageFile(String scId, String resName) {
        return getAssetFile(scId, resName, AssetKind.IMAGE);
    }

    public static File getSoundFile(String scId, String resName) {
        return getAssetFile(scId, resName, AssetKind.SOUND);
    }

    public static File getFontFile(String scId, String resName) {
        return getAssetFile(scId, resName, AssetKind.FONT);
    }

    public static File getAssetFile(String scId, String resName, AssetKind kind) {
        if (resName == null || resName.trim().isEmpty()) {
            return null;
        }
        String safeId = safeScId(scId);
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null) {
                String path = switch (kind) {
                    case IMAGE -> mgr.f(resName);
                    case SOUND -> mgr.i(resName);
                    case FONT -> mgr.d(resName);
                };
                if (path != null && !path.isEmpty()) {
                    File f = new File(path);
                    if (f.exists() && f.isFile()) {
                        return f;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        File found = findResourceFileInDir(getAssetDirectory(safeId, kind), resName);
        if (found != null) {
            return found;
        }
        // Fallback to project assets/ directory for Preview <-> APK asset parity
        try {
            File assetsDir = new File(WorkspacePathManager.b(safeId), "files/assets");
            return findResourceFileInDir(assetsDir, resName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File getAssetDirectory(String safeId, AssetKind kind) {
        return switch (kind) {
            case IMAGE -> new File(WorkspacePathManager.g(), safeId);
            case SOUND -> new File(WorkspacePathManager.t(), safeId);
            case FONT -> new File(WorkspacePathManager.d(), safeId);
        };
    }

    private static String safeScId(String scId) {
        return scId == null ? "" : scId.trim();
    }

    private static void addNormalizedName(Set<String> out, String rawName) {
        if (rawName == null) {
            return;
        }
        String stripped = stripResourceExtension(rawName.trim());
        if (!stripped.isEmpty()) {
            out.add(stripped);
        }
    }

    private static void collectDirectoryNames(File dir, Set<String> out) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null && file.isFile() && !file.getName().startsWith(".")) {
                addNormalizedName(out, file.getName());
            }
        }
    }

    private static File findResourceFileInDir(File dir, String resName) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || resName == null) {
            return null;
        }
        String trimmed = resName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        File direct = new File(dir, trimmed);
        if (direct.exists() && direct.isFile()) {
            return direct;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        String targetBase = stripResourceExtension(trimmed).toLowerCase(Locale.ROOT);
        String targetSanitized = sanitizeResourceKey(targetBase);
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String fileName = file.getName();
            if (fileName.equalsIgnoreCase(trimmed)) {
                return file;
            }
            String fileBase = stripResourceExtension(fileName).toLowerCase(Locale.ROOT);
            if (fileBase.equals(targetBase) || sanitizeResourceKey(fileBase).equals(targetSanitized)) {
                return file;
            }
        }
        return null;
    }

    private static String stripResourceExtension(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".9.png")) {
            return name.substring(0, name.length() - 6);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private static String sanitizeResourceKey(String base) {
        if (base == null) {
            return "";
        }
        return base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static void quarantineCorruptFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            File corrupt = new File(file.getParentFile(),
                    file.getName() + ".corrupt." + System.currentTimeMillis());
            if (!file.renameTo(corrupt)) {
                byte[] raw = readBytes(file);
                try (FileOutputStream fos = new FileOutputStream(corrupt, false)) {
                    fos.write(raw);
                    fos.flush();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = fis.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }
            return bos.toByteArray();
        }
    }

    public static String readUtf8File(File file) throws IOException {
        return new String(readBytes(file), StandardCharsets.UTF_8);
    }

    public static boolean writeUtf8FileAtomic(File file, String content, boolean keepBackup) {
        if (file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        File tmp = new File(parent, file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp, false)) {
            fos.write(bytes);
            fos.flush();
            try {
                fos.getFD().sync();
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            tmp.delete();
            return false;
        }

        if (keepBackup && file.exists() && file.length() > 0) {
            File bak = getBackupFile(file);
            try {
                byte[] prev = readBytes(file);
                try (FileOutputStream bfos = new FileOutputStream(bak, false)) {
                    bfos.write(prev);
                    bfos.flush();
                }
            } catch (IOException ignored) {
            }
        }

        if (tmp.renameTo(file)) {
            return true;
        }
        // Fallback for Windows / filesystems where renameTo fails over an existing target
        if (file.exists() && file.delete() && tmp.renameTo(file)) {
            return true;
        }
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(bytes);
            fos.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            tmp.delete();
        }
    }
}
