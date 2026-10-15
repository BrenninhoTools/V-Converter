package com.brenninhotools.vconverter.script

object LuaPattern {
    private val classes = mapOf(
        'a' to "A-Za-z", 'd' to "0-9", 'l' to "a-z", 'u' to "A-Z", 's' to "\\s", 'w' to "A-Za-z0-9",
        'x' to "A-Fa-f0-9", 'p' to "!-/:-@\\[-`{-~", 'c' to "\\x00-\\x1F"
    )

    fun toRegex(pattern: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '^' && i == 0 -> out.append('^')
                c == '$' && i == pattern.lastIndex -> out.append('$')
                c == '%' -> {
                    val n = pattern.getOrNull(i + 1) ?: return null
                    if (n == 'b' || n == 'f') return null
                    out.append(classOf(n, false) ?: return null)
                    i++
                }
                c == '[' -> {
                    val end = setEnd(pattern, i) ?: return null
                    out.append(convertSet(pattern.substring(i + 1, end)) ?: return null)
                    i = end
                }
                c == '.' -> out.append("[\\s\\S]")
                c == '-' && out.isNotEmpty() -> out.append("*?")
                c == '(' && pattern.getOrNull(i + 1) == ')' -> return null
                c == '(' || c == ')' -> out.append(c)
                c == '*' || c == '+' || c == '?' -> out.append(c)
                c in "\\|{}/^$" -> out.append('\\').append(c)
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    fun replacement(repl: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < repl.length) {
            val c = repl[i]
            if (c == '%' && i + 1 < repl.length) {
                val n = repl[i + 1]
                when {
                    n.isDigit() -> out.append('$').append(if (n == '0') '0' else n)
                    n == '%' -> out.append('%')
                    else -> out.append(n)
                }
                i += 2
                continue
            }
            if (c == '$') out.append("$$") else out.append(c)
            i++
        }
        return out.toString()
    }

    private fun classOf(n: Char, inSet: Boolean): String? {
        val lower = n.lowercaseChar()
        val range = classes[lower]
        if (range != null) {
            if (n.isUpperCase()) return if (inSet) null else "[^$range]"
            return if (inSet) range else "[$range]"
        }
        if (n.isLetterOrDigit()) return null
        return "\\" + n
    }

    private fun setEnd(pattern: String, start: Int): Int? {
        var i = start + 1
        if (pattern.getOrNull(i) == '^') i++
        if (pattern.getOrNull(i) == ']') i++
        while (i < pattern.length) {
            when (pattern[i]) {
                '%' -> i += 2
                ']' -> return i
                else -> i++
            }
        }
        return null
    }

    private fun convertSet(body: String): String? {
        val out = StringBuilder("[")
        var i = 0
        if (body.startsWith("^")) {
            out.append('^')
            i++
        }
        while (i < body.length) {
            val c = body[i]
            when {
                c == '%' -> {
                    val n = body.getOrNull(i + 1) ?: return null
                    out.append(classOf(n, true) ?: return null)
                    i += 2
                }
                c == '\\' || c == '[' || c == ']' -> {
                    out.append('\\').append(c)
                    i++
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.append(']').toString()
    }
}
