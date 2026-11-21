package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.archive.ArchiveNative
import com.vconvertercrew.vconverter.archive.NativeZipModFiles
import com.vconvertercrew.vconverter.archive.NativeZipModWriter
import com.vconvertercrew.vconverter.io.Archives
import com.vconvertercrew.vconverter.mod.ArchiveKind
import com.vconvertercrew.vconverter.mod.DirectoryModFiles
import com.vconvertercrew.vconverter.mod.DirectoryModWriter
import com.vconvertercrew.vconverter.mod.ModConverter
import com.vconvertercrew.vconverter.mod.ModFiles
import com.vconvertercrew.vconverter.mod.ModInfo
import com.vconvertercrew.vconverter.mod.ModRun
import com.vconvertercrew.vconverter.mod.ModScanner
import com.vconvertercrew.vconverter.mod.ModWriter
import com.vconvertercrew.vconverter.mod.ParallelModWriter
import com.vconvertercrew.vconverter.mod.ZipExtractor
import com.vconvertercrew.vconverter.mod.ZipModWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class NativeArchiveTest {
    private val chart = """{"song":{"song":"Dad Battle","bpm":180,"speed":2,"needsVoices":true,"player1":"bf","player2":"dude","gfVersion":"gf","stage":"club","format":"psych_v1",
        "notes":[{"sectionBeats":4,"mustHitSection":true,"sectionNotes":[[0,0,0],[500,5,0]]}],"events":[[100,[["Change Character","dad","bf"]]]]}}"""
    private val character = """{"animations":[{"offsets":[0,0],"loop":false,"fps":24,"anim":"idle","indices":[],"name":"dude idle"}],
        "image":"characters/dude","position":[0,0],"healthicon":"dude","flip_x":false,"camera_position":[0,0],"sing_duration":4,"scale":1,"vocals_file":"dude"}"""
    private val stage = """{"directory":"","defaultZoom":0.9,"boyfriend":[770,100],"girlfriend":[400,130],"opponent":[100,100],"camera_boyfriend":[0,0],"camera_opponent":[0,0],"camera_girlfriend":[0,0]}"""
    private val week = """{"songs":[["Dad Battle","dude",[255,0,0]]],"weekCharacters":["dude","bf","gf"],"weekBackground":"club","storyName":"Club Night","weekName":"Week Dude","startUnlocked":true,"hideStoryMode":false,"hideFreeplay":false,"difficulties":""}"""
    private val xml = """<TextureAtlas imagePath="dude.png"><SubTexture name="dude idle0000" x="0" y="0" width="100" height="100"/></TextureAtlas>"""

    private val audio = Random(7).nextBytes(300_000)
    private val image = ByteArray(200_000) { (it % 251).toByte() }

    private fun archive(): File {
        val entries = linkedMapOf<String, ByteArray>(
            "My Mod/pack.json" to """{"name":"My Mod","description":"Test"}""".toByteArray(),
            "My Mod/data/dad-battle/dad-battle.json" to chart.toByteArray(),
            "My Mod/data/dad-battle/dad-battle-hard.json" to chart.toByteArray(),
            "My Mod/songs/dad-battle/Inst.ogg" to audio,
            "My Mod/songs/dad-battle/Voices-dude.ogg" to audio.copyOf(1000),
            "My Mod/characters/dude.json" to character.toByteArray(),
            "My Mod/images/characters/dude.png" to image,
            "My Mod/images/characters/dude.xml" to xml.toByteArray(),
            "My Mod/images/músicas/capa.png" to image.copyOf(10),
            "My Mod/stages/club.json" to stage.toByteArray(),
            "My Mod/weeks/weekdude.json" to week.toByteArray(),
            "My Mod/images/empty.png" to ByteArray(0),
            "My Mod/scripts/thing.lua" to "function onCreate() makeLuaSprite('a', '', 0, 0) end".toByteArray(),
            "../evil.txt" to "nope".toByteArray()
        )
        val file = Files.createTempFile("native-mod", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("My Mod/"))
            out.closeEntry()
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name)
                if (name.endsWith(".ogg")) {
                    entry.method = ZipEntry.STORED
                    entry.size = content.size.toLong()
                    entry.crc = java.util.zip.CRC32().also { it.update(content) }.value
                }
                out.putNextEntry(entry)
                out.write(content)
                out.closeEntry()
            }
        }
        return file
    }

    private fun zipContents(file: File): Map<String, ByteArray> = ZipFile(file).use { zip ->
        zip.entries().toList().filter { !it.isDirectory }.associate { it.name to zip.getInputStream(it).readBytes() }
    }

    private fun folderContents(root: File): Map<String, ByteArray> =
        root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }

    private fun assertSame(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
        assertEquals(expected.keys.sorted(), actual.keys.sorted())
        expected.forEach { (name, bytes) -> assertArrayEquals(name, bytes, actual.getValue(name)) }
    }

    private fun assertArrayEquals(name: String, expected: ByteArray, actual: ByteArray) {
        if (!expected.contentEquals(actual)) throw AssertionError("$name differs (${expected.size} vs ${actual.size} bytes)")
    }

    private fun convert(files: ModFiles, writer: ModWriter) {
        val scan = ModScanner.scan(files)
        ModConverter(files, scan, writer).convert(ModInfo("My Mod", "Test", "Me"))
    }

    @Test
    fun readsZipEntriesWithoutExtracting() {
        if (!ArchiveNative.available) return
        val zip = archive()
        val extracted = Files.createTempDirectory("java-ex").toFile()
        ZipExtractor.extract(zip, extracted)
        val java = DirectoryModFiles(extracted)
        NativeZipModFiles.open(zip)!!.use { native ->
            assertEquals(java.paths, native.paths)
            assertFalse(native.paths.any { "evil" in it })
            assertTrue("My Mod/images/músicas/capa.png" in native.paths)
            java.paths.forEach { path ->
                assertArrayEquals(path, java.open(path).use { it.readBytes() }, native.open(path).use { it.readBytes() })
                assertEquals(java.size(path), native.size(path))
            }
            val single = native.open("My Mod/songs/dad-battle/Inst.ogg")
            assertEquals(audio[0].toInt() and 0xFF, single.read())
            single.close()
        }
    }

    @Test
    fun nativeZipOutputMatchesTheJavaWriter() {
        if (!ArchiveNative.available) return
        val zip = archive()
        val extracted = Files.createTempDirectory("java-ex").toFile()
        ZipExtractor.extract(zip, extracted)
        val javaZip = Files.createTempFile("java-out", ".zip").toFile()
        ZipModWriter(javaZip.outputStream(), "My Mod").use { convert(DirectoryModFiles(extracted), it) }
        val expected = zipContents(javaZip)
        assertTrue(expected.keys.any { it.endsWith("ChangeCharacterEvent.hxc") })

        val fromZip = Files.createTempFile("native-out", ".zip").toFile()
        NativeZipModFiles.open(zip)!!.use { files -> NativeZipModWriter.create(fromZip, "My Mod")!!.use { convert(files, it) } }
        assertSame(expected, zipContents(fromZip))

        val fromFolder = Files.createTempFile("native-folder", ".zip").toFile()
        NativeZipModWriter.create(fromFolder, "My Mod")!!.use { convert(DirectoryModFiles(extracted), it) }
        assertSame(expected, zipContents(fromFolder))

        val parallel = Files.createTempFile("native-parallel", ".zip").toFile()
        NativeZipModFiles.open(zip)!!.use { files ->
            NativeZipModWriter.create(parallel, "My Mod")!!.use { target ->
                ModRun({}).execute(target, { ParallelModWriter(it) }) { writer, progress ->
                    ModConverter(files, ModScanner.scan(files), writer, progress).convert(ModInfo("My Mod", "Test", "Me"))
                }
            }
        }
        assertSame(expected, zipContents(parallel))

        val stream = ByteArrayOutputStream()
        val cache = Files.createTempDirectory("native-cache").toFile()
        NativeZipModFiles.open(zip)!!.use { files -> NativeZipModWriter.forStream(stream, "My Mod", cache)!!.use { convert(files, it) } }
        val streamed = LinkedHashMap<String, ByteArray>()
        ZipInputStream(stream.toByteArray().inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory) streamed[entry.name] = input.readBytes()
            }
        }
        assertSame(expected, streamed)
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test
    fun folderOutputExtractsStraightFromTheZip() {
        if (!ArchiveNative.available) return
        val zip = archive()
        val extracted = Files.createTempDirectory("java-ex").toFile()
        ZipExtractor.extract(zip, extracted)
        val javaOut = Files.createTempDirectory("java-folder").toFile()
        convert(DirectoryModFiles(extracted), DirectoryModWriter(javaOut))
        val nativeOut = Files.createTempDirectory("native-folder").toFile()
        NativeZipModFiles.open(zip)!!.use { convert(it, DirectoryModWriter(nativeOut)) }
        assertSame(folderContents(javaOut), folderContents(nativeOut))
    }

    @Test
    fun archivesOpenZipsDirectlyAndFallsBack() {
        val zip = archive()
        val cache = Files.createTempDirectory("archives").toFile()
        val opened = Archives.open(zip, zip.name, ArchiveKind.ZIP, cache)
        assertEquals(ArchiveNative.available, opened.direct)
        assertTrue("My Mod/data/dad-battle/dad-battle.json" in opened.files.paths)
        opened.delete()
        ArchiveNative.enabled = false
        try {
            val extracted = Archives.open(zip, zip.name, ArchiveKind.ZIP, cache)
            assertFalse(extracted.direct)
            assertTrue("My Mod/data/dad-battle/dad-battle.json" in extracted.files.paths)
            extracted.delete()
        } finally {
            ArchiveNative.enabled = true
        }
        assertTrue(cache.listFiles().isNullOrEmpty())
    }
}
