package com.vconvertercrew.vconverter.overlay

object MemoryStats {
    private val nativeHeap: (() -> Long)? = runCatching {
        val method = Class.forName("android.os.Debug").getMethod("getNativeHeapAllocatedSize")
        val read: () -> Long = { (method.invoke(null) as? Long) ?: 0L }
        read()
        read
    }.getOrNull()

    fun usedBytes(): Long? {
        val runtime = Runtime.getRuntime()
        val heap = runtime.totalMemory() - runtime.freeMemory()
        return heap + (nativeHeap?.let { runCatching { it() }.getOrDefault(0L) } ?: 0L)
    }

    fun limitBytes(): Long? = Runtime.getRuntime().maxMemory().takeIf { it in 1 until Long.MAX_VALUE && nativeHeap == null }
}
