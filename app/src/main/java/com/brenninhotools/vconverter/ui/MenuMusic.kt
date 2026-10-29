package com.brenninhotools.vconverter.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.brenninhotools.vconverter.overlay.UiSound

private const val MENU_MUSIC = "music/freakyMenu.ogg"

@Composable
fun MenuMusic(enabled: Boolean, volume: Float, suppressed: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val player = remember {
        runCatching {
            context.assets.openFd(MENU_MUSIC).use { fd ->
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    isLooping = true
                    prepare()
                }
            }
        }.getOrNull()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    LaunchedEffect(player, volume) {
        player?.setVolume(volume, volume)
    }
    LaunchedEffect(player, enabled, suppressed, foreground) {
        val p = player ?: return@LaunchedEffect
        if (enabled && !suppressed && foreground) {
            if (!p.isPlaying) p.start()
        } else if (p.isPlaying) {
            p.pause()
        }
    }
}

@Composable
fun UiSounds(enabled: Boolean) {
    val context = LocalContext.current
    UiSound.enabled = enabled
    DisposableEffect(Unit) {
        val pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        val id = runCatching { context.assets.openFd(UiSound.SCROLL).use { pool.load(it, 1) } }.getOrNull()
        if (id != null) UiSound.player = { pool.play(id, 0.8f, 0.8f, 1, 0, 1f) }
        onDispose {
            UiSound.player = null
            pool.release()
        }
    }
}
