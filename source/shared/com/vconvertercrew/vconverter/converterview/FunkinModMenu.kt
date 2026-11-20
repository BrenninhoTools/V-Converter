package com.vconvertercrew.vconverter.converterview

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
import com.vconvertercrew.vconverter.core.Report
import com.vconvertercrew.vconverter.mod.ModProgress
import com.vconvertercrew.vconverter.overlay.FunkinOptionsMenu
import com.vconvertercrew.vconverter.overlay.FunkinTransition
import com.vconvertercrew.vconverter.overlay.HealthIcons
import com.vconvertercrew.vconverter.overlay.MenuOption
import com.vconvertercrew.vconverter.overlay.ModScannerPanel
import com.vconvertercrew.vconverter.overlay.ScannerColors
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vtouch.vMenuNavigation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ModMenuInfo(
    val title: String,
    val description: String,
    val author: String,
    val onTitle: (String) -> Unit,
    val onDescription: (String) -> Unit,
    val onAuthor: (String) -> Unit,
    val note: String
)

class ModMenuSpec(
    val source: String,
    val target: String,
    val accent: Color,
    val modName: String?,
    val modRoot: String?,
    val stats: List<Pair<String, Int>>,
    val songs: List<String>,
    val warnings: List<String>,
    val info: ModMenuInfo,
    val busy: Boolean,
    val progress: ModProgress?,
    val onCancel: (() -> Unit)?,
    val cancelling: Boolean,
    val status: String?,
    val statusError: Boolean,
    val report: Report?,
    val onOpenFolder: (() -> Unit)?,
    val onOpenArchive: () -> Unit,
    val onClear: () -> Unit,
    val onInstall: (() -> Unit)?,
    val onSaveZip: (() -> Unit)?,
    val installLabel: String = "Install Into Mods Folder",
    val zipLabel: String = "Save As Zip",
    val onPlayTest: (() -> Unit)? = null,
    val onTestGame: (() -> Unit)? = null,
    val testGameLabel: String = "Test In Friday Night Funkin'",
    val onBack: () -> Unit
)

object ModMenus {
    fun infoNote(target: String): String = when (target.lowercase()) {
        "psych", "psych engine" -> "Written to pack.json. The icon becomes pack.png."
        "codename", "codename engine" -> "Written to data/config/modpack.ini. The icon becomes icon.png."
        "v-slice" -> "Written to _polymod_meta.json. The icon becomes _polymod_icon.png."
        "nightmarevision" -> "Written to meta.json. The icon becomes images/pack.png."
        "fps plus" -> "Written to meta.json with the uid and api_version FPS Plus needs. The icon becomes icon.png."
        else -> "Written to the mod's metadata file."
    }

    fun options(info: ModMenuInfo): List<MenuOption> = listOf(
        MenuOption.TextEntry("Title", "The mod's name. It also names the mod folder. ${info.note}", { info.title }, { info.onTitle(it) }, "Converted Mod"),
        MenuOption.TextEntry("Description", "Shown in the game's mod list.", { info.description }, { info.onDescription(it) }),
        MenuOption.TextEntry("Author", "Who made the mod.", { info.author }, { info.onAuthor(it) })
    )
}

private class ModItem(
    val label: String,
    val hint: String,
    val icon: String?,
    val enabled: Boolean,
    val run: () -> Unit = {},
    val cycle: ((Int) -> Unit)? = null,
    val kind: Int = 0
)

private const val KIND_SONGS = 1
private const val KIND_REPORT = 2
private const val MAX_LABEL = 24

private fun fit(text: String): String = if (text.length <= MAX_LABEL) text else text.take(MAX_LABEL - 3).trimEnd() + "..."

private fun wrapIndex(index: Int, by: Int, size: Int): Int = if (size == 0) 0 else ((index + by) % size + size) % size

@Composable
fun FunkinModMenu(spec: ModMenuSpec, art: ConverterArt, modifier: Modifier = Modifier, gameBanana: GameBananaState? = null, overlay: @Composable BoxScope.() -> Unit = {}) {
    val font = art.menu?.font
    val focus = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(0) }
    var song by remember { mutableIntStateOf(0) }
    var section by remember { mutableIntStateOf(0) }
    var infoOpen by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val back by rememberUpdatedState(spec.onBack)
    val area = remember { floatArrayOf(1280f, 720f) }
    val hits = remember { arrayOf<List<Rect>>(emptyList()) }

    val loaded = spec.modName != null
    val canWrite = loaded && !spec.busy
    val report = spec.report
    val sections = report?.sections.orEmpty()
    val items = buildList {
        spec.onOpenFolder?.let { add(ModItem("Open Mod Folder", "Pick the ${spec.source} mod's folder.", "face", !spec.busy, run = it)) }
        add(ModItem("Open Archive", "Pick a .zip, .rar or .7z. The mod can sit inside subfolders, it is found automatically.", "face", !spec.busy, run = spec.onOpenArchive))
        gameBanana?.let { banana -> add(ModItem("GameBanana", "Browse Friday Night Funkin' mods on GameBanana; one click downloads a mod and opens it here.", "bf", !spec.busy, run = { banana.show() })) }
        if (spec.songs.isNotEmpty()) {
            val index = song.coerceIn(0, spec.songs.size - 1)
            add(ModItem(
                fit(spec.songs[index]),
                "Song ${index + 1} of ${spec.songs.size}. LEFT / RIGHT look through the mod's songs.",
                HealthIcons.resolve(spec.songs[index].substringBefore(' ').lowercase()),
                true,
                cycle = { by -> song = wrapIndex(index, by, spec.songs.size) },
                kind = KIND_SONGS
            ))
        }
        if (loaded) add(ModItem("Mod Info", "Title, description and author. ${spec.info.note}", "gf", !spec.busy, run = { infoOpen = true }))
        spec.onInstall?.let { add(ModItem(spec.installLabel, "Converts the mod and writes its folder into the ${spec.target} mods folder you pick.", "bf", canWrite, run = it)) }
        spec.onSaveZip?.let { add(ModItem(spec.zipLabel, "Converts the mod into one .zip that holds its folder.", "pico", canWrite, run = it)) }
        if (spec.busy && spec.onCancel != null) add(ModItem(if (spec.cancelling) "Cancelling..." else "Cancel", "Stops the conversion.", "face", !spec.cancelling, run = spec.onCancel))
        if (report != null && !spec.busy) {
            val current = sections.getOrNull(section.coerceIn(0, max(0, sections.size - 1)))
            add(ModItem(
                "Report",
                if (sections.isEmpty()) "What the conversion did." else "LEFT / RIGHT pick a part of the report${current?.let { ": ${it.title}" } ?: ""}.",
                "gf",
                true,
                cycle = if (sections.size > 1) { by -> section = wrapIndex(section, by, sections.size) } else null,
                kind = KIND_REPORT
            ))
        }
        spec.onPlayTest?.let { add(ModItem("PlayTest", "Plays the converted mod with botplay and reports what's broken.", "bf", !spec.busy, run = it)) }
        spec.onTestGame?.let { add(ModItem(spec.testGameLabel, "Puts the mod in Friday Night Funkin's mods folder and switches to the game.", "bf", !spec.busy, run = it)) }
        if (loaded) add(ModItem("Clear", "Closes this mod.", "face", !spec.busy, run = spec.onClear))
        add(ModItem("Back", "Goes back to the converter menu.", null, true, run = { leaving = true }))
    }
    val live by rememberUpdatedState(items)

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val bananaOpen = gameBanana?.open == true
    LaunchedEffect(infoOpen, bananaOpen) { if (!infoOpen && !bananaOpen) runCatching { focus.requestFocus() } }
    LaunchedEffect(spec.modName) {
        song = 0
        section = 0
    }
    LaunchedEffect(report) { section = 0 }
    LaunchedEffect(items.size) { if (selected >= items.size) selected = max(0, items.size - 1) }
    LaunchedEffect(leaving) {
        if (leaving) FunkinTransition.go {
            leaving = false
            back()
        }
    }

    fun move(by: Int) {
        if (live.isEmpty()) return
        selected = wrapIndex(selected, by, live.size)
        UiSound.click()
    }

    fun accept() {
        val item = live.getOrNull(selected) ?: return
        if (!item.enabled) return
        UiSound.click()
        if (item.cycle != null && item.kind != 0) item.cycle.invoke(1) else item.run()
    }

    fun cycle(by: Int) {
        val item = live.getOrNull(selected) ?: return
        if (!item.enabled || item.cycle == null) return
        UiSound.click()
        item.cycle.invoke(by)
    }

    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.85f, stiffness = 260f), label = "modMenu")
    val ticker = rememberInfiniteTransition(label = "modTick")
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
                if (event.type != KeyEventType.KeyDown || infoOpen || bananaOpen) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> move(-1)
                    Key.DirectionDown, Key.S -> move(1)
                    Key.DirectionLeft, Key.A -> cycle(-1)
                    Key.DirectionRight, Key.D -> cycle(1)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> accept()
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
                enabled = { !infoOpen && gameBanana?.open != true }
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
                    colorFilter = ColorFilter.tint(spec.accent, BlendMode.Modulate)
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
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .widthIn(max = 440.dp)
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val big = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, shadow = shadow, textAlign = TextAlign.End)
            val small = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, shadow = shadow, textAlign = TextAlign.End)
            Text("MOD", style = big)
            Text("${spec.source} to ${spec.target}".uppercase(), style = small.copy(color = spec.accent))
            if (spec.modName == null) {
                Text("OPEN A ${spec.source.uppercase()} MOD", style = small)
            } else {
                Text(spec.info.title.ifBlank { spec.modName }, style = small.copy(fontWeight = FontWeight.Bold))
                if (spec.info.title.isNotBlank() && spec.info.title != spec.modName) Text(spec.modName, style = small.copy(color = Color(0xFFBDBDBD)))
                spec.modRoot?.takeIf { it.isNotEmpty() }?.let { Text("ROOT: $it", style = small.copy(color = Color(0xFFBDBDBD))) }
                if (spec.stats.isNotEmpty()) Text(spec.stats.joinToString(" · ") { (label, count) -> "${label.uppercase()} $count" }, style = small.copy(color = spec.accent))
                spec.warnings.forEach { Text(it, style = small.copy(color = Color(0xFFFFD166)), maxLines = 3) }
            }
            if (spec.busy) Text(if (spec.cancelling) "CANCELLING..." else "WORKING...", style = small.copy(color = Color.White.copy(alpha = blink), fontWeight = FontWeight.Bold))
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
            spec.progress?.let { progress ->
                ModScannerPanel(
                    progress = progress,
                    colors = ScannerColors(spec.accent, Color(0xE6000000), Color(0xFF1E1E1E), Color.White, Color(0xFFB0B0B0)),
                    modifier = Modifier.fillMaxWidth(),
                    onCancel = spec.onCancel,
                    cancelling = spec.cancelling
                )
            }
            val panel = Modifier.background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 8.dp)
            val line = TextStyle(color = Color(0xFFCFCFCF), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            if (current?.kind == KIND_REPORT && report != null && spec.progress == null) {
                Column(panel, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (report.stats.isNotEmpty()) Text(report.stats.joinToString(" · ") { "${it.label.uppercase()} ${it.value}" }, style = line.copy(color = spec.accent, fontWeight = FontWeight.Bold))
                    sections.getOrNull(section.coerceIn(0, max(0, sections.size - 1)))?.let { part ->
                        Text("${part.title.uppercase()}" + if (sections.size > 1) "  (${section + 1}/${sections.size})" else "", style = line.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        part.lines.take(10).forEach { Text("• $it", style = line, maxLines = 2) }
                        if (part.lines.size > 10) Text("…and ${part.lines.size - 10} more", style = line.copy(color = Color(0xFFA0A0A0)))
                    }
                    if (report.warnings.isNotEmpty()) {
                        Text("${report.warnings.size} WARNING(S)", style = line.copy(color = Color(0xFFFFD166), fontWeight = FontWeight.Bold))
                        report.warnings.take(4).forEach { Text("• $it", style = line.copy(color = Color(0xFFFFE3A0)), maxLines = 2) }
                    }
                }
            } else if (spec.status != null) {
                Column(panel, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(spec.status, color = if (spec.statusError) Color(0xFFFF6B6B) else Color(0xFF8CFF8C), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 6)
                    if (report != null && report.warnings.isNotEmpty()) Text("${report.warnings.size} WARNING(S) · SELECT REPORT TO READ THEM", style = line.copy(color = Color(0xFFFFD166), fontWeight = FontWeight.Bold))
                }
            }
        }
        overlay()
        val menu = art.menu
        if (infoOpen && menu != null) {
            FunkinOptionsMenu("Mod Info", ModMenus.options(spec.info), menu, onClose = { infoOpen = false })
        }
        if (gameBanana != null && bananaOpen) GameBananaMenu(gameBanana, art)
    }
}
