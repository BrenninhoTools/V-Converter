package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.mod.ModFiles
import com.vconvertercrew.vconverter.mod.readText
import com.vconvertercrew.vconverter.playtest.MemoryModWriter
import com.vconvertercrew.vconverter.vslice.VSlicePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class VSlicePreviewTest {
    private class MemoryFiles(private val files: Map<String, ByteArray>) : ModFiles {
        override val paths: List<String> = files.keys.toList()
        override fun open(path: String): InputStream = ByteArrayInputStream(files.getValue(path))
        override fun size(path: String): Long = files.getValue(path).size.toLong()
    }

    private fun stableMod(writer: com.vconvertercrew.vconverter.mod.ModWriter) {
        writer.writeText("_polymod_meta.json", """{"title":"Test","api_version":"0.7.4"}""")
        writer.writeText("data/songs/tune/tune-metadata.json", """{"songName":"Tune","playData":{"stage":"club","album":"tunealbum"}}""")
        writer.writeText("data/songs/tune/tune-chart.json", """{"notes":{}}""")
        writer.copy("songs/tune/Inst.ogg") { ByteArrayInputStream(byteArrayOf(1)) }
        writer.copy("songs/tune/Voices-dude.ogg") { ByteArrayInputStream(byteArrayOf(2)) }
        writer.writeText("data/characters/dude.json", """{"name":"Dude","assetPath":"shared:characters/DUDE","healthIcon":{"id":"dude"},"animations":[{"name":"idle","prefix":"idle"}]}""")
        writer.copy("images/characters/DUDE.png") { ByteArrayInputStream(byteArrayOf(3)) }
        writer.writeText("images/characters/DUDE.xml", "<TextureAtlas/>")
        writer.copy("images/icons/icon-dude.png") { ByteArrayInputStream(byteArrayOf(4)) }
        writer.copy("images/freeplay/icons/dudepixel.png") { ByteArrayInputStream(byteArrayOf(5)) }
        writer.writeText("data/stages/club.json", """{"name":"Club","props":[{"name":"bg","assetPath":"club/back"},{"name":"tint","assetPath":"#FF0000"},{"name":"base","assetPath":"stageback"}]}""")
        writer.copy("images/club/back.png") { ByteArrayInputStream(byteArrayOf(6)) }
        writer.writeText("data/levels/weekdude.json", """{"name":"Dude Week","titleAsset":"storymenu/titles/weekdude","songs":["tune"],"props":[{"assetPath":"storymenu/props/dude"}]}""")
        writer.copy("images/storymenu/titles/weekdude.png") { ByteArrayInputStream(byteArrayOf(7)) }
        writer.copy("images/storymenu/props/dude.png") { ByteArrayInputStream(byteArrayOf(8)) }
        writer.writeText("images/storymenu/props/dude.xml", "<TextureAtlas/>")
        writer.writeText("scripts/songs/tune.hxc", "class TuneSong extends Song { public function new() { super('tune'); } }")
        writer.writeText("scripts/stages/club.hxc", "class ClubStage extends Stage { public function new() { super('club'); } function onCreate() { Paths.image('club/back'); } }")
        writer.writeText("scripts/modules/Hud.hxc", "class Hud extends Module {}")
        writer.writeText("_merge/data/songs/tune/tune-metadata.json", """[{"op":"add","path":"/playData/songVariations/-","value":"pico"}]""")
        writer.writeText("data/ui/freeplay/albums/tunealbum.json", """{"name":"Album","albumArtAsset":"freeplay/albums/tunecover","albumTitleAsset":"freeplay/albums/tunetitle"}""")
        writer.copy("images/freeplay/albums/tunecover.png") { ByteArrayInputStream(byteArrayOf(9)) }
    }

    private fun previewOf(): Map<String, ByteArray> {
        val memory = MemoryModWriter()
        VSlicePreview.enabled = true
        try {
            VSlicePreview.write(memory, true) { stableMod(it) }
        } finally {
            VSlicePreview.enabled = false
        }
        return memory.files
    }

    @Test
    fun stableOutputIsMovedToThePlayTestLayout() {
        val out = previewOf()
        val paths = out.keys
        listOf(
            "gameplay/songs/tune/tune-metadata.json",
            "gameplay/songs/tune/tune-chart.json",
            "gameplay/songs/tune/Inst.ogg",
            "gameplay/songs/tune/Voices-dude.ogg",
            "gameplay/songs/tune/tune.hxc",
            "gameplay/characters/dude/dude.json",
            "gameplay/characters/dude/DUDE.png",
            "gameplay/characters/dude/DUDE.xml",
            "gameplay/characters/dude/icon-dude.png",
            "ui/freeplay/characters/dude.png",
            "gameplay/stages/club/club.json",
            "gameplay/stages/club/back.png",
            "gameplay/stages/club/club.hxc",
            "ui/story-mode/levels/weekdude.json",
            "ui/story-mode/levels/weekdude.png",
            "ui/story-mode/props/dude.png",
            "ui/story-mode/props/dude.xml",
            "ui/freeplay/albums/tunealbum.json",
            "ui/freeplay/albums/tunecover.png",
            "_merge/gameplay/songs/tune/tune-metadata.json",
            "scripts/modules/Hud.hxc",
            "images/club/back.png"
        ).forEach { assertTrue("missing $it", it in paths) }
        listOf("data/characters/dude.json", "songs/tune/Inst.ogg", "images/characters/DUDE.png", "data/levels/weekdude.json", "scripts/songs/tune.hxc").forEach {
            assertFalse("left behind $it", it in paths)
        }
        val character = JSONObject(out.getValue("gameplay/characters/dude/dude.json").decodeToString())
        assertEquals("gameplay/characters/dude/DUDE", character.getString("assetPath"))
        val stage = JSONObject(out.getValue("gameplay/stages/club/club.json").decodeToString()).getJSONArray("props")
        assertEquals("gameplay/stages/club/back", stage.getJSONObject(0).getString("assetPath"))
        assertEquals("#FF0000", stage.getJSONObject(1).getString("assetPath"))
        assertEquals("stageback", stage.getJSONObject(2).getString("assetPath"))
        val level = JSONObject(out.getValue("ui/story-mode/levels/weekdude.json").decodeToString())
        assertEquals("ui/story-mode/levels/weekdude", level.getString("titleAsset"))
        assertEquals("ui/story-mode/props/dude", level.getJSONArray("props").getJSONObject(0).getString("assetPath"))
        val album = JSONObject(out.getValue("ui/freeplay/albums/tunealbum.json").decodeToString())
        assertEquals("ui/freeplay/albums/tunecover", album.getString("albumArtAsset"))
        assertEquals("freeplay/albums/tunetitle", album.getString("albumTitleAsset"))
        assertEquals(VSlicePreview.API_VERSION, JSONObject(out.getValue("_polymod_meta.json").decodeToString()).getString("api_version"))
        assertTrue(VSlicePreview.isPreview(paths))
    }

    @Test
    fun stableLayoutIsUntouchedWhenTheOptionIsOff() {
        val memory = MemoryModWriter()
        VSlicePreview.write(memory, true) { stableMod(it) }
        assertTrue("data/characters/dude.json" in memory.files)
        assertFalse(VSlicePreview.isPreview(memory.files.keys))
        assertNull(VSlicePreview.previewRoot(memory.files.keys))
    }

    @Test
    fun playTestModsReadAsStableVSlice() {
        val preview = previewOf().mapKeys { "Thorns (Pico Mix) [PREVIEW #4 Build]/${it.key}" } + mapOf("other/readme.txt" to "hi".encodeToByteArray())
        val view = VSlicePreview.asStable(MemoryFiles(preview))
        val root = "Thorns (Pico Mix) [PREVIEW #4 Build]/"
        val paths = view.paths.toSet()
        listOf(
            "data/songs/tune/tune-metadata.json",
            "data/songs/tune/tune-chart.json",
            "songs/tune/Inst.ogg",
            "scripts/songs/tune.hxc",
            "data/characters/dude.json",
            "images/characters/dude/DUDE.png",
            "images/icons/icon-dude.png",
            "images/freeplay/icons/dudepixel.png",
            "data/stages/club.json",
            "images/stages/club/back.png",
            "scripts/stages/club.hxc",
            "data/levels/weekdude.json",
            "images/storymenu/titles/weekdude.png",
            "images/storymenu/props/dude.png",
            "data/ui/freeplay/albums/tunealbum.json",
            "images/freeplay/albums/tunecover.png",
            "_merge/data/songs/tune/tune-metadata.json",
            "scripts/modules/Hud.hxc"
        ).forEach { assertTrue("missing $it", root + it in paths) }
        assertTrue("other/readme.txt" in paths)
        val character = JSONObject(view.readText(root + "data/characters/dude.json"))
        assertEquals("characters/dude/DUDE", character.getString("assetPath"))
        val level = JSONObject(view.readText(root + "data/levels/weekdude.json"))
        assertEquals("storymenu/titles/weekdude", level.getString("titleAsset"))
        assertEquals("storymenu/props/dude", level.getJSONArray("props").getJSONObject(0).getString("assetPath"))
        assertEquals(1, view.open(root + "songs/tune/Inst.ogg").use { it.readBytes() }.single().toInt())
    }

    @Test
    fun atlasFoldersAndRealPlayTestNamesAreMapped() {
        val files = mapOf(
            "gameplay/characters/dad/dad.json" to """{"assetPath":"gameplay/characters/dad/daddy-dearest","renderType":"animateatlas"}""",
            "gameplay/characters/dad/daddy-dearest/Animation.json" to "{}",
            "gameplay/characters/dad/icon-dad.png" to "",
            "gameplay/songs/thorns/thorns-metadata-pico.json" to "{}",
            "gameplay/songs/thorns/Inst-pico.ogg" to "",
            "gameplay/songs/thorns/cutscene/senpai.png" to "",
            "gameplay/dialogue/speakers/pico-pixel-pissed/pico-pixel-pissed.json" to """{"assetPath":"gameplay/dialogue/speakers/pico-pixel-pissed/portrait-pico-pissed"}""",
            "gameplay/dialogue/speakers/pico-pixel-pissed/portrait-pico-pissed.png" to "",
            "_merge/gameplay/songs/thorns/thorns-metadata.json" to "[]"
        ).mapValues { it.value.encodeToByteArray() }
        val view = VSlicePreview.asStable(MemoryFiles(files))
        val paths = view.paths.toSet()
        assertTrue("data/characters/dad.json" in paths)
        assertTrue("images/characters/dad/daddy-dearest/Animation.json" in paths)
        assertTrue("images/icons/icon-dad.png" in paths)
        assertTrue("data/songs/thorns/thorns-metadata-pico.json" in paths)
        assertTrue("songs/thorns/Inst-pico.ogg" in paths)
        assertTrue("gameplay/songs/thorns/cutscene/senpai.png" in paths)
        assertTrue("data/dialogue/speakers/pico-pixel-pissed.json" in paths)
        assertTrue("_merge/data/songs/thorns/thorns-metadata.json" in paths)
        assertEquals("characters/dad/daddy-dearest", JSONObject(view.readText("data/characters/dad.json")).getString("assetPath"))
        assertEquals("dialogue/speakers/pico-pixel-pissed/portrait-pico-pissed", JSONObject(view.readText("data/dialogue/speakers/pico-pixel-pissed.json")).getString("assetPath"))
    }
}
