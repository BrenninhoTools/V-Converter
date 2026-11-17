package com.vconvertercrew.vconverter.web

import com.vconvertercrew.vconverter.platform.Browser

object Prefs {
    private const val PREFIX = "vconverter."

    fun getBoolean(key: String, fallback: Boolean): Boolean = Browser.storageGet(PREFIX + key)?.toBooleanStrictOrNull() ?: fallback

    fun putBoolean(key: String, value: Boolean) = Browser.storageSet(PREFIX + key, value.toString())

    fun getFloat(key: String, fallback: Float): Float = Browser.storageGet(PREFIX + key)?.toFloatOrNull() ?: fallback

    fun putFloat(key: String, value: Float) = Browser.storageSet(PREFIX + key, value.toString())

    fun get(key: String, fallback: String): String = Browser.storageGet(PREFIX + key) ?: fallback

    fun put(key: String, value: String) = Browser.storageSet(PREFIX + key, value)
}
