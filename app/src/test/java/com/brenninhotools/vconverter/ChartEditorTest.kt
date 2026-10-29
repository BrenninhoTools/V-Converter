package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.editor.ChartBundle
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.Hitsound
import com.brenninhotools.vconverter.editor.SongLoader
import com.brenninhotools.vconverter.editor.ChartExport
import com.brenninhotools.vconverter.editor.ChartHistory
import com.brenninhotools.vconverter.editor.EditorEvent
import com.brenninhotools.vconverter.editor.EditorNote
import com.brenninhotools.vconverter.editor.EditorTiming
import com.brenninhotools.vconverter.editor.EventTemplates
import com.brenninhotools.vconverter.reverse.VSliceParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartEditorTest {
    @Test
    fun conductorFollowsTimeChanges() {
        val doc = ChartDocument.blank("test", "Test", 120.0, listOf("normal")).withTiming(listOf(EditorTiming(0.0, 120.0, 4), EditorTiming(2000.0, 60.0, 3)))
        val c = doc.conductor
        assertEquals(4.0, c.beatAt(2000.0), 1e-9)
        assertEquals(5.0, c.beatAt(3000.0), 1e-9)
        assertEquals(3000.0, c.timeAt(5.0), 1e-9)
        assertEquals(500.0, c.snap(480.0, 4), 1e-6)
        assertEquals(2250.0, c.snap(2240.0, 16), 1e-6)
        val lines = c.gridLines(0.0, 5000.0, 4)
        assertEquals(2, lines.first().second)
        assertEquals(2, lines.first { it.first == 2000.0 }.second)
        assertEquals(1, lines.first { it.first == 3000.0 }.second)
        assertEquals(2, lines.first { it.first == 5000.0 }.second)
        assertEquals(1, c.measureAt(2000.0))
    }

    @Test
    fun editingRoundTripsThroughVSliceAndExports() {
        var doc = ChartDocument.blank("my-song", "My Song", 150.0, listOf("easy", "normal"))
        val history = ChartHistory(doc)
        doc = history.push(doc.addNote("normal", EditorNote(400.0, 0)))
        doc = history.push(doc.addNote("normal", EditorNote(800.0, 5, 200.0, "alt")))
        doc = history.push(doc.addEvent(EditorEvent(800.0, "PlayAnimation", EventTemplates.of("PlayAnimation")!!.defaults())))
        assertEquals(2, doc.notes("normal").size)
        assertNotNull(doc.noteAt("normal", 900.0, 5, 10.0))
        assertNull(doc.noteAt("normal", 900.0, 4, 10.0))
        doc = history.undo()
        assertEquals(1, doc.events.size)
        doc = history.redo()
        assertEquals(2, doc.events.size)
        doc = history.push(doc.withSpeed("normal", 2.4).addDifficulty("hard", copyFrom = "normal"))
        assertEquals(listOf("easy", "normal", "hard"), doc.difficultyNames)

        val (metaPath, chartPath) = doc.fileNames()
        assertEquals("data/songs/my-song/my-song-metadata.json", metaPath)
        val metadata = VSliceParser.metadata(doc.metadataJson())
        val chart = VSliceParser.chart(doc.chartJson())
        assertEquals(150.0, metadata.timeChanges.first().bpm, 0.0)
        assertEquals(2.4, chart.scrollSpeed["normal"]!!, 0.0)
        assertEquals("alt", chart.notes["hard"]!![1].kind)

        val reloaded = ChartDocument.load("my-song", "", doc.metadataJson(), doc.chartJson())
        assertEquals(doc.notes("hard"), reloaded.notes("hard"))
        assertEquals(doc.events.map { it.kind }, reloaded.events.map { it.kind })
        assertEquals("My Song", reloaded.meta.songName)

        val psych = ChartExport.psych(doc)
        assertTrue(psych.files.any { it.relativePath == "data/my-song/my-song.json" })
        val psychChart = JSONObject(psych.files.first { it.relativePath == "data/my-song/my-song-hard.json" }.content)
        assertEquals(2.4, (psychChart.optJSONObject("song") ?: psychChart).getDouble("speed"), 0.0)
        val codename = ChartExport.codename(doc)
        assertTrue(codename.files.any { it.relativePath == "songs/my-song/charts/hard.json" })
        assertTrue(codename.files.any { it.relativePath == "songs/my-song/meta.json" })
    }

    @Test
    fun mirrorAndRemove() {
        var doc = ChartDocument.blank("x", "X", 100.0, listOf("normal")).addNote("normal", EditorNote(0.0, 1)).addNote("normal", EditorNote(0.0, 6))
        doc = doc.mirrorNotes("normal")
        assertEquals(listOf(2, 5), doc.notes("normal").map { it.data })
        doc = doc.removeNote("normal", EditorNote(0.0, 2))
        assertEquals(listOf(5), doc.notes("normal").map { it.data })
        assertFalse(runCatching { doc.removeDifficulty("normal") }.isSuccess)
    }

    @Test
    fun bundlesAudioAndReloadsExports() {
        val doc = ChartDocument.blank("tune", "Tune", 128.0, listOf("normal", "hard")).addNote("hard", EditorNote(500.0, 2, 250.0))
        val audio = listOf(EditorAudio("Inst.ogg", byteArrayOf(1)), EditorAudio("Voices-bf.ogg", byteArrayOf(2)), EditorAudio("Voices-dad.ogg", byteArrayOf(3)))
        val vslice = ChartBundle.build(doc, ExportTarget.VSLICE, audio).files.map { it.relativePath }
        assertTrue(vslice.containsAll(listOf("songs/tune/Inst.ogg", "songs/tune/Voices-bf.ogg", "songs/tune/Voices-dad.ogg", "data/songs/tune/tune-chart.json")))
        val psych = ChartBundle.build(doc, ExportTarget.PSYCH, audio).files
        assertTrue(psych.map { it.relativePath }.containsAll(listOf("songs/tune/Inst.ogg", "songs/tune/Voices-Player.ogg", "songs/tune/Voices-Opponent.ogg")))
        val codename = ChartBundle.build(doc, ExportTarget.CODENAME, audio).files
        assertTrue(codename.any { it.relativePath == "songs/tune/song/Voices-Player.ogg" })
        val hard = JSONObject(codename.first { it.relativePath == "songs/tune/charts/hard.json" }.content)
        assertEquals("-Player", (0 until hard.getJSONArray("strumLines").length()).map { hard.getJSONArray("strumLines").getJSONObject(it) }.first { it.getInt("type") == 1 }.getString("vocalsSuffix"))

        val fromVSlice = SongLoader.load(ChartBundle.build(doc, ExportTarget.VSLICE, audio).files.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) })
        assertEquals(doc.notes("hard"), fromVSlice.document.notes("hard"))
        assertEquals("Inst.ogg", fromVSlice.audio.first().name)
        val fromPsych = SongLoader.load(psych.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) })
        assertEquals("Psych Engine", fromPsych.source)
        assertEquals(1, fromPsych.document.notes("hard").size)
        assertEquals(250.0, fromPsych.document.notes("hard").first().length, 1.0)
        val fromCodename = SongLoader.load(codename.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) })
        assertEquals("Codename Engine", fromCodename.source)
        assertEquals(1, fromCodename.document.notes("hard").size)
        assertTrue(Hitsound.wav().size > 44)
    }
}
