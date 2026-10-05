package pro.sketchware.chat.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

object WorkspacePermissionManager {

    /**
     * Tenta persistir a permissão de leitura/escrita da URI SAF retornada pelo sistema.
     */
    @JvmStatic
    fun persistUriPermission(context: Context, uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifica se o app ainda detém a permissão persistida para a URI informada.
     */
    @JvmStatic
    fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
        return try {
            val permissions = context.contentResolver.persistedUriPermissions
            val hasGrant = permissions.any { it.uri == uri && (it.isReadPermission || it.isWritePermission) }
            if (hasGrant) {
                // Tenta validar acesso real
                val doc = DocumentFile.fromTreeUri(context, uri)
                doc != null && doc.canRead()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extrai um nome de exibição amigável a partir da Uri do Storage Access Framework.
     */
    @JvmStatic
    fun extractDisplayName(context: Context, uri: Uri): String {
        return try {
            val doc = DocumentFile.fromTreeUri(context, uri)
            val name = doc?.name
            if (!name.isNullOrBlank()) {
                name
            } else {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size > 1 && parts[1].isNotBlank()) {
                    val subParts = parts[1].split("/")
                    subParts.last { it.isNotBlank() }
                } else {
                    parts[0]
                }
            }
        } catch (_: Exception) {
            "Workspace"
        }
    }
}
