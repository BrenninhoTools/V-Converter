package com.vconvertercrew.vconverter.editorview

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.convert.ConvertedOutput
import com.vconvertercrew.vconverter.editor.ExportTarget
import com.vconvertercrew.vconverter.overlay.MenuArt
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vtouch.vMenuNavigation
import kotlin.math.min
import kotlin.math.roundToInt

class ExportMenuSpec(
    val title: String,
    val target: ExportTarget,
    val onTarget: (ExportTarget) -> Unit,
    val busy: Boolean,
    val canExport: Boolean,
    val output: ConvertedOutput?,
    val status: String?,
    val statusError: Boolean,
    val onExport: () -> Unit,
    val onSaveZip: () -> Unit,
    val onSaveFolder: (() -> Unit)? = null,
    val zipLabel: String = "Save As Zip",
    val folderLabel: String = "Save To Mod Folder",
    val onPlayTest: (() -> Unit)? = null,
    val describe: (ExportTarget) -> String = { ExportMenus.describe(it) }
)

object ExportMenus {
    fun describe(target: ExportTarget): String = when (target) {
        ExportTarget.VSLICE -> "Writes the V-Slice files: data/ json, images/ and songs/ audio."
        ExportTarget.PSYCH -> "Writes Psych Engine files: data/, characters/, stages/, weeks/ and the images."
        ExportTarget.CODENAME -> "Writes Codename Engine files: data/ xml and json, songs/ and the images."
    }
}

private class ExportItem(val label: String, val hint: String, val enabled: Boolean, val selector: Boolean = false, val run: () -> Unit = {})

private const val STAGE_W = 1280f
private const val STAGE_H = 720f

@Composable
fun FunkinExportMenu(spec: ExportMenuSpec, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val art = remember { MenuArt.last }
    val font = art?.font
    val focus = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var closing by remember { mutableStateOf(false) }
    val dim = remember { Animatable(0f) }
    val slide = remember { Animatable(0f) }
    val close by rememberUpdatedState(onClose)
    val area = remember { floatArrayOf(1280f, 720f) }
    val hits = remember { arrayOf<List<Rect>>(emptyList()) }

    fun whenReady(action: () -> Unit) {
        if (spec.output != null && !spec.busy) action() else {
            pending = action
            if (!spec.busy) spec.onExport()
        }
    }

    val targets = ExportTarget.entries
    val ready = spec.output != null
    val items = buildList {
        add(ExportItem(spec.target.label, "Left and right pick the engine. ${spec.describe(spec.target)}", !spec.busy, selector = true) {
            spec.onTarget(targets[(targets.indexOf(spec.target) + 1) % targets.size])
        })
        add(ExportItem(if (ready) "Export Again" else "Export", "Builds the ${spec.target.label} files from what is in the editor.", spec.canExport && !spec.busy) { spec.onExport() })
        spec.onSaveFolder?.let { save ->
            add(ExportItem(spec.folderLabel, "Exports if needed, then writes the files into a mod folder you pick.", spec.canExport && !spec.busy) { whenReady(save) })
        }
        add(ExportItem(spec.zipLabel, "Exports if needed, then saves everything in one .zip.", spec.canExport && !spec.busy) { whenReady(spec.onSaveZip) })
        spec.onPlayTest?.let { play -> add(ExportItem("PlayTest", "Plays what is in the editor right now.", !spec.busy) { play() }) }
        add(ExportItem("Back", "Goes back to the editor.", true) { closing = true })
    }
    val liveItems by rememberUpdatedState(items)

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        dim.animateTo(0.6f, tween(400, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { slide.animateTo(1f, tween(400, delayMillis = 300, easing = FastOutSlowInEasing)) }
    LaunchedEffect(spec.output, spec.busy, spec.statusError) {
        val action = pending ?: return@LaunchedEffect
        if (spec.busy) return@LaunchedEffect
        pending = null
        if (spec.output != null) action()
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        dim.animateTo(0f, tween(180))
        close()
    }
    LaunchedEffect(items.size) { if (selected >= items.size) selected = items.size - 1 }

    fun move(by: Int) {
        if (closing) return
        val size = liveItems.size
        selected = ((selected + by) % size + size) % size
        UiSound.click()
    }

    fun change(by: Int) {
        val item = liveItems.getOrNull(selected) ?: return
        if (!item.selector || !item.enabled) return
        spec.onTarget(targets[((targets.indexOf(spec.target) + by) % targets.size + targets.size) % targets.size])
        UiSound.click()
    }

    fun accept() {
        if (closing) return
        val item = liveItems.getOrNull(selected) ?: return
        if (!item.enabled) return
        UiSound.click()
        item.run()
    }

    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.85f, stiffness = 260f), label = "export")
    val ticker = rememberInfiniteTransition(label = "exportTick")
    val tickValue by ticker.animateFloat(0f, 4f, infiniteRepeatable(tween(4 * 1000 / 24, easing = LinearEasing), RepeatMode.Restart), label = "tick")
    val blink by ticker.animateFloat(0.35f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blink")

    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onSizeChanged {
                area[0] = it.width.toFloat()
                area[1] = it.height.toFloat()
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> move(-1)
                    Key.DirectionDown, Key.S -> move(1)
                    Key.DirectionLeft, Key.A -> change(-1)
                    Key.DirectionRight, Key.D -> change(1)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> accept()
                    Key.Escape, Key.Backspace, Key.Back -> closing = true
                    else -> return@onKeyEvent true
                }
                true
            }
            .vMenuNavigation(
                key = items.size,
                step = { 120f * min(area[0] / STAGE_W, area[1] / STAGE_H) },
                onMove = { move(it) },
                onTap = { tap ->
                    val hit = hits[0].indexOfFirst { it.contains(tap) }
                    when {
                        hit < 0 -> Unit
                        hit == selected -> {
                            val item = liveItems[hit]
                            if (item.selector) {
                                val centerX = area[0] / 2f
                                change(if (tap.x < centerX * 0.6f) -1 else 1)
                            } else accept()
                        }
                        else -> {
                            selected = hit
                            UiSound.click()
                        }
                    }
                },
                onBack = { closing = true }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = dim.value))
            val scale = min(size.width / STAGE_W, size.height / STAGE_H)
            val tick = tickValue.toInt().coerceIn(0, 3)
            val rects = ArrayList<Rect>(items.size)
            items.forEachIndexed { index, item ->
                val target = index - position
                val x = (90f + target * 20f) * scale
                val y = size.height * 0.48f + target * 1.3f * 120f * scale
                val label = item.label.uppercase()
                val lineHeight = (font?.lineHeight ?: 70f) * scale
                val width = (font?.width(label) ?: label.length * 45f) * scale
                rects += Rect(0f, y - 10f * scale, size.width, y + lineHeight + 10f * scale)
                val alpha = (if (index == selected) 1f else 0.6f) * (if (item.enabled) 1f else 0.45f) * (dim.value / 0.6f).coerceIn(0f, 1f)
                if (font != null) with(font) { draw(label, x + (if (item.selector) 50f * scale else 0f), y, scale, alpha, tick) }
                if (item.selector && index == selected) {
                    val mid = y + lineHeight / 2f
                    val arrow = 22f * scale
                    val left = Path().apply {
                        moveTo(x, mid)
                        lineTo(x + arrow, mid - arrow)
                        lineTo(x + arrow, mid + arrow)
                        close()
                    }
                    val rightX = x + 50f * scale + width + 20f * scale
                    val right = Path().apply {
                        moveTo(rightX + arrow, mid)
                        lineTo(rightX, mid - arrow)
                        lineTo(rightX, mid + arrow)
                        close()
                    }
                    drawPath(left, Color.White.copy(alpha = alpha))
                    drawPath(right, Color.White.copy(alpha = alpha))
                }
            }
            hits[0] = rects
        }
        if (font == null) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { index, item ->
                    Text(
                        item.label.uppercase(),
                        color = Color.White.copy(alpha = if (index == selected) 1f else 0.6f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        val shadow = Shadow(Color.Black, Offset(2f, 2f), 0f)
        val infoStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 22.sp, textAlign = TextAlign.End, shadow = shadow)
        val output = spec.output
        val lines = listOfNotNull(
            "EXPORT ${spec.title.uppercase()}",
            "TARGET: ${spec.target.label.uppercase()}",
            when {
                spec.busy -> "EXPORTING..."
                output != null -> "${output.files.size} FILE(S) READY"
                else -> "NOT EXPORTED YET"
            }
        )
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 20.dp)
                .widthIn(max = 460.dp),
            horizontalAlignment = Alignment.End
        ) {
            lines.forEachIndexed { index, line ->
                val progress = ((slide.value * 3f) - index).coerceIn(0f, 1f)
                Text(
                    line,
                    style = infoStyle.copy(fontSize = if (index == 0) 26.sp else 20.sp),
                    modifier = Modifier.offset { IntOffset(0, ((1f - progress) * -20f).roundToInt()) },
                    color = if (index == 2 && spec.busy) Color.White.copy(alpha = blink * progress) else Color.White.copy(alpha = progress)
                )
            }
        }
        val current = items.getOrNull(selected)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .background(Color(0x99000000))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            current?.let { Text(it.hint, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
            spec.status?.let { text ->
                Text(text, color = if (spec.statusError) Color(0xFFFF6B6B) else Color(0xFF8CFF8C), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (output != null) {
                val shown = output.files.take(6)
                shown.forEach { file -> Text("• ${file.relativePath}", color = Color(0xFFCFCFCF), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1) }
                if (output.files.size > shown.size) Text("…and ${output.files.size - shown.size} more", color = Color(0xFFA0A0A0), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                val warnings = output.report.warnings.size
                if (warnings > 0) Text("$warnings WARNING(S) IN THE REPORT", color = Color(0xFFFFD166), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
