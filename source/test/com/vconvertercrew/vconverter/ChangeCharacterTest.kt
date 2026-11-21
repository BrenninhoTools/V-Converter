package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.chart.ChartSource
import com.vconvertercrew.vconverter.chart.ConversionOptions
import com.vconvertercrew.vconverter.chart.PsychToVSliceChart
import com.vconvertercrew.vconverter.chart.psych.PsychChartParser
import com.vconvertercrew.vconverter.convert.PsychChartsConverter
import com.vconvertercrew.vconverter.script.ChangeCharacterEvent
import com.vconvertercrew.vconverter.script.NativeData
import com.vconvertercrew.vconverter.script.NativeScripts
import com.vconvertercrew.vconverter.script.ScriptConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeCharacterTest {
    private val chart = """{"song":{"song":"Swap","bpm":120,"speed":1,"needsVoices":false,"player1":"bf","player2":"dad","gfVersion":"gf","stage":"stage",
        "notes":[{"sectionBeats":4,"mustHitSection":true,"sectionNotes":[[0,0,0]]}],
        "events":[[500,[["Change Character","dad","pico"]]],[1000,[["ChangeCharacter","bf","bf-car"],["Hey!","gf",""]]],[1500,[["change_character","2","gf-car"]]]]}}"""

    private fun convert(keep: Boolean) = PsychToVSliceChart.convert(listOf(ChartSource("normal", PsychChartParser.parse(chart))), ConversionOptions(keepPsychEvents = keep))

    @Test
    fun changeCharacterEventsAlwaysGetAScript() {
        listOf(true, false).forEach { native ->
            NativeData.enabled = native
            try {
                listOf(true, false).forEach { keep ->
                    val result = convert(keep)
                    val swaps = result.chart.events.filter { it.kind == "ChangeCharacter" }
                    assertEquals(3, swaps.size)
                    assertEquals(mapOf("value1" to "dad", "value2" to "pico"), swaps.first().value)
                    assertEquals(3, result.report.scriptedEvents["Change Character"])
                    assertTrue(result.report.unsupportedEvents.isEmpty())
                    val cheer = result.chart.events.single { it.kind == "PlayAnimation" }
                    assertEquals("cheer", cheer.value["anim"])
                    assertEquals("gf", cheer.value["target"])
                    assertTrue(result.report.toReport().sections.any { section -> section.lines.any { "ChangeCharacterEvent.hxc" in it } })
                }
            } finally {
                NativeData.enabled = true
            }
        }
    }

    @Test
    fun changeCharacterScriptSwapsThroughTheStage() {
        val file = ScriptConverter.eventFiles(listOf("ChangeCharacter")).single()
        assertEquals("scripts/events/ChangeCharacterEvent.hxc", file.path)
        val text = file.content
        assertTrue(text.contains("class ChangeCharacterEvent extends SongEvent"))
        assertTrue(text.contains("super(\"ChangeCharacter\", {processOldEvents: true});"))
        assertTrue(text.contains("import funkin.data.character.CharacterDataParser;"))
        assertTrue(text.contains("import funkin.play.character.CharacterType;"))
        assertTrue(text.contains("CharacterDataParser.fetchCharacter(id)"))
        assertTrue(text.contains("stage.addCharacter(next, slot == 1 ? CharacterType.DAD : (slot == 2 ? CharacterType.GF : CharacterType.BF));"))
        assertTrue(text.contains("songEvent.eventKind != \"ChangeCharacter\""))
        assertTrue(text.contains("public override function onCountdownStart(event:CountdownScriptEvent):Void"))
        assertTrue(text.contains("return \"Change Character\";"))
        assertTrue(!text.contains("trace("))
        assertEquals(text.count { it == '{' }, text.count { it == '}' })
        assertEquals(ChangeCharacterEvent.source("ChangeCharacterEvent", "ChangeCharacter", "Change Character"), text)
        if (NativeScripts.available) assertEquals(NativeScripts.builtinEvent("Change Character", "ChangeCharacterEvent", "ChangeCharacter"), text)
    }

    @Test
    fun chartConverterWritesTheEventScript() {
        val converter = PsychChartsConverter()
        converter.keepEvents = false
        val output = converter.build(listOf(com.vconvertercrew.vconverter.convert.PickedText("swap", "swap.json", "data/swap/swap.json", chart, "chart", "swap · normal")))
        assertTrue(output.files.any { it.relativePath == "scripts/events/ChangeCharacterEvent.hxc" })
        assertTrue(output.files.any { it.relativePath == "data/songs/swap/swap-chart.json" && "ChangeCharacter" in it.content })
    }
}
