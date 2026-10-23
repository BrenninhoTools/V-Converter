package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.convert.PsychChartsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameChartsConverter
import com.brenninhotools.vconverter.io.Archives
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.mod.ArchiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileConverterTest {
    private val chart = """{"song":{"song":"Test Song","bpm":120,"speed":2,"player1":"bf","player2":"dad","gfVersion":"gf","stage":"stage","needsVoices":true,
        "notes":[{"sectionBeats":4,"mustHitSection":true,"sectionNotes":[[0,0,0],[500,5,0]]}],"events":[]}}"""

    private fun file(path: String, text: String) = ImportedFile(path.substringAfterLast('/'), path, true) { text.byteInputStream() }

    @Test
    fun psychChartsGroupBySong() {
        val converter = PsychChartsConverter()
        val items = listOf(file("data/test-song/test-song-hard.json", chart), file("data/test-song/test-song.json", chart)).map { converter.classify(it, converter.read(it)) }
        assertEquals(listOf("test-song · hard", "test-song · normal"), items.map { it.label })
        val output = converter.build(items)
        assertTrue(output.files.any { it.relativePath == "data/songs/test-song/test-song-metadata.json" })
        assertTrue(output.files.any { it.relativePath == "data/songs/test-song/test-song-chart.json" })
    }

    @Test
    fun psychChartsBecomeCodenameSong() {
        val converter = PsychToCodenameChartsConverter()
        val items = listOf(file("data/test-song/test-song-hard.json", chart)).map { converter.classify(it, converter.read(it)) }
        val paths = converter.build(items).files.map { it.relativePath }
        assertTrue("songs/test-song/charts/hard.json" in paths)
        assertTrue("songs/test-song/meta.json" in paths)
    }

    @Test
    fun zipArchivesExtract() {
        val dir = Files.createTempDirectory("arch").toFile()
        val zip = File(dir, "mod.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("Mod/data/test/test.json"))
            out.write(chart.toByteArray())
            out.closeEntry()
        }
        assertEquals(ArchiveKind.ZIP, Archives.kind(zip))
        val extracted = Archives.extract(zip, zip.name, ArchiveKind.ZIP, File(dir, "cache"))
        assertEquals(listOf("Mod/data/test/test.json"), extracted.files.paths)
        extracted.delete()
        assertTrue(!extracted.directory.exists())
    }
}
