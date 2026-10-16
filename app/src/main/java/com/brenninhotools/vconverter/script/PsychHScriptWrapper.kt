package com.brenninhotools.vconverter.script

class PsychHScriptWrapper(
    private val className: String,
    private val moduleId: String,
    private val target: ScriptTarget,
    private val siblings: List<String> = emptyList(),
    private val noteTypes: String = "[]",
    private val eventNames: Map<String, String> = emptyMap(),
    private val ownEvent: String? = null
) {
    private enum class Kind { SPACE, IDENT, NUMBER, STRING, PUNCT }

    private data class Tok(val kind: Kind, val text: String, val line: Int)

    private val issues = mutableListOf<ScriptIssue>()

    companion object {
        private val importMap = mapOf(
            "states.PlayState" to "funkin.play.PlayState",
            "PlayState" to "funkin.play.PlayState",
            "backend.Paths" to "funkin.Paths",
            "Paths" to "funkin.Paths",
            "backend.Conductor" to "funkin.Conductor",
            "Conductor" to "funkin.Conductor"
        )
        private val droppedPackages = listOf(
            "backend.", "objects.", "psychlua.", "states.", "substates.", "shaders.", "cutscenes.", "options.", "debug.", "llua.", "sys.", "lime.app."
        )
        private val psychClasses = setOf(
            "BGSprite", "Note", "StrumNote", "Character", "HealthIcon", "CustomSubstate", "FunkinLua", "LuaUtils", "PsychCamera",
            "Alphabet", "Achievements", "StageData", "WeekData", "Song", "Section", "Mods", "CoolUtil", "MusicBeatState",
            "MusicBeatSubstate", "ClientPrefs", "Difficulty", "DialogueBoxPsych", "NoteSplash", "TypedAlphabet", "AttachedSprite",
            "AttachedText", "VideoSprite", "FunkinVideoSprite", "Countdown"
        )
        private val gameMembers = mapOf(
            "boyfriend" to "currentStage.getBoyfriend()",
            "boyfriendGroup" to "currentStage.getBoyfriend()",
            "dad" to "currentStage.getDad()",
            "dadGroup" to "currentStage.getDad()",
            "gf" to "currentStage.getGirlfriend()",
            "gfGroup" to "currentStage.getGirlfriend()",
            "camFollow" to "cameraFollowPoint",
            "defaultCamZoom" to "currentCameraZoom",
            "scoreTxt" to "scoreText",
            "playerStrums" to "playerStrumline.strumlineNotes",
            "opponentStrums" to "opponentStrumline.strumlineNotes",
            "inCutscene" to "isInCutscene",
            "curStage" to "currentStageId"
        )
        private val unsupportedMembers = setOf(
            "strumLineNotes", "notes", "unspawnNotes", "songMisses", "isCameraOnForcedPos", "camZooming", "variables",
            "modchartSprites", "modchartTexts", "luaArray", "hscriptArray", "timeBar", "timeTxt", "botplayTxt", "ratingName",
            "ratingPercent", "dadMap", "boyfriendMap", "gfMap", "cameraSpeed", "songSpeed", "camOther"
        )
        private val conductorMembers = mapOf(
            "songPosition" to "songPosition",
            "crochet" to "beatLengthMs",
            "stepCrochet" to "stepLengthMs",
            "bpm" to "bpm",
            "curBeat" to "currentBeat",
            "curStep" to "currentStep"
        )
        private val bareVariables = mapOf(
            "curBeat" to "Conductor.instance.currentBeat",
            "curStep" to "Conductor.instance.currentStep",
            "curSection" to "Conductor.instance.currentMeasure",
            "curDecBeat" to "Conductor.instance.currentBeatTime",
            "curDecStep" to "Conductor.instance.currentStepTime",
            "game" to "PlayState.instance",
            "boyfriend" to "PlayState.instance.currentStage.getBoyfriend()",
            "dad" to "PlayState.instance.currentStage.getDad()",
            "gf" to "PlayState.instance.currentStage.getGirlfriend()"
        )
        private val prefs = mapOf(
            "downScroll" to "Preferences.downscroll",
            "flashing" to "Preferences.flashingLights",
            "framerate" to "Preferences.framerate",
            "middleScroll" to "false",
            "lowQuality" to "false",
            "ghostTapping" to "true",
            "antialiasing" to "true",
            "shaders" to "true",
            "hideHud" to "false"
        )
        private val helperNames = setOf("debugPrint", "setVar", "getVar", "removeVar", "add", "insert", "remove", "addBehindGF", "addBehindDad", "addBehindBF")
    }

    private fun tokenize(source: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        var line = 1
        while (i < source.length) {
            val c = source[i]
            val start = i
            when {
                c == '\n' -> {
                    line++
                    i++
                    out += Tok(Kind.SPACE, "\n", line)
                }
                c.isWhitespace() -> {
                    while (i < source.length && source[i].isWhitespace() && source[i] != '\n') i++
                    out += Tok(Kind.SPACE, source.substring(start, i).replace("\t", "  ").replace("\r", ""), line)
                }
                c == '/' && source.getOrNull(i + 1) == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                c == '/' && source.getOrNull(i + 1) == '*' -> {
                    val end = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                    line += source.substring(i, end).count { it == '\n' }
                    i = end
                    out += Tok(Kind.SPACE, " ", line)
                }
                c == '"' || c == '\'' -> {
                    i++
                    while (i < source.length && source[i] != c) {
                        if (source[i] == '\\') i++
                        if (i < source.length && source[i] == '\n') line++
                        i++
                    }
                    i++
                    out += Tok(Kind.STRING, source.substring(start, minOf(i, source.length)), line)
                }
                c.isLetter() || c == '_' -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    out += Tok(Kind.IDENT, source.substring(start, i), line)
                }
                c.isDigit() -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '.' && source.getOrNull(i + 1)?.isDigit() == true)) i++
                    out += Tok(Kind.NUMBER, source.substring(start, i), line)
                }
                else -> {
                    i++
                    out += Tok(Kind.PUNCT, c.toString(), line)
                }
            }
        }
        return out
    }

    private class Segment(val kind: String, val name: String, val head: List<Tok>, val body: List<Tok>, val line: Int)

    private fun segments(tokens: List<Tok>): List<Segment> {
        val result = mutableListOf<Segment>()
        var i = 0
        fun skipSpace() {
            while (i < tokens.size && tokens[i].kind == Kind.SPACE) i++
        }
        fun untilSemicolonOrBlock(start: Int): Int {
            var depth = 0
            var j = start
            while (j < tokens.size) {
                val t = tokens[j]
                if (t.kind == Kind.PUNCT) {
                    when (t.text) {
                        "{", "(", "[" -> depth++
                        "}", ")", "]" -> {
                            depth--
                            if (depth == 0 && t.text == "}") {
                                var k = j + 1
                                while (k < tokens.size && tokens[k].kind == Kind.SPACE) k++
                                val next = tokens.getOrNull(k)
                                if (next == null || !(next.kind == Kind.IDENT && (next.text == "else" || next.text == "catch")) && !(next.kind == Kind.PUNCT && (next.text == ";" || next.text == ")" || next.text == ","))) {
                                    return j + 1
                                }
                            }
                        }
                        ";" -> if (depth == 0) return j + 1
                    }
                }
                j++
            }
            return tokens.size
        }
        while (true) {
            skipSpace()
            if (i >= tokens.size) break
            val t = tokens[i]
            val line = t.line
            if (t.kind == Kind.IDENT && (t.text == "import" || t.text == "using" || t.text == "package")) {
                val end = untilSemicolonOrBlock(i)
                result += Segment(t.text, "", tokens.subList(i, end), emptyList(), line)
                i = end
                continue
            }
            if (t.kind == Kind.IDENT && t.text == "function") {
                var j = i + 1
                while (j < tokens.size && tokens[j].kind == Kind.SPACE) j++
                if (j < tokens.size && tokens[j].kind == Kind.IDENT) {
                    val name = tokens[j].text
                    var k = j + 1
                    while (k < tokens.size && !(tokens[k].kind == Kind.PUNCT && tokens[k].text == "{")) k++
                    val head = tokens.subList(j + 1, k)
                    var depth = 0
                    var end = k
                    while (end < tokens.size) {
                        val tk = tokens[end]
                        if (tk.kind == Kind.PUNCT && tk.text == "{") depth++
                        if (tk.kind == Kind.PUNCT && tk.text == "}") {
                            depth--
                            if (depth == 0) break
                        }
                        end++
                    }
                    result += Segment("function", name, head, tokens.subList(minOf(k + 1, tokens.size), minOf(end, tokens.size)), line)
                    i = minOf(end + 1, tokens.size)
                    continue
                }
            }
            if (t.kind == Kind.IDENT && (t.text == "var" || t.text == "final")) {
                val end = untilSemicolonOrBlock(i)
                var j = i + 1
                while (j < end && tokens[j].kind == Kind.SPACE) j++
                val name = tokens.getOrNull(j)?.takeIf { it.kind == Kind.IDENT }?.text ?: ""
                val eq = (j until end).firstOrNull { tokens[it].kind == Kind.PUNCT && tokens[it].text == "=" }
                val initializer = if (eq != null) tokens.subList(eq + 1, end).dropLastWhile { it.kind == Kind.SPACE || (it.kind == Kind.PUNCT && it.text == ";") } else emptyList()
                result += Segment("var", name, emptyList(), initializer, line)
                i = end
                continue
            }
            val end = untilSemicolonOrBlock(i)
            result += Segment("statement", "", emptyList(), tokens.subList(i, end), line)
            i = end
        }
        return result
    }

    fun convert(source: String): GeneratedScript {
        val segs = segments(tokenize(source))
        val functions = segs.filter { it.kind == "function" }.map { it.name }.toSet()
        val fields = segs.filter { it.kind == "var" && it.name.isNotEmpty() }.map { it.name }.toSet()
        val imports = linkedSetOf("funkin.Conductor", "funkin.Paths", "funkin.Preferences")

        segs.filter { it.kind == "import" || it.kind == "using" }.forEach { seg ->
            val path = seg.head.filter { it.kind != Kind.SPACE }.drop(1).takeWhile { !(it.kind == Kind.PUNCT && it.text == ";") }.joinToString("") { it.text }
            if (seg.kind == "using") return@forEach
            val mapped = importMap[path]
            when {
                mapped != null -> imports += mapped
                droppedPackages.any { path.startsWith(it) } -> issue(seg.line, "Import '$path' is Psych-specific and was dropped.")
                path.isNotEmpty() -> imports += path
            }
        }

        val boot = mutableListOf<String>()
        segs.forEach { seg ->
            when (seg.kind) {
                "var" -> if (seg.body.isNotEmpty()) boot += "h_${seg.name} = ${rewrite(seg.body, functions, fields).trim()};"
                "statement" -> boot += rewrite(seg.body, functions, fields).trim().lines().map { it.trimEnd() }.filter { it.isNotBlank() }
                else -> Unit
            }
        }

        val tail = mutableListOf<String>()
        segs.filter { it.kind == "function" }.forEach { seg ->
            tail += ""
            val head = rewrite(seg.head, functions, fields).trim()
            tail += "function h_${seg.name}$head"
            tail += "{"
            val bodyLines = rewrite(seg.body, functions, fields).trimEnd().lines().filter { it.isNotBlank() }
            val indent = bodyLines.minOfOrNull { line -> line.length - line.trimStart().length } ?: 0
            bodyLines.forEach { tail += "  " + it.drop(indent).trimEnd() }
            tail += "}"
        }

        tail.addAll(0, usedHelpers((boot + tail).joinToString("\n")))

        val callbacks = LinkedHashMap<String, CallbackRef>()
        val arities = segs.filter { it.kind == "function" }.associate { seg ->
            val inner = seg.head.joinToString("") { it.text }.substringAfter('(').substringBeforeLast(')')
            seg.name to if (inner.isBlank()) 0 else inner.split(',').size
        }
        PsychApi.callbacks.filter { it in functions }.forEach { callbacks[it] = CallbackRef(arities[it], true) }

        val text = ModuleWriter.write(
            ModuleSpec(
                className = className,
                moduleId = moduleId,
                siblings = siblings,
                noteTypes = noteTypes,
                eventNames = eventNames,
                ownEvent = ownEvent,
                target = target,
                prefix = "h_",
                fields = fields.map { "h_$it" }.sorted(),
                bootLines = boot,
                callbacks = callbacks,
                dispatch = arities.mapValues { it.value },
                setters = fields.toList(),
                noteObjects = true,
                imports = imports.toList(),
                tailLines = tail
            )
        )
        if (functions.any { it in setOf("goodNoteHit", "opponentNoteHit", "noteMiss", "onSpawnNote") }) {
            issue(0, "Note callbacks receive V-Slice NoteSprite objects; fields like note.noteData or note.noteType differ from Psych's Note and may need edits.")
        }
        return GeneratedScript(className, moduleId, text, issues.sortedBy { it.line }, callbacks.keys.toList(), emptySet())
    }

    private fun issue(line: Int, message: String) {
        if (issues.none { it.line == line && it.message == message }) issues += ScriptIssue(line, message)
    }

    private fun rewrite(tokens: List<Tok>, functions: Set<String>, fields: Set<String>): String {
        val sb = StringBuilder()
        var i = 0
        fun prevSignificant(index: Int): Tok? {
            var j = index - 1
            while (j >= 0 && tokens[j].kind == Kind.SPACE) j--
            return tokens.getOrNull(j)
        }
        fun nextSignificant(index: Int): Int {
            var j = index + 1
            while (j < tokens.size && tokens[j].kind == Kind.SPACE) j++
            return j
        }
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.kind != Kind.IDENT) {
                sb.append(t.text)
                i++
                continue
            }
            val prev = prevSignificant(i)
            val afterDot = prev?.kind == Kind.PUNCT && prev.text == "."
            if (afterDot) {
                sb.append(t.text)
                i++
                continue
            }
            val n1 = nextSignificant(i)
            val hasMember = tokens.getOrNull(n1)?.let { it.kind == Kind.PUNCT && it.text == "." } == true
            val n2 = nextSignificant(n1)
            val member = if (hasMember) tokens.getOrNull(n2)?.takeIf { it.kind == Kind.IDENT }?.text else null

            if ((t.text == "game" || t.text == "PlayState") && member != null) {
                var base = "PlayState.instance"
                var consumed = n2
                if (t.text == "PlayState") {
                    if (member != "instance") {
                        sb.append(t.text)
                        i++
                        continue
                    }
                    val n3 = nextSignificant(n2)
                    val n4 = nextSignificant(n3)
                    val inner = if (tokens.getOrNull(n3)?.let { it.kind == Kind.PUNCT && it.text == "." } == true) tokens.getOrNull(n4)?.takeIf { it.kind == Kind.IDENT }?.text else null
                    if (inner == null) {
                        sb.append("PlayState.instance")
                        i = n2 + 1
                        continue
                    }
                    base = "PlayState.instance"
                    consumed = n4
                    appendMember(sb, base, inner, t.line)
                    i = consumed + 1
                    continue
                }
                appendMember(sb, base, member, t.line)
                i = consumed + 1
                continue
            }
            if (t.text == "Conductor" && member != null && member in conductorMembers) {
                sb.append("Conductor.instance.").append(conductorMembers.getValue(member))
                i = n2 + 1
                continue
            }
            if (t.text == "ClientPrefs") {
                var j = i
                val parts = mutableListOf<String>()
                var k = nextSignificant(j)
                while (tokens.getOrNull(k)?.let { it.kind == Kind.PUNCT && it.text == "." } == true) {
                    val name = tokens.getOrNull(nextSignificant(k))?.takeIf { it.kind == Kind.IDENT } ?: break
                    parts += name.text
                    j = nextSignificant(k)
                    k = nextSignificant(j)
                }
                val field = parts.lastOrNull()
                sb.append(prefs[field] ?: "null")
                if (field !in prefs) issue(t.line, "ClientPrefs.${parts.joinToString(".")} has no V-Slice equivalent and became null.")
                i = j + 1
                continue
            }
            when {
                t.text in functions -> sb.append("h_").append(t.text)
                t.text in fields -> sb.append("h_").append(t.text)
                t.text in bareVariables && !(tokens.getOrNull(n1)?.let { it.kind == Kind.PUNCT && it.text == ":" } == true) -> sb.append(bareVariables.getValue(t.text))
                t.text in psychClasses -> {
                    issue(t.line, "Psych class '${t.text}' does not exist in V-Slice.")
                    sb.append(t.text)
                }
                else -> sb.append(t.text)
            }
            i++
        }
        return sb.toString()
    }

    private fun appendMember(sb: StringBuilder, base: String, member: String, line: Int) {
        val mapped = gameMembers[member]
        if (member in unsupportedMembers) issue(line, "PlayState.$member has no direct V-Slice equivalent.")
        sb.append(base).append('.').append(mapped ?: member)
    }

    private fun usedHelpers(code: String): List<String> {
        val blocks = LinkedHashMap<String, String>()
        var name: String? = null
        val current = StringBuilder()
        helperLines().forEach { line ->
            val m = Regex("^function ([A-Za-z_][A-Za-z0-9_]*)").find(line)
            if (m != null) {
                name?.let { blocks[it] = current.toString() }
                name = m.groupValues[1]
                current.setLength(0)
            }
            if (name != null) current.append(line).append('\n')
        }
        name?.let { blocks[it] = current.toString() }
        val included = LinkedHashSet<String>()
        val queue = ArrayDeque(listOf(code))
        while (queue.isNotEmpty()) {
            val text = queue.removeFirst()
            blocks.keys.filter { it !in included && Regex("\\b$it\\(").containsMatchIn(text) }.forEach {
                included += it
                queue += blocks.getValue(it)
            }
        }
        return blocks.filterKeys { it in included }.values.flatMap { (it.trimEnd() + "\n").lines() }
    }

    private fun helperLines(): List<String> = """
function removeVar(name:String):Dynamic
{
  return setVar(name, null);
}

function add(obj:Dynamic):Dynamic
{
  return psychAdd(obj, true);
}

function insert(position:Int, obj:Dynamic):Dynamic
{
  return psychAdd(obj, false);
}

function addBehindGF(obj:Dynamic):Dynamic
{
  return psychAdd(obj, false);
}

function addBehindDad(obj:Dynamic):Dynamic
{
  return psychAdd(obj, false);
}

function addBehindBF(obj:Dynamic):Dynamic
{
  return psychAdd(obj, false);
}

function remove(obj:Dynamic, ?splice:Bool):Dynamic
{
  var game:Dynamic = PlayState.instance;
  if (game == null || obj == null) return obj;
  if (game.currentStage != null) game.currentStage.remove(obj, true);
  game.remove(obj, true);
  return obj;
}

function psychAdd(obj:Dynamic, front:Bool):Dynamic
{
  var game:Dynamic = PlayState.instance;
  if (game == null || obj == null) return obj;
  var stage:Dynamic = game.currentStage;
  if (stage == null)
  {
    game.add(obj);
    return obj;
  }
  var z:Int = front ? 0 : 2147483647;
  for (c in [stage.getBoyfriend(), stage.getDad(), stage.getGirlfriend()])
  {
    if (c == null) continue;
    if (front && c.zIndex > z) z = c.zIndex;
    if (!front && c.zIndex < z) z = c.zIndex;
  }
  if (z == 2147483647) z = 0;
  obj.zIndex = front ? z + 1 : z - 1;
  stage.add(obj);
  stage.refresh();
  return obj;
}
""".trim().lines()
}
