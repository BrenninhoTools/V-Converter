package com.vconvertercrew.vconverter.launcher

import com.vconvertercrew.vconverter.mod.ModConverter
import com.vconvertercrew.vconverter.mod.ModWriter
import com.vconvertercrew.vconverter.platform.InputStream

class FunkinModWriter(private val target: ModWriter, private val prefix: String) : ModWriter {
    var count = 0
        private set
    private var hasMeta = false

    override fun writeText(path: String, text: String) {
        target.writeText(prefix + mark(path), text)
    }

    override fun copy(path: String, source: () -> InputStream) {
        target.copy(prefix + mark(path), source)
    }

    private fun mark(path: String): String {
        val clean = path.replace('\\', '/').trimStart('/')
        synchronized(this) {
            count++
            if (clean == META) hasMeta = true
        }
        return clean
    }

    fun finish(title: String) {
        if (!hasMeta) writeText(META, metadata(title))
    }

    companion object {
        const val META = "_polymod_meta.json"

        private fun quote(text: String): String = buildString {
            append('"')
            text.forEach { c ->
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

        fun metadata(title: String): String =
            "{\n  \"title\": ${quote(title)},\n  \"description\": \"Converted with V-Converter.\",\n  \"contributors\": [],\n  \"homepage\": \"\",\n  \"api_version\": \"${ModConverter.API_VERSION}\",\n  \"mod_version\": \"1.0.0\",\n  \"license\": \"\"\n}"

        fun prefix(name: String, insideMods: Boolean): String = if (insideMods) "$name/" else "mods/$name/"
    }
}
