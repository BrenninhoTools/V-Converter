package com.brenninhotools.vconverter.script

object PsychNativeLibrary {
    class Member(val name: String, val text: String, val isField: Boolean)

    class Library(
        val imports: List<String>,
        val members: Map<String, Member>,
        val cases: List<Pair<List<String>, String>>,
        val builtinEvents: Map<String, String>
    )

    class Embedded(val imports: List<String>, val members: String, val usedCalls: Set<String>, val included: Set<String>)

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

    fun embed(moduleText: String, noteTypes: String, siblings: List<String>, eventNames: Map<String, String> = emptyMap()): Embedded {
        val lib = library
        val calls = Regex("psych\\(\"([^\"]+)\"").findAll(moduleText).map { it.groupValues[1] }.toSet()
        val usesVar = moduleText.contains("psychVar(")
        val selected = lib.cases.filter { (labels, _) -> labels.any { it in calls } }
        val generated = StringBuilder()
        if (selected.isNotEmpty() || calls.isNotEmpty()) {
            generated.append("  function psych(name:String, a:Array<Dynamic>):Dynamic\n  {\n")
            generated.append("    if (!ensureGame()) return null;\n    owner = this;\n    if (a == null) a = [];\n    try\n    {\n      switch (name)\n      {\n")
            selected.forEach { (_, body) -> generated.append(body.lines().joinToString("\n") { "  $it" }).append('\n') }
            generated.append("      }\n    }\n    catch (e:Dynamic) {}\n    return null;\n  }\n")
        }
        if (usesVar) {
            generated.append("\n  function psychVar(name:String):Dynamic\n  {\n    if (!ensureGame()) return null;\n    try\n    {\n      return variable(name);\n    }\n    catch (e:Dynamic)\n    {\n      return null;\n    }\n  }\n")
        }
        val included = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        fun scan(text: String) {
            word.findAll(text).forEach { m ->
                val name = m.value
                if (name !in included && name !in skipped && lib.members.containsKey(name)) {
                    included += name
                    queue += name
                }
            }
        }
        scan(moduleText)
        scan(generated.toString())
        if (selected.isNotEmpty()) scan("luaOwnObject ensureGame luaCleanup")
        while (queue.isNotEmpty()) scan(lib.members.getValue(queue.removeFirst()).text)
        if ("noteTypeNames" in included && "luaInitNoteTypes" !in included) {
            included += "luaInitNoteTypes"
            scan(lib.members.getValue("luaInitNoteTypes").text)
            while (queue.isNotEmpty()) scan(lib.members.getValue(queue.removeFirst()).text)
        }
        if ("mustHitSection" in included) included += "luaTrackFocus"

        val body = StringBuilder()
        lib.members.values.filter { it.isField && it.name in included }.forEach { member ->
            val text = when (member.name) {
                "LUA_SIBLINGS" -> "  var LUA_SIBLINGS:Array<String> = [${siblings.joinToString(", ") { LuaToHScript.hxString(it) }}];"
                "LUA_EVENT_NAMES" -> "  var LUA_EVENT_NAMES:Array<Dynamic> = [${eventNames.entries.joinToString(", ") { (id, name) -> "[${LuaToHScript.hxString(id)}, ${LuaToHScript.hxString(name)}]" }}];"
                else -> member.text
            }
            body.append(text).append('\n')
        }
        lib.members.values.filter { !it.isField && it.name in included }.forEach { member ->
            body.append('\n').append(if (member.name == "luaInitNoteTypes") member.text.replace("__NOTE_TYPES__", noteTypes) else member.text).append('\n')
        }
        if (generated.isNotEmpty()) body.append('\n').append(generated)
        val allText = moduleText + body
        val imports = lib.imports.filter { imp ->
            val simple = imp.substringAfterLast('.')
            Regex("\\b${Regex.escape(simple)}\\b").containsMatchIn(body.toString())
        }
        return Embedded(imports, body.toString().trimEnd('\n'), selected.flatMap { it.first }.filter { it in calls }.toSet() + (if (allText.contains("psychVar(")) setOf("psychVar") else emptySet()), included)
    }
}
