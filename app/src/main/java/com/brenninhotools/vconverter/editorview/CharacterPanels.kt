package com.brenninhotools.vconverter.editorview

import com.brenninhotools.vconverter.overlay.soundClickable
import androidx.compose.foundation.background
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
import com.brenninhotools.vconverter.core.CharacterSlot

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterToolbar(state: CharacterEditorState, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorChip(if (state.playing) "Pause" else "Play", state.playing) { state.togglePlay() }
        EditorChip("< Frame", false) { state.step(-1) }
        EditorChip("Frame >", false) { state.step(1) }
        EditorChip("Undo", false) { if (state.canUndo) state.undo() }
        EditorChip("Redo", false) { if (state.canRedo) state.redo() }
        EditorChip("Ghost", state.ghost) { state.ghost = !state.ghost }
        EditorChip("Camera", state.showCamera) { state.showCamera = !state.showCamera }
        EditorChip("Stage", state.showBackground) { state.showBackground = !state.showBackground }
        EditorChip("Zoom -", false) { state.zoom = (state.zoom / 1.25f).coerceAtLeast(0.05f) }
        EditorChip("Zoom +", false) { state.zoom = (state.zoom * 1.25f).coerceAtMost(8f) }
        EditorChip("Reset view", false) { state.resetView() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DragModeBar(state: CharacterEditorState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Drag moves", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DragMode.entries.forEach { mode -> EditorChip(mode.label, mode == state.dragMode) { state.dragMode = mode } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimationListPanel(state: CharacterEditorState, modifier: Modifier = Modifier) {
    val document = state.document
    val scheme = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorSectionTitle("Animations")
        document.animations.forEachIndexed { index, anim ->
            val active = index == state.selected
            val frames = state.framesOf(anim).size
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) scheme.primary else scheme.surfaceVariant)
                    .soundClickable { state.select(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(anim.name.ifEmpty { "(no name)" }, color = if (active) scheme.onPrimary else scheme.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(anim.prefix, color = if (active) scheme.onPrimary.copy(alpha = 0.75f) else scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (state.sprite == null) "${trim(anim.offsetX)}, ${trim(anim.offsetY)}" else "$frames fr · ${trim(anim.offsetX)}, ${trim(anim.offsetY)}",
                    color = if (active) scheme.onPrimary else if (state.sprite != null && frames == 0) scheme.error else scheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            EditorChip(if (adding) "Close" else "Add animation", adding) { adding = !adding }
            EditorChip("Move up", false) { state.moveSelected(-1) }
            EditorChip("Move down", false) { state.moveSelected(1) }
            EditorChip("Delete", false) { state.removeSelected() }
        }
        if (adding) {
            val prefixes = state.sprite?.atlas?.prefixes() ?: emptyList()
            if (prefixes.isEmpty()) {
                var prefix by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextValueField("Prefix in the spritesheet", prefix, Modifier.weight(1f)) { prefix = it }
                    EditorChip("Add", false) { if (prefix.isNotBlank()) { state.addAnimation(prefix.trim()); adding = false } }
                }
            } else {
                Text("Pick a prefix from the spritesheet:", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    prefixes.forEach { prefix ->
                        val used = document.animations.any { it.prefix == prefix }
                        EditorChip(if (used) "$prefix ✓" else prefix, false) { state.addAnimation(prefix); adding = false }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimationPanel(state: CharacterEditorState, modifier: Modifier = Modifier) {
    val anim = state.animation
    val index = state.selected
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Animation")
        if (anim == null) {
            Text("Add an animation to edit it.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        fun update(change: (com.brenninhotools.vconverter.editor.CharacterAnim) -> com.brenninhotools.vconverter.editor.CharacterAnim) =
            state.edit { it.replaceAnim(index, change(it.animations[index])) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Name (idle, singLEFT…)", anim.name, Modifier.weight(1f)) { v -> update { it.copy(name = v.trim()) } }
            NumberField("FPS", anim.frameRate.toDouble(), Modifier.weight(0.5f)) { v -> if (v >= 1) update { it.copy(frameRate = v.toInt()) } }
        }
        TextValueField("Prefix", anim.prefix, Modifier.fillMaxWidth()) { v -> update { it.copy(prefix = v) } }
        TextValueField("Indices (empty = all frames)", anim.indices.joinToString(","), Modifier.fillMaxWidth()) { v ->
            val list = v.split(',', ' ').mapNotNull { it.trim().toIntOrNull() }
            update { it.copy(indices = list) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("Offset X", anim.offsetX, Modifier.weight(1f)) { v -> update { it.copy(offsetX = v) } }
            NumberField("Offset Y", anim.offsetY, Modifier.weight(1f)) { v -> update { it.copy(offsetY = v) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("← 1", false) { state.nudgeOffset(1.0, 0.0) }
            EditorChip("→ 1", false) { state.nudgeOffset(-1.0, 0.0) }
            EditorChip("↑ 1", false) { state.nudgeOffset(0.0, 1.0) }
            EditorChip("↓ 1", false) { state.nudgeOffset(0.0, -1.0) }
            EditorChip("← 10", false) { state.nudgeOffset(10.0, 0.0) }
            EditorChip("→ 10", false) { state.nudgeOffset(-10.0, 0.0) }
            EditorChip("↑ 10", false) { state.nudgeOffset(0.0, 10.0) }
            EditorChip("↓ 10", false) { state.nudgeOffset(0.0, -10.0) }
        }
        Row {
            CheckRow("Loop", anim.looped) { v -> update { it.copy(looped = v) } }
            CheckRow("Flip X", anim.flipX) { v -> update { it.copy(flipX = v) } }
        }
        val names = state.document.animations.map { it.name }.filter { it.isNotEmpty() }
        if (names.isNotEmpty()) {
            ChoiceField("Ghost", state.ghostAnimation?.name ?: names.first(), names.map { it to it }, Modifier.fillMaxWidth()) { state.ghostAnim = it }
        }
    }
}

@Composable
fun CharacterInfoPanel(state: CharacterEditorState, modifier: Modifier = Modifier) {
    val doc = state.document
    fun update(change: (com.brenninhotools.vconverter.editor.CharacterDocument) -> com.brenninhotools.vconverter.editor.CharacterDocument) = state.edit(change)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Character")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Character id", doc.id, Modifier.weight(1f)) { v ->
                val clean = v.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
                if (clean.isNotEmpty()) update { it.copy(id = clean) }
            }
            TextValueField("Name", doc.name, Modifier.weight(1f)) { v -> update { it.copy(name = v) } }
        }
        TextValueField("Spritesheet path (images/…)", doc.assetPath, Modifier.fillMaxWidth()) { v -> update { it.copy(assetPath = v.trim()) } }
        ChoiceField("Side", doc.slot, CharacterSlot.entries.map { it to it.label }, Modifier.fillMaxWidth()) { v -> update { it.copy(slot = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("Scale", doc.scale, Modifier.weight(1f)) { v -> if (v > 0) update { it.copy(scale = v) } }
            NumberField("Sing time", doc.singTime, Modifier.weight(1f)) { v -> if (v > 0) update { it.copy(singTime = v) } }
            NumberField("Dance every", doc.danceEvery, Modifier.weight(1f)) { v -> if (v >= 0) update { it.copy(danceEvery = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("Position X", doc.offsetX, Modifier.weight(1f)) { v -> update { it.copy(offsetX = v) } }
            NumberField("Position Y", doc.offsetY, Modifier.weight(1f)) { v -> update { it.copy(offsetY = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("Camera X", doc.cameraX, Modifier.weight(1f)) { v -> update { it.copy(cameraX = v) } }
            NumberField("Camera Y", doc.cameraY, Modifier.weight(1f)) { v -> update { it.copy(cameraY = v) } }
            NumberField("Preview zoom", state.stageZoom.toDouble(), Modifier.weight(1f)) { v -> if (v > 0) state.stageZoom = v.toFloat() }
        }
        Text(
            "The camera point is the character's center plus Camera X/Y, plus the stage's default offset for the ${doc.slot.label.lowercase()} side (${trim(doc.slot.cameraBaseX)}, ${trim(doc.slot.cameraBaseY)}), like V-Slice. The dashed frame is what a 1280×720 game camera sees at the preview zoom.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextValueField("Health icon", doc.healthIcon, Modifier.weight(1f)) { v -> update { it.copy(healthIcon = v.trim()) } }
            CheckRow("Pixel icon", doc.healthIconPixel) { v -> update { it.copy(healthIconPixel = v) } }
        }
        val names = doc.animations.map { it.name }.filter { it.isNotEmpty() }
        if (names.isNotEmpty()) {
            ChoiceField("Starting animation", doc.startingAnimation.takeIf { it in names } ?: names.first(), names.map { it to it }, Modifier.fillMaxWidth()) { v -> update { it.copy(startingAnimation = v) } }
        }
        Row {
            CheckRow("Flip X", doc.flipX) { v -> update { it.copy(flipX = v) } }
            CheckRow("Pixel art", doc.isPixel) { v -> update { it.copy(isPixel = v) } }
        }
    }
}
