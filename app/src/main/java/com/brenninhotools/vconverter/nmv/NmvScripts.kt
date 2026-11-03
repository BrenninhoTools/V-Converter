package com.brenninhotools.vconverter.nmv

import com.brenninhotools.vconverter.script.ScriptIssue

data class NmvHxResult(val source: String, val issues: List<ScriptIssue>)

object NmvScripts {
    private val presetClasses = setOf(
        "ClientPrefs", "Paths", "Conductor", "PlayState", "Character", "Note", "BGSprite", "StrumNote", "HealthIcon", "CoolUtil",
        "Alphabet", "NoteSplash", "GameOverSubstate", "FunkinSprite", "MusicBeatState", "StageData", "Bar", "DialogueBox",
        "CutsceneHandler", "BackgroundDancer", "BackgroundGirls", "FunkinVideoSprite"
    )

    private val psychToNmvPackages = listOf(
        "objects." to "funkin.objects.",
        "states.stages.objects." to "funkin.objects.stageobjects.",
        "states." to "funkin.states.",
        "substates." to "funkin.states.substates.",
        "backend." to "funkin.backend.",
        "cutscenes." to "funkin.objects.",
        "options." to "funkin.states.options."
    )

    private val nmvToPsych = mapOf(
        "funkin.data.ClientPrefs" to "backend.ClientPrefs",
        "funkin.Paths" to "backend.Paths",
        "funkin.backend.Conductor" to "backend.Conductor",
        "funkin.states.PlayState" to "states.PlayState",
        "funkin.objects.Character" to "objects.Character",
        "funkin.objects.note.Note" to "objects.Note",
        "funkin.objects.note.StrumNote" to "objects.StrumNote",
        "funkin.objects.BGSprite" to "objects.BGSprite",
        "funkin.objects.HealthIcon" to "objects.HealthIcon",
        "funkin.objects.Alphabet" to "objects.Alphabet",
        "funkin.objects.Bar" to "objects.Bar",
        "funkin.utils.CoolUtil" to "backend.CoolUtil",
        "funkin.states.substates.GameOverSubstate" to "substates.GameOverSubstate",
        "funkin.objects.stageobjects.BackgroundDancer" to "states.stages.objects.BackgroundDancer",
        "funkin.objects.stageobjects.BackgroundGirls" to "states.stages.objects.BackgroundGirls",
        "funkin.backend.FunkinShader.FunkinRuntimeShader" to "flixel.addons.display.FlxRuntimeShader"
    )

    private val clientPrefs = mapOf(
        "antialiasing" to "globalAntialiasing",
        "cacheOnGPU" to "gpuCaching"
    )

    private val importLine = Regex("(?m)^[ \\t]*import\\s+([A-Za-z0-9_.*]+)\\s*;[ \\t]*\\r?\\n?")

    fun mapImport(full: String): String? {
        val short = full.substringAfterLast('.')
        if (full.startsWith("flixel.") || full.startsWith("openfl.") || full.startsWith("haxe.") || full.startsWith("lime.") || full.startsWith("sys.") || full.startsWith("funkin.")) {
            return if (short in presetClasses && full.startsWith("funkin.")) null else full
        }
        if (short in presetClasses) return null
        if (full.startsWith("psychlua.") || full.startsWith("llua.") || full.startsWith("debug.")) return null
        psychToNmvPackages.forEach { (from, to) -> if (full.startsWith(from)) return to + full.removePrefix(from) }
        return if (full.contains('.')) null else full
    }

    private fun defines(text: String, name: String) = Regex("(?m)^\\s*(?:override\\s+)?function\\s+$name\\s*\\(").containsMatchIn(text)

    fun psychHxToNmv(source: String): NmvHxResult {
        val issues = mutableListOf<ScriptIssue>()
        var text = importLine.replace(source) { m ->
            val full = m.groupValues[1]
            val mapped = mapImport(full)
            if (mapped == null) {
                if (full.startsWith("psychlua.") || full.startsWith("llua.")) issues += ScriptIssue(0, "import $full is Psych-only and was removed.")
                ""
            } else "import $mapped;\n"
        }
        text = Regex("ClientPrefs\\.data\\.([A-Za-z_][A-Za-z0-9_]*)").replace(text) { "ClientPrefs." + (clientPrefs[it.groupValues[1]] ?: it.groupValues[1]) }
        text = text.replace(Regex("\\bConductor\\.crochet\\b"), "Conductor.crotchet").replace(Regex("\\bConductor\\.stepCrochet\\b"), "Conductor.stepCrotchet")
        text = text.replace(Regex("(?<![\\w.])debugPrint\\s*\\("), "trace(")
        if (defines(text, "onCreate") && !defines(text, "onLoad")) text = Regex("(?m)^(\\s*)function\\s+onCreate\\s*\\(").replace(text) { "${it.groupValues[1]}function onLoad(" }
        if (defines(text, "onEventPushed") && !defines(text, "onEventPush")) {
            text = text.trimEnd() + "\n\nfunction onEventPush(event)\n{\n  onEventPushed(event.event, event.value1, event.value2, event.strumTime);\n}\n"
        }
        listOf("createGlobalCallback", "createCallback", "addLuaScript", "addHScript", "openCustomSubstate").forEach { name ->
            if (Regex("(?<![\\w.])$name\\s*\\(").containsMatchIn(text)) issues += ScriptIssue(0, "$name() is Psych-only and does nothing in NightmareVision.")
        }
        listOf("onUpdateScore", "onGameOverStart", "onGameOverConfirm", "onNextDialogue", "onSkipDialogue", "onCustomSubstateCreate").forEach { name ->
            if (defines(text, name)) issues += ScriptIssue(0, "Callback $name has no NightmareVision event, so it never runs.")
        }
        return NmvHxResult(text, issues)
    }

    fun nmvHxToPsych(source: String): NmvHxResult {
        val issues = mutableListOf<ScriptIssue>()
        var text = importLine.replace(source) { m ->
            val full = m.groupValues[1]
            val mapped = nmvToPsych[full] ?: when {
                full.startsWith("funkin.") -> {
                    issues += ScriptIssue(0, "import $full is NightmareVision-only and was removed.")
                    null
                }
                else -> full
            }
            if (mapped == null) "" else "import $mapped;\n"
        }
        val reverse = clientPrefs.entries.associate { (k, v) -> v to k }
        text = Regex("(?<![\\w.])ClientPrefs\\.(?!data\\.)([A-Za-z_][A-Za-z0-9_]*)").replace(text) { "ClientPrefs.data." + (reverse[it.groupValues[1]] ?: it.groupValues[1]) }
        text = text.replace(Regex("\\bConductor\\.crotchet\\b"), "Conductor.crochet").replace(Regex("\\bConductor\\.stepCrotchet\\b"), "Conductor.stepCrochet")
        if (defines(text, "onLoad") && !defines(text, "onCreate")) text = Regex("(?m)^(\\s*)function\\s+onLoad\\s*\\(").replace(text) { "${it.groupValues[1]}function onCreate(" }
        listOf("ModManager", "SubModifier", "NoteModifier", "EventTimeline", "playFields", "playHUD", "newOption", "getOption").forEach { name ->
            if (Regex("(?<![\\w])$name\\b").containsMatchIn(text)) issues += ScriptIssue(0, "$name is NightmareVision-only; that part of the script needs a manual port.")
        }
        return NmvHxResult(text, issues)
    }
}
