package com.vconvertercrew.vconverter.character

import com.vconvertercrew.vconverter.core.CharacterSlot
import com.vconvertercrew.vconverter.core.SparrowAtlas
import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.script.NativeData

internal object NativeCharacter {
    fun frames(atlas: SparrowAtlas): JSONArray {
        val array = JSONArray()
        atlas.frames.forEach { frame -> array.put(JSONArray().put(frame.name).put(frame.sourceWidth).put(frame.sourceHeight)) }
        return array
    }

    fun convert(source: CharacterSource): CharacterResult? = runCatching { build(source) }.getOrNull()

    private fun build(source: CharacterSource): CharacterResult? {
        if (source.character.raw.isBlank()) return null
        val gameOver = source.gameOver
        if (gameOver != null && (gameOver.raw.isBlank() || source.gameOverAnimate != null)) return null
        val atlases = JSONObject()
        source.atlases.forEach { (key, atlas) -> atlases.put(key, frames(atlas)) }
        val input = JSONObject()
            .put("id", source.id)
            .put("text", source.character.raw)
            .put("slot", when (source.slot) {
                CharacterSlot.PLAYER -> "player"
                CharacterSlot.GIRLFRIEND -> "girlfriend"
                else -> "opponent"
            })
            .put("displayName", source.displayName)
            .put("atlases", atlases)
        if (gameOver != null) input.put("gameOver", JSONObject().put("id", source.gameOverId).put("text", gameOver.raw))
        val result = NativeData.convert("character", input) ?: return null
        val json = result.optJSONObject("json") ?: return null
        return CharacterResult(source.id, json.toString(2), NativeData.report(result.optJSONObject("report")))
    }
}
