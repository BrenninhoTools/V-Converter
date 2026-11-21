package com.vconvertercrew.vconverter.mod

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import com.vconvertercrew.vconverter.archive.NativeZipModFiles

class DirectoryModFiles(private val root: File) : ModFiles {
    override val paths: List<String> by lazy {
        root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    fun file(path: String): File = File(root, path)

    override fun open(path: String): InputStream = File(root, path).inputStream()

    override fun size(path: String): Long = File(root, path).length()
}

class DirectoryModWriter(private val root: File) : ModWriter {
    override fun writeText(path: String, text: String) {
        target(path).writeText(text, Charsets.UTF_8)
    }

    override fun copy(path: String, source: () -> InputStream) {
        source().use { input -> target(path).outputStream().use { output -> input.copyTo(output, 256 * 1024) } }
    }

    override fun copyFrom(path: String, files: ModFiles, source: String) {
        when (files) {
            is DirectoryModFiles -> Files.copy(files.file(source).toPath(), target(path).toPath(), StandardCopyOption.REPLACE_EXISTING)
            is NativeZipModFiles -> files.extract(source, target(path))
            else -> copy(path) { files.open(source) }
        }
    }

    private fun target(path: String): File {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        return file
    }
}
