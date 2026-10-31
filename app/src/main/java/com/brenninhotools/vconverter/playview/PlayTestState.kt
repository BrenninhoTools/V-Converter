package com.brenninhotools.vconverter.playview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.editor.AtlasFrame
import com.brenninhotools.vconverter.editor.CharacterAnim
import com.brenninhotools.vconverter.editor.CharacterDocument
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.StageSlot
import com.brenninhotools.vconverter.editorview.CharacterSprite
import com.brenninhotools.vconverter.editorview.SongPlayer
import com.brenninhotools.vconverter.editorview.StageEditorState
import com.brenninhotools.vconverter.editorview.StageImage
import com.brenninhotools.vconverter.playtest.CharacterRole
import com.brenninhotools.vconverter.playtest.IssueLevel
import com.brenninhotools.vconverter.playtest.Judgement
import com.brenninhotools.vconverter.playtest.PlayEvent
import com.brenninhotools.vconverter.playtest.PlayIssue
import com.brenninhotools.vconverter.playtest.PlayTestEngine
import com.brenninhotools.vconverter.playtest.PlayTestLoader
import com.brenninhotools.vconverter.playtest.PlayTestMod
import com.brenninhotools.vconverter.playtest.PlayTestSong
import com.brenninhotools.vconverter.playtest.PlayTestSongRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.floor
import com.brenninhotools.vconverter.core.fmt
import com.brenninhotools.vconverter.platform.Platform

interface PlayTestPlatform {
    fun player(audio: List<EditorAudio>): SongPlayer?
    fun sprite(png: ByteArray, xml: String): CharacterSprite?
    fun image(asset: StageAsset): StageImage?
}

enum class PlayPhase { SETUP, LOADING, PLAYING, PAUSED, RESULTS }

class Actor(val role: CharacterRole, val document: CharacterDocument?, val sprite: CharacterSprite?, val name: String) {
    var anim: String = ""
        internal set
    var start: Double = 0.0
        internal set
    var holdUntil: Double = -1.0
        internal set
    private var leftNext = true

    val reference: AtlasFrame? = document?.let { doc ->
        val anim = doc.anim(doc.startingAnimation) ?: doc.anim("idle") ?: doc.anim("danceRight") ?: doc.animations.firstOrNull()
        anim?.let { a -> sprite?.atlas?.framesFor(a.prefix, a.indices)?.firstOrNull() }
    }

    val height: Double get() = (reference?.frameHeight ?: 380) * (document?.scale?.takeIf { it > 0 } ?: 1.0)

    fun play(name: String, time: Double, force: Boolean = true) {
        val doc = document ?: return
        if (doc.anim(name) == null) return
        if (!force && anim == name) return
        anim = name
        start = time
    }

    fun dance(time: Double) {
        val doc = document ?: return
        if (doc.anim("danceLeft") != null && doc.anim("danceRight") != null) {
            play(if (leftNext) "danceLeft" else "danceRight", time)
            leftNext = !leftNext
        } else {
            play(if (doc.anim("idle") != null) "idle" else doc.startingAnimation, time)
        }
    }

    fun current(): CharacterAnim? = document?.let { it.anim(anim) ?: it.anim(it.startingAnimation) ?: it.anim("idle") ?: it.animations.firstOrNull() }

    fun frame(time: Double): AtlasFrame? {
        val anim = current() ?: return null
        val frames = sprite?.atlas?.framesFor(anim.prefix, anim.indices) ?: return null
        if (frames.isEmpty()) return null
        val index = (((time - start) / 1000.0) * anim.frameRate.coerceAtLeast(1)).toInt().coerceAtLeast(0)
        return frames[if (anim.looped) index % frames.size else index.coerceAtMost(frames.size - 1)]
    }
}

class Popup(val text: String, val time: Double, val lane: Int = -1)

class PlayTestState(private val scope: CoroutineScope, private val platform: PlayTestPlatform) {
    var mod by mutableStateOf<PlayTestMod?>(null)
        private set
    var source by mutableStateOf("Nothing loaded")
        private set
    var selected by mutableStateOf<PlayTestSongRef?>(null)
        private set
    var song by mutableStateOf<PlayTestSong?>(null)
        private set
    var difficulty by mutableStateOf("normal")
    var botplay by mutableStateOf(false)
    var downscroll by mutableStateOf(false)
    var showStage by mutableStateOf(true)
    var startAt by mutableDoubleStateOf(0.0)
    var phase by mutableStateOf(PlayPhase.SETUP)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var time by mutableDoubleStateOf(0.0)
        private set
    var frames by mutableIntStateOf(0)
        private set
    var engine by mutableStateOf<PlayTestEngine?>(null)
        private set
    var popups by mutableStateOf<List<Popup>>(emptyList())
        private set
    var runIssues by mutableStateOf<List<PlayIssue>>(emptyList())
        private set

    internal var actors: Map<CharacterRole, Actor> = emptyMap()
    internal var stage: StageEditorState = StageEditorState(StageDocument("none"))
    internal var hasStage = false
    internal var camX = 640.0
    internal var camY = 360.0
    internal var lastConfirm = DoubleArray(8) { -10000.0 }
    private var player: SongPlayer? = null
    private var clockStart = 0L
    private var lastNanos = -1L
    private var lastBeat = Int.MIN_VALUE
    private var focus = 1
    private var eventIndex = 0

    val issues: List<PlayIssue> get() = (song?.issues ?: emptyList()) + runIssues
    val songs: List<PlayTestSongRef> get() = mod?.songs ?: emptyList()
    val document: ChartDocument? get() = song?.document
    val duration: Double get() = maxOf(player?.duration ?: 0.0, (engine?.lastTime ?: 0.0) + 1500.0)

    fun open(files: List<Pair<String, ByteArray>>, label: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val opened = withContext(Dispatchers.Default) { PlayTestLoader.open(files) }
                mod = opened
                source = "$label · ${opened.engine}"
                song = null
                selected = null
                if (opened.songs.isEmpty()) error = "No songs found in $label." else load(opened, opened.songs.first())
            } catch (e: Exception) {
                error = e.message ?: "Could not open the files."
            } finally {
                busy = false
            }
        }
    }

    fun select(ref: PlayTestSongRef) {
        val current = mod ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                load(current, ref)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun load(current: PlayTestMod, ref: PlayTestSongRef) {
        try {
            error = null
            val loaded = withContext(Dispatchers.Default) { PlayTestLoader.song(current, ref) }
            prepare(loaded)
            selected = ref
        } catch (e: Exception) {
            error = e.message ?: "Could not load ${ref.label}."
        }
    }

    fun useChart(document: ChartDocument, audio: List<EditorAudio>, label: String) {
        stop()
        mod = null
        selected = null
        source = label
        error = null
        scope.launch { prepare(PlayTestLoader.fromEditor(document, audio)) }
    }

    private suspend fun prepare(loaded: PlayTestSong) {
        val visuals = withContext(Dispatchers.Default) {
            val sprites = loaded.characters.mapValues { (_, c) ->
                val png = c.image
                val xml = c.xml
                if (png != null && xml != null) runCatching { platform.sprite(png, xml) }.getOrNull() else null
            }
            val images = loaded.stage?.assets?.mapNotNull { (key, asset) -> runCatching { platform.image(asset) }.getOrNull()?.let { key to it } }?.toMap() ?: emptyMap()
            sprites to images
        }
        val stageDoc = loaded.stage?.document ?: StageDocument(loaded.stageId.ifBlank { "stage" })
        stage = StageEditorState(stageDoc).also { it.load(stageDoc, loaded.stage?.assets ?: emptyMap(), visuals.second) }
        hasStage = loaded.stage != null
        actors = CharacterRole.entries.associateWith { role ->
            val c = loaded.characters[role]
            Actor(role, c?.document, visuals.first[role], loaded.characterIds[role] ?: role.label)
        }
        song = loaded
        val names = loaded.document.difficultyNames
        difficulty = when {
            difficulty in names && loaded.document.notes(difficulty).isNotEmpty() -> difficulty
            "normal" in names && loaded.document.notes("normal").isNotEmpty() -> "normal"
            else -> names.firstOrNull { loaded.document.notes(it).isNotEmpty() } ?: names.firstOrNull() ?: "normal"
        }
        phase = PlayPhase.SETUP
    }

    internal fun slotOf(role: CharacterRole): StageSlot = when (role) {
        CharacterRole.PLAYER -> StageSlot.BOYFRIEND
        CharacterRole.OPPONENT -> StageSlot.DAD
        CharacterRole.GIRLFRIEND -> StageSlot.GIRLFRIEND
    }

    internal fun roleOf(slot: StageSlot): CharacterRole = when (slot) {
        StageSlot.BOYFRIEND -> CharacterRole.PLAYER
        StageSlot.DAD -> CharacterRole.OPPONENT
        StageSlot.GIRLFRIEND -> CharacterRole.GIRLFRIEND
    }

    private fun cameraTarget(role: CharacterRole, dx: Double, dy: Double): Pair<Double, Double> {
        val spot = stage.document.spot(slotOf(role))
        val actor = actors[role]
        val doc = actor?.document
        val x = spot.x + (doc?.cameraX ?: 0.0) + spot.cameraX + dx
        val y = spot.y - (actor?.height ?: 380.0) * spot.scale / 2.0 + (doc?.cameraY ?: 0.0) + spot.cameraY + dy
        return x to y
    }

    fun start() {
        val current = song ?: return
        if (phase == PlayPhase.LOADING) return
        stop()
        phase = PlayPhase.LOADING
        runIssues = emptyList()
        scope.launch {
            val created = withContext(Dispatchers.Default) { runCatching { platform.player(current.audio) }.getOrNull() }
            if (current.audio.isNotEmpty() && created == null) runIssues = runIssues + PlayIssue(IssueLevel.WARNING, "Audio", "The audio could not be played; PlayTest uses a silent clock.")
            val notes = current.document.notes(difficulty)
            val game = PlayTestEngine(notes, botplay)
            val begin = startAt.coerceAtLeast(0.0)
            game.seek(begin)
            engine = game
            player = created
            popups = emptyList()
            lastConfirm = DoubleArray(8) { -10000.0 }
            lastBeat = Int.MIN_VALUE
            eventIndex = 0
            focus = 1
            current.document.events.forEach { if (it.time <= begin && it.kind == "FocusCamera") focus = focusOf(it.field("char")) }
            val target = cameraTarget(roleFor(focus), 0.0, 0.0)
            camX = target.first
            camY = target.second
            actors.values.forEach { it.dance(begin) }
            time = begin
            created?.seek(begin)
            clockStart = Platform.nanoTime() - (begin * 1_000_000).toLong()
            lastNanos = -1L
            created?.play()
            phase = PlayPhase.PLAYING
        }
    }

    private fun focusOf(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 1
        else -> 1
    }

    private fun roleFor(focus: Int): CharacterRole = when (focus) {
        0 -> CharacterRole.PLAYER
        2 -> CharacterRole.GIRLFRIEND
        else -> CharacterRole.OPPONENT
    }

    private fun now(): Double = player?.position() ?: ((Platform.nanoTime() - clockStart) / 1_000_000.0)

    fun tick(nanos: Long) {
        if (phase != PlayPhase.PLAYING) return
        val game = engine ?: return
        val current = song ?: return
        val dt = if (lastNanos < 0) 0.0 else (nanos - lastNanos) / 1_000_000_000.0
        lastNanos = nanos
        val t = now()
        time = t
        handle(game.update(t), t)
        val doc = current.document
        val beat = floor(doc.conductor.beatAt(t)).toInt()
        if (beat != lastBeat) {
            lastBeat = beat
            actors.values.forEach { actor ->
                val every = (actor.document?.danceEvery ?: 2.0).coerceAtLeast(1.0).toInt()
                val dances = actor.document?.anim("danceLeft") != null
                if (t > actor.holdUntil && (dances || beat % every == 0)) actor.dance(t)
            }
        }
        val events = doc.events
        var dx = 0.0
        var dy = 0.0
        while (eventIndex < events.size && events[eventIndex].time <= t) {
            val event = events[eventIndex++]
            if (event.kind == "FocusCamera") {
                focus = focusOf(event.field("char"))
                dx = (event.field("x") as? Number)?.toDouble() ?: 0.0
                dy = (event.field("y") as? Number)?.toDouble() ?: 0.0
            }
        }
        val target = cameraTarget(roleFor(focus), dx, dy)
        val k = 1.0 - exp(-dt * 2.4)
        camX += (target.first - camX) * k
        camY += (target.second - camY) * k
        popups = popups.filter { t - it.time < 700 }
        val player = this.player
        val ended = player == null || (!player.isPlaying && t >= player.duration - 50)
        if (t > game.lastTime + 1500 && ended) finish()
        frames++
    }

    private fun sing(role: CharacterRole, lane: Int, time: Double, length: Double, miss: Boolean) {
        val actor = actors[role] ?: return
        val doc = actor.document ?: return
        val base = listOf("singLEFT", "singDOWN", "singUP", "singRIGHT")[lane]
        val name = if (miss && doc.anim(base + "miss") != null) base + "miss" else base
        actor.play(name, time)
        val step = song?.document?.conductor?.stepMs(time) ?: 125.0
        actor.holdUntil = maxOf(time + doc.singTime * step, time + length)
    }

    private fun handle(events: List<PlayEvent>, t: Double) {
        if (events.isEmpty()) return
        val added = ArrayList<Popup>()
        events.forEach { event ->
            when (event) {
                is PlayEvent.Hit -> {
                    lastConfirm[event.note.lane + 4] = t
                    sing(CharacterRole.PLAYER, event.note.lane, t, event.note.length, false)
                    added += Popup(event.judgement.label, t, event.note.lane)
                }
                is PlayEvent.Miss -> {
                    sing(CharacterRole.PLAYER, event.note.lane, t, 0.0, true)
                    added += Popup("MISS", t, event.note.lane)
                }
                is PlayEvent.OpponentHit -> {
                    lastConfirm[event.note.lane] = t
                    sing(CharacterRole.OPPONENT, event.note.lane, t, event.note.length, false)
                }
                is PlayEvent.HoldDropped -> added += Popup("DROP", t, event.note.lane)
                is PlayEvent.HoldFinished -> Unit
                is PlayEvent.Died -> runIssues = runIssues + PlayIssue(IssueLevel.INFO, "Gameplay", "Health ran out at ${formatMs(event.time)} (PlayTest keeps going).")
            }
        }
        if (added.isNotEmpty()) popups = (popups + added).takeLast(6)
    }

    fun press(lane: Int) {
        if (phase != PlayPhase.PLAYING) return
        val game = engine ?: return
        val t = now()
        handle(game.press(lane, t), t)
    }

    fun release(lane: Int) {
        val game = engine ?: return
        game.release(lane, now())
    }

    fun toggleBotplay() {
        botplay = !botplay
        engine?.botplay = botplay
    }

    fun pause() {
        if (phase != PlayPhase.PLAYING) return
        player?.pause()
        phase = PlayPhase.PAUSED
    }

    fun resume() {
        if (phase != PlayPhase.PAUSED) return
        clockStart = Platform.nanoTime() - (time * 1_000_000).toLong()
        lastNanos = -1L
        player?.seek(time)
        player?.play()
        phase = PlayPhase.PLAYING
    }

    private fun finish() {
        player?.pause()
        phase = PlayPhase.RESULTS
        val game = engine ?: return
        val offsets = game.offsets
        if (offsets.size >= 8) {
            val average = offsets.average()
            if (kotlin.math.abs(average) > 25) {
                runIssues = runIssues + PlayIssue(IssueLevel.INFO, "Gameplay", "Hits were on average ${fmt("%.0f", kotlin.math.abs(average))} ms ${if (average > 0) "late" else "early"}: check the song offset or your device latency.")
            }
        }
    }

    fun stop() {
        player?.pause()
        player?.release()
        player = null
        if (phase == PlayPhase.PLAYING || phase == PlayPhase.PAUSED) phase = if (engine != null) PlayPhase.RESULTS else PlayPhase.SETUP
    }

    fun backToSetup() {
        stop()
        phase = PlayPhase.SETUP
    }

    companion object {
        fun formatMs(ms: Double): String {
            val total = ms.coerceAtLeast(0.0).toLong()
            return fmt("%d:%02d", total / 60000, (total / 1000) % 60)
        }

        val judgementOrder = Judgement.entries
    }
}
