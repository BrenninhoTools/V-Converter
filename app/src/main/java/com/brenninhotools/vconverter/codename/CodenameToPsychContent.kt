package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.psych.PsychWriters
import com.brenninhotools.vconverter.script.hx.HxSource
import com.brenninhotools.vconverter.stage.PsychPoint
import com.brenninhotools.vconverter.stage.PsychStage
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychWeek
import com.brenninhotools.vconverter.week.PsychWeekSong

data class PsychFile(val path: String, val text: String)

data class PsychCopy(val from: String, val to: String)

data class CodenamePsychResult(
    val id: String,
    val files: List<PsychFile>,
    val report: Report,
    val copies: List<PsychCopy> = emptyList(),
    val hooks: PsychHooks = PsychHooks(),
    val extensions: List<String> = emptyList()
) {
    val mainText: String get() = files.firstOrNull()?.text ?: ""
}

object CodenameToPsychCharacter {
    fun convert(
        character: CodenameCharacter,
        slot: CharacterSlot,
        atlases: Map<String, SparrowAtlas>,
        animate: Boolean = false
    ): CodenamePsychResult {
        val built = CodenamePsychCharacter.build(character, slot, atlases, null)
        val report = ReportBuilder()
        report.stat("Animations", built.character.animations.size)
        report.stat("Side", slot.name.lowercase())
        built.notes.warnings.forEach(report::warn)
        built.notes.sections.forEach { report.section(it.title, it.lines) }
        if (character.extraSheets.isNotEmpty()) report.section("Spritesheets", listOf("Extra sheets were joined into the image field: ${built.character.image}"))
        if (atlases.isEmpty() && !animate) report.warn("Spritesheet XML not provided, so mirrored offsets and centercam corrections are approximate.")
        if (animate) report.warn("This character uses an Animate atlas; Psych loads it from the same folder, but check its offsets in the character editor.")
        val gameOver = character.gameOverCharacter
        val notes = mutableListOf<String>()
        if (gameOver != null && gameOver != "bf-dead") notes += "Game over character \"$gameOver\": set \"gameOverChar\": \"$gameOver\" in the charts (V-Converter does this for the songs it converts)."
        character.interval?.let { if (it != 2) notes += "Codename dance interval $it became danceEveryNumBeats in the script below; Psych reads it from the character's animations (idle every 2 beats, danceLeft/danceRight every beat)." }
        report.section("Notes", notes)
        val hooks = character.interval?.takeIf { it != 2 && character.animations.any { a -> a.name == "idle" } }?.let { interval ->
            PsychHooks(callbacks = mapOf("onCreatePost" to listOf("if (self != null) self.danceEveryNumBeats = $interval;")))
        } ?: PsychHooks()
        return CodenamePsychResult(character.id, listOf(PsychFile("characters/${character.id}.json", PsychWriters.character(built.character))), report.build(), hooks = hooks)
    }
}

object CodenameToPsychStage {
    private val groups = mapOf("bf" to ("boyfriendGroup" to "boyfriend"), "gf" to ("gfGroup" to "gf"), "dad" to ("dadGroup" to "dad"))

    fun isPixel(stage: CodenameStage): Boolean =
        stage.extensions.any { it.substringAfterLast('/').equals("pixel", ignoreCase = true) } || stage.id.lowercase().startsWith("school")

    fun convert(stage: CodenameStage): CodenamePsychResult {
        val report = ReportBuilder()
        val pixel = isPixel(stage)
        val spots = stage.spots
        fun spot(key: String) = spots.getValue(key)
        val psych = PsychStage(
            directory = "",
            defaultZoom = stage.zoom,
            isPixelStage = pixel,
            stageUI = if (pixel) "pixel" else "",
            boyfriend = PsychPoint(spot("bf").x, spot("bf").y),
            girlfriend = PsychPoint(spot("gf").x, spot("gf").y),
            opponent = PsychPoint(spot("dad").x, spot("dad").y),
            hideGirlfriend = false,
            cameraBoyfriend = PsychPoint(spot("bf").cameraX, spot("bf").cameraY),
            cameraOpponent = PsychPoint(spot("dad").cameraX, spot("dad").cameraY),
            cameraGirlfriend = PsychPoint(spot("gf").cameraX, spot("gf").cameraY),
            cameraSpeed = 1.0,
            preload = emptyList(),
            objects = stage.objects
        )
        val json = PsychWriters.stage(psych)

        val fields = mutableListOf<String>()
        val create = mutableListOf<String>()
        val post = mutableListOf<String>()
        val beat = mutableListOf<String>()
        val countdown = mutableListOf<String>()
        val functions = mutableListOf<String>()
        val notes = mutableListOf<String>()

        val defaults = mapOf("bf" to 1.0, "gf" to 0.95, "dad" to 1.0)
        spots.forEach { (key, s) ->
            val (group, character) = groups[key] ?: return@forEach
            val base = defaults.getValue(key)
            if (s.scroll.x != base || s.scroll.y != base) post += "game.$group.scrollFactor.set(${num(s.scroll.x)}, ${num(s.scroll.y)});"
            if (s.scale != 1.0) post += "if (game.$character != null) game.$character.scale.set(game.$character.scale.x * ${num(s.scale)}, game.$character.scale.y * ${num(s.scale)});"
            if (s.alpha != 1.0) post += "if (game.$character != null) game.$character.alpha = ${num(s.alpha)};"
            if (s.angle != 0.0) post += "if (game.$character != null) game.$character.angle = ${num(s.angle)};"
            if (s.flip != null && s.flip != (key == "bf")) post += "if (game.$character != null) game.$character.flipX = !game.$character.flipX;"
        }

        if (stage.beats.isNotEmpty()) {
            fields += "var cneBeatSprites:Array<Dynamic> = [" + stage.beats.joinToString(", ") { b ->
                "{name: ${HxSource.hxString(b.name)}, interval: ${b.interval}, offset: ${b.offset}, anims: [${b.anims.joinToString(", ") { HxSource.hxString(it) }}], counted: 0, skipCountdown: ${b.onCountdown}}"
            } + "];"
            functions += """
function cneStageDance(beat:Int, countdown:Bool):Void
{
  for (b in cneBeatSprites)
  {
    if (countdown && b.skipCountdown) continue;
    var spr:Dynamic = getVar(b.name);
    if (spr == null || !spr.exists) continue;
    var shifted:Int = beat + b.offset;
    if (shifted % b.interval != 0) continue;
    var list:Array<String> = b.anims;
    var anim:String = list[b.counted % list.length];
    b.counted++;
    if (anim == null || anim == "none") continue;
    if (Reflect.hasField(spr, "animOffsets")) spr.playAnim(anim, true);
    else spr.animation.play(anim, true);
  }
}
""".trim()
            beat += "cneStageDance(curBeat, false);"
            countdown += "cneStageDance(counter - 4, true);"
        }

        val imports = linkedSetOf<String>()
        stage.properties.forEach { p ->
            val path = p.path.split('.')
            if (path.any { !Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(it) }) {
                notes += "Property ${p.sprite}.${p.path} was skipped (not a plain field path)."
                return@forEach
            }
            val value = when (p.type) {
                "int", "float", "number", "f", "i" -> p.value.toDoubleOrNull()?.let { num(it) }
                "bool", "boolean", "b" -> (p.value.trim().lowercase() == "true").toString()
                "color" -> "FlxColor.fromString(${HxSource.hxString(p.value)})"
                else -> HxSource.hxString(p.value)
            } ?: return@forEach
            post += "if (getVar(${HxSource.hxString(p.sprite)}) != null) getVar(${HxSource.hxString(p.sprite)}).${p.path} = $value;"
        }
        stage.blends.forEach { (name, blend) ->
            imports += "psychlua.LuaUtils"
            post += "if (getVar(${HxSource.hxString(name)}) != null) getVar(${HxSource.hxString(name)}).blend = LuaUtils.blendModeFromString(${HxSource.hxString(blend)});"
        }
        stage.startCamera?.let { (x, y) ->
            if (x != null) post += "game.camFollow.x = ${num(x)};"
            if (y != null) post += "game.camFollow.y = ${num(y)};"
            post += "FlxG.camera.snapToTarget();"
            notes += "The start camera position became camFollow in onCreatePost."
        }
        val extensions = stage.extensions.filter { !it.substringAfterLast('/').equals("pixel", ignoreCase = true) }
        extensions.forEach { ext -> create += "game.startHScriptsNamed(${HxSource.hxString("stages/extensions/${ext.substringAfterLast('/')}.hx")});" }
        if (pixel) notes += "The pixel extension became stageUI \"pixel\" (Psych's built-in pixel notes, countdown and ratings)."
        if (stage.zoomFactors.isNotEmpty()) report.warn("Sprites with zoomfactor (${stage.zoomFactors.keys.joinToString()}) zoom with the camera in Psych; it has no zoom factor.")
        if (stage.extraCharacters.isNotEmpty()) report.warn("Extra character slots (${stage.extraCharacters.keys.joinToString()}) have no Psych stage equivalent; their notes cannot be sung in Psych.")
        stage.ratings?.let { notes += "The ratings position is not part of Psych stages (move game.comboGroup in a script)." }
        stage.warnings.filterNot { it.contains("zoomfactor") || it.contains("Extra character") }.forEach { report.warn(it.replace("V-Slice", "Psych")) }
        notes += stage.notes.map { it.replace("V-Slice always loads the full stage", "Psych keeps them with the low quality filter") }.filterNot { it.contains("Start camera") }

        report.stat("Objects", stage.objects.count { it.type in setOf("sprite", "animatedSprite", "square") })
        report.stat("Dancing sprites", stage.beats.size)
        report.section("Notes", notes)
        val hooks = PsychHooks(
            fields = fields,
            callbacks = linkedMapOf<String, List<String>>().apply {
                if (create.isNotEmpty()) put("onCreate", create)
                if (post.isNotEmpty()) put("onCreatePost", post)
                if (beat.isNotEmpty()) put("onBeatHit", beat)
                if (countdown.isNotEmpty()) put("onCountdownTick", countdown)
            },
            functions = functions,
            imports = imports.toList()
        )
        return CodenamePsychResult(stage.id, listOf(PsychFile("stages/${stage.id}.json", json)), report.build(), hooks = hooks, extensions = extensions)
    }

    private fun num(value: Double): String = CodenameToPsychEvents.number(value)
}

data class PsychSongInfo(val icon: String, val color: List<Int>)

object CodenameToPsychWeek {
    private val defaultDifficulties = listOf("easy", "normal", "hard")

    fun convert(week: CodenameWeek, songInfo: (String) -> PsychSongInfo?, songName: (String) -> String = { it }): CodenamePsychResult {
        val report = ReportBuilder()
        val songs = week.songs.map { song ->
            val name = songName(song.name)
            val info = songInfo(song.name)
            PsychWeekSong(name, info?.icon ?: "face", info?.color ?: listOf(146, 113, 253))
        }
        val difficulties = if (week.difficulties.map { it.lowercase() } == defaultDifficulties) emptyList()
            else week.difficulties.map { it.replaceFirstChar { c -> c.uppercaseChar() } }
        val psych = PsychWeek(
            songs = songs,
            weekCharacters = week.characters.take(3).map { it ?: "" } + List((3 - week.characters.size).coerceAtLeast(0)) { "" },
            weekBackground = "",
            storyName = week.name,
            weekName = week.name,
            weekBefore = "",
            startUnlocked = true,
            hiddenUntilUnlocked = false,
            hideStoryMode = false,
            hideFreeplay = false,
            difficulties = difficulties
        )
        val copies = listOf(
            PsychCopy("images/menus/storymenu/weeks/${week.sprite}.png", "images/storymenu/${week.id}.png")
        )
        report.stat("Songs", songs.size)
        val notes = mutableListOf<String>()
        notes += "Week image: images/menus/storymenu/weeks/${week.sprite}.png → images/storymenu/${week.id}.png"
        if (difficulties.isNotEmpty()) notes += "Difficulties: ${difficulties.joinToString()}"
        val hidden = week.songs.filter { it.hidden }
        if (hidden.isNotEmpty()) report.warn("Songs hidden from freeplay in Codename (${hidden.joinToString { it.name }}) are listed in Psych's freeplay; Psych hides whole weeks only.")
        week.songs.mapNotNull { s -> s.variation?.let { "${s.name} plays its \"$it\" variant in Codename; the week now lists the \"${songName(s.name)}\" song." } }.forEach { notes += it }
        report.section("Notes", notes)
        return CodenamePsychResult(week.id, listOf(PsychFile("weeks/${week.id}.json", PsychWriters.week(psych))), report.build(), copies)
    }

    fun freeplayWeek(id: String, songs: List<PsychWeekSong>): PsychFile = PsychFile(
        "weeks/$id.json",
        PsychWriters.week(PsychWeek(songs, listOf("", "", ""), "", "", "Freeplay", "", true, false, true, false, emptyList()))
    )

    fun menuCharacter(menu: CodenameMenuCharacter, id: String): CodenamePsychResult {
        val report = ReportBuilder()
        val idle = menu.animations.firstOrNull { it.name == "idle" } ?: menu.animations.firstOrNull()
        val confirm = menu.animations.firstOrNull { it.name == "confirm" }
        val dx = (idle?.x ?: 0.0) * menu.scale
        val dy = (idle?.y ?: 0.0) * menu.scale
        val psych = PsychMenuCharacter(
            image = id,
            scale = menu.scale,
            positionX = menu.x + dx,
            positionY = menu.y + dy,
            idleAnim = idle?.prefix ?: "",
            confirmAnim = confirm?.prefix ?: "",
            flipX = menu.flipX,
            antialiasing = menu.antialiasing
        )
        if (idle?.indices?.isNotEmpty() == true || confirm?.indices?.isNotEmpty() == true) report.warn("Menu character $id uses frame indices; Psych menu characters play whole prefixes.")
        if (confirm != null && (confirm.x != idle?.x || confirm.y != idle?.y)) report.warn("Menu character $id moves its confirm animation; Psych uses one position for both animations.")
        val copies = listOf("png", "xml").map { ext -> PsychCopy("images/${menu.sprite}.$ext", "images/menucharacters/$id.$ext") }
        return CodenamePsychResult(id, listOf(PsychFile("images/menucharacters/$id.json", PsychWriters.menuCharacter(psych))), report.build(), copies)
    }
}
