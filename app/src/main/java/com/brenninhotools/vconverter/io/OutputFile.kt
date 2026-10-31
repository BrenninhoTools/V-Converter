package com.brenninhotools.vconverter.io

import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.platform.inputStream

class OutputFile(val directories: List<String>, val name: String, val content: String, val bytes: ByteArray? = null) {
    val relativePath: String get() = (directories + name).joinToString("/")

    fun writeTo(writer: ModWriter) {
        val data = bytes
        if (data != null) writer.copy(relativePath) { data.inputStream() } else writer.writeText(relativePath, content)
    }

    companion object {
        fun of(path: String, text: String?, bytes: ByteArray?): OutputFile {
            val clean = path.trim('/')
            return OutputFile(clean.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }, clean.substringAfterLast('/'), text ?: "", bytes)
        }
    }
}
