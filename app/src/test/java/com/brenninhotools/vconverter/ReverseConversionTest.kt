package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.reverse.HxcKind
import com.brenninhotools.vconverter.reverse.HxcToPsych
import com.brenninhotools.vconverter.reverse.VSliceModConverter
import com.brenninhotools.vconverter.reverse.VSliceModScanner
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.reverse.VSliceSongSource
import com.brenninhotools.vconverter.reverse.VSliceToPsychCharacter
import com.brenninhotools.vconverter.reverse.VSliceToPsychChart
import com.brenninhotools.vconverter.reverse.VSliceToPsychStage
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReverseConversionTest {
    private val xml = """
        <TextureAtlas imagePath="dude.png">
          <SubTexture name="dude idle0000" x="0" y="0" width="400" height="380" frameX="-2" frameY="-4" frameWidth="410" frameHeight="400"/>
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
         "camera_boyfriend":[3,4],"camera_opponent":[5,5],"camera_girlfriend":[0,0],"camera_speed":1,
         "objects":[{"type":"sprite","name":"bg","image":"bg","x":-500,"y":-300,"scale":[1,1],"scroll":[0.9,0.9],"alpha":1,"angle":0,"color":"FFFFFF","filters":3,"antialiasing":true},
                    {"type":"dad"},{"type":"gf"},{"type":"square","name":"fog","x":0,"y":0,"scale":[200,100],"scroll":[1,1],"alpha":0.5,"angle":0,"color":"112233","filters":3},{"type":"boyfriend"}]}
    """.trimIndent()

    private val chart = """{"song":{"song":"Dad Battle","bpm":120,"speed":2.2,"needsVoices":true,"player1":"bf","player2":"dude","gfVersion":"gf","stage":"stage","format":"psych_v1",
        "notes":[{"sectionBeats":4,"mustHitSection":false,"sectionNotes":[[0,4,0],[500,5,250,"Alt Animation"]]},
                 {"sectionBeats":4,"mustHitSection":true,"sectionNotes":[[2000,0,0],[2500,3,0,"Hurt Note"]]},
                 {"sectionBeats":4,"mustHitSection":true,"gfSection":true,"changeBPM":true,"bpm":150,"sectionNotes":[[4100,1,0]]}],
        "events":[[1000,[["Change Scroll Speed","1.5","1"],["Custom Thing","a","b"]]]]}}"""

    @Test
    fun characterRoundTripKeepsScreenPositionAndCamera() {
        val psych = PsychCharacterParser.parse(character)
        val atlases = mapOf(PsychToVSliceCharacter.atlasKey(psych.image) to SparrowAtlas.parse(xml))
        val forward = PsychToVSliceCharacter.convert(CharacterSource("dude", psych, CharacterSlot.OPPONENT, atlases))
        val back = VSliceToPsychCharacter.convert("dude", VSliceParser.character(forward.json), CharacterSlot.OPPONENT, atlases)
        val json = JSONObject(back.json)
        val pos = json.getJSONArray("position")
        val anims = json.getJSONArray("animations")
        psych.animations.forEachIndexed { i, anim ->
            val offsets = anims.getJSONObject(i).getJSONArray("offsets")
            assertEquals(anim.offsetX - psych.positionX, offsets.getDouble(0) - pos.getDouble(0), 0.01)
            assertEquals(anim.offsetY - psych.positionY, offsets.getDouble(1) - pos.getDouble(1), 0.01)
        }
        assertEquals(0.0, anims.getJSONObject(0).getJSONArray("offsets").getDouble(0), 0.001)
        val cam = json.getJSONArray("camera_position")
        assertEquals(psych.cameraX - (pos.getDouble(0) - psych.positionX), cam.getDouble(0), 0.01)
        assertEquals(psych.cameraY - (pos.getDouble(1) - psych.positionY), cam.getDouble(1), 0.01)
        assertEquals("characters/Dude", json.getString("image"))
        assertEquals(4.0, json.getDouble("sing_duration"), 0.001)
    }

    @Test
    fun stageRoundTripKeepsPositionsAndLayers() {
        val forward = PsychToVSliceStage.convert(StageSource("club", PsychStageParser.parse(stage)))
        val back = JSONObject(VSliceToPsychStage.convert("club", VSliceParser.stage(forward.json)).json)
        assertEquals(770.0, back.getJSONArray("boyfriend").getDouble(0), 0.001)
        assertEquals(130.0, back.getJSONArray("girlfriend").getDouble(1), 0.001)
        assertEquals(5.0, back.getJSONArray("camera_opponent").getDouble(0), 0.001)
        assertEquals(4.0, back.getJSONArray("camera_boyfriend").getDouble(1), 0.001)
        val types = (0 until back.getJSONArray("objects").length()).map { back.getJSONArray("objects").getJSONObject(it).getString("type") }
        assertEquals(listOf("sprite", "dadGroup", "gfGroup", "square", "boyfriendGroup"), types)
        val fog = back.getJSONArray("objects").getJSONObject(3)
        assertEquals("112233", fog.getString("color"))
        assertEquals(200.0, fog.getJSONArray("scale").getDouble(0), 0.001)
        val reparsed = PsychStageParser.parse(back.toString())
        assertEquals(0.85, reparsed.defaultZoom, 0.001)
    }

    @Test
    fun chartRoundTripKeepsNotesSectionsAndEvents() {
        val forward = PsychToVSliceChart.convert(listOf(ChartSource("hard", PsychChartParser.parse(chart))), ConversionOptions(songName = "Dad Battle"))
        val meta = VSliceParser.metadata(VSliceWriter.metadataJson(forward.metadata))
        val vchart = VSliceParser.chart(VSliceWriter.chartJson(forward.chart))
        val result = VSliceToPsychChart.convert("dad-battle", listOf(VSliceSongSource("", meta, vchart)))
        assertEquals("Dad Battle", result.songName)
        assertEquals(listOf("data/dad-battle/dad-battle-hard.json"), result.files.map { it.path })
        val back = PsychChartParser.parse(result.files.single().json)
        val original = PsychChartParser.parse(chart)
        val notes = back.sections.flatMap { it.notes }.sortedBy { it.time }
        val expected = original.sections.flatMap { it.notes }.sortedBy { it.time }
        assertEquals(expected.map { it.time to it.lane }, notes.map { it.time to it.lane })
        assertEquals(expected.map { it.type }, notes.map { it.type })
        assertEquals(listOf(false, true), back.sections.take(2).map { it.mustHit })
        assertTrue(back.sections[2].gfSection)
        assertTrue(back.sections.any { it.changeBpm && it.bpm == 150.0 })
        assertEquals(2.2, back.speed, 0.001)
        val names = back.events.map { it.name }
        assertTrue("Change Scroll Speed" in names)
        assertTrue("CustomThing" in names)
        assertEquals("a", back.events.first { it.name == "CustomThing" }.value1)
        assertEquals("stage", back.stage)
    }

    @Test
    fun hxcBecomesPsychHScript() {
        val source = """
            import funkin.play.PlayState;
            import funkin.modding.module.Module;
            import funkin.Conductor;

            class DudeSong extends funkin.play.song.Song
            {
              var bumps:Int = 0;

              public function new()
              {
                super("dad-battle");
              }

              override public function onBeatHit(event:SongTimeScriptEvent):Void
              {
                super.onBeatHit(event);
                if (event.beat % 4 == 0) PlayState.instance.currentCameraZoom += 0.02;
                bumps++;
              }

              override public function onNoteHit(event:HitNoteScriptEvent):Void
              {
                super.onNoteHit(event);
                if (event.note.noteData.getMustHitNote()) PlayState.instance.currentStage.getBoyfriend().playAnimation("hey", true);
              }

              override public function onSongEnd(event:ScriptEvent):Void
              {
                if (bumps < 2) event.cancel();
              }
            }
        """.trimIndent()
        val result = HxcToPsych.convert("DudeSong.hxc", source)
        assertEquals(HxcKind.SONG, result.kind)
        val file = result.files.single()
        assertEquals("data/dad-battle/DudeSong.hx", file.path)
        val text = file.content
        assertTrue(text.contains("var bumps:Int = 0;"))
        assertTrue(text.contains("function onBeatHit()"))
        assertTrue(text.contains("if (curBeat % 4 == 0) game.defaultCamZoom += 0.02;"))
        assertTrue(text.contains("function goodNoteHit(note:Note)"))
        assertTrue(text.contains("note.mustPress"))
        assertTrue(text.contains("boyfriend.playAnimation"))
        assertTrue(text.contains("return Function_Stop;"))
        assertTrue(text.contains("import states.PlayState;"))
        assertFalse(text.contains("super."))
        assertFalse(text.contains("funkin."))
    }

    @Test
    fun vSliceOnlyClassesAreLeftOut() {
        val source = """
            import funkin.play.PlayState;
            import funkin.graphics.shaders.RuntimeRainShader;
            import funkin.play.cutscene.VideoCutscene;
            import funkin.play.stage.Stage;
            class RainStage extends Stage
            {
              var rain:RuntimeRainShader = new RuntimeRainShader();
              var prop:StageProp;
              public function new() { super("rain"); }
              override function onCreate(event:ScriptEvent):Void
              {
                rain.intensity = 0.5;
                if (rain != null) rain.update(1);
                else trace("none");
                FlxTweenUtil.pauseTweensOf(prop);
                var keep:Float = 2;
                keep = 3;
                VideoCutscene.play(Paths.videos("intro.mp4"));
              }
            }
        """.trimIndent()
        val text = HxcToPsych.convert("RainStage.hxc", source).files.single().content
        listOf("RuntimeRainShader", "StageProp", "FlxTweenUtil", "VideoCutscene", "rain.").forEach { assertFalse("$it left in", text.contains(it)) }
        assertTrue(text.contains("var prop:Dynamic;"))
        assertTrue(text.contains("var keep:Float = 2;"))
        assertTrue(text.contains("keep = 3;"))
        assertTrue(text.contains("PlayState.instance.startVideo(\"intro\");"))
        assertEquals(text.count { it == '{' }, text.count { it == '}' })
    }

    @Test
    fun songEventBecomesCustomEvent() {
        val source = """
            import funkin.play.event.SongEvent;
            import funkin.data.event.SongEventSchema;
            class FlashEvent extends SongEvent
            {
              public function new() { super("Flash"); }
              override public function handleEvent(data:SongEventData):Void
              {
                var alpha:Float = data.value.alpha;
                PlayState.instance.camGame.flash(0xFFFFFFFF, alpha);
              }
              override public function getTitle():String { return "Flash Screen"; }
              override public function getEventSchema():SongEventSchema
              {
                return new SongEventSchema([{name: "alpha", title: "Alpha", type: "float", defaultValue: 1}]);
              }
            }
        """.trimIndent()
        val result = HxcToPsych.convert("FlashEvent.hxc", source)
        assertEquals(listOf("custom_events/Flash.hx", "custom_events/Flash.txt"), result.files.map { it.path })
        val text = result.files.first().content
        assertTrue(text.contains("function onEvent(eventName:String, value1:String, value2:String, strumTime:Float)"))
        assertTrue(text.contains("if (eventName != \"Flash\") return;"))
        assertTrue(text.contains("var alpha:Float = Std.parseFloat(value1);"))
        assertTrue(result.files[1].content.startsWith("Flash Screen"))
    }

    @Test
    fun wholeModRoundTrip() {
        val psych = Files.createTempDirectory("psych").toFile()
        fun put(path: String, text: String) = File(psych, path).apply { parentFile.mkdirs(); writeText(text) }
        put("pack.json", """{"name":"Round Trip","description":"Test"}""")
        put("data/dad-battle/dad-battle-hard.json", chart)
        put("characters/dude.json", character)
        put("images/characters/Dude.xml", xml)
        put("stages/stage.json", stage)
        put("songs/dad-battle/Inst.ogg", "x")
        put("songs/dad-battle/Voices-Player.ogg", "x")
        put("songs/dad-battle/Voices-Opponent.ogg", "x")
        put("weeks/weekdude.json", """{"songs":[["Dad Battle","dude",[255,0,0]]],"weekCharacters":["dad","bf","gf"],"storyName":"Club Night","weekName":"Week Dude","difficulties":"Hard"}""")
        val vslice = Files.createTempDirectory("vslice").toFile()
        val files = DirectoryModFiles(psych)
        val scan = ModScanner.scan(files)
        ModConverter(files, scan, DirectoryModWriter(vslice)).convert(ModInfo("Round Trip", "Test", "Me"))

        val vfiles = DirectoryModFiles(vslice)
        assertTrue(VSliceModScanner.looksLikeVSlice(vfiles))
        val vscan = VSliceModScanner.scan(vfiles)
        assertEquals(1, vscan.songs.size)
        val info = VSliceModScanner.readInfo(vfiles, vscan)!!
        assertEquals("Round Trip", info.title)
        val back = Files.createTempDirectory("back").toFile()
        val result = VSliceModConverter(vfiles, vscan, DirectoryModWriter(back)).convert(info)
        assertTrue(result.filesWritten > 5)
        assertTrue(File(back, "data/dad-battle/dad-battle-hard.json").exists())
        assertTrue(File(back, "characters/dude.json").exists())
        assertTrue(File(back, "stages/stage.json").exists())
        assertTrue(File(back, "weeks/weekdude.json").exists())
        assertTrue(File(back, "songs/dad-battle/Inst.ogg").exists())
        assertTrue(File(back, "songs/dad-battle/Voices-Player.ogg").exists())
        assertTrue(File(back, "songs/dad-battle/Voices-Opponent.ogg").exists())
        assertEquals("Round Trip", JSONObject(File(back, "pack.json").readText()).getString("name"))
        val week = JSONObject(File(back, "weeks/weekdude.json").readText())
        assertEquals("Dad Battle", week.getJSONArray("songs").getJSONArray(0).getString(0))
        assertEquals("Hard", week.getString("difficulties"))
        val roundChar = JSONObject(File(back, "characters/dude.json").readText())
        assertEquals("dude", roundChar.getString("healthicon"))
    }
}
