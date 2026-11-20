package com.vconvertercrew.vconverter.launcher

import com.vconvertercrew.vconverter.NativeLibraries

object NativeLauncher {
    private const val LIBRARY = "vconverter_launcher"

    const val OK = 0
    const val NOT_FOUND = 1
    const val INVALID = 2
    const val FAILED = 3
    const val UNSUPPORTED = 4

    var loadError: String? = null
        private set

    val loaded: Boolean by lazy {
        val result = runCatching {
            NativeLibraries.load(LIBRARY, "vconverter.launcher.library")
            version()
        }
        loadError = result.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
        result.isSuccess
    }

    @JvmStatic external fun version(): String
    @JvmStatic external fun resolve(path: String): String?
    @JvmStatic external fun search(roots: Array<String>): String?
    @JvmStatic external fun modsFolder(executable: String): String?
    @JvmStatic external fun installFolder(source: String, mods: String, name: String): Int
    @JvmStatic external fun launch(executable: String): Int
    @JvmStatic external fun launchPackage(context: Any, packageName: String): Int
    @JvmStatic external fun installed(context: Any, packageName: String): Boolean
    @JvmStatic external fun openStore(context: Any, packageName: String): Int
}
