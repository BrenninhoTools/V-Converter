package com.brenninhotools.vconverter.convert

import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.psych.PsychEvent
import com.brenninhotools.vconverter.codename.CodenameCharacterParser
import com.brenninhotools.vconverter.codename.CodenameChartParser
import com.brenninhotools.vconverter.codename.CodenameEvents
import com.brenninhotools.vconverter.codename.CodenameMeta
import com.brenninhotools.vconverter.codename.CodenamePsychCharacter
import com.brenninhotools.vconverter.codename.CodenamePsychHelpers
import com.brenninhotools.vconverter.codename.CodenamePsychOptions
import com.brenninhotools.vconverter.codename.CodenamePsychScriptContext
import com.brenninhotools.vconverter.codename.CodenameScope
import com.brenninhotools.vconverter.codename.CodenameScriptTarget
import com.brenninhotools.vconverter.codename.CodenameSong
import com.brenninhotools.vconverter.codename.CodenameStageParser
import com.brenninhotools.vconverter.codename.CodenameToPsychCharacter
import com.brenninhotools.vconverter.codename.CodenameToPsychChart
import com.brenninhotools.vconverter.codename.CodenameToPsychEvents
import com.brenninhotools.vconverter.codename.CodenameToPsychScript
import com.brenninhotools.vconverter.codename.CodenameToPsychStage
import com.brenninhotools.vconverter.codename.CodenameToPsychWeek
import com.brenninhotools.vconverter.codename.CodenameWeekParser
import com.brenninhotools.vconverter.codename.EventDefinition
import com.brenninhotools.vconverter.codename.LuaToCodename
import com.brenninhotools.vconverter.codename.PsychCodenameChartOptions
import com.brenninhotools.vconverter.codename.PsychHxToCodename
import com.brenninhotools.vconverter.codename.PsychSongInfo
import com.brenninhotools.vconverter.codename.PsychToCodenameCharacter
import com.brenninhotools.vconverter.codename.PsychToCodenameChart
import com.brenninhotools.vconverter.codename.PsychToCodenameEvents
import com.brenninhotools.vconverter.codename.PsychToCodenameStage
import com.brenninhotools.vconverter.codename.PsychToCodenameWeek
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.script.ScriptIssue
import com.brenninhotools.vconverter.stage.LuaStageExtractor
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.week.PsychWeekParser

private fun pathParts(path: String) = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }

private fun folderAfter(path: String, folder: String): String? {
    val parts = pathParts(path)
    val index = parts.indexOfLast { it.equals(folder, ignoreCase = true) }
    return if (index >= 0 && index + 1 < parts.size - 1) parts[index + 1] else null
}

private fun issueLines(issues: List<ScriptIssue>) = issues.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message }

abstract class CodenameToPsychConverter : FileConverter() {
    override val engine = "Codename Engine"
}

abstract class PsychToCodenameConverter : FileConverter() {
    override val engine = "Psych Engine"
}

class CodenameToPsychChartsConverter : CodenameToPsychConverter() {
    override val noun = "chart"

    override fun downloadName() = "charts-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val stem = file.name.substringBeforeLast('.')
        return when {
            CodenameChartParser.isCodenameChart(text) -> {
                CodenameChartParser.parseChart(text)
                val variant = pathParts(file.path).let { parts -> if (parts.size >= 3 && parts[parts.size - 3].equals("charts", ignoreCase = true)) parts[parts.size - 2] else "" }
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
            file.path.replace('\\', '/').contains("data/events/", ignoreCase = true) -> {
                CodenameEvents.parseDefinition(stem, text)
                PickedText(file.path, file.name, file.path, text, "definition", "Event params")
            }
            else -> throw IllegalArgumentException("not a Codename chart, meta.json, events.json or data/events/*.json")
        }
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "chart" }

    private fun variantOf(item: PickedText): String = when (item.kind) {
        "chart" -> item.label.substringAfter(" · ", "")
        else -> item.label.takeUnless { it == "default" } ?: ""
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val definitions: Map<String, EventDefinition> = items.filter { it.kind == "definition" }.associate { stem(it) to CodenameEvents.parseDefinition(stem(it), it.text) }
        val customEvents = sortedMapOf<String, Int>(String.CASE_INSENSITIVE_ORDER)
        val noteTypes = sortedSetOf<String>()
        items.filter { it.kind != "definition" }.groupBy { folderAfter(it.path, "songs") ?: "" }.forEach { (folder, group) ->
            val baseMeta = group.firstOrNull { it.kind == "metadata" && variantOf(it).isEmpty() }?.let { CodenameChartParser.parseMeta(it.text, folder.ifEmpty { "song" }) }
            val name = folder.ifEmpty { baseMeta?.displayName ?: "song" }
            group.filter { it.kind == "chart" }.map(::variantOf).distinct().sorted().forEach { variant ->
                val charts = group.filter { it.kind == "chart" && variantOf(it) == variant }.associate { stem(it).lowercase() to CodenameChartParser.parseChart(it.text) }
                val meta = group.firstOrNull { it.kind == "metadata" && variantOf(it) == variant }?.let { CodenameChartParser.parseMeta(it.text, name) } ?: baseMeta ?: CodenameMeta.default(name)
                val events = (group.firstOrNull { it.kind == "events" && variantOf(it) == variant } ?: group.firstOrNull { it.kind == "events" && variantOf(it).isEmpty() })
                    ?.let { CodenameChartParser.parseEvents(it.text) } ?: emptyList()
                val result = CodenameToPsychChart.convert(CodenameSong(SongPath.format(name), meta, charts, events, variant), CodenamePsychOptions(eventDefinitions = definitions))
                result.files.forEach { outputs += out(it.path, it.json) }
                result.customEvents.forEach { (n, c) -> customEvents[n] = maxOf(customEvents[n] ?: 0, c) }
                noteTypes += result.noteTypes
                val label = result.songId
                result.report.stats.forEach { report.stat("$label ${it.label}", it.value) }
                result.report.sections.forEach { report.section("$label · ${it.title}", it.lines) }
                result.report.warnings.forEach { report.warn("$label: $it") }
            }
            if (group.none { it.kind == "metadata" }) report.warn("${folder.ifEmpty { name }}: no meta.json was added, so the BPM defaults to 100. Add songs/<song>/meta.json for the right timing.")
        }
        var compat = false
        val scripted = mutableListOf<String>()
        customEvents.keys.forEach { name ->
            val builtin = CodenameToPsychEvents.script(name)
            outputs += out("custom_events/$name.txt", (builtin?.description ?: CodenameToPsychEvents.description(name, definitions[name]?.params?.map { it.title } ?: emptyList())) + "\n")
            if (builtin != null) {
                outputs += out("custom_events/$name.hx", builtin.script)
                scripted += name
                if (name in CodenameToPsychEvents.needsCompat) compat = true
            }
        }
        if (CodenameToPsychChart.HIDDEN_GF in noteTypes) CodenameToPsychEvents.noteTypeScript(CodenameToPsychChart.HIDDEN_GF)?.let { outputs += out("custom_notetypes/${CodenameToPsychChart.HIDDEN_GF}.hx", it) }
        if (compat) outputs += out(CodenamePsychHelpers.COMPAT_SCRIPT, CodenamePsychHelpers.compatScript())
        if (scripted.isNotEmpty()) report.section("Events", scripted.map { "$it → custom_events/$it.hx" })
        val unscripted = customEvents.keys.filter { it !in scripted && it !in CodenameToPsychEvents.scripted }
        if (unscripted.isNotEmpty()) report.warn("Mod events (${unscripted.joinToString()}) are kept in the charts; convert their data/events/*.hx in the Scripts tab so Psych can run them.")
        report.section("Audio", listOf("Copy songs/<song>/song/Inst.ogg and Voices.ogg to songs/<song>/ in the Psych mod (the whole-mod tab does this for you)."))
        require(outputs.isNotEmpty()) { "Nothing to convert: add a Codename chart (songs/<song>/charts/<difficulty>.json)." }
        return ConvertedOutput(outputs, report.build())
    }
}

class CodenameToPsychCharactersConverter : CodenameToPsychConverter() {
    override val noun = "character"

    override fun downloadName() = "characters-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "xml"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val id = file.name.substringBeforeLast('.')
        if (CodenameCharacterParser.isCharacterXml(text)) {
            val character = CodenameCharacterParser.parse(id, text)
            return PickedText(file.path, file.name, file.path, text, "character", "${character.animations.size} anims", CodenamePsychCharacter.defaultSlot(character))
        }
        val atlas = SparrowAtlas.parse(text)
        require(atlas.frames.isNotEmpty()) { "not a Codename character XML or a Sparrow spritesheet" }
        return PickedText(file.path, file.name, file.path, text, "atlas", "${atlas.frames.size} frames")
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "character" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val atlases = items.filter { it.kind == "atlas" }.associate { PsychToVSliceCharacter.atlasKey(stem(it)) to SparrowAtlas.parse(it.text) }
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        items.filter { it.kind == "character" }.forEach { item ->
            val id = stem(item)
            val character = CodenameCharacterParser.parse(id, item.text)
            val slot = item.slot ?: CharacterSlot.OPPONENT
            val result = CodenameToPsychCharacter.convert(character, slot, atlases)
            result.files.forEach { outputs += out(it.path, it.text) }
            if (!result.hooks.isEmpty) {
                val script = CodenameToPsychScript(CodenameScriptTarget(CodenameScope.CHARACTER, id), CodenamePsychScriptContext(characterId = id, hooks = result.hooks)).convert("")
                outputs += out("characters/$id.hx", script.source)
            }
            result.report.stats.forEach { report.stat("$id ${it.label}", it.value) }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
            if (result.copies.isNotEmpty()) report.section("$id · Images", result.copies.map { "Copy ${it.from} to ${it.to}" })
        }
        return ConvertedOutput(outputs, report.build())
    }
}

class CodenameToPsychStagesConverter : CodenameToPsychConverter() {
    override val noun = "stage"

    override fun downloadName() = "stages-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "xml"

    override fun classify(file: ImportedFile, text: String): PickedText {
        require(CodenameStageParser.isStageXml(text)) { "not a Codename stage XML" }
        val stage = CodenameStageParser.parse(file.name.substringBeforeLast('.'), text, includeLowMemory = true)
        return PickedText(file.path, file.name, file.path, text, "stage", "${stage.spriteNames.size} sprites")
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        items.forEach { item ->
            val id = stem(item)
            val stage = CodenameStageParser.parse(id, item.text, includeLowMemory = true)
            val result = CodenameToPsychStage.convert(stage)
            result.files.forEach { outputs += out(it.path, it.text) }
            if (!result.hooks.isEmpty) {
                val script = CodenameToPsychScript(CodenameScriptTarget(CodenameScope.STAGE, id), CodenamePsychScriptContext(stageSprites = stage.spriteNames.toSet(), hooks = result.hooks)).convert("")
                outputs += out("stages/$id.hx", script.source)
                if (script.usesCompat) outputs += out(CodenamePsychHelpers.COMPAT_SCRIPT, CodenamePsychHelpers.compatScript())
            }
            result.report.warnings.forEach { report.warn("$id: $it") }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            if (result.extensions.isNotEmpty()) report.warn("$id loads ${result.extensions.joinToString { "$it.hx" }}; convert those in the Scripts tab (they go to stages/extensions/).")
        }
        report.section("Stage scripts", listOf("If the stage has data/stages/<stage>.hx, convert it in the Scripts tab together with the stage XML; it is merged with the generated stages/<stage>.hx."))
        report.stat("Stages", items.size)
        return ConvertedOutput(outputs.distinctBy { it.relativePath }, report.build())
    }
}

class CodenameToPsychWeeksConverter : CodenameToPsychConverter() {
    override val noun = "week"

    override fun downloadName() = "weeks-psych.zip"

    override fun accept(file: ImportedFile) = file.extension == "xml" || (file.extension == "json" && file.name.equals("meta.json", ignoreCase = true))

    override fun classify(file: ImportedFile, text: String): PickedText {
        val id = file.name.substringBeforeLast('.')
        return when {
            file.extension == "json" -> {
                val folder = folderAfter(file.path, "songs") ?: throw IllegalArgumentException("meta.json must sit in songs/<song>/")
                CodenameChartParser.parseMeta(text, folder)
                PickedText(file.path, file.name, file.path, text, "meta", folder)
            }
            CodenameWeekParser.isWeekXml(text) -> PickedText(file.path, file.name, file.path, text, "week", "${CodenameWeekParser.parseWeek(id, text).songs.size} songs")
            CodenameWeekParser.isMenuCharacterXml(text) -> PickedText(file.path, file.name, file.path, text, "menu", "Menu character")
            else -> throw IllegalArgumentException("not a Codename week, week character XML or song meta.json")
        }
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "week" || it.kind == "menu" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val metas = items.filter { it.kind == "meta" }.associate { it.label.lowercase() to CodenameChartParser.parseMeta(it.text, it.label) }
        val info: (String) -> PsychSongInfo? = { song ->
            metas[song.lowercase()]?.let { m -> PsychSongInfo(m.icon.ifBlank { "face" }, if (m.color.isNotBlank()) CodenamePsychCharacter.rgb(m.color) else listOf(146, 113, 253)) }
        }
        val names: (String) -> String = { song -> metas[song.lowercase()]?.displayName?.takeIf { it.isNotBlank() } ?: song }
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val order = mutableListOf<String>()
        items.filter { it.kind == "week" }.forEach { item ->
            val id = stem(item)
            val result = CodenameToPsychWeek.convert(CodenameWeekParser.parseWeek(id, item.text), info, names)
            result.files.forEach { outputs += out(it.path, it.text) }
            order += id
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
            if (result.copies.isNotEmpty()) report.section("$id · Images", result.copies.map { "Copy ${it.from} to ${it.to}" })
        }
        items.filter { it.kind == "menu" }.forEach { item ->
            val id = stem(item)
            val result = CodenameToPsychWeek.menuCharacter(CodenameWeekParser.parseMenuCharacter(id, item.text), id)
            result.files.forEach { outputs += out(it.path, it.text) }
            result.report.warnings.forEach { report.warn("$id: $it") }
            if (result.copies.isNotEmpty()) report.section("$id · Images", result.copies.map { "Copy ${it.from} to ${it.to}" })
        }
        if (order.isNotEmpty()) outputs += out("weeks/weekList.txt", order.joinToString("\n") + "\n")
        if (metas.isEmpty() && order.isNotEmpty()) report.warn("Add the songs' meta.json files so freeplay icons and colors match.")
        report.stat("Weeks", order.size)
        return ConvertedOutput(outputs, report.build())
    }
}

class CodenameToPsychScriptsConverter : CodenameToPsychConverter() {
    override val noun = "script"

    override fun downloadName() = "scripts-psych.zip"

    override fun accept(file: ImportedFile) = file.extension in setOf("hx", "hxs", "hsc", "hscript") || file.extension == "xml" || file.extension == "json"

    private fun target(path: String, stem: String): CodenameScriptTarget {
        val parts = pathParts(path).map { it.lowercase() }
        return when {
            "notes" in parts -> CodenameScriptTarget(CodenameScope.NOTE_TYPE, stem)
            "events" in parts -> CodenameScriptTarget(CodenameScope.EVENT, stem)
            "stages" in parts -> CodenameScriptTarget(CodenameScope.STAGE, stem)
            "characters" in parts -> CodenameScriptTarget(CodenameScope.CHARACTER, stem)
            folderAfter(path, "songs") != null && "scripts" in parts -> CodenameScriptTarget(CodenameScope.SONG, SongPath.format(folderAfter(path, "songs")!!))
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
        val stages = items.filter { it.kind == "stage" }.associate { stem(it) to CodenameStageParser.parse(stem(it), it.text, includeLowMemory = true) }
        val events = items.filter { it.kind == "event" }.associate { stem(it) to CodenameEvents.parseDefinition(stem(it), it.text).params.size }
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        var compat = false
        items.filter { it.kind == "script" }.forEach { item ->
            val stem = stem(item)
            var target = target(item.path, stem)
            if (target.scope == CodenameScope.GLOBAL && stages.containsKey(stem)) target = CodenameScriptTarget(CodenameScope.STAGE, stem)
            val stage = stages[target.key]
            val hooks = if (target.scope == CodenameScope.STAGE && stage != null) CodenameToPsychStage.convert(stage).hooks else com.brenninhotools.vconverter.codename.PsychHooks()
            val context = CodenamePsychScriptContext(
                stageSprites = if (target.scope == CodenameScope.STAGE) stage?.spriteNames?.toSet() ?: emptySet() else emptySet(),
                eventParamCounts = events,
                characterId = if (target.scope == CodenameScope.CHARACTER) target.key else "",
                eventName = if (target.scope == CodenameScope.EVENT) target.key else "",
                hooks = hooks
            )
            val result = CodenameToPsychScript(target, context).convert(item.text)
            val path = when (target.scope) {
                CodenameScope.GLOBAL -> "scripts/$stem.hx"
                CodenameScope.SONG -> "data/${target.key}/$stem.hx"
                CodenameScope.STAGE -> "stages/${target.key}.hx"
                CodenameScope.CHARACTER -> "characters/${CodenameToPsychChart.psychCharacter(target.key)}.hx"
                CodenameScope.EVENT -> if (CodenameToPsychEvents.script(target.key) != null) "scripts/${CodenameEvents.eventId(target.key)}Event.hx" else "custom_events/${target.key}.hx"
                CodenameScope.NOTE_TYPE -> "custom_notetypes/${CodenameToPsychChart.noteTypeFor(target.key)}.hx"
            }
            outputs += out(path, result.source)
            if (target.scope == CodenameScope.EVENT) {
                val builtin = CodenameToPsychEvents.script(target.key)
                outputs += out("custom_events/${target.key}.txt", (builtin?.description ?: CodenameToPsychEvents.description(target.key)) + "\n")
                if (builtin != null) {
                    outputs += out("custom_events/${target.key}.hx", builtin.script)
                    if (target.key in CodenameToPsychEvents.needsCompat) compat = true
                }
            }
            if (result.usesCompat) compat = true
            if (result.issues.isNotEmpty()) report.section(item.name, issueLines(result.issues))
            if (target.scope == CodenameScope.STAGE && stage == null) report.warn("${item.name}: add the stage XML too so stage sprites are fetched with getVar().")
        }
        if (compat) outputs += out(CodenamePsychHelpers.COMPAT_SCRIPT, CodenamePsychHelpers.compatScript())
        report.stat("Scripts", items.count { it.kind == "script" })
        return ConvertedOutput(outputs, report.build(), outputs.singleOrNull()?.content)
    }
}

class PsychToCodenameChartsConverter : PsychToCodenameConverter() {
    override val noun = "chart"

    override fun downloadName() = "charts-codename.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

    private fun songFolder(path: String, stem: String): String =
        folderAfter(path, "data") ?: pathParts(path).dropLast(1).lastOrNull()?.takeIf { stem.lowercase().startsWith(it.lowercase()) } ?: stem.substringBefore('-')

    override fun classify(file: ImportedFile, text: String): PickedText {
        val stem = file.name.substringBeforeLast('.')
        val song = PsychChartParser.parse(text)
        val folder = songFolder(file.path, stem).lowercase()
        if (stem.equals("events", ignoreCase = true)) return PickedText(file.path, file.name, file.path, text, "events", "$folder · ${song.events.size} events")
        require(song.sections.isNotEmpty()) { "not a Psych chart" }
        val difficulty = when {
            stem.equals(folder, ignoreCase = true) -> "normal"
            stem.lowercase().startsWith("$folder-") -> stem.substring(folder.length + 1).lowercase()
            else -> "normal"
        }
        return PickedText(file.path, file.name, file.path, text, "chart", "$folder · $difficulty")
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "chart" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val events = sortedSetOf<String>(String.CASE_INSENSITIVE_ORDER)
        val types = sortedSetOf<String>()
        items.groupBy { it.label.substringBefore(" · ") }.forEach { (folder, group) ->
            val sources = group.filter { it.kind == "chart" }.map { ChartSource(it.label.substringAfter(" · "), PsychChartParser.parse(it.text)) }
            if (sources.isEmpty()) return@forEach
            val extra: List<PsychEvent> = group.filter { it.kind == "events" }.flatMap { PsychChartParser.parseEventsOnly(it.text) }
            val id = SongPath.format(folder).ifEmpty { folder }
            val result = PsychToCodenameChart.convert(sources, extra, PsychCodenameChartOptions(songFolder = id))
            result.charts.forEach { (difficulty, json) -> outputs += out("songs/$id/charts/$difficulty.json", json) }
            outputs += out("songs/$id/meta.json", result.meta)
            events += result.events.keys
            types += result.noteTypes
            result.report.stats.forEach { report.stat("$id ${it.label}", it.value) }
            result.report.sections.filter { it.title != "Difficulties" }.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
        }
        events.forEach { name ->
            PsychToCodenameEvents.script(name)?.let { script ->
                outputs += out("data/events/$name.hx", script)
                outputs += out("data/events/$name.json", PsychToCodenameEvents.definition(name))
            }
        }
        types.forEach { name -> PsychToCodenameEvents.noteTypeScript(name)?.let { outputs += out("data/notes/$name.hx", it) } }
        report.section("Audio", listOf("Copy songs/<song>/Inst.ogg and the Voices files to songs/<song>/song/ in the Codename mod (the whole-mod tab does this for you)."))
        require(outputs.isNotEmpty()) { "Nothing to convert: add a Psych chart (data/<song>/<song>-<difficulty>.json)." }
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychToCodenameCharactersConverter : PsychToCodenameConverter() {
    override val noun = "character"

    override fun downloadName() = "characters-codename.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val id = file.name.substringBeforeLast('.')
        val character = PsychCharacterParser.parse(text)
        return PickedText(file.path, file.name, file.path, text, "character", "${character.animations.size} anims", PsychToCodenameCharacter.defaultSlot(id, character))
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        items.forEach { item ->
            val id = stem(item)
            val result = PsychToCodenameCharacter.convert(id, PsychCharacterParser.parse(item.text), item.slot ?: CharacterSlot.OPPONENT)
            result.files.forEach { outputs += out(it.path, it.text) }
            result.report.stats.forEach { report.stat("$id ${it.label}", it.value) }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
        }
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychToCodenameStagesConverter : PsychToCodenameConverter() {
    override val noun = "stage"

    override fun downloadName() = "stages-codename.zip"

    override fun accept(file: ImportedFile) = file.extension == "json" || file.extension == "lua"

    override fun classify(file: ImportedFile, text: String): PickedText {
        if (file.extension == "lua") return PickedText(file.path, file.name, file.path, text, "lua", "Stage Lua")
        val stage = PsychStageParser.parse(text)
        return PickedText(file.path, file.name, file.path, text, "stage", "${stage.objects?.size ?: 0} objects")
    }


    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val json = items.filter { it.kind == "stage" }.associateBy { stem(it) }
        val lua = items.filter { it.kind == "lua" }.associateBy { stem(it) }
        (json.keys + lua.keys).distinct().forEach { id ->
            var stage = json[id]?.let { PsychStageParser.parse(it.text) } ?: LuaStageExtractor.defaultStage()
            val notes = mutableListOf<String>()
            var skip: Set<Int> = emptySet()
            lua[id]?.let { script ->
                val props = LuaStageExtractor.extract(script.text, stage.objectNames)
                stage = LuaStageExtractor.merge(stage, props)
                skip = props.removedLines
                if (!props.isEmpty) notes += "${props.behind.size + props.front.size} sprite(s) created in stages/$id.lua were moved into the stage XML."
                notes += props.notes
            }
            if (json[id] == null) report.warn("$id: no stages/$id.json was added; default character positions were used.")
            val result = PsychToCodenameStage.convert(id, stage, notes)
            result.files.forEach { outputs += out(it.path, it.text) }
            val names = stage.objects?.map { it.name }?.toSet() ?: emptySet()
            val script = lua[id]
            if (script != null) {
                val converted = LuaToCodename(CodenameScope.STAGE, names, result.hooks.callbacks, skip).convert(script.text)
                outputs += out("data/stages/$id.hx", converted.source)
                if (converted.issues.isNotEmpty()) report.section("$id · stages/$id.lua", issueLines(converted.issues))
            } else if (result.hooks.callbacks.isNotEmpty()) {
                outputs += out("data/stages/$id.hx", result.hooks.callbacks.entries.joinToString("\n") { (name, lines) ->
                    "function ${if (name == "postCreate" || name == "create") "$name()" else "$name(event)"}\n{\n" + lines.joinToString("") { "  $it\n" } + "}\n"
                })
            }
            result.report.warnings.forEach { report.warn("$id: $it") }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
        }
        report.stat("Stages", (json.keys + lua.keys).distinct().size)
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychToCodenameWeeksConverter : PsychToCodenameConverter() {
    override val noun = "week"

    override fun downloadName() = "weeks-codename.zip"

    override fun accept(file: ImportedFile) = file.extension == "json"

    override fun classify(file: ImportedFile, text: String): PickedText {
        val menu = pathParts(file.path).any { it.equals("menucharacters", ignoreCase = true) } || (text.contains("idle_anim") && !text.contains("weekCharacters"))
        return if (menu) {
            PsychWeekParser.parseMenuCharacter(text)
            PickedText(file.path, file.name, file.path, text, "menu", "Menu character")
        } else {
            val week = PsychWeekParser.parse(text)
            PickedText(file.path, file.name, file.path, text, "week", "${week.songs.size} songs")
        }
    }


    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val order = mutableListOf<String>()
        val freeplay = mutableListOf<String>()
        items.filter { it.kind == "week" }.forEach { item ->
            val id = stem(item)
            val week = PsychWeekParser.parse(item.text)
            val result = PsychToCodenameWeek.convert(id, week)
            result.files.forEach { outputs += out(it.path, it.text) }
            if (result.files.isNotEmpty()) order += id
            if (!week.hideFreeplay) freeplay += week.songs.map { SongPath.format(it.name) }
            result.report.sections.forEach { report.section("$id · ${it.title}", it.lines) }
            result.report.warnings.forEach { report.warn("$id: $it") }
        }
        items.filter { it.kind == "menu" }.forEach { item ->
            val id = stem(item)
            val result = PsychToCodenameWeek.menuCharacter(id, PsychWeekParser.parseMenuCharacter(item.text))
            result.files.forEach { outputs += out(it.path, it.text) }
        }
        if (order.isNotEmpty()) outputs += out("data/weeks/weeks.txt", order.joinToString("\n") + "\n")
        if (freeplay.isNotEmpty()) outputs += out("data/config/freeplaySonglist.txt", freeplay.distinct().joinToString("\n") + "\n")
        report.stat("Weeks", order.size)
        return ConvertedOutput(outputs, report.build())
    }
}

class PsychToCodenameScriptsConverter : PsychToCodenameConverter() {
    override val noun = "script"

    override fun downloadName() = "scripts-codename.zip"

    override fun accept(file: ImportedFile) = file.extension in setOf("lua", "hx", "hxs", "hscript") || (file.extension == "json" && pathParts(file.path).any { it.equals("stages", true) })

    private fun scope(path: String): Pair<CodenameScope, String> {
        val parts = pathParts(path).map { it.lowercase() }
        return when {
            "custom_notetypes" in parts -> CodenameScope.NOTE_TYPE to ""
            "custom_events" in parts -> CodenameScope.EVENT to ""
            "stages" in parts -> CodenameScope.STAGE to ""
            "characters" in parts -> CodenameScope.CHARACTER to ""
            folderAfter(path, "data") != null -> CodenameScope.SONG to folderAfter(path, "data")!!.lowercase()
            else -> CodenameScope.GLOBAL to ""
        }
    }

    override fun classify(file: ImportedFile, text: String): PickedText {
        if (file.extension == "json") {
            PsychStageParser.parse(text)
            return PickedText(file.path, file.name, file.path, text, "stage", "Stage objects")
        }
        val (scope, key) = scope(file.path)
        val label = scope.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() } + if (key.isNotEmpty()) " · $key" else ""
        return PickedText(file.path, file.name, file.path, text, "script", "$label · ${file.extension}")
    }

    override fun canConvert(items: List<PickedText>): Boolean = items.any { it.kind == "script" }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        val outputs = mutableListOf<OutputFile>()
        val stageObjects = items.filter { it.kind == "stage" }.associate { stem(it) to (PsychStageParser.parse(it.text).objects?.map { o -> o.name }?.toSet() ?: emptySet()) }
        val scripts = items.filter { it.kind == "script" }
        val tags = scripts.filter { it.name.endsWith(".lua", true) }.flatMap { s -> Regex("""\b(makeLuaSprite|makeAnimatedLuaSprite|makeLuaText)\s*\(\s*["']([^"']+)["']""").findAll(s.text).map { it.groupValues[2] }.toList() }.toSet()
        scripts.forEach { item ->
            val stem = stem(item)
            val (scope, key) = scope(item.path)
            val lua = item.name.endsWith(".lua", true)
            val hooks: Map<String, List<String>> = emptyMap()
            var skip: Set<Int> = emptySet()
            var known = tags
            if (scope == CodenameScope.STAGE && lua) {
                val props = LuaStageExtractor.extract(item.text, stageObjects[stem] ?: emptySet())
                if (!props.isEmpty && stageObjects.containsKey(stem)) skip = props.removedLines
                known = tags + (stageObjects[stem] ?: emptySet())
            }
            val result = if (lua) LuaToCodename(scope, known, hooks, skip).convert(item.text) else PsychHxToCodename(scope, hooks).convert(item.text)
            val path = when (scope) {
                CodenameScope.GLOBAL -> "songs/$stem.hx"
                CodenameScope.SONG -> "songs/$key/scripts/$stem.hx"
                CodenameScope.STAGE -> "data/stages/$stem.hx"
                CodenameScope.CHARACTER -> "songs/character-$stem.hx"
                CodenameScope.EVENT -> "data/events/$stem.hx"
                CodenameScope.NOTE_TYPE -> "data/notes/${PsychToCodenameChart.noteTypeFor(stem)}.hx"
            }
            if (result.source.isBlank()) {
                report.warn("${item.name} could not be parsed.")
            } else outputs += out(path, result.source)
            if (scope == CodenameScope.EVENT) outputs += out("data/events/$stem.json", PsychToCodenameEvents.definition(stem))
            if (scope == CodenameScope.CHARACTER) report.section(item.name, listOf("Psych character scripts run like song scripts, so this became $path (it runs in every song; move it to songs/<song>/scripts/ to limit it)."))
            if (scope == CodenameScope.STAGE && skip.isNotEmpty()) report.section(item.name, listOf("Sprites made in this Lua are in the converted stage XML, so their lines were left out (convert the stage in the Stages tab)."))
            if (result.issues.isNotEmpty()) report.section(item.name, issueLines(result.issues))
        }
        report.stat("Scripts", scripts.size)
        return ConvertedOutput(outputs, report.build(), outputs.singleOrNull()?.content)
    }
}
