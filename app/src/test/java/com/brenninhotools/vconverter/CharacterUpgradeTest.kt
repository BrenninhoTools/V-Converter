package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.convert.PickedText
import com.brenninhotools.vconverter.convert.PsychCharactersConverter
import com.brenninhotools.vconverter.convert.ReverseCharactersConverter
import com.brenninhotools.vconverter.io.ImportedFile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterUpgradeTest {
    private val mainXml = """
        <TextureAtlas imagePath="dude.png">
          <SubTexture name="Dude idle0000" x="0" y="0" width="400" height="400" frameX="-5" frameY="-2" frameWidth="410" frameHeight="410"/>
          <SubTexture name="Dude left0000" x="0" y="0" width="420" height="390"/>
          <SubTexture name="Dude down0000" x="0" y="0" width="380" height="380"/>
        </TextureAtlas>
    """.trimIndent()

    private val deadXml = """
        <TextureAtlas imagePath="dude-dead.png">
          <SubTexture name="Dude dies0000" x="0" y="0" width="500" height="520"/>
          <SubTexture name="Dude dead loop0000" x="0" y="0" width="500" height="520"/>
          <SubTexture name="Dude retry0000" x="0" y="0" width="500" height="520"/>
        </TextureAtlas>
    """.trimIndent()

    private val vslice = """
        {"version":"1.0.0","name":"Dude","renderType":"multisparrow","assetPath":"shared:characters/dude","scale":1.2,
         "offsets":[10,-6],"cameraOffsets":[0,-50],"singTime":8,"danceEvery":2,"flipX":true,"startingAnimation":"idle",
         "healthIcon":{"id":"dude"},
         "animations":[
           {"name":"idle","prefix":"Dude idle","offsets":[0,0]},
           {"name":"singLEFT","prefix":"Dude left","offsets":[12,-4]},
           {"name":"singDOWN-hold","prefix":"Dude down","offsets":[-3,8],"looped":true},
           {"name":"firstDeath","prefix":"Dude dies","assetPath":"shared:characters/dude-dead","offsets":[40,30]},
           {"name":"deathLoop","prefix":"Dude dead loop","assetPath":"shared:characters/dude-dead","offsets":[40,30],"looped":true},
           {"name":"deathConfirm","prefix":"Dude retry","assetPath":"shared:characters/dude-dead","offsets":[44,36]}
         ]}
    """.trimIndent()

    private fun item(c: com.brenninhotools.vconverter.convert.FileConverter, path: String, text: String): PickedText =
        c.classify(ImportedFile(path.substringAfterLast('/'), path, true) { text.byteInputStream() }, text)

    private fun anims(o: JSONObject, key: String) = (0 until o.getJSONArray("animations").length()).map { o.getJSONArray("animations").getJSONObject(it) }.associateBy { it.getString(key) }

    @Test
    fun deathAnimationsSplitAndMergeBack() {
        val rc = ReverseCharactersConverter()
        val out = rc.build(listOf(item(rc, "data/characters/dude.json", vslice), item(rc, "images/characters/dude.xml", mainXml), item(rc, "images/characters/dude-dead.xml", deadXml)))
        val paths = out.files.map { it.relativePath }
        assertTrue(paths.toString(), paths.containsAll(listOf("characters/dude.json", "characters/dude-dead.json", "characters/dude.lua")))
        val main = JSONObject(out.files.first { it.relativePath == "characters/dude.json" }.content)
        val dead = JSONObject(out.files.first { it.relativePath == "characters/dude-dead.json" }.content)
        assertEquals("characters/dude", main.getString("image"))
        assertEquals("characters/dude-dead", dead.getString("image"))
        assertFalse(anims(main, "anim").containsKey("firstDeath"))
        assertTrue(anims(main, "anim").containsKey("singDOWN-loop"))
        assertEquals(setOf("firstDeath", "deathLoop", "deathConfirm"), anims(dead, "anim").keys)
        assertTrue(out.files.first { it.relativePath == "characters/dude.lua" }.content.contains("'characterName', 'dude-dead'"))
        assertEquals("Exact", out.report.stats.first { it.label == "dude" }.value)

        val pc = PsychCharactersConverter()
        val back = pc.build(out.files.filter { it.relativePath.endsWith(".json") }.map { item(pc, it.relativePath, it.content) } +
            listOf(item(pc, "images/characters/dude.xml", mainXml), item(pc, "images/characters/dude-dead.xml", deadXml)))
        assertFalse(back.files.any { it.name == "dude-dead.json" })
        val again = JSONObject(back.files.first { it.name == "dude.json" }.content)
        assertEquals("multisparrow", again.getString("renderType"))
        val original = anims(JSONObject(vslice), "name")
        val converted = anims(again, "name")
        assertEquals(original.keys, converted.keys)
        original.forEach { (name, anim) ->
            val ox = anim.getJSONArray("offsets").getDouble(0) - 10.0
            val oy = anim.getJSONArray("offsets").getDouble(1) + 6.0
            val c = converted.getValue(name).getJSONArray("offsets")
            assertEquals(name, ox, c.getDouble(0), 0.01)
            assertEquals(name, oy, c.getDouble(1), 0.01)
        }
        assertEquals("characters/dude-dead", converted.getValue("firstDeath").getString("assetPath"))
    }
}
