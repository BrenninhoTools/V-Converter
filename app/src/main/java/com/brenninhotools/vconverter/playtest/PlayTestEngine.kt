package com.brenninhotools.vconverter.playtest

import com.brenninhotools.vconverter.editor.EditorNote
import kotlin.math.abs

enum class Judgement(val label: String, val window: Double, val score: Int, val health: Double, val weight: Double) {
    SICK("SICK!!", 45.0, 350, 0.04, 1.0),
    GOOD("GOOD!", 90.0, 200, 0.02, 0.75),
    BAD("BAD", 135.0, 100, 0.0, 0.5),
    SHIT("SHIT", 160.0, 50, -0.02, 0.25);

    companion object {
        val MISS_WINDOW = SHIT.window

        fun of(diff: Double): Judgement? {
            val distance = abs(diff)
            return entries.firstOrNull { distance <= it.window }
        }
    }
}

class PlayNote(val id: Int, val time: Double, val lane: Int, val player: Boolean, val length: Double, val kind: String) {
    var hit = false
        internal set
    var missed = false
        internal set
    var judgement: Judgement? = null
        internal set
    var holding = false
        internal set
    var holdDone = false
        internal set
    var dropped = false
        internal set
    var hitTime = 0.0
        internal set

    val end: Double get() = time + length
    val resolved: Boolean get() = missed || (hit && (length <= 0.0 || holdDone || dropped))
}

sealed interface PlayEvent {
    data class Hit(val note: PlayNote, val judgement: Judgement, val offset: Double) : PlayEvent
    data class Miss(val note: PlayNote) : PlayEvent
    data class OpponentHit(val note: PlayNote) : PlayEvent
    data class HoldDropped(val note: PlayNote) : PlayEvent
    data class HoldFinished(val note: PlayNote) : PlayEvent
    data class Died(val time: Double) : PlayEvent
}

class PlayTestEngine(source: List<EditorNote>, var botplay: Boolean = false) {
    val notes: List<PlayNote> = source
        .sortedWith(compareBy({ it.time }, { it.data }))
        .mapIndexed { i, n -> PlayNote(i, n.time, n.data.mod(4), n.data.mod(8) < 4, n.length.coerceAtLeast(0.0), n.kind) }

    private val playerNotes = notes.filter { it.player }
    private val opponentNotes = notes.filter { !it.player }
    private var opponentCursor = 0
    private var playerCursor = 0
    private val held = BooleanArray(4)

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var misses = 0
        private set
    var holdDrops = 0
        private set
    var health = 1.0
        private set
    var lowestHealth = 1.0
        private set
    var deathTime: Double? = null
        private set
    val counts: MutableMap<Judgement, Int> = Judgement.entries.associateWith { 0 }.toMutableMap()
    private var weightSum = 0.0
    private var judged = 0
    val offsets = ArrayList<Double>()

    val playerNoteCount: Int get() = playerNotes.size
    val accuracy: Double get() = if (judged == 0) 1.0 else weightSum / judged
    val fullCombo: Boolean get() = misses == 0 && judged > 0
    val finished: Boolean get() = playerNotes.all { it.resolved } && opponentNotes.all { it.hit }
    val lastTime: Double get() = notes.maxOfOrNull { it.end } ?: 0.0

    val rating: String get() = when {
        judged == 0 -> "?"
        misses == 0 && counts.getValue(Judgement.SICK) == judged -> "PFC"
        misses == 0 && counts.getValue(Judgement.BAD) == 0 && counts.getValue(Judgement.SHIT) == 0 -> "GFC"
        misses == 0 -> "FC"
        misses < 10 -> "SDCB"
        else -> "Clear"
    }

    fun isHeld(lane: Int): Boolean = held.getOrElse(lane) { false }

    private fun changeHealth(amount: Double, time: Double, events: MutableList<PlayEvent>) {
        health = (health + amount).coerceIn(0.0, 2.0)
        if (health < lowestHealth) lowestHealth = health
        if (health <= 0.0 && deathTime == null) {
            deathTime = time
            events += PlayEvent.Died(time)
        }
    }

    private fun judge(note: PlayNote, time: Double, events: MutableList<PlayEvent>) {
        val diff = time - note.time
        val judgement = Judgement.of(diff) ?: Judgement.SHIT
        note.hit = true
        note.judgement = judgement
        note.hitTime = time
        note.holding = note.length > 0.0
        score += judgement.score
        combo++
        if (combo > maxCombo) maxCombo = combo
        counts[judgement] = counts.getValue(judgement) + 1
        weightSum += judgement.weight
        judged++
        offsets += diff
        changeHealth(judgement.health, time, events)
        events += PlayEvent.Hit(note, judgement, diff)
    }

    private fun miss(note: PlayNote, time: Double, events: MutableList<PlayEvent>) {
        note.missed = true
        combo = 0
        misses++
        judged++
        score -= 10
        changeHealth(-0.08, time, events)
        events += PlayEvent.Miss(note)
    }

    fun update(time: Double): List<PlayEvent> {
        val events = ArrayList<PlayEvent>()
        while (opponentCursor < opponentNotes.size && opponentNotes[opponentCursor].time <= time) {
            val note = opponentNotes[opponentCursor++]
            note.hit = true
            note.hitTime = note.time
            events += PlayEvent.OpponentHit(note)
        }
        for (i in playerCursor until playerNotes.size) {
            val note = playerNotes[i]
            if (note.time > time + Judgement.MISS_WINDOW) break
            if (botplay && !note.hit && !note.missed && note.time <= time) {
                judge(note, note.time, events)
                continue
            }
            if (!note.hit && !note.missed && time - note.time > Judgement.MISS_WINDOW) miss(note, time, events)
            if (note.hit && note.holding) {
                if (time >= note.end) {
                    note.holding = false
                    note.holdDone = true
                    score += (note.length / 1000.0 * 250).toInt()
                    events += PlayEvent.HoldFinished(note)
                } else if (!botplay && !held[note.lane] && time < note.end - 100.0) {
                    note.holding = false
                    note.dropped = true
                    holdDrops++
                    combo = 0
                    changeHealth(-0.04, time, events)
                    events += PlayEvent.HoldDropped(note)
                }
            }
        }
        while (playerCursor < playerNotes.size && playerNotes[playerCursor].resolved) playerCursor++
        return events
    }

    fun press(lane: Int, time: Double): List<PlayEvent> {
        if (lane !in 0..3) return emptyList()
        held[lane] = true
        if (botplay) return emptyList()
        val events = ArrayList<PlayEvent>()
        val candidate = (playerCursor until playerNotes.size).asSequence()
            .map { playerNotes[it] }
            .takeWhile { it.time <= time + Judgement.MISS_WINDOW }
            .filter { it.lane == lane && !it.hit && !it.missed && abs(it.time - time) <= Judgement.MISS_WINDOW }
            .minByOrNull { abs(it.time - time) }
        if (candidate != null) judge(candidate, time, events)
        return events
    }

    fun release(lane: Int, time: Double): List<PlayEvent> {
        if (lane !in 0..3) return emptyList()
        held[lane] = false
        return emptyList()
    }

    fun seek(time: Double) {
        notes.forEach { note ->
            if (note.time < time) {
                note.hit = true
                note.holdDone = true
                note.holding = false
            }
        }
        while (opponentCursor < opponentNotes.size && opponentNotes[opponentCursor].time < time) opponentCursor++
        while (playerCursor < playerNotes.size && playerNotes[playerCursor].resolved) playerCursor++
    }
}
