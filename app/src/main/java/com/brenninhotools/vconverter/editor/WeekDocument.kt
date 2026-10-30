package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeek
import com.brenninhotools.vconverter.week.PsychWeekSong
import org.json.JSONArray
import org.json.JSONObject

enum class WeekSlot(val key: String, val label: String) { DAD("dad", "Opponent"), BF("bf", "Boyfriend"), GF("gf", "Girlfriend") }

data class MenuAnim(
    val name: String,
    val prefix: String,
    val frameRate: Int = 24,
    val looped: Boolean = false,
    val indices: List<Int> = emptyList(),
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0
)

data class WeekProp(
    val assetPath: String,
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val isPixel: Boolean = false,
    val flipX: Boolean = false,
    val danceEvery: Double = 0.0,
    val animations: List<MenuAnim> = listOf(MenuAnim("idle", "idle", looped = true)),
    val raw: String? = null
) {
    val isBase: Boolean get() = WeekDocument.baseAsset.containsValue(assetPath.lowercase())

    fun anim(name: String): MenuAnim? = animations.firstOrNull { it.name == name }

    val idleName: String get() = when {
        anim("idle") != null -> "idle"
        anim("danceLeft") != null -> "danceLeft"
        else -> animations.firstOrNull()?.name ?: "idle"
    }

    fun withPrefix(name: String, prefix: String): WeekProp {
        val clean = prefix.trim()
        val existing = anim(name)
        return when {
            existing != null && clean.isEmpty() && name == "confirm" -> copy(animations = animations.filter { it.name != name })
            existing != null -> copy(animations = animations.map { if (it.name == name) it.copy(prefix = clean) else it })
            clean.isEmpty() -> this
            else -> copy(animations = animations + MenuAnim(name, clean, looped = name != "confirm"))
        }
    }

    fun toJson(shift: Double): JSONObject {
        val entry = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val anims = JSONArray()
        animations.forEach { a ->
            val obj = JSONObject().put("name", a.name).put("prefix", a.prefix).put("frameRate", a.frameRate).put("looped", a.looped)
            if (a.indices.isNotEmpty()) obj.put("frameIndices", JSONArray(a.indices))
            if (a.offsetX != 0.0 || a.offsetY != 0.0) obj.put("offsets", JSONArray(listOf(a.offsetX.toJsonNumber(), a.offsetY.toJsonNumber())))
            anims.put(obj)
        }
        entry.put("assetPath", assetPath)
            .put("scale", scale.toJsonNumber())
            .put("offsets", JSONArray(listOf((offsetX + shift).toJsonNumber(), offsetY.toJsonNumber())))
            .put("animations", anims)
        if (danceEvery != 0.0 || entry.has("danceEvery")) entry.put("danceEvery", danceEvery.toJsonNumber())
        if (isPixel || entry.has("isPixel")) entry.put("isPixel", isPixel)
        if (flipX || entry.has("flipX")) entry.put("flipX", flipX)
        if (anim("idle") != null && (!entry.has("startingAnimation") || anim(entry.optString("startingAnimation")) == null)) entry.put("startingAnimation", "idle")
        return entry
    }

    companion object {
        fun base(slot: WeekSlot): WeekProp = fromJson(JSONObject(PsychToVSliceLevel.basePropDefinitions.getValue(slot.key)), 0.0)

        fun fromJson(obj: JSONObject, shift: Double): WeekProp {
            val offsets = obj.optJSONArray("offsets")
            val anims = obj.optJSONArray("animations")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val a = array.optJSONObject(i) ?: return@mapNotNull null
                    val off = a.optJSONArray("offsets")
                    val indices = a.optJSONArray("frameIndices")?.let { list -> (0 until list.length()).map { list.optInt(it) } } ?: emptyList()
                    MenuAnim(a.optString("name"), a.optString("prefix"), a.optDouble("frameRate", 24.0).toInt(), a.optBoolean("looped", false), indices, off?.optDouble(0, 0.0) ?: 0.0, off?.optDouble(1, 0.0) ?: 0.0)
                }
            } ?: emptyList()
            return WeekProp(
                assetPath = obj.optString("assetPath"),
                scale = obj.optDouble("scale", 1.0),
                offsetX = (offsets?.optDouble(0, 0.0) ?: 0.0) - shift,
                offsetY = offsets?.optDouble(1, 0.0) ?: 0.0,
                isPixel = obj.optBoolean("isPixel", false),
                flipX = obj.optBoolean("flipX", false),
                danceEvery = obj.optDouble("danceEvery", 0.0),
                animations = anims,
                raw = obj.toString()
            )
        }
    }
}

data class WeekSong(val id: String, val name: String = "", val icon: String = "", val color: String = "#9271FD") {
    val displayName: String get() = name.ifBlank { id }
}

data class WeekDocument(
    val id: String,
    val name: String = "",
    val weekName: String = "",
    val titleAsset: String = "",
    val background: String = DEFAULT_BACKGROUND,
    val songs: List<WeekSong> = emptyList(),
    val props: List<WeekProp?> = WeekSlot.entries.map { WeekProp.base(it) },
    val difficulties: List<String> = DEFAULT_DIFFICULTIES,
    val visible: Boolean = true,
    val hideFreeplay: Boolean = false,
    val weekBefore: String = "",
    val startUnlocked: Boolean = true,
    val hiddenUntilUnlocked: Boolean = false,
    val rawJson: String? = null
) {
    val title: String get() = titleAsset.ifBlank { "storymenu/titles/$id" }
    val storyName: String get() = name.ifBlank { Ids.pretty(id) }
    val isLocked: Boolean get() = weekBefore.isNotBlank() && !startUnlocked
    val backgroundIsColor: Boolean get() = background.trim().startsWith("#")

    fun prop(slot: WeekSlot): WeekProp? = props.getOrNull(slot.ordinal)

    fun withProp(slot: WeekSlot, prop: WeekProp?): WeekDocument = copy(props = WeekSlot.entries.map { if (it == slot) prop else this.prop(it) })

    fun replaceSong(index: Int, song: WeekSong): WeekDocument = copy(songs = songs.mapIndexed { i, s -> if (i == index) song else s })

    fun moveSong(index: Int, direction: Int): WeekDocument {
        val target = index + direction
        if (index !in songs.indices || target !in songs.indices) return this
        val list = songs.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        return copy(songs = list)
    }

    fun toVSlice(): String {
        val root = rawJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val array = JSONArray()
        WeekSlot.entries.forEach { slot ->
            val prop = prop(slot) ?: return@forEach
            array.put(prop.toJson((slot.ordinal - array.length()) * SLOT_WIDTH))
        }
        root.put("version", root.optString("version").ifEmpty { PsychToVSliceLevel.LEVEL_VERSION })
            .put("name", storyName)
            .put("titleAsset", title)
            .put("props", array)
            .put("background", background.trim().ifEmpty { DEFAULT_BACKGROUND })
            .put("songs", JSONArray(songs.map { it.id }))
        if (!visible) root.put("visible", false) else root.remove("visible")
        return root.toString(2)
    }

    fun toPsychWeek(): PsychWeek = PsychWeek(
        songs = songs.map { PsychWeekSong(it.displayName, it.icon.ifBlank { "face" }, rgb(it.color)) },
        weekCharacters = WeekSlot.entries.map { prop(it)?.let { p -> baseAsset.entries.firstOrNull { e -> e.value == p.assetPath.lowercase() }?.key ?: p.assetPath.substringAfterLast('/') } ?: "" },
        weekBackground = "",
        storyName = storyName,
        weekName = weekName.ifBlank { storyName },
        weekBefore = weekBefore.trim(),
        startUnlocked = startUnlocked,
        hiddenUntilUnlocked = hiddenUntilUnlocked,
        hideStoryMode = !visible,
        hideFreeplay = hideFreeplay,
        difficulties = difficulties
    )

    fun toSnapshot(): JSONObject = JSONObject()
        .put("id", id)
        .put("level", toVSlice())
        .put("name", name)
        .put("weekName", weekName)
        .put("titleAsset", titleAsset)
        .put("songs", JSONArray().also { array -> songs.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("color", it.color)) } })
        .put("difficulties", JSONArray(difficulties))
        .put("visible", visible)
        .put("hideFreeplay", hideFreeplay)
        .put("weekBefore", weekBefore)
        .put("startUnlocked", startUnlocked)
        .put("hiddenUntilUnlocked", hiddenUntilUnlocked)

    companion object {
        fun fromSnapshot(snapshot: JSONObject): WeekDocument {
            val base = fromVSlice(snapshot.optString("id", "week"), snapshot.getString("level"))
            val songs = snapshot.optJSONArray("songs")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { WeekSong(it.optString("id"), it.optString("name"), it.optString("icon"), it.optString("color", "#9271FD")) }
            } ?: base.songs
            val difficulties = snapshot.optJSONArray("difficulties")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }
            return base.copy(
                name = snapshot.optString("name", base.name),
                weekName = snapshot.optString("weekName"),
                titleAsset = snapshot.optString("titleAsset", base.titleAsset),
                songs = songs,
                difficulties = difficulties?.ifEmpty { null } ?: DEFAULT_DIFFICULTIES,
                visible = snapshot.optBoolean("visible", true),
                hideFreeplay = snapshot.optBoolean("hideFreeplay", false),
                weekBefore = snapshot.optString("weekBefore"),
                startUnlocked = snapshot.optBoolean("startUnlocked", true),
                hiddenUntilUnlocked = snapshot.optBoolean("hiddenUntilUnlocked", false)
            )
        }

        const val DEFAULT_BACKGROUND = "#F9CF51"
        const val SLOT_WIDTH = 320.0
        val DEFAULT_DIFFICULTIES = listOf("easy", "normal", "hard")
        val baseAsset = mapOf("dad" to "storymenu/props/dad", "bf" to "storymenu/props/bf", "gf" to "storymenu/props/gf")

        fun rgb(hex: String): List<Int> {
            val clean = hex.trim().removePrefix("#").removePrefix("0x").let { if (it.length == 8) it.substring(2) else it }
            val value = clean.toIntOrNull(16) ?: return listOf(146, 113, 253)
            return listOf((value shr 16) and 255, (value shr 8) and 255, value and 255)
        }

        fun hex(rgb: List<Int>): String = "#%02X%02X%02X".format(rgb.getOrElse(0) { 146 }, rgb.getOrElse(1) { 113 }, rgb.getOrElse(2) { 253 })

        fun fromVSlice(id: String, json: String): WeekDocument {
            val level = VSliceParser.level(json)
            val root = JSONObject(json)
            val rawProps = root.optJSONArray("props") ?: JSONArray()
            val slots = arrayOfNulls<WeekProp>(3)
            val loose = ArrayList<Pair<Int, JSONObject>>()
            (0 until rawProps.length()).forEach { index ->
                val obj = rawProps.optJSONObject(index) ?: return@forEach
                val base = baseAsset.entries.firstOrNull { it.value == obj.optString("assetPath").lowercase() }?.key
                val slot = WeekSlot.entries.firstOrNull { it.key == base }
                if (slot != null && slots[slot.ordinal] == null) slots[slot.ordinal] = WeekProp.fromJson(obj, (slot.ordinal - index) * SLOT_WIDTH)
                else loose += index to obj
            }
            loose.forEach { (index, obj) ->
                val slot = (index until 3).firstOrNull { slots[it] == null } ?: (0 until 3).firstOrNull { slots[it] == null } ?: return@forEach
                slots[slot] = WeekProp.fromJson(obj, (slot - index) * SLOT_WIDTH)
            }
            return WeekDocument(
                id = id,
                name = level.name,
                titleAsset = level.titleAsset,
                background = level.background,
                songs = level.songs.map { WeekSong(it) },
                props = slots.toList(),
                visible = level.visible,
                rawJson = json
            )
        }
    }
}
