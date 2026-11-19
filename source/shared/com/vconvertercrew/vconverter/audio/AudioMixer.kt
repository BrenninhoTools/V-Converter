package com.vconvertercrew.vconverter.audio

class PcmAudio(val samples: ShortArray, val channels: Int, val rate: Int) {
    val frames: Int get() = if (channels > 0) samples.size / channels else 0
    val duration: Double get() = if (rate > 0) frames.toDouble() / rate else 0.0

    companion object {
        fun fromFloats(samples: FloatArray, channels: Int, rate: Int): PcmAudio =
            PcmAudio(AudioMixer.toShorts(samples), channels, rate)
    }
}

class AudioMeter(val peakLeft: Float, val peakRight: Float, val rmsLeft: Float, val rmsRight: Float, val gainReduction: Float)

enum class AudioQuality { NEAREST, LINEAR, CUBIC }

interface AudioMixer {
    val sampleRate: Int
    val channels: Int
    val backend: String
    val duration: Double
    val trackCount: Int

    fun add(audio: PcmAudio): Boolean
    fun play()
    fun pause()
    fun seek(seconds: Double)
    fun setTrackVolume(index: Int, volume: Float)
    fun setMasterVolume(volume: Float)
    fun setPitch(pitch: Float)
    fun render(output: FloatArray, frames: Int)
    fun release()

    val advanced: Boolean get() = false
    val clock: Double get() = 0.0

    fun setTrackPan(index: Int, pan: Float) {}
    fun setTrackBus(index: Int, bus: Int) {}
    fun fadeTrack(index: Int, volume: Float, seconds: Double) {}
    fun setBusVolume(bus: Int, volume: Float) {}
    fun setBusFilters(bus: Int, lowpass: Float, highpass: Float) {}
    fun setBusEcho(bus: Int, seconds: Float, feedback: Float, mix: Float) {}
    fun setQuality(quality: AudioQuality) {}
    fun setLimiter(enabled: Boolean, ceiling: Float = 1f) {}
    fun meter(): AudioMeter? = null

    companion object {
        fun renderAll(mixer: AudioMixer, chunk: Int = 4096): FloatArray {
            val total = kotlin.math.ceil(mixer.duration * mixer.sampleRate).toInt()
            val out = FloatArray(total * mixer.channels)
            val buffer = FloatArray(chunk * mixer.channels)
            mixer.seek(0.0)
            mixer.play()
            var done = 0
            while (done < total) {
                val frames = minOf(chunk, total - done)
                mixer.render(buffer, frames)
                buffer.copyInto(out, done * mixer.channels, 0, frames * mixer.channels)
                done += frames
            }
            mixer.pause()
            return out
        }

        fun toShorts(samples: FloatArray): ShortArray =
            ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
    }
}

class SoftMixer(override val sampleRate: Int, override val channels: Int = 2) : AudioMixer {
    private class Track(val audio: PcmAudio) {
        var cursor = 0.0
        var volume = 1f
        var playing = false
    }

    private val tracks = ArrayList<Track>()
    private var master = 1f
    private var pitch = 1f

    override val backend: String = "Kotlin"
    override val duration: Double get() = tracks.maxOfOrNull { it.audio.duration } ?: 0.0
    override val trackCount: Int get() = tracks.size

    override fun add(audio: PcmAudio): Boolean {
        if (audio.frames <= 0 || audio.rate <= 0) return false
        tracks += Track(audio)
        return true
    }

    override fun play() {
        tracks.forEach { if (it.cursor < it.audio.frames) it.playing = true }
    }

    override fun pause() {
        tracks.forEach { it.playing = false }
    }

    override fun seek(seconds: Double) {
        tracks.forEach { it.cursor = (seconds.coerceAtLeast(0.0) * it.audio.rate).coerceAtMost(it.audio.frames.toDouble()) }
    }

    override fun setTrackVolume(index: Int, volume: Float) {
        tracks.getOrNull(index)?.volume = volume.coerceIn(0f, 1f)
    }

    override fun setMasterVolume(volume: Float) {
        master = volume.coerceIn(0f, 1f)
    }

    override fun setPitch(pitch: Float) {
        if (pitch > 0f) this.pitch = pitch
    }

    override fun render(output: FloatArray, frames: Int) {
        val count = frames * channels
        output.fill(0f, 0, count)
        for (track in tracks) {
            if (!track.playing) continue
            val audio = track.audio
            val step = audio.rate.toDouble() / sampleRate * pitch
            val total = audio.frames
            for (i in 0 until frames) {
                if (track.cursor >= total) {
                    track.cursor = total.toDouble()
                    track.playing = false
                    break
                }
                val frame = track.cursor.toInt()
                val next = if (frame + 1 < total) frame + 1 else frame
                val t = (track.cursor - frame).toFloat()
                val left = sample(audio, frame, next, 0, t)
                val right = if (audio.channels > 1) sample(audio, frame, next, 1, t) else left
                if (channels == 1) {
                    output[i] += (left + right) * 0.5f * track.volume
                } else {
                    output[i * channels] += left * track.volume
                    output[i * channels + 1] += right * track.volume
                }
                track.cursor += step
            }
        }
        for (i in 0 until count) output[i] = (output[i] * master).let { if (it.isNaN()) 0f else it.coerceIn(-1f, 1f) }
    }

    private fun sample(audio: PcmAudio, frame: Int, next: Int, channel: Int, t: Float): Float {
        val a = audio.samples[frame * audio.channels + channel] / 32768f
        val b = audio.samples[next * audio.channels + channel] / 32768f
        return a + (b - a) * t
    }

    override fun release() {
        tracks.clear()
    }
}
