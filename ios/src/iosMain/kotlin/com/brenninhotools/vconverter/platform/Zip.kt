package com.brenninhotools.vconverter.platform

object Crc32 {
    private val table = IntArray(256).also { t ->
        for (n in 0 until 256) {
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            t[n] = c
        }
    }

    fun update(crc: Int, data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var c = crc.inv()
        for (i in offset until offset + length) c = table[(c xor data[i].toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    fun of(data: ByteArray): Int = update(0, data)
}

class ZipFormatException(message: String) : IOException(message)

object Inflate {
    private val lengthBase = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val lengthExtra = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val distBase = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val distExtra = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val order = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private class Huffman(val count: IntArray, val symbol: IntArray)

    private class State(val input: ByteArray, var pos: Int, val end: Int, capacity: Int) {
        var out = ByteArray(maxOf(capacity, 1024))
        var size = 0
        var bitBuffer = 0
        var bitCount = 0

        fun bits(need: Int): Int {
            var value = bitBuffer
            while (bitCount < need) {
                if (pos >= end) throw ZipFormatException("The compressed data ends early.")
                value = value or ((input[pos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            bitBuffer = value ushr need
            bitCount -= need
            return value and ((1 shl need) - 1)
        }

        fun ensure(extra: Int) {
            if (size + extra <= out.size) return
            var n = out.size * 2
            while (n < size + extra) n *= 2
            out = out.copyOf(n)
        }
    }

    private fun build(lengths: IntArray, offset: Int, n: Int): Huffman {
        val count = IntArray(16)
        val symbol = IntArray(n)
        for (s in 0 until n) count[lengths[offset + s]]++
        val offs = IntArray(16)
        for (len in 1 until 15) offs[len + 1] = offs[len] + count[len]
        for (s in 0 until n) if (lengths[offset + s] != 0) symbol[offs[lengths[offset + s]]++] = s
        return Huffman(count, symbol)
    }

    private fun decode(s: State, h: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..15) {
            code = code or s.bits(1)
            val count = h.count[len]
            if (code - count < first) return h.symbol[index + (code - first)]
            index += count
            first += count
            first = first shl 1
            code = code shl 1
        }
        throw ZipFormatException("Bad compressed data.")
    }

    private fun codes(s: State, lencode: Huffman, distcode: Huffman) {
        while (true) {
            var symbol = decode(s, lencode)
            if (symbol < 256) {
                s.ensure(1)
                s.out[s.size++] = symbol.toByte()
            } else if (symbol == 256) {
                return
            } else {
                symbol -= 257
                if (symbol >= 29) throw ZipFormatException("Bad length code.")
                val len = lengthBase[symbol] + s.bits(lengthExtra[symbol])
                val ds = decode(s, distcode)
                if (ds >= 30) throw ZipFormatException("Bad distance code.")
                val dist = distBase[ds] + s.bits(distExtra[ds])
                if (dist > s.size) throw ZipFormatException("Distance too far back.")
                s.ensure(len)
                var from = s.size - dist
                repeat(len) { s.out[s.size++] = s.out[from++] }
            }
        }
    }

    private val fixed: Pair<Huffman, Huffman> by lazy {
        val lengths = IntArray(288 + 30)
        for (i in 0 until 144) lengths[i] = 8
        for (i in 144 until 256) lengths[i] = 9
        for (i in 256 until 280) lengths[i] = 7
        for (i in 280 until 288) lengths[i] = 8
        for (i in 288 until 318) lengths[i] = 5
        build(lengths, 0, 288) to build(lengths, 288, 30)
    }

    private fun dynamic(s: State) {
        val nlen = s.bits(5) + 257
        val ndist = s.bits(5) + 1
        val ncode = s.bits(4) + 4
        if (nlen > 286 || ndist > 30) throw ZipFormatException("Bad code counts.")
        val lengths = IntArray(320)
        for (i in 0 until ncode) lengths[order[i]] = s.bits(3)
        val lencode = build(lengths, 0, 19)
        var index = 0
        val all = IntArray(320)
        while (index < nlen + ndist) {
            var symbol = decode(s, lencode)
            if (symbol < 16) {
                all[index++] = symbol
            } else {
                var len = 0
                when (symbol) {
                    16 -> {
                        if (index == 0) throw ZipFormatException("Repeat with no first length.")
                        len = all[index - 1]
                        symbol = 3 + s.bits(2)
                    }
                    17 -> symbol = 3 + s.bits(3)
                    else -> symbol = 11 + s.bits(7)
                }
                if (index + symbol > nlen + ndist) throw ZipFormatException("Too many lengths.")
                repeat(symbol) { all[index++] = len }
            }
        }
        val litlen = build(all, 0, nlen)
        val dist = build(all, nlen, ndist)
        codes(s, litlen, dist)
    }

    fun inflate(input: ByteArray, offset: Int = 0, length: Int = input.size - offset, expected: Int = 0): ByteArray {
        val s = State(input, offset, offset + length, if (expected > 0) expected else length * 3)
        do {
            val last = s.bits(1)
            when (s.bits(2)) {
                0 -> {
                    s.bitBuffer = 0
                    s.bitCount = 0
                    if (s.pos + 4 > s.end) throw ZipFormatException("The compressed data ends early.")
                    val len = (s.input[s.pos].toInt() and 0xFF) or ((s.input[s.pos + 1].toInt() and 0xFF) shl 8)
                    s.pos += 4
                    if (s.pos + len > s.end) throw ZipFormatException("The compressed data ends early.")
                    s.ensure(len)
                    s.input.copyInto(s.out, s.size, s.pos, s.pos + len)
                    s.size += len
                    s.pos += len
                }
                1 -> codes(s, fixed.first, fixed.second)
                2 -> dynamic(s)
                else -> throw ZipFormatException("Bad block type.")
            }
        } while (last == 0)
        return if (s.size == s.out.size) s.out else s.out.copyOf(s.size)
    }
}

interface RandomSource : AutoCloseable {
    val size: Long
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int)
}

class ZipEntryInfo(val name: String, val method: Int, val crc: Int, val compressedSize: Long, val size: Long, val headerOffset: Long, val isDirectory: Boolean)

class ZipReader(private val source: RandomSource) : AutoCloseable {
    val entries: List<ZipEntryInfo>

    private fun bytes(position: Long, length: Int): ByteArray = ByteArray(length).also { source.read(position, it, 0, length) }

    private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long = (u16(b, i).toLong()) or (u16(b, i + 2).toLong() shl 16)

    private fun u64(b: ByteArray, i: Int): Long = u32(b, i) or (u32(b, i + 4) shl 32)

    init {
        val size = source.size
        if (size < 22) throw ZipFormatException("This is not a ZIP file.")
        val tailLength = minOf(size, 65557L).toInt()
        val tail = bytes(size - tailLength, tailLength)
        var eocd = -1
        for (i in tailLength - 22 downTo 0) {
            if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4B.toByte() && tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()) {
                eocd = i
                break
            }
        }
        if (eocd < 0) throw ZipFormatException("The ZIP file is damaged (no central directory).")
        var count = u16(tail, eocd + 10).toLong()
        var cdSize = u32(tail, eocd + 12)
        var cdOffset = u32(tail, eocd + 16)
        val locator = eocd - 20
        if (locator >= 0 && u32(tail, locator) == 0x07064b50L) {
            val zip64 = u64(tail, locator + 8)
            val record = bytes(zip64, 56)
            if (u32(record, 0) == 0x06064b50L) {
                count = u64(record, 32)
                cdSize = u64(record, 40)
                cdOffset = u64(record, 48)
            }
        }
        val cd = bytes(cdOffset, cdSize.toInt())
        val list = ArrayList<ZipEntryInfo>()
        var p = 0
        var n = 0L
        while (n < count && p + 46 <= cd.size && u32(cd, p) == 0x02014b50L) {
            val flags = u16(cd, p + 8)
            val method = u16(cd, p + 10)
            val crc = u32(cd, p + 16).toInt()
            var compressed = u32(cd, p + 20)
            var uncompressed = u32(cd, p + 24)
            val nameLength = u16(cd, p + 28)
            val extraLength = u16(cd, p + 30)
            val commentLength = u16(cd, p + 32)
            var offset = u32(cd, p + 42)
            val nameBytes = cd.copyOfRange(p + 46, p + 46 + nameLength)
            val name = if (flags and 0x800 != 0) nameBytes.decodeToString() else decodeLegacy(nameBytes)
            var e = p + 46 + nameLength
            val extraEnd = e + extraLength
            while (e + 4 <= extraEnd) {
                val id = u16(cd, e)
                val len = u16(cd, e + 2)
                if (id == 0x0001) {
                    var q = e + 4
                    if (uncompressed == 0xFFFFFFFFL && q + 8 <= e + 4 + len) { uncompressed = u64(cd, q); q += 8 }
                    if (compressed == 0xFFFFFFFFL && q + 8 <= e + 4 + len) { compressed = u64(cd, q); q += 8 }
                    if (offset == 0xFFFFFFFFL && q + 8 <= e + 4 + len) { offset = u64(cd, q) }
                }
                e += 4 + len
            }
            list += ZipEntryInfo(name, method, crc, compressed, uncompressed, offset, name.endsWith("/"))
            p = extraEnd + commentLength
            n++
        }
        entries = list
    }

    private fun decodeLegacy(bytes: ByteArray): String {
        val text = bytes.decodeToString()
        if (!text.contains('�')) return text
        return buildString { bytes.forEach { append(cp437[it.toInt() and 0xFF]) } }
    }

    fun read(entry: ZipEntryInfo): ByteArray {
        val local = bytes(entry.headerOffset, 30)
        if (u32(local, 0) != 0x04034b50L) throw ZipFormatException("The ZIP file is damaged (${entry.name}).")
        val start = entry.headerOffset + 30 + u16(local, 26) + u16(local, 28)
        if (entry.compressedSize > Int.MAX_VALUE) throw ZipFormatException("${entry.name} is too big.")
        val data = bytes(start, entry.compressedSize.toInt())
        return when (entry.method) {
            0 -> data
            8 -> Inflate.inflate(data, 0, data.size, entry.size.toInt())
            else -> throw ZipFormatException("${entry.name} uses an unsupported compression method (${entry.method}).")
        }
    }

    override fun close() = source.close()

    companion object {
        private val cp437 = (0 until 128).map { it.toChar() }.joinToString("") +
            "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "
    }
}

class ZipWriterCore(private val output: OutputStream) : AutoCloseable {
    private class Record(val name: ByteArray, val crc: Int, val size: Int, val compressed: Int, val method: Int, val offset: Long)

    private val records = ArrayList<Record>()
    private var position = 0L
    private var closed = false

    private fun u16(value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }

    private fun u32(value: Long) {
        u16((value and 0xFFFF).toInt())
        u16(((value ushr 16) and 0xFFFF).toInt())
    }

    fun add(name: String, data: ByteArray) {
        val nameBytes = name.encodeToByteArray()
        val crc = Crc32.of(data)
        val offset = position
        u32(0x04034b50L)
        u16(10)
        u16(0x800)
        u16(0)
        u16(0)
        u16(0x21)
        u32(crc.toLong() and 0xFFFFFFFFL)
        u32(data.size.toLong())
        u32(data.size.toLong())
        u16(nameBytes.size)
        u16(0)
        output.write(nameBytes)
        output.write(data)
        position += 30 + nameBytes.size + data.size
        records += Record(nameBytes, crc, data.size, data.size, 0, offset)
    }

    override fun close() {
        if (closed) return
        closed = true
        val start = position
        records.forEach { r ->
            u32(0x02014b50L)
            u16(20)
            u16(10)
            u16(0x800)
            u16(r.method)
            u16(0)
            u16(0x21)
            u32(r.crc.toLong() and 0xFFFFFFFFL)
            u32(r.compressed.toLong())
            u32(r.size.toLong())
            u16(r.name.size)
            u16(0)
            u16(0)
            u16(0)
            u16(0)
            u32(0)
            u32(r.offset)
            output.write(r.name)
            position += 46 + r.name.size
        }
        val size = position - start
        u32(0x06054b50L)
        u16(0)
        u16(0)
        u16(minOf(records.size, 0xFFFF))
        u16(minOf(records.size, 0xFFFF))
        u32(size)
        u32(start)
        u16(0)
        output.flush()
        output.close()
    }
}
