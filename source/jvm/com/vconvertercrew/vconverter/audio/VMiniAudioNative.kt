package com.vconvertercrew.vconverter.audio

object VMiniAudioNative {
    private const val LIBRARY = "vconverter_audio"

    val loaded: Boolean by lazy { load() }
    var loadError: String? = null
        private set

    fun platformFolder(): String = com.vconvertercrew.vconverter.NativeLibraries.platformFolder()

    fun libraryFile(): String = System.mapLibraryName(LIBRARY)

    private fun load(): Boolean {
        val result = runCatching {
            com.vconvertercrew.vconverter.NativeLibraries.load(LIBRARY, "vconverter.audio.library")
            version()
        }
        loadError = result.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
        return result.isSuccess
    }

    @JvmStatic external fun version(): String
    @JvmStatic external fun init(rate: Int, channels: Int): Long
    @JvmStatic external fun shutdown(context: Long)
    @JvmStatic external fun createPcm(context: Long, samples: ShortArray, frames: Int, channels: Int, rate: Int): Long
    @JvmStatic external fun loadMemory(context: Long, bytes: ByteArray): Long
    @JvmStatic external fun destroy(audio: Long)
    @JvmStatic external fun play(audio: Long): Int
    @JvmStatic external fun pause(audio: Long): Int
    @JvmStatic external fun stop(audio: Long): Int
    @JvmStatic external fun seek(audio: Long, seconds: Double): Int
    @JvmStatic external fun position(audio: Long): Double
    @JvmStatic external fun duration(audio: Long): Double
    @JvmStatic external fun state(audio: Long): Int
    @JvmStatic external fun setVolume(audio: Long, volume: Float): Int
    @JvmStatic external fun setPitch(audio: Long, pitch: Float): Int
    @JvmStatic external fun setLooping(audio: Long, looping: Boolean): Int
    @JvmStatic external fun setMasterVolume(context: Long, volume: Float): Int
    @JvmStatic external fun render(context: Long, output: FloatArray, frames: Int): Int
    @JvmStatic external fun decode(bytes: ByteArray, info: IntArray): ShortArray?
    @JvmStatic external fun setPan(audio: Long, pan: Float): Int
    @JvmStatic external fun fade(audio: Long, target: Float, seconds: Double, end: Int): Int
    @JvmStatic external fun setLoopPoints(audio: Long, start: Double, end: Double): Int
    @JvmStatic external fun playDelayed(audio: Long, seconds: Double): Int
    @JvmStatic external fun peak(audio: Long): Float
    @JvmStatic external fun setBus(audio: Long, bus: Int): Int
    @JvmStatic external fun busVolume(context: Long, bus: Int, volume: Float): Int
    @JvmStatic external fun busMuted(context: Long, bus: Int, muted: Boolean): Int
    @JvmStatic external fun busFilters(context: Long, bus: Int, lowpass: Float, highpass: Float): Int
    @JvmStatic external fun busEcho(context: Long, bus: Int, seconds: Float, feedback: Float, mix: Float): Int
    @JvmStatic external fun busPeak(context: Long, bus: Int): Float
    @JvmStatic external fun setInterpolation(context: Long, mode: Int): Int
    @JvmStatic external fun setPanLaw(context: Long, law: Int): Int
    @JvmStatic external fun setLimiter(context: Long, enabled: Boolean, ceiling: Float): Int
    @JvmStatic external fun meter(context: Long, out: FloatArray): Int
    @JvmStatic external fun time(context: Long): Double

    fun canDecode(bytes: ByteArray): Boolean = bytes.size > 12 &&
        ((bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()) ||
            (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()))

    fun decodePcm(bytes: ByteArray): PcmAudio? {
        if (!canDecode(bytes) || !loaded) return null
        val info = IntArray(2)
        val samples = runCatching { decode(bytes, info) }.getOrNull() ?: return null
        return if (info[0] > 0 && info[1] > 0 && samples.isNotEmpty()) PcmAudio(samples, info[0], info[1]) else null
    }
}

class NativeMixer private constructor(
    private val context: Long,
    override val sampleRate: Int,
    override val channels: Int
) : AudioMixer {
    private val audios = ArrayList<Long>()
    private val durations = ArrayList<Double>()
    private var released = false

    override val backend: String = "V-MiniAudio ${VMiniAudioNative.version()}"
    override val duration: Double get() = durations.maxOrNull() ?: 0.0
    override val trackCount: Int get() = audios.size

    override fun add(audio: PcmAudio): Boolean {
        if (released || audio.frames <= 0) return false
        val handle = VMiniAudioNative.createPcm(context, audio.samples, audio.frames, audio.channels, audio.rate)
        if (handle == 0L) return false
        audios += handle
        durations += VMiniAudioNative.duration(handle)
        return true
    }

    override fun play() {
        if (released) return
        audios.forEachIndexed { index, audio ->
            if (VMiniAudioNative.position(audio) < durations[index]) VMiniAudioNative.play(audio)
        }
    }

    override fun pause() {
        if (released) return
        audios.forEach { VMiniAudioNative.pause(it) }
    }

    override fun seek(seconds: Double) {
        if (released) return
        audios.forEach { VMiniAudioNative.seek(it, seconds.coerceAtLeast(0.0)) }
    }

    override fun setTrackVolume(index: Int, volume: Float) {
        if (released) return
        audios.getOrNull(index)?.let { VMiniAudioNative.setVolume(it, volume) }
    }

    override fun setMasterVolume(volume: Float) {
        if (!released) VMiniAudioNative.setMasterVolume(context, volume.coerceIn(0f, 1f))
    }

    override fun setPitch(pitch: Float) {
        if (released || pitch <= 0f) return
        audios.forEach { VMiniAudioNative.setPitch(it, pitch) }
    }

    override val advanced: Boolean get() = true
    override val clock: Double get() = if (released) 0.0 else VMiniAudioNative.time(context)
    private val meterValues = FloatArray(5)

    override fun setTrackPan(index: Int, pan: Float) {
        if (!released) audios.getOrNull(index)?.let { VMiniAudioNative.setPan(it, pan.coerceIn(-1f, 1f)) }
    }

    override fun setTrackBus(index: Int, bus: Int) {
        if (!released) audios.getOrNull(index)?.let { VMiniAudioNative.setBus(it, bus) }
    }

    override fun fadeTrack(index: Int, volume: Float, seconds: Double) {
        if (!released) audios.getOrNull(index)?.let { VMiniAudioNative.fade(it, volume.coerceIn(0f, 1f), seconds, 0) }
    }

    override fun setBusVolume(bus: Int, volume: Float) {
        if (!released) VMiniAudioNative.busVolume(context, bus, volume)
    }

    override fun setBusFilters(bus: Int, lowpass: Float, highpass: Float) {
        if (!released) VMiniAudioNative.busFilters(context, bus, lowpass, highpass)
    }

    override fun setBusEcho(bus: Int, seconds: Float, feedback: Float, mix: Float) {
        if (!released) VMiniAudioNative.busEcho(context, bus, seconds, feedback, mix)
    }

    override fun setQuality(quality: AudioQuality) {
        if (!released) VMiniAudioNative.setInterpolation(context, quality.ordinal)
    }

    override fun setLimiter(enabled: Boolean, ceiling: Float) {
        if (!released) VMiniAudioNative.setLimiter(context, enabled, ceiling)
    }

    override fun meter(): AudioMeter? {
        if (released || VMiniAudioNative.meter(context, meterValues) != 0) return null
        return AudioMeter(meterValues[0], meterValues[1], meterValues[2], meterValues[3], meterValues[4])
    }

    override fun render(output: FloatArray, frames: Int) {
        if (released) {
            output.fill(0f, 0, frames * channels)
            return
        }
        VMiniAudioNative.render(context, output, frames)
    }

    override fun release() {
        if (released) return
        released = true
        VMiniAudioNative.shutdown(context)
        audios.clear()
        durations.clear()
    }

    companion object {
        fun create(rate: Int, channels: Int): NativeMixer? {
            if (!VMiniAudioNative.loaded) return null
            val context = VMiniAudioNative.init(rate, channels)
            return if (context == 0L) null else NativeMixer(context, rate, channels)
        }
    }
}

object Mixers {
    fun create(rate: Int, channels: Int = 2): AudioMixer = NativeMixer.create(rate, channels) ?: SoftMixer(rate, channels)

    val nativeAvailable: Boolean get() = VMiniAudioNative.loaded
}
