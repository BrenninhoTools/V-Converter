package com.vconvertercrew.vconverter.launcher

import java.io.File
import java.util.zip.ZipFile

class FunkinInstall(val executable: File, val mods: File)

class InstalledMod(val folder: File, val files: Int)

class FunkinLaunchException(message: String) : Exception(message)

object FunkinGame {
    const val TITLE = "Friday Night Funkin'"
    const val PACKAGE = "me.funkin.fnf"

    val available: Boolean get() = NativeLauncher.loaded

    private fun native() {
        if (!NativeLauncher.loaded) throw FunkinLaunchException("The V-Launcher library could not be loaded: ${NativeLauncher.loadError ?: "unknown error"}.")
    }

    fun searchRoots(env: (String) -> String? = System::getenv, home: String = System.getProperty("user.home").orEmpty()): List<String> {
        val roots = mutableListOf<String>()
        fun add(path: String?) {
            if (!path.isNullOrBlank()) roots += path
        }
        listOf("Desktop", "Downloads", "Games", "Documents", "Applications").forEach { add(if (home.isEmpty()) null else File(home, it).path) }
        add(env("ProgramFiles"))
        add(env("ProgramFiles(x86)"))
        env("LOCALAPPDATA")?.let { add(File(it, "itch/apps").path); add(File(it, "Programs").path) }
        env("APPDATA")?.let { add(File(it, "itch/apps").path) }
        add("/Applications")
        if (home.isNotEmpty()) {
            add(File(home, "Library/Application Support/itch/apps").path)
            add(File(home, ".config/itch/apps").path)
            add(File(home, ".local/share/itch/apps").path)
        }
        return roots.distinct()
    }

    fun resolve(path: File): FunkinInstall? {
        native()
        val executable = NativeLauncher.resolve(path.absolutePath) ?: return null
        val mods = NativeLauncher.modsFolder(executable) ?: throw FunkinLaunchException("Could not open the mods folder of $executable.")
        return FunkinInstall(File(executable), File(mods))
    }

    fun search(roots: List<String> = searchRoots()): FunkinInstall? {
        native()
        val found = NativeLauncher.search(roots.toTypedArray()) ?: return null
        return resolve(File(found))
    }

    fun install(game: FunkinInstall, output: File, fallbackName: String): InstalledMod {
        if (output.isDirectory) {
            native()
            val name = output.name.ifBlank { fallbackName }
            val files = NativeLauncher.installFolder(output.absolutePath, game.mods.absolutePath, name)
            if (files < 0) throw FunkinLaunchException(
                when (-files) {
                    NativeLauncher.NOT_FOUND -> "${output.path} is gone."
                    NativeLauncher.INVALID -> "Can't copy ${output.name} into the game's mods folder."
                    else -> "Could not copy the mod into ${game.mods.path}."
                }
            )
            return InstalledMod(File(game.mods, name), files)
        }
        return extract(output, game.mods, fallbackName)
    }

    fun zipRoot(names: List<String>): String? {
        val tops = names.map { it.replace('\\', '/').trimStart('/') }.filter { it.isNotEmpty() }.map { it.substringBefore('/') to it.contains('/') }
        if (tops.isEmpty() || tops.any { !it.second }) return null
        return tops.map { it.first }.distinct().singleOrNull()
    }

    fun extract(zip: File, mods: File, fallbackName: String): InstalledMod {
        ZipFile(zip).use { file ->
            val entries = file.entries().toList()
            val root = zipRoot(entries.filter { !it.isDirectory }.map { it.name })
            val name = (root ?: zip.nameWithoutExtension.ifBlank { fallbackName }).replace(Regex("[<>:\"/\\\\|?*]"), "_").trim('.', ' ').ifEmpty { fallbackName }
            val target = File(mods, name).canonicalFile
            val base = target.path + File.separator
            var count = 0
            entries.filter { !it.isDirectory }.forEach { entry ->
                val path = entry.name.replace('\\', '/').trimStart('/').let { if (root != null) it.removePrefix("$root/") else it }
                val out = File(target, path).canonicalFile
                if (!out.path.startsWith(base)) return@forEach
                out.parentFile?.mkdirs()
                file.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                count++
            }
            return InstalledMod(target, count)
        }
    }

    fun launch(game: FunkinInstall) {
        native()
        when (NativeLauncher.launch(game.executable.absolutePath)) {
            NativeLauncher.OK -> Unit
            NativeLauncher.NOT_FOUND -> throw FunkinLaunchException("${game.executable.path} is gone.")
            else -> throw FunkinLaunchException("Could not open ${game.executable.name}.")
        }
    }
}
