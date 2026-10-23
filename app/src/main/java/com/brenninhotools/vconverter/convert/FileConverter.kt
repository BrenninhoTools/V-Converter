package com.brenninhotools.vconverter.convert

import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile

data class PickedText(
    val key: String,
    val name: String,
    val path: String,
    val text: String,
    val kind: String,
    val label: String,
    val slot: CharacterSlot? = null
)

data class ConvertedOutput(
    val files: List<OutputFile>,
    val report: Report,
    val preview: String? = null
)

abstract class FileConverter {
    abstract val noun: String

    open val engine: String = "V-Slice"

    open fun downloadName(): String = "converted.zip"

    open fun read(file: ImportedFile): String = file.readText()

    abstract fun accept(file: ImportedFile): Boolean

    abstract fun classify(file: ImportedFile, text: String): PickedText

    abstract fun build(items: List<PickedText>): ConvertedOutput

    open fun canConvert(items: List<PickedText>): Boolean = items.isNotEmpty()

    protected fun out(path: String, text: String) = OutputFile.of(path, text, null)

    protected fun stem(item: PickedText) = item.name.substringBeforeLast('.')
}
