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
import com.vconvertercrew.vconverter.skin.IntroState
import com.vconvertercrew.vconverter.skin.SkinJson
import com.vconvertercrew.vconverter.skin.TitleSkin
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TitleArt(
    val font: AlphabetFont?,
    val skin: TitleSkin,
    val sprites: List<SkinSpriteArt>,
    val introSprites: List<SkinSpriteArt>,
    val intro: List<List<String>>
) {
    companion object {
        const val INTRO_TEXT = "data/introText.txt"

        fun parseIntro(raw: String?): List<List<String>> =
            raw.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { line -> line.split("--").map { it.trim() } }.toList()
                .ifEmpty { listOf(listOf("v-converter", "playtest")) }

        fun load(image: (String) -> ImageBitmap?, text: (String) -> String?): TitleArt {
            val skin = SkinJson.title(text(SkinJson.TITLE))
            return TitleArt(
                AlphabetFont.create(image(AlphabetFont.IMAGE), text(AlphabetFont.ATLAS)),
                skin,
                SkinSpriteArt.loadAll(skin.sprites, image, text),
                SkinSpriteArt.loadAll(skin.introSprites, image, text),
                parseIntro(text(skin.introText) ?: text(INTRO_TEXT))
            )
        }
    }
}

private const val TITLE_W = 1280f
private const val TITLE_H = 720f

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
    val skin = art.skin
    val beatLength = skin.beatLength
    val background = Color(skin.background)
    val flashColor = Color(skin.flashColor)

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
            if (skippedAt == null && time >= beatLength * skin.introEnd) skippedAt = beatLength * skin.introEnd
            val pressed = pressedAt
            if (pressed != null && time - pressed >= skin.confirmDelay && !finished) {
                finished = true
                FunkinTransition.go { finish() }
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .background(background)
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
        val area = SkinArea(ox, oy, TITLE_W * k, TITLE_H * k, k)
        clipRect(ox, oy, ox + TITLE_W * k, oy + TITLE_H * k) {
            if (skipped == null) {
                drawIntro(art, skin.intro(beat, wacky), area, time)
            } else {
                drawTitle(art, beat, sinceBeat, time, pressedAt, area)
                val pressed = pressedAt
                val flash = if (pressed != null) fadeOut(time - pressed, skin.confirmFlash) else fadeOut(time - skipped, skin.skipFlash)
                if (flash > 0.0) drawRect(flashColor.copy(alpha = (flashColor.alpha * flash).toFloat()), Offset(ox, oy), Size(TITLE_W * k, TITLE_H * k))
            }
        }
    }
}

private fun fadeOut(elapsed: Double, length: Double): Double = if (length <= 0.0) 0.0 else max(0.0, 1.0 - elapsed / length)

private fun DrawScope.drawIntro(art: TitleArt, state: IntroState, area: SkinArea, time: Double) {
    art.introSprites.forEach { sprite ->
        if (sprite.spec.id in state.sprites) with(sprite) { draw(area, sprite.spec.play, time, art.skin.atlasScale) }
    }
    val font = art.font ?: return
    val scale = area.scale * art.skin.introScale
    state.lines.forEachIndexed { index, line ->
        val width = font.width(line) * scale
        with(font) { draw(line, area.left + (area.width - width) / 2f, area.top + (art.skin.introTop + index * art.skin.introSpacing) * area.scale, scale, 1f, 0) }
    }
}

private fun DrawScope.drawTitle(art: TitleArt, beat: Int, sinceBeat: Double, time: Double, pressedAt: Double?, area: SkinArea) {
    art.sprites.forEach { sprite ->
        val spec = sprite.spec
        val (animation, seconds) = when {
            pressedAt != null && spec.pressed != null -> spec.pressed to time - pressedAt
            spec.beat.isNotEmpty() -> spec.beat[((beat % spec.beat.size) + spec.beat.size) % spec.beat.size] to sinceBeat
            else -> spec.play to time
        }
        with(sprite) { draw(area, animation, seconds, art.skin.atlasScale) }
    }
}
