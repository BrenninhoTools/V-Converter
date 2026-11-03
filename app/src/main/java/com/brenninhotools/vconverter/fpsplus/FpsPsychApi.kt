package com.brenninhotools.vconverter.fpsplus

object FpsPsychApi {
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
var psychOffsets:StringMap<Dynamic> = new StringMap();
var psychStrumDefaults:Array<Dynamic> = null;

function psychReplace(text:String, from:String, to:String):String
{
  return text.split(from).join(to);
}

function psychTrimText(text:String):String
{
  var start:Int = 0;
  var end:Int = text.length;
  while (start < end && (text.charAt(start) == " " || text.charAt(start) == "\t" || text.charAt(start) == "\n" || text.charAt(start) == "\r")) start++;
  while (end > start && (text.charAt(end - 1) == " " || text.charAt(end - 1) == "\t" || text.charAt(end - 1) == "\n" || text.charAt(end - 1) == "\r")) end--;
  return text.substring(start, end);
}

function psychVars():Dynamic
{
  return playstate.arbitraryData;
}

function psychNoteField(target:Dynamic, name:String):String
{
  if (target != null && Std.isOfType(target, Note))
  {
    switch (name)
    {
      case "noteData":
        return "direction";
      case "noteType":
        return "type";
      case "ignoreNote":
        return "isFake";
    }
  }
  return name;
}

function psychStrums():Array<Dynamic>
{
  var out:Array<Dynamic> = [];
  if (playstate.enemyStrums != null) for (s in playstate.enemyStrums.members) out.push(s);
  if (playstate.playerStrums != null) for (s in playstate.playerStrums.members) out.push(s);
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

function psychObj(name:String):Dynamic
{
  if (name == null) return null;
  if (psychVars().exists(name)) return psychVars().get(name);
  switch (name)
  {
    case "bf", "boyfriend", "boyfriendGroup":
      return boyfriend;
    case "dad", "dadGroup":
      return dad;
    case "gf", "girlfriend", "gfGroup":
      return gf;
    case "camOther":
      return playstate.camOverlay;
    case "camFollowPos":
      return playstate.camFollow;
    case "opponentStrums":
      return playstate.enemyStrums;
    case "strumLineNotes":
      return psychStrums();
    case "songScore":
      return playstate.songStats.score;
    case "songMisses":
      return playstate.songStats.missCount;
    case "game":
      return playstate;
  }
  return ReflectUtil.getProperty(playstate, name);
}

function psychAt(value:Dynamic, key:String):Dynamic
{
  if (value == null) return null;
  var index:Null<Int> = Std.parseInt(key);
  if (index != null)
  {
    var list:Dynamic = Std.isOfType(value, Array) ? value : ReflectUtil.getProperty(value, "members");
    if (list == null) list = value;
    return list[index];
  }
  return ReflectUtil.getProperty(value, key);
}

function psychStep(target:Dynamic, part:String, first:Bool):Dynamic
{
  var bracket:Int = part.indexOf("[");
  var name:String = bracket < 0 ? part : part.substr(0, bracket);
  var value:Dynamic = null;
  if (first) value = psychObj(name);
  else if (name == "length" && Std.isOfType(target, Array)) value = target.length;
  else value = ReflectUtil.getProperty(target, psychNoteField(target, name));
  if (bracket >= 0)
  {
    for (piece in part.substr(bracket + 1).split("["))
      value = psychAt(value, psychReplace(piece, "]", ""));
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
  try
  {
    ReflectUtil.setProperty(holder, psychNoteField(holder, last), value);
  }
  catch (e:Dynamic) {}
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
    if (psychVars().exists(name)) psychVars().set(name, value);
    else if (name == "songScore") playstate.songStats.score = Std.int(value);
    else if (name == "songMisses") playstate.songStats.missCount = Std.int(value);
    else ReflectUtil.setProperty(playstate, name, value);
    return value;
  }
  var last:String = parts.pop();
  var holder:Dynamic = psychGetProperty(parts.join("."));
  if (holder == null) return null;
  var bracket:Int = last.indexOf("[");
  if (bracket >= 0)
  {
    var list:Dynamic = psychStep(holder, last.substr(0, bracket), false);
    var members:Dynamic = Std.isOfType(list, Array) ? list : ReflectUtil.getProperty(list, "members");
    members[Std.parseInt(psychReplace(last.substr(bracket + 1), "]", ""))] = value;
    return value;
  }
  ReflectUtil.setProperty(holder, psychNoteField(holder, last), value);
  return value;
}

function psychGroup(name:String):Dynamic
{
  var g:Dynamic = psychGetProperty(name);
  if (g == null) return null;
  if (Std.isOfType(g, Array)) return g;
  var list:Dynamic = ReflectUtil.getProperty(g, "members");
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
      return playstate.camHUD;
    case "camother", "other":
      return playstate.camOverlay;
  }
  return playstate.camGame;
}

function psychColor(value:Dynamic):Int
{
  if (value == null) return 0xFFFFFFFF;
  if (!Std.isOfType(value, String)) return Std.int(value);
  var text:String = psychTrimText(Std.string(value));
  if (ScriptingUtil.startsWith(text, "#")) text = text.substr(1);
  if (ScriptingUtil.startsWith(text.toLowerCase(), "0x")) text = text.substr(2);
  if (text.length == 6) text = "FF" + text;
  var parsed:Null<Int> = text.length == 8 ? Std.parseInt("0x" + text) : null;
  if (parsed != null) return parsed;
  var named:Null<Int> = ScriptingUtil.colorFromString(Std.string(value));
  return named == null ? 0xFFFFFFFF : named;
}

function psychEase(name:Dynamic):Dynamic
{
  if (name == null) return FlxEase.linear;
  if (!Std.isOfType(name, String)) return name;
  var key:String = Std.string(name).toLowerCase();
  for (field in ReflectUtil.getClassFields(FlxEase))
    if (field.toLowerCase() == key) return ReflectUtil.getField(FlxEase, field);
  return FlxEase.linear;
}

function psychChar(name:Dynamic):Dynamic
{
  var key:String = name == null ? "" : Std.string(name).toLowerCase();
  switch (key)
  {
    case "dad", "opponent", "1":
      return dad;
    case "gf", "girlfriend", "2":
      return gf;
  }
  return boyfriend;
}

function psychTag(tag:String, obj:Dynamic):Dynamic
{
  var old:Dynamic = psychVars().get(tag);
  if (old != null && old != obj)
  {
    try
    {
      removeFromBackground(old);
      removeFromForeground(old);
      removeGeneric(old);
      old.destroy();
    }
    catch (e:Dynamic) {}
  }
  psychVars().set(tag, obj);
  return obj;
}

function psychMakeLuaSprite(tag:String, ?image:String, ?x:Float, ?y:Float):Dynamic
{
  var sprite:FlxSprite = new FlxSprite(x == null ? 0 : x, y == null ? 0 : y);
  if (image != null && image.length > 0) sprite.loadGraphic(Paths.image(image));
  return psychTag(tag, sprite);
}

function psychMakeAnimatedLuaSprite(tag:String, ?image:String, ?x:Float, ?y:Float, ?type:String):Dynamic
{
  var sprite:FlxSprite = new FlxSprite(x == null ? 0 : x, y == null ? 0 : y);
  if (image != null && image.length > 0) psychFrames(sprite, image, type);
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
      var n:Null<Int> = Std.parseInt(psychTrimText(part));
      if (n != null) out.push(n);
    }
    return out;
  }
  var list:Array<Dynamic> = value;
  for (v in list) out.push(Std.int(v));
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
  if (Std.isOfType(sprite, Character))
  {
    sprite.addOffset(anim, x == null ? 0 : x, y == null ? 0 : y);
    return;
  }
  psychOffsets.set(Std.string(obj) + "::" + anim, [x == null ? 0 : x, y == null ? 0 : y]);
}

function psychPlayAnim(obj:Dynamic, name:String, ?forced:Dynamic, ?reverse:Dynamic, ?frame:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  var force:Bool = forced == true;
  var back:Bool = reverse == true;
  var start:Int = frame == null ? 0 : Std.int(frame);
  if (Std.isOfType(sprite, Character))
  {
    sprite.playAnim(name, force, back, start);
    return;
  }
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

function psychScreenCenter(obj:Dynamic, ?axis:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return;
  var key:String = axis == null ? "xy" : Std.string(axis).toLowerCase();
  ScriptingUtil.screenCenter(sprite, key != "y", key != "x");
}

function psychSetObjectCamera(obj:Dynamic, ?camera:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null) sprite.cameras = [psychCamera(camera == null ? "game" : camera)];
}

function psychSetBlendMode(obj:Dynamic, ?blend:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null) sprite.blend = BlendMode.fromString(blend == null ? "normal" : Std.string(blend).toLowerCase());
}

function psychAddLuaSprite(tag:Dynamic, ?front:Bool):Void
{
  var obj:Dynamic = psychResolve(tag);
  if (obj == null) return;
  if (front == true) addToForeground(obj) else addToBackground(obj);
}

function psychRemoveLuaSprite(tag:Dynamic, ?destroy:Dynamic):Void
{
  var obj:Dynamic = psychResolve(tag);
  if (obj == null) return;
  removeFromBackground(obj);
  removeFromForeground(obj);
  removeHud(obj);
  if (destroy != false)
  {
    obj.destroy();
    if (Std.isOfType(tag, String)) psychVars().remove(tag);
  }
}

function psychLuaSpriteExists(tag:String):Bool
{
  return psychVars().exists(tag) && Std.isOfType(psychVars().get(tag), FlxSprite);
}

function psychLuaTextExists(tag:String):Bool
{
  return psychVars().exists(tag) && Std.isOfType(psychVars().get(tag), FlxText);
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

function psychObjectsOverlap(a:Dynamic, b:Dynamic):Bool
{
  var first:Dynamic = psychResolve(a);
  var second:Dynamic = psychResolve(b);
  return first != null && second != null && FlxG.overlap(first, second);
}

function psychMakeLuaText(tag:String, ?text:String, ?width:Dynamic, ?x:Dynamic, ?y:Dynamic):Dynamic
{
  var label:FlxText = new FlxText(x == null ? 0 : x, y == null ? 0 : y, width == null ? 0 : width, text == null ? "" : text, 16);
  label.setFormat(Paths.font("vcr"), 16, 0xFFFFFFFF, "center", FlxTextBorderStyle.OUTLINE, 0xFF000000);
  label.cameras = [playstate.camHUD];
  label.scrollFactor.set();
  label.borderSize = 2;
  return psychTag(tag, label);
}

function psychAddLuaText(tag:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) addToHud(label);
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

function psychSetTextWidth(tag:Dynamic, width:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.fieldWidth = width;
}

function psychSetTextColor(tag:Dynamic, color:Dynamic):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.color = psychColor(color);
}

function psychSetTextFont(tag:Dynamic, font:String):Void
{
  var label:Dynamic = psychResolve(tag);
  if (label != null) label.font = Paths.font(font.split(".")[0], font.indexOf(".") >= 0 ? font.split(".").pop() : "ttf");
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

function psychTweenDone(tag:String, target:Dynamic):Void
{
  psychTweens.remove(tag);
  psychOnTweenCompleted(tag, target);
}

function psychTween(tag:String, target:Dynamic, values:Dynamic, ?duration:Dynamic, ?ease:Dynamic):Dynamic
{
  psychCancelTween(tag);
  var obj:Dynamic = psychResolve(target);
  if (obj == null) return null;
  var tween:Dynamic = FlxTween.tween(obj, values, duration == null ? 1 : duration, {ease: psychEase(ease), onComplete: function(t:Dynamic) { psychTweenDone(tag, target); }});
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
  var tween:Dynamic = FlxTween.color(obj, duration == null ? 1 : duration, obj.color, psychColor(color), {ease: psychEase(ease), onComplete: function(t:Dynamic) { psychTweenDone(tag, target); }});
  if (tag != null && tag != "") psychTweens.set(tag, tween);
  return tween;
}

function psychStartTween(tag:String, target:Dynamic, values:Dynamic, ?duration:Dynamic, ?options:Dynamic):Dynamic
{
  var ease:Dynamic = options == null ? null : ReflectUtil.getAnonymousField(options, "ease");
  return psychTween(tag, target, values, duration, ease);
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

function psychCancelTween(tag:String):Void
{
  if (tag == null) return;
  var tween:Dynamic = psychTweens.get(tag);
  if (tween == null) return;
  tween.cancel();
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
  if (key == "dad" || key == "opponent")
  {
    playstate.camFocusOpponent();
    return true;
  }
  if (key == "gf" || key == "girlfriend") playstate.camFocusGF() else playstate.camFocusBF();
  return false;
}

function psychSetCameraFollowPoint(x:Dynamic, y:Dynamic):Void
{
  playstate.camMove(x, y);
}

function psychGetCameraFollowX():Float
{
  return playstate.camFollow.x;
}

function psychGetCameraFollowY():Float
{
  return playstate.camFollow.y;
}

function psychEventTags(name:String, v1:String, v2:String):Array<String>
{
  var a:String = v1 == null ? "" : psychReplace(Std.string(v1), ";", ",");
  var b:String = v2 == null ? "" : psychReplace(Std.string(v2), ";", ",");
  var who:String = psychTrimText(b).toLowerCase();
  switch (name)
  {
    case "Hey!":
      var target:String = psychTrimText(a).toLowerCase();
      if (target == "bf" || target == "boyfriend" || target == "0") return ["playAnim;bf;hey;true"];
      if (target == "gf" || target == "girlfriend" || target == "1") return ["playAnim;gf;cheer;true"];
      return ["playAnim;bf;hey;true", "playAnim;gf;cheer;true"];
    case "Play Animation":
      var c:String = (who == "bf" || who == "boyfriend" || who == "1") ? "bf" : (who == "gf" || who == "girlfriend" || who == "2") ? "gf" : "dad";
      return ["playAnim;" + c + ";" + psychTrimText(a) + ";true"];
    case "Set GF Speed":
      var speed:Null<Int> = Std.parseInt(psychTrimText(a));
      return ["setBopFreq;gf;" + (speed == null || speed < 1 ? 1 : speed) + ";0"];
    case "Add Camera Zoom":
      return ["camBop"];
    case "Change Scroll Speed":
      return ["scrollSpeedMultiplier;" + (psychTrimText(a) == "" ? "1" : psychTrimText(a)) + ";" + (psychTrimText(b) == "" ? "0" : psychTrimText(b))];
    case "Play Sound":
      return ["playSound;" + psychTrimText(a) + ";" + (psychTrimText(b) == "" ? "1" : psychTrimText(b))];
  }
  return [name + ";" + a + ";" + b];
}

function psychTriggerEvent(name:String, ?value1:Dynamic, ?value2:Dynamic):Bool
{
  for (tag in psychEventTags(name, value1, value2)) playstate.executeEvent(tag);
  return true;
}

function psychCharacterDance(character:Dynamic):Void
{
  var c:Dynamic = psychChar(character);
  if (c != null) c.dance();
}

function psychGetCharacterX(character:Dynamic):Float
{
  return psychChar(character).x;
}

function psychGetCharacterY(character:Dynamic):Float
{
  return psychChar(character).y;
}

function psychSetCharacterX(character:Dynamic, value:Dynamic):Void
{
  psychChar(character).x = value;
}

function psychSetCharacterY(character:Dynamic, value:Dynamic):Void
{
  psychChar(character).y = value;
}

function psychSetHealth(?value:Dynamic):Void
{
  playstate.health = value == null ? 1 : value;
}

function psychAddHealth(?value:Dynamic):Void
{
  playstate.health += value == null ? 0 : value;
}

function psychGetHealth():Float
{
  return playstate.health;
}

function psychSetScore(?value:Dynamic):Void
{
  playstate.songStats.score = Std.int(value == null ? 0 : value);
}

function psychAddScore(?value:Dynamic):Void
{
  playstate.songStats.score += Std.int(value == null ? 0 : value);
}

function psychSetMisses(?value:Dynamic):Void
{
  playstate.songStats.missCount = Std.int(value == null ? 0 : value);
}

function psychAddMisses(?value:Dynamic):Void
{
  playstate.songStats.missCount += Std.int(value == null ? 0 : value);
}

function psychGetSongPosition():Float
{
  return Conductor.songPosition;
}

function psychEndSong():Bool
{
  playstate.endSong();
  return true;
}

function psychRestartSong(?skipTransition:Dynamic):Bool
{
  FlxG.resetState();
  return true;
}

function psychExitSong(?skipTransition:Dynamic):Bool
{
  playstate.returnToMenu();
  return true;
}

function psychStartCountdown():Bool
{
  playstate.startCountdown();
  return true;
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

function psychGetSoundTime(?tag:Dynamic):Float
{
  var sound:Dynamic = psychSound(tag);
  return sound == null ? 0 : sound.time;
}

function psychSetSoundTime(?tag:Dynamic, ?value:Dynamic):Void
{
  var sound:Dynamic = psychSound(tag);
  if (sound != null) sound.time = value == null ? 0 : value;
}

function psychLuaSoundExists(tag:Dynamic):Bool
{
  return tag != null && psychSounds.exists(Std.string(tag));
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

function psychGetRandomInt(?min:Dynamic, ?max:Dynamic, ?exclude:Dynamic):Int
{
  var skip:Array<Int> = [];
  if (exclude != null) for (part in Std.string(exclude).split(","))
  {
    var n:Null<Int> = Std.parseInt(psychTrimText(part));
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
  return ScriptingUtil.startsWith(Std.string(text), Std.string(start));
}

function psychStringEndsWith(text:Dynamic, end:Dynamic):Bool
{
  return ScriptingUtil.endsWith(Std.string(text), Std.string(end));
}

function psychStringSplit(text:Dynamic, split:Dynamic):Array<String>
{
  return Std.string(text).split(Std.string(split));
}

function psychStringTrim(text:Dynamic):String
{
  return psychTrimText(Std.string(text));
}

function psychBind(name:String):String
{
  switch (name == null ? "" : name.toLowerCase())
  {
    case "left":
      return "gameplayLeft";
    case "down":
      return "gameplayDown";
    case "up":
      return "gameplayUp";
    case "right":
      return "gameplayRight";
    case "ui_left":
      return "menuLeft";
    case "ui_down":
      return "menuDown";
    case "ui_up":
      return "menuUp";
    case "ui_right":
      return "menuRight";
    case "accept":
      return "menuAccept";
    case "back":
      return "menuBack";
    case "pause":
      return "pause";
    case "reset":
      return "killbind";
  }
  return null;
}

function psychKeyJustPressed(name:String):Bool
{
  if (name != null && name.toLowerCase() == "space") return FlxG.keys.justPressed.SPACE;
  var bind:String = psychBind(name);
  return bind != null && Binds.justPressed(bind);
}

function psychKeyPressed(name:String):Bool
{
  if (name != null && name.toLowerCase() == "space") return FlxG.keys.pressed.SPACE;
  var bind:String = psychBind(name);
  return bind != null && Binds.pressed(bind);
}

function psychKeyReleased(name:String):Bool
{
  if (name != null && name.toLowerCase() == "space") return FlxG.keys.justReleased.SPACE;
  var bind:String = psychBind(name);
  return bind != null && Binds.justReleased(bind);
}

function psychKeyboardJustPressed(name:String):Bool
{
  return ScriptingUtil.anyJustPressed([Std.string(name).toUpperCase()]);
}

function psychKeyboardPressed(name:String):Bool
{
  return ScriptingUtil.anyPressed([Std.string(name).toUpperCase()]);
}

function psychKeyboardReleased(name:String):Bool
{
  return ScriptingUtil.anyJustReleased([Std.string(name).toUpperCase()]);
}

function psychMouseClicked(?button:Dynamic):Bool
{
  var name:String = button == null ? "left" : Std.string(button).toLowerCase();
  return name == "right" ? FlxG.mouse.justPressedRight : name == "middle" ? FlxG.mouse.justPressedMiddle : FlxG.mouse.justPressed;
}

function psychMousePressed(?button:Dynamic):Bool
{
  var name:String = button == null ? "left" : Std.string(button).toLowerCase();
  return name == "right" ? FlxG.mouse.pressedRight : name == "middle" ? FlxG.mouse.pressedMiddle : FlxG.mouse.pressed;
}

function psychMouseReleased(?button:Dynamic):Bool
{
  var name:String = button == null ? "left" : Std.string(button).toLowerCase();
  return name == "right" ? FlxG.mouse.justReleasedRight : name == "middle" ? FlxG.mouse.justReleasedMiddle : FlxG.mouse.justReleased;
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
  save = ReflectUtil.createInstance(ReflectUtil.resolveClass("flixel.util.FlxSave"), []);
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
  var value:Dynamic = ReflectUtil.getAnonymousField(psychSave(name).data, field);
  return value == null ? fallback : value;
}

function psychSetDataFromSave(name:String, field:String, value:Dynamic):Void
{
  ReflectUtil.setAnonymousField(psychSave(name).data, field, value);
}

function psychFlushSaveData(name:String):Bool
{
  return psychSave(name).flush();
}

function psychEraseSaveData(name:String):Void
{
  psychSave(name).erase();
}

function psychCallOnScripts(name:String, ?args:Dynamic, ?ignoreStops:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Dynamic
{
  var result:Dynamic = null;
  for (s in playstate.scripts)
  {
    try
    {
      var r:Dynamic = s.scriptCall(name, args == null ? [] : args);
      if (r != null) result = r;
    }
    catch (e:Dynamic) {}
  }
  return result;
}

function psychCallOnLuas(name:String, ?args:Dynamic, ?ignoreStops:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Dynamic
{
  return psychCallOnScripts(name, args);
}

function psychCallOnHScript(name:String, ?args:Dynamic, ?ignoreStops:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Dynamic
{
  return psychCallOnScripts(name, args);
}

function psychCallScript(path:String, name:String, ?args:Dynamic):Dynamic
{
  return psychCallOnScripts(name, args);
}

function psychSetOnScripts(name:String, value:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Void
{
  for (s in playstate.scripts)
  {
    try
    {
      s.scriptSet(name, value);
    }
    catch (e:Dynamic) {}
  }
}

function psychSetOnLuas(name:String, value:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Void
{
  psychSetOnScripts(name, value);
}

function psychSetOnHScript(name:String, value:Dynamic, ?ignoreSelf:Dynamic, ?exclude:Dynamic):Void
{
  psychSetOnScripts(name, value);
}

function psychSetVar(name:String, value:Dynamic):Dynamic
{
  psychVars().set(name, value);
  return value;
}

function psychGetVar(name:String):Dynamic
{
  return psychVars().get(name);
}

function psychRemoveVar(name:String):Bool
{
  return psychVars().remove(name);
}

function psychCheckFileExists(path:String, ?absolute:Dynamic):Bool
{
  return Utils.exists(path) || Utils.exists("assets/" + path);
}

function psychGetTextFromFile(path:String, ?ignoreModFolders:Dynamic):String
{
  return Utils.exists(path) ? Utils.getText(path) : Utils.getText("assets/" + path);
}

function psychClass(name:String):Dynamic
{
  var short:String = name.split(".").pop();
  switch (short)
  {
    case "ClientPrefs":
      return Config;
    case "PlayState":
      return PlayState;
    case "Conductor":
      return Conductor;
    case "GameOverSubstate", "GameOverSubState":
      return GameOverSubState;
    case "Paths":
      return Paths;
    case "FlxG":
      return FlxG;
    case "Character":
      return Character;
    case "Note":
      return Note;
  }
  return ReflectUtil.resolveClass(name);
}

function psychClassField(name:String, field:String):String
{
  var short:String = name.split(".").pop();
  if (short == "PlayState" && ScriptingUtil.startsWith(field, "instance.")) return field.substr(9);
  if (short != "ClientPrefs") return field;
  var key:String = ScriptingUtil.startsWith(field, "data.") ? field.substr(5) : field;
  switch (key)
  {
    case "downScroll":
      return "downscroll";
    case "middleScroll":
      return "centeredNotes";
    case "flashing":
      return "flashingLights";
    case "framerate":
      return "framerate";
    case "noteOffset":
      return "offset";
  }
  return key;
}

function psychGetPropertyFromClass(name:String, field:String, ?allowMaps:Dynamic):Dynamic
{
  var target:Dynamic = psychClass(name);
  if (target == null) return null;
  var short:String = name.split(".").pop();
  if (short == "PlayState" && ScriptingUtil.startsWith(field, "instance.")) target = playstate;
  return psychGetIn(target, psychClassField(name, field));
}

function psychSetPropertyFromClass(name:String, field:String, value:Dynamic, ?allowMaps:Dynamic):Dynamic
{
  var target:Dynamic = psychClass(name);
  if (target == null) return null;
  var short:String = name.split(".").pop();
  if (short == "PlayState" && ScriptingUtil.startsWith(field, "instance.")) target = playstate;
  return psychSetIn(target, psychClassField(name, field), value);
}

function psychCallMethod(path:String, ?args:Dynamic):Dynamic
{
  var parts:Array<String> = path.split(".");
  var name:String = parts.pop();
  var target:Dynamic = parts.length == 0 ? playstate : psychGetProperty(parts.join("."));
  if (target == null) return null;
  return ReflectUtil.callMethod(target, name, args == null ? [] : args);
}

function psychCallMethodFromClass(name:String, method:String, ?args:Dynamic):Dynamic
{
  var target:Dynamic = psychClass(name);
  if (target == null) return null;
  return ReflectUtil.callMethod(target, method, args == null ? [] : args);
}

function psychCreateInstance(tag:String, name:String, ?args:Dynamic):Bool
{
  var cls:Dynamic = ReflectUtil.resolveClass(name);
  if (cls == null) return false;
  psychTag(tag, ReflectUtil.createInstance(cls, args == null ? [] : args));
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

function psychSetSpriteShader(obj:Dynamic, shader:String):Bool
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite == null) return false;
  sprite.shader = new RuntimeShader(Utils.getText(Paths.shader(shader)));
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

function psychSetShaderFloat(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null && sprite.shader != null) sprite.shader.setFloat(prop, value);
}

function psychSetShaderInt(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null && sprite.shader != null) sprite.shader.setInt(prop, Std.int(value));
}

function psychSetShaderBool(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null && sprite.shader != null) sprite.shader.setBool(prop, value == true);
}

function psychSetShaderFloatArray(obj:Dynamic, prop:String, value:Dynamic):Void
{
  var sprite:Dynamic = psychResolve(obj);
  if (sprite != null && sprite.shader != null) sprite.shader.setFloatArray(prop, value);
}

function psychGetShaderFloat(obj:Dynamic, prop:String):Dynamic
{
  var sprite:Dynamic = psychResolve(obj);
  return sprite == null || sprite.shader == null ? null : sprite.shader.getFloat(prop);
}

function psychMustHit(gfOnly:Bool):Bool
{
  return gfOnly ? playstate.camFocus == "gf" : playstate.camFocus == "bf";
}
"""
}
