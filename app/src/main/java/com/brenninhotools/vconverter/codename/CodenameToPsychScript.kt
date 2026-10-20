package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.ScriptIssue
import com.brenninhotools.vconverter.script.hx.HxKind
import com.brenninhotools.vconverter.script.hx.HxSegment
import com.brenninhotools.vconverter.script.hx.HxSource
import com.brenninhotools.vconverter.script.hx.HxTok

data class PsychHooks(
    val fields: List<String> = emptyList(),
    val callbacks: Map<String, List<String>> = emptyMap(),
    val functions: List<String> = emptyList(),
    val imports: List<String> = emptyList()
) {
    operator fun plus(other: PsychHooks): PsychHooks = PsychHooks(
        fields + other.fields,
        (callbacks.keys + other.callbacks.keys).associateWith { (callbacks[it] ?: emptyList()) + (other.callbacks[it] ?: emptyList()) },
        functions + other.functions,
        (imports + other.imports).distinct()
    )

    val isEmpty: Boolean get() = fields.isEmpty() && callbacks.values.all { it.isEmpty() } && functions.isEmpty()
}

data class CodenamePsychScriptContext(
    val stageSprites: Set<String> = emptySet(),
    val eventParamCounts: Map<String, Int> = emptyMap(),
    val characterId: String = "",
    val eventName: String = "",
    val hooks: PsychHooks = PsychHooks()
)

data class CodenamePsychScriptResult(val source: String, val issues: List<ScriptIssue>, val callbacks: List<String>, val usesCompat: Boolean)

class CodenameToPsychScript(
    private val target: CodenameScriptTarget,
    private val context: CodenamePsychScriptContext = CodenamePsychScriptContext()
) {
    private val issues = mutableListOf<ScriptIssue>()
    private val helpers = linkedSetOf<String>()
    private val imports = linkedSetOf<String>()
    private var compat = false
    private var functions: Set<String> = emptySet()
    private var fields: Set<String> = emptySet()
    private var renames: Map<String, String> = emptyMap()
    private val shaderVars = HashSet<String>()
    private val usedSprites = linkedSetOf<String>()

    companion object {
        val callbacks = setOf(
            "create", "postCreate", "update", "postUpdate", "beatHit", "stepHit", "measureHit", "onSongStart", "onStartSong",
            "onSongEnd", "onStartCountdown", "onPostStartCountdown", "onCountdown", "onPostCountdown", "onNoteHit", "onPlayerHit",
            "onDadHit", "onPostNoteHit", "onPlayerMiss", "onPostPlayerMiss", "onNoteCreation", "onPostNoteCreation", "onEvent",
            "onPostEvent", "onGamePause", "onGameOver", "destroy", "onCameraMove", "onDance", "onPlaySingAnim", "postDeathStart",
            "onFocus", "onFocusLost", "onSubstateOpen", "onSubstateClose", "draw", "postDraw"
        )

        private val unsupportedCallbacks = mapOf(
            "onFocus" to "Psych scripts are not told when the window gains focus.",
            "onFocusLost" to "Psych scripts are not told when the window loses focus.",
            "draw" to "Psych scripts cannot hook drawing.",
            "postDraw" to "Psych scripts cannot hook drawing.",
            "onStrumCreation" to "Psych creates the strums before scripts can change them; edit game.strumLineNotes in onCreatePost instead.",
            "onPostStrumCreation" to "Psych creates the strums before scripts can change them; edit game.strumLineNotes in onCreatePost instead.",
            "onRatingUpdate" to "Psych has no rating update hook (onRecalculateRating is the closest).",
            "onPostGenerateStrums" to "Psych has no strum generation hook.",
            "onPreGenerateStrums" to "Psych has no strum generation hook.",
            "onInputUpdate" to "Psych has no input update hook (use onKeyPress).",
            "onPostInputUpdate" to "Psych has no input update hook (use onKeyPress).",
            "onBopZoom" to "Psych has no camera bop hook.",
            "onPostBopZoom" to "Psych has no camera bop hook.",
            "onVocalsResync" to "Psych has no vocals resync hook.",
            "onSplashShown" to "Psych has no splash hook.",
            "onRatingsShown" to "Psych has no rating popup hook.",
            "onPostRatingsShown" to "Psych has no rating popup hook.",
            "onStageXMLParsed" to "Psych stages are JSON files.",
            "onStageNodeParsed" to "Psych stages are JSON files.",
            "onPlayAnim" to "Psych characters have no animation hook.",
            "onGetCamPos" to "Psych characters have no camera position hook.",
            "onTryDance" to "Psych characters have no dance hook.",
            "onCharacterXMLParsed" to "Psych characters are JSON files.",
            "onCharacterNodeParsed" to "Psych characters are JSON files.",
            "onStageDestroy" to "Psych has no stage destroy hook (onDestroy runs for every script).",
            "onSubstateOpen" to "Psych scripts are only told about pausing (onPause).",
            "onSubstateClose" to "Psych scripts are only told about resuming (onResume)."
        )

        private val reservedNames = setOf(
            "game", "this", "setVar", "getVar", "removeVar", "debugPrint", "createGlobalCallback", "createCallback", "addHaxeLibrary",
            "parentLua", "controls", "buildTarget", "customSubstate", "customSubstateName", "Function_Stop", "Function_Continue",
            "Function_StopLua", "Function_StopHScript", "Function_StopAll", "keyJustPressed", "keyPressed", "keyReleased",
            "getModSetting", "Type", "File", "FileSystem", "PsychCamera", "Countdown", "Achievements", "Alphabet", "CustomSubstate",
            "ErrorHandledRuntimeShader", "ShaderFilter", "eventParams"
        )

        private val droppedImports = setOf(
            "flixel.FlxG", "flixel.FlxSprite", "flixel.text.FlxText", "flixel.FlxCamera", "flixel.util.FlxTimer", "flixel.tweens.FlxTween",
            "flixel.tweens.FlxEase", "flixel.util.FlxColor", "flixel.math.FlxMath", "flixel.util.FlxAxes", "openfl.display.BlendMode",
            "flixel.addons.display.FlxRuntimeShader", "openfl.filters.ShaderFilter"
        )

        private val importMap = mapOf(
            "funkin.game.Strum" to "objects.StrumNote",
            "funkin.game.HealthIcon" to "objects.HealthIcon",
            "funkin.game.GameOverSubstate" to "substates.GameOverSubstate",
            "funkin.backend.FunkinSprite" to "psychlua.ModchartSprite",
            "funkin.backend.utils.CoolUtil" to "backend.CoolUtil"
        )

        private val presetClasses = setOf(
            "funkin.game.PlayState", "funkin.game.Character", "funkin.game.Note", "funkin.backend.system.Conductor", "funkin.backend.assets.Paths",
            "funkin.backend.FunkinText", "funkin.backend.shaders.CustomShader", "funkin.backend.shaders.FunkinShader", "funkin.options.Options",
            "funkin.backend.system.Logs", "funkin.game.HudCamera", "funkin.game.Stage", "funkin.backend.system.Flags"
        )

        private val bareMembers = mapOf(
            "bf" to "boyfriend",
            "cpuStrums" to "opponentStrums",
            "cpu" to "opponentStrums",
            "player" to "playerStrums",
            "misses" to "songMisses",
            "scrollSpeed" to "songSpeed",
            "curMeasure" to "curSection",
            "curBeatFloat" to "curDecBeat",
            "curStepFloat" to "curDecStep",
            "curMeasureFloat" to "(curDecBeat / 4)",
            "inst" to "FlxG.sound.music",
            "curSong" to "PlayState.SONG.song",
            "curSongID" to "songName",
            "accuracy" to "ratingPercent",
            "downscroll" to "ClientPrefs.data.downScroll",
            "isStoryMode" to "PlayState.isStoryMode",
            "storyPlaylist" to "PlayState.storyPlaylist",
            "difficulty" to "Difficulty.getString()",
            "chartingMode" to "PlayState.chartingMode",
            "deathCounter" to "PlayState.deathCounter",
            "seenCutscene" to "PlayState.seenCutscene",
            "daPixelZoom" to "PlayState.daPixelZoom",
            "SONG" to "PlayState.SONG",
            "gameOverSong" to "GameOverSubstate.loopSoundName",
            "lossSFX" to "GameOverSubstate.deathSoundName",
            "retrySFX" to "GameOverSubstate.endSoundName",
            "events" to "eventNotes",
            "healthBarBG" to "healthBar.bg",
            "canDie" to "(!practiceMode)",
            "validScore" to "(!chartingMode)",
            "opponentMode" to "false",
            "coopMode" to "false",
            "playCutscenes" to "PlayState.isStoryMode",
            "maxHealth" to "2",
            "curStage" to "PlayState.curStage",
            "lerp" to "FlxMath.lerp",
            "__script__" to "this"
        )

        private val compatMembers = setOf(
            "camZoomingInterval", "camZoomingStrength", "camZoomingOffset", "camZoomingEvery", "defaultHudZoom", "maxCamZoom",
            "camGameZoomLerp", "camHUDZoomLerp", "camGameZoomMult", "camHUDZoomMult", "curCameraTarget"
        )

        private val unsupportedMembers = mapOf(
            "introLength" to "Psych's countdown length is fixed.",
            "introSprites" to "Psych picks countdown sprites from the stage UI (stageUI).",
            "introSounds" to "Psych picks countdown sounds from the stage UI (introSoundsSuffix).",
            "ratingManager" to "Psych ratings live in game.ratingsData.",
            "splashHandler" to "Psych splashes live in game.grpNoteSplashes.",
            "cameraFocusOffset" to "Psych has no camera focus offset.",
            "muteVocalsOnMiss" to "Psych always mutes vocals on a miss.",
            "missesTxt" to "Psych shows misses inside scoreTxt.",
            "accuracyTxt" to "Psych shows accuracy inside scoreTxt.",
            "comboRatings" to "Psych rating names live in PlayState.ratingStuff.",
            "useCamZoomMult" to "Psych has no zoom multiplier mode."
        )

        private val conductorMembers = mapOf(
            "songPosition" to "Conductor.songPosition",
            "crochet" to "Conductor.crochet",
            "stepCrochet" to "Conductor.stepCrochet",
            "bpm" to "Conductor.bpm",
            "curBeat" to "curBeat",
            "curStep" to "curStep",
            "curMeasure" to "curSection",
            "curBeatFloat" to "curDecBeat",
            "curStepFloat" to "curDecStep",
            "curMeasureFloat" to "(curDecBeat / 4)",
            "beatsPerMeasure" to "4",
            "stepsPerBeat" to "4",
            "songOffset" to "ClientPrefs.data.noteOffset",
            "measureCrochet" to "(Conductor.crochet * 4)"
        )

        val options = mapOf(
            "downscroll" to "ClientPrefs.data.downScroll",
            "middlescroll" to "ClientPrefs.data.middleScroll",
            "ghostTapping" to "ClientPrefs.data.ghostTapping",
            "flashingMenu" to "ClientPrefs.data.flashing",
            "camZoomOnBeat" to "ClientPrefs.data.camZooms",
            "framerate" to "ClientPrefs.data.framerate",
            "antialiasing" to "ClientPrefs.data.antialiasing",
            "lowMemoryMode" to "ClientPrefs.data.lowQuality",
            "gameplayShaders" to "ClientPrefs.data.shaders",
            "splashesEnabled" to "(ClientPrefs.data.splashAlpha > 0)",
            "songOffset" to "ClientPrefs.data.noteOffset",
            "hitWindow" to "ClientPrefs.data.badWindow",
            "naughtyness" to "true",
            "week6PixelPerfect" to "false",
            "colorHealthBar" to "true",
            "autoPause" to "ClientPrefs.data.autoPause",
            "fpsCounter" to "ClientPrefs.data.showFPS",
            "volumeSFX" to "1",
            "volumeMusic" to "1",
            "streamedMusic" to "false",
            "streamedVocals" to "false"
        )

        private val methodRenames = mapOf(
            "getAnimName" to "getAnimationName",
            "isAnimFinished" to "isAnimationFinished",
            "hasAnim" to "hasAnimation",
            "getNameOfCurrentAnim" to "getAnimationName"
        )

        private val playContexts = setOf("SING", "DANCE", "MISS", "LOCK", "NONE")

        private val pathsRenames = mapOf(
            "getFrames" to "getAtlas",
            "file" to "getPath",
            "frag" to "shaderFragment",
            "vert" to "shaderVertex"
        )

        private val characterMembers = setOf(
            "playAnim", "dance", "animation", "x", "y", "scale", "alpha", "visible", "angle", "flipX", "flipY", "color", "shader", "offset",
            "frameOffset", "width", "height", "getAnimName", "isAnimFinished", "hasAnim", "curCharacter", "isPlayer", "holdTime",
            "lastHit", "stunned", "getMidpoint", "getGraphicMidpoint", "cameraOffset", "globalOffset", "icon", "iconColor", "idleSuffix",
            "antialiasing", "updateHitbox", "setPosition", "scrollFactor", "cameras", "camera", "exists", "active", "kill", "revive",
            "frames", "frame", "origin", "setGraphicSize", "loadGraphic", "blend", "setColorTransform", "colorTransform", "extra",
            "beatInterval", "playSingAnim", "danceOnBeat", "getCameraPosition", "isGF", "sprite", "skew", "zoomFactor", "animateAtlas"
        )

        private val characterRenames = mapOf(
            "frameOffset" to "offset",
            "holdTime" to "singDuration",
            "icon" to "healthIcon",
            "extra" to "extraData",
            "beatInterval" to "danceEveryNumBeats",
            "sprite" to "imageFile",
            "animateAtlas" to "atlas"
        )

        private val adapterNames = setOf(
            "onCreate", "onCreatePost", "onUpdate", "onUpdatePost", "onBeatHit", "onStepHit", "onSectionHit", "onSongStart", "onEndSong",
            "onStartCountdown", "onCountdownStarted", "onCountdownTick", "goodNoteHitPre", "goodNoteHit", "opponentNoteHitPre",
            "opponentNoteHit", "noteMiss", "noteMissPress", "onEvent", "onPause", "onResume", "onGameOver", "onGameOverStart", "onDestroy",
            "onMoveCamera", "onSpawnNote"
        )
    }

    fun convert(original: String): CodenamePsychScriptResult {
        currentSourceHint = original
        val tokens = HxSource.tokenize(original)
        val segs = HxSource.segments(tokens)
        val declaredFunctions = segs.filter { it.kind == "function" }.map { it.name }
        val declaredFields = segs.filter { it.kind == "var" && it.name.isNotEmpty() }.map { it.name } +
            segs.filter { it.kind == "vars" }.flatMap { varNames(it.body) }
        val renameMap = LinkedHashMap<String, String>()
        val aliases = HashSet<String>()
        if (target.scope == CodenameScope.CHARACTER) {
            segs.filter { it.kind == "var" && it.body.filter { t -> t.kind != HxKind.SPACE }.let { b -> b.size == 1 && b[0].isIdent("this") } }.forEach {
                renameMap[it.name] = "self"
                aliases += it.name
            }
        }
        (declaredFunctions + declaredFields).filter { it !in aliases }.forEach { name ->
            if (name in reservedNames || name.startsWith("cne")) renameMap[name] = "script" + name.replaceFirstChar { it.uppercaseChar() }
        }
        val firstPlan = adapterPlan(declaredFunctions.toSet())
        declaredFunctions.forEach { name ->
            if (name in adapterNames && firstPlan.containsKey(name) && !firstPlan.getValue(name).passThrough) renameMap[name] = "cne" + name.replaceFirstChar { it.uppercaseChar() }
        }
        renames = renameMap
        val adapters = adapterPlan(declaredFunctions.toSet())
        functions = declaredFunctions.toSet()
        fields = declaredFields.toSet()
        segs.filter { it.kind == "var" && it.body.any { t -> t.isIdent("CustomShader") || t.isIdent("FunkinShader") } }.forEach { shaderVars += it.name }
        tokens.forEachIndexed { i, t ->
            if (t.kind == HxKind.IDENT && (t.text == "CustomShader" || t.text == "FunkinShader")) {
                val p = HxSource.prevIndex(tokens, i)
                if (tokens.getOrNull(p)?.isIdent("new") == true) {
                    val eq = HxSource.prevIndex(tokens, p)
                    if (tokens.getOrNull(eq)?.isPunct("=") == true) {
                        val v = tokens.getOrNull(HxSource.prevIndex(tokens, eq))
                        if (v?.kind == HxKind.IDENT) shaderVars += v.text
                    }
                } else if (tokens.getOrNull(p)?.isPunct(":") == true) {
                    tokens.getOrNull(HxSource.prevIndex(tokens, p))?.takeIf { it.kind == HxKind.IDENT }?.let { shaderVars += it.text }
                }
            }
        }

        declaredFunctions.filter { it in unsupportedCallbacks }.forEach { issue(0, "Codename callback $it() never runs in Psych: ${unsupportedCallbacks.getValue(it)}") }

        segs.filter { it.kind == "import" }.forEach { seg ->
            val path = seg.head.filter { it.kind != HxKind.SPACE }.drop(1).takeWhile { !it.isPunct(";") }.joinToString("") { it.text }
            when {
                path in droppedImports || path in presetClasses -> Unit
                importMap.containsKey(path) -> imports += importMap.getValue(path)
                path.startsWith("funkin.backend.scripting") -> Unit
                path.startsWith("funkin.") -> issue(seg.line, "Import '$path' is Codename-only and was dropped.")
                path.endsWith(".*") -> issue(seg.line, "Wildcard import '$path' is not supported by Psych's HScript and was dropped.")
                path.isNotEmpty() -> imports += path
            }
        }
        segs.filter { it.kind == "using" }.forEach { issue(it.line, "'using' is not supported by Psych's HScript and was dropped.") }
        segs.filter { it.kind in setOf("class", "interface", "enum", "abstract", "typedef") }.forEach {
            issue(it.line, "Script ${it.kind} ${it.name} was dropped; Psych's HScript cannot declare ${it.kind}s.")
        }
        segs.filter { it.isPublic && it.kind in setOf("var", "function") }.forEach {
            issue(it.line, "public ${it.kind} ${it.name} is shared between Codename scripts; in Psych it is local to this script (share it with setVar/getVar).")
        }

        val out = StringBuilder()
        val body = StringBuilder()
        segs.forEach { seg ->
            when (seg.kind) {
                "var" -> {
                    if (renames[seg.name] == "self") return@forEach
                    val name = renames[seg.name] ?: seg.name
                    val init = if (seg.body.isNotEmpty()) " = " + rewrite(seg.body, emptySet()).trim() else ""
                    val type = typeText(seg.head)
                    body.append("var ").append(name).append(type).append(init).append(";\n")
                }
                "vars" -> body.append(rewrite(seg.body, emptySet()).trim().let { if (it.endsWith(";")) it else "$it;" }).append('\n')
                "statement" -> {
                    val text = rewrite(seg.body, emptySet()).trim()
                    if (text.isNotEmpty() && text != ";") body.append(text.let { if (it.endsWith(";") || it.endsWith("}")) it else "$it;" }).append('\n')
                }
            }
        }
        context.hooks.fields.forEach { body.append(it).append('\n') }
        segs.filter { it.kind == "function" }.forEach { seg ->
            val params = HxSource.paramNames(seg.head, seg.head.indexOfFirst { it.isPunct("(") }.coerceAtLeast(0)).toSet()
            val name = renames[seg.name] ?: seg.name
            body.append('\n')
            body.append("function ").append(name).append(rewrite(seg.head, params).trim()).append('\n')
            body.append("{\n")
            val inner = rewrite(seg.body, params)
            val text = if (seg.braced) inner else inner.trim().let { if (it.endsWith(";") || it.endsWith("}")) it else "$it;" }
            HxSource.indentBody(text).forEach { body.append(it).append('\n') }
            body.append("}\n")
        }
        val spriteFields = (usedSprites - fields).toList()
        val adapterText = writeAdapters(adapters, spriteFields)
        context.hooks.functions.forEach { body.append('\n').append(it.trimEnd()).append('\n') }

        if ("cameraMove" in helpers) imports += "flixel.math.FlxPoint"
        imports += context.hooks.imports
        imports.forEach { out.append("import ").append(it).append(";\n") }
        if (imports.isNotEmpty()) out.append('\n')
        spriteFields.forEach { out.append("var ").append(it).append(":Dynamic = null;\n") }
        if (target.scope == CodenameScope.CHARACTER) out.append("var self:Dynamic = null;\n")
        if (needsDisabled) out.append("var scriptDisabled:Bool = false;\n")
        if (helpers.contains("pendingHit")) out.append("var cnePendingHits:Array<Dynamic> = [];\n")
        out.append(body)
        out.append(adapterText)
        if (target.scope == CodenameScope.CHARACTER) out.append('\n').append(CodenamePsychHelpers.selfFunction(context.characterId.ifEmpty { target.key })).append('\n')
        CodenamePsychHelpers.emit(helpers, context.eventParamCounts) { out.append(it).append('\n') }
        val text = HxSource.stripGenerics(out.toString()).replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
        val usedCallbacks = declaredFunctions.filter { it in callbacks }
        return CodenamePsychScriptResult(text, issues.sortedBy { it.line }, usedCallbacks, compat)
    }

    private var needsDisabled = false

    private fun issue(line: Int, message: String) {
        if (issues.none { it.message == message }) issues += ScriptIssue(line, message)
    }

    private fun varNames(tokens: List<HxTok>): List<String> {
        val names = mutableListOf<String>()
        var depth = 0
        var expect = true
        tokens.forEach { t ->
            if (t.kind == HxKind.PUNCT) {
                when (t.text) {
                    "(", "[", "{", "<" -> depth++
                    ")", "]", "}", ">" -> depth--
                    "," -> if (depth == 0) expect = true
                }
            } else if (t.kind == HxKind.IDENT && depth == 0 && expect && t.text != "var" && t.text != "final") {
                names += t.text
                expect = false
            }
        }
        return names
    }

    private fun typeText(head: List<HxTok>): String {
        val text = HxSource.text(head).trim()
        if (!text.startsWith(":")) return ""
        val type = text.removePrefix(":").trim()
        return when {
            type.isEmpty() -> ""
            type in setOf("CustomShader", "FunkinShader", "FunkinSprite", "FunkinText", "HudCamera", "StrumLine", "Strum", "Stage", "HealthIcon") -> ":Dynamic"
            type.contains("<") -> ":Dynamic"
            else -> ":$type"
        }
    }

    private class Adapter(val psych: String, val params: String, val lines: List<String>, val passThrough: Boolean, val returns: Boolean = false)

    private fun adapterPlan(defined: Set<String>): Map<String, Adapter> {
        fun has(name: String) = name in defined
        fun call(name: String, vararg args: String) = "${callName(name)}(${args.joinToString(", ")});"
        val plan = LinkedHashMap<String, Adapter>()
        val hooks = context.hooks.callbacks
        fun hook(name: String) = hooks[name] ?: emptyList()
        val character = target.scope == CodenameScope.CHARACTER

        val create = mutableListOf<String>()
        if (character) create += "self = cneSelf();"
        create += hook("onCreate")
        if (has("create")) create += call("create")
        if (create.isNotEmpty()) plan["onCreate"] = Adapter("onCreate", "", create, false)

        val post = mutableListOf<String>()
        post += hook("onCreatePost")
        if (has("onNoteCreation") || has("onPostNoteCreation")) {
            helpers += "noteCreation"
            post += "for (note in game.unspawnNotes)"
            post += "{"
            post += "  var e:Dynamic = cneNoteCreation(note);"
            if (has("onNoteCreation")) post += "  " + call("onNoteCreation", "e")
            if (has("onPostNoteCreation")) post += "  " + call("onPostNoteCreation", "e")
            post += "  cneApplyNoteCreation(note, e);"
            post += "}"
        }
        if (has("postCreate")) post += call("postCreate")
        if (post.isNotEmpty()) plan["onCreatePost"] = Adapter("onCreatePost", "", post, false)

        val update = hook("onUpdate") + if (has("update")) listOf(call("update", "elapsed")) else emptyList()
        if (update.isNotEmpty()) plan["onUpdate"] = Adapter("onUpdate", "elapsed:Float", update, false)

        val postUpdate = mutableListOf<String>()
        postUpdate += hook("onUpdatePost")
        if (has("onCameraMove")) {
            helpers += "cameraMove"
            postUpdate += "var cam:Dynamic = cneCameraEvent();"
            postUpdate += "if (cam != null)"
            postUpdate += "{"
            postUpdate += "  " + call("onCameraMove", "cam")
            postUpdate += "  if (!cam.cancelled) game.camFollow.setPosition(cam.position.x, cam.position.y);"
            postUpdate += "}"
        }
        if (has("postUpdate")) postUpdate += call("postUpdate", "elapsed")
        if (postUpdate.isNotEmpty()) plan["onUpdatePost"] = Adapter("onUpdatePost", "elapsed:Float", postUpdate, false)

        val beat = mutableListOf<String>()
        beat += hook("onBeatHit")
        if (character && has("onDance")) {
            helpers += "cancellable"
            beat += "if (self != null && self.danceEveryNumBeats > 0 && curBeat % self.danceEveryNumBeats == 0 && !self.specialAnim)"
            beat += "{"
            beat += "  var e:Dynamic = cneCancellable();"
            beat += "  " + call("onDance", "e")
            beat += "  self.skipDance = false;"
            beat += "  if (!e.cancelled && (self.getAnimationName() == null || !StringTools.startsWith(self.getAnimationName(), \"sing\"))) self.dance();"
            beat += "  self.skipDance = true;"
            beat += "}"
            create += "if (self != null) self.skipDance = true;"
            plan["onCreate"] = Adapter("onCreate", "", create, false)
        }
        if (has("beatHit")) beat += call("beatHit", "curBeat")
        if (beat.isNotEmpty()) plan["onBeatHit"] = Adapter("onBeatHit", "", beat, false)

        val step = hook("onStepHit") + if (has("stepHit")) listOf(call("stepHit", "curStep")) else emptyList()
        if (step.isNotEmpty()) plan["onStepHit"] = Adapter("onStepHit", "", step, false)
        if (has("measureHit")) plan["onSectionHit"] = Adapter("onSectionHit", "", listOf(call("measureHit", "curSection")), false)

        if (has("onStartSong")) plan["onSongStart"] = Adapter("onSongStart", "", listOfNotNull(if (has("onSongStart")) call("onSongStart") else null, call("onStartSong")), false)
        else if (has("onSongStart")) plan["onSongStart"] = Adapter("onSongStart", "", emptyList(), true)

        if (has("onSongEnd")) {
            helpers += "cancellable"
            plan["onEndSong"] = Adapter("onEndSong", "", listOf("var e:Dynamic = cneCancellable();", call("onSongEnd", "e"), "if (e.cancelled) return Function_Stop;", "return Function_Continue;"), false, true)
        }
        if (has("onStartCountdown")) {
            helpers += "cancellable"
            plan["onStartCountdown"] = Adapter("onStartCountdown", "", listOf("var e:Dynamic = cneCancellable();", call("onStartCountdown", "e"), "if (e.cancelled) return Function_Stop;", "return Function_Continue;"), false, true)
        }
        if (has("onPostStartCountdown")) {
            helpers += "cancellable"
            plan["onCountdownStarted"] = Adapter("onCountdownStarted", "", listOf("var e:Dynamic = cneCancellable();", call("onPostStartCountdown", "e")), false)
        }
        val countdown = mutableListOf<String>()
        countdown += hook("onCountdownTick")
        if (has("onCountdown") || has("onPostCountdown")) {
            helpers += "countdown"
            countdown += "var e:Dynamic = cneCountdownEvent(counter);"
            if (has("onCountdown")) countdown += call("onCountdown", "e")
            if (has("onPostCountdown")) countdown += call("onPostCountdown", "e")
        }
        if (countdown.isNotEmpty()) plan["onCountdownTick"] = Adapter("onCountdownTick", "tick, counter", countdown, false)

        val hitPre = has("onPlayerHit") || has("onNoteHit")
        val dadPre = has("onDadHit") || has("onNoteHit")
        val singHook = character && has("onPlaySingAnim")
        if (hitPre || has("onPostNoteHit") || singHook) {
            helpers += "noteEvent"
            if (has("onPostNoteHit")) helpers += "pendingHit"
            plan["goodNoteHitPre"] = Adapter("goodNoteHitPre", "note", hitLines(true, has("onPlayerHit"), has("onNoteHit"), has("onPostNoteHit"), singHook), false, true)
            if (has("onPostNoteHit")) plan["goodNoteHit"] = Adapter("goodNoteHit", "note", listOf("var e:Dynamic = cneTakeHit(note);", "if (e != null) " + call("onPostNoteHit", "e")), false)
        }
        if (dadPre || has("onPostNoteHit") || singHook) {
            helpers += "noteEvent"
            if (has("onPostNoteHit")) helpers += "pendingHit"
            plan["opponentNoteHitPre"] = Adapter("opponentNoteHitPre", "note", hitLines(false, has("onDadHit"), has("onNoteHit"), has("onPostNoteHit"), singHook), false, true)
            if (has("onPostNoteHit")) plan["opponentNoteHit"] = Adapter("opponentNoteHit", "note", listOf("var e:Dynamic = cneTakeHit(note);", "if (e != null) " + call("onPostNoteHit", "e")), false)
        }
        if (has("onPlayerMiss") || has("onPostPlayerMiss")) {
            helpers += "missEvent"
            val miss = mutableListOf("var e:Dynamic = cneMissEvent(note, note.noteData);")
            if (has("onPlayerMiss")) miss += call("onPlayerMiss", "e")
            miss += "cneApplyMiss(e);"
            if (has("onPostPlayerMiss")) miss += call("onPostPlayerMiss", "e")
            plan["noteMiss"] = Adapter("noteMiss", "note", miss, false)
            val press = mutableListOf("var e:Dynamic = cneMissEvent(null, direction);")
            if (has("onPlayerMiss")) press += call("onPlayerMiss", "e")
            press += "cneApplyMiss(e);"
            if (has("onPostPlayerMiss")) press += call("onPostPlayerMiss", "e")
            plan["noteMissPress"] = Adapter("noteMissPress", "direction", press, false)
            if (Regex("\\.cancel\\s*\\(").containsMatchIn(currentSourceHint)) issue(0, "Misses cannot be cancelled in Psych (noteMiss runs after the miss); event.cancel() only skips the converted extras.")
        }
        if (has("onEvent") || has("onPostEvent") || target.scope == CodenameScope.EVENT) {
            helpers += "songEvent"
            val ev = mutableListOf<String>()
            if (target.scope == CodenameScope.EVENT && context.eventName.isNotEmpty()) ev += "if (name != ${HxSource.hxString(context.eventName)}) return;"
            ev += "var e:Dynamic = cneSongEvent(name, value1, value2, strumTime);"
            if (has("onEvent")) ev += call("onEvent", "e")
            if (has("onPostEvent")) ev += "if (!e.cancelled) " + call("onPostEvent", "e")
            if (has("onEvent") || has("onPostEvent")) plan["onEvent"] = Adapter("onEvent", "name:String, value1:String, value2:String, strumTime:Float", ev, false)
        }
        if (has("onGamePause")) {
            helpers += "cancellable"
            plan["onPause"] = Adapter("onPause", "", listOf("var e:Dynamic = cneCancellable();", call("onGamePause", "e"), "if (e.cancelled) return Function_Stop;", "return Function_Continue;"), false, true)
        }
        if (has("onGameOver")) {
            helpers += "cancellable"
            plan["onGameOver"] = Adapter("onGameOver", "", listOf("var e:Dynamic = cneCancellable();", call("onGameOver", "e"), "if (e.cancelled) return Function_Stop;", "return Function_Continue;"), false, true)
        }
        if (has("postDeathStart")) plan["onGameOverStart"] = Adapter("onGameOverStart", "", listOf(call("postDeathStart")), false)
        val destroy = hook("onDestroy") + if (has("destroy")) listOf(call("destroy")) else emptyList()
        if (destroy.isNotEmpty()) plan["onDestroy"] = Adapter("onDestroy", "", destroy, false)
        hooks.keys.filter { it !in plan && it !in setOf("onCreate", "onCreatePost", "onUpdate", "onUpdatePost", "onBeatHit", "onStepHit", "onCountdownTick", "onDestroy") }.forEach { name ->
            plan[name] = Adapter(name, if (name == "onEvent") "name:String, value1:String, value2:String, strumTime:Float" else "", hook(name), false)
        }
        return plan
    }

    private var currentSourceHint: String = ""

    private fun hitLines(player: Boolean, side: Boolean, both: Boolean, post: Boolean, sing: Boolean): List<String> {
        val sideName = if (player) "onPlayerHit" else "onDadHit"
        val lines = mutableListOf("var e:Dynamic = cneNoteEvent(note, ${if (player) "true" else "false"});")
        if (side) lines += "${callName(sideName)}(e);"
        if (both) lines += "${callName("onNoteHit")}(e);"
        if (sing) {
            lines += "if (self != null && e.characters.indexOf(self) >= 0 && !e.animCancelled)"
            lines += "{"
            lines += "  var s:Dynamic = cneSingEvent(e);"
            lines += "  ${callName("onPlaySingAnim")}(s);"
            lines += "  if (s.cancelled) e.animCancelled = true;"
            lines += "}"
        }
        if (post) lines += "cneKeepHit(note, e);"
        lines += "return cneApplyNoteEvent(note, e);"
        return lines
    }

    private fun callName(name: String): String = renames[name] ?: name

    private fun writeAdapters(plan: Map<String, Adapter>, spriteFields: List<String>): String {
        val sb = StringBuilder()
        val all = LinkedHashMap(plan)
        if (spriteFields.isNotEmpty() && !("onCreate" !in all && "onCreate" in functions)) {
            val existing = all["onCreate"]
            val lines = spriteFields.map { "$it = getVar(${HxSource.hxString(it)});" }
            all["onCreate"] = Adapter("onCreate", "", lines + (existing?.lines ?: emptyList()), false)
        }
        all.values.filter { !it.passThrough }.forEach { a ->
            if (a.psych in functions && renames[a.psych] == null) {
                return@forEach
            }
            sb.append('\n')
            sb.append("function ").append(a.psych).append('(').append(a.params).append(")\n{\n")
            if (needsDisabled) sb.append("  if (scriptDisabled) return").append(if (a.returns) " Function_Continue" else "").append(";\n")
            a.lines.forEach { sb.append("  ").append(it).append('\n') }
            sb.append("}\n")
        }
        return sb.toString()
    }

    private fun receiverStart(tokens: List<HxTok>, end: Int): Int {
        var i = end
        while (i >= 0) {
            val t = tokens[i]
            when {
                t.kind == HxKind.SPACE -> {
                    val p = HxSource.prevIndex(tokens, i)
                    if (p >= 0 && tokens[p].isPunct(".")) i = p else return i + 1
                }
                t.isPunct(")") || t.isPunct("]") -> {
                    val open = if (t.isPunct(")")) "(" else "["
                    var depth = 0
                    var k = i
                    while (k >= 0) {
                        if (tokens[k].isPunct(t.text)) depth++
                        if (tokens[k].isPunct(open)) {
                            depth--
                            if (depth == 0) break
                        }
                        k--
                    }
                    i = k - 1
                    if (i >= 0 && tokens[i].kind != HxKind.IDENT && !tokens[i].isPunct(")") && !tokens[i].isPunct("]") && !tokens[i].isPunct(".")) return k
                }
                t.kind == HxKind.IDENT || t.kind == HxKind.STRING || t.kind == HxKind.NUMBER -> {
                    val p = HxSource.prevIndex(tokens, i)
                    if (p >= 0 && tokens[p].isPunct(".")) i = p - 1
                    else if (p >= 0 && tokens[p].isIdent("new")) return p
                    else return i
                }
                t.isPunct(".") -> i--
                else -> return i + 1
            }
        }
        return 0
    }

    private fun structural(input: List<HxTok>): List<HxTok> {
        var tokens = input
        var changed = true
        var guard = 0
        while (changed && guard++ < 2000) {
            changed = false
            for (i in tokens.indices) {
                val t = tokens[i]
                if (t.isIdent("for")) {
                    val open = HxSource.skipSpace(tokens, i + 1)
                    if (tokens.getOrNull(open)?.isPunct("(") != true) continue
                    val k = HxSource.skipSpace(tokens, open + 1)
                    val arrow = HxSource.skipSpace(tokens, k + 1)
                    if (tokens.getOrNull(k)?.kind == HxKind.IDENT && tokens.getOrNull(arrow)?.isPunct("=") == true && tokens.getOrNull(arrow + 1)?.isPunct(">") == true) {
                        val vIndex = HxSource.skipSpace(tokens, arrow + 2)
                        val inIndex = HxSource.skipSpace(tokens, vIndex + 1)
                        if (tokens.getOrNull(vIndex)?.kind != HxKind.IDENT || tokens.getOrNull(inIndex)?.isIdent("in") != true) continue
                        val close = HxSource.matching(tokens, open)
                        val mapTokens = tokens.subList(inIndex + 1, close)
                        val key = tokens[k].text
                        val value = tokens[vIndex].text
                        val bodyStart = HxSource.skipSpace(tokens, close + 1)
                        val head = HxSource.tokenize("for ($key in (${HxSource.text(mapTokens).trim()}).keys())")
                        val decl = HxSource.tokenize(" var $value = (${HxSource.text(mapTokens).trim()}).get($key); ")
                        tokens = if (tokens.getOrNull(bodyStart)?.isPunct("{") == true) {
                            tokens.subList(0, i) + head + tokens.subList(close + 1, bodyStart + 1) + decl + tokens.subList(bodyStart + 1, tokens.size)
                        } else {
                            val end = HxSource.statementEnd(tokens, bodyStart)
                            tokens.subList(0, i) + head + HxSource.tokenize(" {") + decl + tokens.subList(bodyStart, end) + HxSource.tokenize(" }") + tokens.subList(end, tokens.size)
                        }
                        changed = true
                        break
                    }
                }
                if (t.isIdent("is")) {
                    val p = HxSource.prevIndex(tokens, i)
                    val n = HxSource.skipSpace(tokens, i + 1)
                    val typeTok = tokens.getOrNull(n)
                    if (p < 0 || typeTok?.kind != HxKind.IDENT) continue
                    var typeEnd = n
                    while (tokens.getOrNull(typeEnd + 1)?.isPunct(".") == true && tokens.getOrNull(typeEnd + 2)?.kind == HxKind.IDENT) typeEnd += 2
                    val start = receiverStart(tokens, p)
                    val left = HxSource.text(tokens.subList(start, p + 1)).trim()
                    val type = HxSource.text(tokens.subList(n, typeEnd + 1))
                    tokens = tokens.subList(0, start) + HxSource.tokenize("Std.isOfType($left, $type)") + tokens.subList(typeEnd + 1, tokens.size)
                    changed = true
                    break
                }
                if (t.kind == HxKind.IDENT && tokens.getOrNull(HxSource.prevIndex(tokens, i))?.isPunct(".") == true) {
                    val open = HxSource.skipSpace(tokens, i + 1)
                    val isCall = tokens.getOrNull(open)?.isPunct("(") == true
                    val dot = HxSource.prevIndex(tokens, i)
                    val recvEnd = HxSource.prevIndex(tokens, dot)
                    if (recvEnd < 0) continue
                    val helper = when (t.text) {
                        "addShader" -> "cneAddShader"
                        "removeShader" -> "cneRemoveShader"
                        "addAnim" -> "cneAddAnim"
                        "loadSprite" -> "cneLoadSprite"
                        "playSingAnim" -> "cneSingAnim"
                        "hset" -> if (isShaderReceiver(tokens, recvEnd)) "cneShaderSet" else null
                        "hget" -> if (isShaderReceiver(tokens, recvEnd)) "cneShaderGet" else null
                        else -> null
                    }
                    if (helper != null && isCall) {
                        val close = HxSource.matching(tokens, open)
                        val start = receiverStart(tokens, recvEnd)
                        val recv = tokens.subList(start, recvEnd + 1)
                        val args = tokens.subList(open + 1, close)
                        helpers += helper.removePrefix("cne").replaceFirstChar { it.lowercaseChar() }
                        tokens = tokens.subList(0, start) + HxSource.tokenize("$helper(") + recv +
                            (if (args.any { it.kind != HxKind.SPACE }) HxSource.tokenize(", ") + args else emptyList()) + HxSource.tokenize(")") + tokens.subList(close + 1, tokens.size)
                        changed = true
                        break
                    }
                    if (t.text == "playAnim" && isCall && removeContext(tokens, open)?.let { tokens = it } != null) {
                        changed = true
                        break
                    }
                }
                if (t.isIdent("playAnim") && t.text !in functions && tokens.getOrNull(HxSource.prevIndex(tokens, i))?.isPunct(".") != true) {
                    val open = HxSource.skipSpace(tokens, i + 1)
                    if (tokens.getOrNull(open)?.isPunct("(") == true) {
                        val updated = removeContext(tokens, open)
                        if (updated != null) {
                            tokens = updated
                            changed = true
                            break
                        }
                    }
                }
                if (t.kind == HxKind.IDENT && t.text in shaderVars && tokens.getOrNull(HxSource.prevIndex(tokens, i))?.isPunct(".") != true) {
                    val dot = HxSource.skipSpace(tokens, i + 1)
                    val fieldIndex = HxSource.skipSpace(tokens, dot + 1)
                    val field = tokens.getOrNull(fieldIndex)
                    if (tokens.getOrNull(dot)?.isPunct(".") != true || field?.kind != HxKind.IDENT) continue
                    if (field.text in setOf("setFloat", "setInt", "setBool", "setFloatArray", "setIntArray", "setBoolArray", "getFloat", "getInt", "getBool", "getFloatArray", "setBitmapData", "data", "glFragmentSource", "glVertexSource", "hset", "hget")) continue
                    val after = HxSource.skipSpace(tokens, fieldIndex + 1)
                    val next = tokens.getOrNull(after)
                    if (next?.isPunct("(") == true) continue
                    if (next?.isPunct("=") == true && tokens.getOrNull(after + 1)?.isPunct("=") != true) {
                        val end = HxSource.statementEnd(tokens, after + 1)
                        val valueEnd = if (tokens.getOrNull(end - 1)?.isPunct(";") == true) end - 1 else end
                        helpers += "shaderSet"
                        val valueStart = HxSource.skipSpace(tokens, after + 1)
                        tokens = tokens.subList(0, i) + HxSource.tokenize("cneShaderSet(${t.text}, \"${field.text}\", ") + tokens.subList(valueStart, valueEnd).dropLastWhile { it.kind == HxKind.SPACE } + HxSource.tokenize(")") + tokens.subList(valueEnd, tokens.size)
                    } else {
                        helpers += "shaderGet"
                        tokens = tokens.subList(0, i) + HxSource.tokenize("cneShaderGet(${t.text}, \"${field.text}\")") + tokens.subList(fieldIndex + 1, tokens.size)
                    }
                    changed = true
                    break
                }
            }
        }
        return tokens
    }

    private fun removeContext(tokens: List<HxTok>, open: Int): List<HxTok>? {
        val close = HxSource.matching(tokens, open)
        val parts = splitArgs(tokens, open, close)
        if (parts.size < 3) return null
        val third = parts[2].filter { it.kind != HxKind.SPACE }
        val ctx = third.singleOrNull()
        val isContext = ctx != null && (ctx.kind == HxKind.STRING && ctx.text.trim('"', '\'').uppercase() in playContexts || ctx.kind == HxKind.IDENT && ctx.text in playContexts) ||
            (third.size == 3 && third[0].isIdent("PlayAnimContext"))
        if (!isContext) return null
        val keep = parts.filterIndexed { idx, _ -> idx != 2 }
        val rebuilt = mutableListOf<HxTok>()
        keep.forEachIndexed { idx, p ->
            if (idx > 0) rebuilt += HxTok(HxKind.PUNCT, ",", tokens[open].line)
            rebuilt += p
        }
        return tokens.subList(0, open + 1) + rebuilt + tokens.subList(close, tokens.size)
    }

    private fun isShaderReceiver(tokens: List<HxTok>, recvEnd: Int): Boolean {
        val t = tokens.getOrNull(recvEnd) ?: return false
        return t.kind == HxKind.IDENT && (t.text in shaderVars || t.text == "shader")
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
        if (start < close) parts += tokens.subList(start, close)
        return parts
    }

    private fun rewrite(input: List<HxTok>, extraLocals: Set<String>): String {
        val tokens = structural(input)
        val sb = StringBuilder()
        val locals = HashSet<String>(extraLocals)
        locals += HxSource.declaredLocals(tokens)
        locals += HxSource.arrowParams(tokens)
        var i = 0
        fun next(index: Int) = HxSource.skipSpace(tokens, index + 1)
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.kind == HxKind.STRING && t.text.startsWith("'") && t.text.contains('$')) {
                sb.append(interpolate(t.text, locals))
                i++
                continue
            }
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
                val renamed = methodRenames[t.text]
                when {
                    renamed != null && callNext -> sb.append(renamed)
                    t.text == "frameOffset" -> {
                        issue(t.line, "frameOffset is Codename-only; it became offset (Psych offsets are not scaled with the sprite).")
                        sb.append("offset")
                    }
                    t.text == "zoomFactor" -> {
                        issue(t.line, "zoomFactor is Codename-only and does nothing in Psych.")
                        sb.append("zoomFactor")
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
            val hasMember = tokens.getOrNull(n1)?.isPunct(".") == true
            val n2 = next(n1)
            val member = if (hasMember) tokens.getOrNull(n2)?.takeIf { it.kind == HxKind.IDENT }?.text else null
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
                t.text == "members" && member == "indexOf" -> {
                    helpers += "indexOf"
                    sb.append("cneIndexOf")
                    i = n2 + 1
                }
                t.text == "PlayState" && member == "instance" -> {
                    val n3 = next(n2)
                    val inner = if (tokens.getOrNull(n3)?.isPunct(".") == true) tokens.getOrNull(next(n3))?.takeIf { it.kind == HxKind.IDENT } else null
                    val n4 = if (inner != null) next(next(n3)) else -1
                    val innerMember = if (inner != null && tokens.getOrNull(n4)?.isPunct(".") == true) tokens.getOrNull(next(n4))?.takeIf { it.kind == HxKind.IDENT }?.text else null
                    if (inner?.text == "members" && innerMember == "indexOf") {
                        helpers += "indexOf"
                        sb.append("cneIndexOf")
                        i = next(n4) + 1
                    } else if (inner == null) {
                        sb.append("game")
                        i = n2 + 1
                    } else {
                        sb.append(gameMember(inner.text, inner.line, true))
                        i = next(n3) + 1
                    }
                }
                t.text == "Conductor" && member != null -> {
                    val mapped = conductorMembers[member]
                    if (mapped == null) {
                        sb.append("Conductor.").append(member)
                        if (member in setOf("getStepForTime", "getTimeForStep", "getBeatForTime", "getMeasureLength", "getMeasuresLength")) issue(t.line, "Conductor.$member is Codename-only.")
                    } else sb.append(mapped)
                    i = n2 + 1
                }
                t.text == "Paths" && member != null && member in pathsRenames -> {
                    sb.append("Paths.").append(pathsRenames.getValue(member))
                    i = n2 + 1
                }
                t.text == "Options" && member != null -> {
                    val mapped = options[member]
                    if (mapped == null) issue(t.line, "Options.$member has no Psych equivalent and became null.")
                    sb.append(mapped ?: "null")
                    i = n2 + 1
                }
                t.text == "FlxAxes" && member != null -> {
                    sb.append(when (member) {
                        "X" -> "0x01"
                        "Y" -> "0x10"
                        "NONE" -> "0x00"
                        else -> "0x11"
                    })
                    i = n2 + 1
                }
                t.text == "BlendMode" && member != null -> {
                    imports += "psychlua.LuaUtils"
                    sb.append("LuaUtils.blendModeFromString(\"").append(member.lowercase()).append("\")")
                    i = n2 + 1
                }
                t.text == "stage" && member != null -> {
                    when (member) {
                        "getSprite" -> {
                            sb.append("getVar")
                            i = n2 + 1
                        }
                        "stageSprites" -> {
                            sb.append("game.variables")
                            i = n2 + 1
                        }
                        "defaultZoom" -> {
                            helpers += "compat"
                            compat = true
                            sb.append("cne().stageZoom")
                            i = n2 + 1
                        }
                        else -> {
                            issue(t.line, "stage.$member is Codename-only (Psych stages are JSON).")
                            sb.append("null")
                            i = n2 + 1
                            if (tokens.getOrNull(next(n2))?.isPunct("(") == true) i = HxSource.matching(tokens, next(n2)) + 1
                        }
                    }
                }
                t.text == "SONG" && member != null -> {
                    val n3 = next(n2)
                    val sub = if (tokens.getOrNull(n3)?.isPunct(".") == true) tokens.getOrNull(next(n3))?.takeIf { it.kind == HxKind.IDENT }?.text else null
                    when {
                        member == "meta" && sub != null -> {
                            sb.append(when (sub) {
                                "name", "displayName" -> "PlayState.SONG.song"
                                "bpm" -> "PlayState.SONG.bpm"
                                "needsVoices" -> "PlayState.SONG.needsVoices"
                                else -> {
                                    issue(t.line, "SONG.meta.$sub has no Psych equivalent.")
                                    "null"
                                }
                            })
                            i = next(n3) + 1
                        }
                        member == "scrollSpeed" -> {
                            sb.append("PlayState.SONG.speed")
                            i = n2 + 1
                        }
                        member == "stage" -> {
                            sb.append("PlayState.SONG.stage")
                            i = n2 + 1
                        }
                        member == "strumLines" || member == "noteTypes" || member == "events" -> {
                            issue(t.line, "SONG.$member is part of Codename's chart format; Psych charts use sections (PlayState.SONG.notes).")
                            sb.append("PlayState.SONG.").append(if (member == "events") "events" else "notes")
                            i = n2 + 1
                        }
                        else -> {
                            sb.append("PlayState.SONG.").append(member)
                            i = n2 + 1
                        }
                    }
                }
                (t.text == "CustomShader" || t.text == "FunkinShader") && callNext && p?.isIdent("new") == true -> {
                    val close = HxSource.matching(tokens, n1)
                    val args = splitArgs(tokens, n1, close)
                    val first = args.firstOrNull()?.let { rewrite(it, locals).trim() } ?: "\"\""
                    removeTrailingNew(sb)
                    sb.append("game.createRuntimeShader(").append(first).append(")")
                    i = close + 1
                }
                (t.text == "FunkinSprite") && callNext && p?.isIdent("new") == true -> {
                    val close = HxSource.matching(tokens, n1)
                    val args = splitArgs(tokens, n1, close).map { rewrite(it, locals).trim() }
                    removeTrailingNew(sb)
                    helpers += "sprite"
                    imports += "psychlua.ModchartSprite"
                    sb.append("cneSprite(").append(listOf(args.getOrNull(0) ?: "0", args.getOrNull(1) ?: "0", args.getOrNull(2) ?: "null").joinToString(", ")).append(")")
                    i = close + 1
                }
                t.text == "FunkinSprite" -> {
                    imports += "psychlua.ModchartSprite"
                    sb.append("ModchartSprite")
                    i++
                }
                t.text == "FunkinText" -> {
                    sb.append("FlxText")
                    i++
                }
                t.text == "Strum" -> {
                    imports += "objects.StrumNote"
                    sb.append("StrumNote")
                    i++
                }
                t.text == "HealthIcon" -> {
                    imports += "objects.HealthIcon"
                    sb.append(t.text)
                    i++
                }
                t.text == "GameOverSubstate" -> {
                    imports += "substates.GameOverSubstate"
                    if (member == "script") issue(t.line, "GameOverSubstate.script is Codename-only; move that code into onGameOverStart.")
                    sb.append(t.text)
                    i++
                }
                t.text == "CoolUtil" -> {
                    imports += "backend.CoolUtil"
                    if (member != null && member !in setOf("coolTextFile", "floorDecimal", "dominantColor", "browserLoad", "openFolder", "boundTo", "listFromString", "colorFromString", "showPopUp")) issue(t.line, "CoolUtil.$member is Codename-only.")
                    sb.append(t.text)
                    i++
                }
                t.text == "Difficulty" -> {
                    imports += "backend.Difficulty"
                    sb.append(t.text)
                    i++
                }
                t.text == "importScript" && callNext -> {
                    issue(t.line, "importScript() has no Psych equivalent; the imported script was not loaded (copy its code here, or load it with game.startHScriptsNamed).")
                    sb.append("null")
                    i = HxSource.matching(tokens, n1) + 1
                }
                t.text == "disableScript" && callNext -> {
                    needsDisabled = true
                    sb.append("scriptDisabled = true")
                    i = HxSource.matching(tokens, n1) + 1
                }
                t.text == "this" && target.scope == CodenameScope.CHARACTER -> {
                    sb.append("self")
                    i++
                }
                target.scope == CodenameScope.CHARACTER && t.text in characterMembers && !(t.text in setOf("x", "y", "color", "alpha", "width", "height", "visible", "angle") && p?.isIdent("var") == true) -> {
                    sb.append("self.").append(if (callNext && methodRenames.containsKey(t.text)) methodRenames.getValue(t.text) else characterRenames[t.text] ?: t.text)
                    if (t.text == "playSingAnim") issue(t.line, "playSingAnim() was replaced by a helper that plays sing<DIR><suffix>.")
                    if (t.text == "lastHit" || t.text == "globalOffset" || t.text == "cameraOffset") issue(t.line, "Character.${t.text} is Codename-only.")
                    i++
                }
                t.text in context.stageSprites && !callNext -> {
                    usedSprites += t.text
                    sb.append(t.text)
                    i++
                }
                t.text in compatMembers -> {
                    helpers += "compat"
                    compat = true
                    sb.append("cne().").append(t.text)
                    i++
                }
                t.text == "strumLines" -> {
                    helpers += "strumLines"
                    issue(t.line, "strumLines became a read-only list built from playerStrums/opponentStrums; Psych has exactly two strumlines.")
                    sb.append("cneStrumLines()")
                    i++
                }
                t.text == "camZoomingMult" -> {
                    issue(t.line, "camZoomingMult works differently in Psych (it scales the section bop).")
                    sb.append(t.text)
                    i++
                }
                bareMembers.containsKey(t.text) && !callNext || t.text == "lerp" && callNext -> {
                    val mapped = bareMembers.getValue(t.text)
                    if (mapped.startsWith("Difficulty")) imports += "backend.Difficulty"
                    if (mapped.startsWith("GameOverSubstate")) imports += "substates.GameOverSubstate"
                    sb.append(mapped)
                    i++
                }
                unsupportedMembers.containsKey(t.text) -> {
                    issue(t.line, "${t.text} has no Psych equivalent: ${unsupportedMembers.getValue(t.text)}")
                    sb.append("null")
                    i++
                }
                t.text == "gameOver" && callNext -> {
                    sb.append("game.health = -1")
                    i = HxSource.matching(tokens, n1) + 1
                }
                t.text == "startCutscene" && callNext -> {
                    issue(t.line, "startCutscene() is Codename-only; play videos with game.startVideo.")
                    sb.append("null")
                    i = HxSource.matching(tokens, n1) + 1
                }
                else -> {
                    sb.append(t.text)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun removeTrailingNew(sb: StringBuilder) {
        val text = sb.toString().trimEnd()
        if (text.endsWith("new")) {
            sb.setLength(text.length - 3)
        }
    }

    private fun gameMember(member: String, line: Int, prefixed: Boolean): String {
        unsupportedMembers[member]?.let { issue(line, "PlayState.$member has no Psych equivalent: $it") }
        return when {
            member in compatMembers -> {
                helpers += "compat"
                compat = true
                "cne().$member"
            }
            member == "strumLines" -> {
                helpers += "strumLines"
                "cneStrumLines()"
            }
            member == "stage" -> {
                issue(line, "PlayState.instance.stage is Codename-only (Psych stages are JSON); stage sprites are in game.variables.")
                "game.variables"
            }
            bareMembers.containsKey(member) -> bareMembers.getValue(member).let { if (it.startsWith("PlayState.") || it.startsWith("(") || it.startsWith("Flx") || it.startsWith("ClientPrefs") || it.startsWith("Difficulty") || it.startsWith("GameOver") || it.first().isDigit() || it == "false" || it == "this") it else "game.$it" }
            else -> "game.$member"
        }
    }

    private fun interpolate(literal: String, locals: Set<String>): String {
        val body = literal.substring(1, literal.length - 1)
        val out = StringBuilder("'")
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    out.append(c).append(body[i + 1])
                    i += 2
                }
                c == '$' && body.getOrNull(i + 1) == '$' -> {
                    out.append("$$")
                    i += 2
                }
                c == '$' && body.getOrNull(i + 1) == '{' -> {
                    var depth = 0
                    var j = i + 1
                    while (j < body.length) {
                        if (body[j] == '{') depth++
                        if (body[j] == '}') {
                            depth--
                            if (depth == 0) break
                        }
                        j++
                    }
                    val expr = body.substring(i + 2, minOf(j, body.length))
                    out.append("${'$'}{").append(rewrite(HxSource.tokenize(expr), locals)).append("}")
                    i = j + 1
                }
                c == '$' && body.getOrNull(i + 1)?.let { it.isLetter() || it == '_' } == true -> {
                    var j = i + 1
                    while (j < body.length && (body[j].isLetterOrDigit() || body[j] == '_')) j++
                    val name = body.substring(i + 1, j)
                    val mapped = rewrite(HxSource.tokenize(name), locals)
                    out.append(if (mapped == name) "$$name" else "${'$'}{$mapped}")
                    i = j
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.append("'").toString()
    }
}
