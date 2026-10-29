package com.brenninhotools.vconverter.convert

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychNoteStyles
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.AnimateAtlases
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

    var keepEvents: Boolean = true

    override fun downloadName() = "charts-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

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
        val (id, difficulty) = PsychChartNames.split(stem, folder)
        return PickedText(file.path, file.name, file.path, text, "chart", "${psychId(id)} · $difficulty")
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "chart" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val songs = items.groupBy { it.label.substringBefore(" · ") }
        songs.forEach { (id, group) ->
            val sources = group.filter { it.kind == "chart" }.map { ChartSource(it.label.substringAfter(" · "), PsychChartParser.parse(it.text)) }
            if (sources.isEmpty()) return@forEach
            val main = (sources.firstOrNull { it.difficulty == "normal" } ?: sources.first()).song
            val events = group.filter { it.kind == "events" }.flatMap { PsychChartParser.parseEventsOnly(it.text) }
            val style = main.arrowSkin.takeIf { PsychNoteStyles.isCustom(it) }?.let { PsychNoteStyles.build(it, null) }
            if (style != null) {
                outputs += out("data/notestyles/${style.id}.json", style.json)
                report.section(if (songs.size > 1) "$id · Note style ${style.id}" else "Note style ${style.id}", listOf("Made from the chart's arrowSkin \"${main.arrowSkin}\".") + style.notes)
            }
            val conversion = PsychToVSliceChart.convert(sources, ConversionOptions(songName = main.name.ifBlank { id }, extraEvents = events, keepPsychEvents = keepEvents, noteStyle = style?.id))
            conversion.files(id).forEach { (name, text) -> outputs += out("data/songs/$id/$name", text) }
            if (keepEvents) ScriptConverter.eventFiles(conversion.report.unsupportedEvents.keys).forEach { outputs += out(it.path, it.content) }
            val converted = conversion.report.toReport()
            val prefix = if (songs.size > 1) "$id " else ""
            converted.stats.forEach { report.stat(prefix + it.label, it.value) }
            converted.sections.forEach { report.section(if (songs.size > 1) "$id · ${it.title}" else it.title, it.lines) }
            converted.warnings.forEach { report.warn(if (songs.size > 1) "$id: $it" else it) }
        }
        require(outputs.isNotEmpty()) { "Nothing to convert: add a Psych chart (data/<song>/<song>-<difficulty>.json)." }
        return ConvertedOutput(outputs.distinctBy { it.relativePath }, report.build())
    }
}

object PsychChartNames {
    fun split(stem: String, folder: String?): Pair<String, String> {
        val lower = stem.lowercase()
        val dir = folder?.lowercase()?.takeUnless { it == "data" || it == "charts" }
        if (dir != null) {
            if (lower == dir) return dir to "normal"
            if (lower.startsWith("$dir-") && lower.length > dir.length + 1) return dir to lower.substring(dir.length + 1)
        }
        val suffix = lower.substringAfterLast('-', "")
        return if (suffix in PsychToVSliceChart.difficultyOrder) lower.substringBeforeLast('-') to suffix else lower to "normal"
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
        if (AnimateAtlases.isAtlasFile(file.name)) {
            return PickedText(file.path, file.name, file.path, text, "animate", "Animate atlas · ${AnimateAtlases.key(file.path)}")
        }
        val character = PsychCharacterParser.parse(text)
        val id = psychId(stem)
        return PickedText(file.path, file.name, file.path, text, "character", "${character.animations.size} anims", PsychToVSliceCharacter.defaultSlot(id, character))
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "character" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val atlases = items.filter { it.kind == "atlas" }.associate { PsychToVSliceCharacter.atlasKey(stem(it)) to SparrowAtlas.parse(it.text) }
        val animates = AnimateAtlases.collect(items.filter { it.kind == "animate" }.map { it.path to it.text })
        val report = ReportBuilder()
        val outputs = items.filter { it.kind == "character" }.map { item ->
            val id = psychId(stem(item))
            val character = PsychCharacterParser.parse(item.text)
            val animate = animates[PsychToVSliceCharacter.atlasKey(character.image)]
            val converted = PsychToVSliceCharacter.convert(CharacterSource(id, character, item.slot ?: CharacterSlot.OPPONENT, atlases, animate = animate))
            converted.report.stats.firstOrNull { it.label == "Accuracy" }?.let { report.stat(id, it.value) }
            converted.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            converted.report.warnings.forEach { report.warn("$id: $it") }
            val missing = character.atlasImages.ifEmpty { listOf(character.image) }.filter { PsychToVSliceCharacter.atlasKey(it) !in atlases && PsychToVSliceCharacter.atlasKey(it) !in animates }
            if (missing.isNotEmpty()) report.warn("$id: add the spritesheet XML (${missing.joinToString()}), or Animation.json + spritemap for Animate atlases, for exact offsets.")
            out("data/characters/$id.json", converted.json)
        }
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychStagesConverter : PsychToVSliceConverter() {
    override val noun = "stage"

    override fun downloadName() = "stages-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "json" || file.extension == "lua" || (file.extension == "xml" && file.isUnder("images"))

    override fun classify(file: ImportedFile, text: String): PickedText {
        if (file.extension == "lua") return PickedText(file.path, file.name, file.path, text, "lua", "Stage Lua")
        if (file.extension == "xml") {
            val atlas = SparrowAtlas.parse(text)
            require(atlas.frames.isNotEmpty()) { "not a Sparrow spritesheet" }
            val image = psychParts(file.path).let { parts -> parts.indexOfLast { it.equals("images", true) }.let { i -> if (i >= 0) parts.drop(i + 1) else listOf(parts.last()) } }.joinToString("/").substringBeforeLast('.')
            return PickedText(file.path, file.name, file.path, text, "atlas", image)
        }
        val stage = PsychStageParser.parse(text)
        return PickedText(file.path, file.name, file.path, text, "stage", "${stage.objects?.size ?: 0} objects")
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "stage" || it.kind == "lua" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val json = items.filter { it.kind == "stage" }.associateBy { stem(it) }
        val lua = items.filter { it.kind == "lua" }.associateBy { stem(it) }
        val atlases = items.filter { it.kind == "atlas" }.map { it.label.lowercase() to SparrowAtlas.parse(it.text) }
        val lookup: (String) -> SparrowAtlas? = { image ->
            val key = image.lowercase()
            atlases.firstOrNull { it.first == key }?.second ?: atlases.firstOrNull { it.first.substringAfterLast('/') == key.substringAfterLast('/') }?.second
        }
        val outputs = (json.keys + lua.keys).distinct().map { id ->
            var stage = json[id]?.let { PsychStageParser.parse(it.text) } ?: LuaStageExtractor.defaultStage()
            lua[id]?.let { script ->
                val props = LuaStageExtractor.extract(script.text, stage.objectNames)
                stage = LuaStageExtractor.merge(stage, props)
                if (props.notes.isNotEmpty()) report.section("$id · Lua stage", props.notes)
            }
            if (json[id] == null) report.warn("$id: no stage .json was added; default character positions were used.")
            val converted = PsychToVSliceStage.convert(StageSource(id, stage, atlas = lookup))
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
        val outputs = items.filter { it.kind == "week" }.flatMap { item ->
            val id = stem(item)
            val converted = PsychToVSliceLevel.convert(WeekSource(id, PsychWeekParser.parse(item.text), menu))
            report.stat("$id songs", converted.songIds.size)
            converted.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            converted.report.warnings.forEach { report.warn("$id: $it") }
            listOf(out("data/levels/$id.json", converted.json)) + converted.extra.map { (path, text) -> out(path, text) }
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
