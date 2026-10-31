package com.brenninhotools.vconverter.mod

import java.io.File
import java.io.InputStream

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
