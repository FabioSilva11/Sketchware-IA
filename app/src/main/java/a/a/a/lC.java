package a.a.a;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

public class lC {
    public static final String PROJECT_KIND_KEY = "project_kind";
    public static final String PROJECT_KIND_SKETCHWARE = "sketchware";
    public static final String PROJECT_KIND_ANDROID_STUDIO = "android_studio";
    public static DB a;

    public static ArrayList<HashMap<String, Object>> a() {
        ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
        oB oBVar = new oB();
        Set<String> knownProjectIds = new HashSet<>();
        addProjectsFromRoot(arrayList, new File(wq.n()), oBVar, knownProjectIds, PROJECT_KIND_SKETCHWARE);
        addProjectsFromRoot(arrayList, new File(wq.getAndroidStudioProjectsRoot()), oBVar, knownProjectIds, PROJECT_KIND_ANDROID_STUDIO);
        return arrayList;
    }

    private static void addProjectsFromRoot(ArrayList<HashMap<String, Object>> projects, File root, oB fileUtil,
                                            Set<String> knownProjectIds, String projectKind) {
        File[] listFiles = root.listFiles();
        if (listFiles == null) {
            return;
        }
        for (File file : listFiles) {
            try {
                if (knownProjectIds.contains(file.getName())) {
                    continue;
                }
                File projectMetadata = new File(file, "project");
                if (projectMetadata.exists()) {
                    HashMap<String, Object> metadata = vB.a(fileUtil.a(fileUtil.h(projectMetadata.getAbsolutePath())));
                    if (yB.c(metadata, "sc_id").equals(file.getName())) {
                        if (yB.c(metadata, PROJECT_KIND_KEY).isEmpty()) {
                            metadata.put(PROJECT_KIND_KEY, projectKind);
                        }
                        if (PROJECT_KIND_ANDROID_STUDIO.equals(projectKind)) {
                            metadata.put("proj_type", 2);
                            metadata.put("studio_path", file.getAbsolutePath());
                        }
                        projects.add(metadata);
                        knownProjectIds.add(file.getName());
                    }
                }
            } catch (Throwable e) {
                Log.e("ERROR", e.getMessage(), e);
            }
        }
    }

    public static HashMap<String, Object> a(String str) {
        for (HashMap<String, Object> stringObjectHashMap : a()) {
            if (yB.c(stringObjectHashMap, "my_sc_pkg_name").equals(str) && yB.b(stringObjectHashMap, "proj_type") == 1) {
                return stringObjectHashMap;
            }
        }
        return null;
    }

    public static void a(Context context, String str) {
        File file = new File(wq.c(str));
        if (file.exists()) {
            oB oBVar = new oB();
            oBVar.a(file);
            oBVar.b(wq.d(str));
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(wq.g());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(wq.t());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(wq.d());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            stringBuilder = new StringBuilder();
            stringBuilder.append(wq.e());
            stringBuilder.append(File.separator);
            stringBuilder.append(str);
            oBVar.b(stringBuilder.toString());
            oBVar.b(wq.b(str));
            oBVar.b(wq.a(str));
            stringBuilder = new StringBuilder();
            stringBuilder.append("D01_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D02_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D03_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
            stringBuilder = new StringBuilder();
            stringBuilder.append("D04_");
            stringBuilder.append(str);
            new DB(context, stringBuilder.toString()).a();
        }
    }

    public static void deleteAndroidStudioProject(String str) {
        String projectPath = wq.getAndroidStudioProjectPath(str);
        // Run the legacy helper first, then guarantee removal with a recursive
        // delete so the project folder under .sketcware_ide is always cleared.
        new oB().b(projectPath);
        deleteRecursively(new File(projectPath));
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
            a = new DB(context, "P15");
        }
    }

    public static void a(String str, HashMap<String, Object> hashMap) {
        hashMap.put(PROJECT_KIND_KEY, PROJECT_KIND_SKETCHWARE);
        saveProjectMetadata(wq.c(str), hashMap);
    }

    public static void saveAndroidStudioProject(String str, HashMap<String, Object> hashMap) {
        hashMap.put(PROJECT_KIND_KEY, PROJECT_KIND_ANDROID_STUDIO);
        hashMap.put("proj_type", 2);
        hashMap.put("studio_path", wq.getAndroidStudioProjectPath(str));
        saveProjectMetadata(wq.getAndroidStudioProjectPath(str), hashMap);
    }

    private static void saveProjectMetadata(String projectDirectory, HashMap<String, Object> hashMap) {
        File file = new File(projectDirectory);
        if (!file.exists()) {
            file.mkdirs();
        }
        String projectMetadataPath = projectDirectory + File.separator + "project";
        String a = vB.a(hashMap);
        oB oBVar = new oB();
        try {
            oBVar.a(projectMetadataPath, oBVar.d(a));
        } catch (Throwable e) {
            Log.e("ERROR", e.getMessage(), e);
        }
    }

    public static String b() {
        int parseInt = Integer.parseInt("600") + 1;
        Set<String> reservedProjectIds = new HashSet<>();
        collectProjectDirectoryNames(reservedProjectIds, new File(wq.n()));
        collectProjectDirectoryNames(reservedProjectIds, new File(wq.getAndroidStudioProjectsRoot()));
        for (HashMap<String, Object> stringObjectHashMap : a()) {
            try {
                String projectId = yB.c(stringObjectHashMap, "sc_id");
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
        HashMap<String, Object> nativeProject = readProjectMetadata(new File(wq.c(str)), str, PROJECT_KIND_SKETCHWARE);
        if (nativeProject != null) {
            return nativeProject;
        }
        return readProjectMetadata(new File(wq.getAndroidStudioProjectPath(str)), str, PROJECT_KIND_ANDROID_STUDIO);
    }

    private static HashMap<String, Object> readProjectMetadata(File projectDirectory, String expectedId, String projectKind) {
        Throwable e;
        oB oBVar = new oB();
        HashMap<String, Object> hashMap = null;
        try {
            if (!projectDirectory.exists()) {
                return null;
            }
            String path = projectDirectory.getAbsolutePath() + File.separator + "project";
            HashMap<String, Object> a = vB.a(oBVar.a(oBVar.h(path)));
            try {
                if (!yB.c(a, "sc_id").equals(expectedId)) {
                    return null;
                }
                if (yB.c(a, PROJECT_KIND_KEY).isEmpty()) {
                    a.put(PROJECT_KIND_KEY, projectKind);
                }
                if (PROJECT_KIND_ANDROID_STUDIO.equals(projectKind)) {
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
        File file = getProjectDirectoryForUpdate(str, hashMap);
        if (file.exists()) {
            String path = file + File.separator + "project";
            oB fileUtil = new oB();
            try {
                HashMap<String, Object> a = vB.a(fileUtil.a(fileUtil.h(path)));
                if (yB.c(a, "sc_id").equals(str)) {
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
                    fileUtil.a(path, fileUtil.d(vB.a(a)));
                }
            } catch (Throwable e) {
                Log.e("DEBUG", e.getMessage(), e);
            }
        }
    }

    private static File getProjectDirectoryForUpdate(String scId, HashMap<String, Object> hashMap) {
        File nativeProject = new File(wq.c(scId));
        File androidStudioProject = new File(wq.getAndroidStudioProjectPath(scId));
        if (isAndroidStudioProject(hashMap) && androidStudioProject.exists()) {
            return androidStudioProject;
        }
        if (nativeProject.exists()) {
            return nativeProject;
        }
        return androidStudioProject;
    }

    public static boolean isAndroidStudioProject(HashMap<String, Object> projectMap) {
        return PROJECT_KIND_ANDROID_STUDIO.equals(yB.c(projectMap, PROJECT_KIND_KEY)) || yB.b(projectMap, "proj_type") == 2;
    }

    public static String c() {
        ArrayList<HashMap<String, Object>> var0 = a();
        ArrayList<Integer> projectIndices = new ArrayList<>();

        for (HashMap<String, Object> stringObjectHashMap : var0) {
            String workspaceName = yB.c(stringObjectHashMap, "my_ws_name");
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
        for (String str : a.c().keySet()) {
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
