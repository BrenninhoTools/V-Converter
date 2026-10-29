package com.brenninhotools.vconverter.codename

object PsychCodenameHelpers {
    private class Helper(val deps: List<String>, val imports: List<String>, val code: String)

    private fun h(code: String, deps: List<String> = emptyList(), imports: List<String> = emptyList()) = Helper(deps, imports, code)

    private val all = linkedMapOf(
        "vars" to h("""
function setVar(name:String, value:Dynamic):Dynamic
{
  PlayState.instance.scripts.publicVariables.set(name, value);
  return value;
}

function getVar(name:String):Dynamic
{
  var shared:Dynamic = PlayState.instance.scripts.publicVariables;
  if (shared.exists(name)) return shared.get(name);
  return PlayState.instance.stage != null ? PlayState.instance.stage.getSprite(name) : null;
}

function removeVar(name:String):Bool
{
  return PlayState.instance.scripts.publicVariables.remove(name);
}"""),
        "camera" to h("""
function psychCamera(name:String):FlxCamera
{
  var key:String = name == null ? "" : StringTools.trim(name).toLowerCase();
  if (key == "hud" || key == "camhud") return PlayState.instance.camHUD;
  if (key == "other" || key == "camother")
  {
    var cam:FlxCamera = PlayState.instance.scripts.publicVariables.get("camOther");
    if (cam == null)
    {
      cam = new FlxCamera();
      cam.bgColor = 0;
      FlxG.cameras.add(cam, false);
      PlayState.instance.scripts.publicVariables.set("camOther", cam);
    }
    return cam;
  }
  return PlayState.instance.camGame;
}"""),
        "color" to h("""
function psychColor(value:Dynamic):Int
{
  if (value == null) return 0xFFFFFFFF;
  if (Std.isOfType(value, Int)) return value;
  var text:String = StringTools.trim(Std.string(value));
  if (StringTools.startsWith(text, "#")) text = text.substr(1);
  if (StringTools.startsWith(text.toLowerCase(), "0x")) text = text.substr(2);
  if (text.length == 6) text = "FF" + text;
  var parsed:Null<Int> = Std.parseInt("0x" + text);
  if (parsed == null) return FlxColor.fromString(Std.string(value));
  return parsed;
}"""),
        "ease" to h("""
function psychEase(name:String):Dynamic
{
  if (name == null || name == "") return FlxEase.linear;
  var lower:String = StringTools.trim(name).toLowerCase();
  for (field in Type.getClassFields(FlxEase)) if (field.toLowerCase() == lower) return Reflect.field(FlxEase, field);
  return FlxEase.linear;
}"""),
        "field" to h("""
function psychField(name:String):String
{
  switch (name)
  {
    case "songMisses": return "misses";
    case "songSpeed": return "scrollSpeed";
    case "opponentStrums": return "cpuStrums";
    case "curSection": return "curMeasure";
    case "curDecBeat": return "curBeatFloat";
    case "curDecStep": return "curStepFloat";
    case "ratingPercent": return "accuracy";
    case "camZoomingMult": return "camZoomingStrength";
    case "boyfriendGroup": return "boyfriend";
    case "dadGroup": return "dad";
    case "gfGroup": return "gf";
    case "imageFile": return "sprite";
    case "healthIcon": return "icon";
    case "singDuration": return "holdTime";
    case "danceEveryNumBeats": return "beatInterval";
    case "ignoreNote": return "avoid";
    default: return name;
  }
}"""),
        "object" to h("""
function psychObject(tag:String):Dynamic
{
  if (tag == null) return null;
  var game:Dynamic = PlayState.instance;
  var shared:Dynamic = game.scripts.publicVariables;
  if (shared.exists(tag)) return shared.get(tag);
  if (game.stage != null)
  {
    var sprite:Dynamic = game.stage.getSprite(tag);
    if (sprite != null) return sprite;
  }
  switch (tag)
  {
    case "boyfriend", "bf", "boyfriendGroup": return game.boyfriend;
    case "dad", "dadGroup": return game.dad;
    case "gf", "girlfriend", "gfGroup": return game.gf;
    case "camOther": return psychCamera("other");
    case "camGame": return game.camGame;
    case "camHUD": return game.camHUD;
  }
  return Reflect.getProperty(game, psychField(tag));
}

function psychRegister(tag:String, obj:Dynamic):Dynamic
{
  PlayState.instance.scripts.publicVariables.set(tag, obj);
  return obj;
}""", listOf("camera", "field")),
        "property" to h("""
function psychPath(path:String):Array<Dynamic>
{
  var parts:Array<String> = path.split(".");
  var target:Dynamic = null;
  for (i in 0...parts.length)
  {
    var part:String = parts[i];
    var index:Int = -1;
    var bracket:Int = part.indexOf("[");
    if (bracket >= 0)
    {
      index = Std.parseInt(part.substring(bracket + 1, part.indexOf("]")));
      part = part.substr(0, bracket);
    }
    if (i == parts.length - 1 && index < 0) return [target == null ? PlayState.instance : target, psychField(part)];
    target = target == null ? psychObject(part) : Reflect.getProperty(target, psychField(part));
    if (index >= 0 && target != null)
    {
      if (Reflect.hasField(target, "members")) target = target.members[index];
      else target = target[index];
    }
    if (target == null) return [null, part];
  }
  return [target, null];
}

function getProperty(path:String):Dynamic
{
  var p:Array<Dynamic> = psychPath(path);
  if (p[0] == null) return null;
  if (p[1] == null) return p[0];
  if (p[0] == PlayState.instance && !Reflect.hasField(p[0], p[1])) return psychObject(p[1]);
  return Reflect.getProperty(p[0], p[1]);
}

function setProperty(path:String, value:Dynamic):Dynamic
{
  var p:Array<Dynamic> = psychPath(path);
  if (p[0] == null || p[1] == null) return value;
  Reflect.setProperty(p[0], p[1], value);
  return value;
}""", listOf("object")),
        "notes" to h("""
function psychNotes():Array<Dynamic>
{
  var list:Array<Dynamic> = [];
  for (line in PlayState.instance.strumLines.members) if (line != null) for (note in line.notes.members) if (note != null) list.push(note);
  return list;
}"""),
        "group" to h("""
function psychGroup(name:String):Array<Dynamic>
{
  var game:Dynamic = PlayState.instance;
  switch (name)
  {
    case "playerStrums": return game.playerStrums.members;
    case "opponentStrums": return game.cpuStrums.members;
    case "strumLineNotes": return game.cpuStrums.members.concat(game.playerStrums.members);
    case "notes", "unspawnNotes": return psychNotes();
    case "members": return game.members;
  }
  var obj:Dynamic = getProperty(name);
  if (obj == null) return [];
  if (Reflect.hasField(obj, "members")) return obj.members;
  return obj;
}

function psychGroupItem(group:String, index:Dynamic):Dynamic
{
  if (index != null && !Std.isOfType(index, Int) && !Std.isOfType(index, Float)) return index;
  var list:Array<Dynamic> = psychGroup(group);
  var i:Int = Std.int(index);
  return i >= 0 && i < list.length ? list[i] : null;
}

function getPropertyFromGroup(group:String, index:Dynamic, field:String):Dynamic
{
  var obj:Dynamic = psychGroupItem(group, index);
  if (obj == null) return null;
  var parts:Array<String> = field.split(".");
  for (i in 0...parts.length - 1) obj = Reflect.getProperty(obj, psychField(parts[i]));
  return obj == null ? null : Reflect.getProperty(obj, psychField(parts[parts.length - 1]));
}

function setPropertyFromGroup(group:String, index:Dynamic, field:String, value:Dynamic):Dynamic
{
  var obj:Dynamic = psychGroupItem(group, index);
  if (obj == null) return value;
  var parts:Array<String> = field.split(".");
  for (i in 0...parts.length - 1) obj = Reflect.getProperty(obj, psychField(parts[i]));
  if (obj != null) Reflect.setProperty(obj, psychField(parts[parts.length - 1]), value);
  return value;
}""", listOf("notes", "property")),
        "strum" to h("""
function psychStrum(index:Int):Dynamic
{
  var game:Dynamic = PlayState.instance;
  if (index < 4) return game.cpuStrums.members[index];
  return game.playerStrums.members[index - 4];
}"""),
        "character" to h("""
function psychCharacter(name:Dynamic):Dynamic
{
  var key:String = name == null ? "" : StringTools.trim(Std.string(name)).toLowerCase();
  if (key == "dad" || key == "opponent" || key == "1") return PlayState.instance.dad;
  if (key == "gf" || key == "girlfriend" || key == "2") return PlayState.instance.gf;
  return PlayState.instance.boyfriend;
}"""),
        "sprite" to h("""
function psychMakeSprite(image:String, x:Float, y:Float, animated:Bool):FunkinSprite
{
  var spr:FunkinSprite = new FunkinSprite(x, y);
  if (image != null && image != "")
  {
    if (animated) spr.loadSprite(Paths.image(image));
    else spr.loadGraphic(Paths.image(image));
  }
  spr.antialiasing = Options.antialiasing;
  return spr;
}

function psychAddSprite(obj:Dynamic, front:Bool):Void
{
  if (obj == null) return;
  var game:Dynamic = PlayState.instance;
  if (front)
  {
    game.add(obj);
    return;
  }
  var index:Int = game.members.length;
  for (c in [game.gf, game.dad, game.boyfriend]) if (c != null)
  {
    var i:Int = game.members.indexOf(c);
    if (i >= 0 && i < index) index = i;
  }
  game.insert(index, obj);
}"""),
        "anim" to h("""
function psychAddAnim(obj:Dynamic, name:String, prefix:String, ?fps:Float = 24, ?loop:Bool = true, ?indices:Dynamic):Void
{
  if (obj == null) return;
  var list:Array<Int> = [];
  if (indices != null)
  {
    if (Std.isOfType(indices, String))
    {
      for (i in Std.string(indices).split(","))
      {
        var n:Null<Int> = Std.parseInt(StringTools.trim(i));
        if (n != null) list.push(n);
      }
    }
    else
    {
      var raw:Array<Dynamic> = indices;
      for (i in raw) list.push(Std.int(i));
    }
  }
  if (list.length > 0) obj.animation.addByIndices(name, prefix, list, "", fps, loop);
  else obj.animation.addByPrefix(name, prefix, fps, loop);
  if (obj.animation.curAnim == null) psychPlayAnim(obj, name, true);
}

function psychPlayAnim(obj:Dynamic, name:String, ?forced:Bool = false, ?reverse:Bool = false, ?frame:Int = 0):Void
{
  if (obj == null) return;
  if (Std.isOfType(obj, FunkinSprite)) obj.playAnim(name, forced, null, reverse, frame);
  else obj.animation.play(name, forced, reverse, frame);
}

function psychAddOffset(obj:Dynamic, name:String, x:Float, y:Float):Void
{
  if (obj == null || !Std.isOfType(obj, FunkinSprite)) return;
  obj.addOffset(name, x / (obj.scale.x == 0 ? 1 : obj.scale.x), y / (obj.scale.y == 0 ? 1 : obj.scale.y));
}"""),
        "tween" to h("""
var psychTweens:Dynamic = {};

function psychCancelTween(tag:String):Void
{
  var old:FlxTween = Reflect.field(psychTweens, tag);
  if (old != null)
  {
    old.cancel();
    old.destroy();
    Reflect.deleteField(psychTweens, tag);
  }
}

function psychTween(tag:String, target:Dynamic, values:Dynamic, duration:Float, ease:String, ?options:Dynamic):FlxTween
{
  psychCancelTween(tag);
  if (target == null) return null;
  var settings:Dynamic = {ease: psychEase(ease), onComplete: function(t:FlxTween) {
    Reflect.deleteField(psychTweens, tag);
    PlayState.instance.scripts.call("onTweenCompleted", [tag]);
  }};
  if (options != null)
  {
    if (Reflect.hasField(options, "startDelay")) settings.startDelay = options.startDelay;
    if (Reflect.hasField(options, "loopDelay")) settings.loopDelay = options.loopDelay;
  }
  var tween:FlxTween = FlxTween.tween(target, values, duration, settings);
  Reflect.setField(psychTweens, tag, tween);
  return tween;
}

function psychColorTween(tag:String, target:Dynamic, duration:Float, color:Dynamic, ease:String):FlxTween
{
  psychCancelTween(tag);
  if (target == null) return null;
  var tween:FlxTween = FlxTween.color(target, duration, target.color, psychColor(color), {ease: psychEase(ease), onComplete: function(t:FlxTween) {
    Reflect.deleteField(psychTweens, tag);
    PlayState.instance.scripts.call("onTweenCompleted", [tag]);
  }});
  Reflect.setField(psychTweens, tag, tween);
  return tween;
}""", listOf("ease", "color")),
        "timer" to h("""
var psychTimers:Dynamic = {};

function psychCancelTimer(tag:String):Void
{
  var old:FlxTimer = Reflect.field(psychTimers, tag);
  if (old != null)
  {
    old.cancel();
    old.destroy();
    Reflect.deleteField(psychTimers, tag);
  }
}

function psychTimer(tag:String, ?time:Float = 1, ?loops:Int = 1):FlxTimer
{
  psychCancelTimer(tag);
  var timer:FlxTimer = new FlxTimer().start(time, function(t:FlxTimer) {
    if (t.finished) Reflect.deleteField(psychTimers, tag);
    PlayState.instance.scripts.call("onTimerCompleted", [tag, t.loops, t.loopsLeft]);
  }, loops);
  Reflect.setField(psychTimers, tag, timer);
  return timer;
}"""),
        "sound" to h("""
var psychSounds:Dynamic = {};

function psychSound(tag:String):FlxSound
{
  if (tag == null || tag == "") return FlxG.sound.music;
  return Reflect.field(psychSounds, tag);
}

function psychPlaySound(sound:String, ?volume:Float = 1, ?tag:String):FlxSound
{
  if (tag == null || tag == "") return FlxG.sound.play(Paths.sound(sound), volume);
  var old:FlxSound = Reflect.field(psychSounds, tag);
  if (old != null) old.stop();
  var snd:FlxSound = FlxG.sound.play(Paths.sound(sound), volume, false, null, true, function() {
    Reflect.deleteField(psychSounds, tag);
    PlayState.instance.scripts.call("onSoundFinished", [tag]);
  });
  Reflect.setField(psychSounds, tag, snd);
  return snd;
}"""),
        "save" to h("""
var psychSaves:Dynamic = {};

function psychSave(name:String, ?folder:String):FlxSave
{
  var save:FlxSave = Reflect.field(psychSaves, name);
  if (save == null)
  {
    save = new FlxSave();
    save.bind(name, folder == null || folder == "" ? "psychenginemods" : folder);
    Reflect.setField(psychSaves, name, save);
  }
  return save;
}

function psychGetSave(name:String, field:String, ?fallback:Dynamic):Dynamic
{
  var save:FlxSave = psychSave(name);
  var value:Dynamic = Reflect.field(save.data, field);
  return value == null ? fallback : value;
}""", imports = listOf("flixel.util.FlxSave")),
        "noteType" to h("""
function psychNoteType(name:String):String
{
  if (name == null) return "";
  switch (name)
  {
    case "Alt Anim Note": return "Alt Animation";
    case "No Anim Note": return "No Animation";
    default: return name;
  }
}"""),
        "eventArgs" to h("""
function psychEventArgs(e:Dynamic):Array<Dynamic>
{
  var p:Array<Dynamic> = e.params == null ? [] : e.params;
  var text = function(v:Dynamic):String return v == null ? "" : Std.string(v);
  switch (e.name)
  {
    case "Camera Position": return ["Camera Follow Pos", text(p[0]), text(p[1])];
    case "Camera Movement": return null;
    case "BPM Change", "Time Signature Change", "Continuous BPM Change": return null;
    case "Add Camera Zoom": return p[1] == "camHUD" ? ["Add Camera Zoom", "0", text(p[0])] : ["Add Camera Zoom", text(p[0]), "0"];
    case "Play Animation":
      var target:String = p[0] == 1 ? "bf" : (p[0] == 2 ? "gf" : "dad");
      return ["Play Animation", text(p[1]), target];
    case "Scroll Speed Change":
      var speed:Float = p[1] == null ? 1 : p[1];
      var base:Float = PlayState.SONG.scrollSpeed == 0 ? 1 : PlayState.SONG.scrollSpeed;
      var seconds:Float = p[0] == false ? 0 : (p[2] == null ? 4 : p[2]) * Conductor.stepCrochet / 1000;
      return ["Change Scroll Speed", Std.string(speed / base), Std.string(seconds)];
    case "Alt Animation Toggle":
      var who:String = p[2] == 1 ? "bf" : (p[2] == 2 ? "gf" : "dad");
      return ["Alt Idle Animation", who, p[1] == true ? "-alt" : ""];
    default: return [e.name, text(p[0]), text(p[1])];
  }
}"""),
        "triggerEvent" to h("""
var psychForcedTarget:Int = 0;

function triggerEvent(name:String, ?value1:Dynamic, ?value2:Dynamic):Void
{
  var game:Dynamic = PlayState.instance;
  var v1:String = value1 == null ? "" : Std.string(value1);
  var v2:String = value2 == null ? "" : Std.string(value2);
  var f1:Float = Std.parseFloat(v1);
  var f2:Float = Std.parseFloat(v2);
  switch (name)
  {
    case "Camera Follow Pos":
      if (Math.isNaN(f1) && Math.isNaN(f2))
      {
        if (game.curCameraTarget < 0) game.curCameraTarget = psychForcedTarget;
      }
      else
      {
        if (game.curCameraTarget >= 0) psychForcedTarget = game.curCameraTarget;
        game.curCameraTarget = -1;
        game.camFollow.setPosition(Math.isNaN(f1) ? 0 : f1, Math.isNaN(f2) ? 0 : f2);
      }
    case "Add Camera Zoom":
      if (Options.camZoomOnBeat && FlxG.camera.zoom < 1.35)
      {
        FlxG.camera.zoom += Math.isNaN(f1) ? 0.015 : f1;
        game.camHUD.zoom += Math.isNaN(f2) ? 0.03 : f2;
      }
    case "Play Animation":
      var c:Dynamic = psychCharacter(v2 == "" ? "dad" : v2);
      if (c != null) c.playAnim(v1, true);
    case "Hey!":
      var who:String = StringTools.trim(v1).toLowerCase();
      var gfOnly:Bool = who == "gf" || who == "girlfriend" || who == "1";
      var bfOnly:Bool = who == "bf" || who == "boyfriend" || who == "0";
      if (!bfOnly)
      {
        var g:Dynamic = game.dad != null && StringTools.startsWith(game.dad.curCharacter, "gf") ? game.dad : game.gf;
        if (g != null && g.hasAnim("cheer")) g.playAnim("cheer", true);
      }
      if (!gfOnly && game.boyfriend != null && game.boyfriend.hasAnim("hey")) game.boyfriend.playAnim("hey", true);
    case "Set GF Speed":
      game.gfSpeed = Math.isNaN(f1) || f1 < 1 ? 1 : Math.round(f1);
    case "Change Scroll Speed":
      var target:Float = PlayState.SONG.scrollSpeed * (Math.isNaN(f1) ? 1 : f1);
      if (Math.isNaN(f2) || f2 <= 0) game.scrollSpeed = target;
      else FlxTween.tween(game, {scrollSpeed: target}, f2);
    case "Screen Shake":
      var values:Array<String> = [v1, v2];
      var cams:Array<Dynamic> = [game.camGame, game.camHUD];
      for (i in 0...2)
      {
        var split:Array<String> = values[i].split(",");
        var duration:Float = Std.parseFloat(split[0]);
        var intensity:Float = split.length > 1 ? Std.parseFloat(split[1]) : 0;
        if (!Math.isNaN(duration) && !Math.isNaN(intensity) && duration > 0 && intensity != 0) cams[i].shake(intensity, duration);
      }
    case "Play Sound":
      FlxG.sound.play(Paths.sound(v1), Math.isNaN(f2) ? 1 : f2);
    case "Set Property":
      var value:Dynamic = StringTools.trim(v2);
      if (value == "true" || value == "false") value = value == "true";
      else if (!Math.isNaN(f2)) value = f2;
      setProperty(v1, value);
    case "Alt Idle Animation":
      var c:Dynamic = psychCharacter(v1 == "" ? "dad" : v1);
      if (c != null) c.idleSuffix = v2;
    default:
      game.executeEvent({name: name, time: Conductor.songPosition, params: [v1, v2]});
  }
}""", listOf("character", "property")),
        "cameraTarget" to h("""
function psychCameraTarget(name:String):Void
{
  var key:String = name == null ? "" : StringTools.trim(name).toLowerCase();
  var game:Dynamic = PlayState.instance;
  if (key == "gf" || key == "girlfriend") game.curCameraTarget = game.strumLines.members.length > 2 ? 2 : 0;
  else if (key == "dad" || key == "opponent") game.curCameraTarget = 0;
  else game.curCameraTarget = 1;
}"""),
        "random" to h("""
function psychExcludes(text:Dynamic):Array<Int>
{
  var list:Array<Int> = [];
  if (text == null || text == "") return list;
  for (part in Std.string(text).split(","))
  {
    var n:Null<Int> = Std.parseInt(StringTools.trim(part));
    if (n != null) list.push(n);
  }
  return list;
}"""),
        "text" to h("""
function psychMakeText(text:String, width:Float, x:Float, y:Float):FlxText
{
  var t:FlxText = new FlxText(x, y, width, text, 16);
  t.setFormat(Paths.font("vcr.ttf"), 16, 0xFFFFFFFF, "center", FlxTextBorderStyle.OUTLINE, 0xFF000000);
  t.cameras = [PlayState.instance.camHUD];
  t.scrollFactor.set();
  t.borderSize = 2;
  return t;
}""", imports = listOf("flixel.text.FlxTextBorderStyle")),
        "keys" to h("""
function psychKey(name:String, state:Int):Bool
{
  var c:Dynamic = PlayState.instance.controls;
  var key:String = name == null ? "" : StringTools.trim(name).toLowerCase();
  var prefix:String = null;
  if (key == "left") prefix = "NOTE_LEFT";
  else if (key == "down") prefix = "NOTE_DOWN";
  else if (key == "up") prefix = "NOTE_UP";
  else if (key == "right") prefix = "NOTE_RIGHT";
  else if (key == "accept") prefix = "ACCEPT";
  else if (key == "back") prefix = "BACK";
  else if (key == "pause") prefix = "PAUSE";
  else if (key == "reset") prefix = "RESET";
  if (prefix == null) return state == 0 ? FlxG.keys.justPressed.SPACE : (state == 1 ? FlxG.keys.pressed.SPACE : FlxG.keys.justReleased.SPACE);
  var suffix:String = state == 0 ? "_P" : (state == 2 ? "_R" : "");
  if (prefix == "ACCEPT" || prefix == "BACK" || prefix == "PAUSE" || prefix == "RESET") suffix = "";
  return Reflect.getProperty(c, prefix + suffix) == true;
}"""),
        "addBehind" to h("""
function psychAddBehind(target:Dynamic, obj:Dynamic):Dynamic
{
  var game:Dynamic = PlayState.instance;
  var index:Int = target == null ? -1 : game.members.indexOf(target);
  if (index < 0) game.add(obj) else game.insert(index, obj);
  return obj;
}"""),
        "shader" to h("""
function psychShaderSet(obj:Dynamic, name:String, value:Dynamic):Void
{
  if (obj == null) return;
  var shader:Dynamic = Std.isOfType(obj, CustomShader) ? obj : obj.shader;
  if (shader != null) shader.hset(name, value);
}

function psychShaderGet(obj:Dynamic, name:String):Dynamic
{
  if (obj == null) return null;
  var shader:Dynamic = Std.isOfType(obj, CustomShader) ? obj : obj.shader;
  return shader == null ? null : shader.hget(name);
}"""),
        "gamepad" to h("""
function psychGamepad(id:Int, list:String, button:String):Bool
{
  var pad:Dynamic = FlxG.gamepads.getByID(id);
  if (pad == null || button == null) return false;
  return Reflect.getProperty(Reflect.getProperty(pad, list), button.toUpperCase()) == true;
}

function psychAnyGamepad(list:String, button:String):Bool
{
  for (pad in FlxG.gamepads.getActiveGamepads())
  {
    if (Reflect.getProperty(Reflect.getProperty(pad, list), button.toUpperCase()) == true) return true;
  }
  return false;
}

function psychGamepadAxis(id:Int, leftStick:Bool, horizontal:Bool):Float
{
  var pad:Dynamic = FlxG.gamepads.getByID(id);
  if (pad == null || pad.analog == null) return 0;
  var value:Dynamic = Reflect.getProperty(pad.analog.value, (leftStick ? "LEFT_STICK_" : "RIGHT_STICK_") + (horizontal ? "X" : "Y"));
  return value == null ? 0 : value;
}"""),
        "focus" to h("""
var psychFocusEvents:Array<Dynamic> = null;
var psychFocusIndex:Int = 0;
var psychFocusValue:Int = 0;

function psychFocus():Int
{
  if (psychFocusEvents == null)
  {
    psychFocusEvents = [];
    for (e in PlayState.SONG.events) if (e.name == "Camera Movement") psychFocusEvents.push(e);
    psychFocusEvents.sort(function(a, b) return a.time < b.time ? -1 : (a.time > b.time ? 1 : 0));
  }
  var time:Float = Conductor.songPosition;
  if (psychFocusIndex > 0 && psychFocusEvents[psychFocusIndex - 1].time > time)
  {
    psychFocusIndex = 0;
    psychFocusValue = 0;
  }
  while (psychFocusIndex < psychFocusEvents.length && psychFocusEvents[psychFocusIndex].time <= time)
  {
    psychFocusValue = psychFocusEvents[psychFocusIndex].params[0];
    psychFocusIndex++;
  }
  return psychFocusValue;
}"""),
        "allNotes" to h("""
function psychEachNote(callback:Dynamic):Void
{
  for (line in PlayState.instance.strumLines.members) if (line != null) line.notes.forEach(function(n) callback(n));
}""")
    )

    fun resolve(names: Set<String>): List<String> {
        val result = LinkedHashSet<String>()
        fun add(name: String) {
            val helper = all[name] ?: return
            helper.deps.forEach(::add)
            result += name
        }
        names.forEach(::add)
        return all.keys.filter { it in result }
    }

    fun imports(names: Set<String>): List<String> = resolve(names).flatMap { all.getValue(it).imports }.distinct()

    fun emit(names: Set<String>, line: (String) -> Unit) {
        resolve(names).forEach { name ->
            line("")
            all.getValue(name).code.trim('\n').lines().forEach(line)
        }
    }

    val provided: Set<String> = setOf("setVar", "getVar", "removeVar", "getProperty", "setProperty", "getPropertyFromGroup", "setPropertyFromGroup", "triggerEvent")
}
