package com.vconvertercrew.vconverter.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.vconvertercrew.vconverter.editor.EditorAudio
import com.vconvertercrew.vconverter.editor.Hitsound
import com.vconvertercrew.vconverter.editorview.SongPlayer
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vconverter.platform.Browser
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.js.JsAny
import kotlin.js.js

private fun jsDecode(bytes: JsAny, done: (JsAny?) -> Unit): Unit = js("VC.decode(bytes, done)")
private fun jsPlayer(): JsAny? = js("VC.player()")
private fun jsAddBuffer(player: JsAny, buffer: JsAny): Unit = js("VC.addBuffer(player, buffer)")
private fun jsLength(player: JsAny): Double = js("VC.length(player)")
private fun jsPosition(player: JsAny): Double = js("VC.position(player)")
private fun jsIsPlaying(player: JsAny): Boolean = js("VC.isPlaying(player)")
private fun jsPlay(player: JsAny): Unit = js("VC.play(player)")
private fun jsPause(player: JsAny): Unit = js("VC.pause(player)")
private fun jsSeek(player: JsAny, seconds: Double): Unit = js("VC.seek(player, seconds)")
private fun jsVolume(player: JsAny, volume: Double): Unit = js("VC.volume(player, volume)")
private fun jsRelease(player: JsAny): Unit = js("VC.release(player)")
private fun jsShot(buffer: JsAny, volume: Double): Unit = js("VC.shot(buffer, volume)")

object WebAudio {
    suspend fun decode(bytes: ByteArray): JsAny? {
        val array = Browser.toJs(bytes)
        return suspendCancellableCoroutine { continuation ->
            jsDecode(array) { buffer -> if (continuation.isActive) continuation.resume(buffer) }
        }
    }

    fun shot(buffer: JsAny, volume: Float) = jsShot(buffer, volume.toDouble())
}

class WebSongPlayer private constructor(private val handle: JsAny?, private val hit: JsAny?) : SongPlayer {
    override val duration: Double = handle?.let { jsLength(it) * 1000.0 } ?: 0.0

    override val isPlaying: Boolean get() = handle?.let { jsIsPlaying(it) } == true

    override fun position(): Double = handle?.let { jsPosition(it) * 1000.0 } ?: 0.0

    override fun play() {
        handle?.let { jsPlay(it) }
    }

    override fun pause() {
        handle?.let { jsPause(it) }
    }

    override fun seek(ms: Double) {
        handle?.let { jsSeek(it, ms.coerceIn(0.0, duration) / 1000.0) }
    }

    fun setVolume(volume: Float) {
        handle?.let { jsVolume(it, volume.coerceIn(0f, 1f).toDouble()) }
    }

    override fun hitsound() {
        hit?.let { WebAudio.shot(it, 0.8f) }
    }

    override fun release() {
        handle?.let { jsRelease(it) }
    }

    companion object {
        private var hitBuffer: JsAny? = null

        suspend fun create(audio: List<EditorAudio>): Pair<WebSongPlayer, List<String>> {
            val failures = ArrayList<String>()
            val buffers = audio.mapNotNull { track ->
                WebAudio.decode(track.bytes).also { if (it == null) failures += track.name }
            }
            val handle = if (buffers.isEmpty()) null else jsPlayer()?.also { player -> buffers.forEach { jsAddBuffer(player, it) } }
            val hit = hitBuffer ?: WebAudio.decode(Hitsound.wav()).also { hitBuffer = it }
            return WebSongPlayer(handle, hit) to failures
        }
    }
}

@Composable
fun MenuMusic(suppressed: Boolean) {
    val player by produceState<WebSongPlayer?>(null) {
        value = runCatching {
            val bytes = Resources.bytes("music/freakyMenu.ogg") ?: return@runCatching null
            WebSongPlayer.create(listOf(EditorAudio("freakyMenu.ogg", bytes))).first.takeIf { it.duration > 0 }
        }.getOrNull()
    }
    val active = AppSettings.menuMusic && !suppressed
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    LaunchedEffect(player, AppSettings.musicVolume) {
        player?.setVolume(AppSettings.musicVolume)
    }
    LaunchedEffect(player, active) {
        val p = player ?: return@LaunchedEffect
        if (!active) {
            if (p.isPlaying) p.pause()
            return@LaunchedEffect
        }
        p.setVolume(AppSettings.musicVolume)
        p.play()
        while (true) {
            delay(250)
            if (!p.isPlaying) {
                p.seek(0.0)
                p.play()
                p.setVolume(AppSettings.musicVolume)
            }
        }
    }
}

@Composable
fun UiSounds() {
    UiSound.enabled = AppSettings.uiSounds
    LaunchedEffect(Unit) {
        val buffer = Resources.bytes(UiSound.SCROLL)?.let { WebAudio.decode(it) } ?: return@LaunchedEffect
        UiSound.player = { WebAudio.shot(buffer, 0.8f) }
    }
    DisposableEffect(Unit) {
        onDispose { UiSound.player = null }
    }
}
