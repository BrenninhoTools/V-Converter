package com.vconvertercrew.vconverter.overlay

private fun jsHeapUsed(): Double = js("(globalThis.performance && performance.memory) ? performance.memory.usedJSHeapSize : -1")

private fun jsHeapLimit(): Double = js("(globalThis.performance && performance.memory) ? performance.memory.jsHeapSizeLimit : -1")

object MemoryStats {
    fun usedBytes(): Long? = jsHeapUsed().takeIf { it >= 0 }?.toLong()

    fun limitBytes(): Long? = jsHeapLimit().takeIf { it > 0 }?.toLong()
}
