package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.core.PngWriter
import com.vconvertercrew.vconverter.core.RgbaImage
import com.vconvertercrew.vconverter.core.SparrowAtlas
import com.vconvertercrew.vconverter.core.ZlibDeflate
import com.vconvertercrew.vconverter.fpsplus.FpsIcon
import com.vconvertercrew.vconverter.generator.PackOptions
import com.vconvertercrew.vconverter.generator.PixelBox
import com.vconvertercrew.vconverter.generator.SheetFrame
import com.vconvertercrew.vconverter.generator.SheetSource
import com.vconvertercrew.vconverter.generator.SpriteSheetGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.Inflater
import kotlin.random.Random

class SpriteSheetGeneratorTest {
    private fun inflate(data: ByteArray, size: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        var guard = 0
        while (!inflater.finished() && guard++ < 100_000) {
            val n = inflater.inflate(buffer)
            out.write(buffer, 0, n)
        }
        assertTrue(inflater.finished())
        assertEquals(size, out.size())
        return out.toByteArray()
    }

    @Test
    fun deflateRoundTrips() {
        val random = Random(4)
        val samples = listOf(
            ByteArray(0),
            "a".encodeToByteArray(),
            ByteArray(100_000),
            ByteArray(70_000) { random.nextInt(256).toByte() },
            ByteArray(200_000) { (it % 251 xor (it / 999)).toByte() },
            ("Friday Night Funkin' ".repeat(5000)).encodeToByteArray()
        )
        samples.forEach { data ->
            val packed = ZlibDeflate.compress(data)
            assertTrue(data.contentEquals(inflate(packed, data.size)))
        }
        assertTrue(ZlibDeflate.compress(ByteArray(100_000)).size < 2000)
    }

    private fun sprite(width: Int, height: Int, box: PixelBox, color: Int): RgbaImage {
        val image = RgbaImage.blank(width, height)
        for (y in box.y until box.bottom) for (x in box.x until box.right) {
            val o = (y * width + x) * 4
            image.pixels[o] = (color ushr 16).toByte()
            image.pixels[o + 1] = (color ushr 8).toByte()
            image.pixels[o + 2] = color.toByte()
            image.pixels[o + 3] = -1
        }
        return image
    }

    @Test
    fun namesFramesLikeSparrow() {
        assertEquals("idle" to 3, SpriteSheetGenerator.frameName("idle0003.png"))
        assertEquals("BF idle dance" to 12, SpriteSheetGenerator.frameName("chars/BF idle dance_12.png"))
        assertEquals("sing LEFT" to 1, SpriteSheetGenerator.frameName("sing LEFT-1.png"))
        assertEquals("pose" to null, SpriteSheetGenerator.frameName("pose.png"))
    }

    @Test
    fun packsTrimsAndReusesFrames() {
        var id = 0
        fun frame(box: PixelBox, color: Int) = SheetFrame(SheetSource(id++, "f", sprite(100, 80, box, color)), PixelBox(0, 0, 100, 80))
        val files = listOf(
            "idle0002.png" to frame(PixelBox(20, 10, 30, 40), 0xFF0000),
            "idle0001.png" to frame(PixelBox(10, 10, 30, 40), 0x00FF00),
            "idle0003.png" to frame(PixelBox(10, 10, 30, 40), 0x00FF00),
            "sing LEFT0000.png" to frame(PixelBox(0, 0, 100, 80), 0x0000FF)
        )
        val animations = SpriteSheetGenerator.group(files)
        assertEquals(listOf("idle", "sing LEFT"), animations.map { it.name })
        val result = SpriteSheetGenerator.pack(animations, PackOptions("bf.png", padding = 2))
        assertEquals(1, result.reused)
        val frames = result.frames
        assertEquals(listOf("idle0000", "idle0001", "idle0002", "sing LEFT0000"), frames.map { it.name })
        assertEquals(-10, frames[0].frameX)
        assertEquals(-20, frames[1].frameX)
        assertEquals(frames[0].x to frames[0].y, frames[2].x to frames[2].y)
        assertEquals(100, frames[0].frameWidth)
        assertTrue(!frames[3].trimmed)
        val sheet = result.image!!
        frames.forEach { f -> assertTrue(f.x + f.width <= sheet.width && f.y + f.height <= sheet.height) }
        val placed = frames.distinctBy { it.x to it.y }.map { PixelBox(it.x, it.y, it.width, it.height) }
        for (i in placed.indices) for (j in i + 1 until placed.size) assertTrue(!placed[i].intersects(placed[j]))
        assertEquals(0xFF00FF00.toInt(), sheet.argb(frames[0].x, frames[0].y))
        val parsed = SparrowAtlas.parse(result.xml)
        assertEquals(4, parsed.frames.size)
        assertEquals(100.0, parsed.frames[0].sourceWidth, 0.0)
        val png = result.png()!!
        val decoded = FpsIcon.decode(png)!!
        assertEquals(sheet.width, decoded.width)
        assertTrue(sheet.pixels.contentEquals(decoded.pixels))
        assertTrue(sheet.pixels.contentEquals(FpsIcon.decode(PngWriter.encode(sheet))!!.pixels))
    }

    @Test
    fun findsFramesInASheet() {
        val sheet = RgbaImage.blank(200, 100)
        val boxes = listOf(PixelBox(110, 5, 30, 40), PixelBox(5, 5, 40, 45), PixelBox(60, 60, 20, 30), PixelBox(5, 55, 30, 40))
        boxes.forEach { box -> sheet.blit(sprite(200, 100, box, 0xFFFFFF), box.x, box.y, box.width, box.height, box.x, box.y) }
        val found = SpriteSheetGenerator.islands(sheet)
        assertEquals(listOf(boxes[1], boxes[0], boxes[3], boxes[2]), found)
        val grid = SpriteSheetGenerator.grid(sheet, 4, 2)
        assertEquals(4, grid.size)
        val source = SheetSource(0, "sheet", sheet)
        val aligned = SpriteSheetGenerator.aligned(source, found)
        assertTrue(aligned.all { it.canvasWidth == 40 && it.canvasHeight == 45 })
        val described = SpriteSheetGenerator.describe(listOf(com.vconvertercrew.vconverter.generator.SheetAnimation("run", aligned)), "sheet.png")
        assertEquals(5, described.frames[0].x)
        assertEquals(0, described.frames[0].frameX)
        assertEquals(-5, described.frames[1].frameX)
        assertEquals(-5, described.frames[1].frameY)
    }
}
