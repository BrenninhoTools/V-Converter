package com.brenninhotools.vconverter.editor

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object Hitsound {
    const val SAMPLE_RATE = 44100

    fun pcm(): ShortArray {
        val length = (SAMPLE_RATE * 0.045).toInt()
        return ShortArray(length) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 90.0)
            val tone = sin(2 * PI * 1760.0 * t) * 0.6 + sin(2 * PI * 3520.0 * t) * 0.25
            (tone * envelope * 0.85 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun wav(): ByteArray {
        val samples = pcm()
        val data = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            data[i * 2] = (s.toInt() and 0xFF).toByte()
            data[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        val out = ByteArrayOutputStream()
        fun int(v: Int) = out.write(byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(), (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte()))
        fun short(v: Int) = out.write(byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte()))
        out.write("RIFF".toByteArray())
        int(36 + data.size)
        out.write("WAVEfmt ".toByteArray())
        int(16)
        short(1)
        short(1)
        int(SAMPLE_RATE)
        int(SAMPLE_RATE * 2)
        short(2)
        short(16)
        out.write("data".toByteArray())
        int(data.size)
        out.write(data)
        return out.toByteArray()
    }
}
