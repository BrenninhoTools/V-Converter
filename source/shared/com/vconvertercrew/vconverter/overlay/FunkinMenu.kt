package com.vconvertercrew.vconverter.overlay

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.editor.AtlasFrame
import com.vconvertercrew.vconverter.editor.SpriteAtlas
import com.vconvertercrew.vconverter.skin.SkinJson
import androidx.compose.ui.layout.onSizeChanged
import com.vconvertercrew.vtouch.vMenuNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SpriteSheet(val image: ImageBitmap, val atlas: SpriteAtlas) {
    private val groups: Map<String, List<AtlasFrame>> = atlas.frames
        .groupBy { frame -> if (frame.name.length > 4 && frame.name.takeLast(4).all { it.isDigit() }) frame.name.dropLast(4) else frame.name }
        .mapValues { (_, frames) -> frames.sortedBy { it.name } }

    private val prefixes = HashMap<String, List<AtlasFrame>>()

    fun animation(prefix: String): List<AtlasFrame> = groups[prefix].orEmpty()

    fun prefixed(prefix: String): List<AtlasFrame> = groups[prefix] ?: prefixes.getOrPut(prefix) {
        atlas.frames.filter { it.name.startsWith(prefix) }.sortedBy { it.name }
    }

    fun DrawScope.drawFrame(frame: AtlasFrame, left: Float, top: Float, scale: Float, alpha: Float = 1f, colorFilter: ColorFilter? = null) {
        drawImage(
            image = image,
            srcOffset = IntOffset(frame.x, frame.y),
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset((left - frame.frameX * scale).roundToInt(), (top - frame.frameY * scale).roundToInt()),
            dstSize = IntSize(max(1, (frame.width * scale).roundToInt()), max(1, (frame.height * scale).roundToInt())),
            alpha = alpha,
            colorFilter = colorFilter
        )
    }

    companion object {
        fun create(image: ImageBitmap?, xml: String?): SpriteSheet? {
            if (image == null || xml == null) return null
            val atlas = runCatching { SpriteAtlas.parse(xml) }.getOrNull() ?: return null
            return SpriteSheet(image, atlas).takeIf { atlas.frames.isNotEmpty() }
        }
    }
}

class AlphabetFont(val sheet: SpriteSheet) {
    val lineHeight: Float = listOf("A bold", "B bold", "O bold").flatMap { sheet.animation(it) }.maxOfOrNull { it.frameHeight }?.toFloat() ?: 70f

    fun glyph(char: Char, bold: Boolean): List<AtlasFrame> {
        val prefix = when {
            char.isLetter() && char.code < 128 -> when {
                bold -> "${char.uppercaseChar()} bold"
                char.isUpperCase() -> "$char capital"
                else -> "$char lowercase"
            }
            char.isDigit() -> char.toString()
            else -> symbols[char] ?: char.toString()
        }
        return sheet.animation(prefix).ifEmpty { if (char.isLetter()) sheet.animation("${char.uppercaseChar()} bold") else emptyList() }
    }

    fun width(text: String, bold: Boolean = true): Float = text.fold(0f) { total, char -> total + advance(char, bold) }

    private fun advance(char: Char, bold: Boolean): Float =
        if (char == ' ') SPACE else glyph(char, bold).firstOrNull()?.let { it.frameWidth + GAP } ?: SPACE

    fun DrawScope.draw(text: String, left: Float, top: Float, scale: Float, alpha: Float, tick: Int, bold: Boolean = true) {
        var x = left
        text.forEach { char ->
            val frames = if (char == ' ') emptyList() else glyph(char, bold)
            val frame = frames.getOrNull(tick % max(1, frames.size))
            if (frame != null) {
                val y = top + (lineHeight - frame.frameHeight) * scale
                with(sheet) { drawFrame(frame, x, y, scale, alpha) }
            }
            x += advance(char, bold) * scale
        }
    }

    companion object {
        const val SPACE = 40f
        const val GAP = 2f
        const val IMAGE = "images/ui/menu/alphabet.png"
        const val ATLAS = "images/ui/menu/alphabet.xml"
        const val BACKGROUND = "images/ui/menu/menuBGMagenta.png"
        const val BACK_IMAGE = "images/ui/mobile/backButton.png"
        const val BACK_ATLAS = "images/ui/mobile/backButton.xml"

        private val symbols = mapOf(
            '#' to "hashtag ", '$' to "dollarsign ", '&' to "amp", '\'' to "apostraphie", ',' to "comma",
            '!' to "exclamation point", '/' to "forward slash", '.' to "period", '?' to "question mark"
        )

        fun create(image: ImageBitmap?, xml: String?): AlphabetFont? = SpriteSheet.create(image, xml)?.let { AlphabetFont(it) }
    }
}

sealed class MenuOption(val label: String, val hint: String) {
    abstract fun value(): String?

    class Toggle(label: String, hint: String, private val checked: () -> Boolean, val set: (Boolean) -> Unit) : MenuOption(label, hint) {
        override fun value(): String = if (checked()) "ON" else "OFF"
        fun toggle() = set(!checked())
    }

    class Stepper(label: String, hint: String, private val display: () -> String, val step: (Int) -> Unit) : MenuOption(label, hint) {
        override fun value(): String = display()
    }

    class TextEntry(label: String, hint: String, val current: () -> String, val set: (String) -> Unit, val placeholder: String = "") : MenuOption(label, hint) {
        override fun value(): String? = null
    }

    class Action(label: String, hint: String, val run: () -> Unit) : MenuOption(label, hint) {
        override fun value(): String? = null
    }

    class Submenu(label: String, hint: String, val title: String = label, val options: () -> List<MenuOption>) : MenuOption(label, hint) {
        override fun value(): String? = null
    }

    fun text(): String = value()?.let { "$label: $it" } ?: label
}

class MenuArt(val font: AlphabetFont?, val background: ImageBitmap?, val back: BackButtonArt?) {
    companion object {
        fun load(image: (String) -> ImageBitmap?, text: (String) -> String?): MenuArt = MenuArt(
            AlphabetFont.create(image(AlphabetFont.IMAGE), text(AlphabetFont.ATLAS)),
            image(AlphabetFont.BACKGROUND),
            BackButtonArt.load(SkinJson.backButton(text(SkinJson.BACK_BUTTON)), image, text)
        )
    }
}

@Composable
fun FunkinOptionsMenu(
    title: String,
    options: List<MenuOption>,
    art: MenuArt,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeRequests: Int = 0,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val pages = remember { mutableStateListOf<MenuOption.Submenu>() }
    val returns = remember { mutableStateListOf<Int>() }
    val page = pages.lastOrNull()
    val shown = page?.options?.invoke() ?: options
    val heading = page?.let { "$title / ${it.title}" } ?: title
    val live = rememberUpdatedState(shown)
    val closeAction = rememberUpdatedState(onClose)
    var selected by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MenuOption.TextEntry?>(null) }
    var draft by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }
    var backFrame by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.85f, stiffness = 260f), label = "menu")
    val fade by animateFloatAsState(if (closing) 0f else 1f, tween(180), label = "fade")
    val ticker = rememberInfiniteTransition(label = "alphabet")
    val tickValue by ticker.animateFloat(0f, 4f, infiniteRepeatable(tween(4 * 1000 / 24, easing = LinearEasing), RepeatMode.Restart), label = "tick")
    val tick = tickValue.toInt().coerceIn(0, 3)
    val count = shown.size
    val rects = remember { arrayOf<List<Rect>>(emptyList()) }
    val backRect = remember { arrayOf(Rect.Zero) }

    fun close() {
        if (closing) return
        closing = true
        UiSound.click()
        scope.launch {
            art.back?.takeIf { it.skin.playOnPress }?.let { back ->
                for (i in back.frames.indices) {
                    backFrame = i
                    delay(back.skin.frameMillis)
                }
            }
            FunkinTransition.go { closeAction.value() }
        }
    }

    fun back() {
        if (closing) return
        if (pages.isEmpty()) {
            close()
            return
        }
        pages.removeAt(pages.lastIndex)
        selected = returns.removeAt(returns.lastIndex)
        UiSound.click()
    }

    fun move(change: Int) {
        val size = live.value.size
        if (size == 0 || change == 0) return
        selected = ((selected + change) % size + size) % size
        UiSound.click()
    }

    fun accept(direction: Int = 0) {
        if (closing) return
        when (val option = live.value.getOrNull(selected)) {
            is MenuOption.Toggle -> option.toggle()
            is MenuOption.Stepper -> option.step(if (direction == 0) 1 else direction)
            is MenuOption.TextEntry -> if (direction == 0) {
                draft = option.current()
                editing = option
            }
            is MenuOption.Action -> if (direction == 0) option.run()
            is MenuOption.Submenu -> if (direction >= 0) {
                returns.add(selected)
                pages.add(option)
                selected = 0
            }
            null -> return
        }
        UiSound.click()
    }

    LaunchedEffect(closeRequests) {
        if (closeRequests > 0) {
            if (editing != null) editing = null else back()
        }
    }

    LaunchedEffect(count) {
        if (selected >= count) selected = max(0, count - 1)
    }

    LaunchedEffect(editing) {
        if (editing == null) runCatching { focus.requestFocus() }
    }

    val area = remember { floatArrayOf(1280f, 720f) }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || editing != null) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> move(-1)
                    Key.DirectionDown, Key.S -> move(1)
                    Key.DirectionLeft, Key.A -> accept(-1)
                    Key.DirectionRight, Key.D -> accept(1)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> accept()
                    Key.Escape, Key.Backspace, Key.Back -> back()
                    else -> return@onKeyEvent false
                }
                true
            }
            .onSizeChanged {
                area[0] = it.width.toFloat()
                area[1] = it.height.toFloat()
            }
            .vMenuNavigation(
                key = count to pages.size,
                step = { 120f * 1.3f * menuScale(area[0], area[1]) },
                onMove = { move(it) },
                onTap = { tap ->
                    if (backRect[0].contains(tap)) {
                        back()
                    } else {
                        val hit = rects[0].indexOfFirst { it.contains(tap) }
                        if (hit >= 0) {
                            if (hit == selected) accept() else {
                                selected = hit
                                UiSound.click()
                            }
                        }
                    }
                },
                onBack = { back() },
                enabled = { editing == null }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            art.background?.let { bg ->
                val cover = max(w / bg.width, h / bg.height) * 1.1f
                val dw = bg.width * cover
                val dh = bg.height * cover
                drawImage(bg, dstOffset = IntOffset(((w - dw) / 2).roundToInt(), ((h - dh) / 2).roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()), alpha = fade)
            }
            val scale = menuScale(w, h)
            val font = art.font
            val hits = ArrayList<Rect>(count)
            shown.forEachIndexed { index, option ->
                val target = index - position
                val x = (90f + target * 20f) * scale
                val y = h * 0.48f + target * 1.3f * 120f * scale
                val text = option.text().uppercase()
                val alpha = (if (index == selected) 1f else 0.6f) * fade
                val lineHeight = (font?.lineHeight ?: 70f) * scale
                val width = (font?.width(text) ?: text.length * 45f) * scale
                hits += Rect(0f, y - 10f * scale, max(w, x + width), y + lineHeight + 10f * scale)
                if (y + lineHeight < 0 || y > h) return@forEachIndexed
                if (font != null) with(font) { draw(text, x, y, scale, alpha, tick) }
            }
            rects[0] = hits
            backRect[0] = art.back?.let { back -> with(back) { draw(backFrame, scale, fade) } } ?: Rect.Zero
        }
        if (art.font == null) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) {
                shown.forEachIndexed { index, option ->
                    Text(option.text().uppercase(), color = Color.Black.copy(alpha = if (index == selected) 1f else 0.6f), fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(
            heading.uppercase(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 4.dp)
        )
        overlay()
        shown.getOrNull(selected)?.let { option ->
            val extra = when (option) {
                is MenuOption.TextEntry -> option.current().ifBlank { option.placeholder.ifBlank { "(empty)" } }.let { "\n$it" }
                else -> ""
            }
            Text(
                option.hint + extra,
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 120.dp, bottom = 16.dp)
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        editing?.let { option ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1B26)).padding(18.dp)
                ) {
                    Text(option.label.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        placeholder = { Text(option.placeholder) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            option.set(draft)
                            editing = null
                        }),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFEA71FD)),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                editing = null
                                true
                            } else false
                        }
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = UiSound.wrap { editing = null }) { Text("CANCEL", color = Color(0xFFB8B8C8)) }
                        TextButton(onClick = UiSound.wrap {
                            option.set(draft)
                            editing = null
                        }) { Text("SAVE", color = Color(0xFFEA71FD), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun menuScale(width: Float, height: Float): Float = min(width / 1300f, height / 720f).coerceAtLeast(0.2f)
