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
import java.util.Locale;
import java.util.Set;

import pro.sketchware.filemanagers.ProjectResourceManager;
import pro.sketchware.workspace.ProjectDataManagerRegistry;
import pro.sketchware.workspace.WorkspacePathManager;

/**
 * Persistence and Sketchware project resource lookup for {@link GameProject}.
 */
public final class GameProjectRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GameProjectRepository() {
    }

    public static File getGameDir(String scId) {
        return new File(WorkspacePathManager.b(safeScId(scId)), "game");
    }

    public static File getGameProjectFile(String scId) {
        return new File(getGameDir(scId), "game_project.json");
    }

    public static GameProject loadOrCreate(String scId) {
        String safeId = safeScId(scId);
        File file = getGameProjectFile(safeId);
        if (file.exists() && file.isFile() && file.length() > 0) {
            try {
                String json = readUtf8File(file);
                if (json != null && !json.trim().isEmpty()) {
                    GameProject parsed = fromJson(json, safeId);
                    if (parsed != null) {
                        return parsed;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        GameProject created = GameProject.createDefault(safeId);
        save(safeId, created);
        return created;
    }

    public static void save(String scId, GameProject project) {
        if (project == null) {
            return;
        }
        String safeId = safeScId(scId);
        project.ensureInitialized(safeId);
        project.scId = safeId;

        File dir = getGameDir(safeId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = getGameProjectFile(safeId);
        writeUtf8File(file, toJson(project));
    }

    public static String toJson(GameProject project) {
        if (project == null) {
            return "{}";
        }
        return GSON.toJson(project);
    }

    public static GameProject fromJson(String json, String scId) {
        String safeId = safeScId(scId);
        if (json == null || json.trim().isEmpty()) {
            return GameProject.createDefault(safeId);
        }
        try {
            GameProject parsed = GSON.fromJson(json, GameProject.class);
            if (parsed == null) {
                return GameProject.createDefault(safeId);
            }
            parsed.ensureInitialized(safeId);
            return parsed;
        } catch (Throwable ignored) {
            return GameProject.createDefault(safeId);
        }
    }

    public static ArrayList<String> getProjectImageNames(String scId) {
        String safeId = safeScId(scId);
        Set<String> names = new LinkedHashSet<>();
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null && mgr.m() != null) {
                for (String name : mgr.m()) {
                    addNormalizedName(names, name);
                }
            }
        } catch (Throwable ignored) {
        }
        collectDirectoryNames(new File(WorkspacePathManager.g(), safeId), names);
        return new ArrayList<>(names);
    }

    public static ArrayList<String> getProjectSoundNames(String scId) {
        String safeId = safeScId(scId);
        Set<String> names = new LinkedHashSet<>();
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null && mgr.p() != null) {
                for (String name : mgr.p()) {
                    addNormalizedName(names, name);
                }
            }
        } catch (Throwable ignored) {
        }
        collectDirectoryNames(new File(WorkspacePathManager.t(), safeId), names);
        return new ArrayList<>(names);
    }

    public static ArrayList<String> getProjectFontNames(String scId) {
        String safeId = safeScId(scId);
        Set<String> names = new LinkedHashSet<>();
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null && mgr.k() != null) {
                for (String name : mgr.k()) {
                    addNormalizedName(names, name);
                }
            }
        } catch (Throwable ignored) {
        }
        collectDirectoryNames(new File(WorkspacePathManager.d(), safeId), names);
        return new ArrayList<>(names);
    }

    public static File getImageFile(String scId, String resName) {
        if (resName == null || resName.trim().isEmpty()) {
            return null;
        }
        String safeId = safeScId(scId);
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null) {
                String path = mgr.f(resName);
                if (path != null && !path.isEmpty()) {
                    File f = new File(path);
                    if (f.exists() && f.isFile()) {
                        return f;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return findResourceFileInDir(new File(WorkspacePathManager.g(), safeId), resName);
    }

    public static File getSoundFile(String scId, String resName) {
        if (resName == null || resName.trim().isEmpty()) {
            return null;
        }
        String safeId = safeScId(scId);
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null) {
                String path = mgr.i(resName);
                if (path != null && !path.isEmpty()) {
                    File f = new File(path);
                    if (f.exists() && f.isFile()) {
                        return f;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return findResourceFileInDir(new File(WorkspacePathManager.t(), safeId), resName);
    }

    public static File getFontFile(String scId, String resName) {
        if (resName == null || resName.trim().isEmpty()) {
            return null;
        }
        String safeId = safeScId(scId);
        try {
            ProjectResourceManager mgr = ProjectDataManagerRegistry.d(safeId);
            if (mgr != null) {
                String path = mgr.d(resName);
                if (path != null && !path.isEmpty()) {
                    File f = new File(path);
                    if (f.exists() && f.isFile()) {
                        return f;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return findResourceFileInDir(new File(WorkspacePathManager.d(), safeId), resName);
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

    private static String readUtf8File(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = fis.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeUtf8File(File file, String content) {
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException ignored) {
        }
    }
}
