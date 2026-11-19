package com.vconvertercrew.vtouch

import com.vconvertercrew.vconverter.NativeLibraries

object NativeTouch {
    private const val LIBRARY = "vconverter_touch"

    var loadError: String? = null
        private set

    val loaded: Boolean by lazy {
        val result = runCatching {
            NativeLibraries.load(LIBRARY, "vconverter.touch.library")
            version()
        }
        loadError = result.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
        result.isSuccess
    }

    @JvmStatic external fun version(): String
    @JvmStatic external fun create(config: FloatArray): Long
    @JvmStatic external fun destroy(engine: Long)
    @JvmStatic external fun configure(engine: Long, config: FloatArray)
    @JvmStatic external fun viewport(engine: Long, width: Float, height: Float)
    @JvmStatic external fun step(engine: Long, pixels: Float)
    @JvmStatic external fun enabled(engine: Long, enabled: Boolean)
    @JvmStatic external fun pointer(engine: Long, id: Long, action: Int, x: Float, y: Float, time: Double)
    @JvmStatic external fun tick(engine: Long, time: Double)
    @JvmStatic external fun reset(engine: Long)
    @JvmStatic external fun poll(engine: Long, out: FloatArray): Int
    @JvmStatic external fun held(engine: Long, lane: Int): Int
    @JvmStatic external fun laneAt(engine: Long, x: Float, y: Float): Int
    @JvmStatic external fun shape(engine: Long, lane: Int, out: FloatArray): Boolean
    @JvmStatic external fun state(engine: Long): Int
}

class NativeTouchEngine private constructor(private var handle: Long) : VTouchEngine {
    private val event = FloatArray(6)

    override val native: Boolean get() = true
    override val pointerCount: Int get() = if (handle == 0L) 0 else NativeTouch.state(handle) and 0xFF
    override val dragging: Boolean get() = handle != 0L && NativeTouch.state(handle) and 0x100 != 0
    override val pinching: Boolean get() = handle != 0L && NativeTouch.state(handle) and 0x200 != 0

    override fun configure(config: VTouchConfig) {
        if (handle != 0L) NativeTouch.configure(handle, config.toArray())
    }

    override fun viewport(width: Float, height: Float) {
        if (handle != 0L) NativeTouch.viewport(handle, width, height)
    }

    override fun step(pixels: Float) {
        if (handle != 0L) NativeTouch.step(handle, pixels)
    }

    override fun enabled(enabled: Boolean) {
        if (handle != 0L) NativeTouch.enabled(handle, enabled)
    }

    override fun pointer(id: Long, action: Int, x: Float, y: Float, timeMs: Double) {
        if (handle != 0L) NativeTouch.pointer(handle, id, action, x, y, timeMs)
    }

    override fun tick(timeMs: Double) {
        if (handle != 0L) NativeTouch.tick(handle, timeMs)
    }

    override fun reset() {
        if (handle != 0L) NativeTouch.reset(handle)
    }

    override fun poll(): VTouchEvent? {
        if (handle == 0L) return null
        val type = NativeTouch.poll(handle, event)
        if (type == 0) return null
        return VTouchEvent(VTouchEventType.of(type), event[0].toInt(), event[1], event[2], event[3], event[4], event[5])
    }

    override fun held(lane: Int): Int = if (handle == 0L) 0 else NativeTouch.held(handle, lane)

    override fun laneAt(x: Float, y: Float): Int = if (handle == 0L) -1 else NativeTouch.laneAt(handle, x, y)

    override fun shape(lane: Int, out: FloatArray): Boolean = handle != 0L && NativeTouch.shape(handle, lane, out)

    override fun release() {
        if (handle != 0L) {
            NativeTouch.destroy(handle)
            handle = 0L
        }
    }

    companion object {
        fun create(config: VTouchConfig): NativeTouchEngine? {
            if (!NativeTouch.loaded) return null
            val handle = runCatching { NativeTouch.create(config.toArray()) }.getOrDefault(0L)
            return if (handle == 0L) null else NativeTouchEngine(handle)
        }
    }
}

object VTouchEngines {
    val backend: String get() = if (NativeTouch.loaded) "C++ ${NativeTouch.version()}" else "Kotlin"

    fun create(config: VTouchConfig): VTouchEngine = NativeTouchEngine.create(config) ?: KotlinTouchEngine(config)
}
