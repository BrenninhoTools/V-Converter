package com.brenninhotools.vconverter.ui.charts

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.psych.PsychEvent
import com.brenninhotools.vconverter.chart.psych.PsychSong
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedChart(
    val key: String,
    val fileName: String,
    val difficulty: String,
    val song: PsychSong
) {
    val noteCount: Int get() = song.sections.sumOf { it.notes.size }
}

data class PickedEvents(val fileName: String, val events: List<PsychEvent>)

class ChartsViewModel(application: Application) : BaseConverterViewModel(application) {
    var charts by mutableStateOf(listOf<PickedChart>())
        private set
    var events by mutableStateOf<PickedEvents?>(null)
        private set
    var songId by mutableStateOf("")
    var songName by mutableStateOf("")
    var artist by mutableStateOf("")
    var charter by mutableStateOf("")

    override fun downloadName(): String = "${sanitizeId(songId).ifEmpty { "song" }}-vslice.zip"

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val loaded = load(uris, ::isChartCandidate) { file ->
                val text = file.readText()
                if (file.name.equals("events.json", ignoreCase = true)) {
                    PickedEvents(file.name, PsychChartParser.parseEventsOnly(text))
                } else {
                    PickedChart(file.path, file.name, detectDifficulty(file.name), PsychChartParser.parse(text))
                }
            }
            loaded.items.filterIsInstance<PickedEvents>().lastOrNull()?.let { events = it }
            val added = loaded.items.filterIsInstance<PickedChart>()
            charts = charts.filterNot { old -> added.any { it.key == old.key } } + added
            fillSongInfo()
            invalidate()
            loadStatus(loaded, "file(s)")
        }
    }

    private fun isChartCandidate(file: ImportedFile): Boolean =
        file.extension == "json" && listOf("characters", "stages", "weeks", "images").none { file.isUnder(it) }

    fun setDifficulty(index: Int, value: String) {
        charts = charts.mapIndexed { i, chart -> if (i == index) chart.copy(difficulty = value) else chart }
        invalidate()
    }

    fun remove(index: Int) {
        charts = charts.filterIndexed { i, _ -> i != index }
        invalidate()
    }

    fun removeEvents() {
        events = null
        invalidate()
    }

    fun clear() {
        charts = emptyList()
        events = null
        songId = ""
        songName = ""
        status = null
        invalidate()
    }

    fun convert() {
        runTask {
            try {
                val id = sanitizeId(songId)
                require(id.isNotEmpty()) { "Enter a song id. It names the folder and files." }
                val sources = charts.map { ChartSource(it.difficulty, it.song) }
                val options = ConversionOptions(
                    songName = songName,
                    artist = artist,
                    charter = charter,
                    extraEvents = events?.events ?: emptyList()
                )
                val result = withContext(Dispatchers.Default) {
                    val conversion = PsychToVSliceChart.convert(sources, options)
                    val folder = listOf("data", "songs", id)
                    val files = mutableListOf(
                        OutputFile(folder, "$id-metadata.json", VSliceWriter.metadataJson(conversion.metadata)),
                        OutputFile(folder, "$id-chart.json", VSliceWriter.chartJson(conversion.chart))
                    )
                    if (conversion.report.keptPsychEvents > 0) {
                        val noteTypes = sources.flatMap { source -> source.song.sections.flatMap { section -> section.notes.map { it.type } } }.toSet()
                        ScriptConverter.runtimeFiles(RUNTIME_SLUG, ScriptConverter.noteTypeMap(noteTypes)).forEach { file ->
                            files += OutputFile(file.path.substringBeforeLast('/').split('/'), file.path.substringAfterLast('/'), file.content)
                        }
                    }
                    ConvertedOutput(files = files, report = conversion.report.toReport())
                }
                songId = id
                output = result
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Conversion failed.")
            }
        }
    }

    private fun fillSongInfo() {
        val first = charts.firstOrNull() ?: return
        if (songName.isBlank()) songName = first.song.name.ifBlank { baseName(first.fileName) }
        if (songId.isBlank()) songId = sanitizeId(baseName(first.fileName).ifBlank { songName })
    }

    companion object {
        private val knownDifficulties = PsychToVSliceChart.difficultyOrder
        const val RUNTIME_SLUG = "VConverter"

        fun baseName(fileName: String): String {
            val stem = fileName.substringBeforeLast('.')
            val suffix = stem.substringAfterLast('-', "").lowercase()
            return if (suffix in knownDifficulties) stem.substringBeforeLast('-') else stem
        }

        fun detectDifficulty(fileName: String): String {
            val suffix = fileName.substringBeforeLast('.').substringAfterLast('-', "").lowercase()
            return if (suffix in knownDifficulties) suffix else "normal"
        }
    }
}
