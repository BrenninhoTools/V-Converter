package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.WeekDocument
import com.brenninhotools.vconverter.editor.WeekExport
import com.brenninhotools.vconverter.editor.WeekLoader
import com.brenninhotools.vconverter.editor.WeekProp
import com.brenninhotools.vconverter.editor.WeekSlot
import com.brenninhotools.vconverter.editor.WeekSong
import com.brenninhotools.vconverter.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekEditorTest {
    private val document = WeekDocument(
        id = "weekdude",
        name = "Dude Week",
        songs = listOf(WeekSong("song-one", "Song One", "dude", "#FF0000"), WeekSong("song-two", "Song Two", "dude")),
        difficulties = listOf("easy", "normal", "hard", "erect"),
        weekBefore = "week1",
        startUnlocked = false,
        hiddenUntilUnlocked = true
    )

    @Test
    fun newWeekUsesBaseProps() {
        val root = JSONObject(WeekDocument("week9").toVSlice())
        val props = root.getJSONArray("props")
        assertEquals(3, props.length())
        assertEquals("storymenu/props/bf", props.getJSONObject(1).getString("assetPath"))
        assertEquals(150.0, props.getJSONObject(1).getJSONArray("offsets").getDouble(0), 0.0)
        assertEquals("storymenu/titles/week9", root.getString("titleAsset"))
    }

    @Test
    fun emptySlotsShiftPropsAndComeBack() {
        val custom = WeekProp("storymenu/props/dude", offsetX = 40.0, offsetY = 30.0).withPrefix("confirm", "dude confirm")
        val doc = document.withProp(WeekSlot.DAD, null).withProp(WeekSlot.GF, custom)
        val props = JSONObject(doc.toVSlice()).getJSONArray("props")
        assertEquals(2, props.length())
        assertEquals(470.0, props.getJSONObject(0).getJSONArray("offsets").getDouble(0), 0.0)
        assertEquals(360.0, props.getJSONObject(1).getJSONArray("offsets").getDouble(0), 0.0)
        val back = WeekDocument.fromVSlice("weekdude", doc.toVSlice())
        assertNull(back.prop(WeekSlot.DAD))
        assertEquals(150.0, back.prop(WeekSlot.BF)!!.offsetX, 0.0)
        assertEquals(40.0, back.prop(WeekSlot.GF)!!.offsetX, 0.0)
        assertEquals("dude confirm", back.prop(WeekSlot.GF)!!.anim("confirm")!!.prefix)
    }

    @Test
    fun vsliceExportWritesTheLockScript() {
        val output = WeekExport.build(document, ExportTarget.VSLICE, emptyMap())
        val paths = output.files.map { it.relativePath }
        assertTrue(paths.toString(), "data/levels/weekdude.json" in paths)
        val script = output.files.first { it.relativePath.startsWith("scripts/levels/") }.content
        assertTrue(script, script.contains("hasBeatenLevel(\"week1\")"))
        assertTrue(script, script.contains("isVisible"))
        val loaded = WeekLoader.load(output.files.map { it.relativePath to it.content.toByteArray() })
        assertEquals("week1", loaded.document.weekBefore)
        assertFalse(loaded.document.startUnlocked)
        assertTrue(loaded.document.hiddenUntilUnlocked)
        assertEquals(listOf("song-one", "song-two"), loaded.document.songs.map { it.id })
    }

    @Test
    fun psychExportKeepsWeekSettingsAndLoadsBack() {
        val output = WeekExport.build(document, ExportTarget.PSYCH, emptyMap())
        val week = JSONObject(output.files.first { it.relativePath == "weeks/weekdude.json" }.content)
        assertEquals("week1", week.getString("weekBefore"))
        assertFalse(week.getBoolean("startUnlocked"))
        assertTrue(week.getBoolean("hiddenUntilUnlocked"))
        assertEquals("Easy,Normal,Hard,Erect", week.getString("difficulties"))
        assertEquals("Song One", week.getJSONArray("songs").getJSONArray(0).getString(0))
        assertEquals(255, week.getJSONArray("songs").getJSONArray(0).getJSONArray(2).getInt(0))
        assertEquals("dad", week.getJSONArray("weekCharacters").getString(0))
        val loaded = WeekLoader.load(output.files.map { it.relativePath to it.content.toByteArray() }).document
        assertEquals("Psych Engine", WeekLoader.load(output.files.map { it.relativePath to it.content.toByteArray() }).source)
        assertEquals(document.songs, loaded.songs)
        assertEquals(document.difficulties, loaded.difficulties)
        assertEquals("week1", loaded.weekBefore)
        assertEquals(150.0, loaded.prop(WeekSlot.BF)!!.offsetX, 0.0)
    }

    @Test
    fun codenameExportWritesWeekAndCharacters() {
        val custom = WeekProp("storymenu/props/dude", offsetX = 40.0, offsetY = 30.0)
        val output = WeekExport.build(document.withProp(WeekSlot.DAD, custom), ExportTarget.CODENAME, emptyMap())
        val paths = output.files.map { it.relativePath }
        assertTrue(paths.toString(), "data/weeks/weeks/weekdude.xml" in paths)
        assertTrue(paths.toString(), "data/weeks/characters/dude.xml" in paths)
        val xml = output.files.first { it.relativePath == "data/weeks/weeks/weekdude.xml" }.content
        assertTrue(xml, xml.contains("chars=\"dude,bf,gf\""))
        val loaded = WeekLoader.load(output.files.map { it.relativePath to it.content.toByteArray() })
        assertEquals("Codename Engine", loaded.source)
        assertEquals(listOf("song-one", "song-two"), loaded.document.songs.map { it.id })
        assertEquals(40.0, loaded.document.prop(WeekSlot.DAD)!!.offsetX, 0.5)
    }
}
