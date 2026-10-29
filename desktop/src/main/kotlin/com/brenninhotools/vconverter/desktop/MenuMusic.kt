package com.brenninhotools.vconverter.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.brenninhotools.vconverter.editor.EditorAudio
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
