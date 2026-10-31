package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.codename.PsychToCodenameCharacter
import com.brenninhotools.vconverter.core.AnimateAtlas
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.reverse.VSliceToPsychCharacter
import com.brenninhotools.vconverter.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimateAtlasTest {
    private val animation = """{
      "AN": {"N": "dude", "SN": "dude main", "TL": {"L": [{"LN": "Layer 1", "FR": [
        {"I": 0, "DU": 4, "N": "idle", "E": [{"SI": {"SN": "dude idle", "FF": 0, "LP": "LP", "M3D": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]}}]},
        {"I": 4, "DU": 2, "N": "hey", "E": [{"SI": {"SN": "dude hey", "FF": 0, "LP": "PO", "M3D": [1,0,0,0, 0,1,0,0, 0,0,1,0, 10,0,0,1]}}]}
      ]}]}},
      "SD": {"S": [
        {"SN": "dude idle", "TL": {"L": [{"LN": "a", "FR": [{"I": 0, "DU": 4, "E": [{"ASI": {"N": "0000", "M3D": [1,0,0,0, 0,1,0,0, 0,0,1,0, -50,-200,0,1]}}]}]}]}},
        {"SN": "dude idle alt", "TL": {"L": [{"LN": "a", "FR": [{"I": 0, "DU": 1, "E": [{"ASI": {"N": "0000", "M3D": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]}}]}]}]}},
        {"SN": "dude hey", "TL": {"L": [{"LN": "a", "FR": [{"I": 0, "DU": 2, "E": [{"ASI": {"N": "0001", "MX": [2,0,0,2,-80,-260]}}]}]}]}}
      ]},
      "MD": {"FRT": 24}
    }"""

    private val spritemap = """{"ATLAS": {"SPRITES": [
      {"SPRITE": {"name": "0000", "x": 0, "y": 0, "w": 100, "h": 200, "rotated": false}},
      {"SPRITE": {"name": "0001", "x": 100, "y": 0, "w": 60, "h": 120, "rotated": true}}
    ]}, "meta": {"size": {"w": 256, "h": 256}}}"""

    private val psych = """{
      "animations": [
        {"anim": "idle", "name": "dude idle", "fps": 24, "loop": false, "indices": [], "offsets": [0, 0]},
        {"anim": "hey", "name": "dude hey", "fps": 24, "loop": false, "indices": [], "offsets": [-30, 40]}
      ],
      "image": "characters/dude", "scale": 1.5, "position": [20, 300], "camera_position": [10, -20],
      "flip_x": false, "no_antialiasing": false, "healthicon": "dude", "healthbar_colors": [10, 20, 30], "sing_duration": 4
    }"""

    @Test
    fun parsesBoundsAndLabels() {
        val atlas = AnimateAtlas.parse(animation, listOf(spritemap))
        assertEquals("dude main", atlas.mainSymbol)
        val idle = atlas.symbols.getValue("dude idle").bounds
        assertEquals(-50.0, idle.x, 1e-9)
        assertEquals(-200.0, idle.y, 1e-9)
        assertEquals(100.0, idle.width, 1e-9)
        val hey = atlas.symbols.getValue("dude hey").bounds
        assertEquals(-80.0, hey.x, 1e-9)
        assertEquals(240.0, hey.width, 1e-9)
        assertEquals(120.0, hey.height, 1e-9)
        assertEquals(listOf("idle", "hey"), atlas.labels.map { it.name })
        assertEquals(4, atlas.findLabel("hey")!!.start)
        assertEquals("dude idle", atlas.findSymbol("dude idle")!!.name)
        assertEquals(100.0 to 200.0, atlas.firstSprite)
        assertEquals(-70.0, atlas.mainBounds.x, 1e-9)
    }

    @Test
    fun psychAtlasRoundTripsThroughVSlice() {
        val atlas = AnimateAtlas.parse(animation, listOf(spritemap))
        val character = PsychCharacterParser.parse(psych)
        val vslice = PsychToVSliceCharacter.convert(CharacterSource("dude", character, CharacterSlot.OPPONENT, emptyMap(), animate = atlas))
        val json = JSONObject(vslice.json)
        assertEquals("animateatlas", json.getString("renderType"))
        val anims = json.getJSONArray("animations")
        assertEquals("symbol", anims.getJSONObject(0).getString("animType"))
        assertEquals("dude hey", anims.getJSONObject(1).getString("prefix"))
        assertEquals("Exact", vslice.report.stats.first { it.label == "Accuracy" }.value)

        val back = VSliceToPsychCharacter.convert("dude", VSliceParser.character(vslice.json), CharacterSlot.OPPONENT, emptyMap(), atlas)
        val root = JSONObject(back.json)
        val position = root.getJSONArray("position")
        assertEquals(20.0, position.getDouble(0), 1e-2)
        assertEquals(300.0, position.getDouble(1), 1e-2)
        val hey = root.getJSONArray("animations").getJSONObject(1)
        assertEquals(-30.0, hey.getJSONArray("offsets").getDouble(0), 1e-2)
        assertEquals(40.0, hey.getJSONArray("offsets").getDouble(1), 1e-2)
        assertEquals("dude hey", hey.getString("name"))
        assertEquals("dude idle\\", root.getJSONArray("animations").getJSONObject(0).getString("name"))
        val camera = root.getJSONArray("camera_position")
        assertEquals(10.0, camera.getDouble(0), 1e-2)
        assertEquals(-20.0, camera.getDouble(1), 1e-2)
    }

    @Test
    fun frameLabelsBecomeMainSymbolIndices() {
        val atlas = AnimateAtlas.parse(animation, listOf(spritemap))
        val vslice = """{"version": "1.0.0", "name": "Dude", "renderType": "animateatlas", "assetPath": "characters/dude", "scale": 1,
          "animations": [{"name": "idle", "prefix": "idle"}, {"name": "hey", "prefix": "hey", "offsets": [5, 5]}]}"""
        val root = JSONObject(VSliceToPsychCharacter.convert("dude", VSliceParser.character(vslice), CharacterSlot.OPPONENT, emptyMap(), atlas).json)
        val hey = root.getJSONArray("animations").getJSONObject(1)
        assertEquals("dude main", hey.getString("name"))
        assertEquals(listOf(4, 5), (0 until hey.getJSONArray("indices").length()).map { hey.getJSONArray("indices").getInt(it) })
    }

    @Test
    fun codenameGetsExactSymbols() {
        val atlas = AnimateAtlas.parse(animation, listOf(spritemap))
        val character = PsychCharacterParser.parse(psych.replace("\"dude hey\"", "\"dude he\""))
        val result = PsychToCodenameCharacter.convert("dude", character, CharacterSlot.OPPONENT, atlas)
        val xml = result.files.first().text
        assertTrue(xml, xml.contains("anim=\"dude hey\""))
        assertTrue(xml, xml.contains("centercam=\"true\""))
    }
}
