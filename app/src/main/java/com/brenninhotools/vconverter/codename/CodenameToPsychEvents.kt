package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.LuaToHScript
import kotlin.math.abs

data class PsychEventScript(val name: String, val script: String, val description: String)

object CodenameToPsychEvents {
    val needsCompat = setOf("Camera Modulo Change", "Camera Bop")

    val scripted = linkedSetOf("Camera Movement", "Camera Position", "Camera Bop", "Camera Zoom", "Camera Modulo Change", "Camera Flash", "HScript Call")

    val paramCounts = mapOf(
        "Camera Movement" to 2,
        "Camera Position" to 7,
        "Camera Bop" to 1,
        "Camera Zoom" to 8,
        "Camera Modulo Change" to 4,
        "Camera Flash" to 4,
        "HScript Call" to 2
    )

    fun valueText(value: Any?): String = when (value) {
        null -> ""
        is Boolean -> value.toString()
        is Number -> number(value.toDouble())
        else -> value.toString()
    }

    fun number(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val rounded = Math.round(value * 1_000_000.0) / 1_000_000.0
        return if (rounded % 1.0 == 0.0 && abs(rounded) < 1e15) rounded.toLong().toString() else rounded.toString()
    }

    fun encode(values: List<Any?>, count: Int = values.size): Pair<String, String> {
        val padded = (0 until maxOf(count, values.size)).map { values.getOrNull(it) }
        if (padded.size <= 2) return valueText(padded.getOrNull(0)) to valueText(padded.getOrNull(1))
        val split = (padded.size + 1) / 2
        return padded.take(split).joinToString(",") { valueText(it) } to padded.drop(split).joinToString(",") { valueText(it) }
    }

    fun decodeFunction(): String = """
function eventParams(value1:String, value2:String, count:Int):Array<String>
{
  if (value1 == null) value1 = "";
  if (value2 == null) value2 = "";
  if (count <= 2) return [value1, value2];
  var list:Array<String> = [];
  for (v in value1.split(",")) list.push(StringTools.trim(v));
  for (v in value2.split(",")) list.push(StringTools.trim(v));
  while (list.length < count) list.push("");
  return list;
}
""".trim()

    private val easeFunction = """
function easeFor(name:String, type:String):Dynamic
{
  if (name == null || name == "" || name == "linear" || name == "CLASSIC") return FlxEase.linear;
  if (type == null || type == "") type = "In";
  var ease:Dynamic = Reflect.field(FlxEase, name + type);
  if (ease == null) ease = Reflect.field(FlxEase, name);
  return ease == null ? FlxEase.linear : ease;
}
""".trim()

    private fun header(name: String) = "  if (name != ${LuaToHScript.hxString(name)}) return;"

    fun script(name: String): PsychEventScript? {
        val body = when (name) {
            "Camera Movement" -> """
function onEvent(name:String, value1:String, value2:String, strumTime:Float)
{
${header(name)}
  var target:String = StringTools.trim(value1).toLowerCase();
  if (target == "gf" || target == "girlfriend")
  {
    if (game.gf == null) return;
    game.isCameraOnForcedPos = true;
    game.moveCameraToGirlfriend();
  }
  else
  {
    game.isCameraOnForcedPos = false;
    game.moveCamera(target != "bf" && target != "boyfriend");
  }
  if (StringTools.trim(value2) == "snap") FlxG.camera.snapToTarget();
}
"""
            "Camera Position" -> """
function onEvent(name:String, value1:String, value2:String, strumTime:Float)
{
${header(name)}
  var p:Array<String> = eventParams(value1, value2, 7);
  var x:Float = Std.parseFloat(p[0]);
  var y:Float = Std.parseFloat(p[1]);
  if (Math.isNaN(x)) x = 0;
  if (Math.isNaN(y)) y = 0;
  game.isCameraOnForcedPos = true;
  if (p[6] == "true")
  {
    game.camFollow.x += x;
    game.camFollow.y += y;
  }
  else
  {
    game.camFollow.x = x;
    game.camFollow.y = y;
  }
  if (p[2] == "false") FlxG.camera.snapToTarget();
}
"""
            "Camera Bop" -> """
function onEvent(name:String, value1:String, value2:String, strumTime:Float)
{
${header(name)}
  if (!ClientPrefs.data.camZooms) return;
  var amount:Float = Std.parseFloat(value1);
  if (Math.isNaN(amount)) amount = 0.1;
  var c:Dynamic = getVar("codenameCompat");
  var strength:Float = c == null || c.camZoomingStrength == null ? 1 : c.camZoomingStrength;
  game.camGame.zoom += amount * strength;
  game.camHUD.zoom += amount * strength;
}
"""
            "Camera Zoom" -> """
var stageZoom:Float = 1;
var hudZoom:Float = 1;
var hudBefore:Float = 1;
var zoomTween:FlxTween = null;
var hudTween:FlxTween = null;

function onCreatePost()
{
  stageZoom = game.defaultCamZoom;
}

function onUpdate(elapsed:Float)
{
  hudBefore = game.camHUD.zoom;
}

function onUpdatePost(elapsed:Float)
{
  if (hudZoom == 1 || !game.camZooming || hudTween != null) return;
  var f:Float = Math.exp(-elapsed * 3.125 * game.camZoomingDecay * game.playbackRate);
  game.camHUD.zoom = hudZoom + (hudBefore - hudZoom) * f;
}

function setDefault(hud:Bool, value:Float)
{
  if (hud) hudZoom = value;
  else game.defaultCamZoom = value;
}

function onEvent(name:String, value1:String, value2:String, strumTime:Float)
{
${header(name)}
  var p:Array<String> = eventParams(value1, value2, 8);
  var hud:Bool = p[2] == "camHUD";
  var cam:FlxCamera = hud ? game.camHUD : game.camGame;
  var zoom:Float = Std.parseFloat(p[1]);
  if (Math.isNaN(zoom)) zoom = 1;
  var target:Float = zoom * (p[6] == "stage" ? stageZoom : 1);
  if (p[7] == "true") target *= cam.zoom;
  if (hud && hudTween != null) hudTween.cancel();
  if (!hud && zoomTween != null) zoomTween.cancel();
  if (p[0] == "false")
  {
    cam.zoom = target;
    setDefault(hud, target);
    return;
  }
  if (p[4] == "CLASSIC")
  {
    setDefault(hud, target);
    return;
  }
  var steps:Float = Std.parseFloat(p[3]);
  if (Math.isNaN(steps)) steps = 4;
  var tween:FlxTween = FlxTween.tween(cam, {zoom: target}, Conductor.stepCrochet / 1000 * steps / game.playbackRate, {
    ease: easeFor(p[4], p[5]),
    onUpdate: function(t:FlxTween) setDefault(hud, cam.zoom),
    onComplete: function(t:FlxTween)
    {
      setDefault(hud, target);
      if (hud) hudTween = null;
      else zoomTween = null;
    }
  });
  if (hud) hudTween = tween;
  else zoomTween = tween;
}
"""
            "Camera Modulo Change" -> """
function onEvent(name:String, value1:String, value2:String, strumTime:Float)
{
${header(name)}
  var p:Array<String> = eventParams(value1, value2, 4);
  var c:Dynamic = cne();
  var interval:Float = Std.parseFloat(p[0]);
  var strength:Float = Std.parseFloat(p[1]);
  var offset:Float = Std.parseFloat(p[3]);
  c.camZoomingInterval = Math.isNaN(interval) || interval <= 0 ? 4 : interval;
  c.camZoomingStrength = Math.isNaN(strength) ? 1 : strength;
  c.camZoomingEvery = p[2] == "" ? "BEAT" : p[2].toUpperCase();
  c.camZoomingOffset = Math.isNaN(offset) ? 0 : offset;
}
"""
            "Camera Flash" -> """
function onEvent(name:String, value1:String, value2:String, strumTime:Float)
{
${header(name)}
  var p:Array<String> = eventParams(value1, value2, 4);
  var color:Int = FlxColor.fromString(p[1] == "" ? "#FFFFFF" : p[1]);
  var steps:Float = Std.parseFloat(p[2]);
  if (Math.isNaN(steps)) steps = 4;
  var cam:FlxCamera = p[3] == "camGame" ? game.camGame : game.camHUD;
  var duration:Float = Conductor.stepCrochet / 1000 * steps / game.playbackRate;
  if (p[0] == "true") cam.fade(color, duration, false, function() cam.stopFX(), true);
  else cam.flash(color, duration, null, true);
}
"""
            "HScript Call" -> """
function onEvent(name:String, value1:String, value2:String, strumTime:Float)
{
${header(name)}
  var func:String = StringTools.trim(value1);
  if (func == "") return;
  game.callOnScripts(func, value2 == null ? [] : value2.split(","));
}
"""
            else -> return null
        }
        val helpers = buildList {
            if (body.contains("eventParams(")) add(decodeFunction())
            if (body.contains("easeFor(")) add(easeFunction)
            if (body.contains("cne()")) add(CodenamePsychHelpers.compatFunction())
        }
        val text = (listOf(body.trim()) + helpers).joinToString("\n\n") + "\n"
        return PsychEventScript(name, text, description(name))
    }

    fun noteTypeScript(name: String): String? = when (name) {
        CodenameToPsychChart.HIDDEN_GF -> """
function onSpawnNote(note:Note)
{
  if (note.noteType != ${LuaToHScript.hxString(name)}) return;
  note.visible = false;
  note.noteSplashData.disabled = true;
}

function opponentNoteHitPre(note:Note)
{
  if (note.noteType != ${LuaToHScript.hxString(name)}) return Function_Continue;
  var gf:Character = game.gf;
  if (gf != null && !note.noAnimation)
  {
    var anim:String = ["singLEFT", "singDOWN", "singUP", "singRIGHT"][note.noteData % 4] + note.animSuffix;
    if (note.isSustainNote && gf.hasAnimation(anim + "-hold")) anim += "-hold";
    if (!note.isSustainNote || gf.getAnimationName() != anim) gf.playAnim(anim, true);
    gf.holdTimer = 0;
  }
  note.hitByOpponent = true;
  if (!note.isSustainNote) game.invalidateNote(note);
  return Function_Stop;
}
""".trimStart()
        else -> null
    }

    fun description(name: String, params: List<String> = emptyList()): String = when (name) {
        "Camera Movement" -> "Moves the camera to a character in the middle of a section.\nValue 1: dad, bf or gf\nValue 2: snap to jump there instantly"
        "Camera Position" -> "Codename camera position.\nValue 1: X, Y, tween?, tween steps\nValue 2: ease, ease type, is offset?"
        "Camera Bop" -> "Extra camera bop.\nValue 1: amount (1 = one normal bop)"
        "Camera Zoom" -> "Codename camera zoom.\nValue 1: tween?, zoom, camera (camGame/camHUD), steps\nValue 2: ease, ease type, mode (direct/stage), multiplicative?"
        "Camera Modulo Change" -> "Changes how often the camera bops.\nValue 1: interval, strength\nValue 2: BEAT/MEASURE/STEP, offset"
        "Camera Flash" -> "Flashes the camera.\nValue 1: reversed?, color\nValue 2: steps, camera (camGame/camHUD)"
        "HScript Call" -> "Calls a function in every script.\nValue 1: function name\nValue 2: arguments, split with commas"
        else -> {
            val (v1, v2) = if (params.size <= 2) listOf(params.getOrNull(0)).filterNotNull() to listOf(params.getOrNull(1)).filterNotNull()
            else params.take((params.size + 1) / 2) to params.drop((params.size + 1) / 2)
            buildString {
                append("Converted from Codename Engine by V-Converter.")
                if (v1.isNotEmpty()) append("\nValue 1: ").append(v1.joinToString(", "))
                if (v2.isNotEmpty()) append("\nValue 2: ").append(v2.joinToString(", "))
            }
        }
    }
}
