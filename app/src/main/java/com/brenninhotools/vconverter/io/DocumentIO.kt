package com.brenninhotools.vconverter.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import com.brenninhotools.vconverter.mod.ModWriter

class OutputFile(val directories: List<String>, val name: String, val content: String, val bytes: ByteArray? = null) {
    val relativePath: String get() = (directories + name).joinToString("/")

    fun writeTo(writer: ModWriter) {
        val data = bytes
        if (data != null) writer.copy(relativePath) { data.inputStream() } else writer.writeText(relativePath, content)
    }

    companion object {
        fun of(path: String, text: String?, bytes: ByteArray?): OutputFile {
            val clean = path.trim('/')
            return OutputFile(clean.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }, clean.substringAfterLast('/'), text ?: "", bytes)
        }
    }
}

object DocumentIO {
    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open ${displayName(context, uri)}")

    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            ?: throw IOException("Could not open ${displayName(context, uri)}")

    fun folderName(context: Context, treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name ?: "Selected folder"

    fun hasAccess(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission } &&
            DocumentFile.fromTreeUri(context, treeUri)?.canWrite() == true

    fun keepAccess(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
    }

    fun writeFiles(context: Context, treeUri: Uri, files: List<OutputFile>): List<String> {
        if (DocumentFile.fromTreeUri(context, treeUri)?.canWrite() != true) {
            throw IOException("The mod folder is read-only or missing. Pick it again to grant access.")
        }
        val writer = TreeModWriter(context, treeUri)
        return files.map { file ->
            file.writeTo(writer)
            file.relativePath
        }
    }
}
