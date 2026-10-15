package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterAndStageTest {
    private val xml = """
        <TextureAtlas imagePath="dude.png">
          <SubTexture name="dude idle0000" x="0" y="0" width="400" height="380" frameX="-2" frameY="-4" frameWidth="410" frameHeight="400"/>
          <SubTexture name="dude idle0001" x="0" y="0" width="400" height="380" frameX="-2" frameY="-4" frameWidth="410" frameHeight="400"/>
          <SubTexture name="dude left0000" x="0" y="0" width="420" height="390"/>
        </TextureAtlas>
    """.trimIndent()

    private val character = """
        {"animations":[
          {"offsets":[-5,3],"loop":false,"fps":24,"anim":"idle","indices":[],"name":"dude idle"},
          {"offsets":[10,-4],"loop":false,"fps":24,"anim":"singLEFT","indices":[],"name":"dude left"}],
         "image":"characters/Dude","position":[20,300],"healthicon":"dude","flip_x":false,
         "camera_position":[15,-10],"sing_duration":4,"scale":1.5,"no_antialiasing":false}
    """.trimIndent()

    private val stage = """
        {"directory":"","defaultZoom":0.85,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100],
         "camera_boyfriend":[0,0],"camera_opponent":[5,5],"camera_girlfriend":[0,0],"camera_speed":1,
         "objects":[{"type":"sprite","name":"bg","image":"bg","x":-500,"y":-300,"scale":[1,1],"scroll":[0.9,0.9],"alpha":1,"angle":0,"color":"FFFFFF","filters":3,"antialiasing":true},
                    {"type":"dad"},{"type":"gf"},{"type":"boyfriend"}]}
    """.trimIndent()

    @Test
    fun characterLandsWherePsychDrawsIt() {
        val psych = PsychCharacterParser.parse(character)
        val atlas = SparrowAtlas.parse(xml)
        val result = PsychToVSliceCharacter.convert(
            CharacterSource("dude", psych, CharacterSlot.OPPONENT, mapOf(PsychToVSliceCharacter.atlasKey(psych.image) to atlas))
        )
        val json = JSONObject(result.json)
        val stageJson = JSONObject(PsychToVSliceStage.convert(StageSource("s", PsychStageParser.parse(stage))).json)
        val anchor = stageJson.getJSONObject("characters").getJSONObject("dad").getJSONArray("position")
        val s = psych.scale
        val first = atlas.firstFrame!!
        val idle = atlas.firstFrameOf("dude idle")!!

        val animations = json.getJSONArray("animations")
        psych.animations.forEachIndexed { i, anim ->
            val offsets = animations.getJSONObject(i).getJSONArray("offsets")
            val psychLeft = 100 + psych.positionX - anim.offsetX + (1 - s) * first.sourceWidth / 2
            val psychTop = 100 + psych.positionY - anim.offsetY + (1 - s) * first.sourceHeight / 2
            val vLeft = anchor.getDouble(0) - s * idle.sourceWidth / 2 - offsets.getDouble(0) * s
            val vTop = anchor.getDouble(1) - s * idle.sourceHeight - offsets.getDouble(1) * s
            assertEquals(psychLeft, vLeft, 0.01)
            assertEquals(psychTop, vTop, 0.01)
        }
        assertEquals("Exact", result.report.stats.first { it.label == "Accuracy" }.value)
    }

    @Test
    fun stageKeepsLayeringAndCamera() {
        val json = JSONObject(PsychToVSliceStage.convert(StageSource("s", PsychStageParser.parse(stage))).json)
        val chars = json.getJSONObject("characters")
        assertTrue(chars.getJSONObject("dad").getInt("zIndex") < chars.getJSONObject("gf").getInt("zIndex"))
        assertTrue(chars.getJSONObject("gf").getInt("zIndex") < chars.getJSONObject("bf").getInt("zIndex"))
        val dadCamera = chars.getJSONObject("dad").getJSONArray("cameraOffsets")
        assertEquals(155.0, dadCamera.getDouble(0), 0.001)
        assertEquals(-95.0, dadCamera.getDouble(1), 0.001)
        assertEquals(1, json.getJSONArray("props").length())
    }
}
