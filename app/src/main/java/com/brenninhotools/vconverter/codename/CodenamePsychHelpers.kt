package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.hx.HxSource

object CodenamePsychHelpers {
    private class Helper(val deps: List<String>, val code: (Map<String, Int>) -> String)

    private fun fixed(text: String): (Map<String, Int>) -> String = { text }

    const val COMPAT_SCRIPT = "scripts/CodenameCompat.hx"

    private val all = linkedMapOf(
        "cancellable" to Helper(emptyList(), fixed("""
function cneCancellable():Dynamic
{
  var e:Dynamic = {cancelled: false};
  e.cancel = function() e.cancelled = true;
  e.preventDefault = e.cancel;
  return e;
}""")),
        "noteType" to Helper(emptyList(), fixed("""
function cneNoteType(name:String):String
{
  if (name == null || name == "") return null;
  switch (name)
  {
    case "Alt Animation": return "Alt Anim Note";
    case "No Animation": return "No Anim Note";
    default: return name;
  }
}""")),
        "noteEvent" to Helper(listOf("cancellable", "noteType"), fixed("""
function cneNoteEvent(note:Dynamic, player:Bool):Dynamic
{
  var e:Dynamic = cneCancellable();
  var character:Dynamic = note.gfNote && game.gf != null ? game.gf : (player ? game.boyfriend : game.dad);
  e.note = note;
  e.player = player;
  e.direction = note.noteData;
  e.noteType = cneNoteType(note.noteType);
  e.character = character;
  e.characters = [character];
  e.strumLineID = player ? 1 : 0;
  e.animSuffix = note.animSuffix;
  e.animCancelled = note.noAnimation;
  e.healthGain = player ? note.hitHealth : 0;
  e.score = player ? 350 : 0;
  e.rating = player ? "sick" : null;
  e.accuracy = player ? 1 : null;
  e.misses = false;
  e.countAsCombo = player;
  e.showRating = null;
  e.showSplash = player;
  e.forceAnim = true;
  e.deleteNote = !note.isSustainNote;
  e.unmuteVocals = true;
  e.enableCamZooming = true;
  e.strumGlowCancelled = false;
  e.preventAnim = function() e.animCancelled = true;
  e.cancelAnim = e.preventAnim;
  e.preventStrumGlow = function() e.strumGlowCancelled = true;
  e.cancelStrumGlow = e.preventStrumGlow;
  e.preventDeletion = function() e.deleteNote = false;
  e.cancelDeletion = e.preventDeletion;
  e.preventVocalsUnmute = function() e.unmuteVocals = false;
  e.preventCamZooming = function() e.enableCamZooming = false;
  return e;
}

function cneApplyNoteEvent(note:Dynamic, e:Dynamic):Dynamic
{
  if (e.cancelled)
  {
    note.wasGoodHit = true;
    note.hitByOpponent = true;
    if (!note.isSustainNote) game.invalidateNote(note);
    return Function_Stop;
  }
  if (e.animSuffix != null) note.animSuffix = e.animSuffix;
  if (e.healthGain != null && note.mustPress) note.hitHealth = e.healthGain;
  var chars:Array<Dynamic> = e.characters;
  if (e.animCancelled)
  {
    note.noAnimation = true;
    return Function_Continue;
  }
  if (chars != null && chars.length == 1 && chars[0] != null && chars[0] == game.gf && e.character != game.gf)
  {
    note.gfNote = true;
    return Function_Continue;
  }
  if (chars != null && (chars.length != 1 || chars[0] != e.character))
  {
    note.noAnimation = true;
    var anim:String = ["singLEFT", "singDOWN", "singUP", "singRIGHT"][note.noteData % 4] + (note.animSuffix == null ? "" : note.animSuffix);
    for (c in chars)
    {
      if (c == null) continue;
      var play:String = anim;
      if (note.isSustainNote && c.hasAnimation(anim + "-hold")) play = anim + "-hold";
      if (!c.hasAnimation(play)) continue;
      if (!note.isSustainNote || c.getAnimationName() != play) c.playAnim(play, true);
      c.holdTimer = 0;
    }
  }
  return Function_Continue;
}""")),
        "pendingHit" to Helper(emptyList(), fixed("""
function cneKeepHit(note:Dynamic, e:Dynamic):Void
{
  cnePendingHits.push([note, e]);
  if (cnePendingHits.length > 64) cnePendingHits.shift();
}

function cneTakeHit(note:Dynamic):Dynamic
{
  for (i in 0...cnePendingHits.length)
  {
    var entry:Array<Dynamic> = cnePendingHits[i];
    if (entry[0] == note)
    {
      cnePendingHits.splice(i, 1);
      return entry[1];
    }
  }
  return null;
}""")),
        "singEvent" to Helper(listOf("cancellable"), fixed("""
function cneSingEvent(hit:Dynamic):Dynamic
{
  var e:Dynamic = cneCancellable();
  e.direction = hit.direction;
  e.suffix = hit.animSuffix == null ? "" : hit.animSuffix;
  e.context = "SING";
  e.force = true;
  e.note = hit.note;
  return e;
}""")),
        "missEvent" to Helper(listOf("cancellable", "noteType"), fixed("""
function cneMissEvent(note:Dynamic, direction:Int):Dynamic
{
  var e:Dynamic = cneCancellable();
  e.note = note;
  e.direction = note != null ? note.noteData : direction;
  e.noteType = note != null ? cneNoteType(note.noteType) : null;
  e.ghostMiss = note == null;
  e.character = game.boyfriend;
  e.characters = [game.boyfriend];
  e.playerID = 1;
  e.animSuffix = "miss";
  e.animCancelled = false;
  e.healthGain = note != null ? -note.missHealth : -0.04;
  e.score = -10;
  e.misses = 1;
  e.muteVocals = true;
  e.gfSad = true;
  e.playMissSound = true;
  e.forceAnim = true;
  e.preventAnim = function() e.animCancelled = true;
  e.cancelAnim = e.preventAnim;
  return e;
}

function cneApplyMiss(e:Dynamic):Void
{
  if (e.cancelled || e.animCancelled || e.character == null) return;
  var suffix:String = e.animSuffix == null ? "miss" : e.animSuffix;
  if (suffix == "miss") return;
  var anim:String = ["singLEFT", "singDOWN", "singUP", "singRIGHT"][e.direction % 4] + suffix;
  if (e.character.hasAnimation(anim)) e.character.playAnim(anim, true);
}""")),
        "countdown" to Helper(listOf("cancellable"), fixed("""
function cneCountdownEvent(counter:Int):Dynamic
{
  var e:Dynamic = cneCancellable();
  e.swagCounter = counter;
  e.sprite = counter == 1 ? game.countdownReady : (counter == 2 ? game.countdownSet : (counter == 3 ? game.countdownGo : null));
  e.spritePath = null;
  e.soundPath = null;
  e.volume = 0.6;
  e.scale = 1;
  e.antialiasing = !PlayState.isPixelStage;
  return e;
}""")),
        "songEvent" to Helper(listOf("cancellable", "eventParams"), { counts ->
            val entries = counts.entries.sortedBy { it.key }.joinToString(", ") { "${HxSource.hxString(it.key)}: ${it.value}" }
            """
var cneEventCounts:Dynamic = {$entries};

function cneNumeric(text:String):Bool
{
  if (text == null || text.length == 0) return false;
  for (i in 0...text.length)
  {
    var c:String = text.charAt(i);
    if ("0123456789.-+eE".indexOf(c) < 0) return false;
  }
  return !Math.isNaN(Std.parseFloat(text));
}

function cneValue(raw:String):Dynamic
{
  if (raw == null) return null;
  var text:String = StringTools.trim(raw);
  if (text == "true") return true;
  if (text == "false") return false;
  if (cneNumeric(text)) return Std.parseFloat(text);
  return text;
}

function cneStrumIndex(target:String):Int
{
  var t:String = target == null ? "" : StringTools.trim(target).toLowerCase();
  if (t == "bf" || t == "boyfriend" || t == "player" || t == "1") return 1;
  if (t == "gf" || t == "girlfriend" || t == "2") return 2;
  return 0;
}

function cneSongEvent(name:String, value1:String, value2:String, strumTime:Float):Dynamic
{
  var eventName:String = name;
  var params:Array<Dynamic> = [];
  switch (name)
  {
    case "Camera Follow Pos":
      eventName = "Camera Position";
      params = [cneValue(value1), cneValue(value2), true, 4, "CLASSIC", "In", false];
    case "Add Camera Zoom":
      var game1:Float = Std.parseFloat(value1);
      var hud1:Float = Std.parseFloat(value2);
      params = (!Math.isNaN(game1) && game1 != 0) || Math.isNaN(hud1) || hud1 == 0 ? [Math.isNaN(game1) ? 0.015 : game1, "camGame"] : [hud1, "camHUD"];
    case "Play Animation":
      params = [cneStrumIndex(value2), value1, true, "NONE"];
    case "Change Scroll Speed":
      eventName = "Scroll Speed Change";
      var mult:Float = Std.parseFloat(value1);
      var secs:Float = Std.parseFloat(value2);
      if (Math.isNaN(mult)) mult = 1;
      if (Math.isNaN(secs)) secs = 0;
      params = [secs > 0, mult * PlayState.SONG.speed, secs * 1000 / Conductor.stepCrochet, "linear", "In", false];
    case "Alt Idle Animation":
      eventName = "Alt Animation Toggle";
      params = [false, value2 != null && value2 != "", cneStrumIndex(value1)];
    case "Set Property":
      if (value1 == "defaultCamZoom")
      {
        eventName = "Camera Zoom";
        params = [false, cneValue(value2), "camGame", 4, "linear", "In", "direct", false];
      }
      else params = [value1, cneValue(value2)];
    default:
      var count:Dynamic = Reflect.field(cneEventCounts, name);
      var list:Array<String> = eventParams(value1, value2, count == null ? 2 : count);
      for (v in list) params.push(cneValue(v));
      if (name == "Camera Movement" && params.length > 0) params[0] = cneStrumIndex(Std.string(params[0]));
  }
  var e:Dynamic = cneCancellable();
  e.event = {name: eventName, time: strumTime, params: params};
  return e;
}"""
        }),
        "eventParams" to Helper(emptyList(), fixed("\n" + CodenameToPsychEvents.decodeFunction())),
        "noteCreation" to Helper(listOf("cancellable", "noteType"), fixed("""
function cneNoteCreation(note:Dynamic):Dynamic
{
  var e:Dynamic = cneCancellable();
  e.note = note;
  e.strumID = note.noteData;
  e.noteType = cneNoteType(note.noteType);
  e.strumLineID = note.mustPress ? 1 : 0;
  e.mustHit = note.mustPress;
  e.noteSprite = note.texture;
  e.noteScale = note.scale.x;
  e.animSuffix = note.animSuffix;
  return e;
}

function cneApplyNoteCreation(note:Dynamic, e:Dynamic):Void
{
  if (e.cancelled) return;
  if (e.noteSprite != null && e.noteSprite != note.texture) note.texture = e.noteSprite;
  if (e.animSuffix != null) note.animSuffix = e.animSuffix;
}""")),
        "cameraMove" to Helper(listOf("cancellable"), fixed("""
function cneCameraEvent():Dynamic
{
  if (game.isCameraOnForcedPos || !game.generatedMusic || PlayState.SONG.notes[curSection] == null) return null;
  game.moveCameraSection();
  var e:Dynamic = cneCancellable();
  e.position = FlxPoint.get(game.camFollow.x, game.camFollow.y);
  var section:Dynamic = PlayState.SONG.notes[curSection];
  e.strumLine = section.gfSection ? 2 : (section.mustHitSection ? 1 : 0);
  e.focusedCharacters = 1;
  return e;
}""")),
        "compat" to Helper(emptyList(), fixed("""
function cne():Dynamic
{
  var c:Dynamic = getVar("codenameCompat");
  if (c == null)
  {
    c = {camZoomingInterval: 4, camZoomingStrength: 1, camZoomingOffset: 0, camZoomingEvery: "BEAT", defaultHudZoom: 1, maxCamZoom: 1.35, camGameZoomLerp: 0.05, camHUDZoomLerp: 0.05, camGameZoomMult: 0.015, camHUDZoomMult: 0.03, curCameraTarget: -2, stageZoom: game.defaultCamZoom, custom: false};
    setVar("codenameCompat", c);
  }
  c.custom = true;
  return c;
}""")),
        "strumLines" to Helper(emptyList(), fixed("""
function cneStrumLines():Array<Dynamic>
{
  var lines:Array<Dynamic> = [];
  lines.push({characters: [game.dad], members: game.opponentStrums.members, cpu: true, notes: game.notes, vocals: game.opponentVocals, visible: true});
  lines.push({characters: [game.boyfriend], members: game.playerStrums.members, cpu: game.cpuControlled, notes: game.notes, vocals: game.vocals, visible: true});
  if (game.gf != null) lines.push({characters: [game.gf], members: [], cpu: true, notes: game.notes, vocals: null, visible: false});
  return lines;
}""")),
        "sprite" to Helper(listOf("loadSprite"), fixed("""
function cneSprite(x:Float, y:Float, graphic:Dynamic):Dynamic
{
  var spr:ModchartSprite = new ModchartSprite(x, y);
  if (graphic != null)
  {
    if (Std.isOfType(graphic, String)) cneLoadSprite(spr, graphic);
    else spr.loadGraphic(graphic);
  }
  return spr;
}""")),
        "loadSprite" to Helper(emptyList(), fixed("""
function cneLoadSprite(spr:Dynamic, path:String):Dynamic
{
  if (spr == null || path == null) return spr;
  var key:String = StringTools.startsWith(path, "images/") ? path.substr(7) : path;
  if (StringTools.endsWith(key, ".png")) key = key.substr(0, key.length - 4);
  var atlas:Bool = false;
  for (ext in [".xml", ".txt", "/Animation.json"])
  {
    var file:String = "images/" + key + ext;
    if (FileSystem.exists(Paths.modFolders(file)) || FileSystem.exists(Paths.getSharedPath(file))) atlas = true;
  }
  if (atlas) spr.frames = Paths.getAtlas(key);
  else spr.loadGraphic(Paths.image(key));
  return spr;
}""")),
        "addAnim" to Helper(emptyList(), fixed("""
function cneAddAnim(spr:Dynamic, name:String, prefix:String, ?fps:Float = 24, ?loop:Bool = false, ?forced:Dynamic, ?indices:Array<Int>, ?offsetX:Float = 0, ?offsetY:Float = 0):Void
{
  if (spr == null) return;
  if (indices != null && indices.length > 0) spr.animation.addByIndices(name, prefix, indices, "", fps, loop);
  else spr.animation.addByPrefix(name, prefix, fps, loop);
  if ((offsetX != 0 || offsetY != 0) && Reflect.hasField(spr, "animOffsets")) spr.addOffset(name, offsetX, offsetY);
}""")),
        "singAnim" to Helper(emptyList(), fixed("""
function cneSingAnim(character:Dynamic, direction:Int, ?suffix:String = "", ?context:Dynamic, ?force:Bool = true):Void
{
  if (character == null) return;
  var anim:String = ["singLEFT", "singDOWN", "singUP", "singRIGHT"][direction % 4] + (suffix == null ? "" : suffix);
  if (character.hasAnimation(anim)) character.playAnim(anim, force);
  character.holdTimer = 0;
}""")),
        "shaderSet" to Helper(emptyList(), fixed("""
function cneShaderSet(shader:Dynamic, name:String, value:Dynamic):Dynamic
{
  if (shader == null) return value;
  if (Std.isOfType(value, Bool)) shader.setBool(name, value);
  else if (Std.isOfType(value, Array))
  {
    var list:Array<Dynamic> = value;
    if (list.length > 0 && Std.isOfType(list[0], Bool)) shader.setBoolArray(name, value);
    else shader.setFloatArray(name, value);
  }
  else if (Std.isOfType(value, Float)) shader.setFloat(name, value);
  else if (value != null) shader.setBitmapData(name, value);
  return value;
}""")),
        "shaderGet" to Helper(emptyList(), fixed("""
function cneShaderGet(shader:Dynamic, name:String):Dynamic
{
  if (shader == null) return null;
  var value:Dynamic = shader.getFloat(name);
  if (value == null) value = shader.getFloatArray(name);
  if (value == null) value = shader.getBool(name);
  return value;
}""")),
        "addShader" to Helper(emptyList(), fixed("""
function cneAddShader(target:Dynamic, shader:Dynamic):Dynamic
{
  if (target == null || shader == null) return shader;
  if (Std.isOfType(target, FlxCamera))
  {
    var list:Array<Dynamic> = [];
    if (target.filters != null) for (f in target.filters) list.push(f);
    list.push(new ShaderFilter(shader));
    target.filters = list;
  }
  else target.shader = shader;
  return shader;
}""")),
        "indexOf" to Helper(emptyList(), fixed("""
function cneIndexOf(obj:Dynamic):Int
{
  var i:Int = game.members.indexOf(obj);
  if (i >= 0) return i;
  for (g in [game.boyfriendGroup, game.dadGroup, game.gfGroup]) if (g != null && g.members.indexOf(obj) >= 0) return game.members.indexOf(g);
  return game.members.length;
}""")),
        "removeShader" to Helper(emptyList(), fixed("""
function cneRemoveShader(target:Dynamic, shader:Dynamic):Dynamic
{
  if (target == null || shader == null) return shader;
  if (Std.isOfType(target, FlxCamera))
  {
    var list:Array<Dynamic> = [];
    if (target.filters != null) for (f in target.filters) if (f.shader != shader) list.push(f);
    target.filters = list;
  }
  else if (target.shader == shader) target.shader = null;
  return shader;
}"""))
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

    fun emit(names: Set<String>, counts: Map<String, Int>, line: (String) -> Unit) {
        resolve(names).forEach { name ->
            line("")
            all.getValue(name).code(counts).trim('\n').lines().forEach(line)
        }
    }

    fun compatFunction(): String = all.getValue("compat").code(emptyMap()).trim()

    fun selfFunction(characterId: String): String = """
function cneSelf():Dynamic
{
  for (c in [game.boyfriend, game.dad, game.gf]) if (c != null && c.curCharacter == ${HxSource.hxString(characterId)}) return c;
  for (map in [game.boyfriendMap, game.dadMap, game.gfMap]) if (map.exists(${HxSource.hxString(characterId)})) return map.get(${HxSource.hxString(characterId)});
  return null;
}
""".trim()

    fun compatScript(): String = """
var lastBop:Int = -999999;

function compat():Dynamic
{
  return getVar("codenameCompat");
}

function onCreatePost()
{
  var c:Dynamic = compat();
  if (c != null && c.stageZoom == null) c.stageZoom = game.defaultCamZoom;
}

function onStepHit()
{
  var c:Dynamic = compat();
  if (c == null || !c.custom) return;
  game.camZoomingMult = 0;
  var interval:Float = c.camZoomingInterval;
  if (interval == null || interval <= 0) return;
  var every:String = c.camZoomingEvery == null ? "BEAT" : Std.string(c.camZoomingEvery).toUpperCase();
  var position:Float = every == "STEP" ? curStep : (every == "MEASURE" ? curSection : curStep / 4);
  var offset:Float = c.camZoomingOffset == null ? 0 : c.camZoomingOffset;
  var index:Int = Math.floor((position - offset) / interval);
  if (index == lastBop || (position - offset) % interval >= (every == "STEP" ? 1 : 0.25)) return;
  lastBop = index;
  if (!game.camZooming || !ClientPrefs.data.camZooms || FlxG.camera.zoom >= c.maxCamZoom) return;
  var strength:Float = c.camZoomingStrength == null ? 1 : c.camZoomingStrength;
  FlxG.camera.zoom += c.camGameZoomMult * strength;
  game.camHUD.zoom += c.camHUDZoomMult * strength;
}

function onUpdatePost(elapsed:Float)
{
  var c:Dynamic = compat();
  if (c == null || !c.custom || c.defaultHudZoom == 1 || !game.camZooming) return;
  game.camHUD.zoom = FlxMath.lerp(c.defaultHudZoom, game.camHUD.zoom, Math.exp(-elapsed * 3.125 * game.camZoomingDecay * game.playbackRate));
}
""".trim() + "\n"
}
