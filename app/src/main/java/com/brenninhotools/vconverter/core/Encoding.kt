package com.brenninhotools.vconverter.core

object UrlEncoding {
    fun encode(value: String): String {
        val sb = StringBuilder()
        value.encodeToByteArray().forEach { byte ->
            val c = (byte.toInt() and 0xFF).toChar()
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '-' || c == '*' || c == '_' -> sb.append(c)
                c == ' ' -> sb.append('+')
                else -> {
                    val hex = (byte.toInt() and 0xFF).toString(16).uppercase()
                    sb.append('%').append(hex.padStart(2, '0'))
                }
            }
        }
        return sb.toString()
    }
}
