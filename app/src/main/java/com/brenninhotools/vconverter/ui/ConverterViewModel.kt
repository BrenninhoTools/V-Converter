package com.brenninhotools.vconverter.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.ConversionReport
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.psych.PsychEvent
import com.brenninhotools.vconverter.chart.psych.PsychSong
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.io.DocumentIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedChart(
    val uri: Uri,
    val fileName: String,
    val difficulty: String,
    val song: PsychSong
)

data class PickedEvents(val uri: Uri, val fileName: String, val count: Int)

data class ConversionOutput(
    val songId: String,
    val metadataJson: String,
    val chartJson: String,
    val report: ConversionReport
)

sealed interface StatusMessage {
    data class Info(val text: String) : StatusMessage
    data class Error(val text: String) : StatusMessage
}

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    var charts by mutableStateOf(listOf<PickedChart>())
        private set
    var events by mutableStateOf<PickedEvents?>(null)
        private set
    var songId by mutableStateOf("")
    var songName by mutableStateOf("")
    var artist by mutableStateOf("")
    var charter by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<StatusMessage?>(null)
        private set
    var output by mutableStateOf<ConversionOutput?>(null)
        private set

    private var extraEvents = emptyList<PsychEvent>()

    private val context get() = getApplication<Application>()

    fun addCharts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val added = mutableListOf<PickedChart>()
            val failures = mutableListOf<String>()
            var loadedEvents: Pair<PickedEvents, List<PsychEvent>>? = null
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val name = DocumentIO.displayName(context, uri)
                    try {
                        if (name.equals("events.json", ignoreCase = true)) {
                            loadedEvents = readEvents(uri, name)
                        } else {
                            val song = PsychChartParser.parse(DocumentIO.readText(context, uri))
                            added += PickedChart(uri, name, detectDifficulty(name), song)
                        }
                    } catch (e: Exception) {
                        failures += "$name: ${e.message}"
                    }
                }
            }
            loadedEvents?.let { applyEvents(it) }
            charts = (charts.filterNot { old -> added.any { it.uri == old.uri } } + added)
            output = null
            fillSongInfo()
            status = if (failures.isEmpty()) {
                StatusMessage.Info("Loaded ${added.size} chart(s).")
            } else {
                StatusMessage.Error("Some files could not be read:\n" + failures.joinToString("\n"))
            }
        }
    }

    fun pickEvents(uri: Uri) {
        runTask {
            val name = withContext(Dispatchers.IO) { DocumentIO.displayName(context, uri) }
            try {
                applyEvents(withContext(Dispatchers.IO) { readEvents(uri, name) })
                output = null
                status = StatusMessage.Info("Loaded ${events?.count ?: 0} events from $name.")
            } catch (e: Exception) {
                status = StatusMessage.Error("$name: ${e.message}")
            }
        }
    }

    fun clearEvents() {
        events = null
        extraEvents = emptyList()
        output = null
    }

    fun setDifficulty(index: Int, value: String) {
        charts = charts.mapIndexed { i, chart -> if (i == index) chart.copy(difficulty = value) else chart }
        output = null
    }

    fun removeChart(index: Int) {
        charts = charts.filterIndexed { i, _ -> i != index }
        output = null
    }

    fun clearAll() {
        charts = emptyList()
        clearEvents()
        songId = ""
        songName = ""
        status = null
    }

    fun convert() {
        runTask {
            try {
                val id = sanitizeSongId(songId)
                require(id.isNotEmpty()) { "Enter a song id (used for the folder and file names)." }
                val result = withContext(Dispatchers.Default) {
                    val sources = charts.map { ChartSource(it.difficulty, it.song) }
                    val conversion = PsychToVSliceChart.convert(
                        sources,
                        ConversionOptions(
                            songName = songName,
                            artist = artist,
                            charter = charter,
                            extraEvents = extraEvents
                        )
                    )
                    ConversionOutput(
                        songId = id,
                        metadataJson = VSliceWriter.metadataJson(conversion.metadata),
                        chartJson = VSliceWriter.chartJson(conversion.chart),
                        report = conversion.report
                    )
                }
                songId = id
                output = result
                status = StatusMessage.Info("Converted! Choose a folder to save $id-metadata.json and $id-chart.json.")
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Conversion failed.")
            }
        }
    }

    fun save(treeUri: Uri) {
        val result = output ?: return
        runTask {
            try {
                val path = withContext(Dispatchers.IO) {
                    DocumentIO.writeSongFolder(
                        context,
                        treeUri,
                        result.songId,
                        linkedMapOf(
                            "${result.songId}-metadata.json" to result.metadataJson,
                            "${result.songId}-chart.json" to result.chartJson
                        )
                    )
                }
                status = StatusMessage.Info("Saved to $path. Put this folder inside data/songs/ of your V-Slice mod.")
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not save the files.")
            }
        }
    }

    private fun readEvents(uri: Uri, name: String): Pair<PickedEvents, List<PsychEvent>> {
        val parsed = PsychChartParser.parseEventsOnly(DocumentIO.readText(context, uri))
        return PickedEvents(uri, name, parsed.size) to parsed
    }

    private fun applyEvents(loaded: Pair<PickedEvents, List<PsychEvent>>) {
        events = loaded.first
        extraEvents = loaded.second
    }

    private fun fillSongInfo() {
        val first = charts.firstOrNull() ?: return
        if (songName.isBlank()) songName = first.song.name.ifBlank { baseName(first.fileName) }
        if (songId.isBlank()) songId = sanitizeSongId(songName.ifBlank { baseName(first.fileName) })
    }

    private fun runTask(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    companion object {
        private val knownDifficulties = listOf("easy", "normal", "hard", "erect", "nightmare")

        fun baseName(fileName: String): String {
            val stem = fileName.substringBeforeLast('.')
            val suffix = stem.substringAfterLast('-', "").lowercase()
            return if (suffix in knownDifficulties) stem.substringBeforeLast('-') else stem
        }

        fun detectDifficulty(fileName: String): String {
            val suffix = fileName.substringBeforeLast('.').substringAfterLast('-', "").lowercase()
            return if (suffix in knownDifficulties) suffix else "normal"
        }

        fun sanitizeSongId(raw: String): String =
            raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
