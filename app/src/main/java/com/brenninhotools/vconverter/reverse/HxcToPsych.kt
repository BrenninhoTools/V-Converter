package com.brenninhotools.vconverter.reverse

import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder

enum class HxcKind(val label: String) {
    MODULE("Module"),
    SONG("Song"),
    STAGE("Stage"),
    CHARACTER("Character"),
    EVENT("Song event"),
    NOTE_KIND("Note kind"),
    OTHER("Other")
}

data class PsychScriptFile(val path: String, val content: String)

data class HxcResult(val kind: HxcKind, val className: String, val targetId: String, val files: List<PsychScriptFile>, val notes: List<String>)

object HxcToPsych {
    private class Member(val name: String, val isFunction: Boolean, val params: String, val body: String, val isStatic: Boolean)

    private val characterParents = setOf("BaseCharacter", "SparrowCharacter", "MultiSparrowCharacter", "PackerCharacter", "AnimateAtlasCharacter", "MultiAnimateAtlasCharacter")
    private val droppedImportPrefixes = listOf("funkin.")
    private val importMap = mapOf(
        "funkin.play.PlayState" to "states.PlayState",
        "funkin.Conductor" to "backend.Conductor",
        "funkin.Paths" to "backend.Paths",
        "funkin.graphics.FunkinSprite" to "flixel.FlxSprite"
    )
    private val silentDrops = setOf(
        "funkin.modding.module.Module", "funkin.modding.module.ModuleHandler", "funkin.play.song.Song", "funkin.play.stage.Stage",
        "funkin.play.event.SongEvent", "funkin.data.event.SongEventSchema", "funkin.play.notes.notekind.NoteKind", "funkin.Preferences",
        "funkin.audio.FunkinSound", "funkin.modding.events.ScriptEvent"
    ) + characterParents.map { "funkin.play.character.$it" }

    fun kindOf(parent: String): HxcKind = when (parent.substringAfterLast('.')) {
        "Module" -> HxcKind.MODULE
        "Song" -> HxcKind.SONG
        "Stage" -> HxcKind.STAGE
        in characterParents -> HxcKind.CHARACTER
        "SongEvent" -> HxcKind.EVENT
        "NoteKind" -> HxcKind.NOTE_KIND
        else -> HxcKind.OTHER
    }

    fun convert(fileName: String, source: String): HxcResult {
        val notes = mutableListOf<String>()
        val strings = mutableListOf<String>()
        val clean = protect(source, strings)
        val classMatch = Regex("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+extends\\s+([A-Za-z_][A-Za-z0-9_.]*)[^{]*\\{").find(clean)
            ?: throw IllegalArgumentException("$fileName has no \"class … extends …\" declaration.")
        val className = classMatch.groupValues[1]
        val kind = kindOf(classMatch.groupValues[2])
        val bodyStart = classMatch.range.last + 1
        val bodyEnd = matchBrace(clean, classMatch.range.last)
        val members = members(clean.substring(bodyStart, bodyEnd))
        val imports = Regex("^\\s*import\\s+([A-Za-z0-9_.*]+)\\s*;", RegexOption.MULTILINE).findAll(clean.substring(0, classMatch.range.first)).map { it.groupValues[1] }.toList()

        val ctor = members.firstOrNull { it.name == "new" }
        val superArgs = ctor?.let { Regex("\\bsuper\\s*\\(([^)]*)\\)").find(it.body)?.groupValues?.get(1) } ?: ""
        val firstArg = Regex("\u0001(\\d+)\u0001").find(superArgs)?.groupValues?.get(1)?.toInt()
        val targetId = firstArg?.let { unquote(strings[it]) } ?: ""
        val ctorExtra = ctor?.body?.let { rewrite(Regex("\\bsuper\\s*\\([^)]*\\)\\s*;").replace(it, ""), notes) }?.trim().orEmpty()

        val out = StringBuilder()
        val keptImports = linkedSetOf<String>()
        imports.forEach { imp ->
            when {
                importMap.containsKey(imp) -> keptImports += importMap.getValue(imp)
                imp in silentDrops -> Unit
                droppedImportPrefixes.any { imp.startsWith(it) } -> notes += "Import $imp is V-Slice only and was removed."
                else -> keptImports += imp
            }
        }
        val schemaFields = members.firstOrNull { it.name == "getEventSchema" }?.body?.let { body ->
            Regex("\\bname\\s*:\\s*\u0001(\\d+)\u0001").findAll(body).map { unquote(strings[it.groupValues[1].toInt()]) }.toList()
        } ?: emptyList()
        val schemaTypes = members.firstOrNull { it.name == "getEventSchema" }?.body?.let { body ->
            Regex("\\btype\\s*:\\s*\u0001(\\d+)\u0001").findAll(body).map { unquote(strings[it.groupValues[1].toInt()]) }.toList()
        } ?: emptyList()

        keptImports.forEach { out.append("import ").append(it).append(";\n") }
        if (keptImports.isNotEmpty()) out.append('\n')
        members.filter { !it.isFunction }.forEach { member ->
            out.append(rewrite(member.body, notes).trim()).append('\n')
        }
        if (members.any { !it.isFunction }) out.append('\n')

        val guard = when (kind) {
            HxcKind.NOTE_KIND -> "if (note.noteType != ${quote(targetId)}) return;"
            else -> null
        }
        val callbackNames = setOf(
            "onCreate", "onUpdate", "onBeatHit", "onStepHit", "onCountdownStart", "onCountdownStep", "onSongStart", "onSongEnd",
            "onNoteHit", "onNoteMiss", "onNoteGhostMiss", "onNoteIncoming", "onSongEvent", "onPause", "onResume", "onGameOver", "onDestroy",
            "handleEvent", "getTitle", "getEventSchema", "new", "onScriptEvent", "onCountdownEnd", "onSongLoaded", "onSongRetry", "onNoteHoldDrop",
            "onStateChangeBegin", "onStateChangeEnd", "onSubStateOpenBegin", "onSubStateOpenEnd", "onSubStateCloseBegin", "onSubStateCloseEnd",
            "onFocusLost", "onFocusGained", "onAdd", "onDialogueStart", "onDialogueLine", "onDialogueEnd", "onDialogueSkip", "onDialogueCompleteLine"
        )
        var hasCreate = false
        members.filter { it.isFunction }.forEach { member ->
            val body = stripSuper(member.body)
            when (member.name) {
                "new", "getTitle", "getEventSchema" -> Unit
                "onCreate" -> {
                    hasCreate = true
                    emit(out, "onCreate", "", joinBody(ctorExtra, rewriteEvent(body, notes, "onCreate")))
                }
                "onUpdate" -> emit(out, "onUpdate", "elapsed:Float", rewriteEvent(body, notes, "onUpdate"))
                "onBeatHit" -> emit(out, "onBeatHit", "", rewriteEvent(body, notes, "onBeatHit"))
                "onStepHit" -> emit(out, "onStepHit", "", rewriteEvent(body, notes, "onStepHit"))
                "onCountdownStart" -> emit(out, "onStartCountdown", "", rewriteEvent(body, notes, "cancel"))
                "onCountdownStep" -> emit(out, "onCountdownTick", "tick:Dynamic, swagCounter:Int", rewriteEvent(body, notes, "countdown"))
                "onSongStart" -> emit(out, "onSongStart", "", rewriteEvent(body, notes, "plain"))
                "onSongEnd" -> emit(out, "onEndSong", "", rewriteEvent(body, notes, "cancel"))
                "onPause" -> emit(out, "onPause", "", rewriteEvent(body, notes, "cancel"))
                "onResume" -> emit(out, "onResume", "", rewriteEvent(body, notes, "plain"))
                "onGameOver" -> emit(out, "onGameOver", "", rewriteEvent(body, notes, "cancel"))
                "onDestroy" -> emit(out, "onDestroy", "", rewriteEvent(body, notes, "plain"))
                "onNoteHit" -> {
                    val inner = rewriteEvent(body, notes, "note")
                    emit(out, "goodNoteHit", "note:Note", joinBody(guard ?: "", inner))
                    emit(out, "opponentNoteHit", "note:Note", joinBody(guard ?: "", inner))
                }
                "onNoteMiss" -> emit(out, "noteMiss", "note:Note", joinBody(guard ?: "", rewriteEvent(body, notes, "note")))
                "onNoteIncoming" -> emit(out, "onSpawnNote", "note:Note", joinBody(guard ?: "", rewriteEvent(body, notes, "note")))
                "onNoteGhostMiss" -> emit(out, "noteMissPress", "direction:Int", rewriteEvent(body, notes, "ghost"))
                "onSongEvent" -> emit(out, "onEvent", "eventName:String, value1:String, value2:String, strumTime:Float", rewriteEvent(body, notes, "songEvent"))
                "handleEvent" -> {
                    var inner = rewriteEvent(body, notes, "handleEvent")
                    schemaFields.forEachIndexed { index, field ->
                        val value = if (index == 0) "value1" else if (index == 1) "value2" else null
                        val type = schemaTypes.getOrNull(index) ?: "string"
                        val typed = when {
                            value == null -> "null"
                            type == "float" || type == "integer" -> "Std.parseFloat($value)"
                            type == "bool" -> "($value == \"true\")"
                            else -> value
                        }
                        inner = Regex("Reflect\\.field\\(\\s*eventValue\\s*,\\s*\u0001\\d+\u0001\\s*\\)").replace(inner) { m ->
                            val idx = Regex("\u0001(\\d+)\u0001").find(m.value)!!.groupValues[1].toInt()
                            if (unquote(strings[idx]) == field) typed else m.value
                        }
                        inner = Regex("\\beventValue\\.${Regex.escape(field)}\\b").replace(inner, typed)
                    }
                    if (schemaFields.size > 2) notes += "Psych events only have two values; fields after \"${schemaFields[1]}\" are null."
                    if (Regex("\\beventValue\\b").containsMatchIn(inner)) {
                        inner = Regex("\\beventValue\\b").replace(inner, "value1")
                        notes += "The event value object became value1 (a string in Psych); check how it is read."
                    }
                    emit(out, "onEvent", "eventName:String, value1:String, value2:String, strumTime:Float", joinBody("if (eventName != ${quote(targetId)}) return;", inner))
                }
                in callbackNames -> notes += "V-Slice callback ${member.name} has no Psych equivalent and was dropped."
                else -> {
                    val params = rewrite(member.params, notes)
                    emit(out, member.name, params, rewrite(body, notes))
                }
            }
        }
        if (!hasCreate && ctorExtra.isNotBlank()) emit(out, "onCreate", "", ctorExtra)

        var text = restore(out.toString(), strings)
        text = text.replace(Regex("\n{3,}"), "\n\n").trimEnd() + "\n"
        val files = mutableListOf<PsychScriptFile>()
        val id = targetId.ifBlank { className }
        val path = when (kind) {
            HxcKind.MODULE, HxcKind.OTHER -> "scripts/$className.hx"
            HxcKind.SONG -> "data/${VSliceToPsychChart.formatToSongPath(id)}/$className.hx"
            HxcKind.STAGE -> "stages/$id.hx"
            HxcKind.CHARACTER -> "characters/$id.hx"
            HxcKind.EVENT -> "custom_events/$id.hx"
            HxcKind.NOTE_KIND -> "custom_notetypes/$id.hx"
        }
        files += PsychScriptFile(path, text)
        if (kind == HxcKind.EVENT) {
            val title = members.firstOrNull { it.name == "getTitle" }?.body?.let { b ->
                Regex("\u0001(\\d+)\u0001").find(b)?.groupValues?.get(1)?.toInt()?.let { unquote(strings[it]) }
            } ?: id
            val description = buildString {
                append(title)
                if (schemaFields.isNotEmpty()) append("\n\nValue 1: ").append(schemaFields[0])
                if (schemaFields.size > 1) append("\nValue 2: ").append(schemaFields[1])
            }
            files += PsychScriptFile("custom_events/$id.txt", description + "\n")
        }
        if (kind == HxcKind.MODULE) notes += "Modules run in every state in V-Slice; Psych global scripts (scripts/) only run during songs."
        if (kind == HxcKind.OTHER) notes += "Parent class ${classMatch.groupValues[2]} has no Psych equivalent; the script was placed in scripts/."
        if (Regex("\\bModuleHandler\\b").containsMatchIn(text)) notes += "ModuleHandler is V-Slice only."
        if (Regex("\\bFunkinSprite\\b").containsMatchIn(text)) notes += "FunkinSprite helpers (create, createSparrow) do not exist in Psych; use FlxSprite with Paths.image / Paths.getSparrowAtlas."
        return HxcResult(kind, className, targetId, files, notes.distinct())
    }

    private fun emit(out: StringBuilder, name: String, params: String, body: String) {
        out.append("function ").append(name).append('(').append(params.trim()).append(")\n{\n")
        val lines = body.trimEnd().lines().dropWhile { it.isBlank() }
        val indent = lines.filter { it.isNotBlank() }.minOfOrNull { it.length - it.trimStart().length } ?: 0
        lines.forEach { line -> if (line.isBlank()) out.append('\n') else out.append("  ").append(line.drop(indent).trimEnd()).append('\n') }
        out.append("}\n\n")
    }

    private fun joinBody(first: String, second: String): String =
        listOf(first.trim(), second.trimEnd()).filter { it.isNotBlank() }.joinToString("\n")

    private fun stripSuper(body: String): String = Regex("^\\s*super\\.[A-Za-z_]+\\s*\\([^;]*\\)\\s*;\\s*$", RegexOption.MULTILINE).replace(body, "")

    private fun rewriteEvent(body: String, notes: MutableList<String>, mode: String): String {
        var t = body
        when (mode) {
            "onUpdate" -> t = Regex("\\bevent\\.elapsed\\b").replace(t, "elapsed")
            "onBeatHit" -> t = Regex("\\bevent\\.beat\\b").replace(t, "curBeat")
            "onStepHit" -> t = Regex("\\bevent\\.step\\b").replace(t, "curStep")
            "countdown" -> t = Regex("\\bevent\\.step\\b").replace(t, "swagCounter")
            "ghost" -> t = Regex("\\bevent\\.dir\\b").replace(t, "direction")
            "note" -> {
                t = Regex("\\bevent\\.note\\.noteData\\.getMustHitNote\\(\\s*\\)").replace(t, "note.mustPress")
                t = Regex("\\bevent\\.note\\.noteData\\.getDirection\\(\\s*\\)").replace(t, "note.noteData")
                t = Regex("\\bevent\\.note\\.noteData\\.kind\\b").replace(t, "note.noteType")
                t = Regex("\\bevent\\.note\\.noteData\\.data\\b").replace(t, "(note.noteData + (note.mustPress ? 0 : 4))")
                t = Regex("\\bevent\\.note\\.noteData\\.time\\b").replace(t, "note.strumTime")
                t = Regex("\\bevent\\.note\\.noteData\\.length\\b").replace(t, "note.sustainLength")
                t = Regex("\\bevent\\.note\\b").replace(t, "note")
                if (Regex("\\bevent\\.").containsMatchIn(t)) notes += "Note event fields like event.judgement or event.isComboBreak have no Psych equivalent."
            }
            "songEvent" -> {
                t = Regex("\\bevent\\.eventData\\.eventKind\\b").replace(t, "eventName")
                t = Regex("\\bevent\\.eventData\\.value\\b").replace(t, "value1")
                t = Regex("\\bevent\\.eventData\\.time\\b").replace(t, "strumTime")
            }
            "handleEvent" -> {
                t = Regex("\\bdata\\.value\\b").replace(t, "eventValue")
                t = Regex("\\bdata\\.time\\b").replace(t, "strumTime")
                t = Regex("\\bdata\\.eventKind\\b").replace(t, "eventName")
            }
        }
        t = Regex("\\bevent\\.cancel(?:Event)?\\(\\s*\\)\\s*;").replace(t, if (mode == "cancel") "return Function_Stop;" else "return;")
        if (mode != "cancel" && Regex("\\bevent\\.cancel").containsMatchIn(body)) notes += "event.cancel() in this callback cannot be cancelled in Psych and became return."
        if (Regex("\\bevent\\.").containsMatchIn(t) && mode !in setOf("note")) notes += "Some event.* fields were left as-is; Psych callbacks have no event object."
        return rewrite(t, notes)
    }

    private val replacements: List<Pair<Regex, String>> = listOf(
        "PlayState\\.instance\\." to "game.",
        "\\bPlayState\\.instance\\b" to "game",
        "\\bgame\\.currentStage\\.getBoyfriend\\(\\s*\\)" to "boyfriend",
        "\\bgame\\.currentStage\\.getDad\\(\\s*\\)" to "dad",
        "\\bgame\\.currentStage\\.getGirlfriend\\(\\s*\\)" to "gf",
        "\\bgame\\.currentStage\\.getNamedProp\\(" to "getVar(",
        "\\bgetNamedProp\\(" to "getVar(",
        "\\bgetBoyfriend\\(\\s*\\)" to "boyfriend",
        "\\bgetDad\\(\\s*\\)" to "dad",
        "\\bgetGirlfriend\\(\\s*\\)" to "gf",
        "\\bgame\\.cameraFollowPoint\\b" to "game.camFollow",
        "\\bgame\\.currentCameraZoom\\b" to "game.defaultCamZoom",
        "\\bgame\\.defaultCameraZoom\\b" to "game.defaultCamZoom",
        "\\bgame\\.camCutscene\\b" to "game.camOther",
        "\\bgame\\.playerStrumline\\.strumlineNotes\\b" to "game.playerStrums",
        "\\bgame\\.opponentStrumline\\.strumlineNotes\\b" to "game.opponentStrums",
        "\\bgame\\.playerStrumline\\b" to "game.playerStrums",
        "\\bgame\\.opponentStrumline\\b" to "game.opponentStrums",
        "\\bgame\\.scoreText\\b" to "game.scoreTxt",
        "\\bgame\\.isBotPlayMode\\b" to "game.cpuControlled",
        "\\bgame\\.isInCutscene\\b" to "game.inCutscene",
        "\\bgame\\.currentStageId\\b" to "PlayState.curStage",
        "\\bgame\\.currentSong\\.id\\b" to "Paths.formatToSongPath(PlayState.SONG.song)",
        "\\bgame\\.currentChart\\.scrollSpeed\\b" to "game.songSpeed",
        "\\bgame\\.endSong\\(\\s*true\\s*\\)" to "game.endSong()",
        "\\bConductor\\.instance\\.songPosition\\b" to "Conductor.songPosition",
        "\\bConductor\\.instance\\.bpm\\b" to "Conductor.bpm",
        "\\bConductor\\.instance\\.beatLengthMs\\b" to "Conductor.crochet",
        "\\bConductor\\.instance\\.stepLengthMs\\b" to "Conductor.stepCrochet",
        "\\bConductor\\.instance\\.currentBeatTime\\b" to "curDecBeat",
        "\\bConductor\\.instance\\.currentStepTime\\b" to "curDecStep",
        "\\bConductor\\.instance\\.currentBeat\\b" to "curBeat",
        "\\bConductor\\.instance\\.currentStep\\b" to "curStep",
        "\\bConductor\\.instance\\.currentMeasure\\b" to "curSection",
        "\\bPreferences\\.downscroll\\b" to "ClientPrefs.data.downScroll",
        "\\bPreferences\\.flashingLights\\b" to "ClientPrefs.data.flashing",
        "\\bPreferences\\.ghostTapping\\b" to "ClientPrefs.data.ghostTapping",
        "\\bPreferences\\.zoomCamera\\b" to "ClientPrefs.data.camZooms",
        "\\bPreferences\\.framerate\\b" to "ClientPrefs.data.framerate",
        "\\bFunkinSound\\.playOnce\\(" to "FlxG.sound.play(",
        "\\bFunkinSound\\.load\\(" to "FlxG.sound.load(",
        "\\bthis\\." to "",
        "\\bReflect\\.callMethod\\(\\s*([A-Za-z_][\\w.]*)\\s*,\\s*(\u0001\\d+\u0001)" to "Reflect.callMethod($1, Reflect.getProperty($1, $2)"
    ).map { (pattern, replacement) -> Regex(pattern) to replacement }

    private fun rewrite(text: String, notes: MutableList<String>): String {
        var t = text
        replacements.forEach { (regex, replacement) -> t = regex.replace(t, if (replacement.contains("$1")) replacement else Regex.escapeReplacement(replacement)) }
        t = Regex("(^|[^A-Za-z0-9_.])(public|private|static|inline|override|final)\\s+(?=(var|function)\\b)").replace(t) { it.groupValues[1] }
        if (Regex("\\bConductor\\.instance\\b").containsMatchIn(t)) notes += "Some Conductor.instance fields have no Psych equivalent."
        if (Regex("\\bPreferences\\.").containsMatchIn(t)) notes += "Some Preferences fields have no Psych equivalent."
        if (Regex("\\bgame\\.(currentStage|currentSong|currentChart|currentVariation|currentDifficulty)\\b").containsMatchIn(t)) notes += "Some PlayState fields (currentStage, currentSong…) are V-Slice only."
        return t
    }

    private fun members(body: String): List<Member> {
        val result = mutableListOf<Member>()
        var i = 0
        while (i < body.length) {
            val rest = body.substring(i)
            val m = Regex("^\\s*((?:@:[A-Za-z]+(?:\\([^)]*\\))?\\s*)*)((?:(?:public|private|override|static|inline|dynamic)\\s+)*)(var|final|function)\\s+([A-Za-z_][A-Za-z0-9_]*)").find(rest)
            if (m == null) {
                i++
                continue
            }
            val isStatic = "static" in m.groupValues[2]
            val start = i + m.range.first + m.groupValues[0].indexOf(m.groupValues[3])
            if (m.groupValues[3] == "function") {
                val paren = body.indexOf('(', i + m.range.last)
                val close = matchParen(body, paren)
                val brace = body.indexOf('{', close)
                val semi = body.indexOf(';', close)
                if (brace < 0 || (semi in 0 until brace)) {
                    i = if (semi >= 0) semi + 1 else body.length
                    continue
                }
                val end = matchBrace(body, brace)
                result += Member(m.groupValues[4], true, body.substring(paren + 1, close), body.substring(brace + 1, end), isStatic)
                i = end + 1
            } else {
                var depth = 0
                var j = start
                while (j < body.length) {
                    val c = body[j]
                    if (c == '{' || c == '(' || c == '[') depth++
                    if (c == '}' || c == ')' || c == ']') depth--
                    if (c == ';' && depth <= 0) break
                    j++
                }
                val text = body.substring(start, minOf(j + 1, body.length))
                result += Member(m.groupValues[4], false, "", (if (text.startsWith("final")) "var" + text.removePrefix("final") else text), isStatic)
                i = j + 1
            }
        }
        return result
    }

    private fun matchBrace(text: String, open: Int): Int {
        var depth = 0
        for (k in open until text.length) {
            if (text[k] == '{') depth++
            if (text[k] == '}') {
                depth--
                if (depth == 0) return k
            }
        }
        return text.length
    }

    private fun matchParen(text: String, open: Int): Int {
        var depth = 0
        for (k in open until text.length) {
            if (text[k] == '(') depth++
            if (text[k] == ')') {
                depth--
                if (depth == 0) return k
            }
        }
        return text.length
    }

    private fun protect(source: String, strings: MutableList<String>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    val end = source.indexOf("*/", i + 2)
                    i = if (end < 0) source.length else end + 2
                }
                c == '"' || c == '\'' -> {
                    val start = i
                    i++
                    while (i < source.length && source[i] != c) {
                        if (source[i] == '\\') i++
                        i++
                    }
                    i++
                    strings += source.substring(start, minOf(i, source.length))
                    sb.append('\u0001').append(strings.size - 1).append('\u0001')
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun restore(text: String, strings: List<String>): String =
        Regex("\u0001(\\d+)\u0001").replace(text) { Regex.escapeReplacement(strings[it.groupValues[1].toInt()]) }

    private fun unquote(literal: String): String = literal.drop(1).dropLast(1).replace("\\\"", "\"").replace("\\'", "'")

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun report(results: List<Pair<String, HxcResult>>, failures: List<String>): Report {
        val report = ReportBuilder()
        report.stat("Scripts", results.size)
        results.forEach { (name, result) ->
            report.section(
                "$name · ${result.kind.label}${if (result.targetId.isNotEmpty()) " \"${result.targetId}\"" else ""}",
                listOf("→ " + result.files.joinToString { it.path }) + result.notes.take(20)
            )
        }
        failures.forEach(report::warn)
        report.section(
            "How it runs",
            listOf(
                "V-Slice callbacks became Psych HScript callbacks (onCreate, onUpdate, onBeatHit, goodNoteHit, onEvent…).",
                "Song scripts go to data/<song>/, stage scripts to stages/, character scripts to characters/, events to custom_events/ and note kinds to custom_notetypes/."
            )
        )
        return report.build()
    }
}
