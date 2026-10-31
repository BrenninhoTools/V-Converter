package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.codename.CodenameChartParser
import com.brenninhotools.vconverter.codename.CodenameCharacterParser
import com.brenninhotools.vconverter.codename.CodenameEvents
import com.brenninhotools.vconverter.codename.CodenameMeta
import com.brenninhotools.vconverter.codename.CodenameModConverter
import com.brenninhotools.vconverter.codename.CodenameModScanner
import com.brenninhotools.vconverter.codename.CodenameScope
import com.brenninhotools.vconverter.codename.CodenameScriptContext
import com.brenninhotools.vconverter.codename.CodenameScriptConverter
import com.brenninhotools.vconverter.codename.CodenameScriptTarget
import com.brenninhotools.vconverter.codename.CodenameSong
import com.brenninhotools.vconverter.codename.CodenameStageParser
import com.brenninhotools.vconverter.codename.CodenameToVSliceCharacter
import com.brenninhotools.vconverter.codename.CodenameToVSliceChart
import com.brenninhotools.vconverter.codename.CodenameToVSliceLevel
import com.brenninhotools.vconverter.codename.CodenameToVSliceStage
import com.brenninhotools.vconverter.codename.CodenameWeekParser
import com.brenninhotools.vconverter.codename.ImageInfo
import com.brenninhotools.vconverter.codename.XmlNode
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CodenameConversionTest {
    private val chart = """{"codenameChart":true,"stage":"limo","scrollSpeed":2.2,"noteTypes":["Alt Anim Note","Mine Note"],
        "strumLines":[
          {"characters":["dad"],"type":0,"position":"dad","notes":[{"time":0,"id":0,"type":1,"sLen":0},{"time":500,"id":3,"type":0,"sLen":250}]},
          {"characters":["bf"],"type":1,"position":"boyfriend","notes":[{"time":1000,"id":2,"type":2,"sLen":0},{"time":1000,"id":2,"type":0,"sLen":0}]},
          {"characters":["gf"],"type":2,"position":"girlfriend","visible":false,"notes":[{"time":200,"id":1,"type":0,"sLen":0}]}
        ],
        "events":[
          {"name":"Camera Movement","time":0,"params":[1]},
          {"name":"Camera Movement","time":2000,"params":[0,true,8,"circ","InOut",10,-5]},
          {"name":"BPM Change","time":4800,"params":[150]},
          {"name":"Camera Flash","time":6000,"params":[false,"#FF0000",4,"camHUD"]},
          {"name":"Scroll Speed Change","time":7000,"params":[false,3,4,"linear","In",false]},
          {"name":"Play Animation","time":8000,"params":[2,"cheer",true,"NONE"]}
        ]}"""

    @Test
    fun xmlParserHandlesCodenameFiles() {
        val node = XmlNode.parse("<!DOCTYPE codename-engine-stage>\n<stage zoom=\"0.9\"><!-- comment !--><sprite name=\"a\" x=\"1\"/><high-memory><sprite name=\"b\"></sprite></high-memory><song>Bopeebo &amp; Co</song></stage>")
        assertEquals("stage", node.name)
        assertEquals(0.9, node.double("zoom")!!, 0.0001)
        assertEquals(listOf("sprite", "high-memory", "song"), node.children.map { it.name })
        assertEquals("Bopeebo & Co", node.children.last().text)
        assertEquals(listOf(0, 1, 2, 3, 7, 6, 5), XmlNode.numberRange("0..3, 7..5"))
    }

    @Test
    fun chartBecomesVSliceSong() {
        val data = CodenameChartParser.parseChart(chart)
        val meta = CodenameMeta.default("test").copy(bpm = 120.0, displayName = "Test Song")
        val result = CodenameToVSliceChart.convert(CodenameSong("test", meta, mapOf("Hard" to data)))
        val notes = result.chart.notes.getValue("hard")
        assertEquals(listOf(4, 7, 2), notes.map { it.direction })
        assertEquals("alt", notes[0].kind)
        assertEquals("mine-note", notes[2].kind)
        assertEquals(250.0, notes[1].length, 0.0)
        assertEquals("bf", result.metadata.characters.player)
        assertEquals("dad", result.metadata.characters.opponent)
        assertEquals("gf", result.metadata.characters.girlfriend)
        assertEquals("limoRide", result.metadata.stage)
        assertEquals(2, result.metadata.timeChanges.size)
        assertEquals(150.0, result.metadata.timeChanges[1].bpm, 0.0)
        assertEquals(9.6, result.metadata.timeChanges[1].beat, 0.0001)
        val focus = result.chart.events.filter { it.kind == "FocusCamera" }
        assertEquals(0, focus[0].value["char"])
        assertEquals(1, focus[1].value["char"])
        assertEquals("circ", focus[1].value["ease"])
        assertEquals("InOut", focus[1].value["easeDir"])
        assertEquals(8.0, focus[1].value["duration"])
        val flash = result.chart.events.single { it.kind == "CameraFlash" }
        assertEquals("#FF0000", flash.value["color"])
        assertEquals("camHUD", flash.value["camera"])
        val scroll = result.chart.events.single { it.kind == "ScrollSpeed" }
        assertEquals("INSTANT", scroll.value["ease"])
        assertEquals(true, scroll.value["absolute"])
        assertEquals("gf", result.chart.events.single { it.kind == "PlayAnimation" }.value["target"])
        assertEquals(mapOf("Camera Flash" to 1), result.keptEvents.toMap())
        assertTrue(result.report.warnings.any { it.contains("extra (girlfriend/additional) strumlines") })
        assertTrue(result.report.sections.any { s -> s.title == "Notes" && s.lines.any { it.contains("1 stacked duplicates") } })
    }

    @Test
    fun characterOffsetsFollowScale() {
        val xml = """<!DOCTYPE codename-engine-character>
<character x="150" y="360" sprite="senpai" scale="6" antialiasing="false" camx="100" camy="87" icon="senpai" color="#FFAA6F" interval="2">
  <anim name="danceLeft" anim="Senpai Idle instance" indices="0..15"/>
  <anim name="danceRight" anim="Senpai Idle instance" indices="16..31"/>
  <anim name="singUP" anim="SENPAI UP NOTE" x="2" y="6"/>
</character>"""
        val character = CodenameCharacterParser.parse("senpai", xml)
        assertEquals(listOf("characters/senpai"), character.images)
        val result = CodenameToVSliceCharacter.convert(character, CharacterSlot.OPPONENT, emptyMap())
        val json = JSONObject(result.json)
        assertEquals(2, json.getInt("danceEvery"))
        assertTrue(json.getBoolean("isPixel"))
        assertEquals("senpai", json.getJSONObject("healthIcon").getString("id"))
        val anims = json.getJSONArray("animations")
        val dance = anims.getJSONObject(0).getJSONArray("offsets")
        val up = anims.getJSONObject(2).getJSONArray("offsets")
        assertEquals(2.0, up.getDouble(0) - dance.getDouble(0), 0.001)
        assertEquals(6.0, up.getDouble(1) - dance.getDouble(1), 0.001)
        assertEquals((0..15).toList(), (0 until anims.getJSONObject(0).getJSONArray("frameIndices").length()).map { anims.getJSONObject(0).getJSONArray("frameIndices").getInt(it) })
    }

    @Test
    fun playerCharacterOnOpponentSideIsMirrored() {
        val xml = """<character isPlayer="true" flipX="true"><anim name="idle" anim="idle"/><anim name="singLEFT" anim="left" x="10"/><anim name="singRIGHT" anim="right" x="-4"/></character>"""
        val result = CodenameToVSliceCharacter.convert(CodenameCharacterParser.parse("tankman", xml), CharacterSlot.OPPONENT, emptyMap())
        val anims = JSONObject(result.json).getJSONArray("animations")
        val byName = (0 until anims.length()).associate { anims.getJSONObject(it).getString("name") to anims.getJSONObject(it).getString("prefix") }
        assertEquals("right", byName["singLEFT"])
        assertEquals("left", byName["singRIGHT"])
        assertTrue(result.report.warnings.any { it.contains("mirrored") })
    }

    @Test
    fun stageSpritesBecomeProps() {
        val xml = """<!DOCTYPE codename-engine-stage>
<stage zoom="0.9" name="limo" folder="stages/limo/">
  <sprite name="sky" x="-120" y="-50" sprite="limoSunset" scroll="0.1"/>
  <sprite name="big" x="0" y="0" sprite="bigOne" scale="0.5"/>
  <high-memory>
    <sprite name="dancer" x="130" y="80" sprite="limoDancer" scroll="0.4" type="onbeat" beatInterval="1">
      <anim name="danceLeft" anim="bg dancer" indices="0..14"/>
      <anim name="danceRight" anim="bg dancer" indices="15..29"/>
    </sprite>
  </high-memory>
  <low-memory><sprite name="cheap" sprite="cheap"/></low-memory>
  <girlfriend/>
  <box name="floor" x="0" y="700" width="2000" height="100" color="#00FF00"/>
  <boyfriend x="1030" y="-120" camxoffset="-300"/>
  <dad/>
  <use-extension script="pixel"/>
</stage>"""
        val stage = CodenameStageParser.parse("limo", xml) { path -> if (path == "stages/limo/bigOne") ImageInfo(400.0, 200.0, false) else null }
        assertEquals(listOf("data/scripts/pixel"), stage.extensions)
        assertEquals(listOf("sky", "big", "dancer", "floor"), stage.spriteNames)
        val json = JSONObject(CodenameToVSliceStage.convert(stage).json)
        val props = json.getJSONArray("props")
        val byName = (0 until props.length()).associate { props.getJSONObject(it).getString("name") to props.getJSONObject(it) }
        assertEquals(setOf("sky", "big", "dancer", "floor"), byName.keys)
        assertEquals("stages/limo/limoSunset", byName.getValue("sky").getString("assetPath"))
        assertEquals(100.0, byName.getValue("big").getJSONArray("position").getDouble(0), 0.001)
        assertEquals(50.0, byName.getValue("big").getJSONArray("position").getDouble(1), 0.001)
        assertEquals(1, byName.getValue("dancer").getInt("danceEvery"))
        assertEquals("#00FF00", byName.getValue("floor").getString("assetPath"))
        val characters = json.getJSONObject("characters")
        assertTrue(characters.getJSONObject("gf").getInt("zIndex") < byName.getValue("floor").getInt("zIndex"))
        assertTrue(characters.getJSONObject("bf").getInt("zIndex") > byName.getValue("floor").getInt("zIndex"))
        assertEquals(0.9, json.getDouble("cameraZoom"), 0.0001)
    }

    @Test
    fun weekBecomesLevel() {
        val week = CodenameWeekParser.parseWeek("week1", """<week name="DADDY DEAREST" chars="dad,bf,gf" sprite="week1" bgColor="#FFAA00"><song>Bopeebo</song><song hide="true">Dad Battle</song></week>""")
        val menu = CodenameWeekParser.parseMenuCharacter("bf", """<char scale="0.8" x="20" y="-20"><anim name="idle" anim="BF idle"/><anim name="confirm" anim="BF HEY!!" type="none"/></char>""")
        val result = CodenameToVSliceLevel.convert(week, mapOf("bf" to menu))
        val json = JSONObject(result.json)
        assertEquals(listOf("bopeebo", "dad-battle"), result.songIds)
        assertEquals("menus/storymenu/weeks/week1", json.getString("titleAsset"))
        assertEquals("#FFAA00", json.getString("background"))
        assertEquals(3, json.getJSONArray("props").length())
        val bf = json.getJSONArray("props").getJSONObject(1)
        assertEquals("menus/storymenu/characters/bf", bf.getString("assetPath"))
        assertEquals(1280.0 * 0.25 * 2 - 150 - 1280.0 * 0.25 - 20, bf.getJSONArray("offsets").getDouble(0), 0.001)
        assertEquals(90.0, bf.getJSONArray("offsets").getDouble(1), 0.001)
    }

    @Test
    fun stageScriptUsesVSliceApi() {
        val source = """
import funkin.game.PlayState;
import funkin.backend.utils.CoolUtil;

var curColor:Int = 0;
var colors = [0xFF31A2FD, 0xFF31FD8C];

function create() {
	light.color = colors[curColor];
	trace('beat is ${'$'}curBeat and ${'$'}{curStep + 1}');
}

function beatHit(curBeat:Int) {
	if (curBeat % 4 == 0) light.color = colors[curColor];
	gf.playAnim('hairBlow');
	defaultCamZoom = 1.1;
}

function onNoteHit(event) if (event.noteType == "Alt Anim Note") event.animSuffix = "-alt";

function update(elapsed:Float)
	light.alpha = 1 - ((curBeatFloat / 4) % 1) * Conductor.crochet;
"""
        val result = CodenameScriptConverter("TestPhillyStage", "test-philly-stage", CodenameScriptTarget(CodenameScope.STAGE, "philly"), CodenameScriptContext(stageSprites = setOf("light"))).convert(source)
        val code = result.source
        assertTrue(code.contains("class TestPhillyStage extends Module"))
        assertTrue(code.contains("PlayState.instance.currentStage.getNamedProp(\"light\").color = colors[curColor];"))
        assertTrue(code.contains("function beatHit(curBeat:Int)"))
        assertTrue(code.contains("if (curBeat % 4 == 0)"))
        assertTrue(code.contains("PlayState.instance.currentStage.getGirlfriend().playAnimation('hairBlow');"))
        assertTrue(code.contains("PlayState.instance.currentCameraZoom = 1.1;"))
        assertTrue(code.contains("trace('beat is ${'$'}{Conductor.instance.currentBeat} and ${'$'}{Conductor.instance.currentStep + 1}');"))
        assertTrue(code.contains("Conductor.instance.currentBeatTime / 4"))
        assertTrue(code.contains("Conductor.instance.beatLengthMs"))
        assertTrue(code.contains("return game.currentStageId == \"philly\";"))
        assertTrue(code.contains("override public function onBeatHit(event:SongTimeScriptEvent):Void"))
        assertTrue(code.contains("beatHit(event.beat);"))
        assertTrue(code.contains("function noteEvent(event:Dynamic, player:Bool, ?miss:Bool, ?passive:Bool):Dynamic"))
        assertTrue(code.contains("applyNoteEvent(event, e, false);"))
        assertTrue(code.contains("function update(elapsed:Float)"))
        assertFalse(code.contains("funkin.game."))
        assertFalse(code.contains("CoolUtil"))
        assertTrue(code.contains("import funkin.Conductor;"))
        assertEquals(listOf("create", "update", "beatHit", "onNoteHit"), result.callbacks)
    }

    @Test
    fun eventScriptsGetSchemas() {
        val def = CodenameEvents.parseDefinition("Flash Camera", """{"params":[{"name":"Color (Hex)","type":"ColorWheel","defaultValue":"#FFFFFF"},{"name":"Time","type":"Float(0, 10, 0.1)","defaultValue":1},{"name":"Camera","type":"DropDown('camGame', 'camHUD')","defaultValue":"camHUD"}]}""")
        assertEquals(listOf("color", "time", "camera"), def.params.map { it.key })
        assertEquals(listOf("camGame", "camHUD"), def.params[2].options)
        val file = CodenameEvents.songEventFile(def, emptyList())
        assertEquals("scripts/events/FlashCameraEvent.hxc", file.path)
        assertTrue(file.content.contains("super(\"FlashCamera\");"))
        assertTrue(file.content.contains("{name: \"time\", title: \"Time\", type: \"float\", defaultValue: 1.0}"))
        assertTrue(file.content.contains("keys: [\"camGame\" => \"camGame\", \"camHUD\" => \"camHUD\"]"))
        val flash = CodenameEvents.songEventFile(CodenameEvents.builtins.getValue("Camera Flash"), emptyList())
        assertTrue(flash.content.contains("cam.flash(color, time, null, true);"))
        assertTrue(flash.content.contains("import flixel.util.FlxColor;"))
    }

    @Test
    fun wholeModConverts() {
        val root = Files.createTempDirectory("cne").toFile()
        fun put(path: String, text: String) = File(root, "MyMod/$path").apply { parentFile.mkdirs() }.writeText(text)
        put("data/config/modpack.ini", "[Common]\nMOD_NAME=Cool Mod\nMOD_DESCRIPTION=A test\nMOD_AUTHOR=Someone\n")
        put("songs/Dad Battle/charts/hard.json", chart)
        put("songs/Dad Battle/meta.json", """{"displayName":"Dad Battle","bpm":120,"needsVoices":true}""")
        put("songs/Dad Battle/song/Inst.ogg", "inst")
        put("songs/Dad Battle/song/Voices.ogg", "voices")
        put("songs/Dad Battle/scripts/zoom.hx", "function beatHit(curBeat) if (curBeat % 2 == 0) camGame.zoom += 0.02;")
        put("data/characters/dad.xml", """<character x="0" y="0"><anim name="idle" anim="Dad idle"/></character>""")
        put("data/stages/limo.xml", """<stage zoom="0.9" folder="stages/limo/"><sprite name="sky" sprite="sky"/><girlfriend/><dad/><boyfriend/></stage>""")
        put("data/stages/limo.hx", "function create() sky.alpha = 0.5;")
        put("data/notes/Mine Note.hx", "function onPlayerHit(e) if (e.noteType == \"Mine Note\") health -= 0.2;")
        put("data/weeks/weeks/week1.xml", """<week name="Week 1" chars="dad,bf,gf"><song>Dad Battle</song></week>""")
        put("images/stages/limo/sky.png", "png")
        val files = DirectoryModFiles(root)
        assertTrue(CodenameModScanner.looksLikeCodename(files.paths))
        val scan = CodenameModScanner.scan(files)
        assertEquals("MyMod/", scan.root)
        assertEquals(listOf("dad-battle"), scan.songs.map { it.id })
        val info = CodenameModConverter.readModpack(files, scan)!!
        assertEquals("Cool Mod", info.title)
        val out = Files.createTempDirectory("cneout").toFile()
        val result = CodenameModConverter(files, scan, DirectoryModWriter(out)).convert(ModInfo(info.title, info.description, info.author))
        listOf(
            "data/songs/dad-battle/dad-battle-metadata.json",
            "data/songs/dad-battle/dad-battle-chart.json",
            "songs/dad-battle/Inst.ogg",
            "songs/dad-battle/Voices.ogg",
            "data/characters/dad.json",
            "data/stages/limo.json",
            "data/levels/week1.json",
            "images/stages/limo/sky.png",
            "scripts/events/CameraFlashEvent.hxc",
            "scripts/notekinds/AltAnimNoteKind.hxc",
            "scripts/songs/DadBattleSong.hxc",
            "scripts/stages/LimoStage.hxc",
            "scripts/notekinds/MineNoteNoteKind.hxc",
            "_polymod_meta.json"
        ).forEach { assertTrue("missing $it", File(out, it).exists()) }
        val songScript = File(out, "scripts/songs/DadBattleSong.hxc").readText()
        assertTrue(songScript.contains("class DadBattleSong extends Song"))
        assertTrue(songScript.contains("super(\"dad-battle\");"))
        val stageScript = File(out, "scripts/stages/LimoStage.hxc").readText()
        assertTrue(stageScript.contains("class LimoStage extends Stage"))
        assertTrue(stageScript.contains("super(\"limo\");"))
        assertTrue(stageScript.contains("getNamedProp(\"sky\").alpha = 0.5;"))
        assertTrue(!stageScript.contains("onStateChangeBegin"))
        val noteScript = File(out, "scripts/notekinds/MineNoteNoteKind.hxc").readText()
        assertTrue(noteScript.contains("class MineNoteNoteKind extends NoteKind"))
        assertTrue(noteScript.contains("super(\"mine-note\", \"Mine Note\");"))
        assertTrue(!noteScript.contains("onCountdownStart"))
        assertTrue(noteScript.contains("n.kind == \"mine-note\""))
        assertTrue(noteScript.contains("case \"mine-note\": return \"Mine Note\";"))
        assertTrue(noteScript.contains("PlayState.instance.health -= 0.2;"))
        assertEquals("Cool Mod", JSONObject(File(out, "_polymod_meta.json").readText()).getString("title"))
        assertTrue(result.report.stats.any { it.label == "Scripts" && it.value == "3" })
    }
}
