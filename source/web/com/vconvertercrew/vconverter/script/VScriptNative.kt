package com.vconvertercrew.vconverter.script

object VScriptNative {
    val available: Boolean = false

    fun convertLua(source: String, options: String): String? = null

    fun generate(spec: String): String? = null

    fun builtinEvent(name: String, className: String, id: String): String? = null

    fun convertData(kind: String, input: String): String? = null
}
