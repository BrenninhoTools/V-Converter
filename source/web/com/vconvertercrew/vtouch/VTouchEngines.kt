package com.vconvertercrew.vtouch

object VTouchEngines {
    val backend: String get() = "Kotlin"

    fun create(config: VTouchConfig): VTouchEngine = KotlinTouchEngine(config)
}
