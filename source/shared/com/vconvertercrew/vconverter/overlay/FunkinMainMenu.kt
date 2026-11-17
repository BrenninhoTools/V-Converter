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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainMenuEntry(val id: String, val label: String, val flicker: Boolean = true)

class MainMenuArt(val background: ImageBitmap?, val flash: ImageBitmap?, val items: Map<String, SpriteSheet>) {
    companion object {
        const val BACKGROUND = "images/ui/menu/menuBG.png"
        const val FLASH = "images/ui/menu/menuBGMagenta.png"

        fun itemImage(id: String) = "images/ui/menu/main/$id.png"
        fun itemAtlas(id: String) = "images/ui/menu/main/$id.xml"

        fun load(ids: List<String>, image: (String) -> ImageBitmap?, text: (String) -> String?): MainMenuArt = MainMenuArt(
            image(BACKGROUND),
            image(FLASH),
            ids.mapNotNull { id -> SpriteSheet.create(image(itemImage(id)), text(itemAtlas(id)))?.let { id to it } }.toMap()
        )
    }
}

private const val GAME_WIDTH = 1280f
private const val GAME_HEIGHT = 720f
private const val ITEM_SPACING = 160f

@Composable
fun FunkinMainMenu(
    entries: List<MainMenuEntry>,
    art: MainMenuArt,
    version: String,
    onSelect: (MainMenuEntry) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    initial: Int = 0
) {
    var selected by remember { mutableIntStateOf(initial.coerceIn(0, max(0, entries.lastIndex))) }
    var confirming by remember { mutableStateOf<Int?>(null) }
    var flashVisible by remember { mutableStateOf(false) }
    var selectedVisible by remember { mutableStateOf(true) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val camera by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 1f, stiffness = 60f), label = "camera")
    val ticker = rememberInfiniteTransition(label = "items")
    val tickValue by ticker.animateFloat(0f, 72f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "tick")
    val tick = tickValue.toInt()
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
                if (entry.flicker) {
                    var elapsed = 0L
                    while (elapsed < 1100L) {
                        flashVisible = !flashVisible
                        delay(150)
                        elapsed += 150
                    }
                }
                flashVisible = false
            }
            launch { fade.animateTo(0f, tween(400, easing = FastOutSlowInEasing)) }
            var elapsed = 0L
            while (elapsed < 1000L) {
                selectedVisible = !selectedVisible
                delay(60)
                elapsed += 60
            }
            selectedVisible = true
            onSelect(entry)
            fade.snapTo(1f)
            flashVisible = false
            confirming = null
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFFFDE871))
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
            .pointerInput(entries.size) {
                detectTapGestures { tap ->
                    if (confirming != null) return@detectTapGestures
                    val hit = rects[0].indexOfFirst { it.contains(tap) }
                    if (hit < 0) return@detectTapGestures
                    if (hit == selected) accept() else {
                        selected = hit
                        UiSound.click()
                    }
                }
            }
            .pointerInput(entries.size) {
                detectVerticalDragGestures(onDragEnd = { dragged = 0f }) { change, amount ->
                    change.consume()
                    dragged += amount
                    val step = ITEM_SPACING * mainMenuScale(size.width.toFloat(), size.height.toFloat()) * 0.6f
                    while (abs(dragged) > step) {
                        shift(if (dragged > 0) -1 else 1)
                        dragged -= if (dragged > 0) step else -step
                    }
                }
            }
            .pointerInput(entries.size) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta > 0.2f) shift(1) else if (delta < -0.2f) shift(-1)
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val scale = mainMenuScale(w, h)
            val middle = (entries.size - 1) / 2f
            val parallax = (camera - middle) * ITEM_SPACING * scale * 0.18f
            drawCover(if (flashVisible) art.flash ?: art.background else art.background, w, h, parallax)
            val hits = ArrayList<Rect>(entries.size)
            entries.forEachIndexed { index, entry ->
                val sheet = art.items[entry.id]
                val active = index == selected
                val frames = sheet?.animation("${entry.label} ${if (active) "white" else "basic"}").orEmpty()
                val frame = frames.getOrNull(tick % max(1, frames.size))
                val centerY = h / 2f + (index - middle) * ITEM_SPACING * scale
                val alpha = when {
                    confirming == null -> 1f
                    confirming == index -> if (selectedVisible) 1f else 0f
                    else -> fade.value
                }
                if (frame != null && sheet != null) {
                    val fw = frame.frameWidth * scale
                    val fh = frame.frameHeight * scale
                    val left = (w - fw) / 2f
                    val top = centerY - fh / 2f
                    hits += Rect(min(left, w * 0.2f), centerY - ITEM_SPACING * scale / 2f, max(left + fw, w * 0.8f), centerY + ITEM_SPACING * scale / 2f)
                    if (alpha > 0f) with(sheet) { drawFrame(frame, left, top, scale, alpha) }
                } else {
                    hits += Rect(w * 0.2f, centerY - ITEM_SPACING * scale / 2f, w * 0.8f, centerY + ITEM_SPACING * scale / 2f)
                }
            }
            rects[0] = hits
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
        Text(
            version,
            color = Color.White,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, shadow = Shadow(Color.Black, blurRadius = 3f)),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

private fun DrawScope.drawCover(image: ImageBitmap?, w: Float, h: Float, parallax: Float) {
    image ?: return
    val cover = max(w / image.width, h / image.height) * 1.1f
    val dw = image.width * cover
    val dh = image.height * cover
    drawImage(
        image,
        dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f - parallax).roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
    )
}

private fun mainMenuScale(width: Float, height: Float): Float {
    val wide = min(width / GAME_WIDTH, height / GAME_HEIGHT)
    val narrow = width / 900f
    return max(wide, min(narrow, height / GAME_HEIGHT)).coerceAtLeast(0.2f)
}
