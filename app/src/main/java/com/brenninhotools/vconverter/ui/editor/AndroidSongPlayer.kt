package com.brenninhotools.vconverter.ui.editor

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.Hitsound
import com.brenninhotools.vconverter.editorview.SongPlayer
import java.io.File

class AndroidSongPlayer private constructor(
    private val players: List<MediaPlayer>,
    private val folder: File
) : SongPlayer {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val hit: Int = File(folder, "hitsound.wav").also { it.writeBytes(Hitsound.wav()) }.let { pool.load(it.path, 1) }
    private var playing = false
    private var anchorMs = 0.0
    private var anchorClock = 0L
    private var lastReported = -1

    override val duration: Double = players.maxOfOrNull { it.duration.toDouble() } ?: 0.0
    override val isPlaying: Boolean get() = playing && players.isNotEmpty()

    override fun position(): Double {
        val main = players.firstOrNull() ?: return anchorMs
        if (!playing) return anchorMs
        val reported = main.currentPosition
        val now = SystemClock.elapsedRealtime()
        val predicted = anchorMs + (now - anchorClock)
        if (reported != lastReported) {
            lastReported = reported
            if (kotlin.math.abs(predicted - reported) > 60) {
                anchorMs = reported.toDouble()
                anchorClock = now
            }
        }
        return anchorMs + (now - anchorClock)
    }

    override fun play() {
        players.forEach { if (anchorMs < it.duration) it.start() }
        anchorClock = SystemClock.elapsedRealtime()
        lastReported = -1
        playing = true
    }

    override fun pause() {
        anchorMs = position()
        players.forEach { if (it.isPlaying) it.pause() }
        playing = false
    }

    override fun seek(ms: Double) {
        anchorMs = ms
        anchorClock = SystemClock.elapsedRealtime()
        players.forEach { player ->
            val target = ms.toLong().coerceIn(0L, player.duration.toLong())
            player.seekTo(target, MediaPlayer.SEEK_CLOSEST)
            if (playing && !player.isPlaying && ms < player.duration) player.start()
        }
    }

    override fun hitsound() {
        pool.play(hit, 0.7f, 0.7f, 1, 0, 1f)
    }

    override fun release() {
        playing = false
        players.forEach { runCatching { it.release() } }
        pool.release()
        folder.deleteRecursively()
    }

    companion object {
        fun create(context: Context, audio: List<EditorAudio>): AndroidSongPlayer {
            val folder = File(context.cacheDir, "editor-audio/${System.nanoTime()}").apply { mkdirs() }
            val players = audio.mapIndexedNotNull { index, track ->
                val file = File(folder, "$index-${track.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                file.writeBytes(track.bytes)
                runCatching {
                    MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        setDataSource(file.path)
                        prepare()
                    }
                }.getOrNull()
            }
            return AndroidSongPlayer(players, folder)
        }
    }
}
