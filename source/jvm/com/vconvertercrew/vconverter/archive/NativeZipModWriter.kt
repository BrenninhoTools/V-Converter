package com.vconvertercrew.vconverter.archive

import com.vconvertercrew.vconverter.mod.DirectoryModFiles
import com.vconvertercrew.vconverter.mod.ModFiles
import com.vconvertercrew.vconverter.mod.ModWriter
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class NativeZipModWriter private constructor(
    private val target: File,
    prefix: String,
    private val output: OutputStream?
) : ModWriter, Closeable {
    private var handle = ArchiveNative.nativeWriterOpen(ArchiveNative.path(target))
    private val prefix = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    private val written = HashSet<String>()

    val entries: Int get() = synchronized(this) { written.size }

    override fun writeText(path: String, text: String) {
        entry(path) { name -> addBytes(name, text.toByteArray(Charsets.UTF_8)) }
    }

    override fun copy(path: String, source: () -> InputStream) {
        entry(path) { name -> addBytes(name, source().use { it.readBytes() }) }
    }

    override fun copyFrom(path: String, files: ModFiles, source: String) {
        entry(path) { name ->
            when (files) {
                is NativeZipModFiles -> check(ArchiveNative.nativeWriterCopy(handle, ArchiveNative.name(name), files.handle, files.indexOf(source)))
                is DirectoryModFiles -> check(ArchiveNative.nativeWriterAddFile(handle, ArchiveNative.name(name), ArchiveNative.path(files.file(source)), level(name)))
                else -> addBytes(name, files.open(source).use { it.readBytes() })
            }
        }
    }

    private fun addBytes(name: String, data: ByteArray) {
        check(ArchiveNative.nativeWriterAddBytes(handle, ArchiveNative.name(name), data, data.size, level(name)))
    }

    private fun check(ok: Boolean) {
        if (!ok) throw IOException(ArchiveNative.nativeWriterError(handle).ifBlank { "Could not write the ZIP entry." })
    }

    private inline fun entry(path: String, body: (String) -> Unit) {
        val name = prefix + path.replace('\\', '/').trimStart('/')
        synchronized(this) {
            if (handle == 0L) throw IOException("The ZIP is already closed.")
            if (!written.add(name.lowercase())) return
            body(name)
        }
    }

    override fun close() {
        val closing = synchronized(this) { handle.also { handle = 0L } }
        if (closing == 0L) return
        try {
            if (!ArchiveNative.nativeWriterClose(closing)) throw IOException("Could not finish the ZIP.")
            output?.let { stream -> target.inputStream().use { it.copyTo(stream, 256 * 1024) } }
        } finally {
            if (output != null) target.delete()
        }
    }

    companion object {
        private val packed = setOf("png", "jpg", "jpeg", "gif", "webp", "ogg", "mp3", "m4a", "flac", "mp4", "webm", "zip", "ttf", "otf")

        fun level(name: String): Int = if (name.substringAfterLast('.', "").lowercase() in packed) 0 else 6

        fun create(file: File, prefix: String = ""): NativeZipModWriter? {
            if (!ArchiveNative.available) return null
            file.parentFile?.mkdirs()
            return runCatching { NativeZipModWriter(file, prefix, null) }.getOrNull()
        }

        fun forStream(output: OutputStream, prefix: String, cache: File): NativeZipModWriter? {
            if (!ArchiveNative.available) return null
            cache.mkdirs()
            val temp = File(cache, "output-${System.nanoTime()}.zip")
            return runCatching { NativeZipModWriter(temp, prefix, output) }.getOrElse {
                temp.delete()
                null
            }
        }
    }
}
