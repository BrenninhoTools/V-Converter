package com.vconvertercrew.vconverter.vslice

import com.vconvertercrew.vconverter.core.ReportBuilder
import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.mod.ModConversionResult
import com.vconvertercrew.vconverter.mod.ModFiles
import com.vconvertercrew.vconverter.mod.ModWriter
import com.vconvertercrew.vconverter.platform.ByteArrayInputStream
import com.vconvertercrew.vconverter.platform.InputStream

object VSlicePreview {
    const val LABEL = "Preview (Public PlayTest)"
    const val API_VERSION = "0.8.7"

    var enabled: Boolean = false

    private val imageExtensions = listOf("png", "xml", "txt", "json")
    private val audioExtensions = setOf("ogg", "mp3", "wav")

    fun <T> write(target: ModWriter, vslice: Boolean, block: (ModWriter) -> T): T {
        if (!vslice || !enabled) return block(target)
        val writer = PreviewModWriter(target)
        val result = block(writer)
        writer.finish()
        return result
    }

    fun convert(target: ModWriter, vslice: Boolean, block: (ModWriter) -> ModConversionResult): ModConversionResult {
        if (!vslice || !enabled) return block(target)
        val writer = PreviewModWriter(target)
        val result = block(writer)
        val notes = writer.finish()
        val report = ReportBuilder()
        report.section("V-Slice $LABEL", notes)
        return ModConversionResult(result.filesWritten, result.report + report.build())
    }

    fun isPreview(paths: Collection<String>): Boolean = previewRoot(paths) != null

    fun previewRoot(paths: Collection<String>): String? {
        val clean = paths.map { norm(it) }
        val markers = listOf("gameplay/songs/", "gameplay/characters/", "gameplay/stages/", "ui/story-mode/levels/", "gameplay/dialogue/", "gameplay/notestyles/", "gameplay/playable-characters/", "ui/freeplay/albums/")
        val roots = clean.mapNotNull { path ->
            markers.firstNotNullOfOrNull { marker ->
                val at = if (path.startsWith(marker)) 0 else path.indexOf("/$marker").let { if (it >= 0) it + 1 else -1 }
                if (at >= 0 && path.endsWith(".json")) path.substring(0, at) else null
            }
        }.filterNot { root -> listOf("data/", "_merge/", "_append/", "images/", "assets/").any { root.endsWith(it) } }
        if (roots.isEmpty()) return null
        val meta = clean.filter { it.endsWith("_polymod_meta.json") }.map { it.removeSuffix("_polymod_meta.json") }
        val counted = roots.groupingBy { it }.eachCount()
        return meta.firstOrNull { it in counted } ?: counted.maxByOrNull { it.value }?.key
    }

    fun asStable(files: ModFiles): ModFiles {
        val root = previewRoot(files.paths) ?: return files
        return PreviewAsStableFiles(files, root)
    }

    internal fun norm(path: String): String = path.replace('\\', '/').trimStart('/')

    internal fun stripLibrary(key: String): String = key.substringAfter(':', key).trim().trimStart('/')

    internal fun ext(path: String): String = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    internal fun base(path: String): String = path.substringAfterLast('/').substringBeforeLast('.')

    internal fun isAudio(path: String): Boolean = ext(path) in audioExtensions

    internal fun imageFiles(key: String): List<String> = imageExtensions.map { "images/$key.$it" }

    internal fun parse(text: String): JSONObject? = runCatching { JSONObject(text) }.getOrNull()

    internal fun scriptId(text: String): String? =
        Regex("super\\s*\\(\\s*['\"]([^'\"]+)['\"]").find(text)?.groupValues?.get(1)

    internal fun rewriteStrings(value: Any?, map: (String) -> String?): Any? = when (value) {
        is JSONObject -> {
            value.keys().forEach { key ->
                val inner = value.opt(key)
                if (inner is String) map(inner)?.let { value.put(key, it) } else rewriteStrings(inner, map)
            }
            value
        }
        is JSONArray -> {
            for (i in 0 until value.length()) {
                val inner = value.opt(i)
                if (inner is String) map(inner)?.let { value.put(i, it) } else rewriteStrings(inner, map)
            }
            value
        }
        else -> value
    }
}

private class PendingFile(val text: String?, val source: (() -> InputStream)?, val files: ModFiles? = null, val from: String? = null)

class PreviewModWriter(private val target: ModWriter) : ModWriter {
    private val files = LinkedHashMap<String, PendingFile>()

    override fun writeText(path: String, text: String) {
        files[VSlicePreview.norm(path)] = PendingFile(text, null)
    }

    override fun copy(path: String, source: () -> InputStream) {
        files[VSlicePreview.norm(path)] = PendingFile(null, source)
    }

    override fun copyFrom(path: String, files: ModFiles, source: String) {
        this.files[VSlicePreview.norm(path)] = PendingFile(null, { files.open(source) }, files, source)
    }

    private class Owner(val kind: String, val id: String, val folder: String)

    fun finish(): List<String> {
        val destinations = LinkedHashMap<String, String>()
        val texts = HashMap<String, String>()
        val readable = setOf("json", "hxc", "hx", "lua")
        files.forEach { (path, file) ->
            val text = file.text ?: if (VSlicePreview.ext(path) in readable) runCatching { file.source?.invoke()?.use { it.readBytes().decodeToString() } }.getOrNull() else null
            if (text != null) texts[path] = text
        }
        val scriptText = texts.filterKeys { VSlicePreview.ext(it) in setOf("hxc", "hx", "lua") }.values.joinToString("\n")
        val assetMoves = LinkedHashMap<String, String>()
        val keyMoves = HashMap<String, String>()
        val kept = LinkedHashSet<String>()
        val counts = LinkedHashMap<String, Int>()
        val usedNames = HashMap<String, MutableSet<String>>()

        fun count(label: String) {
            counts[label] = (counts[label] ?: 0) + 1
        }

        fun dataPath(path: String): Pair<String, Owner?>? {
            val parts = path.split('/')
            fun json(prefix: String) = path.startsWith(prefix) && path.endsWith(".json") && path.count { it == '/' } == prefix.count { it == '/' }
            return when {
                path.startsWith("data/songs/") && parts.size == 4 -> "gameplay/songs/${parts[2]}/${parts[3]}" to null
                path.startsWith("songs/") && parts.size >= 3 -> "gameplay/songs/${parts[1]}/${parts.drop(2).joinToString("/")}" to null
                json("data/characters/") -> {
                    val id = VSlicePreview.base(path)
                    "gameplay/characters/$id/$id.json" to Owner("character", id, "gameplay/characters/$id")
                }
                json("data/stages/") -> {
                    val id = VSlicePreview.base(path)
                    "gameplay/stages/$id/$id.json" to Owner("stage", id, "gameplay/stages/$id")
                }
                json("data/levels/") -> {
                    val id = VSlicePreview.base(path)
                    "ui/story-mode/levels/$id.json" to Owner("level", id, "ui/story-mode/props")
                }
                json("data/players/") -> {
                    val id = VSlicePreview.base(path)
                    "gameplay/playable-characters/$id/$id.json" to null
                }
                json("data/notestyles/") -> {
                    val id = VSlicePreview.base(path)
                    "gameplay/notestyles/$id/$id.json" to null
                }
                parts.size == 4 && parts[0] == "data" && parts[1] == "dialogue" && parts[2] in setOf("boxes", "conversations", "speakers") && path.endsWith(".json") -> {
                    val id = VSlicePreview.base(path)
                    "gameplay/dialogue/${parts[2]}/$id/$id.json" to Owner("dialogue", id, "gameplay/dialogue/${parts[2]}/$id")
                }
                json("data/ui/freeplay/albums/") -> {
                    val id = VSlicePreview.base(path)
                    "ui/freeplay/albums/$id.json" to Owner("album", id, "ui/freeplay/albums")
                }
                json("data/ui/freeplay/styles/") -> {
                    val id = VSlicePreview.base(path)
                    "ui/freeplay/styles/$id/$id.json" to null
                }
                json("data/stickerpacks/") -> "ui/loading/stickers/stickerpacks/${parts.last()}" to null
                else -> null
            }
        }

        fun scriptPath(path: String, text: String): String? {
            if (!path.startsWith("scripts/") || VSlicePreview.ext(path) != "hxc") return null
            val parts = path.split('/')
            val name = parts.last()
            val id = VSlicePreview.scriptId(text)
            return when (parts.getOrNull(1)) {
                "songs" -> id?.let { "gameplay/songs/$it/$name" }
                "characters" -> id?.let { "gameplay/characters/$it/$name" }
                "stages" -> id?.let { "gameplay/stages/$it/$name" }
                "levels" -> "ui/story-mode/levels/$name"
                "notekinds" -> "gameplay/notekinds/$name"
                "players" -> id?.let { "gameplay/playable-characters/$it/$name" }
                "dialogue" -> null
                else -> null
            }
        }

        fun claim(key: String, folder: String, wanted: String): String? {
            val clean = VSlicePreview.stripLibrary(key)
            if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("gameplay/") || clean.startsWith("ui/")) return null
            keyMoves[clean]?.let { return it }
            val single = VSlicePreview.imageFiles(clean).filter { it in files }
            val folderPrefix = "images/$clean/"
            val nested = files.keys.filter { it.startsWith(folderPrefix) }
            if (single.isEmpty() && nested.isEmpty()) return null
            val names = usedNames.getOrPut(folder) { HashSet() }
            var name = wanted
            var n = 2
            while (!names.add(name)) name = "$wanted-${n++}"
            val newKey = "$folder/$name"
            val keep = scriptText.contains(clean)
            single.forEach { path ->
                assetMoves[path] = "$newKey.${path.substringAfterLast('.')}"
                if (keep) kept += path
            }
            nested.forEach { path ->
                assetMoves[path] = "$newKey/${path.removePrefix(folderPrefix)}"
                if (keep) kept += path
            }
            keyMoves[clean] = newKey
            return newKey
        }

        fun moveIcon(iconId: String) {
            if (iconId.isBlank()) return
            listOf("png", "xml").forEach { e ->
                val health = "images/icons/icon-$iconId.$e"
                if (health in files && health !in assetMoves) {
                    assetMoves[health] = "gameplay/characters/$iconId/icon-$iconId.$e"
                    if (scriptText.contains("icon-$iconId")) kept += health
                }
                val pixel = "images/freeplay/icons/${iconId}pixel.$e"
                if (pixel in files && pixel !in assetMoves) {
                    assetMoves[pixel] = "ui/freeplay/characters/$iconId.$e"
                    if (scriptText.contains("${iconId}pixel")) kept += pixel
                }
            }
        }

        val rewritten = HashMap<String, String>()

        fun rewriteData(path: String, owner: Owner, text: String) {
            val json = VSlicePreview.parse(text) ?: return
            fun field(obj: JSONObject?, key: String, wanted: (String) -> String) {
                if (obj == null) return
                val value = obj.opt(key) as? String ?: return
                claim(value, owner.folder, wanted(VSlicePreview.base(VSlicePreview.stripLibrary(value))))?.let { obj.put(key, it) }
            }
            when (owner.kind) {
                "character" -> {
                    field(json, "assetPath") { it }
                    json.optJSONArray("animations")?.let { anims ->
                        for (i in 0 until anims.length()) field(anims.optJSONObject(i), "assetPath") { it }
                    }
                    moveIcon(json.optJSONObject("healthIcon")?.optString("id").orEmpty().ifBlank { owner.id })
                }
                "stage" -> json.optJSONArray("props")?.let { props ->
                    for (i in 0 until props.length()) field(props.optJSONObject(i), "assetPath") { it }
                }
                "level" -> {
                    json.opt("titleAsset").let { title ->
                        if (title is String) {
                            val clean = VSlicePreview.stripLibrary(title)
                            val png = "images/$clean.png"
                            if (png in files) {
                                assetMoves[png] = "ui/story-mode/levels/${owner.id}.png"
                                if (scriptText.contains(clean)) kept += png
                                keyMoves[clean] = "ui/story-mode/levels/${owner.id}"
                                json.put("titleAsset", "ui/story-mode/levels/${owner.id}")
                            }
                        }
                    }
                    json.optJSONArray("props")?.let { props ->
                        for (i in 0 until props.length()) field(props.optJSONObject(i), "assetPath") { it }
                    }
                }
                "dialogue" -> field(json, "assetPath") { it }
                "album" -> {
                    field(json, "albumArtAsset") { it }
                    field(json, "albumTitleAsset") { it }
                }
            }
            rewritten[path] = json.toString(2)
        }

        val owners = LinkedHashMap<String, Owner>()
        files.keys.forEach { path ->
            val merge = listOf("_merge/", "_append/").firstOrNull { path.startsWith(it) }
            val inner = merge?.let { path.removePrefix(it) } ?: path
            val mapped = dataPath(inner)
            if (mapped != null) {
                destinations[path] = (merge ?: "") + mapped.first
                if (merge == null) mapped.second?.let { owners[path] = it }
                when {
                    merge != null -> count("Merge patches")
                    inner.startsWith("data/songs/") -> count("Song data")
                    inner.startsWith("songs/") -> count("Song audio")
                    else -> count(inner.removePrefix("data/").substringBefore('/').replaceFirstChar { it.uppercase() })
                }
                return@forEach
            }
            val text = texts[path]
            if (text != null) scriptPath(path, text)?.let {
                destinations[path] = it
                count("Scripts")
            }
        }
        owners.forEach { (path, owner) -> texts[path]?.let { rewriteData(path, owner, it) } }

        texts.keys.filter { it.startsWith("_merge/") || it.startsWith("_append/") }.forEach { path ->
            val text = rewritten[path] ?: texts.getValue(path)
            if (keyMoves.isEmpty()) return@forEach
            val parsed = runCatching { if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text) }.getOrNull() ?: return@forEach
            VSlicePreview.rewriteStrings(parsed, { value -> keyMoves[VSlicePreview.stripLibrary(value)] })
            rewritten[path] = if (parsed is JSONArray) parsed.toString(2) else (parsed as JSONObject).toString(2)
        }

        texts["_polymod_meta.json"]?.let { meta ->
            VSlicePreview.parse(meta)?.let {
                it.put("api_version", VSlicePreview.API_VERSION)
                rewritten["_polymod_meta.json"] = it.toString(2)
            }
        }

        files.forEach { (path, file) ->
            val text = rewritten[path] ?: file.text ?: texts[path]
            val outputs = buildList {
                val moved = destinations[path] ?: assetMoves[path]
                if (moved != null) add(moved)
                if (moved == null || path in kept) add(path)
            }
            outputs.forEach { out ->
                if (text != null) target.writeText(out, text) else if (file.files != null && file.from != null) target.copyFrom(out, file.files, file.from) else file.source?.let { target.copy(out, it) }
            }
        }

        val notes = ArrayList<String>()
        notes += "The mod uses the Public PlayTest layout: songs, charts, audio and song scripts in gameplay/songs/<song>/, characters in gameplay/characters/<id>/ (with icon-<id>.png), stages in gameplay/stages/<id>/, weeks in ui/story-mode/levels/ and freeplay albums in ui/freeplay/albums/."
        notes += "_polymod_meta.json targets api_version ${VSlicePreview.API_VERSION}."
        counts.forEach { (label, value) -> notes += "$label: $value file(s) moved." }
        if (assetMoves.isNotEmpty()) notes += "${assetMoves.size} image file(s) moved next to the data that uses them, and their assetPath values were updated."
        if (kept.isNotEmpty()) notes += "${kept.size} of them are also used by scripts, so a copy stays at the old images/ path."
        notes += "Everything else (images, sounds, music, modules and events) keeps its old path; the PlayTest build still finds it through its compatibility layer."
        return notes
    }
}

private class PreviewAsStableFiles(private val base: ModFiles, private val root: String) : ModFiles {
    private val mapping = LinkedHashMap<String, String>()
    private val keyMoves = HashMap<String, String>()
    private val jsonPaths = HashSet<String>()

    init {
        val sources = base.paths.map { it to VSlicePreview.norm(it) }
        sources.forEach { (original, clean) ->
            if (!clean.startsWith(root)) {
                mapping[clean] = original
                return@forEach
            }
            val rel = clean.removePrefix(root)
            val stable = stablePath(rel) ?: rel
            if (stable.endsWith(".json")) jsonPaths += root + stable
            mapping[root + stable] = original
        }
    }

    private fun recordKey(from: String, to: String) {
        keyMoves[from.substringBeforeLast('.')] = to.removePrefix("images/").substringBeforeLast('.')
        var fromDir = from.substringBeforeLast('/', "")
        var toDir = to.removePrefix("images/").substringBeforeLast('/', "")
        while (fromDir.isNotEmpty() && toDir.isNotEmpty() && fromDir.substringAfterLast('/') == toDir.substringAfterLast('/')) {
            keyMoves.getOrPut(fromDir) { toDir }
            fromDir = fromDir.substringBeforeLast('/', "")
            toDir = toDir.substringBeforeLast('/', "")
        }
    }

    private fun stablePath(rel: String): String? {
        val merge = listOf("_merge/", "_append/").firstOrNull { rel.startsWith(it) }
        if (merge != null) return stablePath(rel.removePrefix(merge))?.let { merge + it }
        val parts = rel.split('/')
        val name = parts.last()
        val e = VSlicePreview.ext(rel)
        fun owned(folderDepth: Int, kind: String, dataFolder: String, imageFolder: String, scriptFolder: String): String? {
            if (parts.size < folderDepth + 2) return null
            val id = parts[folderDepth]
            val inside = parts.drop(folderDepth + 1).joinToString("/")
            val atlasData = name == "Animation.json" || name.startsWith("spritemap")
            return when {
                inside == "$id.json" -> "$dataFolder/$id.json"
                e == "hxc" && parts.size == folderDepth + 2 -> "$scriptFolder/$name"
                VSlicePreview.isAudio(rel) || (e == "json" && !atlasData) -> null
                kind == "character" && inside.startsWith("icon-") && parts.size == folderDepth + 2 -> ("images/icons/$inside").also { recordKey(rel, it) }
                else -> ("images/$imageFolder/$id/$inside").also { recordKey(rel, it) }
            }
        }
        return when {
            rel.startsWith("gameplay/songs/") && parts.size >= 4 -> {
                val id = parts[2]
                when {
                    parts.size == 4 && e == "json" -> "data/songs/$id/$name"
                    parts.size == 4 && VSlicePreview.isAudio(rel) -> "songs/$id/$name"
                    parts.size == 4 && e == "hxc" -> "scripts/songs/$name"
                    else -> null
                }
            }
            rel.startsWith("gameplay/characters/") -> owned(2, "character", "data/characters", "characters", "scripts/characters")
            rel.startsWith("gameplay/stages/") -> owned(2, "stage", "data/stages", "stages", "scripts/stages")
            rel.startsWith("gameplay/playable-characters/") -> owned(2, "player", "data/players", "players", "scripts/players")
            rel.startsWith("gameplay/notestyles/") -> owned(2, "notestyle", "data/notestyles", "notestyles", "scripts/notestyles")
            rel.startsWith("gameplay/dialogue/") && parts.size >= 5 -> {
                val type = parts[2]
                owned(3, "dialogue", "data/dialogue/$type", "dialogue/$type", "scripts/dialogue/$type")
            }
            rel.startsWith("gameplay/notekinds/") && e == "hxc" -> "scripts/notekinds/$name"
            rel.startsWith("ui/story-mode/levels/") && parts.size == 4 -> when (e) {
                "json" -> "data/levels/$name"
                "hxc" -> "scripts/levels/$name"
                else -> "images/storymenu/titles/$name".also { recordKey(rel, it) }
            }
            rel.startsWith("ui/story-mode/props/") -> "images/storymenu/props/${parts.drop(3).joinToString("/")}".also { recordKey(rel, it) }
            rel.startsWith("ui/freeplay/albums/") && parts.size == 4 && e == "json" && !rel.endsWith("Animation.json") -> "data/ui/freeplay/albums/$name"
            rel.startsWith("ui/freeplay/albums/") -> "images/freeplay/albums/${parts.drop(3).joinToString("/")}".also { recordKey(rel, it) }
            rel.startsWith("ui/freeplay/styles/") && parts.size == 5 && name == "${parts[3]}.json" -> "data/ui/freeplay/styles/$name"
            rel.startsWith("ui/freeplay/characters/") && parts.size == 4 -> "images/freeplay/icons/${VSlicePreview.base(name)}pixel.$e".also { recordKey(rel, it) }
            rel.startsWith("ui/loading/stickers/stickerpacks/") && e == "json" -> "data/stickerpacks/$name"
            else -> null
        }
    }

    override val paths: List<String> = mapping.keys.toList()

    override fun open(path: String): InputStream {
        val clean = VSlicePreview.norm(path)
        val original = mapping[clean] ?: return base.open(path)
        if (clean !in jsonPaths || keyMoves.isEmpty()) return base.open(original)
        val text = base.open(original).use { it.readBytes().decodeToString() }
        val parsed = runCatching { if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text) }.getOrNull()
            ?: return ByteArrayInputStream(text.encodeToByteArray())
        var changed = false
        VSlicePreview.rewriteStrings(parsed) { value ->
            keyMoves[VSlicePreview.stripLibrary(value)]?.also { changed = true }
        }
        val out = if (!changed) text else if (parsed is JSONArray) parsed.toString(2) else (parsed as JSONObject).toString(2)
        return ByteArrayInputStream(out.encodeToByteArray())
    }

    override fun size(path: String): Long = mapping[VSlicePreview.norm(path)]?.let { base.size(it) } ?: base.size(path)
}
