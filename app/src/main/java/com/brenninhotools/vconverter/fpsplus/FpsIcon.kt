package com.brenninhotools.vconverter.fpsplus

import com.brenninhotools.vconverter.platform.Platform

class RgbaImage(val width: Int, val height: Int, val pixels: ByteArray)

object FpsIcon {
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    private fun u32(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    fun size(png: ByteArray): Pair<Int, Int>? {
        if (png.size < 24 || !png.copyOfRange(0, 8).contentEquals(signature)) return null
        return u32(png, 16) to u32(png, 20)
    }

    fun decode(png: ByteArray): RgbaImage? {
        if (png.size < 33 || !png.copyOfRange(0, 8).contentEquals(signature)) return null
        var i = 8
        var width = 0
        var height = 0
        var depth = 0
        var type = 0
        var interlace = 0
        var palette = ByteArray(0)
        var alpha = ByteArray(0)
        val data = mutableListOf<ByteArray>()
        while (i + 8 <= png.size) {
            val length = u32(png, i)
            val kind = png.copyOfRange(i + 4, i + 8).decodeToString()
            val body = png.copyOfRange(i + 8, minOf(png.size, i + 8 + length))
            when (kind) {
                "IHDR" -> {
                    width = u32(body, 0)
                    height = u32(body, 4)
                    depth = body[8].toInt() and 0xFF
                    type = body[9].toInt() and 0xFF
                    interlace = body[12].toInt() and 0xFF
                }
                "PLTE" -> palette = body
                "tRNS" -> alpha = body
                "IDAT" -> data += body
                "IEND" -> break
            }
            i += 12 + length
        }
        if (depth != 8 || interlace != 0 || width <= 0 || height <= 0) return null
        val channels = when (type) {
            0 -> 1
            2 -> 3
            3 -> 1
            4 -> 2
            6 -> 4
            else -> return null
        }
        val joined = ByteArray(data.sumOf { it.size }).also { out -> var o = 0; data.forEach { it.copyInto(out, o); o += it.size } }
        val raw = runCatching { Platform.inflate(joined) }.getOrNull() ?: return null
        val stride = width * channels
        if (raw.size < (stride + 1) * height) return null
        val rows = ByteArray(stride * height)
        val prev = ByteArray(stride)
        val cur = ByteArray(stride)
        for (y in 0 until height) {
            val base = y * (stride + 1)
            val filter = raw[base].toInt() and 0xFF
            for (x in 0 until stride) {
                val v = raw[base + 1 + x].toInt() and 0xFF
                val a = if (x >= channels) cur[x - channels].toInt() and 0xFF else 0
                val b = prev[x].toInt() and 0xFF
                val c = if (x >= channels) prev[x - channels].toInt() and 0xFF else 0
                val out = when (filter) {
                    1 -> v + a
                    2 -> v + b
                    3 -> v + (a + b) / 2
                    4 -> {
                        val p = a + b - c
                        val pa = kotlin.math.abs(p - a)
                        val pb = kotlin.math.abs(p - b)
                        val pc = kotlin.math.abs(p - c)
                        v + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                    }
                    else -> v
                }
                cur[x] = out.toByte()
            }
            cur.copyInto(rows, y * stride)
            cur.copyInto(prev)
        }
        val pixels = ByteArray(width * height * 4)
        for (p in 0 until width * height) {
            val o = p * 4
            when (type) {
                6 -> rows.copyInto(pixels, o, p * 4, p * 4 + 4)
                2 -> {
                    rows.copyInto(pixels, o, p * 3, p * 3 + 3)
                    pixels[o + 3] = -1
                }
                0 -> {
                    val g = rows[p]
                    pixels[o] = g; pixels[o + 1] = g; pixels[o + 2] = g; pixels[o + 3] = -1
                }
                4 -> {
                    val g = rows[p * 2]
                    pixels[o] = g; pixels[o + 1] = g; pixels[o + 2] = g; pixels[o + 3] = rows[p * 2 + 1]
                }
                3 -> {
                    val index = rows[p].toInt() and 0xFF
                    if (index * 3 + 2 < palette.size) palette.copyInto(pixels, o, index * 3, index * 3 + 3)
                    pixels[o + 3] = if (index < alpha.size) alpha[index] else -1
                }
            }
        }
        return RgbaImage(width, height, pixels)
    }

    private val crcTable = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
        c
    }

    private fun crc(bytes: ByteArray): Int {
        var c = -1
        bytes.forEach { c = crcTable[(c xor it.toInt()) and 0xFF] xor (c ushr 8) }
        return c.inv()
    }

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun chunk(kind: String, body: ByteArray): ByteArray {
        val typed = kind.encodeToByteArray() + body
        return int(body.size) + typed + int(crc(typed))
    }

    fun encode(image: RgbaImage): ByteArray {
        val stride = image.width * 4
        val raw = ByteArray((stride + 1) * image.height)
        for (y in 0 until image.height) image.pixels.copyInto(raw, y * (stride + 1) + 1, y * stride, y * stride + stride)
        val zlib = ArrayList<Byte>(raw.size + raw.size / 65535 * 5 + 16)
        zlib += 0x78.toByte()
        zlib += 0x01.toByte()
        var offset = 0
        do {
            val n = minOf(65535, raw.size - offset)
            val last = offset + n >= raw.size
            zlib += (if (last) 1 else 0).toByte()
            zlib += (n and 0xFF).toByte()
            zlib += (n ushr 8).toByte()
            zlib += (n.inv() and 0xFF).toByte()
            zlib += ((n.inv() ushr 8) and 0xFF).toByte()
            for (k in 0 until n) zlib += raw[offset + k]
            offset += n
        } while (offset < raw.size)
        var a = 1
        var b = 0
        raw.forEach { a = (a + (it.toInt() and 0xFF)) % 65521; b = (b + a) % 65521 }
        int((b shl 16) or a).forEach { zlib += it }
        val header = int(image.width) + int(image.height) + byteArrayOf(8, 6, 0, 0, 0)
        return signature + chunk("IHDR", header) + chunk("IDAT", zlib.toByteArray()) + chunk("IEND", ByteArray(0))
    }

    fun threeFrames(png: ByteArray): ByteArray? {
        val (w, h) = size(png) ?: return null
        if (h <= 0 || w >= h * 3 - 2) return null
        val image = decode(png) ?: return null
        val two = image.width >= image.height * 2 - 2
        val frame = if (two) image.width / 2 else image.width
        val losing = if (two) frame else 0
        val out = ByteArray(frame * 3 * image.height * 4)
        for (y in 0 until image.height) {
            val row = y * image.width * 4
            val target = y * frame * 3 * 4
            image.pixels.copyInto(out, target, row, row + frame * 4)
            image.pixels.copyInto(out, target + frame * 4, row + losing * 4, row + losing * 4 + frame * 4)
            image.pixels.copyInto(out, target + frame * 8, row, row + frame * 4)
        }
        return encode(RgbaImage(frame * 3, image.height, out))
    }
}
