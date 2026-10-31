package com.brenninhotools.vconverter.script

data class CallbackRef(val arity: Int?, val isMethod: Boolean)

class ModuleSpec(
    val className: String,
    val moduleId: String,
    val siblings: List<String>,
    val noteTypes: String,
    val eventNames: Map<String, String>,
    val ownEvent: String?,
    val target: ScriptTarget,
    val prefix: String,
    val fields: List<String>,
    val bootLines: List<String>,
    val callbacks: Map<String, CallbackRef>,
    val dispatch: Map<String, Int?>,
    val setters: List<String>,
    val noteObjects: Boolean,
    val imports: List<String>,
    val tailLines: List<String>,
    val host: ScriptHost = ScriptHost(ScriptBase.MODULE, moduleId)
)

object ModuleWriter {
    private val commonImports = listOf("flixel.FlxG", "funkin.Conductor", "funkin.play.PlayState")

    private class Out {
        private val sb = StringBuilder()
        private var indent = 0

        fun line(text: String) {
            if (text.isEmpty()) sb.append('\n') else sb.append("  ".repeat(indent)).append(text).append('\n')
        }

        fun open(text: String) {
            line(text)
            line("{")
            indent++
        }

        fun close(suffix: String = "") {
            indent--
            line("}$suffix")
        }

        override fun toString() = sb.toString()
    }

    fun write(spec: ModuleSpec): String {
        val out = Out()
        val p = spec.prefix
        fun has(name: String) = spec.callbacks.containsKey(name)
        fun invoke(name: String, vararg values: String): String {
            val ref = spec.callbacks.getValue(name)
            val args = values.take(ref.arity ?: values.size).joinToString(", ")
            val call = "$p$name($args)"
            return if (ref.isMethod) call else "($p$name != null ? $call : null)"
        }

        val host = spec.host
        val base = host.base
        val noteKind = base == ScriptBase.NOTE_KIND
        val guard = if (noteKind) "if (!luaBoot()) return;" else "if (!luaActive()) return;"
        val baseImports = commonImports + ScriptBases.imports.getValue(base)
        (baseImports + spec.imports).distinct().forEach { out.line("import $it;") }
        out.line("")
        out.open("class ${spec.className} extends ${base.className}")
        out.line("var luaGame:Dynamic = null;")
        out.line("var luaClosed:Bool = false;")
        if (has("onSectionHit")) out.line("var luaLastSection:Int = 0;")
        spec.fields.forEach { out.line("var $it:Dynamic = null;") }
        out.line("")
        out.open("public function new()")
        out.line(superCall(host))
        out.line(INIT_MARK)
        out.close()
        out.line("")
        out.open("function luaInScope():Bool")
        out.line("var game:PlayState = PlayState.instance;")
        out.line("if (game == null) return false;")
        out.line(if (base == ScriptBase.SONG || base == ScriptBase.STAGE) "return true;" else scopeCheck(spec.target))
        out.close()
        out.line("")
        out.open("function luaActive():Bool")
        out.line("return !luaClosed && luaGame != null && luaGame == PlayState.instance;")
        out.close()
        out.line("")
        out.open("function luaBoot():Bool")
        out.line("var game:PlayState = PlayState.instance;")
        out.line("if (game == null) return false;")
        out.line("if (luaGame == game) return !luaClosed;")
        out.line("if (!luaInScope()) return false;")
        out.line("luaGame = game;")
        out.line("luaClosed = false;")
        spec.fields.forEach { out.line("$it = null;") }
        spec.bootLines.forEach { out.line(it) }
        if (has("onSectionHit")) out.line("luaLastSection = 0;")
        if (has("onCreate")) out.line(invoke("onCreate") + ";")
        if (has("onEventPushed")) {
            out.open("if (game.songEvents != null && ensureGame())")
            out.open("for (data in game.songEvents)")
            out.line("var info:Dynamic = psychEventInfo(data);")
            out.line("if (info != null) " + invoke("onEventPushed", "info.name", "info.value1", "info.value2", "data.time") + ";")
            out.close()
            out.close()
        }
        if (has("onCreatePost")) out.line(invoke("onCreatePost") + ";")
        out.line("return !luaClosed;")
        out.close()
        out.line("")
        out.open("public function psychClose():Void")
        out.line("luaClosed = true;")
        out.close()
        out.line("")
        out.open("public function psychCallback(name:String, args:Array<Dynamic>):Dynamic")
        out.line("if (!luaActive()) return null;")
        out.line("if (args == null) args = [];")
        out.open("switch (name)")
        spec.dispatch.forEach { (name, arity) ->
            if (arity != null) {
                val callArgs = (0 until arity).joinToString(", ") { "args[$it]" }
                out.line("case ${LuaToHScript.hxString(name)}: return $p$name($callArgs);")
            } else {
                out.line("case ${LuaToHScript.hxString(name)}: return $p$name != null && Reflect.isFunction($p$name) ? luaInvoke($p$name, args) : null;")
            }
        }
        if (spec.setters.isNotEmpty()) {
            out.open("case \"__set\":")
            out.open("switch (Std.string(args[0]))")
            spec.setters.forEach { out.line("case ${LuaToHScript.hxString(it)}: $p$it = args[1];") }
            out.close()
            out.line("return null;")
            out.close()
        }
        out.line("default: return null;")
        out.close()
        out.close()

        if (!noteKind) {
            out.line("")
            out.open("override public function onCountdownStart(event:CountdownScriptEvent):Void")
            out.line("super.onCountdownStart(event);")
            out.line("if (!luaBoot()) return;")
            if (has("onStartCountdown")) {
                out.open("if (${invoke("onStartCountdown")} == CANCEL)")
                out.line("event.cancel();")
                out.line("return;")
                out.close()
            }
            if (has("onCountdownStarted")) out.line(invoke("onCountdownStarted") + ";")
            out.close()
        }

        if (has("onCountdownTick")) {
            out.line("")
            out.open("override public function onCountdownStep(event:CountdownScriptEvent):Void")
            out.line("super.onCountdownStep(event);")
            out.line(guard)
            out.line("var step:String = Std.string(event.step);")
            out.line("var tick:Int = step == \"THREE\" ? 0 : (step == \"TWO\" ? 1 : (step == \"ONE\" ? 2 : (step == \"GO\" ? 3 : 4)));")
            out.line(invoke("onCountdownTick", "tick") + ";")
            out.close()
        }
        val keys = listOf("onKeyPressPre", "onKeyPress", "onKeyReleasePre", "onKeyRelease").filter(::has)
        if (noteKind || has("onUpdate") || has("onUpdatePost") || keys.isNotEmpty()) {
            out.line("")
            out.open("override public function onUpdate(event:UpdateScriptEvent):Void")
            out.line("super.onUpdate(event);")
            out.line(guard)
            if (keys.isNotEmpty()) {
                out.line("var controls:Dynamic = PlayState.instance.controls;")
                out.open("if (controls != null && !PlayState.instance.isBotPlayMode)")
                out.line("var pressed:Array<Bool> = [controls.NOTE_LEFT_P, controls.NOTE_DOWN_P, controls.NOTE_UP_P, controls.NOTE_RIGHT_P];")
                out.line("var released:Array<Bool> = [controls.NOTE_LEFT_R, controls.NOTE_DOWN_R, controls.NOTE_UP_R, controls.NOTE_RIGHT_R];")
                out.open("for (key in 0...4)")
                if (has("onKeyPressPre") || has("onKeyPress")) {
                    out.open("if (pressed[key])")
                    if (has("onKeyPressPre")) out.line(invoke("onKeyPressPre", "key") + ";")
                    if (has("onKeyPress")) out.line(invoke("onKeyPress", "key") + ";")
                    out.close()
                }
                if (has("onKeyReleasePre") || has("onKeyRelease")) {
                    out.open("if (released[key])")
                    if (has("onKeyReleasePre")) out.line(invoke("onKeyReleasePre", "key") + ";")
                    if (has("onKeyRelease")) out.line(invoke("onKeyRelease", "key") + ";")
                    out.close()
                }
                out.close()
                out.close()
            }
            if (has("onUpdate")) out.line(invoke("onUpdate", "event.elapsed") + ";")
            if (has("onUpdatePost")) out.line(invoke("onUpdatePost", "event.elapsed") + ";")
            out.close()
        }
        if (has("onBeatHit") || has("onSectionHit")) {
            out.line("")
            out.open("override public function onBeatHit(event:SongTimeScriptEvent):Void")
            out.line("super.onBeatHit(event);")
            out.line(guard)
            if (has("onBeatHit")) out.line(invoke("onBeatHit") + ";")
            if (has("onSectionHit")) {
                out.line("var section:Int = Conductor.instance.currentMeasure;")
                out.open("if (section > luaLastSection)")
                out.line("luaLastSection = section;")
                out.line(invoke("onSectionHit") + ";")
                out.close()
            }
            out.close()
        }
        simple(out, spec, guard, "onStepHit", "onStepHit", "SongTimeScriptEvent") { invoke("onStepHit") }
        simple(out, spec, guard, "onSongStart", "onSongStart", "ScriptEvent") { invoke("onSongStart") }
        if (has("onEndSong")) {
            out.line("")
            out.open("override public function onSongEnd(event:ScriptEvent):Void")
            out.line("super.onSongEnd(event);")
            out.line(guard)
            out.line("if (${invoke("onEndSong")} == CANCEL) event.cancel();")
            out.close()
        }
        val noteArgs: (String) -> Array<String> = { dir ->
            if (spec.noteObjects) arrayOf("event.note") else arrayOf("0", dir, "kind", "false")
        }
        val hits = listOf("goodNoteHitPre", "goodNoteHit", "opponentNoteHitPre", "opponentNoteHit", "onUpdateScore", "onRecalculateRating")
        if (hits.any(::has)) {
            out.line("")
            out.open("override public function onNoteHit(event:HitNoteScriptEvent):Void")
            out.line("super.onNoteHit(event);")
            out.line(guard)
            out.line("var data:Dynamic = event.note.noteData;")
            out.line("var kind:Dynamic = noteTypeName(data.kind);")
            out.line("var dir:Int = data.data % 4;")
            if (has("goodNoteHitPre") || has("goodNoteHit") || has("onUpdateScore") || has("onRecalculateRating")) {
                out.open("if (data.data < 4)")
                if (has("goodNoteHitPre")) out.line(invoke("goodNoteHitPre", *noteArgs("dir")) + ";")
                if (has("goodNoteHit")) out.line(invoke("goodNoteHit", *noteArgs("dir")) + ";")
                if (has("onRecalculateRating")) out.line(invoke("onRecalculateRating") + ";")
                if (has("onUpdateScore")) out.line(invoke("onUpdateScore", "false") + ";")
                out.close()
            }
            if (has("opponentNoteHitPre") || has("opponentNoteHit")) {
                out.open("if (data.data >= 4)")
                if (has("opponentNoteHitPre")) out.line(invoke("opponentNoteHitPre", *noteArgs("dir")) + ";")
                if (has("opponentNoteHit")) out.line(invoke("opponentNoteHit", *noteArgs("dir")) + ";")
                out.close()
            }
            out.close()
        }
        if (has("noteMiss") || has("onUpdateScore") || has("onRecalculateRating")) {
            out.line("")
            out.open("override public function onNoteMiss(event:NoteScriptEvent):Void")
            out.line("super.onNoteMiss(event);")
            out.line(guard)
            if (has("noteMiss")) {
                out.line("var data:Dynamic = event.note.noteData;")
                out.line("var kind:Dynamic = noteTypeName(data.kind);")
                out.line(invoke("noteMiss", *noteArgs("data.data % 4")) + ";")
            }
            if (has("onRecalculateRating")) out.line(invoke("onRecalculateRating") + ";")
            if (has("onUpdateScore")) out.line(invoke("onUpdateScore", "true") + ";")
            out.close()
        }
        if (has("noteMissPress") || has("onGhostTap")) {
            out.line("")
            out.open("override public function onNoteGhostMiss(event:GhostMissNoteScriptEvent):Void")
            out.line("super.onNoteGhostMiss(event);")
            out.line(guard)
            when {
                has("noteMissPress") && has("onGhostTap") -> out.line("if (event.hasMiss) ${invoke("noteMissPress", "event.dir")}; else ${invoke("onGhostTap", "event.dir")};")
                has("noteMissPress") -> out.line("if (event.hasMiss) ${invoke("noteMissPress", "event.dir")};")
                else -> out.line("if (!event.hasMiss) ${invoke("onGhostTap", "event.dir")};")
            }
            out.close()
        }
        if (has("onSpawnNote")) {
            out.line("")
            out.open("override public function onNoteIncoming(event:NoteScriptEvent):Void")
            out.line("super.onNoteIncoming(event);")
            out.line(guard)
            out.line("var data:Dynamic = event.note.noteData;")
            out.line("var kind:Dynamic = noteTypeName(data.kind);")
            out.line(invoke("onSpawnNote", *noteArgs("data.data % 4")) + ";")
            out.close()
        }
        if (base == ScriptBase.SONG_EVENT) {
            out.line("")
            out.open("override public function handleEvent(data:Dynamic):Void")
            out.line("if (data == null || !luaBoot()) return;")
            if (has("onEvent")) {
                out.line("ensureGame();")
                out.line("var info:Dynamic = psychEventInfo(data);")
                out.line("if (info != null) " + invoke("onEvent", "info.name", "info.value1", "info.value2", "data.time") + ";")
            }
            out.close()
            out.line("")
            out.open("override public function getTitle():String")
            out.line("return ${LuaToHScript.hxString(host.title)};")
            out.close()
            out.line("")
            out.open("override public function getEventSchema():Dynamic")
            out.line("return new SongEventSchema([")
            out.line("  {name: \"value1\", title: \"Value 1\", type: \"string\", defaultValue: \"\"},")
            out.line("  {name: \"value2\", title: \"Value 2\", type: \"string\", defaultValue: \"\"}")
            out.line("]);")
            out.close()
        }
        if (!noteKind) {
        out.line("")
        out.open("override public function onSongEvent(event:SongEventScriptEvent):Void")
        out.line("super.onSongEvent(event);")
        out.line(guard)
        out.line("var data:Dynamic = event.eventData;")
        out.line(FOCUS_MARK)
        spec.ownEvent?.let { out.line("if (data.eventKind == ${LuaToHScript.hxString(it)}) return;") }
        run {
            if (has("onEvent")) {
                out.line("ensureGame();")
                out.line("var info:Dynamic = psychEventInfo(data);")
                out.line("if (info != null) " + invoke("onEvent", "info.name", "info.value1", "info.value2", "data.time") + ";")
            }
            if (has("onMoveCamera")) {
                out.open("if (data.eventKind == \"FocusCamera\")")
                out.line("var target:Dynamic = data.value != null ? (Reflect.isObject(data.value) ? Reflect.field(data.value, \"char\") : data.value) : null;")
                out.line(invoke("onMoveCamera", "target == 0 ? \"boyfriend\" : (target == 2 ? \"gf\" : \"dad\")") + ";")
                out.close()
            }
            out.close()
        }
        }
        if (has("onPause")) {
            out.line("")
            out.open("override public function onPause(event:PauseScriptEvent):Void")
            out.line("super.onPause(event);")
            out.line(guard)
            out.line("if (${invoke("onPause")} == CANCEL) event.cancel();")
            out.close()
        }
        simple(out, spec, guard, "onResume", "onResume", "ScriptEvent") { invoke("onResume") }
        if (has("onGameOver") || has("onGameOverStart")) {
            out.line("")
            out.open("override public function onGameOver(event:ScriptEvent):Void")
            out.line("super.onGameOver(event);")
            out.line(guard)
            if (has("onGameOver")) {
                out.open("if (${invoke("onGameOver")} == CANCEL)")
                out.line("event.cancel();")
                out.line("return;")
                out.close()
            }
            if (has("onGameOverStart")) out.line(invoke("onGameOverStart") + ";")
            out.close()
        }
        if (has("onGameOverConfirm")) {
            out.line("")
            out.open("override public function onSongRetry(event:ScriptEvent):Void")
            out.line("super.onSongRetry(event);")
            out.line(guard)
            out.line(invoke("onGameOverConfirm", "true") + ";")
            out.close()
        }
        out.line("")
        if (base == ScriptBase.MODULE) {
            out.open("override public function onStateChangeBegin(event:StateChangeScriptEvent):Void")
            out.line("super.onStateChangeBegin(event);")
        } else {
            out.open("override public function onDestroy(event:ScriptEvent):Void")
        }
        out.open("if (luaGame != null)")
        if (has("onDestroy")) out.line("if (!luaClosed) " + invoke("onDestroy") + ";")
        out.line(CLEANUP_MARK)
        out.line("luaGame = null;")
        out.close()
        if (base != ScriptBase.MODULE) out.line("super.onDestroy(event);")
        out.close()

        val draft = StringBuilder(out.toString())
        spec.tailLines.forEach { draft.append(if (it.isEmpty()) "\n" else "  $it\n") }
        val embedded = PsychNativeLibrary.embed(draft.toString(), spec.noteTypes, spec.siblings, spec.eventNames)
        val included = embedded.included
        var body = embedded.moduleText
        body = marker(body, INIT_MARK, if ("luaInitNoteTypes" in included) "luaInitNoteTypes();" else null)
        body = marker(body, FOCUS_MARK, if ("luaTrackFocus" in included) "luaTrackFocus(data);" else null)
        body = marker(body, CLEANUP_MARK, if ("luaCleanup" in included) "luaCleanup();" else null)
        val imports = embedded.imports.filter { it !in baseImports && it !in spec.imports }
        val importBlock = imports.joinToString("") { "import $it;\n" }
        val classIndex = body.indexOf("\nclass ")
        body = body.substring(0, classIndex).trimEnd('\n') + "\n" + importBlock + "\n" + body.substring(classIndex + 1)
        val text = StringBuilder(body)
        if (embedded.members.isNotBlank()) text.append('\n').append(embedded.members).append('\n')
        text.append("}\n")
        return Naturalizer.module(text.toString(), ScriptBases.members(base)).replace(Regex("\n{3,}"), "\n\n")
    }

    private const val INIT_MARK = "@@LUA_INIT@@"
    private const val FOCUS_MARK = "@@LUA_FOCUS@@"
    private const val CLEANUP_MARK = "@@LUA_CLEANUP@@"

    private fun marker(text: String, mark: String, replacement: String?): String =
        text.lines().mapNotNull { line ->
            if (line.trim() != mark) line else replacement?.let { line.substringBefore(mark) + it }
        }.joinToString("\n")

    private fun simple(out: Out, spec: ModuleSpec, guard: String, callback: String, method: String, eventType: String, call: () -> String) {
        if (!spec.callbacks.containsKey(callback)) return
        out.line("")
        out.open("override public function $method(event:$eventType):Void")
        out.line("super.$method(event);")
        out.line(guard)
        out.line(call() + ";")
        out.close()
    }

    fun superCall(host: ScriptHost): String {
        val id = LuaToHScript.hxString(host.id)
        return when (host.base) {
            ScriptBase.MODULE -> "super($id, 1100);"
            ScriptBase.NOTE_KIND -> "super($id, ${LuaToHScript.hxString(host.title)}${host.noteStyle?.let { ", " + LuaToHScript.hxString(it) } ?: ""});"
            else -> "super($id);"
        }
    }

    fun scopeCheck(target: ScriptTarget): String = when (target.scope) {
        ScriptScope.GLOBAL -> "return true;"
        ScriptScope.SONG -> "return game.currentSong != null && game.currentSong.id == ${LuaToHScript.hxString(target.key)};"
        ScriptScope.STAGE -> "return game.currentStageId == ${LuaToHScript.hxString(target.key)};"
        ScriptScope.EVENT -> "return ensureGame() && songUsesEvent(${LuaToHScript.hxString(target.key)});"
        ScriptScope.NOTE_TYPE -> "return ensureGame() && songUsesNoteType(${LuaToHScript.hxString(target.key)});"
    }
}
