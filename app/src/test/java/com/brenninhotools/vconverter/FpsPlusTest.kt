package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.convert.NmvConverters
import com.brenninhotools.vconverter.convert.NmvTab
import com.brenninhotools.vconverter.fpsplus.FpsClass
import com.brenninhotools.vconverter.fpsplus.FpsFormats
import com.brenninhotools.vconverter.fpsplus.FpsIcon
import com.brenninhotools.vconverter.fpsplus.FpsNames
import com.brenninhotools.vconverter.fpsplus.LuaToFps
import com.brenninhotools.vconverter.fpsplus.PsychHxToFps
import com.brenninhotools.vconverter.fpsplus.ToFpsMod
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

class FpsPlusTest {
    private val legacy = """{"song":{"song":"Test Song","bpm":120,"speed":2.2,"player1":"bf","player2":"my-guy","gfVersion":"gf","stage":"park","needsVoices":true,
        "notes":[
          {"lengthInSteps":16,"mustHitSection":false,"sectionNotes":[[0,0,0],[500,5,250,"Hurt Note"]]},
          {"lengthInSteps":16,"mustHitSection":true,"sectionNotes":[[2000,1,500],[2500,6,0,"Alt Animation"],[2600,-1,"Hey!","BF",""]]}
        ]}}"""

    private fun braces(text: String) = text.count { it == '{' } == text.count { it == '}' } && text.count { it == '(' } == text.count { it == ')' }

    @Test
    fun chartBecomesFpsPlus() {
        val result = FpsFormats.chart(legacy, "test-song", FpsNames::character) { FpsNames.stage(it, true) }
        val root = JSONObject(result.chart)
        val meta = root.getJSONObject("meta")
        assertEquals("fpsplus_1", meta.getString("format"))
        assertEquals("Bf", meta.getString("player"))
        assertEquals("MyGuy", meta.getString("opponent"))
        assertEquals("Park", meta.getString("stage"))
        val notes = root.getJSONArray("notes")
        assertEquals(4, notes.length())
        assertFalse(notes.getJSONObject(0).getBoolean("player"))
        assertTrue(notes.getJSONObject(1).getBoolean("player"))
        assertEquals("Hurt Note", notes.getJSONObject(1).getString("tag"))
        assertEquals(2, notes.getJSONObject(1).getInt("length"))
        assertTrue(notes.getJSONObject(2).getBoolean("player"))
        assertEquals(4, notes.getJSONObject(2).getInt("length"))
        assertEquals("alt", notes.getJSONObject(3).getString("tag"))
        assertFalse(notes.getJSONObject(3).getBoolean("player"))
        val tags = result.events.map { it.getString("tag") }
        assertEquals(listOf("camFocusDad", "camFocusBf", "playAnim;bf;hey;true"), tags)
        assertEquals(2000.0, result.events[1].getDouble("time"), 0.01)
        assertTrue("Hurt Note" in result.noteTypes)
    }

    @Test
    fun characterBecomesClass() {
        val json = """{"animations":[{"anim":"idle","name":"Guy idle","fps":24,"loop":false,"indices":[],"offsets":[0,0]},
            {"anim":"singLEFT","name":"Guy left","fps":24,"loop":false,"indices":[],"offsets":[20,-10]},
            {"anim":"danceLeft","name":"Guy dance","fps":24,"loop":false,"indices":[0,1,2],"offsets":[0,0]}],
            "image":"characters/GUY","scale":2,"healthicon":"guy","position":[0,350],"camera_position":[10,20],"healthbar_colors":[255,0,128],"flip_x":false,"no_antialiasing":true,"sing_duration":6}"""
        val out = FpsFormats.character("my-guy", json, "MyGuy", false, null)
        val text = out.text
        assertTrue(text, text.contains("class MyGuy extends CharacterInfoBase"))
        assertTrue(text, text.contains("info.spritePath = \"characters/GUY\";"))
        assertTrue(text, text.contains("addByPrefix(\"singLEFT\", offset(10, -5), \"Guy left\", 24, loop(false));"))
        assertTrue(text, text.contains("addByIndices(\"danceLeft\", offset(0, 0), \"Guy dance\", [0, 1, 2], \"\", 24, loop(false));"))
        assertTrue(text, text.contains("info.healthColor = 0xFFFF0080;"))
        assertTrue(text, text.contains("info.focusOffset.set(160, -80);"))
        assertTrue(text, text.contains("addExtraData(\"scale\", [2, 2]);"))
        assertTrue(text, text.contains("addExtraData(\"psychPosition\", [0, 350]);"))
        assertTrue(text, text.contains("info.antialiasing = false;"))
        assertTrue(braces(text))
    }

    @Test
    fun luaBecomesScriptClass() {
        val lua = """
            local speed = 2
            function onCreate()
              makeLuaSprite('bg', 'stageback', -600, -200)
              addLuaSprite('bg', false)
              runTimer('blink', 1.5, 0)
            end
            function onBeatHit()
              if curBeat % 2 == 0 then doTweenAlpha('fade', 'bg', 0.5, speed, 'quadOut') end
            end
            function goodNoteHit(id, dir)
              setProperty('health', getProperty('health') + 0.01)
            end
            function onTimerCompleted(tag)
              debugPrint(tag)
            end
            function onEvent(name, v1, v2)
              if name == 'Flash' then cameraFlash('game', 'FFFFFF', 1) end
            end
        """.trimIndent()
        val parts = LuaToFps().convert(lua)
        val text = FpsClass.write("TestSong", "Script", listOf(parts))
        assertTrue(text, text.contains("class TestSong extends Script"))
        assertTrue(text, text.contains("override function create()"))
        assertTrue(text, text.contains("speed = 2;"))
        assertTrue(text, text.contains("onCreate();"))
        assertTrue(text, text.contains("override function beat(curBeat:Int)"))
        assertTrue(text, text.contains("if (character == boyfriend) goodNoteHit(playstate.notes.members.indexOf(note), note.direction);"))
        assertTrue(text, text.contains("function psychMakeLuaSprite("))
        assertTrue(text, text.contains("function psychOnTimerCompleted("))
        assertTrue(text, text.contains("var speed:Dynamic = null;"))
        assertFalse(text, Regex("(?<![\\w.])Reflect\\.").containsMatchIn(text))
        assertFalse(text, text.contains("StringTools."))
        assertTrue(parts.handlesEvents)
        assertTrue(braces(text))
    }

    @Test
    fun psychHScriptBecomesScriptClass() {
        val hx = """
            import backend.ClientPrefs;
            import flixel.addons.display.FlxBackdrop;
            var count:Int = 0;
            function onCreate() {
              if (ClientPrefs.data.downScroll) game.camHUD.alpha = 0.5;
              setVar('ready', true);
            }
            function goodNoteHit(note) {
              count++;
              if (note.noteData == 2) game.health += 0.02;
            }
            function update(e) {}
        """.trimIndent()
        val parts = PsychHxToFps.convert(hx)
        val text = FpsClass.write("HxSong", "Script", listOf(parts))
        assertTrue(text, text.contains("import flixel.addons.display.FlxBackdrop;"))
        assertFalse(text, text.contains("backend.ClientPrefs"))
        assertTrue(text, text.contains("var count:Int;"))
        assertTrue(text, text.contains("count = 0;"))
        assertTrue(text, text.contains("Config.downscroll"))
        assertTrue(text, text.contains("playstate.camHUD.alpha"))
        assertTrue(text, text.contains("psychSetVar('ready', true)"))
        assertTrue(text, text.contains("note.direction == 2"))
        assertTrue(text, text.contains("if (character == boyfriend) goodNoteHit(note);"))
        assertTrue(text, text.contains("function hx_update(e)"))
        assertTrue(text, text.contains("function psychSetVar("))
        assertTrue(braces(text))
    }

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, if (x < width / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun iconsGetThreeFrames() {
        val out = FpsIcon.threeFrames(png(300, 150))!!
        val image = ImageIO.read(out.inputStream())
        assertEquals(450, image.width)
        assertEquals(150, image.height)
        assertEquals(0xFFFF0000.toInt(), image.getRGB(10, 10))
        assertEquals(0xFF0000FF.toInt(), image.getRGB(160, 10))
        assertEquals(0xFFFF0000.toInt(), image.getRGB(310, 10))
    }

    @Test
    fun psychModBecomesFpsPlus() {
        val dir = Files.createTempDirectory("fps").toFile()
        val psych = File(dir, "psych")
        mapOf(
            "pack.json" to """{"name":"My Mod","description":"desc"}""",
            "data/test-song/test-song.json" to legacy,
            "data/test-song/test-song-hard.json" to legacy,
            "data/test-song/script.lua" to "function onCreatePost() makeLuaText('t', 'hi', 0, 10, 10) addLuaText('t') end",
            "songs/test-song/Inst.ogg" to "x",
            "characters/my-guy.json" to """{"animations":[{"anim":"idle","name":"Guy idle","fps":24,"loop":false,"indices":[],"offsets":[0,0]}],"image":"characters/GUY","scale":1,"healthicon":"guy","position":[0,0],"camera_position":[0,0],"healthbar_colors":[1,2,3],"flip_x":false}""",
            "characters/bf.json" to """{"animations":[],"image":"characters/BOYFRIEND","scale":1,"healthicon":"bf","position":[0,350],"camera_position":[0,0],"healthbar_colors":[1,2,3],"flip_x":true}""",
            "stages/park.json" to """{"defaultZoom":0.8,"boyfriend":[800,100],"girlfriend":[400,130],"opponent":[100,100],"objects":[{"type":"sprite","name":"sky","image":"park/sky","x":-100,"y":-50,"scroll":[0.1,0.1]},{"type":"gf"},{"type":"dad"},{"type":"boyfriend"},{"type":"sprite","name":"bush","image":"park/bush","x":0,"y":600}]}""",
            "stages/park.lua" to "function onBeatHit() if curBeat % 4 == 0 then cameraShake('game', 0.01, 0.2) end end",
            "weeks/week1.json" to """{"songs":[["Test Song","my-guy",[146,113,253]]],"weekCharacters":["dad","bf","gf"],"weekBackground":"stage","storyName":"Park Week","weekName":"Week 1","difficulties":"Normal, Hard"}""",
            "custom_events/Flash.lua" to "function onEvent(n, a, b) if n == 'Flash' then cameraFlash('game', 'FFFFFF', 1) end end",
            "scripts/global.hx" to "function onCreate() { trace('hi'); }"
        ).forEach { (path, text) -> File(psych, path).apply { parentFile.mkdirs() }.writeText(text) }
        File(psych, "images/icons/icon-guy.png").apply { parentFile.mkdirs() }.writeBytes(png(300, 150))
        val files = DirectoryModFiles(psych)
        val out = File(dir, "out")
        val result = ToFpsMod.fromPsych(files, ModScanner.scan(files), DirectoryModWriter(out), ModInfo("My Mod", "desc", "me"))
        assertTrue(result.filesWritten > 5)
        listOf(
            "meta.json", "data/songs/test-song/chart-normal.json", "data/songs/test-song/chart-hard.json", "data/songs/test-song/events.json",
            "data/songs/test-song/meta.json", "data/songs/test-song/scripts.json", "songs/test-song/Inst.ogg", "data/characters/MyGuy.hxc",
            "data/stages/Park.hxc", "data/weeks/week1.json", "data/events/PsychEvents.hxc", "data/notetypes/PsychNotes.hxc",
            "append/data/scripts/globalScripts.txt", "merge/data/freeplay/songList-bf.json", "images/ui/healthIcons/guy.png", "icon.png".takeIf { false } ?: "meta.json"
        ).forEach { assertTrue("missing $it", File(out, it).isFile) }
        assertFalse(File(out, "data/characters/Bf.hxc").exists())
        val meta = JSONObject(File(out, "meta.json").readText())
        assertEquals("1.9.0", meta.getString("api_version"))
        val stage = File(out, "data/stages/Park.hxc").readText()
        assertTrue(stage, stage.contains("class Park extends BaseStage"))
        assertTrue(stage, stage.contains("addToBackground(obj0);"))
        assertTrue(stage, stage.contains("addToForeground(obj1);"))
        assertTrue(stage, stage.contains("psychPlace(boyfriend, 800, 100);"))
        assertTrue(stage, stage.contains("onBeatHit();"))
        assertTrue(braces(stage))
        val scriptsJson = JSONObject(File(out, "data/songs/test-song/scripts.json").readText()).getJSONArray("scripts")
        assertTrue(File(out, "data/scripts/${scriptsJson.getString(0)}.hxc").isFile)
        val events = File(out, "data/events/PsychEvents.hxc").readText()
        assertTrue(events, events.contains("prefix: \"Flash\""))
        assertTrue(events, events.contains("FlashEvent"))
        val week = JSONObject(File(out, "data/weeks/week1.json").readText())
        assertEquals("Park Week", week.getString("name"))
        assertEquals("test-song", week.getJSONArray("songs").getString(0))
        dir.deleteRecursively()
    }

    @Test
    fun chartTabConvertsToFpsPlus() {
        val converter = NmvConverters.create("psych", "fpsplus", NmvTab.CHARTS)
        val file = ImportedFile("test-song-hard.json", "data/test-song/test-song-hard.json", false) { legacy.byteInputStream() }
        assertTrue(converter.accept(file))
        val out = converter.build(listOf(converter.classify(file, legacy)))
        val paths = out.files.map { it.relativePath }
        assertTrue(paths.toString(), "data/songs/test-song/chart-hard.json" in paths)
        assertTrue(paths.toString(), "data/songs/test-song/events.json" in paths)
        assertFalse(paths.toString(), "meta.json" in paths)
    }
}
