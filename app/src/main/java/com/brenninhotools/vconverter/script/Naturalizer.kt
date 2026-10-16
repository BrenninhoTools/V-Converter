package com.brenninhotools.vconverter.script

object Naturalizer {
    private val internal = mapOf(
        "luaGame" to "activeGame",
        "luaClosed" to "closed",
        "luaActive" to "isActive",
        "luaBoot" to "boot",
        "luaInScope" to "inScope",
        "psychClose" to "closeScript",
        "psychCallback" to "callback",
        "LUA_SIBLINGS" to "SIBLINGS",
        "LUA_EVENT_NAMES" to "EVENT_NAMES",
        "luaSiblingModules" to "siblingModules",
        "luaOwnObject" to "ownObject",
        "luaCleanup" to "cleanup",
        "luaTrackFocus" to "trackFocus",
        "luaInitNoteTypes" to "initNoteTypes",
        "luaEventName" to "eventNameOf",
        "psychEventInfo" to "eventInfo",
        "psychAdd" to "addRelative",
        "Function_Stop" to "CANCEL",
        "Function_StopLua" to "CANCEL",
        "Function_StopHScript" to "CANCEL",
        "Function_StopAll" to "CANCEL",
        "Function_Continue" to "CONTINUE",
        "luaTrue" to "truthy",
        "luaIsArray" to "isArray",
        "luaIsTable" to "isTable",
        "luaMod" to "modulo",
        "luaGet" to "getAt",
        "luaSet" to "setAt",
        "luaLen" to "lengthOf",
        "luaKeys" to "keysOf",
        "luaPack" to "packArgs",
        "luaTableFrom" to "tableFrom",
        "luaTrim" to "trimText",
        "luaHex" to "hexOf",
        "luaToString" to "asString",
        "luaToNumber" to "asNumber",
        "luaType" to "typeOf",
        "luaPcall" to "tryCall",
        "luaAssert" to "check",
        "luaInsert" to "insertAt",
        "luaRemove" to "removeAt",
        "luaConcatTable" to "joinText",
        "luaSort" to "sortTable",
        "luaSub" to "subText",
        "luaRep" to "repeatText",
        "luaReverse" to "reverseText",
        "luaChar" to "charText",
        "luaFormat" to "formatText",
        "luaFind" to "findText",
        "luaMatch" to "matchText",
        "luaGsub" to "replaceText",
        "luaGmatch" to "matchAll",
        "luaPatternToRegex" to "patternRegex",
        "luaSelfCall" to "callMethodOf"
    )

    private val moduleMembers = setOf(
        "active", "moduleId", "priority", "state", "toString", "set_active", "set_priority", "scriptCall", "scriptGet", "scriptSet",
        "onBeatHit", "onCapsuleSelected", "onCharacterConfirm", "onCharacterDeselect", "onCharacterSelect", "onCountdownEnd",
        "onCountdownStart", "onCountdownStep", "onCreate", "onDestroy", "onDifficultySwitch", "onFocusGained", "onFocusLost",
        "onFreeplayClose", "onFreeplayIntroDone", "onFreeplayOutro", "onGameOver", "onNoteGhostMiss", "onNoteHit", "onNoteHoldDrop",
        "onNoteIncoming", "onNoteMiss", "onPause", "onResume", "onScriptEvent", "onSongEnd", "onSongEvent", "onSongLoaded",
        "onSongRetry", "onSongSelected", "onSongStart", "onStateChangeBegin", "onStateChangeEnd", "onStateCreate", "onStepHit",
        "onSubStateCloseBegin", "onSubStateCloseEnd", "onSubStateOpenBegin", "onSubStateOpenEnd", "onUpdate", "onAdd"
    )

    private val keywords = setOf(
        "abstract", "break", "case", "cast", "catch", "class", "continue", "default", "do", "dynamic", "else", "enum", "extends",
        "extern", "false", "final", "for", "function", "if", "implements", "import", "in", "inline", "interface", "macro", "new",
        "null", "operator", "overload", "override", "package", "private", "public", "return", "static", "super", "switch", "this",
        "throw", "true", "try", "typedef", "untyped", "using", "var", "while", "trace", "Std", "Math", "Reflect", "Type", "FlxG",
        "PlayState", "Conductor", "Paths", "Preferences", "StringMap", "Array", "String", "Int", "Float", "Bool", "Dynamic", "Void"
    )

    private val classMember = Regex("^  (?:(?:override|public|private|static|inline)\\s+)*(var|function)\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.MULTILINE)

    fun module(text: String): String {
        var t = PsychNativeLibrary.replaceIdentifiers(text, internal)
        internal.forEach { (from, to) ->
            if (from.startsWith("lua") || from.startsWith("psych")) t = t.replace("\"$from\"", "\"$to\"")
        }
        val leftovers = Regex("\\blua[A-Z][A-Za-z0-9_]*").findAll(blankStrings(t)).map { it.value }.toSet()
        t = PsychNativeLibrary.replaceIdentifiers(t, leftovers.associateWith { it.removePrefix("lua").replaceFirstChar { c -> c.lowercaseChar() } })

        val declared = classMember.findAll(t).map { it.groupValues[2] }.toList()
        val prefixed = declared.filter { isPrefixed(it) }.distinct()
        val reserved = (declared.filter { !isPrefixed(it) } + moduleMembers + keywords).toMutableSet()
        val renames = LinkedHashMap<String, String>()
        prefixed.forEach { name ->
            val base = name.substring(2)
            var candidate = if (base in moduleMembers || base in reserved) base + "Script" else base
            var n = 2
            while (candidate in reserved) candidate = base + "Script" + n++
            reserved += candidate
            renames[name] = candidate
        }
        t = PsychNativeLibrary.replaceIdentifiers(t, renames)

        val functions = classMember.findAll(t).filter { it.groupValues[1] == "function" }.map { it.groupValues[2] }.toSet()
        val locals = HashSet<String>()
        Regex("\\n {3,}.*?\\bvar\\s+([A-Za-z_][A-Za-z0-9_]*)").findAll(t).forEach { locals += it.groupValues[1] }
        Regex("function\\s*[A-Za-z0-9_]*\\s*\\(([^)]*)\\)").findAll(t).forEach { m ->
            m.groupValues[1].split(',').forEach { p ->
                val name = p.trim().removePrefix("?").substringBefore(':').substringBefore('=').trim()
                if (name.isNotEmpty()) locals += name
            }
        }
        (functions intersect locals).filter { it !in renames.values && it !in moduleMembers }.forEach { name ->
            var target = name + "Fn"
            while (target in functions) target += "_"
            t = t.replace(Regex("(?<![.\\w])${Regex.escape(name)}(?=\\s*\\()"), target)
        }
        return addConstants(t)
    }

    private fun blankStrings(text: String): String {
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
                for (k in i until minOf(j + 1, sb.length)) sb.setCharAt(k, ' ')
                i = j + 1
            } else {
                i++
            }
        }
        return sb.toString()
    }

    private fun isPrefixed(name: String) = name.length > 2 && (name.startsWith("g_") || name.startsWith("l_") || name.startsWith("h_"))

    fun addConstants(text: String): String {
        val lines = text.lines().toMutableList()
        val classLine = lines.indexOfFirst { it.startsWith("class ") && it.contains("extends Module") }.takeIf { it >= 0 }
            ?: lines.indexOfFirst { it.startsWith("class ") }.takeIf { it >= 0 } ?: return text
        val open = (classLine until lines.size).firstOrNull { lines[it].trim() == "{" } ?: return text
        val add = mutableListOf<String>()
        if (Regex("\\bCONTINUE\\b").containsMatchIn(text) && !text.contains("var CONTINUE")) add += "  var CONTINUE:String = \"continue\";"
        if (Regex("\\bCANCEL\\b").containsMatchIn(text) && !text.contains("var CANCEL")) add += "  var CANCEL:String = \"cancel\";"
        if (add.isEmpty()) return text
        lines.addAll(open + 1, add)
        return lines.joinToString("\n")
    }
}
