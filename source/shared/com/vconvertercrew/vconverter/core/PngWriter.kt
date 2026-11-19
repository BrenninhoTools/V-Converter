package com.vconvertercrew.vconverter.core

import kotlin.math.abs

object PngWriter {
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    private val crcTable = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
        c
    }

    private fun crc(bytes: ByteArray): Int {
        var c = -1
        for (b in bytes) c = crcTable[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun chunk(kind: String, body: ByteArray): ByteArray {
        val typed = kind.encodeToByteArray() + body
        return int(body.size) + typed + int(crc(typed))
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    fun filtered(image: RgbaImage): ByteArray {
        val stride = image.width * 4
        val raw = ByteArray((stride + 1) * image.height)
        val candidate = Array(5) { ByteArray(stride) }
        val px = image.pixels
        for (y in 0 until image.height) {
            val row = y * stride
            val up = row - stride
            var best = 0
            var bestScore = Long.MAX_VALUE
            for (filter in 0..4) {
                val target = candidate[filter]
                var score = 0L
                for (x in 0 until stride) {
                    val v = px[row + x].toInt() and 0xFF
                    val a = if (x >= 4) px[row + x - 4].toInt() and 0xFF else 0
                    val b = if (y > 0) px[up + x].toInt() and 0xFF else 0
                    val c = if (x >= 4 && y > 0) px[up + x - 4].toInt() and 0xFF else 0
                    val out = when (filter) {
                        1 -> v - a
                        2 -> v - b
                        3 -> v - (a + b) / 2
                        4 -> v - paeth(a, b, c)
                        else -> v
                    } and 0xFF
                    target[x] = out.toByte()
                    score += if (out < 128) out else 256 - out
                }
                if (score < bestScore) {
                    bestScore = score
                    best = filter
                }
            }
            val at = y * (stride + 1)
            raw[at] = best.toByte()
            candidate[best].copyInto(raw, at + 1)
        }
        return raw
    }

    fun encode(image: RgbaImage): ByteArray {
        val header = int(image.width) + int(image.height) + byteArrayOf(8, 6, 0, 0, 0)
        return signature + chunk("IHDR", header) + chunk("IDAT", ZlibDeflate.compress(filtered(image))) + chunk("IEND", ByteArray(0))
    }
}
