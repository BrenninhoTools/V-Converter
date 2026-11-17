package com.vconvertercrew.vconverter.editorview

interface SongPlayer {
    val duration: Double
    val isPlaying: Boolean
    fun position(): Double
    fun play()
    fun pause()
    fun seek(ms: Double)
    fun hitsound()
    fun release()
}
