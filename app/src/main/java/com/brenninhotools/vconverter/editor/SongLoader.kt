package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.convert.CodenameChartsConverter
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.PsychChartsConverter
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.platform.byteInputStream

class EditorAudio(val name: String, val bytes: ByteArray) {
    val isInstrumental: Boolean get() = name.startsWith("Inst", ignoreCase = true)
}

data class LoadedSong(val document: ChartDocument, val audio: List<EditorAudio>, val source: String)

object SongLoader {
    private val vsliceName = Regex("^(.+)-(metadata|chart)(?:-(.+))?\\.json$", RegexOption.IGNORE_CASE)
    private val audioExtensions = setOf("ogg", "mp3", "wav")

    fun load(files: List<Pair<String, ByteArray>>): LoadedSong {
        val texts = files.filter { it.first.endsWith(".json", ignoreCase = true) }.map { it.first to it.second.decodeToString() }
        val audio = files.filter { it.first.substringAfterLast('.').lowercase() in audioExtensions }
            .map { EditorAudio(it.first.replace('\\', '/').substringAfterLast('/'), it.second) }
        val vslice = fromVSlice(texts)
        if (vslice != null) return LoadedSong(vslice, pickAudio(audio, vslice.variation), "V-Slice")
        val converted = convert(PsychChartsConverter(), texts)?.let { it to "Psych Engine" }
            ?: convert(CodenameChartsConverter(), texts)?.let { it to "Codename Engine" }
            ?: throw IllegalArgumentException("No chart found. Pick a V-Slice <song>-metadata.json + <song>-chart.json, Psych charts or a Codename song.")
        return LoadedSong(converted.first, pickAudio(audio, converted.first.variation), converted.second)
    }

    private fun fromVSlice(texts: List<Pair<String, String>>): ChartDocument? {
        val metas = HashMap<Pair<String, String>, String>()
        val charts = HashMap<Pair<String, String>, String>()
        texts.forEach { (path, text) ->
            val match = vsliceName.find(path.replace('\\', '/').substringAfterLast('/')) ?: return@forEach
            val key = match.groupValues[1].lowercase() to match.groupValues[3].lowercase()
            if (match.groupValues[2].equals("metadata", ignoreCase = true)) metas[key] = text else charts[key] = text
        }
        val key = metas.keys.filter { it in charts }.sortedWith(compareBy({ it.second.isNotEmpty() }, { it.first })).firstOrNull() ?: return null
        return ChartDocument.load(key.first, key.second, metas.getValue(key), charts.getValue(key))
    }

    private fun convert(converter: FileConverter, texts: List<Pair<String, String>>): ChartDocument? {
        val items = texts.mapNotNull { (path, text) ->
            runCatching { converter.classify(ImportedFile(path.substringAfterLast('/'), path, true) { text.byteInputStream() }, text) }.getOrNull()
        }
        if (!converter.canConvert(items)) return null
        val output = runCatching { converter.build(items) }.getOrNull() ?: return null
        return fromVSlice(output.files.filter { it.bytes == null }.map { it.relativePath to it.content })
    }

    private fun pickAudio(audio: List<EditorAudio>, variation: String): List<EditorAudio> {
        val suffix = if (variation.isEmpty()) "" else "-$variation"
        val matching = audio.filter { it.name.substringBeforeLast('.').endsWith(suffix, ignoreCase = true) }
        val chosen = if (suffix.isNotEmpty() && matching.isNotEmpty()) matching else audio.filter { a ->
            val stem = a.name.substringBeforeLast('.')
            stem.equals("Inst", true) || stem.startsWith("Voices", true)
        }.ifEmpty { audio }
        return chosen.sortedByDescending { it.isInstrumental }
    }
}
