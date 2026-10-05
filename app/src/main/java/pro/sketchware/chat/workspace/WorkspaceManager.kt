package pro.sketchware.chat.workspace

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Gerenciador singleton que mantém o Workspace ativo e sua instância de WorkspaceFileSystem.
 */
object WorkspaceManager {

    private var activeWorkspaceInstance: Workspace? = null
    private var activeFileSystemInstance: WorkspaceFileSystem? = null

    @JvmStatic
    val activeWorkspace: Workspace?
        get() = activeWorkspaceInstance

    @JvmStatic
    val activeFileSystem: WorkspaceFileSystem?
        get() = activeFileSystemInstance

    @JvmStatic
    fun openWorkspace(context: Context, workspace: Workspace): WorkspaceFileSystem {
        activeWorkspaceInstance = workspace

        val fs: WorkspaceFileSystem = if (workspace.rootUri.startsWith("content://")) {
            val treeUri = Uri.parse(workspace.rootUri)
            SafWorkspaceFileSystem(context.applicationContext, treeUri)
        } else {
            val folder = File(workspace.rootUri)
            LocalFolderWorkspaceFileSystem(folder)
        }

        activeFileSystemInstance = fs
        WorkspaceRepository(context).updateLastOpened(workspace.id)
        return fs
    }

    @JvmStatic
    fun openFromTreeUri(context: Context, treeUri: Uri, customName: String? = null): Workspace {
        WorkspacePermissionManager.persistUriPermission(context, treeUri)

        val uriStr = treeUri.toString()
        val repo = WorkspaceRepository(context)
        var existing = repo.getByUri(uriStr)

        val finalName = if (!customName.isNullOrBlank()) {
            customName
        } else if (existing != null && existing.name.isNotBlank()) {
            existing.name
        } else {
            WorkspacePermissionManager.extractDisplayName(context, treeUri)
        }

        val fs = SafWorkspaceFileSystem(context.applicationContext, treeUri)
        val tech = WorkspaceDetector.detectTechnologies(fs)

        val workspace = (existing ?: Workspace(
            id = UUID.randomUUID().toString(),
            name = finalName,
            rootUri = uriStr,
            displayPath = treeUri.path ?: uriStr,
            isPinned = false,
            permissionState = Workspace.PermissionState.GRANTED,
            lastOpened = System.currentTimeMillis(),
            detectedTechnology = tech
        )).copy(name = finalName, lastOpened = System.currentTimeMillis(), detectedTechnology = tech)

        repo.save(workspace)
        openWorkspace(context, workspace)
        return workspace
    }

    @JvmStatic
    fun openLocalFolder(context: Context, folder: File, customName: String? = null): Workspace {
        val folderPath = folder.absolutePath
        val repo = WorkspaceRepository(context)
        var existing = repo.getByUri(folderPath)

        val finalName = if (!customName.isNullOrBlank()) customName else existing?.name ?: folder.name
        val fs = LocalFolderWorkspaceFileSystem(folder)
        val tech = WorkspaceDetector.detectTechnologies(fs)

        val workspace = (existing ?: Workspace(
            id = UUID.randomUUID().toString(),
            name = finalName,
            rootUri = folderPath,
            displayPath = folderPath,
            isPinned = false,
            permissionState = Workspace.PermissionState.GRANTED,
            lastOpened = System.currentTimeMillis(),
            detectedTechnology = tech
        )).copy(name = finalName, lastOpened = System.currentTimeMillis(), detectedTechnology = tech)

        repo.save(workspace)
        openWorkspace(context, workspace)
        return workspace
    }

    @JvmStatic
    fun getFileSystemForWorkspace(context: Context, workspaceId: String?): WorkspaceFileSystem? {
        if (workspaceId.isNullOrBlank()) return activeFileSystemInstance
        if (activeWorkspaceInstance?.id == workspaceId && activeFileSystemInstance != null) {
            return activeFileSystemInstance
        }
        val repo = WorkspaceRepository(context)
        val ws = repo.getById(workspaceId) ?: return null
        return openWorkspace(context, ws)
    }

    /** Makes an already-built workspace the active one (Sketchware projects are not stored in the repository). */
    @JvmStatic
    fun activate(fs: WorkspaceFileSystem, workspace: Workspace) {
        activeFileSystemInstance = fs
        activeWorkspaceInstance = workspace
    }

    @JvmStatic
    fun setCustomFileSystemForTesting(fs: WorkspaceFileSystem, workspace: Workspace) {
        activeFileSystemInstance = fs
        activeWorkspaceInstance = workspace
    }
}
