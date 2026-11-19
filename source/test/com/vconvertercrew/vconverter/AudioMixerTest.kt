package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.audio.AudioMixer
import com.vconvertercrew.vconverter.audio.Mixers
import com.vconvertercrew.vconverter.audio.NativeMixer
import com.vconvertercrew.vconverter.audio.PcmAudio
import com.vconvertercrew.vconverter.audio.PcmOutput
import com.vconvertercrew.vconverter.audio.SoftMixer
import com.vconvertercrew.vconverter.audio.StreamSongPlayer
import com.vconvertercrew.vconverter.audio.VMiniAudioNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioMixerTest {
    private fun tone(frames: Int, rate: Int, channels: Int, frequency: Double, amplitude: Double): PcmAudio {
        val samples = ShortArray(frames * channels)
        for (f in 0 until frames) {
            val value = (sin(2 * PI * frequency * f / rate) * amplitude * 32767).toInt().toShort()
            for (c in 0 until channels) samples[f * channels + c] = value
        }
        return PcmAudio(samples, channels, rate)
    }

    private fun fill(mixer: AudioMixer) {
        mixer.add(tone(44100, 44100, 2, 440.0, 0.4))
        mixer.add(tone(22050, 22050, 1, 220.0, 0.3))
    }

    @Test
    fun softMixerMixesResamplesAndStops() {
        val mixer = SoftMixer(44100)
        fill(mixer)
        assertEquals(2, mixer.trackCount)
        assertEquals(1.0, mixer.duration, 1e-9)
        val out = FloatArray(512 * 2)
        mixer.render(out, 512)
        assertTrue(out.all { it == 0f })
        mixer.play()
        mixer.render(out, 512)
        assertTrue(out.any { it != 0f })
        mixer.seek(0.999)
        mixer.render(out, 512)
        mixer.render(out, 512)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun nativeMixerMatchesKotlinMixer() {
        assumeTrue(VMiniAudioNative.loaded)
        val native = NativeMixer.create(48000, 2)!!
        val soft = SoftMixer(48000)
        fill(native)
        fill(soft)
        native.setQuality(com.vconvertercrew.vconverter.audio.AudioQuality.LINEAR)
        native.setLimiter(false)
        assertTrue(native.backend.startsWith("V-MiniAudio"))
        assertEquals(soft.duration, native.duration, 1e-9)
        native.setMasterVolume(0.8f)
        soft.setMasterVolume(0.8f)
        native.play()
        soft.play()
        val a = FloatArray(1024 * 2)
        val b = FloatArray(1024 * 2)
        repeat(10) {
            native.render(a, 1024)
            soft.render(b, 1024)
            for (i in a.indices) assertEquals("sample $i", b[i], a[i], 2e-4f)
        }
        native.seek(0.5)
        soft.seek(0.5)
        native.setPitch(1.5f)
        soft.setPitch(1.5f)
        native.render(a, 1024)
        soft.render(b, 1024)
        for (i in a.indices) assertEquals(b[i], a[i], 2e-4f)
        native.release()
        native.render(a, 16)
    }

    @Test
    fun renderAllProducesWholeSong() {
        val mixer = Mixers.create(44100)
        fill(mixer)
        val all = AudioMixer.renderAll(mixer)
        assertEquals(44100 * 2, all.size)
        assertTrue(all.take(2000).any { abs(it) > 0.1f })
        mixer.release()
    }

    private class FakeOutput : PcmOutput {
        override val sampleRate = 44100
        override val channels = 2
        override val chunkFrames = 441
        @Volatile var played = 0L
        @Volatile var running = false
        var writes = 0

        override fun start() { running = true }
        override fun write(samples: FloatArray, frames: Int) {
            writes++
            Thread.sleep(2)
            if (running) played += frames
        }
        override fun framesPlayed(): Long = played
        override fun pause() { running = false }
        override fun flush() {}
        override fun close() { running = false }
    }

    @Test
    fun streamPlayerTracksPositionAndFinishes() {
        val mixer = Mixers.create(44100)
        mixer.add(tone(4410, 44100, 2, 440.0, 0.4))
        val output = FakeOutput()
        val player = StreamSongPlayer(mixer, output)
        assertEquals(100.0, player.duration, 1e-6)
        assertFalse(player.isPlaying)
        player.seek(50.0)
        assertEquals(50.0, player.position(), 1e-6)
        player.play()
        assertTrue(player.isPlaying)
        val deadline = System.currentTimeMillis() + 3000
        while (player.isPlaying && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertFalse(player.isPlaying)
        assertEquals(100.0, player.position(), 1e-6)
        player.play()
        assertTrue(player.position() < 20.0)
        player.pause()
        player.release()
        assertTrue(output.writes > 0)
    }
}
