package pro.sketchware.chat.workspace

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositório responsável pela persistência da lista de workspaces recentes e seus metadados.
 */
class WorkspaceRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "axion_workspaces_pref"
        private const val KEY_WORKSPACES = "workspaces_list"
    }

    @Synchronized
    fun getAll(): List<Workspace> {
        val jsonStr = prefs.getString(KEY_WORKSPACES, null) ?: return emptyList()
        val list = mutableListOf<Workspace>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(Workspace.fromJson(item))
            }
        } catch (_: Exception) {
        }
        // Pinned primeiro, depois ordenados pelo último acesso decrescente
        return list.sortedWith(compareByDescending<Workspace> { it.isPinned }.thenByDescending { it.lastOpened })
    }

    @Synchronized
    fun save(workspace: Workspace) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == workspace.id || it.rootUri == workspace.rootUri }
        if (index >= 0) {
            current[index] = workspace
        } else {
            current.add(0, workspace)
        }
        persistList(current)
    }

    @Synchronized
    fun getById(id: String): Workspace? {
        return getAll().firstOrNull { it.id == id }
    }

    @Synchronized
    fun getByUri(uri: String): Workspace? {
        return getAll().firstOrNull { it.rootUri == uri }
    }

    @Synchronized
    fun togglePin(id: String): Workspace? {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = current[index].copy(isPinned = !current[index].isPinned)
            current[index] = updated
            persistList(current)
            return updated
        }
        return null
    }

    @Synchronized
    fun remove(id: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == id }
        persistList(current)
    }

    @Synchronized
    fun updateLastOpened(id: String, timeMs: Long = System.currentTimeMillis()) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(lastOpened = timeMs)
            persistList(current)
        }
    }

    @Synchronized
    fun updatePermissionState(id: String, state: Workspace.PermissionState) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(permissionState = state)
            persistList(current)
        }
    }

    private fun persistList(list: List<Workspace>) {
        val array = JSONArray()
        for (w in list) {
            array.put(w.toJson())
        }
        prefs.edit().putString(KEY_WORKSPACES, array.toString()).apply()
    }
}
