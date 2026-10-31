package com.brenninhotools.vconverter.mod

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacter
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychNoteStyles
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.psych.PsychSong
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.AnimateAtlas
import com.brenninhotools.vconverter.core.AnimateAtlases
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptSource
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.stage.LuaStageExtractor
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeekParser
import com.brenninhotools.vconverter.week.WeekSource
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.core.sortedMutableSet

data class ModInfo(
    val title: String,
    val description: String,
    val author: String,
    val version: String = "1.0.0"
)

data class ModProgress(val done: Int, val total: Int, val label: String)

data class ModConversionResult(val filesWritten: Int, val report: Report)

class ModConverter(
    private val files: ModFiles,
    private val scan: ModScan,
    private val writer: ModWriter,
    private val onProgress: (ModProgress) -> Unit = {}
) {
    private val report = ReportBuilder()
    private val pathIndex: Map<String, String> = files.paths.filter { it.startsWith(scan.root) }.associateBy { (scan.root + scan.relative(it)).lowercase() }
    private var written = 0
    private var keptPsychEvents = 0
    private val keptEventNames = sortedMutableSet<String>()
    private val stageSkipLines = HashMap<String, Set<Int>>()
    private var done = 0
    private val total = scan.songs.size + scan.characters.size + scan.stages.size + scan.weeks.size + scan.assets.size + scan.scripts.size + 2

    fun convert(info: ModInfo): ModConversionResult {
        val characters = parseCharacters()
        val charts = parseCharts()
        val stageIds = convertStages()
        convertSongs(charts, characters, stageIds)
        convertCharacters(characters, charts)
        convertWeeks(charts.keys)
        convertScripts(info, charts)
        copyAssets()
        writeMeta(info)

        report.stat("Songs", charts.size)
        report.stat("Characters", characters.size)
        report.stat("Stages", stageIds.size)
        report.stat("Weeks", scan.weeks.size)
        report.stat("Assets", scan.assets.size)
        report.stat("Files", written)


        if (scan.placeholders.isNotEmpty()) {
            report.section("Empty placeholder folders removed", scan.placeholders.map { (dir, file) -> "$dir/ (only ${file.substringAfterLast('/')})" })
        }
        if (scan.ignored.isNotEmpty()) {
            report.section("Other files skipped", limited(scan.ignored.map(scan::relative)))
        }
        if (scan.otherRoots.isNotEmpty()) {
            report.warn("The archive has more than one mod. Only \"${scan.root.ifEmpty { "/" }}\" was converted; also found: ${scan.otherRoots.joinToString()}.")
        }
        if (scan.weekList != null) {
            report.warn("Psych's weekList.txt order is not used by V-Slice; levels are listed in their own order.")
        }

        return ModConversionResult(written, report.build())
    }

    private fun parseCharacters(): Map<String, PsychCharacter> {
        val result = LinkedHashMap<String, PsychCharacter>()
        scan.characters.forEach { path ->
            val id = path.substringAfterLast('/').substringBeforeLast('.')
            try {
                result[id] = PsychCharacterParser.parse(files.readText(path))
            } catch (e: Exception) {
                report.warn("Character \"$id\" could not be read: ${e.message}")
            }
        }
        return result
    }

    private fun parseCharts(): Map<String, Pair<List<ChartSource>, PsychSong>> {
        val result = LinkedHashMap<String, Pair<List<ChartSource>, PsychSong>>()
        scan.songs.forEach { song ->
            val sources = song.charts.mapNotNull { (difficulty, path) ->
                try {
                    ChartSource(difficulty, PsychChartParser.parse(files.readText(path)))
                } catch (e: Exception) {
                    report.warn("Chart ${scan.relative(path)} could not be read: ${e.message}")
                    null
                }
            }
            if (sources.isNotEmpty()) {
                val main = sources.firstOrNull { it.difficulty == "normal" } ?: sources.first()
                result[song.id] = sources to main.song
            }
        }
        return result
    }

    private fun convertStages(): Set<String> {
        val ids = LinkedHashSet<String>()
        val luaStages = scan.scripts.filter {
            val rel = scan.relative(it).lowercase()
            rel.startsWith("stages/") && rel.endsWith(".lua") && rel.count { c -> c == '/' } == 1
        }.associateBy { it.substringAfterLast('/').substringBeforeLast('.') }
        val jsonStages = scan.stages.associateBy { it.substringAfterLast('/').substringBeforeLast('.') }
        (jsonStages.keys + luaStages.keys).distinct().forEach { id ->
            val path = jsonStages[id]
            step("Stage $id")
            try {
                var stage = if (path != null) PsychStageParser.parse(files.readText(path)) else LuaStageExtractor.defaultStage()
                luaStages[id]?.let { lua ->
                    val props = LuaStageExtractor.extract(files.readText(lua), stage.objectNames)
                    stage = LuaStageExtractor.merge(stage, props)
                    stageSkipLines[id] = props.removedLines
                    if (props.notes.isNotEmpty()) report.section("Stage $id · stages/$id.lua", props.notes)
                }
                if (path == null) report.warn("Stage $id has no stages/$id.json; default character positions were used.")
                if (stage.isPixelStage) pixelStages += id
                val result = PsychToVSliceStage.convert(StageSource(id, stage, atlas = ::stageAtlas))
                writer.writeText("data/stages/$id.json", result.json)
                written++
                ids += id
                result.report.warnings.forEach { report.warn("Stage $id: $it") }
                result.report.sections.filter { it.title == "Notes" || it.title == "Skipped objects" }.forEach { section ->
                    val lines = section.lines.filterNot { it.startsWith("Character positions use") || it.startsWith("Layers keep") }
                    if (lines.isNotEmpty()) report.section("Stage $id · ${section.title}", lines)
                }
            } catch (e: Exception) {
                report.warn("Stage \"$id\" was not converted: ${e.message}")
            }
        }
        return ids
    }

    private val atlasCache = HashMap<String, SparrowAtlas?>()
    private val pixelStages = HashSet<String>()
    private val noteStyles = HashMap<String, String?>()

    private fun sheet(image: String): SparrowAtlas? {
        val path = pathIndex["${scan.root}images/${image.trim().removePrefix("images/").removeSuffix(".png")}.xml".lowercase()] ?: return null
        return runCatching { SparrowAtlas.parse(files.readText(path)) }.getOrNull()
    }

    private fun noteStyleFor(skin: String, splash: String = "", strumline: Boolean = true, label: String = ""): String? {
        if (!PsychNoteStyles.isCustom(skin)) return null
        val key = skin.trim().lowercase() + "|" + splash.trim().lowercase() + "|" + strumline
        return noteStyles.getOrPut(key) {
            val notes = sheet(skin)
            if (notes == null) report.warn("Note skin images/$skin.xml was not found; ${label.ifBlank { "its songs" }} keep the default note style.")
            val style = notes?.let { PsychNoteStyles.build(skin, it, splash.takeIf { s -> s.isNotBlank() }, splash.takeIf { s -> s.isNotBlank() }?.let(::sheet), strumline) }
            if (notes != null && style == null) report.warn("images/$skin.xml has no Psych note animations (purple0…), so it was not turned into a note style.")
            style?.let {
                val id = if (strumline) it.id else it.id + "-notes"
                writer.writeText("data/notestyles/$id.json", it.json)
                written++
                report.section("Note style $id", listOf("Made from images/$skin.") + it.notes)
                id
            }
        }
    }

    private fun animateAtlas(image: String): AnimateAtlas? {
        val folder = "${scan.root}images/${image.trim().removeSuffix("/")}/".lowercase()
        val animation = pathIndex["${folder}animation.json"] ?: return null
        val spritemaps = pathIndex.filterKeys { it.startsWith(folder) && !it.substring(folder.length).contains('/') && AnimateAtlases.isAtlasFile(it.substringAfterLast('/')) }.values.filter { it != animation }.sortedBy { it.lowercase() }
        return runCatching { AnimateAtlas.parse(files.readText(animation), spritemaps.map { files.readText(it) }) }
            .onFailure { report.warn("Animate atlas images/$image could not be read: ${it.message}") }
            .getOrNull()
    }

    private fun stageAtlas(image: String): SparrowAtlas? = atlasCache.getOrPut(image.lowercase()) {
        val path = pathIndex["${scan.root}images/$image.xml".lowercase()] ?: return@getOrPut null
        runCatching { SparrowAtlas.parse(files.readText(path)) }.getOrNull()
    }

    private fun convertSongs(
        charts: Map<String, Pair<List<ChartSource>, PsychSong>>,
        characters: Map<String, PsychCharacter>,
        stageIds: Set<String>
    ) {
        scan.songs.forEach { scanned ->
            step("Song ${scanned.id}")
            val (sources, main) = charts[scanned.id] ?: return@forEach
            try {
                val audioNames = scanned.audio.map { it.substringAfterLast('/').substringBeforeLast('.').lowercase() }.toSet()
                val playerKey = characters[main.player1]?.vocalsFile?.takeIf { it.isNotBlank() } ?: "Player"
                val opponentKey = characters[main.player2]?.vocalsFile?.takeIf { it.isNotBlank() } ?: "Opponent"
                val extraEvents = scanned.events?.let { path ->
                    runCatching { PsychChartParser.parseEventsOnly(files.readText(path)) }
                        .onFailure { report.warn("Events for ${scanned.id} could not be read: ${it.message}") }
                        .getOrNull()
                } ?: emptyList()
                val conversion = PsychToVSliceChart.convert(
                    sources,
                    ConversionOptions(
                        songName = main.name,
                        extraEvents = extraEvents,
                        customStageIds = stageIds,
                        noteStyle = noteStyleFor(main.arrowSkin, main.splashSkin, label = "song ${scanned.id}"),
                        pixelStages = pixelStages,
                        playerVocals = listOf(playerKey).takeIf { "voices-${playerKey.lowercase()}" in audioNames },
                        opponentVocals = listOf(opponentKey).takeIf { "voices-${opponentKey.lowercase()}" in audioNames }
                    )
                )
                conversion.files(scanned.id).forEach { (name, text) ->
                    writer.writeText("data/songs/${scanned.id}/$name", text)
                    written++
                }
                conversion.variations.forEach { variation ->
                    scanned.audio.filter { it.substringAfterLast('/').startsWith("Voices", ignoreCase = true) }.forEach { path ->
                        val file = path.substringAfterLast('/')
                        val target = "songs/${scanned.id}/${file.substringBeforeLast('.')}-${variation.variation}.${file.substringAfterLast('.')}"
                        runCatching { writer.copy(target) { files.open(path) } }
                            .onSuccess { written++ }
                            .onFailure { report.warn("Could not copy $target: ${it.message}") }
                    }
                }
                keptPsychEvents += conversion.report.keptPsychEvents
                keptEventNames += conversion.report.unsupportedEvents.keys
                conversion.report.warnings.forEach { report.warn("Song ${scanned.id}: $it") }
                if (conversion.report.unsupportedEvents.isNotEmpty()) {
                    report.section(
                        "Song ${scanned.id} · Psych events kept for scripts",
                        conversion.report.unsupportedEvents.map { (name, count) -> "$name ×$count" }
                    )
                }
                if (scanned.audio.none { it.substringAfterLast('/').startsWith("Inst", ignoreCase = true) }) {
                    report.warn("Song ${scanned.id}: no Inst audio found in songs/${scanned.id}/.")
                }
            } catch (e: Exception) {
                report.warn("Song \"${scanned.id}\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertCharacters(
        characters: Map<String, PsychCharacter>,
        charts: Map<String, Pair<List<ChartSource>, PsychSong>>
    ) {
        val usage = HashMap<String, MutableMap<CharacterSlot, Int>>()
        fun count(id: String, slot: CharacterSlot) {
            val slots = usage.getOrPut(id) { mutableMapOf() }
            slots[slot] = (slots[slot] ?: 0) + 1
        }
        charts.values.forEach { (_, song) ->
            count(song.player1, CharacterSlot.PLAYER)
            count(song.player2, CharacterSlot.OPPONENT)
            count(song.girlfriend, CharacterSlot.GIRLFRIEND)
        }

        var approximate = 0
        characters.forEach { (id, character) ->
            step("Character $id")
            try {
                val slot = usage[id]?.maxByOrNull { it.value }?.key ?: PsychToVSliceCharacter.defaultSlot(id, character)
                val atlases = character.atlasImages.ifEmpty { listOf(character.image) }.mapNotNull { image ->
                    val path = pathIndex["${scan.root}images/$image.xml".lowercase()] ?: return@mapNotNull null
                    PsychToVSliceCharacter.atlasKey(image) to SparrowAtlas.parse(files.readText(path))
                }.toMap()
                val result = PsychToVSliceCharacter.convert(CharacterSource(id, character, slot, atlases, animate = animateAtlas(character.image)))
                writer.writeText("data/characters/$id.json", result.json)
                written++
                if (result.report.stats.any { it.label == "Accuracy" && it.value != "Exact" }) approximate++
                result.report.warnings
                    .filterNot { it.startsWith("Spritesheet XML not provided") }
                    .forEach { report.warn("Character $id: $it") }
            } catch (e: Exception) {
                report.warn("Character \"$id\" was not converted: ${e.message}")
            }
        }
        if (approximate > 0) {
            report.warn("$approximate character(s) are missing their spritesheet XML or Animate atlas files, so their positions are approximate.")
        }
    }

    private fun convertWeeks(songIds: Set<String>) {
        val menu = LinkedHashMap<String, PsychMenuCharacter>()
        scan.menuCharacters.forEach { path ->
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            runCatching { PsychWeekParser.parseMenuCharacter(files.readText(path)) }
                .onSuccess { menu[name] = it }
        }
        scan.weeks.forEach { path ->
            val id = path.substringAfterLast('/').substringBeforeLast('.')
            step("Week $id")
            try {
                val result = PsychToVSliceLevel.convert(WeekSource(id, PsychWeekParser.parse(files.readText(path)), menu))
                writer.writeText("data/levels/$id.json", result.json)
                written++
                result.extra.forEach { (extraPath, text) ->
                    writer.writeText(extraPath, text)
                    written++
                }
                result.report.sections.filter { it.title.startsWith("Level script") }.forEach { report.section("Week $id · ${it.title}", it.lines) }
                result.report.warnings.forEach { report.warn("Week $id: $it") }
                val missing = result.songIds.filter { it !in songIds }
                if (missing.isNotEmpty()) {
                    report.warn("Week $id lists songs with no chart in this mod: ${missing.joinToString()}.")
                }
            } catch (e: Exception) {
                report.warn("Week \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertScripts(info: ModInfo, charts: Map<String, Pair<List<ChartSource>, PsychSong>>) {
        val scriptFiles = scan.scripts.filter { it.substringAfterLast('.').lowercase() in setOf("lua", "hx", "hxs", "hscript") }
        val noteTypeNames = linkedSetOf<String>()
        charts.values.forEach { (sources, _) ->
            sources.forEach { source -> source.song.sections.forEach { section -> section.notes.forEach { if (it.type.isNotBlank()) noteTypeNames += it.type } } }
        }
        scriptFiles.filter { "/custom_notetypes/" in "/" + scan.relative(it).lowercase() }.forEach { noteTypeNames += it.substringAfterLast('/').substringBeforeLast('.') }
        val keptEvents = keptPsychEvents > 0
        if (scriptFiles.isEmpty() && !keptEvents) return
        val slug = ScriptConverter.slug(info.title.ifBlank { "Mod" })
        val sources = scriptFiles.mapNotNull { path ->
            runCatching {
                val rel = scan.relative(path)
                val stem = rel.substringAfterLast('/').substringBeforeLast('.')
                val skip = if (rel.lowercase().startsWith("stages/") && rel.lowercase().endsWith(".lua")) stageSkipLines[stem] ?: emptySet() else emptySet()
                ScriptSource(rel, files.readText(path), skipLines = skip)
            }.getOrNull()
        }
        sources.forEach { step("Script ${it.path.substringAfterLast('/')}") }
        val textured = sources.filter { "/custom_notetypes/" in "/" + it.path.lowercase() }.mapNotNull { source ->
            val texture = PsychNoteStyles.textureIn(source.text) ?: return@mapNotNull null
            val typeName = source.path.substringAfterLast('/').substringBeforeLast('.')
            val style = noteStyleFor(texture, strumline = false, label = "note type $typeName") ?: return@mapNotNull null
            Triple(typeName, texture, style)
        }
        val noteStyles = textured.associate { (typeName, _, style) -> PsychToVSliceChart.noteKindFor(typeName) to style }
        val bundle = ScriptConverter.convert(slug, sources, ScriptConverter.noteTypeMap(noteTypeNames), keptEventNames, noteStyles)
        val scriptedEvents = sources.map { ScriptConverter.detectTarget(it.path) }.filter { it.scope == com.brenninhotools.vconverter.script.ScriptScope.EVENT }.map { it.key }
        val outputs = bundle.files + ScriptConverter.eventFiles(keptEventNames, scriptedEvents)
        val renamed = keptEventNames.filter { ScriptConverter.eventId(it) != it }
        if (renamed.isNotEmpty()) report.section("Events renamed to V-Slice ids", renamed.map { "\"$it\" → \"${ScriptConverter.eventId(it)}\"" })
        outputs.forEach { file ->
            writer.writeText(file.path, file.content)
            written++
        }
        textured.forEach { (typeName, texture, style) ->
            val kind = PsychToVSliceChart.noteKindFor(typeName)
            val scripted = bundle.noteKinds[kind]
            val path = if (scripted != null) {
                "scripts/notekinds/$scripted.hxc"
            } else {
                val (path, text) = PsychNoteStyles.noteKindScript(kind, typeName, style)
                writer.writeText(path, text)
                written++
                path
            }
            report.section("Note type $typeName", listOf("Its texture images/$texture became note style \"$style\", applied by $path."))
        }
        report.stat("Scripts", bundle.converted)
        bundle.report.sections.forEach { report.section("Script · ${it.title}", it.lines) }
        bundle.report.warnings.forEach { report.warn(it) }
        val others = scan.scripts.size - scriptFiles.size
        if (others > 0) report.warn("$others other script files (not .lua or .hx) were skipped.")
    }

    private fun copyAssets() {
        scan.assets.forEach { path ->
            val target = scan.relative(path)
            step(target)
            try {
                writer.copy(target) { files.open(path) }
                written++
            } catch (e: Exception) {
                report.warn("Could not copy $target: ${e.message}")
            }
        }
        scan.packIcon?.let { icon ->
            runCatching {
                writer.copy("_polymod_icon.png") { files.open(icon) }
                written++
            }
        }
    }

    private fun writeMeta(info: ModInfo) {
        step("_polymod_meta.json")
        val contributors = JSONArray()
        if (info.author.isNotBlank()) contributors.put(JSONObject().put("name", info.author))
        val meta = JSONObject()
            .put("title", info.title.ifBlank { "Converted Mod" })
            .put("description", info.description)
            .put("contributors", contributors)
            .put("homepage", "")
            .put("api_version", API_VERSION)
            .put("mod_version", info.version)
            .put("license", "")
        writer.writeText("_polymod_meta.json", meta.toString(2))
        written++
        done = total
        onProgress(ModProgress(total, total, "Done"))
    }

    private fun step(label: String) {
        done++
        onProgress(ModProgress(done.coerceAtMost(total), total, label))
    }

    private fun limited(lines: List<String>, max: Int = 40): List<String> =
        if (lines.size <= max) lines else lines.take(max) + "…and ${lines.size - max} more"

    companion object {
        const val API_VERSION = "0.8.0"

        fun readPack(files: ModFiles, scan: ModScan): ModInfo? {
            val path = scan.pack ?: return null
            return runCatching {
                val json = JSONObject(files.readText(path).trim().removePrefix("﻿"))
                ModInfo(
                    title = json.value("name").asText(),
                    description = json.value("description").asText(),
                    author = ""
                )
            }.getOrNull()
        }
    }
}
