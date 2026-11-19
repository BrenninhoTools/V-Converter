package com.vconvertercrew.vtouch

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import vtouch.vct_configure
import vtouch.vct_create
import vtouch.vct_poll
import vtouch.vtouch_destroy
import vtouch.vtouch_is_dragging
import vtouch.vtouch_is_pinching
import vtouch.vtouch_lane_at
import vtouch.vtouch_lane_held
import vtouch.vtouch_lane_shape
import vtouch.vtouch_pointer
import vtouch.vtouch_pointer_count
import vtouch.vtouch_reset
import vtouch.vtouch_set_enabled
import vtouch.vtouch_set_step
import vtouch.vtouch_set_viewport
import vtouch.vtouch_tick
import vtouch.vtouch_version

class NativeTouchEngine private constructor(private var handle: COpaquePointer?) : VTouchEngine {
    private val event = FloatArray(6)

    private val engine: CPointer<vtouch.VTouchEngine>? get() = handle?.reinterpret()

    override val native: Boolean get() = true
    override val pointerCount: Int get() = engine?.let { vtouch_pointer_count(it) } ?: 0
    override val dragging: Boolean get() = engine?.let { vtouch_is_dragging(it) != 0 } ?: false
    override val pinching: Boolean get() = engine?.let { vtouch_is_pinching(it) != 0 } ?: false

    override fun configure(config: VTouchConfig) {
        val h = handle ?: return
        vct_configure(
            h, config.density, config.tapSlopDp, config.doubleTapMs, config.longPressMs, config.swipeBackDp, config.stepPx,
            config.flickDpPerSecond, config.maxFlickSteps, config.padHitScale, config.lanes, config.layout, config.features
        )
    }

    override fun viewport(width: Float, height: Float) {
        engine?.let { vtouch_set_viewport(it, width, height) }
    }

    override fun step(pixels: Float) {
        engine?.let { vtouch_set_step(it, pixels) }
    }

    override fun enabled(enabled: Boolean) {
        engine?.let { vtouch_set_enabled(it, if (enabled) 1 else 0) }
    }

    override fun pointer(id: Long, action: Int, x: Float, y: Float, timeMs: Double) {
        engine?.let { vtouch_pointer(it, id, action, x, y, timeMs) }
    }

    override fun tick(timeMs: Double) {
        engine?.let { vtouch_tick(it, timeMs) }
    }

    override fun reset() {
        engine?.let { vtouch_reset(it) }
    }

    override fun poll(): VTouchEvent? {
        val h = handle ?: return null
        val type = event.usePinned { vct_poll(h, it.addressOf(0)) }
        if (type == 0) return null
        return VTouchEvent(VTouchEventType.of(type), event[0].toInt(), event[1], event[2], event[3], event[4], event[5])
    }

    override fun held(lane: Int): Int = engine?.let { vtouch_lane_held(it, lane) } ?: 0

    override fun laneAt(x: Float, y: Float): Int = engine?.let { vtouch_lane_at(it, x, y) } ?: -1

    override fun shape(lane: Int, out: FloatArray): Boolean {
        val e = engine ?: return false
        if (out.size < 4) return false
        return out.usePinned { vtouch_lane_shape(e, lane, it.addressOf(0)) } != 0
    }

    override fun release() {
        engine?.let { vtouch_destroy(it) }
        handle = null
    }

    companion object {
        fun create(config: VTouchConfig): NativeTouchEngine? {
            val handle = vct_create(
                config.density, config.tapSlopDp, config.doubleTapMs, config.longPressMs, config.swipeBackDp, config.stepPx,
                config.flickDpPerSecond, config.maxFlickSteps, config.padHitScale, config.lanes, config.layout, config.features
            ) ?: return null
            return NativeTouchEngine(handle)
        }
    }
}

object VTouchEngines {
    val backend: String get() = "C++ ${vtouch_version()?.toKString().orEmpty()}"

    fun create(config: VTouchConfig): VTouchEngine = NativeTouchEngine.create(config) ?: KotlinTouchEngine(config)
}
