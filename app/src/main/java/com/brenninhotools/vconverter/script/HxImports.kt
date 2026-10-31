package com.brenninhotools.vconverter.script

object HxImports {
    private val known = linkedMapOf(
        "Reflect" to "Reflect",
        "Type" to "Type",
        "StringTools" to "StringTools",
        "Lambda" to "Lambda",
        "EReg" to "EReg",
        "Date" to "Date",
        "StringBuf" to "StringBuf",
        "Json" to "haxe.Json",
        "StringMap" to "haxe.ds.StringMap",
        "IntMap" to "haxe.ds.IntMap",
        "FlxBasic" to "flixel.FlxBasic",
        "FlxCamera" to "flixel.FlxCamera",
        "FlxObject" to "flixel.FlxObject",
        "FlxSprite" to "flixel.FlxSprite",
        "FlxState" to "flixel.FlxState",
        "FlxSubState" to "flixel.FlxSubState",
        "FlxRuntimeShader" to "flixel.addons.display.FlxRuntimeShader",
        "FlxBackdrop" to "flixel.addons.display.FlxBackdrop",
        "FlxTrail" to "flixel.addons.effects.FlxTrail",
        "FlxGroup" to "flixel.group.FlxGroup",
        "FlxTypedGroup" to "flixel.group.FlxGroup.FlxTypedGroup",
        "FlxSpriteGroup" to "flixel.group.FlxSpriteGroup",
        "FlxAngle" to "flixel.math.FlxAngle",
        "FlxMath" to "flixel.math.FlxMath",
        "FlxPoint" to "flixel.math.FlxPoint",
        "FlxRect" to "flixel.math.FlxRect",
        "FlxSound" to "flixel.sound.FlxSound",
        "FlxText" to "flixel.text.FlxText",
        "FlxTextBorderStyle" to "flixel.text.FlxTextBorderStyle",
        "FlxEase" to "flixel.tweens.FlxEase",
        "FlxTween" to "flixel.tweens.FlxTween",
        "FlxAxes" to "flixel.util.FlxAxes",
        "FlxColor" to "flixel.util.FlxColor",
        "FlxGradient" to "flixel.util.FlxGradient",
        "FlxTimer" to "flixel.util.FlxTimer",
        "FlxSort" to "flixel.util.FlxSort",
        "BlendMode" to "openfl.display.BlendMode",
        "ShaderFilter" to "openfl.filters.ShaderFilter",
        "Conductor" to "funkin.Conductor",
        "Highscore" to "funkin.Highscore",
        "PlayerSettings" to "funkin.PlayerSettings",
        "FunkinSound" to "funkin.audio.FunkinSound",
        "FunkinSprite" to "funkin.graphics.FunkinSprite",
        "Module" to "funkin.modding.module.Module",
        "ModuleHandler" to "funkin.modding.module.ModuleHandler",
        "PlayState" to "funkin.play.PlayState",
        "PlayStatePlaylist" to "funkin.play.PlayStatePlaylist",
        "SongEvent" to "funkin.play.event.SongEvent",
        "SongEventSchema" to "funkin.data.event.SongEventSchema",
        "SongEventRegistry" to "funkin.data.event.SongEventRegistry",
        "Song" to "funkin.play.song.Song",
        "Stage" to "funkin.play.stage.Stage",
        "NoteKind" to "funkin.play.notes.notekind.NoteKind",
        "NoteKindManager" to "funkin.play.notes.notekind.NoteKindManager",
        "BaseCharacter" to "funkin.play.character.BaseCharacter",
        "CharacterType" to "funkin.play.character.CharacterType",
        "Countdown" to "funkin.play.Countdown",
        "CameraUtil" to "funkin.util.CameraUtil",
        "EaseUtil" to "funkin.util.EaseUtil",
        "Save" to "funkin.save.Save"
    )

    private val builtIn = setOf(
        "Math", "Std", "Array", "Bool", "Dynamic", "Float", "Int", "String", "Void", "Null", "Any",
        "Assets", "Paths", "Preferences", "Constants", "FlxG"
    )

    fun pathOf(name: String): String? = known[name]

    fun complete(text: String): String {
        val code = blank(text)
        val lines = text.lines().toMutableList()
        val imported = HashSet<String>()
        Regex("^import\\s+([\\w.]+)(?:\\s+as\\s+(\\w+))?\\s*;", RegexOption.MULTILINE).findAll(code).forEach {
            imported += it.groupValues[2].ifEmpty { it.groupValues[1].substringAfterLast('.') }
        }
        val declared = Regex("\\b(?:class|enum|typedef|abstract)\\s+(\\w+)").findAll(code).map { it.groupValues[1] }.toSet()
        val body = code.lines().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
        val used = LinkedHashSet<String>()
        Regex("(?<![\\w.])([A-Z]\\w*)(?=\\s*\\.)").findAll(body).forEach { used += it.groupValues[1] }
        Regex("\\bnew\\s+([A-Z]\\w*)").findAll(body).forEach { used += it.groupValues[1] }
        Regex("\\bisOfType\\([^,]+,\\s*([A-Z]\\w*)").findAll(body).forEach { used += it.groupValues[1] }
        Regex("\\bextends\\s+([A-Z]\\w*)").findAll(body).forEach { used += it.groupValues[1] }
        val missing = used.filter { it !in imported && it !in declared && it !in builtIn && it in known }
        if (missing.isEmpty()) return text
        val add = missing.map { "import ${known.getValue(it)};" }
        val lastImport = lines.indexOfLast { it.startsWith("import ") }
        if (lastImport >= 0) {
            lines.addAll(lastImport + 1, add)
        } else {
            val first = lines.indexOfFirst { it.startsWith("package ") }
            lines.addAll(first + 1, if (first >= 0) listOf("") + add else add + "")
        }
        val sorted = sortImports(lines)
        return sorted.joinToString("\n")
    }

    private fun sortImports(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { it.startsWith("import ") }
        if (start < 0) return lines
        var end = start
        while (end + 1 < lines.size && lines[end + 1].startsWith("import ")) end++
        val block = lines.subList(start, end + 1).distinct().sortedBy { it.removePrefix("import ").removeSuffix(";").lowercase() }
        return lines.subList(0, start) + block + lines.subList(end + 1, lines.size)
    }

    private fun blank(text: String): String {
        val sb = StringBuilder(text)
        var i = 0
        while (i < sb.length) {
            val c = sb[i]
            when {
                c == '/' && i + 1 < sb.length && sb[i + 1] == '/' -> {
                    while (i < sb.length && sb[i] != '\n') sb.set(i++, ' ')
                }
                c == '/' && i + 1 < sb.length && sb[i + 1] == '*' -> {
                    val end = sb.indexOf("*/", i + 2).let { if (it < 0) sb.length else it + 2 }
                    for (k in i until end) if (sb[k] != '\n') sb.set(k, ' ')
                    i = end
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < sb.length && sb[j] != c) {
                        if (sb[j] == '\\') j++
                        j++
                    }
                    for (k in i until minOf(j + 1, sb.length)) if (sb[k] != '\n') sb.set(k, ' ')
                    i = j + 1
                }
                else -> i++
            }
        }
        return sb.toString()
    }
}
