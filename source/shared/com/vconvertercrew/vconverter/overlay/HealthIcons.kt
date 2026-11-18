package com.vconvertercrew.vconverter.overlay

import androidx.compose.ui.graphics.ImageBitmap

class HealthIcons(private val image: (String) -> ImageBitmap?) {
    private val cache = HashMap<String, ImageBitmap?>()

    operator fun get(character: String): ImageBitmap? {
        val key = character.trim().lowercase()
        return cache.getOrPut(key) {
            candidates(key).firstNotNullOfOrNull { name -> runCatching { image(path(name)) }.getOrNull() }
        }
    }

    companion object {
        const val FALLBACK = "face"
        const val FRAME = 150

        val bundled = listOf(
            "bf", "spooky", "pico", "mom", "tankman", "face", "dad", "bf-old", "gf",
            "parents-christmas", "monster", "bf-pixel", "senpai", "spirit"
        )

        private val aliases = mapOf(
            "boyfriend" to "bf",
            "bf-car" to "bf",
            "bf-christmas" to "bf",
            "bf-holding-gf" to "bf",
            "bf-dead" to "bf",
            "bf-pixel-dead" to "bf-pixel",
            "bf-pixel-opponent" to "bf-pixel",
            "girlfriend" to "gf",
            "gf-car" to "gf",
            "gf-christmas" to "gf",
            "gf-pixel" to "gf",
            "gf-tankmen" to "gf",
            "mom-car" to "mom",
            "monster-christmas" to "monster",
            "senpai-angry" to "senpai",
            "spooky-kids" to "spooky",
            "skid-and-pump" to "spooky",
            "parents" to "parents-christmas",
            "pico-playable" to "pico",
            "pico-speaker" to "pico",
            "tankman-atlas" to "tankman",
            "daddy-dearest" to "dad",
            "mommy-mearest" to "mom"
        )

        fun path(name: String) = "images/icons/icon-$name.png"

        fun resolve(character: String): String {
            var name = character.trim().lowercase().removePrefix("icon-")
            while (true) {
                aliases[name]?.let { return it }
                if (name in bundled) return name
                val cut = name.lastIndexOf('-')
                if (cut <= 0) return FALLBACK
                name = name.substring(0, cut)
            }
        }

        private fun candidates(key: String): List<String> =
            listOf(key.removePrefix("icon-"), resolve(key), FALLBACK).filter { it.isNotEmpty() }.distinct()
    }
}
