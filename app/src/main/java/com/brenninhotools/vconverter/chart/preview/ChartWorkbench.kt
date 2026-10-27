package com.brenninhotools.vconverter.chart.preview

import com.brenninhotools.vconverter.convert.CodenameChartsConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychChartsConverter
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.PickedText
import com.brenninhotools.vconverter.convert.PsychChartsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameChartsConverter
import com.brenninhotools.vconverter.convert.ReverseChartsConverter
import com.brenninhotools.vconverter.core.ReportBuilder

data class ChartRun(val output: ConvertedOutput, val after: List<ChartPreview>)

object ChartWorkbench {
    fun engines(converter: FileConverter): Pair<ChartEngine, ChartEngine>? = when (converter) {
        is PsychChartsConverter -> ChartEngine.PSYCH to ChartEngine.VSLICE
        is ReverseChartsConverter -> ChartEngine.VSLICE to ChartEngine.PSYCH
        is CodenameChartsConverter -> ChartEngine.CODENAME to ChartEngine.VSLICE
        is CodenameToPsychChartsConverter -> ChartEngine.CODENAME to ChartEngine.PSYCH
        is PsychToCodenameChartsConverter -> ChartEngine.PSYCH to ChartEngine.CODENAME
        else -> null
    }

    fun before(converter: FileConverter, items: List<PickedText>): List<ChartPreview> {
        val (source, _) = engines(converter) ?: return emptyList()
        if (canRenameDifficulty(converter)) {
            return ChartPreviews.read(source, items.map { item ->
                val song = songOf(converter, item)
                when {
                    song.isEmpty() -> item.path to item.text
                    item.kind == "events" -> "data/$song/events.json" to item.text
                    else -> "data/$song/$song-${difficultyOf(converter, item)}.json" to item.text
                }
            })
        }
        return ChartPreviews.read(source, items.map { it.path to it.text })
    }

    fun run(converter: FileConverter, items: List<PickedText>, tweaks: ChartTweaks): ChartRun {
        val (_, target) = engines(converter) ?: return ChartRun(converter.build(items), emptyList())
        if (converter is PsychChartsConverter) converter.keepEvents = tweaks.keepUnknownEvents
        val output = converter.build(items)
        val files = ChartTweaker.apply(target, output.files, tweaks)
        val after = ChartPreviews.read(target, files.filter { it.bytes == null }.map { it.relativePath to it.content })
        val changes = tweaks.summary()
        val report = if (changes.isEmpty()) output.report else ReportBuilder().apply {
            output.report.stats.forEach { stat(it.label, it.value) }
            section("Your changes", changes)
            output.report.sections.forEach { section(it.title, it.lines) }
            output.report.warnings.forEach { warn(it) }
        }.build()
        return ChartRun(output.copy(files = files, report = report), after)
    }

    fun withDifficulty(item: PickedText, difficulty: String): PickedText {
        val clean = difficulty.trim().lowercase().replace(Regex("\\s+"), "-")
        if (item.kind != "chart" || !item.label.contains(" · ") || clean.isEmpty()) return item
        return item.copy(label = item.label.substringBefore(" · ") + " · " + clean)
    }

    fun difficultyOf(converter: FileConverter, item: PickedText): String? =
        if (canRenameDifficulty(converter) && item.kind == "chart" && item.label.contains(" · ")) item.label.substringAfter(" · ") else null

    fun songOf(converter: FileConverter, item: PickedText): String =
        if (canRenameDifficulty(converter) && item.label.contains(" · ")) item.label.substringBefore(" · ") else ""

    fun canRenameDifficulty(converter: FileConverter): Boolean =
        converter is PsychChartsConverter || converter is PsychToCodenameChartsConverter
}
