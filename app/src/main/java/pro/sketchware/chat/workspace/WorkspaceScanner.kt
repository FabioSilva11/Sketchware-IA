package pro.sketchware.chat.workspace

/**
 * Utilitário de indexação e sumarização estrutural de workspaces para o contexto do Agente.
 */
object WorkspaceScanner {

    private const val MAX_TREE_DEPTH = 4
    private const val MAX_TOTAL_ENTRIES = 120

    @JvmStatic
    fun generateStructureOverview(fs: WorkspaceFileSystem, maxEntries: Int = MAX_TOTAL_ENTRIES): String {
        val sb = StringBuilder()
        var count = 0

        fun walk(relPath: String, depth: Int, prefix: String) {
            if (depth > MAX_TREE_DEPTH || count >= maxEntries) return
            val entries = fs.list(relPath)
            for (entry in entries) {
                if (WorkspaceIgnoreRules.isDefaultIgnored(entry.name)) continue
                count++
                if (count > maxEntries) {
                    sb.append(prefix).append("... (arquivos restantes omitidos)\n")
                    return
                }
                if (entry.isDirectory) {
                    sb.append(prefix).append("📁 ").append(entry.name).append("/\n")
                    walk(entry.relativePath, depth + 1, "$prefix  ")
                } else {
                    sb.append(prefix).append("📄 ").append(entry.name).append("\n")
                }
            }
        }

        walk("", 1, "")
        return if (sb.isEmpty()) "Empty workspace" else sb.toString()
    }
}
