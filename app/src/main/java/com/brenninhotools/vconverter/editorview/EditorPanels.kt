package com.brenninhotools.vconverter.editorview

import com.brenninhotools.vconverter.overlay.UiSound
import com.brenninhotools.vconverter.overlay.soundClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editor.EditorEvent
import com.brenninhotools.vconverter.editor.EventTemplate
import com.brenninhotools.vconverter.editor.EventTemplates
import com.brenninhotools.vconverter.editor.FieldType
import com.brenninhotools.vconverter.json.JSONObject
import kotlin.math.abs
import com.brenninhotools.vconverter.core.fmt

object EditorTyping {
    var fields by mutableIntStateOf(0)
}

fun Modifier.typingField(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (focused) EditorTyping.fields-- } }
    onFocusChanged {
        if (it.isFocused != focused) {
            focused = it.isFocused
            EditorTyping.fields += if (focused) 1 else -1
        }
    }
}

private val directionNames = listOf("Left", "Down", "Up", "Right")

fun formatTime(ms: Double): String {
    val total = ms.coerceAtLeast(0.0).toLong()
    return fmt("%d:%02d.%03d", total / 60000, (total / 1000) % 60, total % 1000)
}

@Composable
fun EditorSectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        fontSize = 13.sp,
        color = if (selected) scheme.onPrimary else scheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .soundClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun NumberField(label: String, value: Double, modifier: Modifier = Modifier, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(trim(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            next.replace(',', '.').toDoubleOrNull()?.let { if (it != value) onValue(it) }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.typingField()
    )
}

@Composable
fun TextValueField(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValue(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.typingField()
    )
}

@Composable
fun <T> ChoiceField(label: String, value: T, choices: List<Pair<T, String>>, modifier: Modifier = Modifier, onValue: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = UiSound.wrap { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(choices.firstOrNull { it.first == value }?.second ?: value.toString())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { UiSound.click(); open = false; onValue(key) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DifficultyPanel(state: ChartEditorState, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val document = state.document
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Difficulty")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            document.difficultyNames.forEach { diff ->
                EditorChip("$diff (${document.notes(diff).size})", diff == state.difficulty) {
                    state.difficulty = diff
                    state.clearSelection()
                }
            }
        }
        NumberField("Scroll speed", document.difficulties[state.difficulty]?.speed ?: 1.0, Modifier.fillMaxWidth()) { speed ->
            if (speed > 0) state.edit { it.withSpeed(state.difficulty, speed) }
        }
        OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("New difficulty") }, singleLine = true, modifier = Modifier.fillMaxWidth().typingField())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("Add empty", false) {
                runCatching { state.edit { it.addDifficulty(name) } }.onSuccess { name = "" }.onFailure { error = it.message }
            }
            EditorChip("Copy ${state.difficulty}", false) {
                runCatching { state.edit { it.addDifficulty(name, state.difficulty) } }.onSuccess { name = "" }.onFailure { error = it.message }
            }
            EditorChip("Mirror notes", false) { state.edit { it.mirrorNotes(state.difficulty) } }
            EditorChip("Clear notes", false) { state.edit { it.clearNotes(state.difficulty) }; state.clearSelection() }
            EditorChip("Delete ${state.difficulty}", false) {
                runCatching { state.edit { it.removeDifficulty(state.difficulty) } }.onFailure { error = it.message }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionPanel(state: ChartEditorState, modifier: Modifier = Modifier) {
    val note = state.selectedNote
    val event = state.selectedEvent
    val draft = state.eventDraftTime
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            note != null -> {
                EditorSectionTitle("Note")
                Text(
                    "${if (note.isPlayer) "Player" else "Opponent"} ${directionNames[note.direction]} at ${formatTime(note.time)}" +
                        if (note.length > 0) " · hold ${trim(Math.round(note.length).toDouble())} ms" else "",
                    fontSize = 13.sp
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorChip("Hold -", false) { state.changeHold(-1) }
                    EditorChip("Hold +", false) { state.changeHold(1) }
                    EditorChip("Delete", false) { state.deleteSelection() }
                    EditorChip("Deselect", false) { state.clearSelection() }
                }
                TextValueField("Note kind (alt, hurt...)", note.kind, Modifier.fillMaxWidth()) { state.setKind(it) }
            }
            event != null -> {
                EditorSectionTitle("Event · ${event.kind}")
                Text("At ${formatTime(event.time)}", fontSize = 13.sp)
                EventValueEditor(event) { state.updateEvent(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorChip("Delete", false) { state.deleteSelection() }
                    EditorChip("Deselect", false) { state.clearSelection() }
                }
            }
            draft != null -> {
                EditorSectionTitle("Add event at ${formatTime(draft)}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventTemplates.all.forEach { template -> EditorChip(template.title, false) { state.addEvent(template.kind, template.defaults()) } }
                }
                var kind by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = kind, onValueChange = { kind = it }, label = { Text("Custom event kind") }, singleLine = true, modifier = Modifier.weight(1f).typingField())
                    TextButton(onClick = { if (kind.isNotBlank()) state.addEvent(kind.trim(), JSONObject()) }) { Text("Add") }
                }
                EditorChip("Cancel", false) { state.clearSelection() }
            }
            else -> {
                EditorSectionTitle("Editing")
                Text(
                    "Tap a lane to place a note, tap it again to select, and tap a selected note to delete it. " +
                        "The lane on the right holds events. Drag or use the mouse wheel to scroll, or tap the mini-map to jump.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EventValueEditor(event: EditorEvent, onValue: (Any?) -> Unit) {
    val template = EventTemplates.of(event.kind)
    val value = event.value
    if (template != null && value is JSONObject) TemplateFields(template, value, onValue) else RawValueField(event, onValue)
}

@Composable
private fun TemplateFields(template: EventTemplate, value: JSONObject, onValue: (Any?) -> Unit) {
    fun put(key: String, v: Any) = onValue(JSONObject(value.toString()).put(key, v))
    template.fields.forEach { field ->
        when (field.type) {
            FieldType.NUMBER -> NumberField(field.label, value.optDouble(field.key, (field.default as Number).toDouble()), Modifier.fillMaxWidth()) { put(field.key, it) }
            FieldType.TEXT -> TextValueField(field.label, value.optString(field.key, field.default.toString()), Modifier.fillMaxWidth()) { put(field.key, it) }
            FieldType.BOOL -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = value.optBoolean(field.key, field.default as Boolean), onCheckedChange = { put(field.key, it) })
                Text(field.label)
            }
            FieldType.CHOICE -> {
                val raw = value.opt(field.key) ?: field.default
                val current = field.choices.firstOrNull { it.first.toString() == raw.toString() || (it.first is Number && raw is Number && (it.first as Number).toDouble() == raw.toDouble()) }?.first ?: raw
                ChoiceField(field.label, current, field.choices, Modifier.fillMaxWidth()) { put(field.key, it) }
            }
        }
    }
}

@Composable
private fun RawValueField(event: EditorEvent, onValue: (Any?) -> Unit) {
    val initial = when (val v = event.value) {
        is JSONObject -> v.toString()
        null -> ""
        else -> v.toString()
    }
    var text by remember(event) { mutableStateOf(initial) }
    var invalid by remember(event) { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            val trimmed = next.trim()
            val parsed: Any? = when {
                trimmed.startsWith("{") -> runCatching { JSONObject(trimmed) }.getOrNull()
                trimmed.isEmpty() -> JSONObject()
                else -> trimmed.toDoubleOrNull() ?: trimmed
            }
            invalid = parsed == null
            if (parsed != null) onValue(parsed)
        },
        label = { Text("Value (JSON, number or text)") },
        isError = invalid,
        modifier = Modifier.fillMaxWidth().typingField()
    )
}

@Composable
fun TimingPanel(state: ChartEditorState, modifier: Modifier = Modifier) {
    val timing = state.document.timing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("BPM & time signature")
        timing.forEachIndexed { index, change ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatTime(change.time), fontSize = 12.sp, modifier = Modifier.width(70.dp).soundClickable { state.seek(change.time) })
                NumberField("BPM", change.bpm, Modifier.weight(1f)) { bpm ->
                    if (bpm > 0) state.edit { doc -> doc.withTiming(doc.timing.mapIndexed { i, t -> if (i == index) t.copy(bpm = bpm) else t }) }
                }
                NumberField("Beats", change.numerator.toDouble(), Modifier.width(80.dp)) { n ->
                    val beats = n.toInt()
                    if (beats in 1..32) state.edit { doc -> doc.withTiming(doc.timing.mapIndexed { i, t -> if (i == index) t.copy(numerator = beats) else t }) }
                }
                if (index > 0) TextButton(onClick = { state.edit { doc -> doc.withTiming(doc.timing.filterIndexed { i, _ -> i != index }) } }) { Text("X") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("Add change at playhead", false) { state.addTimingHere() }
        }
        val playhead = state.document.conductor.floorSnap(state.position, 4)
        if (timing.none { abs(it.time - playhead) < 0.5 }) {
            Text("New changes snap to the beat at ${formatTime(playhead)}.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SongInfoPanel(state: ChartEditorState, modifier: Modifier = Modifier) {
    val meta = state.document.meta
    fun update(change: (com.brenninhotools.vconverter.editor.EditorMeta) -> com.brenninhotools.vconverter.editor.EditorMeta) =
        state.edit { it.copy(meta = change(it.meta)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Song")
        TextValueField("Song id", state.document.id, Modifier.fillMaxWidth()) { id ->
            val clean = id.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
            if (clean.isNotEmpty()) state.edit { it.copy(id = clean) }
        }
        TextValueField("Variation (empty = default)", state.document.variation, Modifier.fillMaxWidth()) { v -> state.edit { it.copy(variation = v.trim().lowercase()) } }
        TextValueField("Song name", meta.songName, Modifier.fillMaxWidth()) { v -> update { it.copy(songName = v) } }
        TextValueField("Artist", meta.artist, Modifier.fillMaxWidth()) { v -> update { it.copy(artist = v) } }
        TextValueField("Charter", meta.charter, Modifier.fillMaxWidth()) { v -> update { it.copy(charter = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Player", meta.player, Modifier.weight(1f)) { v -> update { it.copy(player = v) } }
            TextValueField("Opponent", meta.opponent, Modifier.weight(1f)) { v -> update { it.copy(opponent = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Girlfriend", meta.girlfriend, Modifier.weight(1f)) { v -> update { it.copy(girlfriend = v) } }
            TextValueField("Stage", meta.stage, Modifier.weight(1f)) { v -> update { it.copy(stage = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Note style", meta.noteStyle, Modifier.weight(1f)) { v -> update { it.copy(noteStyle = v) } }
            NumberField("Offset (ms)", meta.offset, Modifier.weight(1f)) { v -> update { it.copy(offset = v) } }
        }
    }
}

