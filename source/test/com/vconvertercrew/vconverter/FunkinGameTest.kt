package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.launcher.FunkinGame
import com.vconvertercrew.vconverter.launcher.NativeLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FunkinGameTest {
    private fun zip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
    }

    @Test
    fun findsTheZipRoot() {
        assertEquals("My Mod", FunkinGame.zipRoot(listOf("My Mod/_polymod_metadata.json", "My Mod/data/songs/a.json")))
        assertNull(FunkinGame.zipRoot(listOf("_polymod_metadata.json", "data/songs/a.json")))
        assertNull(FunkinGame.zipRoot(listOf("a/x.json", "b/y.json")))
        assertNull(FunkinGame.zipRoot(emptyList()))
    }

    @Test
    fun extractsIntoTheModsFolder() {
        val dir = Files.createTempDirectory("funkin-game").toFile()
        try {
            val mods = File(dir, "mods").apply { mkdirs() }
            val flat = File(dir, "cool-mod.zip")
            zip(flat, mapOf("_polymod_metadata.json" to "{}", "data/songs/a/a-chart.json" to "chart", "../escape.txt" to "no"))
            val installed = FunkinGame.extract(flat, mods, "fallback")
            assertEquals(File(mods, "cool-mod").canonicalFile, installed.folder)
            assertEquals(2, installed.files)
            assertEquals("chart", File(mods, "cool-mod/data/songs/a/a-chart.json").readText())
            assertFalse(File(mods, "escape.txt").exists())
            assertFalse(File(dir, "escape.txt").exists())

            val nested = File(dir, "whatever.zip")
            zip(nested, mapOf("Week Mod/_polymod_metadata.json" to "{}", "Week Mod/images/x.png" to "png"))
            val second = FunkinGame.extract(nested, mods, "fallback")
            assertEquals("Week Mod", second.folder.name)
            assertTrue(File(mods, "Week Mod/images/x.png").isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun searchRootsIncludeCommonPlaces() {
        val roots = FunkinGame.searchRoots({ if (it == "LOCALAPPDATA") "C:/Users/a/AppData/Local" else null }, "/home/a")
        assertTrue(roots.contains(File("/home/a", "Downloads").path))
        assertTrue(roots.contains(File("C:/Users/a/AppData/Local", "itch/apps").path))
        assertEquals(roots.size, roots.distinct().size)
    }

    @Test
    fun nativeFindsInstallsAndLaunches() {
        if (System.getProperty("vconverter.launcher.library") == null) return
        assertTrue(NativeLauncher.loadError ?: "", NativeLauncher.loaded)
        assertEquals("1.0.0", NativeLauncher.version())
        val dir = Files.createTempDirectory("funkin-native").toFile().canonicalFile
        try {
            val folder = File(dir, "funkin-linux-64bit").apply { mkdirs() }
            val marker = File(dir, "ran.txt")
            val game = File(folder, "Funkin")
            game.writeText("#!/bin/sh\ntouch \"${marker.path}\"\n")
            game.setExecutable(true)
            assertNull(FunkinGame.search(listOf(File(dir, "nope").path)))
            val install = FunkinGame.search(listOf(dir.path))
            assertNotNull(install)
            assertEquals(game, install!!.executable)
            assertEquals(File(folder, "mods"), install.mods)
            assertTrue(install.mods.isDirectory)

            val converted = File(dir, "out/Converted").apply { mkdirs() }
            File(converted, "_polymod_metadata.json").writeText("{}")
            File(converted, "data").mkdirs()
            File(converted, "data/a.json").writeText("a")
            val installed = FunkinGame.install(install, converted, "x")
            assertEquals(2, installed.files)
            assertEquals("a", File(install.mods, "Converted/data/a.json").readText())

            FunkinGame.launch(install)
            var waited = 0
            while (!marker.exists() && waited < 100) {
                Thread.sleep(20)
                waited++
            }
            assertTrue(marker.exists())
            assertEquals(NativeLauncher.NOT_FOUND, NativeLauncher.launch(File(dir, "missing").path))
        } finally {
            dir.deleteRecursively()
        }
    }
}
