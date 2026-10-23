package com.brenninhotools.vconverter.io

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

object Archives {
    fun extract(
        source: File,
        name: String,
        kind: ArchiveKind,
        cache: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ExtractedArchive {
        val session = File(cache, UUID.randomUUID().toString())
        val target = File(session, "files")
        try {
            session.mkdirs()
            when (kind) {
                ArchiveKind.ZIP -> ZipExtractor.extract(source, target, onProgress)
                ArchiveKind.RAR, ArchiveKind.SEVEN_ZIP -> SevenZipExtractor.extract(source, target, onProgress)
            }
        } catch (e: Exception) {
            session.deleteRecursively()
            throw if (e is IOException) e else IOException(e.message ?: "Could not extract $name.", e)
        }
        return ExtractedArchive(name, kind, target)
    }

    fun kind(file: File): ArchiveKind? {
        val header = ByteArray(8)
        val read = file.inputStream().use { it.read(header) }
        val detected = if (read > 0) ArchiveKind.detect(header) else null
        if (detected == null && ArchiveKind.fromName(file.name) != null) throw IOException("${file.name} is not a valid ZIP, RAR or 7z archive.")
        return detected
    }
}
