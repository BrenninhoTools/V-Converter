package com.vconvertercrew.vconverter.archive

import com.vconvertercrew.vconverter.mod.ModFiles
import com.vconvertercrew.vconverter.mod.SafePaths
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class NativeZipModFiles private constructor(val archive: File, handle: Long) : ModFiles, AutoCloseable {
    private val lock = Any()
    private var open = handle
    private val index = HashMap<String, Int>()
    private val sizes = HashMap<String, Long>()

    override val paths: List<String>

    init {
        val count = ArchiveNative.nativeCount(handle)
        val entrySizes = LongArray(count)
        val flags = IntArray(count)
        val names = ArchiveNative.nativeNames(handle, entrySizes, flags)
        for (i in names.indices) {
            if (flags[i] and 5 != 0) continue
            val safe = SafePaths.normalize(decode(names[i], flags[i] and 2 != 0)) ?: continue
            if (index.containsKey(safe)) continue
            index[safe] = i
            sizes[safe] = entrySizes[i]
        }
        paths = index.keys.sorted()
    }

    val handle: Long get() = synchronized(lock) { if (open == 0L) throw IOException("The archive is closed.") else open }

    fun indexOf(path: String): Int =
        index[path] ?: SafePaths.normalize(path)?.let { index[it] } ?: throw FileNotFoundException(path)

    override fun open(path: String): InputStream = EntryStream(handle, indexOf(path))

    override fun size(path: String): Long = sizes[path] ?: SafePaths.normalize(path)?.let { sizes[it] } ?: throw FileNotFoundException(path)

    fun extract(path: String, target: File) {
        target.parentFile?.mkdirs()
        if (!ArchiveNative.nativeExtract(handle, indexOf(path), ArchiveNative.path(target))) throw IOException("Could not extract $path.")
    }

    override fun close() {
        val closing = synchronized(lock) { open.also { open = 0L } }
        if (closing != 0L) ArchiveNative.nativeClose(closing)
    }

    private class EntryStream(archive: Long, index: Int) : InputStream() {
        private var stream = ArchiveNative.nativeStreamOpen(archive, index)
        private val single = ByteArray(1)

        override fun read(): Int = if (read(single, 0, 1) <= 0) -1 else single[0].toInt() and 0xFF

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (stream == 0L) throw IOException("The entry is closed.")
            if (length == 0) return 0
            return ArchiveNative.nativeStreamRead(stream, buffer, offset, length)
        }

        override fun close() {
            if (stream != 0L) {
                ArchiveNative.nativeStreamClose(stream)
                stream = 0L
            }
        }
    }

    companion object {
        private val legacy: Charset = runCatching { Charset.forName("IBM437") }.getOrDefault(Charsets.ISO_8859_1)

        fun decode(bytes: ByteArray, utf8: Boolean): String {
            if (utf8) return String(bytes, Charsets.UTF_8)
            return runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            }.getOrElse { String(bytes, legacy) }
        }

        fun open(file: File): NativeZipModFiles? {
            if (!ArchiveNative.available) return null
            val handle = runCatching { ArchiveNative.nativeOpen(ArchiveNative.path(file)) }.getOrNull() ?: return null
            return runCatching { NativeZipModFiles(file, handle) }.getOrElse {
                ArchiveNative.nativeClose(handle)
                null
            }
        }
    }
}
