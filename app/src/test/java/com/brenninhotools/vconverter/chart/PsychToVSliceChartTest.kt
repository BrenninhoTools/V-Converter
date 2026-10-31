package com.brenninhotools.vconverter.chart

import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PsychToVSliceChartTest {
    private val legacyChart = """
        {"song":{"song":"Test Song","bpm":120,"speed":2.5,"needsVoices":true,
        "player1":"bf","player2":"dad","player3":"gf","stage":"stage",
        "notes":[
          {"lengthInSteps":16,"mustHitSection":false,"sectionNotes":[[0,0,0],[500,5,250],[750,-1,"Hey!","bf",""]]},
          {"lengthInSteps":16,"mustHitSection":true,"changeBPM":true,"bpm":60,"sectionNotes":[[2000,1,0,"Alt Animation"],[2000,1,0]]}
        ]}}
    """.trimIndent()

    private val psychV1Chart = """
        {"song":"Test Song","bpm":120,"speed":3,"format":"psych_v1","player1":"bf","player2":"pico","gfVersion":"gf","stage":"school",
        "events":[[1000,[["Change Scroll Speed","1.5","1"],["Add Camera Zoom","",""]]]],
        "notes":[{"sectionBeats":4,"mustHitSection":true,"gfSection":false,"sectionNotes":[[0,4,0],[100,0,0,"Hurt Note"]]}]}
    """.trimIndent()

    @Test
    fun legacyLanesAreFlippedBySection() {
        val song = PsychChartParser.parse(legacyChart)
        val lanes = song.sections.flatMap { s -> s.notes.map { it.lane } }
        assertEquals(listOf(4, 1, 1, 1), lanes)
        assertEquals(1, song.events.size)
    }

    @Test
    fun timingFollowsSectionBpmChanges() {
        val result = PsychToVSliceChart.convert(listOf(ChartSource("Hard", PsychChartParser.parse(legacyChart))))
        val changes = result.metadata.timeChanges
        assertEquals(2, changes.size)
        assertEquals(2000.0, changes[1].time, 0.001)
        assertEquals(4.0, changes[1].beat, 0.001)
        assertEquals(listOf("hard"), result.metadata.difficulties)
        assertEquals(3, result.report.noteCounts["hard"])
        assertEquals(1, result.report.duplicateNotes["hard"])
    }

    @Test
    fun psychV1EventsAndKindsAreConverted() {
        val result = PsychToVSliceChart.convert(listOf(ChartSource("normal", PsychChartParser.parse(psychV1Chart))))
        val kinds = result.chart.events.map { it.kind }
        assertTrue("ScrollSpeed" in kinds)
        assertEquals(1, result.report.unsupportedEvents["Add Camera Zoom"])
        assertEquals("pixel", result.metadata.noteStyle)
        assertEquals("hurt", result.chart.notes.getValue("normal").first { it.time == 100.0 }.kind)
        val chartJson = JSONObject(VSliceWriter.chartJson(result.chart))
        assertEquals(2, chartJson.getJSONObject("notes").getJSONArray("normal").length())
    }
}
