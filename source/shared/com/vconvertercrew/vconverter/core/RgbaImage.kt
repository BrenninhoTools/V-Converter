package com.vconvertercrew.vconverter.core

class RgbaImage(val width: Int, val height: Int, val pixels: ByteArray) {
    fun alpha(x: Int, y: Int): Int = pixels[(y * width + x) * 4 + 3].toInt() and 0xFF

    fun argb(x: Int, y: Int): Int {
        val o = (y * width + x) * 4
        return ((pixels[o + 3].toInt() and 0xFF) shl 24) or ((pixels[o].toInt() and 0xFF) shl 16) or
            ((pixels[o + 1].toInt() and 0xFF) shl 8) or (pixels[o + 2].toInt() and 0xFF)
    }

    fun crop(left: Int, top: Int, w: Int, h: Int): RgbaImage {
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) pixels.copyInto(out, y * w * 4, ((top + y) * width + left) * 4, ((top + y) * width + left + w) * 4)
        return RgbaImage(w, h, out)
    }

    fun blit(source: RgbaImage, left: Int, top: Int, w: Int, h: Int, x: Int, y: Int) {
        for (row in 0 until h) {
            val from = ((top + row) * source.width + left) * 4
            source.pixels.copyInto(pixels, ((y + row) * width + x) * 4, from, from + w * 4)
        }
    }

    companion object {
        fun blank(width: Int, height: Int) = RgbaImage(width, height, ByteArray(width * height * 4))

        fun fromArgb(width: Int, height: Int, argb: IntArray): RgbaImage {
            val out = ByteArray(width * height * 4)
            for (i in 0 until width * height) {
                val c = argb[i]
                val o = i * 4
                out[o] = (c ushr 16).toByte()
                out[o + 1] = (c ushr 8).toByte()
                out[o + 2] = c.toByte()
                out[o + 3] = (c ushr 24).toByte()
            }
            return RgbaImage(width, height, out)
        }
    }
}
