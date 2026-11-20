package com.vconvertercrew.vconverter.converterview

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.chart.preview.ChartField
import com.vconvertercrew.vconverter.chart.preview.ChartPreview
import com.vconvertercrew.vconverter.chart.preview.ChartTweaks
import com.vconvertercrew.vconverter.chartview.ChartStrip
import com.vconvertercrew.vconverter.chartview.ChartStripColors
import com.vconvertercrew.vconverter.chartview.formatBpm
import com.vconvertercrew.vconverter.chartview.formatDuration
import com.vconvertercrew.vconverter.convert.ConvertedOutput
import com.vconvertercrew.vconverter.core.CharacterSlot
import com.vconvertercrew.vconverter.overlay.FunkinOptionsMenu
import com.vconvertercrew.vconverter.overlay.FunkinTransition
import com.vconvertercrew.vconverter.overlay.HealthIcons
import com.vconvertercrew.vconverter.overlay.MenuArt
import com.vconvertercrew.vconverter.overlay.MenuOption
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vtouch.vMenuNavigation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ConverterRow(
    val name: String,
    val detail: String,
    val tag: String,
    val icon: String,
    val slot: CharacterSlot? = null,
    val warning: String? = null
)

class ConverterMenuSpec(
    val title: String,
    val direction: String,
    val accent: Color,
    val rows: List<ConverterRow>,
    val busy: Boolean,
    val canConvert: Boolean,
    val output: ConvertedOutput?,
    val status: String?,
    val statusError: Boolean,
    val onAddFiles: () -> Unit,
    val onAddFolder: (() -> Unit)?,
    val onRemove: (Int) -> Unit,
    val onClear: () -> Unit,
    val onSlot: ((Int, CharacterSlot) -> Unit)?,
    val onConvert: () -> Unit,
    val onSaveZip: () -> Unit,
    val onSaveFolder: (() -> Unit)?,
    val zipLabel: String = "Save As Zip",
    val folderLabel: String = "Save To Mod Folder",
    val onPlayTest: (() -> Unit)? = null,
    val onTestGame: (() -> Unit)? = null,
    val testGameLabel: String = "Test In Friday Night Funkin'",
    val options: List<MenuOption> = emptyList(),
    val previews: List<ChartPreview> = emptyList(),
    val preview: ChartPreview? = null,
    val onPreview: ((ChartPreview) -> Unit)? = null,
    val showingAfter: Boolean = false,
    val onToggleAfter: (() -> Unit)? = null,
    val onBack: () -> Unit
)

class ConverterArt(val menu: MenuArt?, val background: ImageBitmap?, val icons: HealthIcons) {
    companion object {
        fun load(image: (String) -> ImageBitmap?, text: (String) -> String?): ConverterArt = ConverterArt(
            MenuArt.last ?: MenuArt.load(image, text),
            image("images/ui/menu/menuDesat.png") ?: image("images/ui/menu/menuBG.png"),
            HealthIcons(image)
        )
    }
}

object ConverterMenus {
    fun chartOptions(
        fields: Set<ChartField>,
        tweaks: () -> ChartTweaks,
        update: ((ChartTweaks) -> ChartTweaks) -> Unit,
        difficulties: List<String>,
        detectedSpeed: (String) -> Double?,
        setSpeed: (String, String) -> Unit,
        sample: ChartPreview?,
        renames: List<Triple<String, () -> String, (String) -> Unit>> = emptyList()
    ): List<MenuOption> = buildList {
        fun text(label: String, hint: String, placeholder: String?, get: (ChartTweaks) -> String, set: (ChartTweaks, String) -> ChartTweaks) {
            add(MenuOption.TextEntry(label, hint, { get(tweaks()).ifEmpty { placeholder.orEmpty() } }, { value -> update { set(it, value.trim()) } }, placeholder.orEmpty()))
        }
        if (ChartField.SONG_NAME in fields) text("Song Name", "Empty keeps the chart's own name.", null, { it.songName }) { t, v -> t.copy(songName = v) }
        if (ChartField.ARTIST in fields) text("Artist", "Shown in Freeplay.", null, { it.artist }) { t, v -> t.copy(artist = v) }
        if (ChartField.ARTIST in fields) text("Charter", "Who made the chart.", null, { it.charter }) { t, v -> t.copy(charter = v) }
        if (ChartField.CHARACTERS in fields) {
            text("Player", "Empty keeps the chart's player.", sample?.player, { it.player }) { t, v -> t.copy(player = v) }
            text("Opponent", "Empty keeps the chart's opponent.", sample?.opponent, { it.opponent }) { t, v -> t.copy(opponent = v) }
            text("Girlfriend", "Empty keeps the chart's girlfriend.", sample?.girlfriend, { it.girlfriend }) { t, v -> t.copy(girlfriend = v) }
        }
        if (ChartField.STAGE in fields) text("Stage", "Empty keeps the chart's stage.", sample?.stage, { it.stage }) { t, v -> t.copy(stage = v) }
        if (ChartField.NOTE_STYLE in fields) {
            val styles = listOf("" to "Auto", "funkin" to "Funkin", "pixel" to "Pixel")
            add(MenuOption.Stepper("Note Style", "Auto keeps what the chart uses.", { styles.firstOrNull { it.first == tweaks().noteStyle }?.second ?: "Auto" }) { by ->
                val index = styles.indexOfFirst { it.first == tweaks().noteStyle }.coerceAtLeast(0)
                val next = styles[((index + by) % styles.size + styles.size) % styles.size].first
                update { it.copy(noteStyle = next) }
            })
        }
        if (ChartField.SPEED in fields) difficulties.forEach { difficulty ->
            val detected = detectedSpeed(difficulty)
            add(MenuOption.TextEntry("Speed ${difficulty.replaceFirstChar { it.uppercase() }}", "Scroll speed for $difficulty. Empty keeps ${detected?.let(::formatBpm) ?: "the chart's"}.", {
                tweaks().speeds[difficulty]?.let(::formatBpm) ?: detected?.let(::formatBpm).orEmpty()
            }, { setSpeed(difficulty, it) }, detected?.let(::formatBpm).orEmpty()))
        }
        if (ChartField.KEEP_EVENTS in fields) add(MenuOption.Toggle("Keep Psych Events", "Events without a V-Slice twin stay in the chart, with SongEvent scripts for the built-in ones.", { tweaks().keepUnknownEvents }) { value ->
            update { it.copy(keepUnknownEvents = value) }
        })
        renames.forEach { (label, get, set) -> add(MenuOption.TextEntry("Difficulty: $label", "Renames the difficulty this chart becomes.", get, set)) }
        add(MenuOption.Action("Reset Options", "Puts every option back to what the chart already uses.") { update { ChartTweaks(keepUnknownEvents = it.keepUnknownEvents) } })
    }

    fun iconFor(name: String): String {
        val base = name.substringAfterLast('/').substringBeforeLast('.').lowercase()
        return HealthIcons.resolve(base)
    }
}

private class ConverterItem(
    val label: String,
    val hint: String,
    val icon: String?,
    val enabled: Boolean,
    val row: Int = -1,
    val run: () -> Unit = {},
    val cycle: ((Int) -> Unit)? = null
)

private const val MAX_CHARS = 24
private fun shorten(text: String): String = if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS - 3).trimEnd() + "..."

@Composable
fun FunkinConverterMenu(spec: ConverterMenuSpec, art: ConverterArt, stripColors: ChartStripColors, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val font = art.menu?.font
    val focus = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(0) }
    var optionsOpen by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val back by rememberUpdatedState(spec.onBack)
    val area = remember { floatArrayOf(1280f, 720f) }
    val hits = remember { arrayOf<List<Rect>>(emptyList()) }

    fun whenReady(action: () -> Unit) {
        if (spec.output != null) action() else if (spec.canConvert && !spec.busy) {
            pending = action
            spec.onConvert()
        }
    }

    val ready = spec.output != null
    val items = buildList {
        add(ConverterItem("Add Files", "Pick ${spec.title.lowercase()} files or archives (.zip, .rar, .7z).", "face", !spec.busy, run = spec.onAddFiles))
        spec.onAddFolder?.let { add(ConverterItem("Add Folder", "Adds every matching file inside a folder.", "face", !spec.busy, run = it)) }
        spec.rows.forEachIndexed { index, row ->
            val slot = row.slot
            add(ConverterItem(
                shorten(row.name.substringAfterLast('/').substringBeforeLast('.')),
                listOfNotNull(row.detail, slot?.let { "LEFT / RIGHT: ${it.label.uppercase()} SIDE" }, "DELETE removes it.").joinToString("\n"),
                row.icon,
                !spec.busy,
                row = index,
                cycle = if (slot != null && spec.onSlot != null) { by ->
                    val all = CharacterSlot.entries
                    spec.onSlot.invoke(index, all[((all.indexOf(slot) + by) % all.size + all.size) % all.size])
                } else null
            ))
        }
        if (spec.previews.isNotEmpty() && spec.onPreview != null) {
            val current = spec.preview
            add(ConverterItem(
                "Preview",
                "LEFT / RIGHT pick the chart to look at. ENTER swaps between before and after converting.",
                "bf",
                true,
                run = { spec.onToggleAfter?.invoke() },
                cycle = { by ->
                    val list = spec.previews
                    val index = list.indexOf(current).coerceAtLeast(0)
                    spec.onPreview.invoke(list[((index + by) % list.size + list.size) % list.size])
                }
            ))
        }
        if (spec.options.isNotEmpty()) add(ConverterItem("Options", "Song name, characters, stage, scroll speed and more.", "gf", !spec.busy, run = { optionsOpen = true }))
        add(ConverterItem(if (ready) "Convert Again" else "Convert", "Converts ${spec.direction}.", "bf", spec.canConvert && !spec.busy, run = spec.onConvert))
        spec.onSaveFolder?.let { save -> add(ConverterItem(spec.folderLabel, "Converts if needed, then writes into a mod folder.", "pico", spec.canConvert && !spec.busy, run = { whenReady(save) })) }
        add(ConverterItem(spec.zipLabel, "Converts if needed, then saves one .zip.", "pico", spec.canConvert && !spec.busy, run = { whenReady(spec.onSaveZip) }))
        spec.onPlayTest?.let { play -> add(ConverterItem("PlayTest", "Plays the converted result.", "bf", ready && !spec.busy, run = play)) }
        spec.onTestGame?.let { test -> add(ConverterItem(spec.testGameLabel, "Puts the mod in Friday Night Funkin's mods folder and switches to the game.", "bf", !spec.busy, run = test)) }
        if (spec.rows.isNotEmpty()) add(ConverterItem("Clear All", "Removes every file.", "face", !spec.busy, run = spec.onClear))
        add(ConverterItem("Back", "Goes back to the converter menu.", null, true, run = { leaving = true }))
    }
    val live by rememberUpdatedState(items)

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(optionsOpen) { if (!optionsOpen) runCatching { focus.requestFocus() } }
    LaunchedEffect(spec.output, spec.busy) {
        val action = pending ?: return@LaunchedEffect
        if (spec.busy) return@LaunchedEffect
        pending = null
        if (spec.output != null) action()
    }
    LaunchedEffect(items.size) { if (selected >= items.size) selected = max(0, items.size - 1) }
    LaunchedEffect(leaving) {
        if (leaving) FunkinTransition.go {
            leaving = false
            back()
        }
    }

    fun move(by: Int) {
        val size = live.size
        if (size == 0) return
        selected = ((selected + by) % size + size) % size
        UiSound.click()
    }

    fun accept() {
        val item = live.getOrNull(selected) ?: return
        if (!item.enabled) return
        UiSound.click()
        if (item.row >= 0 && item.cycle != null) item.cycle.invoke(1) else item.run()
    }

    fun cycle(by: Int) {
        val item = live.getOrNull(selected) ?: return
        if (!item.enabled || item.cycle == null) return
        UiSound.click()
        item.cycle.invoke(by)
    }

    fun remove() {
        val item = live.getOrNull(selected) ?: return
        if (item.row < 0 || spec.busy) return
        UiSound.click()
        spec.onRemove(item.row)
    }

    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.85f, stiffness = 260f), label = "converter")
    val tint by animateColorAsState(if (ready) spec.accent else spec.accent.copy(alpha = 1f), tween(500), label = "tint")
    val ticker = rememberInfiniteTransition(label = "converterTick")
    val tickValue by ticker.animateFloat(0f, 4f, infiniteRepeatable(tween(4 * 1000 / 24, easing = LinearEasing), RepeatMode.Restart), label = "tick")
    val blink by ticker.animateFloat(0.4f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blink")

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onSizeChanged {
                area[0] = it.width.toFloat()
                area[1] = it.height.toFloat()
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || optionsOpen) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> move(-1)
                    Key.DirectionDown, Key.S -> move(1)
                    Key.DirectionLeft, Key.A -> cycle(-1)
                    Key.DirectionRight, Key.D -> cycle(1)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> accept()
                    Key.Delete, Key.Backspace -> remove()
                    Key.Escape, Key.Back -> leaving = true
                    else -> return@onKeyEvent false
                }
                true
            }
            .vMenuNavigation(
                key = items.size,
                step = { 120f * 1.3f * min(area[0] / 1280f, area[1] / 720f) },
                onMove = { move(it) },
                onTap = { tap ->
                    val hit = hits[0].indexOfFirst { it.contains(tap) }
                    when {
                        hit < 0 -> Unit
                        hit == selected -> accept()
                        else -> {
                            selected = hit
                            UiSound.click()
                        }
                    }
                },
                onBack = { leaving = true },
                enabled = { !optionsOpen },
                onLongPress = { remove() }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            art.background?.let { bg ->
                val cover = max(w / bg.width, h / bg.height) * 1.1f
                val dw = bg.width * cover
                val dh = bg.height * cover
                drawImage(
                    bg,
                    dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f - position * 6f).roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    colorFilter = ColorFilter.tint(tint, BlendMode.Modulate)
                )
            }
            val scale = min(w / 1300f, h / 720f).coerceAtLeast(0.2f)
            val tick = tickValue.toInt().coerceIn(0, 3)
            val rects = ArrayList<Rect>(items.size)
            items.forEachIndexed { index, item ->
                val target = index - position
                val x = (90f + target * 20f) * scale
                val y = h * 0.48f + target * 1.3f * 120f * scale
                val text = item.label.uppercase()
                val lineHeight = (font?.lineHeight ?: 70f) * scale
                val width = (font?.width(text) ?: text.length * 45f) * scale
                rects += Rect(0f, y - 10f * scale, w, y + lineHeight + 10f * scale)
                if (y + lineHeight + 150f * scale < 0f || y - 150f * scale > h) return@forEachIndexed
                val alpha = (if (index == selected) 1f else 0.6f) * (if (item.enabled) 1f else 0.45f)
                if (font != null) with(font) { draw(text, x, y, scale, alpha, tick) }
                item.icon?.let { name ->
                    val icon = art.icons[name] ?: return@let
                    val frame = if (icon.width >= icon.height * 2) icon.width / 2 else icon.width
                    val iconSize = HealthIcons.FRAME * scale
                    drawImage(
                        icon,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(frame, icon.height),
                        dstOffset = IntOffset((x + width + 10f * scale).roundToInt(), (y - 30f * scale).roundToInt()),
                        dstSize = IntSize((iconSize * frame / icon.height).roundToInt(), iconSize.roundToInt()),
                        alpha = alpha
                    )
                }
                if (item.cycle != null && index == selected) {
                    val mid = y + lineHeight / 2f
                    val arrow = 18f * scale
                    val left = x - 34f * scale
                    drawPath(Path().apply {
                        moveTo(left, mid)
                        lineTo(left + arrow, mid - arrow)
                        lineTo(left + arrow, mid + arrow)
                        close()
                    }, Color.White.copy(alpha = alpha))
                }
            }
            hits[0] = rects
        }
        if (font == null) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) {
                items.forEachIndexed { index, item ->
                    Text(item.label.uppercase(), color = Color.White.copy(alpha = if (index == selected) 1f else 0.6f), fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        val shadow = Shadow(Color.Black, Offset(2f, 2f), 0f)
        val current = items.getOrNull(selected)
        val row = current?.row?.takeIf { it >= 0 }?.let { spec.rows.getOrNull(it) }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .widthIn(max = 430.dp)
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val big = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, shadow = shadow, textAlign = TextAlign.End)
            val small = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, shadow = shadow, textAlign = TextAlign.End)
            Text(spec.title.uppercase(), style = big)
            Text(spec.direction.uppercase(), style = small.copy(color = spec.accent))
            Text("${spec.rows.size} FILE(S)" + (spec.output?.let { " · ${it.files.size} READY" } ?: ""), style = small)
            if (spec.busy) Text("CONVERTING...", style = small.copy(color = Color.White.copy(alpha = blink), fontWeight = FontWeight.Bold))
            if (row != null) {
                Text(row.name, style = small.copy(fontWeight = FontWeight.Bold))
                Text(row.tag.uppercase() + (row.slot?.let { " · < ${it.label.uppercase()} >" } ?: ""), style = small.copy(color = spec.accent))
                row.warning?.let { Text(it, style = small.copy(color = Color(0xFFFFD166))) }
            }
            current?.let { Text(it.hint, style = small.copy(color = Color(0xFFDADADA))) }
        }
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .widthIn(max = 560.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val preview = spec.preview
            if (preview != null && current?.cycle != null && current.row < 0) {
                Column(Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (low, high) = preview.bpmRange
                    Text(
                        "${preview.song} · ${preview.label}" + if (spec.showingAfter) "  (AFTER)" else "  (BEFORE)",
                        color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    ChartStrip(preview, stripColors)
                    Text(
                        "P ${preview.playerNotes} · O ${preview.opponentNotes} · HOLDS ${preview.holdNotes} · ${formatDuration(preview.length)} · BPM ${if (low == high) formatBpm(low) else "${formatBpm(low)}-${formatBpm(high)}"} · SPEED ${formatBpm(preview.speed)}",
                        color = Color(0xFFCFCFCF), fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    )
                }
            }
            val output = spec.output
            if (spec.status != null || output != null) {
                Column(Modifier.background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    spec.status?.let { Text(it, color = if (spec.statusError) Color(0xFFFF6B6B) else Color(0xFF8CFF8C), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 6) }
                    if (output != null) {
                        output.files.take(5).forEach { Text("• ${it.relativePath}", color = Color(0xFFCFCFCF), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1) }
                        if (output.files.size > 5) Text("…and ${output.files.size - 5} more", color = Color(0xFFA0A0A0), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        if (output.report.warnings.isNotEmpty()) Text("${output.report.warnings.size} WARNING(S)", color = Color(0xFFFFD166), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        overlay()
        val menu = art.menu
        if (optionsOpen && menu != null) {
            FunkinOptionsMenu("Options", spec.options, menu, onClose = { optionsOpen = false })
        }
    }
}
