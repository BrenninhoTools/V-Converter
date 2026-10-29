package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.AnimateAtlas
import com.brenninhotools.vconverter.core.AnimateAtlases
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.mod.ModConversionResult
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.readText
import com.brenninhotools.vconverter.psych.PsychWriters
import com.brenninhotools.vconverter.week.PsychWeekSong

class CodenameToPsychMod(
    private val files: ModFiles,
    private val scan: CodenameModScan,
    private val writer: ModWriter,
    private val onProgress: (ModProgress) -> Unit = {}
) {
    private val report = ReportBuilder()
    private val pathIndex: Map<String, String> = files.paths.associateBy { it.lowercase() }
    private var written = 0
    private var done = 0
    private val total = scan.songs.size + scan.characters.size + scan.stages.size + scan.weeks.size + scan.assets.size + scan.scriptCount + 3
    private val customEvents = sortedMapOf<String, Int>(String.CASE_INSENSITIVE_ORDER)
    private val noteTypes = sortedSetOf<String>()
    private val usage = HashMap<String, MutableMap<CharacterSlot, Int>>()
    private val songInfo = HashMap<String, PsychSongInfo>()
    private val songNames = HashMap<String, String>()
    private val songOutputs = LinkedHashMap<String, List<String>>()
    private val psychSongs = LinkedHashMap<String, PsychWeekSong>()
    private var compat = false
    private var scripts = 0
    private val writtenPaths = HashSet<String>()

    fun convert(info: ModInfo): ModConversionResult {
        val definitions = scan.events.mapNotNull { (name, path) -> runCatching { name to CodenameEvents.parseDefinition(name, files.readText(path)) }.getOrNull() }.toMap()
        val characters = parseCharacters()
        val stages = parseStages()
        convertSongs(definitions, characters, stages.keys)
        val eventCounts = (customEvents + definitions.mapValues { it.value.params.size }).toMutableMap()
        convertStages(stages, eventCounts)
        convertCharacters(characters, eventCounts)
        convertWeeks()
        convertEvents(definitions, eventCounts)
        convertNoteTypes(eventCounts)
        convertGlobalScripts(eventCounts)
        if (compat) write(CodenamePsychHelpers.COMPAT_SCRIPT, CodenamePsychHelpers.compatScript())
        copyAssets()
        writePack(info)

        report.stat("Songs", songOutputs.values.sumOf { it.size })
        report.stat("Characters", characters.size)
        report.stat("Stages", stages.size)
        report.stat("Weeks", scan.weeks.size)
        report.stat("Scripts", scripts)
        report.stat("Assets", scan.assets.size)
        report.stat("Files", written)
        if (scan.placeholders.isNotEmpty()) report.section("Empty placeholder folders removed", scan.placeholders.map { (dir, file) -> "$dir/ (only ${file.substringAfterLast('/')})" })
        if (scan.ignored.isNotEmpty()) report.section("Other files skipped", limited(scan.ignored.map(scan::relative)))
        if (scan.stateScripts.isNotEmpty()) report.warn("State scripts (data/states/) were skipped; Psych can only script PlayState: ${scan.stateScripts.joinToString { scan.relative(it) }}.")
        if (compat) report.section("Codename compatibility", listOf("${CodenamePsychHelpers.COMPAT_SCRIPT} recreates Codename's camera bop settings (camZoomingInterval, camZoomingStrength, maxCamZoom, defaultHudZoom) for the converted scripts and events."))
        return ModConversionResult(written, report.build())
    }

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
            val first = runCatching { SparrowAtlas.parse(files.readText(xml)) }.getOrNull()?.firstFrame
            if (first != null) return ImageInfo(first.sourceWidth, first.sourceHeight, true)
        }
        return ImageInfo(w.toDouble(), h.toDouble(), xml != null)
    }

    private fun parseStages(): Map<String, CodenameStage> {
        val result = LinkedHashMap<String, CodenameStage>()
        scan.stages.forEach { (id, path) ->
            runCatching { CodenameStageParser.parse(id, files.readText(path), includeLowMemory = true, images = ::imageInfo) }
                .onSuccess { result[id] = it }
                .onFailure { report.warn("Stage \"$id\" could not be read: ${it.message}") }
        }
        return result
    }

    private fun audio(song: CodenameScannedSong, name: String): String? =
        song.audio.filter { it.substringAfterLast('/').substringBeforeLast('.').equals(name, ignoreCase = true) }
            .minByOrNull { listOf("ogg", "wav", "mp3").indexOf(ext(it)).let { i -> if (i < 0) 9 else i } }

    private fun ext(path: String) = path.substringAfterLast('.', "ogg").lowercase()

    private fun count(id: String, slot: CharacterSlot) {
        if (id.isBlank()) return
        val slots = usage.getOrPut(id) { mutableMapOf() }
        slots[slot] = (slots[slot] ?: 0) + 1
    }

    private fun convertSongs(definitions: Map<String, EventDefinition>, characters: Map<String, CodenameCharacter>, stageIds: Set<String>) {
        val customStages = stageIds.associateWith { it }
        scan.songs.forEach { song ->
            step("Song ${song.id}")
            val outputs = mutableListOf<String>()
            try {
                val baseMeta = song.metas[""]?.let { CodenameChartParser.parseMeta(files.readText(it), song.folder) } ?: CodenameMeta.default(song.folder)
                val variants = (song.variants + baseMeta.variants).distinct().filter { song.charts.containsKey(it) }
                listOf("").plus(variants).forEach { variant ->
                    val paths = song.charts[variant] ?: return@forEach
                    val raw = paths.mapValues { files.readText(it.value) }
                    val suffix = if (variant.isEmpty()) "" else "-$variant"
                    if (raw.values.any { !CodenameChartParser.isCodenameChart(it) }) {
                        val id = SongPath.format(song.folder + suffix)
                        raw.forEach { (diff, text) ->
                            val file = if (diff.equals("normal", true)) "$id.json" else "$id-${SongPath.format(diff)}.json"
                            write("data/$id/$file", text)
                        }
                        report.warn("Song ${song.id}$suffix already uses Psych/base game charts; they were copied unchanged.")
                        raw.values.firstNotNullOfOrNull { text -> runCatching { com.brenninhotools.vconverter.chart.psych.PsychChartParser.parse(text) }.getOrNull() }?.let { legacy ->
                            count(legacy.player1, CharacterSlot.PLAYER)
                            count(legacy.player2, CharacterSlot.OPPONENT)
                            count(legacy.girlfriend, CharacterSlot.GIRLFRIEND)
                            val opponentChar = characters[legacy.player2]
                            val info = PsychSongInfo(
                                baseMeta.icon.takeIf { it.isNotBlank() && it != "face" } ?: opponentChar?.icon ?: "face",
                                if (baseMeta.color.isNotBlank()) CodenamePsychCharacter.rgb(baseMeta.color) else opponentChar?.color?.let { CodenamePsychCharacter.rgb(it) } ?: listOf(146, 113, 253)
                            )
                            val key = (song.folder + suffix).lowercase()
                            val name = baseMeta.displayName.takeIf { SongPath.format(it) == id } ?: legacy.name.takeIf { SongPath.format(it) == id } ?: id
                            songInfo[key] = info
                            songNames[key] = name
                            psychSongs[id] = PsychWeekSong(name, info.icon, info.color)
                        }
                        copyAudio(song, id, "", "", null, null)
                        outputs += id
                        return@forEach
                    }
                    val meta = song.metas[variant]?.let { CodenameChartParser.parseMeta(files.readText(it), song.folder) } ?: baseMeta
                    val charts = raw.mapValues { CodenameChartParser.parseChart(it.value) }
                    val events = (song.events[variant] ?: song.events[""])?.let { p -> runCatching { CodenameChartParser.parseEvents(files.readText(p)) }.getOrNull() } ?: emptyList()
                    val main = charts["normal"] ?: charts.values.first()
                    val player = main.strumLines.firstOrNull { it.isPlayer }
                    val gameOver = player?.characters?.firstOrNull()?.let { characters[it]?.gameOverCharacter }
                    val result = CodenameToPsychChart.convert(
                        CodenameSong(song.id, meta, charts, events, variant),
                        CodenamePsychOptions(stageIds = customStages, eventDefinitions = definitions, gameOverCharacter = gameOver)
                    )
                    result.files.forEach { write(it.path, it.json) }
                    result.customEvents.forEach { (name, n) -> customEvents[name] = maxOf(customEvents[name] ?: 0, n) }
                    noteTypes += result.noteTypes
                    count(result.characters["player"] ?: "", CharacterSlot.PLAYER)
                    count(result.characters["opponent"] ?: "", CharacterSlot.OPPONENT)
                    count(result.characters["girlfriend"] ?: "", CharacterSlot.GIRLFRIEND)
                    result.report.warnings.forEach { report.warn("Song ${song.id}$suffix: $it") }
                    result.report.sections.filter { it.title != "Difficulties" }.forEach { report.section("Song ${song.id}$suffix · ${it.title}", it.lines) }
                    val merged = meta.merge(main.meta)
                    val opponent = main.strumLines.firstOrNull { it.isOpponent }
                    val opponentChar = opponent?.characters?.firstOrNull()?.let { characters[it] }
                    songInfo[(song.folder + suffix).lowercase()] = PsychSongInfo(
                        merged.icon.takeIf { it.isNotBlank() && it != "face" } ?: opponentChar?.icon ?: "face",
                        if (merged.color.isNotBlank()) CodenamePsychCharacter.rgb(merged.color) else opponentChar?.color?.let { CodenamePsychCharacter.rgb(it) } ?: listOf(146, 113, 253)
                    )
                    songNames[(song.folder + suffix).lowercase()] = result.songName
                    songInfo[(song.folder + suffix).lowercase()]?.let { psychSongs[result.songId] = PsychWeekSong(result.songName, it.icon, it.color) }
                    val playerSuffix = player?.vocalsSuffix?.takeIf { it.isNotEmpty() }
                    val opponentSuffix = opponent?.vocalsSuffix?.takeIf { it.isNotEmpty() }
                    copyAudio(song, result.songId, merged.instSuffix, merged.vocalsSuffix, playerSuffix, opponentSuffix)
                    outputs += result.songId
                }
                if (song.cutscenes.isNotEmpty()) report.warn("Song ${song.id}: cutscene/dialogue scripts (${song.cutscenes.joinToString { it.substringAfterLast('/') }}) were not converted; Psych plays cutscenes with startVideo/startDialogue in a song script.")
            } catch (e: Exception) {
                report.warn("Song \"${song.id}\" was not converted: ${e.message}")
            }
            songOutputs[song.id] = outputs
        }
    }

    private fun copyAudio(song: CodenameScannedSong, id: String, instSuffix: String, vocalsSuffix: String, playerSuffix: String?, opponentSuffix: String?) {
        val inst = audio(song, "Inst$instSuffix") ?: audio(song, "Inst")
        if (inst == null) report.warn("Song $id: no Inst audio found in songs/${song.folder}/song/.")
        else copy(inst, "songs/$id/Inst.${ext(inst)}")
        val playerVoice = playerSuffix?.let { audio(song, "Voices$vocalsSuffix$it") }
        val opponentVoice = opponentSuffix?.let { audio(song, "Voices$vocalsSuffix$it") }
        playerVoice?.let { copy(it, "songs/$id/Voices-Player.${ext(it)}") }
        opponentVoice?.let { copy(it, "songs/$id/Voices-Opponent.${ext(it)}") }
        val shared = audio(song, "Voices$vocalsSuffix") ?: audio(song, "Voices")
        if (shared != null && playerVoice == null) copy(shared, "songs/$id/Voices.${ext(shared)}")
    }

    private fun atlasesFor(images: List<String>): Map<String, SparrowAtlas> = images.mapNotNull { image ->
        val path = pathIndex["${scan.root}images/$image.xml".lowercase()] ?: return@mapNotNull null
        runCatching { PsychToVSliceCharacter.atlasKey(image) to SparrowAtlas.parse(files.readText(path)) }.getOrNull()
    }.toMap()

    private fun convertCharacters(characters: Map<String, CodenameCharacter>, eventCounts: Map<String, Int>) {
        characters.forEach { (sourceId, sourceCharacter) ->
            step("Character $sourceId")
            val id = CodenameToPsychChart.psychCharacter(sourceId).takeIf { it == sourceId || it !in characters } ?: sourceId
            val character = if (id == sourceId) sourceCharacter else sourceCharacter.copy(id = id)
            try {
                val slot = usage[id]?.maxByOrNull { it.value }?.key ?: CodenamePsychCharacter.defaultSlot(character)
                val folder = "${scan.root}images/${character.images.first()}/".lowercase()
                val atlas = pathIndex["${folder}animation.json"]?.let { animation ->
                    val spritemaps = files.paths.filter { it.lowercase().startsWith(folder) && it != animation && AnimateAtlases.isAtlasFile(it.substringAfterLast('/')) && !it.substring(folder.length).contains('/') }.sortedBy { it.lowercase() }
                    runCatching { AnimateAtlas.parse(files.readText(animation), spritemaps.map { files.readText(it) }) }.getOrNull()
                }
                val result = CodenameToPsychCharacter.convert(character, slot, atlasesFor(character.images), atlas)
                if (id != sourceId) report.section("Character $sourceId", listOf("Renamed to \"$id\": Psych has the speaker shooting built into that character (it reads data/<song>/picospeaker.json), so the Codename character script was not needed."))
                result.files.forEach { write(it.path, it.text) }
                result.report.warnings.filterNot { it.startsWith("Spritesheet XML not provided") && !character.animations.any { a -> a.x != 0.0 } }.forEach { report.warn("Character $id: $it") }
                result.report.sections.filter { it.title == "Notes" }.forEach { report.section("Character $id", it.lines) }
                val script = scan.characterScripts[sourceId]?.takeIf { id == sourceId }
                if (script != null || !result.hooks.isEmpty) {
                    convertScript(script, "characters/$id.hx", CodenameScriptTarget(CodenameScope.CHARACTER, id),
                        CodenamePsychScriptContext(characterId = id, eventParamCounts = eventCounts, hooks = result.hooks))
                }
            } catch (e: Exception) {
                report.warn("Character \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertStages(stages: Map<String, CodenameStage>, eventCounts: Map<String, Int>) {
        stages.forEach { (id, stage) ->
            step("Stage $id")
            try {
                val result = CodenameToPsychStage.convert(stage)
                result.files.forEach { write(it.path, it.text) }
                result.report.warnings.forEach { report.warn("Stage $id: $it") }
                result.report.sections.forEach { report.section("Stage $id · ${it.title}", it.lines) }
                val script = scan.stageScripts[id]
                val sprites = stage.spriteNames.toSet()
                if (script != null || !result.hooks.isEmpty) {
                    convertScript(script, "stages/$id.hx", CodenameScriptTarget(CodenameScope.STAGE, id),
                        CodenamePsychScriptContext(stageSprites = sprites, eventParamCounts = eventCounts, hooks = result.hooks))
                }
                result.extensions.forEach { ext ->
                    val path = scan.libraryScripts[ext] ?: scan.libraryScripts.entries.firstOrNull { it.key.equals(ext, ignoreCase = true) }?.value
                    if (path == null) report.warn("Stage $id loads $ext.hx, which is not in the mod.")
                    else convertScript(path, "stages/extensions/${ext.substringAfterLast('/')}.hx", CodenameScriptTarget(CodenameScope.STAGE, id),
                        CodenamePsychScriptContext(stageSprites = sprites, eventParamCounts = eventCounts))
                }
            } catch (e: Exception) {
                report.warn("Stage \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertScript(path: String?, target: String, scope: CodenameScriptTarget, context: CodenamePsychScriptContext): Boolean {
        step("Script ${target.substringAfterLast('/')}")
        return try {
            val source = path?.let { files.readText(it) } ?: ""
            val result = CodenameToPsychScript(scope, context).convert(source)
            write(target, result.source)
            scripts++
            if (result.usesCompat) compat = true
            if (result.issues.isNotEmpty()) report.section("Script · ${path?.let(scan::relative) ?: target}", result.issues.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message })
            true
        } catch (e: Exception) {
            report.warn("Script ${path?.let(scan::relative) ?: target} was not converted: ${e.message}")
            false
        }
    }

    private fun convertWeeks() {
        val menu = LinkedHashMap<String, CodenameMenuCharacter>()
        scan.weekCharacters.forEach { (name, path) ->
            runCatching { CodenameWeekParser.parseMenuCharacter(name, files.readText(path)) }.onSuccess { menu[name] = it }
        }
        val inWeeks = HashSet<String>()
        val order = mutableListOf<String>()
        scan.weeks.forEach { (id, path) ->
            step("Week $id")
            try {
                val week = CodenameWeekParser.parseWeek(id, files.readText(path))
                val result = CodenameToPsychWeek.convert(week, { name -> songInfo[name.lowercase()] }, { name -> songNames[name.lowercase()] ?: name })
                result.files.forEach { write(it.path, it.text) }
                result.copies.forEach { copyRelative(it.from, it.to) }
                result.report.warnings.forEach { report.warn("Week $id: $it") }
                week.songs.forEach { inWeeks += it.name.lowercase() }
                order += id
                val missing = week.songs.filter { s -> scan.songs.none { it.folder.equals(s.name, true) || it.id == SongPath.format(s.name) } }
                if (missing.isNotEmpty()) report.warn("Week $id lists songs with no chart in this mod: ${missing.joinToString { it.name }}.")
            } catch (e: Exception) {
                report.warn("Week \"$id\" was not converted: ${e.message}")
            }
        }
        menu.forEach { (name, character) ->
            val result = CodenameToPsychWeek.menuCharacter(character, name)
            result.files.forEach { write(it.path, it.text) }
            result.copies.forEach { copyRelative(it.from, it.to) }
            result.report.warnings.forEach { report.warn("Week character $name: $it") }
        }
        val list = scan.weekList?.let { p -> runCatching { files.readText(p).lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } }.getOrNull() } ?: emptyList()
        val loose = scan.songs.filter { s -> s.folder.lowercase() !in inWeeks && s.id !in inWeeks }
        val freeplay = loose.flatMap { s -> (songOutputs[s.id] ?: emptyList()).mapNotNull { psychSongs[it] } }
        if (freeplay.isNotEmpty()) {
            val file = CodenameToPsychWeek.freeplayWeek("codename-freeplay", freeplay)
            write(file.path, file.text)
            order += "codename-freeplay"
            report.section("Freeplay", listOf("Songs outside every week (${freeplay.joinToString { it.name }}) were put in weeks/codename-freeplay.json, hidden from story mode, so they show up in Psych's freeplay."))
        }
        if (list.isNotEmpty() || freeplay.isNotEmpty()) {
            val names = (list.filter { it in order } + order.filter { it !in list }).distinct()
            write("weeks/weekList.txt", names.joinToString("\n") + "\n")
        }
    }

    private fun convertEvents(definitions: Map<String, EventDefinition>, eventCounts: Map<String, Int>) {
        val names = (customEvents.keys + scan.eventScripts.keys + definitions.keys).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        names.forEach { name ->
            val builtin = CodenameToPsychEvents.script(name)
            val modScript = scan.eventScripts[name]
            val def = definitions[name]
            val description = if (builtin != null) builtin.description else CodenameToPsychEvents.description(name, def?.params?.map { it.title } ?: emptyList())
            write("custom_events/$name.txt", description + "\n")
            if (builtin != null) {
                write("custom_events/$name.hx", builtin.script)
                if (name in CodenameToPsychEvents.needsCompat) compat = true
                if (modScript != null) convertScript(modScript, "scripts/${CodenameEvents.eventId(name)}Event.hx", CodenameScriptTarget(CodenameScope.EVENT, name),
                    CodenamePsychScriptContext(eventParamCounts = eventCounts, eventName = name))
            } else if (modScript != null) {
                convertScript(modScript, "custom_events/$name.hx", CodenameScriptTarget(CodenameScope.EVENT, name), CodenamePsychScriptContext(eventParamCounts = eventCounts, eventName = name))
            } else if (name in customEvents && name !in CodenameToPsychEvents.scripted) {
                report.warn("Event \"$name\" is kept in the charts, but the mod has no data/events/$name.hx, so nothing handles it in Psych.")
            }
        }
    }

    private fun convertNoteTypes(eventCounts: Map<String, Int>) {
        if (CodenameToPsychChart.HIDDEN_GF in noteTypes) {
            CodenameToPsychEvents.noteTypeScript(CodenameToPsychChart.HIDDEN_GF)?.let { write("custom_notetypes/${CodenameToPsychChart.HIDDEN_GF}.hx", it) }
        }
        scan.noteScripts.forEach { (name, path) ->
            val psychName = CodenameToPsychChart.noteTypeFor(name)
            if (psychName in CodenameToPsychChart.builtinNoteTypes) {
                report.section("Note types", listOf("data/notes/$name.hx was skipped: Psych's built-in \"$psychName\" note type does the same."))
                return@forEach
            }
            convertScript(path, "custom_notetypes/$psychName.hx", CodenameScriptTarget(CodenameScope.NOTE_TYPE, name), CodenamePsychScriptContext(eventParamCounts = eventCounts))
        }
        val unscripted = noteTypes.filter { it !in CodenameToPsychChart.builtinNoteTypes && it != CodenameToPsychChart.HIDDEN_GF && scan.noteScripts.keys.none { k -> CodenameToPsychChart.noteTypeFor(k) == it } }
        if (unscripted.isNotEmpty()) report.warn("Note types without a script (${unscripted.joinToString()}) only keep their name in Psych.")
    }

    private fun convertGlobalScripts(eventCounts: Map<String, Int>) {
        scan.globalScripts.forEach { path ->
            val stem = path.substringAfterLast('/').substringBeforeLast('.')
            convertScript(path, "scripts/$stem.hx", CodenameScriptTarget(CodenameScope.GLOBAL), CodenamePsychScriptContext(eventParamCounts = eventCounts))
        }
        scan.songs.forEach { song ->
            val outputs = songOutputs[song.id] ?: emptyList()
            song.scripts.forEach { path ->
                val stem = path.substringAfterLast('/').substringBeforeLast('.')
                outputs.forEach { id ->
                    convertScript(path, "data/$id/$stem.hx", CodenameScriptTarget(CodenameScope.SONG, song.id), CodenamePsychScriptContext(eventParamCounts = eventCounts))
                }
            }
        }
        val used = scan.libraryScripts.keys
        val stageExtensions = HashSet<String>()
        writtenPaths.filter { it.startsWith("stages/extensions/") }.forEach { stageExtensions += it.substringAfterLast('/').substringBeforeLast('.') }
        val leftovers = used.filter { it.substringAfterLast('/') !in stageExtensions && !it.substringAfterLast('/').equals("pixel", true) }
        if (leftovers.isNotEmpty()) report.warn("Library scripts only loaded with importScript (${leftovers.joinToString { "$it.hx" }}) were not converted.")
    }

    private fun write(path: String, text: String) {
        try {
            writer.writeText(path, text)
            if (writtenPaths.add(path)) written++
        } catch (e: Exception) {
            report.warn("Could not write $path: ${e.message}")
        }
    }

    private fun copy(source: String, target: String) {
        try {
            writer.copy(target) { files.open(source) }
            if (writtenPaths.add(target)) written++
        } catch (e: Exception) {
            report.warn("Could not copy $target: ${e.message}")
        }
    }

    private fun copyRelative(from: String, to: String) {
        val source = pathIndex["${scan.root}$from".lowercase()] ?: return
        copy(source, to)
    }

    private fun copyAssets() {
        scan.assets.forEach { path ->
            val target = scan.relative(path)
            step(target)
            copy(path, target)
        }
        scan.icon?.let { copy(it, "pack.png") }
    }

    private fun writePack(info: ModInfo) {
        step("pack.json")
        val description = listOf(info.description, if (info.author.isNotBlank()) "By ${info.author}" else "").filter { it.isNotBlank() }.joinToString("\n")
        write("pack.json", PsychWriters.pack(info.title.ifBlank { "Converted Mod" }, description))
        done = total
        onProgress(ModProgress(total, total, "Done"))
    }

    private fun step(label: String) {
        done++
        onProgress(ModProgress(done.coerceAtMost(total), total, label))
    }

    private fun limited(lines: List<String>, max: Int = 40): List<String> =
        if (lines.size <= max) lines else lines.take(max) + "…and ${lines.size - max} more"
}
