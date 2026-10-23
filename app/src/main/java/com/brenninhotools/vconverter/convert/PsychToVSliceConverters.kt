package com.brenninhotools.vconverter.convert

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptSource
import com.brenninhotools.vconverter.stage.LuaStageExtractor
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeekParser
import com.brenninhotools.vconverter.week.WeekSource

private fun psychParts(path: String) = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }

private fun psychId(raw: String) = raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

abstract class PsychToVSliceConverter : FileConverter() {
    override val engine = "Psych Engine"
}

class PsychChartsConverter : PsychToVSliceConverter() {
    override val noun = "chart"

    override fun downloadName() = "charts-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

    private fun baseName(stem: String): String {
        val suffix = stem.substringAfterLast('-', "").lowercase()
        return if (suffix in PsychToVSliceChart.difficultyOrder) stem.substringBeforeLast('-') else stem
    }

    override fun classify(file: ImportedFile, text: String): PickedText {
        val stem = file.name.substringBeforeLast('.')
        val parts = psychParts(file.path)
        val folder = parts.dropLast(1).lastOrNull()
        val song = PsychChartParser.parse(text)
        if (stem.equals("events", ignoreCase = true)) {
            val owner = folder ?: throw IllegalArgumentException("events.json must sit in its song folder (data/<song>/events.json)")
            return PickedText(file.path, file.name, file.path, text, "events", "${psychId(owner)} · ${song.events.size} events")
        }
        require(song.sections.isNotEmpty()) { "not a Psych chart" }
        val id = psychId(baseName(stem))
        val suffix = stem.substringAfterLast('-', "").lowercase()
        val difficulty = if (suffix in PsychToVSliceChart.difficultyOrder) suffix else "normal"
        return PickedText(file.path, file.name, file.path, text, "chart", "$id · $difficulty")
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "chart" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        items.groupBy { it.label.substringBefore(" · ") }.forEach { (id, group) ->
            val sources = group.filter { it.kind == "chart" }.map { ChartSource(it.label.substringAfter(" · "), PsychChartParser.parse(it.text)) }
            if (sources.isEmpty()) return@forEach
            val main = (sources.firstOrNull { it.difficulty == "normal" } ?: sources.first()).song
            val events = group.filter { it.kind == "events" }.flatMap { PsychChartParser.parseEventsOnly(it.text) }
            val conversion = PsychToVSliceChart.convert(sources, ConversionOptions(songName = main.name.ifBlank { id }, extraEvents = events))
            outputs += out("data/songs/$id/$id-metadata.json", VSliceWriter.metadataJson(conversion.metadata))
            outputs += out("data/songs/$id/$id-chart.json", VSliceWriter.chartJson(conversion.chart))
            ScriptConverter.eventFiles(conversion.report.unsupportedEvents.keys).forEach { outputs += out(it.path, it.content) }
            val converted = conversion.report.toReport()
            converted.stats.forEach { report.stat("$id ${it.label}", it.value) }
            converted.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            converted.warnings.forEach { report.warn("$id: $it") }
        }
        require(outputs.isNotEmpty()) { "Nothing to convert: add a Psych chart (data/<song>/<song>-<difficulty>.json)." }
        return ConvertedOutput(outputs.distinctBy { it.relativePath }, report.build())
    }
}

class PsychCharactersConverter : PsychToVSliceConverter() {
    override val noun = "character"

    override fun downloadName() = "characters-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "json" || file.extension == "xml"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val stem = file.name.substringBeforeLast('.')
        if (file.extension == "xml") {
            val atlas = SparrowAtlas.parse(text)
            require(atlas.frames.isNotEmpty()) { "not a Sparrow spritesheet" }
            return PickedText(file.path, file.name, file.path, text, "atlas", "${atlas.frames.size} frames")
        }
        val character = PsychCharacterParser.parse(text)
        val id = psychId(stem)
        return PickedText(file.path, file.name, file.path, text, "character", "${character.animations.size} anims", PsychToVSliceCharacter.defaultSlot(id, character))
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "character" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val atlases = items.filter { it.kind == "atlas" }.associate { PsychToVSliceCharacter.atlasKey(stem(it)) to SparrowAtlas.parse(it.text) }
        val report = ReportBuilder()
        val outputs = items.filter { it.kind == "character" }.map { item ->
            val id = psychId(stem(item))
            val character = PsychCharacterParser.parse(item.text)
            val converted = PsychToVSliceCharacter.convert(CharacterSource(id, character, item.slot ?: CharacterSlot.OPPONENT, atlases))
            converted.report.stats.firstOrNull { it.label == "Accuracy" }?.let { report.stat(id, it.value) }
            converted.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            converted.report.warnings.forEach { report.warn("$id: $it") }
            val missing = character.atlasImages.ifEmpty { listOf(character.image) }.filter { PsychToVSliceCharacter.atlasKey(it) !in atlases }
            if (missing.isNotEmpty()) report.warn("$id: add the spritesheet XML (${missing.joinToString()}) for exact offsets.")
            out("data/characters/$id.json", converted.json)
        }
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychStagesConverter : PsychToVSliceConverter() {
    override val noun = "stage"

    override fun downloadName() = "stages-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "json" || file.extension == "lua"

    override fun classify(file: ImportedFile, text: String): PickedText {
        if (file.extension == "lua") return PickedText(file.path, file.name, file.path, text, "lua", "Stage Lua")
        val stage = PsychStageParser.parse(text)
        return PickedText(file.path, file.name, file.path, text, "stage", "${stage.objects?.size ?: 0} objects")
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val json = items.filter { it.kind == "stage" }.associateBy { stem(it) }
        val lua = items.filter { it.kind == "lua" }.associateBy { stem(it) }
        val outputs = (json.keys + lua.keys).distinct().map { id ->
            var stage = json[id]?.let { PsychStageParser.parse(it.text) } ?: LuaStageExtractor.defaultStage()
            lua[id]?.let { script ->
                val props = LuaStageExtractor.extract(script.text)
                stage = LuaStageExtractor.merge(stage, props)
                if (props.notes.isNotEmpty()) report.section("$id · Lua stage", props.notes)
            }
            if (json[id] == null) report.warn("$id: no stage .json was added; default character positions were used.")
            val converted = PsychToVSliceStage.convert(StageSource(id, stage))
            converted.report.stats.firstOrNull { it.label == "Props" }?.let { report.stat("$id props", it.value) }
            converted.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            converted.report.warnings.forEach { report.warn("$id: $it") }
            out("data/stages/$id.json", converted.json)
        }
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychWeeksConverter : PsychToVSliceConverter() {
    override val noun = "week"

    override fun downloadName() = "weeks-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

    override fun classify(file: ImportedFile, text: String): PickedText =
        if (PsychWeekParser.isMenuCharacter(text)) {
            PsychWeekParser.parseMenuCharacter(text)
            PickedText(file.path, file.name, file.path, text, "menu", "Menu character")
        } else {
            val week = PsychWeekParser.parse(text)
            PickedText(file.path, file.name, file.path, text, "week", "${week.songs.size} songs")
        }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "week" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val menu = items.filter { it.kind == "menu" }.associate { stem(it) to PsychWeekParser.parseMenuCharacter(it.text) }
        val report = ReportBuilder()
        val outputs = items.filter { it.kind == "week" }.map { item ->
            val id = stem(item)
            val converted = PsychToVSliceLevel.convert(WeekSource(id, PsychWeekParser.parse(item.text), menu))
            report.stat("$id songs", converted.songIds.size)
            converted.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            converted.report.warnings.forEach { report.warn("$id: $it") }
            out("data/levels/$id.json", converted.json)
        }
        report.section("Notes", listOf("Song ids use Psych's song path format (\"Dad Battle\" → \"${SongPath.format("Dad Battle")}\"), matching the Charts converter."))
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychScriptsConverter : PsychToVSliceConverter() {
    override val noun = "script"

    override fun downloadName() = "scripts-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension in setOf("lua", "hx", "hxs", "hscript")

    override fun classify(file: ImportedFile, text: String): PickedText {
        val target = ScriptConverter.detectTarget(file.path)
        return PickedText(file.path, file.name, file.path, text, "script", target.scope.label + if (target.key.isNotEmpty()) " · ${target.key}" else "")
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val sources = items.map { ScriptSource(it.path, it.text, ScriptConverter.detectTarget(it.path)) }
        val noteTypes = sources.mapNotNull { it.target }.filter { it.scope == com.brenninhotools.vconverter.script.ScriptScope.NOTE_TYPE }.map { it.key }
        val bundle = ScriptConverter.convert("VConverter", sources, ScriptConverter.noteTypeMap(noteTypes))
        require(bundle.converted > 0) { bundle.report.warnings.firstOrNull() ?: "No script could be converted." }
        val outputs = bundle.files.map { out(it.path, it.content) }
        return ConvertedOutput(outputs, bundle.report, outputs.singleOrNull()?.content)
    }
}
