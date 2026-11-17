package com.vconvertercrew.vconverter.audio

import java.io.File

object VMiniAudioNative {
    private const val LIBRARY = "vconverter_audio"

    val loaded: Boolean by lazy { load() }
    var loadError: String? = null
        private set

    private fun isAndroid(): Boolean =
        System.getProperty("java.vm.name").orEmpty().contains("Dalvik", true) ||
            System.getProperty("java.vendor").orEmpty().contains("Android", true)

    fun platformFolder(): String {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val arch = System.getProperty("os.arch").orEmpty().lowercase()
        val system = when {
            "win" in os -> "windows"
            "mac" in os || "darwin" in os -> "macos"
            else -> "linux"
        }
        val cpu = when (arch) {
            "amd64", "x86_64", "x64" -> "x64"
            "aarch64", "arm64" -> "arm64"
            else -> arch
        }
        return "$system-$cpu"
    }

    fun libraryFile(): String = System.mapLibraryName(LIBRARY)

    private fun load(): Boolean {
        val result = runCatching {
            if (isAndroid()) {
                System.loadLibrary(LIBRARY)
            } else {
                val override = System.getProperty("vconverter.audio.library")
                if (override != null) {
                    System.load(File(override).absolutePath)
                } else {
                    val resource = "native/${platformFolder()}/${libraryFile()}"
                    val stream = VMiniAudioNative::class.java.classLoader?.getResourceAsStream(resource)
                        ?: throw UnsatisfiedLinkError("$resource is not bundled")
                    val folder = File(System.getProperty("java.io.tmpdir"), "v-converter-native").apply { mkdirs() }
                    val target = File(folder, "${System.getProperty("user.name").orEmpty().hashCode().toUInt()}-${libraryFile()}")
                    val bytes = stream.use { it.readBytes() }
                    if (!target.exists() || target.length() != bytes.size.toLong() || !target.readBytes().contentEquals(bytes)) {
                        val temp = File(folder, "${target.name}.${System.nanoTime()}")
                        temp.writeBytes(bytes)
                        if (!temp.renameTo(target)) {
                            target.delete()
                            temp.renameTo(target)
                        }
                    }
                    System.load(target.absolutePath)
                }
            }
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
