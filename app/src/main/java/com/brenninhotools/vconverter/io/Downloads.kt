package com.brenninhotools.vconverter.io

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.OutputStream

data class DownloadedFile(val uri: Uri, val name: String, val location: String, val entries: Int)

object Downloads {
    const val FOLDER = "V-Converter"
    const val MIME = "application/zip"

    val needsPicker: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun write(context: Context, fileName: String, picked: Uri?, block: (OutputStream) -> Int): DownloadedFile {
        if (picked != null) {
            val entries = open(context, picked).use(block)
            return DownloadedFile(picked, DocumentIO.displayName(context, picked), "the folder you picked", entries)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IllegalStateException("Pick where to save the file.")
        return writeToDownloads(context, fileName, block)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDownloads(context: Context, fileName: String, block: (OutputStream) -> Int): DownloadedFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create $fileName in Downloads.")
        try {
            val entries = open(context, uri).use(block)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            val name = DocumentIO.displayName(context, uri).ifBlank { fileName }
            return DownloadedFile(uri, name, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER", entries)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun open(context: Context, uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the file for writing.")

    fun share(context: Context, file: DownloadedFile) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openDownloads(context: Context) {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
