package com.brenninhotools.vconverter.script.hx

enum class HxKind { SPACE, IDENT, NUMBER, STRING, PUNCT }

data class HxTok(val kind: HxKind, val text: String, val line: Int) {
    fun isPunct(value: String) = kind == HxKind.PUNCT && text == value
    fun isIdent(value: String) = kind == HxKind.IDENT && text == value
}

class HxSegment(
    val kind: String,
    val name: String,
    val head: List<HxTok>,
    val body: List<HxTok>,
    val braced: Boolean,
    val line: Int,
    val modifiers: Set<String> = emptySet()
) {
    val isPublic: Boolean get() = "public" in modifiers
    val isStatic: Boolean get() = "static" in modifiers
}

object HxSource {
    private val modifierWords = setOf("public", "private", "static", "override", "inline", "dynamic")
    private val operators = listOf(">>>=", "<<=", ">>=", "...", "&&", "||", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "++", "--", "=>", "->", "??", "?.")

    fun tokenize(source: String, joinOperators: Boolean = false): List<HxTok> {
        val out = mutableListOf<HxTok>()
        var i = 0
        var line = 1
        while (i < source.length) {
            val c = source[i]
            val start = i
            when {
                c == '\n' -> {
                    line++
                    i++
                    out += HxTok(HxKind.SPACE, "\n", line)
                }
                c.isWhitespace() -> {
                    while (i < source.length && source[i].isWhitespace() && source[i] != '\n') i++
                    out += HxTok(HxKind.SPACE, source.substring(start, i).replace("\t", "  ").replace("\r", ""), line)
                }
                c == '/' && source.getOrNull(i + 1) == '/' -> while (i < source.length && source[i] != '\n') i++
                c == '/' && source.getOrNull(i + 1) == '*' -> {
                    val end = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                    line += source.substring(i, end).count { it == '\n' }
                    i = end
                    out += HxTok(HxKind.SPACE, " ", line)
                }
                c == '"' || c == '\'' -> {
                    i++
                    while (i < source.length && source[i] != c) {
                        if (source[i] == '\\') i++
                        if (i < source.length && source[i] == '\n') line++
                        i++
                    }
                    i++
                    out += HxTok(HxKind.STRING, source.substring(start, minOf(i, source.length)), line)
                }
                c.isLetter() || c == '_' -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    out += HxTok(HxKind.IDENT, source.substring(start, i), line)
                }
                c.isDigit() -> {
                    if (c == '0' && (source.getOrNull(i + 1) == 'x' || source.getOrNull(i + 1) == 'X')) {
                        i += 2
                        while (i < source.length && (source[i].isLetterOrDigit())) i++
                    } else {
                        while (i < source.length && (source[i].isDigit() || source[i] == '.' && source.getOrNull(i + 1)?.isDigit() == true || source[i] == 'e' || source[i] == 'E' ||
                                ((source[i] == '-' || source[i] == '+') && (source[i - 1] == 'e' || source[i - 1] == 'E')))) i++
                    }
                    out += HxTok(HxKind.NUMBER, source.substring(start, i), line)
                }
                else -> {
                    val op = if (joinOperators) operators.firstOrNull { source.startsWith(it, i) } else null
                    if (op != null) {
                        i += op.length
                        out += HxTok(HxKind.PUNCT, op, line)
                    } else {
                        i++
                        out += HxTok(HxKind.PUNCT, c.toString(), line)
                    }
                }
            }
        }
        return out
    }

    fun text(tokens: List<HxTok>): String = tokens.joinToString("") { it.text }

    fun skipSpace(tokens: List<HxTok>, from: Int): Int {
        var j = from
        while (j < tokens.size && tokens[j].kind == HxKind.SPACE) j++
        return j
    }

    fun prevIndex(tokens: List<HxTok>, index: Int): Int {
        var j = index - 1
        while (j >= 0 && tokens[j].kind == HxKind.SPACE) j--
        return j
    }

    fun matching(tokens: List<HxTok>, open: Int): Int {
        val o = tokens[open].text
        val c = when (o) {
            "(" -> ")"
            "[" -> "]"
            "{" -> "}"
            else -> return open
        }
        var depth = 0
        var j = open
        while (j < tokens.size) {
            val t = tokens[j]
            if (t.kind == HxKind.PUNCT) {
                if (t.text == o) depth++
                if (t.text == c) {
                    depth--
                    if (depth == 0) return j
                }
            }
            j++
        }
        return tokens.size - 1
    }

    fun statementEnd(tokens: List<HxTok>, start: Int): Int {
        var depth = 0
        var j = start
        while (j < tokens.size) {
            val t = tokens[j]
            if (t.kind == HxKind.PUNCT) {
                when (t.text) {
                    "{", "(", "[" -> depth++
                    "}", ")", "]" -> {
                        depth--
                        if (depth == 0 && t.text == "}") {
                            val k = skipSpace(tokens, j + 1)
                            val next = tokens.getOrNull(k)
                            if (next == null || !(next.kind == HxKind.IDENT && (next.text == "else" || next.text == "catch")) &&
                                !(next.kind == HxKind.PUNCT && (next.text == ";" || next.text == ")" || next.text == "," || next.text == "."))) return j + 1
                        }
                        if (depth < 0) return j
                    }
                    ";" -> if (depth == 0) return j + 1
                }
            }
            j++
        }
        return tokens.size
    }

    fun segments(tokens: List<HxTok>): List<HxSegment> {
        val result = mutableListOf<HxSegment>()
        var i = 0
        while (true) {
            i = skipSpace(tokens, i)
            if (i >= tokens.size) break
            var t = tokens[i]
            val line = t.line
            var j = i
            val mods = linkedSetOf<String>()
            while (tokens.getOrNull(j)?.let { it.kind == HxKind.IDENT && it.text in modifierWords } == true) {
                mods += tokens[j].text
                j = skipSpace(tokens, j + 1)
            }
            if (tokens.getOrNull(j)?.isPunct("@") == true) {
                var k = j + 1
                if (tokens.getOrNull(k)?.isPunct(":") == true) k++
                if (tokens.getOrNull(k)?.kind == HxKind.IDENT) k++
                if (tokens.getOrNull(k)?.isPunct("(") == true) k = matching(tokens, k) + 1
                i = k
                continue
            }
            if (j != i) t = tokens.getOrNull(j) ?: break
            if (t.kind == HxKind.IDENT && (t.text == "class" || t.text == "interface" || t.text == "enum" || t.text == "abstract")) {
                var k = j
                while (k < tokens.size && !tokens[k].isPunct("{")) k++
                val end = if (k < tokens.size) matching(tokens, k) else tokens.size - 1
                val name = tokens.getOrNull(skipSpace(tokens, j + 1))?.text ?: ""
                result += HxSegment(t.text, name, emptyList(), tokens.subList(j, minOf(end + 1, tokens.size)), true, line, mods)
                i = minOf(end + 1, tokens.size)
                continue
            }
            if (t.kind == HxKind.IDENT && t.text == "typedef") {
                val end = statementEnd(tokens, j)
                result += HxSegment("typedef", "", emptyList(), tokens.subList(j, end), false, line, mods)
                i = end
                continue
            }
            if (t.kind == HxKind.IDENT && (t.text == "import" || t.text == "using" || t.text == "package")) {
                val end = statementEnd(tokens, j)
                result += HxSegment(t.text, "", tokens.subList(j, end), emptyList(), false, line, mods)
                i = end
                continue
            }
            if (t.kind == HxKind.IDENT && t.text == "function") {
                val n = skipSpace(tokens, j + 1)
                val nameTok = tokens.getOrNull(n)
                if (nameTok != null && nameTok.kind == HxKind.IDENT) {
                    var k = n + 1
                    while (k < tokens.size && !tokens[k].isPunct("(")) k++
                    k = if (k < tokens.size) matching(tokens, k) else tokens.size - 1
                    var bodyStart = skipSpace(tokens, k + 1)
                    if (tokens.getOrNull(bodyStart)?.isPunct(":") == true) {
                        bodyStart = skipSpace(tokens, bodyStart + 1)
                        var depth = 0
                        while (bodyStart < tokens.size) {
                            val tk = tokens[bodyStart]
                            if (tk.isPunct("<")) depth++
                            if (tk.isPunct(">")) depth--
                            if (depth <= 0 && tk.isPunct("{")) break
                            if (depth <= 0 && tk.kind == HxKind.SPACE && tk.text == "\n") break
                            bodyStart++
                        }
                        bodyStart = skipSpace(tokens, bodyStart)
                    }
                    val head = tokens.subList(n + 1, minOf(k + 1, tokens.size))
                    val braced = tokens.getOrNull(bodyStart)?.isPunct("{") == true
                    if (braced) {
                        val end = matching(tokens, bodyStart)
                        result += HxSegment("function", nameTok.text, head, tokens.subList(minOf(bodyStart + 1, tokens.size), minOf(end, tokens.size)), true, line, mods)
                        i = minOf(end + 1, tokens.size)
                    } else {
                        val end = statementEnd(tokens, bodyStart)
                        result += HxSegment("function", nameTok.text, head, tokens.subList(bodyStart, end), false, line, mods)
                        i = end
                    }
                    continue
                }
            }
            if (t.kind == HxKind.IDENT && (t.text == "var" || t.text == "final")) {
                val end = statementEnd(tokens, j)
                val n = skipSpace(tokens, j + 1)
                val name = tokens.getOrNull(n)?.takeIf { it.kind == HxKind.IDENT }?.text ?: ""
                var depth = 0
                var eq: Int? = null
                for (k in n until end) {
                    val tk = tokens[k]
                    if (tk.isPunct("<") || tk.isPunct("(") || tk.isPunct("[") || tk.isPunct("{")) depth++
                    if (tk.isPunct(">") || tk.isPunct(")") || tk.isPunct("]") || tk.isPunct("}")) depth--
                    if (depth == 0 && tk.isPunct("=") && tokens.getOrNull(k + 1)?.isPunct("=") != true && tokens.getOrNull(k - 1)?.let { p -> p.isPunct("=") || p.isPunct("!") || p.isPunct("<") || p.isPunct(">") } != true) {
                        eq = k
                        break
                    }
                }
                val typeTokens = tokens.subList(n + 1, eq ?: end).dropLastWhile { it.kind == HxKind.SPACE || it.isPunct(";") }
                val initializer = if (eq != null) tokens.subList(eq + 1, end).dropLastWhile { it.kind == HxKind.SPACE || it.isPunct(";") } else emptyList()
                val multiple = depthComma(tokens, n, end)
                result += HxSegment(if (multiple) "vars" else "var", name, typeTokens, if (multiple) tokens.subList(j, end) else initializer, false, line, mods)
                i = end
                continue
            }
            val end = statementEnd(tokens, i)
            result += HxSegment("statement", "", emptyList(), tokens.subList(i, end), false, line, mods)
            i = maxOf(end, i + 1)
        }
        return result
    }

    private fun depthComma(tokens: List<HxTok>, from: Int, to: Int): Boolean {
        var depth = 0
        for (k in from until to) {
            val tk = tokens[k]
            if (tk.kind != HxKind.PUNCT) continue
            when (tk.text) {
                "(", "[", "{", "<" -> depth++
                ")", "]", "}", ">" -> depth--
                "," -> if (depth == 0) return true
            }
        }
        return false
    }

    fun paramNames(tokens: List<HxTok>, open: Int): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var expectName = false
        var j = open
        while (j < tokens.size) {
            val t = tokens[j]
            if (t.kind == HxKind.PUNCT) {
                when (t.text) {
                    "(" -> {
                        depth++
                        if (depth == 1) expectName = true
                    }
                    ")" -> {
                        depth--
                        if (depth == 0) return result
                    }
                    "," -> if (depth == 1) expectName = true
                    "?" -> Unit
                    else -> if (depth == 1) expectName = false
                }
            } else if (t.kind == HxKind.IDENT && depth == 1 && expectName) {
                result += t.text
                expectName = false
            }
            j++
        }
        return result
    }

    fun declaredLocals(tokens: List<HxTok>): Set<String> {
        val result = HashSet<String>()
        tokens.forEachIndexed { index, tok ->
            if (tok.kind != HxKind.IDENT) return@forEachIndexed
            when (tok.text) {
                "var", "final" -> {
                    var k = skipSpace(tokens, index + 1)
                    while (true) {
                        val nameTok = tokens.getOrNull(k)?.takeIf { it.kind == HxKind.IDENT } ?: break
                        result += nameTok.text
                        var depth = 0
                        var m = k + 1
                        var more = false
                        while (m < tokens.size) {
                            val t = tokens[m]
                            if (t.kind == HxKind.PUNCT) {
                                if (t.text in setOf("(", "[", "{", "<")) depth++
                                if (t.text in setOf(")", "]", "}", ">")) {
                                    depth--
                                    if (depth < 0) break
                                }
                                if (depth == 0 && t.text == ";") break
                                if (depth == 0 && t.text == ",") {
                                    more = true
                                    break
                                }
                            }
                            if (depth == 0 && t.kind == HxKind.SPACE && t.text == "\n") break
                            m++
                        }
                        if (!more) break
                        k = skipSpace(tokens, m + 1)
                    }
                }
                "for", "catch" -> {
                    val open = skipSpace(tokens, index + 1)
                    if (tokens.getOrNull(open)?.text == "(") {
                        val first = skipSpace(tokens, open + 1)
                        tokens.getOrNull(first)?.takeIf { it.kind == HxKind.IDENT }?.let { result += it.text }
                        val arrow = skipSpace(tokens, first + 1)
                        if (tokens.getOrNull(arrow)?.isPunct("=") == true && tokens.getOrNull(arrow + 1)?.isPunct(">") == true) {
                            tokens.getOrNull(skipSpace(tokens, arrow + 2))?.takeIf { it.kind == HxKind.IDENT }?.let { result += it.text }
                        }
                    }
                }
                "function" -> {
                    var open = skipSpace(tokens, index + 1)
                    if (tokens.getOrNull(open)?.kind == HxKind.IDENT) open = skipSpace(tokens, open + 1)
                    if (tokens.getOrNull(open)?.text == "(") result += paramNames(tokens, open)
                }
            }
        }
        return result
    }

    fun arrowParams(tokens: List<HxTok>): Set<String> {
        val result = HashSet<String>()
        for (i in tokens.indices) {
            if (!(tokens[i].isPunct("-") && tokens.getOrNull(i + 1)?.isPunct(">") == true)) continue
            val p = prevIndex(tokens, i)
            val t = tokens.getOrNull(p) ?: continue
            if (t.kind == HxKind.IDENT) result += t.text
            if (t.isPunct(")")) {
                var depth = 0
                var k = p
                while (k >= 0) {
                    if (tokens[k].isPunct(")")) depth++
                    if (tokens[k].isPunct("(")) {
                        depth--
                        if (depth == 0) break
                    }
                    k--
                }
                if (k >= 0) result += paramNames(tokens, k)
            }
        }
        return result
    }

    fun stripGenerics(text: String): String {
        val out = StringBuilder()
        var i = 0
        val pattern = Regex("\\bnew\\s+[A-Za-z_][\\w.]*\\s*<")
        while (i < text.length) {
            val m = pattern.find(text, i) ?: break
            out.append(text, i, m.range.last)
            var depth = 0
            var j = m.range.last
            while (j < text.length) {
                if (text[j] == '<') depth++
                if (text[j] == '>') {
                    depth--
                    if (depth == 0) break
                }
                j++
            }
            i = j + 1
        }
        out.append(text.substring(minOf(i, text.length)))
        return out.toString()
    }

    fun indentBody(text: String, indent: String = "  "): List<String> {
        val lines = text.trimEnd().lines().dropWhile { it.isBlank() }
        val nonBlank = lines.filter { it.isNotBlank() }
        val min = nonBlank.minOfOrNull { line -> line.length - line.trimStart().length } ?: 0
        return lines.map { if (it.isBlank()) "" else indent + it.drop(min).trimEnd() }
    }

    fun hxString(value: String): String {
        val sb = StringBuilder("\"")
        value.forEach { c ->
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format("\\x%02X", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
