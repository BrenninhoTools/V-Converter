package com.brenninhotools.vconverter.script

object Helpers {
    private class Helper(val deps: List<String>, val code: String)

    private val all = linkedMapOf(
        "luaTrue" to Helper(emptyList(), """
function luaTrue(v:Dynamic):Bool
{
  return v != null && v != false;
}"""),
        "luaIsArray" to Helper(emptyList(), """
function luaIsArray(t:Dynamic):Bool
{
  return t != null && Std.isOfType(t, Array);
}"""),
        "luaIsTable" to Helper(listOf("luaIsArray"), """
function luaIsTable(t:Dynamic):Bool
{
  return t != null && Reflect.getClassNameOf(t) == "Unknown" && Reflect.isObject(t) && !Reflect.isFunction(t);
}"""),
        "luaMod" to Helper(emptyList(), """
function luaMod(a:Dynamic, b:Dynamic):Dynamic
{
  return a - Math.floor(a / b) * b;
}"""),
        "luaGet" to Helper(listOf("luaIsArray", "luaIsTable"), """
function luaGet(t:Dynamic, k:Dynamic):Dynamic
{
  if (t == null || k == null) return null;
  if (luaIsArray(t))
  {
    var list:Array<Dynamic> = t;
    var i:Int = Std.int(Std.parseFloat(Std.string(k))) - 1;
    return (i >= 0 && i < list.length) ? list[i] : null;
  }
  if (luaIsTable(t)) return Reflect.field(t, Std.string(k));
  return Reflect.getProperty(t, Std.string(k));
}"""),
        "luaSet" to Helper(listOf("luaIsArray", "luaIsTable"), """
function luaSet(t:Dynamic, k:Dynamic, v:Dynamic):Void
{
  if (t == null || k == null) return;
  if (luaIsArray(t))
  {
    var list:Array<Dynamic> = t;
    var i:Int = Std.int(Std.parseFloat(Std.string(k))) - 1;
    if (i < 0) return;
    if (v == null && i == list.length - 1)
    {
      list.pop();
      return;
    }
    while (list.length < i) list.push(null);
    if (i == list.length) list.push(v) else list[i] = v;
    return;
  }
  if (luaIsTable(t)) Reflect.setField(t, Std.string(k), v) else Reflect.setProperty(t, Std.string(k), v);
}"""),
        "luaLen" to Helper(listOf("luaIsArray", "luaIsTable"), """
function luaLen(t:Dynamic):Int
{
  if (t == null) return 0;
  if (Std.isOfType(t, String)) return ("" + t).length;
  if (luaIsArray(t))
  {
    var list:Array<Dynamic> = t;
    return list.length;
  }
  if (!luaIsTable(t)) return 0;
  var n:Int = 0;
  while (Reflect.field(t, Std.string(n + 1)) != null) n++;
  return n;
}"""),
        "luaKeys" to Helper(listOf("luaIsArray", "luaIsTable"), """
function luaKeys(t:Dynamic):Array<Dynamic>
{
  var keys:Array<Dynamic> = [];
  if (t == null) return keys;
  if (luaIsArray(t))
  {
    var list:Array<Dynamic> = t;
    for (i in 0...list.length) if (list[i] != null) keys.push(i + 1);
    return keys;
  }
  if (!luaIsTable(t)) return keys;
  for (f in Reflect.fields(t))
  {
    if (Reflect.field(t, f) == null) continue;
    var n:Null<Int> = Std.parseInt(f);
    keys.push(n != null && Std.string(n) == f ? n : f);
  }
  return keys;
}"""),
        "luaPack" to Helper(emptyList(), """
function luaPack(values:Array<Dynamic>):Array<Dynamic>
{
  while (values.length > 0 && values[values.length - 1] == null) values.pop();
  return values;
}"""),
        "luaTableFrom" to Helper(emptyList(), """
function luaTableFrom(positional:Array<Dynamic>, pairs:Array<Dynamic>):Dynamic
{
  var t:Dynamic = {};
  for (i in 0...positional.length) Reflect.setField(t, Std.string(i + 1), positional[i]);
  for (p in pairs) Reflect.setField(t, Std.string(p[0]), p[1]);
  return t;
}"""),
        "luaTrim" to Helper(emptyList(), """
function luaTrim(v:Dynamic):String
{
  var str:String = "" + v;
  var start:Int = 0;
  var end:Int = str.length;
  while (start < end && " \t\r\n".indexOf(str.charAt(start)) >= 0) start++;
  while (end > start && " \t\r\n".indexOf(str.charAt(end - 1)) >= 0) end--;
  return str.substring(start, end);
}"""),
        "luaHex" to Helper(emptyList(), """
function luaHex(value:Dynamic):String
{
  var n:Int = Std.int(value);
  if (n == 0) return "0";
  var digits:String = "0123456789ABCDEF";
  var out:String = "";
  var negative:Bool = n < 0;
  if (negative) n = -n;
  while (n > 0)
  {
    out = digits.charAt(n % 16) + out;
    n = Std.int(n / 16);
  }
  return negative ? "-" + out : out;
}"""),
        "luaToString" to Helper(emptyList(), """
function luaToString(v:Dynamic):String
{
  if (v == null) return "nil";
  return Std.string(v);
}"""),
        "luaToNumber" to Helper(listOf("luaTrim"), """
function luaToNumber(v:Dynamic):Dynamic
{
  if (v == null) return null;
  var f:Float = Std.parseFloat(luaTrim(Std.string(v)));
  return Math.isNaN(f) ? null : f;
}"""),
        "luaType" to Helper(listOf("luaIsArray", "luaIsTable"), """
function luaType(v:Dynamic):String
{
  if (v == null) return "nil";
  if (v == true || v == false) return "boolean";
  if (Reflect.isFunction(v)) return "function";
  if (Std.isOfType(v, String)) return "string";
  if (luaIsArray(v) || luaIsTable(v)) return "table";
  if (!Reflect.isObject(v)) return "number";
  return "userdata";
}"""),
        "luaInvoke" to Helper(emptyList(), """
function luaInvoke(f:Dynamic, args:Array<Dynamic>):Dynamic
{
  if (f == null) return null;
  var a:Array<Dynamic> = args == null ? [] : args;
  switch (a.length)
  {
    case 0: return f();
    case 1: return f(a[0]);
    case 2: return f(a[0], a[1]);
    case 3: return f(a[0], a[1], a[2]);
    case 4: return f(a[0], a[1], a[2], a[3]);
    case 5: return f(a[0], a[1], a[2], a[3], a[4]);
    case 6: return f(a[0], a[1], a[2], a[3], a[4], a[5]);
    case 7: return f(a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
    default: return f(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7]);
  }
}"""),
        "luaPcall" to Helper(listOf("luaInvoke"), """
function luaPcall(f:Dynamic, args:Array<Dynamic>):Bool
{
  try
  {
    luaInvoke(f, args);
    return true;
  }
  catch (e:Dynamic)
  {
    return false;
  }
}"""),
        "luaAssert" to Helper(listOf("luaTrue"), """
function luaAssert(v:Dynamic, message:Dynamic):Dynamic
{
  if (!luaTrue(v)) throw (message == null ? "assertion failed!" : message);
  return v;
}"""),
        "luaInsert" to Helper(listOf("luaLen", "luaSet", "luaIsArray", "luaGet"), """
function luaInsert(t:Dynamic, value:Dynamic, position:Dynamic):Void
{
  if (t == null) return;
  if (position == null)
  {
    luaSet(t, luaLen(t) + 1, value);
    return;
  }
  var pos:Int = Std.int(position);
  if (luaIsArray(t))
  {
    var list:Array<Dynamic> = t;
    list.insert(pos - 1, value);
    return;
  }
  var n:Int = luaLen(t);
  var i:Int = n;
  while (i >= pos)
  {
    luaSet(t, i + 1, luaGet(t, i));
    i--;
  }
  luaSet(t, pos, value);
}"""),
        "luaRemove" to Helper(listOf("luaLen", "luaGet", "luaSet", "luaIsArray"), """
function luaRemove(t:Dynamic, position:Dynamic):Dynamic
{
  if (t == null) return null;
  var n:Int = luaLen(t);
  if (n == 0) return null;
  var pos:Int = position == null ? n : Std.int(position);
  if (luaIsArray(t))
  {
    var list:Array<Dynamic> = t;
    var removed:Array<Dynamic> = list.splice(pos - 1, 1);
    return removed.length > 0 ? removed[0] : null;
  }
  var value:Dynamic = luaGet(t, pos);
  for (i in pos...n) luaSet(t, i, luaGet(t, i + 1));
  Reflect.setField(t, Std.string(n), null);
  return value;
}"""),
        "luaConcatTable" to Helper(listOf("luaLen", "luaGet"), """
function luaConcatTable(t:Dynamic, separator:Dynamic):String
{
  var parts:Array<String> = [];
  for (i in 0...luaLen(t)) parts.push(Std.string(luaGet(t, i + 1)));
  return parts.join(separator == null ? "" : Std.string(separator));
}"""),
        "luaSort" to Helper(listOf("luaTrue", "luaIsArray"), """
function luaSort(t:Dynamic, compare:Dynamic):Void
{
  if (!luaIsArray(t)) return;
  var list:Array<Dynamic> = t;
  if (compare == null)
  {
    list.sort(function(a:Dynamic, b:Dynamic):Int
    {
      return a < b ? -1 : (a > b ? 1 : 0);
    });
  }
  else
  {
    list.sort(function(a:Dynamic, b:Dynamic):Int
    {
      return luaTrue(compare(a, b)) ? -1 : (luaTrue(compare(b, a)) ? 1 : 0);
    });
  }
}"""),
        "luaSub" to Helper(emptyList(), """
function luaSub(s:Dynamic, i:Dynamic, j:Dynamic):String
{
  var str:String = "" + s;
  var len:Int = str.length;
  var a:Int = i == null ? 1 : Std.int(i);
  var b:Int = j == null ? -1 : Std.int(j);
  if (a < 0) a = len + a + 1;
  if (b < 0) b = len + b + 1;
  if (a < 1) a = 1;
  if (b > len) b = len;
  if (a > b) return "";
  return str.substring(a - 1, b);
}"""),
        "luaRep" to Helper(emptyList(), """
function luaRep(s:Dynamic, n:Dynamic, separator:Dynamic):String
{
  var parts:Array<String> = [];
  for (i in 0...Std.int(n)) parts.push("" + s);
  return parts.join(separator == null ? "" : Std.string(separator));
}"""),
        "luaReverse" to Helper(emptyList(), """
function luaReverse(s:Dynamic):String
{
  var chars:Array<String> = ("" + s).split("");
  chars.reverse();
  return chars.join("");
}"""),
        "luaChar" to Helper(emptyList(), """
function luaChar(codes:Array<Dynamic>):String
{
  var out:String = "";
  for (c in codes) out += String.fromCharCode(Std.int(c));
  return out;
}"""),
        "luaFormat" to Helper(listOf("luaToString", "luaHex"), """
function luaFormat(format:Dynamic, args:Array<Dynamic>):String
{
  var fmt:String = "" + format;
  var out:String = "";
  var arg:Int = 0;
  var i:Int = 0;
  while (i < fmt.length)
  {
    var c:String = fmt.charAt(i);
    if (c != "%")
    {
      out += c;
      i++;
      continue;
    }
    i++;
    var spec:String = "";
    while (i < fmt.length && "-+ #0123456789.".indexOf(fmt.charAt(i)) >= 0)
    {
      spec += fmt.charAt(i);
      i++;
    }
    var kind:String = fmt.charAt(i);
    i++;
    if (kind == "%")
    {
      out += "%";
      continue;
    }
    var value:Dynamic = args[arg];
    arg++;
    var left:Bool = spec.indexOf("-") >= 0;
    var zero:Bool = !left && spec.indexOf("0") == 0;
    var flags:String = spec;
    while (flags.length > 0 && "-+ #0".indexOf(flags.charAt(0)) >= 0) flags = flags.substr(1);
    var dot:Int = flags.indexOf(".");
    var precision:Int = dot >= 0 ? Std.parseInt(flags.substr(dot + 1)) : -1;
    var width:Null<Int> = Std.parseInt(dot >= 0 ? flags.substr(0, dot) : flags);
    var text:String = "";
    if (kind == "d" || kind == "i") text = Std.string(Std.int(value));
    else if (kind == "f" || kind == "g" || kind == "e")
    {
      var p:Int = precision < 0 ? 6 : precision;
      var factor:Float = Math.pow(10, p);
      var rounded:Float = Math.round(value * factor) / factor;
      text = Std.string(rounded);
      if (p > 0 && kind == "f")
      {
        if (text.indexOf(".") < 0) text += ".";
        while (text.length - text.indexOf(".") - 1 < p) text += "0";
      }
    }
    else if (kind == "x") text = luaHex(value).toLowerCase();
    else if (kind == "X") text = luaHex(value);
    else if (kind == "c") text = String.fromCharCode(Std.int(value));
    else if (kind == "q") text = "\"" + Std.string(value) + "\"";
    else text = luaToString(value);
    if (width != null && width > text.length)
    {
      var pad:String = zero ? "0" : " ";
      while (text.length < width) text = left ? text + " " : pad + text;
    }
    out += text;
  }
  return out;
}"""),
        "luaFind" to Helper(emptyList(), """
function luaFind(s:Dynamic, pattern:String, init:Dynamic):Dynamic
{
  var str:String = "" + s;
  var start:Int = init == null ? 0 : Std.int(init) - 1;
  if (start < 0) start = str.length + start + 1;
  if (start < 0) start = 0;
  var r:EReg = new EReg(pattern, "");
  if (start > str.length || !r.matchSub(str, start)) return null;
  return r.matchedPos().pos + 1;
}"""),
        "luaMatch" to Helper(emptyList(), """
function luaMatch(s:Dynamic, pattern:String, init:Dynamic):Dynamic
{
  var str:String = "" + s;
  var start:Int = init == null ? 0 : Std.int(init) - 1;
  if (start < 0) start = 0;
  var r:EReg = new EReg(pattern, "");
  if (start > str.length || !r.matchSub(str, start)) return null;
  try
  {
    var group:String = r.matched(1);
    if (group != null) return group;
  }
  catch (e:Dynamic) {}
  return r.matched(0);
}"""),
        "luaGsub" to Helper(emptyList(), """
function luaGsub(s:Dynamic, pattern:String, replacement:Dynamic, limit:Dynamic):String
{
  var str:String = "" + s;
  var r:EReg = new EReg(pattern, "g");
  if (Reflect.isFunction(replacement) || (replacement != null && Reflect.getClassNameOf(replacement) == "Unknown" && Reflect.isObject(replacement)))
  {
    return r.map(str, function(m:EReg):String
    {
      var key:String = m.matched(0);
      try
      {
        var group:String = m.matched(1);
        if (group != null) key = group;
      }
      catch (e:Dynamic) {}
      var result:Dynamic = Reflect.isFunction(replacement) ? replacement(key) : Reflect.field(replacement, key);
      return (result == null || result == false) ? m.matched(0) : Std.string(result);
    });
  }
  return r.replace(str, Std.string(replacement));
}"""),
        "luaGmatch" to Helper(emptyList(), """
function luaGmatch(s:Dynamic, pattern:String):Array<Dynamic>
{
  var str:String = "" + s;
  var r:EReg = new EReg(pattern, "");
  var results:Array<Dynamic> = [];
  var pos:Int = 0;
  while (pos <= str.length && r.matchSub(str, pos))
  {
    var captures:Array<Dynamic> = [];
    var g:Int = 1;
    while (true)
    {
      var value:String = null;
      try
      {
        value = r.matched(g);
      }
      catch (e:Dynamic)
      {
        break;
      }
      if (value == null) break;
      captures.push(value);
      g++;
    }
    if (captures.length == 0) captures.push(r.matched(0));
    results.push(captures);
    var matched:Dynamic = r.matchedPos();
    pos = matched.pos + (matched.len > 0 ? matched.len : 1);
  }
  return results;
}"""),
        "luaPatternToRegex" to Helper(emptyList(), """
function luaPatternToRegex(pattern:Dynamic):String
{
  var p:String = "" + pattern;
  var out:String = "";
  var i:Int = 0;
  while (i < p.length)
  {
    var c:String = p.charAt(i);
    if (c == "%" && i + 1 < p.length)
    {
      var n:String = p.charAt(i + 1);
      if (n == "d") out += "[0-9]";
      else if (n == "a") out += "[A-Za-z]";
      else if (n == "s") out += "\\s";
      else if (n == "w") out += "[A-Za-z0-9]";
      else if (n == "l") out += "[a-z]";
      else if (n == "u") out += "[A-Z]";
      else out += "\\" + n;
      i += 2;
      continue;
    }
    if (c == "-") out += "*?";
    else if (c == "." ) out += "[\\s\\S]";
    else if ("\\|{}/".indexOf(c) >= 0) out += "\\" + c;
    else out += c;
    i++;
  }
  return out;
}"""),
        "luaSelfCall" to Helper(listOf("luaGet", "luaInvoke"), """
function luaSelfCall(target:Dynamic, name:String, args:Array<Dynamic>):Dynamic
{
  var f:Dynamic = luaGet(target, name);
  if (f == null) return null;
  return luaInvoke(f, [target].concat(args));
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

    fun emit(names: Set<String>, line: (String) -> Unit) {
        resolve(names).forEach { name ->
            line("")
            all.getValue(name).code.trim('\n').lines().forEach(line)
        }
    }
}
