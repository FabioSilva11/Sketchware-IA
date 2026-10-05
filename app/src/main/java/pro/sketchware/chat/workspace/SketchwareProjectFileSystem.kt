package pro.sketchware.chat.workspace

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A native Sketchware project as one workspace.
 *
 * Paths are relative to the `.sketchware` folder, as Sketchware stores them: `data/<id>/...` (logic, view, file,
 * library, resource and the project's custom files), `mysc/<id>/...` (the generated Android project),
 * `mysc/list/<id>/project` (name, package, version...) and `resources/{images,sounds,fonts,icons}/<id>/...`.
 * Nothing outside these folders exists for the agent, so another project's files can't be read or changed.
 *
 * The extensionless project files Sketchware keeps AES-encrypted are decrypted on read and encrypted again on write,
 * so reading, searching and editing work on their real content.
 */
class SketchwareProjectFileSystem(
    val scId: String,
    val rootDir: File
) : WorkspaceFileSystem {

    private val dataDir = "data/$scId"
    private val myscDir = "mysc/$scId"
    private val listDir = "mysc/list/$scId"
    private val resourceDirs = RESOURCE_TYPES.map { "resources/$it/$scId" }

    /** Real folders the agent may read. */
    private val readableRoots = listOf(dataDir, myscDir, listDir) + resourceDirs

    /** Folders above the real ones, listed with only this project's entry. */
    private fun virtualChildren(path: String): List<String>? = when (path) {
        "" -> listOf("data", "mysc", "resources")
        "data" -> listOf(scId)
        "mysc" -> listOf(scId, "list")
        "mysc/list" -> listOf(scId)
        "resources" -> RESOURCE_TYPES
        else -> RESOURCE_TYPES.firstOrNull { path == "resources/$it" }?.let { listOf(scId) }
    }

    private fun isUnder(path: String, root: String) = path == root || path.startsWith("$root/")

    private fun isReadable(path: String) = virtualChildren(path) != null || readableRoots.any { isUnder(path, it) }

    /** Project data and metadata can change; the generated project only in its source/build folders. */
    private fun isWritable(path: String): Boolean {
        if (path == dataDir || path == listDir || path == myscDir) return false
        return path.startsWith("$dataDir/") ||
                path == "$listDir/project" ||
                path.startsWith("$myscDir/app/") || path.startsWith("$myscDir/bin/") || path.startsWith("$myscDir/gen/")
    }

    /** Files whose loss would break the project in Sketchware; editing them is fine, deleting isn't. */
    private fun isProtected(path: String) = path == "$listDir/project" ||
            NATIVE_FILES.any { path == "$dataDir/$it" }

    /** Whether a path (relative to `.sketchware`, or absolute inside it) belongs to this project. */
    fun canRead(relativePath: String): Boolean = try {
        isReadable(normalize(relativePath))
    } catch (_: Exception) {
        false
    }

    fun canWrite(relativePath: String): Boolean = try {
        isWritable(normalize(relativePath))
    } catch (_: Exception) {
        false
    }

    /** The project's real folders that exist, for callers that walk java.io.File trees. */
    fun readableRootFiles(): List<File> = readableRoots.map { File(rootDir, it) }.filter { it.isDirectory }

    private fun normalize(relativePath: String): String {
        var path = WorkspacePath.normalize(relativePath)
        // Models often repeat the root: ".sketchware/data/601/logic" or "/storage/emulated/0/.sketchware/data/601/logic"
        val marker = path.indexOf(".sketchware/")
        if (marker >= 0) path = path.substring(marker + ".sketchware/".length)
        if (path == ".sketchware") path = ""
        // "mysc/601/601/app/..." happens when a model joins the id twice
        if (path.startsWith("$myscDir/$scId/")) path = "$myscDir/" + path.substring("$myscDir/$scId/".length)
        if (path == "project" || path == "project.json") path = "$listDir/project"
        return path
    }

    private fun readableFile(relativePath: String): Pair<String, File> {
        val path = normalize(relativePath)
        if (!isReadable(path)) throw SecurityException(outOfProject(relativePath))
        return path to (if (path.isEmpty()) rootDir else File(rootDir, path))
    }

    private fun writableFile(relativePath: String): Pair<String, File> {
        val path = normalize(relativePath)
        if (!isWritable(path)) {
            throw SecurityException(
                if (isReadable(path)) "Read-only in this Sketchware project: $relativePath" else outOfProject(relativePath)
            )
        }
        return path to File(rootDir, path)
    }

    private fun outOfProject(requested: String) =
        "Outside Sketchware project $scId: $requested (use data/$scId/..., mysc/$scId/..., mysc/list/$scId/project)"

    /** Extensionless files in the project's data and list folders are Sketchware's encrypted project files. */
    private fun isNativeFile(path: String, file: File): Boolean {
        if (file.name.contains('.')) return false
        return path.startsWith("$dataDir/") && path.count { it == '/' } == 2 || path == "$listDir/project"
    }

    override fun readText(relativePath: String): String = String(readBytes(relativePath), StandardCharsets.UTF_8)

    override fun readBytes(relativePath: String): ByteArray {
        val (path, file) = readableFile(relativePath)
        if (!file.isFile) throw NoSuchFileException(file, reason = "File does not exist or is a directory: $relativePath")
        val raw = file.readBytes()
        return if (isNativeFile(path, file)) decryptOrRaw(raw) else raw
    }

    override fun writeText(relativePath: String, content: String) =
        writeBytes(relativePath, content.toByteArray(StandardCharsets.UTF_8))

    override fun writeBytes(relativePath: String, data: ByteArray) {
        val (path, file) = writableFile(relativePath)
        file.parentFile?.mkdirs()
        // Keep each file as Sketchware stores it: encrypted stays encrypted, plain (project_config, proguard...) stays plain
        val encrypt = isNativeFile(path, file) && if (file.exists() && file.length() > 0L) {
            isEncrypted(file.readBytes())
        } else {
            isProtected(path)
        }
        file.writeBytes(if (encrypt) encrypt(data) else data)
    }

    override fun createFile(relativePath: String): Boolean {
        val (_, file) = writableFile(relativePath)
        if (file.exists()) return true
        file.parentFile?.mkdirs()
        return file.createNewFile()
    }

    override fun createDirectory(relativePath: String): Boolean {
        val (_, file) = writableFile(relativePath)
        return file.mkdirs() || file.isDirectory
    }

    override fun delete(relativePath: String): Boolean {
        val (path, file) = writableFile(relativePath)
        if (isProtected(path)) throw SecurityException("Sketchware needs this file, it can't be deleted: $relativePath")
        if (!file.exists()) return false
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    override fun rename(relativePath: String, newName: String): Boolean {
        val (path, _) = writableFile(relativePath)
        val parent = path.substringBeforeLast('/', "")
        return move(path, if (parent.isEmpty()) newName else "$parent/$newName")
    }

    override fun move(sourceRelativePath: String, destinationRelativePath: String): Boolean {
        val (srcPath, src) = writableFile(sourceRelativePath)
        val (_, dst) = writableFile(destinationRelativePath)
        if (isProtected(srcPath)) throw SecurityException("Sketchware needs this file, it can't be moved: $sourceRelativePath")
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        return src.renameTo(dst)
    }

    override fun copy(sourceRelativePath: String, destinationRelativePath: String): Boolean {
        val (_, src) = readableFile(sourceRelativePath)
        val (_, dst) = writableFile(destinationRelativePath)
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        return try {
            if (src.isDirectory) src.copyRecursively(dst, overwrite = true) else src.copyTo(dst, overwrite = true).exists()
        } catch (_: Exception) {
            false
        }
    }

    override fun exists(relativePath: String): Boolean {
        val path = normalize(relativePath)
        if (virtualChildren(path) != null) return true
        return isReadable(path) && File(rootDir, path).exists()
    }

    override fun isDirectory(relativePath: String): Boolean {
        val path = normalize(relativePath)
        if (virtualChildren(path) != null) return true
        return isReadable(path) && File(rootDir, path).isDirectory
    }

    override fun list(relativePath: String): List<WorkspaceFileSystem.FileEntry> {
        val path = normalize(relativePath)
        virtualChildren(path)?.let { children ->
            return children.mapNotNull { name ->
                val child = if (path.isEmpty()) name else "$path/$name"
                // Only show what this project actually has
                if (virtualChildren(child) == null && !File(rootDir, child).exists()) return@mapNotNull null
                if (child == "resources" && resourceDirs.none { File(rootDir, it).exists() }) return@mapNotNull null
                if (child.startsWith("resources/") && virtualChildren(child) != null &&
                    !File(rootDir, "$child/$scId").exists()) return@mapNotNull null
                WorkspaceFileSystem.FileEntry(name = name, relativePath = child, isDirectory = true)
            }
        }
        if (!isReadable(path)) return emptyList()
        val dir = File(rootDir, path)
        val files = dir.listFiles() ?: return emptyList()
        return files.map { f ->
            WorkspaceFileSystem.FileEntry(
                name = f.name,
                relativePath = "$path/${f.name}",
                isDirectory = f.isDirectory,
                size = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** Walks every real folder of the project, skipping build output and other ignored folders. */
    private fun walk(visit: (String, File) -> Boolean) {
        fun recurse(dir: File, rel: String): Boolean {
            val files = dir.listFiles() ?: return true
            for (f in files.sortedBy { it.name }) {
                if (WorkspaceIgnoreRules.isDefaultIgnored(f.name)) continue
                val childRel = "$rel/${f.name}"
                if (f.isDirectory) {
                    if (!recurse(f, childRel)) return false
                } else if (!visit(childRel, f)) {
                    return false
                }
            }
            return true
        }
        for (root in readableRoots) {
            val dir = File(rootDir, root)
            if (dir.isDirectory && !recurse(dir, root)) return
        }
    }

    override fun searchFiles(query: String, includePattern: String, maxResults: Int): List<String> {
        val results = mutableListOf<String>()
        val lowerQuery = query.lowercase()
        walk { rel, f ->
            if (f.name.lowercase().contains(lowerQuery) || rel.lowercase().contains(lowerQuery)) results.add(rel)
            results.size < maxResults
        }
        return results
    }

    override fun searchText(query: String, maxResults: Int): List<WorkspaceFileSystem.SearchResult> {
        val results = mutableListOf<WorkspaceFileSystem.SearchResult>()
        if (query.isEmpty()) return results
        walk { rel, f ->
            if (f.length() <= MAX_SEARCH_BYTES && !isBinaryName(f.name)) {
                val text = try {
                    readText(rel)
                } catch (_: Exception) {
                    null
                }
                text?.lineSequence()?.forEachIndexed { index, line ->
                    if (results.size < maxResults && line.contains(query, ignoreCase = true)) {
                        results.add(WorkspaceFileSystem.SearchResult(rel, index + 1, line.trim().take(MAX_LINE)))
                    }
                }
            }
            results.size < maxResults
        }
        return results
    }

    override fun getMetadata(relativePath: String): WorkspaceFileSystem.FileMetadata? {
        val path = normalize(relativePath)
        if (virtualChildren(path) != null) {
            return WorkspaceFileSystem.FileMetadata(path.substringAfterLast('/'), path, true)
        }
        if (!isReadable(path)) return null
        val file = File(rootDir, path)
        if (!file.exists()) return null
        return WorkspaceFileSystem.FileMetadata(
            name = file.name,
            relativePath = path,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified()
        )
    }

    override fun openInputStream(relativePath: String): InputStream? {
        val (path, file) = readableFile(relativePath)
        if (!file.isFile) return null
        return if (isNativeFile(path, file)) ByteArrayInputStream(readBytes(path)) else FileInputStream(file)
    }

    override fun openOutputStream(relativePath: String): OutputStream? {
        val (path, file) = writableFile(relativePath)
        file.parentFile?.mkdirs()
        if (!isNativeFile(path, file)) return FileOutputStream(file)
        return object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                writeBytes(path, toByteArray())
            }
        }
    }

    companion object {
        /** Sketchware's encrypted project files in data/<id>/. */
        @JvmField
        val NATIVE_FILES = listOf("file", "logic", "view", "library", "resource")
        private val RESOURCE_TYPES = listOf("images", "sounds", "fonts", "icons")
        private const val MAX_SEARCH_BYTES = 4_000_000L
        private const val MAX_LINE = 400
        private val KEY = "sketchwaresecure".toByteArray(StandardCharsets.UTF_8)

        @JvmStatic
        fun forProject(scId: String, sketchwareRoot: File) = SketchwareProjectFileSystem(scId, sketchwareRoot)

        private fun cipher(mode: Int): Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(mode, SecretKeySpec(KEY, "AES"), IvParameterSpec(KEY))
        }

        @JvmStatic
        fun encrypt(plain: ByteArray): ByteArray = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)

        /** Decrypted content, or the bytes themselves when the file isn't encrypted (older or hand-written files). */
        @JvmStatic
        fun decryptOrRaw(raw: ByteArray): ByteArray {
            if (raw.isEmpty() || !isEncrypted(raw)) return raw
            return try {
                cipher(Cipher.DECRYPT_MODE).doFinal(raw)
            } catch (_: Exception) {
                raw
            }
        }

        /** Encrypted files are whole AES blocks and don't start like the text Sketchware stores. */
        @JvmStatic
        fun isEncrypted(raw: ByteArray): Boolean {
            if (raw.isEmpty() || raw.size % 16 != 0) return false
            val head = String(raw, 0, minOf(raw.size, 16), StandardCharsets.ISO_8859_1).trimStart()
            if (head.startsWith("{") || head.startsWith("[") || head.startsWith("@") || head.startsWith("<")) return false
            return try {
                cipher(Cipher.DECRYPT_MODE).doFinal(raw)
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun isBinaryName(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in setOf(
                "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "mp3", "wav", "ogg", "m4a", "mp4", "ttf", "otf",
                "apk", "aab", "dex", "jar", "zip", "so", "class", "keystore", "jks", "flat", "arsc", "aar"
            )
        }
    }
}
