package com.brenninhotools.vconverter.mod

import com.brenninhotools.vconverter.platform.InputStream
import com.brenninhotools.vconverter.platform.OutputStream

interface ModFiles {
    val paths: List<String>
    fun open(path: String): InputStream
    fun size(path: String): Long
}

fun ModFiles.readText(path: String): String = open(path).use { it.readBytes().decodeToString() }

interface ModWriter {
    fun writeText(path: String, text: String)
    fun copy(path: String, source: () -> InputStream)
}

object SafePaths {
    fun normalize(raw: String): String? {
        val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || parts.any { it.contains('\u0000') }) return null
        return parts.joinToString("/")
    }

    fun copyStream(input: InputStream, output: OutputStream): Long = input.copyTo(output, 64 * 1024)
}
