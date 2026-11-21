package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.chart.ChartSource
import com.vconvertercrew.vconverter.chart.ConversionOptions
import com.vconvertercrew.vconverter.chart.PsychToVSliceChart
import com.vconvertercrew.vconverter.chart.psych.PsychChartParser
import com.vconvertercrew.vconverter.chart.psych.PsychEvent
import com.vconvertercrew.vconverter.character.CharacterSource
import com.vconvertercrew.vconverter.character.PsychCharacterParser
import com.vconvertercrew.vconverter.character.PsychToVSliceCharacter
import com.vconvertercrew.vconverter.core.CharacterSlot
import com.vconvertercrew.vconverter.core.SparrowAtlas
import com.vconvertercrew.vconverter.script.NativeData
import com.vconvertercrew.vconverter.script.NativeScripts
import com.vconvertercrew.vconverter.stage.LuaStageExtractor
import com.vconvertercrew.vconverter.stage.PsychStageParser
import com.vconvertercrew.vconverter.stage.PsychToVSliceStage
import com.vconvertercrew.vconverter.stage.StageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDataTest {
    private fun <T> both(block: () -> T): Pair<T, T> {
        NativeData.enabled = false
        val kotlin = try { block() } finally { NativeData.enabled = true }
        val before = NativeData.conversions
        val native = block()
        assertEquals(before + 1, NativeData.conversions)
        return native to kotlin
    }

    private fun chart(bpm: Int, events: String, extra: String = "") = """
        {"song":{"song":"Test Song","bpm":$bpm,"speed":2.4,"offset":12.5,"needsVoices":true,"player1":"bf","player2":"dad","gfVersion":"gf","stage":"stage","arrowSkin":"",$extra
         "notes":[
          {"lengthInSteps":16,"mustHitSection":true,"gfSection":false,"altAnim":false,"sectionNotes":[[0,0,0],[150.5,1,300],[150.5,1,300],[600,5,0,"Hurt Note"],[750,2,0,"My Custom"]]},
          {"lengthInSteps":16,"mustHitSection":false,"gfSection":false,"altAnim":true,"changeBPM":true,"bpm":${bpm * 2},"sectionNotes":[[2400,0,0],[2500,4,120.25,"Alt Animation"]]},
          {"sectionBeats":3.5,"mustHitSection":false,"gfSection":true,"sectionNotes":[[3200,7,0],[3300,9,0]]},
          {"lengthInSteps":16,"mustHitSection":true,"sectionNotes":[[4000,3,0,"No Animation"]]}
         ],
         "events":$events}}
    """.trimIndent()

    private val events = """[
        [100,[["Hey!","gf",""],["Play Animation","shoot","1"]]],
        [500,[["Camera Follow Pos","",""],["Camera Follow Pos","300","-50.5"]]],
        [900,[["Change Scroll Speed","1.25","2"],["Change Scroll Speed","0.8",""]]],
        [1300,[["Set GF Speed","2.6",""],["Screen Shake","0.1, 0.02",""],["Some Custom","a","b"]]]
    ]"""

    @Test
    fun chartsMatchTheKotlinConverter() {
        if (!NativeScripts.available) return
        val normal = chart(150, events)
        val hard = chart(150, events)
        val erect = chart(170, "[[0,[[\"Hey!\",\"\",\"\"]]]]", "\"player3\":\"gf-car\",")
        val options = ConversionOptions(
            artist = "Kawai Sprite",
            extraEvents = listOf(PsychEvent(2000.0, "Change Scroll Speed", "2", "1")),
            playerVocals = listOf("bf"),
            opponentVocals = listOf("dad", "extra"),
            pixelStages = setOf("Stage")
        )
        listOf(true, false).forEach { split ->
            listOf(true, false).forEach { keep ->
                val (native, kotlin) = both {
                    PsychToVSliceChart.convert(
                        listOf(ChartSource("Hard", PsychChartParser.parse(hard)), ChartSource("normal", PsychChartParser.parse(normal)), ChartSource("Erect", PsychChartParser.parse(erect))),
                        options.copy(splitVariations = split, keepPsychEvents = keep)
                    )
                }
                assertEquals(kotlin.files("test"), native.files("test"))
                assertEquals(kotlin.report.summary(), native.report.summary())
                assertEquals(kotlin.report.toReport(), native.report.toReport())
                assertEquals(kotlin.metadata, native.metadata)
                assertEquals(kotlin.chart, native.chart)
            }
        }
        val (native, _) = both { PsychToVSliceChart.convert(listOf(ChartSource("normal", PsychChartParser.parse(normal)))) }
        val speed = native.chart.events.first { it.kind == "SetTargetBopSpeed" }
        assertEquals(mapOf("target" to "girlfriend", "rate" to 3), speed.value)
    }

    @Test
    fun renderedChartFilesMatchTheKotlinWriter() {
        if (!NativeScripts.available) return
        val odd = """{"song":{"song":"Odd </script> \u2028 \u0085 \t Song ü 😀","bpm":133.33333,"speed":1.0000004,"offset":-0.0004,"needsVoices":true,"player1":"bf","player2":"dad","stage":"stage",
            "notes":[{"lengthInSteps":16,"mustHitSection":true,"sectionNotes":[[0.0004,0,0],[1234.56789,1,0.0001],[12345678.9,2,99999999.123],[-5.5,3,-1],[7.0005,4,0.0005,"Weird / Kind"]]}],
            "events":[[0.00049,[["Camera Follow Pos","1e-7","123456789.5"]]],[10.5,[["Change Scroll Speed","0.33333","1.5"]]]]}}"""
        val (native, kotlin) = both { PsychToVSliceChart.convert(listOf(ChartSource("normal", PsychChartParser.parse(odd)))) }
        assertEquals(kotlin.files("odd"), native.files("odd"))
        assertEquals(kotlin.chart, native.chart)
        assertEquals(kotlin.metadata, native.metadata)
    }

    private val atlasXml = """<TextureAtlas imagePath="dude.png">
          <SubTexture name="dude idle0000" x="0" y="0" width="400" height="380" frameX="-2" frameY="-4" frameWidth="410" frameHeight="400"/>
          <SubTexture name="dude idle0001" x="0" y="0" width="400" height="380"/>
          <SubTexture name="dude left0000" x="0" y="0" width="420" height="390"/>
          <SubTexture name="dude dies0000" x="0" y="0" width="300" height="300"/>
        </TextureAtlas>"""

    @Test
    fun charactersMatchTheKotlinConverter() {
        if (!NativeScripts.available) return
        val text = """
            {"animations":[
              {"offsets":[-5,3],"loop":false,"fps":24,"anim":"idle","indices":[],"name":"dude idle"},
              {"offsets":[10,-4],"loop":true,"fps":30,"anim":"singLEFT","indices":[0,1],"name":"dude left"},
              {"offsets":[0,0],"loop":false,"fps":24,"anim":"singRIGHT","indices":[],"name":"dude missing"}],
             "image":"characters/Dude","position":[20,300],"healthicon":"dude","flip_x":true,"healthbar_colors":[12,200,30],
             "camera_position":[15,-10],"sing_duration":6.1,"scale":1.5,"no_antialiasing":true,"vocals_file":"Dude"}
        """.trimIndent()
        val dead = """
            {"animations":[{"offsets":[3,4],"loop":false,"fps":24,"anim":"firstDeath","indices":[],"name":"dude dies"},
              {"offsets":[0,0],"loop":true,"fps":24,"anim":"deathLoop","indices":[],"name":"dude dies"}],
             "image":"characters/Dude","position":[0,350],"healthicon":"dude","camera_position":[0,0],"scale":1.5}
        """.trimIndent()
        val psych = PsychCharacterParser.parse(text)
        val atlases = mapOf(PsychToVSliceCharacter.atlasKey(psych.image) to SparrowAtlas.parse(atlasXml))
        CharacterSlot.entries.forEach { slot ->
            listOf(atlases, emptyMap()).forEach { maps ->
                val (native, kotlin) = both {
                    PsychToVSliceCharacter.convert(CharacterSource("dude", psych, slot, maps, gameOver = PsychCharacterParser.parse(dead), gameOverId = "dude-dead"))
                }
                assertEquals(kotlin.json, native.json)
                assertEquals(kotlin.report, native.report)
            }
        }
    }

    @Test
    fun stagesMatchTheKotlinConverter() {
        if (!NativeScripts.available) return
        val atlas = SparrowAtlas.parse(
            """<TextureAtlas imagePath="dancer.png">
              <SubTexture name="bop0000" x="0" y="0" width="200" height="300"/>
              <SubTexture name="bop0001" x="200" y="0" width="220" height="300"/>
              <SubTexture name="hey0000" x="400" y="0" width="240" height="310"/>
            </TextureAtlas>"""
        )
        val stage = """{
            "directory": "week3", "defaultZoom": 0.85, "isPixelStage": true, "stageUI": "pixel",
            "boyfriend": [770.5, 100], "girlfriend": [400, 130], "opponent": [100, 100], "hide_girlfriend": false,
            "camera_boyfriend": [10, -20], "camera_opponent": [0, 0], "camera_girlfriend": [0, 0], "camera_speed": 1.5,
            "objects": [
              {"type": "sprite", "name": "bg", "image": "images/stageback.png", "x": -600, "y": -200, "scale": [1.1, 1.1], "scroll": [0.9, 0.9], "alpha": 1.4, "angle": 12, "color": "#f00", "filters": 3, "antialiasing": false, "flipX": true},
              {"type": "animatedSprite", "name": "dancer", "image": "dancer", "x": 100, "y": 50, "scale": [2, 1.5], "scroll": [1, 1], "alpha": 1, "angle": 0, "color": "purple", "filters": 7,
                "firstAnimation": "bop", "animations": [
                  {"anim": "bop", "name": "bop", "fps": 24, "loop": false, "indices": [], "offsets": [0, 0]},
                  {"anim": "hey", "name": "hey", "fps": 24, "loop": false, "indices": [0], "offsets": [-180, -140]},
                  {"anim": "gone", "name": "nothing", "fps": 12, "loop": true, "indices": [], "offsets": [1, 1]}
                ]},
              {"type": "animatedSprite", "name": "dancer", "image": "dancer", "x": 0, "y": 0, "scale": [1, 1], "scroll": [1, 1], "alpha": 1, "angle": 0, "color": "zzz", "filters": 3, "animations": []},
              {"type": "animatedSprite", "name": "", "image": "other", "x": 0, "y": 0, "scale": [3, 3], "scroll": [1, 1], "alpha": 1, "angle": 0, "color": "FFFFFF", "filters": 3,
                "animations": [{"anim": "a", "name": "a", "fps": 24, "loop": false, "indices": [], "offsets": [9, 9]}]},
              {"type": "gf"},
              {"type": "square", "name": "fade", "x": 0, "y": 0, "scale": [1280.6, 0.2], "scroll": [0, 0], "alpha": 0.5, "angle": 0, "color": "0xFF000000", "filters": 2},
              {"type": "sprite", "name": "lowOnly", "image": "x", "x": 0, "y": 0, "scale": [1, 1], "scroll": [1, 1], "alpha": 1, "angle": 0, "color": "FFFFFF", "filters": 1},
              {"type": "weird", "name": "w"},
              {"type": "dad"}
            ]
        }"""
        val lua = """
function onCreatePost()
    playAnim('dancer', 'bop', true)
    setBlendMode('dancer', 'add')
end

function onBeatHit()
    if curBeat % 2 == 0 then
        playAnim('dancer', 'bop', true)
    end
end
"""
        val parsed = PsychStageParser.parse(stage)
        val merged = LuaStageExtractor.merge(parsed, LuaStageExtractor.extract(lua, parsed.objectNames))
        listOf(parsed, merged).forEach { psych ->
            listOf<(String) -> SparrowAtlas?>({ if (it == "dancer") atlas else null }, { null }).forEach { lookup ->
                listOf("", "My Stage").forEach { name ->
                    val (native, kotlin) = both { PsychToVSliceStage.convert(StageSource("my-cool_stage", psych, name, lookup)) }
                    assertEquals(kotlin.json, native.json)
                    assertEquals(kotlin.report, native.report)
                }
            }
        }
        val code = """{"directory":"","defaultZoom":1.05,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100],"preload":["images/a","b"]}"""
        val (native, kotlin) = both { PsychToVSliceStage.convert(StageSource("philly", PsychStageParser.parse(code))) }
        assertEquals(kotlin.json, native.json)
        assertEquals(kotlin.report, native.report)
        assertTrue(native.json.contains("\"version\": \"1.0.2\""))
    }
}
