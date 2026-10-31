package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.mod.ModConversionResult
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.readText
import com.brenninhotools.vconverter.script.ScriptConverter
import org.json.JSONArray
import org.json.JSONObject

object ImageProbe {
    fun pngSize(header: ByteArray): Pair<Int, Int>? {
        if (header.size < 24 || header[0] != 0x89.toByte() || header[1] != 'P'.code.toByte()) return null
        fun int(at: Int) = ((header[at].toInt() and 255) shl 24) or ((header[at + 1].toInt() and 255) shl 16) or ((header[at + 2].toInt() and 255) shl 8) or (header[at + 3].toInt() and 255)
        return int(16) to int(20)
    }
}

class CodenameModConverter(
    private val files: ModFiles,
    private val scan: CodenameModScan,
    private val writer: ModWriter,
    private val onProgress: (ModProgress) -> Unit = {}
) {
    private val report = ReportBuilder()
    private val pathIndex: Map<String, String> = files.paths.associateBy { it.lowercase() }
    private var written = 0
    private var done = 0
    private val total = scan.songs.size + scan.characters.size + scan.stages.size + scan.weeks.size + scan.assets.size + scan.scriptCount + 2
    private val keptEvents = sortedMapOf<String, Int>(String.CASE_INSENSITIVE_ORDER)
    private val noteKindNames = LinkedHashMap<String, String>()
    private val usage = HashMap<String, MutableMap<CharacterSlot, Int>>()
    private val moduleIds = mutableListOf<String>()
    private val scriptFiles = mutableListOf<Pair<String, String>>()

    fun convert(info: ModInfo): ModConversionResult {
        val definitions = parseEventDefinitions()
        val characters = parseCharacters()
        val stages = parseStages()
        val songIds = convertSongs(definitions, stages.keys)
        convertStages(stages)
        convertCharacters(characters)
        convertWeeks(songIds)
        convertScripts(info, stages, definitions)
        copyAssets()
        writeMeta(info)

        report.stat("Songs", scan.songs.size)
        report.stat("Characters", characters.size)
        report.stat("Stages", stages.size)
        report.stat("Weeks", scan.weeks.size)
        report.stat("Assets", scan.assets.size)
        report.stat("Files", written)
        if (scan.placeholders.isNotEmpty()) report.section("Empty placeholder folders removed", scan.placeholders.map { (dir, file) -> "$dir/ (only ${file.substringAfterLast('/')})" })
        if (scan.ignored.isNotEmpty()) report.section("Other files skipped", limited(scan.ignored.map(scan::relative)))
        if (scan.weekList != null) report.warn("Codename's weeks.txt order is not used by V-Slice; levels are listed in their own order.")
        if (scan.stateScripts.isNotEmpty()) report.warn("State scripts (data/states/) were skipped; V-Slice modules can hook states with onStateChangeEnd instead: ${scan.stateScripts.joinToString { scan.relative(it) }}.")
        return ModConversionResult(written, report.build())
    }

    private fun parseEventDefinitions(): Map<String, EventDefinition> =
        scan.events.mapNotNull { (name, path) -> runCatching { name to CodenameEvents.parseDefinition(name, files.readText(path)) }.getOrNull() }.toMap()

    private fun parseCharacters(): Map<String, CodenameCharacter> {
        val result = LinkedHashMap<String, CodenameCharacter>()
        scan.characters.forEach { (id, path) ->
            runCatching { CodenameCharacterParser.parse(id, files.readText(path)) }
                .onSuccess { result[id] = it }
                .onFailure { report.warn("Character \"$id\" could not be read: ${it.message}") }
        }
        return result
    }

    private fun imageInfo(path: String): ImageInfo? {
        val png = pathIndex["${scan.root}images/$path.png".lowercase()] ?: return null
        val header = runCatching { files.open(png).use { input -> ByteArray(24).also { buf -> var read = 0; while (read < 24) { val n = input.read(buf, read, 24 - read); if (n < 0) break; read += n } } } }.getOrNull() ?: return null
        val (w, h) = ImageProbe.pngSize(header) ?: return null
        val xml = pathIndex["${scan.root}images/$path.xml".lowercase()]
        if (xml != null) {
            val atlas = runCatching { SparrowAtlas.parse(files.readText(xml)) }.getOrNull()
            val first = atlas?.firstFrame
            if (first != null) return ImageInfo(first.sourceWidth, first.sourceHeight, true)
        }
        return ImageInfo(w.toDouble(), h.toDouble(), xml != null)
    }

    private fun parseStages(): Map<String, CodenameStage> {
        val result = LinkedHashMap<String, CodenameStage>()
        scan.stages.forEach { (id, path) ->
            runCatching { CodenameStageParser.parse(id, files.readText(path), images = ::imageInfo) }
                .onSuccess { result[id] = it }
                .onFailure { report.warn("Stage \"$id\" could not be read: ${it.message}") }
        }
        return result
    }

    private fun audio(song: CodenameScannedSong, name: String): String? =
        song.audio.filter { it.substringAfterLast('/').substringBeforeLast('.').equals(name, ignoreCase = true) }
            .minByOrNull { listOf("ogg", "wav", "mp3").indexOf(ext(it)).let { i -> if (i < 0) 9 else i } }

    private fun ext(path: String) = path.substringAfterLast('.', "ogg").lowercase()

    private fun convertSongs(definitions: Map<String, EventDefinition>, stageIds: Set<String>): Set<String> {
        val ids = LinkedHashSet<String>()
        scan.songs.forEach { song ->
            step("Song ${song.id}")
            try {
                val baseMeta = song.metas[""]?.let { CodenameChartParser.parseMeta(files.readText(it), song.folder) } ?: CodenameMeta.default(song.folder)
                val variants = (song.variants + baseMeta.variants).distinct().filter { song.charts.containsKey(it) }
                listOf("") .plus(variants).forEach { variant ->
                    val paths = song.charts[variant] ?: return@forEach
                    val raw = paths.mapValues { files.readText(it.value) }
                    if (raw.values.any { !CodenameChartParser.isCodenameChart(it) }) {
                        convertLegacy(song, variant, raw)
                        return@forEach
                    }
                    val meta = song.metas[variant]?.let { CodenameChartParser.parseMeta(files.readText(it), song.folder) } ?: baseMeta
                    val charts = raw.mapValues { CodenameChartParser.parseChart(it.value) }
                    val events = (song.events[variant] ?: song.events[""])?.let { p -> runCatching { CodenameChartParser.parseEvents(files.readText(p)) }.getOrNull() } ?: emptyList()
                    val suffix = if (variant.isEmpty()) "" else "-$variant"
                    val main = charts["normal"] ?: charts.values.first()
                    val mergedMeta = meta.merge(main.meta)
                    val player = main.strumLines.firstOrNull { it.isPlayer }
                    val opponent = main.strumLines.firstOrNull { it.isOpponent }
                    val playerId = player?.characters?.firstOrNull() ?: "bf"
                    val opponentId = opponent?.characters?.firstOrNull() ?: "dad"
                    val playerVoice = player?.let { audio(song, "Voices${mergedMeta.vocalsSuffix}${it.vocalsSuffix}") }?.takeIf { player.vocalsSuffix.isNotEmpty() }
                    val opponentVoice = opponent?.let { audio(song, "Voices${mergedMeta.vocalsSuffix}${it.vocalsSuffix}") }?.takeIf { opponent.vocalsSuffix.isNotEmpty() }
                    val result = CodenameToVSliceChart.convert(
                        CodenameSong(song.id, meta, charts, events, variant),
                        CodenameChartOptions(
                            customStageIds = stageIds,
                            eventDefinitions = definitions,
                            playerVocals = if (playerVoice != null) listOf(playerId) else null,
                            opponentVocals = if (opponentVoice != null) listOf(opponentId) else null,
                            instrumental = variant,
                            songVariations = if (variant.isEmpty()) variants else emptyList()
                        )
                    )
                    val metaName = if (variant.isEmpty()) "${song.id}-metadata.json" else "${song.id}-metadata-$variant.json"
                    val chartName = if (variant.isEmpty()) "${song.id}-chart.json" else "${song.id}-chart-$variant.json"
                    writer.writeText("data/songs/${song.id}/$metaName", VSliceWriter.metadataJson(result.metadata))
                    writer.writeText("data/songs/${song.id}/$chartName", VSliceWriter.chartJson(result.chart))
                    written += 2
                    count(result.metadata.characters.player, CharacterSlot.PLAYER)
                    count(result.metadata.characters.opponent, CharacterSlot.OPPONENT)
                    count(result.metadata.characters.girlfriend, CharacterSlot.GIRLFRIEND)
                    result.keptEvents.forEach { (name, n) -> keptEvents[name] = (keptEvents[name] ?: 0) + n }
                    noteKindNames.putAll(result.noteTypeNames)
                    result.report.warnings.forEach { report.warn("Song ${song.id}$suffix: $it") }
                    if (result.keptEvents.isNotEmpty()) report.section("Song ${song.id}$suffix · kept events", result.keptEvents.map { (name, n) -> "$name ×$n" })

                    val inst = audio(song, "Inst${mergedMeta.instSuffix}") ?: audio(song, "Inst")
                    if (inst == null) report.warn("Song ${song.id}$suffix: no Inst audio found in songs/${song.folder}/song/.")
                    else copy(inst, "songs/${song.id}/Inst$suffix.${ext(inst)}")
                    if (playerVoice != null) copy(playerVoice, "songs/${song.id}/Voices-$playerId$suffix.${ext(playerVoice)}")
                    if (opponentVoice != null) copy(opponentVoice, "songs/${song.id}/Voices-$opponentId$suffix.${ext(opponentVoice)}")
                    if (playerVoice == null || opponentVoice == null) {
                        val shared = audio(song, "Voices${mergedMeta.vocalsSuffix}") ?: audio(song, "Voices")
                        if (shared != null) copy(shared, "songs/${song.id}/Voices$suffix.${ext(shared)}")
                        else if (mergedMeta.needsVoices) report.warn("Song ${song.id}$suffix: no Voices audio found.")
                    }
                }
                ids += song.id
                if (song.cutscenes.isNotEmpty()) report.warn("Song ${song.id}: cutscene/dialogue scripts (${song.cutscenes.joinToString { it.substringAfterLast('/') }}) were not converted; V-Slice cutscenes use song scripts or conversations.")
            } catch (e: Exception) {
                report.warn("Song \"${song.id}\" was not converted: ${e.message}")
            }
        }
        return ids
    }

    private fun convertLegacy(song: CodenameScannedSong, variant: String, raw: Map<String, String>) {
        val sources = raw.mapNotNull { (diff, text) -> runCatching { ChartSource(diff, PsychChartParser.parse(text)) }.getOrNull() }
        if (sources.isEmpty()) {
            report.warn("Song ${song.id}: its charts are neither Codename nor Psych/legacy charts.")
            return
        }
        val conversion = PsychToVSliceChart.convert(sources, ConversionOptions(songName = song.folder, splitVariations = false, generatedBy = "V-Converter (Codename Engine to V-Slice)"))
        val suffix = if (variant.isEmpty()) "" else "-$variant"
        writer.writeText("data/songs/${song.id}/${song.id}-metadata$suffix.json", VSliceWriter.metadataJson(conversion.metadata))
        writer.writeText("data/songs/${song.id}/${song.id}-chart$suffix.json", VSliceWriter.chartJson(conversion.chart))
        written += 2
        report.warn("Song ${song.id}$suffix uses legacy (Psych/base game) charts, so it was converted with the Psych chart rules.")
        count(conversion.metadata.characters.player, CharacterSlot.PLAYER)
        count(conversion.metadata.characters.opponent, CharacterSlot.OPPONENT)
        count(conversion.metadata.characters.girlfriend, CharacterSlot.GIRLFRIEND)
        audio(song, "Inst")?.let { copy(it, "songs/${song.id}/Inst$suffix.${ext(it)}") }
        audio(song, "Voices")?.let { copy(it, "songs/${song.id}/Voices$suffix.${ext(it)}") }
    }

    private fun count(id: String, slot: CharacterSlot) {
        if (id.isBlank()) return
        val slots = usage.getOrPut(id) { mutableMapOf() }
        slots[slot] = (slots[slot] ?: 0) + 1
    }

    private fun atlasesFor(images: List<String>): Map<String, SparrowAtlas> = images.mapNotNull { image ->
        val path = pathIndex["${scan.root}images/$image.xml".lowercase()] ?: return@mapNotNull null
        runCatching { PsychToVSliceCharacter.atlasKey(image) to SparrowAtlas.parse(files.readText(path)) }.getOrNull()
    }.toMap()

    private fun convertCharacters(characters: Map<String, CodenameCharacter>) {
        characters.forEach { (id, character) ->
            step("Character $id")
            try {
                val slot = usage[id]?.maxByOrNull { it.value }?.key ?: CodenameToVSliceCharacter.defaultSlot(character)
                val gameOver = if (slot == CharacterSlot.PLAYER && character.animations.none { it.name == "firstDeath" }) {
                    (character.gameOverCharacter ?: "bf-dead").takeIf { it != id }?.let { characters[it] }
                } else null
                val images = character.images + (gameOver?.images ?: emptyList())
                val atlasMissing = images.any { img -> pathIndex["${scan.root}images/$img.xml".lowercase()] == null }
                val animate = images.any { img -> pathIndex["${scan.root}images/$img/animation.json".lowercase()] != null }
                val result = CodenameToVSliceCharacter.convert(character, slot, atlasesFor(images), gameOver)
                val json = if (animate) JSONObject(result.json).put("renderType", "animateatlas").toString(2) else result.json
                writer.writeText("data/characters/$id.json", json)
                written++
                result.report.warnings
                    .filterNot { it.startsWith("Spritesheet XML not provided") }
                    .filterNot { id.endsWith("-dead") && it.startsWith("No \"idle\"") }
                    .filterNot { animate && it.startsWith("centercam") }
                    .forEach { report.warn("Character $id: $it") }
                if (animate) report.warn("Character $id uses an Animate atlas (renderType \"animateatlas\"); its offsets are approximate, so check it in the V-Slice character editor.")
                else if (atlasMissing) report.warn("Character $id: spritesheet XML missing, so its position is approximate.")
                result.report.sections.filter { it.title == "Game over" }.forEach { report.section("Character $id · game over", it.lines) }
            } catch (e: Exception) {
                report.warn("Character \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertStages(stages: Map<String, CodenameStage>) {
        stages.forEach { (id, stage) ->
            step("Stage $id")
            try {
                val result = CodenameToVSliceStage.convert(stage)
                writer.writeText("data/stages/$id.json", result.json)
                written++
                val zoom = result.report.warnings.filter { it.contains("zoomfactor") }
                result.report.warnings.filterNot { it.contains("zoomfactor") }.forEach { report.warn("Stage $id: $it") }
                if (zoom.isNotEmpty()) report.warn("Stage $id: ${zoom.size} sprite(s) use zoomfactor (${zoom.map { it.substringAfter('"').substringBefore('"') }.joinToString()}); V-Slice has no zoom factor, so they now zoom with the camera.")
            } catch (e: Exception) {
                report.warn("Stage \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertWeeks(songIds: Set<String>) {
        val menu = LinkedHashMap<String, CodenameMenuCharacter>()
        scan.weekCharacters.forEach { (name, path) ->
            runCatching { CodenameWeekParser.parseMenuCharacter(name, files.readText(path)) }.onSuccess { menu[name] = it }
        }
        val folders = scan.songs.associate { it.folder.lowercase() to it.id }
        scan.weeks.forEach { (id, path) ->
            step("Week $id")
            try {
                val week = CodenameWeekParser.parseWeek(id, files.readText(path))
                val result = CodenameToVSliceLevel.convert(week, menu) { name -> folders[name.lowercase()] ?: SongPath.format(name) }
                writer.writeText("data/levels/$id.json", result.json)
                written++
                result.report.warnings.forEach { report.warn("Week $id: $it") }
                val missing = result.songIds.filter { it !in songIds }
                if (missing.isNotEmpty()) report.warn("Week $id lists songs with no chart in this mod: ${missing.joinToString()}.")
            } catch (e: Exception) {
                report.warn("Week \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private class Job(val path: String, val stem: String, val target: CodenameScriptTarget, val sprites: Set<String> = emptySet())

    private fun convertScripts(info: ModInfo, stages: Map<String, CodenameStage>, definitions: Map<String, EventDefinition>) {
        val jobs = mutableListOf<Job>()
        scan.songs.forEach { song ->
            song.scripts.forEach { path ->
                val stem = path.substringAfterLast('/').substringBeforeLast('.')
                jobs += Job(path, stem, CodenameScriptTarget(CodenameScope.SONG, song.id))
            }
        }
        scan.stageScripts.forEach { (id, path) ->
            jobs += Job(path, id, CodenameScriptTarget(CodenameScope.STAGE, id), stages[id]?.spriteNames?.toSet() ?: emptySet())
        }
        stages.forEach { (id, stage) ->
            stage.extensions.forEach { ext ->
                val path = scan.libraryScripts[ext] ?: scan.libraryScripts.entries.firstOrNull { it.key.equals(ext, ignoreCase = true) }?.value
                if (path == null) report.warn("Stage $id loads $ext.hx, which is not in the mod.")
                else jobs += Job(path, ext.substringAfterLast('/'), CodenameScriptTarget(CodenameScope.STAGE, id), stage.spriteNames.toSet())
            }
        }
        scan.characterScripts.forEach { (id, path) ->
            jobs += Job(path, id, CodenameScriptTarget(CodenameScope.CHARACTER, id))
        }
        scan.eventScripts.forEach { (name, path) ->
            jobs += Job(path, name, CodenameScriptTarget(CodenameScope.EVENT, name))
        }
        scan.noteScripts.forEach { (name, path) ->
            jobs += Job(path, name, CodenameScriptTarget(CodenameScope.NOTE_TYPE, name))
        }
        scan.globalScripts.forEach { path ->
            val stem = path.substringAfterLast('/').substringBeforeLast('.')
            jobs += Job(path, stem, CodenameScriptTarget(CodenameScope.GLOBAL))
        }

        val planner = CodenameScriptPlanner(info.title)
        val usedEvents = (keptEvents.keys + definitions.keys).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        val eventDefs = usedEvents.map { CodenameEvents.definitionFor(it, definitions, 0) }
        val context = CodenameScriptContext(noteTypeNames = noteKindNames, events = eventDefs)
        val results = mutableListOf<Triple<Job, String, CodenameScriptResult>>()
        val kept = mutableListOf<String>()
        jobs.forEach { job ->
            step("Script ${job.path.substringAfterLast('/')}")
            try {
                val text = files.readText(job.path)
                val plan = planner.plan(job.target, job.stem, text)
                val result = CodenameScriptConverter(plan.className, plan.host.id, job.target, context.copy(stageSprites = job.sprites), plan.host).convert(text)
                results += Triple(job, plan.path, result)
                plan.note?.let { kept += "${scan.relative(job.path)} → ${plan.className} (Module): $it" }
            } catch (e: Exception) {
                report.warn("Script ${scan.relative(job.path)} was not converted: ${e.message}")
            }
        }
        moduleIds += planner.refs
        results.forEach { (job, path, result) ->
            writer.writeText(path, result.source)
            written++
            if (result.issues.isNotEmpty()) report.section("Script · ${scan.relative(job.path)}", result.issues.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message })
        }
        if (kept.isNotEmpty()) report.section("Scripts kept as Modules", kept)
        val eventFiles = eventDefs.filter { def ->
            def.name !in CodenameEvents.nativeConversions && (def.name in CodenameEvents.implemented || definitions.containsKey(def.name)) &&
                !(def.name == "Camera Zoom" && def.name !in keptEvents)
        }
        eventFiles.forEach { def ->
            val file = CodenameEvents.songEventFile(def, moduleIds)
            writer.writeText(file.path, file.content)
            written++
        }
        if (noteKindNames.containsKey("alt")) {
            val file = CodenameEvents.altNoteKindFile()
            writer.writeText(file.path, file.content)
            written++
        }
        val unhandled = keptEvents.keys.filter { name -> name !in CodenameEvents.implemented && name !in scan.eventScripts.keys }
        if (unhandled.isNotEmpty()) report.warn("These events are kept in the charts but nothing handles them in V-Slice yet: ${unhandled.joinToString()}.")
        report.stat("Scripts", results.size)
    }

    private fun copy(source: String, target: String) {
        try {
            writer.copy(target) { files.open(source) }
            written++
        } catch (e: Exception) {
            report.warn("Could not copy $target: ${e.message}")
        }
    }

    private fun copyAssets() {
        scan.assets.forEach { path ->
            val target = scan.relative(path)
            step(target)
            copy(path, target)
        }
        scan.icon?.let { copy(it, "_polymod_icon.png") }
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
            .put("api_version", ModConverter.API_VERSION)
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
        fun readModpack(files: ModFiles, scan: CodenameModScan): ModInfo? {
            val path = scan.modpack ?: return null
            return runCatching {
                val values = files.readText(path).lines()
                    .map { it.trim() }
                    .filter { it.contains('=') && !it.startsWith(";") && !it.startsWith("#") && !it.startsWith("[") }
                    .associate { it.substringBefore('=').trim().uppercase() to it.substringAfter('=').trim().trim('"') }
                ModInfo(
                    title = values["NAME"] ?: values["MOD_NAME"] ?: "",
                    description = values["DESCRIPTION"] ?: values["MOD_DESCRIPTION"] ?: "",
                    author = values["AUTHOR"] ?: values["MOD_AUTHOR"] ?: "",
                    version = values["VERSION"] ?: values["MOD_VERSION"] ?: "1.0.0"
                )
            }.getOrNull()
        }
    }
}

