package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.reverse.VSliceToPsychStage
import com.brenninhotools.vconverter.stage.LuaStageExtractor
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageConversionTest {
    private val atlas = SparrowAtlas.parse(
        """<TextureAtlas imagePath="dancer.png">
          <SubTexture name="bop0000" x="0" y="0" width="200" height="300"/>
          <SubTexture name="bop0001" x="200" y="0" width="200" height="300"/>
          <SubTexture name="hey0000" x="400" y="0" width="200" height="300"/>
        </TextureAtlas>"""
    )

    private val psychStage = """{
        "directory": "mymod", "defaultZoom": 0.8, "isPixelStage": false, "stageUI": "normal",
        "boyfriend": [770, 100], "girlfriend": [400, 130], "opponent": [100, 100], "hide_girlfriend": true,
        "camera_boyfriend": [0, 0], "camera_opponent": [0, 0], "camera_girlfriend": [0, 0], "camera_speed": 1,
        "objects": [
          {"type": "sprite", "name": "bg", "image": "stageback", "x": -600, "y": -200, "scale": [1, 1], "scroll": [0.9, 0.9], "alpha": 1, "angle": 0, "color": "FFFFFF", "filters": 3},
          {"type": "animatedSprite", "name": "dancer", "image": "dancer", "x": 100, "y": 50, "scale": [2, 2], "scroll": [1, 1], "alpha": 1, "angle": 0, "color": "FFFFFF", "filters": 3,
            "firstAnimation": "bop", "animations": [
              {"anim": "bop", "name": "bop", "fps": 24, "loop": false, "indices": [], "offsets": [0, 0]},
              {"anim": "hey", "name": "hey", "fps": 24, "loop": false, "indices": [], "offsets": [-180, -140]}
            ]},
          {"type": "square", "name": "fade", "x": 0, "y": 0, "scale": [1280, 720], "scroll": [0, 0], "alpha": 0.5, "angle": 0, "color": "000000", "filters": 2},
          {"type": "sprite", "name": "lowOnly", "image": "x", "x": 0, "y": 0, "scale": [1, 1], "scroll": [1, 1], "alpha": 1, "angle": 0, "color": "FFFFFF", "filters": 1}
        ]
    }"""

    @Test
    fun psychStageBecomesLoadableVSliceStage() {
        val result = PsychToVSliceStage.convert(StageSource("custom", PsychStageParser.parse(psychStage), atlas = { if (it == "dancer") atlas else null }))
        val json = JSONObject(result.json)
        assertEquals("shared", json.getString("directory"))
        val props = json.getJSONArray("props")
        assertEquals(3, props.length())
        val chars = json.getJSONObject("characters")
        assertEquals(0.0, chars.getJSONObject("gf").getDouble("alpha"), 0.0)
        val top = (0 until props.length()).maxOf { props.getJSONObject(it).getInt("zIndex") }
        assertTrue(chars.getJSONObject("bf").getInt("zIndex") > top)
        assertTrue(chars.getJSONObject("bf").getInt("zIndex") > chars.getJSONObject("dad").getInt("zIndex"))
        val dancer = props.getJSONObject(1)
        assertEquals("bop", dancer.getString("startingAnimation"))
        val anims = dancer.getJSONArray("animations")
        assertFalse(anims.getJSONObject(0).has("offsets"))
        val hey = anims.getJSONObject(1).getJSONArray("offsets")
        assertEquals((-180.0 + 100.0) / 2.0, hey.getDouble(0), 0.001)
        assertEquals((-140.0 + 150.0) / 2.0, hey.getDouble(1), 0.001)
        val fade = props.getJSONObject(2)
        assertEquals("#000000", fade.getString("assetPath"))
        assertEquals(1280, fade.getJSONArray("scale").getInt(0))
    }

    @Test
    fun luaBopsBecomeDanceEvery() {
        val lua = """
function onCreatePost()
    playAnim('dancer', 'bop', true)
    setBlendMode('dancer', 'add')
end

function onBeatHit()
    if curBeat % 2 == 0 then
        playAnim('dancer', 'bop', true)
    end
end
"""
        val stage = PsychStageParser.parse(psychStage)
        val props = LuaStageExtractor.extract(lua, stage.objectNames)
        val merged = LuaStageExtractor.merge(stage, props)
        val dancer = merged.objects!!.first { it.name == "dancer" }
        assertEquals(2.0, dancer.danceEvery, 0.0)
        assertEquals("add", dancer.blend)
        assertTrue(props.removedLines.isNotEmpty())
        val json = JSONObject(PsychToVSliceStage.convert(StageSource("custom", merged, atlas = { atlas })).json)
        val prop = json.getJSONArray("props").getJSONObject(1)
        assertEquals(2.0, prop.getDouble("danceEvery"), 0.0)
        assertEquals("idle", prop.getString("startingAnimation"))
        assertEquals("add", prop.getString("blend"))
    }

    @Test
    fun vsliceStageGetsLuaForBoppers() {
        val vslice = """{"version":"1.0.0","name":"Test","cameraZoom":1.1,"directory":"week4","props":[
            {"name":"dancer","assetPath":"dancer","position":[10,20],"zIndex":10,"scale":[2,2],"danceEvery":1,"animations":[
              {"name":"danceLeft","prefix":"bop","frameIndices":[0]},{"name":"danceRight","prefix":"bop","frameIndices":[1]}]},
            {"name":"trees","assetPath":"trees","position":[0,0],"zIndex":5,"animType":"packer","startingAnimation":"loop","animations":[{"name":"loop","frameIndices":[0,1,2],"looped":true}]},
            {"name":"glow","assetPath":"glow","position":[0,0],"zIndex":20,"blend":"add"}
          ],"characters":{"bf":{"zIndex":300,"position":[989.5,885],"cameraOffsets":[-100,-100]},"dad":{"zIndex":200,"position":[335,885],"cameraOffsets":[150,-100]},"gf":{"zIndex":100,"position":[751.5,787],"cameraOffsets":[0,0],"alpha":0}}}"""
        val result = VSliceToPsychStage.convert("test", VSliceParser.stage(vslice), { if (it == "dancer") atlas else null })
        val root = JSONObject(result.json)
        assertTrue(root.getBoolean("hide_girlfriend"))
        val lua = result.extra.single { it.first == "stages/test.lua" }.second
        assertTrue(lua.contains("function onBeatHit()"))
        assertTrue(lua.contains("playAnim('dancer', 'danceLeft', false)"))
        assertTrue(lua.contains("addAnimation('trees', 'loop', {0, 1, 2}, 24, true)"))
        assertTrue(lua.contains("setBlendMode('glow', 'add')"))
        val back = PsychStageParser.parse(result.json).let { LuaStageExtractor.merge(it, LuaStageExtractor.extract(lua, it.objectNames)) }
        assertEquals(1.0, back.objects!!.first { it.name == "dancer" }.danceEvery, 0.0)
        val again = JSONObject(PsychToVSliceStage.convert(StageSource("test", back, atlas = { if (it == "dancer") atlas else null })).json)
        val dancer = (0 until again.getJSONArray("props").length()).map { again.getJSONArray("props").getJSONObject(it) }.first { it.getString("name") == "dancer" }
        assertEquals(1.0, dancer.getDouble("danceEvery"), 0.0)
        val offsets = dancer.getJSONArray("animations").getJSONObject(1).optJSONArray("offsets")
        assertNull(offsets)
        val trees = (0 until again.getJSONArray("props").length()).map { again.getJSONArray("props").getJSONObject(it) }.first { it.getString("name") == "trees" }
        assertEquals("packer", trees.getString("animType"))
    }
}
