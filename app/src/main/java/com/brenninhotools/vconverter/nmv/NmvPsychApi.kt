package com.brenninhotools.vconverter.nmv

object NmvPsychApi {
    class Member(val name: String, val text: String, val isField: Boolean)

    val members: Map<String, Member> by lazy { parse(source) }

    val functions: Set<String> by lazy { members.values.filter { !it.isField && it.name.startsWith("psych") }.map { it.name }.toSet() }

    fun helperName(api: String): String = "psych" + api.replaceFirstChar { it.uppercaseChar() }

    fun has(api: String): Boolean = helperName(api) in functions

    fun emit(used: Set<String>, line: (String) -> Unit) {
        val all = resolve(used)
        members.values.filter { it.isField && it.name in all }.forEach { line(it.text) }
        members.values.filter { !it.isField && it.name in all }.forEach {
            line("")
            it.text.lines().forEach(line)
        }
    }

    fun resolve(used: Set<String>): Set<String> {
        val out = linkedSetOf<String>()
        val queue = ArrayDeque(used.filter { it in members })
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!out.add(name)) continue
            val text = members.getValue(name).text
            word.findAll(text).map { it.value }.filter { it != name && it in members && it !in out }.forEach { queue.addLast(it) }
        }
        return out
    }

    private val word = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val start = Regex("^(var|function) ([A-Za-z_][A-Za-z0-9_]*)")

    private fun parse(text: String): Map<String, Member> {
        val lines = text.trim('\n').lines()
        val out = LinkedHashMap<String, Member>()
        var i = 0
        while (i < lines.size) {
            val m = start.find(lines[i])
            if (m == null) {
                i++
                continue
            }
            val isField = m.groupValues[1] == "var"
            val begin = i
            if (isField) {
                i++
            } else {
                var depth = 0
                var opened = false
                while (i < lines.size) {
                    lines[i].forEach { c ->
                        if (c == '{') {
                            depth++
                            opened = true
                        }
                        if (c == '}') depth--
                    }
                    i++
                    if (opened && depth == 0) break
                }
            }
            out[m.groupValues[2]] = Member(m.groupValues[2], lines.subList(begin, i).joinToString("\n").trimEnd(), isField)
        }
        return out
    }

    private val source = """
var psychTweens:StringMap<Dynamic> = new StringMap();
var psychTimers:StringMap<Dynamic> = new StringMap();
var psychSounds:StringMap<Dynamic> = new StringMap();
var psychSaves:StringMap<Dynamic> = new StringMap();
var psychNoteSets:StringMap<Dynamic> = new StringMap();
var psychOffsets:StringMap<Dynamic> = new StringMap();
var psychStrumDefaults:Array<Dynamic> = null;
var psychUnspawnList:Array<Dynamic> = null;
var psychUnspawnSize:Int = -1;

function psychHud(name:String):Dynamic
{
  if (game.playHUD == null) return null;
  return Reflect.getProperty(game.playHUD, name);
}

function psychStageObject(name:String):Dynamic
{
  if (game.stage == null) return null;
  var objects:Dynamic = Reflect.getProperty(game.stage, "objects");
  if (objects == null) return null;
  try
  {
    if (objects.exists(name)) return objects.get(name);
  }
  catch (e:Dynamic) {}
  return null;
}

function psychObj(name:String):Dynamic
{
  if (name == null) return null;
  if (game.variables.exists(name)) return game.variables.get(name);
  switch (name)
  {
    case "unspawnNotes":
      return psychUnspawn();
    case "healthBar", "iconP1", "iconP2", "scoreTxt", "timeBar", "timeTxt":
      return psychHud(name);
    case "bf", "boyfriend":
      return game.boyfriend;
    case "dad":
      return game.dad;
    case "gf", "girlfriend":
      return game.gf;
    case "camFollowPos":
      return Reflect.getProperty(game, "camFollow");
    case "strumLineNotes":
      return psychStrums();
  }
  var value:Dynamic = Reflect.getProperty(game, name);
  if (value == null) value = psychStageObject(name);
  return value;
}

function psychAt(value:Dynamic, key:String):Dynamic
{
  if (value == null) return null;
  var index:Null<Int> = Std.parseInt(key);
  if (index != null)
  {
    var list:Dynamic = Reflect.getProperty(value, "members");
    if (list == null) list = value;
    return list[index];
  }
  try
  {
    if (value.exists(key)) return value.get(key);
  }
  catch (e:Dynamic) {}
  return Reflect.getProperty(value, key);
}

function psychStep(target:Dynamic, part:String, first:Bool):Dynamic
{
  var bracket:Int = part.indexOf("[");
  var name:String = bracket < 0 ? part : part.substr(0, bracket);
  var value:Dynamic = null;
  if (first) value = psychObj(name);
  else if (name == "length")
  {
    try
    {
      value = target.length;
    }
    catch (e:Dynamic)
    {
      value = Reflect.getProperty(target, name);
    }
  }
  else value = Reflect.getProperty(target, name);
  if (bracket >= 0)
  {
    for (piece in part.substr(bracket + 1).split("["))
      value = psychAt(value, StringTools.replace(piece, "]", ""));
  }
  return value;
}

function psychGetIn(target:Dynamic, path:String):Dynamic
{
  var value:Dynamic = target;
  for (part in path.split("."))
  {
    if (value == null) return null;
    value = psychStep(value, part, false);
  }
  return value;
}

function psychSetIn(target:Dynamic, path:String, value:Dynamic):Dynamic
{
  var parts:Array<String> = path.split(".");
  var last:String = parts.pop();
  var holder:Dynamic = parts.length > 0 ? psychGetIn(target, parts.join(".")) : target;
  if (holder == null) return null;
  Reflect.setProperty(holder, last, value);
  return value;
}

function psychGetProperty(path:String, ?allowMaps:Dynamic):Dynamic
{
  if (path == null) return null;
  var parts:Array<String> = Std.string(path).split(".");
  var value:Dynamic = psychStep(null, parts[0], true);
  for (k in 1...parts.length)
  {
    if (value == null) return null;
    value = psychStep(value, parts[k], false);
  }
  return value;
}

function psychSetProperty(path:String, value:Dynamic, ?allowMaps:Dynamic):Dynamic
{
  if (path == null) return null;
  var parts:Array<String> = Std.string(path).split(".");
  if (parts.length == 1 && parts[0].indexOf("[") < 0)
  {
    var name:String = parts[0];
    if (game.variables.exists(name)) game.variables.set(name, value);
    else if (name == "camFollowPos") return value;
    else Reflect.setProperty(game, name, value);
    return value;
  }
  var last:String = parts.pop();
  var holder:Dynamic = psychGetProperty(parts.join("."));
  if (holder == null) return null;
  var bracket:Int = last.indexOf("[");
  if (bracket >= 0)
  {
    var list:Dynamic = psychStep(holder, last.substr(0, bracket), false);
    var key:String = StringTools.replace(last.substr(bracket + 1), "]", "");
    var members:Dynamic = Reflect.getProperty(list, "members");
    if (members == null) members = list;
    members[Std.parseInt(key)] = value;
    return value;
  }
  Reflect.setProperty(holder, last, value);
  return value;
}

function psychStrums():Array<Dynamic>
{
  var out:Array<Dynamic> = [];
  if (game.opponentStrums != null) for (s in game.opponentStrums.members) out.push(s);
  if (game.playerStrums != null) for (s in game.playerStrums.members) out.push(s);
  return out;
}

function psychStrum(index:Dynamic):Dynamic
{
  return psychStrums()[Std.int(index)];
}

function psychDefaultStrum(index:Int, axis:String):Float
{
  if (psychStrumDefaults == null)
  {
    psychStrumDefaults = [];
    for (s in psychStrums()) psychStrumDefaults.push({x: s.x, y: s.y});
  }
  var item:Dynamic = psychStrumDefaults[index];
  if (item == null) return 0;
  return axis == "x" ? item.x : item.y;
}

function psychNoteKey(time:Float, data:Int, player:Bool):String
{
  return Std.string(Math.round(time * 10)) + ":" + Std.string(data) + ":" + (player ? "1" : "0");
}

function psychUnspawn():Array<Dynamic>
{
  var queue:Array<Dynamic> = game.queueNotes;
  if (queue == null) return [];
  if (psychUnspawnList != null && psychUnspawnSize == queue.length) return psychUnspawnList;
  psychUnspawnList = [];
  for (q in queue)
  {
    var player:Bool = q[6] == 0;
    psychUnspawnList.push({strumTime: q[0], sustainLength: q[1], noteData: q[2], noteType: q[3] == null ? "" : q[3], isSustainNote: q[4], mustPress: player, gfNote: q[7], psychKey: psychNoteKey(q[0], q[2], player)});
  }
  psychUnspawnSize = queue.length;
  return psychUnspawnList;
}

function psychApplyNoteSets(note:Dynamic):Void
{
  if (note == null) return;
  var source:Dynamic = note.isSustainNote && note.parent != null ? note.parent : note;
  var sets:Dynamic = psychNoteSets.get(psychNoteKey(source.strumTime, source.noteData, source.mustPress));
  if (sets == null) return;
  for (field in Reflect.fields(sets))
  {
    try
    {
      psychSetIn(note, field, Reflect.field(sets, field));
    }
    catch (e:Dynamic) {}
  }
}

function psychGroup(name:String):Dynamic
{
  var g:Dynamic = psychGetProperty(name);
  if (g == null) return null;
  var list:Dynamic = Reflect.getProperty(g, "members");
  return list != null ? list : g;
}

function psychGetPropertyFromGroup(group:String, index:Dynamic, field:Dynamic, ?allowMaps:Dynamic):Dynamic
{
  var list:Dynamic = psychGroup(group);
  if (list == null) return null;
  var item:Dynamic = list[Std.int(index)];
  if (item == null) return null;
  return psychGetIn(item, Std.string(field));
}

function psychSetPropertyFromGroup(group:String, index:Dynamic, field:Dynamic, value:Dynamic, ?allowMaps:Dynamic):Dynamic
{
  var list:Dynamic = psychGroup(group);
  if (list == null) return null;
  var item:Dynamic = list[Std.int(index)];
  if (item == null) return null;
  if (group == "unspawnNotes" && item.psychKey != null)
  {
    var sets:Dynamic = psychNoteSets.get(item.psychKey);
    if (sets == null)
    {
      sets = {};
      psychNoteSets.set(item.psychKey, sets);
    }
    Reflect.setField(sets, Std.string(field), value);
    Reflect.setField(item, Std.string(field), value);
    return value;
  }
  return psychSetIn(item, Std.string(field), value);
}

function psychResolve(target:Dynamic):Dynamic
{
  if (target == null) return null;
  if (Std.isOfType(target, String)) return psychGetProperty(target);
  return target;
}

function psychCamera(name:Dynamic):Dynamic
{
  if (name != null && !Std.isOfType(name, String)) return name;
  var key:String = name == null ? "" : Std.string(name).toLowerCase();
  switch (key)
  {
    case "camhud", "hud":
      return game.camHUD;
    case "camother", "other":
      return game.camOther;
  }
  return game.camGame;
}

function psychColor(value:Dynamic):Int
{
  if (value == null) return 0xFFFFFFFF;
  if (!Std.isOfType(value, String)) return Std.int(value);
  var text:String = StringTools.trim(value);
  if (StringTools.startsWith(text, "#")) text = text.substr(1);
  if (StringTools.startsWith(text.toLowerCase(), "0x")) text = text.substr(2);
  if (text.length == 6) text = "FF" + text;
  var parsed:Null<Int> = text.length == 8 ? Std.parseInt("0x" + text) : null;
  if (parsed != null) return parsed;
  var named:Null<Int> = FlxColor.fromString(value);
  return named == null ? 0xFFFFFFFF : named;
}

function psychEase(name:Dynamic):Dynamic
{
  if (name == null) return FlxEase.linear;
  if (!Std.isOfType(name, String)) return name;
  var key:String = Std.string(name).toLowerCase();
  for (field in Type.getClassFields(FlxEase))
    if (field.toLowerCase() == key) return Reflect.field(FlxEase, field);
  return FlxEase.linear;
}

function psychChar(name:Dynamic):Dynamic
{
  var key:String = name == null ? "" : Std.string(name).toLowerCase();
  switch (key)
  {
    case "dad", "opponent", "1":
      return game.dad;
    case "gf", "girlfriend", "2":
      return game.gf;
  }
  return game.boyfriend;
}

function psychCharGroup(name:Dynamic):Dynamic
{
  var key:String = name == null ? "" : Std.string(name).toLowerCase();
  switch (key)
  {
    case "dad", "opponent", "1":
      return game.dadGroup;
    case "gf", "girlfriend", "2":
      return game.gfGroup;
  }
  return game.boyfriendGroup;
}

function psychSortStage():Void
{
  var stage:Dynamic = game.stage;
  if (stage == null) return;
  var list:Array<Dynamic> = stage.members;
  var sorted:Array<Dynamic> = [];
  for (item in list)
  {
    if (item == null) continue;
    var at:Int = sorted.length;
    while (at > 0 && sorted[at - 1].zIndex > item.zIndex) at--;
    sorted.insert(at, item);
  }
  for (item in sorted) stage.remove(item, true);
  var z:Int = 0;
  for (item in sorted)
  {
    item.zIndex = z;
    z += 10;
    stage.add(item);
  }
}

function psychRenumberStage():Void
{
  var stage:Dynamic = game.stage;
  if (stage == null) return;
  var z:Int = 0;
  for (item in stage.members)
  {
    if (item == null) continue;
    item.zIndex = z;
    z += 10;
  }
}

function psychAddBehind(obj:Dynamic):Void
{
  var stage:Dynamic = game.stage;
  if (stage == null)
  {
    game.add(obj);
    return;
  }
  psychSortStage();
  var at:Int = -1;
  for (g in [game.gfGroup, game.dadGroup, game.boyfriendGroup])
  {
    var i:Int = g == null ? -1 : stage.members.indexOf(g);
    if (i >= 0 && (at < 0 || i < at)) at = i;
  }
  if (at < 0) stage.add(obj) else stage.insert(at, obj);
  psychRenumberStage();
}

function psychAddLuaSprite(tag:Dynamic, ?front:Bool):Void
{
  var obj:Dynamic = psychResolve(tag);
  if (obj == null) return;
  if (front == true) game.add(obj) else psychAddBehind(obj);
}

function psychRemoveLuaSprite(tag:Dynamic, ?destroy:Dynamic):Void
{
  var obj:Dynamic = psychResolve(tag);
  if (obj == null) return;
  if (game.stage != null) game.stage.remove(obj, true);
  game.remove(obj, true);
  if (destroy != false)
  {
    obj.destroy();
    if (Std.isOfType(tag, String)) game.variables.remove(tag);
  }
}

function psychTag(tag:String, obj:Dynamic):Dynamic
{
  var old:Dynamic = game.variables.get(tag);
  if (old != null && old != obj)
  {
    try
    {
      if (game.stage != null) game.stage.remove(old, true);
      game.remove(old, true);
      old.destroy();
    }
    catch (e:Dynamic) {}
  }
  game.variables.set(tag, obj);
  return obj;
}

function psychMakeLuaSprite(tag:String, ?image:String, ?x:Float, ?y:Float):Dynamic
{
  var sprite:FlxSprite = new FlxSprite(x == null ? 0 : x, y == null ? 0 : y);
  if (image != null && image.length > 0) sprite.loadGraphic(Paths.image(image));
  sprite.antialiasing = ClientPrefs.globalAntialiasing;
  return psychTag(tag, sprite);
}

function psychMakeAnimatedLuaSprite(tag:String, ?image:String, ?x:Float, ?y:Float, ?type:String):Dynamic
{
  var sprite:FlxSprite = new FlxSprite(x == null ? 0 : x, y == null ? 0 : y);
  if (image != null && image.length > 0) psychFrames(sprite, image, type);
  sprite.antialiasing = ClientPrefs.globalAntialiasing;
  return psychTag(tag, sprite);
}

function psychFrames(sprite:Dynamic, image:String, ?type:String):Void
{
  var kind:String = type == null ? "sparrow" : type.toLowerCase();
  if (kind == "packer" || kind == "packeratlas" || kind == "pac") sprite.frames = Paths.getPackerAtlas(image);
  else sprite.frames = Paths.getSparrowAtlas(image);
}

function psychLoadFrames(obj:Dynamic, image:String, ?type:String):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null) psychFrames(sprite, image, type);
}

function psychLoadGraphic(obj:Dynamic, image:String, ?gridX:Dynamic, ?gridY:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  var w:Int = gridX == null ? 0 : Std.int(gridX);
  var h:Int = gridY == null ? 0 : Std.int(gridY);
  sprite.loadGraphic(Paths.image(image), w > 0 || h > 0, w, h);
}

function psychMakeGraphic(obj:Dynamic, ?width:Dynamic, ?height:Dynamic, ?color:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  sprite.makeGraphic(Std.int(width == null ? 256 : width), Std.int(height == null ? 256 : height), psychColor(color == null ? "FFFFFF" : color));
}

function psychIndices(value:Dynamic):Array<Int>
{
  var out:Array<Int> = [];
  if (value == null) return out;
  if (Std.isOfType(value, String))
  {
    for (part in Std.string(value).split(","))
    {
      var n:Null<Int> = Std.parseInt(StringTools.trim(part));
      if (n != null) out.push(n);
    }
    return out;
  }
  for (v in (value:Array<Dynamic>)) out.push(Std.int(v));
  return out;
}

function psychAddAnimationByPrefix(obj:Dynamic, name:String, prefix:String, ?fps:Dynamic, ?loop:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  sprite.animation.addByPrefix(name, prefix, fps == null ? 24 : fps, loop == null ? true : loop);
  if (sprite.animation.curAnim == null) sprite.animation.play(name, true);
}

function psychAddAnimationByIndices(obj:Dynamic, name:String, prefix:String, indices:Dynamic, ?fps:Dynamic, ?loop:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  sprite.animation.addByIndices(name, prefix, psychIndices(indices), "", fps == null ? 24 : fps, loop == null ? false : loop);
  if (sprite.animation.curAnim == null) sprite.animation.play(name, true);
}

function psychAddAnimationByIndicesLoop(obj:Dynamic, name:String, prefix:String, indices:Dynamic, ?fps:Dynamic):Void
{
  psychAddAnimationByIndices(obj, name, prefix, indices, fps, true);
}

function psychAddAnimation(obj:Dynamic, name:String, frames:Dynamic, ?fps:Dynamic, ?loop:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  sprite.animation.add(name, psychIndices(frames), fps == null ? 24 : fps, loop == null ? true : loop);
  if (sprite.animation.curAnim == null) sprite.animation.play(name, true);
}

function psychAddOffset(obj:Dynamic, anim:String, ?x:Dynamic, ?y:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  try
  {
    sprite.addOffset(anim, x == null ? 0 : x, y == null ? 0 : y);
    return;
  }
  catch (e:Dynamic) {}
  var key:String = Std.string(obj) + "::" + anim;
  psychOffsets.set(key, [x == null ? 0 : x, y == null ? 0 : y]);
}

function psychPlayAnim(obj:Dynamic, name:String, ?forced:Dynamic, ?reverse:Dynamic, ?frame:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  var force:Bool = forced == true;
  var back:Bool = reverse == true;
  var start:Int = frame == null ? 0 : Std.int(frame);
  try
  {
    sprite.playAnim(name, force, back, start);
    return;
  }
  catch (e:Dynamic) {}
  if (sprite.animation == null || sprite.animation.getByName(name) == null) return;
  sprite.animation.play(name, force, back, start);
  var offset:Dynamic = psychOffsets.get(Std.string(obj) + "::" + name);
  if (offset != null) sprite.offset.set(offset[0], offset[1]);
}

function psychObjectPlayAnimation(obj:Dynamic, name:String, ?forced:Dynamic, ?frame:Dynamic):Void
{
  psychPlayAnim(obj, name, forced, false, frame);
}

function psychSetScrollFactor(obj:Dynamic, ?x:Dynamic, ?y:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null) sprite.scrollFactor.set(x == null ? 1 : x, y == null ? 1 : y);
}

function psychScaleObject(obj:Dynamic, ?x:Dynamic, ?y:Dynamic, ?updateHitbox:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  sprite.scale.set(x == null ? 1 : x, y == null ? 1 : y);
  if (updateHitbox != false) sprite.updateHitbox();
}

function psychSetGraphicSize(obj:Dynamic, ?x:Dynamic, ?y:Dynamic, ?updateHitbox:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  sprite.setGraphicSize(Std.int(x == null ? 0 : x), Std.int(y == null ? 0 : y));
  if (updateHitbox != false) sprite.updateHitbox();
}

function psychUpdateHitbox(obj:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null) sprite.updateHitbox();
}

function psychUpdateHitboxFromGroup(group:String, index:Dynamic):Void
{
  var list:Dynamic = psychGroup(group);
  if (list != null && list[Std.int(index)] != null) list[Std.int(index)].updateHitbox();
}

function psychScreenCenter(obj:Dynamic, ?axis:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  var key:String = axis == null ? "xy" : Std.string(axis).toLowerCase();
  if (key == "x") sprite.screenCenter(FlxAxes.X);
  else if (key == "y") sprite.screenCenter(FlxAxes.Y);
  else sprite.screenCenter(FlxAxes.XY);
}

function psychSetObjectCamera(obj:Dynamic, ?camera:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null) sprite.cameras = [psychCamera(camera == null ? "game" : camera)];
}

function psychSetBlendMode(obj:Dynamic, ?blend:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null) sprite.blend = CoolUtil.getBlendFromString(blend == null ? "normal" : Std.string(blend));
}

function psychGetObjectOrder(obj:Dynamic):Int
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return -1;
  if (game.stage != null)
  {
    var i:Int = game.stage.members.indexOf(sprite);
    if (i >= 0) return i;
  }
  return game.members.indexOf(sprite);
}

function psychSetObjectOrder(obj:Dynamic, position:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  var at:Int = Std.int(position);
  if (game.members.indexOf(sprite) >= 0 && (game.stage == null || game.stage.members.indexOf(sprite) < 0))
  {
    game.remove(sprite, true);
    game.insert(at, sprite);
    return;
  }
  if (game.stage == null) return;
  psychSortStage();
  game.stage.remove(sprite, true);
  if (at > game.stage.members.length) at = game.stage.members.length;
  game.stage.insert(at, sprite);
  psychRenumberStage();
}

function psychGetMidpointX(obj:Dynamic):Float
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? 0 : sprite.getMidpoint().x;
}

function psychGetMidpointY(obj:Dynamic):Float
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? 0 : sprite.getMidpoint().y;
}

function psychGetGraphicMidpointX(obj:Dynamic):Float
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? 0 : sprite.getGraphicMidpoint().x;
}

function psychGetGraphicMidpointY(obj:Dynamic):Float
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? 0 : sprite.getGraphicMidpoint().y;
}

function psychGetScreenPositionX(obj:Dynamic, ?camera:Dynamic):Float
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? 0 : sprite.getScreenPosition(null, psychCamera(camera)).x;
}

function psychGetScreenPositionY(obj:Dynamic, ?camera:Dynamic):Float
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? 0 : sprite.getScreenPosition(null, psychCamera(camera)).y;
}

function psychObjectsOverlap(a:Dynamic, b:Dynamic):Bool
{
  var first:Dynamic = psychResolve(a);
  var second:Dynamic = psychResolve(b);
  return first != null && second != null && FlxG.overlap(first, second);
}

function psychGetPixelColor(obj:Dynamic, x:Dynamic, y:Dynamic):Int
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? 0 : sprite.pixels.getPixel32(Std.int(x), Std.int(y));
}

function psychLuaSpriteExists(tag:String):Bool
{
  return game.variables.exists(tag) && Std.isOfType(game.variables.get(tag), FlxSprite);
}

function psychLuaTextExists(tag:String):Bool
{
  return game.variables.exists(tag) && Std.isOfType(game.variables.get(tag), FlxText);
}

function psychMakeLuaText(tag:String, ?text:String, ?width:Dynamic, ?x:Dynamic, ?y:Dynamic):Dynamic
{
  var label:FlxText = new FlxText(x == null ? 0 : x, y == null ? 0 : y, width == null ? 0 : width, text == null ? "" : text, 16);
  label.setFormat(Paths.font("vcr.ttf"), 16, 0xFFFFFFFF, "center", FlxTextBorderStyle.OUTLINE, 0xFF000000);
  label.cameras = [game.camHUD];
  label.scrollFactor.set();
  label.borderSize = 2;
  return psychTag(tag, label);
}

function psychAddLuaText(tag:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) game.add(label);
}

function psychRemoveLuaText(tag:Dynamic, ?destroy:Dynamic):Void
{
  psychRemoveLuaSprite(tag, destroy);
}

function psychSetTextString(tag:Dynamic, text:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.text = Std.string(text);
}

function psychGetTextString(tag:Dynamic):String
{
  var label:Dynamic = psychResolve(tag);
  return label == null ? null : label.text;
}

function psychSetTextSize(tag:Dynamic, size:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.size = Std.int(size);
}

function psychGetTextSize(tag:Dynamic):Int
{
  var label:Dynamic = psychResolve(tag);
  return label == null ? 0 : label.size;
}

function psychSetTextWidth(tag:Dynamic, width:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.fieldWidth = width;
}

function psychGetTextWidth(tag:Dynamic):Float
{
  var label:Dynamic = psychResolve(tag);
  return label == null ? 0 : label.fieldWidth;
}

function psychSetTextHeight(tag:Dynamic, height:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.fieldHeight = height;
}

function psychSetTextColor(tag:Dynamic, color:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.color = psychColor(color);
}

function psychSetTextFont(tag:Dynamic, font:String):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.font = Paths.font(font);
}

function psychGetTextFont(tag:Dynamic):String
{
  var label:Dynamic = psychResolve(tag);
  return label == null ? null : label.font;
}

function psychSetTextBorder(tag:Dynamic, ?size:Dynamic, ?color:Dynamic, ?style:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label == null) return;
  var width:Float = size == null ? 1 : size;
  if (width <= 0) label.setBorderStyle(FlxTextBorderStyle.NONE);
  else label.setBorderStyle(FlxTextBorderStyle.OUTLINE, psychColor(color == null ? "000000" : color), width);
}

function psychSetTextAlignment(tag:Dynamic, ?alignment:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.alignment = alignment == null ? "left" : Std.string(alignment).toLowerCase();
}

function psychSetTextItalic(tag:Dynamic, ?italic:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.italic = italic != false;
}

function psychSetTextAutoSize(tag:Dynamic, ?value:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.autoSize = value != false;
}

function psychTweenDone(tag:String, target:Dynamic):Void
{
  psychTweens.remove(tag);
  psychOnTweenCompleted(tag, target);
}

function psychTween(tag:String, target:Dynamic, values:Dynamic, ?duration:Dynamic, ?ease:Dynamic, ?options:Dynamic):Dynamic
{
  psychCancelTween(tag);
  var obj:Dynamic = psychResolve(target);
  if (obj == null) return null;
  var settings:Dynamic = {ease: psychEase(ease), onComplete: function(t:Dynamic) psychTweenDone(tag, target)};
  if (options != null)
  {
    for (field in Reflect.fields(options))
      if (field != "ease" && field != "onComplete" && field != "onUpdate" && field != "onStart") Reflect.setField(settings, field, Reflect.field(options, field));
    if (Reflect.field(options, "type") != null && Std.isOfType(Reflect.field(options, "type"), String))
    {
      var type:String = Std.string(Reflect.field(options, "type")).toLowerCase();
      Reflect.setField(settings, "type", type == "looping" ? 2 : type == "pingpong" ? 4 : type == "persist" ? 1 : type == "backward" ? 16 : 8);
    }
  }
  var tween:Dynamic = FlxTween.tween(obj, values, duration == null ? 1 : duration, settings);
  if (tag != null && tag != "") psychTweens.set(tag, tween);
  return tween;
}

function psychDoTweenX(tag:String, target:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, target, {x: value}, duration, ease);
}

function psychDoTweenY(tag:String, target:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, target, {y: value}, duration, ease);
}

function psychDoTweenAlpha(tag:String, target:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, target, {alpha: value}, duration, ease);
}

function psychDoTweenAngle(tag:String, target:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, target, {angle: value}, duration, ease);
}

function psychDoTweenZoom(tag:String, camera:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, psychCamera(camera), {zoom: value}, duration, ease);
}

function psychDoTweenColor(tag:String, target:Dynamic, color:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  psychCancelTween(tag);
  var obj:Dynamic = psychResolve(target);
  if (obj == null) return null;
  var tween:Dynamic = FlxTween.color(obj, duration == null ? 1 : duration, obj.color, psychColor(color), {ease: psychEase(ease), onComplete: function(t:Dynamic) psychTweenDone(tag, target)});
  if (tag != null && tag != "") psychTweens.set(tag, tween);
  return tween;
}

function psychStartTween(tag:String, target:Dynamic, values:Dynamic, ?duration:Dynamic, ?options:Dynamic):Dynamic
{
  var ease:Dynamic = options == null ? null : Reflect.field(options, "ease");
  return psychTween(tag, target, values, duration, ease, options);
}

function psychNoteTweenX(tag:String, note:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, psychStrum(note), {x: value}, duration, ease);
}

function psychNoteTweenY(tag:String, note:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, psychStrum(note), {y: value}, duration, ease);
}

function psychNoteTweenAlpha(tag:String, note:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, psychStrum(note), {alpha: value}, duration, ease);
}

function psychNoteTweenAngle(tag:String, note:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, psychStrum(note), {angle: value}, duration, ease);
}

function psychNoteTweenDirection(tag:String, note:Dynamic, value:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  return psychTween(tag, psychStrum(note), {direction: value}, duration, ease);
}

function psychCancelTween(tag:String):Void
{
  if (tag == null) return;
  var tween:Dynamic = psychTweens.get(tag);
  if (tween == null) return;
  tween.cancel();
  tween.destroy();
  psychTweens.remove(tag);
}

function psychRunTimer(tag:String, ?time:Dynamic, ?loops:Dynamic):Void
{
  psychCancelTimer(tag);
  var timer:FlxTimer = new FlxTimer();
  timer.start(time == null ? 1 : time, function(t:FlxTimer)
  {
    if (t.finished) psychTimers.remove(tag);
    psychOnTimerCompleted(tag, t.loops, t.loopsLeft);
  }, loops == null ? 1 : Std.int(loops));
  psychTimers.set(tag, timer);
}

function psychCancelTimer(tag:String):Void
{
  if (tag == null) return;
  var timer:Dynamic = psychTimers.get(tag);
  if (timer == null) return;
  timer.cancel();
  timer.destroy();
  psychTimers.remove(tag);
}

function psychCameraShake(camera:Dynamic, ?intensity:Dynamic, ?duration:Dynamic):Void
{
  psychCamera(camera).shake(intensity == null ? 0.05 : intensity, duration == null ? 0.5 : duration);
}

function psychCameraFlash(camera:Dynamic, ?color:Dynamic, ?duration:Dynamic, ?forced:Dynamic):Void
{
  psychCamera(camera).flash(psychColor(color == null ? "FFFFFF" : color), duration == null ? 1 : duration, null, forced == true);
}

function psychCameraFade(camera:Dynamic, ?color:Dynamic, ?duration:Dynamic, ?forced:Dynamic, ?fadeOut:Dynamic):Void
{
  psychCamera(camera).fade(psychColor(color == null ? "000000" : color), duration == null ? 1 : duration, fadeOut == true, null, forced == true);
}

function psychCameraSetTarget(target:Dynamic):Bool
{
  var key:String = target == null ? "" : Std.string(target).toLowerCase();
  var isDad:Bool = key == "dad" || key == "opponent";
  game.moveCamera(isDad);
  return isDad;
}

function psychSetCameraScroll(x:Dynamic, y:Dynamic):Void
{
  FlxG.camera.scroll.set(x - FlxG.width / 2, y - FlxG.height / 2);
}

function psychAddCameraScroll(?x:Dynamic, ?y:Dynamic):Void
{
  FlxG.camera.scroll.add(x == null ? 0 : x, y == null ? 0 : y);
}

function psychGetCameraScrollX():Float
{
  return FlxG.camera.scroll.x + FlxG.width / 2;
}

function psychGetCameraScrollY():Float
{
  return FlxG.camera.scroll.y + FlxG.height / 2;
}

function psychSetCameraFollowPoint(x:Dynamic, y:Dynamic):Void
{
  var follow:Dynamic = Reflect.getProperty(game, "camFollow");
  if (follow != null) follow.setPosition(x, y);
}

function psychAddCameraFollowPoint(?x:Dynamic, ?y:Dynamic):Void
{
  var follow:Dynamic = Reflect.getProperty(game, "camFollow");
  if (follow == null) return;
  follow.x += x == null ? 0 : x;
  follow.y += y == null ? 0 : y;
}

function psychGetCameraFollowX():Float
{
  var follow:Dynamic = Reflect.getProperty(game, "camFollow");
  return follow == null ? 0 : follow.x;
}

function psychGetCameraFollowY():Float
{
  var follow:Dynamic = Reflect.getProperty(game, "camFollow");
  return follow == null ? 0 : follow.y;
}

function psychTriggerEvent(name:String, ?value1:Dynamic, ?value2:Dynamic):Bool
{
  game.triggerEventNote(name, value1 == null ? "" : Std.string(value1), value2 == null ? "" : Std.string(value2));
  return true;
}

function psychCharacterDance(character:Dynamic):Void
{
  var c:Dynamic = psychChar(character);
  if (c != null) c.dance();
}

function psychCharacterPlayAnim(character:Dynamic, anim:String, ?forced:Dynamic):Void
{
  var c:Dynamic = psychChar(character);
  if (c != null) c.playAnim(anim, forced == true);
}

function psychGetCharacterX(character:Dynamic):Float
{
  return psychCharGroup(character).x;
}

function psychGetCharacterY(character:Dynamic):Float
{
  return psychCharGroup(character).y;
}

function psychSetCharacterX(character:Dynamic, value:Dynamic):Void
{
  psychCharGroup(character).x = value;
}

function psychSetCharacterY(character:Dynamic, value:Dynamic):Void
{
  psychCharGroup(character).y = value;
}

function psychAddCharacterToList(name:String, ?type:Dynamic):Void
{
  var key:String = type == null ? "" : Std.string(type).toLowerCase();
  game.addCharacterToList(name, key == "dad" || key == "opponent" || key == "1" ? 1 : key == "gf" || key == "girlfriend" || key == "2" ? 2 : 0);
}

function psychSetHealth(?value:Dynamic):Void
{
  game.health = value == null ? 1 : value;
}

function psychAddHealth(?value:Dynamic):Void
{
  game.health += value == null ? 0 : value;
}

function psychGetHealth():Float
{
  return game.health;
}

function psychSetScore(?value:Dynamic):Void
{
  game.songScore = Std.int(value == null ? 0 : value);
}

function psychAddScore(?value:Dynamic):Void
{
  game.songScore += Std.int(value == null ? 0 : value);
}

function psychSetMisses(?value:Dynamic):Void
{
  game.songMisses = Std.int(value == null ? 0 : value);
}

function psychAddMisses(?value:Dynamic):Void
{
  game.songMisses += Std.int(value == null ? 0 : value);
}

function psychSetHits(?value:Dynamic):Void
{
  game.songHits = Std.int(value == null ? 0 : value);
}

function psychAddHits(?value:Dynamic):Void
{
  game.songHits += Std.int(value == null ? 0 : value);
}

function psychGetSongPosition():Float
{
  return Conductor.songPosition;
}

function psychEndSong():Bool
{
  game.endSong();
  return true;
}

function psychRestartSong(?skipTransition:Dynamic):Bool
{
  FlxG.resetState();
  return true;
}

function psychExitSong(?skipTransition:Dynamic):Bool
{
  var target:Dynamic = Type.resolveClass(PlayState.isStoryMode ? "funkin.states.StoryMenuState" : "funkin.states.FreeplayState");
  if (target != null) FlxG.switchState(Type.createInstance(target, []));
  return true;
}

function psychStartCountdown():Bool
{
  game.startCountdown();
  return true;
}

function psychLoadSong(?name:String, ?difficulty:Dynamic):Void
{
  var song:String = name == null ? PlayState.SONG.song : name;
  PlayState.prepareForSong(song, difficulty == null || difficulty < 0 ? PlayState.storyMeta.difficulty : Std.int(difficulty), PlayState.isStoryMode);
  FlxG.switchState(new PlayState());
}

function psychStartVideo(?name:String, ?canSkip:Dynamic):Bool
{
  new FlxTimer().start(0.01, function(t:FlxTimer) game.startCountdown());
  return true;
}

function psychStartAndEndVideo(?name:String, ?canSkip:Dynamic):Bool
{
  new FlxTimer().start(0.01, function(t:FlxTimer) game.endSong());
  return true;
}

function psychSetHealthBarColors(?left:Dynamic, ?right:Dynamic):Void
{
  var bar:Dynamic = psychHud("healthBar");
  if (bar == null) return;
  try
  {
    bar.setColors(psychColor(left == null ? "FF0000" : left), psychColor(right == null ? "66FF33" : right));
  }
  catch (e:Dynamic) {}
}

function psychSetTimeBarColors(?left:Dynamic, ?right:Dynamic):Void
{
  var bar:Dynamic = psychHud("timeBar");
  if (bar == null) return;
  try
  {
    bar.setColors(psychColor(left == null ? "FFFFFF" : left), psychColor(right == null ? "000000" : right));
  }
  catch (e:Dynamic) {}
}

function psychPrecacheImage(name:String, ?allowGPU:Dynamic):Void
{
  Paths.image(name);
}

function psychPrecacheSound(name:String):Void
{
  Paths.sound(name);
}

function psychPrecacheMusic(name:String):Void
{
  Paths.music(name);
}

function psychPlayMusic(name:String, ?volume:Dynamic, ?loop:Dynamic):Void
{
  FlxG.sound.playMusic(Paths.music(name), volume == null ? 1 : volume, loop == true);
}

function psychPlaySound(name:String, ?volume:Dynamic, ?tag:String, ?loop:Dynamic):Dynamic
{
  var sound:Dynamic = FlxG.sound.play(Paths.sound(name), volume == null ? 1 : volume, loop == true);
  if (tag != null && tag.length > 0)
  {
    var old:Dynamic = psychSounds.get(tag);
    if (old != null) old.stop();
    psychSounds.set(tag, sound);
    sound.onComplete = function()
    {
      if (loop != true) psychSounds.remove(tag);
      psychOnSoundFinished(tag);
    };
  }
  return sound;
}

function psychSound(tag:Dynamic):Dynamic
{
  if (tag == null || tag == "") return FlxG.sound.music;
  return psychSounds.get(Std.string(tag));
}

function psychStopSound(?tag:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound == null) return;
  sound.stop();
  if (tag != null && tag != "") psychSounds.remove(Std.string(tag));
}

function psychPauseSound(?tag:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.pause();
}

function psychResumeSound(?tag:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.play();
}

function psychSetSoundVolume(?tag:Dynamic, ?value:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.volume = value == null ? 1 : value;
}

function psychGetSoundVolume(?tag:Dynamic):Float
{
  var sound:Dynamic = psychSound(tag);
  return sound == null ? 0 : sound.volume;
}

function psychSetSoundTime(?tag:Dynamic, ?value:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.time = value == null ? 0 : value;
}

function psychGetSoundTime(?tag:Dynamic):Float
{
  var sound:Dynamic = psychSound(tag);
  return sound == null ? 0 : sound.time;
}

function psychSetSoundPitch(?tag:Dynamic, ?value:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.pitch = value == null ? 1 : value;
}

function psychGetSoundPitch(?tag:Dynamic):Float
{
  var sound:Dynamic = psychSound(tag);
  return sound == null ? 1 : sound.pitch;
}

function psychSoundFadeIn(?tag:Dynamic, ?duration:Dynamic, ?from:Dynamic, ?to:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.fadeIn(duration == null ? 1 : duration, from == null ? 0 : from, to == null ? 1 : to);
}

function psychSoundFadeOut(?tag:Dynamic, ?duration:Dynamic, ?to:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.fadeOut(duration == null ? 1 : duration, to == null ? 0 : to);
}

function psychSoundFadeCancel(?tag:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null && sound.fadeTween != null) sound.fadeTween.cancel();
}

function psychLuaSoundExists(tag:Dynamic):Bool
{
  return tag != null && psychSounds.exists(Std.string(tag));
}

function psychMusicFadeIn(?duration:Dynamic, ?from:Dynamic, ?to:Dynamic):Void
{
  if (FlxG.sound.music != null) FlxG.sound.music.fadeIn(duration == null ? 1 : duration, from == null ? 0 : from, to == null ? 1 : to);
}

function psychMusicFadeOut(?duration:Dynamic, ?to:Dynamic):Void
{
  if (FlxG.sound.music != null) FlxG.sound.music.fadeOut(duration == null ? 1 : duration, to == null ? 0 : to);
}

function psychGetRandomInt(?min:Dynamic, ?max:Dynamic, ?exclude:Dynamic):Int
{
  var skip:Array<Int> = [];
  if (exclude != null) for (part in Std.string(exclude).split(","))
  {
    var n:Null<Int> = Std.parseInt(StringTools.trim(part));
    if (n != null) skip.push(n);
  }
  return FlxG.random.int(Std.int(min == null ? 0 : min), Std.int(max == null ? 2147483647 : max), skip);
}

function psychGetRandomFloat(?min:Dynamic, ?max:Dynamic, ?exclude:Dynamic):Float
{
  return FlxG.random.float(min == null ? 0 : min, max == null ? 1 : max);
}

function psychGetRandomBool(?chance:Dynamic):Bool
{
  return FlxG.random.bool(chance == null ? 50 : chance);
}

function psychGetColorFromHex(color:Dynamic):Int
{
  return psychColor(color);
}

function psychGetColorFromString(color:Dynamic):Int
{
  return psychColor(color);
}

function psychGetColorFromName(color:Dynamic):Int
{
  return psychColor(color);
}

function psychStringStartsWith(text:Dynamic, start:Dynamic):Bool
{
  return StringTools.startsWith(Std.string(text), Std.string(start));
}

function psychStringEndsWith(text:Dynamic, end:Dynamic):Bool
{
  return StringTools.endsWith(Std.string(text), Std.string(end));
}

function psychStringSplit(text:Dynamic, split:Dynamic):Array<String>
{
  return Std.string(text).split(Std.string(split));
}

function psychStringTrim(text:Dynamic):String
{
  return StringTools.trim(Std.string(text));
}

function psychKeyState(name:String, mode:Int):Bool
{
  var controls:Dynamic = Reflect.getProperty(game, "controls");
  var key:String = name == null ? "" : name.toLowerCase();
  if (key == "space") return mode == 0 ? FlxG.keys.justPressed.SPACE : mode == 1 ? FlxG.keys.pressed.SPACE : FlxG.keys.justReleased.SPACE;
  if (controls == null) return false;
  var field:String = switch (key)
  {
    case "left": "NOTE_LEFT";
    case "down": "NOTE_DOWN";
    case "up": "NOTE_UP";
    case "right": "NOTE_RIGHT";
    case "ui_left": "UI_LEFT";
    case "ui_down": "UI_DOWN";
    case "ui_up": "UI_UP";
    case "ui_right": "UI_RIGHT";
    case "accept": "ACCEPT";
    case "back": "BACK";
    case "pause": "PAUSE";
    case "reset": "RESET";
    default: null;
  }
  if (field == null) return false;
  if (field == "ACCEPT" || field == "BACK" || field == "PAUSE" || field == "RESET") return mode == 0 ? Reflect.getProperty(controls, field) == true : false;
  return Reflect.getProperty(controls, field + (mode == 0 ? "_P" : mode == 2 ? "_R" : "")) == true;
}

function psychKeyJustPressed(name:String):Bool
{
  return psychKeyState(name, 0);
}

function psychKeyPressed(name:String):Bool
{
  return psychKeyState(name, 1);
}

function psychKeyReleased(name:String):Bool
{
  return psychKeyState(name, 2);
}

function psychKeyboardJustPressed(name:String):Bool
{
  return Reflect.getProperty(FlxG.keys.justPressed, Std.string(name).toUpperCase()) == true;
}

function psychKeyboardPressed(name:String):Bool
{
  return Reflect.getProperty(FlxG.keys.pressed, Std.string(name).toUpperCase()) == true;
}

function psychKeyboardReleased(name:String):Bool
{
  return Reflect.getProperty(FlxG.keys.justReleased, Std.string(name).toUpperCase()) == true;
}

function psychMouseState(button:Dynamic, kind:String):Bool
{
  var name:String = button == null ? "left" : Std.string(button).toLowerCase();
  var suffix:String = name == "right" ? "Right" : name == "middle" ? "Middle" : "";
  return Reflect.getProperty(FlxG.mouse, kind + suffix) == true;
}

function psychMouseClicked(?button:Dynamic):Bool
{
  return psychMouseState(button, "justPressed");
}

function psychMousePressed(?button:Dynamic):Bool
{
  return psychMouseState(button, "pressed");
}

function psychMouseReleased(?button:Dynamic):Bool
{
  return psychMouseState(button, "justReleased");
}

function psychGetMouseX(?camera:Dynamic):Float
{
  return FlxG.mouse.getScreenPosition(psychCamera(camera)).x;
}

function psychGetMouseY(?camera:Dynamic):Float
{
  return FlxG.mouse.getScreenPosition(psychCamera(camera)).y;
}

function psychSave(name:String, ?folder:String):Dynamic
{
  var save:Dynamic = psychSaves.get(name);
  if (save != null) return save;
  save = Type.createInstance(Type.resolveClass("flixel.util.FlxSave"), []);
  save.bind(name, folder == null ? "psychenginemods" : folder);
  psychSaves.set(name, save);
  return save;
}

function psychInitSaveData(name:String, ?folder:String):Void
{
  psychSave(name, folder);
}

function psychGetDataFromSave(name:String, field:String, ?fallback:Dynamic):Dynamic
{
  var value:Dynamic = Reflect.field(psychSave(name).data, field);
  return value == null ? fallback : value;
}

function psychSetDataFromSave(name:String, field:String, value:Dynamic):Void
{
  Reflect.setField(psychSave(name).data, field, value);
}

function psychFlushSaveData(name:String):Bool
{
  return psychSave(name).flush();
}

function psychEraseSaveData(name:String):Void
{
  psychSave(name).erase();
}

function psychCallOnLuas(name:String, ?args:Dynamic, ?ignoreStops:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Dynamic
{
  return game.scripts.call(name, args == null ? [] : args);
}

function psychCallOnScripts(name:String, ?args:Dynamic, ?ignoreStops:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Dynamic
{
  return game.scripts.call(name, args == null ? [] : args);
}

function psychCallOnHScript(name:String, ?args:Dynamic, ?ignoreStops:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Dynamic
{
  return game.scripts.call(name, args == null ? [] : args);
}

function psychCallScript(path:String, name:String, ?args:Dynamic):Dynamic
{
  return game.scripts.call(name, args == null ? [] : args);
}

function psychSetOnLuas(name:String, value:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Void
{
  game.scripts.set(name, value);
}

function psychSetOnScripts(name:String, value:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Void
{
  game.scripts.set(name, value);
}

function psychSetOnHScript(name:String, value:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Void
{
  game.scripts.set(name, value);
}

function psychSetVar(name:String, value:Dynamic):Dynamic
{
  game.variables.set(name, value);
  return value;
}

function psychGetVar(name:String):Dynamic
{
  return game.variables.get(name);
}

function psychRemoveVar(name:String):Bool
{
  return game.variables.remove(name);
}

function psychClose():Bool
{
  game.scripts.removeScript(script);
  return true;
}

function psychCheckFileExists(path:String, ?absolute:Dynamic):Bool
{
  return Paths.fileExists(path);
}

function psychGetTextFromFile(path:String, ?ignoreModFolders:Dynamic):String
{
  return Paths.getTextFromFile(path);
}

function psychDirectoryFileList(folder:String):Array<String>
{
  return Paths.listAllFilesInDirectory(folder);
}

function psychClass(name:String):Dynamic
{
  var short:String = name.split(".").pop();
  switch (short)
  {
    case "ClientPrefs":
      return ClientPrefs;
    case "PlayState":
      return PlayState;
    case "Conductor":
      return Conductor;
    case "GameOverSubstate":
      return GameOverSubstate;
    case "Paths":
      return Paths;
    case "FlxG":
      return FlxG;
    case "CoolUtil":
      return CoolUtil;
    case "Character":
      return Character;
    case "Note":
      return Note;
  }
  return Type.resolveClass(name);
}

function psychClassField(name:String, field:String):String
{
  var short:String = name.split(".").pop();
  if (short != "ClientPrefs") return field;
  var key:String = StringTools.startsWith(field, "data.") ? field.substr(5) : field;
  if (key == "antialiasing") return "globalAntialiasing";
  if (key == "cacheOnGPU") return "gpuCaching";
  return key;
}

function psychGetPropertyFromClass(name:String, field:String, ?allowMaps:Dynamic):Dynamic
{
  var target:Dynamic = psychClass(name);
  if (target == null) return null;
  return psychGetIn(target, psychClassField(name, field));
}

function psychSetPropertyFromClass(name:String, field:String, value:Dynamic, ?allowMaps:Dynamic):Dynamic
{
  var target:Dynamic = psychClass(name);
  if (target == null) return null;
  return psychSetIn(target, psychClassField(name, field), value);
}

function psychCallMethod(path:String, ?args:Dynamic):Dynamic
{
  var parts:Array<String> = path.split(".");
  var name:String = parts.pop();
  var target:Dynamic = parts.length == 0 ? game : psychGetProperty(parts.join("."));
  if (target == null) return null;
  return Reflect.callMethod(target, Reflect.field(target, name), args == null ? [] : args);
}

function psychCallMethodFromClass(name:String, method:String, ?args:Dynamic):Dynamic
{
  var target:Dynamic = psychClass(name);
  if (target == null) return null;
  return Reflect.callMethod(target, Reflect.field(target, method), args == null ? [] : args);
}

function psychCreateInstance(tag:String, name:String, ?args:Dynamic):Bool
{
  var short:String = name.split(".").pop();
  var cls:Dynamic = short == "Character" ? Character : short == "BGSprite" ? BGSprite : short == "HealthIcon" ? HealthIcon : Type.resolveClass(name);
  if (cls == null) return false;
  psychTag(tag, Type.createInstance(cls, args == null ? [] : args));
  return true;
}

function psychAddInstance(tag:String, ?front:Dynamic):Void
{
  psychAddLuaSprite(tag, front == true);
}

function psychAddToGroup(group:String, tag:Dynamic, ?index:Dynamic):Void
{
  var g:Dynamic = psychGetProperty(group);
  var obj:Dynamic = psychResolve(tag);
  if (g == null || obj == null) return;
  if (index == null || index < 0) g.add(obj) else g.insert(Std.int(index), obj);
}

function psychRemoveFromGroup(group:String, ?index:Dynamic, ?tag:Dynamic, ?destroy:Dynamic):Void
{
  var g:Dynamic = psychGetProperty(group);
  if (g == null) return;
  var obj:Dynamic = tag != null ? psychResolve(tag) : psychGroup(group)[Std.int(index == null ? 0 : index)];
  if (obj == null) return;
  g.remove(obj, true);
  if (destroy != false) obj.destroy();
}

function psychSetSpriteShader(obj:Dynamic, shader:String):Bool
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return false;
  sprite.shader = newShader(shader);
  return true;
}

function psychRemoveSpriteShader(obj:Dynamic):Bool
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return false;
  sprite.shader = null;
  return true;
}

function psychInitLuaShader(name:String, ?glslVersion:Dynamic):Bool
{
  return true;
}

function psychShaderOf(obj:Dynamic):Dynamic
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null ? null : sprite.shader;
}

function psychSetShaderFloat(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var shader:Dynamic = psychShaderOf(obj);
  if (shader != null) shader.setFloat(prop, value);
}

function psychSetShaderInt(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var shader:Dynamic = psychShaderOf(obj);
  if (shader != null) shader.setInt(prop, Std.int(value));
}

function psychSetShaderBool(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var shader:Dynamic = psychShaderOf(obj);
  if (shader != null) shader.setBool(prop, value == true);
}

function psychSetShaderFloatArray(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var shader:Dynamic = psychShaderOf(obj);
  if (shader != null) shader.setFloatArray(prop, value);
}

function psychSetShaderIntArray(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var shader:Dynamic = psychShaderOf(obj);
  if (shader != null) shader.setIntArray(prop, value);
}

function psychSetShaderBoolArray(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var shader:Dynamic = psychShaderOf(obj);
  if (shader != null) shader.setBoolArray(prop, value);
}

function psychSetShaderSampler2D(obj:Dynamic, prop:String, image:String):Void
{
  var shader:Dynamic = psychShaderOf(obj);
  var graphic:Dynamic = Paths.image(image);
  if (shader != null && graphic != null) shader.setSampler2D(prop, graphic.bitmap);
}

function psychGetShaderFloat(obj:Dynamic, prop:String):Dynamic
{
  var shader:Dynamic = psychShaderOf(obj);
  return shader == null ? null : shader.getFloat(prop);
}

function psychGetShaderInt(obj:Dynamic, prop:String):Dynamic
{
  var shader:Dynamic = psychShaderOf(obj);
  return shader == null ? null : shader.getInt(prop);
}

function psychGetShaderBool(obj:Dynamic, prop:String):Dynamic
{
  var shader:Dynamic = psychShaderOf(obj);
  return shader == null ? null : shader.getBool(prop);
}

function psychMustHit(gfOnly:Bool):Bool
{
  if (PlayState.SONG == null || PlayState.SONG.notes == null) return false;
  var section:Dynamic = PlayState.SONG.notes[game.curSection];
  if (section == null) return false;
  return gfOnly ? section.gfSection == true : section.mustHitSection == true;
}

function psychAltAnim():Bool
{
  if (PlayState.SONG == null || PlayState.SONG.notes == null) return false;
  var section:Dynamic = PlayState.SONG.notes[game.curSection];
  return section != null && section.altAnim == true;
}
"""
}
