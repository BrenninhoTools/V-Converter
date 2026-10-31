package com.brenninhotools.vconverter.mod

import com.brenninhotools.vconverter.platform.ByteArrayOutputStream
import com.brenninhotools.vconverter.platform.File
import com.brenninhotools.vconverter.platform.FileSource
import com.brenninhotools.vconverter.platform.IOException
import com.brenninhotools.vconverter.platform.InputStream
import com.brenninhotools.vconverter.platform.OutputStream
import com.brenninhotools.vconverter.platform.ZipFormatException
import com.brenninhotools.vconverter.platform.ZipReader
import com.brenninhotools.vconverter.platform.ZipWriterCore

class DirectoryModFiles(private val root: File) : ModFiles {
    override val paths: List<String> by lazy {
        root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    override fun open(path: String): InputStream = File(root, path).inputStream()

    override fun size(path: String): Long = File(root, path).length()
}

class DirectoryModWriter(private val root: File) : ModWriter {
    override fun writeText(path: String, text: String) {
        target(path).writeText(text)
    }

    override fun copy(path: String, source: () -> InputStream) {
        source().use { input -> target(path).outputStream().use { output -> input.copyTo(output) } }
    }

    private fun target(path: String): File {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        return file
    }
}

class ZipModWriter(output: OutputStream, prefix: String = "") : ModWriter, AutoCloseable {
    private val zip = ZipWriterCore(output)
    private val prefix = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    private val written = HashSet<String>()

    val entries: Int get() = written.size

    override fun writeText(path: String, text: String) {
        entry(path) { text.encodeToByteArray() }
    }

    override fun copy(path: String, source: () -> InputStream) {
        entry(path) { source().use { it.readBytes() } }
    }

    private inline fun entry(path: String, body: () -> ByteArray) {
        val name = prefix + path.replace('\\', '/').trimStart('/')
        if (!written.add(name.lowercase())) return
        zip.add(name, body())
    }

    override fun close() {
        zip.close()
    }

    companion object {
        fun folderName(title: String): String =
            title.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f\\u007f]"), "").trim().trim('.').ifEmpty { "V-Slice Mod" }
    }
}

object ZipExtractor {
    fun extract(archive: File, destination: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        destination.mkdirs()
        var written = 0
        try {
            ZipReader(FileSource(archive)).use { zip ->
                val entries = zip.entries.filter { !it.isDirectory }
                entries.forEachIndexed { index, entry ->
                    val safe = SafePaths.normalize(entry.name) ?: return@forEachIndexed
                    val target = File(destination, safe)
                    target.writeBytes(zip.read(entry))
                    written++
                    onProgress(index + 1, entries.size)
                }
            }
        } catch (e: ZipFormatException) {
            throw IOException("The ZIP file is damaged or not supported: ${e.message}", e)
        }
        return written
    }
}
