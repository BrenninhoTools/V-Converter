package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.convert.NmvConverters
import com.brenninhotools.vconverter.convert.NmvTab
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.nmv.LuaToNmv
import com.brenninhotools.vconverter.nmv.NmvFormats
import com.brenninhotools.vconverter.nmv.NmvModScanner
import com.brenninhotools.vconverter.nmv.NmvScripts
import com.brenninhotools.vconverter.nmv.NmvTarget
import com.brenninhotools.vconverter.nmv.NmvToMod
import com.brenninhotools.vconverter.nmv.ToNmvMod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NmvTest {
    private val psychV1 = """{"song":"Test Song","bpm":120,"speed":2,"player1":"bf","player2":"dad","gfVersion":"gf","stage":"stage","needsVoices":true,"format":"psych_v1",
        "notes":[{"sectionBeats":4,"mustHitSection":false,"sectionNotes":[[0,0,0],[500,5,250,"Hurt Note"]]}],"events":[[0,[["Hey!","BF",""]]]]}"""

    private fun braces(text: String) = text.count { it == '{' } == text.count { it == '}' } && text.count { it == '(' } == text.count { it == ')' }

    private fun write(root: File, files: Map<String, String>) {
        files.forEach { (path, text) -> File(root, path).apply { parentFile.mkdirs() }.writeText(text) }
    }

    @Test
    fun chartsRoundTrip() {
        val nmv = JSONObject(NmvFormats.chartToNmv(psychV1, "test-song").text).getJSONObject("song")
        assertEquals("nmv2", nmv.getString("format"))
        assertEquals(4, nmv.getInt("keys"))
        assertEquals(2, nmv.getInt("lanes"))
        val back = JSONObject(NmvFormats.chartToPsych(JSONObject().put("song", nmv).toString()).text).getJSONObject("song")
        assertEquals("psych_v1", back.getString("format"))
        assertEquals(5, back.getJSONArray("notes").getJSONObject(0).getJSONArray("sectionNotes").getJSONArray(1).getInt(1))
        assertFalse(back.has("keys"))
    }

    @Test
    fun extraLanesAreDropped() {
        val chart = """{"song":{"song":"x","bpm":100,"format":"nmv2","keys":4,"lanes":3,"notes":[{"mustHitSection":true,"sectionNotes":[[0,1,0],[0,6,0],[0,9,0]]}]}}"""
        val result = NmvFormats.chartToPsych(chart)
        val notes = JSONObject(result.text).getJSONObject("song").getJSONArray("notes").getJSONObject(0).getJSONArray("sectionNotes")
        assertEquals(2, notes.length())
        assertEquals(6, notes.getJSONArray(1).getInt(1))
        assertTrue(result.warnings.any { it.contains("dropped") })
    }

    @Test
    fun stagesKeepLayering() {
        val psych = """{"defaultZoom":0.9,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100],"objects":[
            {"type":"sprite","name":"sky","image":"sky","x":-100,"y":-50,"scale":[1,1],"scroll":[0.1,0.1],"color":"FFFFFF"},
            {"type":"gf"},{"type":"dad"},{"type":"boyfriend"},
            {"type":"animatedSprite","name":"crowd","image":"crowd","x":0,"y":500,"animations":[{"anim":"idle","name":"crowd idle","fps":24,"loop":true,"indices":[],"offsets":[0,0]}],"firstAnimation":"idle","color":"FF0000"}]}"""
        val nmv = JSONObject(NmvFormats.stageToNmv(psych).text)
        val objects = nmv.getJSONArray("stageObjects")
        assertEquals(2, objects.length())
        val sky = objects.getJSONObject(0)
        val crowd = objects.getJSONObject(1)
        assertTrue(sky.getInt("zIndex") < nmv.getInt("gfZIndex"))
        assertTrue(crowd.getInt("zIndex") > nmv.getInt("bfZIndex"))
        assertEquals("#FF0000", crowd.getString("colour"))
        val back = JSONObject(NmvFormats.stageToPsych(nmv.toString()).text).getJSONArray("objects")
        assertEquals(listOf("sky", "gf", "dad", "boyfriend", "crowd"), (0 until back.length()).map { back.getJSONObject(it).optString("name").ifEmpty { back.getJSONObject(it).getString("type") } })
    }

    @Test
    fun luaBecomesNmvHScript() {
        val lua = """
            local speed = 2
            function onCreate()
              makeLuaSprite('bg', 'stageback', -600, -200)
              setScrollFactor('bg', 0.9, 0.9)
              addLuaSprite('bg', false)
              runTimer('blink', 1.5, 0)
            end
            function onCreatePost()
              for i = 0, getProperty('unspawnNotes.length') - 1 do
                if getPropertyFromGroup('unspawnNotes', i, 'noteType') == 'Fire' then
                  setPropertyFromGroup('unspawnNotes', i, 'texture', 'FIRE_NOTE')
                end
              end
            end
            function goodNoteHit(id, dir, noteType, sustain)
              if noteType == 'Fire' then setProperty('health', getProperty('health') - 0.1) end
              if downscroll then debugPrint('down', dir) end
            end
            function onTimerCompleted(tag, loops, left)
              if tag == 'blink' then doTweenAlpha('fade', 'bg', 0.5, speed, 'quadOut') end
            end
            function onStartCountdown()
              return Function_Stop
            end
        """.trimIndent()
        val out = LuaToNmv().convert(lua)
        val text = out.source
        assertTrue(text, text.contains("function onLoad()"))
        assertTrue(text, text.contains("function goodNoteHit(note, field)"))
        assertTrue(text, text.contains("psychMakeLuaSprite("))
        assertTrue(text, text.contains("function psychMakeLuaSprite("))
        assertTrue(text, text.contains("function psychOnTimerCompleted("))
        assertTrue(text, text.contains("function onSpawnNote(note)"))
        assertTrue(text, text.contains("psychApplyNoteSets(note)"))
        assertTrue(text, text.contains("ClientPrefs.downScroll"))
        assertTrue(text, text.contains("return Function_Stop"))
        assertFalse(text, text.contains("function psychCameraShake("))
        assertTrue(text, braces(text))
    }

    @Test
    fun psychHScriptBecomesNmv() {
        val hx = """
            import backend.ClientPrefs;
            import objects.Character;
            import flixel.addons.display.FlxBackdrop;
            function onCreate() {
              if (ClientPrefs.data.lowQuality) return;
              trace(Conductor.crochet);
            }
        """.trimIndent()
        val out = NmvScripts.psychHxToNmv(hx).source
        assertTrue(out, out.contains("function onLoad()"))
        assertTrue(out, out.contains("ClientPrefs.lowQuality"))
        assertTrue(out, out.contains("Conductor.crotchet"))
        assertFalse(out, out.contains("import backend.ClientPrefs"))
        assertTrue(out, out.contains("import flixel.addons.display.FlxBackdrop;"))
        val back = NmvScripts.nmvHxToPsych(out).source
        assertTrue(back, back.contains("function onCreate()"))
        assertTrue(back, back.contains("ClientPrefs.data.lowQuality"))
    }

    @Test
    fun psychModBecomesNmvAndBack() {
        val dir = Files.createTempDirectory("nmv").toFile()
        val psych = File(dir, "psych/My Mod")
        write(psych, mapOf(
            "pack.json" to """{"name":"My Mod","description":"desc","restart":false,"runsGlobally":false}""",
            "data/test-song/test-song.json" to psychV1,
            "data/test-song/test-song-hard.json" to psychV1,
            "data/test-song/events.json" to """{"song":{"notes":[],"events":[[1000,[["Hey!","",""]]]]}}""",
            "data/test-song/script.lua" to "function onCreate() makeLuaText('t', 'hi', 0, 10, 10) addLuaText('t') end",
            "songs/test-song/Inst.ogg" to "x",
            "songs/test-song/Voices-Player.ogg" to "x",
            "characters/dad.json" to """{"animations":[{"anim":"idle","name":"Dad idle","fps":24,"loop":false,"indices":[],"offsets":[0,0]}],"image":"characters/DADDY_DEAREST","scale":1,"healthicon":"dad","position":[0,0],"camera_position":[0,0],"healthbar_colors":[175,102,206],"flip_x":false,"no_antialiasing":false,"sing_duration":6.1}""",
            "stages/park.json" to """{"defaultZoom":0.9,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100]}""",
            "stages/park.lua" to "function onCreate() makeLuaSprite('sky', 'park/sky', 0, 0) addLuaSprite('sky') end",
            "weeks/week1.json" to """{"songs":[["Test Song","dad",[146,113,253]]],"weekCharacters":["dad","bf","gf"],"weekBackground":"stage","storyName":"x","weekName":"Week 1","startUnlocked":true}""",
            "images/menucharacters/dad.json" to """{"image":"Menu_Dad","scale":1,"position":[0,0],"idle_anim":"idle","confirm_anim":"idle","flipX":false}""",
            "images/storymenu/week1.png" to "png",
            "custom_events/Flash.lua" to "function onEvent(n, a, b) if n == 'Flash' then cameraFlash('game', 'FFFFFF', 1) end end",
            "custom_events/Flash.txt" to "Flash the screen"
        ))
        val files = DirectoryModFiles(psych)
        val outDir = File(dir, "nmv")
        val result = ToNmvMod.fromPsych(files, ModScanner.scan(files), DirectoryModWriter(outDir), ModInfo("My Mod", "desc", "me"))
        assertTrue(result.filesWritten > 5)
        listOf(
            "meta.json", "songs/test-song/charts/normal.json", "songs/test-song/charts/hard.json", "songs/test-song/charts/events.json",
            "songs/test-song/meta.json", "songs/test-song/script.hx", "songs/test-song/audio/Inst.ogg", "songs/test-song/audio/Voices-player.ogg",
            "data/characters/dad.json", "data/stages/park/data.json", "data/stages/park/script.hx", "data/weeks/week1.json",
            "images/menus/story/characters/dad.json", "images/menus/story/weeks/week1.png", "data/events/Flash.hx", "data/events/Flash.txt"
        ).forEach { assertTrue("missing $it", File(outDir, it).isFile) }
        assertEquals("nmv2", JSONObject(File(outDir, "songs/test-song/charts/hard.json").readText()).getJSONObject("song").getString("format"))
        assertTrue(File(outDir, "data/stages/park/script.hx").readText().contains("function onLoad()"))
        assertTrue(braces(File(outDir, "data/events/Flash.hx").readText()))

        val nmvFiles = DirectoryModFiles(outDir)
        val scan = NmvModScanner.scan(nmvFiles)
        assertEquals(listOf("test-song"), scan.songs)
        val back = File(dir, "back")
        NmvToMod(nmvFiles, scan, DirectoryModWriter(back)).convert(ModInfo("My Mod", "desc", "me"), NmvTarget.PSYCH)
        listOf(
            "pack.json", "data/test-song/test-song.json", "data/test-song/test-song-hard.json", "data/test-song/events.json", "songs/test-song/Inst.ogg",
            "songs/test-song/Voices-Player.ogg", "characters/dad.json", "stages/park.json", "stages/park.hx", "weeks/week1.json",
            "images/menucharacters/dad.json", "images/storymenu/week1.png", "custom_events/Flash.hx"
        ).forEach { assertTrue("missing $it", File(back, it).isFile) }
        assertTrue(File(back, "stages/park.hx").readText().contains("function onCreate()"))

        val vslice = File(dir, "vslice")
        val toV = NmvToMod(nmvFiles, scan, DirectoryModWriter(vslice)).convert(ModInfo("My Mod", "desc", "me"), NmvTarget.VSLICE)
        assertTrue(toV.filesWritten > 0)
        assertTrue(File(vslice, "data/songs/test-song/test-song-chart.json").isFile)
        val cne = File(dir, "codename")
        NmvToMod(nmvFiles, scan, DirectoryModWriter(cne)).convert(ModInfo("My Mod", "desc", "me"), NmvTarget.CODENAME)
        assertTrue(File(cne, "songs/test-song/charts/hard.json").isFile)
        dir.deleteRecursively()
    }

    @Test
    fun fileTabsConvertNmvCharts() {
        val chart = NmvFormats.chartToNmv(psychV1, "test-song").text
        val file = ImportedFile("hard.json", "mod/songs/test-song/charts/hard.json", false) { chart.byteInputStream() }
        val toPsych = NmvConverters.create("nmv", "psych", NmvTab.CHARTS)
        assertTrue(toPsych.accept(file))
        val picked = toPsych.classify(file, chart)
        val out = toPsych.build(listOf(picked))
        assertEquals("data/test-song/test-song-hard.json", out.files.single().relativePath)
        val toNmv = NmvConverters.create("psych", "nmv", NmvTab.CHARTS)
        val psychFile = ImportedFile("test-song-hard.json", "data/test-song/test-song-hard.json", false) { psychV1.byteInputStream() }
        val back = toNmv.build(listOf(toNmv.classify(psychFile, psychV1)))
        assertTrue(back.files.any { it.relativePath == "songs/test-song/charts/hard.json" })
    }
}
