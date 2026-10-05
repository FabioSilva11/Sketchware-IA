package pro.sketchware.chat.workspace

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Implementação de WorkspaceFileSystem operando diretamente sobre um File/diretório local.
 */
class LocalFolderWorkspaceFileSystem(val rootDir: File) : WorkspaceFileSystem {

    init {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    private fun resolveFile(relativePath: String): File {
        val normalized = WorkspacePath.normalize(relativePath)
        return if (normalized.isEmpty()) rootDir else File(rootDir, normalized)
    }

    override fun readText(relativePath: String): String {
        val file = resolveFile(relativePath)
        if (!file.exists() || file.isDirectory) {
            throw NoSuchFileException(file, reason = "File does not exist or is a directory: $relativePath")
        }
        return file.readText(StandardCharsets.UTF_8)
    }

    override fun readBytes(relativePath: String): ByteArray {
        val file = resolveFile(relativePath)
        if (!file.exists() || file.isDirectory) {
            throw NoSuchFileException(file, reason = "File does not exist or is a directory: $relativePath")
        }
        return file.readBytes()
    }

    override fun writeText(relativePath: String, content: String) {
        val file = resolveFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)
    }

    override fun writeBytes(relativePath: String, data: ByteArray) {
        val file = resolveFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    override fun createFile(relativePath: String): Boolean {
        val file = resolveFile(relativePath)
        if (file.exists()) return true
        file.parentFile?.mkdirs()
        return file.createNewFile()
    }

    override fun createDirectory(relativePath: String): Boolean {
        val file = resolveFile(relativePath)
        return file.mkdirs() || file.isDirectory
    }

    override fun delete(relativePath: String): Boolean {
        val file = resolveFile(relativePath)
        if (!file.exists()) return false
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    override fun rename(relativePath: String, newName: String): Boolean {
        val file = resolveFile(relativePath)
        if (!file.exists()) return false
        val parent = file.parentFile ?: rootDir
        val target = File(parent, newName)
        return file.renameTo(target)
    }

    override fun move(sourceRelativePath: String, destinationRelativePath: String): Boolean {
        val src = resolveFile(sourceRelativePath)
        val dst = resolveFile(destinationRelativePath)
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        return src.renameTo(dst)
    }

    override fun copy(sourceRelativePath: String, destinationRelativePath: String): Boolean {
        val src = resolveFile(sourceRelativePath)
        val dst = resolveFile(destinationRelativePath)
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        return try {
            if (src.isDirectory) {
                src.copyRecursively(dst, overwrite = true)
            } else {
                src.copyTo(dst, overwrite = true)
                dst.exists() && dst.length() == src.length()
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun exists(relativePath: String): Boolean {
        return resolveFile(relativePath).exists()
    }

    override fun isDirectory(relativePath: String): Boolean {
        return resolveFile(relativePath).isDirectory
    }

    override fun list(relativePath: String): List<WorkspaceFileSystem.FileEntry> {
        val dir = resolveFile(relativePath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val normPrefix = WorkspacePath.normalize(relativePath)
        val files = dir.listFiles() ?: return emptyList()

        return files.map { f ->
            val childRel = if (normPrefix.isEmpty()) f.name else "$normPrefix/${f.name}"
            WorkspaceFileSystem.FileEntry(
                name = f.name,
                relativePath = childRel,
                isDirectory = f.isDirectory,
                size = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override fun searchFiles(query: String, includePattern: String, maxResults: Int): List<String> {
        val results = mutableListOf<String>()
        val lowerQuery = query.lowercase()

        fun walk(dir: File, relPrefix: String) {
            if (results.size >= maxResults) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (WorkspaceIgnoreRules.isDefaultIgnored(f.name)) continue
                val rel = if (relPrefix.isEmpty()) f.name else "$relPrefix/${f.name}"
                if (f.name.lowercase().contains(lowerQuery) || rel.lowercase().contains(lowerQuery)) {
                    results.add(rel)
                    if (results.size >= maxResults) return
                }
                if (f.isDirectory) {
                    walk(f, rel)
                }
            }
        }

        walk(rootDir, "")
        return results
    }

    override fun searchText(query: String, maxResults: Int): List<WorkspaceFileSystem.SearchResult> {
        val results = mutableListOf<WorkspaceFileSystem.SearchResult>()
        if (query.isEmpty()) return results

        fun walk(dir: File, relPrefix: String) {
            if (results.size >= maxResults) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (WorkspaceIgnoreRules.isDefaultIgnored(f.name)) continue
                val rel = if (relPrefix.isEmpty()) f.name else "$relPrefix/${f.name}"
                if (f.isDirectory) {
                    walk(f, rel)
                } else if (f.isFile && f.length() <= 2_000_000L) {
                    try {
                        var lineNum = 1
                        f.forEachLine(StandardCharsets.UTF_8) { line ->
                            if (results.size < maxResults && line.contains(query, ignoreCase = true)) {
                                results.add(
                                    WorkspaceFileSystem.SearchResult(
                                        relativePath = rel,
                                        lineNumber = lineNum,
                                        lineContent = line.trim()
                                    )
                                )
                            }
                            lineNum++
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        walk(rootDir, "")
        return results
    }

    override fun getMetadata(relativePath: String): WorkspaceFileSystem.FileMetadata? {
        val file = resolveFile(relativePath)
        if (!file.exists()) return null
        return WorkspaceFileSystem.FileMetadata(
            name = file.name,
            relativePath = WorkspacePath.normalize(relativePath),
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified()
        )
    }

    override fun openInputStream(relativePath: String): InputStream? {
        val file = resolveFile(relativePath)
        return if (file.exists() && file.isFile) FileInputStream(file) else null
    }

    override fun openOutputStream(relativePath: String): OutputStream? {
        val file = resolveFile(relativePath)
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }
}
