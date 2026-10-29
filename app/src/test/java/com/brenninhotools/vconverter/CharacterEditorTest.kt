package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.editor.CharacterDocument
import com.brenninhotools.vconverter.editor.CharacterExport
import com.brenninhotools.vconverter.editor.CharacterLoader
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SpriteAtlas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterEditorTest {
    private val xml = """
        <TextureAtlas imagePath="Dude.png">
          <SubTexture name="dude idle0000" x="0" y="0" width="400" height="380" frameX="-2" frameY="-4" frameWidth="410" frameHeight="400"/>
          <SubTexture name="dude idle0001" x="400" y="0" width="400" height="380" frameX="-2" frameY="-4" frameWidth="410" frameHeight="400"/>
          <SubTexture name="dude left0000" x="0" y="400" width="420" height="390"/>
          <SubTexture name="dude down0000" x="420" y="400" width="420" height="390"/>
        </TextureAtlas>
    """.trimIndent()

    private val psych = """
        {"animations":[
          {"offsets":[-5,3],"loop":false,"fps":24,"anim":"idle","indices":[],"name":"dude idle"},
          {"offsets":[10,-4],"loop":false,"fps":24,"anim":"singLEFT","indices":[],"name":"dude left"}],
         "image":"characters/Dude","position":[20,300],"healthicon":"dude","flip_x":false,
         "camera_position":[15,-10],"sing_duration":4,"scale":1.5,"no_antialiasing":false}
    """.trimIndent()

    private val png = byteArrayOf(-119, 80, 78, 71)

    @Test
    fun atlasFramesFollowIndices() {
        val atlas = SpriteAtlas.parse(xml)
        assertEquals("Dude.png", atlas.imagePath)
        assertEquals(listOf("dude idle0000", "dude idle0001"), atlas.framesFor("dude idle").map { it.name })
        assertEquals(listOf("dude idle0001", "dude idle0000"), atlas.framesFor("dude idle", listOf(1, 0)).map { it.name })
        assertEquals(-2, atlas.framesFor("dude idle").first().frameX)
        assertEquals(410, atlas.framesFor("dude idle").first().frameWidth)
        assertEquals(listOf("dude idle", "dude left", "dude down"), atlas.prefixes())
    }

    @Test
    fun loadsPsychAndExportsEverywhere() {
        val loaded = CharacterLoader.load(listOf("characters/dude.json" to psych.toByteArray(), "images/characters/Dude.xml" to xml.toByteArray(), "images/characters/Dude.png" to png))
        assertEquals("Psych Engine", loaded.source)
        assertNotNull(loaded.image)
        assertNotNull(loaded.xml)
        val doc = loaded.document
        assertEquals("dude", doc.id)
        assertEquals(listOf("idle", "singLEFT"), doc.animations.map { it.name })
        assertEquals(1.5, doc.scale, 0.0)

        val edited = doc.replaceAnim(1, doc.animations[1].copy(offsetX = 42.0)).copy(slot = CharacterSlot.OPPONENT)
        val vslice = CharacterExport.build(edited, ExportTarget.VSLICE, loaded.image, loaded.xml).files
        val json = JSONObject(vslice.first { it.relativePath == "data/characters/dude.json" }.content)
        assertEquals(42.0, json.getJSONArray("animations").getJSONObject(1).getJSONArray("offsets").getDouble(0), 0.0)
        assertTrue(vslice.any { it.relativePath == "images/characters/Dude.png" && it.bytes != null })

        val again = CharacterDocument.fromVSlice("dude", json.toString())
        assertEquals(edited.animations, again.animations)

        val psychOut = CharacterExport.build(edited, ExportTarget.PSYCH, loaded.image, loaded.xml).files
        assertTrue(psychOut.map { it.relativePath }.toString(), psychOut.any { it.relativePath == "characters/dude.json" })
        assertTrue(psychOut.any { it.relativePath == "images/characters/Dude.xml" })
        val codename = CharacterExport.build(edited, ExportTarget.CODENAME, loaded.image, loaded.xml).files
        assertTrue(codename.map { it.relativePath }.toString(), codename.any { it.relativePath == "data/characters/dude.xml" })
        assertTrue(codename.any { it.relativePath == "images/characters/Dude.png" })

        val back = CharacterLoader.load(codename.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) })
        assertEquals("Codename Engine", back.source)
        assertEquals(listOf("idle", "singLEFT"), back.document.animations.map { it.name })
    }

    @Test
    fun spritesheetOnlyGuessesAnimations() {
        val loaded = CharacterLoader.load(listOf("Dude.xml" to xml.toByteArray(), "Dude.png" to png))
        assertEquals("Spritesheet", loaded.source)
        assertEquals(setOf("idle", "singLEFT", "singDOWN"), loaded.document.animations.map { it.name }.toSet())
        assertEquals("characters/Dude", loaded.document.assetPath)
    }
}
