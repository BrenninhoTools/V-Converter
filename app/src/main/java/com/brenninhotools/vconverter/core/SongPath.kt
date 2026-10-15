package com.brenninhotools.vconverter.core

object SongPath {
    private val invalidChars = Regex("[~&;:<>#\\s]")
    private val hideChars = Regex("[.,'\"%?!]")

    fun format(name: String): String =
        name.replace(invalidChars, "-").replace(hideChars, "").trim().lowercase()
}
