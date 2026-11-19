package com.vconvertercrew.vconverter.core

object ZlibDeflate {
    private const val WINDOW = 32768
    private const val HASH_BITS = 15
    private const val HASH_SIZE = 1 shl HASH_BITS
    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 258
    private const val MAX_CHAIN = 48

    private val lengthBase = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val lengthExtra = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val distBase = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val distExtra = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

    private val lengthCode = IntArray(MAX_MATCH + 1).also { table ->
        var code = 0
        for (length in MIN_MATCH..MAX_MATCH) {
            while (code < lengthBase.size - 1 && lengthBase[code + 1] <= length) code++
            table[length] = code
        }
    }

    private class Bits(capacity: Int) {
        var buffer = ByteArray(maxOf(64, capacity))
        var size = 0
        private var bits = 0L
        private var count = 0

        fun put(value: Int, length: Int) {
            bits = bits or ((value.toLong() and ((1L shl length) - 1)) shl count)
            count += length
            while (count >= 8) {
                byte((bits and 0xFF).toInt())
                bits = bits ushr 8
                count -= 8
            }
        }

        fun reversed(code: Int, length: Int) {
            var r = 0
            var c = code
            repeat(length) {
                r = (r shl 1) or (c and 1)
                c = c ushr 1
            }
            put(r, length)
        }

        fun byte(value: Int) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = value.toByte()
        }

        fun flush() {
            if (count > 0) put(0, 8 - count)
        }
    }

    private fun literal(out: Bits, symbol: Int) {
        when {
            symbol < 144 -> out.reversed(0x30 + symbol, 8)
            symbol < 256 -> out.reversed(0x190 + symbol - 144, 9)
            symbol < 280 -> out.reversed(symbol - 256, 7)
            else -> out.reversed(0xC0 + symbol - 280, 8)
        }
    }

    private fun distanceCode(distance: Int): Int {
        var code = 0
        while (code < distBase.size - 1 && distBase[code + 1] <= distance) code++
        return code
    }

    fun compress(data: ByteArray): ByteArray {
        val out = Bits(data.size / 3 + 64)
        out.byte(0x78)
        out.byte(0x9C)
        out.put(1, 1)
        out.put(1, 2)
        val head = IntArray(HASH_SIZE) { -1 }
        val prev = IntArray(WINDOW)
        val n = data.size
        fun hash(i: Int): Int {
            val v = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
            return (v * -0x61c88647 ushr (32 - HASH_BITS))
        }
        fun insert(i: Int) {
            if (i + MIN_MATCH > n) return
            val h = hash(i)
            prev[i and (WINDOW - 1)] = head[h]
            head[h] = i
        }
        var i = 0
        while (i < n) {
            var bestLength = 0
            var bestDistance = 0
            if (i + MIN_MATCH <= n) {
                var candidate = head[hash(i)]
                var chain = MAX_CHAIN
                val limit = minOf(MAX_MATCH, n - i)
                while (candidate >= 0 && i - candidate <= WINDOW && chain-- > 0) {
                    if (data[candidate + bestLength] == data[i + bestLength] || bestLength == 0) {
                        var length = 0
                        while (length < limit && data[candidate + length] == data[i + length]) length++
                        if (length > bestLength) {
                            bestLength = length
                            bestDistance = i - candidate
                            if (length == limit) break
                        }
                    }
                    val next = prev[candidate and (WINDOW - 1)]
                    if (next >= candidate) break
                    candidate = next
                }
            }
            if (bestLength >= MIN_MATCH) {
                val code = lengthCode[bestLength]
                literal(out, 257 + code)
                if (lengthExtra[code] > 0) out.put(bestLength - lengthBase[code], lengthExtra[code])
                val dc = distanceCode(bestDistance)
                out.reversed(dc, 5)
                if (distExtra[dc] > 0) out.put(bestDistance - distBase[dc], distExtra[dc])
                val end = i + bestLength
                while (i < end) insert(i++)
            } else {
                literal(out, data[i].toInt() and 0xFF)
                insert(i++)
            }
        }
        literal(out, 256)
        out.flush()
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        val adler = (b shl 16) or a
        out.byte(adler ushr 24)
        out.byte(adler ushr 16)
        out.byte(adler ushr 8)
        out.byte(adler)
        return out.buffer.copyOf(out.size)
    }
}
