package com.brenninhotools.vconverter.script

import com.brenninhotools.vconverter.script.lua.Expr
import com.brenninhotools.vconverter.script.lua.LuaParser
import com.brenninhotools.vconverter.script.lua.LuaSyntaxException
import com.brenninhotools.vconverter.script.lua.Stat
import com.brenninhotools.vconverter.script.lua.TableField

enum class ScriptScope(val label: String) {
    GLOBAL("Global"),
    SONG("Song"),
    STAGE("Stage"),
    EVENT("Event"),
    NOTE_TYPE("Note type")
}

data class ScriptTarget(val scope: ScriptScope, val key: String = "")

data class ScriptIssue(val line: Int, val message: String)

data class GeneratedScript(
    val className: String,
    val moduleId: String,
    val source: String,
    val issues: List<ScriptIssue>,
    val callbacks: List<String>,
    val apiCalls: Set<String>
)

class LuaToHScript(
    private val className: String,
    private val moduleId: String,
    private val target: ScriptTarget,
    private val siblings: List<String> = emptyList(),
    private val noteTypes: String = "[]",
    private val eventNames: Map<String, String> = emptyMap(),
    private val ownEvent: String? = null
) {
    private val issues = mutableListOf<ScriptIssue>()
    private val helpers = linkedSetOf<String>()
    private val apiCalls = linkedSetOf<String>()
    private val assignedGlobals = linkedSetOf<String>()
    private val chunkLocals = linkedSetOf<String>()
    private val methodNames = linkedMapOf<String, Int>()
    private val usedFields = linkedSetOf<String>()
    private val scopes = ArrayDeque<MutableMap<String, String>>()
    private val loopContinueLabels = ArrayDeque<String?>()
    private var varargDepth = 0
    private var temp = 0

    companion object {
        private const val VARARGS = 8
        private val reserved = setOf(
            "abstract", "break", "case", "cast", "catch", "class", "continue", "default", "do", "dynamic", "else", "enum",
            "extends", "extern", "false", "final", "for", "function", "if", "implements", "import", "in", "inline", "interface",
            "macro", "new", "null", "operator", "overload", "override", "package", "private", "public", "return", "static",
            "super", "switch", "this", "throw", "trace", "true", "try", "typedef", "untyped", "using", "var", "while",
            "Std", "Math", "FlxG", "PlayState", "ModuleHandler", "Reflect", "Type", "Date", "EReg", "StringTools", "Lambda",
            "Paths", "Conductor", "Module", "Array", "String", "Int", "Float", "Bool", "Dynamic", "Void", "event", "game"
        )
        private val identifier = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        private val stringMethods = setOf("len", "upper", "lower", "sub", "rep", "reverse", "format", "find", "match", "gmatch", "gsub", "byte", "char")
        private val comparison = setOf("==", "~=", "<", ">", "<=", ">=")

        fun hxString(value: String): String {
            val sb = StringBuilder("\"")
            value.forEach { c ->
                when {
                    c == '\\' -> sb.append("\\\\")
                    c == '"' -> sb.append("\\\"")
                    c == '\n' -> sb.append("\\n")
                    c == '\r' -> sb.append("\\r")
                    c == '\t' -> sb.append("\\t")
                    c == '$' -> sb.append("$")
                    c.code < 0x20 -> sb.append(String.format("\\x%02X", c.code))
                    else -> sb.append(c)
                }
            }
            return sb.append('"').toString()
        }
    }

    fun convert(source: String): GeneratedScript {
        val chunk = try {
            LuaParser.parse(source)
        } catch (e: LuaSyntaxException) {
            issues += ScriptIssue(e.line, "Lua syntax error: ${e.message?.substringAfter(": ")}")
            return GeneratedScript(className, moduleId, "", issues, emptyList(), emptySet())
        }

        analyze(chunk)
        val methods = StringBuilder()
        val boot = Code()

        scopes.addLast(mutableMapOf())
        chunk.forEach { stat ->
            when {
                stat is Stat.FunctionDecl && stat.path.size == 1 && stat.method == null && stat.path[0] !in chunkLocals && "g_${stat.path[0]}" in methodNames ->
                    methods.append(method("g_" + stat.path[0], stat.function))
                stat is Stat.LocalFunction && "l_${stat.name}" in methodNames ->
                    methods.append(method("l_" + stat.name, stat.function))
                stat is Stat.Return -> issue(stat.line, "A top-level return was ignored.")
                else -> statement(stat, boot, chunkLevel = true)
            }
        }
        scopes.removeLast()

        val callbacks = methodNames.keys.filter { it.startsWith("g_") }.map { it.removePrefix("g_") }.filter { it in PsychApi.callbacks }
        val sourceCode = assemble(boot, methods.toString(), callbacks)
        return GeneratedScript(className, moduleId, sourceCode, issues.sortedBy { it.line }, callbacks, apiCalls)
    }

    private fun issue(line: Int, message: String) {
        if (issues.none { it.line == line && it.message == message }) issues += ScriptIssue(line, message)
    }

    private fun analyze(chunk: List<Stat>) {
        chunk.forEach { stat ->
            when (stat) {
                is Stat.Local -> chunkLocals += stat.names
                is Stat.LocalFunction -> {
                    chunkLocals += stat.name
                    methodNames["l_${stat.name}"] = stat.function.params.size
                }
                else -> Unit
            }
        }
        val functionDecls = HashMap<String, Int>()
        val otherAssignments = HashSet<String>()
        fun walkBlock(stats: List<Stat>, locals: Set<String>, top: Boolean) {
            val scope = locals.toMutableSet()
            stats.forEach { stat ->
                when (stat) {
                    is Stat.Local -> {
                        stat.values.forEach { walkExpr(it, scope) { b, l -> walkBlock(b, l, false) } }
                        if (!top) scope += stat.names
                    }
                    is Stat.LocalFunction -> {
                        if (!top) scope += stat.name
                        walkBlock(stat.function.body, scope + stat.function.params, false)
                    }
                    is Stat.FunctionDecl -> {
                        val name = stat.path[0]
                        if (stat.path.size == 1 && stat.method == null && name !in scope && name !in chunkLocals) {
                            assignedGlobals += name
                            if (top) functionDecls[name] = (functionDecls[name] ?: 0) + 1 else otherAssignments += name
                        }
                        walkBlock(stat.function.body, scope + stat.function.params, false)
                    }
                    is Stat.Assign -> {
                        stat.targets.forEach { t ->
                            if (t is Expr.Name && t.name !in scope && t.name !in chunkLocals) {
                                assignedGlobals += t.name
                                otherAssignments += t.name
                            } else {
                                walkExpr(t, scope) { b, l -> walkBlock(b, l, false) }
                            }
                        }
                        stat.values.forEach { walkExpr(it, scope) { b, l -> walkBlock(b, l, false) } }
                    }
                    is Stat.CallStat -> walkExpr(stat.call, scope) { b, l -> walkBlock(b, l, false) }
                    is Stat.Do -> walkBlock(stat.body, scope, false)
                    is Stat.While -> {
                        walkExpr(stat.condition, scope) { b, l -> walkBlock(b, l, false) }
                        walkBlock(stat.body, scope, false)
                    }
                    is Stat.Repeat -> {
                        walkBlock(stat.body, scope, false)
                        walkExpr(stat.condition, scope) { b, l -> walkBlock(b, l, false) }
                    }
                    is Stat.If -> {
                        stat.branches.forEach { (c, b) ->
                            walkExpr(c, scope) { bb, l -> walkBlock(bb, l, false) }
                            walkBlock(b, scope, false)
                        }
                        stat.otherwise?.let { walkBlock(it, scope, false) }
                    }
                    is Stat.NumericFor -> walkBlock(stat.body, scope + stat.variable, false)
                    is Stat.GenericFor -> {
                        stat.iterators.forEach { walkExpr(it, scope) { b, l -> walkBlock(b, l, false) } }
                        walkBlock(stat.body, scope + stat.names, false)
                    }
                    is Stat.Return -> stat.values.forEach { walkExpr(it, scope) { b, l -> walkBlock(b, l, false) } }
                    else -> Unit
                }
            }
        }
        walkBlock(chunk, emptySet(), true)
        functionDecls.forEach { (name, count) ->
            if (count == 1 && name !in otherAssignments) {
                val decl = chunk.filterIsInstance<Stat.FunctionDecl>().first { it.path.size == 1 && it.path[0] == name && it.method == null }
                methodNames["g_$name"] = decl.function.params.size
                methodNames[name] = decl.function.params.size
            }
        }
        methodNames.keys.filter { !it.startsWith("g_") && !it.startsWith("l_") }.forEach { methodNames.remove(it) }
    }

    private fun walkExpr(e: Expr, scope: Set<String>, block: (List<Stat>, Set<String>) -> Unit) {
        when (e) {
            is Expr.Func -> block(e.body, scope + e.params)
            is Expr.Binary -> {
                walkExpr(e.left, scope, block)
                walkExpr(e.right, scope, block)
            }
            is Expr.Unary -> walkExpr(e.operand, scope, block)
            is Expr.Index -> {
                walkExpr(e.target, scope, block)
                walkExpr(e.key, scope, block)
            }
            is Expr.Field -> walkExpr(e.target, scope, block)
            is Expr.Call -> {
                walkExpr(e.function, scope, block)
                e.args.forEach { walkExpr(it, scope, block) }
            }
            is Expr.Method -> {
                walkExpr(e.target, scope, block)
                e.args.forEach { walkExpr(it, scope, block) }
            }
            is Expr.Paren -> walkExpr(e.inner, scope, block)
            is Expr.Table -> e.fields.forEach { f ->
                when (f) {
                    is TableField.Positional -> walkExpr(f.value, scope, block)
                    is TableField.Named -> walkExpr(f.value, scope, block)
                    is TableField.Keyed -> {
                        walkExpr(f.key, scope, block)
                        walkExpr(f.value, scope, block)
                    }
                }
            }
            else -> Unit
        }
    }

    private class Code {
        private val sb = StringBuilder()
        private var indent = 0

        fun line(text: String) {
            sb.append("  ".repeat(indent)).append(text).append('\n')
        }

        fun open(text: String) {
            line(text)
            line("{")
            indent++
        }

        fun close(suffix: String = "") {
            indent--
            line("}$suffix")
        }

        fun nested(block: Code.() -> Unit) {
            indent++
            block()
            indent--
        }

        fun lines(): List<String> = sb.toString().trimEnd('\n').split('\n').filter { it.isNotEmpty() }

        override fun toString(): String = sb.toString()
    }

    private fun tmp(prefix: String): String = "__$prefix${++temp}"

    private fun declareLocal(luaName: String): String {
        val safe = if (luaName in reserved || luaName.startsWith("lua") || luaName.startsWith("psych") || luaName.startsWith("__")) "v_$luaName" else luaName
        scopes.last()[luaName] = safe
        return safe
    }

    private fun lookupLocal(name: String): String? {
        for (scope in scopes.reversed()) scope[name]?.let { return it }
        return null
    }

    private fun withScope(block: () -> Unit) {
        scopes.addLast(mutableMapOf())
        try {
            block()
        } finally {
            scopes.removeLast()
        }
    }

    private fun params(func: Expr.Func): String {
        val names = func.params.map { declareLocal(it) }.toMutableList()
        if (func.isVararg) repeat(VARARGS) { names += "va$it" }
        return names.joinToString(", ") { "?$it" }
    }

    private fun method(name: String, func: Expr.Func): String {
        val code = Code()
        scopes.addLast(mutableMapOf())
        if (func.isVararg) varargDepth++
        val parameters = params(func)
        code.nested {
            open("function $name($parameters)")
            block(func.body, this, loop = false)
            close()
        }
        if (func.isVararg) varargDepth--
        scopes.removeLast()
        return "\n" + code.toString()
    }

    private fun block(stats: List<Stat>, code: Code, loop: Boolean, continueLabel: String? = null) {
        withScope {
            if (loop) loopContinueLabels.addLast(continueLabel)
            stats.forEach { statement(it, code, chunkLevel = false) }
            if (loop) loopContinueLabels.removeLast()
        }
    }

    private fun loopLabel(body: List<Stat>): String? = (body.lastOrNull() as? Stat.Label)?.name

    private fun statement(stat: Stat, code: Code, chunkLevel: Boolean) {
        when (stat) {
            is Stat.Local -> {
                val values = stat.values.map { expr(it) }
                if (stat.names.size > 1 && stat.values.size == 1 && isMultiValue(stat.values[0])) {
                    issue(stat.line, "Only the first value of a multiple-value call is kept.")
                }
                stat.names.forEachIndexed { i, n ->
                    val value = values.getOrNull(i) ?: "null"
                    if (chunkLevel) {
                        usedFields += "l_$n"
                        code.line("l_$n = $value;")
                    } else {
                        val target = declareLocal(n)
                        code.line("var $target = $value;")
                    }
                }
                values.drop(stat.names.size).forEach { code.line("$it;") }
            }
            is Stat.LocalFunction -> {
                if (chunkLevel) {
                    usedFields += "l_${stat.name}"
                    code.line("l_${stat.name} = ${expr(stat.function)};")
                } else {
                    val n = declareLocal(stat.name)
                    code.line("var $n = null;")
                    code.line("$n = ${expr(stat.function)};")
                }
            }
            is Stat.FunctionDecl -> {
                val fn = expr(stat.function)
                if (stat.path.size == 1 && stat.method == null) {
                    code.line("${assignTarget(Expr.Name(stat.path[0], stat.line))} = $fn;")
                } else {
                    var target: Expr = Expr.Name(stat.path[0], stat.line)
                    stat.path.drop(1).forEach { target = Expr.Field(target, it, stat.line) }
                    if (stat.method != null) target = Expr.Field(target, stat.method, stat.line)
                    assign(target, fn, code)
                }
            }
            is Stat.Assign -> {
                if (stat.targets.size == 1) {
                    assign(stat.targets[0], expr(stat.values.firstOrNull() ?: Expr.Nil(stat.line)), code)
                    stat.values.drop(1).forEach { code.line("${expr(it)};") }
                } else {
                    if (stat.values.size == 1 && isMultiValue(stat.values[0])) {
                        issue(stat.line, "Only the first value of a multiple-value call is kept.")
                    }
                    val temps = stat.targets.indices.map { i ->
                        val t = tmp("v")
                        code.line("var $t = ${stat.values.getOrNull(i)?.let { expr(it) } ?: "null"};")
                        t
                    }
                    stat.values.drop(stat.targets.size).forEach { code.line("${expr(it)};") }
                    stat.targets.forEachIndexed { i, t -> assign(t, temps[i], code) }
                }
            }
            is Stat.CallStat -> code.line("${expr(stat.call)};")
            is Stat.Do -> {
                code.line("{")
                code.nested { block(stat.body, this, loop = false) }
                code.line("}")
            }
            is Stat.While -> {
                code.open("while (${cond(stat.condition)})")
                block(stat.body, code, loop = true, continueLabel = loopLabel(stat.body))
                code.close()
            }
            is Stat.Repeat -> {
                code.open("do")
                block(stat.body, code, loop = true, continueLabel = loopLabel(stat.body))
                code.close(" while (!(${cond(stat.condition)}));")
            }
            is Stat.If -> {
                stat.branches.forEachIndexed { i, (c, body) ->
                    if (i == 0) code.open("if (${cond(c)})") else code.open("else if (${cond(c)})")
                    block(body, code, loop = false)
                    code.close()
                }
                stat.otherwise?.let {
                    code.open("else")
                    block(it, code, loop = false)
                    code.close()
                }
            }
            is Stat.NumericFor -> numericFor(stat, code)
            is Stat.GenericFor -> genericFor(stat, code)
            is Stat.Return -> {
                if (stat.values.size > 1) issue(stat.line, "Only the first returned value is kept.")
                code.line(if (stat.values.isEmpty()) "return null;" else "return ${expr(stat.values[0])};")
            }
            is Stat.Break -> code.line("break;")
            is Stat.Goto -> {
                if (loopContinueLabels.lastOrNull() == stat.label) code.line("continue;")
                else issue(stat.line, "goto ${stat.label} is not supported and was removed.")
            }
            is Stat.Label -> Unit
        }
    }

    private fun numericFor(stat: Stat.NumericFor, code: Code) {
        val limit = tmp("limit")
        val step = tmp("step")
        code.line("{")
        code.nested {
            line("var $limit = ${expr(stat.limit)};")
            val stepValue = stat.step?.let { expr(it) } ?: "1"
            line("var $step = $stepValue;")
            val counter = tmp("i")
            line("var $counter = (${expr(stat.start)}) - $step;")
            open("while (true)")
            line("$counter += $step;")
            val positive = stat.step == null || (stat.step is Expr.Num && !(stat.step as Expr.Num).text.startsWith("-"))
            line(if (positive) "if ($counter > $limit) break;" else "if ($step > 0 ? $counter > $limit : $counter < $limit) break;")
            withScope {
                val v = declareLocal(stat.variable)
                line("var $v = $counter;")
                block(stat.body, this, loop = true, continueLabel = loopLabel(stat.body))
            }
            close()
        }
        code.line("}")
    }

    private fun genericFor(stat: Stat.GenericFor, code: Code) {
        val iterator = stat.iterators.firstOrNull()
        val call = iterator as? Expr.Call
        val fnName = (call?.function as? Expr.Name)?.name
        val fieldName = (call?.function as? Expr.Field)?.let { f -> ((f.target as? Expr.Name)?.name ?: "") + "." + f.name }
        val methodName = (iterator as? Expr.Method)?.name
        when {
            fnName == "pairs" && lookupLocal("pairs") == null && call.args.size == 1 -> {
                helpers += "luaKeys"
                helpers += "luaGet"
                val table = tmp("t")
                val key = tmp("k")
                code.line("{")
                code.nested {
                    line("var $table = ${expr(call.args[0])};")
                    open("for ($key in luaKeys($table))")
                    withScope {
                        val k = declareLocal(stat.names[0])
                        line("var $k = $key;")
                        stat.names.getOrNull(1)?.let { line("var ${declareLocal(it)} = luaGet($table, $key);") }
                        block(stat.body, this, loop = true, continueLabel = loopLabel(stat.body))
                    }
                    close()
                }
                code.line("}")
            }
            fnName == "ipairs" && lookupLocal("ipairs") == null && call.args.size == 1 -> {
                helpers += "luaLen"
                helpers += "luaGet"
                val table = tmp("t")
                val index = tmp("i")
                code.line("{")
                code.nested {
                    line("var $table = ${expr(call.args[0])};")
                    open("for ($index in 0...luaLen($table))")
                    withScope {
                        val i = declareLocal(stat.names[0])
                        line("var $i = $index + 1;")
                        val v = stat.names.getOrNull(1)?.let { declareLocal(it) } ?: tmp("v")
                        line("var $v = luaGet($table, $i);")
                        line("if ($v == null) break;")
                        block(stat.body, this, loop = true, continueLabel = loopLabel(stat.body))
                    }
                    close()
                }
                code.line("}")
            }
            (fieldName == "string.gmatch" && call != null && call.args.size == 2) || (methodName == "gmatch" && (iterator as Expr.Method).args.size == 1) -> {
                val subject: Expr
                val pattern: Expr
                if (call != null) {
                    subject = call.args[0]
                    pattern = call.args[1]
                } else {
                    val m = iterator as Expr.Method
                    subject = m.target
                    pattern = m.args[0]
                }
                helpers += "luaGmatch"
                val match = tmp("m")
                code.open("for ($match in luaGmatch(${expr(subject)}, ${regexArg(pattern, stat.line)}))")
                withScope {
                    stat.names.forEachIndexed { i, n -> line("var ${declareLocal(n)} = $match[$i];", code) }
                    block(stat.body, code, loop = true, continueLabel = loopLabel(stat.body))
                }
                code.close()
            }
            else -> issue(stat.line, "This kind of generic for loop is not supported and was removed (only pairs, ipairs and string.gmatch).")
        }
    }

    private fun line(text: String, code: Code) = code.line(text)

    private fun assignTarget(name: Expr.Name): String {
        lookupLocal(name.name)?.let { return it }
        if (name.name in chunkLocals) {
            usedFields += "l_${name.name}"
            return "l_${name.name}"
        }
        usedFields += "g_${name.name}"
        return "g_${name.name}"
    }

    private fun assign(target: Expr, value: String, code: Code) {
        when (target) {
            is Expr.Name -> code.line("${assignTarget(target)} = $value;")
            is Expr.Field -> {
                if (identifier.matches(target.name) && target.name !in reserved) {
                    code.line("${expr(target.target)}.${target.name} = $value;")
                } else {
                    helpers += "luaSet"
                    code.line("luaSet(${expr(target.target)}, ${hxString(target.name)}, $value);")
                }
            }
            is Expr.Index -> {
                helpers += "luaSet"
                code.line("luaSet(${expr(target.target)}, ${expr(target.key)}, $value);")
            }
            else -> issue(target.line, "Unsupported assignment target.")
        }
    }

    private fun isMultiValue(e: Expr): Boolean = e is Expr.Call || e is Expr.Method || e is Expr.Vararg

    private fun isBoolExpr(e: Expr): Boolean = when (e) {
        is Expr.Bool -> true
        is Expr.Paren -> isBoolExpr(e.inner)
        is Expr.Unary -> e.op == "not"
        is Expr.Binary -> e.op in comparison || ((e.op == "and" || e.op == "or") && isBoolExpr(e.left) && isBoolExpr(e.right))
        else -> false
    }

    private fun cond(e: Expr): String = when {
        e is Expr.Paren -> cond(e.inner)
        e is Expr.Unary && e.op == "not" -> "!(${cond(e.operand)})"
        e is Expr.Binary && e.op == "and" -> "(${cond(e.left)} && ${cond(e.right)})"
        e is Expr.Binary && e.op == "or" -> "(${cond(e.left)} || ${cond(e.right)})"
        isBoolExpr(e) -> expr(e)
        else -> {
            helpers += "luaTrue"
            "luaTrue(${expr(e)})"
        }
    }

    private fun isSimple(e: Expr): Boolean = when (e) {
        is Expr.Name, is Expr.Num, is Expr.Str, is Expr.Nil, is Expr.Bool -> true
        is Expr.Field -> isSimple(e.target)
        is Expr.Paren -> isSimple(e.inner)
        else -> false
    }

    private fun isTruthyLiteral(e: Expr): Boolean = e is Expr.Num || e is Expr.Str || e is Expr.Table || e is Expr.Func || (e is Expr.Bool && e.value)

    fun expr(e: Expr): String = when (e) {
        is Expr.Nil -> "null"
        is Expr.Bool -> e.value.toString()
        is Expr.Num -> number(e.text)
        is Expr.Str -> hxString(e.value)
        is Expr.Vararg -> {
            if (varargDepth == 0) issue(e.line, "'...' used outside a vararg function.")
            helpers += "luaPack"
            "luaPack([${(0 until VARARGS).joinToString(", ") { "va$it" }}])"
        }
        is Expr.Paren -> "(${expr(e.inner)})"
        is Expr.Func -> function(e)
        is Expr.Table -> table(e)
        is Expr.Unary -> when (e.op) {
            "not" -> "!(${cond(e.operand)})"
            "-" -> "-(${expr(e.operand)})"
            "#" -> {
                helpers += "luaLen"
                "luaLen(${expr(e.operand)})"
            }
            else -> "(~${expr(e.operand)})"
        }
        is Expr.Binary -> binary(e)
        is Expr.Name -> name(e)
        is Expr.Field -> field(e)
        is Expr.Index -> index(e)
        is Expr.Call -> call(e)
        is Expr.Method -> method(e)
    }

    private fun number(text: String): String {
        var t = text
        if (t.startsWith(".")) t = "0$t"
        if (t.endsWith(".")) t += "0"
        if (!t.startsWith("0x") && !t.startsWith("0X") && !t.contains('.') && !t.contains('e') && !t.contains('E')) {
            val value = t.toBigIntegerOrNull()
            if (value != null && value > Int.MAX_VALUE.toBigInteger()) t += ".0"
        }
        return t
    }

    private fun function(func: Expr.Func): String {
        val code = Code()
        scopes.addLast(mutableMapOf())
        if (func.isVararg) varargDepth++
        val parameters = params(func)
        code.open("function($parameters)")
        block(func.body, code, loop = false)
        code.close()
        if (func.isVararg) varargDepth--
        scopes.removeLast()
        return code.lines().joinToString("\n")
    }

    private fun table(e: Expr.Table): String {
        if (e.fields.isEmpty()) return "{}"
        val positional = e.fields.filterIsInstance<TableField.Positional>()
        val named = e.fields.filterIsInstance<TableField.Named>()
        val keyed = e.fields.filterIsInstance<TableField.Keyed>()
        if (named.isEmpty() && keyed.isEmpty()) {
            if (positional.lastOrNull()?.value is Expr.Vararg && varargDepth > 0) {
                helpers += "luaPack"
                val items = positional.dropLast(1).map { expr(it.value) } + (0 until VARARGS).map { "va$it" }
                return "luaPack([" + items.joinToString(", ") + "])"
            }
            return "[" + positional.joinToString(", ") { expr(it.value) } + "]"
        }
        if (positional.isEmpty() && keyed.isEmpty() && named.all { it.name !in reserved }) {
            return "{" + named.joinToString(", ") { "${it.name}: ${expr(it.value)}" } + "}"
        }
        helpers += "luaTableFrom"
        helpers += "luaSet"
        val pairs = named.map { "[${hxString(it.name)}, ${expr(it.value)}]" } + keyed.map { "[${expr(it.key)}, ${expr(it.value)}]" }
        return "luaTableFrom([${positional.joinToString(", ") { expr(it.value) }}], [${pairs.joinToString(", ")}])"
    }

    private fun binary(e: Expr.Binary): String {
        val l = e.left
        val r = e.right
        return when (e.op) {
            "+", "-", "*", "/" -> "(${expr(l)} ${e.op} ${expr(r)})"
            "%" -> {
                helpers += "luaMod"
                "luaMod(${expr(l)}, ${expr(r)})"
            }
            "//" -> "Math.floor(${expr(l)} / ${expr(r)})"
            "^" -> "Math.pow(${expr(l)}, ${expr(r)})"
            ".." -> "(\"\" + " + concatParts(e).joinToString(" + ") { part -> expr(part) } + ")"
            "==" -> "(${expr(l)} == ${expr(r)})"
            "~=" -> "(${expr(l)} != ${expr(r)})"
            "<", ">", "<=", ">=" -> "(${expr(l)} ${e.op} ${expr(r)})"
            "&", "|", "<<", ">>" -> "(${expr(l)} ${e.op} ${expr(r)})"
            "~" -> "(${expr(l)} ^ ${expr(r)})"
            "and" -> when {
                isBoolExpr(l) && isBoolExpr(r) -> "(${cond(l)} && ${cond(r)})"
                isSimple(l) -> {
                    helpers += "luaTrue"
                    "(luaTrue(${expr(l)}) ? ${expr(r)} : ${expr(l)})"
                }
                else -> {
                    helpers += "luaTrue"
                    val t = tmp("a")
                    "{ var $t = ${expr(l)}; luaTrue($t) ? ${expr(r)} : $t; }"
                }
            }
            "or" -> when {
                l is Expr.Binary && l.op == "and" && isTruthyLiteral(l.right) -> "(${cond(l.left)} ? ${expr(l.right)} : ${expr(r)})"
                isBoolExpr(l) && isBoolExpr(r) -> "(${cond(l)} || ${cond(r)})"
                isSimple(l) -> {
                    helpers += "luaTrue"
                    "(luaTrue(${expr(l)}) ? ${expr(l)} : ${expr(r)})"
                }
                else -> {
                    helpers += "luaTrue"
                    val t = tmp("o")
                    "{ var $t = ${expr(l)}; luaTrue($t) ? $t : ${expr(r)}; }"
                }
            }
            else -> {
                issue(e.line, "Operator ${e.op} is not supported.")
                "null"
            }
        }
    }

    private fun concatParts(e: Expr): List<Expr> =
        if (e is Expr.Binary && e.op == "..") concatParts(e.left) + concatParts(e.right) else listOf(e)

    private fun name(e: Expr.Name): String {
        lookupLocal(e.name)?.let { return it }
        if (e.name in chunkLocals) {
            usedFields += "l_${e.name}"
            return "l_${e.name}"
        }
        if (e.name in assignedGlobals) {
            if ("g_${e.name}" !in methodNames) usedFields += "g_${e.name}"
            return "g_${e.name}"
        }
        PsychApi.constants[e.name]?.let { return hxString(it) }
        return when (e.name) {
            "self" -> "self"
            "math", "string", "table", "os", "bit", "coroutine", "io" -> {
                issue(e.line, "The Lua library '${e.name}' cannot be used as a value.")
                "null"
            }
            "_G" -> {
                issue(e.line, "_G is not supported.")
                "null"
            }
            else -> {
                if (e.name in PsychApi.implemented || e.name in PsychApi.notImplemented) {
                    issue(e.line, "Psych function '${e.name}' used as a value is not supported.")
                    "null"
                } else {
                    "psychVar(${hxString(e.name)})"
                }
            }
        }
    }

    private fun field(e: Expr.Field): String {
        val base = (e.target as? Expr.Name)?.takeIf { lookupLocal(it.name) == null && it.name !in chunkLocals && it.name !in assignedGlobals }?.name
        when (base) {
            "math" -> return when (e.name) {
                "pi" -> "Math.PI"
                "huge" -> "Math.POSITIVE_INFINITY"
                "maxinteger" -> "2147483647"
                "mininteger" -> "-2147483648"
                else -> {
                    issue(e.line, "math.${e.name} is not supported.")
                    "null"
                }
            }
            "string", "table", "os", "bit", "coroutine", "io" -> {
                issue(e.line, "${base}.${e.name} can only be called.")
                return "null"
            }
        }
        if (identifier.matches(e.name) && e.name !in reserved) return "${expr(e.target)}.${e.name}"
        helpers += "luaGet"
        return "luaGet(${expr(e.target)}, ${hxString(e.name)})"
    }

    private fun index(e: Expr.Index): String {
        val key = e.key
        if (key is Expr.Str && identifier.matches(key.value) && key.value !in reserved) {
            return "${expr(e.target)}.${key.value}"
        }
        helpers += "luaGet"
        return "luaGet(${expr(e.target)}, ${expr(key)})"
    }

    private fun args(list: List<Expr>): String = list.joinToString(", ") { arg ->
        if (arg is Expr.Vararg && varargDepth > 0) (0 until VARARGS).joinToString(", ") { "va$it" } else expr(arg)
    }

    private fun regexArg(pattern: Expr, line: Int, plain: Boolean = false): String {
        if (pattern is Expr.Str) {
            if (plain) return hxString(escapeRegex(pattern.value))
            val regex = LuaPattern.toRegex(pattern.value)
            if (regex != null) return hxString(regex)
            issue(line, "The Lua pattern ${hxString(pattern.value)} uses features that cannot be converted; it is matched as plain text.")
            return hxString(escapeRegex(pattern.value))
        }
        helpers += "luaPatternToRegex"
        return "luaPatternToRegex(${expr(pattern)})"
    }

    private fun escapeRegex(text: String): String = buildString {
        text.forEach { c ->
            if (c in "\\^$.|?*+()[]{}/") append('\\')
            append(c)
        }
    }

    private fun call(e: Expr.Call): String {
        val fn = e.function
        if (fn is Expr.Name && lookupLocal(fn.name) == null && fn.name !in chunkLocals && fn.name !in assignedGlobals) {
            stdCall(fn.name, e.args, e.line)?.let { return it }
            if (fn.name in PsychApi.implemented || fn.name in PsychApi.notImplemented || fn.name !in PsychApi.variables) {
                apiCalls += fn.name
                if (fn.name in PsychApi.notImplemented) {
                    issue(e.line, "Psych function '${fn.name}' has no V-Slice equivalent yet; the call does nothing.")
                } else if (fn.name !in PsychApi.implemented) {
                    issue(e.line, "Unknown function '${fn.name}' (not defined in this script); it has no V-Slice implementation and does nothing.")
                }
                return "psych(${hxString(fn.name)}, [${args(e.args)}])"
            }
        }
        if (fn is Expr.Field) {
            val base = (fn.target as? Expr.Name)?.takeIf { lookupLocal(it.name) == null && it.name !in chunkLocals && it.name !in assignedGlobals }?.name
            if (base != null) libraryCall(base, fn.name, e.args, e.line)?.let { return it }
        }
        return "${expr(fn)}(${args(e.args)})"
    }

    private fun stdCall(name: String, a: List<Expr>, line: Int): String? {
        fun arg(i: Int) = a.getOrNull(i)?.let { expr(it) } ?: "null"
        return when (name) {
            "print" -> {
                apiCalls += "debugPrint"
                "psych(\"debugPrint\", [${args(a)}])"
            }
            "tostring" -> {
                helpers += "luaToString"
                "luaToString(${arg(0)})"
            }
            "tonumber" -> {
                helpers += "luaToNumber"
                "luaToNumber(${arg(0)})"
            }
            "type" -> {
                helpers += "luaType"
                "luaType(${arg(0)})"
            }
            "unpack" -> {
                issue(line, "unpack() can only pass the first value.")
                helpers += "luaGet"
                "luaGet(${arg(0)}, 1)"
            }
            "pcall" -> {
                helpers += "luaPcall"
                "luaPcall(${arg(0)}, [${args(a.drop(1))}])"
            }
            "error" -> "throw ${arg(0)}"
            "assert" -> {
                helpers += "luaAssert"
                "luaAssert(${arg(0)}, ${arg(1)})"
            }
            "select" -> {
                if (a.firstOrNull() is Expr.Str && (a[0] as Expr.Str).value == "#") {
                    helpers += "luaPack"
                    helpers += "luaLen"
                    "luaLen(luaPack([${args(a.drop(1))}]))"
                } else {
                    issue(line, "select() is only supported as select('#', ...).")
                    "null"
                }
            }
            "setmetatable" -> {
                issue(line, "Metatables are not supported; setmetatable() returns the table unchanged.")
                arg(0)
            }
            "getmetatable", "rawget", "rawset", "rawequal", "next", "require", "dofile", "loadstring", "load", "collectgarbage", "module" -> {
                issue(line, "$name() is not supported and returns nil.")
                "null"
            }
            "pairs", "ipairs" -> {
                issue(line, "$name() is only supported inside a for loop.")
                "null"
            }
            else -> null
        }
    }

    private fun libraryCall(lib: String, fn: String, a: List<Expr>, line: Int): String? {
        fun arg(i: Int) = a.getOrNull(i)?.let { expr(it) } ?: "null"
        fun use(vararg names: String) = names.forEach { helpers += it }
        return when (lib) {
            "math" -> when (fn) {
                "floor", "ceil", "abs", "sqrt", "sin", "cos", "tan", "asin", "acos", "exp" -> "Math.$fn(${arg(0)})"
                "atan" -> if (a.size >= 2) "Math.atan2(${arg(0)}, ${arg(1)})" else "Math.atan(${arg(0)})"
                "log" -> if (a.size >= 2) "(Math.log(${arg(0)}) / Math.log(${arg(1)}))" else "Math.log(${arg(0)})"
                "log10" -> "(Math.log(${arg(0)}) / Math.log(10))"
                "pow" -> "Math.pow(${arg(0)}, ${arg(1)})"
                "fmod" -> "(${arg(0)} % ${arg(1)})"
                "modf" -> "Math.ffloor(${arg(0)})"
                "min", "max" -> a.map { expr(it) }.reduceOrNull { acc, s -> "Math.$fn($acc, $s)" } ?: "null"
                "random" -> when (a.size) {
                    0 -> "Math.random()"
                    1 -> "FlxG.random.int(1, Std.int(${arg(0)}))"
                    else -> "FlxG.random.int(Std.int(${arg(0)}), Std.int(${arg(1)}))"
                }
                "randomseed" -> "null"
                "round" -> "Math.round(${arg(0)})"
                "rad" -> "(${arg(0)} * Math.PI / 180)"
                "deg" -> "(${arg(0)} * 180 / Math.PI)"
                "tointeger" -> "Std.int(${arg(0)})"
                else -> unsupportedLib(lib, fn, line)
            }
            "string" -> if (fn in stringMethods || fn == "trim") stringFunction(fn, a, line) else unsupportedLib(lib, fn, line)
            "table" -> when (fn) {
                "insert" -> {
                    use("luaInsert", "luaLen", "luaSet")
                    if (a.size >= 3) "luaInsert(${arg(0)}, ${arg(2)}, ${arg(1)})" else "luaInsert(${arg(0)}, ${arg(1)}, null)"
                }
                "remove" -> {
                    use("luaRemove", "luaLen", "luaGet", "luaSet")
                    "luaRemove(${arg(0)}, ${arg(1)})"
                }
                "concat" -> {
                    use("luaConcatTable", "luaLen", "luaGet")
                    "luaConcatTable(${arg(0)}, ${if (a.size > 1) arg(1) else "\"\""})"
                }
                "getn" -> {
                    use("luaLen")
                    "luaLen(${arg(0)})"
                }
                "sort" -> {
                    use("luaSort", "luaTrue")
                    "luaSort(${arg(0)}, ${arg(1)})"
                }
                "unpack" -> {
                    issue(line, "table.unpack() can only pass the first value.")
                    use("luaGet")
                    "luaGet(${arg(0)}, 1)"
                }
                else -> unsupportedLib(lib, fn, line)
            }
            "os" -> when (fn) {
                "time" -> "Std.int(Date.now().getTime() / 1000)"
                "clock" -> "(Date.now().getTime() / 1000)"
                "date" -> "Date.now().toString()"
                else -> unsupportedLib(lib, fn, line)
            }
            "bit" -> when (fn) {
                "band" -> "(${arg(0)} & ${arg(1)})"
                "bor" -> "(${arg(0)} | ${arg(1)})"
                "bxor" -> "(${arg(0)} ^ ${arg(1)})"
                "bnot" -> "(~${arg(0)})"
                "lshift" -> "(${arg(0)} << ${arg(1)})"
                "rshift" -> "(${arg(0)} >>> ${arg(1)})"
                "arshift" -> "(${arg(0)} >> ${arg(1)})"
                "tobit" -> "Std.int(${arg(0)})"
                else -> unsupportedLib(lib, fn, line)
            }
            "coroutine", "io", "debug", "package" -> unsupportedLib(lib, fn, line)
            else -> null
        }
    }

    private fun unsupportedLib(lib: String, fn: String, line: Int): String {
        issue(line, "$lib.$fn() is not supported and returns nil.")
        return "null"
    }

    private fun stringFunction(fn: String, a: List<Expr>, line: Int): String {
        fun arg(i: Int) = a.getOrNull(i)?.let { expr(it) } ?: "null"
        val s = arg(0)
        return when (fn) {
            "len" -> "(\"\" + $s).length"
            "upper" -> "(\"\" + $s).toUpperCase()"
            "lower" -> "(\"\" + $s).toLowerCase()"
            "sub" -> {
                helpers += "luaSub"
                "luaSub($s, ${arg(1)}, ${arg(2)})"
            }
            "rep" -> {
                helpers += "luaRep"
                "luaRep($s, ${arg(1)}, ${arg(2)})"
            }
            "reverse" -> {
                helpers += "luaReverse"
                "luaReverse($s)"
            }
            "trim" -> {
                helpers += "luaTrim"
                "luaTrim($s)"
            }
            "byte" -> "(\"\" + $s).charCodeAt(${if (a.size > 1) "Std.int(${arg(1)}) - 1" else "0"})"
            "char" -> {
                helpers += "luaChar"
                "luaChar([${a.joinToString(", ") { expr(it) }}])"
            }
            "format" -> {
                helpers += "luaFormat"
                helpers += "luaToString"
                "luaFormat(${arg(0)}, [${a.drop(1).joinToString(", ") { expr(it) }}])"
            }
            "find" -> {
                helpers += "luaFind"
                val plain = a.getOrNull(3).let { it is Expr.Bool && it.value }
                "luaFind($s, ${a.getOrNull(1)?.let { regexArg(it, line, plain) } ?: "\"\""}, ${arg(2)})"
            }
            "match" -> {
                helpers += "luaMatch"
                "luaMatch($s, ${a.getOrNull(1)?.let { regexArg(it, line) } ?: "\"\""}, ${arg(2)})"
            }
            "gsub" -> {
                helpers += "luaGsub"
                val repl = a.getOrNull(2)
                val replacement = if (repl is Expr.Str) hxString(LuaPattern.replacement(repl.value)) else expr(repl ?: Expr.Nil(line))
                "luaGsub($s, ${a.getOrNull(1)?.let { regexArg(it, line) } ?: "\"\""}, $replacement, ${arg(3)})"
            }
            "gmatch" -> {
                issue(line, "string.gmatch() is only supported in a for loop.")
                "null"
            }
            else -> "null"
        }
    }

    private fun method(e: Expr.Method): String {
        if (e.name in stringMethods) return stringFunction(e.name, listOf(e.target) + e.args, e.line)
        val callArgs = args(e.args)
        if (isSimple(e.target)) {
            val t = expr(e.target)
            val member = if (identifier.matches(e.name) && e.name !in reserved) "$t.${e.name}" else {
                helpers += "luaGet"
                "luaGet($t, ${hxString(e.name)})"
            }
            return "$member($t${if (callArgs.isEmpty()) "" else ", $callArgs"})"
        }
        helpers += "luaSelfCall"
        helpers += "luaGet"
        return "luaSelfCall(${expr(e.target)}, ${hxString(e.name)}, [$callArgs])"
    }

    private fun assemble(boot: Code, methods: String, callbacks: List<String>): String {
        val callbackRefs = LinkedHashMap<String, CallbackRef>()
        PsychApi.callbacks.forEach { name ->
            when {
                "g_$name" in methodNames -> callbackRefs[name] = CallbackRef(methodNames["g_$name"], true)
                name in assignedGlobals -> callbackRefs[name] = CallbackRef(null, false)
            }
        }
        val dispatch = LinkedHashMap<String, Int?>()
        methodNames.filterKeys { it.startsWith("g_") }.forEach { (method, arity) -> dispatch[method.removePrefix("g_")] = arity }
        assignedGlobals.filter { "g_$it" !in methodNames && "g_$it" in usedFields }.forEach { dispatch[it] = null }
        val fields = usedFields.filter { it !in methodNames }.sorted()
        val tail = mutableListOf<String>()
        Helpers.emit(helpers) { tail += it }
        methods.lines().forEach { tail += it.removePrefix("  ") }
        return ModuleWriter.write(
            ModuleSpec(
                className = className,
                moduleId = moduleId,
                siblings = siblings,
                noteTypes = noteTypes,
                eventNames = eventNames,
                ownEvent = ownEvent,
                target = target,
                prefix = "g_",
                fields = fields,
                bootLines = boot.lines().map { it.trimEnd() },
                callbacks = callbackRefs,
                dispatch = dispatch,
                setters = assignedGlobals.filter { "g_$it" in usedFields && "g_$it" !in methodNames },
                noteObjects = false,
                imports = emptyList(),
                tailLines = tail
            )
        )
    }
}
