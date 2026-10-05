package pro.sketchware.chat.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Implementação de WorkspaceFileSystem operando via Storage Access Framework (SAF)
 * usando DocumentFile e ContentResolver para pastas selecionadas pelo usuário.
 */
class SafWorkspaceFileSystem(
    private val context: Context,
    val rootTreeUri: Uri
) : WorkspaceFileSystem {

    private val rootDoc: DocumentFile by lazy {
        DocumentFile.fromTreeUri(context, rootTreeUri)
            ?: throw IllegalStateException("Cannot access tree Uri: $rootTreeUri")
    }

    private fun findDocument(relativePath: String, createParentDirsIfMissing: Boolean = false): DocumentFile? {
        val normalized = WorkspacePath.normalize(relativePath)
        if (normalized.isEmpty()) return rootDoc

        val segments = WorkspacePath.splitSegments(normalized)
        var current: DocumentFile = rootDoc

        for (i in segments.indices) {
            val segment = segments[i]
            val isLast = i == segments.size - 1

            var child = current.findFile(segment)
            if (child == null) {
                if (createParentDirsIfMissing && !isLast) {
                    child = current.createDirectory(segment) ?: return null
                } else {
                    return null
                }
            }
            current = child
        }
        return current
    }

    private fun getOrCreateDocument(relativePath: String, isDirectory: Boolean): DocumentFile {
        val normalized = WorkspacePath.normalize(relativePath)
        if (normalized.isEmpty()) return rootDoc

        val segments = WorkspacePath.splitSegments(normalized)
        var current: DocumentFile = rootDoc

        for (i in segments.indices) {
            val segment = segments[i]
            val isLast = i == segments.size - 1

            var child = current.findFile(segment)
            if (child == null) {
                child = if (isLast && !isDirectory) {
                    current.createFile("application/octet-stream", segment)
                } else {
                    current.createDirectory(segment)
                }
                if (child == null) {
                    throw IllegalStateException("Failed to create document segment: $segment at path: $relativePath")
                }
            }
            current = child
        }
        return current
    }

    override fun readText(relativePath: String): String {
        val doc = findDocument(relativePath)
            ?: throw NoSuchFileException(java.io.File(relativePath), reason = "Document not found: $relativePath")
        val stream = context.contentResolver.openInputStream(doc.uri)
            ?: throw NoSuchFileException(java.io.File(relativePath), reason = "Cannot open input stream for: $relativePath")
        return stream.use {
            it.bufferedReader(StandardCharsets.UTF_8).readText()
        }
    }

    override fun readBytes(relativePath: String): ByteArray {
        val doc = findDocument(relativePath)
            ?: throw NoSuchFileException(java.io.File(relativePath), reason = "Document not found: $relativePath")
        val stream = context.contentResolver.openInputStream(doc.uri)
            ?: throw NoSuchFileException(java.io.File(relativePath), reason = "Cannot open input stream for: $relativePath")
        return stream.use { it.readBytes() }
    }

    override fun writeText(relativePath: String, content: String) {
        val doc = getOrCreateDocument(relativePath, isDirectory = false)
        val stream = context.contentResolver.openOutputStream(doc.uri, "wt")
            ?: throw IllegalStateException("Cannot open output stream for: $relativePath")
        stream.use {
            it.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    override fun writeBytes(relativePath: String, data: ByteArray) {
        val doc = getOrCreateDocument(relativePath, isDirectory = false)
        val stream = context.contentResolver.openOutputStream(doc.uri, "wt")
            ?: throw IllegalStateException("Cannot open output stream for: $relativePath")
        stream.use {
            it.write(data)
        }
    }

    override fun createFile(relativePath: String): Boolean {
        return try {
            getOrCreateDocument(relativePath, isDirectory = false)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun createDirectory(relativePath: String): Boolean {
        return try {
            getOrCreateDocument(relativePath, isDirectory = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun delete(relativePath: String): Boolean {
        val doc = findDocument(relativePath) ?: return false
        return doc.delete()
    }

    override fun rename(relativePath: String, newName: String): Boolean {
        val doc = findDocument(relativePath) ?: return false
        return doc.renameTo(newName)
    }

    override fun move(sourceRelativePath: String, destinationRelativePath: String): Boolean {
        val src = findDocument(sourceRelativePath) ?: return false
        if (!src.isFile) return false
        // Copy the bytes first, verify the destination, and only then remove
        // the source: a failed verification must not destroy the original.
        val bytes = try {
            readBytes(sourceRelativePath)
        } catch (_: Exception) {
            return false
        }
        return try {
            writeBytes(destinationRelativePath, bytes)
            val written = findDocument(destinationRelativePath) ?: return false
            val stream = context.contentResolver.openInputStream(written.uri) ?: return false
            val writtenBytes = stream.use { it.readBytes() }
            if (!writtenBytes.contentEquals(bytes)) return false
            src.delete()
        } catch (_: Exception) {
            false
        }
    }

    override fun copy(sourceRelativePath: String, destinationRelativePath: String): Boolean {
        val src = findDocument(sourceRelativePath) ?: return false
        if (!src.isFile) return false
        val bytes = try {
            readBytes(sourceRelativePath)
        } catch (_: Exception) {
            return false
        }
        return try {
            writeBytes(destinationRelativePath, bytes)
            val written = findDocument(destinationRelativePath) ?: return false
            val stream = context.contentResolver.openInputStream(written.uri) ?: return false
            val writtenBytes = stream.use { it.readBytes() }
            writtenBytes.contentEquals(bytes)
        } catch (_: Exception) {
            false
        }
    }

    override fun exists(relativePath: String): Boolean {
        return findDocument(relativePath) != null
    }

    override fun isDirectory(relativePath: String): Boolean {
        val doc = findDocument(relativePath) ?: return false
        return doc.isDirectory
    }

    override fun list(relativePath: String): List<WorkspaceFileSystem.FileEntry> {
        val doc = findDocument(relativePath) ?: return emptyList()
        if (!doc.isDirectory) return emptyList()

        val normPrefix = WorkspacePath.normalize(relativePath)
        val children = doc.listFiles()

        return children.map { child ->
            val name = child.name ?: "unknown"
            val childRel = if (normPrefix.isEmpty()) name else "$normPrefix/$name"
            WorkspaceFileSystem.FileEntry(
                name = name,
                relativePath = childRel,
                isDirectory = child.isDirectory,
                size = child.length(),
                lastModified = child.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override fun searchFiles(query: String, includePattern: String, maxResults: Int): List<String> {
        val results = mutableListOf<String>()
        val lowerQuery = query.lowercase()

        fun walk(currentDoc: DocumentFile, relPrefix: String) {
            if (results.size >= maxResults) return
            val children = currentDoc.listFiles()
            for (child in children) {
                val name = child.name ?: continue
                if (WorkspaceIgnoreRules.isDefaultIgnored(name)) continue
                val rel = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                if (name.lowercase().contains(lowerQuery) || rel.lowercase().contains(lowerQuery)) {
                    results.add(rel)
                    if (results.size >= maxResults) return
                }
                if (child.isDirectory) {
                    walk(child, rel)
                }
            }
        }

        walk(rootDoc, "")
        return results
    }

    override fun searchText(query: String, maxResults: Int): List<WorkspaceFileSystem.SearchResult> {
        val results = mutableListOf<WorkspaceFileSystem.SearchResult>()
        if (query.isEmpty()) return results

        fun walk(currentDoc: DocumentFile, relPrefix: String) {
            if (results.size >= maxResults) return
            val children = currentDoc.listFiles()
            for (child in children) {
                val name = child.name ?: continue
                if (WorkspaceIgnoreRules.isDefaultIgnored(name)) continue
                val rel = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                if (child.isDirectory) {
                    walk(child, rel)
                } else if (child.isFile && child.length() <= 2_000_000L) {
                    try {
                        val stream = context.contentResolver.openInputStream(child.uri)
                        if (stream != null) {
                            var lineNum = 1
                            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).useLines { lines ->
                                for (line in lines) {
                                    if (results.size >= maxResults) break
                                    if (line.contains(query, ignoreCase = true)) {
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
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        walk(rootDoc, "")
        return results
    }

    override fun getMetadata(relativePath: String): WorkspaceFileSystem.FileMetadata? {
        val doc = findDocument(relativePath) ?: return null
        return WorkspaceFileSystem.FileMetadata(
            name = doc.name ?: "",
            relativePath = WorkspacePath.normalize(relativePath),
            isDirectory = doc.isDirectory,
            size = doc.length(),
            lastModified = doc.lastModified(),
            mimeType = doc.type
        )
    }

    override fun openInputStream(relativePath: String): InputStream? {
        val doc = findDocument(relativePath) ?: return null
        return context.contentResolver.openInputStream(doc.uri)
    }

    override fun openOutputStream(relativePath: String): OutputStream? {
        val doc = getOrCreateDocument(relativePath, isDirectory = false)
        return context.contentResolver.openOutputStream(doc.uri, "wt")
    }
}
