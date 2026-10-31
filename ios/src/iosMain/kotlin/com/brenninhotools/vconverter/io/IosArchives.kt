package com.brenninhotools.vconverter.io

import com.brenninhotools.vconverter.mod.ArchiveKind
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.ZipExtractor
import com.brenninhotools.vconverter.platform.File
import com.brenninhotools.vconverter.platform.IOException
import com.brenninhotools.vconverter.platform.Platform

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
        val session = File(cache, Platform.randomId())
        val target = File(session, "files")
        try {
            session.mkdirs()
            when (kind) {
                ArchiveKind.ZIP -> ZipExtractor.extract(source, target, onProgress)
                ArchiveKind.RAR, ArchiveKind.SEVEN_ZIP -> throw IOException("${kind.label} archives can't be opened on iOS. Extract $name first, or use a .zip.")
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
