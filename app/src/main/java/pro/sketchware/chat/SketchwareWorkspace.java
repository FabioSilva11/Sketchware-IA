package pro.sketchware.chat;

import android.os.Environment;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashMap;

import a.a.a.wq;
import pro.sketchware.chat.workspace.LocalFolderWorkspaceFileSystem;
import pro.sketchware.chat.workspace.SketchwareProjectFileSystem;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspaceManager;

/**
 * Turns a Sketchware project id into the workspace the agent works in: the project folder of an Android Studio
 * project, or {@link SketchwareProjectFileSystem} for a native project. Every run resolves its files through here,
 * so a chat can only ever touch its own project.
 */
public final class SketchwareWorkspace {

    private SketchwareWorkspace() {
    }

    public static File sketchwareRoot() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware");
    }

    /** @return The project's workspace, or null when no project has this id. */
    @Nullable
    public static Workspace workspaceOf(@Nullable String scId) {
        if (scId == null || scId.trim().isEmpty()) {
            return null;
        }
        HashMap<String, Object> project = ProjectManager.b(scId);
        if (project == null) {
            return null;
        }
        Object name = project.get("my_ws_name");
        String root = rootOf(scId).getAbsolutePath();
        return new Workspace(scId, name instanceof String s && !s.isEmpty() ? s : scId, root,
                displayPathOf(scId), false, Workspace.PermissionState.GRANTED, System.currentTimeMillis(),
                ProjectManager.isAndroidStudioProject(scId) ? "Android Studio (Gradle)" : "Sketchware");
    }

    /** @return The files the agent may use for this project, or null when no project has this id. */
    @Nullable
    public static WorkspaceFileSystem fileSystemOf(@Nullable String scId) {
        if (workspaceOf(scId) == null) {
            return null;
        }
        if (ProjectManager.isAndroidStudioProject(scId)) {
            return new LocalFolderWorkspaceFileSystem(rootOf(scId));
        }
        return SketchwareProjectFileSystem.forProject(scId, sketchwareRoot());
    }

    /** Makes the project the active workspace of the chat UI (references, diffs, skills). */
    public static boolean activate(@Nullable String scId) {
        Workspace workspace = workspaceOf(scId);
        WorkspaceFileSystem fileSystem = fileSystemOf(scId);
        if (workspace == null || fileSystem == null) {
            return false;
        }
        WorkspaceManager.activate(fileSystem, workspace);
        return true;
    }

    private static File rootOf(String scId) {
        return ProjectManager.isAndroidStudioProject(scId)
                ? new File(wq.getAndroidStudioProjectPath(scId))
                : sketchwareRoot();
    }

    private static String displayPathOf(String scId) {
        return ProjectManager.isAndroidStudioProject(scId)
                ? wq.ANDROID_STUDIO_PROJECTS + "/" + scId
                : ".sketchware (data/" + scId + ", mysc/" + scId + ", mysc/list/" + scId + ")";
    }
}
