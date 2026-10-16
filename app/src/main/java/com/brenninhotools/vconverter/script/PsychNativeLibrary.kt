package com.brenninhotools.vconverter.script

object PsychNativeLibrary {
    class Member(val name: String, val text: String, val isField: Boolean)

    class Library(
        val imports: List<String>,
        val members: Map<String, Member>,
        val cases: List<Pair<List<String>, String>>,
        val builtinEvents: Map<String, String>
    )

    class Embedded(val imports: List<String>, val members: String, val usedCalls: Set<String>, val included: Set<String>, val moduleText: String)

    private val skipped = setOf("new", "psychVariable", "psychCall", "dispatch", "psychClose", "psychCallback")
    private val memberStart = Regex("^  (?:(?:override|public|private|static|inline)\\s+)*(var|final|function)\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val caseLine = Regex("^      case\\s+((?:\"[^\"]*\"\\s*,?\\s*)+):(.*)$")
    private val word = Regex("[A-Za-z_][A-Za-z0-9_]*")

    val library: Library by lazy { parse(load("PsychApiLibrary.hxc")) }

    val supportedCalls: Set<String> by lazy { library.cases.flatMap { it.first }.toSet() }

    val builtinEventNames: Set<String> get() = library.builtinEvents.keys

    private fun load(name: String): String =
        PsychNativeLibrary::class.java.getResourceAsStream("/vconverter/$name")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Missing bundled library $name")

    private fun parse(source: String): Library {
        val lines = source.lines()
        val imports = lines.filter { it.startsWith("import ") }.map { it.removePrefix("import ").removeSuffix(";").trim() }
        val members = LinkedHashMap<String, Member>()
        var i = 0
        while (i < lines.size) {
            val match = memberStart.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val isField = match.groupValues[1] != "function"
            val start = i
            if (isField) {
                while (i < lines.size && !lines[i].trimEnd().endsWith(";")) i++
                i++
            } else {
                var depth = 0
                var opened = false
                while (i < lines.size) {
                    depth += braceDelta(lines[i])
                    if (lines[i].contains('{')) opened = true
                    i++
                    if (opened && depth <= 0) break
                }
            }
            members[match.groupValues[2]] = Member(match.groupValues[2], lines.subList(start, i).joinToString("\n"), isField)
        }
        val dispatch = members["dispatch"]?.text?.lines() ?: emptyList()
        val cases = mutableListOf<Pair<List<String>, String>>()
        var current: MutableList<String>? = null
        var labels: List<String> = emptyList()
        for (line in dispatch) {
            val m = caseLine.find(line)
            if (m != null) {
                current?.let { cases += labels to it.joinToString("\n") }
                labels = Regex("\"([^\"]*)\"").findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
                current = mutableListOf(line)
            } else if (current != null) {
                if (line.startsWith("    }") && !line.startsWith("      ")) {
                    cases += labels to current.joinToString("\n")
                    current = null
                } else {
                    current += line
                }
            }
        }
        current?.let { cases += labels to it.joinToString("\n") }
        return Library(imports, members, cases, builtinEvents(members["handleBuiltinEvent"]?.text ?: ""))
    }

    private fun builtinEvents(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var name: String? = null
        val body = mutableListOf<String>()
        for (line in text.lines()) {
            val m = Regex("^        case \"([^\"]+)\":\\s*$").find(line)
            if (m != null) {
                name?.let { result[it] = body.joinToString("\n") }
                name = m.groupValues[1]
                body.clear()
            } else if (name != null) {
                if (line.startsWith("      }")) {
                    result[name] = body.joinToString("\n")
                    name = null
                } else {
                    body += line
                }
            }
        }
        return result
    }

    private fun braceDelta(line: String): Int {
        var depth = 0
        var inString: Char? = null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inString != null) {
                if (c == '\\') i++ else if (c == inString) inString = null
            } else when (c) {
                '"', '\'' -> inString = c
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return depth
    }

    fun apiName(label: String): String {
        var name = label.replace("LuaSprite", "Sprite").replace("LuaText", "Text").replace("Lua", "")
        if (name.startsWith("lua") && name.length > 3) name = name.substring(3).replaceFirstChar { it.lowercaseChar() }
        return name
    }

    private class CallSite(val start: Int, val end: Int, val label: String, val args: String, val count: Int)

    private fun skipString(text: String, start: Int): Int {
        val quote = text[start]
        var i = start + 1
        while (i < text.length && text[i] != quote) {
            if (text[i] == '\\') i++
            i++
        }
        return i + 1
    }

    private fun callSites(text: String): List<CallSite> {
        val result = mutableListOf<CallSite>()
        var from = 0
        while (true) {
            val start = text.indexOf("psych(\"", from)
            if (start < 0) break
            if (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_' || text[start - 1] == '.')) {
                from = start + 6
                continue
            }
            val labelEnd = text.indexOf('"', start + 7)
            val label = text.substring(start + 7, labelEnd)
            var i = labelEnd + 1
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != ',') {
                from = labelEnd
                continue
            }
            i++
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != '[') {
                from = labelEnd
                continue
            }
            val open = i
            var depth = 0
            var commas = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '"' || c == '\'') {
                    i = skipString(text, i)
                    continue
                }
                if (c == '[' || c == '(' || c == '{') depth++
                if (c == ']' || c == ')' || c == '}') {
                    depth--
                    if (depth == 0) break
                }
                if (c == ',' && depth == 1) commas++
                i++
            }
            val close = i
            var j = close + 1
            while (j < text.length && text[j].isWhitespace()) j++
            if (j >= text.length || text[j] != ')') {
                from = close
                continue
            }
            val args = text.substring(open + 1, close)
            result += CallSite(start, j + 1, label, args, if (args.isBlank()) 0 else commas + 1)
            from = j + 1
        }
        return result
    }

    fun replaceIdentifiers(text: String, map: Map<String, String>): String {
        if (map.isEmpty()) return text
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' || c == '\'' -> {
                    val end = skipString(text, i).coerceAtMost(text.length)
                    sb.append(text, i, end)
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    val id = text.substring(i, j)
                    val prev = if (i > 0) text[i - 1] else ' '
                    val member = prev == '.' && !(i >= 2 && text[i - 2] == '.')
                    sb.append(if (!member && !prev.isDigit()) map[id] ?: id else id)
                    i = j
                }
                c.isDigit() -> {
                    var j = i
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_' || (text[j] == '.' && j + 1 < text.length && text[j + 1].isDigit()))) j++
                    sb.append(text, i, j)
                    i = j
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private val bareApi = setOf("debugPrint", "setVar", "getVar")

    fun embed(moduleText: String, noteTypes: String, siblings: List<String>, eventNames: Map<String, String> = emptyMap()): Embedded {
        val lib = library
        val caseFor = HashMap<String, String>()
        lib.cases.forEach { (labels, body) -> labels.forEach { if (it !in caseFor) caseFor[it] = body } }
        val callMax = HashMap<String, Int>()
        val names = LinkedHashMap<String, String>()
        var current = moduleText
        for (pass in 0 until 32) {
            val sites = callSites(current)
            if (sites.isEmpty()) break
            val out = StringBuilder()
            var last = 0
            sites.forEach { site ->
                callMax[site.label] = maxOf(callMax[site.label] ?: 0, site.count)
                out.append(current, last, site.start)
                val name = if (site.label in caseFor) names.getOrPut(site.label) {
                    var candidate = apiName(site.label)
                    while (candidate in names.values) candidate += "2"
                    candidate
                } else null
                out.append(if (name != null) "$name(${site.args.trim()})" else "null")
                last = site.end
            }
            out.append(current.substring(last))
            current = out.toString()
        }
        bareApi.forEach { name ->
            if (Regex("(?<![.\\w])$name\\s*\\(").containsMatchIn(moduleText) && !Regex("function\\s+$name\\b").containsMatchIn(moduleText)) {
                callMax[name] = maxOf(callMax[name] ?: 0, 8)
            }
        }
        val used = callMax.keys.filter { it in caseFor }
        used.forEach { label ->
            names.getOrPut(label) {
                var candidate = apiName(label)
                while (candidate in names.values) candidate += "2"
                candidate
            }
        }
        val usesVar = moduleText.contains("psychVar(")
        val wrapperNames = names.values.toSet() + if (usesVar) setOf("variableOf") else emptySet()
        val renames = lib.members.keys.filter { it in wrapperNames }.associateWith { it + "Impl" }
        val members = lib.members.mapKeys { (k, _) -> renames[k] ?: k }.mapValues { (_, m) ->
            Member(renames[m.name] ?: m.name, replaceIdentifiers(m.text, renames), m.isField)
        }

        val generated = StringBuilder()
        used.forEach { label ->
            val raw = replaceIdentifiers(caseFor.getValue(label), renames)
            val lines = raw.lines()
            val first = lines.first().substringAfter(':', "").trim()
            val rest = lines.drop(1).map { if (it.startsWith("        ")) it.substring(8) else it.trimStart() }
            val body = (listOf(first) + rest).filter { it.isNotBlank() }
            val bodyText = body.joinToString("\n")
            var arity = 0
            Regex("\\ba\\[(\\d+)\\]").findAll(bodyText).forEach { arity = maxOf(arity, it.groupValues[1].toInt() + 1) }
            Regex("\\barg\\(a,\\s*(\\d+)").findAll(bodyText).forEach { arity = maxOf(arity, it.groupValues[1].toInt() + 1) }
            val stripped = bodyText.replace(Regex("\\ba\\[\\d+\\]"), "").replace(Regex("\\barg\\(a,"), "")
            val whole = Regex("(?<![.\\w])a(?![\\w])").containsMatchIn(stripped)
            arity = maxOf(arity, callMax[label] ?: 0)
            val usesA = Regex("(?<![.\\w])a(?![\\w])").containsMatchIn(bodyText)
            val params = (0 until arity).joinToString(", ") { "?a$it:Dynamic" }
            generated.append("  function ${names.getValue(label)}($params):Dynamic\n  {\n")
            generated.append("    if (!ensureGame()) return null;\n    owner = this;\n")
            if (usesA) generated.append("    var a:Array<Dynamic> = [${(0 until arity).joinToString(", ") { "a$it" }}];\n")
            if (whole) generated.append("    while (a.length > 0 && a[a.length - 1] == null) a.pop();\n")
            generated.append("    try\n    {\n")
            body.forEach { generated.append("      ").append(it).append('\n') }
            generated.append("    }\n    catch (e:Dynamic) {}\n    return null;\n  }\n\n")
        }
        if (usesVar) {
            generated.append("  function variableOf(name:String):Dynamic\n  {\n    if (!ensureGame()) return null;\n    try\n    {\n      return variable(name);\n    }\n    catch (e:Dynamic)\n    {\n      return null;\n    }\n  }\n")
        }

        val newModule = current.replace("psychVar(", "variableOf(")

        val included = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        fun scan(text: String) {
            word.findAll(text).forEach { m ->
                val name = m.value
                if (name !in included && name !in skipped && members.containsKey(name)) {
                    included += name
                    queue += name
                }
            }
        }
        scan(newModule)
        scan(generated.toString())
        if (used.isNotEmpty()) scan("luaOwnObject ensureGame luaCleanup")
        while (queue.isNotEmpty()) scan(members.getValue(queue.removeFirst()).text)
        if ("noteTypeNames" in included && "luaInitNoteTypes" !in included) {
            included += "luaInitNoteTypes"
            scan(members.getValue("luaInitNoteTypes").text)
            while (queue.isNotEmpty()) scan(members.getValue(queue.removeFirst()).text)
        }
        if ("mustHitSection" in included) included += "luaTrackFocus"

        val body = StringBuilder()
        members.values.filter { it.isField && it.name in included }.forEach { member ->
            val text = when (member.name) {
                "LUA_SIBLINGS" -> "  var LUA_SIBLINGS:Array<String> = [${siblings.joinToString(", ") { LuaToHScript.hxString(it) }}];"
                "LUA_EVENT_NAMES" -> "  var LUA_EVENT_NAMES:Array<Dynamic> = [${eventNames.entries.joinToString(", ") { (id, name) -> "[${LuaToHScript.hxString(id)}, ${LuaToHScript.hxString(name)}]" }}];"
                else -> member.text
            }
            body.append(text).append('\n')
        }
        members.values.filter { !it.isField && it.name in included }.forEach { member ->
            body.append('\n').append(if (member.name == "luaInitNoteTypes") member.text.replace("__NOTE_TYPES__", noteTypes) else member.text).append('\n')
        }
        if (generated.isNotEmpty()) body.append('\n').append(generated.toString().trimEnd()).append('\n')
        val imports = lib.imports.filter { imp ->
            val simple = imp.substringAfterLast('.')
            Regex("\\b${Regex.escape(simple)}\\b").containsMatchIn(body.toString())
        }
        return Embedded(imports, body.toString().trimEnd('\n'), used.toSet() + (if (usesVar) setOf("psychVar") else emptySet()), included, newModule)
    }
}
