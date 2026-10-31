package com.brenninhotools.vconverter.psych

import com.brenninhotools.vconverter.character.PsychCharacter
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.stage.PsychPoint
import com.brenninhotools.vconverter.stage.PsychStage
import com.brenninhotools.vconverter.stage.PsychStageObject
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychWeek
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject

object PsychWriters {
    fun character(c: PsychCharacter): String {
        val animations = JSONArray()
        c.animations.forEach { anim ->
            animations.put(
                JSONObject()
                    .put("offsets", JSONArray(listOf(anim.offsetX.toJsonNumber(), anim.offsetY.toJsonNumber())))
                    .put("loop", anim.loop)
                    .put("fps", anim.fps)
                    .put("anim", anim.anim)
                    .put("indices", JSONArray(anim.indices))
                    .put("name", anim.prefix)
            )
        }
        val root = JSONObject()
            .put("animations", animations)
            .put("image", c.image)
            .put("position", point(c.positionX, c.positionY))
            .put("healthicon", c.healthIcon)
            .put("flip_x", c.flipX)
            .put("healthbar_colors", JSONArray(c.healthBarColor))
            .put("camera_position", point(c.cameraX, c.cameraY))
            .put("sing_duration", c.singDuration.toJsonNumber())
            .put("scale", c.scale.toJsonNumber())
            .put("no_antialiasing", c.noAntialiasing)
            .put("vocals_file", c.vocalsFile)
        c.editorIsPlayer?.let { root.put("_editor_isPlayer", it) }
        return root.toString(2)
    }

    fun stage(stage: PsychStage, extra: JSONObject? = null): String {
        val root = JSONObject()
            .put("directory", stage.directory)
            .put("defaultZoom", stage.defaultZoom.toJsonNumber())
            .put("isPixelStage", stage.isPixelStage)
            .put("stageUI", stage.stageUI.ifEmpty { if (stage.isPixelStage) "pixel" else "normal" })
            .put("boyfriend", point(stage.boyfriend))
            .put("girlfriend", point(stage.girlfriend))
            .put("opponent", point(stage.opponent))
            .put("hide_girlfriend", stage.hideGirlfriend)
            .put("camera_boyfriend", point(stage.cameraBoyfriend))
            .put("camera_opponent", point(stage.cameraOpponent))
            .put("camera_girlfriend", point(stage.cameraGirlfriend))
            .put("camera_speed", stage.cameraSpeed.toJsonNumber())
        if (stage.preload.isNotEmpty()) root.put("preload", JSONObject().also { obj -> stage.preload.forEach { obj.put(it, "sound") } })
        stage.objects?.let { list -> root.put("objects", JSONArray(list.map(::stageObject))) }
        extra?.keys()?.forEach { key -> root.put(key, extra.get(key)) }
        return root.toString(2)
    }

    fun stageObject(obj: PsychStageObject): JSONObject {
        if (obj.type in setOf("gf", "gfGroup", "dad", "dadGroup", "boyfriend", "boyfriendGroup")) return JSONObject().put("type", obj.type)
        val json = JSONObject()
            .put("type", obj.type)
            .put("name", obj.name)
            .put("x", obj.x.toJsonNumber())
            .put("y", obj.y.toJsonNumber())
            .put("scale", point(obj.scale))
            .put("scroll", point(obj.scroll))
            .put("alpha", obj.alpha.toJsonNumber())
            .put("angle", obj.angle.toJsonNumber())
            .put("color", obj.color.removePrefix("#").removePrefix("0x").removePrefix("0X").let { if (it.length == 8) it.substring(2) else it }.uppercase().ifEmpty { "FFFFFF" })
            .put("filters", obj.filters)
        if (obj.type != "square") {
            json.put("image", obj.image)
            json.put("antialiasing", obj.antialiasing)
            json.put("flipX", obj.flipX)
            json.put("flipY", obj.flipY)
        }
        if (obj.type == "animatedSprite") {
            json.put("animations", JSONArray(obj.animations.map { anim ->
                JSONObject()
                    .put("anim", anim.anim)
                    .put("name", anim.prefix)
                    .put("fps", anim.fps)
                    .put("loop", anim.loop)
                    .put("indices", JSONArray(anim.indices))
                    .put("offsets", JSONArray(listOf(anim.offsetX.toJsonNumber(), anim.offsetY.toJsonNumber())))
            }))
            json.put("firstAnimation", obj.firstAnimation.ifEmpty { obj.animations.firstOrNull()?.anim ?: "" })
        }
        return json
    }

    fun week(week: PsychWeek): String = JSONObject()
        .put("songs", JSONArray(week.songs.map { JSONArray().put(it.name).put(it.icon).put(JSONArray(it.color)) }))
        .put("weekCharacters", JSONArray(week.weekCharacters))
        .put("weekBackground", week.weekBackground)
        .put("weekBefore", week.weekBefore)
        .put("storyName", week.storyName)
        .put("weekName", week.weekName)
        .put("freeplayColor", JSONArray(listOf(146, 113, 253)))
        .put("startUnlocked", week.startUnlocked)
        .put("hiddenUntilUnlocked", week.hiddenUntilUnlocked)
        .put("hideStoryMode", week.hideStoryMode)
        .put("hideFreeplay", week.hideFreeplay)
        .put("difficulties", week.difficulties.joinToString(","))
        .toString(2)

    fun menuCharacter(c: PsychMenuCharacter): String = JSONObject()
        .put("image", c.image)
        .put("scale", c.scale.toJsonNumber())
        .put("position", point(c.positionX, c.positionY))
        .put("idle_anim", c.idleAnim)
        .put("confirm_anim", c.confirmAnim)
        .put("flipX", c.flipX)
        .put("antialiasing", c.antialiasing)
        .toString(2)

    fun pack(name: String, description: String, color: List<Int> = listOf(170, 0, 255)): String = JSONObject()
        .put("name", name)
        .put("description", description)
        .put("restart", false)
        .put("runsGlobally", false)
        .put("color", JSONArray(color))
        .put("discordRPC", "")
        .toString(2)

    private fun point(p: PsychPoint) = point(p.x, p.y)

    private fun point(x: Double, y: Double) = JSONArray(listOf(x.toJsonNumber(), y.toJsonNumber()))
}
