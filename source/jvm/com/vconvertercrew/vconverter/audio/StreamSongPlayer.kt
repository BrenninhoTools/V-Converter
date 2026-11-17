package com.vconvertercrew.vconverter.audio

import com.vconvertercrew.vconverter.editorview.SongPlayer

interface PcmOutput {
    val sampleRate: Int
    val channels: Int
    val chunkFrames: Int

    fun start()
    fun write(samples: FloatArray, frames: Int)
    fun framesPlayed(): Long
    fun pause()
    fun flush()
    fun close()
}

class StreamSongPlayer(
    private val mixer: AudioMixer,
    private val output: PcmOutput,
    private val hit: () -> Unit = {},
    private val releaseExtra: () -> Unit = {}
) : SongPlayer {
    private val lock = Object()
    private val buffer = FloatArray(output.chunkFrames * output.channels)
    private val rate = output.sampleRate.toDouble()

    @Volatile private var alive = true
    @Volatile private var playing = false
    @Volatile private var generation = 0
    private var cursorFrames = 0L
    private var baseFrame = 0L
    private var basePlayed = 0L
    private var pausedMs = 0.0

    override val duration: Double = mixer.duration * 1000.0
    override val isPlaying: Boolean get() = playing

    val backend: String get() = mixer.backend

    private val pump = Thread({ loop() }, "song-player").apply {
        isDaemon = true
        priority = Thread.MAX_PRIORITY
        start()
    }

    private fun loop() {
        val total = (duration / 1000.0 * rate).toLong()
        while (alive) {
            if (!playing) {
                synchronized(lock) { if (alive && !playing) lock.wait(20) }
                continue
            }
            val gen: Int
            val frames: Int
            synchronized(lock) {
                gen = generation
                frames = output.chunkFrames
                mixer.render(buffer, frames)
            }
            output.write(buffer, frames)
            synchronized(lock) {
                if (gen == generation) {
                    cursorFrames += frames
                    if (cursorFrames >= total + output.chunkFrames * 2) {
                        pausedMs = duration
                        playing = false
                        generation++
                        output.pause()
                        output.flush()
                    }
                }
            }
        }
    }

    override fun position(): Double {
        if (!playing) return pausedMs
        synchronized(lock) {
            val played = output.framesPlayed() - basePlayed
            return ((baseFrame + played) * 1000.0 / rate).coerceIn(0.0, duration)
        }
    }

    override fun play() {
        if (duration <= 0.0) return
        synchronized(lock) {
            if (pausedMs >= duration - 1) pausedMs = 0.0
            generation++
            output.flush()
            mixer.seek(pausedMs / 1000.0)
            mixer.play()
            cursorFrames = (pausedMs / 1000.0 * rate).toLong()
            baseFrame = cursorFrames
            basePlayed = output.framesPlayed()
            output.start()
            playing = true
            lock.notifyAll()
        }
    }

    override fun pause() {
        synchronized(lock) {
            if (!playing) return
            pausedMs = position()
            playing = false
            generation++
            mixer.pause()
            output.pause()
            output.flush()
        }
    }

    override fun seek(ms: Double) {
        synchronized(lock) {
            val target = ms.coerceIn(0.0, duration)
            pausedMs = target
            if (!playing) {
                mixer.seek(target / 1000.0)
                return
            }
            generation++
            output.pause()
            output.flush()
            mixer.seek(target / 1000.0)
            mixer.play()
            cursorFrames = (target / 1000.0 * rate).toLong()
            baseFrame = cursorFrames
            basePlayed = output.framesPlayed()
            output.start()
        }
    }

    fun setVolume(volume: Float) {
        mixer.setMasterVolume(volume)
    }

    fun setTrackVolume(index: Int, volume: Float) {
        mixer.setTrackVolume(index, volume)
    }

    override fun hitsound() {
        hit()
    }

    override fun release() {
        synchronized(lock) {
            alive = false
            playing = false
            generation++
            lock.notifyAll()
        }
        runCatching { output.pause() }
        runCatching { output.flush() }
        pump.join(500)
        runCatching { output.close() }
        mixer.release()
        releaseExtra()
    }
}
