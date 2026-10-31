package com.brenninhotools.vconverter.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.Hitsound
import com.brenninhotools.vconverter.editorview.SongPlayer
import com.brenninhotools.vconverter.overlay.UiSound
import com.brenninhotools.vconverter.platform.File
import com.brenninhotools.vconverter.platform.toNSData
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL
import stbvorbis.vc_decode_ogg
import stbvorbis.vc_free

class PcmTrack(val samples: ShortArray, val rate: Int)

object AudioDecoder {
    private fun ogg(bytes: ByteArray): PcmTrack = memScoped {
        val channels = alloc<IntVar>()
        val rate = alloc<IntVar>()
        val output = alloc<CPointerVar<ShortVar>>()
        val frames = bytes.usePinned { pinned ->
            vc_decode_ogg(pinned.addressOf(0).reinterpret<UByteVar>(), bytes.size, channels.ptr, rate.ptr, output.ptr)
        }
        val data = output.value
        if (frames <= 0 || data == null) throw IllegalArgumentException("Not a valid Ogg Vorbis file.")
        val count = channels.value.coerceAtLeast(1)
        val out = ShortArray(frames * 2)
        for (f in 0 until frames) {
            val left = data[f * count]
            val right = if (count > 1) data[f * count + 1] else left
            out[f * 2] = left
            out[f * 2 + 1] = right
        }
        vc_free(data)
        PcmTrack(out, rate.value)
    }

    private fun other(name: String, bytes: ByteArray): PcmTrack {
        val temp = File(File.temporaryDirectory, "decode-" + name.replace('/', '_'))
        temp.writeBytes(bytes)
        try {
            val file = AVAudioFile(forReading = NSURL.fileURLWithPath(temp.path), error = null)
            val format = file.processingFormat
            val frames = file.length.toInt()
            val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt()) ?: throw IllegalArgumentException("Could not read $name.")
            file.readIntoBuffer(buffer, null)
            val length = buffer.frameLength.toInt()
            val channels = format.channelCount.toInt().coerceAtLeast(1)
            val data = buffer.floatChannelData ?: throw IllegalArgumentException("Could not read $name.")
            val left = data[0] ?: throw IllegalArgumentException("Could not read $name.")
            val right = if (channels > 1) data[1] ?: left else left
            val out = ShortArray(length * 2)
            for (i in 0 until length) {
                out[i * 2] = (left[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                out[i * 2 + 1] = (right[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            return PcmTrack(out, format.sampleRate.toInt())
        } finally {
            temp.delete()
        }
    }

    fun decode(audio: EditorAudio): PcmTrack =
        if (audio.name.substringAfterLast('.').equals("ogg", true)) ogg(audio.bytes) else other(audio.name, audio.bytes)

    fun resample(track: PcmTrack, rate: Int): ShortArray {
        if (track.rate == rate) return track.samples
        val frames = track.samples.size / 2
        val outFrames = (frames.toLong() * rate / track.rate).toInt()
        val out = ShortArray(outFrames * 2)
        val step = track.rate.toDouble() / rate
        for (f in 0 until outFrames) {
            val src = f * step
            val i = src.toInt().coerceAtMost(frames - 1)
            val next = (i + 1).coerceAtMost(frames - 1)
            val t = src - i
            for (c in 0..1) out[f * 2 + c] = (track.samples[i * 2 + c] * (1 - t) + track.samples[next * 2 + c] * t).toInt().toShort()
        }
        return out
    }

    fun wav(samples: ShortArray, rate: Int): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        fun putInt(at: Int, v: Int) {
            out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte(); out[at + 2] = (v shr 16).toByte(); out[at + 3] = (v shr 24).toByte()
        }
        fun putShort(at: Int, v: Int) {
            out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte()
        }
        "RIFF".encodeToByteArray().copyInto(out, 0)
        putInt(4, 36 + dataSize)
        "WAVEfmt ".encodeToByteArray().copyInto(out, 8)
        putInt(16, 16)
        putShort(20, 1)
        putShort(22, 2)
        putInt(24, rate)
        putInt(28, rate * 4)
        putShort(32, 4)
        putShort(34, 16)
        "data".encodeToByteArray().copyInto(out, 36)
        putInt(40, dataSize)
        var j = 44
        for (s in samples) {
            out[j++] = s.toByte()
            out[j++] = (s.toInt() shr 8).toByte()
        }
        return out
    }
}

class IosSongPlayer private constructor(wav: ByteArray?, frames: Int, rate: Int) : SongPlayer {
    private val player: AVAudioPlayer? = wav?.let { AVAudioPlayer(data = it.toNSData(), error = null) }?.also { it.prepareToPlay() }
    private val hits: List<AVAudioPlayer> = List(4) { AVAudioPlayer(data = Hitsound.wav().toNSData(), error = null) }
    private var nextHit = 0
    private var paused = 0.0

    override val duration: Double = if (rate > 0) frames * 1000.0 / rate else 0.0

    override val isPlaying: Boolean get() = player?.playing == true

    override fun position(): Double {
        val p = player ?: return paused
        return if (p.playing) p.currentTime * 1000.0 else paused
    }

    override fun play() {
        val p = player ?: return
        if (paused >= duration - 1) paused = 0.0
        p.currentTime = paused / 1000.0
        p.play()
    }

    override fun pause() {
        val p = player ?: return
        paused = p.currentTime * 1000.0
        p.pause()
    }

    override fun seek(ms: Double) {
        paused = ms.coerceIn(0.0, duration)
        player?.currentTime = paused / 1000.0
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    override fun hitsound() {
        val h = hits[nextHit]
        nextHit = (nextHit + 1) % hits.size
        h.currentTime = 0.0
        h.play()
    }

    override fun release() {
        player?.stop()
        hits.forEach { it.stop() }
    }

    companion object {
        fun create(audio: List<EditorAudio>): Pair<IosSongPlayer, List<String>> {
            val failures = ArrayList<String>()
            val tracks = audio.mapNotNull { a -> runCatching { AudioDecoder.decode(a) }.onFailure { failures += a.name }.getOrNull() }
            val rate = tracks.firstOrNull()?.rate ?: 44100
            val resampled = tracks.map { AudioDecoder.resample(it, rate) }
            val mixed = ShortArray(resampled.maxOfOrNull { it.size } ?: 0)
            if (resampled.size == 1) {
                resampled[0].copyInto(mixed)
            } else {
                for (i in mixed.indices) {
                    var sum = 0
                    for (t in resampled) if (i < t.size) sum += t[i]
                    mixed[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            val frames = mixed.size / 2
            return IosSongPlayer(if (frames > 0) AudioDecoder.wav(mixed, rate) else null, frames, rate) to failures
        }
    }
}

@Composable
fun MenuMusic(suppressed: Boolean) {
    val player by produceState<IosSongPlayer?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Resources.bytes("music/freakyMenu.ogg") ?: return@runCatching null
                IosSongPlayer.create(listOf(EditorAudio("freakyMenu.ogg", bytes))).first.takeIf { it.duration > 0 }
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
        val players = runCatching {
            val bytes = Resources.bytes(UiSound.SCROLL) ?: return@runCatching emptyList()
            val track = AudioDecoder.decode(EditorAudio("scrollMenu.ogg", bytes))
            val wav = AudioDecoder.wav(track.samples, track.rate).toNSData()
            List(3) { AVAudioPlayer(data = wav, error = null).apply { prepareToPlay() } }
        }.getOrDefault(emptyList())
        var next = 0
        if (players.isNotEmpty()) {
            UiSound.player = {
                val p = players[next]
                next = (next + 1) % players.size
                p.stop()
                p.currentTime = 0.0
                p.play()
            }
        }
        onDispose {
            UiSound.player = null
            players.forEach { it.stop() }
        }
    }
}
