package com.brenninhotools.vconverter.mod

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipFile

enum class ArchiveKind(val label: String) {
    ZIP("ZIP"),
    RAR("RAR"),
    SEVEN_ZIP("7z");

    companion object {
        private val extensions = mapOf("zip" to ZIP, "rar" to RAR, "7z" to SEVEN_ZIP, "fnfc" to ZIP)

        fun detect(header: ByteArray): ArchiveKind? = when {
            header.startsWith(0x50, 0x4B, 0x03, 0x04) || header.startsWith(0x50, 0x4B, 0x05, 0x06) -> ZIP
            header.startsWith(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> RAR
            header.startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> SEVEN_ZIP
            else -> null
        }

        fun fromName(name: String): ArchiveKind? = extensions[name.substringAfterLast('.', "").lowercase()]

        private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
            size >= bytes.size && bytes.indices.all { (this[it].toInt() and 0xFF) == bytes[it] }
    }
}

object ZipExtractor {
    fun extract(archive: File, destination: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        return try {
            extractWith(archive, destination, Charsets.UTF_8, onProgress)
        } catch (e: IllegalArgumentException) {
            extractWith(archive, destination, Charset.forName("IBM437"), onProgress)
        } catch (e: ZipException) {
            if (e.message?.contains("MALFORMED", ignoreCase = true) == true) {
                extractWith(archive, destination, Charset.forName("IBM437"), onProgress)
            } else {
                throw IOException("The ZIP file is damaged or not supported: ${e.message}", e)
            }
        }
    }

    private fun extractWith(archive: File, destination: File, charset: Charset, onProgress: (Int, Int) -> Unit): Int {
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        var written = 0
        ZipFile(archive, charset).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory }
            entries.forEachIndexed { index, entry ->
                val safe = SafePaths.normalize(entry.name) ?: return@forEachIndexed
                val target = File(canonicalRoot, safe)
                if (!target.canonicalPath.startsWith(canonicalRoot.path + File.separator)) return@forEachIndexed
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().use { SafePaths.copyStream(input, it) } }
                written++
                onProgress(index + 1, entries.size)
            }
        }
        return written
    }
}
