package com.brenninhotools.vconverter.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

object DocumentIO {
    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "chart.json"
    }

    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            ?: throw IOException("Could not open ${displayName(context, uri)}")

    fun writeSongFolder(context: Context, treeUri: Uri, songId: String, files: Map<String, String>): String {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Could not access the selected folder.")
        val existing = root.findFile(songId)
        val folder = when {
            existing == null -> root.createDirectory(songId)
            existing.isDirectory -> existing
            else -> throw IOException("A file named \"$songId\" already exists in the selected folder.")
        } ?: throw IOException("Could not create the \"$songId\" folder.")

        files.forEach { (name, content) ->
            folder.findFile(name)?.delete()
            val file = folder.createFile("application/octet-stream", name) ?: throw IOException("Could not create $name.")
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not write $name.")
        }

        return "${root.name ?: "selected folder"}/$songId"
    }
}
