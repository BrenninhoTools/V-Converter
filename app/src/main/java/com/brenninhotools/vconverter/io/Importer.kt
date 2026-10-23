package com.brenninhotools.vconverter.io

import android.content.Context
import android.net.Uri
import com.brenninhotools.vconverter.mod.ArchiveKind
import java.io.File
import java.io.IOException
import java.util.UUID

object Importer {
    private const val CACHE_FOLDER = "imports"

    fun clearCache(context: Context) {
        File(context.cacheDir, CACHE_FOLDER).deleteRecursively()
    }

    fun archiveKind(context: Context, uri: Uri, name: String): ArchiveKind? {
        val header = ByteArray(8)
        val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
        val detected = if (read > 0) ArchiveKind.detect(header) else null
        if (detected == null && ArchiveKind.fromName(name) != null) {
            throw IOException("$name is not a valid ZIP, RAR or 7z archive.")
        }
        return detected
    }

    fun extract(
        context: Context,
        uri: Uri,
        name: String,
        kind: ArchiveKind,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ExtractedArchive {
        val cache = File(context.cacheDir, CACHE_FOLDER)
        cache.mkdirs()
        val copy = File(cache, "${UUID.randomUUID()}.bin")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it, 64 * 1024) }
            } ?: throw IOException("Could not open $name.")
            return Archives.extract(copy, name, kind, cache, onProgress)
        } finally {
            copy.delete()
        }
    }

    fun importFiles(context: Context, uris: List<Uri>): Pair<List<ImportedFile>, List<ExtractedArchive>> {
        val files = mutableListOf<ImportedFile>()
        val archives = mutableListOf<ExtractedArchive>()
        uris.forEach { uri ->
            val name = DocumentIO.displayName(context, uri)
            val kind = archiveKind(context, uri, name)
            if (kind == null) {
                files += ImportedFile(name, name, false) {
                    context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open $name.")
                }
            } else {
                val archive = extract(context, uri, name, kind)
                archives += archive
                val root = archive.directory
                archive.files.paths.forEach { path ->
                    files += ImportedFile(path.substringAfterLast('/'), path, true) { File(root, path).inputStream() }
                }
            }
        }
        return files to archives
    }
}
