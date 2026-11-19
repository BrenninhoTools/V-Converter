package com.vconvertercrew.vtouch

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

object VTouchFeature {
    const val LANES = 1
    const val TAP = 1 shl 1
    const val STEPS = 1 shl 2
    const val SWIPE_BACK = 1 shl 3
    const val PINCH = 1 shl 4
    const val LONG_PRESS = 1 shl 5
    const val FLICK = 1 shl 6
}

object VTouchAction {
    const val DOWN = 0
    const val MOVE = 1
    const val UP = 2
    const val CANCEL = 3
}

enum class VTouchEventType {
    NONE, LANE_PRESS, LANE_RELEASE, TAP, DOUBLE_TAP, LONG_PRESS, STEP, SWIPE_BACK, ZOOM, PAN, DRAG_START, DRAG_END;

    companion object {
        fun of(code: Int): VTouchEventType = entries.getOrElse(code) { NONE }
    }
}

class VTouchEvent(val type: VTouchEventType, val lane: Int, val x: Float, val y: Float, val value: Float, val dx: Float, val dy: Float)

data class VTouchConfig(
    val density: Float = 1f,
    val tapSlopDp: Float = 8f,
    val doubleTapMs: Float = 300f,
    val longPressMs: Float = 500f,
    val swipeBackDp: Float = 110f,
    val stepPx: Float = 120f,
    val flickDpPerSecond: Float = 1400f,
    val maxFlickSteps: Int = 4,
    val padHitScale: Float = 1.35f,
    val lanes: Int = 4,
    val layout: Int = 0,
    val features: Int = VTouchFeature.LANES
) {
    fun toArray(): FloatArray = floatArrayOf(
        density, tapSlopDp, doubleTapMs, longPressMs, swipeBackDp, stepPx, flickDpPerSecond,
        maxFlickSteps.toFloat(), padHitScale, lanes.toFloat(), layout.toFloat(), features.toFloat()
    )

    companion object {
        const val MAX_LANES = 9
        const val MAX_POINTERS = 10
        const val QUEUE_SIZE = 128
    }
}

interface VTouchEngine {
    val native: Boolean
    val pointerCount: Int
    val dragging: Boolean
    val pinching: Boolean

    fun configure(config: VTouchConfig)
    fun viewport(width: Float, height: Float)
    fun step(pixels: Float)
    fun enabled(enabled: Boolean)
    fun pointer(id: Long, action: Int, x: Float, y: Float, timeMs: Double)
    fun tick(timeMs: Double)
    fun reset()
    fun poll(): VTouchEvent?
    fun held(lane: Int): Int
    fun laneAt(x: Float, y: Float): Int
    fun shape(lane: Int, out: FloatArray): Boolean
    fun release()
}

class KotlinTouchEngine(config: VTouchConfig) : VTouchEngine {
    private class Point(var x: Float = 0f, var y: Float = 0f) {
        fun set(px: Float, py: Float): Point {
            x = px
            y = py
            return this
        }
    }

    private class Pointer {
        var active = false
        var id = 0L
        val start = Point()
        val position = Point()
        var downTime = 0.0
        var lane = -1
        val sx = FloatArray(SAMPLES)
        val sy = FloatArray(SAMPLES)
        val st = DoubleArray(SAMPLES)
        var count = 0
        var head = 0

        fun clear() {
            active = false
            id = 0
            start.set(0f, 0f)
            position.set(0f, 0f)
            downTime = 0.0
            lane = -1
            count = 0
            head = 0
        }

        fun record(x: Float, y: Float, time: Double) {
            sx[head] = x
            sy[head] = y
            st[head] = time
            head = (head + 1) % SAMPLES
            if (count < SAMPLES) count++
        }

        fun velocityY(now: Double): Float {
            var oldest = -1
            var newest = -1
            for (i in 0 until count) {
                val at = (head - 1 - i + SAMPLES) % SAMPLES
                if (now - st[at] > WINDOW_MS) break
                if (newest < 0) newest = at
                oldest = at
            }
            if (oldest < 0 || newest < 0 || st[newest] - st[oldest] < 1.0) return 0f
            val seconds = ((st[newest] - st[oldest]) / 1000.0).toFloat()
            return (sy[newest] - sy[oldest]) / seconds
        }
    }

    private var config = VTouchConfig()
    private var configured = false
    private var on = true
    private var width = 1f
    private var height = 1f
    private val pointers = Array(VTouchConfig.MAX_POINTERS) { Pointer() }
    private val heldLanes = IntArray(VTouchConfig.MAX_LANES)
    private val queue = ArrayDeque<VTouchEvent>()
    private var drag = false
    private var pinch = false
    private var multi = false
    private var longFired = false
    private var totalX = 0f
    private var totalY = 0f
    private var vertical = 0f
    private var pinchX = 0f
    private var pinchY = 0f
    private var pinchSpan = 0f
    private var lastTapTime = -1.0
    private var lastTapX = 0f
    private var lastTapY = 0f

    init {
        configure(config)
    }

    override val native: Boolean get() = false
    override val pointerCount: Int get() = pointers.count { it.active }
    override val dragging: Boolean get() = drag
    override val pinching: Boolean get() = pinch

    override fun configure(config: VTouchConfig) {
        val next = config.copy(
            density = if (config.density > 0f) config.density else 1f,
            lanes = config.lanes.coerceIn(1, VTouchConfig.MAX_LANES),
            layout = if (config.layout in 0..2) config.layout else 0
        )
        val changed = next.lanes != this.config.lanes || next.layout != this.config.layout
        if (changed && configured) releaseAllLanes()
        this.config = next
        configured = true
    }

    override fun viewport(width: Float, height: Float) {
        this.width = if (width > 1f) width else 1f
        this.height = if (height > 1f) height else 1f
    }

    override fun step(pixels: Float) {
        config = config.copy(stepPx = pixels)
    }

    override fun enabled(enabled: Boolean) {
        if (on && !enabled) reset()
        on = enabled
    }

    override fun pointer(id: Long, action: Int, x: Float, y: Float, timeMs: Double) {
        if (!on || !x.isFinite() || !y.isFinite()) return
        when (action) {
            VTouchAction.DOWN -> down(id, x, y, timeMs)
            VTouchAction.MOVE -> move(id, x, y, timeMs)
            VTouchAction.UP -> up(id, x, y, timeMs, false)
            VTouchAction.CANCEL -> up(id, x, y, timeMs, true)
        }
    }

    override fun tick(timeMs: Double) {
        if (!has(VTouchFeature.LONG_PRESS) || longFired || multi || drag) return
        val only = single() ?: return
        if (timeMs - only.downTime >= config.longPressMs && distance(only.position.x - only.start.x, only.position.y - only.start.y) <= slop()) {
            longFired = true
            emit(VTouchEventType.LONG_PRESS, -1, only.position.x, only.position.y, (timeMs - only.downTime).toFloat())
        }
    }

    override fun reset() {
        releaseAllLanes()
        pointers.forEach { it.clear() }
        endGesture()
    }

    override fun poll(): VTouchEvent? = queue.removeFirstOrNull()

    override fun held(lane: Int): Int = heldLanes.getOrElse(lane) { 0 }

    override fun laneAt(x: Float, y: Float): Int = when (config.layout) {
        0 -> if (x < 0f || y < 0f || x > width || y > height) -1 else (x / width * config.lanes).toInt().coerceIn(0, config.lanes - 1)
        1 -> {
            var best = -1
            var bestDistance = 0f
            val circle = FloatArray(4)
            for (lane in 0 until config.lanes) {
                pad(lane, circle)
                val d = distance(circle[0] - x, circle[1] - y)
                if (d <= circle[2] * config.padHitScale && (best < 0 || d < bestDistance)) {
                    best = lane
                    bestDistance = d
                }
            }
            best
        }
        else -> -1
    }

    override fun shape(lane: Int, out: FloatArray): Boolean {
        if (lane < 0 || lane >= config.lanes || out.size < 4 || config.layout == 2) return false
        if (config.layout == 0) {
            val w = width / config.lanes
            out[0] = w * lane
            out[1] = 0f
            out[2] = w
            out[3] = height
            return true
        }
        pad(lane, out)
        return true
    }

    override fun release() {
        queue.clear()
    }

    private fun has(feature: Int): Boolean = config.features and feature != 0
    private fun slop(): Float = config.tapSlopDp * config.density
    private fun distance(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

    private fun pad(lane: Int, out: FloatArray) {
        val base = (if (width < height) width else height) * 0.085f
        if (config.lanes == 4) {
            val gap = base * 2.25f
            val mx = width - gap * 1.6f
            val my = height - gap * 1.4f
            val ox = floatArrayOf(-gap, 0f, 0f, gap)
            val oy = floatArrayOf(0f, gap * 0.62f, -gap * 0.62f, 0f)
            val rotations = floatArrayOf(0f, 270f, 90f, 180f)
            out[0] = mx + ox[lane]
            out[1] = my + oy[lane]
            out[2] = base
            out[3] = rotations[lane]
            return
        }
        val radius = base * if (config.lanes > 6) 0.8f else 1f
        val gap = radius * 2.3f
        val total = gap * (config.lanes - 1)
        val right = width - radius * 1.8f
        var left = right - total
        if (left < radius * 1.8f) left = radius * 1.8f
        val step = if (config.lanes > 1) (right - left) / (config.lanes - 1) else 0f
        val arc = height - radius * 2.2f
        val t = if (config.lanes > 1) lane.toFloat() / (config.lanes - 1) else 0.5f
        val lift = sin(t * PI_F) * radius * 0.9f
        out[0] = left + step * lane
        out[1] = arc - lift
        out[2] = radius
        out[3] = 0f
    }

    private fun emit(type: VTouchEventType, lane: Int, x: Float, y: Float, value: Float = 0f, dx: Float = 0f, dy: Float = 0f) {
        if (queue.size < VTouchConfig.QUEUE_SIZE) queue.addLast(VTouchEvent(type, lane, x, y, value, dx, dy))
    }

    private fun find(id: Long): Pointer? = pointers.firstOrNull { it.active && it.id == id }

    private fun single(): Pointer? {
        var found: Pointer? = null
        for (p in pointers) {
            if (!p.active) continue
            if (found != null) return null
            found = p
        }
        return found
    }

    private fun press(lane: Int, x: Float, y: Float) {
        if (lane !in 0 until VTouchConfig.MAX_LANES) return
        if (++heldLanes[lane] == 1) emit(VTouchEventType.LANE_PRESS, lane, x, y)
    }

    private fun releaseLane(lane: Int, x: Float, y: Float) {
        if (lane !in 0 until VTouchConfig.MAX_LANES || heldLanes[lane] == 0) return
        if (--heldLanes[lane] == 0) emit(VTouchEventType.LANE_RELEASE, lane, x, y)
    }

    private fun releaseAllLanes() {
        for (p in pointers) {
            if (p.active && p.lane >= 0) {
                releaseLane(p.lane, p.position.x, p.position.y)
                p.lane = -1
            }
        }
        for (lane in 0 until VTouchConfig.MAX_LANES) {
            if (heldLanes[lane] > 0) {
                heldLanes[lane] = 0
                emit(VTouchEventType.LANE_RELEASE, lane, 0f, 0f)
            }
        }
    }

    private fun updateLane(p: Pointer) {
        if (!has(VTouchFeature.LANES)) return
        val lane = laneAt(p.position.x, p.position.y)
        if (lane == p.lane) return
        if (p.lane >= 0) releaseLane(p.lane, p.position.x, p.position.y)
        p.lane = lane
        if (lane >= 0) press(lane, p.position.x, p.position.y)
    }

    private fun snapshot(): FloatArray {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (p in pointers) {
            if (!p.active) continue
            sx += p.position.x
            sy += p.position.y
            n++
        }
        val cx = if (n > 0) sx / n else 0f
        val cy = if (n > 0) sy / n else 0f
        var total = 0f
        for (p in pointers) if (p.active) total += distance(p.position.x - cx, p.position.y - cy)
        return floatArrayOf(cx, cy, if (n > 0) total / n else 0f)
    }

    private fun down(id: Long, x: Float, y: Float, time: Double) {
        val p = find(id) ?: pointers.firstOrNull { !it.active } ?: return
        val first = pointerCount == 0
        p.clear()
        p.active = true
        p.id = id
        p.start.set(x, y)
        p.position.set(x, y)
        p.downTime = time
        p.record(x, y, time)
        if (first) {
            totalX = 0f
            totalY = 0f
            vertical = 0f
            longFired = false
            multi = false
        } else {
            multi = true
            if (drag) {
                drag = false
                emit(VTouchEventType.DRAG_END, -1, x, y)
            }
        }
        updateLane(p)
        if (has(VTouchFeature.PINCH) && pointerCount >= 2) {
            pinch = true
            val s = snapshot()
            pinchX = s[0]
            pinchY = s[1]
            pinchSpan = s[2]
        }
    }

    private fun move(id: Long, x: Float, y: Float, time: Double) {
        val p = find(id) ?: return
        var dx = x - p.position.x
        var dy = y - p.position.y
        p.position.set(x, y)
        p.record(x, y, time)
        updateLane(p)

        if (pinch && pointerCount >= 2) {
            val s = snapshot()
            if (pinchSpan > 0.5f && s[2] > 0.5f) {
                val zoom = s[2] / pinchSpan
                if (abs(zoom - 1f) > 1e-4f) emit(VTouchEventType.ZOOM, -1, s[0], s[1], zoom)
            }
            val px = s[0] - pinchX
            val py = s[1] - pinchY
            if (px != 0f || py != 0f) emit(VTouchEventType.PAN, -1, s[0], s[1], 0f, px, py)
            pinchX = s[0]
            pinchY = s[1]
            pinchSpan = s[2]
            return
        }

        if (multi || single() !== p) return

        if (!drag && distance(x - p.start.x, y - p.start.y) > slop() && (has(VTouchFeature.STEPS) || has(VTouchFeature.SWIPE_BACK))) {
            drag = true
            emit(VTouchEventType.DRAG_START, -1, p.start.x, p.start.y)
            dx = x - p.start.x
            dy = y - p.start.y
        }
        if (!drag) return

        totalX += dx
        totalY += dy
        if (!has(VTouchFeature.STEPS)) return
        if (abs(totalX) > abs(totalY) * 1.5f) return
        vertical += dy
        val size = if (config.stepPx < 24f) 24f else config.stepPx
        while (abs(vertical) > size) {
            emit(VTouchEventType.STEP, -1, x, y, if (vertical > 0f) -1f else 1f)
            vertical -= if (vertical > 0f) size else -size
        }
    }

    private fun up(id: Long, x: Float, y: Float, time: Double, cancelled: Boolean) {
        val p = find(id) ?: return
        p.position.set(x, y)
        p.record(x, y, time)
        val last = pointerCount == 1
        if (p.lane >= 0) {
            releaseLane(p.lane, x, y)
            p.lane = -1
        }
        if (last && !cancelled && !multi) {
            if (drag) {
                finishDrag(p, time)
            } else if (has(VTouchFeature.TAP) && !longFired && distance(x - p.start.x, y - p.start.y) <= slop() &&
                (!has(VTouchFeature.LONG_PRESS) || time - p.downTime < config.longPressMs)
            ) {
                emit(VTouchEventType.TAP, -1, x, y)
                if (lastTapTime >= 0.0 && time - lastTapTime <= config.doubleTapMs && distance(x - lastTapX, y - lastTapY) <= slop() * 4f) {
                    emit(VTouchEventType.DOUBLE_TAP, -1, x, y)
                    lastTapTime = -1.0
                } else {
                    lastTapTime = time
                    lastTapX = x
                    lastTapY = y
                }
            }
        } else if (last && drag) {
            emit(VTouchEventType.DRAG_END, -1, x, y)
        }
        p.clear()
        if (pointerCount < 2 && pinch) {
            pinch = false
        } else if (pinch) {
            val s = snapshot()
            pinchX = s[0]
            pinchY = s[1]
            pinchSpan = s[2]
        }
        if (pointerCount == 0) endGesture()
    }

    private fun finishDrag(p: Pointer, time: Double) {
        val swipe = config.swipeBackDp * config.density
        if (has(VTouchFeature.SWIPE_BACK) && totalX > swipe && abs(totalX) > abs(totalY) * 2f) {
            emit(VTouchEventType.SWIPE_BACK, -1, p.position.x, p.position.y, totalX)
        }
        if (has(VTouchFeature.STEPS) && has(VTouchFeature.FLICK) && abs(totalX) <= abs(totalY) * 1.5f) {
            val velocity = p.velocityY(time)
            val threshold = config.flickDpPerSecond * config.density
            if (threshold > 0f && abs(velocity) > threshold) {
                val steps = (abs(velocity) / threshold).toInt().coerceAtMost(config.maxFlickSteps)
                val direction = if (velocity > 0f) -1f else 1f
                repeat(steps) { emit(VTouchEventType.STEP, -1, p.position.x, p.position.y, direction, 0f, velocity) }
            }
        }
        emit(VTouchEventType.DRAG_END, -1, p.position.x, p.position.y)
    }

    private fun endGesture() {
        drag = false
        pinch = false
        multi = false
        longFired = false
        totalX = 0f
        totalY = 0f
        vertical = 0f
    }

    companion object {
        private const val SAMPLES = 8
        private const val WINDOW_MS = 100.0
        private const val PI_F = 3.14159265358979323846f
    }
}
