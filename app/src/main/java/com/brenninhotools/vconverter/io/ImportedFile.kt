package com.brenninhotools.vconverter.io

import com.brenninhotools.vconverter.platform.InputStream

class ImportedFile(
    val name: String,
    val path: String,
    val fromArchive: Boolean,
    private val opener: () -> InputStream
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    fun open(): InputStream = opener()

    fun readText(): String = open().use { it.readBytes().decodeToString() }

    fun isUnder(folder: String): Boolean {
        val segments = path.lowercase().split('/')
        return segments.dropLast(1).contains(folder.lowercase())
    }
}
