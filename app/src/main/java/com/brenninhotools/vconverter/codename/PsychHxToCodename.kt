package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.ScriptIssue
import com.brenninhotools.vconverter.script.hx.HxKind
import com.brenninhotools.vconverter.script.hx.HxSource
import com.brenninhotools.vconverter.script.hx.HxTok

data class CodenameScriptOut(val source: String, val issues: List<ScriptIssue>, val callbacks: List<String>)

class PsychHxToCodename(
    private val scope: CodenameScope,
    private val hooks: Map<String, List<String>> = emptyMap(),
    private val inline: Boolean = false
) {
    private val issues = mutableListOf<ScriptIssue>()
    private val helpers = linkedSetOf<String>()
    private val imports = linkedSetOf<String>()
    private var functions: Set<String> = emptySet()
    private var fields: Set<String> = emptySet()
    private var renames: Map<String, String> = emptyMap()
    private val shaderVars = HashSet<String>()

    companion object {
        val generatedCallbacks = setOf(
            "create", "postCreate", "update", "postUpdate", "beatHit", "stepHit", "measureHit", "destroy", "onSongEnd", "onPostStartCountdown",
            "onCountdown", "onPlayerHit", "onDadHit", "onPostNoteHit", "onPostPlayerMiss", "onGamePause", "onSubstateClose", "onCameraMove",
            "onPostNoteCreation", "onRatingUpdate"
        )

        private val droppedImports = setOf(
            "backend.ClientPrefs", "backend.Conductor", "backend.Paths", "states.PlayState", "objects.Character", "objects.Note", "psychlua.ModchartSprite",
            "psychlua.LuaUtils", "backend.CoolUtil", "objects.HealthIcon", "substates.GameOverSubstate", "backend.Difficulty", "psychlua.FunkinLua",
            "psychlua.HScript", "backend.MusicBeatState", "backend.MusicBeatSubstate", "psychlua.CustomSubstate", "backend.BaseStage", "flixel.util.FlxColor",
            "objects.StrumNote", "shaders.ErrorHandledShader", "backend.Mods", "backend.Highscore", "backend.WeekData", "backend.Song", "objects.Alphabet",
            "backend.StageData", "objects.NoteSplash"
        )

        val fieldMap = mapOf(
            "opponentStrums" to "cpuStrums",
            "songMisses" to "misses",
            "songSpeed" to "scrollSpeed",
            "curSection" to "curMeasure",
            "curDecBeat" to "curBeatFloat",
            "curDecStep" to "curStepFloat",
            "ratingPercent" to "accuracy",
            "camZoomingMult" to "camZoomingStrength",
            "boyfriendGroup" to "boyfriend",
            "dadGroup" to "dad",
            "gfGroup" to "gf",
            "eventNotes" to "events",
            "songHits" to "ratingNum",
            "songName" to "PlayState.SONG.meta.name",
            "curSong" to "PlayState.SONG.meta.name",
            "songLength" to "inst.length",
            "playbackRate" to "1",
            "healthGain" to "1",
            "healthLoss" to "1",
            "cpuControlled" to "playerStrums.cpu",
            "practiceMode" to "(!canDie)",
            "instakillOnMiss" to "false",
            "ratingName" to "curRating.rating",
            "ratingFC" to "curRating.rating",
            "variables" to "PlayState.instance.scripts.publicVariables",
            "modchartSprites" to "PlayState.instance.scripts.publicVariables",
            "modchartTexts" to "PlayState.instance.scripts.publicVariables",
            "isCameraOnForcedPos" to "(curCameraTarget < 0)",
            "cameraSpeed" to "(FlxG.camera.followLerp / 0.04)",
            "spawnTime" to "1500",
            "introSoundsSuffix" to "\"\""
        )

        private val unsupportedFields = mapOf(
            "timeBar" to "Codename has no time bar.",
            "timeTxt" to "Codename has no time text.",
            "botplayTxt" to "Codename has no botplay text.",
            "luaDebugGroup" to "Codename prints script logs to the console.",
            "boyfriendMap" to "Codename swaps characters without character maps.",
            "dadMap" to "Codename swaps characters without character maps.",
            "gfMap" to "Codename swaps characters without character maps.",
            "skipCountdown" to "Cancel onStartCountdown instead.",
            "skipArrowStartTween" to "Codename has no strum intro flag.",
            "camZoomingDecay" to "Codename zoom lerp is camGameZoomLerp.",
            "grpNoteSplashes" to "Codename splashes live in splashHandler.",
            "stageUI" to "Codename pixel stages use the pixel extension."
        )

        val methodMap = mapOf(
            "getAnimationName" to "getAnimName",
            "isAnimationFinished" to "isAnimFinished",
            "hasAnimation" to "hasAnim",
            "finishAnimation" to "finishAnim"
        )

        private val shaderSetters = setOf("setFloat", "setInt", "setBool", "setFloatArray", "setIntArray", "setBoolArray", "setBitmapData", "setSampler2D")
        private val shaderGetters = setOf("getFloat", "getInt", "getBool", "getFloatArray", "getIntArray", "getBoolArray")

        val options = mapOf(
            "downScroll" to "Options.downscroll",
            "middleScroll" to "Options.middlescroll",
            "ghostTapping" to "Options.ghostTapping",
            "flashing" to "Options.flashingMenu",
            "camZooms" to "Options.camZoomOnBeat",
            "lowQuality" to "Options.lowMemoryMode",
            "shaders" to "Options.gameplayShaders",
            "antialiasing" to "Options.antialiasing",
            "framerate" to "Options.framerate",
            "noteOffset" to "Options.songOffset",
            "showFPS" to "Options.fpsCounter",
            "autoPause" to "Options.autoPause",
            "hideHud" to "false",
            "timeBarType" to "\"Disabled\"",
            "scoreZoom" to "true",
            "healthBarAlpha" to "1",
            "noReset" to "false",
            "splashAlpha" to "(Options.splashesEnabled ? 0.6 : 0)",
            "comboStacking" to "true",
            "pauseMusic" to "\"Tea Time\"",
            "checkForUpdates" to "false",
            "discordRPC" to "false",
            "vsync" to "false",
            "cacheOnGPU" to "false",
            "language" to "\"en-US\""
        )

        private val pathRenames = mapOf(
            "getAtlas" to "getFrames",
            "getMultiAtlas" to "getMultiFrames",
            "shaderFragment" to "fragShader",
            "shaderVertex" to "vertShader",
            "modFolders" to "getPath",
            "getSharedPath" to "getPath",
            "modsImages" to "image",
            "modsXml" to "xml",
            "modsTxt" to "txt",
            "modsJson" to "json",
            "modsVideo" to "video",
            "modsSounds" to "sound"
        )

        private val songFields = mapOf(
            "song" to "meta.name",
            "speed" to "scrollSpeed",
            "bpm" to "meta.bpm",
            "needsVoices" to "meta.needsVoices",
            "player1" to "strumLines[1].characters[0]",
            "player2" to "strumLines[0].characters[0]",
            "gfVersion" to "strumLines[2].characters[0]",
            "stage" to "stage",
            "events" to "events"
        )
    }

    fun convert(original: String): CodenameScriptOut {
        val tokens = HxSource.tokenize(original)
        val segs = HxSource.segments(tokens)
        val declaredFunctions = segs.filter { it.kind == "function" }.map { it.name }
        val declaredFields = segs.filter { it.kind == "var" && it.name.isNotEmpty() }.map { it.name }
        val renameMap = LinkedHashMap<String, String>()
        declaredFunctions.forEach { name ->
            if (name in generatedCallbacks || name in PsychCodenameHelpers.provided && name !in setOf("setVar", "getVar")) renameMap[name] = "script" + name.replaceFirstChar { it.uppercaseChar() }
            PsychCodenameAdapters.collisions[name]?.let { renameMap[name] = it }
        }
        declaredFields.forEach { name -> if (name.startsWith("psych")) renameMap[name] = "script" + name.replaceFirstChar { it.uppercaseChar() } }
        renames = renameMap
        functions = declaredFunctions.toSet()
        fields = declaredFields.toSet()
        tokens.forEachIndexed { i, t ->
            if (t.isIdent("createRuntimeShader") || t.isIdent("FlxRuntimeShader") || t.isIdent("ErrorHandledRuntimeShader")) {
                var j = HxSource.prevIndex(tokens, i)
                while (j >= 0 && (tokens[j].isPunct(".") || tokens[j].kind == HxKind.IDENT && tokens[j].text in setOf("game", "PlayState", "instance", "new"))) j = HxSource.prevIndex(tokens, j)
                if (j >= 0 && tokens[j].isPunct("=")) tokens.getOrNull(HxSource.prevIndex(tokens, j))?.takeIf { it.kind == HxKind.IDENT }?.let { shaderVars += it.text }
            }
        }

        segs.filter { it.kind == "import" }.forEach { seg ->
            val path = seg.head.filter { it.kind != HxKind.SPACE }.drop(1).takeWhile { !it.isPunct(";") }.joinToString("") { it.text }
            when {
                path in droppedImports -> Unit
                path.startsWith("shaders.") -> issue(seg.line, "Psych shader class import '$path' was dropped; Codename shaders use CustomShader('name').")
                path.startsWith("psychlua.") || path.startsWith("backend.") || path.startsWith("states.") || path.startsWith("substates.") || path.startsWith("objects.") ->
                    issue(seg.line, "Import '$path' is Psych-only and was dropped.")
                path.isNotEmpty() -> imports += path
            }
        }
        segs.filter { it.kind in setOf("class", "interface", "enum", "abstract") }.forEach { issue(it.line, "Script ${it.kind} ${it.name} was kept; Codename runs it as a scripted ${it.kind}.") }

        val body = StringBuilder()
        segs.forEach { seg ->
            when (seg.kind) {
                "var" -> {
                    val name = renames[seg.name] ?: seg.name
                    val init = if (seg.body.isNotEmpty()) " = " + rewrite(seg.body, emptySet()).trim() else ""
                    val type = typeText(seg.head)
                    body.append(if (seg.isPublic) "public var " else "var ").append(name).append(type).append(init).append(";\n")
                }
                "vars", "statement" -> {
                    val text = rewrite(seg.body, emptySet()).trim()
                    if (text.isNotEmpty() && text != ";") body.append(text.let { if (it.endsWith(";") || it.endsWith("}")) it else "$it;" }).append('\n')
                }
                "class", "interface", "enum", "abstract" -> body.append('\n').append(rewrite(seg.body, emptySet()).trim()).append('\n')
            }
        }
        segs.filter { it.kind == "function" }.forEach { seg ->
            val params = HxSource.paramNames(seg.head, seg.head.indexOfFirst { it.isPunct("(") }.coerceAtLeast(0)).toSet()
            val name = renames[seg.name] ?: seg.name
            body.append('\n').append(if (seg.isPublic) "public function " else "function ").append(name).append(rewrite(seg.head, params).trim()).append('\n').append("{\n")
            val inner = rewrite(seg.body, params)
            val text = if (seg.braced) inner else inner.trim().let { if (it.endsWith(";") || it.endsWith("}")) it else "$it;" }
            HxSource.indentBody(text).forEach { body.append(it).append('\n') }
            body.append("}\n")
        }
        val callbacks = declaredFunctions.filter { it in PsychCodenameAdapters.callbacks || it in PsychCodenameAdapters.unsupported }
        val adapterText = if (inline) "" else {
            val adapters = PsychCodenameAdapters(declaredFunctions.toSet(), lua = false, hooks = hooks)
            val text = adapters.write()
            helpers += adapters.helpers
            adapters.warnings.forEach { issue(0, it) }
            adapters.fields.joinToString("") { "$it\n" } + text
        }
        val out = StringBuilder()
        if (!inline) imports.addAll(PsychCodenameHelpers.imports(helpers))
        imports.forEach { out.append("import ").append(it).append(";\n") }
        if (imports.isNotEmpty()) out.append('\n')
        out.append(body)
        out.append(adapterText)
        if (!inline) PsychCodenameHelpers.emit(helpers) { out.append(it).append('\n') }
        val text = out.toString().replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
        return CodenameScriptOut(text, issues.sortedBy { it.line }, callbacks)
    }

    fun usedHelpers(): Set<String> = helpers

    private fun issue(line: Int, message: String) {
        if (issues.none { it.message == message }) issues += ScriptIssue(line, message)
    }

    private fun typeText(head: List<HxTok>): String {
        val text = HxSource.text(head).trim()
        if (!text.startsWith(":")) return ""
        val type = text.removePrefix(":").trim()
        return when {
            type.isEmpty() -> ""
            type in setOf("ModchartSprite") -> ":FunkinSprite"
            type in setOf("StrumNote") -> ":Strum"
            type in setOf("FlxRuntimeShader", "ErrorHandledRuntimeShader") -> ":CustomShader"
            type in setOf("Bar", "PsychCamera", "DialogueBoxPsych", "VideoSprite", "Alphabet", "BGSprite", "NoteSplash") -> ":Dynamic"
            else -> ":$type"
        }
    }

    private fun splitArgs(tokens: List<HxTok>, open: Int, close: Int): List<List<HxTok>> {
        val parts = mutableListOf<List<HxTok>>()
        var depth = 0
        var start = open + 1
        for (j in open + 1 until close) {
            val t = tokens[j]
            if (t.kind == HxKind.PUNCT) {
                when (t.text) {
                    "(", "[", "{" -> depth++
                    ")", "]", "}" -> depth--
                    "," -> if (depth == 0) {
                        parts += tokens.subList(start, j)
                        start = j + 1
                    }
                }
            }
        }
        if (start < close && tokens.subList(start, close).any { it.kind != HxKind.SPACE }) parts += tokens.subList(start, close)
        return parts
    }

    private fun structural(input: List<HxTok>): List<HxTok> {
        var tokens = input
        var guard = 0
        var changed = true
        while (changed && guard++ < 3000) {
            changed = false
            for (i in tokens.indices) {
                val t = tokens[i]
                if (t.kind != HxKind.IDENT) continue
                val prev = tokens.getOrNull(HxSource.prevIndex(tokens, i))
                val open = HxSource.skipSpace(tokens, i + 1)
                val isCall = tokens.getOrNull(open)?.isPunct("(") == true
                if (prev?.isPunct(".") == true && isCall) {
                    val close = HxSource.matching(tokens, open)
                    val parts = splitArgs(tokens, open, close)
                    if (t.text in shaderSetters && parts.size >= 2 && (receiverIsShader(tokens, HxSource.prevIndex(tokens, HxSource.prevIndex(tokens, i))))) {
                        tokens = tokens.subList(0, i) + HxSource.tokenize("hset") + tokens.subList(i + 1, tokens.size)
                        changed = true
                        break
                    }
                    if (t.text in shaderGetters && parts.size == 1 && receiverIsShader(tokens, HxSource.prevIndex(tokens, HxSource.prevIndex(tokens, i)))) {
                        tokens = tokens.subList(0, i) + HxSource.tokenize("hget") + tokens.subList(i + 1, tokens.size)
                        changed = true
                        break
                    }
                    if (t.text == "playAnim" && parts.size >= 3) {
                        val rebuilt = mutableListOf<HxTok>()
                        val list = parts.take(2) + listOf(HxSource.tokenize("null")) + parts.drop(2)
                        list.forEachIndexed { idx, p ->
                            if (idx > 0) rebuilt += HxTok(HxKind.PUNCT, ",", t.line)
                            if (idx > 0) rebuilt += HxTok(HxKind.SPACE, " ", t.line)
                            rebuilt += p.dropWhile { it.kind == HxKind.SPACE }
                        }
                        tokens = tokens.subList(0, open + 1) + rebuilt + tokens.subList(close, tokens.size)
                        changed = true
                        break
                    }
                }
            }
        }
        return tokens
    }

    private fun receiverIsShader(tokens: List<HxTok>, index: Int): Boolean {
        val t = tokens.getOrNull(index) ?: return false
        return t.kind == HxKind.IDENT && (t.text in shaderVars || t.text == "shader")
    }

    private fun rewrite(input: List<HxTok>, extraLocals: Set<String>): String {
        val tokens = structural(input)
        val sb = StringBuilder()
        val locals = HashSet<String>(extraLocals)
        locals += HxSource.declaredLocals(tokens)
        locals += HxSource.arrowParams(tokens)
        var i = 0
        fun next(index: Int) = HxSource.skipSpace(tokens, index + 1)
        fun member(at: Int): String? = if (tokens.getOrNull(next(at))?.isPunct(".") == true) tokens.getOrNull(next(next(at)))?.takeIf { it.kind == HxKind.IDENT }?.text else null
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.kind != HxKind.IDENT) {
                sb.append(t.text)
                i++
                continue
            }
            val p = tokens.getOrNull(HxSource.prevIndex(tokens, i))
            val afterDot = p?.isPunct(".") == true
            val n1 = next(i)
            val callNext = tokens.getOrNull(n1)?.isPunct("(") == true
            if (afterDot) {
                when {
                    callNext && methodMap.containsKey(t.text) -> sb.append(methodMap.getValue(t.text))
                    t.text == "specialAnim" -> {
                        issue(t.line, "Character.specialAnim is Psych-only; in Codename play the animation without a context so the character waits for it to finish.")
                        sb.append("stunned")
                    }
                    t.text == "danceEveryNumBeats" -> sb.append("beatInterval")
                    t.text == "imageFile" -> sb.append("sprite")
                    t.text == "healthIcon" -> sb.append("icon")
                    t.text == "singDuration" -> sb.append("holdTime")
                    t.text == "ignoreNote" -> sb.append("avoid")
                    t.text == "hitByOpponent" -> sb.append("wasGoodHit")
                    t.text in setOf("noAnimation", "gfNote", "texture", "multAlpha", "noteSplashData", "rgbShader", "cameraPosition", "positionArray", "healthColorArray", "skipDance", "heyTimer") -> {
                        issue(t.line, ".${t.text} is a Psych-only field; check how it is used in Codename.")
                        sb.append(t.text)
                    }
                    else -> sb.append(t.text)
                }
                i++
                continue
            }
            val objectKey = tokens.getOrNull(n1)?.isPunct(":") == true && p?.let { it.isPunct("{") || it.isPunct(",") } == true
            if (objectKey) {
                sb.append(t.text)
                i++
                continue
            }
            val m1 = member(i)
            when {
                t.text in locals -> {
                    sb.append(t.text)
                    i++
                }
                t.text in renames && (t.text in functions || t.text in fields) -> {
                    sb.append(renames.getValue(t.text))
                    i++
                }
                t.text in functions || t.text in fields -> {
                    sb.append(t.text)
                    i++
                }
                t.text == "game" || t.text == "PlayState" && m1 == "instance" -> {
                    val start = if (t.text == "game") i else next(next(i))
                    val inner = member(start)
                    val innerIndex = if (inner != null) next(next(start)) else -1
                    if (inner == null) {
                        sb.append("PlayState.instance")
                        i = start + 1
                    } else {
                        val result = gameMember(inner, tokens, innerIndex, locals, t.line)
                        sb.append(result.first)
                        i = result.second
                    }
                }
                t.text == "PlayState" && m1 == "SONG" -> {
                    val songIndex = next(next(i))
                    val field = member(songIndex)
                    if (field == null) {
                        sb.append("PlayState.SONG")
                        i = songIndex + 1
                    } else if (field == "notes" && tokens.getOrNull(next(next(next(songIndex))))?.isPunct("[") == true &&
                        HxSource.matching(tokens, next(next(next(songIndex)))).let { close -> member(close) == "mustHitSection" || member(close) == "gfSection" }) {
                        val close = HxSource.matching(tokens, next(next(next(songIndex))))
                        helpers += "focus"
                        sb.append(if (member(close) == "mustHitSection") "(psychFocus() == 1)" else "(psychFocus() == 2)")
                        i = next(next(close)) + 1
                    } else {
                        val mapped = songFields[field]
                        if (mapped == null) issue(t.line, "PlayState.SONG.$field is part of Psych's chart format.")
                        if (field == "gfVersion") issue(t.line, "PlayState.SONG.gfVersion became the third strumline's character (null when the song has no girlfriend).")
                        sb.append("PlayState.SONG.").append(mapped ?: field)
                        i = next(next(songIndex)) + 1
                    }
                }
                t.text == "PlayState" && m1 != null -> {
                    when (m1) {
                        "storyDifficulty" -> sb.append("PlayState.difficulty")
                        "curStage" -> sb.append("PlayState.instance.curStage")
                        "isPixelStage" -> sb.append("(PlayState.instance.stage != null && PlayState.instance.stage.stageName.indexOf(\"school\") >= 0)")
                        "storyWeek" -> sb.append("PlayState.storyWeek")
                        else -> sb.append("PlayState.").append(m1)
                    }
                    i = next(next(i)) + 1
                }
                t.text == "ClientPrefs" && m1 == "data" -> {
                    val dataIndex = next(next(i))
                    val field = member(dataIndex)
                    val mapped = field?.let { options[it] }
                    if (field != null && mapped == null) issue(t.line, "ClientPrefs.data.$field has no Codename option and became null.")
                    sb.append(mapped ?: "null")
                    i = if (field != null) next(next(dataIndex)) + 1 else dataIndex + 1
                }
                t.text == "ClientPrefs" && m1 == "getGameplaySetting" -> {
                    val open = next(next(next(i)) - 1).let { next(next(i)) }
                    val callOpen = next(open)
                    issue(t.line, "Gameplay modifiers (ClientPrefs.getGameplaySetting) don't exist in Codename; the default value is used.")
                    sb.append("1")
                    i = if (tokens.getOrNull(callOpen)?.isPunct("(") == true) HxSource.matching(tokens, callOpen) + 1 else open + 1
                }
                t.text == "Paths" && m1 != null && m1 in pathRenames -> {
                    sb.append("Paths.").append(pathRenames.getValue(m1))
                    i = next(next(i)) + 1
                }
                t.text == "Paths" && (m1 == "fileExists" || m1 == "getTextFromFile") -> {
                    val open = next(next(next(i)))
                    if (tokens.getOrNull(open)?.isPunct("(") == true) {
                        val close = HxSource.matching(tokens, open)
                        val first = splitArgs(tokens, open, close).firstOrNull()?.let { rewrite(it, locals).trim() } ?: "\"\""
                        sb.append(if (m1 == "fileExists") "Assets.exists(Paths.getPath($first))" else "Assets.getText(Paths.getPath($first))")
                        i = close + 1
                    } else {
                        sb.append("Paths.").append(m1)
                        i = next(next(i)) + 1
                    }
                }
                t.text == "Paths" && m1 == "formatToSongPath" -> {
                    sb.append("StringTools.replace(StringTools.trim(")
                    val open = next(next(next(i)))
                    val close = HxSource.matching(tokens, open)
                    sb.append(rewrite(tokens.subList(open + 1, close), locals).trim()).append(").toLowerCase(), \" \", \"-\")")
                    i = close + 1
                }
                t.text == "LuaUtils" && m1 == "blendModeFromString" -> {
                    sb.append("BlendMode.fromString")
                    i = next(next(i)) + 1
                }
                t.text == "Difficulty" && m1 != null -> {
                    sb.append(when (m1) {
                        "getString" -> "PlayState.difficulty"
                        "list" -> "PlayState.SONG.meta.difficulties"
                        else -> "PlayState.difficulty"
                    })
                    i = next(next(i)) + 1
                    if (m1 == "getString" && tokens.getOrNull(next(i - 1))?.isPunct("(") == true) i = HxSource.matching(tokens, next(i - 1)) + 1
                }
                t.text == "ModchartSprite" -> {
                    sb.append("FunkinSprite")
                    i++
                }
                t.text == "StrumNote" -> {
                    sb.append("Strum")
                    i++
                }
                (t.text == "FlxRuntimeShader" || t.text == "ErrorHandledRuntimeShader") && callNext && p?.isIdent("new") == true -> {
                    val close = HxSource.matching(tokens, n1)
                    issue(t.line, "new ${t.text}(source) became an empty CustomShader; Codename loads shaders by name (shaders/<name>.frag) with new CustomShader(\"name\").")
                    sb.append("CustomShader(\"\")")
                    i = close + 1
                }
                t.text == "debugPrint" && callNext -> {
                    sb.append("trace")
                    i++
                }
                t.text == "Function_Stop" || t.text == "Function_StopLua" || t.text == "Function_StopHScript" || t.text == "Function_StopAll" -> {
                    sb.append("true")
                    i++
                }
                t.text == "Function_Continue" -> {
                    sb.append("false")
                    i++
                }
                (t.text == "setVar" || t.text == "getVar" || t.text == "removeVar") && callNext -> {
                    helpers += "vars"
                    sb.append(t.text)
                    i++
                }
                t.text == "addHaxeLibrary" && callNext -> {
                    val close = HxSource.matching(tokens, n1)
                    val args = splitArgs(tokens, n1, close).map { HxSource.text(it).trim().trim('"', '\'') }
                    val lib = args.getOrNull(0) ?: ""
                    val pkg = args.getOrNull(1) ?: ""
                    if (lib.isNotEmpty()) imports += if (pkg.isEmpty()) lib else "$pkg.$lib"
                    sb.append("null")
                    i = close + 1
                }
                t.text in setOf("createGlobalCallback", "createCallback") && callNext -> {
                    issue(t.line, "${t.text}() registers Lua callbacks; Codename has no Lua, so it was removed.")
                    sb.append("null")
                    i = HxSource.matching(tokens, n1) + 1
                }
                t.text == "parentLua" -> {
                    issue(t.line, "parentLua only exists for HScript inside Lua scripts.")
                    sb.append("null")
                    i++
                }
                t.text == "controls" -> {
                    sb.append("PlayState.instance.controls")
                    i++
                }
                t.text == "Countdown" && m1 != null -> {
                    sb.append(when (m1) {
                        "THREE" -> "0"
                        "TWO" -> "1"
                        "ONE" -> "2"
                        "GO" -> "3"
                        else -> "4"
                    })
                    i = next(next(i)) + 1
                }
                t.text == "buildTarget" -> {
                    sb.append("\"android\"")
                    i++
                }
                else -> {
                    sb.append(t.text)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun gameMember(name: String, tokens: List<HxTok>, index: Int, locals: Set<String>, line: Int): Pair<String, Int> {
        val next = HxSource.skipSpace(tokens, index + 1)
        val callNext = tokens.getOrNull(next)?.isPunct("(") == true
        unsupportedFields[name]?.let {
            issue(line, "game.$name has no Codename equivalent: $it")
            return "null" to index + 1
        }
        if (callNext) {
            val close = HxSource.matching(tokens, next)
            val args = splitArgs(tokens, next, close).map { rewrite(it, locals).trim() }
            fun a(i: Int) = args.getOrNull(i) ?: "null"
            val text = when (name) {
                "addBehindGF" -> {
                    helpers += "addBehind"
                    "psychAddBehind(PlayState.instance.gf, ${a(0)})"
                }
                "addBehindDad" -> {
                    helpers += "addBehind"
                    "psychAddBehind(PlayState.instance.dad, ${a(0)})"
                }
                "addBehindBF" -> {
                    helpers += "addBehind"
                    "psychAddBehind(PlayState.instance.boyfriend, ${a(0)})"
                }
                "triggerEvent" -> {
                    helpers += "triggerEvent"
                    "triggerEvent(${a(0)}, ${a(1)}, ${a(2)})"
                }
                "callOnScripts", "callOnLuas", "callOnHScript" -> "PlayState.instance.scripts.call(${a(0)}, ${args.getOrNull(1) ?: "[]"})"
                "setOnScripts", "setOnLuas", "setOnHScript" -> "PlayState.instance.scripts.set(${a(0)}, ${a(1)})"
                "getLuaObject" -> {
                    helpers += "object"
                    "psychObject(${a(0)})"
                }
                "createRuntimeShader" -> "new CustomShader(${a(0)})"
                "moveCamera" -> "PlayState.instance.curCameraTarget = (${a(0)}) ? 0 : 1"
                "moveCameraSection", "moveCameraToGirlfriend" -> {
                    issue(line, "game.$name() follows Psych sections; set curCameraTarget instead.")
                    "null"
                }
                "addTextToDebug" -> "trace(${a(0)})"
                "startVideo" -> {
                    issue(line, "startVideo() became a VideoCutscene; check that the video plays (Codename looks in videos/).")
                    imports += "funkin.game.cutscenes.VideoCutscene"
                    "PlayState.instance.openSubState(new VideoCutscene(Paths.video(${a(0)}), function() {}))"
                }
                "reloadHealthBarColors", "updateScore", "doScoreBop", "clearNotesBefore", "setSongTime", "KillNotes", "popUpScore", "cachePopUpScore" -> {
                    issue(line, "game.$name() is Psych-only and was removed.")
                    "null"
                }
                "addCharacterToList", "startCharacterScripts", "startLuasNamed", "startHScriptsNamed", "initHScript", "startDialogue", "openChartEditor" -> {
                    issue(line, "game.$name() is Psych-only and was removed.")
                    "null"
                }
                else -> "PlayState.instance.${fieldMap[name] ?: name}(${args.joinToString(", ")})"
            }
            return text to close + 1
        }
        val mapped = fieldMap[name]
        val text = when {
            name == "camOther" -> {
                helpers += "camera"
                "psychCamera(\"other\")"
            }
            name == "notes" || name == "unspawnNotes" -> {
                helpers += "notes"
                "psychNotes()"
            }
            name == "strumLineNotes" -> {
                helpers += "group"
                "psychGroup(\"strumLineNotes\")"
            }
            mapped == null -> "PlayState.instance.$name"
            mapped.startsWith("PlayState.") || mapped.startsWith("(") || mapped.first().isDigit() || mapped.startsWith("\"") || mapped == "false" || mapped == "true" -> mapped
            else -> "PlayState.instance.$mapped"
        }
        if (name == "isCameraOnForcedPos") {
            val eq = HxSource.skipSpace(tokens, index + 1)
            if (tokens.getOrNull(eq)?.isPunct("=") == true && tokens.getOrNull(eq + 1)?.isPunct("=") != true) {
                val end = HxSource.statementEnd(tokens, eq + 1)
                val valueEnd = if (tokens.getOrNull(end - 1)?.isPunct(";") == true) end - 1 else end
                val value = rewrite(tokens.subList(eq + 1, valueEnd), locals).trim()
                return "if ($value) PlayState.instance.curCameraTarget = -1" to valueEnd
            }
        }
        return text to index + 1
    }
}
