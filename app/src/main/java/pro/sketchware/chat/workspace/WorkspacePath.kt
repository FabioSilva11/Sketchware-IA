package pro.sketchware.chat.workspace

import java.io.File

/**
 * Utilitário para validação, normalização e segurança de caminhos dentro de um Workspace.
 */
object WorkspacePath {

    /**
     * Normaliza e valida um caminho relativo.
     * Lança [SecurityException] ou [IllegalArgumentException] se o caminho tentar escapar do workspace.
     */
    @JvmStatic
    fun normalize(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) {
            return ""
        }
        var path = rawPath.trim().replace('\\', '/')

        // Remove leading / ou ./
        while (path.startsWith("/")) {
            path = path.substring(1)
        }
        while (path.startsWith("./")) {
            path = path.substring(2)
        }
        if (path == "." || path.isEmpty()) {
            return ""
        }

        // Verifica traversal explicitamente
        val segments = path.split("/").filter { it.isNotEmpty() }
        val resolved = mutableListOf<String>()

        for (segment in segments) {
            if (segment == ".") {
                continue
            }
            if (segment == "..") {
                if (resolved.isEmpty()) {
                    throw SecurityException("Path traversal attempt blocked: $rawPath")
                }
                resolved.removeAt(resolved.size - 1)
            } else {
                resolved.add(segment)
            }
        }

        return resolved.joinToString("/")
    }

    /**
     * Verifica se o caminho é seguro e não contém tentativas de escapar do workspace.
     */
    @JvmStatic
    fun isSafe(path: String?): Boolean {
        if (path == null) return true
        return try {
            normalize(path)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Retorna a lista de segmentos de pasta e nome do arquivo a partir de um caminho relativo normalizado.
     */
    @JvmStatic
    fun splitSegments(normalizedPath: String): List<String> {
        if (normalizedPath.isBlank()) return emptyList()
        return normalizedPath.split("/").filter { it.isNotEmpty() }
    }

    /**
     * Obtém o nome base do arquivo a partir do caminho.
     */
    @JvmStatic
    fun getFilename(path: String): String {
        val normalized = normalize(path)
        val idx = normalized.lastIndexOf('/')
        return if (idx >= 0) normalized.substring(idx + 1) else normalized
    }

    /**
     * Verifica se o caminho contém traversal pai (../).
     */
    @JvmStatic
    fun hasParentTraversal(path: String?): Boolean {
        if (path == null) return false
        val normalized = path.trim().replace('\\', '/')
        return normalized.contains("../") || normalized.endsWith("/..") || normalized == ".."
    }

    /**
     * Obtém o diretório pai relativo.
     */
    @JvmStatic
    fun getParentPath(path: String): String {
        val normalized = normalize(path)
        val idx = normalized.lastIndexOf('/')
        return if (idx >= 0) normalized.substring(0, idx) else ""
    }
}
