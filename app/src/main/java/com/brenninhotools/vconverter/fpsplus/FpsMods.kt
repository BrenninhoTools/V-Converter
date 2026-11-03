package com.brenninhotools.vconverter.fpsplus

import com.brenninhotools.vconverter.codename.CodenameModScan
import com.brenninhotools.vconverter.codename.CodenameToPsychMod
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.mod.ModConversionResult
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModScan
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.PsychLayout
import com.brenninhotools.vconverter.nmv.NmvAsPsychFiles
import com.brenninhotools.vconverter.nmv.NmvScan
import com.brenninhotools.vconverter.platform.InputStream
import com.brenninhotools.vconverter.reverse.VSliceModConverter
import com.brenninhotools.vconverter.reverse.VSliceModScan

class PsychToFpsWriter(private val target: ModWriter, private val info: ModInfo? = null, private val wholeMod: Boolean = true) : ModWriter {
    val report = ReportBuilder()
    var files = 0
        private set
    var scripts = 0
        private set

    private class Script(val path: String, val text: String, val lua: Boolean)

    private val written = HashSet<String>()
    private val charts = LinkedHashMap<String, MutableMap<String, String>>()
    private val songEvents = HashMap<String, String>()
    private val songScripts = LinkedHashMap<String, MutableList<Script>>()
    private val songFiles = mutableListOf<Pair<String, ByteArray>>()
    private val characters = LinkedHashMap<String, String>()
    private val characterScripts = LinkedHashMap<String, MutableList<Script>>()
    private val stages = LinkedHashMap<String, String>()
    private val stageScripts = LinkedHashMap<String, MutableList<Script>>()
    private val weeks = LinkedHashMap<String, String>()
    private var weekList: String? = null
    private val menuCharacters = LinkedHashMap<String, String>()
    private val menuImages = HashMap<String, ByteArray>()
    private val globalScripts = mutableListOf<Script>()
    private val eventScripts = LinkedHashMap<String, MutableList<Script>>()
    private val noteTypeScripts = LinkedHashMap<String, MutableList<Script>>()
    private val eventDescriptions = HashMap<String, String>()
    private var pack: String? = null
    private var hasIcon = false
    private val classNames = HashSet<String>()
    private val allPaths = HashSet<String>()

    private val baseStoryCharacters = setOf("bf", "dad", "darnell", "gf", "mom", "nene", "parents-xmas", "pico", "pico-player", "senpai", "spooky", "tankman")
    private val baseIcons = setOf("bf-lil", "bf-pixel", "bf", "dad", "darnell", "face-lil", "face", "gf-menu", "gf", "mom", "monster", "old-bf", "parents", "pico-lil", "pico-pixel", "pico", "senpai-angry", "senpai", "spirit", "spooky", "tankman-bloody", "tankman")

    private fun put(path: String, text: String) {
        if (!written.add(path.lowercase())) return
        files++
        target.writeText(path, text)
    }

    private fun putBytes(path: String, bytes: ByteArray) {
        if (!written.add(path.lowercase())) return
        files++
        target.copy(path) { com.brenninhotools.vconverter.platform.ByteArrayInputStream(bytes) }
    }

    private fun parts(path: String) = path.split('/').filter { it.isNotEmpty() }

    override fun writeText(path: String, text: String) = accept(path.trim('/'), text, null)

    override fun copy(path: String, source: () -> InputStream) {
        val clean = path.trim('/')
        val p = parts(clean)
        val name = p.lastOrNull() ?: return
        val ext = name.substringAfterLast('.', "").lowercase()
        val top = p.first().lowercase()
        val needsText = ext in setOf("json", "lua", "hx", "hxs", "hscript", "txt", "xml") && (top in setOf("data", "characters", "stages", "weeks", "scripts", "custom_events", "custom_notetypes") || (p.size == 1 && name.equals("pack.json", true)) || clean.startsWith("images/menucharacters/", true))
        val needsBytes = (p.size == 1 && name.equals("pack.png", true)) || clean.startsWith("images/menucharacters/", true) || clean.startsWith("images/icons/", true)
        if (needsText || needsBytes) {
            val bytes = source().use { it.readBytes() }
            accept(clean, bytes.decodeToString(), bytes)
            return
        }
        allPaths += clean.lowercase()
        val mapped = when {
            clean.startsWith("images/storymenu/", true) -> "images/menu/story/weeks/" + clean.substring("images/storymenu/".length)
            top == "shaders" -> "data/shaders/" + p.drop(1).joinToString("/")
            top == "songs" && p.size == 3 -> "songs/${FpsNames.songFolder(p[1])}/$name"
            else -> clean
        }
        if (!written.add(mapped.lowercase())) return
        files++
        target.copy(mapped, source)
    }

    private fun accept(clean: String, text: String, bytes: ByteArray?) {
        val p = parts(clean)
        if (p.isEmpty()) return
        allPaths += clean.lowercase()
        val name = p.last()
        val ext = name.substringAfterLast('.', "").lowercase()
        val stem = name.substringBeforeLast('.')
        val script = ext == "lua" || ext == "hx" || ext == "hxs" || ext == "hscript"
        when {
            p.size == 1 && name.equals("pack.json", true) -> pack = text
            p.size == 1 && name.equals("pack.png", true) -> {
                hasIcon = true
                putBytes("icon.png", bytes ?: text.encodeToByteArray())
            }
            p[0].equals("data", true) && p.size == 3 -> {
                val folder = p[1]
                when {
                    name.equals("events.json", true) -> songEvents[folder] = text
                    ext == "json" && (stem.equals(folder, true) || stem.startsWith("$folder-", true)) -> {
                        val diff = if (stem.equals(folder, true)) "normal" else stem.substring(folder.length + 1).lowercase()
                        charts.getOrPut(folder) { linkedMapOf() }[diff] = text
                    }
                    script -> songScripts.getOrPut(folder) { mutableListOf() } += Script(clean, text, ext == "lua")
                    else -> songFiles += "data/songs/${FpsNames.songFolder(folder)}/$name" to (bytes ?: text.encodeToByteArray())
                }
            }
            p[0].equals("songs", true) && p.size == 3 -> putBytes("songs/${FpsNames.songFolder(p[1])}/$name", bytes ?: text.encodeToByteArray())
            p[0].equals("characters", true) && p.size == 2 -> when {
                ext == "json" -> characters[stem] = text
                script -> characterScripts.getOrPut(stem) { mutableListOf() } += Script(clean, text, ext == "lua")
            }
            p[0].equals("stages", true) && p.size == 2 -> when {
                ext == "json" -> stages[stem] = text
                script -> stageScripts.getOrPut(stem) { mutableListOf() } += Script(clean, text, ext == "lua")
            }
            p[0].equals("weeks", true) -> if (name.equals("weekList.txt", true)) weekList = text else if (ext == "json") weeks[stem] = text
            p[0].equals("scripts", true) -> if (script) globalScripts += Script(clean, text, ext == "lua")
            p[0].equals("custom_events", true) -> when {
                script -> eventScripts.getOrPut(stem) { mutableListOf() } += Script(clean, text, ext == "lua")
                ext == "txt" -> eventDescriptions[stem] = text
            }
            p[0].equals("custom_notetypes", true) -> if (script) noteTypeScripts.getOrPut(stem) { mutableListOf() } += Script(clean, text, ext == "lua")
            clean.startsWith("images/menucharacters/", true) -> {
                val rest = clean.substring("images/menucharacters/".length)
                if (ext == "json" && !rest.contains('/')) menuCharacters[stem] = text else menuImages[rest.lowercase()] = bytes ?: text.encodeToByteArray()
            }
            clean.startsWith("images/icons/", true) && ext == "png" -> {
                val icon = stem.removePrefix("icon-")
                if (icon in baseIcons) return
                val source = bytes ?: return
                val converted = FpsIcon.threeFrames(source)
                putBytes("images/ui/healthIcons/$icon.png", converted ?: source)
                if (converted == null && FpsIcon.size(source)?.let { (w, h) -> w < h * 3 - 2 } == true) report.warn("Icon $icon could not be split into the 3 frames FPS Plus uses (neutral, losing, winning); add a third frame by hand.")
                if (icon.endsWith("-pixel")) put("images/ui/healthIcons/$icon.json", "{\n  \"antialiasing\": false\n}")
            }
            else -> if (bytes != null) putBytes(clean, bytes) else put(clean, text)
        }
    }

    private fun uniqueClass(base: String): String {
        var name = base
        var n = 2
        while (name in classNames || name in FpsNames.builtinCharacters || name in FpsNames.builtinStageClasses) name = base + n++
        classNames += name
        return name
    }

    private fun characterClass(id: String): String {
        FpsNames.builtinCharacter(id)?.let { return it }
        return characterClasses[id] ?: FpsNames.pascal(id)
    }

    private val characterClasses = HashMap<String, String>()

    private fun convertScript(script: Script): FpsParts {
        val parts = if (script.lua) LuaToFps().convert(script.text) else PsychHxToFps.convert(script.text)
        scripts++
        return parts
    }

    private fun reportIssues(target: String, source: String, parts: FpsParts) {
        val lines = parts.issues.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message }.distinct()
        if (lines.isNotEmpty()) report.section("$target (from $source)", lines)
    }

    private val eventHandlers = linkedSetOf<String>()

    private fun writeScriptClass(className: String, script: Script, folder: String = "data/scripts"): Boolean {
        val parts = convertScript(script)
        if (parts.isEmpty) {
            put("$folder/unconverted/${script.path.substringAfterLast('/')}", script.text)
            report.warn("${script.path} could not be converted (${parts.issues.firstOrNull()?.message ?: "nothing to run"}); it was kept in $folder/unconverted.")
            return false
        }
        if (parts.handlesEvents) eventHandlers += className
        put("$folder/$className.hxc", FpsClass.write(className, "Script", listOf(parts)))
        reportIssues("$folder/$className.hxc", script.path, parts)
        return true
    }

    fun finish(): Report {
        characters.keys.forEach { id ->
            if (FpsNames.builtinCharacter(id) == null) characterClasses[id] = uniqueClass(FpsNames.pascal(id))
        }
        val builtinUsed = mutableListOf<String>()
        characters.forEach { (id, text) ->
            val builtin = FpsNames.builtinCharacter(id)
            if (builtin != null) {
                builtinUsed += "$id → $builtin"
                return@forEach
            }
            val cls = characterClasses.getValue(id)
            val image = runCatching { JSONObject(text).optString("image") }.getOrDefault("")
            val atlas = "images/$image/animation.json".lowercase() in allPaths
            val death = characters.keys.firstOrNull { it == "$id-dead" }?.let { characterClass(it) }
            val out = runCatching { FpsFormats.character(id, text, cls, atlas, death) }.getOrElse {
                report.warn("characters/$id.json could not be read: ${it.message}")
                return@forEach
            }
            put("data/characters/$cls.hxc", out.text)
            out.warnings.forEach { report.warn(it) }
        }
        if (builtinUsed.isNotEmpty()) report.section("Characters FPS Plus already has", listOf("These ids use FPS Plus's own character classes instead of a converted copy: " + builtinUsed.joinToString(", ") + "."))

        val stageClasses = HashMap<String, String>()
        val stageIds = (stages.keys + stageScripts.keys + charts.values.flatMap { it.values }.mapNotNull { runCatching { val r = JSONObject(it); (r.optJSONObject("song") ?: r).optString("stage") }.getOrNull() }.filter { it.isNotBlank() }).distinct()
        stageIds.forEach { id ->
            val text = stages[id]
            val builtin = FpsNames.psychStages[id]
            if (builtin != null) {
                stageClasses[id] = builtin
                if (text != null || stageScripts.containsKey(id)) report.warn("Stage $id is a base game stage, so FPS Plus's own $builtin stage is used and the mod's copy was not converted.")
                return@forEach
            }
            if (text == null && !stageScripts.containsKey(id)) {
                stageClasses[id] = FpsNames.pascal(id)
                report.warn("Stage $id is used by a chart but the mod has no stages/$id.json; FPS Plus needs a data/stages class named ${FpsNames.pascal(id)}.")
                return@forEach
            }
            val cls = uniqueClass(FpsNames.stage(id, true))
            stageClasses[id] = cls
            val out = FpsFormats.stage(id, text, cls)
            val scriptParts = stageScripts[id].orEmpty().map { s -> convertScript(s).also { reportIssues("data/stages/$cls.hxc", s.path, it) } }
            val place = FpsParts(emptySet(), emptyList(), emptyList(), listOf(FpsFormats.placeMethod), emptyMap(), emptyList(), emptyList(), false)
            if (scriptParts.any { it.handlesEvents }) eventHandlers += "___STAGE_SCRIPT___"
            put("data/stages/$cls.hxc", FpsClass.write(cls, "BaseStage", listOf(place) + scriptParts, out.constructorLines, out.createPrefix, out.imports))
            out.warnings.forEach { report.warn(it) }
        }

        val characterScriptClasses = HashMap<String, String>()
        characterScripts.forEach { (id, list) ->
            list.forEachIndexed { i, s ->
                val cls = uniqueClass(FpsNames.pascal(id) + "Character" + if (i > 0) "${i + 1}" else "")
                if (writeScriptClass(cls, s)) characterScriptClasses[id] = cls
            }
        }

        val noteTypes = linkedSetOf<String>()
        val customEvents = linkedSetOf<String>()
        val songNames = LinkedHashMap<String, String>()
        charts.forEach { (folder, diffs) ->
            val id = FpsNames.songFolder(folder)
            val events = mutableListOf<JSONObject>()
            var name = folder
            val used = linkedSetOf<String>()
            diffs.entries.sortedBy { if (it.key == "normal") 0 else 1 }.forEachIndexed { index, (diff, text) ->
                val result = runCatching {
                    FpsFormats.chart(text, id, ::characterClass) { s -> stageClasses[s] ?: FpsNames.stage(s, false) }
                }.getOrElse {
                    report.warn("data/$folder: the $diff chart could not be read (${it.message}).")
                    return@forEachIndexed
                }
                if (diff !in setOf("easy", "normal", "hard")) report.warn("$id: FPS Plus only has easy, normal and hard; the $diff chart was saved as chart-$diff.json, which only an erect song can use.")
                put("data/songs/$id/chart-$diff.json", result.chart)
                if (index == 0) {
                    events += result.events
                    name = result.song
                }
                result.warnings.forEach { report.warn("$id ($diff): $it") }
                noteTypes += result.noteTypes
                customEvents += result.customEvents
                runCatching {
                    val song = JSONObject(text).let { r -> r.optJSONObject("song")?.takeIf { it.opt("notes") is JSONArray } ?: r }
                    listOf("player1", "player2", "gfVersion", "player3").forEach { k -> song.optString(k).takeIf { it.isNotBlank() }?.let { used += it } }
                }
            }
            songEvents[folder]?.let { text ->
                runCatching { FpsFormats.eventsJson(text, ::characterClass) }.getOrNull()?.let { (list, custom) ->
                    events += list
                    customEvents += custom
                }
            }
            put("data/songs/$id/events.json", FpsFormats.writeEvents(events))
            put("data/songs/$id/meta.json", FpsFormats.songMeta(name))
            songNames[id] = name
            val classes = mutableListOf<String>()
            songScripts[folder].orEmpty().forEach { s ->
                val cls = uniqueClass(FpsNames.pascal(id) + FpsNames.pascal(s.path.substringAfterLast('/').substringBeforeLast('.')) + "Song")
                if (writeScriptClass(cls, s)) classes += cls
            }
            used.mapNotNull { characterScriptClasses[it] }.forEach { classes += it }
            if (classes.isNotEmpty()) put("data/songs/$id/scripts.json", JSONObject().put("scripts", JSONArray().also { a -> classes.distinct().forEach { a.put(it) } }).toString(2))
        }
        songFiles.forEach { (path, bytes) -> putBytes(path, bytes) }
        songScripts.keys.filter { it !in charts }.forEach { folder -> report.warn("data/$folder has scripts but no charts; they were not converted.") }

        val globals = mutableListOf<String>()
        globalScripts.forEach { s ->
            val cls = uniqueClass(FpsNames.pascal(s.path.substringAfterLast('/').substringBeforeLast('.')) + "Script")
            if (writeScriptClass(cls, s)) globals += cls
        }
        eventScripts.forEach { (event, list) ->
            customEvents += event
            list.forEach { s ->
                val cls = uniqueClass(FpsNames.pascal(event) + "Event")
                if (writeScriptClass(cls, s)) globals += cls
            }
        }
        noteTypeScripts.forEach { (type, list) ->
            noteTypes += type
            list.forEach { s ->
                val cls = uniqueClass(FpsNames.pascal(type) + "NoteType")
                if (writeScriptClass(cls, s)) globals += cls
            }
        }
        if (globals.isNotEmpty()) put("append/data/scripts/globalScripts.txt", globals.joinToString("\n") + "\n")
        if (customEvents.isNotEmpty()) put("data/events/PsychEvents.hxc", eventsClass(customEvents))
        if (noteTypes.isNotEmpty()) put("data/notetypes/PsychNotes.hxc", notesClass(noteTypes))

        val order = weekList?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val freeplay = mutableListOf<Pair<String, String>>()
        weeks.forEach { (id, text) ->
            val index = order.indexOf(id).let { if (it < 0) order.size + weeks.keys.indexOf(id) else it }
            runCatching { FpsFormats.week(id, text, index, ::characterClass) }.onSuccess { (json, icons) ->
                put("data/weeks/$id.json", json)
                val root = JSONObject(text)
                if (!root.optBoolean("hideFreeplay", false)) {
                    val songs = root.optJSONArray("songs") ?: JSONArray()
                    for (i in 0 until songs.length()) songs.optJSONArray(i)?.let { freeplay += FpsNames.songFolder(it.optString(0)) to (icons.getOrNull(i) ?: "face") }
                }
            }.onFailure { report.warn("weeks/$id.json could not be read: ${it.message}") }
        }
        songNames.keys.filter { s -> freeplay.none { it.first == s } && weeks.isEmpty() }.forEach { freeplay += it to "face" }
        if (freeplay.isNotEmpty() && wholeMod) {
            val title = info?.title?.ifBlank { null } ?: "Mod"
            val patch = JSONArray()
            freeplay.distinctBy { it.first }.forEach { (song, icon) ->
                patch.put(JSONObject().put("op", "add").put("path", "/songs/-").put("value", JSONObject().put("song", song).put("icon", icon).put("categories", JSONArray().put("ALL").put(title))))
            }
            put("merge/data/freeplay/songList-bf.json", patch.toString(2))
        }

        menuCharacters.forEach { (id, text) ->
            if (id in baseStoryCharacters) return@forEach
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return@forEach
            val image = root.optString("image").lowercase()
            val png = menuImages["$image.png"]
            val xml = menuImages["$image.xml"]
            if (png == null || xml == null) {
                report.warn("Menu character $id: images/menucharacters/$image.png and .xml are needed for FPS Plus's story menu.")
                return@forEach
            }
            putBytes("images/menu/story/characters/$id.png", png)
            put("images/menu/story/characters/$id.xml", FpsFormats.renameAtlasAnims(xml.decodeToString(), root.optString("idle_anim"), root.optString("confirm_anim")))
            FpsFormats.storyCharacterOffsets(text)?.let { put("images/menu/story/characters/$id.json", it) }
        }

        if (wholeMod) {
            val packJson = pack?.let { runCatching { JSONObject(it) }.getOrNull() }
            val title = info?.title?.ifBlank { null } ?: packJson?.optString("name")?.ifBlank { null } ?: "Converted Mod"
            val description = info?.description?.ifBlank { null } ?: packJson?.optString("description") ?: ""
            put("meta.json", FpsFormats.modMeta(title, description, info?.author ?: "", info?.version ?: "1.0.0"))
        }
        return report.build()
    }

    private fun eventsClass(names: Set<String>): String {
        val sb = StringBuilder()
        sb.append("class PsychEvents extends Events\n{\n\n\toverride function defineEvents() {\n\t\thideFromDropdown = false;\n\n")
        names.forEach { n ->
            val desc = (eventDescriptions[n] ?: "Psych event $n").lines().firstOrNull { it.isNotBlank() }?.replace("\"", "'") ?: "Psych event $n"
            sb.append("\t\tregisterEvent({prefix: \"").append(n.replace("\"", "'")).append("\", eventFunction: psychEvent, editor: {description: \"").append(desc)
                .append("\", arguments: [{name: \"Value 1\", type: \"string\", value: \"\"}, {name: \"Value 2\", type: \"string\", value: \"\"}]}});\n")
        }
        sb.append("\t}\n\n\tfunction psychEvent(tag:String):Void{\n")
        sb.append("\t\tvar name:String = tag.split(\";\")[0];\n")
        sb.append("\t\tvar args = Events.getArgs(tag, [\"\", \"\"]);\n")
        sb.append("\t\tfor (key in [").append(eventHandlers.joinToString(", ") { "\"$it\"" }).append("]) {\n")
        sb.append("\t\t\tif (!playstate.scripts.exists(key)) continue;\n")
        sb.append("\t\t\ttry {\n\t\t\t\tplaystate.scripts.get(key).scriptCall(\"onEvent\", [name, args[0], args[1], Conductor.songPosition]);\n\t\t\t}\n\t\t\tcatch (e:Dynamic) {}\n")
        sb.append("\t\t}\n\t}\n\n}\n")
        return sb.toString()
    }

    private fun notesClass(types: Set<String>): String {
        val sb = StringBuilder()
        sb.append("class PsychNotes extends NoteType\n{\n\n\toverride function defineTypes():Void{\n")
        types.forEach { t ->
            val p = t.replace("\"", "'")
            sb.append(if (t == "Hurt Note") "\t\tregisterNoteType({prefix: \"$p\", noteHit: hurtHit, sustainHit: hurtHit, noteMiss: hurtMiss, sustainMiss: hurtMiss, editor: {description: \"Psych hurt note: hitting it costs health, missing it is fine.\"}});\n"
            else "\t\tregisterNoteType({prefix: \"$p\", editor: {description: \"Psych note type $p.\"}});\n")
        }
        sb.append("\t}\n")
        if ("Hurt Note" in types) {
            sb.append("\n\tfunction hurtHit(note:Note, character:Character){\n\t\tif (character == boyfriend) {\n\t\t\thealthAdjust = -0.3;\n\t\t\tif (character.canAutoAnim) character.playAnim(\"hurt\", true);\n\t\t}\n\t}\n")
            sb.append("\n\tfunction hurtMiss(note:Note, character:Character){\n\t\thealthAdjust = 0;\n\t}\n")
        }
        sb.append("\n}\n")
        return sb.toString()
    }
}

object ToFpsMod {
    private fun result(fps: PsychToFpsWriter, base: Report?, stats: List<Pair<String, Any>> = emptyList()): ModConversionResult {
        val finished = fps.finish()
        val report = ReportBuilder()
        stats.forEach { (label, value) -> report.stat(label, value) }
        report.stat("Scripts as FPS Plus classes", fps.scripts)
        report.section(
            "FPS Plus",
            listOf(
                "Charts are in data/songs/<song>/chart-<difficulty>.json (fpsplus_1) with events.json and meta.json; section focus became camFocusBf/camFocusDad events and Psych events became FPS Plus event tags.",
                "Characters and stages are .hxc classes in data/characters and data/stages; Psych positions are kept, so converted characters stand where Psych put them.",
                "Lua and HScript became Script classes in data/scripts, listed in each song's scripts.json or in append/data/scripts/globalScripts.txt; the Psych functions they use are written into each class.",
                "Songs are added to Freeplay with merge/data/freeplay/songList-bf.json, and meta.json carries the uid and api_version FPS Plus needs."
            )
        )
        val built = report.build()
        return ModConversionResult(fps.files, (base?.let { it + built } ?: built) + finished)
    }

    fun fromPsych(files: ModFiles, scan: ModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit = {}): ModConversionResult {
        val fps = PsychToFpsWriter(writer, info)
        val roots = listOf(scan.root) + scan.otherRoots
        val inside = files.paths.filter { p -> roots.any { p.startsWith(it) } && p !in scan.placeholders.values }
        inside.forEachIndexed { index, path ->
            progress(ModProgress(index, inside.size, path))
            val root = roots.filter { path.startsWith(it) }.maxByOrNull { it.length } ?: scan.root
            val rel = PsychLayout.normalize(path.removePrefix(root))
            if (rel.isNotEmpty()) fps.copy(rel) { files.open(path) }
        }
        progress(ModProgress(inside.size, inside.size, "Writing FPS Plus classes"))
        return result(fps, null, listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size))
    }

    fun fromNmv(files: ModFiles, scan: NmvScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit = {}): ModConversionResult {
        val view = NmvAsPsychFiles(files, scan)
        val fps = PsychToFpsWriter(writer, info)
        view.paths.forEachIndexed { index, path ->
            progress(ModProgress(index, view.paths.size, path))
            fps.copy(path) { view.open(path) }
        }
        view.warnings.distinct().forEach { fps.report.warn(it) }
        return result(fps, null, listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size))
    }

    fun fromVSlice(files: ModFiles, scan: VSliceModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit = {}): ModConversionResult {
        val fps = PsychToFpsWriter(writer, info)
        val base = VSliceModConverter(files, scan, fps, progress).convert(info)
        return result(fps, base.report)
    }

    fun fromCodename(files: ModFiles, scan: CodenameModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit = {}): ModConversionResult {
        val fps = PsychToFpsWriter(writer, info)
        val base = CodenameToPsychMod(files, scan, fps, progress).convert(info)
        return result(fps, base.report)
    }
}
