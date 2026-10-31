package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.codename.CodenameChartParser
import com.brenninhotools.vconverter.codename.CodenameMeta
import com.brenninhotools.vconverter.codename.CodenameModConverter
import com.brenninhotools.vconverter.codename.CodenameModScanner
import com.brenninhotools.vconverter.codename.CodenameScope
import com.brenninhotools.vconverter.codename.CodenameScriptTarget
import com.brenninhotools.vconverter.codename.CodenameSong
import com.brenninhotools.vconverter.codename.CodenameToPsychChart
import com.brenninhotools.vconverter.codename.CodenameToPsychMod
import com.brenninhotools.vconverter.codename.CodenameToPsychScript
import com.brenninhotools.vconverter.codename.LuaToCodename
import com.brenninhotools.vconverter.codename.PsychHxToCodename
import com.brenninhotools.vconverter.codename.PsychToCodenameCharacter
import com.brenninhotools.vconverter.codename.PsychToCodenameChart
import com.brenninhotools.vconverter.codename.PsychToCodenameEvents
import com.brenninhotools.vconverter.codename.PsychToCodenameMod
import com.brenninhotools.vconverter.codename.XmlNode
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrossEngineTest {
    private val psychChart = """{"song":{"song":"Test Song","bpm":120,"speed":2.5,"player1":"bf","player2":"dad","gfVersion":"gf","stage":"stage","needsVoices":true,
        "notes":[
          {"sectionBeats":4,"mustHitSection":false,"sectionNotes":[[0,0,0],[500,1,250,"Hurt Note"]]},
          {"sectionBeats":4,"mustHitSection":true,"sectionNotes":[[2000,6,0],[2500,7,0,"Hey!"]]},
          {"sectionBeats":4,"mustHitSection":true,"gfSection":true,"sectionNotes":[[4000,2,0]]}
        ],
        "events":[[1000,[["Hey!","BF","0.5"],["Add Camera Zoom","0.03",""]]],[3000,[["Change Scroll Speed","1.5","0"]]],[3500,[["Camera Follow Pos","100","200"]]],[5000,[["Camera Follow Pos","",""]]]]}}"""

    private val codenameChart = """{"codenameChart":true,"stage":"mall-evil","scrollSpeed":2.2,"noteTypes":["Alt Anim Note","Hurt Note"],
        "strumLines":[
          {"characters":["dad"],"type":0,"position":"dad","notes":[{"time":0,"id":0,"type":1,"sLen":0},{"time":500,"id":3,"type":0,"sLen":250}]},
          {"characters":["bf"],"type":1,"position":"boyfriend","notes":[{"time":1000,"id":2,"type":2,"sLen":0}]},
          {"characters":["gf"],"type":2,"position":"girlfriend","notes":[]}
        ],
        "events":[
          {"name":"Camera Movement","time":0,"params":[1]},
          {"name":"Camera Movement","time":2000,"params":[0]},
          {"name":"Add Camera Zoom","time":2500,"params":[0.05,"camGame"]},
          {"name":"Camera Flash","time":3000,"params":[false,"#FFFFFF",4,"camGame"]}
        ]}"""

    @Test
    fun psychChartBecomesCodenameStrumlines() {
        val song = PsychChartParser.parse(psychChart)
        val result = PsychToCodenameChart.convert(listOf(ChartSource("hard", song)))
        val chart = JSONObject(result.charts.getValue("hard"))
        assertTrue(chart.getBoolean("codenameChart"))
        val lines = chart.getJSONArray("strumLines")
        assertEquals(3, lines.length())
        assertEquals("dad", lines.getJSONObject(0).getJSONArray("characters").getString(0))
        assertEquals(1, lines.getJSONObject(1).getInt("type"))
        assertEquals(2, lines.getJSONObject(2).getInt("type"))
        val types = (0 until chart.getJSONArray("noteTypes").length()).map { chart.getJSONArray("noteTypes").getString(it) }
        assertTrue("Hurt Note" in types && "Hey!" in types)
        val events = chart.getJSONArray("events")
        val names = (0 until events.length()).map { events.getJSONObject(it).getString("name") }
        assertTrue("Camera Movement" in names)
        assertTrue("Hey!" in names)
        assertTrue("Scroll Speed Change" in names)
        assertTrue("Camera Position" in names)
        val zooms = (0 until events.length()).map { events.getJSONObject(it) }.filter { it.getString("name") == "Add Camera Zoom" }
        assertEquals(listOf("camGame", "camHUD"), zooms.map { it.getJSONArray("params").getString(1) })
        assertEquals(1, result.events["Hey!"])
        assertTrue(result.meta.contains("\"bpm\": 120"))
    }

    @Test
    fun forcedCameraKeepsFocusEvents() {
        val song = PsychChartParser.parse(psychChart)
        val chart = JSONObject(PsychToCodenameChart.convert(listOf(ChartSource("normal", song))).charts.getValue("normal"))
        val events = chart.getJSONArray("events").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val reapplied = events.filter { it.getString("name") == "Camera Position" && it.getDouble("time") > 3500 && it.getDouble("time") < 5000 }
        assertTrue(reapplied.isNotEmpty() || events.none { it.getString("name") == "Camera Movement" && it.getDouble("time") in 3500.0..5000.0 })
    }

    @Test
    fun codenameChartBecomesPsychChart() {
        val data = CodenameChartParser.parseChart(codenameChart)
        val meta = CodenameMeta.default("test").copy(bpm = 150.0, displayName = "Test")
        val result = CodenameToPsychChart.convert(CodenameSong("test", meta, mapOf("hard" to data)))
        val file = result.files.single { it.path.endsWith("test-hard.json") }
        val root = JSONObject(file.json)
        val song = root.optJSONObject("song") ?: root
        assertEquals("mallEvil", song.getString("stage"))
        assertEquals(150.0, song.getDouble("bpm"), 0.0)
        assertEquals("dad", song.getString("player2"))
        assertEquals("gf", song.getString("gfVersion"))
        val parsed = PsychChartParser.parse(file.json)
        val notes = parsed.sections.flatMap { it.notes }
        assertEquals(3, notes.size)
        assertTrue(notes.any { it.type == "Alt Animation" })
        assertTrue(notes.any { it.type == "Hurt Note" })
        assertTrue(parsed.sections.first().mustHit)
        assertTrue(parsed.events.any { it.name == "Add Camera Zoom" })
        assertTrue(result.customEvents.containsKey("Camera Flash"))
    }

    @Test
    fun psychCharacterOffsetsAreScaled() {
        val json = """{"image":"characters/spooky","scale":2,"position":[10,20],"camera_position":[5,-10],"flip_x":false,"no_antialiasing":true,"healthicon":"spooky","healthbar_colors":[213,126,0],"sing_duration":4,
            "animations":[{"anim":"idle","name":"spooky dance idle","fps":24,"loop":false,"indices":[0,1,2,3],"offsets":[0,0]},{"anim":"singUP","name":"spooky UP NOTE","fps":24,"loop":false,"indices":[],"offsets":[-20,26]}]}"""
        val result = PsychToCodenameCharacter.convert("spooky", PsychCharacterParser.parse(json), CharacterSlot.OPPONENT)
        val node = XmlNode.parse(result.mainText)
        assertEquals("character", node.name)
        assertEquals("spooky", node.attr("sprite") ?: "spooky")
        assertEquals("#D57E00", node.attr("color"))
        assertEquals("false", node.attr("antialiasing"))
        assertEquals(4.4, node.double("holdTime")!!, 0.001)
        val up = node.children.single { it.attr("name") == "singUP" }
        assertEquals(-10.0, up.double("x")!!, 0.001)
        assertEquals(13.0, up.double("y")!!, 0.001)
        assertEquals("0..3", node.children.single { it.attr("name") == "idle" }.attr("indices"))
    }

    @Test
    fun luaBecomesCodenameScript() {
        val lua = """
function onCreate()
    makeLuaSprite('sky', 'stages/sky', -200, -100)
    setScrollFactor('sky', 0.5, 0.5)
    addLuaSprite('sky', false)
end

function onBeatHit()
    if mustHitSection then
        setProperty('sky.alpha', 0.5)
    end
end

function goodNoteHit(id, dir, noteType, sustain)
    if noteType == 'Hurt Note' then
        return Function_Stop
    end
end
"""
        val result = LuaToCodename(CodenameScope.SONG).convert(lua)
        val source = result.source
        assertTrue(source.contains("public var sky"))
        assertTrue(source.contains("function create()"))
        assertTrue(source.contains("function beatHit(curBeat:Int)"))
        assertTrue(source.contains("psychFocus() == 1"))
        assertTrue(source.contains("sky.alpha = 0.5"))
        assertTrue(source.contains("function onPostNoteHit(event)"))
        assertFalse(source.contains("Function_Stop"))
        assertFalse(source.contains("PSYCHLUA"))
        assertFalse(Regex("(?m)^\\s*null;\\s*$").containsMatchIn(source))
    }

    @Test
    fun psychHScriptBecomesCodenameScript() {
        val hx = """
import backend.ClientPrefs;

function onCreatePost()
{
    game.boyfriend.alpha = 0.5;
    if (PlayState.SONG.notes[curSection].mustHitSection) debugPrint('player');
    setVar('ready', true);
}

function goodNoteHitPre(note:Note)
{
    if (note.noteType == 'Hurt Note') return Function_Stop;
    return Function_Continue;
}
"""
        val source = PsychHxToCodename(CodenameScope.SONG).convert(hx).source
        assertTrue(source.contains("function postCreate()"))
        assertTrue(source.contains("boyfriend.alpha = 0.5"))
        assertTrue(source.contains("psychFocus() == 1"))
        assertTrue(source.contains("function onPlayerHit(event)"))
        assertTrue(source.contains("return true;"))
        assertFalse(source.contains("import backend"))
        assertFalse(source.contains("Function_Stop"))
    }

    @Test
    fun codenameScriptBecomesPsychScript() {
        val hx = """
function beatHit(curBeat:Int) {
    if (curBeat % 4 == 0) camGame.zoom += 0.03;
}

function onPlayerHit(event) {
    if (event.noteType == "Hurt Note") event.cancel();
}
"""
        val result = CodenameToPsychScript(CodenameScriptTarget(CodenameScope.SONG, "test")).convert(hx)
        assertTrue(result.source.contains("function onBeatHit()"))
        assertTrue(result.source.contains("function goodNoteHitPre("))
        assertTrue(result.source.contains("game.camGame.zoom") || result.source.contains("camGame.zoom"))
    }

    @Test
    fun builtinEventScriptsAreCodenameHScript() {
        listOf("Hey!", "Set GF Speed", "Screen Shake", "Change Character", "Set Property", "Play Sound", "Alt Idle Animation").forEach { name ->
            val script = PsychToCodenameEvents.script(name)!!
            assertTrue(script.contains("function onEvent(e)"))
            assertTrue(script.contains("e.event.name != "))
            val definition = JSONObject(PsychToCodenameEvents.definition(name))
            assertEquals(2, definition.getJSONArray("params").length())
        }
        assertTrue(PsychToCodenameEvents.noteTypeScript("Hurt Note")!!.contains("event.note.avoid = true;"))
        assertTrue(PsychToCodenameEvents.noteTypeScript("GF Sing")!!.contains("event.characters = [gf];"))
    }

    @Test
    fun wholePsychModBecomesCodenameMod() {
        val root = Files.createTempDirectory("p2c").toFile()
        fun put(path: String, text: String) = File(root, "PsychMod/$path").apply { parentFile.mkdirs() }.writeText(text)
        put("pack.json", """{"name":"Psych Mod","description":"Converted"}""")
        put("data/test-song/test-song-hard.json", psychChart)
        put("data/test-song/test-song.json", psychChart)
        put("data/test-song/script.lua", "function onCreate() makeLuaSprite('fog', 'fog', 0, 0) addLuaSprite('fog', true) end")
        put("songs/test-song/Inst.ogg", "inst")
        put("songs/test-song/Voices-Player.ogg", "p")
        put("songs/test-song/Voices-Opponent.ogg", "o")
        put("characters/spooky.json", """{"image":"characters/spooky","scale":1,"position":[0,0],"camera_position":[0,0],"healthicon":"spooky","healthbar_colors":[1,2,3],"sing_duration":4,"animations":[{"anim":"idle","name":"idle","fps":24,"loop":false,"indices":[],"offsets":[0,0]}]}""")
        put("stages/stage.json", """{"directory":"","defaultZoom":0.9,"isPixelStage":false,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100],"hide_girlfriend":false,"camera_boyfriend":[0,0],"camera_opponent":[0,0],"camera_girlfriend":[0,0],"camera_speed":1}""")
        put("stages/stage.lua", "function onCreate() makeLuaSprite('back', 'stageback', -600, -200) setScrollFactor('back', 0.9, 0.9) addLuaSprite('back', false) end\nfunction onBeatHit() setProperty('back.alpha', 0.9) end")
        put("weeks/week1.json", """{"songs":[["Test Song","dad",[146,113,253]]],"weekCharacters":["dad","bf","gf"],"weekBackground":"stage","storyName":"Week One","weekName":"Week 1","startUnlocked":true,"hiddenUntilUnlocked":false,"hideStoryMode":false,"hideFreeplay":false,"difficulties":""}""")
        put("custom_events/My Event.lua", "function onEvent(n, v1, v2) if n == 'My Event' then cameraFlash('game', 'FFFFFF', 0.5) end end")
        put("custom_events/My Event.txt", "Flashes.\nValue 1: Color\nValue 2: Time")
        put("custom_notetypes/Spark.lua", "function goodNoteHit(i, d, t, s) if t == 'Spark' then setProperty('health', 2) end end")
        put("scripts/global.hx", "function onUpdate(elapsed:Float) { game.camHUD.alpha = 1; }")
        put("images/fog.png", "png")
        val files = DirectoryModFiles(root)
        val scan = ModScanner.scan(files)
        val out = Files.createTempDirectory("p2cout").toFile()
        val result = PsychToCodenameMod(files, scan, DirectoryModWriter(out)).convert(ModInfo("Psych Mod", "Converted", "Me"))
        listOf(
            "songs/test-song/charts/hard.json",
            "songs/test-song/charts/normal.json",
            "songs/test-song/meta.json",
            "songs/test-song/song/Inst.ogg",
            "songs/test-song/song/Voices-Player.ogg",
            "songs/test-song/scripts/script.hx",
            "data/characters/spooky.xml",
            "data/stages/stage.xml",
            "data/stages/stage.hx",
            "data/weeks/weeks/week1.xml",
            "data/weeks/weeks.txt",
            "data/events/Hey!.hx",
            "data/events/Hey!.json",
            "data/events/My Event.hx",
            "data/events/My Event.json",
            "data/notes/Hurt Note.hx",
            "data/notes/Hey!.hx",
            "data/config/freeplaySonglist.txt",
            "data/config/modpack.ini",
            "songs/global.hx",
            "images/fog.png"
        ).forEach { assertTrue("missing $it", File(out, it).exists()) }
        assertFalse(File(out, "songs/test-song/Inst.ogg").exists())
        val chart = JSONObject(File(out, "songs/test-song/charts/hard.json").readText())
        assertEquals("-Player", chart.getJSONArray("strumLines").getJSONObject(1).getString("vocalsSuffix"))
        val stage = File(out, "data/stages/stage.xml").readText()
        assertTrue(stage.contains("name=\"back\""))
        val stageScript = File(out, "data/stages/stage.hx").readText()
        assertTrue(stageScript.contains("back.alpha = 0.9"))
        assertFalse(stageScript.contains("psychMakeSprite"))
        val week = File(out, "data/weeks/weeks/week1.xml").readText()
        assertTrue(week.contains("displayName=\"Test Song\">test-song</song>"))
        assertTrue(File(out, "data/events/My Event.json").readText().contains("\"Color\""))
        assertTrue(File(out, "data/config/modpack.ini").readText().contains("NAME=\"Psych Mod\""))
        assertTrue(File(out, "data/notes/Spark.hx").exists())
        assertTrue(result.report.stats.any { it.label == "Songs" && it.value == "1" })
    }

    @Test
    fun wholeCodenameModBecomesPsychMod() {
        val root = Files.createTempDirectory("c2p").toFile()
        fun put(path: String, text: String) = File(root, "CneMod/$path").apply { parentFile.mkdirs() }.writeText(text)
        put("data/config/modpack.ini", "[Common]\nNAME=\"Cne Mod\"\nDESCRIPTION=\"Test\"\n")
        put("songs/Test/charts/hard.json", codenameChart)
        put("songs/Test/meta.json", """{"displayName":"Test","bpm":150,"needsVoices":true}""")
        put("songs/Test/song/Inst.ogg", "inst")
        put("songs/Test/song/Voices.ogg", "voices")
        put("songs/Test/scripts/zoom.hx", "function beatHit(curBeat:Int) if (curBeat % 2 == 0) camGame.zoom += 0.02;")
        put("data/characters/dad.xml", """<character x="0" y="0"><anim name="idle" anim="Dad idle"/></character>""")
        put("data/stages/mall-evil.xml", """<stage zoom="0.9"><sprite name="tree" sprite="stages/mall/tree" x="10" y="20"/><girlfriend/><dad/><boyfriend/></stage>""")
        put("data/weeks/weeks/week5.xml", """<week name="Week 5" chars="dad,bf,gf"><song>Test</song></week>""")
        val files = DirectoryModFiles(root)
        val scan = CodenameModScanner.scan(files)
        val info = CodenameModConverter.readModpack(files, scan)!!
        assertEquals("Cne Mod", info.title)
        val out = Files.createTempDirectory("c2pout").toFile()
        CodenameToPsychMod(files, scan, DirectoryModWriter(out)).convert(ModInfo(info.title, info.description, info.author))
        listOf(
            "data/test/test-hard.json",
            "songs/test/Inst.ogg",
            "songs/test/Voices.ogg",
            "data/test/zoom.hx",
            "characters/dad.json",
            "stages/mall-evil.json",
            "weeks/week5.json",
            "custom_events/Camera Flash.hx",
            "pack.json"
        ).forEach { assertTrue("missing $it", File(out, it).exists()) }
        assertTrue(File(out, "data/test/zoom.hx").readText().contains("function onBeatHit()"))
        assertEquals("Cne Mod", JSONObject(File(out, "pack.json").readText()).getString("name"))
    }
}
