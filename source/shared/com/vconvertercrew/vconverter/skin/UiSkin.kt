package com.vconvertercrew.vconverter.skin

import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject

enum class SkinAnchor(val fx: Float, val fy: Float) {
    TOP_LEFT(0f, 0f), TOP(0.5f, 0f), TOP_RIGHT(1f, 0f),
    LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

    fun place(areaLeft: Float, areaTop: Float, areaWidth: Float, areaHeight: Float, x: Float, y: Float, width: Float, height: Float, scale: Float): Pair<Float, Float> {
        val left = areaLeft + fx * (areaWidth - width) + (if (fx == 1f) -x else x) * scale
        val top = areaTop + fy * (areaHeight - height) + (if (fy == 1f) -y else y) * scale
        return left to top
    }

    companion object {
        fun parse(text: String?, fallback: SkinAnchor = TOP_LEFT): SkinAnchor {
            val key = text?.lowercase()?.filter { it.isLetter() } ?: return fallback
            return when (key) {
                "topleft", "lefttop" -> TOP_LEFT
                "top", "topcenter", "centertop" -> TOP
                "topright", "righttop" -> TOP_RIGHT
                "left", "centerleft", "leftcenter" -> LEFT
                "center", "middle" -> CENTER
                "right", "centerright", "rightcenter" -> RIGHT
                "bottomleft", "leftbottom" -> BOTTOM_LEFT
                "bottom", "bottomcenter", "centerbottom" -> BOTTOM
                "bottomright", "rightbottom" -> BOTTOM_RIGHT
                else -> fallback
            }
        }
    }
}

class SkinAnimation(
    val name: String,
    val prefix: String,
    val fps: Double = 24.0,
    val loop: Boolean = true,
    val indices: List<Int>? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    fun frameIndex(seconds: Double, count: Int): Int {
        if (count <= 0) return -1
        val step = if (seconds <= 0.0) 0 else (seconds * fps).toInt()
        return if (loop) step % count else minOf(step, count - 1)
    }
}

class SkinSprite(
    val id: String,
    val image: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val alpha: Float = 1f,
    val anchor: SkinAnchor = SkinAnchor.TOP_LEFT,
    val layer: Int = 0,
    val visible: Boolean = true,
    val flipX: Boolean = false,
    val tint: Int? = null,
    val scrollFactor: Float = 0f,
    val animations: List<SkinAnimation> = emptyList(),
    val play: String? = null,
    val beat: List<String> = emptyList(),
    val pressed: String? = null
) {
    fun animation(name: String?): SkinAnimation? = name?.let { wanted -> animations.firstOrNull { it.name == wanted } }

    val pngPath: String get() = SkinJson.png(image)
    val xmlPath: String get() = SkinJson.xml(image)
}

class SkinImage(val image: String, val scale: Float = 1f, val scrollFactor: Float = 0f, val tint: Int? = null, val alpha: Float = 1f) {
    val pngPath: String get() = SkinJson.png(image)
}

class SkinText(
    val text: String,
    val anchor: SkinAnchor = SkinAnchor.BOTTOM_LEFT,
    val x: Float = 8f,
    val y: Float = 6f,
    val size: Float = 14f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val shadow: Int? = 0xFF000000.toInt(),
    val visible: Boolean = true
) {
    fun format(values: Map<String, String>): String = values.entries.fold(text) { out, (key, value) -> out.replace("{$key}", value) }
}

enum class IntroAction { SET, ADD, CLEAR, SHOW, HIDE }

class IntroEvent(val beat: Int, val action: IntroAction, val values: List<String>)

class IntroState(val lines: List<String>, val sprites: Set<String>)

class TitleSkin(
    val bpm: Double = 102.0,
    val background: Int = 0xFF000000.toInt(),
    val introText: String = "data/introText.txt",
    val introEnd: Int = 16,
    val introTop: Float = 200f,
    val introSpacing: Float = 60f,
    val introScale: Float = 1f,
    val events: List<IntroEvent> = TitleDefaults.events,
    val introSprites: List<SkinSprite> = emptyList(),
    val sprites: List<SkinSprite> = TitleDefaults.sprites,
    val flashColor: Int = 0xFFFFFFFF.toInt(),
    val skipFlash: Double = 4.0,
    val confirmFlash: Double = 1.0,
    val confirmDelay: Double = 2.0,
    val atlasScale: Float = 1f
) {
    val beatLength: Double get() = 60.0 / bpm.coerceAtLeast(1.0)

    fun intro(beat: Int, wacky: List<String>): IntroState {
        val lines = ArrayList<String>()
        val shown = LinkedHashSet<String>()
        fun fill(value: String): String = Regex("\\{wacky(\\d*)\\}").replace(value) { match ->
            val index = match.groupValues[1]
            if (index.isEmpty()) wacky.joinToString(" ") else wacky.getOrElse(index.toInt()) { "" }
        }
        events.filter { it.beat <= beat }.sortedBy { it.beat }.forEach { event ->
            when (event.action) {
                IntroAction.SET -> {
                    lines.clear()
                    lines += event.values.map(::fill)
                }
                IntroAction.ADD -> lines += event.values.map(::fill)
                IntroAction.CLEAR -> {
                    lines.clear()
                    shown.clear()
                }
                IntroAction.SHOW -> shown += event.values
                IntroAction.HIDE -> shown -= event.values.toSet()
            }
        }
        return IntroState(lines.filter { it.isNotEmpty() }, shown)
    }
}

class MenuItemStyle(val image: String? = null, val idle: String? = null, val selected: String? = null, val offsetX: Float = 0f, val offsetY: Float = 0f, val scale: Float? = null)

class MainMenuSkin(
    val background: SkinImage = SkinImage("images/ui/menu/menuBG", 1.1f, 0.18f),
    val flash: SkinImage? = SkinImage("images/ui/menu/menuBGMagenta", 1.1f, 0.18f),
    val flashInterval: Long = 150,
    val flashDuration: Long = 1100,
    val folder: String = "images/ui/menu/main",
    val spacing: Float = 160f,
    val itemsX: Float = 0f,
    val itemsY: Float = 0f,
    val itemsAnchor: SkinAnchor = SkinAnchor.CENTER,
    val itemScale: Float = 1f,
    val idle: String = "{label} basic",
    val selected: String = "{label} white",
    val itemFps: Double = 24.0,
    val styles: Map<String, MenuItemStyle> = emptyMap(),
    val blinkInterval: Long = 60,
    val blinkDuration: Long = 1000,
    val fadeMillis: Int = 400,
    val cameraStiffness: Float = 60f,
    val version: SkinText = SkinText("{version}"),
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val sprites: List<SkinSprite> = emptyList()
) {
    fun style(id: String): MenuItemStyle = styles[id] ?: MenuItemStyle()

    fun itemPath(id: String): String = style(id).image ?: "$folder/$id"

    fun idleFor(id: String, label: String): String = (style(id).idle ?: idle).replace("{label}", label).replace("{id}", id)

    fun selectedFor(id: String, label: String): String = (style(id).selected ?: selected).replace("{label}", label).replace("{id}", id)

    fun arrange(ids: List<String>): List<String> {
        val visible = ids.filter { it !in hidden }
        val first = order.filter { it in visible }.distinct()
        return first + visible.filter { it !in first }
    }
}

class BackButtonSkin(
    val image: String = "images/ui/mobile/backButton",
    val prefix: String = "back",
    val fps: Double = 24.0,
    val anchor: SkinAnchor = SkinAnchor.BOTTOM_RIGHT,
    val x: Float = 24f,
    val y: Float = 24f,
    val scale: Float = 0.6f,
    val alpha: Float = 1f,
    val hitPadding: Float = 10f,
    val visible: Boolean = true,
    val playOnPress: Boolean = true
) {
    val pngPath: String get() = SkinJson.png(image)
    val xmlPath: String get() = SkinJson.xml(image)
    val frameMillis: Long get() = (1000.0 / fps.coerceAtLeast(1.0)).toLong()
}

object TitleDefaults {
    val events = listOf(
        IntroEvent(1, IntroAction.SET, listOf("brenninho", "vconverter crew")),
        IntroEvent(3, IntroAction.ADD, listOf("present")),
        IntroEvent(4, IntroAction.CLEAR, emptyList()),
        IntroEvent(5, IntroAction.SET, listOf("not associated", "with")),
        IntroEvent(7, IntroAction.ADD, listOf("newgrounds")),
        IntroEvent(8, IntroAction.CLEAR, emptyList()),
        IntroEvent(9, IntroAction.SET, listOf("{wacky0}")),
        IntroEvent(11, IntroAction.ADD, listOf("{wacky1}")),
        IntroEvent(12, IntroAction.CLEAR, emptyList()),
        IntroEvent(13, IntroAction.ADD, listOf("friday")),
        IntroEvent(14, IntroAction.ADD, listOf("night")),
        IntroEvent(15, IntroAction.ADD, listOf("converter"))
    )

    val sprites = listOf(
        SkinSprite(
            "gf", "images/ui/title/gfDanceTitle", 512f, 50.4f, 2f, 2f, layer = 0,
            animations = listOf(
                SkinAnimation("danceLeft", "gfDance", 24.0, false, listOf(30) + (0..14).toList()),
                SkinAnimation("danceRight", "gfDance", 24.0, false, (15..29).toList())
            ),
            beat = listOf("danceLeft", "danceRight")
        ),
        SkinSprite(
            "logo", "images/ui/title/logoBumpin", -150f, -100f, 2f, 2f, layer = 1,
            animations = listOf(SkinAnimation("bump", "logo bumpin", 24.0, false)),
            beat = listOf("bump")
        ),
        SkinSprite(
            "enter", "images/ui/title/titleEnter", 100f, 576f, 2f, 2f, layer = 2,
            animations = listOf(SkinAnimation("idle", "Press Enter to Begin", 24.0, true), SkinAnimation("pressed", "ENTER PRESSED", 24.0, true)),
            play = "idle",
            pressed = "pressed"
        )
    )
}

object SkinJson {
    const val TITLE = "data/ui/title.json"
    const val MAIN_MENU = "data/ui/mainMenu.json"
    const val BACK_BUTTON = "data/ui/backButton.json"

    fun png(path: String): String = if (path.endsWith(".png", true)) path else "$path.png"

    fun xml(path: String): String = (if (path.endsWith(".png", true)) path.dropLast(4) else path) + ".xml"

    private fun obj(text: String?): JSONObject? = text?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun color(value: Any?, fallback: Int?): Int? {
        val text = (value as? String)?.trim() ?: return (value as? Number)?.toLong()?.toInt() ?: fallback
        val hex = text.removePrefix("#").removePrefix("0x").removePrefix("0X")
        val parsed = hex.toLongOrNull(16) ?: return fallback
        return when (hex.length) {
            6 -> (0xFF000000L or parsed).toInt()
            8 -> parsed.toInt()
            else -> fallback
        }
    }

    private fun float(json: JSONObject, key: String, fallback: Float): Float = json.optDouble(key, fallback.toDouble()).toFloat()

    private fun strings(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it)?.toString() }
        is String -> listOf(value)
        else -> emptyList()
    }

    private fun ints(value: Any?): List<Int>? = (value as? JSONArray)?.let { array -> (0 until array.length()).mapNotNull { (array.opt(it) as? Number)?.toInt() ?: array.opt(it)?.toString()?.toIntOrNull() } }

    private fun pair(json: JSONObject, key: String, fallbackX: Float, fallbackY: Float): Pair<Float, Float> = when (val value = json.opt(key)) {
        is Number -> value.toFloat() to value.toFloat()
        is JSONArray -> value.optDouble(0, fallbackX.toDouble()).toFloat() to value.optDouble(1, fallbackY.toDouble()).toFloat()
        is JSONObject -> value.optDouble("x", fallbackX.toDouble()).toFloat() to value.optDouble("y", fallbackY.toDouble()).toFloat()
        else -> fallbackX to fallbackY
    }

    fun animation(json: JSONObject, fallbackName: String = "idle"): SkinAnimation {
        val (ox, oy) = pair(json, "offset", 0f, 0f)
        return SkinAnimation(
            json.optString("name", fallbackName).ifEmpty { fallbackName },
            json.optString("prefix", json.optString("name", fallbackName)),
            json.optDouble("fps", 24.0),
            json.optBoolean("loop", true),
            ints(json.opt("indices"))?.takeIf { it.isNotEmpty() },
            ox, oy
        )
    }

    fun sprite(json: JSONObject, index: Int = 0): SkinSprite? {
        val image = json.optString("image").ifEmpty { return null }
        val (x, y) = json.optJSONArray("position")?.let { it.optDouble(0, 0.0).toFloat() to it.optDouble(1, 0.0).toFloat() }
            ?: (float(json, "x", 0f) to float(json, "y", 0f))
        val (sx, sy) = pair(json, "scale", 1f, 1f)
        val animations = ArrayList<SkinAnimation>()
        json.optJSONArray("animations")?.let { array -> for (i in 0 until array.length()) array.optJSONObject(i)?.let { animations += animation(it, "anim$i") } }
        json.optJSONObject("animation")?.let { animations += animation(it, "idle") }
        val beat = strings(json.opt("beat"))
        val play = json.optString("play", "").ifEmpty { null } ?: animations.firstOrNull()?.name?.takeIf { beat.isEmpty() }
        return SkinSprite(
            json.optString("id", "sprite$index").ifEmpty { "sprite$index" },
            image, x, y, sx, sy,
            float(json, "alpha", 1f).coerceIn(0f, 1f),
            SkinAnchor.parse(json.optString("anchor", ""), SkinAnchor.TOP_LEFT),
            json.optInt("layer", 0),
            json.optBoolean("visible", true),
            json.optBoolean("flipX", false),
            color(json.opt("color"), null),
            float(json, "scrollFactor", 0f),
            animations,
            play,
            beat,
            json.optString("pressed", "").ifEmpty { null }
        )
    }

    private fun sprites(array: JSONArray?): List<SkinSprite> {
        array ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { sprite(it, i) } }
    }

    private fun image(json: JSONObject?, fallback: SkinImage?): SkinImage? {
        if (json == null) return fallback
        if (!json.optBoolean("visible", true)) return null
        return SkinImage(
            json.optString("image", fallback?.image ?: "").ifEmpty { return fallback },
            float(json, "scale", fallback?.scale ?: 1f),
            float(json, "scrollFactor", fallback?.scrollFactor ?: 0f),
            color(json.opt("color"), fallback?.tint),
            float(json, "alpha", fallback?.alpha ?: 1f)
        )
    }

    private fun text(json: JSONObject?, fallback: SkinText): SkinText {
        json ?: return fallback
        return SkinText(
            json.optString("text", fallback.text),
            SkinAnchor.parse(json.optString("anchor", ""), fallback.anchor),
            float(json, "x", fallback.x),
            float(json, "y", fallback.y),
            float(json, "size", fallback.size),
            color(json.opt("color"), fallback.color) ?: fallback.color,
            if (json.has("shadow") && json.opt("shadow") == false) null else color(json.opt("shadow"), fallback.shadow),
            json.optBoolean("visible", fallback.visible)
        )
    }

    private fun action(text: String): IntroAction? = when (text.lowercase()) {
        "set", "create", "text" -> IntroAction.SET
        "add", "more" -> IntroAction.ADD
        "clear", "delete" -> IntroAction.CLEAR
        "show" -> IntroAction.SHOW
        "hide" -> IntroAction.HIDE
        else -> null
    }

    fun title(raw: String?): TitleSkin {
        val json = obj(raw) ?: return TitleSkin()
        val intro = json.optJSONObject("intro") ?: JSONObject()
        val events = intro.optJSONArray("events")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val event = array.optJSONObject(i) ?: return@mapNotNull null
                val kind = action(event.optString("action", "set")) ?: return@mapNotNull null
                IntroEvent(event.optInt("beat", 0), kind, strings(event.opt("text") ?: event.opt("sprites")))
            }
        } ?: TitleDefaults.events
        val flash = json.optJSONObject("flash") ?: JSONObject()
        val defaults = TitleSkin()
        return TitleSkin(
            json.optDouble("bpm", defaults.bpm),
            color(json.opt("background"), defaults.background) ?: defaults.background,
            intro.optString("textFile", defaults.introText),
            intro.optInt("length", defaults.introEnd),
            float(intro, "top", defaults.introTop),
            float(intro, "spacing", defaults.introSpacing),
            float(intro, "scale", defaults.introScale),
            events,
            sprites(intro.optJSONArray("sprites")),
            json.optJSONArray("sprites")?.let(::sprites) ?: defaults.sprites,
            color(flash.opt("color"), defaults.flashColor) ?: defaults.flashColor,
            flash.optDouble("skip", defaults.skipFlash),
            flash.optDouble("confirm", defaults.confirmFlash),
            json.optDouble("confirmDelay", defaults.confirmDelay),
            float(json, "atlasScale", defaults.atlasScale)
        )
    }

    fun mainMenu(raw: String?): MainMenuSkin {
        val json = obj(raw) ?: return MainMenuSkin()
        val defaults = MainMenuSkin()
        val items = json.optJSONObject("items") ?: JSONObject()
        val flash = json.optJSONObject("flash")
        val confirm = json.optJSONObject("confirm") ?: JSONObject()
        val styles = LinkedHashMap<String, MenuItemStyle>()
        items.optJSONObject("overrides")?.let { overrides ->
            overrides.keys().forEach { id ->
                val style = overrides.optJSONObject(id) ?: return@forEach
                val (ox, oy) = pair(style, "offset", 0f, 0f)
                styles[id] = MenuItemStyle(
                    style.optString("image", "").ifEmpty { null },
                    style.optString("idle", "").ifEmpty { null },
                    style.optString("selected", "").ifEmpty { null },
                    ox, oy,
                    if (style.has("scale")) style.optDouble("scale", 1.0).toFloat() else null
                )
            }
        }
        val (ix, iy) = pair(items, "offset", defaults.itemsX, defaults.itemsY)
        return MainMenuSkin(
            image(json.optJSONObject("background"), defaults.background) ?: defaults.background,
            if (json.has("flash") && flash == null) null else image(flash, defaults.flash),
            flash?.optLong("interval", defaults.flashInterval) ?: defaults.flashInterval,
            flash?.optLong("duration", defaults.flashDuration) ?: defaults.flashDuration,
            items.optString("folder", defaults.folder),
            float(items, "spacing", defaults.spacing),
            ix, iy,
            SkinAnchor.parse(items.optString("anchor", ""), defaults.itemsAnchor),
            float(items, "scale", defaults.itemScale),
            items.optString("idle", defaults.idle),
            items.optString("selected", defaults.selected),
            items.optDouble("fps", defaults.itemFps),
            styles,
            confirm.optLong("blinkInterval", defaults.blinkInterval),
            confirm.optLong("blinkDuration", defaults.blinkDuration),
            confirm.optInt("fadeMillis", defaults.fadeMillis),
            float(json.optJSONObject("camera") ?: JSONObject(), "stiffness", defaults.cameraStiffness),
            text(json.optJSONObject("version"), defaults.version),
            strings(json.opt("order")),
            strings(json.opt("hidden")).toSet(),
            sprites(json.optJSONArray("sprites"))
        )
    }

    fun backButton(raw: String?): BackButtonSkin {
        val json = obj(raw) ?: return BackButtonSkin()
        val defaults = BackButtonSkin()
        val animation = json.optJSONObject("animation") ?: JSONObject()
        val (x, y) = json.optJSONArray("position")?.let { it.optDouble(0, 24.0).toFloat() to it.optDouble(1, 24.0).toFloat() }
            ?: (float(json, "x", defaults.x) to float(json, "y", defaults.y))
        return BackButtonSkin(
            json.optString("image", defaults.image),
            animation.optString("prefix", defaults.prefix),
            animation.optDouble("fps", defaults.fps),
            SkinAnchor.parse(json.optString("anchor", ""), defaults.anchor),
            x, y,
            float(json, "scale", defaults.scale),
            float(json, "alpha", defaults.alpha).coerceIn(0f, 1f),
            float(json, "hitPadding", defaults.hitPadding),
            json.optBoolean("visible", defaults.visible),
            animation.optBoolean("playOnPress", defaults.playOnPress)
        )
    }
}
