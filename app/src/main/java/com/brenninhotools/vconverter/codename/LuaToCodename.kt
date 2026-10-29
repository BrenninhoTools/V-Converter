package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.Helpers
import com.brenninhotools.vconverter.script.LuaTranspiler
import com.brenninhotools.vconverter.script.PsychApi
import com.brenninhotools.vconverter.script.hx.HxSource
import com.brenninhotools.vconverter.script.lua.Expr
import com.brenninhotools.vconverter.script.lua.TableField

class LuaToCodename(
    private val scope: CodenameScope,
    private val knownTags: Set<String> = emptySet(),
    private val hooks: Map<String, List<String>> = emptyMap(),
    skipLines: Set<Int> = emptySet()
) : LuaTranspiler(skipLines) {
    private val cneHelpers = linkedSetOf<String>()
    private val imports = linkedSetOf<String>()
    private val createdTags = linkedSetOf<String>()
    private val hoisted = mutableListOf<String>()
    private var haxeBlocks = 0
    private var needsDefaults = false

    companion object {
        private val identifier = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        private val codenameNames = setOf(
            "boyfriend", "bf", "dad", "gf", "camGame", "camHUD", "health", "songScore", "misses", "combo", "curBeat", "curStep", "curMeasure",
            "defaultCamZoom", "camFollow", "playerStrums", "cpuStrums", "iconP1", "iconP2", "healthBar", "scoreTxt", "inst", "vocals", "strumLines",
            "stage", "members", "add", "insert", "remove", "scrollSpeed", "accuracy", "camZooming", "curCameraTarget", "events", "paused", "FlxG",
            "Paths", "Conductor", "Options", "PlayState", "Std", "Math", "StringTools", "Reflect", "Type", "Assets", "FlxSprite", "FunkinSprite",
            "FlxText", "FlxTween", "FlxEase", "FlxTimer", "FlxColor", "FlxCamera", "Character", "Note", "setVar", "getVar", "removeVar",
            "getProperty", "setProperty", "getPropertyFromGroup", "setPropertyFromGroup", "triggerEvent", "controls", "trace", "disableScript",
            "importScript", "curRating", "gfSpeed", "endSong", "startCountdown", "executeEvent", "SONG", "create", "postCreate", "update",
            "postUpdate", "beatHit", "stepHit", "measureHit", "destroy", "CustomShader", "BlendMode", "FlxAxes", "FlxPoint", "Json", "Lambda",
            "Date", "Xml", "camZoomingStrength", "camZoomingInterval", "maxCamZoom", "defaultHudZoom", "comboGroup", "ratingNum"
        )

        private val reservedWords = setOf(
            "abstract", "break", "case", "cast", "catch", "class", "continue", "default", "do", "dynamic", "else", "enum", "extends", "extern",
            "false", "final", "for", "function", "if", "implements", "import", "in", "inline", "interface", "macro", "new", "null", "operator",
            "overload", "override", "package", "private", "public", "return", "static", "super", "switch", "this", "throw", "true", "try",
            "typedef", "untyped", "using", "var", "while", "event"
        )

        private val gameObjects = mapOf(
            "boyfriend" to "boyfriend", "bf" to "boyfriend", "boyfriendGroup" to "boyfriend",
            "dad" to "dad", "dadGroup" to "dad",
            "gf" to "gf", "gfGroup" to "gf", "girlfriend" to "gf",
            "camGame" to "camGame", "camHUD" to "camHUD",
            "iconP1" to "iconP1", "iconP2" to "iconP2", "healthBar" to "healthBar", "scoreTxt" to "scoreTxt",
            "camFollow" to "camFollow", "playerStrums" to "playerStrums", "opponentStrums" to "cpuStrums",
            "comboGroup" to "comboGroup", "inst" to "inst", "vocals" to "vocals"
        )

        private val playStateFields = setOf(
            "health", "songScore", "songMisses", "combo", "defaultCamZoom", "camZooming", "camZoomingMult", "songSpeed", "curSection", "curDecBeat",
            "curDecStep", "curBeat", "curStep", "inCutscene", "startedCountdown", "generatedMusic", "endingSong", "startingSong", "paused",
            "gfSpeed", "ratingPercent", "ratingName", "songHits", "cpuControlled", "practiceMode", "isCameraOnForcedPos", "cameraSpeed",
            "canPause", "canReset", "playbackRate", "healthGain", "healthLoss", "songLength", "boyfriendCameraOffset", "opponentCameraOffset",
            "girlfriendCameraOffset", "camZoomingDecay", "introSoundsSuffix", "skipCountdown", "skipArrowStartTween", "spawnTime", "camOther"
        )

        private val unsupported = PsychApi.notImplemented - setOf("runHaxeCode", "runHaxeFunction")
    }

    private val boolApis = setOf(
        "stringStartsWith", "stringEndsWith", "luaSpriteExists", "luaTextExists", "luaSoundExists", "keyJustPressed", "keyPressed", "keyReleased",
        "keyboardJustPressed", "keyboardPressed", "keyboardReleased", "mouseClicked", "mousePressed", "mouseReleased", "checkFileExists",
        "getRandomBool", "isRunning", "anyGamepadJustPressed", "anyGamepadPressed", "anyGamepadReleased", "gamepadJustPressed", "gamepadPressed",
        "gamepadReleased", "flushSaveData", "objectsOverlap", "getSoundExists"
    )

    override fun boolCall(call: Expr.Call): Boolean = (call.function as? Expr.Name)?.name?.let { it in boolApis && !isLuaName(it) } == true

    fun convert(source: String): CodenameScriptOut {
        val result = transpile(source) ?: return CodenameScriptOut("", issues.toList(), emptyList())
        val methods = globalMethods()
        val allHooks = LinkedHashMap(hooks)
        if (needsDefaults) {
            allHooks["postCreate"] = listOf(
                "psychDefaults = {boyfriendX: boyfriend.x, boyfriendY: boyfriend.y, opponentX: dad.x, opponentY: dad.y, girlfriendX: gf != null ? gf.x : 0.0, girlfriendY: gf != null ? gf.y : 0.0,",
                "  playerStrumX: [for (s in playerStrums.members) s.x], playerStrumY: [for (s in playerStrums.members) s.y], opponentStrumX: [for (s in cpuStrums.members) s.x], opponentStrumY: [for (s in cpuStrums.members) s.y]};"
            ) + (hooks["postCreate"] ?: emptyList())
        }
        val adapters = PsychCodenameAdapters(methods.toSet(), lua = true, hooks = allHooks)
        val adapterText = adapters.write()
        cneHelpers += adapters.helpers
        adapters.warnings.forEach { issue(0, it) }

        val out = StringBuilder()
        imports += PsychCodenameHelpers.imports(cneHelpers)
        imports.forEach { out.append("import ").append(it).append(";\n") }
        if (imports.isNotEmpty()) out.append('\n')
        createdTags.forEach { out.append("public var ").append(it).append(":Dynamic;\n") }
        if (needsDefaults) out.append("var psychDefaults:Dynamic = null;\n")
        val bootLines = result.boot.lines().map { it.trimEnd() }.toMutableList()
        val declared = linkedSetOf<String>()
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
        fieldNames.forEach { f ->
            if (f in inline) return@forEach
            out.append("var ").append(f).append(":Dynamic = null;\n")
            declared += f
        }
        val bootOut = bootLines.mapIndexedNotNull { index, line ->
            val m = Regex("^([A-Za-z_][A-Za-z0-9_]*) = (.+);$").find(line)
            if (m != null && inline[m.groupValues[1]] == m.groupValues[2] && firstUse[m.groupValues[1]] == index) "var ${m.groupValues[1]} = ${m.groupValues[2]};" else line
        }
        bootOut.filter { it.trim() != "null;" }.forEach { out.append(it).append('\n') }
        result.methods.lines().filter { it.trim() != "null;" }.forEach { out.append(it.removePrefix("  ")).append('\n') }
        hoisted.forEach { out.append('\n').append(it.trimEnd()).append('\n') }
        adapters.fields.forEach { out.append(it).append('\n') }
        out.append(adapterText)
        val luaHelpers = StringBuilder()
        Helpers.emit(helpers) { luaHelpers.append(it).append('\n') }
        out.append(luaHelpers.toString().replace("Reflect.getClassNameOf(t) == \"Unknown\"", "Type.getClass(t) == null").replace("Reflect.getClassNameOf(replacement) == \"Unknown\"", "Type.getClass(replacement) == null"))
        PsychCodenameHelpers.emit(cneHelpers) { out.append(it).append('\n') }
        val text = out.toString().replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
        val callbacks = methods.filter { it in PsychCodenameAdapters.callbacks || it in PsychCodenameAdapters.unsupported }
        return CodenameScriptOut(text, issues.sortedBy { it.line }, callbacks)
    }

    private fun safe(name: String): String = if (name in reservedWords || name in codenameNames || name.startsWith("psych") || name.startsWith("lua") || name.startsWith("__")) "v_$name" else name

    override fun emitName(key: String): String {
        val name = key.substring(2)
        if (key.startsWith("g_") && key in methodNames) {
            PsychCodenameAdapters.collisions[name]?.let { return it }
            if (name in PsychHxToCodename.generatedCallbacks) return "script" + name.replaceFirstChar { it.uppercaseChar() }
            if (name in PsychCodenameAdapters.callbacks || name in PsychCodenameAdapters.unsupported) return name
        }
        return safe(name)
    }

    override fun localName(luaName: String): String = safe(luaName)

    override fun constantRef(name: String, line: Int): String = if (name == "Function_Continue") "false" else "true"

    private fun use(vararg names: String) {
        cneHelpers += names
    }

    override fun variableRef(name: String, line: Int): String {
        Regex("^default(Player|Opponent)Strum([XY])([0-9])$").find(name)?.let { m ->
            needsDefaults = true
            return "psychDefaults.${m.groupValues[1].lowercase()}Strum${m.groupValues[2]}[${m.groupValues[3]}]"
        }
        return when (name) {
            "curBeat" -> "curBeat"
            "curStep" -> "curStep"
            "curSection" -> "curMeasure"
            "curDecBeat" -> "curBeatFloat"
            "curDecStep" -> "curStepFloat"
            "songPosition" -> "Conductor.songPosition"
            "bpm", "curBpm" -> "Conductor.bpm"
            "crochet" -> "Conductor.crochet"
            "stepCrochet" -> "Conductor.stepCrochet"
            "songLength" -> "inst.length"
            "songName", "loadedSongName" -> "PlayState.SONG.meta.name"
            "songPath", "loadedSongPath" -> "PlayState.instance.curSongID"
            "startedCountdown" -> "startedCountdown"
            "seenCutscene" -> "PlayState.seenCutscene"
            "isStoryMode" -> "PlayState.isStoryMode"
            "difficulty" -> "PlayState.SONG.meta.difficulties.indexOf(PlayState.difficulty)"
            "difficultyName", "difficultyPath", "difficultyNameTranslation" -> "PlayState.difficulty"
            "weekRaw", "week" -> "(PlayState.storyWeek == null ? \"\" : PlayState.storyWeek.id)"
            "deaths" -> "PlayState.deathCounter"
            "boyfriendName" -> "boyfriend.curCharacter"
            "dadName" -> "dad.curCharacter"
            "gfName" -> "(gf == null ? \"\" : gf.curCharacter)"
            "curStage" -> "PlayState.instance.curStage"
            "mustHitSection" -> {
                use("focus")
                "(psychFocus() == 1)"
            }
            "gfSection" -> {
                use("focus")
                "(psychFocus() == 2)"
            }
            "altAnim" -> "cpuStrums.altAnim"
            "downscroll" -> "Options.downscroll"
            "middlescroll" -> "Options.middlescroll"
            "ghostTapping" -> "Options.ghostTapping"
            "hideHud" -> "false"
            "timeBarType" -> "\"Disabled\""
            "scoreZoom" -> "true"
            "cameraZoomOnBeat" -> "Options.camZoomOnBeat"
            "flashingLights" -> "Options.flashingMenu"
            "noteOffset" -> "Options.songOffset"
            "healthBarAlpha" -> "healthBar.alpha"
            "noResetButton" -> "false"
            "lowQuality" -> "Options.lowMemoryMode"
            "shadersEnabled" -> "Options.gameplayShaders"
            "framerate" -> "Options.framerate"
            "scriptName" -> "\"script\""
            "currentModDirectory", "modFolder" -> "\"\""
            "buildTarget" -> "\"android\""
            "version" -> "\"1.0.4\""
            "screenWidth" -> "FlxG.width"
            "screenHeight" -> "FlxG.height"
            "inGameOver" -> "(FlxG.state.subState != null && Std.isOfType(FlxG.state.subState, GameOverSubstate))".also { imports += "funkin.game.GameOverSubstate" }
            "score" -> "songScore"
            "misses" -> "misses"
            "hits", "totalNotesHit", "totalPlayed" -> "ratingNum"
            "combo" -> "combo"
            "rating" -> "accuracy"
            "ratingName", "ratingFC" -> "curRating.rating"
            "botPlay" -> "playerStrums.cpu"
            "practice" -> "false"
            "playbackRate" -> "1"
            "healthGainMult", "healthLossMult" -> "1"
            "instakillOnMiss", "guitarHeroSustains" -> "false"
            "luaDebugMode", "luaDeprecatedWarnings" -> "false"
            "splashAlpha" -> "1"
            "splashSkin", "noteSkin", "splashSkinPostfix", "noteSkinPostfix" -> "\"\""
            "chartPath" -> "\"\""
            "cameraX" -> "camFollow.x"
            "cameraY" -> "camFollow.y"
            "scrollSpeed" -> "scrollSpeed"
            "hasVocals" -> "PlayState.SONG.meta.needsVoices"
            "defaultBoyfriendX", "defaultBoyfriendY", "defaultOpponentX", "defaultOpponentY", "defaultGirlfriendX", "defaultGirlfriendY" -> {
                needsDefaults = true
                "psychDefaults." + name.removePrefix("default").replaceFirstChar { it.lowercaseChar() }
            }
            else -> {
                issue(line, "Unknown variable '$name' (not defined in this script) became null.")
                "null"
            }
        }
    }

    override fun printCall(args: List<Expr>, line: Int): String = "trace(${args.joinToString(" + \", \" + ") { expr(it) }.ifEmpty { "\"\"" }})"

    private fun str(a: List<Expr>, i: Int): String? = (a.getOrNull(i) as? Expr.Str)?.value

    private fun lit(a: List<Expr>, i: Int, fallback: String): String = a.getOrNull(i)?.takeIf { it !is Expr.Nil }?.let { expr(it) } ?: fallback

    private fun isLuaName(name: String): Boolean = "g_$name" in methodNames || "l_$name" in methodNames || name in assignedGlobals || name in chunkLocals

    private fun tagIdentifier(tag: String): String? =
        if (identifier.matches(tag) && tag !in reservedWords && tag !in codenameNames && !tag.startsWith("psych") && !isLuaName(tag)) tag else null

    private fun objectRef(e: Expr?): String {
        if (e == null || e is Expr.Nil) return "null"
        val tag = (e as? Expr.Str)?.value
        if (tag == null) {
            use("object")
            return "psychObject(${expr(e)})"
        }
        if (tag.contains('.') || tag.contains('[')) return path(tag, e.line) ?: run {
            use("property")
            "getProperty(${HxSource.hxString(tag)})"
        }
        return tagRef(tag)
    }

    private fun tagRef(tag: String): String {
        val id = tagIdentifier(tag)
        if (id != null && (tag in createdTags || tag in knownTags)) return id
        gameObjects[tag]?.let { return it }
        if (tag == "camOther") {
            use("camera")
            return "psychCamera(\"other\")"
        }
        use("object")
        return "psychObject(${HxSource.hxString(tag)})"
    }

    private fun path(raw: String, line: Int): String? {
        val parts = raw.split('.')
        if (parts.any { p -> !Regex("^[A-Za-z_][A-Za-z0-9_]*(\\[[0-9]+\\])*$").matches(p) }) return null
        val first = parts.first()
        val firstName = first.substringBefore('[')
        val firstIndex = first.removePrefix(firstName)
        val head = when {
            tagIdentifier(firstName) != null && (firstName in createdTags || firstName in knownTags) -> firstName
            gameObjects.containsKey(firstName) -> gameObjects.getValue(firstName)
            firstName == "camOther" -> {
                use("camera")
                "psychCamera(\"other\")"
            }
            firstName in playStateFields -> {
                val mapped = PsychHxToCodename.fieldMap[firstName]
                if (firstName in setOf("camZoomingDecay", "introSoundsSuffix", "skipCountdown", "skipArrowStartTween", "spawnTime", "boyfriendCameraOffset", "opponentCameraOffset", "girlfriendCameraOffset")) {
                    issue(line, "$firstName is Psych-only; it does nothing in Codename.")
                    return "null"
                }
                when {
                    mapped == null -> firstName
                    mapped.startsWith("PlayState.instance.") -> mapped.removePrefix("PlayState.instance.")
                    else -> mapped
                }
            }
            firstName in setOf("timeBar", "timeTxt", "botplayTxt", "luaDebugGroup") -> {
                issue(line, "$firstName does not exist in Codename.")
                return "null"
            }
            else -> {
                use("object")
                "psychObject(${HxSource.hxString(firstName)})"
            }
        }
        val sb = StringBuilder(head).append(firstIndex)
        parts.drop(1).forEach { part ->
            val name = part.substringBefore('[')
            val renamed = when (name) {
                "imageFile" -> "sprite"
                "healthIcon" -> "icon"
                "singDuration" -> "holdTime"
                "danceEveryNumBeats" -> "beatInterval"
                "ignoreNote" -> "avoid"
                else -> name
            }
            sb.append('.').append(renamed).append(part.removePrefix(name))
        }
        return sb.toString()
    }

    private fun groupItem(group: Expr?, index: Expr?): String {
        val g = (group as? Expr.Str)?.value
        val idx = index?.let { expr(it) } ?: "0"
        return when (g) {
            "playerStrums" -> "playerStrums.members[$idx]"
            "opponentStrums" -> "cpuStrums.members[$idx]"
            else -> {
                use("group")
                "psychGroupItem(${group?.let { expr(it) } ?: "\"\""}, $idx)"
            }
        }
    }

    private fun fieldPath(field: Expr?): String? {
        val f = (field as? Expr.Str)?.value ?: return null
        if (!Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$").matches(f)) return null
        return f.split('.').joinToString(".") { PsychHxToCodename.methodMap[it] ?: when (it) { "ignoreNote" -> "avoid"; "imageFile" -> "sprite"; else -> it } }
    }

    private fun color(e: Expr?): String {
        if (e == null || e is Expr.Nil) return "0xFFFFFFFF"
        val s = (e as? Expr.Str)?.value
        if (s != null) {
            val hex = s.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            if (Regex("^[0-9A-Fa-f]{6}$").matches(hex)) return "0xFF" + hex.uppercase()
            if (Regex("^[0-9A-Fa-f]{8}$").matches(hex)) return "0x" + hex.uppercase()
        }
        use("color")
        return "psychColor(${expr(e)})"
    }

    private fun easeArg(a: List<Expr>, i: Int): String = a.getOrNull(i)?.takeIf { it !is Expr.Nil }?.let { expr(it) } ?: "\"linear\""

    private fun tween(a: List<Expr>, field: String, valueIndex: Int = 2): String {
        use("tween")
        val target = objectRef(a.getOrNull(1))
        return "psychTween(${lit(a, 0, "\"\"")}, $target, {$field: ${lit(a, valueIndex, "0")}}, ${lit(a, valueIndex + 1, "1")}, ${easeArg(a, valueIndex + 2)})"
    }

    private fun noteTween(a: List<Expr>, field: String): String {
        use("tween", "strum")
        return "psychTween(${lit(a, 0, "\"\"")}, psychStrum(Std.int(${lit(a, 1, "0")})), {$field: ${lit(a, 2, "0")}}, ${lit(a, 3, "1")}, ${easeArg(a, 4)})"
    }

    private fun tableToObject(e: Expr?): String {
        val table = e as? Expr.Table ?: return e?.let { expr(it) } ?: "{}"
        if (table.fields.all { it is TableField.Named }) {
            return "{" + table.fields.joinToString(", ") { f -> f as TableField.Named; "${f.name}: ${expr(f.value)}" } + "}"
        }
        return expr(e)
    }

    private fun haxeCode(code: String, line: Int): String {
        val converter = PsychHxToCodename(scope, inline = true)
        val converted = converter.convert(code)
        cneHelpers += converter.usedHelpers()
        converted.issues.forEach { issue(line, "runHaxeCode: ${it.message}") }
        val tokens = HxSource.tokenize(converted.source)
        val segs = HxSource.segments(tokens)
        val statements = StringBuilder()
        segs.forEach { seg ->
            when (seg.kind) {
                "import" -> imports += seg.head.filter { it.kind != com.brenninhotools.vconverter.script.hx.HxKind.SPACE }.drop(1).takeWhile { !it.isPunct(";") }.joinToString("") { it.text }
                "function" -> hoisted += "function " + seg.name + HxSource.text(seg.head).trim() + "\n{\n" + HxSource.indentBody(HxSource.text(seg.body)).joinToString("\n") + "\n}"
                else -> statements.append(HxSource.text(seg.body).trim().let { if (seg.kind == "var") "var ${seg.name}" + (if (seg.body.isNotEmpty()) " = $it" else "") + ";" else it }).append('\n')
            }
        }
        val body = statements.toString().trim()
        if (body.isEmpty()) return "null"
        val name = "haxeCode${++haxeBlocks}"
        hoisted += "function $name():Dynamic\n{\n" + HxSource.indentBody(body).joinToString("\n") + "\n  return null;\n}"
        return "$name()"
    }

    override fun apiCall(name: String, args: List<Expr>, line: Int): String {
        val a = args
        fun arg(i: Int, fallback: String = "null") = lit(a, i, fallback)
        fun obj(i: Int) = objectRef(a.getOrNull(i))
        return when (name) {
            "makeLuaSprite", "makeAnimatedLuaSprite" -> {
                use("sprite")
                val animated = name == "makeAnimatedLuaSprite"
                val image = a.getOrNull(1)?.takeIf { it !is Expr.Nil }?.let { expr(it) } ?: "null"
                val make = "psychMakeSprite($image, ${arg(2, "0")}, ${arg(3, "0")}, $animated)"
                val tag = str(a, 0)
                val id = tag?.let { tagIdentifier(it) }
                if (id != null) {
                    createdTags += id
                    "$id = $make"
                } else {
                    use("object")
                    "psychRegister(${arg(0)}, $make)"
                }
            }
            "makeFlxAnimateSprite" -> {
                use("sprite")
                val folder = a.getOrNull(3)?.takeIf { it !is Expr.Nil }?.let { expr(it) } ?: "null"
                val make = "psychMakeSprite($folder, ${arg(1, "0")}, ${arg(2, "0")}, true)"
                val id = str(a, 0)?.let { tagIdentifier(it) }
                if (id != null) {
                    createdTags += id
                    "$id = $make"
                } else {
                    use("object")
                    "psychRegister(${arg(0)}, $make)"
                }
            }
            "loadAnimateAtlas" -> "${obj(0)}.loadSprite(Paths.image(${arg(1)}))"
            "addAnimationBySymbol" -> "${obj(0)}.anim.addBySymbol(${arg(1)}, ${arg(2)}, ${arg(3, "24")}, ${arg(4, "false")})"
            "addAnimationBySymbolIndices" -> "${obj(0)}.anim.addBySymbolIndices(${arg(1)}, ${arg(2)}, ${indexList(a.getOrNull(3), line)}, ${arg(4, "24")}, ${arg(5, "false")})"
            "instanceArg" -> {
                val cls = str(a, 1)
                if (cls != null) {
                    imports += cls
                    "${cls.substringAfterLast('.')}.${str(a, 0) ?: "null"}"
                } else obj(0)
            }
            "addCameraFollowPoint" -> "{ camFollow.x += ${arg(0, "0")}; camFollow.y += ${arg(1, "0")}; }"
            "setCameraScroll" -> "FlxG.camera.scroll.set(${arg(0, "0")} - FlxG.width / 2, ${arg(1, "0")} - FlxG.height / 2)"
            "addCameraScroll" -> "FlxG.camera.scroll.add(${arg(0, "0")}, ${arg(1, "0")})"
            "getCameraScrollX" -> "(FlxG.camera.scroll.x + FlxG.width / 2)"
            "getCameraScrollY" -> "(FlxG.camera.scroll.y + FlxG.height / 2)"
            "gamepadAnalogX", "gamepadAnalogY" -> {
                use("gamepad")
                "psychGamepadAxis(Std.int(${arg(0, "0")}), ${arg(1, "true")}, ${name.endsWith("X")})"
            }
            "gamepadJustPressed", "gamepadPressed", "gamepadReleased" -> {
                use("gamepad")
                val list = when (name) {
                    "gamepadJustPressed" -> "justPressed"
                    "gamepadPressed" -> "pressed"
                    else -> "justReleased"
                }
                "psychGamepad(Std.int(${arg(0, "0")}), \"$list\", ${arg(1, "\"\"")})"
            }
            "anyGamepadJustPressed", "anyGamepadPressed", "anyGamepadReleased" -> {
                use("gamepad")
                val list = when (name) {
                    "anyGamepadJustPressed" -> "justPressed"
                    "anyGamepadPressed" -> "pressed"
                    else -> "justReleased"
                }
                "psychAnyGamepad(\"$list\", ${arg(0, "\"\"")})"
            }
            "getPixelColor" -> "${obj(0)}.pixels.getPixel32(Std.int(${arg(1, "0")}), Std.int(${arg(2, "0")}))"
            "loadMultipleFrames" -> {
                val list = (a.getOrNull(1) as? Expr.Table)?.fields?.filterIsInstance<TableField.Positional>()?.map { expr(it.value) }
                if (list != null && list.isNotEmpty()) "{ var frames:Dynamic = Paths.getFrames(Paths.image(${list.first()}));" + list.drop(1).joinToString("") { " frames.addAtlas(Paths.getFrames(Paths.image($it)));" } + " ${obj(0)}.frames = frames; }" else "null"
            }
            "updateHitboxFromGroup" -> "{ var g:Dynamic = getProperty(${arg(0)}); var list:Dynamic = g != null && g.members != null ? g.members : g; if (list != null && list[Std.int(${arg(1, "0")})] != null) list[Std.int(${arg(1, "0")})].updateHitbox(); }"
            "directoryFileList" -> "Paths.getFolderContent(${arg(0)})"
            "loadSong" -> {
                issue(line, "loadSong() became PlayState.loadSong + a new PlayState; the difficulty number is mapped to easy/normal/hard.")
                "{ var diffs:Array<String> = [\"easy\", \"normal\", \"hard\"]; var n:Int = Std.int(${arg(1, "-1")}); PlayState.loadSong(${arg(0, "PlayState.SONG.meta.name")}, n >= 0 && n < diffs.length ? diffs[n] : PlayState.difficulty); FlxG.switchState(new PlayState()); }"
            }
            "makeLuaText" -> {
                use("text")
                val make = "psychMakeText(${arg(1, "\"\"")}, ${arg(2, "0")}, ${arg(3, "0")}, ${arg(4, "0")})"
                val id = str(a, 0)?.let { tagIdentifier(it) }
                if (id != null) {
                    createdTags += id
                    "$id = $make"
                } else {
                    use("object")
                    "psychRegister(${arg(0)}, $make)"
                }
            }
            "makeGraphic", "luaSpriteMakeGraphic" -> "${obj(0)}.makeGraphic(Std.int(${arg(1, "1")}), Std.int(${arg(2, "1")}), ${color(a.getOrNull(3))})"
            "loadGraphic" -> "${obj(0)}.loadGraphic(Paths.image(${arg(1)}), ${if (a.size > 2) "true, Std.int(${arg(2)}), Std.int(${arg(3, "0")})" else "false"})"
            "loadFrames" -> "${obj(0)}.frames = Paths.getFrames(${arg(1)})"
            "addLuaSprite" -> {
                use("sprite")
                "psychAddSprite(${obj(0)}, ${arg(1, "false")})"
            }
            "addLuaText" -> "add(${obj(0)})"
            "removeLuaSprite", "removeLuaText" -> {
                val target = obj(0)
                val destroy = a.getOrNull(1)
                if (destroy is Expr.Bool && !destroy.value) "remove($target, true)" else "{ var o:Dynamic = $target; if (o != null) { remove(o, true); o.destroy(); } }"
            }
            "luaSpriteExists", "luaTextExists" -> "(${obj(0)} != null)"
            "addAnimationByPrefix", "luaSpriteAddAnimationByPrefix" -> {
                use("anim")
                "psychAddAnim(${obj(0)}, ${arg(1)}, ${arg(2)}, ${arg(3, "24")}, ${arg(4, "true")})"
            }
            "addAnimationByIndices", "luaSpriteAddAnimationByIndices" -> {
                use("anim")
                "psychAddAnim(${obj(0)}, ${arg(1)}, ${arg(2)}, ${arg(4, "24")}, ${arg(5, "false")}, ${arg(3, "\"\"")})"
            }
            "addAnimationByIndicesLoop" -> {
                use("anim")
                "psychAddAnim(${obj(0)}, ${arg(1)}, ${arg(2)}, ${arg(4, "24")}, true, ${arg(3, "\"\"")})"
            }
            "addAnimation" -> "${obj(0)}.animation.add(${arg(1)}, ${arg(2, "[]")}, ${arg(3, "24")}, ${arg(4, "true")})"
            "addOffset" -> {
                use("anim")
                "psychAddOffset(${obj(0)}, ${arg(1)}, ${arg(2, "0")}, ${arg(3, "0")})"
            }
            "playAnim", "objectPlayAnimation", "luaSpritePlayAnimation" -> {
                use("anim")
                "psychPlayAnim(${obj(0)}, ${arg(1)}, ${arg(2, "false")}, ${arg(3, "false")}, ${arg(4, "0")})"
            }
            "setScrollFactor", "setLuaSpriteScrollFactor" -> "${obj(0)}.scrollFactor.set(${arg(1, "1")}, ${arg(2, "1")})"
            "scaleObject", "scaleLuaSprite" -> {
                val target = obj(0)
                val hitbox = a.getOrNull(3)
                if (hitbox is Expr.Bool && !hitbox.value) "$target.scale.set(${arg(1, "1")}, ${arg(2, "1")})"
                else if (identifier.matches(target)) "$target.scale.set(${arg(1, "1")}, ${arg(2, "1")}); $target.updateHitbox()"
                else "{ var o:Dynamic = $target; o.scale.set(${arg(1, "1")}, ${arg(2, "1")}); o.updateHitbox(); }"
            }
            "setGraphicSize" -> {
                val target = obj(0)
                val hitbox = a.getOrNull(3)
                if (hitbox is Expr.Bool && !hitbox.value) "$target.setGraphicSize(${arg(1, "0")}, ${arg(2, "0")})"
                else if (identifier.matches(target)) "$target.setGraphicSize(${arg(1, "0")}, ${arg(2, "0")}); $target.updateHitbox()"
                else "{ var o:Dynamic = $target; o.setGraphicSize(${arg(1, "0")}, ${arg(2, "0")}); o.updateHitbox(); }"
            }
            "addToGroup" -> {
                use("group")
                val index = a.getOrNull(2)
                "{ var g:Dynamic = getProperty(${arg(0)}); var o:Dynamic = ${obj(1)}; if (g != null && o != null) ${if (index == null || index is Expr.Nil) "g.add(o)" else "g.insert(Std.int(${arg(2)}), o)"}; }"
            }
            "removeFromGroup" -> {
                use("group")
                "{ var g:Dynamic = getProperty(${arg(0)}); var o:Dynamic = ${if (a.getOrNull(2) != null && a[2] !is Expr.Nil) obj(2) else "psychGroupItem(${arg(0)}, ${arg(1, "0")})"}; if (g != null && o != null) { g.remove(o, true); if (${arg(3, "true")}) o.destroy(); } }"
            }
            "createInstance" -> {
                val cls = str(a, 1)
                val list = (a.getOrNull(2) as? Expr.Table)?.fields?.filterIsInstance<TableField.Positional>()?.map { expr(it.value) } ?: emptyList()
                val make = if (cls != null && (cls.startsWith("flixel.") || cls.startsWith("openfl."))) {
                    imports += cls
                    "new ${cls.substringAfterLast('.')}(${list.joinToString(", ")})"
                } else "Type.createInstance(Type.resolveClass(${arg(1)}), [${list.joinToString(", ")}])"
                val id = str(a, 0)?.let { tagIdentifier(it) }
                if (id != null) {
                    createdTags += id
                    "$id = $make"
                } else {
                    use("object")
                    "psychRegister(${arg(0)}, $make)"
                }
            }
            "addInstance" -> {
                use("sprite")
                "psychAddSprite(${obj(0)}, ${arg(1, "false")})"
            }
            "callMethodFromClass" -> {
                val cls = str(a, 0)
                val method = str(a, 1)
                val list = (a.getOrNull(2) as? Expr.Table)?.fields?.filterIsInstance<TableField.Positional>()?.map { expr(it.value) } ?: emptyList()
                when {
                    cls == "backend.StageData" && method == "getStageFile" -> "{defaultZoom: PlayState.instance.stage.defaultZoom}"
                    cls != null && method != null && (cls.startsWith("flixel.") || cls.startsWith("openfl.")) -> {
                        imports += cls
                        "${cls.substringAfterLast('.')}.$method(${list.joinToString(", ")})"
                    }
                    else -> {
                        issue(line, "callMethodFromClass($cls, $method) calls a Psych-only class; the call was removed.")
                        "null"
                    }
                }
            }
            "updateHitbox" -> "${obj(0)}.updateHitbox()"
            "screenCenter" -> {
                val axes = when (str(a, 1)?.lowercase()) {
                    "x" -> "FlxAxes.X"
                    "y" -> "FlxAxes.Y"
                    else -> "FlxAxes.XY"
                }
                "${obj(0)}.screenCenter($axes)"
            }
            "setObjectCamera", "setLuaSpriteCamera" -> {
                use("camera")
                "${obj(0)}.cameras = [psychCamera(${arg(1, "\"game\"")})]"
            }
            "setObjectOrder" -> "{ var o:Dynamic = ${obj(0)}; remove(o, true); insert(Std.int(${arg(1, "0")}), o); }"
            "getObjectOrder" -> "members.indexOf(${obj(0)})"
            "setBlendMode" -> "${obj(0)}.blend = BlendMode.fromString(${arg(1, "\"normal\"")})"
            "getMidpointX" -> "${obj(0)}.getMidpoint().x"
            "getMidpointY" -> "${obj(0)}.getMidpoint().y"
            "getGraphicMidpointX" -> "${obj(0)}.getGraphicMidpoint().x"
            "getGraphicMidpointY" -> "${obj(0)}.getGraphicMidpoint().y"
            "getScreenPositionX" -> {
                use("camera")
                "${obj(0)}.getScreenPosition(null, psychCamera(${arg(1, "\"game\"")})).x"
            }
            "getScreenPositionY" -> {
                use("camera")
                "${obj(0)}.getScreenPosition(null, psychCamera(${arg(1, "\"game\"")})).y"
            }
            "objectsOverlap" -> "FlxG.overlap(${obj(0)}, ${obj(1)})"
            "precacheImage" -> "FlxG.bitmap.add(Paths.image(${arg(0)}))"
            "precacheSound" -> "FlxG.sound.load(Paths.sound(${arg(0)}))"
            "precacheMusic" -> "FlxG.sound.load(Paths.music(${arg(0)}))"
            "setTextString" -> "${obj(0)}.text = ${arg(1, "\"\"")}"
            "getTextString" -> "${obj(0)}.text"
            "setTextSize" -> "${obj(0)}.size = Std.int(${arg(1, "16")})"
            "getTextSize" -> "${obj(0)}.size"
            "setTextWidth" -> "${obj(0)}.fieldWidth = ${arg(1, "0")}"
            "getTextWidth" -> "${obj(0)}.fieldWidth"
            "setTextHeight" -> "${obj(0)}.fieldHeight = ${arg(1, "0")}"
            "setTextColor" -> "${obj(0)}.color = ${color(a.getOrNull(1))}"
            "setTextFont" -> "${obj(0)}.font = Paths.font(${arg(1)})"
            "getTextFont" -> "${obj(0)}.font"
            "setTextBorder" -> {
                imports += "flixel.text.FlxTextBorderStyle"
                "${obj(0)}.setBorderStyle(FlxTextBorderStyle.OUTLINE, ${color(a.getOrNull(2))}, ${arg(1, "1")})"
            }
            "setTextAlignment" -> "${obj(0)}.alignment = ${arg(1, "\"left\"")}"
            "setTextItalic" -> "${obj(0)}.italic = ${arg(1, "true")}"
            "setTextAutoSize" -> "${obj(0)}.autoSize = ${arg(1, "true")}"
            "getProperty", "getPropertyLuaSprite" -> {
                val p = if (name == "getPropertyLuaSprite") str(a, 0)?.let { t -> str(a, 1)?.let { "$t.$it" } } else str(a, 0)
                p?.let { path(it, line) } ?: run {
                    use("property")
                    if (name == "getPropertyLuaSprite") "getProperty(${arg(0)} + \".\" + ${arg(1)})" else "getProperty(${arg(0)})"
                }
            }
            "setProperty", "setPropertyLuaSprite" -> {
                val p = if (name == "setPropertyLuaSprite") str(a, 0)?.let { t -> str(a, 1)?.let { "$t.$it" } } else str(a, 0)
                val valueIndex = if (name == "setPropertyLuaSprite") 2 else 1
                val value = arg(valueIndex)
                when (p) {
                    "isCameraOnForcedPos" -> "if ($value) curCameraTarget = -1"
                    "cameraSpeed" -> "FlxG.camera.followLerp = 0.04 * $value"
                    else -> p?.let { path(it, line) }?.let { if (it == "null") "null" else "$it = $value" } ?: run {
                        use("property")
                        if (name == "setPropertyLuaSprite") "setProperty(${arg(0)} + \".\" + ${arg(1)}, $value)" else "setProperty(${arg(0)}, $value)"
                    }
                }
            }
            "getPropertyFromClass", "setPropertyFromClass" -> classProperty(name == "setPropertyFromClass", a, line)
            "getPropertyFromGroup" -> {
                val f = fieldPath(a.getOrNull(2))
                if (f != null) "${groupItem(a.getOrNull(0), a.getOrNull(1))}.$f" else {
                    use("group")
                    "getPropertyFromGroup(${arg(0)}, ${arg(1)}, ${arg(2)})"
                }
            }
            "setPropertyFromGroup" -> {
                val f = fieldPath(a.getOrNull(2))
                if (f != null) "${groupItem(a.getOrNull(0), a.getOrNull(1))}.$f = ${arg(3)}" else {
                    use("group")
                    "setPropertyFromGroup(${arg(0)}, ${arg(1)}, ${arg(2)}, ${arg(3)})"
                }
            }
            "setVar" -> {
                use("vars")
                "setVar(${arg(0)}, ${arg(1)})"
            }
            "getVar" -> {
                use("vars")
                "getVar(${arg(0)})"
            }
            "callMethod" -> {
                val method = str(a, 0)
                val list = (a.getOrNull(1) as? Expr.Table)?.fields?.filterIsInstance<TableField.Positional>()?.map { expr(it.value) }
                if (method != null && list != null && method.split('.').size >= 2) {
                    val target = method.substringBeforeLast('.')
                    "${path(target, line) ?: objectRef(Expr.Str(target, line))}.${method.substringAfterLast('.')}(${list.joinToString(", ")})"
                } else if (method != null && list != null) "${PsychHxToCodename.fieldMap[method] ?: method}(${list.joinToString(", ")})"
                else {
                    use("property")
                    "Reflect.callMethod(null, getProperty(${arg(0)}), ${arg(1, "[]")})"
                }
            }
            "doTweenX" -> tween(a, "x")
            "doTweenY" -> tween(a, "y")
            "doTweenAlpha" -> tween(a, "alpha")
            "doTweenAngle" -> tween(a, "angle")
            "doTweenZoom" -> {
                use("tween", "camera")
                "psychTween(${arg(0, "\"\"")}, psychCamera(${arg(1, "\"game\"")}), {zoom: ${arg(2, "1")}}, ${arg(3, "1")}, ${easeArg(a, 4)})"
            }
            "doTweenColor" -> {
                use("tween")
                "psychColorTween(${arg(0, "\"\"")}, ${obj(1)}, ${arg(3, "1")}, ${color(a.getOrNull(2))}, ${easeArg(a, 4)})"
            }
            "startTween" -> {
                use("tween")
                val options = a.getOrNull(4) as? Expr.Table
                val ease = options?.fields?.filterIsInstance<TableField.Named>()?.firstOrNull { it.name == "ease" }?.value?.let { expr(it) } ?: "\"linear\""
                "psychTween(${arg(0, "\"\"")}, ${obj(1)}, ${tableToObject(a.getOrNull(2))}, ${arg(3, "1")}, $ease, ${a.getOrNull(4)?.let { tableToObject(it) } ?: "null"})"
            }
            "cancelTween" -> {
                use("tween")
                "psychCancelTween(${arg(0)})"
            }
            "noteTweenX" -> noteTween(a, "x")
            "noteTweenY" -> noteTween(a, "y")
            "noteTweenAlpha" -> noteTween(a, "alpha")
            "noteTweenAngle" -> noteTween(a, "angle")
            "noteTweenDirection" -> {
                issue(line, "noteTweenDirection: Codename strums have no direction field; the tween changes angle instead.")
                noteTween(a, "angle")
            }
            "runTimer" -> {
                use("timer")
                "psychTimer(${arg(0)}, ${arg(1, "1")}, Std.int(${arg(2, "1")}))"
            }
            "cancelTimer" -> {
                use("timer")
                "psychCancelTimer(${arg(0)})"
            }
            "cameraShake" -> {
                use("camera")
                "psychCamera(${arg(0, "\"game\"")}).shake(${arg(1, "0.05")}, ${arg(2, "0.5")})"
            }
            "cameraFlash" -> {
                use("camera")
                "psychCamera(${arg(0, "\"game\"")}).flash(${color(a.getOrNull(1))}, ${arg(2, "1")}, null, ${arg(3, "false")})"
            }
            "cameraFade" -> {
                use("camera")
                "psychCamera(${arg(0, "\"game\"")}).fade(${color(a.getOrNull(1))}, ${arg(2, "1")}, false, null, ${arg(3, "false")})"
            }
            "cameraSetTarget" -> {
                use("cameraTarget")
                "psychCameraTarget(${arg(0, "\"bf\"")})"
            }
            "setCameraFollowPoint" -> "camFollow.setPosition(${arg(0, "0")}, ${arg(1, "0")})"
            "getCameraFollowX" -> "camFollow.x"
            "getCameraFollowY" -> "camFollow.y"
            "triggerEvent" -> {
                use("triggerEvent")
                "triggerEvent(${arg(0, "\"\"")}, ${arg(1, "\"\"")}, ${arg(2, "\"\"")})"
            }
            "characterPlayAnim" -> {
                use("character")
                "psychCharacter(${arg(0)}).playAnim(${arg(1)}, ${arg(2, "false")})"
            }
            "characterDance" -> {
                use("character")
                "psychCharacter(${arg(0)}).dance()"
            }
            "getCharacterX" -> {
                use("character")
                "psychCharacter(${arg(0)}).x"
            }
            "getCharacterY" -> {
                use("character")
                "psychCharacter(${arg(0)}).y"
            }
            "setCharacterX" -> {
                use("character")
                "psychCharacter(${arg(0)}).x = ${arg(1, "0")}"
            }
            "setCharacterY" -> {
                use("character")
                "psychCharacter(${arg(0)}).y = ${arg(1, "0")}"
            }
            "setHealth" -> "health = ${arg(0, "1")}"
            "getHealth" -> "health"
            "addHealth" -> "health += ${arg(0, "0")}"
            "setScore" -> "songScore = Std.int(${arg(0, "0")})"
            "addScore" -> "songScore += Std.int(${arg(0, "0")})"
            "setMisses" -> "misses = Std.int(${arg(0, "0")})"
            "addMisses" -> "misses += Std.int(${arg(0, "0")})"
            "setHits", "addHits" -> {
                issue(line, "$name() has no Codename equivalent (hits are counted per rating).")
                "null"
            }
            "getSongPosition" -> "Conductor.songPosition"
            "endSong" -> "endSong()"
            "restartSong" -> "FlxG.resetState()"
            "exitSong" -> "FlxG.switchState(PlayState.isStoryMode ? new StoryMenuState() : new FreeplayState())"
            "startCountdown" -> "startCountdown()"
            "setHealthBarColors" -> "{ healthBar.createFilledBar(${color(a.getOrNull(0))}, ${color(a.getOrNull(1))}); healthBar.updateBar(); }"
            "setTimeBarColors", "setRatingPercent", "setRatingName", "setRatingFC", "updateScoreText" -> {
                issue(line, "$name() has no Codename equivalent and was removed.")
                "null"
            }
            "playSound" -> {
                use("sound")
                "psychPlaySound(${arg(0)}, ${arg(1, "1")}${if (a.size > 2) ", ${arg(2)}" else ""})"
            }
            "playMusic" -> "FlxG.sound.playMusic(Paths.music(${arg(0)}), ${arg(1, "1")}, ${arg(2, "false")})"
            "stopSound" -> {
                use("sound")
                "{ var s:Dynamic = psychSound(${arg(0, "\"\"")}); if (s != null) s.stop(); }"
            }
            "pauseSound" -> {
                use("sound")
                "{ var s:Dynamic = psychSound(${arg(0, "\"\"")}); if (s != null) s.pause(); }"
            }
            "resumeSound" -> {
                use("sound")
                "{ var s:Dynamic = psychSound(${arg(0, "\"\"")}); if (s != null) s.play(); }"
            }
            "setSoundVolume" -> {
                use("sound")
                "{ var s:Dynamic = psychSound(${arg(0, "\"\"")}); if (s != null) s.volume = ${arg(1, "1")}; }"
            }
            "getSoundVolume" -> {
                use("sound")
                "psychSound(${arg(0, "\"\"")}).volume"
            }
            "getSoundTime" -> {
                use("sound")
                "psychSound(${arg(0, "\"\"")}).time"
            }
            "setSoundTime" -> {
                use("sound")
                "psychSound(${arg(0, "\"\"")}).time = ${arg(1, "0")}"
            }
            "getSoundPitch" -> {
                use("sound")
                "psychSound(${arg(0, "\"\"")}).pitch"
            }
            "setSoundPitch" -> {
                use("sound")
                "psychSound(${arg(0, "\"\"")}).pitch = ${arg(1, "1")}"
            }
            "soundFadeIn" -> {
                use("sound")
                "psychSound(${arg(0, "\"\"")}).fadeIn(${arg(1, "1")}, ${arg(2, "0")}, ${arg(3, "1")})"
            }
            "soundFadeOut" -> {
                use("sound")
                "psychSound(${arg(0, "\"\"")}).fadeOut(${arg(1, "1")}, ${arg(2, "0")})"
            }
            "soundFadeCancel" -> {
                use("sound")
                "{ var s:Dynamic = psychSound(${arg(0, "\"\"")}); if (s != null && s.fadeTween != null) s.fadeTween.cancel(); }"
            }
            "luaSoundExists" -> {
                use("sound")
                "(psychSound(${arg(0, "\"\"")}) != null)"
            }
            "musicFadeIn" -> "FlxG.sound.music.fadeIn(${arg(0, "1")}, ${arg(1, "0")}, ${arg(2, "1")})"
            "musicFadeOut" -> "FlxG.sound.music.fadeOut(${arg(0, "1")}, ${arg(1, "0")})"
            "getRandomInt" -> {
                if (a.size > 2) {
                    use("random")
                    "FlxG.random.int(Std.int(${arg(0, "0")}), Std.int(${arg(1, "2147483647")}), psychExcludes(${arg(2)}))"
                } else "FlxG.random.int(Std.int(${arg(0, "0")}), Std.int(${arg(1, "2147483647")}))"
            }
            "getRandomFloat" -> "FlxG.random.float(${arg(0, "0")}, ${arg(1, "1")})"
            "getRandomBool" -> "FlxG.random.bool(${arg(0, "50")})"
            "getColorFromHex", "getColorFromString", "getColorFromName" -> color(a.getOrNull(0))
            "stringStartsWith" -> "StringTools.startsWith(${arg(0, "\"\"")}, ${arg(1, "\"\"")})"
            "stringEndsWith" -> "StringTools.endsWith(${arg(0, "\"\"")}, ${arg(1, "\"\"")})"
            "stringSplit" -> "(${arg(0, "\"\"")}).split(${arg(1, "\"\"")})"
            "stringTrim" -> "StringTools.trim(${arg(0, "\"\"")})"
            "debugPrint" -> "trace(${a.joinToString(" + \", \" + ") { expr(it) }.ifEmpty { "\"\"" }})"
            "close" -> "disableScript()"
            "keyJustPressed" -> {
                use("keys")
                "psychKey(${arg(0)}, 0)"
            }
            "keyPressed" -> {
                use("keys")
                "psychKey(${arg(0)}, 1)"
            }
            "keyReleased" -> {
                use("keys")
                "psychKey(${arg(0)}, 2)"
            }
            "keyboardJustPressed", "keyboardPressed", "keyboardReleased" -> {
                val state = when (name) {
                    "keyboardJustPressed" -> "justPressed"
                    "keyboardPressed" -> "pressed"
                    else -> "justReleased"
                }
                val key = str(a, 0)
                if (key != null && identifier.matches(key)) "FlxG.keys.$state.${key.uppercase()}" else "Reflect.getProperty(FlxG.keys.$state, ${arg(0)})"
            }
            "mouseClicked", "mousePressed", "mouseReleased" -> {
                val button = str(a, 0)?.lowercase() ?: "left"
                val base = when (name) {
                    "mouseClicked" -> "justPressed"
                    "mousePressed" -> "pressed"
                    else -> "justReleased"
                }
                "FlxG.mouse.$base" + when (button) {
                    "right" -> "Right"
                    "middle" -> "Middle"
                    else -> ""
                }
            }
            "getMouseX" -> {
                use("camera")
                "FlxG.mouse.getScreenPosition(psychCamera(${arg(0, "\"game\"")})).x"
            }
            "getMouseY" -> {
                use("camera")
                "FlxG.mouse.getScreenPosition(psychCamera(${arg(0, "\"game\"")})).y"
            }
            "initSaveData" -> {
                use("save")
                "psychSave(${arg(0)}, ${arg(1, "null")})"
            }
            "getDataFromSave" -> {
                use("save")
                "psychGetSave(${arg(0)}, ${arg(1)}, ${arg(2)})"
            }
            "setDataFromSave" -> {
                use("save")
                "Reflect.setField(psychSave(${arg(0)}).data, ${arg(1)}, ${arg(2)})"
            }
            "flushSaveData" -> {
                use("save")
                "psychSave(${arg(0)}).flush()"
            }
            "eraseSaveData" -> {
                use("save")
                "psychSave(${arg(0)}).erase()"
            }
            "callOnLuas", "callOnScripts", "callOnHScript" -> "PlayState.instance.scripts.call(${arg(0)}, ${arg(1, "[]")})"
            "setOnLuas", "setOnScripts", "setOnHScript" -> "PlayState.instance.scripts.set(${arg(0)}, ${arg(1)})"
            "callScript" -> "PlayState.instance.scripts.call(${arg(1)}, ${arg(2, "[]")})"
            "isRunning" -> "true"
            "addCharacterToList" -> {
                issue(line, "addCharacterToList() is not needed in Codename (characters load when created).")
                "null"
            }
            "checkFileExists" -> "Assets.exists(Paths.getPath(${arg(0)}))"
            "getTextFromFile" -> "Assets.getText(Paths.getPath(${arg(0)}))"
            "getModSetting" -> {
                issue(line, "getModSetting(): Psych mod settings don't exist in Codename; the value is null.")
                "null"
            }
            "initLuaShader" -> "null"
            "setSpriteShader" -> "${obj(0)}.shader = new CustomShader(${arg(1)})"
            "removeSpriteShader" -> "${obj(0)}.shader = null"
            "setShaderFloat", "setShaderInt", "setShaderBool", "setShaderFloatArray", "setShaderIntArray", "setShaderBoolArray" -> {
                use("shader")
                "psychShaderSet(${obj(0)}, ${arg(1)}, ${arg(2)})"
            }
            "setShaderSampler2D" -> {
                use("shader")
                "psychShaderSet(${obj(0)}, ${arg(1)}, Assets.getBitmapData(Paths.image(${arg(2)})))"
            }
            "getShaderFloat", "getShaderInt", "getShaderBool", "getShaderFloatArray", "getShaderIntArray", "getShaderBoolArray" -> {
                use("shader")
                "psychShaderGet(${obj(0)}, ${arg(1)})"
            }
            "addHaxeLibrary" -> {
                val lib = str(a, 0)
                val pkg = str(a, 1) ?: ""
                if (lib != null) imports += if (pkg.isEmpty()) lib else "$pkg.$lib"
                "null"
            }
            "runHaxeCode" -> {
                val code = str(a, 0)
                if (code != null) haxeCode(code, line) else {
                    issue(line, "runHaxeCode() with code built at runtime cannot be converted.")
                    "null"
                }
            }
            "runHaxeFunction" -> {
                val fn = str(a, 0)
                val list = (a.getOrNull(1) as? Expr.Table)?.fields?.filterIsInstance<TableField.Positional>()?.map { expr(it.value) }
                if (fn != null) "$fn(${list?.joinToString(", ") ?: ""})" else {
                    issue(line, "runHaxeFunction() with a function name built at runtime cannot be converted.")
                    "null"
                }
            }
            "startVideo" -> {
                imports += "funkin.game.cutscenes.VideoCutscene"
                issue(line, "startVideo() became a VideoCutscene; check that the video plays (Codename looks in videos/).")
                "PlayState.instance.openSubState(new VideoCutscene(Paths.video(${arg(0)}), function() startCountdown()))"
            }
            "cameraZoomOnBeat" -> "Options.camZoomOnBeat"
            else -> {
                if (name in unsupported) issue(line, "Psych function '$name' has no Codename equivalent; the call was removed.")
                else issue(line, "Unknown function '$name' (not defined in this script) was removed.")
                "null"
            }
        }
    }

    private fun indexList(value: Expr?, line: Int): String = when (value) {
        is Expr.Table -> "[" + value.fields.filterIsInstance<TableField.Positional>().joinToString(", ") { "Std.int(${expr(it.value)})" } + "]"
        is Expr.Str -> "[" + value.value.split(',').mapNotNull { it.trim().toIntOrNull() }.joinToString(", ") + "]"
        else -> {
            issue(line, "Frame indices built at runtime were dropped; pass them as a table or a \"0,1,2\" string.")
            "[]"
        }
    }

    private fun classProperty(set: Boolean, a: List<Expr>, line: Int): String {
        val cls = str(a, 0)?.removePrefix("backend.")?.removePrefix("states.")?.removePrefix("substates.")?.removePrefix("objects.")
        val field = str(a, 1)
        val value = if (set) lit(a, 2, "null") else ""
        fun out(target: String) = if (set) "$target = $value" else target
        if (cls == null || field == null) {
            issue(line, "${if (set) "set" else "get"}PropertyFromClass with names built at runtime cannot be converted.")
            return "null"
        }
        return when (cls) {
            "ClientPrefs" -> {
                val key = field.removePrefix("data.")
                val mapped = PsychHxToCodename.options[key]
                if (mapped == null) {
                    issue(line, "ClientPrefs.$field has no Codename option.")
                    "null"
                } else out(mapped)
            }
            "flixel.FlxG", "FlxG" -> out("FlxG.$field")
            "PlayState" -> out(if (field.startsWith("SONG.")) "PlayState.SONG." + when (field.removePrefix("SONG.")) {
                "song" -> "meta.name"
                "speed" -> "scrollSpeed"
                "bpm" -> "meta.bpm"
                else -> field.removePrefix("SONG.")
            } else if (field in setOf("isStoryMode", "seenCutscene", "deathCounter", "chartingMode", "daPixelZoom", "storyPlaylist")) "PlayState.$field" else "PlayState.instance.${PsychHxToCodename.fieldMap[field] ?: field}")
            "Conductor" -> out("Conductor.$field")
            "GameOverSubstate" -> when (field) {
                "characterName" -> {
                    issue(line, "GameOverSubstate.characterName: Codename takes the game over character from the player character's gameOverChar.")
                    "null"
                }
                "deathSoundName" -> out("PlayState.instance.lossSFX")
                "loopSoundName" -> out("PlayState.instance.gameOverSong")
                "endSoundName" -> out("PlayState.instance.retrySFX")
                else -> {
                    issue(line, "GameOverSubstate.$field is Psych-only.")
                    "null"
                }
            }
            else -> {
                val full = str(a, 0) ?: cls
                if (full.startsWith("flixel.") || full.startsWith("openfl.") || full.startsWith("lime.")) {
                    imports += full
                    out("${full.substringAfterLast('.')}.$field")
                } else {
                    issue(line, "Class '$full' is Psych-only.")
                    "null"
                }
            }
        }
    }
}
