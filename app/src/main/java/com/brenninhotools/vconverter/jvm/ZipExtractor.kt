package com.brenninhotools.vconverter.mod

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipFile

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
