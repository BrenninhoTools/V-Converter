package com.brenninhotools.vconverter.ios

import platform.Foundation.NSUserDefaults

object Prefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    fun getBoolean(key: String, fallback: Boolean): Boolean = if (defaults.objectForKey(key) == null) fallback else defaults.boolForKey(key)

    fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)

    fun getFloat(key: String, fallback: Float): Float = if (defaults.objectForKey(key) == null) fallback else defaults.floatForKey(key)

    fun putFloat(key: String, value: Float) = defaults.setFloat(value, key)

    fun get(key: String, fallback: String): String = defaults.stringForKey(key) ?: fallback

    fun put(key: String, value: String) = defaults.setObject(value, key)
}
