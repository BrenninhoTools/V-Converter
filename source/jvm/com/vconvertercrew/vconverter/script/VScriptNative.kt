package com.vconvertercrew.vconverter.script

import com.vconvertercrew.vconverter.NativeLibraries

object VScriptNative {
    private const val LIBRARY = "vconverter_script"

    var loadError: String? = null
        private set

    val available: Boolean by lazy {
        val result = runCatching {
            NativeLibraries.load(LIBRARY, "vconverter.script.library")
            nativeVersion()
        }
        loadError = result.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
        result.isSuccess
    }

    fun convertLua(source: String, options: String): String? = if (available) nativeConvertLua(source, options) else null

    fun generate(spec: String): String? = if (available) nativeGenerate(spec) else null

    fun builtinEvent(name: String, className: String, id: String): String? = if (available) nativeBuiltinEvent(name, className, id) else null

    @JvmStatic private external fun nativeVersion(): String
    @JvmStatic private external fun nativeConvertLua(source: String, options: String): String?
    @JvmStatic private external fun nativeGenerate(spec: String): String?
    @JvmStatic private external fun nativeBuiltinEvent(name: String, className: String, id: String): String?
}
