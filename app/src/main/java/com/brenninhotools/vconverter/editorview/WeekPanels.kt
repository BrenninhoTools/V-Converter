package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editor.WeekDocument
import com.brenninhotools.vconverter.editor.WeekProp
import com.brenninhotools.vconverter.editor.WeekSlot
import com.brenninhotools.vconverter.overlay.soundClickable

@Composable
private fun WeekCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 14.sp)
    }
}

private fun swatch(raw: String): Color {
    val value = raw.trim().removePrefix("#").toLongOrNull(16) ?: return Color.Gray
    return Color(0xFF000000 or (value and 0xFFFFFF))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekToolbar(state: WeekEditorState, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorChip("Undo", false) { if (state.canUndo) state.undo() }
        EditorChip("Redo", false) { if (state.canRedo) state.redo() }
        EditorChip(if (state.playing) "Pause" else "Play", state.playing) { state.playing = !state.playing }
        EditorChip("Confirm", false) { state.confirm() }
        EditorChip("◀ Difficulty", false) { state.changeDifficulty(-1) }
        EditorChip("Difficulty ▶", false) { state.changeDifficulty(1) }
    }
}

@Composable
fun WeekInfoPanel(state: WeekEditorState, modifier: Modifier = Modifier) {
    val doc = state.document
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Week")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Week id", doc.id, Modifier.weight(1f)) { v ->
                val clean = v.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
                if (clean.isNotEmpty()) state.edit { it.copy(id = clean) }
            }
            TextValueField("Story name", doc.name, Modifier.weight(1f)) { v -> state.edit { it.copy(name = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Week name (Psych freeplay)", doc.weekName, Modifier.weight(1f)) { v -> state.edit { it.copy(weekName = v) } }
            TextValueField("Title image (images/…)", doc.titleAsset.ifBlank { doc.title }, Modifier.weight(1f)) { v -> state.edit { it.copy(titleAsset = v.trim()) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextValueField("Background (#RRGGBB or images/…)", doc.background, Modifier.weight(1f)) { v -> state.edit { it.copy(background = v.trim()) } }
            if (doc.backgroundIsColor) {
                Row(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(swatch(doc.background))) {}
            }
        }
        TextValueField("Difficulties (comma separated)", doc.difficulties.joinToString(", "), Modifier.fillMaxWidth()) { v ->
            val list = v.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            if (list.isNotEmpty()) state.edit { it.copy(difficulties = list) }
        }
        Row {
            WeekCheck("Show in story mode", doc.visible) { v -> state.edit { it.copy(visible = v) } }
            WeekCheck("Hide songs from freeplay", doc.hideFreeplay) { v -> state.edit { it.copy(hideFreeplay = v) } }
        }
        EditorSectionTitle("Unlocking")
        TextValueField("Week to beat first (weekBefore)", doc.weekBefore, Modifier.fillMaxWidth()) { v -> state.edit { it.copy(weekBefore = v.trim()) } }
        Row {
            WeekCheck("Start unlocked", doc.startUnlocked) { v -> state.edit { it.copy(startUnlocked = v) } }
            WeekCheck("Hidden until unlocked", doc.hiddenUntilUnlocked) { v -> state.edit { it.copy(hiddenUntilUnlocked = v) } }
        }
        Text(
            if (doc.isLocked) "Locked until \"${doc.weekBefore}\" is beaten. V-Slice gets a level script for it (scripts/levels/), Psych keeps it in the week file; Codename has no week locking."
            else "Turn off \"Start unlocked\" and name a week to lock this one until that week is beaten.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekSongsPanel(state: WeekEditorState, modifier: Modifier = Modifier) {
    val doc = state.document
    val scheme = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorSectionTitle("Songs (${doc.songs.size})")
        doc.songs.forEachIndexed { index, song ->
            val active = index == state.selectedSong
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) scheme.primary else scheme.surfaceVariant)
                    .soundClickable { state.selectedSong = if (active) -1 else index }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(swatch(song.color))) {}
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${song.displayName}", color = if (active) scheme.onPrimary else scheme.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${song.id} · icon ${song.icon.ifBlank { "face" }}", color = if (active) scheme.onPrimary.copy(alpha = 0.75f) else scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (doc.songs.isEmpty()) Text("No songs yet: add the songs of this week below, in the order they play.", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextValueField("Song name", draft, Modifier.weight(1f)) { draft = it }
            EditorChip("Add song", false) {
                state.addSong(draft)
                draft = ""
            }
        }
        val index = state.selectedSong
        val song = doc.songs.getOrNull(index)
        if (song != null) {
            EditorSectionTitle(song.displayName)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextValueField("Song id (data/songs/…)", song.id, Modifier.weight(1f)) { v ->
                    val clean = v.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
                    if (clean.isNotEmpty() && doc.songs.none { it !== song && it.id == clean }) state.edit { it.replaceSong(index, song.copy(id = clean)) }
                }
                TextValueField("Display name", song.name, Modifier.weight(1f)) { v -> state.edit { it.replaceSong(index, song.copy(name = v)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextValueField("Freeplay icon", song.icon, Modifier.weight(1f)) { v -> state.edit { it.replaceSong(index, song.copy(icon = v.trim())) } }
                TextValueField("Freeplay color (#RRGGBB)", song.color, Modifier.weight(1f)) { v ->
                    val hex = v.trim().removePrefix("#")
                    if (hex.matches(Regex("[0-9A-Fa-f]{6}"))) state.edit { it.replaceSong(index, song.copy(color = "#" + hex.uppercase())) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorChip("Move up", false) { state.moveSong(index, -1) }
                EditorChip("Move down", false) { state.moveSong(index, 1) }
                EditorChip("Remove", false) { state.removeSong(index) }
            }
            Text("Icon and color are used by Psych's freeplay; V-Slice reads the song's own metadata.", fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekPropsPanel(state: WeekEditorState, modifier: Modifier = Modifier, onLoadImage: ((WeekSlot) -> Unit)? = null) {
    val doc = state.document
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Menu characters")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekSlot.entries.forEach { slot ->
                val prop = doc.prop(slot)
                EditorChip("${slot.label}: ${prop?.let { if (it.isBase) "base ${slot.key}" else it.assetPath.substringAfterLast('/') } ?: "none"}", state.selected == slot) {
                    state.selected = if (state.selected == slot) null else slot
                }
            }
        }
        val slot = state.selected
        if (slot == null) {
            Text(
                "Tap a character in the preview (or above) to edit it, then drag it to move it. Each slot sits a quarter of the screen further right, like V-Slice's story menu.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        val prop = doc.prop(slot)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("None", prop == null) { state.edit { it.withProp(slot, null) } }
            EditorChip("Base ${slot.key}", prop?.isBase == true) { state.edit { it.withProp(slot, WeekProp.base(slot)) } }
            EditorChip("Custom", prop != null && !prop.isBase) {
                if (prop == null || prop.isBase) {
                    val base = prop ?: WeekProp.base(slot)
                    state.edit { it.withProp(slot, WeekProp("storymenu/props/${it.id}-${slot.key}", offsetX = base.offsetX, offsetY = base.offsetY)) }
                }
            }
            if (onLoadImage != null) EditorChip("Load image…", false) { onLoadImage(slot) }
        }
        if (prop == null) return@Column
        PropFields(state, slot, prop)
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
        if (state.imageFor(prop.assetPath) == null) {
            Text(
                if (prop.isBase) "The base game sprite isn't bundled, so it shows as a box; it is not exported (the game already has it)."
                else "No image loaded for images/${prop.assetPath}.png yet: use \"Load image…\" with the .png and its .xml.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PropFields(state: WeekEditorState, slot: WeekSlot, prop: WeekProp) {
    fun update(change: (WeekProp) -> WeekProp) = state.updateProp(slot, change)
    TextValueField("Image (images/…)", prop.assetPath, Modifier.fillMaxWidth()) { v -> if (v.isNotBlank()) update { it.copy(assetPath = v.trim()) } }
    val prefixes = state.imageFor(prop.assetPath)?.atlas?.prefixes() ?: emptyList()
    val idle = prop.anim(prop.idleName)?.prefix ?: ""
    val confirm = prop.anim("confirm")?.prefix ?: ""
    if (prefixes.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceField("Idle animation", idle, prefixes.map { it to it }, Modifier.weight(1f)) { v -> update { it.withPrefix(it.idleName, v) } }
            ChoiceField("Confirm animation", confirm, listOf("" to "None") + prefixes.map { it to it }, Modifier.weight(1f)) { v -> update { it.withPrefix("confirm", v) } }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Idle prefix", idle, Modifier.weight(1f)) { v -> update { it.withPrefix(it.idleName, v) } }
            TextValueField("Confirm prefix", confirm, Modifier.weight(1f)) { v -> update { it.withPrefix("confirm", v) } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Offset X", prop.offsetX, Modifier.weight(1f)) { v -> update { it.copy(offsetX = v) } }
        NumberField("Offset Y", prop.offsetY, Modifier.weight(1f)) { v -> update { it.copy(offsetY = v) } }
        NumberField("Scale", prop.scale, Modifier.weight(1f)) { v -> if (v > 0) update { it.copy(scale = v) } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        NumberField("Dance every (beats)", prop.danceEvery, Modifier.weight(1f)) { v -> if (v >= 0) update { it.copy(danceEvery = v) } }
        WeekCheck("Pixel", prop.isPixel) { v -> update { it.copy(isPixel = v) } }
        WeekCheck("Flip X", prop.flipX) { v -> update { it.copy(flipX = v) } }
    }
    if (confirm.isNotEmpty()) {
        Text("Press \"Confirm\" above the preview to play the confirm animation.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun weekSummary(doc: WeekDocument): String =
    "${doc.songs.size} song(s) · ${WeekSlot.entries.count { doc.prop(it) != null }} menu character(s)" + if (doc.isLocked) " · locked" else ""
