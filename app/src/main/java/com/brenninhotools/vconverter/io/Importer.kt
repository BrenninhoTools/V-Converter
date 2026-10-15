package com.brenninhotools.vconverter.io

import android.content.Context
import android.net.Uri
import com.brenninhotools.vconverter.mod.ArchiveKind
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.ZipExtractor
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class ImportedFile(
    val name: String,
    val path: String,
    val fromArchive: Boolean,
    private val opener: () -> InputStream
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    fun open(): InputStream = opener()

    fun readText(): String = open().use { it.readBytes().toString(Charsets.UTF_8) }

    fun isUnder(folder: String): Boolean {
        val segments = path.lowercase().split('/')
        return segments.dropLast(1).contains(folder.lowercase())
    }
}

class ExtractedArchive(val name: String, val kind: ArchiveKind, val directory: File) {
    val files: DirectoryModFiles by lazy { DirectoryModFiles(directory) }

    fun delete() {
        directory.parentFile?.deleteRecursively()
    }
}

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
        val session = File(File(context.cacheDir, CACHE_FOLDER), UUID.randomUUID().toString())
        session.mkdirs()
        val copy = File(session, "source.bin")
        val target = File(session, "files")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it, 64 * 1024) }
            } ?: throw IOException("Could not open $name.")
            when (kind) {
                ArchiveKind.ZIP -> ZipExtractor.extract(copy, target, onProgress)
                ArchiveKind.RAR, ArchiveKind.SEVEN_ZIP -> SevenZipExtractor.extract(copy, target, onProgress)
            }
        } catch (e: Exception) {
            session.deleteRecursively()
            throw if (e is IOException) e else IOException(e.message ?: "Could not extract $name.", e)
        } finally {
            copy.delete()
        }
        return ExtractedArchive(name, kind, target)
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
