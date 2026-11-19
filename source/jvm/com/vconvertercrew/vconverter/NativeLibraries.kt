package com.vconvertercrew.vconverter

import java.io.File

object NativeLibraries {
    fun isAndroid(): Boolean =
        System.getProperty("java.vm.name").orEmpty().contains("Dalvik", true) ||
            System.getProperty("java.vendor").orEmpty().contains("Android", true)

    fun platformFolder(): String {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val arch = System.getProperty("os.arch").orEmpty().lowercase()
        val system = when {
            "win" in os -> "windows"
            "mac" in os || "darwin" in os -> "macos"
            else -> "linux"
        }
        val cpu = when (arch) {
            "amd64", "x86_64", "x64" -> "x64"
            "aarch64", "arm64" -> "arm64"
            else -> arch
        }
        return "$system-$cpu"
    }

    fun load(library: String, overrideProperty: String) {
        if (isAndroid()) {
            System.loadLibrary(library)
            return
        }
        val override = System.getProperty(overrideProperty)
        if (override != null) {
            System.load(File(override).absolutePath)
            return
        }
        val fileName = System.mapLibraryName(library)
        val resource = "native/${platformFolder()}/$fileName"
        val stream = NativeLibraries::class.java.classLoader?.getResourceAsStream(resource)
            ?: throw UnsatisfiedLinkError("$resource is not bundled")
        val folder = File(System.getProperty("java.io.tmpdir"), "v-converter-native").apply { mkdirs() }
        val target = File(folder, "${System.getProperty("user.name").orEmpty().hashCode().toUInt()}-$fileName")
        val bytes = stream.use { it.readBytes() }
        if (!target.exists() || target.length() != bytes.size.toLong() || !target.readBytes().contentEquals(bytes)) {
            val temp = File(folder, "${target.name}.${System.nanoTime()}")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
        System.load(target.absolutePath)
    }
}
