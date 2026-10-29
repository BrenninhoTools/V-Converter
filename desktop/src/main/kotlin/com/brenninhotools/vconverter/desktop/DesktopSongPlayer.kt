package com.brenninhotools.vconverter.desktop

import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.Hitsound
import com.brenninhotools.vconverter.editorview.SongPlayer
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

class DesktopSongPlayer private constructor(private val pcm: ShortArray, private val rate: Float) : SongPlayer {
    private val totalFrames = pcm.size / 2
    private val format = AudioFormat(rate, 16, 2, true, false)
    private val line: SourceDataLine? = if (totalFrames == 0) null else runCatching {
        AudioSystem.getSourceDataLine(format).apply { open(format, (rate / 12).toInt() * 4) }
    }.getOrNull()
    private val clip: Clip? = runCatching {
        AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(ByteArrayInputStream(Hitsound.wav()))) }
    }.getOrNull()
    private val lock = Any()

    @Volatile private var alive = true
    @Volatile private var playing = false
    @Volatile private var cursor = 0
    @Volatile private var generation = 0
    private var baseFrame = 0L
    private var baseLine = 0L
    private var pausedMs = 0.0

    override val duration: Double = totalFrames * 1000.0 / rate
    override val isPlaying: Boolean get() = playing && line != null

    init {
        if (line != null) thread(isDaemon = true, name = "editor-audio") { pump(line) }
    }

    private fun pump(line: SourceDataLine) {
        val chunk = 1024
        val bytes = ByteArray(chunk * 4)
        while (alive) {
            if (!playing) {
                Thread.sleep(4)
                continue
            }
            val gen: Int
            val count: Int
            synchronized(lock) {
                gen = generation
                count = minOf(chunk, totalFrames - cursor)
                if (count > 0) {
                    var j = 0
                    for (i in cursor * 2 until (cursor + count) * 2) {
                        val s = pcm[i].toInt()
                        bytes[j++] = (s and 0xFF).toByte()
                        bytes[j++] = ((s shr 8) and 0xFF).toByte()
                    }
                }
            }
            if (count <= 0) {
                line.drain()
                synchronized(lock) { if (gen == generation) { pausedMs = duration; playing = false } }
                continue
            }
            line.write(bytes, 0, count * 4)
            synchronized(lock) { if (gen == generation) cursor += count }
        }
    }

    override fun position(): Double {
        val l = line ?: return pausedMs
        if (!playing) return pausedMs
        synchronized(lock) {
            return (baseFrame + (l.longFramePosition - baseLine)) * 1000.0 / rate
        }
    }

    override fun play() {
        val l = line ?: return
        synchronized(lock) {
            generation++
            cursor = (pausedMs / 1000.0 * rate).toInt().coerceIn(0, totalFrames)
            l.flush()
            baseFrame = cursor.toLong()
            baseLine = l.longFramePosition
            l.start()
            playing = cursor < totalFrames
        }
    }

    override fun pause() {
        val l = line ?: return
        synchronized(lock) {
            pausedMs = position()
            playing = false
            generation++
            l.stop()
            l.flush()
        }
    }

    override fun seek(ms: Double) {
        val l = line
        if (l == null || !playing) {
            pausedMs = ms
            return
        }
        synchronized(lock) {
            generation++
            cursor = (ms / 1000.0 * rate).toInt().coerceIn(0, totalFrames)
            l.flush()
            baseFrame = cursor.toLong()
            baseLine = l.longFramePosition
            pausedMs = ms
        }
    }

    fun setVolume(volume: Float) {
        val l = line ?: return
        runCatching {
            val control = l.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN) as javax.sound.sampled.FloatControl
            val db = if (volume <= 0.0001f) control.minimum else (20.0 * kotlin.math.log10(volume.toDouble())).toFloat()
            control.value = db.coerceIn(control.minimum, control.maximum)
        }
    }

    override fun hitsound() {
        val c = clip ?: return
        c.stop()
        c.framePosition = 0
        c.start()
    }

    override fun release() {
        alive = false
        playing = false
        runCatching { line?.stop(); line?.close() }
        runCatching { clip?.close() }
    }

    companion object {
        private class Track(val samples: ShortArray, val rate: Float)

        private fun decode(audio: EditorAudio): Track {
            val source = AudioSystem.getAudioInputStream(BufferedInputStream(ByteArrayInputStream(audio.bytes)))
            val base = source.format
            val channels = base.channels.coerceAtLeast(1)
            val target = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, channels, channels * 2, base.sampleRate, false)
            val bytes = AudioSystem.getAudioInputStream(target, source).use { it.readBytes() }
            val frames = bytes.size / (channels * 2)
            val out = ShortArray(frames * 2)
            for (f in 0 until frames) {
                val left = ((bytes[f * channels * 2 + 1].toInt() shl 8) or (bytes[f * channels * 2].toInt() and 0xFF)).toShort()
                val right = if (channels > 1) ((bytes[f * channels * 2 + 3].toInt() shl 8) or (bytes[f * channels * 2 + 2].toInt() and 0xFF)).toShort() else left
                out[f * 2] = left
                out[f * 2 + 1] = right
            }
            return Track(out, base.sampleRate)
        }

        private fun resample(track: Track, rate: Float): ShortArray {
            if (track.rate == rate) return track.samples
            val frames = track.samples.size / 2
            val outFrames = (frames * rate / track.rate).toInt()
            val out = ShortArray(outFrames * 2)
            val step = track.rate / rate
            for (f in 0 until outFrames) {
                val src = f * step
                val i = src.toInt().coerceAtMost(frames - 1)
                val next = (i + 1).coerceAtMost(frames - 1)
                val t = src - i
                for (c in 0..1) out[f * 2 + c] = (track.samples[i * 2 + c] * (1 - t) + track.samples[next * 2 + c] * t).toInt().toShort()
            }
            return out
        }

        fun create(audio: List<EditorAudio>): Pair<DesktopSongPlayer, List<String>> {
            val failures = ArrayList<String>()
            val tracks = audio.mapNotNull { a -> runCatching { decode(a) }.onFailure { failures += a.name }.getOrNull() }
            val rate = tracks.firstOrNull()?.rate ?: 44100f
            val resampled = tracks.map { resample(it, rate) }
            val mixed = ShortArray(resampled.maxOfOrNull { it.size } ?: 0)
            if (resampled.size == 1) {
                resampled[0].copyInto(mixed)
            } else {
                for (i in mixed.indices) {
                    var sum = 0
                    for (t in resampled) if (i < t.size) sum += t[i]
                    mixed[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            return DesktopSongPlayer(mixed, rate) to failures
        }
    }
}
