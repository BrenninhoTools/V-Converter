package com.brenninhotools.vconverter.generator

import com.brenninhotools.vconverter.core.Report

class GenFile(val path: String, val text: String? = null, val bytes: ByteArray? = null) {
    init {
        require(text != null || bytes != null) { "A generated file needs content." }
    }
}

data class Generated(val files: List<GenFile>, val report: Report)

data class PickedAsset(val name: String, val bytes: ByteArray) {
    val stem: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

object Ids {
    fun of(raw: String): String = raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun className(raw: String): String {
        val parts = raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        val joined = parts.joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        return when {
            joined.isEmpty() -> "Generated"
            joined.first().isDigit() -> "S$joined"
            else -> joined
        }
    }

    fun pretty(id: String): String =
        id.split('-', '_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

    fun color(raw: String, fallback: String): String {
        val hex = raw.trim().removePrefix("#")
        return if (hex.matches(Regex("[0-9A-Fa-f]{6}"))) "#" + hex.uppercase() else fallback
    }
}
