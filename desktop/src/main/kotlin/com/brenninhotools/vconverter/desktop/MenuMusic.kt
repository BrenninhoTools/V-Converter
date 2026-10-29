package com.brenninhotools.vconverter.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.overlay.UiSound
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MenuMusic(suppressed: Boolean) {
    val player by produceState<DesktopSongPlayer?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Resources.bytes("music/freakyMenu.ogg") ?: return@runCatching null
                DesktopSongPlayer.create(listOf(EditorAudio("freakyMenu.ogg", bytes))).first.takeIf { it.duration > 0 }
            }.getOrNull()
        }
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
    DisposableEffect(Unit) {
        val clips = runCatching {
            val bytes = Resources.bytes(UiSound.SCROLL) ?: return@runCatching emptyList()
            val source = AudioSystem.getAudioInputStream(BufferedInputStream(ByteArrayInputStream(bytes)))
            val base = source.format
            val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false)
            val pcm = AudioSystem.getAudioInputStream(format, source).use { it.readBytes() }
            List(3) {
                AudioSystem.getClip().apply { open(format, pcm, 0, pcm.size) }
            }
        }.getOrDefault(emptyList())
        var next = 0
        if (clips.isNotEmpty()) {
            UiSound.player = {
                val clip = clips[next]
                next = (next + 1) % clips.size
                clip.stop()
                clip.framePosition = 0
                clip.start()
            }
        }
        onDispose {
            UiSound.player = null
            clips.forEach { runCatching { it.close() } }
        }
    }
}
