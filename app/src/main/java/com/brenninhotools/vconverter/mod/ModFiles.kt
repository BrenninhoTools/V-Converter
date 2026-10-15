package com.brenninhotools.vconverter.mod

import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface ModFiles {
    val paths: List<String>
    fun open(path: String): InputStream
    fun size(path: String): Long
}

fun ModFiles.readText(path: String): String = open(path).use { it.readBytes().toString(Charsets.UTF_8) }

interface ModWriter {
    fun writeText(path: String, text: String)
    fun copy(path: String, source: () -> InputStream)
}

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
        target(path).writeText(text, Charsets.UTF_8)
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

object SafePaths {
    fun normalize(raw: String): String? {
        val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || parts.any { it.contains('\u0000') }) return null
        return parts.joinToString("/")
    }

    fun copyStream(input: InputStream, output: OutputStream): Long = input.copyTo(output, 64 * 1024)
}
