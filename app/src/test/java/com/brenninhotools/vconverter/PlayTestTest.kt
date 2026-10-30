package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.CharacterAnim
import com.brenninhotools.vconverter.editor.CharacterDocument
import com.brenninhotools.vconverter.editor.EditorNote
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.StageProp
import com.brenninhotools.vconverter.playtest.CharacterRole
import com.brenninhotools.vconverter.playtest.IssueLevel
import com.brenninhotools.vconverter.playtest.Judgement
import com.brenninhotools.vconverter.playtest.PlayEvent
import com.brenninhotools.vconverter.playtest.PlayTestEngine
import com.brenninhotools.vconverter.playtest.PlayTestLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTestTest {
    private val notes = listOf(
        EditorNote(1000.0, 0),
        EditorNote(1500.0, 1, 400.0),
        EditorNote(2000.0, 2),
        EditorNote(2500.0, 3),
        EditorNote(1000.0, 4),
        EditorNote(3000.0, 6)
    )

    @Test
    fun judgesHitsMissesAndHolds() {
        val engine = PlayTestEngine(notes)
        assertEquals(4, engine.playerNoteCount)
        val first = engine.press(0, 1020.0).single() as PlayEvent.Hit
        assertEquals(Judgement.SICK, first.judgement)
        assertTrue(engine.update(1020.0).any { it is PlayEvent.OpponentHit })
        engine.release(0, 1030.0)
        val hold = engine.press(1, 1440.0).single() as PlayEvent.Hit
        assertEquals(Judgement.GOOD, hold.judgement)
        engine.update(1600.0)
        engine.release(1, 1650.0)
        assertTrue(engine.update(1660.0).any { it is PlayEvent.HoldDropped })
        assertEquals(1, engine.holdDrops)
        assertTrue(engine.press(2, 2300.0).isEmpty())
        val missed = engine.update(2200.0)
        assertTrue(missed.any { it is PlayEvent.Miss })
        assertEquals(1, engine.misses)
        assertEquals(0, engine.combo)
        engine.press(3, 2640.0)
        engine.update(3100.0)
        assertTrue(engine.finished)
        assertEquals(2, engine.maxCombo)
        assertEquals("SDCB", engine.rating)
        assertTrue(engine.accuracy < 1.0)
    }

    @Test
    fun botplayHitsEverythingAndHealthCanRunOut() {
        val bot = PlayTestEngine(notes, botplay = true)
        listOf(500.0, 1000.0, 1500.0, 2000.0, 2500.0, 3000.0).forEach { bot.update(it) }
        assertEquals(0, bot.misses)
        assertEquals("PFC", bot.rating)
        assertTrue(bot.finished)
        val many = (0 until 40).map { EditorNote(1000.0 + it * 100.0, it % 4) }
        val loser = PlayTestEngine(many)
        loser.update(10000.0)
        assertNotNull(loser.deathTime)
        assertEquals(0.0, loser.health, 0.0)
    }

    private fun vsliceMod(): List<Pair<String, ByteArray>> {
        val doc = ChartDocument.blank("bopeebo", "Bopeebo", 100.0, listOf("easy", "normal"))
            .addNote("normal", EditorNote(600.0, 1))
            .addNote("normal", EditorNote(601.0, 1))
            .addNote("normal", EditorNote(1200.0, 5))
            .let { it.copy(meta = it.meta.copy(player = "bf", opponent = "dad", girlfriend = "gf", stage = "mainStage")) }
        val bf = CharacterDocument("bf", "Boyfriend", assetPath = "characters/BOYFRIEND", animations = listOf(CharacterAnim("idle", "BF idle dance"), CharacterAnim("singLEFT", "BF NOTE LEFT")))
        val stage = StageDocument("mainStage", "Main Stage", props = listOf(StageProp("bg", "stageback", -600.0, -200.0)))
        val xml = """<TextureAtlas imagePath="BOYFRIEND.png"><SubTexture name="BF idle dance0000" x="0" y="0" width="10" height="10"/></TextureAtlas>"""
        return listOf(
            "mymod/_polymod_meta.json" to "{}".toByteArray(),
            "mymod/data/songs/bopeebo/bopeebo-metadata.json" to doc.metadataJson().toByteArray(),
            "mymod/data/songs/bopeebo/bopeebo-chart.json" to doc.chartJson().toByteArray(),
            "mymod/songs/bopeebo/Inst.ogg" to byteArrayOf(1, 2, 3),
            "mymod/data/characters/bf.json" to bf.toJson().toByteArray(),
            "mymod/images/characters/BOYFRIEND.png" to byteArrayOf(1),
            "mymod/images/characters/BOYFRIEND.xml" to xml.toByteArray(),
            "mymod/data/stages/mainStage.json" to stage.toJson().toByteArray()
        )
    }

    @Test
    fun loadsVSliceModsAndReportsProblems() {
        val mod = PlayTestLoader.open(vsliceMod())
        assertEquals("V-Slice", mod.engine)
        assertEquals(listOf("bopeebo"), mod.songs.map { it.id })
        val song = PlayTestLoader.song(mod, mod.songs.single())
        assertEquals(3, song.document.notes("normal").size)
        assertEquals(listOf("Inst.ogg"), song.audio.map { it.name })
        assertNotNull(song.characters[CharacterRole.PLAYER])
        assertNull(song.characters[CharacterRole.OPPONENT])
        assertNotNull(song.stage)
        val text = song.issues.joinToString("\n") { "${it.level} ${it.area}: ${it.message}" }
        assertTrue(text, song.issues.any { it.area == "Chart" && it.message.contains("stacked") })
        assertTrue(text, song.issues.any { it.area.startsWith("Opponent") && it.message.contains("dad.json") })
        assertTrue(text, song.issues.any { it.area.startsWith("Player") && it.message.contains("singDOWN") })
        assertTrue(text, song.issues.any { it.level == IssueLevel.ERROR && it.message.contains("singLEFT") })
        assertTrue(text, song.issues.any { it.area.startsWith("Stage") && it.message.contains("bg") })
    }

    @Test
    fun convertsPsychModsBeforePlaying() {
        val chart = """{"song":{"song":"Test Song","notes":[{"sectionNotes":[[0,0,0],[500,5,0]],"mustHitSection":true,"lengthInSteps":16}],"bpm":120,"needsVoices":false,"player1":"bf","player2":"dad","gfVersion":"gf","stage":"stage","speed":1.5}}"""
        val files = listOf(
            "PsychMod/pack.json" to """{"name":"Test"}""".toByteArray(),
            "PsychMod/data/test-song/test-song.json" to chart.toByteArray(),
            "PsychMod/songs/test-song/Inst.ogg" to byteArrayOf(1, 2)
        )
        val mod = PlayTestLoader.open(files)
        assertTrue(mod.engine, mod.engine.startsWith("Psych"))
        val ref = mod.songs.single()
        val song = PlayTestLoader.song(mod, ref)
        assertEquals(2, song.document.difficulties.values.sumOf { it.notes.size })
        assertTrue(song.audio.isNotEmpty())
    }
}
