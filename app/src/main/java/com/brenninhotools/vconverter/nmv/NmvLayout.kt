package com.brenninhotools.vconverter.nmv

import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.readText
import com.brenninhotools.vconverter.platform.ByteArrayInputStream
import com.brenninhotools.vconverter.platform.InputStream

enum class NmvKind { COPY, CHART, EVENTS, CHARACTER, STAGE, HSCRIPT, LUA, META }

data class NmvMapping(val path: String, val kind: NmvKind)

object NmvPaths {
    private fun parts(rel: String) = rel.split('/').filter { it.isNotEmpty() }

    private val audio = setOf("ogg", "mp3", "wav")

    private fun psychVoice(name: String): String = when {
        name.startsWith("Voices-player.", true) -> "Voices-Player." + name.substringAfterLast('.')
        name.startsWith("Voices-opp.", true) -> "Voices-Opponent." + name.substringAfterLast('.')
        else -> name
    }

    private fun nmvVoice(name: String): String = when {
        name.startsWith("Voices-Player.", true) -> "Voices-player." + name.substringAfterLast('.')
        name.startsWith("Voices-Opponent.", true) -> "Voices-opp." + name.substringAfterLast('.')
        else -> name
    }

    private fun isScript(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("hx", "hxs", "hscript")

    private val menuFolders = listOf(
        "images/menus/story/characters/" to "images/menucharacters/",
        "images/menus/story/weeks/" to "images/storymenu/",
        "images/menus/story/bgs/" to "images/menubackgrounds/",
        "images/menus/story/difficulties/" to "images/menudifficulties/"
    )

    fun toPsych(rel: String): NmvMapping? {
        val p = parts(rel)
        if (p.isEmpty()) return null
        val lower = rel.lowercase()
        val name = p.last()
        val ext = name.substringAfterLast('.', "").lowercase()
        if (p.size == 1 && name.equals("meta.json", true)) return NmvMapping("pack.json", NmvKind.META)
        if (p[0].equals("songs", true) && p.size >= 3) {
            val song = p[1]
            if (p.size == 4 && p[2].equals("charts", true) && ext == "json") {
                val diff = name.substringBeforeLast('.')
                if (diff.equals("events", true)) return NmvMapping("data/$song/events.json", NmvKind.EVENTS)
                val file = if (diff.equals("normal", true)) song else "$song-${diff.lowercase()}"
                return NmvMapping("data/$song/$file.json", NmvKind.CHART)
            }
            if (p.size == 3 && name.equals("meta.json", true)) return null
            if (p.size == 4 && p[2].equals("audio", true) && ext in audio) return NmvMapping("songs/$song/${psychVoice(name)}", NmvKind.COPY)
            if (p.size == 3 && ext in audio) return NmvMapping("songs/$song/${psychVoice(name)}", NmvKind.COPY)
            if (isScript(name) && (p.size == 3 || (p.size == 4 && p[2].equals("scripts", true)))) return NmvMapping("data/$song/${name.substringBeforeLast('.')}.hx", NmvKind.HSCRIPT)
            if (p[2].equals("charts", true)) return null
            return NmvMapping(rel, NmvKind.COPY)
        }
        if (p[0].equals("data", true) && p.size >= 3) {
            val folder = p[1].lowercase()
            val rest = p.drop(2).joinToString("/")
            when (folder) {
                "characters" -> return when {
                    p.size == 3 && ext == "json" -> NmvMapping("characters/$name", NmvKind.CHARACTER)
                    p.size == 3 && isScript(name) -> NmvMapping("characters/${name.substringBeforeLast('.')}.hx", NmvKind.HSCRIPT)
                    else -> null
                }
                "stages" -> {
                    if (p.size == 3 && ext == "json") return NmvMapping("stages/$name", NmvKind.STAGE)
                    if (p.size == 3 && isScript(name)) return NmvMapping("stages/${name.substringBeforeLast('.')}.hx", NmvKind.HSCRIPT)
                    if (p.size == 4 && name.equals("data.json", true)) return NmvMapping("stages/${p[2]}.json", NmvKind.STAGE)
                    if (p.size == 4 && name.substringBeforeLast('.').equals("script", true) && isScript(name)) return NmvMapping("stages/${p[2]}.hx", NmvKind.HSCRIPT)
                    return null
                }
                "weeks" -> return NmvMapping("weeks/$rest", NmvKind.COPY)
                "scripts" -> return if (isScript(name)) NmvMapping("scripts/${rest.substringBeforeLast('.')}.hx", NmvKind.HSCRIPT) else NmvMapping("scripts/$rest", NmvKind.COPY)
                "events" -> return when {
                    isScript(name) -> NmvMapping("custom_events/${rest.substringBeforeLast('.')}.hx", NmvKind.HSCRIPT)
                    ext == "txt" -> NmvMapping("custom_events/$rest", NmvKind.COPY)
                    else -> null
                }
                "notetypes" -> return if (isScript(name)) NmvMapping("custom_notetypes/${rest.substringBeforeLast('.')}.hx", NmvKind.HSCRIPT) else null
                "noteskins", "freeplay.json" -> return null
                "dialogue" -> return if (p.size == 4) NmvMapping("data/${p[2]}/$name", NmvKind.COPY) else null
            }
        }
        if (p[0].equals("data", true) && p.size == 2 && name.equals("freeplay.json", true)) return null
        if (p[0].equals("stages", true) && p.size == 3 && name.equals("data.json", true)) return NmvMapping("stages/${p[1]}.json", NmvKind.STAGE)
        if (p[0].equals("stages", true) && p.size == 3 && isScript(name)) return NmvMapping("stages/${p[1]}.hx", NmvKind.HSCRIPT)
        if (p[0].equals("scripts", true) && isScript(name)) return NmvMapping(rel.substringBeforeLast('.') + ".hx", NmvKind.HSCRIPT)
        menuFolders.forEach { (nmv, psych) -> if (lower.startsWith(nmv)) return NmvMapping(psych + rel.substring(nmv.length), NmvKind.COPY) }
        return NmvMapping(rel, NmvKind.COPY)
    }

    fun fromPsych(rel: String): NmvMapping? {
        val p = parts(rel)
        if (p.isEmpty()) return null
        val lower = rel.lowercase()
        val name = p.last()
        val ext = name.substringAfterLast('.', "").lowercase()
        val stem = name.substringBeforeLast('.')
        val script = ext == "lua" || ext == "hx" || ext == "hxs" || ext == "hscript"
        val scriptKind = if (ext == "lua") NmvKind.LUA else NmvKind.HSCRIPT
        if (p.size == 1 && name.equals("pack.json", true)) return NmvMapping("meta.json", NmvKind.META)
        if (p.size == 1 && name.equals("pack.png", true)) return NmvMapping("images/pack.png", NmvKind.COPY)
        when (p[0].lowercase()) {
            "data" -> if (p.size == 3) {
                val song = NmvFormats.sanitize(p[1])
                val folder = p[1]
                if (name.equals("events.json", true)) return NmvMapping("songs/$song/charts/events.json", NmvKind.EVENTS)
                if (ext == "json" && (stem.equals(folder, true) || stem.startsWith("$folder-", true))) {
                    val diff = if (stem.equals(folder, true)) "normal" else NmvFormats.sanitize(stem.substring(folder.length + 1))
                    return NmvMapping("songs/$song/charts/$diff.json", NmvKind.CHART)
                }
                if (script) return NmvMapping("songs/$song/$stem.hx", scriptKind)
                if (name.contains("dialogue", true)) return NmvMapping("data/dialogue/$song/$name", NmvKind.COPY)
                return NmvMapping("songs/$song/$name", NmvKind.COPY)
            }
            "songs" -> if (p.size == 3) {
                val song = NmvFormats.sanitize(p[1])
                return if (ext in audio) NmvMapping("songs/$song/audio/${nmvVoice(name)}", NmvKind.COPY) else NmvMapping("songs/$song/$name", NmvKind.COPY)
            }
            "characters" -> if (p.size == 2) return when {
                ext == "json" -> NmvMapping("data/characters/$name", NmvKind.CHARACTER)
                script -> NmvMapping("data/characters/$stem.hx", scriptKind)
                else -> NmvMapping("data/characters/$name", NmvKind.COPY)
            }
            "stages" -> if (p.size == 2) return when {
                ext == "json" -> NmvMapping("data/stages/$stem/data.json", NmvKind.STAGE)
                script -> NmvMapping("data/stages/$stem/script.hx", scriptKind)
                else -> NmvMapping("data/stages/$stem/$name", NmvKind.COPY)
            }
            "weeks" -> return NmvMapping("data/weeks/" + p.drop(1).joinToString("/"), NmvKind.COPY)
            "scripts" -> return if (script) NmvMapping("data/scripts/" + p.drop(1).joinToString("/").substringBeforeLast('.') + ".hx", scriptKind) else NmvMapping("data/scripts/" + p.drop(1).joinToString("/"), NmvKind.COPY)
            "custom_events" -> return if (script) NmvMapping("data/events/" + p.drop(1).joinToString("/").substringBeforeLast('.') + ".hx", scriptKind) else NmvMapping("data/events/" + p.drop(1).joinToString("/"), NmvKind.COPY)
            "custom_notetypes" -> return if (script) NmvMapping("data/notetypes/" + p.drop(1).joinToString("/").substringBeforeLast('.') + ".hx", scriptKind) else if (ext == "txt") null else NmvMapping("data/notetypes/" + p.drop(1).joinToString("/"), NmvKind.COPY)
        }
        menuFolders.forEach { (nmv, psych) -> if (lower.startsWith(psych)) return NmvMapping(nmv + rel.substring(psych.length), NmvKind.COPY) }
        return NmvMapping(rel, NmvKind.COPY)
    }
}

data class NmvScan(
    val root: String,
    val songs: List<String>,
    val characters: List<String>,
    val stages: List<String>,
    val weeks: List<String>,
    val scripts: Int,
    val assets: Int,
    val meta: String?
) {
    val isEmpty: Boolean get() = songs.isEmpty() && characters.isEmpty() && stages.isEmpty() && weeks.isEmpty() && scripts == 0 && assets == 0
}

object NmvModScanner {
    private val markers = listOf("songs/", "data/", "images/", "music/", "sounds/", "shaders/", "videos/", "fonts/")

    private fun rootOf(path: String): String? {
        var best: String? = null
        markers.forEach { m ->
            val idx = if (path.startsWith(m, true)) 0 else path.lowercase().indexOf("/$m").let { if (it >= 0) it + 1 else -1 }
            if (idx >= 0 && (best == null || idx < best!!.length)) best = path.substring(0, idx)
        }
        if (path.endsWith("meta.json", true) && path.count { it == '/' } <= 1) {
            val r = path.substring(0, path.length - "meta.json".length)
            if (best == null || r.length < best!!.length) best = r
        }
        return best
    }

    fun scan(files: ModFiles): NmvScan {
        val counts = HashMap<String, Int>()
        files.paths.forEach { p -> rootOf(p)?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        val root = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length }).firstOrNull()?.key ?: ""
        val rels = files.paths.filter { it.startsWith(root) }.map { it.removePrefix(root) }
        val songs = rels.mapNotNull { r -> Regex("^songs/([^/]+)/charts/[^/]+\\.json$", RegexOption.IGNORE_CASE).find(r)?.groupValues?.get(1) }.distinct().sorted()
        val characters = rels.filter { Regex("^data/characters/[^/]+\\.json$", RegexOption.IGNORE_CASE).matches(it) }
        val stages = rels.mapNotNull { r ->
            Regex("^data/stages/([^/]+)/data\\.json$", RegexOption.IGNORE_CASE).find(r)?.groupValues?.get(1)
                ?: Regex("^data/stages/([^/]+)\\.json$", RegexOption.IGNORE_CASE).find(r)?.groupValues?.get(1)
        }.distinct()
        val weeks = rels.filter { Regex("^data/weeks/[^/]+\\.json$", RegexOption.IGNORE_CASE).matches(it) }
        val scripts = rels.count { it.substringAfterLast('.', "").lowercase() in setOf("hx", "hxs", "hscript") }
        val assets = rels.count { r -> listOf("images/", "sounds/", "music/", "shaders/", "videos/", "fonts/").any { r.startsWith(it, true) } }
        val meta = rels.firstOrNull { it.equals("meta.json", true) }?.let { root + it }
        return NmvScan(root, songs, characters, stages, weeks, scripts, assets, meta)
    }

    fun readInfo(files: ModFiles, scan: NmvScan): ModInfo? {
        val path = scan.meta ?: return null
        val json = runCatching { JSONObject(files.readText(path)) }.getOrNull() ?: return null
        return ModInfo(json.optString("name"), json.optString("description"), "")
    }
}

class NmvAsPsychFiles(private val files: ModFiles, private val scan: NmvScan) : ModFiles {
    private val sources = LinkedHashMap<String, Pair<String, NmvKind>>()
    private val cache = HashMap<String, ByteArray>()
    val warnings = mutableListOf<String>()

    init {
        files.paths.filter { it.startsWith(scan.root) }.forEach { full ->
            val mapping = NmvPaths.toPsych(full.removePrefix(scan.root)) ?: return@forEach
            if (mapping.path !in sources) sources[mapping.path] = full to mapping.kind
        }
        val meta = scan.meta
        if (meta != null) {
            val icon = runCatching { JSONObject(files.readText(meta)).optString("iconFile") }.getOrNull()
            val iconPath = icon?.takeIf { it.isNotBlank() }?.let { scan.root + "images/$it.png" }
            if (iconPath != null && iconPath in files.paths && "pack.png" !in sources) sources["pack.png"] = iconPath to NmvKind.COPY
        }
    }

    override val paths: List<String> = sources.keys.toList()

    private fun transformed(path: String): ByteArray? {
        val (source, kind) = sources[path] ?: return null
        if (kind == NmvKind.COPY || kind == NmvKind.EVENTS) return null
        cache[path]?.let { return it }
        val text = files.readText(source)
        val out = runCatching {
            when (kind) {
                NmvKind.CHART -> NmvFormats.chartToPsych(text).also { r -> r.warnings.forEach { warnings += "$path: $it" } }.text
                NmvKind.CHARACTER -> NmvFormats.characterToPsych(text).also { r -> r.warnings.forEach { warnings += "$path: $it" } }.text
                NmvKind.STAGE -> NmvFormats.stageToPsych(text).also { r -> r.warnings.forEach { warnings += "$path: $it" } }.text
                NmvKind.HSCRIPT -> NmvScripts.nmvHxToPsych(text).also { r -> r.issues.forEach { warnings += "$path: ${it.message}" } }.source
                NmvKind.META -> NmvFormats.metaToPack(text)
                else -> text
            }
        }.getOrElse {
            warnings += "$path: could not be converted (${it.message}); the original was kept."
            text
        }
        return out.encodeToByteArray().also { cache[path] = it }
    }

    override fun open(path: String): InputStream {
        transformed(path)?.let { return ByteArrayInputStream(it) }
        val source = sources[path]?.first ?: throw IllegalArgumentException("Missing $path")
        return files.open(source)
    }

    override fun size(path: String): Long {
        transformed(path)?.let { return it.size.toLong() }
        val source = sources[path]?.first ?: return 0
        return files.size(source)
    }
}

class PsychToNmvWriter(private val target: ModWriter, private val info: ModInfo? = null) : ModWriter {
    val report = ReportBuilder()
    private val written = HashSet<String>()
    private val metaSongs = HashSet<String>()
    private var pack: String? = null
    private var icon = false
    var files = 0
        private set
    var scripts = 0
        private set

    private fun put(path: String, text: String) {
        written += path.lowercase()
        files++
        target.writeText(path, text)
    }

    private fun free(path: String): String {
        if (path.lowercase() !in written) return path
        val stem = path.substringBeforeLast('.')
        val ext = path.substringAfterLast('.', "")
        var n = 2
        while ("$stem-$n.$ext".lowercase() in written) n++
        return "$stem-$n.$ext"
    }

    private fun scriptSlot(path: String): String {
        if (path.lowercase() !in written) return path
        val alt = free(path)
        val single = path.startsWith("data/stages/") || path.startsWith("data/events/") || path.startsWith("data/notetypes/") || path.startsWith("data/characters/")
        if (single) report.warn("$path already exists, so the second script was saved as $alt; NightmareVision only loads $path, so merge them by hand.")
        return alt
    }

    override fun writeText(path: String, text: String) {
        val clean = path.trim('/')
        val mapping = NmvPaths.fromPsych(clean) ?: return
        when (mapping.kind) {
            NmvKind.COPY, NmvKind.EVENTS -> {
                if (mapping.path.lowercase() in written) return
                put(mapping.path, text)
            }
            NmvKind.META -> pack = text
            NmvKind.CHART -> {
                val folder = mapping.path.split('/')[1]
                val result = runCatching { NmvFormats.chartToNmv(text, folder) }.getOrElse {
                    report.warn("$clean: ${it.message}; the chart was copied without changes.")
                    NmvText(text)
                }
                result.warnings.forEach { report.warn("$clean: $it") }
                put(mapping.path, result.text)
                if (metaSongs.add(folder)) put("songs/$folder/meta.json", NmvFormats.songMeta(text, folder))
            }
            NmvKind.CHARACTER -> put(mapping.path, runCatching { NmvFormats.characterToNmv(text).text }.getOrDefault(text))
            NmvKind.STAGE -> {
                val result = runCatching { NmvFormats.stageToNmv(text) }.getOrElse { NmvText(text) }
                result.warnings.forEach { report.warn("$clean: $it") }
                put(mapping.path, result.text)
            }
            NmvKind.HSCRIPT -> {
                val result = NmvScripts.psychHxToNmv(text)
                val slot = scriptSlot(mapping.path)
                put(slot, result.source)
                scripts++
                if (result.issues.isNotEmpty()) report.section("$slot (from $clean)", result.issues.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message })
            }
            NmvKind.LUA -> {
                val result = LuaToNmv().convert(text)
                val slot = scriptSlot(mapping.path)
                if (result.source.isBlank()) {
                    val keep = free(slot.substringBeforeLast('.') + ".lua")
                    put(keep, text)
                    report.warn("$clean could not be converted (${result.issues.firstOrNull()?.message ?: "syntax error"}); the Lua file was kept as $keep, which NightmareVision does not run.")
                    return
                }
                put(slot, result.source)
                scripts++
                val lines = result.issues.map { if (it.line > 0) "line ${it.line}: ${it.message}" else it.message }
                if (lines.isNotEmpty()) report.section("$slot (from $clean)", lines)
            }
        }
    }

    override fun copy(path: String, source: () -> InputStream) {
        val clean = path.trim('/')
        val mapping = NmvPaths.fromPsych(clean) ?: return
        if (mapping.kind != NmvKind.COPY) {
            writeText(clean, source().use { it.readBytes().decodeToString() })
            return
        }
        if (mapping.path.lowercase() in written) return
        if (mapping.path.equals("images/pack.png", true)) icon = true
        written += mapping.path.lowercase()
        files++
        target.copy(mapping.path, source)
    }

    fun finish() {
        val text = pack?.let { NmvFormats.packToMeta(it, icon) } ?: NmvFormats.meta(info?.title ?: "", info?.description ?: "", icon)
        val merged = if (info != null && info.title.isNotBlank()) JSONObject(text).put("name", info.title).also { if (info.description.isNotBlank()) it.put("description", info.description) }.toString(2) else text
        put("meta.json", merged)
    }
}
