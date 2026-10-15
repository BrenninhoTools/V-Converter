package com.brenninhotools.vconverter.script.lua

enum class TokenType { NAME, NUMBER, STRING, KEYWORD, SYMBOL, EOF }

data class Token(val type: TokenType, val text: String, val line: Int)

class LuaSyntaxException(message: String, val line: Int) : Exception("Line $line: $message")

class LuaLexer(private val source: String) {
    private var pos = 0
    private var line = 1

    companion object {
        val keywords = setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in",
            "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"
        )
        private val symbols = listOf(
            "...", "..", "==", "~=", "<=", ">=", "//", "::", "<<", ">>",
            "+", "-", "*", "/", "%", "^", "#", "&", "~", "|", "<", ">", "=", "(", ")", "{", "}", "[", "]", ";", ":", ",", "."
        )
    }

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        if (source.startsWith("#")) skipLine()
        while (true) {
            skipSpaceAndComments()
            if (pos >= source.length) {
                tokens += Token(TokenType.EOF, "", line)
                return tokens
            }
            val c = source[pos]
            val startLine = line
            when {
                c.isLetter() || c == '_' -> {
                    val start = pos
                    while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) pos++
                    val word = source.substring(start, pos)
                    tokens += Token(if (word in keywords) TokenType.KEYWORD else TokenType.NAME, word, startLine)
                }
                c.isDigit() || (c == '.' && peekChar(1)?.isDigit() == true) -> tokens += Token(TokenType.NUMBER, readNumber(), startLine)
                c == '"' || c == '\'' -> tokens += Token(TokenType.STRING, readQuoted(c), startLine)
                c == '[' && longBracketLevel() >= 0 -> tokens += Token(TokenType.STRING, readLongBracket(), startLine)
                else -> {
                    val symbol = symbols.firstOrNull { source.startsWith(it, pos) }
                        ?: throw LuaSyntaxException("Unexpected character '$c'", line)
                    pos += symbol.length
                    tokens += Token(TokenType.SYMBOL, symbol, startLine)
                }
            }
        }
    }

    private fun peekChar(offset: Int): Char? = source.getOrNull(pos + offset)

    private fun skipLine() {
        while (pos < source.length && source[pos] != '\n') pos++
    }

    private fun skipSpaceAndComments() {
        while (pos < source.length) {
            val c = source[pos]
            when {
                c == '\n' -> {
                    line++
                    pos++
                }
                c.isWhitespace() -> pos++
                c == '-' && peekChar(1) == '-' -> {
                    pos += 2
                    if (source.getOrNull(pos) == '[' && longBracketLevel() >= 0) readLongBracket() else skipLine()
                }
                else -> return
            }
        }
    }

    private fun readNumber(): String {
        val start = pos
        if (source[pos] == '0' && (peekChar(1) == 'x' || peekChar(1) == 'X')) {
            pos += 2
            while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '.')) pos++
            return source.substring(start, pos)
        }
        while (pos < source.length) {
            val c = source[pos]
            when {
                c.isDigit() || c == '.' -> pos++
                (c == 'e' || c == 'E') -> {
                    pos++
                    if (source.getOrNull(pos) == '+' || source.getOrNull(pos) == '-') pos++
                }
                else -> break
            }
        }
        return source.substring(start, pos)
    }

    private fun readQuoted(quote: Char): String {
        pos++
        val sb = StringBuilder()
        while (true) {
            if (pos >= source.length) throw LuaSyntaxException("Unfinished string", line)
            val c = source[pos]
            when {
                c == quote -> {
                    pos++
                    return sb.toString()
                }
                c == '\n' -> throw LuaSyntaxException("Unfinished string", line)
                c == '\\' -> {
                    pos++
                    val e = source.getOrNull(pos) ?: throw LuaSyntaxException("Unfinished string", line)
                    when (e) {
                        'n' -> { sb.append('\n'); pos++ }
                        't' -> { sb.append('\t'); pos++ }
                        'r' -> { sb.append('\r'); pos++ }
                        'a' -> { sb.append('\u0007'); pos++ }
                        'b' -> { sb.append('\b'); pos++ }
                        'f' -> { sb.append('\u000C'); pos++ }
                        'v' -> { sb.append('\u000B'); pos++ }
                        '\\', '"', '\'' -> { sb.append(e); pos++ }
                        '\n' -> { sb.append('\n'); line++; pos++ }
                        'x' -> {
                            val hex = source.substring(pos + 1, minOf(pos + 3, source.length))
                            sb.append(hex.toInt(16).toChar())
                            pos += 3
                        }
                        'z' -> {
                            pos++
                            while (pos < source.length && source[pos].isWhitespace()) {
                                if (source[pos] == '\n') line++
                                pos++
                            }
                        }
                        else -> if (e.isDigit()) {
                            val start = pos
                            while (pos < source.length && pos - start < 3 && source[pos].isDigit()) pos++
                            sb.append(source.substring(start, pos).toInt().toChar())
                        } else {
                            sb.append(e)
                            pos++
                        }
                    }
                }
                else -> {
                    sb.append(c)
                    pos++
                }
            }
        }
    }

    private fun longBracketLevel(): Int {
        if (source.getOrNull(pos) != '[') return -1
        var i = pos + 1
        var level = 0
        while (source.getOrNull(i) == '=') {
            level++
            i++
        }
        return if (source.getOrNull(i) == '[') level else -1
    }

    private fun readLongBracket(): String {
        val level = longBracketLevel()
        pos += level + 2
        if (source.getOrNull(pos) == '\r') pos++
        if (source.getOrNull(pos) == '\n') {
            line++
            pos++
        }
        val close = "]" + "=".repeat(level) + "]"
        val end = source.indexOf(close, pos)
        if (end < 0) throw LuaSyntaxException("Unfinished long string or comment", line)
        val text = source.substring(pos, end)
        line += text.count { it == '\n' }
        pos = end + close.length
        return text
    }
}
