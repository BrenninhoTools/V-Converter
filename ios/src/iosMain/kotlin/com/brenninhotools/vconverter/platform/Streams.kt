package com.brenninhotools.vconverter.platform

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

abstract class InputStream : AutoCloseable {
    abstract fun read(): Int

    open fun read(buffer: ByteArray): Int = read(buffer, 0, buffer.size)

    open fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var count = 0
        while (count < length) {
            val b = read()
            if (b < 0) return if (count == 0) -1 else count
            buffer[offset + count] = b.toByte()
            count++
        }
        return count
    }

    open fun skip(count: Long): Long {
        var left = count
        val buffer = ByteArray(8192)
        while (left > 0) {
            val n = read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) break
            left -= n
        }
        return count - left
    }

    open fun available(): Int = 0

    override fun close() {}

    fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        copyTo(out)
        return out.toByteArray()
    }

    fun copyTo(out: OutputStream, bufferSize: Int = 8192): Long {
        val buffer = ByteArray(bufferSize)
        var total = 0L
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return total
    }

    fun buffered(size: Int = 8192): InputStream = this

    fun bufferedReader(): LineReader = LineReader(this)
}

class LineReader(private val input: InputStream) : AutoCloseable {
    fun readText(): String = input.readBytes().decodeToString()

    fun readLines(): List<String> = readText().lines()

    override fun close() = input.close()
}

abstract class OutputStream : AutoCloseable {
    abstract fun write(b: Int)

    open fun write(buffer: ByteArray) = write(buffer, 0, buffer.size)

    open fun write(buffer: ByteArray, offset: Int, length: Int) {
        for (i in offset until offset + length) write(buffer[i].toInt())
    }

    open fun flush() {}

    override fun close() {}

    fun buffered(size: Int = 8192): OutputStream = this
}

open class ByteArrayInputStream(private val data: ByteArray, offset: Int = 0, length: Int = data.size - offset) : InputStream() {
    private var position = offset
    private val end = minOf(data.size, offset + length)

    override fun read(): Int = if (position < end) data[position++].toInt() and 0xFF else -1

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= end) return -1
        val n = minOf(length, end - position)
        data.copyInto(buffer, offset, position, position + n)
        position += n
        return n
    }

    override fun skip(count: Long): Long {
        val n = minOf(count, (end - position).toLong()).coerceAtLeast(0)
        position += n.toInt()
        return n
    }

    override fun available(): Int = end - position
}

open class ByteArrayOutputStream(capacity: Int = 256) : OutputStream() {
    private var data = ByteArray(maxOf(capacity, 16))
    private var count = 0

    private fun ensure(extra: Int) {
        if (count + extra <= data.size) return
        var size = data.size * 2
        while (size < count + extra) size *= 2
        data = data.copyOf(size)
    }

    override fun write(b: Int) {
        ensure(1)
        data[count++] = b.toByte()
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensure(length)
        buffer.copyInto(data, count, offset, offset + length)
        count += length
    }

    fun size(): Int = count

    fun reset() {
        count = 0
    }

    fun toByteArray(): ByteArray = data.copyOf(count)

    override fun toString(): String = toByteArray().decodeToString()
}

fun ByteArray.inputStream(): InputStream = ByteArrayInputStream(this)

fun String.byteInputStream(): InputStream = ByteArrayInputStream(encodeToByteArray())
