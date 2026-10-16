package com.brenninhotools.vconverter.codename

class XmlException(message: String) : Exception(message)

class XmlNode(val name: String, val attributes: Map<String, String>, val children: List<XmlNode>, val text: String) {
    fun attr(key: String): String? = attributes[key] ?: attributes.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    fun has(key: String): Boolean = attr(key) != null

    fun double(key: String): Double? = attr(key)?.trim()?.toDoubleOrNull()?.takeUnless { it.isNaN() || it.isInfinite() }

    fun int(key: String): Int? = attr(key)?.trim()?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

    fun bool(key: String): Boolean? = attr(key)?.trim()?.lowercase()?.let { it == "true" }

    fun elements(vararg names: String): List<XmlNode> = children.filter { child -> names.any { it.equals(child.name, ignoreCase = true) } }

    companion object {
        fun parse(raw: String): XmlNode {
            val parser = Parser(raw.removePrefix("﻿"))
            return parser.document()
        }

        fun numberRange(input: String): List<Int> {
            val result = mutableListOf<Int>()
            input.split(',').forEach { raw ->
                val part = raw.trim()
                val dots = part.indexOf("..")
                if (dots >= 0) {
                    val start = part.substring(0, dots).trim().toIntOrNull() ?: return@forEach
                    val end = part.substring(dots + 2).trim().toIntOrNull() ?: return@forEach
                    if (start <= end) (start..end).forEach { result += it } else (start downTo end).forEach { result += it }
                } else {
                    part.toIntOrNull()?.let { result += it }
                }
            }
            return result
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun document(): XmlNode {
            var root: XmlNode? = null
            while (i < s.length) {
                skipMisc()
                if (i >= s.length) break
                if (s.startsWith("<", i)) {
                    val node = element()
                    if (root == null) root = node
                } else {
                    i++
                }
            }
            return root ?: throw XmlException("The file has no XML element.")
        }

        private fun skipMisc() {
            while (i < s.length) {
                when {
                    s[i].isWhitespace() -> i++
                    s.startsWith("<?", i) -> i = end("?>", i + 2)
                    s.startsWith("<!--", i) -> i = end("-->", i + 4)
                    s.startsWith("<!", i) && !s.startsWith("<![CDATA[", i) -> i = declarationEnd(i)
                    else -> return
                }
            }
        }

        private fun declarationEnd(start: Int): Int {
            var depth = 0
            var j = start
            while (j < s.length) {
                when (s[j]) {
                    '[' -> depth++
                    ']' -> depth--
                    '>' -> if (depth <= 0) return j + 1
                }
                j++
            }
            return s.length
        }

        private fun end(token: String, from: Int): Int {
            val at = s.indexOf(token, from)
            return if (at < 0) s.length else at + token.length
        }

        private fun element(): XmlNode {
            if (s[i] != '<') throw XmlException("Expected '<' at ${position()}.")
            i++
            val name = readName()
            if (name.isEmpty()) throw XmlException("Missing tag name at ${position()}.")
            val attributes = LinkedHashMap<String, String>()
            while (true) {
                skipSpace()
                if (i >= s.length) throw XmlException("Unclosed <$name>.")
                if (s.startsWith("/>", i)) {
                    i += 2
                    return XmlNode(name, attributes, emptyList(), "")
                }
                if (s[i] == '>') {
                    i++
                    break
                }
                val key = readName()
                if (key.isEmpty()) {
                    i++
                    continue
                }
                skipSpace()
                if (i < s.length && s[i] == '=') {
                    i++
                    skipSpace()
                    val quote = s.getOrNull(i)
                    val value = if (quote == '"' || quote == '\'') {
                        val close = s.indexOf(quote, i + 1).let { if (it < 0) s.length else it }
                        val v = s.substring(i + 1, close)
                        i = minOf(close + 1, s.length)
                        v
                    } else {
                        val start = i
                        while (i < s.length && !s[i].isWhitespace() && s[i] != '>' && !s.startsWith("/>", i)) i++
                        s.substring(start, i)
                    }
                    attributes[key] = unescape(value)
                } else {
                    attributes[key] = ""
                }
            }
            val children = mutableListOf<XmlNode>()
            val text = StringBuilder()
            while (i < s.length) {
                when {
                    s.startsWith("</", i) -> {
                        i = end(">", i + 2)
                        return XmlNode(name, attributes, children, unescape(text.toString()).trim())
                    }
                    s.startsWith("<!--", i) -> i = end("-->", i + 4)
                    s.startsWith("<![CDATA[", i) -> {
                        val close = s.indexOf("]]>", i + 9).let { if (it < 0) s.length else it }
                        text.append(s, i + 9, close)
                        i = minOf(close + 3, s.length)
                    }
                    s.startsWith("<?", i) -> i = end("?>", i + 2)
                    s.startsWith("<!", i) -> i = declarationEnd(i)
                    s[i] == '<' -> children += element()
                    else -> {
                        text.append(s[i])
                        i++
                    }
                }
            }
            return XmlNode(name, attributes, children, unescape(text.toString()).trim())
        }

        private fun readName(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "_-:.")) i++
            return s.substring(start, i)
        }

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun position(): String {
            val line = s.substring(0, minOf(i, s.length)).count { it == '\n' } + 1
            return "line $line"
        }

        private fun unescape(text: String): String = text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value }
            .replace("&amp;", "&")
    }
}
