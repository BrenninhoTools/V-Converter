package com.vconvertercrew.vconverter.audio

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import vminiaudio.vca_add_s16
import vminiaudio.vca_bus_echo
import vminiaudio.vca_bus_filters
import vminiaudio.vca_bus_volume
import vminiaudio.vca_fade
import vminiaudio.vca_meter
import vminiaudio.vca_set_bus
import vminiaudio.vca_set_interpolation
import vminiaudio.vca_set_limiter
import vminiaudio.vca_set_pan
import vminiaudio.vca_time
import vminiaudio.vca_duration
import vminiaudio.vca_init
import vminiaudio.vca_pause
import vminiaudio.vca_play
import vminiaudio.vca_position
import vminiaudio.vca_render
import vminiaudio.vca_seek
import vminiaudio.vca_set_master_volume
import vminiaudio.vca_set_pitch
import vminiaudio.vca_set_volume
import vminiaudio.vca_shutdown
import vminiaudio.vca_version

class VMiniAudioMixer private constructor(
    private val context: COpaquePointer,
    override val sampleRate: Int,
    override val channels: Int
) : AudioMixer {
    private val audios = ArrayList<COpaquePointer>()
    private val durations = ArrayList<Double>()
    private var released = false

    override val backend: String = "V-MiniAudio ${vca_version()?.toKString().orEmpty()}"
    override val duration: Double get() = durations.maxOrNull() ?: 0.0
    override val trackCount: Int get() = audios.size

    override fun add(audio: PcmAudio): Boolean {
        if (released || audio.frames <= 0) return false
        val handle = audio.samples.usePinned { pinned ->
            vca_add_s16(context, pinned.addressOf(0), audio.frames, audio.channels, audio.rate)
        } ?: return false
        audios += handle
        durations += vca_duration(handle)
        return true
    }

    override fun play() {
        if (released) return
        audios.forEachIndexed { index, audio -> if (vca_position(audio) < durations[index]) vca_play(audio) }
    }

    override fun pause() {
        if (!released) audios.forEach { vca_pause(it) }
    }

    override fun seek(seconds: Double) {
        if (!released) audios.forEach { vca_seek(it, seconds.coerceAtLeast(0.0)) }
    }

    override fun setTrackVolume(index: Int, volume: Float) {
        if (!released) audios.getOrNull(index)?.let { vca_set_volume(it, volume) }
    }

    override fun setMasterVolume(volume: Float) {
        if (!released) vca_set_master_volume(context, volume.coerceIn(0f, 1f))
    }

    override fun setPitch(pitch: Float) {
        if (!released && pitch > 0f) audios.forEach { vca_set_pitch(it, pitch) }
    }

    override val advanced: Boolean get() = true
    override val clock: Double get() = if (released) 0.0 else vca_time(context)
    private val meterValues = FloatArray(5)

    override fun setTrackPan(index: Int, pan: Float) {
        if (!released) audios.getOrNull(index)?.let { vca_set_pan(it, pan.coerceIn(-1f, 1f)) }
    }

    override fun setTrackBus(index: Int, bus: Int) {
        if (!released) audios.getOrNull(index)?.let { vca_set_bus(it, bus) }
    }

    override fun fadeTrack(index: Int, volume: Float, seconds: Double) {
        if (!released) audios.getOrNull(index)?.let { vca_fade(it, volume.coerceIn(0f, 1f), seconds) }
    }

    override fun setBusVolume(bus: Int, volume: Float) {
        if (!released) vca_bus_volume(context, bus, volume)
    }

    override fun setBusFilters(bus: Int, lowpass: Float, highpass: Float) {
        if (!released) vca_bus_filters(context, bus, lowpass, highpass)
    }

    override fun setBusEcho(bus: Int, seconds: Float, feedback: Float, mix: Float) {
        if (!released) vca_bus_echo(context, bus, seconds, feedback, mix)
    }

    override fun setQuality(quality: AudioQuality) {
        if (!released) vca_set_interpolation(context, quality.ordinal)
    }

    override fun setLimiter(enabled: Boolean, ceiling: Float) {
        if (!released) vca_set_limiter(context, if (enabled) 1 else 0, ceiling)
    }

    override fun meter(): AudioMeter? {
        if (released) return null
        val result = meterValues.usePinned { vca_meter(context, it.addressOf(0)) }
        if (result != 0) return null
        return AudioMeter(meterValues[0], meterValues[1], meterValues[2], meterValues[3], meterValues[4])
    }

    override fun render(output: FloatArray, frames: Int) {
        if (released || frames <= 0) {
            output.fill(0f, 0, frames.coerceAtLeast(0) * channels)
            return
        }
        output.usePinned { vca_render(context, it.addressOf(0), frames) }
    }

    override fun release() {
        if (released) return
        released = true
        audios.clear()
        durations.clear()
        vca_shutdown(context)
    }

    companion object {
        fun create(rate: Int, channels: Int): VMiniAudioMixer? =
            vca_init(rate, channels)?.let { VMiniAudioMixer(it, rate, channels) }
    }
}

object IosMixers {
    fun create(rate: Int, channels: Int = 2): AudioMixer = VMiniAudioMixer.create(rate, channels) ?: SoftMixer(rate, channels)
}
