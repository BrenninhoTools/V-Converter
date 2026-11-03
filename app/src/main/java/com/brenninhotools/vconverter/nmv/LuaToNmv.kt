package com.brenninhotools.vconverter.nmv

import com.brenninhotools.vconverter.script.Helpers
import com.brenninhotools.vconverter.script.LuaTranspiler
import com.brenninhotools.vconverter.script.PsychApi
import com.brenninhotools.vconverter.script.ScriptIssue
import com.brenninhotools.vconverter.script.hx.HxKind
import com.brenninhotools.vconverter.script.hx.HxSource
import com.brenninhotools.vconverter.script.lua.Expr
import com.brenninhotools.vconverter.script.lua.TableField

data class NmvScriptOut(val source: String, val issues: List<ScriptIssue>, val callbacks: List<String>)

class LuaToNmv(skipLines: Set<Int> = emptySet()) : LuaTranspiler(skipLines) {
    private val used = linkedSetOf<String>()
    private val imports = linkedSetOf<String>()
    private val hoisted = mutableListOf<String>()
    private var haxeBlocks = 0

    private class Bridge(val nmv: String, val params: String, val args: String)

    companion object {
        private const val NOTE_ARGS = "game.notes.members.indexOf(note), Std.int(Math.abs(note.noteData)), note.noteType, note.isSustainNote"

        private val bridges = mapOf(
            "onCreate" to Bridge("onLoad", "", ""),
            "goodNoteHit" to Bridge("goodNoteHit", "note, field", NOTE_ARGS),
            "goodNoteHitPre" to Bridge("goodNoteHitPre", "note, field", NOTE_ARGS),
            "opponentNoteHit" to Bridge("opponentNoteHit", "note, field", NOTE_ARGS),
            "opponentNoteHitPre" to Bridge("opponentNoteHitPre", "note, field", NOTE_ARGS),
            "noteMiss" to Bridge("noteMiss", "note, field", NOTE_ARGS),
            "onSpawnNote" to Bridge("onSpawnNote", "note", "$NOTE_ARGS, note.strumTime"),
            "onEventPushed" to Bridge("onEventPush", "event", "event.event, event.value1, event.value2, event.strumTime")
        )

        val direct = setOf(
            "onCreatePost", "onUpdate", "onUpdatePost", "onBeatHit", "onStepHit", "onSectionHit", "onSongStart", "onEndSong",
            "onStartCountdown", "onCountdownStarted", "onCountdownTick", "onPause", "onResume", "onGameOver", "onDestroy",
            "onMoveCamera", "onRecalculateRating", "onGhostTap", "onKeyPress", "onKeyRelease", "noteMissPress", "onEvent",
            "eventEarlyTrigger"
        )

        private val internal = setOf("onTimerCompleted", "onTweenCompleted", "onSoundFinished")

        private val reservedWords = setOf(
            "abstract", "break", "case", "cast", "catch", "class", "continue", "default", "do", "dynamic", "else", "enum", "extends", "extern",
            "false", "final", "for", "function", "if", "implements", "import", "in", "inline", "interface", "macro", "new", "null", "operator",
            "overload", "override", "package", "private", "public", "return", "static", "super", "switch", "this", "throw", "true", "try",
            "typedef", "untyped", "using", "var", "while"
        )

        private val presetNames = setOf(
            "game", "script", "add", "stage", "global", "setVar", "getVar", "initScript", "newShader", "newOption", "getOption", "trace",
            "FlxG", "FlxSprite", "FlxText", "FlxTween", "FlxEase", "FlxTimer", "FlxColor", "FlxMath", "FlxCamera", "FlxSound", "Paths",
            "Conductor", "ClientPrefs", "CoolUtil", "PlayState", "GameOverSubstate", "Character", "Note", "BGSprite", "StrumNote",
            "HealthIcon", "Std", "Math", "StringTools", "Reflect", "Type", "StringMap", "Main", "Assets", "Function_Stop",
            "Function_Continue", "Function_Halt", "version", "modFolder", "inPlaystate", "bpm", "scrollSpeed", "songName", "isStoryMode",
            "difficulty", "weekRaw", "seenCutscene", "week", "difficultyName", "songLength", "botPlay", "practice", "startedCountdown",
            "mustHitSection", "curBpm", "crotchet", "stepCrotchet", "inGameOver", "onLoad", "Random", "Controls"
        ) + bridges.values.map { it.nmv }

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

        private val presetImports = setOf(
            "ClientPrefs", "Paths", "Conductor", "PlayState", "Character", "Note", "BGSprite", "StrumNote", "HealthIcon", "CoolUtil",
            "Alphabet", "NoteSplash", "GameOverSubstate", "FunkinSprite", "FlxG", "FlxSprite", "FlxText", "FlxTween", "FlxEase", "FlxTimer",
            "FlxColor", "FlxMath", "FlxSound", "FlxCamera", "FlxPoint", "FlxAxes", "FlxTextBorderStyle", "FlxBackdrop", "FlxTiledSprite",
            "FlxTypedGroup", "FlxSpriteGroup", "FlxEmitter", "FlxFlicker", "FlxSpriteUtil", "FlxRuntimeShader", "FlxKey", "BlendMode",
            "FlxTextAlign", "StringTools", "Std", "Math", "Type", "Reflect"
        )
    }

    private val hasHelper = setOf("getProperty", "setProperty", "getPropertyFromGroup", "setPropertyFromGroup")

    override fun boolCall(call: Expr.Call): Boolean = (call.function as? Expr.Name)?.name?.let { it in boolHelpers && !isLuaName(it) } == true

    private fun isLuaName(name: String): Boolean = "g_$name" in methodNames || "l_$name" in methodNames || name in assignedGlobals || name in chunkLocals

    fun convert(source: String): NmvScriptOut {
        val result = transpile(source) ?: return NmvScriptOut("", issues.toList(), emptyList())
        val methods = globalMethods().toSet()
        methods.filter { it in PsychApi.callbacks && it !in bridges && it !in direct && it !in internal }.forEach {
            issue(0, "Psych callback '$it' has no NightmareVision event, so it never runs.")
        }
        PsychApi.unsupportedCallbacks.filter { it in methods && it !in direct }.forEach {
            issue(0, "Psych callback '$it' has no NightmareVision event, so it never runs.")
        }
        val adapters = StringBuilder()
        val resolved = NmvPsychApi.resolve(used.toSet())
        val applySets = "psychNoteSets" in resolved
        if (applySets) used += "psychApplyNoteSets"
        bridges.forEach { (lua, bridge) ->
            val defined = lua in methods
            val spawn = lua == "onSpawnNote" && applySets
            if (!defined && !spawn) return@forEach
            adapters.append("\nfunction ${bridge.nmv}(${bridge.params})\n{\n")
            if (spawn) adapters.append("  psychApplyNoteSets(note);\n")
            if (defined) adapters.append("  return ${emitName("g_$lua")}(${bridge.args});\n") else adapters.append("  return null;\n")
            adapters.append("}\n")
        }
        val callbackStubs = StringBuilder()
        listOf(
            Triple("psychOnTimerCompleted", "tag:String, loops:Int, left:Int", Triple("onTimerCompleted", "tag, loops, left", "psychRunTimer")),
            Triple("psychOnTweenCompleted", "tag:String, target:Dynamic", Triple("onTweenCompleted", "tag, target", "psychTweenDone")),
            Triple("psychOnSoundFinished", "tag:String", Triple("onSoundFinished", "tag", "psychPlaySound"))
        ).forEach { (name, params, call) ->
            if (call.third !in resolved) return@forEach
            callbackStubs.append("\nfunction $name($params):Void\n{\n")
            if (call.first in methods) callbackStubs.append("  ${emitName("g_${call.first}")}(${call.second});\n")
            callbackStubs.append("}\n")
        }

        val out = StringBuilder()
        if (dynamicGlobals) imports += "haxe.ds.StringMap"
        imports.forEach { out.append("import ").append(it).append(";\n") }
        if (imports.isNotEmpty()) out.append('\n')
        val helperLines = mutableListOf<String>()
        NmvPsychApi.emit(used) { helperLines += it }
        helperLines.filter { it.startsWith("var ") }.forEach { out.append(it).append('\n') }
        val bootLines = result.boot.lines().map { it.trimEnd() }
        val fieldNames = usedFields.filter { it !in methodNames }.map { emitName(it) }.distinct()
        val inline = HashMap<String, String>()
        val firstUse = HashMap<String, Int>()
        bootLines.forEachIndexed { index, line ->
            fieldNames.forEach { f -> if (!firstUse.containsKey(f) && Regex("(?<![\\w.])${Regex.escape(f)}(?!\\w)").containsMatchIn(line)) firstUse[f] = index }
        }
        bootLines.forEachIndexed { index, line ->
            val m = Regex("^([A-Za-z_][A-Za-z0-9_]*) = (.+);$").find(line) ?: return@forEachIndexed
            val name = m.groupValues[1]
            if (name in fieldNames && firstUse[name] == index && name !in inline && !m.groupValues[2].contains(Regex("(?<![\\w.])${Regex.escape(name)}(?!\\w)"))) inline[name] = m.groupValues[2]
        }
        fieldNames.forEach { f -> if (f !in inline) out.append("var ").append(f).append(":Dynamic = null;\n") }
        bootLines.mapIndexed { index, line ->
            val m = Regex("^([A-Za-z_][A-Za-z0-9_]*) = (.+);$").find(line)
            if (m != null && inline[m.groupValues[1]] == m.groupValues[2] && firstUse[m.groupValues[1]] == index) "var ${m.groupValues[1]} = ${m.groupValues[2]};" else line
        }.filter { it.trim() != "null;" }.forEach { out.append(it).append('\n') }
        result.methods.lines().filter { it.trim() != "null;" }.forEach { out.append(it.removePrefix("  ")).append('\n') }
        hoisted.forEach { out.append('\n').append(it.trimEnd()).append('\n') }
        if (dynamicGlobals) {
            out.append("\nvar luaGlobals:Dynamic = null;\n")
            globalAccessorLines("luaGlobals").forEach { out.append(it).append('\n') }
        }
        out.append(adapters)
        out.append(callbackStubs)
        val luaHelpers = StringBuilder()
        Helpers.emit(helpers) { luaHelpers.append(it).append('\n') }
        out.append(luaHelpers.toString().replace("Reflect.getClassNameOf(t) == \"Unknown\"", "Type.getClass(t) == null").replace("Reflect.getClassNameOf(replacement) == \"Unknown\"", "Type.getClass(replacement) == null"))
        helperLines.filter { !it.startsWith("var ") }.forEach { out.append(it).append('\n') }
        val text = out.toString().replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
        val callbacks = methods.filter { it in bridges || it in direct }
        return NmvScriptOut(text, issues.sortedBy { it.line }, callbacks)
    }

    private fun safe(name: String): String =
        if (name in reservedWords || name in presetNames || name.startsWith("psych") || name.startsWith("lua") || name.startsWith("__")) "v_$name" else name

    override fun emitName(key: String): String {
        val name = key.substring(2)
        if (key.startsWith("g_") && key in methodNames) {
            if (name in bridges) return "lua" + name.replaceFirstChar { it.uppercaseChar() }
            if (name in direct || name in internal) return name
        }
        return safe(name)
    }

    override fun localName(luaName: String): String = safe(luaName)

    override fun constantRef(name: String, line: Int): String = if (name == "Function_Continue") "Function_Continue" else "Function_Stop"

    override fun printCall(args: List<Expr>, line: Int): String = "trace(${args.joinToString(" + \", \" + ") { expr(it) }.ifEmpty { "\"\"" }})"

    private fun helper(api: String, args: List<Expr>): String {
        val name = NmvPsychApi.helperName(api)
        used += name
        return "$name(${args(args)})"
    }

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
            "curBeat" -> "game.curBeat"
            "curStep" -> "game.curStep"
            "curSection" -> "game.curSection"
            "curDecBeat" -> "game.curDecBeat"
            "curDecStep" -> "game.curDecStep"
            "songPosition" -> "Conductor.songPosition"
            "bpm", "curBpm" -> "Conductor.bpm"
            "crochet" -> "Conductor.crotchet"
            "stepCrochet" -> "Conductor.stepCrotchet"
            "songLength" -> "FlxG.sound.music.length"
            "songName", "loadedSongName" -> "PlayState.SONG.song"
            "songPath", "loadedSongPath" -> "Paths.sanitize(PlayState.SONG.song)"
            "startedCountdown" -> "game.startedCountdown"
            "seenCutscene" -> "PlayState.seenCutscene"
            "isStoryMode" -> "PlayState.isStoryMode"
            "difficulty" -> "PlayState.storyMeta.difficulty"
            "difficultyName", "difficultyPath", "difficultyNameTranslation" -> "difficultyName"
            "weekRaw" -> "PlayState.storyMeta.curWeek"
            "week" -> "week"
            "deaths" -> "PlayState.deathCounter"
            "boyfriendName" -> "game.boyfriend.curCharacter"
            "dadName" -> "game.dad.curCharacter"
            "gfName" -> "(game.gf == null ? \"\" : game.gf.curCharacter)"
            "curStage" -> "game.stage.curStage"
            "mustHitSection" -> "${use("psychMustHit")}(false)"
            "gfSection" -> "${use("psychMustHit")}(true)"
            "altAnim" -> "${use("psychAltAnim")}()"
            "downscroll" -> "ClientPrefs.downScroll"
            "middlescroll" -> "ClientPrefs.middleScroll"
            "ghostTapping" -> "ClientPrefs.ghostTapping"
            "hideHud" -> "ClientPrefs.hideHud"
            "timeBarType" -> "ClientPrefs.timeBarType"
            "scoreZoom" -> "ClientPrefs.scoreZoom"
            "cameraZoomOnBeat" -> "ClientPrefs.camZooms"
            "flashingLights" -> "ClientPrefs.flashing"
            "noteOffset" -> "ClientPrefs.noteOffset"
            "healthBarAlpha" -> "ClientPrefs.healthBarAlpha"
            "noResetButton" -> "ClientPrefs.noReset"
            "lowQuality" -> "ClientPrefs.lowQuality"
            "shadersEnabled" -> "ClientPrefs.shaders"
            "framerate" -> "ClientPrefs.framerate"
            "scriptName" -> "\"script\""
            "currentModDirectory", "modFolder" -> "modFolder"
            "buildTarget" -> "\"windows\""
            "version" -> "version"
            "screenWidth" -> "FlxG.width"
            "screenHeight" -> "FlxG.height"
            "inGameOver" -> "(GameOverSubstate.instance != null)"
            "score" -> "game.songScore"
            "misses" -> "game.songMisses"
            "hits", "totalNotesHit", "totalPlayed" -> "game.songHits"
            "combo" -> "game.combo"
            "rating" -> "game.ratingPercent"
            "ratingName" -> "game.ratingName"
            "ratingFC" -> "game.ratingFC"
            "botPlay" -> "game.cpuControlled"
            "practice" -> "game.practiceMode"
            "playbackRate" -> "game.playbackRate"
            "healthGainMult" -> "game.healthGain"
            "healthLossMult" -> "game.healthLoss"
            "instakillOnMiss" -> "game.instakillOnMiss"
            "guitarHeroSustains", "luaDebugMode", "luaDeprecatedWarnings" -> "false"
            "splashAlpha" -> "1"
            "splashSkin", "noteSkin", "splashSkinPostfix", "noteSkinPostfix", "chartPath" -> "\"\""
            "cameraX" -> "${use("psychGetCameraFollowX")}()"
            "cameraY" -> "${use("psychGetCameraFollowY")}()"
            "scrollSpeed" -> "game.songSpeed"
            "hasVocals" -> "PlayState.SONG.needsVoices"
            "defaultBoyfriendX" -> "game.boyfriendPosition.x"
            "defaultBoyfriendY" -> "game.boyfriendPosition.y"
            "defaultOpponentX" -> "game.dadPosition.x"
            "defaultOpponentY" -> "game.dadPosition.y"
            "defaultGirlfriendX" -> "game.gfPosition.x"
            "defaultGirlfriendY" -> "game.gfPosition.y"
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
        val converted = NmvScripts.psychHxToNmv(code)
        converted.issues.forEach { issue(line, "runHaxeCode: ${it.message}") }
        val tokens = HxSource.tokenize(converted.source)
        val statements = StringBuilder()
        (args.getOrNull(1) as? Expr.Table)?.fields?.filterIsInstance<TableField.Named>()?.forEach {
            statements.append("var ${it.name}:Dynamic = ${expr(it.value)};\n")
        }
        HxSource.segments(tokens).forEach { seg ->
            when (seg.kind) {
                "import" -> imports += seg.head.filter { it.kind != HxKind.SPACE }.drop(1).takeWhile { !it.isPunct(";") }.joinToString("") { it.text }
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
                if (lib != null && lib !in presetImports) NmvScripts.mapImport(if (pkg.isEmpty()) lib else "$pkg.$lib")?.let { imports += it }
                "null"
            }
            "debugPrint" -> printCall(args, line)
            "getPropertyLuaSprite" -> "${use("psychGetProperty")}(${expr(args.getOrNull(0) ?: Expr.Str("", line))} + \".\" + ${expr(args.getOrNull(1) ?: Expr.Str("", line))})"
            "setPropertyLuaSprite" -> "${use("psychSetProperty")}(${expr(args.getOrNull(0) ?: Expr.Str("", line))} + \".\" + ${expr(args.getOrNull(1) ?: Expr.Str("", line))}, ${args.getOrNull(2)?.let { expr(it) } ?: "null"})"
            "isRunning" -> "true"
            "getModSetting" -> {
                issue(line, "getModSetting(): Psych mod settings don't exist in NightmareVision; the value is null.")
                "null"
            }
            "instanceArg" -> {
                issue(line, "instanceArg() has no NightmareVision equivalent; the argument became null.")
                "null"
            }
            else -> when {
                api in hasHelper || NmvPsychApi.has(api) -> {
                    if (api == "startVideo" || api == "startAndEndVideo") issue(line, "$api(): videos are skipped in the converted script; the song continues right away.")
                    helper(api, args)
                }
                api in PsychApi.notImplemented -> {
                    issue(line, "Psych function '$api' has no NightmareVision equivalent; the call was removed.")
                    "null"
                }
                else -> {
                    issue(line, "Unknown function '$api' (not defined in this script) was removed.")
                    "null"
                }
            }
        }
    }
}
