package com.brenninhotools.vconverter.script.lua

class LuaParser(source: String) {
    private val tokens = LuaLexer(source).tokenize()
    private var index = 0

    companion object {
        private val binaryPriority = mapOf(
            "or" to (1 to 1), "and" to (2 to 2),
            "<" to (3 to 3), ">" to (3 to 3), "<=" to (3 to 3), ">=" to (3 to 3), "~=" to (3 to 3), "==" to (3 to 3),
            "|" to (4 to 4), "~" to (5 to 5), "&" to (6 to 6), "<<" to (7 to 7), ">>" to (7 to 7),
            ".." to (9 to 8),
            "+" to (10 to 10), "-" to (10 to 10),
            "*" to (11 to 11), "/" to (11 to 11), "//" to (11 to 11), "%" to (11 to 11),
            "^" to (14 to 13)
        )
        private const val UNARY_PRIORITY = 12

        fun parse(source: String): List<Stat> = LuaParser(source).parseChunk()
    }

    fun parseChunk(): List<Stat> {
        val body = block()
        if (peek().type != TokenType.EOF) fail("'<eof>' expected near '${peek().text}'")
        return body
    }

    private fun peek(offset: Int = 0): Token = tokens[minOf(index + offset, tokens.lastIndex)]

    private fun next(): Token = tokens[index].also { if (index < tokens.lastIndex) index++ }

    private fun check(text: String): Boolean {
        val t = peek()
        return (t.type == TokenType.SYMBOL || t.type == TokenType.KEYWORD) && t.text == text
    }

    private fun accept(text: String): Boolean {
        if (check(text)) {
            next()
            return true
        }
        return false
    }

    private fun expect(text: String, context: String = ""): Token {
        if (!check(text)) fail("'$text' expected${if (context.isNotEmpty()) " $context" else ""} near '${peek().text.ifEmpty { "<eof>" }}'")
        return next()
    }

    private fun name(): String {
        val t = peek()
        if (t.type != TokenType.NAME) fail("<name> expected near '${t.text.ifEmpty { "<eof>" }}'")
        return next().text
    }

    private fun fail(message: String): Nothing = throw LuaSyntaxException(message, peek().line)

    private fun blockEnds(): Boolean {
        val t = peek()
        return t.type == TokenType.EOF || (t.type == TokenType.KEYWORD && t.text in setOf("end", "else", "elseif", "until"))
    }

    private fun block(): List<Stat> {
        val stats = mutableListOf<Stat>()
        while (!blockEnds()) {
            if (check("return")) {
                stats += returnStat()
                break
            }
            statement()?.let { stats += it }
        }
        return stats
    }

    private fun returnStat(): Stat {
        val line = next().line
        val values = if (blockEnds() || check(";")) emptyList() else exprList()
        accept(";")
        return Stat.Return(values, line)
    }

    private fun statement(): Stat? {
        val t = peek()
        val line = t.line
        if (t.type == TokenType.SYMBOL) {
            when (t.text) {
                ";" -> {
                    next()
                    return null
                }
                "::" -> {
                    next()
                    val label = name()
                    expect("::")
                    return Stat.Label(label, line)
                }
            }
        }
        if (t.type == TokenType.KEYWORD) {
            when (t.text) {
                "if" -> return ifStat()
                "while" -> {
                    next()
                    val condition = expr()
                    expect("do")
                    val body = block()
                    expect("end", "to close 'while' at line $line")
                    return Stat.While(condition, body, line)
                }
                "do" -> {
                    next()
                    val body = block()
                    expect("end", "to close 'do' at line $line")
                    return Stat.Do(body, line)
                }
                "for" -> return forStat()
                "repeat" -> {
                    next()
                    val body = block()
                    expect("until", "to close 'repeat' at line $line")
                    return Stat.Repeat(body, expr(), line)
                }
                "function" -> {
                    next()
                    val path = mutableListOf(name())
                    while (accept(".")) path += name()
                    val method = if (accept(":")) name() else null
                    return Stat.FunctionDecl(path, method, funcBody(line, method != null), line)
                }
                "local" -> {
                    next()
                    if (accept("function")) {
                        val fn = name()
                        return Stat.LocalFunction(fn, funcBody(line, false), line)
                    }
                    val names = mutableListOf(name())
                    skipAttribute()
                    while (accept(",")) {
                        names += name()
                        skipAttribute()
                    }
                    val values = if (accept("=")) exprList() else emptyList()
                    return Stat.Local(names, values, line)
                }
                "return" -> return returnStat()
                "break" -> {
                    next()
                    return Stat.Break(line)
                }
                "goto" -> {
                    next()
                    return Stat.Goto(name(), line)
                }
            }
        }
        return exprStat()
    }

    private fun skipAttribute() {
        if (check("<")) {
            next()
            name()
            expect(">")
        }
    }

    private fun ifStat(): Stat {
        val line = next().line
        val branches = mutableListOf<Pair<Expr, List<Stat>>>()
        val condition = expr()
        expect("then")
        branches += condition to block()
        var otherwise: List<Stat>? = null
        while (true) {
            when {
                accept("elseif") -> {
                    val c = expr()
                    expect("then")
                    branches += c to block()
                }
                accept("else") -> {
                    otherwise = block()
                    expect("end", "to close 'if' at line $line")
                    break
                }
                else -> {
                    expect("end", "to close 'if' at line $line")
                    break
                }
            }
        }
        return Stat.If(branches, otherwise, line)
    }

    private fun forStat(): Stat {
        val line = next().line
        val first = name()
        if (accept("=")) {
            val start = expr()
            expect(",")
            val limit = expr()
            val step = if (accept(",")) expr() else null
            expect("do")
            val body = block()
            expect("end", "to close 'for' at line $line")
            return Stat.NumericFor(first, start, limit, step, body, line)
        }
        val names = mutableListOf(first)
        while (accept(",")) names += name()
        expect("in")
        val iterators = exprList()
        expect("do")
        val body = block()
        expect("end", "to close 'for' at line $line")
        return Stat.GenericFor(names, iterators, body, line)
    }

    private fun exprStat(): Stat {
        val line = peek().line
        val first = suffixedExpr()
        if (check("=") || check(",")) {
            val targets = mutableListOf(first)
            while (accept(",")) targets += suffixedExpr()
            expect("=")
            val values = exprList()
            targets.forEach { if (it !is Expr.Name && it !is Expr.Index && it !is Expr.Field) fail("cannot assign to this expression") }
            return Stat.Assign(targets, values, line)
        }
        if (first !is Expr.Call && first !is Expr.Method) fail("syntax error near '${peek().text}'")
        return Stat.CallStat(first, line)
    }

    private fun funcBody(line: Int, isMethod: Boolean): Expr.Func {
        expect("(")
        val params = mutableListOf<String>()
        if (isMethod) params += "self"
        var vararg = false
        if (!check(")")) {
            do {
                if (accept("...")) {
                    vararg = true
                    break
                }
                params += name()
            } while (accept(","))
        }
        expect(")")
        val body = block()
        expect("end", "to close 'function' at line $line")
        return Expr.Func(params, vararg, body, line)
    }

    private fun exprList(): List<Expr> {
        val list = mutableListOf(expr())
        while (accept(",")) list += expr()
        return list
    }

    fun expr(limit: Int = 0): Expr {
        val t = peek()
        var left: Expr = if ((t.type == TokenType.KEYWORD && t.text == "not") || (t.type == TokenType.SYMBOL && t.text in setOf("-", "#", "~"))) {
            next()
            Expr.Unary(t.text, expr(UNARY_PRIORITY), t.line)
        } else {
            simpleExpr()
        }
        while (true) {
            val op = peek()
            val priority = if (op.type == TokenType.SYMBOL || op.type == TokenType.KEYWORD) binaryPriority[op.text] else null
            if (priority == null || priority.first <= limit) break
            next()
            val right = expr(priority.second)
            left = Expr.Binary(op.text, left, right, op.line)
        }
        return left
    }

    private fun simpleExpr(): Expr {
        val t = peek()
        return when {
            t.type == TokenType.NUMBER -> Expr.Num(next().text, t.line)
            t.type == TokenType.STRING -> Expr.Str(next().text, t.line)
            t.type == TokenType.KEYWORD && t.text == "nil" -> { next(); Expr.Nil(t.line) }
            t.type == TokenType.KEYWORD && t.text == "true" -> { next(); Expr.Bool(true, t.line) }
            t.type == TokenType.KEYWORD && t.text == "false" -> { next(); Expr.Bool(false, t.line) }
            t.type == TokenType.SYMBOL && t.text == "..." -> { next(); Expr.Vararg(t.line) }
            t.type == TokenType.SYMBOL && t.text == "{" -> table()
            t.type == TokenType.KEYWORD && t.text == "function" -> {
                next()
                funcBody(t.line, false)
            }
            else -> suffixedExpr()
        }
    }

    private fun primaryExpr(): Expr {
        val t = peek()
        return when {
            t.type == TokenType.NAME -> Expr.Name(next().text, t.line)
            t.type == TokenType.SYMBOL && t.text == "(" -> {
                next()
                val inner = expr()
                expect(")")
                Expr.Paren(inner, t.line)
            }
            else -> fail("unexpected symbol near '${t.text.ifEmpty { "<eof>" }}'")
        }
    }

    private fun suffixedExpr(): Expr {
        var e = primaryExpr()
        while (true) {
            val t = peek()
            if (t.type != TokenType.SYMBOL && t.type != TokenType.STRING) return e
            e = when {
                t.type == TokenType.STRING -> Expr.Call(e, listOf(Expr.Str(next().text, t.line)), t.line)
                t.text == "." -> {
                    next()
                    Expr.Field(e, name(), t.line)
                }
                t.text == "[" -> {
                    next()
                    val key = expr()
                    expect("]")
                    Expr.Index(e, key, t.line)
                }
                t.text == ":" -> {
                    next()
                    val method = name()
                    Expr.Method(e, method, callArgs(), t.line)
                }
                t.text == "(" || t.text == "{" -> Expr.Call(e, callArgs(), t.line)
                else -> return e
            }
        }
    }

    private fun callArgs(): List<Expr> {
        val t = peek()
        return when {
            t.type == TokenType.STRING -> listOf(Expr.Str(next().text, t.line))
            t.type == TokenType.SYMBOL && t.text == "{" -> listOf(table())
            t.type == TokenType.SYMBOL && t.text == "(" -> {
                next()
                val args = if (check(")")) emptyList() else exprList()
                expect(")")
                args
            }
            else -> fail("function arguments expected near '${t.text}'")
        }
    }

    private fun table(): Expr {
        val line = expect("{").line
        val fields = mutableListOf<TableField>()
        while (!check("}")) {
            fields += when {
                check("[") -> {
                    next()
                    val key = expr()
                    expect("]")
                    expect("=")
                    TableField.Keyed(key, expr())
                }
                peek().type == TokenType.NAME && peek(1).let { it.type == TokenType.SYMBOL && it.text == "=" } -> {
                    val key = name()
                    next()
                    TableField.Named(key, expr())
                }
                else -> TableField.Positional(expr())
            }
            if (!accept(",") && !accept(";")) break
        }
        expect("}", "to close '{' at line $line")
        return Expr.Table(fields, line)
    }
}
