package com.vconvertercrew.vconverter.archive

import com.vconvertercrew.vconverter.NativeLibraries
import java.io.File

object ArchiveNative {
    private const val LIBRARY = "vconverter_archive"

    var enabled: Boolean = true

    var loadError: String? = null
        private set

    private val loaded: Boolean by lazy {
        val result = runCatching {
            NativeLibraries.load(LIBRARY, "vconverter.archive.library")
            nativeVersion()
        }
        loadError = result.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
        result.isSuccess
    }

    val available: Boolean get() = enabled && loaded

    fun path(file: File): ByteArray = file.absolutePath.toByteArray(Charsets.UTF_8)

    fun name(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    @JvmStatic external fun nativeVersion(): String
    @JvmStatic external fun nativeOpen(path: ByteArray): Long
    @JvmStatic external fun nativeClose(archive: Long)
    @JvmStatic external fun nativeCount(archive: Long): Int
    @JvmStatic external fun nativeNames(archive: Long, sizes: LongArray, flags: IntArray): Array<ByteArray>
    @JvmStatic external fun nativeRead(archive: Long, index: Int): ByteArray
    @JvmStatic external fun nativeExtract(archive: Long, index: Int, path: ByteArray): Boolean
    @JvmStatic external fun nativeStreamOpen(archive: Long, index: Int): Long
    @JvmStatic external fun nativeStreamRead(stream: Long, buffer: ByteArray, offset: Int, length: Int): Int
    @JvmStatic external fun nativeStreamClose(stream: Long)
    @JvmStatic external fun nativeWriterOpen(path: ByteArray): Long
    @JvmStatic external fun nativeWriterAddBytes(archive: Long, name: ByteArray, data: ByteArray, length: Int, level: Int): Boolean
    @JvmStatic external fun nativeWriterAddFile(archive: Long, name: ByteArray, path: ByteArray, level: Int): Boolean
    @JvmStatic external fun nativeWriterCopy(archive: Long, name: ByteArray, source: Long, index: Int): Boolean
    @JvmStatic external fun nativeWriterError(archive: Long): String
    @JvmStatic external fun nativeWriterClose(archive: Long): Boolean
}
