package com.brenninhotools.vconverter.convert

import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.codename.CodenameCharacterParser
import com.brenninhotools.vconverter.codename.CodenameChartParser
import com.brenninhotools.vconverter.codename.CodenameEvents
import com.brenninhotools.vconverter.codename.CodenameMeta
import com.brenninhotools.vconverter.codename.CodenameScope
import com.brenninhotools.vconverter.codename.CodenameScriptContext
import com.brenninhotools.vconverter.codename.CodenameScriptConverter
import com.brenninhotools.vconverter.codename.CodenameScriptTarget
import com.brenninhotools.vconverter.codename.CodenameSong
import com.brenninhotools.vconverter.codename.CodenameStageParser
import com.brenninhotools.vconverter.codename.CodenameToVSliceCharacter
import com.brenninhotools.vconverter.codename.CodenameToVSliceChart
import com.brenninhotools.vconverter.codename.CodenameToVSliceLevel
import com.brenninhotools.vconverter.codename.CodenameToVSliceStage
import com.brenninhotools.vconverter.codename.CodenameWeekParser
import com.brenninhotools.vconverter.codename.EventDefinition
import com.brenninhotools.vconverter.codename.ImageInfo
import com.brenninhotools.vconverter.codename.ImageProbe
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.script.ScriptConverter

private fun segments(path: String) = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }

private fun after(path: String, folder: String): String? {
    val parts = segments(path)
    val index = parts.indexOfLast { it.equals(folder, ignoreCase = true) }
    return if (index >= 0 && index + 1 < parts.size - 1) parts[index + 1] else null
}

abstract class CodenameEngineConverter : FileConverter() {
    override val engine = "Codename Engine"
}

class CodenameChartsConverter : CodenameEngineConverter() {
    override val noun = "chart"

    override fun downloadName() = "charts-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val stem = file.name.substringBeforeLast('.')
        return when {
            CodenameChartParser.isCodenameChart(text) -> {
                CodenameChartParser.parseChart(text)
                val variant = segments(file.path).let { parts -> if (parts.size >= 3 && parts[parts.size - 3].equals("charts", ignoreCase = true)) parts[parts.size - 2] else "" }
                PickedText(file.path, file.name, file.path, text, "chart", stem.lowercase() + if (variant.isNotEmpty()) " · $variant" else "")
            }
            stem.equals("meta", ignoreCase = true) || stem.lowercase().startsWith("meta-") -> {
                CodenameChartParser.parseMeta(text, stem)
                PickedText(file.path, file.name, file.path, text, "metadata", if (stem.contains('-')) stem.substringAfter('-') else "default")
            }
            stem.equals("events", ignoreCase = true) || stem.lowercase().startsWith("events-") -> {
                CodenameChartParser.parseEvents(text)
                PickedText(file.path, file.name, file.path, text, "events", if (stem.contains('-')) stem.substringAfter('-') else "default")
            }
            else -> throw IllegalArgumentException("not a Codename chart, meta.json or events.json")
        }
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "chart" }

    private fun songFolder(item: PickedText): String = after(item.path, "songs") ?: ""

    private fun variantOf(item: PickedText): String = when (item.kind) {
        "chart" -> item.label.substringAfter(" · ", "")
        else -> item.label.takeUnless { it == "default" } ?: ""
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val kept = sortedSetOf<String>()
        var altKind = false
        items.groupBy { songFolder(it) }.forEach { (folder, group) ->
            val baseMeta = group.firstOrNull { it.kind == "metadata" && variantOf(it).isEmpty() }?.let { CodenameChartParser.parseMeta(it.text, folder.ifEmpty { "song" }) }
            val name = folder.ifEmpty { baseMeta?.displayName ?: "song" }
            val id = SongPath.format(name)
            val variants = group.filter { it.kind == "chart" }.map(::variantOf).distinct().sorted()
            variants.forEach { variant ->
                val charts = group.filter { it.kind == "chart" && variantOf(it) == variant }
                    .associate { stem(it).lowercase() to CodenameChartParser.parseChart(it.text) }
                val meta = group.firstOrNull { it.kind == "metadata" && variantOf(it) == variant }?.let { CodenameChartParser.parseMeta(it.text, name) }
                    ?: baseMeta ?: CodenameMeta.default(name)
                val events = (group.firstOrNull { it.kind == "events" && variantOf(it) == variant } ?: group.firstOrNull { it.kind == "events" && variantOf(it).isEmpty() })
                    ?.let { CodenameChartParser.parseEvents(it.text) } ?: emptyList()
                val result = CodenameToVSliceChart.convert(
                    CodenameSong(id, meta, charts, events, variant),
                    com.brenninhotools.vconverter.codename.CodenameChartOptions(instrumental = variant, songVariations = if (variant.isEmpty()) variants.filter { it.isNotEmpty() } else emptyList())
                )
                val suffix = if (variant.isEmpty()) "" else "-$variant"
                outputs += out("data/songs/$id/$id-metadata$suffix.json", VSliceWriter.metadataJson(result.metadata))
                outputs += out("data/songs/$id/$id-chart$suffix.json", VSliceWriter.chartJson(result.chart))
                kept += result.keptEvents.keys
                if ("alt" in result.noteKinds) altKind = true
                result.report.stats.forEach { report.stat("$id$suffix ${it.label}", it.value) }
                result.report.sections.forEach { report.section("$id$suffix · ${it.title}", it.lines) }
                result.report.warnings.forEach { report.warn("$id$suffix: $it") }
            }
            if (group.none { it.kind == "metadata" }) report.warn("$id: no meta.json was added, so the BPM defaults to 100. Add songs/<song>/meta.json for the right timing.")
        }
        kept.filter { it in CodenameEvents.implemented }.forEach { name ->
            val file = CodenameEvents.songEventFile(CodenameEvents.builtins.getValue(name), emptyList())
            outputs += out(file.path, file.content)
        }
        if (altKind) CodenameEvents.altNoteKindFile().let { outputs += out(it.path, it.content) }
        report.section("Audio", listOf("Copy song/Inst.ogg to songs/<song>/Inst.ogg and song/Voices.ogg to songs/<song>/Voices.ogg (the whole-mod tab does this for you)."))
        require(outputs.isNotEmpty()) { "Nothing to convert: add a Codename chart (songs/<song>/charts/<difficulty>.json)." }
        return ConvertedOutput(outputs, report.build())
    }
}

class CodenameCharactersConverter : CodenameEngineConverter() {
    override val noun = "character"

    override fun downloadName() = "characters-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "xml"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val id = file.name.substringBeforeLast('.')
        if (CodenameCharacterParser.isCharacterXml(text)) {
            val character = CodenameCharacterParser.parse(id, text)
            return PickedText(file.path, file.name, file.path, text, "character", "${character.animations.size} anims", CodenameToVSliceCharacter.defaultSlot(character))
        }
        val atlas = SparrowAtlas.parse(text)
        require(atlas.frames.isNotEmpty()) { "not a Codename character XML or a Sparrow spritesheet" }
        return PickedText(file.path, file.name, file.path, text, "atlas", "${atlas.frames.size} frames")
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "character" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val atlases = items.filter { it.kind == "atlas" }.associate { PsychToVSliceCharacter.atlasKey(stem(it)) to SparrowAtlas.parse(it.text) }
        val characters = items.filter { it.kind == "character" }.associate { stem(it) to (it to CodenameCharacterParser.parse(stem(it), it.text)) }
        val report = ReportBuilder()
        val outputs = characters.map { (id, pair) ->
            val (item, character) = pair
            val slot = item.slot ?: CharacterSlot.OPPONENT
            val gameOver = if (slot == CharacterSlot.PLAYER && character.animations.none { it.name == "firstDeath" }) {
                characters[character.gameOverCharacter ?: "$id-dead"]?.second
                    ?: if (character.gameOverCharacter == null) characters["bf-dead"]?.second else null
            } else null
            val result = CodenameToVSliceCharacter.convert(character, slot, atlases, gameOver)
            result.report.stats.firstOrNull { it.label == "Accuracy" }?.let { report.stat(id, it.value) }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
            out("data/characters/$id.json", result.json)
        }
        return ConvertedOutput(outputs, report.build())
    }
}

class CodenameStagesConverter : CodenameEngineConverter() {
    override val noun = "stage"

    override fun downloadName() = "stages-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "xml" || file.extension == "png"

    override fun read(file: ImportedFile): String =
        if (file.extension == "png") file.open().use { input ->
            val header = ByteArray(24)
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
            ImageProbe.pngSize(header)?.let { "${it.first}x${it.second}" } ?: ""
        } else file.readText()

    override fun classify(file: ImportedFile, text: String): PickedText = when (file.extension) {
        "png" -> {
            require(text.contains('x')) { "not a PNG image" }
            PickedText(file.path, file.name, file.path, text, "image", text)
        }
        else -> if (CodenameStageParser.isStageXml(text)) {
            val stage = CodenameStageParser.parse(file.name.substringBeforeLast('.'), text)
            PickedText(file.path, file.name, file.path, text, "stage", "${stage.spriteNames.size} sprites")
        } else {
            val atlas = SparrowAtlas.parse(text)
            require(atlas.frames.isNotEmpty()) { "not a Codename stage XML or a Sparrow spritesheet" }
            PickedText(file.path, file.name, file.path, text, "atlas", "${atlas.frames.size} frames")
        }
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "stage" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val images = items.filter { it.kind == "image" }.associate { stem(it).lowercase() to it.text }
        val atlases = items.filter { it.kind == "atlas" }.associate { stem(it).lowercase() to SparrowAtlas.parse(it.text) }
        val lookup: (String) -> ImageInfo? = { path ->
            val key = path.substringAfterLast('/').lowercase()
            val atlas = atlases[key]?.firstFrame
            val size = images[key]?.split('x')?.mapNotNull { it.toDoubleOrNull() }
            when {
                atlas != null -> ImageInfo(atlas.sourceWidth, atlas.sourceHeight, true)
                size != null && size.size == 2 -> ImageInfo(size[0], size[1], false)
                else -> null
            }
        }
        val report = ReportBuilder()
        val outputs = items.filter { it.kind == "stage" }.map { item ->
            val id = stem(item)
            val result = CodenameToVSliceStage.convert(CodenameStageParser.parse(id, item.text, images = lookup))
            result.report.warnings.forEach { report.warn("$id: $it") }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            out("data/stages/$id.json", result.json)
        }
        report.stat("Stages", outputs.size)
        return ConvertedOutput(outputs, report.build())
    }
}

class CodenameWeeksConverter : CodenameEngineConverter() {
    override val noun = "week"

    override fun downloadName() = "weeks-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension == "xml"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val id = file.name.substringBeforeLast('.')
        return when {
            CodenameWeekParser.isWeekXml(text) -> PickedText(file.path, file.name, file.path, text, "level", "${CodenameWeekParser.parseWeek(id, text).songs.size} songs")
            CodenameWeekParser.isMenuCharacterXml(text) -> PickedText(file.path, file.name, file.path, text, "menu", "Menu character")
            else -> throw IllegalArgumentException("not a Codename week or week character XML")
        }
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "level" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val menu = items.filter { it.kind == "menu" }.associate { stem(it) to CodenameWeekParser.parseMenuCharacter(stem(it), it.text) }
        val report = ReportBuilder()
        val outputs = items.filter { it.kind == "level" }.map { item ->
            val id = stem(item)
            val result = CodenameToVSliceLevel.convert(CodenameWeekParser.parseWeek(id, item.text), menu)
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
            out("data/levels/$id.json", result.json)
        }
        report.stat("Weeks", outputs.size)
        return ConvertedOutput(outputs, report.build())
    }
}

class CodenameScriptsConverter : CodenameEngineConverter() {
    override val noun = "script"

    override fun downloadName() = "scripts-vslice.zip"

    override fun accept(file: ImportedFile) = file.extension in setOf("hx", "hxs", "hsc", "hscript") || file.extension == "xml" || file.extension == "json"

    private fun target(path: String, stem: String): CodenameScriptTarget {
        val parts = segments(path).map { it.lowercase() }
        return when {
            "notes" in parts -> CodenameScriptTarget(CodenameScope.NOTE_TYPE, stem)
            "events" in parts -> CodenameScriptTarget(CodenameScope.EVENT, stem)
            "stages" in parts -> CodenameScriptTarget(CodenameScope.STAGE, stem)
            "characters" in parts -> CodenameScriptTarget(CodenameScope.CHARACTER, stem)
            after(path, "songs") != null && "scripts" in parts -> CodenameScriptTarget(CodenameScope.SONG, SongPath.format(after(path, "songs")!!))
            else -> CodenameScriptTarget(CodenameScope.GLOBAL)
        }
    }

    override fun classify(file: ImportedFile, text: String): PickedText {
        val stem = file.name.substringBeforeLast('.')
        return when (file.extension) {
            "xml" -> {
                require(CodenameStageParser.isStageXml(text)) { "only stage XMLs are used here (for the sprite names)" }
                PickedText(file.path, file.name, file.path, text, "stage", "Stage sprites")
            }
            "json" -> {
                val def = CodenameEvents.parseDefinition(stem, text)
                require(def.params.isNotEmpty() || text.contains("params")) { "not a Codename event definition (data/events/<name>.json)" }
                PickedText(file.path, file.name, file.path, text, "event", "${def.params.size} params")
            }
            else -> {
                val t = target(file.path, stem)
                PickedText(file.path, file.name, file.path, text, "script", t.scope.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() } + if (t.key.isNotEmpty()) " · ${t.key}" else "")
            }
        }
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "script" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val stages = items.filter { it.kind == "stage" }.associate { stem(it) to CodenameStageParser.parse(stem(it), it.text) }
        val events: Map<String, EventDefinition> = items.filter { it.kind == "event" }.associate { stem(it) to CodenameEvents.parseDefinition(stem(it), it.text) }
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val used = HashSet<String>()
        val moduleIds = mutableListOf<String>()
        val context = CodenameScriptContext(events = (CodenameEvents.builtins.values + events.values).toList())
        items.filter { it.kind == "script" }.forEach { item ->
            val stem = stem(item)
            var target = target(item.path, stem)
            if (target.scope == CodenameScope.GLOBAL && stages.containsKey(stem)) target = CodenameScriptTarget(CodenameScope.STAGE, stem)
            val base = when (target.scope) {
                CodenameScope.GLOBAL -> ScriptConverter.keepName(stem)
                CodenameScope.SONG -> ScriptConverter.pascal(target.key) + ScriptConverter.pascal(stem)
                CodenameScope.STAGE -> ScriptConverter.pascal(target.key) + "Stage"
                CodenameScope.CHARACTER -> ScriptConverter.pascal(target.key) + "Character"
                CodenameScope.EVENT -> CodenameEvents.eventId(target.key) + "Handler"
                CodenameScope.NOTE_TYPE -> ScriptConverter.pascal(target.key) + "NoteType"
            }
            var className = base
            var n = 2
            while (!used.add(className)) className = base + n++
            val moduleId = if (target.scope == CodenameScope.GLOBAL) stem else ScriptConverter.kebab(className)
            val sprites = if (target.scope == CodenameScope.STAGE) stages[target.key]?.spriteNames?.toSet() ?: emptySet() else emptySet()
            val result = CodenameScriptConverter(className, moduleId, target, context.copy(stageSprites = sprites)).convert(item.text)
            val fileName = if (target.scope == CodenameScope.GLOBAL) ScriptConverter.keepFileName(stem) else className
            outputs += out("scripts/modules/$fileName.hxc", result.source)
            moduleIds += moduleId
            if (result.issues.isNotEmpty()) report.section(item.name, result.issues.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message })
            if (target.scope == CodenameScope.STAGE && sprites.isEmpty()) report.warn("${item.name}: add the stage XML too so references to stage sprites become getNamedProp() calls.")
        }
        events.values.forEach { def ->
            val file = CodenameEvents.songEventFile(def, moduleIds)
            outputs += out(file.path, file.content)
        }
        report.stat("Scripts", items.count { it.kind == "script" })
        return ConvertedOutput(outputs, report.build(), outputs.singleOrNull()?.content)
    }
}
