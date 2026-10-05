package pro.sketchware.chat.workspace

/**
 * Regras de exclusão de pastas e arquivos para indexação, busca e contexto da IA.
 */
object WorkspaceIgnoreRules {

    private val DEFAULT_IGNORED_DIRS = setOf(
        ".git",
        ".gradle",
        ".idea",
        ".kotlin",
        "build",
        "dist",
        "node_modules",
        "target",
        "out",
        "bin",
        "obj",
        "coverage",
        ".next",
        ".cache",
        ".axion_cache",
        ".turbo",
        ".dart_tool",
        "venv",
        ".venv",
        "__pycache__"
    )

    private val DEFAULT_IGNORED_EXTENSIONS = setOf(
        "class",
        "dex",
        "apk",
        "aab",
        "jar",
        "aar",
        "so",
        "dll",
        "exe",
        "bin",
        "pyc",
        "zip",
        "tar",
        "gz",
        "png",
        "jpg",
        "jpeg",
        "webp",
        "gif",
        "mp3",
        "mp4",
        "wav",
        "flac",
        "pdf"
    )

    @JvmStatic
    fun isDefaultIgnored(name: String): Boolean {
        if (DEFAULT_IGNORED_DIRS.contains(name)) return true
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx > 0 && dotIdx < name.length - 1) {
            val ext = name.substring(dotIdx + 1).lowercase()
            if (DEFAULT_IGNORED_EXTENSIONS.contains(ext)) return true
        }
        return false
    }

    @JvmStatic
    fun isIgnoredDirectory(name: String): Boolean {
        return DEFAULT_IGNORED_DIRS.contains(name)
    }

    @JvmStatic
    fun isBinaryFile(name: String): Boolean {
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx > 0 && dotIdx < name.length - 1) {
            val ext = name.substring(dotIdx + 1).lowercase()
            return DEFAULT_IGNORED_EXTENSIONS.contains(ext)
        }
        return false
    }
}
