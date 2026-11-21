package com.vconvertercrew.vconverter.stage

import com.vconvertercrew.vconverter.character.NativeCharacter
import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.script.NativeData

internal object NativeStage {
    private fun point(p: PsychPoint): JSONArray = JSONArray().put(p.x).put(p.y)

    private fun assetPath(image: String): String = image.trim().replace('\\', '/').removePrefix("images/").removeSuffix(".png")

    private fun objectJson(obj: PsychStageObject): JSONObject {
        val animations = JSONArray()
        obj.animations.forEach { anim ->
            animations.put(
                JSONObject()
                    .put("anim", anim.anim)
                    .put("prefix", anim.prefix)
                    .put("fps", anim.fps)
                    .put("loop", anim.loop)
                    .put("indices", JSONArray(anim.indices))
                    .put("offsetX", anim.offsetX)
                    .put("offsetY", anim.offsetY)
            )
        }
        return JSONObject()
            .put("type", obj.type)
            .put("name", obj.name)
            .put("image", obj.image)
            .put("x", obj.x)
            .put("y", obj.y)
            .put("scale", point(obj.scale))
            .put("scroll", point(obj.scroll))
            .put("alpha", obj.alpha)
            .put("angle", obj.angle)
            .put("color", obj.color)
            .put("antialiasing", obj.antialiasing)
            .put("flipX", obj.flipX)
            .put("flipY", obj.flipY)
            .put("filters", obj.filters)
            .put("animations", animations)
            .put("firstAnimation", obj.firstAnimation)
            .put("blend", obj.blend)
            .put("danceEvery", obj.danceEvery)
            .put("rawOffsets", obj.rawOffsets)
            .put("startReplayed", obj.startReplayed)
            .put("paused", obj.paused)
    }

    fun convert(source: StageSource): StageResult? = runCatching { build(source) }.getOrNull()

    private fun build(source: StageSource): StageResult? {
        val psych = source.stage
        val stage = JSONObject()
            .put("directory", psych.directory)
            .put("defaultZoom", psych.defaultZoom)
            .put("isPixelStage", psych.isPixelStage)
            .put("boyfriend", point(psych.boyfriend))
            .put("girlfriend", point(psych.girlfriend))
            .put("opponent", point(psych.opponent))
            .put("hideGirlfriend", psych.hideGirlfriend)
            .put("cameraBoyfriend", point(psych.cameraBoyfriend))
            .put("cameraOpponent", point(psych.cameraOpponent))
            .put("cameraGirlfriend", point(psych.cameraGirlfriend))
            .put("cameraSpeed", psych.cameraSpeed)
            .put("preload", JSONArray(psych.preload))
        val atlases = JSONObject()
        psych.objects?.let { objects ->
            val array = JSONArray()
            objects.forEach { obj ->
                array.put(objectJson(obj))
                if (obj.type == "animatedSprite") {
                    val path = assetPath(obj.image)
                    if (!atlases.has(path)) source.atlas(path)?.let { atlases.put(path, NativeCharacter.frames(it)) }
                }
            }
            stage.put("objects", array)
        }
        val input = JSONObject()
            .put("id", source.id)
            .put("displayName", source.displayName)
            .put("stage", stage)
            .put("atlases", atlases)
        val result = NativeData.convert("stage", input) ?: return null
        val json = result.optJSONObject("json") ?: return null
        return StageResult(source.id, json.toString(2), NativeData.report(result.optJSONObject("report")))
    }
}
