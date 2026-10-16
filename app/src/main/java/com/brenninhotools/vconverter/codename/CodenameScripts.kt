package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.HxImports
import com.brenninhotools.vconverter.script.LuaToHScript
import com.brenninhotools.vconverter.script.Naturalizer
import com.brenninhotools.vconverter.script.ReflectCalls
import com.brenninhotools.vconverter.script.ScriptIssue

enum class CodenameScope { GLOBAL, SONG, STAGE, EVENT, NOTE_TYPE, CHARACTER }

data class CodenameScriptTarget(val scope: CodenameScope, val key: String = "")

data class CodenameScriptContext(
    val stageSprites: Set<String> = emptySet(),
    val noteTypeNames: Map<String, String> = emptyMap(),
    val events: List<EventDefinition> = emptyList()
)

data class CodenameScriptResult(val className: String, val moduleId: String, val source: String, val issues: List<ScriptIssue>, val callbacks: List<String>)

class CodenameScriptConverter(
    private val className: String,
    private val moduleId: String,
    private val target: CodenameScriptTarget,
    private val context: CodenameScriptContext = CodenameScriptContext()
) {
    private enum class Kind { SPACE, IDENT, NUMBER, STRING, PUNCT }

    private data class Tok(val kind: Kind, val text: String, val line: Int)

    private class Segment(val kind: String, val name: String, val head: List<Tok>, val body: List<Tok>, val braced: Boolean, val line: Int)

    private val issues = mutableListOf<ScriptIssue>()

    companion object {
        private val importMap = mapOf(
            "funkin.game.PlayState" to "funkin.play.PlayState",
            "funkin.backend.system.Conductor" to "funkin.Conductor",
            "funkin.backend.assets.Paths" to "funkin.Paths",
            "funkin.backend.FunkinSprite" to "funkin.graphics.FunkinSprite",
            "funkin.game.Character" to "funkin.play.character.BaseCharacter",
            "funkin.backend.system.Logs" to null,
            "funkin.backend.utils.CoolUtil" to null,
            "funkin.options.Options" to null,
            "funkin.backend.scripting.Script" to null,
            "funkin.backend.scripting.events" to null
        )

        private val droppedPackages = listOf("funkin.backend.", "funkin.game.", "funkin.menus.", "funkin.options.", "funkin.editors.", "funkin.savedata.", "funkin.backend", "hxvlc.", "sys.", "lime.app.")

        private const val GAME = "PlayState.instance"

        private val gameMembers = mapOf(
            "boyfriend" to "currentStage.getBoyfriend()",
            "bf" to "currentStage.getBoyfriend()",
            "dad" to "currentStage.getDad()",
            "gf" to "currentStage.getGirlfriend()",
            "stage" to "currentStage",
            "defaultCamZoom" to "currentCameraZoom",
            "camFollow" to "cameraFollowPoint",
            "curStage" to "currentStageId",
            "playerStrums" to "playerStrumline",
            "cpuStrums" to "opponentStrumline",
            "scoreTxt" to "scoreText",
            "inCutscene" to "isInCutscene",
            "difficulty" to "currentDifficulty",
            "defaultHudZoom" to "defaultHUDCameraZoom",
            "camGame" to "camGame",
            "camHUD" to "camHUD",
            "health" to "health",
            "songScore" to "songScore",
            "vocals" to "vocals",
            "iconP1" to "iconP1",
            "iconP2" to "iconP2",
            "healthBar" to "healthBar",
            "healthBarBG" to "healthBarBG"
        )

        private val bareGlobals = mapOf(
            "inst" to "FlxG.sound.music",
            "curSong" to "$GAME.currentSong.id",
            "scrollSpeed" to "$GAME.playerStrumline.scrollSpeed",
            "downscroll" to "Preferences.downscroll",
            "misses" to "Highscore.tallies.missed",
            "combo" to "Highscore.tallies.combo",
            "isStoryMode" to "PlayStatePlaylist.isStoryMode",
            "curBeat" to "Conductor.instance.currentBeat",
            "curStep" to "Conductor.instance.currentStep",
            "curMeasure" to "Conductor.instance.currentMeasure",
            "curBeatFloat" to "Conductor.instance.currentBeatTime",
            "curStepFloat" to "Conductor.instance.currentStepTime",
            "curMeasureFloat" to "Conductor.instance.currentMeasureTime",
            "state" to "FlxG.state",
            "game" to GAME
        )

        private val unsupportedMembers = setOf(
            "SONG", "graphicCache", "seenCutscene", "camZooming", "camZoomingInterval", "camZoomingStrength", "camZoomingOffset", "camZoomingEvery", "strumLine",
            "comboGroup", "accuracy", "events", "introSprites", "introSounds", "paused", "startedCountdown", "canPause",
            "maxHealth", "gfSpeed", "camGameZoomLerp", "camHUDZoomLerp", "curCameraTarget", "cameraFocusOffset", "opponentMode",
            "coopMode", "ratingManager", "splashHandler", "hits", "accuracyTxt", "missesTxt", "camZoomingMult", "useCamZoomMult"
        )

        private val conductorMembers = mapOf(
            "songPosition" to "songPosition",
            "crochet" to "beatLengthMs",
            "stepCrochet" to "stepLengthMs",
            "bpm" to "bpm",
            "curBeat" to "currentBeat",
            "curStep" to "currentStep",
            "curMeasure" to "currentMeasure",
            "curBeatFloat" to "currentBeatTime",
            "curStepFloat" to "currentStepTime",
            "curMeasureFloat" to "currentMeasureTime",
            "beatsPerMeasure" to "beatsPerMeasure",
            "stepsPerBeat" to "stepsPerBeat"
        )

        private val options = mapOf(
            "downscroll" to "Preferences.downscroll",
            "flashingMenu" to "Preferences.flashingLights",
            "camZoomOnBeat" to "Preferences.zoomCamera",
            "framerate" to "Preferences.framerate",
            "naughtyness" to "Preferences.naughtyness",
            "lowMemoryMode" to "false",
            "gameplayShaders" to "true",
            "antialiasing" to "true",
            "ghostTapping" to "true",
            "middlescroll" to "false"
        )

        private val methodRenames = mapOf(
            "hset" to "setFloat",
            "playAnim" to "playAnimation",
            "hasAnim" to "hasAnimation",
            "getAnimName" to "getCurrentAnimation",
            "getSprite" to "getNamedProp"
        )

        private val codenameClasses = setOf(
            "CoolUtil", "Options", "FunkinText", "HealthIcon", "Note", "Strum", "StrumLine", "ModState", "ModSubState",
            "GameOverSubstate", "PauseSubState", "PauseSubstate", "HudCamera", "CustomShader", "FunkinShader", "Logs", "Main",
            "Application", "FlxAnimate", "Boyfriend", "Character"
        )

        val callbacks = setOf(
            "create", "postCreate", "update", "postUpdate", "beatHit", "stepHit", "measureHit", "onSongStart", "onStartSong",
            "onSongEnd", "onStartCountdown", "onPostStartCountdown", "onCountdown", "onPostCountdown", "onNoteHit", "onPlayerHit",
            "onDadHit", "onPostNoteHit", "onPlayerMiss", "onPostPlayerMiss", "onNoteCreation", "onPostNoteCreation", "onEvent",
            "onPostEvent", "onGamePause", "onGameOver", "onFocus", "onFocusLost", "destroy"
        )

        private val unsupportedCallbacks = setOf(
            "draw", "postDraw", "onCameraMove", "onStrumCreation", "onPostStrumCreation", "onRatingUpdate", "onPostGenerateStrums",
            "onPreGenerateStrums", "onSubstateOpen", "onSubstateClose", "onInputUpdate", "onPostInputUpdate", "onBopZoom",
            "onPostBopZoom", "onVocalsResync", "onSplashShown", "onRatingsShown", "onPostRatingsShown", "onPostGameOver",
            "onStageXMLParsed", "onStageNodeParsed", "onPostStageCreation", "onDance", "onPlayAnim", "onPlaySingAnim", "onGetCamPos",
            "onTryDance", "onCharacterXMLParsed", "onCharacterNodeParsed", "onStageDestroy"
        )
    }

    fun convert(original: String): CodenameScriptResult {
        val reflect = ReflectCalls.toVSlice(original)
        reflect.unsupported.forEach { issue(0, if (it.endsWith(".typeof")) "Type.typeof is blocked in V-Slice scripts." else "$it doesn't exist in V-Slice's Reflect (funkin.util.ReflectUtil); rewrite that part.") }
        val segs = segments(tokenize(reflect.text))
        val functions = segs.filter { it.kind == "function" }.map { it.name }.toSet()
        val fields = segs.filter { it.kind == "var" && it.name.isNotEmpty() }.map { it.name }.toSet()
        val imports = linkedSetOf("flixel.FlxG", "funkin.Conductor", "funkin.modding.module.Module", "funkin.play.PlayState")
        segs.filter { it.kind == "import" }.forEach { seg ->
            val path = seg.head.filter { it.kind != Kind.SPACE }.drop(1).takeWhile { !(it.kind == Kind.PUNCT && it.text == ";") }.joinToString("") { it.text }
            when {
                importMap.containsKey(path) -> importMap[path]?.let { imports += it }
                droppedPackages.any { path.startsWith(it) } -> issue(seg.line, "Import '$path' is Codename-specific and was dropped.")
                path.isNotEmpty() -> imports += path
            }
        }
        functions.filter { it in unsupportedCallbacks }.forEach { issue(0, "Codename callback $it() has no V-Slice equivalent and never runs.") }

        val boot = mutableListOf<String>()
        val fieldInit = mutableListOf<String>()
        segs.forEach { seg ->
            when (seg.kind) {
                "var" -> if (seg.body.isNotEmpty()) fieldInit += "h_${seg.name} = ${rewrite(seg.body, functions, fields).trim()};"
                "statement" -> boot += rewrite(seg.body, functions, fields).trim().lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            }
        }

        val arities = LinkedHashMap<String, Int>()
        val tail = mutableListOf<String>()
        segs.filter { it.kind == "function" }.forEach { seg ->
            val inner = seg.head.joinToString("") { it.text }.substringAfter('(').substringBeforeLast(')')
            arities[seg.name] = if (inner.isBlank()) 0 else inner.split(',').size
            tail += ""
            val params = paramNames(seg.head, seg.head.indexOfFirst { it.kind == Kind.PUNCT && it.text == "(" }.coerceAtLeast(0))
            tail += "function h_${seg.name}${rewrite(seg.head, functions, fields, params).trim()}"
            tail += "{"
            val bodyText = rewrite(seg.body, functions, fields, params).trimEnd()
            val bodyLines = (if (seg.braced) bodyText else bodyText.trim().let { if (it.endsWith(";") || it.endsWith("}")) it else "$it;" }).lines().filter { it.isNotBlank() }
            val indent = bodyLines.minOfOrNull { line -> line.length - line.trimStart().length } ?: 0
            bodyLines.forEach { tail += "  " + it.drop(indent).trimEnd() }
            tail += "}"
        }

        val hoisted = segs.filter { it.kind == "class" || it.kind == "interface" || it.kind == "enum" }
        hoisted.forEach { issue(it.line, "Script ${it.kind} ${it.name} was moved next to the module; Polymod loads it as its own scripted ${it.kind}.") }
        val used = callbacks.filter { it in functions }
        val text = write(imports.toList(), fields.toList(), fieldInit, boot, arities, tail)
        val classes = hoisted.joinToString("") { "\n" + rewrite(it.body, emptySet(), emptySet()).trim() + "\n" }
        val final = stripGenerics(Naturalizer.module(text) + classes)
        return CodenameScriptResult(className, moduleId, HxImports.complete(final.replace(Regex("\n{3,}"), "\n\n")), issues.sortedBy { it.line }, used)
    }

    private fun issue(line: Int, message: String) {
        if (issues.none { it.message == message }) issues += ScriptIssue(line, message)
    }

    private fun write(
        imports: List<String>,
        fields: List<String>,
        fieldInit: List<String>,
        boot: List<String>,
        arities: Map<String, Int>,
        tail: List<String>
    ): String {
        val out = StringBuilder()
        var indent = 0
        fun line(text: String = "") {
            if (text.isEmpty()) out.append('\n') else out.append("  ".repeat(indent)).append(text).append('\n')
        }
        fun open(text: String) {
            line(text)
            line("{")
            indent++
        }
        fun close() {
            indent--
            line("}")
        }
        fun has(name: String) = arities.containsKey(name)
        fun call(name: String, vararg args: String): String {
            val n = arities[name] ?: 0
            return "h_$name(${(0 until n).joinToString(", ") { args.getOrNull(it) ?: "null" }});"
        }
        fun override(method: String, type: String, body: () -> Unit) {
            line()
            open("override public function $method(event:$type):Void")
            line("super.$method(event);")
            body()
            close()
        }

        val needsHit = listOf("onNoteHit", "onPlayerHit", "onDadHit", "onPostNoteHit").any(::has)
        val needsMiss = listOf("onPlayerMiss", "onPostPlayerMiss").any(::has)
        val needsQueue = needsHit || needsMiss

        imports.distinct().forEach { line("import $it;") }
        line()
        open("class $className extends Module")
        line("var activeGame:Dynamic = null;")
        line("var closed:Bool = false;")
        if (needsQueue) line("var pendingAnims:Array<Dynamic> = [];")
        fields.forEach { line("var h_$it:Dynamic = null;") }
        line()
        open("public function new()")
        line("super(${LuaToHScript.hxString(moduleId)}, 1100);")
        close()
        line()
        open("function inScope():Bool")
        line("var game:Dynamic = PlayState.instance;")
        line("if (game == null) return false;")
        scope().forEach { line(it) }
        close()
        line()
        open("function isActive():Bool")
        line("return !closed && activeGame != null && activeGame == PlayState.instance;")
        close()
        line()
        open("function boot():Bool")
        line("var game:Dynamic = PlayState.instance;")
        line("if (game == null) return false;")
        line("if (activeGame == game) return !closed;")
        line("if (!inScope()) return false;")
        line("activeGame = game;")
        line("closed = false;")
        if (needsQueue) line("pendingAnims = [];")
        fields.forEach { line("h_$it = null;") }
        fieldInit.forEach { line(it) }
        boot.forEach { line(it) }
        if (has("create")) line(call("create"))
        if (has("postCreate")) line(call("postCreate"))
        line("return !closed;")
        close()
        line()
        open("public function disableScript():Void")
        line("closed = true;")
        close()
        line()
        open("public function hscriptCall(name:String, args:Array<Dynamic>):Dynamic")
        line("if (!isActive()) return null;")
        line("if (args == null) args = [];")
        open("switch (name)")
        arities.forEach { (name, arity) ->
            line("case ${LuaToHScript.hxString(name)}: return h_$name(${(0 until arity).joinToString(", ") { "args[$it]" }});")
        }
        line("default: return null;")
        close()
        close()

        override("onCountdownStart", "CountdownScriptEvent") {
            line("if (!boot()) return;")
            if (has("onStartCountdown") || has("onPostStartCountdown")) {
                line("var e:Dynamic = cancellable(event);")
                if (has("onStartCountdown")) line(call("onStartCountdown", "e"))
                line("if (e.cancelled) return;")
                if (has("onPostStartCountdown")) line(call("onPostStartCountdown", "e"))
            }
        }
        if (has("onCountdown") || has("onPostCountdown")) {
            override("onCountdownStep", "CountdownScriptEvent") {
                line("if (!isActive()) return;")
                line("var step:String = Std.string(event.step);")
                line("var tick:Int = step == \"THREE\" ? 0 : (step == \"TWO\" ? 1 : (step == \"ONE\" ? 2 : (step == \"GO\" ? 3 : 4)));")
                line("var e:Dynamic = cancellable(event);")
                line("e.swagCounter = tick;")
                if (has("onCountdown")) line(call("onCountdown", "e"))
                if (has("onPostCountdown")) line(call("onPostCountdown", "e"))
            }
        }
        if (has("update") || has("postUpdate") || needsQueue) {
            override("onUpdate", "UpdateScriptEvent") {
                line("if (!isActive()) return;")
                if (needsQueue) line("flushAnims();")
                if (has("update")) line(call("update", "event.elapsed"))
                if (has("postUpdate")) line(call("postUpdate", "event.elapsed"))
            }
        }
        if (has("beatHit") || has("measureHit")) {
            override("onBeatHit", "SongTimeScriptEvent") {
                line("if (!isActive()) return;")
                if (has("beatHit")) line(call("beatHit", "event.beat"))
                if (has("measureHit")) {
                    line("var perMeasure:Int = Std.int(Conductor.instance.beatsPerMeasure);")
                    line("if (perMeasure > 0 && event.beat % perMeasure == 0) " + call("measureHit", "Std.int(event.beat / perMeasure)"))
                }
            }
        }
        if (has("stepHit")) override("onStepHit", "SongTimeScriptEvent") {
            line("if (!isActive()) return;")
            line(call("stepHit", "event.step"))
        }
        if (has("onSongStart") || has("onStartSong")) override("onSongStart", "ScriptEvent") {
            line("if (!isActive()) return;")
            if (has("onStartSong")) line(call("onStartSong"))
            if (has("onSongStart")) line(call("onSongStart"))
        }
        if (has("onSongEnd")) override("onSongEnd", "ScriptEvent") {
            line("if (!isActive()) return;")
            line("var e:Dynamic = cancellable(event);")
            line(call("onSongEnd", "e"))
        }
        if (needsHit) override("onNoteHit", "HitNoteScriptEvent") {
            line("if (!isActive()) return;")
            line("var player:Bool = event.note.noteData.data < 4;")
            line("var e:Dynamic = noteEvent(event, player);")
            if (has("onPlayerHit")) line("if (player) " + call("onPlayerHit", "e"))
            if (has("onDadHit")) line("if (!player) " + call("onDadHit", "e"))
            if (has("onNoteHit")) line(call("onNoteHit", "e"))
            if (has("onPostNoteHit")) line(call("onPostNoteHit", "e"))
            line("applyNoteEvent(event, e, false);")
        }
        if (needsMiss) {
            override("onNoteMiss", "NoteScriptEvent") {
                line("if (!isActive()) return;")
                line("var e:Dynamic = noteEvent(event, true, true);")
                if (has("onPlayerMiss")) line(call("onPlayerMiss", "e"))
                if (has("onPostPlayerMiss")) line(call("onPostPlayerMiss", "e"))
                line("applyNoteEvent(event, e, true);")
            }
            override("onNoteGhostMiss", "GhostMissNoteScriptEvent") {
                line("if (!isActive()) return;")
                line("var e:Dynamic = ghostEvent(event);")
                if (has("onPlayerMiss")) line(call("onPlayerMiss", "e"))
                if (has("onPostPlayerMiss")) line(call("onPostPlayerMiss", "e"))
                line("if (e.cancelled) event.cancel();")
            }
        }
        if (has("onNoteCreation") || has("onPostNoteCreation")) override("onNoteIncoming", "NoteScriptEvent") {
            line("if (!isActive()) return;")
            line("var e:Dynamic = noteEvent(event, event.note.noteData.data < 4, false, true);")
            if (has("onNoteCreation")) line(call("onNoteCreation", "e"))
            if (has("onPostNoteCreation")) line(call("onPostNoteCreation", "e"))
        }
        if (has("onEvent") || has("onPostEvent")) override("onSongEvent", "SongEventScriptEvent") {
            line("if (!isActive()) return;")
            line("var e:Dynamic = songEvent(event);")
            if (has("onEvent")) line(call("onEvent", "e"))
            line("if (e.cancelled) event.cancel();")
            if (has("onPostEvent")) line(call("onPostEvent", "e"))
        }
        if (has("onGamePause")) override("onPause", "PauseScriptEvent") {
            line("if (!isActive()) return;")
            line("var e:Dynamic = cancellable(event);")
            line(call("onGamePause", "e"))
        }
        if (has("onGameOver")) override("onGameOver", "ScriptEvent") {
            line("if (!isActive()) return;")
            line("var e:Dynamic = cancellable(event);")
            line(call("onGameOver", "e"))
        }
        if (has("onFocus")) override("onFocusGained", "FocusScriptEvent") {
            line("if (!isActive()) return;")
            line(call("onFocus"))
        }
        if (has("onFocusLost")) override("onFocusLost", "FocusScriptEvent") {
            line("if (!isActive()) return;")
            line(call("onFocusLost"))
        }
        override("onStateChangeBegin", "StateChangeScriptEvent") {
            open("if (activeGame != null)")
            if (has("destroy")) line("if (!closed) " + call("destroy"))
            line("activeGame = null;")
            close()
        }

        val body = StringBuilder(out)
        tail.forEach { body.append(if (it.isEmpty()) "\n" else "  $it\n") }
        val code = body.toString()
        val helpers = StringBuilder()
        fun uses(name: String) = Regex("(?<![\\w.])$name\\(").containsMatchIn(code + helpers)
        if (uses("noteEvent") || uses("ghostEvent")) helpers.append(noteNames())
        if (uses("noteEvent")) helpers.append(NOTE_EVENT)
        if (uses("ghostEvent")) helpers.append(GHOST_EVENT)
        if (uses("applyNoteEvent")) helpers.append(APPLY_NOTE_EVENT)
        if (needsQueue) helpers.append(FLUSH_ANIMS)
        if (uses("songEvent")) helpers.append(songEventHelper())
        if (uses("cancellable") || uses("noteEvent") || uses("ghostEvent") || uses("songEvent")) helpers.append(CANCELLABLE)
        if (uses("add") || uses("insert") || uses("remove")) helpers.append(STAGE_HELPERS)
        return body.append(helpers).append("}\n").toString()
    }

    private fun scope(): List<String> = when (target.scope) {
        CodenameScope.GLOBAL -> listOf("return true;")
        CodenameScope.SONG -> listOf("return game.currentSong != null && game.currentSong.id == ${LuaToHScript.hxString(target.key)};")
        CodenameScope.STAGE -> listOf("return game.currentStageId == ${LuaToHScript.hxString(target.key)};")
        CodenameScope.CHARACTER -> listOf(
            "var stage:Dynamic = game.currentStage;",
            "if (stage == null) return false;",
            "for (c in [stage.getBoyfriend(), stage.getDad(), stage.getGirlfriend()]) if (c != null && c.characterId == ${LuaToHScript.hxString(target.key)}) return true;",
            "return false;"
        )
        CodenameScope.EVENT -> listOf(
            "if (game.currentChart == null) return false;",
            "var events:Array<Dynamic> = game.currentChart.events;",
            "for (e in events) if (e.eventKind == ${LuaToHScript.hxString(CodenameEvents.eventId(target.key))}) return true;",
            "return false;"
        )
        CodenameScope.NOTE_TYPE -> listOf(
            "if (game.currentChart == null) return false;",
            "var notes:Array<Dynamic> = game.currentChart.notes;",
            "for (n in notes) if (n.kind == ${LuaToHScript.hxString(CodenameToVSliceChart.noteKindFor(target.key))}) return true;",
            "return false;"
        )
    }

    private fun noteNames(): String {
        val cases = context.noteTypeNames.entries.filter { it.key !in setOf("", "alt", "noanim") }.joinToString("") { (kind, name) -> "      case ${LuaToHScript.hxString(kind)}: return ${LuaToHScript.hxString(name)};\n" }
        return """
  function noteTypeName(kind:Dynamic):String
  {
    if (kind == null) return null;
    switch (Std.string(kind))
    {
      case "": return null;
$cases      case "alt": return "Alt Anim Note";
      case "noanim": return "No Anim Note";
      default: return Std.string(kind);
    }
  }
"""
    }

    private fun songEventHelper(): String {
        val cases = StringBuilder()
        context.events.forEach { def ->
            cases.append("      case ${LuaToHScript.hxString(def.id)}:\n")
            cases.append("        name = ${LuaToHScript.hxString(def.name)};\n")
            cases.append("        params = [" + def.params.joinToString(", ") { "field(v, ${LuaToHScript.hxString(it.key)})" } + "];\n")
        }
        return """
  function field(v:Dynamic, key:String):Dynamic
  {
    if (v == null || !Reflect.isObject(v)) return null;
    return Reflect.field(v, key);
  }

  function songEvent(event:Dynamic):Dynamic
  {
    var data:Dynamic = event.eventData;
    var kind:String = Std.string(data.eventKind);
    var v:Dynamic = data.value;
    var name:String = kind;
    var params:Array<Dynamic> = [];
    switch (kind)
    {
      case "FocusCamera":
        name = "Camera Movement";
        var c:Dynamic = Reflect.isObject(v) ? field(v, "char") : v;
        params = [c == 0 ? 1 : (c == 1 ? 0 : 2), field(v, "ease") != "INSTANT", field(v, "duration"), field(v, "ease"), field(v, "easeDir"), field(v, "x"), field(v, "y")];
      case "PlayAnimation":
        name = "Play Animation";
        var t:Dynamic = field(v, "target");
        params = [t == "bf" || t == "boyfriend" || t == "player" ? 1 : (t == "dad" || t == "opponent" ? 0 : 2), field(v, "anim"), field(v, "force") != false, "NONE"];
      case "ScrollSpeed":
        name = "Scroll Speed Change";
        params = [field(v, "ease") != "INSTANT", field(v, "scroll"), field(v, "duration"), field(v, "ease"), field(v, "easeDir"), field(v, "absolute") == false];
      case "ZoomCamera":
        name = "Camera Zoom";
        params = [field(v, "ease") != "INSTANT", field(v, "zoom"), "camGame", field(v, "duration"), field(v, "ease"), field(v, "easeDir"), field(v, "mode"), false];
      case "SetCameraBop":
        name = "Camera Modulo Change";
        params = [field(v, "rate"), field(v, "intensity"), "BEAT", field(v, "offset")];
$cases      default:
        if (Reflect.isObject(v)) for (k in Reflect.fields(v)) params.push(Reflect.field(v, k));
        else params = [v];
    }
    var e:Dynamic = cancellable(event);
    e.event = {name: name, time: data.time, params: params};
    return e;
  }
"""
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
                c == '/' && source.getOrNull(i + 1) == '/' -> while (i < source.length && source[i] != '\n') i++
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

    private fun segments(tokens: List<Tok>): List<Segment> {
        val result = mutableListOf<Segment>()
        val modifiers = setOf("public", "private", "static", "override", "inline", "dynamic")
        var i = 0
        fun skipSpace(from: Int): Int {
            var j = from
            while (j < tokens.size && tokens[j].kind == Kind.SPACE) j++
            return j
        }
        fun statementEnd(start: Int): Int {
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
                                val k = skipSpace(j + 1)
                                val next = tokens.getOrNull(k)
                                if (next == null || !(next.kind == Kind.IDENT && (next.text == "else" || next.text == "catch")) && !(next.kind == Kind.PUNCT && (next.text == ";" || next.text == ")" || next.text == "," || next.text == "."))) return j + 1
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
            i = skipSpace(i)
            if (i >= tokens.size) break
            var t = tokens[i]
            val line = t.line
            var j = i
            while (tokens.getOrNull(j)?.let { it.kind == Kind.IDENT && it.text in modifiers } == true) j = skipSpace(j + 1)
            if (j != i) t = tokens.getOrNull(j) ?: break
            if (t.kind == Kind.IDENT && (t.text == "class" || t.text == "interface" || t.text == "enum")) {
                var k = j
                while (k < tokens.size && !(tokens[k].kind == Kind.PUNCT && tokens[k].text == "{")) k++
                var d = 0
                var end = k
                while (end < tokens.size) {
                    val tk = tokens[end]
                    if (tk.kind == Kind.PUNCT && tk.text == "{") d++
                    if (tk.kind == Kind.PUNCT && tk.text == "}") {
                        d--
                        if (d == 0) break
                    }
                    end++
                }
                val name = tokens.getOrNull(skipSpace(j + 1))?.text ?: ""
                result += Segment(t.text, name, emptyList(), tokens.subList(j, minOf(end + 1, tokens.size)), true, line)
                i = minOf(end + 1, tokens.size)
                continue
            }
            if (t.kind == Kind.IDENT && t.text == "typedef") {
                val end = statementEnd(j)
                result += Segment("typedef", "", emptyList(), emptyList(), false, line)
                i = end
                continue
            }
            if (t.kind == Kind.IDENT && (t.text == "import" || t.text == "using" || t.text == "package")) {
                val end = statementEnd(j)
                if (t.text == "import") result += Segment("import", "", tokens.subList(j, end), emptyList(), false, line)
                i = end
                continue
            }
            if (t.kind == Kind.IDENT && t.text == "function") {
                val n = skipSpace(j + 1)
                val nameTok = tokens.getOrNull(n)
                if (nameTok != null && nameTok.kind == Kind.IDENT) {
                    var k = n + 1
                    var depth = 0
                    while (k < tokens.size) {
                        val tk = tokens[k]
                        if (tk.kind == Kind.PUNCT && tk.text == "(") depth++
                        if (tk.kind == Kind.PUNCT && tk.text == ")") {
                            depth--
                            if (depth == 0) break
                        }
                        k++
                    }
                    var bodyStart = skipSpace(k + 1)
                    if (tokens.getOrNull(bodyStart)?.let { it.kind == Kind.PUNCT && it.text == ":" } == true) {
                        bodyStart = skipSpace(bodyStart + 1)
                        while (bodyStart < tokens.size && !(tokens[bodyStart].kind == Kind.PUNCT && tokens[bodyStart].text == "{")) {
                            if (tokens[bodyStart].kind == Kind.SPACE && tokens[bodyStart].text == "\n") break
                            bodyStart++
                        }
                        bodyStart = skipSpace(bodyStart)
                    }
                    val head = tokens.subList(n + 1, minOf(k + 1, tokens.size))
                    val braced = tokens.getOrNull(bodyStart)?.let { it.kind == Kind.PUNCT && it.text == "{" } == true
                    if (braced) {
                        var d = 0
                        var end = bodyStart
                        while (end < tokens.size) {
                            val tk = tokens[end]
                            if (tk.kind == Kind.PUNCT && tk.text == "{") d++
                            if (tk.kind == Kind.PUNCT && tk.text == "}") {
                                d--
                                if (d == 0) break
                            }
                            end++
                        }
                        result += Segment("function", nameTok.text, head, tokens.subList(minOf(bodyStart + 1, tokens.size), minOf(end, tokens.size)), true, line)
                        i = minOf(end + 1, tokens.size)
                    } else {
                        val end = statementEnd(bodyStart)
                        result += Segment("function", nameTok.text, head, tokens.subList(bodyStart, end), false, line)
                        i = end
                    }
                    continue
                }
            }
            if (t.kind == Kind.IDENT && (t.text == "var" || t.text == "final")) {
                val end = statementEnd(j)
                val n = skipSpace(j + 1)
                val name = tokens.getOrNull(n)?.takeIf { it.kind == Kind.IDENT }?.text ?: ""
                val eq = (n until end).firstOrNull { tokens[it].kind == Kind.PUNCT && tokens[it].text == "=" }
                val initializer = if (eq != null) tokens.subList(eq + 1, end).dropLastWhile { it.kind == Kind.SPACE || (it.kind == Kind.PUNCT && it.text == ";") } else emptyList()
                result += Segment("var", name, emptyList(), initializer, false, line)
                i = end
                continue
            }
            val end = statementEnd(i)
            result += Segment("statement", "", emptyList(), tokens.subList(i, end), false, line)
            i = end
        }
        return result
    }

    private fun rewrite(tokens: List<Tok>, functions: Set<String>, fields: Set<String>, extraLocals: Set<String> = emptySet()): String {
        val sb = StringBuilder()
        var i = 0
        fun prev(index: Int): Tok? {
            var j = index - 1
            while (j >= 0 && tokens[j].kind == Kind.SPACE) j--
            return tokens.getOrNull(j)
        }
        fun next(index: Int): Int {
            var j = index + 1
            while (j < tokens.size && tokens[j].kind == Kind.SPACE) j++
            return j
        }
        fun isPunct(index: Int, text: String) = tokens.getOrNull(index)?.let { it.kind == Kind.PUNCT && it.text == text } == true
        val locals = HashSet<String>(extraLocals)
        locals += declaredLocals(tokens)
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.kind == Kind.STRING && t.text.startsWith("'") && t.text.contains('$')) {
                sb.append(interpolate(t.text, functions, fields, locals))
                i++
                continue
            }
            if (t.kind != Kind.IDENT) {
                sb.append(t.text)
                i++
                continue
            }
            val p = prev(i)
            val afterDot = p?.kind == Kind.PUNCT && p.text == "."
            val n1 = next(i)
            val callNext = isPunct(n1, "(")
            if (afterDot) {
                val renamed = methodRenames[t.text]
                when {
                    renamed != null && callNext -> sb.append(renamed)
                    t.text == "frameOffset" -> {
                        issue(t.line, "frameOffset is Codename-only; it became offset, which V-Slice applies in screen pixels (not scaled).")
                        sb.append("offset")
                    }
                    else -> sb.append(t.text)
                }
                i++
                continue
            }
            val objectKey = isPunct(n1, ":") && p?.let { it.kind == Kind.PUNCT && (it.text == "{" || it.text == ",") } == true
            if (objectKey) {
                sb.append(t.text)
                i++
                continue
            }
            val hasMember = isPunct(n1, ".")
            val n2 = next(n1)
            val member = if (hasMember) tokens.getOrNull(n2)?.takeIf { it.kind == Kind.IDENT }?.text else null
            when {
                t.text == "PlayState" && member == "instance" -> {
                    val n3 = next(n2)
                    val inner = if (isPunct(n3, ".")) tokens.getOrNull(next(n3))?.takeIf { it.kind == Kind.IDENT }?.text else null
                    if (inner == null) {
                        sb.append(GAME)
                        i = n2 + 1
                    } else {
                        appendGameMember(sb, inner, t.line)
                        i = next(n3) + 1
                    }
                }
                t.text == "Conductor" && member != null && member in conductorMembers -> {
                    sb.append("Conductor.instance.").append(conductorMembers.getValue(member))
                    i = n2 + 1
                }
                t.text == "Options" && member != null -> {
                    val mapped = options[member]
                    if (mapped == null) issue(t.line, "Options.$member has no V-Slice equivalent and became null.")
                    sb.append(mapped ?: "null")
                    i = n2 + 1
                }
                (t.text == "CustomShader" || t.text == "FunkinShader") && callNext && p?.let { it.kind == Kind.IDENT && it.text == "new" } == true -> {
                    val close = skipCall(tokens, n1)
                    val inner = rewrite(tokens.subList(n1 + 1, close), functions, fields, locals)
                    val firstArg = inner.substringBefore(',').trim()
                    issue(t.line, "${t.text} became FlxRuntimeShader (loads shaders/<name>.frag); set uniforms with shader.setFloat/setInt/setBool instead of assigning them.")
                    sb.append("FlxRuntimeShader(Assets.getText(Paths.frag(").append(firstArg).append(")))")
                    i = close + 1
                }
                t.text == "importScript" && callNext -> {
                    issue(t.line, "importScript() has no V-Slice equivalent; merge that script into this one.")
                    sb.append("null")
                    i = skipCall(tokens, n1) + 1
                }
                t.text == "disableScript" && callNext && t.text !in functions -> {
                    sb.append("disableScript")
                    i++
                }
                t.text in locals -> {
                    sb.append(t.text)
                    i++
                }
                t.text in functions -> {
                    sb.append("h_").append(t.text)
                    i++
                }
                t.text in fields -> {
                    sb.append("h_").append(t.text)
                    i++
                }
                t.text == "strumLines" -> {
                    sb.append("[$GAME.opponentStrumline, $GAME.playerStrumline]")
                    if (member == "members") i = n2 + 1 else i++
                }
                t.text in context.stageSprites && !callNext -> {
                    sb.append("$GAME.currentStage.getNamedProp(${LuaToHScript.hxString(t.text)})")
                    i++
                }
                gameMembers.containsKey(t.text) -> {
                    sb.append(GAME).append('.').append(gameMembers.getValue(t.text))
                    i++
                }
                t.text in bareGlobals -> {
                    sb.append(bareGlobals.getValue(t.text))
                    i++
                }
                t.text in unsupportedMembers -> {
                    issue(t.line, "PlayState.${t.text} has no direct V-Slice equivalent.")
                    sb.append(GAME).append('.').append(t.text)
                    i++
                }
                t.text in codenameClasses && !callNext -> {
                    issue(t.line, "Codename class '${t.text}' does not exist in V-Slice.")
                    sb.append(t.text)
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

    private fun declaredLocals(tokens: List<Tok>): Set<String> {
        val result = HashSet<String>()
        fun next(index: Int): Int {
            var j = index + 1
            while (j < tokens.size && tokens[j].kind == Kind.SPACE) j++
            return j
        }
        tokens.forEachIndexed { index, tok ->
            if (tok.kind != Kind.IDENT) return@forEachIndexed
            when (tok.text) {
                "var", "final" -> tokens.getOrNull(next(index))?.takeIf { it.kind == Kind.IDENT }?.let { result += it.text }
                "for", "catch" -> {
                    val open = next(index)
                    if (tokens.getOrNull(open)?.text == "(") tokens.getOrNull(next(open))?.takeIf { it.kind == Kind.IDENT }?.let { result += it.text }
                }
                "function" -> {
                    var open = next(index)
                    if (tokens.getOrNull(open)?.kind == Kind.IDENT) open = next(open)
                    if (tokens.getOrNull(open)?.text == "(") result += paramNames(tokens, open)
                }
            }
        }
        return result
    }

    private fun paramNames(tokens: List<Tok>, open: Int): Set<String> {
        val result = HashSet<String>()
        var depth = 0
        var expectName = false
        var j = open
        while (j < tokens.size) {
            val t = tokens[j]
            if (t.kind == Kind.PUNCT) {
                when (t.text) {
                    "(" -> {
                        depth++
                        if (depth == 1) expectName = true
                    }
                    ")" -> {
                        depth--
                        if (depth == 0) return result
                    }
                    "," -> if (depth == 1) expectName = true
                    "?" -> Unit
                    else -> if (depth == 1) expectName = false
                }
            } else if (t.kind == Kind.IDENT && depth == 1 && expectName) {
                result += t.text
                expectName = false
            }
            j++
        }
        return result
    }

    private fun interpolate(literal: String, functions: Set<String>, fields: Set<String>, locals: Set<String>): String {
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
                    out.append("${'$'}{").append(rewrite(tokenize(expr), functions, fields, locals)).append("}")
                    i = j + 1
                }
                c == '$' && body.getOrNull(i + 1)?.let { it.isLetter() || it == '_' } == true -> {
                    var j = i + 1
                    while (j < body.length && (body[j].isLetterOrDigit() || body[j] == '_')) j++
                    val name = body.substring(i + 1, j)
                    val mapped = rewrite(tokenize(name), functions, fields, locals)
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

    private fun stripGenerics(text: String): String {
        val out = StringBuilder()
        var i = 0
        val pattern = Regex("\\bnew\\s+[A-Za-z_][\\w.]*\\s*<")
        while (i < text.length) {
            val m = pattern.find(text, i) ?: break
            out.append(text, i, m.range.last)
            var depth = 0
            var j = m.range.last
            while (j < text.length) {
                if (text[j] == '<') depth++
                if (text[j] == '>') {
                    depth--
                    if (depth == 0) break
                }
                j++
            }
            i = j + 1
        }
        out.append(text.substring(minOf(i, text.length)))
        return out.toString()
    }

    private fun skipCall(tokens: List<Tok>, open: Int): Int {
        var depth = 0
        var j = open
        while (j < tokens.size) {
            val t = tokens[j]
            if (t.kind == Kind.PUNCT && t.text == "(") depth++
            if (t.kind == Kind.PUNCT && t.text == ")") {
                depth--
                if (depth == 0) return j
            }
            j++
        }
        return tokens.size - 1
    }

    private fun appendGameMember(sb: StringBuilder, member: String, line: Int) {
        if (member in unsupportedMembers) issue(line, "PlayState.$member has no direct V-Slice equivalent.")
        when (member) {
            "strumLines" -> sb.append("[$GAME.opponentStrumline, $GAME.playerStrumline]")
            "inst" -> sb.append("FlxG.sound.music")
            "scrollSpeed" -> sb.append("$GAME.playerStrumline.scrollSpeed")
            "curSong" -> sb.append("$GAME.currentSong.id")
            "misses" -> sb.append("Highscore.tallies.missed")
            "combo" -> sb.append("Highscore.tallies.combo")
            "downscroll" -> sb.append("Preferences.downscroll")
            else -> sb.append(GAME).append('.').append(gameMembers[member] ?: member)
        }
    }
}

private const val CANCELLABLE = """
  function cancellable(event:Dynamic):Dynamic
  {
    var e:Dynamic = {cancelled: false};
    e.cancel = function() {
      e.cancelled = true;
      if (event != null) event.cancel();
    };
    e.preventDefault = e.cancel;
    return e;
  }
"""

private const val NOTE_EVENT = """
  function noteEvent(event:Dynamic, player:Bool, ?miss:Bool, ?passive:Bool):Dynamic
  {
    var note:Dynamic = event.note;
    var data:Dynamic = note == null ? null : note.noteData;
    var stage:Dynamic = PlayState.instance.currentStage;
    var character:Dynamic = stage == null ? null : (player ? stage.getBoyfriend() : stage.getDad());
    var e:Dynamic = cancellable(passive == true ? null : event);
    e.note = note;
    e.player = player;
    e.direction = data == null ? 0 : data.data % 4;
    e.noteType = data == null ? null : noteTypeName(data.kind);
    e.character = character;
    e.characters = [character];
    e.strumLineID = player ? 1 : 0;
    e.score = null;
    e.healthGain = null;
    e.rating = null;
    try
    {
      e.healthGain = event.healthChange;
      if (miss == true) e.score = event.scoreChange;
      else
      {
        e.score = event.score;
        e.rating = event.judgement;
      }
    }
    catch (err:Dynamic) {}
    e.misses = miss == true;
    e.animSuffix = miss == true ? "miss" : "";
    e.animCancelled = false;
    e.preventAnim = function() {
      e.animCancelled = true;
    };
    e.cancelAnim = e.preventAnim;
    e.preventDeletion = function() {};
    e.cancelDeletion = function() {};
    e.preventStrumGlow = function() {};
    return e;
  }
"""

private const val GHOST_EVENT = """
  function ghostEvent(event:Dynamic):Dynamic
  {
    var stage:Dynamic = PlayState.instance.currentStage;
    var e:Dynamic = cancellable(event);
    e.note = null;
    e.direction = event.dir;
    e.noteType = null;
    e.ghostMiss = true;
    e.character = stage == null ? null : stage.getBoyfriend();
    e.characters = [e.character];
    e.animSuffix = "miss";
    e.preventAnim = function() {};
    e.cancelAnim = e.preventAnim;
    return e;
  }
"""

private const val APPLY_NOTE_EVENT = """
  function applyNoteEvent(event:Dynamic, e:Dynamic, miss:Bool):Void
  {
    if (e.cancelled) return;
    try
    {
      if (e.healthGain != null && e.healthGain != event.healthChange) event.healthChange = e.healthGain;
      if (miss)
      {
        if (e.score != null && e.score != event.scoreChange) event.scoreChange = e.score;
      }
      else if (e.score != null && e.score != event.score) event.score = e.score;
    }
    catch (err:Dynamic) {}
    var character:Dynamic = e.character;
    if (character == null || e.animCancelled) return;
    var suffix:String = e.animSuffix == null ? "" : Std.string(e.animSuffix);
    if (suffix == "" || (miss && suffix == "miss")) return;
    var dirs:Array<String> = ["LEFT", "DOWN", "UP", "RIGHT"];
    var anim:String = "sing" + dirs[e.direction % 4] + (miss && !StringTools.startsWith(suffix, "miss") ? "miss" : "") + suffix;
    if (character.hasAnimation(anim)) pendingAnims.push([character, anim]);
  }
"""

private const val FLUSH_ANIMS = """
  function flushAnims():Void
  {
    if (pendingAnims.length == 0) return;
    for (entry in pendingAnims)
    {
      var character:Dynamic = entry[0];
      if (character != null) character.playAnimation(entry[1], true);
    }
    pendingAnims = [];
  }
"""

private const val STAGE_HELPERS = """
  function add(obj:Dynamic):Dynamic
  {
    var game:Dynamic = PlayState.instance;
    if (game == null || obj == null) return obj;
    var stage:Dynamic = game.currentStage;
    if (stage == null)
    {
      game.add(obj);
      return obj;
    }
    var z:Int = 0;
    for (c in [stage.getBoyfriend(), stage.getDad(), stage.getGirlfriend()]) if (c != null && c.zIndex > z) z = c.zIndex;
    obj.zIndex = z + 1;
    stage.add(obj);
    stage.refresh();
    return obj;
  }

  function insert(position:Int, obj:Dynamic):Dynamic
  {
    var game:Dynamic = PlayState.instance;
    if (game == null || obj == null) return obj;
    var stage:Dynamic = game.currentStage;
    if (stage == null)
    {
      game.insert(position, obj);
      return obj;
    }
    obj.zIndex = position;
    stage.add(obj);
    stage.refresh();
    return obj;
  }

  function remove(obj:Dynamic, ?splice:Bool):Dynamic
  {
    var game:Dynamic = PlayState.instance;
    if (game == null || obj == null) return obj;
    if (game.currentStage != null) game.currentStage.remove(obj, true);
    game.remove(obj, true);
    return obj;
  }
"""
