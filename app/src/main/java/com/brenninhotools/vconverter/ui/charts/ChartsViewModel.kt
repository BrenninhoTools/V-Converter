package com.brenninhotools.vconverter.ui.charts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.brenninhotools.vconverter.chart.preview.ChartEngine
import com.brenninhotools.vconverter.chart.preview.ChartField
import com.brenninhotools.vconverter.chart.preview.ChartPreview
import com.brenninhotools.vconverter.chart.preview.ChartTweaker
import com.brenninhotools.vconverter.chart.preview.ChartTweaks
import com.brenninhotools.vconverter.chart.preview.ChartWorkbench
import com.brenninhotools.vconverter.convert.CodenameChartsConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychChartsConverter
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.PsychChartsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameChartsConverter
import com.brenninhotools.vconverter.convert.ReverseChartsConverter
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.reverse.PickedText
import com.brenninhotools.vconverter.ui.reverse.ReverseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class ChartsViewModel(application: Application, private val converter: FileConverter) : ReverseViewModel(application, converter) {
    val source: ChartEngine
    val target: ChartEngine

    init {
        val engines = ChartWorkbench.engines(converter) ?: (ChartEngine.PSYCH to ChartEngine.VSLICE)
        source = engines.first
        target = engines.second
    }

    val fields: Set<ChartField> = ChartTweaker.fields(source, target)

    val canRenameDifficulty: Boolean = ChartWorkbench.canRenameDifficulty(converter)

    var tweaks by mutableStateOf(ChartTweaks())
        private set
    var before by mutableStateOf(listOf<ChartPreview>())
        private set
    var after by mutableStateOf(listOf<ChartPreview>())
        private set
    var showAfter by mutableStateOf(false)
    var selected by mutableStateOf<String?>(null)
    var zoomed by mutableStateOf(false)

    private var previewJob: Job? = null
    private var pendingAfter = listOf<ChartPreview>()

    val difficulties: List<String> get() = before.map { it.difficulty }.distinct()

    val selectedBefore: ChartPreview? get() = before.firstOrNull { key(it) == selected } ?: before.firstOrNull()

    val selectedAfter: ChartPreview? get() {
        val source = selectedBefore ?: return after.firstOrNull()
        return after.firstOrNull { it.matches(source) } ?: after.firstOrNull { it.difficulty.equals(source.difficulty, ignoreCase = true) }
    }

    val current: ChartPreview? get() = if (showAfter && after.isNotEmpty()) selectedAfter ?: selectedBefore else selectedBefore

    fun key(preview: ChartPreview): String = "${preview.song}|${preview.variation}|${preview.difficulty}"

    fun select(preview: ChartPreview) {
        selected = key(preview)
    }

    fun updateTweaks(change: (ChartTweaks) -> ChartTweaks) {
        tweaks = change(tweaks)
        invalidate()
        after = emptyList()
        showAfter = false
    }

    fun setSpeed(difficulty: String, raw: String) {
        val value = raw.replace(',', '.').toDoubleOrNull()
        updateTweaks { current ->
            val speeds = current.speeds.toMutableMap()
            if (value == null || value <= 0.0) speeds.remove(difficulty) else speeds[difficulty] = value
            current.copy(speeds = speeds)
        }
    }

    fun resetTweaks() = updateTweaks { ChartTweaks(keepUnknownEvents = it.keepUnknownEvents) }

    fun setDifficulty(index: Int, value: String) {
        val item = files.getOrNull(index) ?: return
        files = files.mapIndexed { i, old -> if (i == index) ChartWorkbench.withDifficulty(item, value) else old }
        invalidate()
        onFilesChanged()
    }

    fun difficultyOf(item: PickedText): String? = ChartWorkbench.difficultyOf(converter, item)

    fun songOf(item: PickedText): String = ChartWorkbench.songOf(converter, item)

    override fun onFilesChanged() {
        after = emptyList()
        showAfter = false
        val snapshot = files
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            before = withContext(Dispatchers.Default) { runCatching { ChartWorkbench.before(converter, snapshot) }.getOrDefault(emptyList()) }
            if (before.none { key(it) == selected }) selected = before.firstOrNull()?.let(::key)
        }
    }

    override fun produce(snapshot: List<PickedText>): ConvertedOutput {
        val run = ChartWorkbench.run(converter, snapshot, tweaks)
        pendingAfter = run.after
        return run.output
    }

    override fun onConverted() {
        after = pendingAfter
        showAfter = after.isNotEmpty()
    }
}

class PsychToVSliceChartsViewModel(application: Application) : ChartsViewModel(application, PsychChartsConverter())

class ReverseChartsViewModel(application: Application) : ChartsViewModel(application, ReverseChartsConverter())

class CodenameChartsViewModel(application: Application) : ChartsViewModel(application, CodenameChartsConverter())

class CodenameToPsychChartsViewModel(application: Application) : ChartsViewModel(application, CodenameToPsychChartsConverter())

class PsychToCodenameChartsViewModel(application: Application) : ChartsViewModel(application, PsychToCodenameChartsConverter())
