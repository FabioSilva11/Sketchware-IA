package pro.sketchware.workspace;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

import pro.sketchware.core.MapJsonCodec;
import pro.sketchware.core.MapValueUtils;
import pro.sketchware.core.PreferenceStore;
import pro.sketchware.filemanagers.FileCryptoUtils;

public class RecentProjectsRegistry {
    public static final String PROJECT_KIND_KEY = "project_kind";
    public static final String PROJECT_KIND_SKETCHWARE = "sketchware";
    public static final String PROJECT_KIND_ANDROID_STUDIO = "android_studio";
    public static final String PROJECT_KIND_GAME_2D = "game_2d";
    // Lazily shared store handle, touched from several threads (activities, data reset).
    public static volatile PreferenceStore a;

    public static ArrayList<HashMap<String, Object>> a() {
        ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
        File[] directories = new File(WorkspacePathManager.n()).listFiles();
        if (directories != null) {
            for (File directory : directories) {
                HashMap<String, Object> metadata = readProjectMetadata(directory, directory.getName());
                if (metadata != null) {
                    arrayList.add(metadata);
                }
            }
        }
        return arrayList;
    }

    public static HashMap<String, Object> a(String str) {
        for (HashMap<String, Object> stringObjectHashMap : a()) {
            if (MapValueUtils.c(stringObjectHashMap, "my_sc_pkg_name").equals(str) && MapValueUtils.b(stringObjectHashMap, "proj_type") == 1) {
                return stringObjectHashMap;
            }
        }
        return null;
    }

    public static void a(Context context, String str) {
        File file = new File(WorkspacePathManager.c(str));
        if (file.exists()) {
            FileCryptoUtils oBVar = new FileCryptoUtils();
            oBVar.a(file);
            oBVar.b(WorkspacePathManager.d(str));
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(WorkspacePathManager.g());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(WorkspacePathManager.t());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(WorkspacePathManager.d());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(WorkspacePathManager.e());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            oBVar.b(WorkspacePathManager.b(str));
            oBVar.b(WorkspacePathManager.a(str));
            stringBuilder = new StringBuilder();
            stringBuilder.append("D01_");
            stringBuilder.append(str);
            new PreferenceStore(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D02_");
            stringBuilder.append(str);
            new PreferenceStore(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D03_");
            stringBuilder.append(str);
            new PreferenceStore(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D04_");
            stringBuilder.append(str);
            new PreferenceStore(context, stringBuilder.toString()).a();
        }
    }

    public static void deleteAndroidStudioProject(String str) {
        String projectPath = WorkspacePathManager.getAndroidStudioProjectPath(str);
        // Run the legacy helper first, then guarantee removal with a recursive
        // delete so the project folder under the project list and auxiliary files are cleared.
        FileCryptoUtils fileCryptoUtils = new FileCryptoUtils();
        fileCryptoUtils.b(projectPath);
        deleteRecursively(new File(projectPath));
        deleteRecursively(new File(WorkspacePathManager.b(str)));
        deleteRecursively(new File(WorkspacePathManager.e() + File.separator + str));
        deleteRecursively(new File(WorkspacePathManager.g() + File.separator + str));
        deleteRecursively(new File(WorkspacePathManager.t() + File.separator + str));
        deleteRecursively(new File(WorkspacePathManager.d() + File.separator + str));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            Log.e("ERROR", "Failed to delete " + file.getAbsolutePath());
        }
    }

    public static void a(Context context, boolean z) {
        if (a == null) {
            synchronized (RecentProjectsRegistry.class) {
                if (a == null) {
                    a = new PreferenceStore(context, "P15");
                }
            }
        }
    }

    public static void a(String str, HashMap<String, Object> hashMap) {
        if (MapValueUtils.c(hashMap, PROJECT_KIND_KEY).isEmpty()) {
            hashMap.put(PROJECT_KIND_KEY, PROJECT_KIND_SKETCHWARE);
        }
        saveProjectMetadata(WorkspacePathManager.c(str), hashMap);
    }

    public static void saveAndroidStudioProject(String str, HashMap<String, Object> hashMap) {
        hashMap.put(PROJECT_KIND_KEY, PROJECT_KIND_ANDROID_STUDIO);
        hashMap.put("proj_type", 2);
        hashMap.put("studio_path", WorkspacePathManager.getAndroidStudioProjectPath(str));
        saveProjectMetadata(WorkspacePathManager.getAndroidStudioProjectPath(str), hashMap);
    }

    private static void saveProjectMetadata(String projectDirectory, HashMap<String, Object> hashMap) {
        File file = new File(projectDirectory);
        if (!file.exists()) {
            file.mkdirs();
        }
        String projectMetadataPath = projectDirectory + File.separator + "project";
        String a = MapJsonCodec.a(hashMap);
        FileCryptoUtils oBVar = new FileCryptoUtils();
        try {
            oBVar.a(projectMetadataPath, oBVar.d(a));
        } catch (Throwable e) {
            Log.e("ERROR", e.getMessage(), e);
        }
    }

    public static String b() {
        int parseInt = Integer.parseInt("600") + 1;
        Set<String> reservedProjectIds = new HashSet<>();
        collectProjectDirectoryNames(reservedProjectIds, new File(WorkspacePathManager.n()));
        // A project removed outside Sketchware can leave data/<id> or mysc/<id> behind; reusing that id would hand

        // the new project the old project's generated code and build output.

        collectProjectDirectoryNames(reservedProjectIds, new File(WorkspacePathManager.getAbsolutePathOf(WorkspacePathManager.d)));
        collectProjectDirectoryNames(reservedProjectIds, new File(WorkspacePathManager.getAbsolutePathOf(WorkspacePathManager.b)));
        for (HashMap<String, Object> stringObjectHashMap : a()) {
            try {
                String projectId = MapValueUtils.c(stringObjectHashMap, "sc_id");
                reservedProjectIds.add(projectId);
                parseInt = Math.max(parseInt, Integer.parseInt(projectId) + 1);
            } catch (Exception ignored) {
            }
        }
        for (String projectId : reservedProjectIds) {
            try {
                parseInt = Math.max(parseInt, Integer.parseInt(projectId) + 1);
            } catch (Exception ignored) {
            }
        }
        while (reservedProjectIds.contains(String.valueOf(parseInt))) {
            parseInt++;
        }
        return String.valueOf(parseInt);
    }

    private static void collectProjectDirectoryNames(Set<String> projectIds, File root) {
        File[] listFiles = root.listFiles();
        if (listFiles == null) {
            return;
        }
        for (File file : listFiles) {
            if (file.exists()) {
                projectIds.add(file.getName());
            }
        }
    }

    public static HashMap<String, Object> b(String str) {
        return readProjectMetadata(new File(WorkspacePathManager.c(str)), str);
    }

    private static HashMap<String, Object> readProjectMetadata(File projectDirectory, String expectedId) {
        Throwable e;
        FileCryptoUtils oBVar = new FileCryptoUtils();
        HashMap<String, Object> hashMap = null;
        try {
            if (!new File(projectDirectory, "project").isFile()) {
                return null;
            }
            String path = projectDirectory.getAbsolutePath() + File.separator + "project";
            HashMap<String, Object> a = MapJsonCodec.a(oBVar.a(oBVar.h(path)));
            try {
                if (!MapValueUtils.c(a, "sc_id").equals(expectedId)) {
                    return null;
                }
                if (MapValueUtils.c(a, PROJECT_KIND_KEY).isEmpty()) {
                    a.put(PROJECT_KIND_KEY, PROJECT_KIND_SKETCHWARE);
                }
                if (isAndroidStudioProject(a)) {
                    a.put(PROJECT_KIND_KEY, PROJECT_KIND_ANDROID_STUDIO);
                    a.put("proj_type", 2);
                    a.put("studio_path", projectDirectory.getAbsolutePath());
                }
                return a;
            } catch (Exception e2) {
                e = e2;
                hashMap = a;
                Log.e("ERROR", e.getMessage(), e);
                return hashMap;
            }
        } catch (Exception e3) {
            e = e3;
            Log.e("ERROR", e.getMessage(), e);
            return hashMap;
        }
    }

    public static void b(String str, HashMap<String, Object> hashMap) {
        File file = new File(WorkspacePathManager.c(str));
        if (file.exists()) {
            String path = file + File.separator + "project";
            FileCryptoUtils fileUtil = new FileCryptoUtils();
            try {
                HashMap<String, Object> a = MapJsonCodec.a(fileUtil.a(fileUtil.h(path)));
                if (MapValueUtils.c(a, "sc_id").equals(str)) {
                    if (hashMap.containsKey("isIconAdaptive")) {
                        a.put("isIconAdaptive", hashMap.get("isIconAdaptive"));
                    }
                    if (hashMap.containsKey("custom_icon")) {
                        a.put("custom_icon", hashMap.get("custom_icon"));
                    }
                    a.put("my_sc_pkg_name", hashMap.get("my_sc_pkg_name"));
                    a.put("my_ws_name", hashMap.get("my_ws_name"));
                    a.put("my_app_name", hashMap.get("my_app_name"));
                    a.put("sc_ver_code", hashMap.get("sc_ver_code"));
                    a.put("sc_ver_name", hashMap.get("sc_ver_name"));
                    a.put("sketchware_ver", hashMap.get("sketchware_ver"));
                    a.put("color_accent", hashMap.get("color_accent"));
                    a.put("color_primary", hashMap.get("color_primary"));
                    a.put("color_primary_dark", hashMap.get("color_primary_dark"));
                    a.put("color_control_highlight", hashMap.get("color_control_highlight"));
                    a.put("color_control_normal", hashMap.get("color_control_normal"));
                    if (hashMap.containsKey(PROJECT_KIND_KEY)) {
                        a.put(PROJECT_KIND_KEY, hashMap.get(PROJECT_KIND_KEY));
                    }
                    if (hashMap.containsKey("proj_type")) {
                        a.put("proj_type", hashMap.get("proj_type"));
                    }
                    if (hashMap.containsKey("studio_path")) {
                        a.put("studio_path", hashMap.get("studio_path"));
                    }
                    fileUtil.a(path, fileUtil.d(MapJsonCodec.a(a)));
                }
            } catch (Throwable e) {
                Log.e("DEBUG", e.getMessage(), e);
            }
        }
    }

    public static boolean isAndroidStudioProject(HashMap<String, Object> projectMap) {
        return PROJECT_KIND_ANDROID_STUDIO.equals(MapValueUtils.c(projectMap, PROJECT_KIND_KEY)) || MapValueUtils.b(projectMap, "proj_type") == 2;
    }

    public static boolean isGameProject(HashMap<String, Object> projectMap) {
        return PROJECT_KIND_GAME_2D.equals(MapValueUtils.c(projectMap, PROJECT_KIND_KEY));
    }

    public static String c() {
        ArrayList<HashMap<String, Object>> var0 = a();
        ArrayList<Integer> projectIndices = new ArrayList<>();

        for (HashMap<String, Object> stringObjectHashMap : var0) {
            String workspaceName = MapValueUtils.c(stringObjectHashMap, "my_ws_name");
            if (workspaceName.equals("NewProject")) {
                projectIndices.add(1);
            } else if (workspaceName.indexOf("NewProject") == 0) {
                try {
                    projectIndices.add(Integer.parseInt(workspaceName.substring(10)));
                } catch (Exception ignored) {
                }
            }
        }

        projectIndices.sort(new IntegerComparator());
        int var3 = 0;

        for (int nextProjectIndex : projectIndices) {
            int var5 = var3 + 1;
            if (nextProjectIndex == var5) {
                var3 = var5;
            } else {
                if (nextProjectIndex == var3) {
                    continue;
                }
                break;
            }
        }

        if (var3 == 0) {
            return "NewProject";
        } else {
            return "NewProject" + (var3 + 1);
        }
    }

    public static void d() {
        for (String str : (Set<String>)a.c().keySet()) {
            a(str, a.g(str));
        }
        a.a();
    }

    private static class IntegerComparator implements Comparator<Integer> {

        @Override
        public int compare(Integer first, Integer second) {
            return first.compareTo(second);
        }
    }
}
