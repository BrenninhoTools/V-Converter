package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.hx.HxSource

object PsychToCodenameEvents {
    private val descriptions = mapOf(
        "Hey!" to listOf("Target (BF, GF or empty for both)", "Duration (seconds)"),
        "Set GF Speed" to listOf("Speed (1 = every beat)", "Unused"),
        "Screen Shake" to listOf("Game camera (duration, intensity)", "HUD camera (duration, intensity)"),
        "Change Character" to listOf("Target (BF, Dad or GF)", "New character"),
        "Set Property" to listOf("Property (e.g. boyfriend.alpha)", "Value"),
        "Play Sound" to listOf("Sound (from sounds/)", "Volume (0 to 1)"),
        "Alt Idle Animation" to listOf("Target (BF, Dad or GF)", "Idle suffix (e.g. -alt)")
    )

    fun definition(name: String, titles: List<String> = emptyList()): String {
        val names = descriptions[name] ?: titles.ifEmpty { listOf("Value 1", "Value 2") }
        val params = listOf(names.getOrElse(0) { "Value 1" }, names.getOrElse(1) { "Value 2" })
        return CodenameWriters.eventDefinition(params.map { Triple(it, "String", "") })
    }

    fun titles(description: String): List<String> {
        val lines = description.lines().map { it.trim() }
        fun find(prefix: String) = lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
        return listOfNotNull(find("Value 1"), find("Value 2")).takeIf { it.size == 2 } ?: emptyList()
    }

    private const val TARGET = """
function psychTarget(raw:String, fallback:Int):Int
{
  var key:String = StringTools.trim(raw).toLowerCase();
  if (key == "bf" || key == "boyfriend" || key == "player") return 1;
  if (key == "gf" || key == "girlfriend") return 2;
  if (key == "dad" || key == "opponent") return 0;
  return fallback;
}

function psychLine(kind:Int):Dynamic
{
  var names:Array<String> = ["dad", "boyfriend", "girlfriend"];
  for (l in strumLines.members) if (l.data != null && l.data.position == names[kind]) return l;
  for (l in strumLines.members) if (l.data != null && l.data.position == null && l.data.type == kind) return l;
  return null;
}

function psychTargetChars(kind:Int):Array<Dynamic>
{
  var line:Dynamic = psychLine(kind);
  if (line != null) return line.characters;
  if (kind == 2 && gf != null) return [gf];
  return [];
}
"""

    private const val HEY = """
function psychHey(c:Dynamic, anim:String, time:Float)
{
  if (c == null || !c.hasAnimation(anim)) return;
  c.playAnim(anim, true, "LOCK");
  new FlxTimer().start(time, function(t) {
    if (c.getAnimName() == anim) c.dance();
  });
}
"""

    private fun event(name: String, body: String, extra: String = "", imports: List<String> = emptyList()): String = buildString {
        imports.forEach { append("import ").append(it).append(";\n") }
        if (imports.isNotEmpty()) append('\n')
        append("function onEvent(e)\n{\n")
        append("  if (e.event.name != ").append(com.brenninhotools.vconverter.script.hx.HxSource.hxString(name)).append(") return;\n")
        append("  var value1:String = e.event.params[0] == null ? \"\" : Std.string(e.event.params[0]);\n")
        append("  var value2:String = e.event.params[1] == null ? \"\" : Std.string(e.event.params[1]);\n")
        body.trim('\n').lines().forEach { append("  ").append(it).append('\n') }
        append("}\n")
        if (extra.isNotBlank()) append(extra.trimEnd()).append('\n')
    }

    fun script(name: String): String? = when (name) {
        "Hey!" -> event(name, """
var key:String = StringTools.trim(value1).toLowerCase();
var time:Float = Std.parseFloat(value2);
if (Math.isNaN(time) || time <= 0) time = 0.6;
var who:Int = 2;
if (key == "bf" || key == "boyfriend" || key == "0") who = 0;
else if (key == "gf" || key == "girlfriend" || key == "1") who = 1;
if (who != 0)
{
  if (dad != null && StringTools.startsWith(dad.curCharacter, "gf")) psychHey(dad, "cheer", time);
  else if (gf != null) psychHey(gf, "cheer", time);
}
if (who != 1) psychHey(boyfriend, "hey", time);
""", HEY, listOf("flixel.util.FlxTimer"))
        "Set GF Speed" -> event(name, """
var speed:Null<Int> = Std.parseInt(StringTools.trim(value1));
if (speed == null || speed < 1) speed = 1;
gfSpeed = speed;
""")
        "Screen Shake" -> event(name, """
var targets:Array<Dynamic> = [camGame, camHUD];
var values:Array<String> = [value1, value2];
for (i in 0...2)
{
  var split:Array<String> = values[i].split(",");
  var duration:Float = split.length > 0 ? Std.parseFloat(StringTools.trim(split[0])) : 0;
  var intensity:Float = split.length > 1 ? Std.parseFloat(StringTools.trim(split[1])) : 0;
  if (Math.isNaN(duration)) duration = 0;
  if (Math.isNaN(intensity)) intensity = 0;
  if (duration > 0 && intensity != 0) targets[i].shake(intensity, duration);
}
""")
        "Change Character" -> event(name, """
var kind:Int = psychTarget(value1, -1);
if (kind < 0)
{
  var n:Null<Int> = Std.parseInt(StringTools.trim(value1));
  kind = n == 2 ? 2 : (n == 1 ? 0 : 1);
}
var id:String = StringTools.trim(value2);
var line:Dynamic = psychLine(kind);
if (line == null || line.characters.length == 0 || id == "") return;
var old:Dynamic = line.characters[0];
if (old.curCharacter == id) return;
var names:Array<String> = ["dad", "boyfriend", "girlfriend"];
var pos:String = names[kind];
var c:Character = new Character(0, 0, id, stage.isCharFlipped(stage.characterPoses[id] != null ? id : pos, kind == 1));
stage.applyCharStuff(c, pos, 0);
c.visible = old.visible;
c.alpha = old.alpha;
remove(old);
line.characters[0] = c;
old.destroy();
if (kind == 1) iconP1.setIcon(c.getIcon());
if (kind == 0) iconP2.setIcon(c.getIcon());
var left:Int = dad != null && dad.iconColor != null ? dad.iconColor : 0xFFFF0000;
var right:Int = boyfriend != null && boyfriend.iconColor != null ? boyfriend.iconColor : 0xFF66FF33;
healthBar.createFilledBar(left, right);
healthBar.updateBar();
""", TARGET, listOf("funkin.game.Character"))
        "Set Property" -> {
            val helpers = StringBuilder()
            PsychCodenameHelpers.emit(setOf("property")) { helpers.append(it).append('\n') }
            event(name, """
var raw:String = StringTools.trim(value2);
var value:Dynamic = raw;
if (raw == "true") value = true;
else if (raw == "false") value = false;
else
{
  var f:Float = Std.parseFloat(raw);
  if (!Math.isNaN(f)) value = f;
}
setProperty(StringTools.trim(value1), value);
""", helpers.toString(), PsychCodenameHelpers.imports(setOf("property")))
        }
        "Play Sound" -> event(name, """
var volume:Float = Std.parseFloat(value2);
if (Math.isNaN(volume)) volume = 1;
FlxG.sound.play(Paths.sound(StringTools.trim(value1)), volume);
""")
        "Alt Idle Animation" -> event(name, """
for (c in psychTargetChars(psychTarget(value1, 0)))
{
  if (c == null) continue;
  c.idleSuffix = value2;
  if (c.getAnimName() == "idle" || StringTools.startsWith(c.getAnimName(), "dance")) c.dance();
}
""", TARGET)
        else -> null
    }

    fun noteTypeScript(name: String, hurtTexture: String? = null): String? = when (name) {
        "GF Sing" -> """
function onPlayerHit(event) if (event.noteType == "GF Sing" && gf != null) event.characters = [gf];

function onDadHit(event) if (event.noteType == "GF Sing" && gf != null) event.characters = [gf];

function onPlayerMiss(event) if (event.noteType == "GF Sing" && gf != null) event.characters = [gf];
""".trimStart()
        "Hey!" -> """
import flixel.util.FlxTimer;

function onPlayerHit(event) psychHeyNote(event);

function onDadHit(event) psychHeyNote(event);

function psychHeyNote(event)
{
  if (event.noteType != "Hey!") return;
  for (c in event.characters)
  {
    if (c == null || !c.hasAnimation("hey")) continue;
    event.preventAnim();
    psychHey(c, "hey", 0.6);
  }
}
""".trimStart() + HEY
        "Hurt Note" -> buildString {
            append("function onNoteCreation(event)\n{\n  if (event.noteType != \"Hurt Note\") return;\n")
            if (hurtTexture != null) append("  event.noteSprite = ").append(com.brenninhotools.vconverter.script.hx.HxSource.hxString(hurtTexture)).append(";\n")
            append("}\n\n")
            append("function onPostNoteCreation(event)\n{\n  if (event.noteType != \"Hurt Note\") return;\n  event.note.avoid = true;\n")
            if (hurtTexture == null) append("  event.note.color = 0xFF4A4A4A;\n")
            append("}\n\n")
            append("""
function onPlayerHit(event)
{
  if (event.noteType != "Hurt Note") return;
  event.cancel();
  health -= event.note.isSustainNote ? 0.25 : 0.1;
  if (!event.note.isSustainNote)
  {
    misses++;
    combo = 0;
    FlxG.sound.play(Paths.sound("menu/cancel"));
  }
  if (boyfriend != null && boyfriend.hasAnimation("hurt")) boyfriend.playAnim("hurt", true, null);
}

function onPlayerMiss(event)
{
  if (event.noteType != "Hurt Note") return;
  event.misses = 0;
  event.score = 0;
  event.healthGain = 0;
  event.accuracy = null;
  event.gfSad = false;
  event.preventMissSound();
  event.preventResetCombo();
  event.preventStunned();
  event.preventAnim();
  event.preventVocalsMute();
}
""".trimStart())
        }
        else -> null
    }

    fun noteSkinScript(skin: String): String {
        val path = HxSource.hxString(skin)
        return """
function onNoteCreation(event)
{
  if (event.note.noteType == null || event.note.noteType == "") event.noteSprite = $path;
}

function onStrumCreation(event)
{
  event.sprite = $path;
}
""".trimStart()
    }
}
