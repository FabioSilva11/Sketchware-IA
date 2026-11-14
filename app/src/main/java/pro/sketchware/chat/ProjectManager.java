package pro.sketchware.chat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import pro.sketchware.workspace.RecentProjectsRegistry;
import pro.sketchware.workspace.WorkspacePathManager;

/**
 * Sketchware's project list as the ported chat expects it. Projects are Sketchware's own (lC); an Android Studio
 * project lives in one folder, a native Sketchware project is spread over data/, mysc/ and mysc/list/ and is served
 * to the agent by {@link SketchwareProjectFileSystem}.
 */
public final class ProjectManager {

    public static final String PROJECT_KIND_KEY = RecentProjectsRegistry.PROJECT_KIND_KEY;
    public static final String PROJECT_KIND_ANDROID_STUDIO = RecentProjectsRegistry.PROJECT_KIND_ANDROID_STUDIO;
    public static final String PROJECT_KIND_SKETCHWARE = RecentProjectsRegistry.PROJECT_KIND_SKETCHWARE;

    private ProjectManager() {
    }

    /** Every project, sorted as Sketchware's main screen sorts them. */
    public static ArrayList<HashMap<String, Object>> a() {
        return RecentProjectsRegistry.a();
    }

    /** The project's metadata (name, package, kind...), or null when it doesn't exist. */
    public static HashMap<String, Object> b(String scId) {
        return RecentProjectsRegistry.b(scId);
    }

    public static void b(String scId, HashMap<String, Object> data) {
        RecentProjectsRegistry.b(scId, data);
    }

    public static boolean isAndroidStudioProject(String scId) {
        try {
            HashMap<String, Object> project = RecentProjectsRegistry.b(scId);
            return project != null && RecentProjectsRegistry.isAndroidStudioProject(project);
        } catch (Exception e) {
            return false;
        }
    }

    public static String getAndroidStudioProjectsRoot() {
        return WorkspacePathManager.getAbsolutePathOf(WorkspacePathManager.ANDROID_STUDIO_PROJECTS);
    }

    /** Sketchware has no web projects; kept so the ported path checks compile to "no such root". */
    public static String getWebProjectsRoot() {
        return "";
    }

    /**
     * The folder that holds the project's chat state and that relative tool paths start from: the project folder for
     * Android Studio projects, the {@code .sketchware} root for native projects (whose files are addressed as
     * {@code data/<id>/...}, {@code mysc/<id>/...} and {@code mysc/list/<id>/...}).
     */
    public static String getProjectDir(String scId) {
        if (isAndroidStudioProject(scId)) {
            return WorkspacePathManager.getAndroidStudioProjectPath(scId);
        }
        return new File(WorkspacePathManager.getAbsolutePathOf(".sketchware")).getAbsolutePath();
    }
}
