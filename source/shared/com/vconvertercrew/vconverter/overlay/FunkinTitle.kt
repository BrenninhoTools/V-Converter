package com.vconvertercrew.vconverter.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TitleArt(val font: AlphabetFont?, val logo: SpriteSheet?, val gf: SpriteSheet?, val enter: SpriteSheet?, val intro: List<List<String>>) {
    companion object {
        const val ATLAS_SCALE = 2f
        const val INTRO_TEXT = "data/introText.txt"

        fun imagePath(name: String) = "images/ui/title/$name.png"
        fun atlasPath(name: String) = "images/ui/title/$name.xml"

        fun parseIntro(raw: String?): List<List<String>> =
            raw.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { line -> line.split("--").map { it.trim() } }.toList()
                .ifEmpty { listOf(listOf("v-converter", "playtest")) }

        fun load(image: (String) -> ImageBitmap?, text: (String) -> String?): TitleArt = TitleArt(
            AlphabetFont.create(image(AlphabetFont.IMAGE), text(AlphabetFont.ATLAS)),
            SpriteSheet.create(image(imagePath("logoBumpin")), text(atlasPath("logoBumpin"))),
            SpriteSheet.create(image(imagePath("gfDanceTitle")), text(atlasPath("gfDanceTitle"))),
            SpriteSheet.create(image(imagePath("titleEnter")), text(atlasPath("titleEnter"))),
            parseIntro(text(INTRO_TEXT))
        )
    }
}

private const val TITLE_W = 1280f
private const val TITLE_H = 720f
private const val BPM = 102.0
private const val FPS = 24.0
private val DANCE_LEFT = listOf(30) + (0..14).toList()
private val DANCE_RIGHT = (15..29).toList()

private fun introLines(beat: Int, wacky: List<String>): List<String> = when (beat) {
    1, 2 -> listOf("brenninho", "vconverter crew")
    3 -> listOf("brenninho", "vconverter crew", "present")
    5, 6 -> listOf("not associated", "with")
    7 -> listOf("not associated", "with", "newgrounds")
    9, 10 -> listOf(wacky.getOrElse(0) { "" })
    11 -> listOf(wacky.getOrElse(0) { "" }, wacky.drop(1).joinToString(" "))
    13 -> listOf("friday")
    14 -> listOf("friday", "night")
    15 -> listOf("friday", "night", "converter")
    else -> emptyList()
}

@Composable
fun FunkinTitle(art: TitleArt, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val wacky = remember { art.intro.random() }
    var start by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var skippedAt by remember { mutableStateOf<Double?>(null) }
    var pressedAt by remember { mutableStateOf<Double?>(null) }
    var finished by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinish)
    val focus = remember { FocusRequester() }
    val beatLength = 60.0 / BPM

    fun elapsed(): Double = if (start == 0L) 0.0 else (now - start) / 1_000_000_000.0

    fun press() {
        val time = elapsed()
        when {
            skippedAt == null -> skippedAt = time
            pressedAt == null -> {
                pressedAt = time
                UiSound.click()
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        while (true) {
            withFrameNanos { frame ->
                if (start == 0L) start = frame
                now = frame
            }
            val time = elapsed()
            if (skippedAt == null && time >= beatLength * 16) skippedAt = beatLength * 16
            val pressed = pressedAt
            if (pressed != null && time - pressed >= 2.0 && !finished) {
                finished = true
                finish()
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        press()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures { press() } }
    ) {
        val time = elapsed()
        val k = min(size.width / TITLE_W, size.height / TITLE_H)
        val ox = (size.width - TITLE_W * k) / 2f
        val oy = (size.height - TITLE_H * k) / 2f
        val beat = floor(time / beatLength).toInt()
        val sinceBeat = time - beat * beatLength
        val skipped = skippedAt
        clipRect(ox, oy, ox + TITLE_W * k, oy + TITLE_H * k) {
            if (skipped == null) {
                drawIntro(art, introLines(beat, wacky), k, ox, oy)
            } else {
                drawTitle(art, beat, sinceBeat, time, pressedAt, k, ox, oy)
                val pressed = pressedAt
                val flash = if (pressed != null) max(0.0, 1.0 - (time - pressed)) else max(0.0, 1.0 - (time - skipped) / 4.0)
                if (flash > 0.0) drawRect(Color.White.copy(alpha = flash.toFloat()), Offset(ox, oy), Size(TITLE_W * k, TITLE_H * k))
            }
        }
    }
}

private fun DrawScope.drawIntro(art: TitleArt, lines: List<String>, k: Float, ox: Float, oy: Float) {
    val font = art.font ?: return
    lines.forEachIndexed { index, line ->
        val width = font.width(line) * k
        with(font) { draw(line, ox + (TITLE_W * k - width) / 2f, oy + (200f + index * 60f) * k, k, 1f, 0) }
    }
}

private fun DrawScope.drawTitle(art: TitleArt, beat: Int, sinceBeat: Double, time: Double, pressedAt: Double?, k: Float, ox: Float, oy: Float) {
    val scale = k * TitleArt.ATLAS_SCALE
    val step = (sinceBeat * FPS).toInt()
    art.gf?.let { sheet ->
        val frames = sheet.animation("gfDance")
        val indices = if (beat % 2 == 1) DANCE_RIGHT else DANCE_LEFT
        val frame = frames.getOrNull(indices[min(indices.lastIndex, step)])
        if (frame != null) with(sheet) { drawFrame(frame, ox + TITLE_W * 0.4f * k, oy + TITLE_H * 0.07f * k, scale) }
    }
    art.logo?.let { sheet ->
        val frames = sheet.animation("logo bumpin")
        frames.getOrNull(min(frames.lastIndex, step))?.let { frame -> with(sheet) { drawFrame(frame, ox - 150f * k, oy - 100f * k, scale) } }
    }
    art.enter?.let { sheet ->
        val frames = sheet.animation(if (pressedAt != null) "ENTER PRESSED" else "Press Enter to Begin")
        val loop = ((time - (pressedAt ?: 0.0)) * FPS).toInt()
        frames.getOrNull(loop % max(1, frames.size))?.let { frame -> with(sheet) { drawFrame(frame, ox + 100f * k, oy + TITLE_H * 0.8f * k, scale) } }
    }
}
