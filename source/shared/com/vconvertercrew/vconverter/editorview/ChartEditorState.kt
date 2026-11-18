package com.vconvertercrew.vconverter.editorview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vconvertercrew.vconverter.editor.ChartDocument
import com.vconvertercrew.vconverter.editor.ChartHistory
import com.vconvertercrew.vconverter.editor.EditorEvent
import com.vconvertercrew.vconverter.editor.EditorNote
import com.vconvertercrew.vconverter.editor.EditorTiming
import com.vconvertercrew.vconverter.editor.EventTemplates
import com.vconvertercrew.vconverter.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.floor
import com.vconvertercrew.vconverter.core.roundLong
import kotlin.math.ceil

class ChartEditorState(initial: ChartDocument) {
    private val history = ChartHistory(initial)

    var document by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var difficulty by mutableStateOf(initial.difficultyNames.first())
    var snap by mutableIntStateOf(16)
    var zoom by mutableFloatStateOf(1f)
    var selectedNote by mutableStateOf<EditorNote?>(null)
        private set
    var selectedEvent by mutableStateOf<EditorEvent?>(null)
        private set
    var eventDraftTime by mutableStateOf<Double?>(null)
        private set
    var position by mutableDoubleStateOf(0.0)
        private set
    var playing by mutableStateOf(false)
        private set
    var hitsounds by mutableStateOf(true)
    var player: SongPlayer? by mutableStateOf(null)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var gridHeightPx by mutableFloatStateOf(0f)
    var basePxPerMs by mutableFloatStateOf(1f)

    val visibleMs: Double get() = if (gridHeightPx <= 0f) 4000.0 else (gridHeightPx / (basePxPerMs * zoom)).toDouble()

    val snaps = listOf(4, 8, 12, 16, 24, 32, 48, 64, 96, 192)

    val songLength: Double get() = max(document.length + 2000.0, player?.duration ?: 0.0).coerceAtLeast(10000.0)

    fun attach(next: SongPlayer?) {
        player?.release()
        player = next
        playing = false
        next?.seek(position)
    }

    var onLocalChange: ((ChartDocument, ChartDocument) -> Unit)? = null

    private fun announce(old: ChartDocument) {
        if (old !== document) onLocalChange?.invoke(old, document)
    }

    fun edit(change: (ChartDocument) -> ChartDocument) {
        val next = change(document)
        if (next == document) return
        val old = document
        document = history.push(next)
        if (difficulty !in document.difficulties) difficulty = document.difficultyNames.first()
        refreshHistory()
        announce(old)
    }

    fun applyRemote(transform: (ChartDocument) -> ChartDocument) {
        document = history.rebase(transform)
        clearMissingSelection()
        refreshHistory()
    }

    fun loadRemote(next: ChartDocument) {
        document = history.reset(next)
        if (difficulty !in document.difficulties) difficulty = next.difficultyNames.first()
        selectedNote = null
        selectedEvent = null
        eventDraftTime = null
        refreshHistory()
    }

    fun load(next: ChartDocument) {
        val old = document
        document = history.reset(next)
        difficulty = next.difficultyNames.first()
        selectedNote = null
        selectedEvent = null
        eventDraftTime = null
        seek(0.0)
        refreshHistory()
        announce(old)
    }

    fun undo() {
        val old = document
        document = history.undo()
        clearMissingSelection()
        refreshHistory()
        announce(old)
    }

    fun redo() {
        val old = document
        document = history.redo()
        clearMissingSelection()
        refreshHistory()
        announce(old)
    }

    private fun refreshHistory() {
        canUndo = history.canUndo
        canRedo = history.canRedo
        if (difficulty !in document.difficulties) difficulty = document.difficultyNames.first()
        revision++
    }

    private fun clearMissingSelection() {
        if (selectedNote != null && document.notes(difficulty).none { it == selectedNote }) selectedNote = null
        if (selectedEvent != null && document.events.none { it == selectedEvent }) selectedEvent = null
    }

    fun tap(time: Double, column: Int) {
        val conductor = document.conductor
        val tolerance = max(conductor.stepMs(time) * 4.0 / snap, 30.0) * 0.6
        if (column < 0) {
            val near = document.eventsNear(time, tolerance).minByOrNull { abs(it.time - time) }
            selectedNote = null
            if (near != null) {
                selectedEvent = near
                eventDraftTime = null
            } else {
                selectedEvent = null
                eventDraftTime = conductor.snap(time, snap).coerceAtLeast(0.0)
            }
            return
        }
        eventDraftTime = null
        selectedEvent = null
        val data = if (column >= 4) column - 4 else column + 4
        val existing = document.noteAt(difficulty, time, data, tolerance)
        if (existing != null) {
            if (existing == selectedNote) {
                edit { it.removeNote(difficulty, existing) }
                selectedNote = null
            } else {
                selectedNote = existing
            }
            return
        }
        val note = EditorNote(conductor.snap(time, snap).coerceAtLeast(0.0), data)
        edit { it.addNote(difficulty, note) }
        selectedNote = note
    }

    fun selectAt(time: Double, column: Int): Boolean {
        val tolerance = max(document.conductor.stepMs(time) * 4.0 / snap, 30.0) * 0.6
        eventDraftTime = null
        if (column < 0) {
            selectedNote = null
            selectedEvent = document.eventsNear(time, tolerance).minByOrNull { abs(it.time - time) }
            return selectedEvent != null
        }
        selectedEvent = null
        selectedNote = document.noteAt(difficulty, time, if (column >= 4) column - 4 else column + 4, tolerance)
        return selectedNote != null
    }

    var legacyTab by mutableIntStateOf(0)

    val section: Int get() = floor(document.conductor.beatAt(position) / 4.0 + 1e-6).toInt().coerceAtLeast(0)

    fun sectionStart(index: Int): Double = document.conductor.timeAt(index.coerceAtLeast(0) * 4.0)

    fun sectionEnd(index: Int): Double = document.conductor.timeAt((index.coerceAtLeast(0) + 1) * 4.0)

    private fun inSection(time: Double, index: Int): Boolean = time >= sectionStart(index) - 0.5 && time < sectionEnd(index) - 0.5

    fun sectionNotes(index: Int = section): List<EditorNote> = document.notes(difficulty).filter { inSection(it.time, index) }

    fun timeAtRow(index: Int, row: Double): Double = document.conductor.timeAt(index * 4.0 + row / 4.0)

    fun rowAt(index: Int, time: Double): Double = (document.conductor.beatAt(time) - index * 4.0) * 4.0

    private fun pause() {
        if (playing) togglePlay()
    }

    fun changeSection(delta: Int) {
        pause()
        seek(sectionStart((section + delta).coerceAtLeast(0)))
    }

    fun resetSection(toStart: Boolean) {
        pause()
        seek(if (toStart) 0.0 else sectionStart(section))
    }

    fun placeNote(time: Double, column: Int, mirror: Boolean) {
        val data = if (column >= 4) column - 4 else column + 4
        val note = EditorNote(time.coerceAtLeast(0.0), data)
        edit { doc ->
            val added = doc.addNote(difficulty, note)
            if (mirror) added.addNote(difficulty, note.copy(data = (data + 4) % 8)) else added
        }
        selectedEvent = null
        eventDraftTime = null
        selectedNote = note
    }

    fun noteAtCell(time: Double, column: Int): EditorNote? {
        val data = if (column >= 4) column - 4 else column + 4
        return document.noteAt(difficulty, time, data, document.conductor.stepMs(time) * 0.5)
    }

    fun removeNote(note: EditorNote) {
        edit { it.removeNote(difficulty, note) }
        if (selectedNote == note) selectedNote = null
    }

    fun selectNote(note: EditorNote) {
        selectedEvent = null
        eventDraftTime = null
        selectedNote = note
    }

    fun copySection(back: Int) {
        val current = section
        val from = current - back
        if (back == 0 || from < 0) return
        val shift = sectionStart(current) - sectionStart(from)
        val copies = sectionNotes(from).map { it.copy(time = it.time + shift) }
        if (copies.isEmpty()) return
        edit { doc -> copies.fold(doc) { acc, note -> acc.addNote(difficulty, note) } }
    }

    fun clearSection() {
        val notes = sectionNotes()
        if (notes.isEmpty()) return
        edit { doc -> notes.fold(doc) { acc, note -> acc.removeNote(difficulty, note) } }
        clearMissingSelection()
    }

    fun swapSection() {
        val notes = sectionNotes()
        if (notes.isEmpty()) return
        edit { doc -> notes.fold(doc) { acc, note -> acc.replaceNote(difficulty, note, note.copy(data = (note.data + 4) % 8)) } }
        selectedNote = null
    }

    fun mustHit(index: Int = section): Boolean {
        val start = sectionStart(index)
        val focus = document.events.filter { it.kind == "FocusCamera" && it.time <= start + 0.5 }.maxByOrNull { it.time } ?: return false
        val target = when (val v = focus.value) {
            is JSONObject -> v.opt("char")
            else -> v
        }
        return target?.toString()?.toDoubleOrNull()?.toInt() == 0
    }

    fun setMustHit(value: Boolean) {
        val start = sectionStart(section)
        edit { doc ->
            val cleared = doc.events.filter { it.kind == "FocusCamera" && abs(it.time - start) < 0.5 }.fold(doc) { acc, event -> acc.removeEvent(event) }
            cleared.addEvent(EditorEvent(start, "FocusCamera", JSONObject().put("char", if (value) 0 else 1)))
        }
    }

    fun changesBpm(index: Int = section): Boolean {
        val start = sectionStart(index)
        return index > 0 && document.timing.any { abs(it.time - start) < 0.5 }
    }

    fun sectionBpm(index: Int = section): Double = document.conductor.change(sectionStart(index) + 0.5).bpm

    fun setChangeBpm(value: Boolean) {
        val index = section
        if (index == 0) return
        val start = sectionStart(index)
        val bpm = sectionBpm(index)
        edit { doc ->
            val others = doc.timing.filterNot { abs(it.time - start) < 0.5 }
            doc.withTiming(if (value) others + EditorTiming(start, bpm, doc.conductor.change(start).numerator) else others)
        }
    }

    fun setSectionBpm(bpm: Double) {
        if (bpm <= 0.0) return
        val index = section
        val start = sectionStart(index)
        edit { doc ->
            if (index == 0) {
                doc.withTiming(doc.timing.mapIndexed { i, t -> if (i == 0) t.copy(bpm = bpm) else t })
            } else {
                val others = doc.timing.filterNot { abs(it.time - start) < 0.5 }
                doc.withTiming(others + EditorTiming(start, bpm, doc.conductor.change(start).numerator))
            }
        }
    }

    fun altAnim(index: Int = section): Boolean {
        val opponent = sectionNotes(index).filter { !it.isPlayer }
        return opponent.isNotEmpty() && opponent.all { it.kind == "alt" }
    }

    fun setAltAnim(value: Boolean) {
        val notes = sectionNotes().filter { !it.isPlayer && (it.kind.isEmpty() || it.kind == "alt") }
        if (notes.isEmpty()) return
        edit { doc -> notes.fold(doc) { acc, note -> acc.replaceNote(difficulty, note, note.copy(kind = if (value) "alt" else "")) } }
        selectedNote = null
    }

    fun setSustain(ms: Double) {
        val note = selectedNote ?: return
        val updated = note.copy(length = (ms.coerceAtLeast(0.0) * 1000.0).roundLong() / 1000.0)
        edit { it.replaceNote(difficulty, note, updated) }
        selectedNote = updated
    }

    fun clearSelection() {
        selectedNote = null
        selectedEvent = null
        eventDraftTime = null
    }

    fun changeHold(steps: Int) {
        val note = selectedNote ?: return
        val conductor = document.conductor
        val stepMs = conductor.stepMs(note.time) * 16.0 / snap
        val length = (note.length + steps * stepMs).coerceAtLeast(0.0).let { if (it < stepMs * 0.5) 0.0 else it }
        val updated = note.copy(length = (length * 1000.0).roundLong() / 1000.0)
        edit { it.replaceNote(difficulty, note, updated) }
        selectedNote = updated
    }

    fun setKind(kind: String) {
        val note = selectedNote ?: return
        val updated = note.copy(kind = kind.trim())
        edit { it.replaceNote(difficulty, note, updated) }
        selectedNote = updated
    }

    fun deleteSelection() {
        selectedNote?.let { note -> edit { it.removeNote(difficulty, note) } }
        selectedEvent?.let { event -> edit { it.removeEvent(event) } }
        selectedNote = null
        selectedEvent = null
    }

    fun addEvent(kind: String, value: Any?) {
        val time = eventDraftTime ?: document.conductor.snap(position, snap)
        val event = EditorEvent(time, kind, value ?: EventTemplates.of(kind)?.defaults() ?: JSONObject())
        edit { it.addEvent(event) }
        selectedEvent = event
        eventDraftTime = null
    }

    fun updateEvent(value: Any?) {
        val event = selectedEvent ?: return
        val updated = event.copy(value = value)
        edit { it.replaceEvent(event, updated) }
        selectedEvent = updated
    }

    fun addTimingHere(bpm: Double? = null) {
        val time = document.conductor.floorSnap(position, 4)
        val current = document.conductor.change(time)
        edit { it.withTiming(it.timing.filterNot { t -> abs(t.time - time) < 0.5 } + EditorTiming(time, bpm ?: current.bpm, current.numerator)) }
    }

    fun scrollBy(deltaMs: Double) = seek(position + deltaMs)

    fun seek(ms: Double) {
        position = ms.coerceIn(0.0, songLength)
        player?.seek(position)
    }

    fun jumpMeasure(direction: Int) {
        val conductor = document.conductor
        val change = conductor.change(position)
        val beat = conductor.beatAt(position)
        val target = if (direction > 0) floor(beat / change.numerator + 1e-6) * change.numerator + change.numerator
        else ceil(beat / change.numerator - 1e-6) * change.numerator - change.numerator
        seek(conductor.timeAt(target.coerceAtLeast(0.0)))
    }

    fun togglePlay() {
        val p = player
        if (playing) {
            p?.pause()
            playing = false
        } else {
            p?.seek(position)
            p?.play()
            playing = true
            lastTick = -1L
        }
    }

    private var lastTick = -1L

    fun tick(frameNanos: Long) {
        if (!playing) return
        val previous = position
        val p = player
        val next = if (p != null && p.isPlaying) p.position() else {
            val elapsed = if (lastTick < 0) 0.0 else (frameNanos - lastTick) / 1_000_000.0
            position + elapsed
        }
        lastTick = frameNanos
        if (next >= songLength) {
            playing = false
            p?.pause()
            position = songLength
            return
        }
        position = next
        if (hitsounds && next > previous) {
            val crossed = document.notes(difficulty).any { it.time > previous && it.time <= next }
            if (crossed) p?.hitsound()
        }
    }
}
