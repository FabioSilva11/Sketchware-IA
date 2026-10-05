package pro.sketchware.chat.workspace

import java.io.InputStream
import java.io.OutputStream

/**
 * Abstração central de sistema de arquivos do Workspace.
 * Toda a camada de IA, editor e ferramentas trabalha com caminhos RELATIVOS à raiz do workspace.
 */
interface WorkspaceFileSystem {

    data class FileEntry(
        val name: String,
        val relativePath: String,
        val isDirectory: Boolean,
        val size: Long = 0L,
        val lastModified: Long = 0L
    )

    data class FileMetadata(
        val name: String,
        val relativePath: String,
        val isDirectory: Boolean,
        val size: Long = 0L,
        val lastModified: Long = 0L,
        val mimeType: String? = null
    )

    data class SearchResult(
        val relativePath: String,
        val lineNumber: Int,
        val lineContent: String
    )

    fun readText(relativePath: String): String
    fun readBytes(relativePath: String): ByteArray
    fun writeText(relativePath: String, content: String)
    fun writeBytes(relativePath: String, data: ByteArray)

    fun createFile(relativePath: String): Boolean
    fun createDirectory(relativePath: String): Boolean

    fun delete(relativePath: String): Boolean
    fun rename(relativePath: String, newName: String): Boolean
    fun move(sourceRelativePath: String, destinationRelativePath: String): Boolean
    fun copy(sourceRelativePath: String, destinationRelativePath: String): Boolean

    fun exists(relativePath: String): Boolean
    fun isDirectory(relativePath: String): Boolean

    fun list(relativePath: String = ""): List<FileEntry>
    fun searchFiles(query: String, includePattern: String = "", maxResults: Int = 100): List<String>
    fun searchText(query: String, maxResults: Int = 100): List<SearchResult>
    fun getMetadata(relativePath: String): FileMetadata?

    fun openInputStream(relativePath: String): InputStream?
    fun openOutputStream(relativePath: String): OutputStream?
}
