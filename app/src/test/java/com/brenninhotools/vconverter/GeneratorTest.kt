package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.generator.Callback
import com.brenninhotools.vconverter.generator.CharacterGenerator
import com.brenninhotools.vconverter.generator.CharacterSpec
import com.brenninhotools.vconverter.generator.LevelGenerator
import com.brenninhotools.vconverter.generator.LevelPropSpec
import com.brenninhotools.vconverter.generator.LevelSpec
import com.brenninhotools.vconverter.generator.MenuProp
import com.brenninhotools.vconverter.generator.ModGenerator
import com.brenninhotools.vconverter.generator.ModSpec
import com.brenninhotools.vconverter.generator.PickedAsset
import com.brenninhotools.vconverter.generator.PropSpec
import com.brenninhotools.vconverter.generator.ScriptGenerator
import com.brenninhotools.vconverter.generator.ScriptKind
import com.brenninhotools.vconverter.generator.ScriptSpec
import com.brenninhotools.vconverter.generator.SongGenerator
import com.brenninhotools.vconverter.generator.SongSpec
import com.brenninhotools.vconverter.generator.StageGenerator
import com.brenninhotools.vconverter.generator.StageSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {
    @Test
    fun modBaseHasMetaIconAndModule() {
        val result = ModGenerator.generate(ModSpec("Vs Dude", "Test", "Me, You", icon = PickedAsset("icon.png", byteArrayOf(1))))
        val meta = JSONObject(result.files.first { it.path == "_polymod_meta.json" }.text!!)
        assertEquals("Vs Dude", meta.getString("title"))
        assertEquals(2, meta.getJSONArray("contributors").length())
        assertTrue(result.files.any { it.path == "_polymod_icon.png" && it.bytes != null })
        assertTrue(result.files.any { it.path == "scripts/modules/VsDudeModule.hxc" })
    }

    @Test
    fun songWithErectVariation() {
        val result = SongGenerator.generate(
            SongSpec("My Song", "My Song", bpm = 150.0, difficulties = listOf("easy", "hard", "erect"), instrumental = PickedAsset("inst.ogg", byteArrayOf(1)))
        )
        val paths = result.files.map { it.path }
        assertTrue("data/songs/my-song/my-song-metadata.json" in paths)
        assertTrue("data/songs/my-song/my-song-chart-erect.json" in paths)
        assertTrue("songs/my-song/Inst.ogg" in paths)
        val meta = JSONObject(result.files.first { it.path.endsWith("my-song-metadata.json") }.text!!)
        assertEquals("erect", meta.getJSONObject("playData").getJSONArray("songVariations").getString(0))
        assertEquals(150.0, meta.getJSONArray("timeChanges").getJSONObject(0).getDouble("bpm"), 0.0)
        val chart = JSONObject(result.files.first { it.path.endsWith("my-song-chart.json") }.text!!)
        assertEquals(0, chart.getJSONObject("notes").getJSONArray("hard").length())
    }

    @Test
    fun characterAnimationsAreGuessedFromAtlas() {
        val atlas = SparrowAtlas.parse(
            """<TextureAtlas imagePath="dude.png">
              <SubTexture name="Dude Idle0000" x="0" y="0" width="10" height="10"/>
              <SubTexture name="Dude Idle0001" x="0" y="0" width="10" height="10"/>
              <SubTexture name="Dude Sing Left0000" x="0" y="0" width="10" height="10"/>
              <SubTexture name="Dude Sing Left Miss0000" x="0" y="0" width="10" height="10"/>
              <SubTexture name="Dude Sing UP0000" x="0" y="0" width="10" height="10"/>
            </TextureAtlas>"""
        )
        val anims = CharacterGenerator.guessAll(CharacterGenerator.prefixes(atlas))
        assertEquals(listOf("idle", "singLEFT", "singLEFTmiss", "singUP"), anims.map { it.name })
        val result = CharacterGenerator.generate(CharacterSpec("dude", animations = anims, image = PickedAsset("Dude.png", byteArrayOf(1))))
        val json = JSONObject(result.files.first().text!!)
        assertEquals("characters/Dude", json.getString("assetPath"))
        assertEquals(4, json.getJSONArray("animations").length())
        assertTrue(result.files.any { it.path == "images/characters/Dude.png" })
        assertTrue(result.report.warnings.any { it.contains("singDOWN") })
    }

    @Test
    fun stageUsesBaseGamePositions() {
        val result = StageGenerator.generate(
            StageSpec("Dude Club", cameraZoom = 0.9, backgroundColor = "222222", props = listOf(PropSpec("floor", PickedAsset("floor.png", byteArrayOf(1)), y = 600.0)))
        )
        val json = JSONObject(result.files.first().text!!)
        val bf = json.getJSONObject("characters").getJSONObject("bf").getJSONArray("position")
        assertEquals(989.5, bf.getDouble(0), 0.0)
        assertEquals(885.0, bf.getDouble(1), 0.0)
        assertEquals("#222222", json.getJSONArray("props").getJSONObject(0).getString("assetPath"))
        assertEquals("stages/dude-club/floor", json.getJSONArray("props").getJSONObject(1).getString("assetPath"))
        assertTrue(result.files.any { it.path == "images/stages/dude-club/floor.png" })
    }

    @Test
    fun levelListsSongsAndProps() {
        val result = LevelGenerator.generate(
            LevelSpec("weekdude", "Dude Week", listOf("Song One", "song-two"), opponent = LevelPropSpec(MenuProp.CUSTOM, idlePrefix = "dude idle"), girlfriend = LevelPropSpec(MenuProp.NONE))
        )
        val json = JSONObject(result.files.first().text!!)
        assertEquals("song-one", json.getJSONArray("songs").getString(0))
        assertEquals(2, json.getJSONArray("props").length())
        assertEquals("storymenu/props/weekdude-dad", json.getJSONArray("props").getJSONObject(0).getString("assetPath"))
        assertEquals("DUDE WEEK", json.getString("name"))
    }

    @Test
    fun scriptTemplatesHaveTheRightShape() {
        ScriptKind.entries.forEach { kind ->
            val spec = ScriptSpec(kind, "", "dude", Callback.entries.toSet(), fields = ScriptGenerator.parseFields("intensity:float, label, on:bool"))
            val file = ScriptGenerator.generate(spec).files.single()
            val text = file.text!!
            assertTrue(file.path.startsWith("scripts/${kind.folder}/"))
            assertTrue(text.contains("super(\"dude\""))
            assertFalse(text.contains("trace("))
            assertFalse(text.contains("//"))
            assertEquals(text.count { it == '{' }, text.count { it == '}' })
        }
        val noteKind = ScriptGenerator.generate(ScriptSpec(ScriptKind.NOTE_KIND, "", "dude", Callback.entries.toSet())).files.single().text!!
        assertFalse(noteKind.contains("onBeatHit"))
        assertTrue(noteKind.contains("onNoteHit"))
    }
}
