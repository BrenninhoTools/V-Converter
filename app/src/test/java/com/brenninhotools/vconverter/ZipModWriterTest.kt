package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.mod.ZipModWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ZipModWriterTest {
    @Test
    fun writesEntriesUnderPrefixOnce() {
        val bytes = ByteArrayOutputStream()
        ZipModWriter(bytes, "My Mod/").use { zip ->
            zip.writeText("data/songs/a/a-chart.json", "{}")
            zip.copy("songs/a/Inst.ogg") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            zip.writeText("/data/songs/a/a-chart.json", "dup")
            assertEquals(2, zip.entries)
        }
        val found = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                found[entry.name] = input.readBytes().size.toString()
            }
        }
        assertEquals(mapOf("My Mod/data/songs/a/a-chart.json" to "2", "My Mod/songs/a/Inst.ogg" to "3"), found)
    }

    @Test
    fun folderNameDropsIllegalCharacters() {
        assertEquals("Vs Dude 2", ZipModWriter.folderName("Vs: Dude/2?".replace("/", " ")).replace("  ", " "))
        assertEquals("V-Slice Mod", ZipModWriter.folderName("  "))
    }
}
