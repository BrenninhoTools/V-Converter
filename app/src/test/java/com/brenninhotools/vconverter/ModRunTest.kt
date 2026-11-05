package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModAbortedException
import com.brenninhotools.vconverter.mod.ModCancel
import com.brenninhotools.vconverter.mod.ModCancelledException
import com.brenninhotools.vconverter.mod.ModCategory
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModRun
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.ParallelModWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

class ModRunTest {
    private val chart = """{"song":{"song":"Tune","bpm":120,"speed":2,"needsVoices":true,"player1":"bf","player2":"dad","gfVersion":"gf","stage":"stage","format":"psych_v1",
        "notes":[{"sectionBeats":4,"mustHitSection":true,"sectionNotes":[[0,0,0]]}],"events":[]}}"""

    private fun mod(images: Int = 3): File {
        val root = Files.createTempDirectory("modrun").toFile()
        File(root, "data/tune").mkdirs()
        File(root, "data/tune/tune.json").writeText(chart)
        File(root, "songs/tune").mkdirs()
        File(root, "songs/tune/Inst.ogg").writeBytes(ByteArray(64) { it.toByte() })
        File(root, "images").mkdirs()
        repeat(images) { File(root, "images/pic$it.png").writeBytes(ByteArray(32) { b -> (b * it).toByte() }) }
        return root
    }

    private class FlakyWriter(private val base: ModWriter, private val broken: (String) -> Boolean) : ModWriter {
        override fun writeText(path: String, text: String) {
            if (broken(path)) throw IOException("disk said no")
            base.writeText(path, text)
        }

        override fun copy(path: String, source: () -> InputStream) {
            if (broken(path)) throw IOException("disk said no")
            base.copy(path, source)
        }
    }

    @Test
    fun brokenFilesAreSkippedAndReported() {
        val root = mod()
        val files = DirectoryModFiles(root)
        val scan = ModScanner.scan(files)
        val out = Files.createTempDirectory("modrun-out").toFile()
        val updates = mutableListOf<ModProgress>()
        val result = ModRun({ updates += it }).execute(FlakyWriter(DirectoryModWriter(out)) { it.endsWith("pic1.png") }) { writer, progress ->
            ModConverter(files, scan, writer, progress).convert(ModInfo("Tune", "", ""))
        }
        assertTrue(File(out, "images/pic0.png").exists())
        assertTrue(File(out, "images/pic2.png").exists())
        assertTrue(File(out, "_polymod_meta.json").exists())
        assertTrue(result.report.sections.any { section -> section.title == "Files skipped because of errors" && section.lines.any { "pic1.png" in it } })
        val last = updates.last()
        assertEquals("Done", last.label)
        assertEquals(1, last.skipped)
        assertTrue((last.counts[ModCategory.IMAGES] ?: 0) >= 2)
        assertTrue((last.counts[ModCategory.AUDIO] ?: 0) >= 1)
    }

    @Test
    fun cancelStopsTheConversion() {
        val root = mod(images = 40)
        val files = DirectoryModFiles(root)
        val scan = ModScanner.scan(files)
        val out = Files.createTempDirectory("modrun-cancel").toFile()
        val cancel = ModCancel()
        var writes = 0
        val counting = object : ModWriter {
            val base = DirectoryModWriter(out)
            override fun writeText(path: String, text: String) {
                base.writeText(path, text)
            }

            override fun copy(path: String, source: () -> InputStream) {
                if (++writes == 5) cancel.cancel()
                base.copy(path, source)
            }
        }
        try {
            ModRun({}, cancel).execute(counting) { writer, progress -> ModConverter(files, scan, writer, progress).convert(ModInfo("Tune", "", "")) }
            fail("Expected cancellation")
        } catch (e: ModCancelledException) {
            assertTrue(writes < 40)
        }
    }

    @Test
    fun manyFailuresInARowAbort() {
        val root = mod(images = 40)
        val files = DirectoryModFiles(root)
        val scan = ModScanner.scan(files)
        try {
            ModRun({}).execute(FlakyWriter(DirectoryModWriter(Files.createTempDirectory("x").toFile())) { true }) { writer, progress ->
                ModConverter(files, scan, writer, progress).convert(ModInfo("Tune", "", ""))
            }
            fail("Expected abort")
        } catch (e: ModAbortedException) {
            assertTrue(e.message!!.contains("in a row"))
        }
    }

    @Test
    fun parallelWriterMatchesSerialOutput() {
        val root = mod(images = 30)
        val files = DirectoryModFiles(root)
        val scan = ModScanner.scan(files)
        val serial = Files.createTempDirectory("serial").toFile()
        val parallel = Files.createTempDirectory("parallel").toFile()
        ModRun({}).execute(DirectoryModWriter(serial)) { w, p -> ModConverter(files, scan, w, p).convert(ModInfo("Tune", "", "")) }
        ModRun({}).execute(DirectoryModWriter(parallel), { ParallelModWriter(it) }) { w, p -> ModConverter(files, scan, w, p).convert(ModInfo("Tune", "", "")) }
        val a = serial.walkTopDown().filter { it.isFile }.associate { it.relativeTo(serial).invariantSeparatorsPath to it.readBytes().toList() }
        val b = parallel.walkTopDown().filter { it.isFile }.associate { it.relativeTo(parallel).invariantSeparatorsPath to it.readBytes().toList() }
        assertEquals(a.keys, b.keys)
        assertEquals(a, b)
    }

    @Test
    fun categoriesFollowPaths() {
        assertEquals(ModCategory.AUDIO, ModCategory.of("songs/tune/Inst.ogg"))
        assertEquals(ModCategory.CHARTS, ModCategory.of("data/songs/tune/tune-chart.json"))
        assertEquals(ModCategory.CHARACTERS, ModCategory.of("data/characters/dad.json"))
        assertEquals(ModCategory.STAGES, ModCategory.of("data/stages/club.json"))
        assertEquals(ModCategory.SCRIPTS, ModCategory.of("scripts/songs/tune.hxc"))
        assertEquals(ModCategory.IMAGES, ModCategory.of("images/characters/dad.xml"))
        assertEquals(ModCategory.WEEKS, ModCategory.of("data/levels/week1.json"))
        assertEquals("Charts", ModCategory.phase("Song tune"))
    }
}
