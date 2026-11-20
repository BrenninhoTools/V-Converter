package com.vconvertercrew.vconverter.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.core.FrameStats
import com.vconvertercrew.vconverter.core.fmt
import kotlin.math.max
import kotlin.math.min

enum class FpsMode(val label: String) {
    OFF("Off"),
    SIMPLE("FPS Only"),
    DETAILED("FPS + Memory"),
    GRAPH("FPS + Graph");

    companion object {
        fun parse(text: String?): FpsMode? = entries.firstOrNull { it.name.equals(text?.trim(), true) }
    }
}

enum class FpsCorner(val label: String, val alignment: Alignment) {
    TOP_LEFT("Top Left", Alignment.TopStart),
    TOP_RIGHT("Top Right", Alignment.TopEnd),
    BOTTOM_LEFT("Bottom Left", Alignment.BottomStart),
    BOTTOM_RIGHT("Bottom Right", Alignment.BottomEnd);

    companion object {
        fun parse(text: String?): FpsCorner? = entries.firstOrNull { it.name.equals(text?.trim(), true) }
    }
}

object FpsDisplay {
    const val MODE = "fpsMode"
    const val CORNER = "fpsCorner"
    const val COLORS = "fpsColors"
    const val BACKGROUND = "fpsBackground"

    var mode by mutableStateOf(FpsMode.SIMPLE)
    var corner by mutableStateOf(FpsCorner.TOP_LEFT)
    var colors by mutableStateOf(true)
    var background by mutableStateOf(false)

    val enabled: Boolean get() = mode != FpsMode.OFF

    fun load(legacy: Boolean?, get: (String) -> String?) {
        mode = FpsMode.parse(get(MODE)) ?: if (legacy == false) FpsMode.OFF else FpsMode.SIMPLE
        FpsCorner.parse(get(CORNER))?.let { corner = it }
        get(COLORS)?.toBooleanStrictOrNull()?.let { colors = it }
        get(BACKGROUND)?.toBooleanStrictOrNull()?.let { background = it }
    }

    private fun <T : Enum<T>> List<T>.step(current: T, by: Int): T = this[((indexOf(current) + by) % size + size) % size]

    fun submenu(save: (String, String) -> Unit, onEnabled: (Boolean) -> Unit = {}): MenuOption.Submenu = MenuOption.Submenu(
        "FPS Counter",
        "How the FPS counter looks: off, FPS only, FPS with frame times and memory, or with a frame time graph."
    ) {
        listOf(
            MenuOption.Stepper("Display", "FPS Only shows the FPS like FNF. FPS + Memory adds frame times, the 1% low and the memory. FPS + Graph also draws the last frames.", { mode.label }) { by ->
                mode = FpsMode.entries.step(mode, by)
                save(MODE, mode.name)
                onEnabled(enabled)
            },
            MenuOption.Stepper("Position", "Which corner of the screen it sits in.", { corner.label }) { by ->
                corner = FpsCorner.entries.step(corner, by)
                save(CORNER, corner.name)
            },
            MenuOption.Toggle("Color By Speed", "Turns yellow and red when the FPS drops, like Psych Engine.", { colors }) {
                colors = it
                save(COLORS, it.toString())
            },
            MenuOption.Toggle("Background", "Draws a dark box behind the text so it reads on bright screens.", { background }) {
                background = it
                save(BACKGROUND, it.toString())
            }
        )
    }
}

private fun megabytes(bytes: Long): String = fmt("%.1f MB", bytes / 1024.0 / 1024.0)

private fun speedColor(fps: Int, refresh: Int, enabled: Boolean): Color {
    if (!enabled || refresh <= 0 || fps <= 0) return Color.White
    return when {
        fps < refresh / 2 -> Color(0xFFFF4040)
        fps < refresh * 3 / 4 -> Color(0xFFFFD040)
        else -> Color.White
    }
}

@Composable
fun FpsCounter(modifier: Modifier = Modifier, fontFamily: FontFamily = FontFamily.Monospace, mode: FpsMode = FpsDisplay.mode) {
    if (mode == FpsMode.OFF) return
    val stats = remember { FrameStats() }
    var version by remember { mutableIntStateOf(0) }
    var graphTick by remember { mutableIntStateOf(0) }
    var memory by remember { mutableStateOf<Long?>(null) }
    var peak by remember { mutableStateOf(0L) }
    LaunchedEffect(mode) {
        stats.reset()
        while (true) {
            withFrameNanos { now ->
                if (stats.frame(now)) {
                    version++
                    if (mode != FpsMode.SIMPLE) {
                        memory = runCatching { MemoryStats.usedBytes() }.getOrNull()
                        memory?.let { if (it > peak) peak = it }
                    }
                }
                if (mode == FpsMode.GRAPH) graphTick++
            }
        }
    }
    val shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 0f)
    val color = speedColor(stats.fps.also { version }, stats.refresh, FpsDisplay.colors)
    val big = TextStyle(color = color, fontSize = 14.sp, fontFamily = fontFamily, fontWeight = FontWeight.Bold, shadow = shadow)
    val small = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = fontFamily, shadow = shadow)
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (FpsDisplay.background) Color.Black.copy(alpha = 0.45f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text("FPS: ${stats.fps}", style = big)
        if (mode != FpsMode.SIMPLE) {
            version
            Text(fmt("Frame: %.1f ms (worst %.1f)", stats.averageMs, stats.worstMs), style = small)
            Text("1% Low: ${stats.lowFps} FPS" + if (stats.refresh > 0) "  /  ${stats.refresh} Hz" else "", style = small)
            val used = memory
            if (used != null) {
                val limit = MemoryStats.limitBytes()
                Text("Memory: ${megabytes(used)}" + (limit?.let { " / ${megabytes(it)}" } ?: "") + "  (peak ${megabytes(peak)})", style = small)
            }
        }
        if (mode == FpsMode.GRAPH) {
            graphTick
            Canvas(Modifier.padding(top = 3.dp).size(180.dp, 44.dp)) {
                drawRect(Color.Black.copy(alpha = 0.35f))
                val budget = if (stats.refresh > 0) 1_000_000_000.0 / stats.refresh else 16_666_667.0
                val ceiling = budget * 3.0
                val bars = min(stats.samples, 120)
                val barWidth = size.width / 120f
                for (i in 0 until bars) {
                    val duration = stats.recent(i).toDouble()
                    val height = (min(duration, ceiling) / ceiling * size.height).toFloat()
                    val barColor = when {
                        duration > budget * 2.0 -> Color(0xFFFF4040)
                        duration > budget * 1.25 -> Color(0xFFFFD040)
                        else -> Color(0xFF40FF80)
                    }
                    val x = size.width - (i + 1) * barWidth
                    drawRect(barColor.copy(alpha = 0.85f), Offset(x, size.height - height), Size(max(1f, barWidth - 0.5f), height))
                }
                val line = (size.height - budget / ceiling * size.height).toFloat()
                drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, line), Offset(size.width, line), 1f)
            }
        }
    }
}

@Composable
fun FpsOverlay(modifier: Modifier = Modifier, fontFamily: FontFamily = FontFamily.Monospace) {
    if (!FpsDisplay.enabled) return
    Box(modifier.fillMaxSize()) {
        FpsCounter(Modifier.align(FpsDisplay.corner.alignment).padding(horizontal = 8.dp, vertical = 6.dp), fontFamily)
    }
}
