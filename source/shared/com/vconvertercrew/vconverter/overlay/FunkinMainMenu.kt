package com.vconvertercrew.vconverter.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import com.vconvertercrew.vtouch.vMenuNavigation
import com.vconvertercrew.vconverter.skin.MainMenuSkin
import com.vconvertercrew.vconverter.skin.SkinAnchor
import com.vconvertercrew.vconverter.skin.SkinJson
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainMenuEntry(val id: String, val label: String, val flicker: Boolean = true)

class MainMenuArt(
    val skin: MainMenuSkin,
    val backgroundArt: SkinImageArt?,
    val flashArt: SkinImageArt?,
    val items: Map<String, SpriteSheet>,
    val sprites: List<SkinSpriteArt>
) {
    val background: ImageBitmap? get() = backgroundArt?.image
    val flash: ImageBitmap? get() = flashArt?.image

    companion object {
        const val BACKGROUND = "images/ui/menu/menuBG.png"
        const val FLASH = "images/ui/menu/menuBGMagenta.png"

        fun load(ids: List<String>, image: (String) -> ImageBitmap?, text: (String) -> String?): MainMenuArt {
            val skin = SkinJson.mainMenu(text(SkinJson.MAIN_MENU))
            return MainMenuArt(
                skin,
                SkinImageArt.load(skin.background, image),
                SkinImageArt.load(skin.flash, image),
                ids.mapNotNull { id ->
                    val path = skin.itemPath(id)
                    SpriteSheet.create(image(SkinJson.png(path)), text(SkinJson.xml(path)))?.let { id to it }
                }.toMap(),
                SkinSpriteArt.loadAll(skin.sprites, image, text)
            )
        }
    }
}

private const val GAME_WIDTH = 1280f
private const val GAME_HEIGHT = 720f

@Composable
fun FunkinMainMenu(
    entries: List<MainMenuEntry>,
    art: MainMenuArt,
    version: String,
    onSelect: (MainMenuEntry) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    initial: Int = 0,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val skin = art.skin
    val all = entries
    val shown = remember(all, skin) { skin.arrange(all.map { it.id }).mapNotNull { id -> all.firstOrNull { it.id == id } } }
    @Suppress("NAME_SHADOWING") val entries = shown
    val spacing = skin.spacing
    val startId = all.getOrNull(initial)?.id
    var selected by remember { mutableIntStateOf(entries.indexOfFirst { it.id == startId }.coerceAtLeast(0)) }
    var clock by remember { mutableFloatStateOf(0f) }
    var confirming by remember { mutableStateOf<Int?>(null) }
    var flashVisible by remember { mutableStateOf(false) }
    var selectedVisible by remember { mutableStateOf(true) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val camera by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 1f, stiffness = skin.cameraStiffness.coerceAtLeast(1f)), label = "camera")
    val rects = remember { arrayOf<List<Rect>>(emptyList()) }

    fun shift(by: Int) {
        if (confirming != null || entries.isEmpty() || by == 0) return
        selected = ((selected + by) % entries.size + entries.size) % entries.size
        UiSound.click()
    }

    fun accept() {
        if (confirming != null || entries.isEmpty()) return
        val index = selected
        val entry = entries[index]
        confirming = index
        UiSound.click()
        scope.launch {
            launch {
                if (entry.flicker && art.flashArt != null) {
                    var elapsed = 0L
                    val step = skin.flashInterval.coerceAtLeast(16L)
                    while (elapsed < skin.flashDuration) {
                        flashVisible = !flashVisible
                        delay(step)
                        elapsed += step
                    }
                }
                flashVisible = false
            }
            launch { fade.animateTo(0f, tween(skin.fadeMillis.coerceAtLeast(0), easing = FastOutSlowInEasing)) }
            var elapsed = 0L
            val step = skin.blinkInterval.coerceAtLeast(16L)
            while (elapsed < skin.blinkDuration) {
                selectedVisible = !selectedVisible
                delay(step)
                elapsed += step
            }
            selectedVisible = true
            flashVisible = false
            FunkinTransition.go {
                onSelect(entry)
                confirming = null
                scope.launch { fade.snapTo(1f) }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) withFrameNanos { now ->
            if (start == 0L) start = now
            clock = (now - start) / 1_000_000_000f
        }
    }

    val area = remember { floatArrayOf(1280f, 720f) }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> shift(-1)
                    Key.DirectionDown, Key.S -> shift(1)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> accept()
                    Key.Escape, Key.Backspace -> onBack?.invoke() ?: return@onKeyEvent false
                    else -> return@onKeyEvent false
                }
                true
            }
            .onSizeChanged {
                area[0] = it.width.toFloat()
                area[1] = it.height.toFloat()
            }
            .vMenuNavigation(
                key = entries.size,
                step = { spacing * mainMenuScale(area[0], area[1]) * 0.6f },
                onMove = { shift(it) },
                onTap = { tap ->
                    if (confirming == null) {
                        val hit = rects[0].indexOfFirst { it.contains(tap) }
                        if (hit >= 0) {
                            if (hit == selected) accept() else {
                                selected = hit
                                UiSound.click()
                            }
                        }
                    }
                },
                onBack = { onBack?.invoke() }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val scale = mainMenuScale(w, h)
            val time = clock.toDouble()
            val middle = (entries.size - 1) / 2f
            val scroll = (camera - middle) * spacing * scale
            val backdrop = if (flashVisible) art.flashArt ?: art.backgroundArt else art.backgroundArt
            backdrop?.let { with(it) { drawCover(w, h, scroll) } }
            val area = SkinArea(0f, 0f, w, h, scale)
            art.sprites.filter { it.spec.layer < 0 }.forEach { sprite -> with(sprite) { draw(area, sprite.spec.play, time, scrollY = scroll) } }
            val hits = ArrayList<Rect>(entries.size)
            entries.forEachIndexed { index, entry ->
                val sheet = art.items[entry.id]
                val style = skin.style(entry.id)
                val active = index == selected
                val prefix = if (active) skin.selectedFor(entry.id, entry.label) else skin.idleFor(entry.id, entry.label)
                val frames = sheet?.prefixed(prefix).orEmpty()
                val frame = frames.getOrNull(((time * skin.itemFps).toInt()) % max(1, frames.size))
                val itemScale = scale * skin.itemScale * (style.scale ?: 1f)
                val slot = (index - middle) * spacing * scale
                val alpha = when {
                    confirming == null -> 1f
                    confirming == index -> if (selectedVisible) 1f else 0f
                    else -> fade.value
                }
                val fw = (frame?.frameWidth ?: 0) * itemScale
                val fh = (frame?.frameHeight ?: 0) * itemScale
                val (anchorLeft, anchorTop) = skin.itemsAnchor.place(0f, 0f, w, h, skin.itemsX + style.offsetX, skin.itemsY + style.offsetY, fw, fh, scale)
                val left = anchorLeft
                val top = anchorTop + slot
                val centerY = top + fh / 2f
                val band = spacing * scale / 2f
                if (frame != null && sheet != null) {
                    hits += Rect(min(left, w * 0.2f), centerY - band, max(left + fw, w * 0.8f), centerY + band)
                    if (alpha > 0f) with(sheet) { drawFrame(frame, left, top, itemScale, alpha) }
                } else {
                    val fallbackCenter = h / 2f + slot
                    hits += Rect(w * 0.2f, fallbackCenter - band, w * 0.8f, fallbackCenter + band)
                }
            }
            rects[0] = hits
            art.sprites.filter { it.spec.layer >= 0 }.forEach { sprite -> with(sprite) { draw(area, sprite.spec.play, time, scrollY = scroll) } }
        }
        if (entries.any { art.items[it.id] == null }) {
            entries.forEachIndexed { index, entry ->
                if (art.items[entry.id] != null) return@forEachIndexed
                Text(
                    entry.label.uppercase(),
                    color = if (index == selected) Color.White else Color.Black,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center).offset(y = ((index - (entries.size - 1) / 2f) * 90f).dp)
                )
            }
        }
        overlay()
        val label = skin.version
        if (label.visible) {
            Text(
                label.format(mapOf("version" to version)),
                color = Color(label.color),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = label.size.sp, shadow = label.shadow?.let { Shadow(Color(it), blurRadius = 3f) }),
                modifier = Modifier.align(label.anchor.alignment()).padding(horizontal = label.x.dp, vertical = label.y.dp)
            )
        }
    }
}

private fun SkinAnchor.alignment(): Alignment = when (this) {
    SkinAnchor.TOP_LEFT -> Alignment.TopStart
    SkinAnchor.TOP -> Alignment.TopCenter
    SkinAnchor.TOP_RIGHT -> Alignment.TopEnd
    SkinAnchor.LEFT -> Alignment.CenterStart
    SkinAnchor.CENTER -> Alignment.Center
    SkinAnchor.RIGHT -> Alignment.CenterEnd
    SkinAnchor.BOTTOM_LEFT -> Alignment.BottomStart
    SkinAnchor.BOTTOM -> Alignment.BottomCenter
    SkinAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
}

private fun mainMenuScale(width: Float, height: Float): Float {
    val wide = min(width / GAME_WIDTH, height / GAME_HEIGHT)
    val narrow = width / 900f
    return max(wide, min(narrow, height / GAME_HEIGHT)).coerceAtLeast(0.2f)
}
