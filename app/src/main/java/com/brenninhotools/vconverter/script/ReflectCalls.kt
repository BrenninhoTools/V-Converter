package com.brenninhotools.vconverter.script

object ReflectCalls {
    data class Result(val text: String, val needsInvoke: Boolean, val unsupported: Set<String>)

    private val vsliceApi = setOf(
        "callMethod", "compare", "compareValues", "compareMethods", "copy", "copyAnonymousFieldsOf", "delete",
        "deleteAnonymousField", "field", "getField", "getAnonymousField", "fields", "getFieldsOf", "getAnonymousFieldsOf",
        "getProperty", "hasField", "hasAnonymousField", "isEnumValue", "isFunction", "isObject", "setField",
        "setAnonymousField", "setProperty", "createEmptyInstance", "createInstance", "resolveClass", "resolveEnum",
        "getClassFields", "getClassFieldsOf", "getInstanceFields", "getInstanceFieldsOf", "getClassName", "getClassNameOf",
        "makeVarArgs"
    )

    private val haxeReflect = setOf(
        "callMethod", "compare", "compareMethods", "copy", "deleteField", "field", "fields", "getProperty", "hasField",
        "isEnumValue", "isFunction", "isObject", "makeVarArgs", "setField", "setProperty"
    )

    private val toPsychNames = mapOf(
        "compareValues" to "Reflect.compare",
        "copyAnonymousFieldsOf" to "Reflect.copy",
        "delete" to "Reflect.deleteField",
        "deleteAnonymousField" to "Reflect.deleteField",
        "getField" to "Reflect.field",
        "getAnonymousField" to "Reflect.field",
        "getFieldsOf" to "Reflect.fields",
        "getAnonymousFieldsOf" to "Reflect.fields",
        "hasAnonymousField" to "Reflect.hasField",
        "setAnonymousField" to "Reflect.setField",
        "createEmptyInstance" to "Type.createEmptyInstance",
        "createInstance" to "Type.createInstance",
        "resolveClass" to "Type.resolveClass",
        "resolveEnum" to "Type.resolveEnum",
        "getClassFields" to "Type.getClassFields",
        "getInstanceFields" to "Type.getInstanceFields",
        "getClassName" to "Type.getClassName"
    )

    private data class Call(val start: Int, val end: Int, val owner: String, val name: String, val args: List<String>)

    fun toVSlice(text: String): Result {
        var needsInvoke = false
        var t = rewriteCalls(text) { call ->
            when {
                call.owner == "Type" && call.name == "getClassName" && call.args.size == 1 && inner(call.args[0], "Type", "getClass") != null ->
                    "Reflect.getClassNameOf(${inner(call.args[0], "Type", "getClass")})"
                call.owner == "Type" && call.name == "getClassFields" && call.args.size == 1 && inner(call.args[0], "Type", "getClass") != null ->
                    "Reflect.getClassFieldsOf(${inner(call.args[0], "Type", "getClass")})"
                call.owner == "Type" && call.name == "getInstanceFields" && call.args.size == 1 && inner(call.args[0], "Type", "getClass") != null ->
                    "Reflect.getInstanceFieldsOf(${inner(call.args[0], "Type", "getClass")})"
                call.owner == "Reflect" && call.name == "deleteField" -> "Reflect.delete(${call.args.joinToString(", ")})"
                call.owner == "Reflect" && call.name == "callMethod" && call.args.size == 3 -> {
                    val (obj, fn, args) = call.args
                    val lookup = findCalls(fn).firstOrNull { it.start == 0 && it.end == fn.length }
                    val name = lookup?.takeIf { it.owner == "Reflect" && it.name in setOf("field", "getProperty", "getField") && it.args.size == 2 && it.args[0] == obj }?.args?.get(1)
                    when {
                        isString(fn) -> null
                        name != null -> "Reflect.callMethod($obj, $name, $args)"
                        else -> {
                            needsInvoke = true
                            "invokeFunction($fn, $args)"
                        }
                    }
                }
                else -> null
            }
        }
        val unsupported = LinkedHashSet<String>()
        findCalls(t).forEach { if ((it.owner == "Reflect" || it.owner == "Type") && it.name !in vsliceApi) unsupported += "${it.owner}.${it.name}" }
        if (needsInvoke && !Regex("function\\s+invokeFunction\\s*\\(").containsMatchIn(t)) t = insertMember(t, invokeMember)
        return Result(t, needsInvoke, unsupported)
    }

    fun toPsych(text: String): String = rewriteCalls(text) { call ->
        if (call.owner != "Reflect" && call.owner != "Type") return@rewriteCalls null
        val args = call.args.joinToString(", ")
        when {
            call.name == "callMethod" && call.args.size == 3 -> {
                val (obj, name, rest) = call.args
                if (Regex("^Reflect\\.(?:field|getProperty)\\(").containsMatchIn(name) || !looksLikeName(name)) null
                else "Reflect.callMethod($obj, Reflect.getProperty($obj, $name), $rest)"
            }
            call.name == "getClassNameOf" && call.args.size == 1 -> {
                val o = call.args[0]
                "($o == null || Type.getClass($o) == null ? \"Unknown\" : Type.getClassName(Type.getClass($o)))"
            }
            call.name == "getClassFieldsOf" && call.args.size == 1 -> "Type.getClassFields(Type.getClass(${call.args[0]}))"
            call.name == "getInstanceFieldsOf" && call.args.size == 1 -> "Type.getInstanceFields(Type.getClass(${call.args[0]}))"
            call.name in toPsychNames -> "${toPsychNames.getValue(call.name)}($args)"
            call.owner == "Type" && call.name in haxeReflect -> "Reflect.${call.name}($args)"
            else -> null
        }
    }

    private fun looksLikeName(arg: String) = isString(arg) || arg.matches(Regex("^\u0001\\d+\u0001$")) || arg.matches(Regex("^[A-Za-z_][\\w.]*$"))

    private fun isString(arg: String) = arg.length >= 2 && (arg.first() == '"' || arg.first() == '\'') && arg.last() == arg.first() || arg.matches(Regex("^\u0001\\d+\u0001$"))

    private fun inner(arg: String, owner: String, name: String): String? {
        val call = findCalls(arg).firstOrNull() ?: return null
        return if (call.start == 0 && call.end == arg.length && call.owner == owner && call.name == name && call.args.size == 1) call.args[0] else null
    }

    private fun rewriteCalls(text: String, transform: (Call) -> String?): String {
        var t = text
        var guard = 0
        while (guard++ < 64) {
            var changed = false
            val sb = StringBuilder()
            var last = 0
            for (call in findCalls(t)) {
                if (call.start < last) continue
                val replacement = transform(call) ?: continue
                sb.append(t, last, call.start).append(replacement)
                last = call.end
                changed = true
            }
            if (!changed) break
            sb.append(t, last, t.length)
            t = sb.toString()
        }
        return t
    }

    private fun findCalls(text: String): List<Call> {
        val calls = mutableListOf<Call>()
        val pattern = Regex("(?<![\\w.])(Reflect|Type)\\s*\\.\\s*(\\w+)\\s*\\(")
        val masked = maskStrings(text)
        pattern.findAll(masked).forEach { m ->
            val open = m.range.last
            val close = matching(masked, open) ?: return@forEach
            calls += Call(m.range.first, close + 1, m.groupValues[1], m.groupValues[2], splitArgs(text.substring(open + 1, close)))
        }
        return calls
    }

    private fun matching(masked: String, open: Int): Int? {
        var depth = 0
        for (i in open until masked.length) {
            when (masked[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> {
                    depth--
                    if (depth == 0) return if (masked[i] == ')') i else null
                }
            }
        }
        return null
    }

    private fun splitArgs(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val masked = maskStrings(raw)
        val parts = mutableListOf<String>()
        var depth = 0
        var from = 0
        masked.forEachIndexed { i, c ->
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) {
                    parts += raw.substring(from, i).trim()
                    from = i + 1
                }
            }
        }
        parts += raw.substring(from).trim()
        return parts
    }

    private fun maskStrings(text: String): String {
        val sb = StringBuilder(text)
        var i = 0
        while (i < sb.length) {
            val c = sb[i]
            if (c == '"' || c == '\'') {
                var j = i + 1
                while (j < sb.length && sb[j] != c) {
                    if (sb[j] == '\\') j++
                    j++
                }
                for (k in i + 1 until minOf(j, sb.length)) sb.setCharAt(k, '_')
                i = j + 1
            } else if (c == '/' && i + 1 < sb.length && sb[i + 1] == '/') {
                while (i < sb.length && sb[i] != '\n') sb.setCharAt(i++, ' ')
            } else {
                i++
            }
        }
        return sb.toString()
    }

    fun insertMember(text: String, member: String): String {
        val masked = maskStrings(text)
        val classMatch = Regex("^class\\s+\\w+[^\\n{]*extends\\s+\\w+", RegexOption.MULTILINE).find(masked)
            ?: Regex("^class\\s+\\w+", RegexOption.MULTILINE).find(masked) ?: return text
        val open = masked.indexOf('{', classMatch.range.last)
        if (open < 0) return text
        var depth = 0
        for (i in open until masked.length) {
            when (masked[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val lineStart = text.lastIndexOf('\n', i - 1) + 1
                        return text.substring(0, lineStart) + "\n" + member + "\n" + text.substring(lineStart)
                    }
                }
            }
        }
        return text
    }

    val invokeMember = """  function invokeFunction(f:Dynamic, args:Array<Dynamic>):Dynamic
  {
    if (f == null) return null;
    var a:Array<Dynamic> = args == null ? [] : args;
    switch (a.length)
    {
      case 0: return f();
      case 1: return f(a[0]);
      case 2: return f(a[0], a[1]);
      case 3: return f(a[0], a[1], a[2]);
      case 4: return f(a[0], a[1], a[2], a[3]);
      case 5: return f(a[0], a[1], a[2], a[3], a[4]);
      case 6: return f(a[0], a[1], a[2], a[3], a[4], a[5]);
      case 7: return f(a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
      default: return f(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7]);
    }
  }"""
}
