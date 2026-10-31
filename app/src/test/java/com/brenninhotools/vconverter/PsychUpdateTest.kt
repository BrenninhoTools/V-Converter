package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.PsychNoteStyles
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.mod.PsychLayout
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeekParser
import com.brenninhotools.vconverter.week.WeekSource
import com.brenninhotools.vconverter.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class PsychUpdateTest {
    @Test
    fun lockedWeeksGetALevelScript() {
        val week = """{"songs": [["Bopeebo", "dad", [146, 113, 253]]], "weekCharacters": ["dad", "bf", "gf"], "weekBackground": "stage", "storyName": "Daddy", "weekName": "Week 2",
            "weekBefore": "week1", "startUnlocked": false, "hiddenUntilUnlocked": true, "hideStoryMode": false, "hideFreeplay": false, "difficulties": ""}"""
        val result = PsychToVSliceLevel.convert(WeekSource("week2", PsychWeekParser.parse(week), emptyMap()))
        val (path, script) = result.extra.single()
        assertEquals("scripts/levels/Week2Level.hxc", path)
        assertTrue(script.contains("hasBeatenLevel(\"week1\")"))
        assertTrue(script.contains("override public function isVisible()"))
        assertTrue(script.contains("isUnlocked() ? songs : []"))

        val open = week.replace("\"startUnlocked\": false", "\"startUnlocked\": true").replace("\"hideFreeplay\": false", "\"hideFreeplay\": true")
        val hidden = PsychToVSliceLevel.convert(WeekSource("week2", PsychWeekParser.parse(open), emptyMap())).extra.single().second
        assertFalse(hidden.contains("isUnlocked():Bool"))
        assertTrue(hidden.contains("return [];"))

        val plain = week.replace("\"startUnlocked\": false", "\"startUnlocked\": true")
        assertTrue(PsychToVSliceLevel.convert(WeekSource("week2", PsychWeekParser.parse(plain), emptyMap())).extra.isEmpty())
    }

    @Test
    fun arrowSkinsBecomeNoteStyles() {
        assertFalse(PsychNoteStyles.isCustom("noteSkins/NOTE_assets"))
        assertFalse(PsychNoteStyles.isCustom("pixelUI/NOTE_assets"))
        assertTrue(PsychNoteStyles.isCustom("NOTE_assets_spooky"))
        val sheet = SparrowAtlas.parse(
            listOf("purple0000", "blue0000", "green0000", "red0000", "arrowLEFT0000", "arrowDOWN0000", "arrowUP0000", "arrowRIGHT0000")
                .joinToString("", "<TextureAtlas>", "</TextureAtlas>") { "<SubTexture name=\"$it\" x=\"0\" y=\"0\" width=\"10\" height=\"10\"/>" }
        )
        val style = PsychNoteStyles.build("NOTE_assets_spooky", sheet)!!
        assertEquals("psych-note-assets-spooky", style.id)
        val json = JSONObject(style.json)
        assertEquals("funkin", json.getString("fallback"))
        assertEquals("purple0", json.getJSONObject("assets").getJSONObject("note").getJSONObject("data").getJSONObject("left").getString("prefix"))
        assertEquals("left confirm", json.getJSONObject("assets").getJSONObject("noteStrumline").getJSONObject("data").getJSONObject("leftConfirm").getString("prefix"))
        assertNull(PsychNoteStyles.build("x", SparrowAtlas.parse("<TextureAtlas><SubTexture name=\"idle0000\" x=\"0\" y=\"0\" width=\"1\" height=\"1\"/></TextureAtlas>")))
        assertEquals("HURTNOTE_assets", PsychNoteStyles.textureIn("setPropertyFromGroup('unspawnNotes', i, 'texture', 'HURTNOTE_assets')"))
        val (path, script) = PsychNoteStyles.noteKindScript("hurt", "Hurt Note", style.id)
        assertEquals("scripts/notekinds/HurtNoteTextureNoteKind.hxc", path)
        assertTrue(script.contains("super(\"hurt\", \"Hurt Note\", \"psych-note-assets-spooky\")"))
    }

    @Test
    fun engineBuildsAreScannedLikeMods() {
        assertEquals("data/bopeebo/bopeebo.json", PsychLayout.normalize("assets/preload/data/bopeebo/bopeebo.json"))
        assertEquals("images/stageback.png", PsychLayout.normalize("assets/base_game/shared/images/stageback.png"))
        assertEquals("songs/bopeebo/Inst.ogg", PsychLayout.normalize("assets/songs/bopeebo/Inst.ogg"))
        val paths = listOf(
            "Mod/example_mods/data/readme.txt", "Mod/example_mods/images/readme.txt", "Mod/example_mods/songs/readme.txt",
            "Mod/assets/preload/data/bopeebo/bopeebo.json", "Mod/assets/preload/data/bopeebo/bopeebo-hard.json",
            "Mod/assets/preload/characters/dad.json", "Mod/assets/preload/weeks/week1.json", "Mod/assets/preload/stages/stage.json",
            "Mod/assets/shared/images/characters/DADDY_DEAREST.png", "Mod/assets/shared/images/characters/DADDY_DEAREST.xml",
            "Mod/assets/songs/bopeebo/Inst.ogg", "Mod/assets/songs/bopeebo/Voices.ogg"
        )
        val files = object : ModFiles {
            override val paths: List<String> = paths
            override fun open(path: String): InputStream = "{}".byteInputStream()
            override fun size(path: String): Long = 2
        }
        val scan = ModScanner.scan(files)
        assertEquals("Mod/", scan.root)
        assertEquals(listOf("bopeebo"), scan.songs.map { it.id })
        assertEquals(setOf("normal", "hard"), scan.songs.single().charts.keys)
        assertEquals(2, scan.songs.single().audio.size)
        assertEquals(1, scan.characters.size)
        assertTrue(scan.assets.any { it.endsWith("DADDY_DEAREST.xml") })
        assertEquals("images/characters/DADDY_DEAREST.xml", scan.relative("Mod/assets/shared/images/characters/DADDY_DEAREST.xml"))
    }

    @Test
    fun loopAnimationsBecomeHoldAnimations() {
        val psych = """{"animations": [{"anim": "idle", "name": "idle", "fps": 24, "loop": false, "indices": [], "offsets": [0, 0]},
            {"anim": "idle-loop", "name": "idle loop", "fps": 24, "loop": true, "indices": [], "offsets": [0, 0]}],
            "image": "characters/dad", "scale": 1, "position": [0, 0], "camera_position": [0, 0], "flip_x": false, "healthicon": "dad", "sing_duration": 4}"""
        val result = PsychToVSliceCharacter.convert(CharacterSource("dad", PsychCharacterParser.parse(psych), CharacterSlot.OPPONENT, emptyMap()))
        val anims = JSONObject(result.json).getJSONArray("animations")
        assertEquals("idle-hold", anims.getJSONObject(1).getString("name"))
        assertNotNull(result.report.sections.firstOrNull { it.title == "Loop animations" })
        assertTrue(result.report.warnings.none { it.contains("-loop") })
    }
}
