package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.character.PsychCharacter
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.psych.PsychEvent
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.mod.ModConversionResult
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModScan
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.readText
import com.brenninhotools.vconverter.script.ScriptIssue
import com.brenninhotools.vconverter.stage.LuaStageExtractor
import com.brenninhotools.vconverter.stage.PsychStage
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychWeek
import com.brenninhotools.vconverter.week.PsychWeekParser

class PsychToCodenameMod(
    private val files: ModFiles,
    private val scan: ModScan,
    private val writer: ModWriter,
    private val onProgress: (ModProgress) -> Unit = {}
) {
    private enum class Kind { GLOBAL, SONG, STAGE, CHARACTER, EVENT, NOTE_TYPE, OTHER }

    private data class ModScript(val path: String, val rel: String, val kind: Kind, val key: String, val stem: String, val lua: Boolean)

    private data class LoadedSong(val id: String, val sources: List<ChartSource>, val extraEvents: List<PsychEvent>, val audio: List<String>)

    private val report = ReportBuilder()
    private val pathIndex: Map<String, String> = files.paths.associateBy { it.lowercase() }
    private var written = 0
    private var done = 0
    private val writtenPaths = HashSet<String>()
    private val usedEvents = sortedMapOf<String, Int>(String.CASE_INSENSITIVE_ORDER)
    private val usedNoteTypes = sortedSetOf<String>()
    private val usage = HashMap<String, MutableMap<CharacterSlot, Int>>()
    private val songCharacters = LinkedHashMap<String, MutableSet<String>>()
    private val scripts: List<ModScript> = scan.scripts.mapNotNull(::classify)
    private val total = scan.songs.size + scan.characters.size + scan.stages.size + scan.weeks.size + scan.assets.size + scripts.size + 3
    private var converted = 0
    private val luaTags: Set<String> = scripts.filter { it.lua }.flatMap { s -> runCatching { tagPattern.findAll(files.readText(s.path)).map { it.groupValues[2] }.toList() }.getOrDefault(emptyList()) }.toSet()
    private var stageObjectNames: Set<String> = emptySet()

    companion object {
        private val tagPattern = Regex("""\b(makeLuaSprite|makeAnimatedLuaSprite|makeLuaText|makeFlxAnimateSprite|makeLuaBackdrop)\s*\(\s*["']([^"']+)["']""")
        private val scriptExtensions = setOf("lua", "hx", "hxs", "hscript")
    }

    fun convert(info: ModInfo): ModConversionResult {
        val characters = parseCharacters()
        val songs = loadSongs()
        val weeks = parseWeeks()
        val stageIds = convertStages()
        convertSongs(songs, characters, stageIds, weeks)
        convertCharacters(characters)
        convertWeeks(weeks, songs)
        convertEvents()
        convertNoteTypes()
        convertScripts(songs)
        copyAssets(songs)
        writeModpack(info)

        report.stat("Songs", songs.size)
        report.stat("Characters", characters.size)
        report.stat("Stages", stageIds.size)
        report.stat("Weeks", weeks.size)
        report.stat("Scripts", converted)
        report.stat("Assets", scan.assets.size)
        report.stat("Files", written)
        if (scan.placeholders.isNotEmpty()) report.section("Empty placeholder folders removed", scan.placeholders.map { (dir, file) -> "$dir/ (only ${file.substringAfterLast('/')})" })
        if (scan.ignored.isNotEmpty()) report.section("Other files skipped", limited(scan.ignored.map(scan::relative)))
        if (scan.otherRoots.isNotEmpty()) report.warn("The archive has more than one mod. Only \"${scan.root.ifEmpty { "/" }}\" was converted; also found: ${scan.otherRoots.joinToString()}.")
        return ModConversionResult(written, report.build())
    }

    private fun classify(path: String): ModScript? {
        val rel = scan.relative(path)
        val parts = rel.split('/')
        val name = parts.last()
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in scriptExtensions) return null
        val stem = name.substringBeforeLast('.')
        val top = parts.first().lowercase()
        val lua = ext == "lua"
        val kind = when {
            top == "scripts" -> Kind.GLOBAL
            top == "data" && parts.size == 3 -> Kind.SONG
            top == "stages" && parts.size == 2 -> Kind.STAGE
            top == "characters" && parts.size == 2 -> Kind.CHARACTER
            top == "custom_events" && parts.size == 2 -> Kind.EVENT
            top == "custom_notetypes" && parts.size == 2 -> Kind.NOTE_TYPE
            else -> Kind.OTHER
        }
        val key = when (kind) {
            Kind.SONG -> parts[1].lowercase()
            Kind.OTHER, Kind.GLOBAL -> stem
            else -> stem
        }
        return ModScript(path, rel, kind, key, stem, lua)
    }

    private fun scriptsOf(kind: Kind) = scripts.filter { it.kind == kind }

    private fun parseCharacters(): Map<String, PsychCharacter> {
        val result = LinkedHashMap<String, PsychCharacter>()
        scan.characters.forEach { path ->
            val id = path.substringAfterLast('/').substringBeforeLast('.')
            runCatching { PsychCharacterParser.parse(files.readText(path)) }
                .onSuccess { result[id] = it }
                .onFailure { report.warn("Character \"$id\" could not be read: ${it.message}") }
        }
        return result
    }

    private fun loadSongs(): List<LoadedSong> = scan.songs.mapNotNull { song ->
        val sources = song.charts.mapNotNull { (difficulty, path) ->
            runCatching { ChartSource(difficulty, PsychChartParser.parse(files.readText(path))) }
                .onFailure { report.warn("Chart ${scan.relative(path)} could not be read: ${it.message}") }
                .getOrNull()
        }
        if (sources.isEmpty()) return@mapNotNull null
        val events = song.events?.let { p ->
            runCatching { PsychChartParser.parseEventsOnly(files.readText(p)) }
                .onFailure { report.warn("Events for ${song.id} could not be read: ${it.message}") }
                .getOrNull()
        } ?: emptyList()
        LoadedSong(song.id, sources, events, song.audio)
    }

    private fun parseWeeks(): Map<String, PsychWeek> {
        val result = LinkedHashMap<String, PsychWeek>()
        scan.weeks.forEach { path ->
            val id = path.substringAfterLast('/').substringBeforeLast('.')
            runCatching { PsychWeekParser.parse(files.readText(path)) }
                .onSuccess { result[id] = it }
                .onFailure { report.warn("Week \"$id\" could not be read: ${it.message}") }
        }
        return result
    }

    private fun count(id: String, slot: CharacterSlot) {
        if (id.isBlank()) return
        val slots = usage.getOrPut(id) { mutableMapOf() }
        slots[slot] = (slots[slot] ?: 0) + 1
    }

    private fun convertStages(): Set<String> {
        val ids = LinkedHashSet<String>()
        val jsonStages = scan.stages.associateBy { it.substringAfterLast('/').substringBeforeLast('.') }
        val stageScripts = scriptsOf(Kind.STAGE).groupBy { it.stem }
        val names = HashSet<String>()
        (jsonStages.keys + stageScripts.keys).distinct().forEach { id ->
            step("Stage $id")
            try {
                var stage: PsychStage = jsonStages[id]?.let { PsychStageParser.parse(files.readText(it)) } ?: LuaStageExtractor.defaultStage()
                val luaScript = stageScripts[id]?.firstOrNull { it.lua }
                var skip: Set<Int> = emptySet()
                val luaNotes = mutableListOf<String>()
                if (luaScript != null) {
                    val props = LuaStageExtractor.extract(files.readText(luaScript.path), stage.objectNames)
                    stage = LuaStageExtractor.merge(stage, props)
                    skip = props.removedLines
                    if (!props.isEmpty) luaNotes += "${props.behind.size + props.front.size} sprite(s) created in stages/$id.lua were moved into the stage XML."
                    luaNotes += props.notes
                }
                if (jsonStages[id] == null) report.warn("Stage $id has no stages/$id.json; default character positions were used.")
                val result = PsychToCodenameStage.convert(id, stage, luaNotes)
                result.files.forEach { write(it.path, it.text) }
                result.report.warnings.forEach { report.warn("Stage $id: $it") }
                result.report.sections.forEach { report.section("Stage $id · ${it.title}", it.lines) }
                val objects = stage.objects?.map { it.name }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                names += objects
                val hooks = result.hooks.callbacks
                val sources = stageScripts[id] ?: emptyList()
                if (sources.isEmpty() && hooks.isNotEmpty()) {
                    write("data/stages/$id.hx", hookScript(hooks))
                } else {
                    sources.forEachIndexed { index, script ->
                        val target = if (index == 0) "data/stages/$id.hx" else "songs/$id-stage-${script.stem}.hx"
                        if (index > 0) report.warn("Stage $id has more than one script; ${script.rel} became a global script ($target).")
                        convertScript(script, target, CodenameScope.STAGE, knownTags = luaTags + objects, hooks = if (index == 0) hooks else emptyMap(), skip = if (script == luaScript) skip else emptySet())
                    }
                }
                ids += id
            } catch (e: Exception) {
                report.warn("Stage \"$id\" was not converted: ${e.message}")
            }
        }
        stageObjectNames = names
        return ids
    }

    private fun hookScript(hooks: Map<String, List<String>>): String = buildString {
        hooks.forEach { (name, lines) ->
            val signature = when (name) {
                "update", "postUpdate" -> "$name(elapsed:Float)"
                "beatHit" -> "beatHit(curBeat:Int)"
                "stepHit" -> "stepHit(curStep:Int)"
                "create", "postCreate", "destroy" -> "$name()"
                else -> "$name(event)"
            }
            if (isNotEmpty()) append('\n')
            append("function ").append(signature).append("\n{\n")
            lines.forEach { append("  ").append(it).append('\n') }
            append("}\n")
        }
    }

    private fun convertSongs(songs: List<LoadedSong>, characters: Map<String, PsychCharacter>, stageIds: Set<String>, weeks: Map<String, PsychWeek>) {
        val weekSongs = weeks.values.flatMap { it.songs }.associateBy { SongPath.format(it.name) }
        val customEvents = scriptsOf(Kind.EVENT).map { it.stem }.toSet() + scan.scripts.filter { scan.relative(it).lowercase().startsWith("custom_events/") && it.endsWith(".txt", true) }.map { it.substringAfterLast('/').substringBeforeLast('.') }
        songs.forEach { song ->
            step("Song ${song.id}")
            try {
                val main = (song.sources.firstOrNull { it.difficulty == "normal" } ?: song.sources.first()).song
                val audioNames = song.audio.map { it.substringAfterLast('/').substringBeforeLast('.').lowercase() }.toSet()
                val playerKey = characters[main.player1]?.vocalsFile?.takeIf { it.isNotBlank() } ?: "Player"
                val opponentKey = characters[main.player2]?.vocalsFile?.takeIf { it.isNotBlank() } ?: "Opponent"
                val weekSong = weekSongs[song.id] ?: weekSongs[SongPath.format(main.name)]
                val result = PsychToCodenameChart.convert(
                    song.sources,
                    song.extraEvents,
                    PsychCodenameChartOptions(
                        songFolder = song.id,
                        customStageIds = stageIds,
                        characterIcons = characters.mapValues { it.value.healthIcon },
                        characterColors = characters.mapValues { it.value.healthBarColor },
                        songColor = weekSong?.color,
                        songIcon = weekSong?.icon?.takeIf { it.isNotBlank() },
                        playerVocals = "-$playerKey".takeIf { "voices-${playerKey.lowercase()}" in audioNames },
                        opponentVocals = "-$opponentKey".takeIf { "voices-${opponentKey.lowercase()}" in audioNames },
                        customEventNames = customEvents
                    )
                )
                result.charts.forEach { (difficulty, json) -> write("songs/${song.id}/charts/$difficulty.json", json) }
                write("songs/${song.id}/meta.json", result.meta)
                result.events.forEach { (name, n) -> usedEvents[name] = (usedEvents[name] ?: 0) + n }
                usedNoteTypes += result.noteTypes
                val chars = songCharacters.getOrPut(song.id) { linkedSetOf() }
                song.sources.forEach { source ->
                    count(source.song.player1, CharacterSlot.PLAYER)
                    count(source.song.player2, CharacterSlot.OPPONENT)
                    count(source.song.girlfriend, CharacterSlot.GIRLFRIEND)
                    chars += listOf(source.song.player1, source.song.player2, source.song.girlfriend).filter { it.isNotBlank() }
                    (source.song.events + song.extraEvents).filter { it.name == "Change Character" }.forEach { chars += it.value2.trim() }
                }
                mergeDifficulties(result.report.warnings, result.charts.keys).forEach { report.warn("Song ${song.id}: $it") }
                result.report.sections.filter { it.title != "Difficulties" }.forEach { report.section("Song ${song.id} · ${it.title}", it.lines) }
                if (song.audio.none { it.substringAfterLast('/').startsWith("Inst", ignoreCase = true) }) report.warn("Song ${song.id}: no Inst audio found in songs/${song.id}/.")
                song.audio.forEach { copy(it, "songs/${song.id}/song/${it.substringAfterLast('/')}") }
            } catch (e: Exception) {
                report.warn("Song \"${song.id}\" was not converted: ${e.message}")
            }
        }
    }

    private fun mergeDifficulties(warnings: List<String>, difficulties: Set<String>): List<String> {
        val grouped = LinkedHashMap<String, MutableList<String>>()
        warnings.forEach { w ->
            val diff = difficulties.firstOrNull { w.startsWith("$it: ") }
            val key = if (diff == null) w else w.removePrefix("$diff: ")
            val list = grouped.getOrPut(key) { mutableListOf() }
            if (diff != null) list += diff
        }
        return grouped.map { (message, diffs) -> if (diffs.isEmpty() || diffs.size == difficulties.size && difficulties.size > 1) message else "${diffs.joinToString("/")}: $message" }
    }

    private fun convertCharacters(characters: Map<String, PsychCharacter>) {
        characters.forEach { (id, character) ->
            step("Character $id")
            try {
                val slot = usage[id]?.maxByOrNull { it.value }?.key ?: PsychToCodenameCharacter.defaultSlot(id, character)
                val images = character.atlasImages.ifEmpty { listOf(character.image) }
                val animate = images.any { img -> pathIndex["${scan.root}images/${img.trim()}/animation.json".lowercase()] != null }
                val result = PsychToCodenameCharacter.convert(id, character, slot, animate)
                result.files.forEach { write(it.path, it.text) }
                result.copies.forEach { copyRelative(it.from, it.to) }
                result.report.warnings.forEach { report.warn("Character $id: $it") }
                result.report.sections.filter { it.title == "Spritesheet" }.forEach { report.section("Character $id", it.lines) }
            } catch (e: Exception) {
                report.warn("Character \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertWeeks(weeks: Map<String, PsychWeek>, songs: List<LoadedSong>) {
        val menu = LinkedHashMap<String, PsychMenuCharacter>()
        scan.menuCharacters.forEach { path ->
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            runCatching { PsychWeekParser.parseMenuCharacter(files.readText(path)) }.onSuccess { menu[name] = it }
        }
        val listed = scan.weekList?.let { p -> runCatching { files.readText(p).lines().map { it.trim() }.filter { it.isNotEmpty() } }.getOrNull() } ?: emptyList()
        val order = (listed.filter { it in weeks } + weeks.keys.filter { it !in listed }).distinct()
        val freeplay = mutableListOf<String>()
        val hidden = HashSet<String>()
        val storyWeeks = mutableListOf<String>()
        val locked = mutableListOf<String>()
        order.forEach { id ->
            val week = weeks.getValue(id)
            step("Week $id")
            try {
                val result = PsychToCodenameWeek.convert(id, week)
                result.files.forEach { write(it.path, it.text) }
                if (result.files.isNotEmpty()) {
                    storyWeeks += id
                    result.copies.forEach { copyRelative(it.from, it.to) }
                }
                result.report.warnings.forEach { if (it.startsWith("Week locking")) locked += id else report.warn("Week $id: $it") }
                week.songs.forEach { s ->
                    val songId = SongPath.format(s.name)
                    if (week.hideFreeplay) hidden += songId else freeplay += songId
                }
                val missing = week.songs.filter { s -> songs.none { it.id == SongPath.format(s.name) } }
                if (missing.isNotEmpty()) report.warn("Week $id lists songs with no chart in this mod: ${missing.joinToString { it.name }}.")
            } catch (e: Exception) {
                report.warn("Week \"$id\" was not converted: ${e.message}")
            }
        }
        menu.forEach { (name, character) ->
            val result = PsychToCodenameWeek.menuCharacter(name, character)
            result.files.forEach { write(it.path, it.text) }
            result.report.warnings.forEach { report.warn("Week character $name: $it") }
        }
        if (locked.isNotEmpty()) report.warn("Week locking (weekBefore/startUnlocked) has no Codename equivalent, so these weeks are always unlocked: ${locked.joinToString()}.")
        if (storyWeeks.isNotEmpty()) write("data/weeks/weeks.txt", storyWeeks.joinToString("\n") + "\n")
        val loose = songs.map { it.id }.filter { it !in freeplay && it !in hidden }
        val list = (freeplay + loose).distinct().filter { id -> songs.any { it.id == id } }
        if (list.isNotEmpty()) write("data/config/freeplaySonglist.txt", list.joinToString("\n") + "\n")
        if (hidden.isNotEmpty()) report.section("Freeplay", listOf("Songs hidden from freeplay in Psych (${hidden.joinToString()}) were left out of data/config/freeplaySonglist.txt."))
    }

    private fun description(name: String): String? {
        val path = pathIndex["${scan.root}custom_events/$name.txt".lowercase()] ?: return null
        return runCatching { files.readText(path) }.getOrNull()
    }

    private fun convertEvents() {
        val modEvents = scriptsOf(Kind.EVENT).groupBy { it.stem }
        val names = (usedEvents.keys + modEvents.keys).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        val handled = mutableListOf<String>()
        names.forEach { name ->
            val sources = modEvents[name] ?: modEvents.entries.firstOrNull { it.key.equals(name, true) }?.value ?: emptyList()
            val builtin = PsychToCodenameEvents.script(name)
            if (sources.isEmpty() && builtin == null) return@forEach
            write("data/events/$name.json", PsychToCodenameEvents.definition(name, description(name)?.let { PsychToCodenameEvents.titles(it) } ?: emptyList()))
            if (sources.isEmpty()) {
                write("data/events/$name.hx", builtin!!)
                handled += "\"$name\" is recreated by data/events/$name.hx"
                return@forEach
            }
            sources.forEachIndexed { index, script ->
                val target = if (index == 0) "data/events/$name.hx" else "data/events/$name-${script.rel.substringAfterLast('.')}.hx"
                if (index > 0) report.warn("Event \"$name\" has both a Lua and an HScript version; ${script.rel} went to $target, which Codename does not load on its own.")
                convertScript(script, target, CodenameScope.EVENT, knownTags = luaTags - stageObjectNames)
            }
            if (builtin != null) handled += "\"$name\" uses the mod's own custom_events script instead of the built-in recreation"
        }
        if (handled.isNotEmpty()) report.section("Events", handled)
        val unhandled = usedEvents.keys.filter { it !in names || (PsychToCodenameEvents.script(it) == null && modEvents.keys.none { k -> k.equals(it, true) }) }
            .filter { it !in PsychToCodenameChart.stageEvents }
        if (unhandled.isNotEmpty()) report.warn("Events with no script in this mod (${unhandled.joinToString()}) are kept in the charts but nothing handles them in Codename.")
    }

    private fun convertNoteTypes() {
        val modTypes = scriptsOf(Kind.NOTE_TYPE).groupBy { PsychToCodenameChart.noteTypeFor(it.stem) }
        val notes = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val hurt = listOf("images/HURTNOTE_assets", "images/noteSkins/HURTNOTE_assets").firstOrNull { pathIndex["${scan.root}$it.png".lowercase()] != null }?.removePrefix("images/")
        (usedNoteTypes + modTypes.keys).distinct().forEach { name ->
            val sources = modTypes[name] ?: emptyList()
            if (sources.isNotEmpty()) {
                sources.forEachIndexed { index, script ->
                    val target = if (index == 0) "data/notes/$name.hx" else "songs/notetype-${SongPath.format(name)}-${script.rel.substringAfterLast('.')}.hx"
                    if (index > 0) report.warn("Note type \"$name\" has more than one script; ${script.rel} became a global script ($target).")
                    convertScript(script, target, CodenameScope.NOTE_TYPE, knownTags = luaTags - stageObjectNames)
                }
                return@forEach
            }
            val builtin = PsychToCodenameEvents.noteTypeScript(name, hurt)
            if (builtin != null) {
                write("data/notes/$name.hx", builtin)
                notes += "\"$name\" is recreated by data/notes/$name.hx" + if (name == "Hurt Note" && hurt == null) " (tinted dark, since the mod has no HURTNOTE_assets image)" else ""
            } else if (name !in setOf("Alt Anim Note", "No Anim Note")) {
                missing += name
            }
        }
        if (notes.isNotEmpty()) report.section("Note types", notes)
        if (missing.isNotEmpty()) report.warn("Note types with no script in this mod only keep their name: ${missing.joinToString()}.")
    }

    private fun convertScripts(songs: List<LoadedSong>) {
        val tags = luaTags - stageObjectNames
        scriptsOf(Kind.GLOBAL).forEach { script ->
            convertScript(script, "songs/${uniqueStem(script)}.hx", CodenameScope.GLOBAL, knownTags = tags)
        }
        val songIds = songs.map { it.id }.toSet()
        scriptsOf(Kind.SONG).forEach { script ->
            val folder = songIds.firstOrNull { it == script.key } ?: script.key
            if (folder !in songIds) report.warn("${script.rel} belongs to a song with no chart; it was still converted.")
            convertScript(script, "songs/$folder/scripts/${uniqueStem(script)}.hx", CodenameScope.SONG, knownTags = tags)
        }
        val characterScripts = scriptsOf(Kind.CHARACTER)
        if (characterScripts.isNotEmpty()) {
            val notes = mutableListOf<String>()
            characterScripts.forEach { script ->
                val inSongs = songCharacters.filterValues { script.stem in it }.keys
                if (inSongs.isEmpty()) {
                    convertScript(script, "songs/character-${uniqueStem(script)}.hx", CodenameScope.GLOBAL, knownTags = tags)
                    notes += "${script.rel} → songs/character-${uniqueStem(script)}.hx (no song uses this character, so it runs in every song)"
                } else {
                    inSongs.forEach { id -> convertScript(script, "songs/$id/scripts/character-${uniqueStem(script)}.hx", CodenameScope.SONG, knownTags = tags, quiet = id != inSongs.first()) }
                    notes += "${script.rel} → songs/<song>/scripts/character-${uniqueStem(script)}.hx in ${inSongs.joinToString()}"
                }
            }
            report.section("Character scripts", listOf("Psych character scripts run like song scripts, so they were placed in the songs that use the character (Codename's data/characters/ scripts run inside the character instead).") + notes)
        }
        val others = scriptsOf(Kind.OTHER)
        if (others.isNotEmpty()) report.warn("Scripts outside Psych's script folders were skipped: ${others.joinToString { it.rel }}.")
    }

    private fun uniqueStem(script: ModScript): String {
        val same = scripts.count { it.kind == script.kind && it.key == script.key && it.stem == script.stem }
        return if (same > 1) "${script.stem}-${script.rel.substringAfterLast('.')}" else script.stem
    }

    private fun convertScript(script: ModScript, target: String, scope: CodenameScope, knownTags: Set<String> = emptySet(), hooks: Map<String, List<String>> = emptyMap(), skip: Set<Int> = emptySet(), quiet: Boolean = false) {
        if (!quiet) step("Script ${script.rel.substringAfterLast('/')}")
        try {
            val source = files.readText(script.path)
            val result = if (script.lua) LuaToCodename(scope, knownTags, hooks, skip).convert(source) else PsychHxToCodename(scope, hooks).convert(source)
            if (result.source.isBlank()) {
                report.warn("${script.rel} could not be parsed and was skipped.")
                issues(script, result.issues)
                return
            }
            write(target, result.source)
            if (!quiet) {
                converted++
                issues(script, result.issues)
            }
        } catch (e: Exception) {
            report.warn("Script ${script.rel} was not converted: ${e.message}")
        }
    }

    private fun issues(script: ModScript, list: List<ScriptIssue>) {
        if (list.isEmpty()) return
        report.section("Script · ${script.rel}", limited(list.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message }))
    }

    private fun copyAssets(songs: List<LoadedSong>) {
        val moved = songs.flatMap { it.audio }.toSet()
        scan.assets.forEach { path ->
            val target = scan.relative(path)
            step(target)
            if (path in moved) return@forEach
            copy(path, target)
        }
        scan.packIcon?.let { copy(it, "icon.png") }
    }

    private fun writeModpack(info: ModInfo) {
        step("modpack.ini")
        write("data/config/modpack.ini", CodenameWriters.modpack(info.title.ifBlank { "Converted Mod" }, info.description, info.author, info.version))
        done = total
        onProgress(ModProgress(total, total, "Done"))
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

    private fun step(label: String) {
        done++
        onProgress(ModProgress(done.coerceAtMost(total), total, label))
    }

    private fun limited(lines: List<String>, max: Int = 40): List<String> =
        if (lines.size <= max) lines else lines.take(max) + "…and ${lines.size - max} more"
}
