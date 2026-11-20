package com.vconvertercrew.vconverter.script

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import vscript.vscript_builtin_event
import vscript.vscript_convert_lua
import vscript.vscript_free
import vscript.vscript_generate

object VScriptNative {
    val available: Boolean = true

    private fun take(result: CPointer<ByteVar>?): String? {
        if (result == null) return null
        return try {
            result.toKString()
        } finally {
            vscript_free(result)
        }
    }

    fun convertLua(source: String, options: String): String? = take(vscript_convert_lua(source, options))

    fun generate(spec: String): String? = take(vscript_generate(spec))

    fun builtinEvent(name: String, className: String, id: String): String? = take(vscript_builtin_event(name, className, id))
}
