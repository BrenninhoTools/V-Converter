package com.brenninhotools.vconverter.mod

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipModWriter(output: OutputStream, prefix: String = "") : ModWriter, Closeable {
    private val zip = ZipOutputStream(output.buffered(64 * 1024))
    private val prefix = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    private val written = HashSet<String>()

    val entries: Int get() = written.size

    override fun writeText(path: String, text: String) {
        entry(path) { zip.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun copy(path: String, source: () -> InputStream) {
        entry(path) { source().use { it.copyTo(zip, 64 * 1024) } }
    }

    private inline fun entry(path: String, body: () -> Unit) {
        val name = prefix + path.replace('\\', '/').trimStart('/')
        if (!written.add(name.lowercase())) return
        zip.putNextEntry(ZipEntry(name))
        body()
        zip.closeEntry()
    }

    override fun close() {
        zip.close()
    }

    companion object {
        fun folderName(title: String): String =
            title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").trim().trim('.').ifEmpty { "V-Slice Mod" }
    }
}
