package com.brenninhotools.vconverter.stage

import com.brenninhotools.vconverter.script.lua.Expr
import com.brenninhotools.vconverter.script.lua.LuaParser
import com.brenninhotools.vconverter.script.lua.Stat
import com.brenninhotools.vconverter.script.lua.TableField

data class LuaStageProps(
    val behind: List<PsychStageObject>,
    val front: List<PsychStageObject>,
    val removedLines: Set<Int>,
    val notes: List<String>
) {
    val isEmpty: Boolean get() = behind.isEmpty() && front.isEmpty()
}

object LuaStageExtractor {
    private class Draft(val tag: String, val line: Int) {
        var type = "sprite"
        var image = ""
        var x = 0.0
        var y = 0.0
        var scaleX = 1.0
        var scaleY = 1.0
        var scrollX = 1.0
        var scrollY = 1.0
        var alpha = 1.0
        var angle = 0.0
        var color = "FFFFFF"
        var antialiasing = true
        var flipX = false
        var flipY = false
        var blend = ""
        var highQualityOnly = false
        var firstAnimation = ""
        val animations = mutableListOf<PsychStageAnimation>()
        val offsets = HashMap<String, Pair<Double, Double>>()
        val lines = mutableListOf<Int>()
        var added = false
        var front = false
        var order = 0
    }

    private val creators = setOf("makeLuaSprite", "makeAnimatedLuaSprite")
    private val handled = setOf(
        "makeLuaSprite", "makeAnimatedLuaSprite", "addAnimationByPrefix", "luaSpriteAddAnimationByPrefix", "addAnimationByIndices",
        "addAnimationByIndicesLoop", "luaSpriteAddAnimationByIndices", "addOffset", "scaleObject", "scaleLuaSprite", "setScrollFactor",
        "setLuaSpriteScrollFactor", "makeGraphic", "luaSpriteMakeGraphic", "setProperty", "addLuaSprite", "playAnim",
        "objectPlayAnimation", "luaSpritePlayAnimation", "updateHitbox", "setBlendMode"
    )

    fun extract(source: String): LuaStageProps {
        val chunk = try {
            LuaParser.parse(source)
        } catch (e: Exception) {
            return LuaStageProps(emptyList(), emptyList(), emptySet(), listOf("The stage script could not be read: ${e.message}"))
        }
        val bodies = chunk.filterIsInstance<Stat.FunctionDecl>()
            .filter { it.method == null && it.path.size == 1 && it.path[0] in setOf("onCreate", "onCreatePost") }
            .sortedBy { if (it.path[0] == "onCreate") 0 else 1 }
        val drafts = LinkedHashMap<String, Draft>()
        var order = 0
        val calls = mutableListOf<Pair<Stat.CallStat, Boolean>>()
        fun collect(stats: List<Stat>, hq: Boolean) {
            stats.forEach { stat ->
                when (stat) {
                    is Stat.CallStat -> calls += stat to hq
                    is Stat.If -> {
                        if (stat.branches.size == 1) {
                            when (qualityBranch(stat.branches[0].first)) {
                                true -> collect(stat.branches[0].second, true)
                                false -> stat.otherwise?.let { collect(it, true) }
                                null -> Unit
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        bodies.forEach { collect(it.function.body, false) }

        calls.forEach { (stat, hq) ->
            val call = stat.call as? Expr.Call ?: return@forEach
            val name = (call.function as? Expr.Name)?.name ?: return@forEach
            if (name !in handled) return@forEach
            val args = call.args.map(::literal)
            if (args.any { it === NotLiteral }) return@forEach
            val tag = args.getOrNull(0) as? String ?: return@forEach
            if (name in creators) {
                if (drafts.containsKey(tag)) return@forEach
                val d = Draft(tag, stat.line)
                d.type = if (name == "makeAnimatedLuaSprite") "animatedSprite" else "sprite"
                d.image = args.getOrNull(1) as? String ?: ""
                d.x = number(args.getOrNull(2), 0.0)
                d.y = number(args.getOrNull(3), 0.0)
                d.highQualityOnly = hq
                d.lines += stat.line
                drafts[tag] = d
                return@forEach
            }
            val d = drafts[tag]?.takeIf { !it.added || name != "addLuaSprite" } ?: run {
                if (name == "setProperty") {
                    val path = tag.substringBefore('.')
                    drafts[path]
                } else null
            } ?: return@forEach
            if (applyOp(d, name, args)) d.lines += stat.line
            if (name == "addLuaSprite" && !d.added) {
                d.added = true
                d.front = args.getOrNull(1) == true
                d.order = order++
            }
        }

        val usable = drafts.values.filter { it.added }
        val notes = mutableListOf<String>()
        drafts.values.filter { !it.added }.forEach { notes += "\"${it.tag}\" is created but never added with addLuaSprite in onCreate, so it stays in the script." }
        val objects = usable.sortedBy { it.order }.map { it to toObject(it) }
        if (usable.isNotEmpty()) notes += "${usable.size} sprite(s) from the stage script became stage props; the script keeps only the rest of its logic."
        return LuaStageProps(
            behind = objects.filter { !it.first.front }.map { it.second },
            front = objects.filter { it.first.front }.map { it.second },
            removedLines = usable.flatMap { it.lines }.toSet(),
            notes = notes
        )
    }

    fun merge(stage: PsychStage, props: LuaStageProps): PsychStage {
        if (props.isEmpty) return stage
        val markers = setOf("gf", "gfGroup", "dad", "dadGroup", "boyfriend", "boyfriendGroup")
        val base = stage.objects ?: emptyList()
        val hasMarkers = base.any { it.type in markers }
        val list = if (hasMarkers) {
            val gfIndex = base.indexOfFirst { it.type == "gf" || it.type == "gfGroup" }.takeIf { it >= 0 } ?: base.indexOfFirst { it.type in markers }
            base.subList(0, gfIndex) + props.behind + base.subList(gfIndex, base.size) + props.front
        } else {
            base + props.behind + listOf(marker("gfGroup"), marker("dadGroup"), marker("boyfriendGroup")) + props.front
        }
        return stage.copy(objects = list)
    }

    fun defaultStage(): PsychStage = PsychStage(
        directory = "",
        defaultZoom = 0.9,
        isPixelStage = false,
        stageUI = "",
        boyfriend = PsychPoint(770.0, 100.0),
        girlfriend = PsychPoint(400.0, 130.0),
        opponent = PsychPoint(100.0, 100.0),
        hideGirlfriend = false,
        cameraBoyfriend = PsychPoint(0.0, 0.0),
        cameraOpponent = PsychPoint(0.0, 0.0),
        cameraGirlfriend = PsychPoint(0.0, 0.0),
        cameraSpeed = 1.0,
        preload = emptyList(),
        objects = null
    )

    private fun marker(type: String) = PsychStageObject(
        type, "", "", 0.0, 0.0, PsychPoint(1.0, 1.0), PsychPoint(1.0, 1.0), 1.0, 0.0, "FFFFFF", true, false, false, 3, emptyList(), ""
    )

    private fun qualityBranch(cond: Expr): Boolean? {
        fun isLowQuality(e: Expr) = (e as? Expr.Name)?.name == "lowQuality"
        return when (cond) {
            is Expr.Unary -> if (cond.op == "not" && isLowQuality(cond.operand)) true else null
            is Expr.Name -> if (isLowQuality(cond)) false else null
            is Expr.Paren -> qualityBranch(cond.inner)
            is Expr.Binary -> {
                val other = when {
                    isLowQuality(cond.left) -> cond.right
                    isLowQuality(cond.right) -> cond.left
                    else -> return null
                }
                val value = (other as? Expr.Bool)?.value ?: return null
                when (cond.op) {
                    "==" -> !value
                    "~=" -> value
                    else -> null
                }
            }
            else -> null
        }
    }

    private object NotLiteral

    private fun literal(e: Expr): Any? = when (e) {
        is Expr.Str -> e.value
        is Expr.Num -> e.text.toDoubleOrNull() ?: e.text.removePrefix("0x").toLongOrNull(16)?.toDouble() ?: NotLiteral
        is Expr.Bool -> e.value
        is Expr.Nil -> null
        is Expr.Paren -> literal(e.inner)
        is Expr.Unary -> if (e.op == "-") (literal(e.operand) as? Double)?.let { -it } ?: NotLiteral else NotLiteral
        is Expr.Binary -> {
            val l = literal(e.left) as? Double
            val r = literal(e.right) as? Double
            if (l == null || r == null) NotLiteral else when (e.op) {
                "+" -> l + r
                "-" -> l - r
                "*" -> l * r
                "/" -> if (r == 0.0) NotLiteral else l / r
                else -> NotLiteral
            }
        }
        is Expr.Table -> e.fields.map { field ->
            when (field) {
                is TableField.Positional -> literal(field.value)
                else -> NotLiteral
            }
        }.let { list -> if (list.any { it === NotLiteral }) NotLiteral else list }
        else -> NotLiteral
    }

    private fun number(value: Any?, fallback: Double): Double = when (value) {
        is Double -> value
        is String -> value.trim().toDoubleOrNull() ?: fallback
        else -> fallback
    }

    private fun bool(value: Any?, fallback: Boolean): Boolean = when (value) {
        is Boolean -> value
        is String -> value.trim().lowercase() == "true"
        else -> fallback
    }

    private fun indices(value: Any?): List<Int> = when (value) {
        is String -> value.split(',').mapNotNull { it.trim().toDoubleOrNull()?.toInt() }
        is List<*> -> value.mapNotNull { (it as? Double)?.toInt() }
        else -> emptyList()
    }

    private fun applyOp(d: Draft, name: String, args: List<Any?>): Boolean {
        when (name) {
            "addAnimationByPrefix", "luaSpriteAddAnimationByPrefix" -> {
                val anim = args.getOrNull(1) as? String ?: return false
                val prefix = args.getOrNull(2) as? String ?: return false
                d.type = "animatedSprite"
                d.animations += PsychStageAnimation(anim, prefix, number(args.getOrNull(3), 24.0).toInt(), bool(args.getOrNull(4), true), emptyList(), 0.0, 0.0)
            }
            "addAnimationByIndices", "luaSpriteAddAnimationByIndices", "addAnimationByIndicesLoop" -> {
                val anim = args.getOrNull(1) as? String ?: return false
                val prefix = args.getOrNull(2) as? String ?: return false
                d.type = "animatedSprite"
                val loop = if (name == "addAnimationByIndicesLoop") true else bool(args.getOrNull(5), false)
                d.animations += PsychStageAnimation(anim, prefix, number(args.getOrNull(4), 24.0).toInt(), loop, indices(args.getOrNull(3)), 0.0, 0.0)
            }
            "addOffset" -> {
                val anim = args.getOrNull(1) as? String ?: return false
                d.offsets[anim] = number(args.getOrNull(2), 0.0) to number(args.getOrNull(3), 0.0)
            }
            "scaleObject", "scaleLuaSprite" -> {
                d.scaleX = number(args.getOrNull(1), 1.0)
                d.scaleY = number(args.getOrNull(2), d.scaleX)
            }
            "setScrollFactor", "setLuaSpriteScrollFactor" -> {
                d.scrollX = number(args.getOrNull(1), 1.0)
                d.scrollY = number(args.getOrNull(2), d.scrollX)
            }
            "makeGraphic", "luaSpriteMakeGraphic" -> {
                d.type = "square"
                d.scaleX = number(args.getOrNull(1), 256.0)
                d.scaleY = number(args.getOrNull(2), 256.0)
                d.color = (args.getOrNull(3) as? String ?: "FFFFFF").removePrefix("#").removePrefix("0x").takeLast(6).uppercase()
            }
            "setBlendMode" -> d.blend = (args.getOrNull(1) as? String ?: return false).lowercase()
            "playAnim", "objectPlayAnimation", "luaSpritePlayAnimation" -> {
                if (d.firstAnimation.isEmpty()) d.firstAnimation = args.getOrNull(1) as? String ?: return false
            }
            "updateHitbox", "addLuaSprite" -> Unit
            "setProperty" -> {
                val path = (args.getOrNull(0) as? String)?.substringAfter('.', "") ?: return false
                val value = args.getOrNull(1)
                when (path) {
                    "x" -> d.x = number(value, d.x)
                    "y" -> d.y = number(value, d.y)
                    "alpha" -> d.alpha = number(value, d.alpha)
                    "angle" -> d.angle = number(value, d.angle)
                    "flipX" -> d.flipX = bool(value, d.flipX)
                    "flipY" -> d.flipY = bool(value, d.flipY)
                    "antialiasing" -> d.antialiasing = bool(value, d.antialiasing)
                    "scale.x" -> d.scaleX = number(value, d.scaleX)
                    "scale.y" -> d.scaleY = number(value, d.scaleY)
                    "scrollFactor.x" -> d.scrollX = number(value, d.scrollX)
                    "scrollFactor.y" -> d.scrollY = number(value, d.scrollY)
                    else -> return false
                }
            }
            else -> return false
        }
        return true
    }

    private fun toObject(d: Draft): PsychStageObject = PsychStageObject(
        type = if (d.type == "animatedSprite" && d.animations.isEmpty()) "sprite" else d.type,
        name = d.tag,
        image = d.image,
        x = d.x,
        y = d.y,
        scale = PsychPoint(d.scaleX, d.scaleY),
        scroll = PsychPoint(d.scrollX, d.scrollY),
        alpha = d.alpha,
        angle = d.angle,
        color = d.color,
        antialiasing = d.antialiasing,
        flipX = d.flipX,
        flipY = d.flipY,
        filters = if (d.highQualityOnly) PsychStageParser.FILTER_HIGH_QUALITY else PsychStageParser.FILTER_LOW_QUALITY or PsychStageParser.FILTER_HIGH_QUALITY,
        animations = d.animations.map { anim ->
            d.offsets[anim.anim]?.let { (ox, oy) -> anim.copy(offsetX = ox, offsetY = oy) } ?: anim
        },
        firstAnimation = d.firstAnimation.ifEmpty { d.animations.firstOrNull()?.anim ?: "" },
        blend = d.blend
    )
}
