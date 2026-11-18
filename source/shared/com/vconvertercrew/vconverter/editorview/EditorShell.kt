package com.vconvertercrew.vconverter.editorview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vconverter.overlay.soundClickable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed class EditorMenuItem {
    class Action(val label: String, val shortcut: String = "", val enabled: Boolean = true, val run: () -> Unit) : EditorMenuItem()
    class Check(val label: String, val checked: Boolean, val shortcut: String = "", val enabled: Boolean = true, val toggle: () -> Unit) : EditorMenuItem()
    data object Separator : EditorMenuItem()
}

class EditorMenu(val title: String, val items: List<EditorMenuItem>)

class EditorWindow(val id: String, val title: String, val width: Dp = 360.dp, val content: @Composable ColumnScope.() -> Unit)

@Stable
class EditorWindows(initial: List<String>) {
    private val visible = mutableStateListOf<String>().apply { addAll(initial) }
    internal val offsets = mutableStateMapOf<String, Offset>()

    val order: List<String> get() = visible.toList()

    fun isOpen(id: String): Boolean = id in visible

    fun show(id: String) {
        if (visible.lastOrNull() == id) return
        visible.remove(id)
        visible.add(id)
    }

    fun close(id: String) {
        visible.remove(id)
    }

    fun toggle(id: String) {
        if (isOpen(id)) close(id) else show(id)
    }

    fun closeAll() {
        visible.clear()
    }
}

@Composable
fun rememberEditorWindows(vararg open: String): EditorWindows = remember { EditorWindows(open.toList()) }

@Composable
fun FunkinEditorShell(
    title: String,
    accent: Color,
    menus: List<EditorMenu>,
    windows: EditorWindows,
    panels: List<EditorWindow>,
    modifier: Modifier = Modifier,
    footer: @Composable BoxScope.() -> Unit = {},
    showMenuBar: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val windowMenu = EditorMenu(
        "Window",
        panels.map { panel -> EditorMenuItem.Check(panel.title, windows.isOpen(panel.id)) { windows.toggle(panel.id) } } +
            listOf(EditorMenuItem.Separator, EditorMenuItem.Action("Close All Windows") { windows.closeAll() })
    )
    val help = menus.indexOfFirst { it.title == "Help" }
    val bar = if (help < 0) menus + windowMenu else menus.subList(0, help) + windowMenu + menus.subList(help, menus.size)
    val shape = RoundedCornerShape(12.dp)
    Column(
        if (showMenuBar) modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).background(MaterialTheme.colorScheme.background)
        else modifier.background(Color.Black)
    ) {
        if (showMenuBar) EditorMenuBar(title, accent, bar)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            content()
            val areaWidth = constraints.maxWidth.toFloat()
            val areaHeight = constraints.maxHeight.toFloat()
            windows.order.forEachIndexed { index, id ->
                val panel = panels.firstOrNull { it.id == id } ?: return@forEachIndexed
                key(id) { ToolboxWindow(panel, windows, areaWidth, areaHeight, panels.indexOf(panel), accent) }
            }
            footer()
        }
    }
}

@Composable
private fun EditorMenuBar(title: String, accent: Color, menus: List<EditorMenu>) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        menus.forEach { MenuTitle(it, accent) }
        Spacer(Modifier.width(18.dp))
        Text(title.uppercase(), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
    }
}

@Composable
private fun MenuTitle(menu: EditorMenu, accent: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            menu.title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (open) accent.copy(alpha = 0.3f) else Color.Transparent)
                .soundClickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu.items.forEach { item ->
                when (item) {
                    is EditorMenuItem.Separator -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    is EditorMenuItem.Action -> DropdownMenuItem(
                        text = { Text(item.label, fontSize = 14.sp) },
                        onClick = {
                            UiSound.click()
                            open = false
                            item.run()
                        },
                        enabled = item.enabled,
                        leadingIcon = { Spacer(Modifier.size(12.dp)) },
                        trailingIcon = { Shortcut(item.shortcut) }
                    )
                    is EditorMenuItem.Check -> DropdownMenuItem(
                        text = { Text(item.label, fontSize = 14.sp) },
                        onClick = {
                            UiSound.click()
                            open = false
                            item.toggle()
                        },
                        enabled = item.enabled,
                        leadingIcon = { CheckMark(item.checked, accent) },
                        trailingIcon = { Shortcut(item.shortcut) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Shortcut(text: String) {
    if (text.isNotBlank()) Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun CheckMark(checked: Boolean, accent: Color) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(12.dp)
            .clip(shape)
            .background(if (checked) accent else Color.Transparent)
            .border(1.5.dp, if (checked) accent else MaterialTheme.colorScheme.outline, shape)
    )
}

@Composable
private fun ToolboxWindow(panel: EditorWindow, windows: EditorWindows, areaWidth: Float, areaHeight: Float, slot: Int, accent: Color) {
    val density = LocalDensity.current
    val widthPx = min(with(density) { panel.width.toPx() }, max(160f, areaWidth - 16f))
    val margin = with(density) { 12.dp.toPx() }
    val step = with(density) { 30.dp.toPx() }
    val cascade = slot % 6
    val fallback = Offset(max(0f, areaWidth - widthPx - margin - cascade * step), margin + cascade * step)
    val stored = windows.offsets[panel.id] ?: fallback
    val position = Offset(
        stored.x.coerceIn(0f, max(0f, areaWidth - widthPx)),
        stored.y.coerceIn(0f, max(0f, areaHeight - with(density) { 44.dp.toPx() }))
    )
    val current by rememberUpdatedState(position)
    val maxHeight = with(density) { max(140f, areaHeight - position.y - margin).toDp() }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(with(density) { widthPx.toDp() })
            .heightIn(max = maxHeight)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .pointerInput(panel.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    windows.show(panel.id)
                }
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.22f))
                .pointerInput(panel.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        windows.offsets[panel.id] = current + drag
                    }
                }
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                panel.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "X",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).soundClickable { windows.close(panel.id) }.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = panel.content
        )
    }
}
