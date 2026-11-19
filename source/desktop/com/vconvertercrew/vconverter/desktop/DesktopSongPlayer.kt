package com.vconvertercrew.vconverter.desktop

import com.vconvertercrew.vconverter.audio.Mixers
import com.vconvertercrew.vconverter.audio.PcmAudio
import com.vconvertercrew.vconverter.audio.PcmOutput
import com.vconvertercrew.vconverter.audio.StreamSongPlayer
import com.vconvertercrew.vconverter.editor.EditorAudio
import com.vconvertercrew.vconverter.editor.Hitsound
import com.vconvertercrew.vconverter.editorview.SongPlayer
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.SourceDataLine

class LineOutput(override val sampleRate: Int) : PcmOutput {
    override val channels: Int = 2
    override val chunkFrames: Int = 1024
    private val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format).apply { open(format, (sampleRate / 12) * 4) }
    private val bytes = ByteArray(chunkFrames * 4)

    override fun start() {
        line.start()
    }

    override fun write(samples: FloatArray, frames: Int) {
        var j = 0
        for (i in 0 until frames * 2) {
            val s = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            bytes[j++] = (s and 0xFF).toByte()
            bytes[j++] = ((s shr 8) and 0xFF).toByte()
        }
        line.write(bytes, 0, frames * 4)
    }

    override fun framesPlayed(): Long = line.longFramePosition

    override fun pause() {
        line.stop()
    }

    override fun flush() {
        line.flush()
    }

    override fun close() {
        line.stop()
        line.close()
    }
}

class DesktopSongPlayer private constructor(private val inner: StreamSongPlayer?) : SongPlayer {
    private val clip: Clip? = runCatching {
        AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(ByteArrayInputStream(Hitsound.wav()))) }
    }.getOrNull()

    override val duration: Double get() = inner?.duration ?: 0.0
    override val isPlaying: Boolean get() = inner?.isPlaying == true
    val backend: String get() = inner?.backend ?: "none"

    override fun position(): Double = inner?.position() ?: 0.0
    override fun play() { inner?.play() }
    override fun pause() { inner?.pause() }
    override fun seek(ms: Double) { inner?.seek(ms) }

    fun setVolume(volume: Float) {
        inner?.setVolume(volume.coerceIn(0f, 1f))
    }

    override fun hitsound() {
        val c = clip ?: return
        c.stop()
        c.framePosition = 0
        c.start()
    }

    override fun release() {
        inner?.release()
        runCatching { clip?.close() }
    }

    companion object {
        private fun decode(audio: EditorAudio): PcmAudio {
            com.vconvertercrew.vconverter.audio.VMiniAudioNative.decodePcm(audio.bytes)?.let { return it }
            val source = AudioSystem.getAudioInputStream(BufferedInputStream(ByteArrayInputStream(audio.bytes)))
            val base = source.format
            val channels = base.channels.coerceAtLeast(1)
            val target = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, channels, channels * 2, base.sampleRate, false)
            val bytes = AudioSystem.getAudioInputStream(target, source).use { it.readBytes() }
            val out = ShortArray(bytes.size / 2)
            for (i in out.indices) out[i] = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
            return PcmAudio(out, channels, base.sampleRate.toInt())
        }

        fun create(audio: List<EditorAudio>): Pair<DesktopSongPlayer, List<String>> {
            val failures = ArrayList<String>()
            val tracks = audio.mapNotNull { a -> runCatching { decode(a) }.onFailure { failures += a.name }.getOrNull() }
            val inner = tracks.firstOrNull()?.let { first ->
                runCatching {
                    val output = LineOutput(first.rate)
                    val mixer = Mixers.create(output.sampleRate, output.channels)
                    tracks.forEach { mixer.add(it) }
                    StreamSongPlayer(mixer, output)
                }.getOrNull()
            }
            return DesktopSongPlayer(inner) to failures
        }
    }
}
