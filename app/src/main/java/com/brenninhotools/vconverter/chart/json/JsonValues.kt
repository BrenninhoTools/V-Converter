package com.brenninhotools.vconverter.chart.json

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToLong

internal fun JSONObject.value(key: String): Any? {
    if (!has(key)) return null
    val raw = opt(key)
    return if (raw == null || raw === JSONObject.NULL) null else raw
}

internal fun JSONArray.value(index: Int): Any? {
    if (index < 0 || index >= length()) return null
    val raw = opt(index)
    return if (raw == null || raw === JSONObject.NULL) null else raw
}

internal fun Any?.asDouble(fallback: Double): Double = when (this) {
    is Number -> toDouble().takeUnless { it.isNaN() || it.isInfinite() } ?: fallback
    is String -> trim().toDoubleOrNull()?.takeUnless { it.isNaN() || it.isInfinite() } ?: fallback
    else -> fallback
}

internal fun Any?.asBool(fallback: Boolean): Boolean = when (this) {
    is Boolean -> this
    is Number -> toDouble() != 0.0
    is String -> when (trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> fallback
    }
    else -> fallback
}

internal fun Any?.asText(): String = when (this) {
    null -> ""
    is Double -> if (this % 1.0 == 0.0 && abs(this) < 1e15) toLong().toString() else toString()
    is Float -> toDouble().asText()
    else -> toString()
}

internal fun Double.toJsonNumber(): Number {
    val rounded = (this * 1000.0).roundToLong() / 1000.0
    return if (rounded % 1.0 == 0.0 && abs(rounded) < 1e15) rounded.toLong() else rounded
}
