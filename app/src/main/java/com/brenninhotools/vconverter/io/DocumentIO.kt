package com.brenninhotools.vconverter.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

data class OutputFile(val directories: List<String>, val name: String, val content: String) {
    val relativePath: String get() = (directories + name).joinToString("/")
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
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Could not access the mod folder.")
        if (!root.canWrite()) throw IOException("The mod folder is read-only. Pick it again to grant access.")
        return files.map { file ->
            val folder = file.directories.fold(root) { parent, segment -> directory(parent, segment) }
            folder.findFile(file.name)?.delete()
            val document = folder.createFile("application/octet-stream", file.name)
                ?: throw IOException("Could not create ${file.relativePath}.")
            context.contentResolver.openOutputStream(document.uri, "wt")?.use { stream ->
                stream.write(file.content.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not write ${file.relativePath}.")
            file.relativePath
        }
    }

    private fun directory(parent: DocumentFile, name: String): DocumentFile {
        val existing = parent.findFile(name)
        return when {
            existing == null -> parent.createDirectory(name) ?: throw IOException("Could not create the \"$name\" folder.")
            existing.isDirectory -> existing
            else -> throw IOException("\"$name\" exists but is not a folder.")
        }
    }
}
