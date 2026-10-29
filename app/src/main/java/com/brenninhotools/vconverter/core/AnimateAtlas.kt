package com.brenninhotools.vconverter.core

import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

data class AtlasBounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    val isEmpty: Boolean get() = width <= 0.0 && height <= 0.0

    fun union(other: AtlasBounds): AtlasBounds {
        if (isEmpty) return other
        if (other.isEmpty) return this
        val left = min(x, other.x)
        val top = min(y, other.y)
        return AtlasBounds(left, top, max(x + width, other.x + other.width) - left, max(y + height, other.y + other.height) - top)
    }

    companion object {
        val EMPTY = AtlasBounds(0.0, 0.0, 0.0, 0.0)
    }
}

data class AtlasMatrix(val a: Double, val b: Double, val c: Double, val d: Double, val tx: Double, val ty: Double) {
    fun apply(rect: AtlasBounds): AtlasBounds {
        if (rect.isEmpty) return AtlasBounds(tx, ty, 0.0, 0.0)
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        listOf(rect.x to rect.y, rect.x + rect.width to rect.y, rect.x to rect.y + rect.height, rect.x + rect.width to rect.y + rect.height).forEach { (px, py) ->
            xs += a * px + c * py + tx
            ys += b * px + d * py + ty
        }
        return AtlasBounds(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())
    }

    companion object {
        val IDENTITY = AtlasMatrix(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    }
}

data class AtlasLabel(val name: String, val start: Int, val duration: Int)

data class AtlasSymbol(val name: String, val frameCount: Int, val bounds: AtlasBounds)

class AnimateAtlas private constructor(
    val mainSymbol: String,
    val symbols: Map<String, AtlasSymbol>,
    val labels: List<AtlasLabel>,
    val frameRate: Double,
    val mainBounds: AtlasBounds,
    val firstSprite: Pair<Double, Double>?
) {
    fun findSymbol(prefix: String): AtlasSymbol? =
        symbols[prefix] ?: symbols.values.filter { it.name.startsWith(prefix) }.minByOrNull { it.name.length }

    fun findLabel(name: String): AtlasLabel? = labels.firstOrNull { it.name == name }

    val main: AtlasSymbol get() = AtlasSymbol(mainSymbol, labels.maxOfOrNull { it.start + it.duration } ?: 0, mainBounds)

    private class Element(val symbol: String?, val sprite: String?, val matrix: AtlasMatrix, val firstFrame: Int, val loop: String)

    private class Keyframe(val index: Int, val duration: Int, val name: String, val elements: List<Element>)

    private class Layer(val visible: Boolean, val frames: List<Keyframe>) {
        fun at(index: Int): Keyframe? = frames.firstOrNull { index >= it.index && index < it.index + it.duration }
        val frameCount: Int get() = frames.maxOfOrNull { it.index + it.duration } ?: 0
    }

    private class Timeline(val layers: List<Layer>) {
        val frameCount: Int get() = layers.maxOfOrNull { it.frameCount } ?: 0
    }

    companion object {
        fun parse(animationJson: String, spritemaps: List<String>): AnimateAtlas {
            val root = JSONObject(animationJson.trim().removePrefix("﻿"))
            val sprites = HashMap<String, Pair<Double, Double>>()
            var first: Pair<Double, Double>? = null
            spritemaps.forEach { raw ->
                val map = runCatching { JSONObject(raw.trim().removePrefix("﻿")) }.getOrNull() ?: return@forEach
                val list = (map.value("ATLAS") as? JSONObject)?.value("SPRITES") as? JSONArray ?: return@forEach
                for (i in 0 until list.length()) {
                    val sprite = (list.value(i) as? JSONObject)?.let { it.value("SPRITE") as? JSONObject ?: it } ?: continue
                    val w = sprite.value("w").asDouble(0.0)
                    val h = sprite.value("h").asDouble(0.0)
                    val rotated = sprite.value("rotated").asBool(false)
                    sprites[sprite.value("name").asText()] = if (rotated) h to w else w to h
                    if (first == null) first = w to h
                }
            }
            val animation = (root.value("AN") ?: root.value("ANIMATION")) as? JSONObject
                ?: throw IllegalArgumentException("Animation.json has no \"AN\"/\"ANIMATION\" block")
            val mainName = (animation.value("SN") ?: animation.value("SYMBOL_name")).asText().ifBlank { animation.value("N").asText() }
            val dictionary = ((root.value("SD") as? JSONObject)?.value("S") ?: (root.value("SYMBOL_DICTIONARY") as? JSONObject)?.value("Symbols")) as? JSONArray
            val timelines = HashMap<String, Timeline>()
            if (dictionary != null) {
                for (i in 0 until dictionary.length()) {
                    val symbol = dictionary.value(i) as? JSONObject ?: continue
                    val name = (symbol.value("SN") ?: symbol.value("SYMBOL_name")).asText()
                    val timeline = (symbol.value("TL") ?: symbol.value("TIMELINE")) as? JSONObject ?: continue
                    timelines[name] = timeline(timeline)
                }
            }
            val mainTimeline = timeline(((animation.value("TL") ?: animation.value("TIMELINE")) as? JSONObject) ?: JSONObject())
            if (mainName.isNotBlank()) timelines[mainName] = mainTimeline
            val labels = mutableListOf<AtlasLabel>()
            mainTimeline.layers.forEach { layer ->
                layer.frames.filter { it.name.isNotBlank() }.forEach { frame ->
                    if (labels.none { it.name == frame.name }) labels += AtlasLabel(frame.name, frame.index, frame.duration)
                }
            }
            val cache = HashMap<Pair<String, Int>, AtlasBounds>()
            fun frameBounds(name: String, index: Int, depth: Int): AtlasBounds {
                if (depth > 32) return AtlasBounds.EMPTY
                cache[name to index]?.let { return it }
                val timeline = timelines[name] ?: return AtlasBounds.EMPTY
                var result = AtlasBounds.EMPTY
                timeline.layers.filter { it.visible }.forEach { layer ->
                    val keyframe = layer.at(index) ?: return@forEach
                    keyframe.elements.forEach { element ->
                        val local = when {
                            element.sprite != null -> sprites[element.sprite]?.let { (w, h) -> AtlasBounds(0.0, 0.0, w, h) } ?: AtlasBounds.EMPTY
                            element.symbol != null -> {
                                val child = timelines[element.symbol]
                                if (child == null) AtlasBounds.EMPTY else {
                                    val count = child.frameCount.coerceAtLeast(1)
                                    val raw = element.firstFrame + (index - keyframe.index)
                                    val childIndex = when (element.loop) {
                                        "SF", "singleframe" -> element.firstFrame
                                        "PO", "playonce" -> min(raw, count - 1)
                                        else -> raw.mod(count)
                                    }
                                    frameBounds(element.symbol, childIndex, depth + 1)
                                }
                            }
                            else -> AtlasBounds.EMPTY
                        }
                        if (!local.isEmpty) result = result.union(element.matrix.apply(local))
                    }
                }
                cache[name to index] = result
                return result
            }
            fun wholeBounds(name: String): AtlasBounds {
                val timeline = timelines[name] ?: return AtlasBounds.EMPTY
                var result = AtlasBounds.EMPTY
                for (i in 0 until timeline.frameCount) result = result.union(frameBounds(name, i, 0))
                return result
            }
            val symbols = LinkedHashMap<String, AtlasSymbol>()
            timelines.forEach { (name, timeline) -> symbols[name] = AtlasSymbol(name, timeline.frameCount, wholeBounds(name)) }
            val frameRate = ((root.value("MD") ?: root.value("metadata")) as? JSONObject)?.let { it.value("FRT") ?: it.value("framerate") }.asDouble(24.0)
            return AnimateAtlas(mainName, symbols, labels, frameRate, symbols[mainName]?.bounds ?: AtlasBounds.EMPTY, first)
        }

        private fun timeline(json: JSONObject): Timeline {
            val layers = (json.value("L") ?: json.value("LAYERS")) as? JSONArray ?: return Timeline(emptyList())
            return Timeline((0 until layers.length()).mapNotNull { i ->
                val layer = layers.value(i) as? JSONObject ?: return@mapNotNull null
                val type = (layer.value("LT") ?: layer.value("Layer_type")).asText()
                val clipped = (layer.value("Clpb") ?: layer.value("Clipped_by")).asText()
                val visible = type != "Clp" && type != "Clipper" && type != "Fld" && type != "Folder" && clipped.isEmpty()
                val frames = (layer.value("FR") ?: layer.value("Frames")) as? JSONArray ?: JSONArray()
                Layer(visible || clipped.isNotEmpty(), (0 until frames.length()).mapNotNull { f ->
                    val frame = frames.value(f) as? JSONObject ?: return@mapNotNull null
                    val elements = (frame.value("E") ?: frame.value("elements")) as? JSONArray ?: JSONArray()
                    Keyframe(
                        (frame.value("I") ?: frame.value("index")).asDouble(0.0).toInt(),
                        (frame.value("DU") ?: frame.value("duration")).asDouble(1.0).toInt().coerceAtLeast(1),
                        (frame.value("N") ?: frame.value("name")).asText(),
                        (0 until elements.length()).mapNotNull { e -> element(elements.value(e) as? JSONObject) }
                    )
                })
            })
        }

        private fun element(json: JSONObject?): Element? {
            json ?: return null
            val symbol = (json.value("SI") ?: json.value("SYMBOL_Instance")) as? JSONObject
            if (symbol != null) {
                return Element(
                    (symbol.value("SN") ?: symbol.value("SYMBOL_name")).asText(),
                    null,
                    matrix(symbol),
                    (symbol.value("FF") ?: symbol.value("firstFrame")).asDouble(0.0).toInt(),
                    (symbol.value("LP") ?: symbol.value("loop")).asText().ifBlank { "LP" }
                )
            }
            val sprite = (json.value("ASI") ?: json.value("ATLAS_SPRITE_instance")) as? JSONObject ?: return null
            return Element(null, (sprite.value("N") ?: sprite.value("name")).asText(), matrix(sprite), 0, "LP")
        }

        private fun matrix(json: JSONObject): AtlasMatrix {
            (json.value("MX") ?: json.value("Matrix"))?.let { raw ->
                val list = numbers(raw)
                if (list.size >= 6) return AtlasMatrix(list[0], list[1], list[2], list[3], list[4], list[5])
            }
            val m3d = json.value("M3D") ?: json.value("Matrix3D")
            val values = when (m3d) {
                is JSONArray -> numbers(m3d)
                is JSONObject -> listOf("m00", "m01", "m02", "m03", "m10", "m11", "m12", "m13", "m20", "m21", "m22", "m23", "m30", "m31", "m32", "m33").map { m3d.value(it).asDouble(0.0) }
                else -> emptyList()
            }
            if (values.size < 16) return AtlasMatrix.IDENTITY
            return AtlasMatrix(values[0], values[1], values[4], values[5], values[12], values[13])
        }

        private fun numbers(raw: Any?): List<Double> = when (raw) {
            is JSONArray -> (0 until raw.length()).map { raw.value(it).asDouble(0.0) }
            is JSONObject -> listOf("a", "b", "c", "d", "tx", "ty").map { raw.value(it).asDouble(0.0) }
            else -> emptyList()
        }
    }
}

object AnimateAtlases {
    fun isAtlasFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "animation.json" || (lower.startsWith("spritemap") && lower.endsWith(".json"))
    }

    fun key(path: String): String = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }.dropLast(1).lastOrNull()?.lowercase().orEmpty()

    fun collect(files: List<Pair<String, String>>): Map<String, AnimateAtlas> =
        files.filter { isAtlasFile(it.first.substringAfterLast('/').substringAfterLast('\\')) }
            .groupBy { key(it.first) }
            .mapNotNull { (key, group) ->
                val animation = group.firstOrNull { it.first.substringAfterLast('/').substringAfterLast('\\').equals("animation.json", ignoreCase = true) } ?: return@mapNotNull null
                val spritemaps = group.filter { it !== animation }.sortedBy { it.first.lowercase() }.map { it.second }
                runCatching { key to AnimateAtlas.parse(animation.second, spritemaps) }.getOrNull()
            }.toMap()
}
