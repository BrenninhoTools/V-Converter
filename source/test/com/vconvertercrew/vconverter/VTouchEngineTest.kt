package com.vconvertercrew.vconverter

import com.vconvertercrew.vtouch.KotlinTouchEngine
import com.vconvertercrew.vtouch.NativeTouch
import com.vconvertercrew.vtouch.NativeTouchEngine
import com.vconvertercrew.vtouch.VTouchAction
import com.vconvertercrew.vtouch.VTouchConfig
import com.vconvertercrew.vtouch.VTouchEngine
import com.vconvertercrew.vtouch.VTouchEventType
import com.vconvertercrew.vtouch.VTouchFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VTouchEngineTest {
    private val all = VTouchFeature.LANES or VTouchFeature.TAP or VTouchFeature.STEPS or VTouchFeature.SWIPE_BACK or
        VTouchFeature.PINCH or VTouchFeature.LONG_PRESS or VTouchFeature.FLICK

    private fun drain(engine: VTouchEngine): List<String> {
        val out = ArrayList<String>()
        while (true) {
            val e = engine.poll() ?: break
            out += "${e.type}:${e.lane}:${"%.2f".format(e.value)}"
        }
        return out
    }

    private fun script(engine: VTouchEngine, seed: Int): List<String> {
        val random = Random(seed)
        val log = ArrayList<String>()
        engine.viewport(1000f, 500f)
        engine.step(60f)
        var time = 0.0
        val down = HashMap<Long, Pair<Float, Float>>()
        repeat(400) {
            time += random.nextInt(1, 40)
            val id = random.nextLong(1, 4)
            val held = down[id]
            when {
                held == null && random.nextInt(4) == 0 -> {
                    val x = random.nextFloat() * 1000f
                    val y = random.nextFloat() * 500f
                    down[id] = x to y
                    engine.pointer(id, VTouchAction.DOWN, x, y, time)
                }
                held != null && random.nextInt(5) == 0 -> {
                    down.remove(id)
                    engine.pointer(id, if (random.nextInt(10) == 0) VTouchAction.CANCEL else VTouchAction.UP, held.first, held.second, time)
                }
                held != null -> {
                    val x = (held.first + random.nextFloat() * 80f - 40f).coerceIn(0f, 1000f)
                    val y = (held.second + random.nextFloat() * 80f - 40f).coerceIn(0f, 500f)
                    down[id] = x to y
                    engine.pointer(id, VTouchAction.MOVE, x, y, time)
                }
                else -> engine.tick(time)
            }
            log += drain(engine)
            log += "held=${(0 until 4).joinToString { engine.held(it).toString() }} p=${engine.pointerCount} d=${engine.dragging} z=${engine.pinching}"
        }
        return log
    }

    @Test
    fun kotlinEngineRecognizesGestures() {
        val engine = KotlinTouchEngine(VTouchConfig(features = all))
        engine.viewport(1000f, 500f)
        engine.pointer(1, VTouchAction.DOWN, 100f, 100f, 0.0)
        engine.pointer(1, VTouchAction.UP, 101f, 100f, 50.0)
        assertEquals(listOf("LANE_PRESS:0:0.00", "LANE_RELEASE:0:0.00", "TAP:-1:0.00"), drain(engine))
        engine.pointer(1, VTouchAction.DOWN, 100f, 100f, 1000.0)
        engine.tick(1600.0)
        assertTrue(drain(engine).any { it.startsWith("LONG_PRESS") })
        engine.pointer(1, VTouchAction.UP, 100f, 100f, 1700.0)
        assertTrue(drain(engine).none { it.startsWith("TAP") })
        val out = FloatArray(4)
        assertTrue(engine.shape(2, out))
        assertEquals(500f, out[0], 0.01f)
        engine.release()
    }

    @Test
    fun nativeMatchesKotlin() {
        if (System.getProperty("vconverter.touch.library") == null) return
        assertTrue(NativeTouch.loadError ?: "", NativeTouch.loaded)
        assertEquals("1.0.0", NativeTouch.version())
        for (seed in 0 until 12) {
            val layout = seed % 2
            val config = VTouchConfig(features = all, layout = layout, lanes = if (seed % 3 == 0) 7 else 4, density = 2f)
            val kotlin = KotlinTouchEngine(config)
            val native = NativeTouchEngine.create(config)!!
            assertTrue(native.native)
            val expected = script(kotlin, seed)
            val actual = script(native, seed)
            assertEquals("seed $seed", expected, actual)
            val a = FloatArray(4)
            val b = FloatArray(4)
            for (lane in 0 until config.lanes) {
                assertEquals(kotlin.shape(lane, a), native.shape(lane, b))
                for (i in 0 until 4) assertEquals(a[i], b[i], 0.01f)
                assertEquals(kotlin.laneAt(a[0], a[1]), native.laneAt(a[0], a[1]))
            }
            native.release()
        }
    }
}
