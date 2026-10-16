package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.mod.ArchiveKind
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.mod.ZipExtractor
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeekParser
import com.brenninhotools.vconverter.week.WeekSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModConversionTest {
    private val chart = """{"song":{"song":"Dad Battle","bpm":180,"speed":2,"needsVoices":true,"player1":"bf","player2":"dude","gfVersion":"gf","stage":"club","format":"psych_v1",
        "notes":[{"sectionBeats":4,"mustHitSection":true,"sectionNotes":[[0,0,0],[500,5,0]]}],"events":[]}}"""
    private val character = """{"animations":[{"offsets":[0,0],"loop":false,"fps":24,"anim":"idle","indices":[],"name":"dude idle"}],
        "image":"characters/dude","position":[0,0],"healthicon":"dude","flip_x":false,"camera_position":[0,0],"sing_duration":4,"scale":1,"vocals_file":"dude"}"""
    private val stage = """{"directory":"","defaultZoom":0.9,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100],"camera_boyfriend":[0,0],"camera_opponent":[0,0],"camera_girlfriend":[0,0]}"""
    private val week = """{"songs":[["Dad Battle","dude",[255,0,0]]],"weekCharacters":["dude","bf","gf"],"weekBackground":"club","storyName":"Club Night","weekName":"Week Dude","startUnlocked":true,"hideStoryMode":false,"hideFreeplay":false,"difficulties":""}"""
    private val menuDude = """{"image":"Menu_Dude","scale":1,"position":[20,10],"idle_anim":"Dude Idle","confirm_anim":"Dude Idle","flipX":false}"""

    private fun zip(entries: Map<String, String>): File {
        val file = Files.createTempFile("mod", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    @Test
    fun songPathMatchesPsych() {
        assertEquals("dad-battle", SongPath.format("Dad Battle"))
        assertEquals("whos-there", SongPath.format("Who's There?"))
    }

    @Test
    fun extractionBlocksPathTraversal() {
        val archive = zip(mapOf("ok/file.txt" to "hi", "../evil.txt" to "nope"))
        val head = archive.inputStream().use { it.readNBytes(8) }
        assertEquals(ArchiveKind.ZIP, ArchiveKind.detect(head))
        val target = Files.createTempDirectory("out").toFile()
        ZipExtractor.extract(archive, File(target, "inside"))
        assertTrue(File(target, "inside/ok/file.txt").exists())
        assertFalse(File(target, "evil.txt").exists())
    }

    @Test
    fun wholeModConvertsFromNestedArchive() {
        val archive = zip(
            mapOf(
                "My Mod/pack.json" to """{"name":"My Mod","description":"Test"}""",
                "My Mod/data/dad-battle/dad-battle.json" to chart,
                "My Mod/data/dad-battle/dad-battle-hard.json" to chart,
                "My Mod/songs/dad-battle/Inst.ogg" to "x",
                "My Mod/songs/dad-battle/Voices-dude.ogg" to "x",
                "My Mod/characters/dude.json" to character,
                "My Mod/stages/club.json" to stage,
                "My Mod/weeks/weekdude.json" to week,
                "My Mod/images/menucharacters/dude.json" to menuDude,
                "My Mod/scripts/thing.lua" to "function onCreate() makeLuaSprite('a', '', 0, 0) end"
            )
        )
        val extracted = Files.createTempDirectory("ex").toFile()
        ZipExtractor.extract(archive, extracted)
        val files = DirectoryModFiles(extracted)
        val scan = ModScanner.scan(files)
        assertEquals("My Mod/", scan.root)
        assertEquals(setOf("normal", "hard"), scan.songs.single().charts.keys)

        val out = Files.createTempDirectory("vslice").toFile()
        val result = ModConverter(files, scan, DirectoryModWriter(out)).convert(ModInfo("My Mod", "Test", "Me"))
        assertTrue(result.filesWritten >= 8)

        val metadata = JSONObject(File(out, "data/songs/dad-battle/dad-battle-metadata.json").readText())
        val characters = metadata.getJSONObject("playData").getJSONObject("characters")
        assertEquals("dude", characters.getJSONArray("opponentVocals").getString(0))
        assertFalse(characters.has("playerVocals"))
        assertEquals("club", metadata.getJSONObject("playData").getString("stage"))

        val level = JSONObject(File(out, "data/levels/weekdude.json").readText())
        assertEquals("dad-battle", level.getJSONArray("songs").getString(0))
        assertEquals(3, level.getJSONArray("props").length())
        assertTrue(File(out, "data/characters/dude.json").exists())
        assertTrue(File(out, "data/stages/club.json").exists())
        assertTrue(File(out, "songs/dad-battle/Voices-dude.ogg").exists())
        assertEquals("My Mod", JSONObject(File(out, "_polymod_meta.json").readText()).getString("title"))
        assertTrue(File(out, "scripts/psych/PsychLua_My_Mod_Global_thing.hxc").readText().contains("extends Module"))
        assertFalse(File(out, "scripts/psych").listFiles()!!.any { it.name.contains("Runtime") })
    }

    @Test
    fun menuCharacterIsPlacedLikePsych() {
        val result = PsychToVSliceLevel.convert(
            WeekSource("w", PsychWeekParser.parse(week), mapOf("dude" to PsychWeekParser.parseMenuCharacter(menuDude)))
        )
        val offsets = JSONObject(result.json).getJSONArray("props").getJSONObject(0).getJSONArray("offsets")
        assertEquals(320.0 - 150.0 - 20.0, offsets.getDouble(0), 0.001)
        assertEquals(70.0 - 10.0, offsets.getDouble(1), 0.001)
    }
}
