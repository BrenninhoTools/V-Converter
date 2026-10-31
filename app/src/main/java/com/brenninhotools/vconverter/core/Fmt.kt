package com.brenninhotools.vconverter.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

fun fmt(pattern: String, vararg args: Any?): String {
    val sb = StringBuilder()
    var i = 0
    var next = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c != '%') {
            sb.append(c)
            i++
            continue
        }
        if (i + 1 < pattern.length && pattern[i + 1] == '%') {
            sb.append('%')
            i += 2
            continue
        }
        var j = i + 1
        var leftAlign = false
        var zeroPad = false
        var plus = false
        while (j < pattern.length && pattern[j] in "-0+ #,") {
            when (pattern[j]) {
                '-' -> leftAlign = true
                '0' -> zeroPad = true
                '+' -> plus = true
            }
            j++
        }
        var width = 0
        while (j < pattern.length && pattern[j].isDigit()) {
            width = width * 10 + (pattern[j] - '0')
            j++
        }
        var precision = -1
        if (j < pattern.length && pattern[j] == '.') {
            j++
            precision = 0
            while (j < pattern.length && pattern[j].isDigit()) {
                precision = precision * 10 + (pattern[j] - '0')
                j++
            }
        }
        if (j >= pattern.length) {
            sb.append(pattern.substring(i))
            break
        }
        val conversion = pattern[j]
        val arg = args.getOrNull(next++)
        var text = when (conversion) {
            'd' -> (arg as? Number)?.toLong()?.toString() ?: arg.toString()
            'f' -> Fmt.fixed((arg as? Number)?.toDouble() ?: 0.0, if (precision < 0) 6 else precision)
            'x' -> Fmt.hex(arg, false)
            'X' -> Fmt.hex(arg, true)
            's', 'S' -> (arg?.toString() ?: "null").let { if (precision >= 0) it.take(precision) else it }.let { if (conversion == 'S') it.uppercase() else it }
            'c' -> arg?.toString() ?: "null"
            'b' -> (arg != null && arg != false).toString()
            else -> arg.toString()
        }
        if (plus && conversion in "df" && !text.startsWith("-")) text = "+$text"
        if (text.length < width) {
            text = when {
                leftAlign -> text.padEnd(width)
                zeroPad && conversion in "dfxX" -> {
                    val sign = if (text.startsWith("-") || text.startsWith("+")) text.substring(0, 1) else ""
                    sign + text.substring(sign.length).padStart(width - sign.length, '0')
                }
                else -> text.padStart(width)
            }
        }
        sb.append(text)
        i = j + 1
    }
    return sb.toString()
}

object Fmt {
    fun fixed(value: Double, digits: Int): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        val scale = 10.0.pow(digits)
        val scaled = abs(value) * scale
        if (scaled >= 9.0e18) return value.toString()
        val rounded = scaled.roundToLong()
        val whole = rounded / scale.toLong()
        val fraction = rounded % scale.toLong()
        val negative = value < 0 && rounded != 0L
        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append(whole)
        if (digits > 0) sb.append('.').append(fraction.toString().padStart(digits, '0'))
        return sb.toString()
    }

    fun hex(arg: Any?, upper: Boolean): String {
        val text = when (arg) {
            is Byte -> (arg.toInt() and 0xFF).toString(16)
            is Short -> (arg.toInt() and 0xFFFF).toString(16)
            is Int -> arg.toUInt().toString(16)
            is Long -> arg.toULong().toString(16)
            is Char -> arg.code.toString(16)
            is Number -> arg.toLong().toULong().toString(16)
            else -> arg.toString()
        }
        return if (upper) text.uppercase() else text
    }
}
