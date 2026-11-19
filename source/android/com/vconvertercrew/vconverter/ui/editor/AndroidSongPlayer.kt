package com.vconvertercrew.vconverter.ui.editor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import com.vconvertercrew.vconverter.audio.Mixers
import com.vconvertercrew.vconverter.audio.PcmAudio
import com.vconvertercrew.vconverter.audio.PcmOutput
import com.vconvertercrew.vconverter.audio.StreamSongPlayer
import com.vconvertercrew.vconverter.editor.EditorAudio
import com.vconvertercrew.vconverter.editor.Hitsound
import com.vconvertercrew.vconverter.editorview.SongPlayer
import java.io.File
import java.nio.ByteOrder

object AndroidDecoder {
    fun decode(file: File): PcmAudio {
        if (file.length() in 13..(256L shl 20)) {
            val head = ByteArray(4)
            val known = runCatching { file.inputStream().use { it.read(head) } == 4 }.getOrDefault(false) &&
                (head.contentEquals("OggS".encodeToByteArray()) || head.contentEquals("RIFF".encodeToByteArray()))
            if (known) com.vconvertercrew.vconverter.audio.VMiniAudioNative.decodePcm(file.readBytes())?.let { return it }
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: throw IllegalArgumentException("${file.name} has no audio track.")
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var encoding = AudioFormat.ENCODING_PCM_16BIT
                val chunks = ArrayList<ShortArray>()
                var total = 0
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var idle = 0
                while (!outputDone && idle < 500) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val input = codec.getInputBuffer(inIndex)!!
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    idle = if (inputDone && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) idle + 1 else 0
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val changed = codec.outputFormat
                            rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (changed.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = changed.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        outIndex >= 0 -> {
                            val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val samples = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val floats = buffer.asFloatBuffer()
                                ShortArray(floats.remaining()) { (floats.get(it).coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
                            } else {
                                val shorts = buffer.asShortBuffer()
                                ShortArray(shorts.remaining()).also { shorts.get(it) }
                            }
                            if (samples.isNotEmpty()) {
                                chunks += samples
                                total += samples.size
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        }
                    }
                }
                val all = ShortArray(total)
                var at = 0
                chunks.forEach { it.copyInto(all, at); at += it.size }
                if (channels <= 0 || all.isEmpty()) throw IllegalArgumentException("${file.name} decoded to silence.")
                return PcmAudio(all, channels, rate)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }
}

class AudioTrackOutput(override val sampleRate: Int) : PcmOutput {
    override val channels: Int = 2
    private val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT).coerceAtLeast(4096)
    override val chunkFrames: Int = (minimum / 8 / 2).coerceIn(256, 2048)
    private val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
        .setBufferSizeInBytes(minimum * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
        .build()

    override fun start() {
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
    }

    override fun write(samples: FloatArray, frames: Int) {
        var offset = 0
        val count = frames * channels
        while (offset < count) {
            val written = track.write(samples, offset, count - offset, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) return
            offset += written
        }
    }

    override fun framesPlayed(): Long = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    override fun pause() {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    override fun flush() {
        track.flush()
    }

    override fun close() {
        runCatching { track.stop() }
        track.release()
    }
}

class MediaSongPlayer(
    private val players: List<MediaPlayer>,
    private val hit: () -> Unit
) : SongPlayer {
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

    override fun hitsound() = hit()

    override fun release() {
        playing = false
        players.forEach { runCatching { it.release() } }
    }
}

class AndroidSongPlayer private constructor(
    private val inner: SongPlayer,
    private val pool: SoundPool,
    private val folder: File
) : SongPlayer by inner {
    init {
        active += this
    }

    val usesMixer: Boolean get() = inner is StreamSongPlayer

    override fun release() {
        active -= this
        inner.release()
        pool.release()
        folder.deleteRecursively()
    }

    companion object {
        private val active = java.util.Collections.newSetFromMap(java.util.WeakHashMap<AndroidSongPlayer, Boolean>())

        fun pauseAll() {
            active.toList().forEach { if (it.isPlaying) it.pause() }
        }

        fun create(context: Context, audio: List<EditorAudio>): AndroidSongPlayer {
            val folder = File(context.cacheDir, "editor-audio/${System.nanoTime()}").apply { mkdirs() }
            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build()
            val hitId = File(folder, "hitsound.wav").also { it.writeBytes(Hitsound.wav()) }.let { pool.load(it.path, 1) }
            val hit = { pool.play(hitId, 0.7f, 0.7f, 1, 0, 1f); Unit }
            val files = audio.mapIndexed { index, track ->
                File(folder, "$index-${track.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}").also { it.writeBytes(track.bytes) }
            }
            val decoded = runCatching { files.map { AndroidDecoder.decode(it) } }.getOrNull()
            val inner: SongPlayer = decoded?.takeIf { it.isNotEmpty() }?.let { tracks ->
                runCatching {
                    val output = AudioTrackOutput(tracks.first().rate)
                    val mixer = Mixers.create(output.sampleRate, output.channels)
                    tracks.forEach { mixer.add(it) }
                    StreamSongPlayer(mixer, output, hit)
                }.getOrNull()
            } ?: MediaSongPlayer(
                files.mapNotNull { file ->
                    runCatching {
                        MediaPlayer().apply {
                            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            setDataSource(file.path)
                            prepare()
                        }
                    }.getOrNull()
                },
                hit
            )
            return AndroidSongPlayer(inner, pool, folder)
        }
    }
}
