package com.brenninhotools.vconverter.fpsplus

import com.brenninhotools.vconverter.script.Helpers
import com.brenninhotools.vconverter.script.LuaTranspiler
import com.brenninhotools.vconverter.script.PsychApi
import com.brenninhotools.vconverter.script.ScriptIssue
import com.brenninhotools.vconverter.script.hx.HxKind
import com.brenninhotools.vconverter.script.hx.HxSource
import com.brenninhotools.vconverter.script.lua.Expr
import com.brenninhotools.vconverter.script.lua.TableField

class FpsParts(
    val imports: Set<String>,
    val fields: List<String>,
    val boot: List<String>,
    val methods: List<String>,
    val hooks: Map<String, List<String>>,
    val issues: List<ScriptIssue>,
    val callbacks: List<String>,
    val handlesEvents: Boolean
) {
    val isEmpty: Boolean get() = fields.isEmpty() && boot.isEmpty() && methods.isEmpty() && hooks.isEmpty()

    companion object {
        fun empty(issues: List<ScriptIssue> = emptyList()) = FpsParts(emptySet(), emptyList(), emptyList(), emptyList(), emptyMap(), issues, emptyList(), false)
    }
}

object FpsClass {
    val hookSignatures = linkedMapOf(
        "create" to "",
        "postCreate" to "",
        "update" to "elapsed:Float",
        "beat" to "curBeat:Int",
        "countdownBeat" to "curBeat:Int",
        "step" to "curStep:Int",
        "songStart" to "",
        "songEnd" to "",
        "pause" to "",
        "unpause" to "",
        "gameOverStart" to "",
        "exit" to "",
        "noteHit" to "character:Character, note:Note",
        "noteMiss" to "direction:Int, countedMiss:Bool"
    )

    val memberNames = hookSignatures.keys + setOf(
        "playstate", "boyfriend", "dad", "gf", "pauseUpdate", "gameOverLoop", "gameOverEnd", "gameOverUpdate", "toString",
        "addToBackground", "removeFromBackground", "addToGf", "removeFromGf", "addToMiddle", "removeFromMiddle", "addToCharacter",
        "removeFromCharacter", "addToForeground", "removeFromForeground", "addToOverlay", "removeFromOverlay", "addToHud", "removeHud",
        "addGeneric", "removeGeneric", "addGenericSubstate", "removeGenericSubstate", "name", "startingZoom", "uiSkin", "uiType",
        "extraData", "events", "instantStart", "bfStart", "dadStart", "gfStart", "addEvent", "addExtraData", "new", "super"
    )

    private fun argNames(signature: String) = signature.split(',').map { it.substringBefore(':').trim() }.filter { it.isNotEmpty() }.joinToString(", ")

    fun write(
        className: String,
        base: String,
        parts: List<FpsParts>,
        constructor: List<String> = emptyList(),
        createPrefix: List<String> = emptyList(),
        imports: Set<String> = emptySet()
    ): String {
        val out = StringBuilder()
        val allImports = (imports + parts.flatMap { it.imports }).distinct().sorted()
        allImports.forEach { out.append("import ").append(it).append(";\n") }
        if (allImports.isNotEmpty()) out.append('\n')
        out.append("class ").append(className).append(" extends ").append(base).append("\n{\n")
        val fields = parts.flatMap { it.fields }.distinct()
        fields.forEach { out.append("  ").append(it).append('\n') }
        if (fields.isNotEmpty()) out.append('\n')
        out.append("  public function new()\n  {\n    super();\n")
        constructor.forEach { out.append("    ").append(it).append('\n') }
        out.append("  }\n")
        val hooks = LinkedHashMap<String, MutableList<String>>()
        val boot = parts.flatMap { it.boot }
        if (createPrefix.isNotEmpty() || boot.isNotEmpty()) hooks.getOrPut("create") { mutableListOf() }.apply { addAll(createPrefix); addAll(boot) }
        parts.forEach { p -> p.hooks.forEach { (name, lines) -> hooks.getOrPut(name) { mutableListOf() }.addAll(lines) } }
        hookSignatures.forEach { (name, signature) ->
            val lines = hooks[name] ?: return@forEach
            if (lines.isEmpty()) return@forEach
            out.append("\n  override function ").append(name).append('(').append(signature).append(")\n  {\n")
            out.append("    super.").append(name).append('(').append(argNames(signature)).append(");\n")
            lines.forEach { line -> line.lines().forEach { out.append("    ").append(it).append('\n') } }
            out.append("  }\n")
        }
        parts.flatMap { it.methods }.forEach { method ->
            out.append('\n')
            method.trimEnd().lines().forEach { out.append("  ").append(it).append('\n') }
        }
        out.append("}\n")
        return out.toString().replace(Regex("\n{3,}"), "\n\n")
    }

    fun polymodSafe(text: String): String = text
        .replace("Reflect.getClassNameOf(t) == \"Unknown\"", "ReflectUtil.getClassNameOf(t) == null")
        .replace("Reflect.getClassNameOf(replacement) == \"Unknown\"", "ReflectUtil.getClassNameOf(replacement) == null")
        .replace(Regex("(?<![\\w.])Reflect\\.field\\("), "ReflectUtil.getField(")
        .replace(Regex("(?<![\\w.])Reflect\\.fields\\("), "ReflectUtil.getFieldsOf(")
        .replace(Regex("(?<![\\w.])Reflect\\."), "ReflectUtil.")
        .replace(Regex("(?<![\\w.])Type\\.resolveClass\\("), "ReflectUtil.resolveClass(")
        .replace(Regex("(?<![\\w.])Type\\.createInstance\\("), "ReflectUtil.createInstance(")
        .replace(Regex("(?<![\\w.])Type\\.getClassFields\\("), "ReflectUtil.getClassFields(")
        .replace(Regex("(?<![\\w.])StringTools\\.startsWith\\("), "ScriptingUtil.startsWith(")
        .replace(Regex("(?<![\\w.])StringTools\\.endsWith\\("), "ScriptingUtil.endsWith(")
        .replace(Regex("(?<![\\w.])StringTools\\.contains\\("), "ScriptingUtil.contains(")
}

private object FpsCallbacks {
    const val LUA_NOTE = "playstate.notes.members.indexOf(note), note.direction, note.type, note.isSustainNote"

    class Hook(val hook: String, val condition: String?, val args: List<String>)

    fun lua(name: String): Hook? = when (name) {
        "onCreate" -> Hook("create", null, emptyList())
        "onCreatePost" -> Hook("postCreate", null, emptyList())
        "onUpdate" -> Hook("update", null, listOf("elapsed"))
        "onUpdatePost" -> Hook("update", null, listOf("elapsed"))
        "onBeatHit" -> Hook("beat", null, emptyList())
        "onSectionHit" -> Hook("beat", "curBeat % 4 == 0", emptyList())
        "onStepHit" -> Hook("step", null, emptyList())
        "onCountdownTick" -> Hook("countdownBeat", "curBeat >= 0", listOf("curBeat"))
        "onSongStart" -> Hook("songStart", null, emptyList())
        "onEndSong" -> Hook("songEnd", null, emptyList())
        "onPause" -> Hook("pause", null, emptyList())
        "onResume" -> Hook("unpause", null, emptyList())
        "onGameOver" -> Hook("gameOverStart", null, emptyList())
        "onDestroy" -> Hook("exit", null, emptyList())
        "goodNoteHitPre", "goodNoteHit" -> Hook("noteHit", "character == boyfriend", LUA_NOTE.split(", "))
        "opponentNoteHitPre", "opponentNoteHit" -> Hook("noteHit", "character != boyfriend", LUA_NOTE.split(", "))
        "noteMiss" -> Hook("noteMiss", "countedMiss", listOf("0", "direction", "\"\"", "false"))
        "noteMissPress" -> Hook("noteMiss", "!countedMiss", listOf("direction"))
        else -> null
    }

    fun hx(name: String): Hook? = when (name) {
        "goodNoteHitPre", "goodNoteHit" -> Hook("noteHit", "character == boyfriend", listOf("note"))
        "opponentNoteHitPre", "opponentNoteHit" -> Hook("noteHit", "character != boyfriend", listOf("note"))
        "noteMiss" -> Hook("noteMiss", "countedMiss", listOf("psychMissNote(direction)"))
        else -> lua(name)
    }

    val order = listOf(
        "onCreate", "onCreatePost", "onUpdate", "onUpdatePost", "onBeatHit", "onSectionHit", "onStepHit", "onCountdownTick", "onSongStart",
        "onEndSong", "onPause", "onResume", "onGameOver", "onDestroy", "goodNoteHitPre", "goodNoteHit", "opponentNoteHitPre", "opponentNoteHit",
        "noteMiss", "noteMissPress"
    )

    val internal = setOf("onTimerCompleted", "onTweenCompleted", "onSoundFinished", "onEvent", "eventEarlyTrigger")

    fun line(method: String, hook: Hook, arity: Int?): String {
        val args = hook.args.take(arity ?: hook.args.size).joinToString(", ")
        val call = "$method($args);"
        return if (hook.condition != null) "if (${hook.condition}) $call" else call
    }

    fun hooks(methods: Map<String, Int?>, rename: (String) -> String, hx: Boolean): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        order.forEach { name ->
            if (name !in methods) return@forEach
            val hook = (if (hx) hx(name) else lua(name)) ?: return@forEach
            out.getOrPut(hook.hook) { mutableListOf() } += line(rename(name), hook, methods[name])
        }
        return out
    }

    val missNote = "function psychMissNote(direction:Int):Dynamic\n{\n  return {noteData: direction, direction: direction, noteType: \"\", type: \"\", isSustainNote: false, mustPress: true};\n}"
}

class LuaToFps(skipLines: Set<Int> = emptySet()) : LuaTranspiler(skipLines) {
    private val used = linkedSetOf<String>()
    private val imports = linkedSetOf<String>()
    private val hoisted = mutableListOf<String>()
    private var haxeBlocks = 0

    companion object {
        private val reservedWords = setOf(
            "abstract", "break", "case", "cast", "catch", "class", "continue", "default", "do", "dynamic", "else", "enum", "extends", "extern",
            "false", "final", "for", "function", "if", "implements", "import", "in", "inline", "interface", "macro", "new", "null", "operator",
            "overload", "override", "package", "private", "public", "return", "static", "super", "switch", "this", "throw", "true", "try",
            "typedef", "untyped", "using", "var", "while"
        )

        private val presetNames = FpsClass.memberNames + setOf(
            "FlxG", "FlxSprite", "FlxText", "FlxTween", "FlxEase", "FlxTimer", "Paths", "Conductor", "Config", "Utils", "Binds", "ScriptingUtil",
            "ReflectUtil", "Character", "Note", "PlayState", "GameOverSubState", "Std", "Math", "StringMap", "BlendMode", "FlxTextBorderStyle",
            "RuntimeShader", "Highscore", "Scoring", "Chart", "Events", "curBeat", "curStep", "elapsed", "character", "note", "direction",
            "countedMiss"
        )

        private val boolHelpers = setOf(
            "stringStartsWith", "stringEndsWith", "luaSpriteExists", "luaTextExists", "luaSoundExists", "keyJustPressed", "keyPressed",
            "keyReleased", "keyboardJustPressed", "keyboardPressed", "keyboardReleased", "mouseClicked", "mousePressed", "mouseReleased",
            "checkFileExists", "getRandomBool", "isRunning", "flushSaveData", "objectsOverlap"
        )

        private val aliases = mapOf(
            "luaSpriteMakeGraphic" to "makeGraphic",
            "luaSpriteAddAnimationByPrefix" to "addAnimationByPrefix",
            "luaSpriteAddAnimationByIndices" to "addAnimationByIndices",
            "luaSpritePlayAnimation" to "objectPlayAnimation",
            "setLuaSpriteScrollFactor" to "setScrollFactor",
            "scaleLuaSprite" to "scaleObject",
            "setLuaSpriteCamera" to "setObjectCamera",
            "FlxColor" to "getColorFromHex"
        )

        val baseImports = setOf("flixel.FlxG", "flixel.FlxSprite", "flixel.text.FlxText", "flixel.tweens.FlxEase", "flixel.tweens.FlxTween", "flixel.util.FlxTimer", "haxe.ds.StringMap")
    }

    override fun boolCall(call: Expr.Call): Boolean = (call.function as? Expr.Name)?.name?.let { it in boolHelpers && !isLuaName(it) } == true

    private fun isLuaName(name: String): Boolean = "g_$name" in methodNames || "l_$name" in methodNames || name in assignedGlobals || name in chunkLocals

    fun convert(source: String): FpsParts {
        val result = transpile(source) ?: return FpsParts.empty(issues.toList())
        val methods = globalMethods().toSet()
        methods.filter { (it in PsychApi.callbacks || it in PsychApi.unsupportedCallbacks) && FpsCallbacks.lua(it) == null && it !in FpsCallbacks.internal }.forEach {
            issue(0, "Psych callback '$it' has no FPS Plus event, so it never runs.")
        }
        val resolved = FpsPsychApi.resolve(used.toSet())
        val stubs = mutableListOf<String>()
        listOf(
            Triple("psychOnTimerCompleted", "tag:String, loops:Int, left:Int", Triple("onTimerCompleted", "tag, loops, left", "psychRunTimer")),
            Triple("psychOnTweenCompleted", "tag:String, target:Dynamic", Triple("onTweenCompleted", "tag, target", "psychTweenDone")),
            Triple("psychOnSoundFinished", "tag:String", Triple("onSoundFinished", "tag", "psychPlaySound"))
        ).forEach { (name, params, call) ->
            if (call.third !in resolved) return@forEach
            val body = if (call.first in methods) "  ${emitName("g_${call.first}")}(${call.second});\n" else ""
            stubs += "function $name($params):Void\n{\n$body}"
        }
        val bootLines = result.boot.lines().map { it.trimEnd() }.filter { it.isNotBlank() && it.trim() != "null;" }
        val fieldNames = usedFields.filter { it !in methodNames }.map { emitName(it) }.distinct()
        val fields = mutableListOf<String>()
        FpsPsychApi.emit(used) { if (it.startsWith("var ")) fields += it }
        fieldNames.forEach { fields += "var $it:Dynamic = null;" }
        if (dynamicGlobals) fields += "var luaGlobals:Dynamic = null;"
        val members = mutableListOf<String>()
        val methodText = result.methods.lines().filter { it.trim() != "null;" }.joinToString("\n") { it.removePrefix("  ") }
        splitFunctions(methodText).forEach { members += it }
        hoisted.forEach { members += it.trimEnd() }
        if (dynamicGlobals) splitFunctions(globalAccessorLines("luaGlobals").joinToString("\n")).forEach { members += it }
        members += stubs
        val luaHelpers = StringBuilder()
        Helpers.emit(helpers) { luaHelpers.append(it).append('\n') }
        splitFunctions(FpsClass.polymodSafe(luaHelpers.toString())).forEach { members += it }
        val helperText = StringBuilder()
        FpsPsychApi.emit(used) { if (!it.startsWith("var ")) helperText.append(it).append('\n') }
        splitFunctions(helperText.toString()).forEach { members += it }
        val arity = methods.associateWith { methodNames["g_$it"] }
        val hooks = FpsCallbacks.hooks(arity, { emitName("g_$it") }, hx = false)
        val callbacks = methods.filter { FpsCallbacks.lua(it) != null }
        return FpsParts(
            imports + if (used.isNotEmpty() || dynamicGlobals) baseImports else emptySet(),
            fields, bootLines, members, hooks, issues.sortedBy { it.line }, callbacks, "onEvent" in methods
        )
    }

    private fun safe(name: String): String =
        if (name in reservedWords || name in presetNames || name.startsWith("psych") || name.startsWith("lua") || name.startsWith("__")) "v_$name" else name

    override fun emitName(key: String): String {
        val name = key.substring(2)
        if (key.startsWith("g_") && key in methodNames && (FpsCallbacks.lua(name) != null || name in FpsCallbacks.internal)) return name
        return safe(name)
    }

    override fun localName(luaName: String): String = safe(luaName)

    override fun constantRef(name: String, line: Int): String = if (name == "Function_Continue") "\"continue\"" else "\"stop\""

    override fun printCall(args: List<Expr>, line: Int): String = "trace(${args.joinToString(" + \", \" + ") { expr(it) }.ifEmpty { "\"\"" }})"

    private fun use(name: String): String {
        used += name
        return name
    }

    override fun variableRef(name: String, line: Int): String {
        Regex("^default(Player|Opponent)Strum([XY])([0-9])$").find(name)?.let { m ->
            val index = m.groupValues[3].toInt() + if (m.groupValues[1] == "Player") 4 else 0
            return "${use("psychDefaultStrum")}($index, \"${m.groupValues[2].lowercase()}\")"
        }
        return when (name) {
            "curBeat" -> "playstate.curBeat"
            "curStep" -> "playstate.curStep"
            "curSection" -> "Std.int(playstate.curBeat / 4)"
            "curDecBeat" -> "Conductor.beat"
            "curDecStep" -> "Conductor.step"
            "songPosition" -> "Conductor.songPosition"
            "bpm", "curBpm" -> "Conductor.bpm"
            "crochet" -> "Conductor.crochet"
            "stepCrochet" -> "Conductor.stepCrochet"
            "songLength" -> "FlxG.sound.music.length"
            "songName", "loadedSongName" -> "PlayState.chart.meta.song"
            "songPath", "loadedSongPath" -> "PlayState.chart.meta.song.toLowerCase()"
            "startedCountdown" -> "playstate.startedCountdown"
            "isStoryMode" -> "PlayState.isStoryMode"
            "difficulty" -> "PlayState.storyDifficulty"
            "difficultyName", "difficultyPath", "difficultyNameTranslation" -> "[\"easy\", \"normal\", \"hard\"][PlayState.storyDifficulty]"
            "weekRaw", "week" -> "PlayState.storyWeek"
            "boyfriendName" -> "boyfriend.curCharacter"
            "dadName" -> "dad.curCharacter"
            "gfName" -> "gf.curCharacter"
            "curStage" -> "PlayState.curStage"
            "mustHitSection" -> "${use("psychMustHit")}(false)"
            "gfSection" -> "${use("psychMustHit")}(true)"
            "downscroll" -> "Config.downscroll"
            "middlescroll" -> "Config.centeredNotes"
            "ghostTapping" -> "(Config.ghostTapType > 0)"
            "flashingLights" -> "Config.flashingLights"
            "framerate" -> "Config.framerate"
            "noteOffset" -> "Config.offset"
            "cameraZoomOnBeat" -> "(Config.camBopAmount > 0)"
            "hideHud", "lowQuality", "noResetButton", "botPlay", "practice", "inGameOver", "guitarHeroSustains", "luaDebugMode", "luaDeprecatedWarnings", "instakillOnMiss", "seenCutscene" -> "false"
            "shadersEnabled", "scoreZoom" -> "true"
            "timeBarType" -> "\"Disabled\""
            "healthBarAlpha", "splashAlpha", "playbackRate", "healthGainMult", "healthLossMult" -> "1"
            "scriptName" -> "\"script\""
            "currentModDirectory", "modFolder", "splashSkin", "noteSkin", "splashSkinPostfix", "noteSkinPostfix", "chartPath" -> "\"\""
            "buildTarget" -> "\"windows\""
            "version" -> "\"1.0.4\""
            "screenWidth" -> "FlxG.width"
            "screenHeight" -> "FlxG.height"
            "score" -> "playstate.songStats.score"
            "misses" -> "playstate.songStats.missCount"
            "hits", "totalNotesHit", "totalPlayed" -> "(playstate.songStats.sickCount + playstate.songStats.goodCount + playstate.songStats.badCount + playstate.songStats.shitCount)"
            "combo" -> "playstate.combo"
            "rating" -> "(playstate.songStats.accuracy / 100)"
            "ratingName", "ratingFC" -> "\"\""
            "cameraX" -> "playstate.camFollow.x"
            "cameraY" -> "playstate.camFollow.y"
            "scrollSpeed" -> "PlayState.chart.meta.scroll"
            "hasVocals" -> "true"
            "deaths" -> "0"
            "defaultBoyfriendX" -> "boyfriend.x"
            "defaultBoyfriendY" -> "boyfriend.y"
            "defaultOpponentX" -> "dad.x"
            "defaultOpponentY" -> "dad.y"
            "defaultGirlfriendX" -> "gf.x"
            "defaultGirlfriendY" -> "gf.y"
            "altAnim" -> "false"
            else -> {
                issue(line, "Unknown variable '$name' (not defined in this script) became null.")
                "null"
            }
        }
    }

    private fun haxeCode(args: List<Expr>, line: Int): String {
        val code = (args.getOrNull(0) as? Expr.Str)?.value
        if (code == null) {
            issue(line, "runHaxeCode() with code built at runtime cannot be converted.")
            return "null"
        }
        val fixed = PsychHxToFps.fixText(code)
        fixed.second.forEach { issue(line, "runHaxeCode: $it") }
        val tokens = HxSource.tokenize(fixed.first)
        val statements = StringBuilder()
        (args.getOrNull(1) as? Expr.Table)?.fields?.filterIsInstance<TableField.Named>()?.forEach {
            statements.append("var ${it.name}:Dynamic = ${expr(it.value)};\n")
        }
        HxSource.segments(tokens).forEach { seg ->
            when (seg.kind) {
                "import" -> PsychHxToFps.mapImport(seg.head.filter { it.kind != HxKind.SPACE }.drop(1).takeWhile { !it.isPunct(";") }.joinToString("") { it.text })?.let { imports += it }
                "function" -> hoisted += "function " + seg.name + HxSource.text(seg.head).trim() + "\n{\n" + HxSource.indentBody(HxSource.text(seg.body)).joinToString("\n") + "\n}"
                else -> statements.append(HxSource.text(seg.body).trim().let { if (seg.kind == "var") "var ${seg.name}" + (if (seg.body.isNotEmpty()) " = $it" else "") + ";" else it }).append('\n')
            }
        }
        val then = (args.getOrNull(2) as? Expr.Str)?.value
        if (then != null) {
            val callArgs = (args.getOrNull(3) as? Expr.Table)?.fields?.filterIsInstance<TableField.Positional>()?.joinToString(", ") { expr(it.value) } ?: ""
            statements.append("return $then($callArgs);\n")
        }
        val body = statements.toString().trim()
        if (body.isEmpty()) return "null"
        val name = "haxeCode${++haxeBlocks}"
        val needsReturn = !body.lines().last().trimStart().startsWith("return")
        hoisted += "function $name():Dynamic\n{\n" + HxSource.indentBody(body).joinToString("\n") + (if (needsReturn) "\n  return null;" else "") + "\n}"
        return "$name()"
    }

    override fun apiCall(name: String, args: List<Expr>, line: Int): String {
        val api = aliases[name] ?: name
        return when (api) {
            "runHaxeCode" -> haxeCode(args, line)
            "runHaxeFunction" -> {
                val fn = (args.getOrNull(0) as? Expr.Str)?.value
                val list = (args.getOrNull(1) as? Expr.Table)?.fields?.filterIsInstance<TableField.Positional>()?.map { expr(it.value) }
                if (fn != null) "$fn(${list?.joinToString(", ") ?: ""})" else {
                    issue(line, "runHaxeFunction() with a function name built at runtime cannot be converted.")
                    "null"
                }
            }
            "addHaxeLibrary" -> {
                val lib = (args.getOrNull(0) as? Expr.Str)?.value
                val pkg = (args.getOrNull(1) as? Expr.Str)?.value ?: ""
                if (lib != null) PsychHxToFps.mapImport(if (pkg.isEmpty()) lib else "$pkg.$lib")?.let { imports += it }
                "null"
            }
            "debugPrint" -> printCall(args, line)
            "close" -> "null"
            "isRunning" -> "true"
            "getPropertyLuaSprite" -> "${use("psychGetProperty")}(${expr(args.getOrNull(0) ?: Expr.Str("", line))} + \".\" + ${expr(args.getOrNull(1) ?: Expr.Str("", line))})"
            "setPropertyLuaSprite" -> "${use("psychSetProperty")}(${expr(args.getOrNull(0) ?: Expr.Str("", line))} + \".\" + ${expr(args.getOrNull(1) ?: Expr.Str("", line))}, ${args.getOrNull(2)?.let { expr(it) } ?: "null"})"
            "setObjectOrder", "getObjectOrder" -> {
                issue(line, "$api(): FPS Plus orders sprites by layer (background, middle, foreground), so the order was left as it is.")
                if (api == "getObjectOrder") "0" else "null"
            }
            "startVideo", "startAndEndVideo" -> {
                issue(line, "$api(): videos are not converted; the song goes on without it.")
                "null"
            }
            else -> if (FpsPsychApi.has(api)) {
                val helperName = FpsPsychApi.helperName(api)
                used += helperName
                "$helperName(${args(args)})"
            } else {
                if (api in PsychApi.notImplemented) issue(line, "Psych function '$api' has no FPS Plus equivalent; the call was removed.")
                else issue(line, "Psych function '$api' has no FPS Plus equivalent or is not defined in this script; the call was removed.")
                "null"
            }
        }
    }
}

internal fun splitFunctions(text: String): List<String> {
    val out = mutableListOf<String>()
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        if (!lines[i].startsWith("function ")) {
            i++
            continue
        }
        val start = i
        var depth = 0
        var opened = false
        while (i < lines.size) {
            lines[i].forEach { c ->
                if (c == '{') {
                    depth++
                    opened = true
                }
                if (c == '}') depth--
            }
            i++
            if (opened && depth == 0) break
        }
        out += lines.subList(start, i).joinToString("\n").trimEnd()
    }
    return out
}

object PsychHxToFps {
    private val dropped = listOf("backend.", "objects.", "states.", "substates.", "psychlua.", "shaders.", "cutscenes.", "options.", "debug.", "llua.", "funkin.", "mobile.")

    private val defaults = setOf("Paths", "Conductor", "PlayState", "Character", "Note", "ClientPrefs", "GameOverSubstate", "CoolUtil", "BGSprite", "HealthIcon", "Alphabet")

    fun mapImport(full: String): String? {
        val short = full.substringAfterLast('.')
        if (short in defaults) return null
        if (dropped.any { full.startsWith(it) }) return null
        if (full.startsWith("sys.") || full.startsWith("lime.app.")) return null
        return if (full.contains('.')) full else null
    }

    private val replacements = listOf(
        Regex("(?<![\\w.])PlayState\\.instance(?!\\w)") to "playstate",
        Regex("(?<![\\w.])game\\.opponentStrums(?!\\w)") to "playstate.enemyStrums",
        Regex("(?<![\\w.])game\\.camOther(?!\\w)") to "playstate.camOverlay",
        Regex("(?<![\\w.])game\\.songScore(?!\\w)") to "playstate.songStats.score",
        Regex("(?<![\\w.])game\\.songMisses(?!\\w)") to "playstate.songStats.missCount",
        Regex("(?<![\\w.])game\\.variables(?!\\w)") to "playstate.arbitraryData",
        Regex("(?<![\\w.])game(?!\\w)") to "playstate",
        Regex("(?<![\\w.])setVar\\(") to "psychSetVar(",
        Regex("(?<![\\w.])getVar\\(") to "psychGetVar(",
        Regex("(?<![\\w.])removeVar\\(") to "psychRemoveVar(",
        Regex("(?<![\\w.])debugPrint\\(") to "trace(",
        Regex("ClientPrefs\\.data\\.downScroll") to "Config.downscroll",
        Regex("ClientPrefs\\.data\\.middleScroll") to "Config.centeredNotes",
        Regex("ClientPrefs\\.data\\.flashing") to "Config.flashingLights",
        Regex("ClientPrefs\\.data\\.ghostTapping") to "(Config.ghostTapType > 0)",
        Regex("ClientPrefs\\.data\\.lowQuality") to "false",
        Regex("ClientPrefs\\.data\\.shaders") to "true",
        Regex("ClientPrefs\\.data\\.antialiasing") to "true",
        Regex("ClientPrefs\\.(?:data\\.)?([A-Za-z]+)") to "Config.$1",
        Regex("\\.noteData(?!\\w)") to ".direction",
        Regex("\\.noteType(?!\\w)") to ".type",
        Regex("Paths\\.formatToSongPath\\(") to "(",
        Regex("(?<![\\w.])Function_Stop(?:LuaHScript|Lua|HScript|All)?(?!\\w)") to "\"stop\"",
        Regex("(?<![\\w.])Function_Continue(?!\\w)") to "\"continue\""
    )

    fun fixText(source: String): Pair<String, List<String>> {
        val issues = mutableListOf<String>()
        var text = FpsClass.polymodSafe(source)
        replacements.forEach { (r, to) -> text = r.replace(text, to) }
        listOf("createGlobalCallback", "createCallback", "addLuaScript", "addHScript", "openCustomSubstate", "startVideo", "setObjectOrder").forEach {
            if (Regex("(?<![\\w.])$it\\s*\\(").containsMatchIn(text)) issues += "$it() is Psych-only and does nothing in FPS Plus."
        }
        if (Regex("(?<![\\w.])Type\\.").containsMatchIn(text)) issues += "Type.* is blocked in FPS Plus scripts; use ReflectUtil instead."
        return text to issues
    }

    fun convert(source: String): FpsParts {
        val (text, textIssues) = fixText(source)
        val issues = textIssues.map { ScriptIssue(0, it) }.toMutableList()
        val tokens = HxSource.tokenize(text)
        val segments = HxSource.segments(tokens)
        val imports = linkedSetOf<String>()
        val fields = mutableListOf<String>()
        val boot = mutableListOf<String>()
        val methods = mutableListOf<String>()
        val arity = LinkedHashMap<String, Int?>()
        val renames = segments.filter { it.kind == "function" && it.name in FpsClass.memberNames }.map { it.name }.toSet()
        fun rename(code: String): String {
            var out = code
            renames.forEach { n -> out = Regex("(?<![\\w.])${Regex.escape(n)}\\s*\\(").replace(out) { "hx_$n(" } }
            return out
        }
        segments.forEach { seg ->
            when (seg.kind) {
                "import" -> {
                    val full = seg.head.filter { it.kind != HxKind.SPACE }.drop(1).takeWhile { !it.isPunct(";") }.joinToString("") { it.text }
                    val mapped = mapImport(full)
                    if (mapped != null) imports += mapped
                    else if (dropped.any { full.startsWith(it) } && full.substringAfterLast('.') !in defaults) issues += ScriptIssue(seg.line, "import $full is Psych-only and was removed.")
                }
                "using", "package", "typedef" -> issues += ScriptIssue(seg.line, "${seg.kind} is not supported in FPS Plus scripts and was removed.")
                "class", "interface", "enum", "abstract" -> issues += ScriptIssue(seg.line, "A ${seg.kind} declared inside a Psych script can't be kept in an FPS Plus script class.")
                "var" -> {
                    val type = HxSource.text(seg.head).trim().ifEmpty { ":Dynamic" }
                    fields += "var ${seg.name}$type;"
                    if (seg.body.isNotEmpty()) boot += rename("${seg.name} = ${HxSource.text(seg.body).trim()};")
                }
                "vars" -> boot += rename(HxSource.text(seg.body).trim().removePrefix("var ").removePrefix("final ").let { "var $it" }.trimEnd(';') + ";")
                "function" -> {
                    val name = if (seg.name in renames) "hx_${seg.name}" else seg.name
                    val headTokens = seg.head
                    val open = headTokens.indexOfFirst { it.isPunct("(") }
                    arity[seg.name] = if (open >= 0) HxSource.paramNames(headTokens, open).size else 0
                    val body = if (seg.braced) HxSource.text(seg.body) else "return " + HxSource.text(seg.body).trim().removePrefix("return ").trimEnd(';') + ";"
                    methods += "function $name" + rename(HxSource.text(headTokens).trim()) + "\n{\n" + HxSource.indentBody(rename(body)).joinToString("\n") + "\n}"
                }
                else -> HxSource.text(seg.body).trim().takeIf { it.isNotEmpty() }?.let { boot += rename(it) }
            }
        }
        val defined = arity.keys.toSet()
        listOf("onSpawnNote", "onStartCountdown", "onMoveCamera", "onUpdateScore", "onEventPushed", "onGameOverConfirm", "onKeyPress", "onKeyRelease", "onGhostTap").forEach {
            if (it in defined) issues += ScriptIssue(0, "Callback $it has no FPS Plus event, so it never runs.")
        }
        val hooks = FpsCallbacks.hooks(arity.filterKeys { it in FpsCallbacks.order }, { it }, hx = true)
        if (hooks.values.flatten().any { it.contains("psychMissNote") }) methods += FpsCallbacks.missNote
        val helperUses = FpsPsychApi.functions.filter { name -> Regex("(?<![\\w.])${Regex.escape(name)}\\s*\\(").containsMatchIn(text) }.toSet()
        if (helperUses.isNotEmpty()) {
            FpsPsychApi.emit(helperUses) { if (it.startsWith("var ")) fields += it }
            val helperText = StringBuilder()
            FpsPsychApi.emit(helperUses) { if (!it.startsWith("var ")) helperText.append(it).append('\n') }
            splitFunctions(helperText.toString()).forEach { methods += it }
            imports += LuaToFps.baseImports
            listOf("psychOnTimerCompleted" to "tag:String, loops:Int, left:Int", "psychOnTweenCompleted" to "tag:String, target:Dynamic", "psychOnSoundFinished" to "tag:String").forEach { (n, p) ->
                if (methods.any { Regex("(?<![\\w.])$n\\(").containsMatchIn(it) }) methods += "function $n($p):Void\n{\n}"
            }
        }
        return FpsParts(imports, fields, boot, methods, hooks, issues, defined.filter { it in FpsCallbacks.order }, "onEvent" in defined)
    }
}
