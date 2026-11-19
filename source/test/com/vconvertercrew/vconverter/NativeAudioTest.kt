package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.audio.AudioQuality
import com.vconvertercrew.vconverter.audio.NativeMixer
import com.vconvertercrew.vconverter.audio.PcmAudio
import com.vconvertercrew.vconverter.audio.VMiniAudioNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAudioTest {
    private val available: Boolean
        get() {
            if (System.getProperty("vconverter.audio.library") == null) return false
            assertTrue(VMiniAudioNative.loadError ?: "", VMiniAudioNative.loaded)
            return true
        }

    @Test
    fun decodesOggNatively() {
        if (!available) return
        val bytes = javaClass.classLoader!!.getResourceAsStream("sounds/scrollMenu.ogg")!!.readBytes()
        val pcm = VMiniAudioNative.decodePcm(bytes)
        assertNotNull(pcm)
        assertTrue(pcm!!.frames > 1000)
        assertTrue(pcm.samples.any { it.toInt() != 0 })
        assertEquals("0.2.0", VMiniAudioNative.version())
    }

    @Test
    fun mixerFeaturesWork() {
        if (!available) return
        val mixer = NativeMixer.create(48000, 2)!!
        assertTrue(mixer.advanced)
        val tone = PcmAudio(ShortArray(48000 * 2) { 16384 }, 2, 48000)
        assertTrue(mixer.add(tone))
        assertTrue(mixer.add(tone))
        mixer.setQuality(AudioQuality.CUBIC)
        mixer.play()
        val out = FloatArray(960 * 2)
        mixer.render(out, 960)
        assertEquals(1.0f, out[100], 0.001f)
        val meter = mixer.meter()!!
        assertTrue(meter.peakLeft > 0.99f)
        mixer.setTrackBus(1, 2)
        mixer.setBusVolume(2, 0f)
        mixer.render(out, 960)
        assertEquals(0.5f, out[100], 0.001f)
        mixer.fadeTrack(0, 0f, 0.01)
        mixer.render(out, 960)
        assertEquals(0f, out[1900], 0.001f)
        assertEquals(2880.0 / 48000.0, mixer.clock, 1e-9)
        mixer.release()
    }
}
