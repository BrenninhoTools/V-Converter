package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.StageProp
import com.brenninhotools.vconverter.editor.StageSlot
import com.brenninhotools.vconverter.editor.StageSpot

@Composable
private fun StageCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageToolbar(state: StageEditorState, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorChip("Undo", false) { if (state.canUndo) state.undo() }
        EditorChip("Redo", false) { if (state.canRedo) state.redo() }
        EditorChip("Zoom -", false) { state.zoom = (state.zoom / 1.25f).coerceAtLeast(0.05f) }
        EditorChip("Zoom +", false) { state.zoom = (state.zoom * 1.25f).coerceAtMost(4f) }
        EditorChip("Reset view", false) { state.resetView() }
        EditorChip("Parallax", state.parallax) { state.parallax = !state.parallax }
        EditorChip("Camera", state.showCamera) { state.showCamera = !state.showCamera }
    }
}

@Composable
fun StageInfoPanel(state: StageEditorState, modifier: Modifier = Modifier) {
    val doc = state.document
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Stage")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Stage id", doc.id, Modifier.weight(1f)) { v ->
                val clean = v.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
                if (clean.isNotEmpty()) state.edit { it.copy(id = clean) }
            }
            TextValueField("Name", doc.name, Modifier.weight(1f)) { v -> state.edit { it.copy(name = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("Camera zoom", doc.cameraZoom, Modifier.weight(1f)) { v -> if (v > 0) state.edit { it.copy(cameraZoom = v) } }
            TextValueField("Library", doc.directory, Modifier.weight(1f)) { v -> state.edit { it.copy(directory = v.trim()) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageLayersPanel(state: StageEditorState, modifier: Modifier = Modifier, onAddImage: (() -> Unit)? = null) {
    val doc = state.document
    val scheme = MaterialTheme.colorScheme
    var color by remember { mutableStateOf("#000000") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorSectionTitle("Layers (top first)")
        state.drawOrder().asReversed().forEach { item ->
            val active = item == state.selection
            val (title, detail) = when (item) {
                is StageSelection.Prop -> doc.props[item.index].let { it.name to "z ${it.zIndex} · ${it.assetPath}" }
                is StageSelection.Spot -> item.slot.label to "z ${doc.spot(item.slot).zIndex} · character"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) scheme.primary else if (item is StageSelection.Spot) scheme.secondaryContainer else scheme.surfaceVariant)
                    .clickable { state.focus(item) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (active) scheme.onPrimary else scheme.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, color = if (active) scheme.onPrimary.copy(alpha = 0.75f) else scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item is StageSelection.Prop && !doc.props[item.index].isSolid && state.imageFor(doc.props[item.index]) == null) {
                    Text("no image", color = if (active) scheme.onPrimary else scheme.error, fontSize = 11.sp)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (onAddImage != null) EditorChip("Add image", false, onAddImage)
            EditorChip("Bring forward", false) { state.moveLayer(1) }
            EditorChip("Send back", false) { state.moveLayer(-1) }
            EditorChip("Duplicate", false) { state.duplicateSelected() }
            EditorChip("Delete", false) { state.deleteSelected() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Solid color (#RRGGBB)", color, Modifier.weight(1f)) { color = it }
            EditorChip("Add color", false) {
                val hex = color.trim().removePrefix("#")
                if (hex.matches(Regex("[0-9A-Fa-f]{6}"))) {
                    val bf = state.document.spot(StageSlot.BOYFRIEND)
                    state.addProp(StageProp("background", "#" + hex.uppercase(), bf.x - 1600, bf.y - 1300, 0, 3200.0, 1800.0), autoLayer = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageSelectionPanel(state: StageEditorState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val sel = state.selection) {
            null -> {
                EditorSectionTitle("Selection")
                Text(
                    "Tap a prop or a character to select it, then drag it to move it. Dragging anywhere else moves the view; the mouse wheel zooms. The dashed frame is the game camera at the stage's zoom.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is StageSelection.Prop -> state.document.props.getOrNull(sel.index)?.let { PropEditor(state, sel.index, it) }
            is StageSelection.Spot -> SpotEditor(state, sel.slot, state.document.spot(sel.slot))
        }
        if (state.selection != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorChip("← 1", false) { state.nudge(-1.0, 0.0) }
                EditorChip("→ 1", false) { state.nudge(1.0, 0.0) }
                EditorChip("↑ 1", false) { state.nudge(0.0, -1.0) }
                EditorChip("↓ 1", false) { state.nudge(0.0, 1.0) }
                EditorChip("← 10", false) { state.nudge(-10.0, 0.0) }
                EditorChip("→ 10", false) { state.nudge(10.0, 0.0) }
                EditorChip("↑ 10", false) { state.nudge(0.0, -10.0) }
                EditorChip("↓ 10", false) { state.nudge(0.0, 10.0) }
            }
        }
    }
}

@Composable
private fun PropEditor(state: StageEditorState, index: Int, prop: StageProp) {
    fun update(change: (StageProp) -> StageProp) = state.edit { doc: StageDocument -> doc.replaceProp(index, change(doc.props[index])) }
    EditorSectionTitle("Prop")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextValueField("Name", prop.name, Modifier.weight(1f)) { v ->
            val clean = v.trim()
            if (clean.isNotEmpty() && state.document.props.none { it !== prop && it.name == clean }) update { it.copy(name = clean) }
        }
        NumberField("Layer (z)", prop.zIndex.toDouble(), Modifier.weight(0.6f)) { v -> update { it.copy(zIndex = v.toInt()) } }
    }
    TextValueField(if (prop.isSolid) "Color (#RRGGBB)" else "Image (images/…)", prop.assetPath, Modifier.fillMaxWidth()) { v -> update { it.copy(assetPath = v.trim()) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("X", prop.x, Modifier.weight(1f)) { v -> update { it.copy(x = v) } }
        NumberField("Y", prop.y, Modifier.weight(1f)) { v -> update { it.copy(y = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(if (prop.isSolid) "Width" else "Scale X", prop.scaleX, Modifier.weight(1f)) { v -> update { it.copy(scaleX = v) } }
        NumberField(if (prop.isSolid) "Height" else "Scale Y", prop.scaleY, Modifier.weight(1f)) { v -> update { it.copy(scaleY = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Scroll X", prop.scrollX, Modifier.weight(1f)) { v -> update { it.copy(scrollX = v) } }
        NumberField("Scroll Y", prop.scrollY, Modifier.weight(1f)) { v -> update { it.copy(scrollY = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Alpha", prop.alpha, Modifier.weight(1f)) { v -> update { it.copy(alpha = v.coerceIn(0.0, 1.0)) } }
        NumberField("Angle", prop.angle, Modifier.weight(1f)) { v -> update { it.copy(angle = v) } }
    }
    if (!prop.isSolid) {
        val prefixes = state.imageFor(prop)?.atlas?.prefixes() ?: emptyList()
        if (prefixes.isNotEmpty()) {
            ChoiceField("Animation", prop.prefix, listOf("" to "Static image") + prefixes.map { it to it }, Modifier.fillMaxWidth()) { v -> update { it.copy(prefix = v) } }
        }
        Row {
            StageCheck("Flip X", prop.flipX) { v -> update { it.copy(flipX = v) } }
            StageCheck("Flip Y", prop.flipY) { v -> update { it.copy(flipY = v) } }
            StageCheck("Pixel", prop.isPixel) { v -> update { it.copy(isPixel = v) } }
        }
    }
}

@Composable
private fun SpotEditor(state: StageEditorState, slot: StageSlot, spot: StageSpot) {
    fun update(change: (StageSpot) -> StageSpot) = state.edit { it.withSpot(slot, change(it.spot(slot))) }
    EditorSectionTitle(slot.label)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("X (feet)", spot.x, Modifier.weight(1f)) { v -> update { it.copy(x = v) } }
        NumberField("Y (feet)", spot.y, Modifier.weight(1f)) { v -> update { it.copy(y = v) } }
        NumberField("Layer (z)", spot.zIndex.toDouble(), Modifier.weight(0.7f)) { v -> update { it.copy(zIndex = v.toInt()) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Camera X", spot.cameraX, Modifier.weight(1f)) { v -> update { it.copy(cameraX = v) } }
        NumberField("Camera Y", spot.cameraY, Modifier.weight(1f)) { v -> update { it.copy(cameraY = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Scroll", spot.scroll, Modifier.weight(1f)) { v -> update { it.copy(scroll = v) } }
        NumberField("Scale", spot.scale, Modifier.weight(1f)) { v -> if (v > 0) update { it.copy(scale = v) } }
        NumberField("Alpha", spot.alpha, Modifier.weight(1f)) { v -> update { it.copy(alpha = v.coerceIn(0.0, 1.0)) } }
    }
}
