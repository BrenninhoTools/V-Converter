package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.chart.preview.ChartEngine
import com.brenninhotools.vconverter.chart.preview.ChartPreviews
import com.brenninhotools.vconverter.chart.preview.ChartSide
import com.brenninhotools.vconverter.chart.preview.ChartTweaks
import com.brenninhotools.vconverter.chart.preview.ChartWorkbench
import com.brenninhotools.vconverter.convert.CodenameToPsychChartsConverter
import com.brenninhotools.vconverter.convert.PickedText
import com.brenninhotools.vconverter.convert.PsychChartsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameChartsConverter
import com.brenninhotools.vconverter.convert.ReverseChartsConverter
import com.brenninhotools.vconverter.io.ImportedFile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartWorkbenchTest {
    private fun section(beats: Int, mustHit: Boolean, notes: String, bpm: Double? = null) =
        """{"sectionBeats": $beats, "mustHitSection": $mustHit, "gfSection": false, "altAnim": false, "changeBPM": ${bpm != null}, "bpm": ${bpm ?: 120}, "sectionNotes": [$notes]}"""

    private fun chart(speed: Double, bpm: Double, sections: List<String>, events: String = "[]", offset: Double = 0.0) = """{"song": {
        "song": "Test Song", "format": "psych_v1", "bpm": $bpm, "speed": $speed, "offset": $offset, "needsVoices": true,
        "player1": "bf", "player2": "dad", "gfVersion": "gf", "stage": "stage",
        "notes": [${sections.joinToString(",")}], "events": $events }}"""

    private val normal = chart(2.0, 120.0, listOf(
        section(4, true, "[0, 0, 0], [500, 5, 250]"),
        section(3, false, "[2000, 1, 0]"),
        section(4, true, "[3500, 3, 0]", bpm = 150.0)
    ), events = """[[1000, [["Hey!", "BF", ""]]], [1500, [["Add Camera Zoom", "0.015", "0.03"]]]]""", offset = 25.0)

    private val hard = chart(2.6, 120.0, listOf(
        section(4, true, "[0, 0, 0], [250, 2, 0], [500, 5, 250]"),
        section(3, false, "[2000, 1, 0]"),
        section(4, true, "[3500, 3, 0]", bpm = 150.0)
    ), events = """[[1000, [["Hey!", "BF", ""]]], [1500, [["Add Camera Zoom", "0.015", "0.03"]]]]""", offset = 25.0)

    private val erect = chart(3.0, 140.0, listOf(section(4, true, "[0, 0, 0]")))

    private fun picked(converter: com.brenninhotools.vconverter.convert.FileConverter, path: String, text: String): PickedText =
        converter.classify(ImportedFile(path.substringAfterLast('/'), path, true) { text.byteInputStream() }, text)

    @Test
    fun psychToVSliceKeepsSignaturesOffsetAndSplitsVariations() {
        val converter = PsychChartsConverter()
        val items = listOf(
            picked(converter, "data/test-song/test-song.json", normal),
            picked(converter, "data/test-song/test-song-hard.json", hard),
            picked(converter, "data/test-song/test-song-erect.json", erect)
        )
        assertEquals("test-song · hard", items[1].label)
        val before = ChartWorkbench.before(converter, items)
        assertEquals(3, before.size)
        assertEquals(listOf(4, 3, 4), before.first { it.difficulty == "normal" }.timing.map { it.numerator })
        val run = ChartWorkbench.run(converter, items, ChartTweaks(artist = "Kawai Sprite", speeds = mapOf("hard" to 3.1), stage = "mainStage"))
        val paths = run.output.files.map { it.relativePath }
        assertTrue(paths.contains("data/songs/test-song/test-song-metadata-erect.json"))
        assertTrue(paths.contains("data/songs/test-song/test-song-chart-erect.json"))
        val meta = JSONObject(run.output.files.first { it.relativePath.endsWith("test-song-metadata.json") }.content)
        assertEquals(25.0, meta.getJSONObject("offsets").getDouble("instrumental"), 0.0)
        assertEquals("Kawai Sprite", meta.getString("artist"))
        assertEquals("mainStage", meta.getJSONObject("playData").getString("stage"))
        assertEquals("erect", meta.getJSONObject("playData").getJSONArray("songVariations").getString(0))
        val changes = meta.getJSONArray("timeChanges")
        assertEquals(3, changes.length())
        assertEquals(3, changes.getJSONObject(1).getInt("n"))
        assertEquals(150.0, changes.getJSONObject(2).getDouble("bpm"), 0.0)
        val chartJson = JSONObject(run.output.files.first { it.relativePath.endsWith("test-song-chart.json") }.content)
        assertEquals(3.1, chartJson.getJSONObject("scrollSpeed").getDouble("hard"), 0.0)
        val erectMeta = JSONObject(run.output.files.first { it.relativePath.endsWith("test-song-metadata-erect.json") }.content)
        assertEquals(140.0, erectMeta.getJSONArray("timeChanges").getJSONObject(0).getDouble("bpm"), 0.0)
        assertEquals(3, run.after.size)
        val afterHard = run.after.first { it.difficulty == "hard" }
        val beforeHard = before.first { it.difficulty == "hard" }
        assertTrue(afterHard.matches(beforeHard))
        assertEquals(beforeHard.notes.size, afterHard.notes.size)
        assertEquals(beforeHard.playerNotes, afterHard.playerNotes)
        assertEquals(listOf(4, 3, 4), afterHard.timing.map { it.numerator })
        assertTrue(run.output.report.sections.any { it.title == "Your changes" })
    }

    @Test
    fun keepEventsToggleDropsUnknownEvents() {
        val converter = PsychChartsConverter()
        val items = listOf(picked(converter, "data/test-song/test-song.json", normal))
        val kept = ChartWorkbench.run(converter, items, ChartTweaks())
        assertTrue(kept.after.first().events.any { it.name == "AddCameraZoom" })
        assertTrue(kept.output.files.any { it.relativePath.startsWith("scripts/events/") })
        val dropped = ChartWorkbench.run(converter, items, ChartTweaks(keepUnknownEvents = false))
        assertFalse(dropped.after.first().events.any { it.name == "AddCameraZoom" })
        assertFalse(dropped.output.files.any { it.relativePath.startsWith("scripts/events/") })
    }

    @Test
    fun vsliceRoundTripKeepsOffsetAndSignatures() {
        val forward = ChartWorkbench.run(PsychChartsConverter(), listOf(picked(PsychChartsConverter(), "data/test-song/test-song.json", normal)), ChartTweaks())
        val reverse = ReverseChartsConverter()
        val items = forward.output.files.filter { it.relativePath.startsWith("data/songs/") }.map { picked(reverse, it.relativePath, it.content) }
        val back = ChartWorkbench.run(reverse, items, ChartTweaks(player = "pico"))
        val psych = back.output.files.first { it.relativePath.endsWith(".json") && it.relativePath.startsWith("data/test-song/") }
        val root = JSONObject(psych.content).let { it.optJSONObject("song") ?: it }
        assertEquals(25.0, root.getDouble("offset"), 0.0)
        assertEquals("pico", root.getString("player1"))
        val beats = (0 until root.getJSONArray("notes").length()).map { root.getJSONArray("notes").getJSONObject(it).getDouble("sectionBeats") }
        assertEquals(listOf(4.0, 3.0, 4.0), beats.take(3))
        assertEquals("pico", back.after.first().player)
    }

    @Test
    fun codenamePreviewsAndTweaks() {
        val p2c = PsychToCodenameChartsConverter()
        val items = listOf(picked(p2c, "data/test-song/test-song.json", normal), picked(p2c, "data/test-song/test-song-hard.json", hard))
        val run = ChartWorkbench.run(p2c, items, ChartTweaks(opponent = "spooky", speeds = mapOf("normal" to 1.7)))
        val normalChart = run.after.first { it.difficulty == "normal" }
        assertEquals("spooky", normalChart.opponent)
        assertEquals(1.7, normalChart.speed, 0.0)
        assertEquals(4, normalChart.notes.size)
        assertEquals(1, normalChart.notes.count { it.side == ChartSide.OPPONENT })
        val c2p = CodenameToPsychChartsConverter()
        val codenameItems = run.output.files.filter { it.relativePath.endsWith(".json") && it.relativePath.startsWith("songs/") }.map { picked(c2p, it.relativePath, it.content) }
        val before = ChartWorkbench.before(c2p, codenameItems)
        assertEquals(setOf("normal", "hard"), before.map { it.difficulty }.toSet())
        assertEquals(listOf(4, 3, 4), before.first { it.difficulty == "normal" }.timing.map { it.numerator })
        val back = ChartWorkbench.run(c2p, codenameItems, ChartTweaks())
        assertEquals(before.first { it.difficulty == "hard" }.notes.size, back.after.first { it.difficulty == "hard" }.notes.size)
        assertEquals(ChartPreviews.read(ChartEngine.PSYCH, listOf("data/test-song/test-song.json" to normal)).first().playerNotes, back.after.first { it.difficulty == "normal" }.playerNotes)
    }
}
