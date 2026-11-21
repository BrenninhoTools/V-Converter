package com.vconvertercrew.vconverter.io

import com.vconvertercrew.vconverter.archive.ArchiveNative
import com.vconvertercrew.vconverter.archive.NativeZipModFiles
import com.vconvertercrew.vconverter.mod.ArchiveKind
import com.vconvertercrew.vconverter.mod.ModFiles
import com.vconvertercrew.vconverter.mod.DirectoryModFiles
import com.vconvertercrew.vconverter.mod.ZipExtractor
import java.io.File
import java.io.IOException
import java.util.UUID

class ExtractedArchive(val name: String, val kind: ArchiveKind, val directory: File, private val opened: ModFiles? = null) {
    val files: ModFiles by lazy { opened ?: DirectoryModFiles(directory) }

    val direct: Boolean get() = opened != null

    fun delete() {
        (opened as? AutoCloseable)?.close()
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

    fun open(
        source: File,
        name: String,
        kind: ArchiveKind,
        cache: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ExtractedArchive {
        if (kind == ArchiveKind.ZIP) {
            val session = File(cache, UUID.randomUUID().toString())
            session.mkdirs()
            val files = NativeZipModFiles.open(source)
            if (files != null) return ExtractedArchive(name, kind, File(session, "files"), files)
            session.deleteRecursively()
        }
        return extract(source, name, kind, cache, onProgress)
    }

    fun openCopy(
        cache: File,
        name: String,
        kind: ArchiveKind,
        copy: (File) -> Unit,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ExtractedArchive {
        if (kind == ArchiveKind.ZIP && ArchiveNative.available) {
            val session = File(cache, UUID.randomUUID().toString())
            session.mkdirs()
            val source = File(session, "source.zip")
            try {
                copy(source)
                val files = NativeZipModFiles.open(source)
                if (files != null) return ExtractedArchive(name, kind, File(session, "files"), files)
                return extract(source, name, kind, cache, onProgress).also { session.deleteRecursively() }
            } catch (e: Exception) {
                session.deleteRecursively()
                throw e
            }
        }
        val temp = File(cache, "${UUID.randomUUID()}.bin")
        try {
            copy(temp)
            return extract(temp, name, kind, cache, onProgress)
        } finally {
            temp.delete()
        }
    }

    fun kind(file: File): ArchiveKind? {
        val header = ByteArray(8)
        val read = file.inputStream().use { it.read(header) }
        val detected = if (read > 0) ArchiveKind.detect(header) else null
        if (detected == null && ArchiveKind.fromName(file.name) != null) throw IOException("${file.name} is not a valid ZIP, RAR or 7z archive.")
        return detected
    }
}
