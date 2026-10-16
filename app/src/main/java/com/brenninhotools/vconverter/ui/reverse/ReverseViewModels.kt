package com.brenninhotools.vconverter.ui.reverse

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.reverse.HxcToPsych
import com.brenninhotools.vconverter.reverse.HxcResult
import com.brenninhotools.vconverter.reverse.LevelSongInfo
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.reverse.VSliceSongSource
import com.brenninhotools.vconverter.reverse.VSliceToPsychCharacter
import com.brenninhotools.vconverter.reverse.VSliceToPsychChart
import com.brenninhotools.vconverter.reverse.VSliceToPsychLevel
import com.brenninhotools.vconverter.reverse.VSliceToPsychStage
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedText(
    val key: String,
    val name: String,
    val path: String,
    val text: String,
    val kind: String,
    val label: String,
    val slot: CharacterSlot? = null
)

abstract class ReverseViewModel(application: Application) : BaseConverterViewModel(application) {
    var files by mutableStateOf(listOf<PickedText>())
        private set

    abstract val noun: String

    open val engine: String = "V-Slice"

    protected open fun read(file: ImportedFile): String = file.readText()

    protected abstract fun accept(file: ImportedFile): Boolean

    protected abstract fun classify(file: ImportedFile, text: String): PickedText

    protected abstract fun build(items: List<PickedText>): ConvertedOutput

    open val canConvert: Boolean get() = files.isNotEmpty()

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val loaded = load(uris, ::accept) { file -> classify(file, read(file)) }
            files = files.filterNot { old -> loaded.items.any { it.key == old.key } } + loaded.items
            invalidate()
            loadStatus(loaded, "file(s)")
        }
    }

    fun remove(index: Int) {
        files = files.filterIndexed { i, _ -> i != index }
        invalidate()
    }

    fun setSlot(index: Int, slot: CharacterSlot) {
        files = files.mapIndexed { i, item -> if (i == index) item.copy(slot = slot) else item }
        invalidate()
    }

    fun clear() {
        files = emptyList()
        status = null
        invalidate()
    }

    fun convert() {
        runTask {
            try {
                val snapshot = files
                require(snapshot.isNotEmpty()) { "Add at least one $engine $noun." }
                output = withContext(Dispatchers.Default) { build(snapshot) }
                download = null
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Conversion failed.")
            }
        }
    }

    protected fun out(path: String, text: String) = OutputFile.of(path, text, null)

    protected fun stem(item: PickedText) = item.name.substringBeforeLast('.')
}

class ReverseChartsViewModel(application: Application) : ReverseViewModel(application) {
    override val noun = "song"

    override fun downloadName() = "charts-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "json" && (file.name.contains("-metadata") || file.name.contains("-chart"))

    override fun classify(file: ImportedFile, text: String): PickedText {
        val base = file.name.substringBeforeLast('.')
        val meta = Regex("^(.+)-metadata(?:-(.+))?$").find(base)
        val chart = Regex("^(.+)-chart(?:-(.+))?$").find(base)
        return when {
            meta != null && VSliceParser.isMetadata(text) -> {
                VSliceParser.metadata(text)
                PickedText(file.path, file.name, file.path, text, "metadata", (meta.groupValues[2].ifEmpty { "default" }))
            }
            chart != null && VSliceParser.isChart(text) -> {
                VSliceParser.chart(text)
                PickedText(file.path, file.name, file.path, text, "chart", (chart.groupValues[2].ifEmpty { "default" }))
            }
            else -> throw IllegalArgumentException("not a V-Slice <song>-metadata.json or <song>-chart.json file")
        }
    }

    private fun songAndVariation(item: PickedText): Pair<String, String> {
        val base = stem(item)
        val m = Regex("^(.+)-(?:metadata|chart)(?:-(.+))?$").find(base)!!
        return m.groupValues[1] to m.groupValues[2]
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val files = mutableListOf<OutputFile>()
        val bySong = items.groupBy { songAndVariation(it).first }
        bySong.forEach { (song, group) ->
            val metas = group.filter { it.kind == "metadata" }.associate { songAndVariation(it).second to VSliceParser.metadata(it.text) }
            val sources = group.filter { it.kind == "chart" }.mapNotNull { item ->
                val variation = songAndVariation(item).second
                val meta = metas[variation]
                if (meta == null) {
                    report.warn("$song: ${item.name} needs its metadata file (${song}-metadata${if (variation.isEmpty()) "" else "-$variation"}.json).")
                    null
                } else VSliceSongSource(variation, meta, VSliceParser.chart(item.text))
            }
            if (sources.isEmpty()) {
                report.warn("$song: add both the metadata and the chart file.")
                return@forEach
            }
            VSliceToPsychChart.convertAll(song, sources).forEach { result ->
                result.files.forEach { files += out(it.path, it.json) }
                result.report.stats.forEach { report.stat("${result.songId} ${it.label}", it.value) }
                result.report.sections.forEach { report.section("${result.songId} · ${it.title}", it.lines) }
                result.report.warnings.forEach { report.warn("${result.songId}: $it") }
            }
        }
        require(files.isNotEmpty()) { "Nothing to convert: add a song's metadata and chart files together." }
        return ConvertedOutput(files, report.build())
    }
}

class ReverseCharactersViewModel(application: Application) : ReverseViewModel(application) {
    override val noun = "character"

    override fun downloadName() = "characters-psych.zip"

    override fun accept(file: ImportedFile) = (file.extension == "json" && file.isUnder("characters")) || (file.extension == "xml" && file.isUnder("images"))

    override fun classify(file: ImportedFile, text: String): PickedText = when (file.extension) {
        "xml" -> {
            val atlas = SparrowAtlas.parse(text)
            require(atlas.frames.isNotEmpty()) { "no Sparrow frames" }
            PickedText(file.path, file.name, file.path, text, "atlas", "${atlas.frames.size} frames")
        }
        "json" -> {
            val c = VSliceParser.character(text)
            val id = file.name.substringBeforeLast('.')
            PickedText(file.path, file.name, file.path, text, "character", "${c.animations.size} anims", VSliceToPsychCharacter.defaultSlot(id))
        }
        else -> throw IllegalArgumentException("only V-Slice character .json and spritesheet .xml files")
    }

    override val canConvert: Boolean get() = files.any { it.kind == "character" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val atlases = items.filter { it.kind == "atlas" }.associate { VSliceToPsychCharacter.atlasKey(stem(it)) to SparrowAtlas.parse(it.text) }
        val report = ReportBuilder()
        val files = items.filter { it.kind == "character" }.map { item ->
            val id = stem(item)
            val result = VSliceToPsychCharacter.convert(id, VSliceParser.character(item.text), item.slot ?: CharacterSlot.OPPONENT, atlases)
            result.report.stats.firstOrNull { it.label == "Accuracy" }?.let { report.stat(id, it.value) }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
            out(result.path, result.json)
        }
        return ConvertedOutput(files, report.build())
    }
}

class ReverseStagesViewModel(application: Application) : ReverseViewModel(application) {
    override val noun = "stage"

    override fun downloadName() = "stages-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "json" && file.isUnder("stages")

    override fun classify(file: ImportedFile, text: String): PickedText {
        val stage = VSliceParser.stage(text)
        return PickedText(file.path, file.name, file.path, text, "stage", "${stage.props.size} props")
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val files = items.map { item ->
            val id = stem(item)
            val result = VSliceToPsychStage.convert(id, VSliceParser.stage(item.text))
            result.report.warnings.forEach { report.warn("$id: $it") }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            out(result.path, result.json)
        }
        report.stat("Stages", files.size)
        return ConvertedOutput(files, report.build())
    }
}

class ReverseWeeksViewModel(application: Application) : ReverseViewModel(application) {
    override val noun = "level"

    override fun downloadName() = "weeks-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "json" && (file.isUnder("levels") || file.name.contains("-metadata"))

    override fun classify(file: ImportedFile, text: String): PickedText = when {
        VSliceParser.isMetadata(text) -> PickedText(file.path, file.name, file.path, text, "metadata", "Song info")
        else -> {
            val level = VSliceParser.level(text)
            PickedText(file.path, file.name, file.path, text, "level", "${level.songs.size} songs")
        }
    }

    override val canConvert: Boolean get() = files.any { it.kind == "level" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val songs = items.filter { it.kind == "metadata" }.mapNotNull { item ->
            val m = Regex("^(.+)-metadata$").find(stem(item)) ?: return@mapNotNull null
            val id = m.groupValues[1]
            val meta = VSliceParser.metadata(item.text)
            id to LevelSongInfo(VSliceToPsychChart.songName(id, meta), meta.opponent, meta.difficulties.map { it.lowercase() })
        }.toMap()
        val report = ReportBuilder()
        val files = mutableListOf<OutputFile>()
        items.filter { it.kind == "level" }.forEach { item ->
            val id = stem(item)
            val result = VSliceToPsychLevel.convert(id, VSliceParser.level(item.text), songs)
            files += out(result.path, result.json)
            result.extra.forEach { (path, text) -> files += out(path, text) }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
        }
        report.stat("Weeks", items.count { it.kind == "level" })
        return ConvertedOutput(files, report.build())
    }
}

class ReverseScriptsViewModel(application: Application) : ReverseViewModel(application) {
    override val noun = "script"

    override fun downloadName() = "scripts-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "hxc"

    override fun classify(file: ImportedFile, text: String): PickedText {
        require(file.extension == "hxc") { "not a .hxc script" }
        val result = HxcToPsych.convert(file.name, text)
        return PickedText(file.path, file.name, file.path, text, "script", result.kind.label + if (result.targetId.isNotEmpty()) " · ${result.targetId}" else "")
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val results = mutableListOf<Pair<String, HxcResult>>()
        val failures = mutableListOf<String>()
        items.forEach { item ->
            try {
                results += item.name to HxcToPsych.convert(item.name, item.text)
            } catch (e: Exception) {
                failures += "${item.name}: ${e.message}"
            }
        }
        require(results.isNotEmpty()) { failures.firstOrNull() ?: "No script could be converted." }
        val files = results.flatMap { (_, r) -> r.files.map { out(it.path, it.content) } }
        return ConvertedOutput(files, HxcToPsych.report(results, failures), results.singleOrNull()?.second?.files?.firstOrNull()?.content)
    }
}
