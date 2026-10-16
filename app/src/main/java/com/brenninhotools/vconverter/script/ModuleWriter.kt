package com.brenninhotools.vconverter.script

data class CallbackRef(val arity: Int?, val isMethod: Boolean)

class ModuleSpec(
    val className: String,
    val moduleId: String,
    val siblings: List<String>,
    val noteTypes: String,
    val target: ScriptTarget,
    val prefix: String,
    val fields: List<String>,
    val bootLines: List<String>,
    val callbacks: Map<String, CallbackRef>,
    val dispatch: Map<String, Int?>,
    val setters: List<String>,
    val noteObjects: Boolean,
    val imports: List<String>,
    val tailLines: List<String>
)

object ModuleWriter {
    private val baseImports = listOf("flixel.FlxG", "funkin.modding.module.Module", "funkin.modding.module.ModuleHandler", "funkin.play.PlayState")

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

        (baseImports + spec.imports).distinct().forEach { out.line("import $it;") }
        out.line("")
        out.open("class ${spec.className} extends Module")
        out.line("var LUA_STOP:String = ${LuaToHScript.hxString(PsychApi.constants.getValue("Function_Stop"))};")
        PsychApi.constants.forEach { (name, value) -> out.line("var $name:String = ${LuaToHScript.hxString(value)};") }
        out.line("var luaGame:Dynamic = null;")
        out.line("var luaClosed:Bool = false;")
        spec.fields.forEach { out.line("var $it:Dynamic = null;") }
        out.line("")
        out.open("public function new()")
        out.line("super(${LuaToHScript.hxString(spec.moduleId)}, 1100);")
        out.line(INIT_MARK)
        out.close()
        out.line("")
        out.open("function luaInScope():Bool")
        out.line("var game:PlayState = PlayState.instance;")
        out.line("if (game == null) return false;")
        out.line(scopeCheck(spec.target))
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
        if (has("onCreate")) out.line(invoke("onCreate") + ";")
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
                out.line("case ${LuaToHScript.hxString(name)}: return $p$name != null && Reflect.isFunction($p$name) ? Reflect.callMethod(null, $p$name, args) : null;")
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

        out.line("")
        out.open("override public function onCountdownStart(event:CountdownScriptEvent):Void")
        out.line("super.onCountdownStart(event);")
        out.line("if (!luaBoot()) return;")
        if (has("onStartCountdown")) {
            out.open("if (${invoke("onStartCountdown")} == LUA_STOP)")
            out.line("event.cancel();")
            out.line("return;")
            out.close()
        }
        if (has("onCountdownStarted")) out.line(invoke("onCountdownStarted") + ";")
        out.close()

        if (has("onCountdownTick")) {
            out.line("")
            out.open("override public function onCountdownStep(event:CountdownScriptEvent):Void")
            out.line("super.onCountdownStep(event);")
            out.line("if (!luaActive()) return;")
            out.line("var step:String = Std.string(event.step);")
            out.line("var tick:Int = step == \"THREE\" ? 0 : (step == \"TWO\" ? 1 : (step == \"ONE\" ? 2 : (step == \"GO\" ? 3 : 4)));")
            out.line(invoke("onCountdownTick", "tick") + ";")
            out.close()
        }
        if (has("onUpdate") || has("onUpdatePost")) {
            out.line("")
            out.open("override public function onUpdate(event:UpdateScriptEvent):Void")
            out.line("super.onUpdate(event);")
            out.line("if (!luaActive()) return;")
            if (has("onUpdate")) out.line(invoke("onUpdate", "event.elapsed") + ";")
            if (has("onUpdatePost")) out.line(invoke("onUpdatePost", "event.elapsed") + ";")
            out.close()
        }
        if (has("onBeatHit") || has("onSectionHit")) {
            out.line("")
            out.open("override public function onBeatHit(event:SongTimeScriptEvent):Void")
            out.line("super.onBeatHit(event);")
            out.line("if (!luaActive()) return;")
            if (has("onBeatHit")) out.line(invoke("onBeatHit") + ";")
            if (has("onSectionHit")) out.line("if (event.beat % 4 == 0) " + invoke("onSectionHit") + ";")
            out.close()
        }
        simple(out, spec, "onStepHit", "onStepHit", "SongTimeScriptEvent") { invoke("onStepHit") }
        simple(out, spec, "onSongStart", "onSongStart", "ScriptEvent") { invoke("onSongStart") }
        if (has("onEndSong")) {
            out.line("")
            out.open("override public function onSongEnd(event:ScriptEvent):Void")
            out.line("super.onSongEnd(event);")
            out.line("if (!luaActive()) return;")
            out.line("if (${invoke("onEndSong")} == LUA_STOP) event.cancel();")
            out.close()
        }
        val noteArgs: (String) -> Array<String> = { dir ->
            if (spec.noteObjects) arrayOf("event.note") else arrayOf("0", dir, "kind", "false")
        }
        val hits = listOf("goodNoteHitPre", "goodNoteHit", "opponentNoteHitPre", "opponentNoteHit")
        if (hits.any(::has)) {
            out.line("")
            out.open("override public function onNoteHit(event:HitNoteScriptEvent):Void")
            out.line("super.onNoteHit(event);")
            out.line("if (!luaActive()) return;")
            out.line("var data:Dynamic = event.note.noteData;")
            out.line("var kind:Dynamic = noteTypeName(data.kind);")
            out.line("var dir:Int = data.data % 4;")
            if (has("goodNoteHitPre") || has("goodNoteHit")) {
                out.open("if (data.data < 4)")
                if (has("goodNoteHitPre")) out.line(invoke("goodNoteHitPre", *noteArgs("dir")) + ";")
                if (has("goodNoteHit")) out.line(invoke("goodNoteHit", *noteArgs("dir")) + ";")
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
        if (has("noteMiss")) {
            out.line("")
            out.open("override public function onNoteMiss(event:NoteScriptEvent):Void")
            out.line("super.onNoteMiss(event);")
            out.line("if (!luaActive()) return;")
            out.line("var data:Dynamic = event.note.noteData;")
            out.line("var kind:Dynamic = noteTypeName(data.kind);")
            out.line(invoke("noteMiss", *noteArgs("data.data % 4")) + ";")
            out.close()
        }
        if (has("noteMissPress")) {
            out.line("")
            out.open("override public function onNoteGhostMiss(event:GhostMissNoteScriptEvent):Void")
            out.line("super.onNoteGhostMiss(event);")
            out.line("if (!luaActive()) return;")
            out.line(invoke("noteMissPress", "event.dir") + ";")
            out.close()
        }
        if (has("onSpawnNote")) {
            out.line("")
            out.open("override public function onNoteIncoming(event:NoteScriptEvent):Void")
            out.line("super.onNoteIncoming(event);")
            out.line("if (!luaActive()) return;")
            out.line("var data:Dynamic = event.note.noteData;")
            out.line("var kind:Dynamic = noteTypeName(data.kind);")
            out.line(invoke("onSpawnNote", *noteArgs("data.data % 4")) + ";")
            out.close()
        }
        out.line("")
        out.open("override public function onSongEvent(event:SongEventScriptEvent):Void")
        out.line("super.onSongEvent(event);")
        out.line("if (!luaActive()) return;")
        out.line("var data:Dynamic = event.eventData;")
        out.line(FOCUS_MARK)
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
        if (has("onPause")) {
            out.line("")
            out.open("override public function onPause(event:PauseScriptEvent):Void")
            out.line("super.onPause(event);")
            out.line("if (!luaActive()) return;")
            out.line("if (${invoke("onPause")} == LUA_STOP) event.cancel();")
            out.close()
        }
        simple(out, spec, "onResume", "onResume", "ScriptEvent") { invoke("onResume") }
        if (has("onGameOver")) {
            out.line("")
            out.open("override public function onGameOver(event:ScriptEvent):Void")
            out.line("super.onGameOver(event);")
            out.line("if (!luaActive()) return;")
            out.line("if (${invoke("onGameOver")} == LUA_STOP) event.cancel();")
            out.close()
        }
        out.line("")
        out.open("override public function onStateChangeBegin(event:StateChangeScriptEvent):Void")
        out.line("super.onStateChangeBegin(event);")
        out.line(CLEANUP_MARK)
        out.open("if (luaGame != null)")
        if (has("onDestroy")) out.line("if (!luaClosed) " + invoke("onDestroy") + ";")
        out.line("luaGame = null;")
        out.close()
        out.close()

        val draft = StringBuilder(out.toString())
        spec.tailLines.forEach { draft.append(if (it.isEmpty()) "\n" else "  $it\n") }
        val embedded = PsychNativeLibrary.embed(draft.toString(), spec.noteTypes, spec.siblings)
        val included = embedded.included
        var body = draft.toString()
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
        return text.toString().replace(Regex("\n{3,}"), "\n\n")
    }

    private const val INIT_MARK = "@@LUA_INIT@@"
    private const val FOCUS_MARK = "@@LUA_FOCUS@@"
    private const val CLEANUP_MARK = "@@LUA_CLEANUP@@"

    private fun marker(text: String, mark: String, replacement: String?): String =
        text.lines().mapNotNull { line ->
            if (line.trim() != mark) line else replacement?.let { line.substringBefore(mark) + it }
        }.joinToString("\n")

    private fun simple(out: Out, spec: ModuleSpec, callback: String, method: String, eventType: String, call: () -> String) {
        if (!spec.callbacks.containsKey(callback)) return
        out.line("")
        out.open("override public function $method(event:$eventType):Void")
        out.line("super.$method(event);")
        out.line("if (!luaActive()) return;")
        out.line(call() + ";")
        out.close()
    }

    fun scopeCheck(target: ScriptTarget): String = when (target.scope) {
        ScriptScope.GLOBAL -> "return true;"
        ScriptScope.SONG -> "return game.currentSong != null && game.currentSong.id == ${LuaToHScript.hxString(target.key)};"
        ScriptScope.STAGE -> "return game.currentStageId == ${LuaToHScript.hxString(target.key)};"
        ScriptScope.EVENT -> "return ensureGame() && songUsesEvent(${LuaToHScript.hxString(target.key)});"
        ScriptScope.NOTE_TYPE -> "return ensureGame() && songUsesNoteType(${LuaToHScript.hxString(target.key)});"
    }
}
