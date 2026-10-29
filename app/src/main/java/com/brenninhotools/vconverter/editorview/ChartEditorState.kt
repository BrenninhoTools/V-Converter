package com.brenninhotools.vconverter.editorview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.ChartHistory
import com.brenninhotools.vconverter.editor.EditorEvent
import com.brenninhotools.vconverter.editor.EditorNote
import com.brenninhotools.vconverter.editor.EditorTiming
import com.brenninhotools.vconverter.editor.EventTemplates
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

interface SongPlayer {
    val duration: Double
    val isPlaying: Boolean
    fun position(): Double
    fun play()
    fun pause()
    fun seek(ms: Double)
    fun hitsound()
    fun release()
}

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

    fun edit(change: (ChartDocument) -> ChartDocument) {
        val next = change(document)
        if (next == document) return
        document = history.push(next)
        if (difficulty !in document.difficulties) difficulty = document.difficultyNames.first()
        refreshHistory()
    }

    fun load(next: ChartDocument) {
        document = history.reset(next)
        difficulty = next.difficultyNames.first()
        selectedNote = null
        selectedEvent = null
        eventDraftTime = null
        seek(0.0)
        refreshHistory()
    }

    fun undo() {
        document = history.undo()
        clearMissingSelection()
        refreshHistory()
    }

    fun redo() {
        document = history.redo()
        clearMissingSelection()
        refreshHistory()
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
        val updated = note.copy(length = Math.round(length * 1000.0) / 1000.0)
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
        val target = if (direction > 0) Math.floor(beat / change.numerator + 1e-6) * change.numerator + change.numerator
        else Math.ceil(beat / change.numerator - 1e-6) * change.numerator - change.numerator
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
