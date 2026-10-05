package pro.sketchware.chat.workspace

import org.json.JSONObject

/**
 * Modelo que representa uma pasta autorizada como workspace no Axion.
 */
data class Workspace(
    val id: String,
    val name: String,
    val rootUri: String,
    val displayPath: String = "",
    val isPinned: Boolean = false,
    val permissionState: PermissionState = PermissionState.GRANTED,
    val lastOpened: Long = System.currentTimeMillis(),
    val detectedTechnology: String = ""
) {
    enum class PermissionState {
        GRANTED,
        REVOKED,
        UNKNOWN
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("rootUri", rootUri)
        json.put("displayPath", displayPath)
        json.put("isPinned", isPinned)
        json.put("permissionState", permissionState.name)
        json.put("lastOpened", lastOpened)
        json.put("detectedTechnology", detectedTechnology)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): Workspace {
            val stateName = json.optString("permissionState", PermissionState.GRANTED.name)
            val state = try {
                PermissionState.valueOf(stateName)
            } catch (_: Exception) {
                PermissionState.UNKNOWN
            }
            return Workspace(
                id = json.optString("id"),
                name = json.optString("name"),
                rootUri = json.optString("rootUri"),
                displayPath = json.optString("displayPath"),
                isPinned = json.optBoolean("isPinned", false),
                permissionState = state,
                lastOpened = json.optLong("lastOpened", System.currentTimeMillis()),
                detectedTechnology = json.optString("detectedTechnology")
            )
        }
    }
}
