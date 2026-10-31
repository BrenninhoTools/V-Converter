package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.StageExport
import com.brenninhotools.vconverter.editor.StageLoader
import com.brenninhotools.vconverter.editor.StageProp
import com.brenninhotools.vconverter.editor.StageSlot
import com.brenninhotools.vconverter.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageEditorTest {
    private val psych = """
        {"directory":"","defaultZoom":0.85,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100],
         "camera_boyfriend":[0,0],"camera_opponent":[5,5],"camera_girlfriend":[0,0],"camera_speed":1,
         "objects":[{"type":"sprite","name":"bg","image":"stages/test/bg","x":-500,"y":-300,"scale":[1,1],"scroll":[0.9,0.9],"alpha":1,"angle":0,"color":"FFFFFF","filters":3,"antialiasing":true},
                    {"type":"dad"},{"type":"gf"},{"type":"boyfriend"}]}
    """.trimIndent()

    private fun png(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(33)
        val header = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82)
        header.copyInto(bytes)
        bytes[16] = (width shr 24).toByte(); bytes[17] = (width shr 16).toByte(); bytes[18] = (width shr 8).toByte(); bytes[19] = width.toByte()
        bytes[20] = (height shr 24).toByte(); bytes[21] = (height shr 16).toByte(); bytes[22] = (height shr 8).toByte(); bytes[23] = height.toByte()
        return bytes
    }

    @Test
    fun psychStageRoundTripsEverywhere() {
        val image = png(1280, 720)
        val loaded = StageLoader.load(listOf("stages/test.json" to psych.toByteArray(), "images/stages/test/bg.png" to image))
        assertEquals("Psych Engine", loaded.source)
        val doc = loaded.document
        assertEquals("test", doc.id)
        assertEquals(0.85, doc.cameraZoom, 0.0)
        assertEquals(listOf("bg"), doc.props.map { it.name })
        assertEquals(0.9, doc.props[0].scrollX, 0.0)
        assertTrue(loaded.assets.containsKey("stages/test/bg"))

        val moved = doc.replaceProp(0, doc.props[0].copy(x = -400.0)).addProp(StageProp(doc.uniqueName("bg"), "#336699", 0.0, 0.0, 5, 200.0, 100.0))
            .withSpot(StageSlot.BOYFRIEND, doc.spot(StageSlot.BOYFRIEND).copy(x = 1100.0))
        assertEquals("bg-2", moved.props[1].name)
        val vslice = StageExport.build(moved, ExportTarget.VSLICE, loaded.assets).files
        val json = JSONObject(vslice.first { it.relativePath == "data/stages/test.json" }.content)
        assertEquals(-400.0, json.getJSONArray("props").getJSONObject(0).getJSONArray("position").getDouble(0), 0.0)
        assertEquals(1100.0, json.getJSONObject("characters").getJSONObject("bf").getJSONArray("position").getDouble(0), 0.0)
        assertTrue(vslice.any { it.relativePath == "images/stages/test/bg.png" && it.bytes != null })
        val again = StageDocument.fromVSlice("test", json.toString())
        assertEquals(moved.props.map { it.copy(raw = null) }, again.props.map { it.copy(raw = null) })
        assertEquals(moved.spots, again.spots)

        val psychOut = StageExport.build(moved, ExportTarget.PSYCH, loaded.assets).files
        assertTrue(psychOut.map { it.relativePath }.toString(), psychOut.any { it.relativePath == "stages/test.json" })
        val codename = StageExport.build(moved, ExportTarget.CODENAME, loaded.assets).files
        assertTrue(codename.map { it.relativePath }.toString(), codename.any { it.relativePath == "data/stages/test.xml" })
        assertTrue(codename.any { it.relativePath == "images/stages/test/bg.png" })

        val back = StageLoader.load(codename.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) })
        assertEquals("Codename Engine", back.source)
        assertTrue(back.document.props.any { it.assetPath.endsWith("bg") })
    }
}
