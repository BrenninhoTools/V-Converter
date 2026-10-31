package com.brenninhotools.vconverter.reverse

object PsychDeadCode {
    class Result(val text: String, val removed: Map<String, Int>, val tainted: Set<String>)

    private val keywords = setOf("if", "while", "for", "switch", "try", "do")
    private val assignment = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*=(?!=)")
    private val declaration = Regex("^(?:(?:static|public|private|final|inline)\\s+)*(?:var|final)\\s+([A-Za-z_][A-Za-z0-9_]*)")

    fun typesToDynamic(text: String, names: Collection<String>): String {
        var t = text
        names.forEach { name ->
            val n = Regex.escape(name)
            t = Regex(":\\s*$n(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\s*(<[^<>]*>)?(?=\\s*[=;,)\\]{])").replace(t, ":Dynamic")
            t = Regex("<\\s*$n\\s*>").replace(t, "<Dynamic>")
            t = Regex("\\bcast\\s*\\(([^(),]+),\\s*$n\\s*\\)").replace(t) { "(${it.groupValues[1].trim()})" }
        }
        return t
    }

    fun prune(text: String, banned: Set<String>): Result {
        if (banned.isEmpty()) return Result(text, emptyMap(), emptySet())
        val removed = LinkedHashMap<String, Int>()
        val tainted = LinkedHashSet<String>()
        val out = Parser(text, banned, removed, tainted).block(0, text.length, HashSet())
        return Result(out.replace(Regex("\n[ \t]+(?=\n)"), ""), removed, tainted)
    }

    private class Parser(val src: String, val banned: Set<String>, val removed: MutableMap<String, Int>, val allTainted: MutableSet<String>) {
        fun hits(code: String, taint: Set<String>): String? {
            for (name in banned) if (Regex("(?<![\\w.])${Regex.escape(name)}\\b").containsMatchIn(code)) return name
            for (name in taint) if (Regex("(?<![\\w.])${Regex.escape(name)}\\b").containsMatchIn(code)) return name
            return null
        }

        fun drop(reason: String) {
            removed[reason] = (removed[reason] ?: 0) + 1
        }

        fun skipSpace(i: Int, end: Int): Int {
            var k = i
            while (k < end && src[k].isWhitespace()) k++
            return k
        }

        fun word(i: Int, end: Int): String {
            var k = i
            while (k < end && (src[k].isLetterOrDigit() || src[k] == '_')) k++
            return src.substring(i, k)
        }

        fun close(open: Int, end: Int, a: Char, b: Char): Int {
            var depth = 0
            var k = open
            while (k < end) {
                if (src[k] == a) depth++
                if (src[k] == b) {
                    depth--
                    if (depth == 0) return k
                }
                k++
            }
            return end - 1
        }

        fun simpleEnd(i: Int, end: Int): Int {
            var depth = 0
            var k = i
            while (k < end) {
                when (src[k]) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> {
                        if (depth == 0) return k
                        depth--
                    }
                    ';' -> if (depth == 0) return k + 1
                    '#' -> if (depth == 0 && (k == 0 || src[k - 1] == '\n' || src.substring(src.lastIndexOf('\n', k - 1) + 1, k).isBlank())) return k
                }
                k++
            }
            return end
        }

        fun labelEnd(i: Int, end: Int): Int {
            var depth = 0
            var k = i
            while (k < end) {
                when (src[k]) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    ':' -> if (depth == 0) return k + 1
                }
                k++
            }
            return end
        }

        fun block(start: Int, end: Int, taint: MutableSet<String>): String {
            val sb = StringBuilder()
            var i = start
            while (i < end) {
                val s = skipSpace(i, end)
                sb.append(src, i, s)
                if (s >= end) break
                val (next, text) = statement(s, end, taint)
                sb.append(text)
                i = if (next <= s) s + 1 else next
            }
            return sb.toString()
        }

        fun body(i: Int, end: Int, taint: MutableSet<String>): Pair<Int, String> {
            val s = skipSpace(i, end)
            if (s < end && src[s] == '{') {
                val c = close(s, end, '{', '}')
                return c + 1 to src.substring(i, s) + "{" + block(s + 1, c, HashSet(taint)) + "}"
            }
            val (next, text) = statement(s, end, taint)
            return next to src.substring(i, s) + text.ifBlank { "{}" }
        }

        fun statement(s: Int, end: Int, taint: MutableSet<String>): Pair<Int, String> {
            val c = src[s]
            if (c == '#') {
                val nl = src.indexOf('\n', s).let { if (it < 0 || it > end) end else it }
                return nl to src.substring(s, nl)
            }
            if (c == '{') {
                val e = close(s, end, '{', '}')
                return e + 1 to "{" + block(s + 1, e, HashSet(taint)) + "}"
            }
            if (c == '@') {
                var k = s + 1
                if (k < end && src[k] == ':') k++
                k += word(k, end).length
                if (k < end && src[k] == '(') k = close(k, end, '(', ')') + 1
                val (next, text) = statement(skipSpace(k, end), end, taint)
                return next to if (text.isBlank()) "" else src.substring(s, skipSpace(k, end)) + text
            }
            val w = if (c.isLetter() || c == '_') word(s, end) else ""
            if (w == "case" || w == "default") {
                val e = labelEnd(s, end)
                return e to src.substring(s, e)
            }
            if (w == "function") {
                val open = src.indexOf('{', s).takeIf { it in s until end } ?: return simple(s, end, taint)
                val semi = src.indexOf(';', s)
                if (semi in s until open) return simple(s, end, taint)
                val e = close(open, end, '{', '}')
                return e + 1 to src.substring(s, open) + "{" + block(open + 1, e, HashSet(taint)) + "}"
            }
            if (w in keywords || w == "else") return compound(s, end, w, taint)
            return simple(s, end, taint)
        }

        fun simple(s: Int, end: Int, taint: MutableSet<String>): Pair<Int, String> {
            val e = simpleEnd(s, end)
            val code = src.substring(s, e)
            val trimmed = code.trim()
            val decl = declaration.find(trimmed)
            if (decl != null) {
                val name = decl.groupValues[1]
                val hit = hits(trimmed.substring(decl.range.last + 1), taint - name)
                if (hit == null) {
                    taint -= name
                    return e to code
                }
                drop(hit)
                taint += name
                allTainted += name
                return e to ""
            }
            val assign = assignment.find(trimmed)
            if (assign != null) {
                val name = assign.groupValues[1]
                val hit = hits(trimmed.substring(assign.range.last + 1), taint - name)
                if (hit == null) {
                    taint -= name
                    return e to code
                }
                drop(hit)
                taint += name
                allTainted += name
                return e to ""
            }
            val hit = hits(code, taint) ?: return e to code
            drop(hit)
            return e to ""
        }

        fun compound(s: Int, end: Int, w: String, taint: MutableSet<String>): Pair<Int, String> {
            var k = s + w.length
            val head = StringBuilder(w)
            when (w) {
                "do" -> {
                    val (afterBody, bodyText) = body(k, end, taint)
                    var j = skipSpace(afterBody, end)
                    var tail = ""
                    if (word(j, end) == "while") {
                        val p = src.indexOf('(', j)
                        val pc = close(p, end, '(', ')')
                        val semi = simpleEnd(pc + 1, end)
                        tail = src.substring(afterBody, semi)
                        j = semi
                        if (hits(tail, taint) != null) {
                            drop(hits(tail, taint)!!)
                            return j to ""
                        }
                    }
                    return j to head.toString() + bodyText + tail
                }
                "try", "else" -> Unit
                else -> {
                    val p = skipSpace(k, end)
                    if (p < end && src[p] == '(') {
                        val pc = close(p, end, '(', ')')
                        val cond = src.substring(k, pc + 1)
                        k = pc + 1
                        head.append(cond)
                    }
                }
            }
            val headHit = if (w == "try" || w == "else") null else hits(head.toString().substring(w.length), taint)
            val loopVar = if (w == "for") Regex("\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+in\\b").find(head)?.groupValues?.get(1) else null
            val inner = HashSet(taint)
            if (loopVar != null) inner -= loopVar
            val (next, bodyText) = body(k, end, inner)
            val sb = StringBuilder(head).append(bodyText)
            var j = next
            while (true) {
                val t = skipSpace(j, end)
                val nw = word(t, end)
                if (w == "if" && nw == "else") {
                    val (n2, elseText) = compound(t, end, "else", taint)
                    sb.append(src, j, t).append(elseText.ifBlank { "else {}" })
                    j = n2
                    continue
                }
                if (w == "try" && nw == "catch") {
                    val p = src.indexOf('(', t)
                    val pc = close(p, end, '(', ')')
                    val (n2, catchBody) = body(pc + 1, end, HashSet(taint))
                    sb.append(src, j, pc + 1).append(catchBody)
                    j = n2
                    continue
                }
                break
            }
            if (headHit != null) {
                drop(headHit)
                return j to ""
            }
            return j to sb.toString()
        }
    }
}
