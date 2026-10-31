package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.chart.PsychEventIds
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.script.HxImports
import com.brenninhotools.vconverter.script.LuaToHScript
import com.brenninhotools.vconverter.script.ScriptFile
import org.json.JSONArray
import org.json.JSONObject

enum class ParamType(val schema: String) { STRING("string"), INTEGER("integer"), FLOAT("float"), BOOL("bool"), ENUM("enum") }

data class EventParam(val key: String, val title: String, val type: ParamType, val default: Any?, val options: List<String> = emptyList())

data class EventDefinition(val name: String, val params: List<EventParam>) {
    val id: String get() = CodenameEvents.eventId(name)
    val className: String get() = CodenameEvents.className(name)
}

object CodenameEvents {
    val easeNames = listOf("CLASSIC", "linear", "back", "bounce", "circ", "cube", "elastic", "expo", "quad", "quart", "quint", "sine", "smoothStep", "smootherStep")

    val nativeConversions = setOf(
        "Camera Movement", "Camera Position", "Play Animation", "Scroll Speed Change", "Camera Modulo Change",
        "BPM Change", "Time Signature Change", "Continuous BPM Change"
    )

    val implemented = setOf("Camera Flash", "Add Camera Zoom", "Camera Bop", "HScript Call", "Camera Zoom")

    val builtins: Map<String, EventDefinition> = listOf(
        EventDefinition("HScript Call", listOf(
            EventParam("function", "Function Name", ParamType.STRING, "myFunc"),
            EventParam("parameters", "Function Parameters (split with commas)", ParamType.STRING, "")
        )),
        EventDefinition("Add Camera Zoom", listOf(
            EventParam("amount", "Amount", ParamType.FLOAT, 0.05),
            EventParam("camera", "Camera", ParamType.ENUM, "camGame", listOf("camGame", "camHUD"))
        )),
        EventDefinition("Camera Bop", listOf(
            EventParam("amount", "Amount", ParamType.FLOAT, 0.1)
        )),
        EventDefinition("Camera Zoom", listOf(
            EventParam("tween", "Tween Zoom?", ParamType.BOOL, true),
            EventParam("zoom", "New Zoom", ParamType.FLOAT, 1.0),
            EventParam("camera", "Camera", ParamType.ENUM, "camGame", listOf("camGame", "camHUD")),
            EventParam("duration", "Tween Time (Steps)", ParamType.FLOAT, 4.0),
            EventParam("ease", "Tween Ease", ParamType.ENUM, "linear", easeNames),
            EventParam("easeDir", "Tween Type", ParamType.ENUM, "In", listOf("In", "Out", "InOut")),
            EventParam("mode", "Mode", ParamType.ENUM, "direct", listOf("direct", "stage")),
            EventParam("multiplicative", "Multiplicative?", ParamType.BOOL, false)
        )),
        EventDefinition("Camera Flash", listOf(
            EventParam("reversed", "Reversed?", ParamType.BOOL, false),
            EventParam("color", "Color", ParamType.STRING, "#FFFFFF"),
            EventParam("duration", "Time (Steps)", ParamType.FLOAT, 4.0),
            EventParam("camera", "Camera", ParamType.ENUM, "camHUD", listOf("camGame", "camHUD"))
        )),
        EventDefinition("Alt Animation Toggle", listOf(
            EventParam("singAlt", "Enable On Sing Poses", ParamType.BOOL, true),
            EventParam("idleAlt", "Enable On Idle", ParamType.BOOL, true),
            EventParam("strumLine", "Strumline", ParamType.INTEGER, 0)
        )),
        EventDefinition("Continuous BPM Change", listOf(
            EventParam("bpm", "Target BPM", ParamType.FLOAT, 100.0),
            EventParam("duration", "Time (steps)", ParamType.FLOAT, 4.0)
        ))
    ).associateBy { it.name }

    fun eventId(name: String): String {
        val pascal = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            .joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            .ifEmpty { "CodenameEvent" }
        val id = if (pascal.first().isDigit()) "Event$pascal" else pascal
        return if (id in PsychEventIds.vsliceEvents) "Codename$id" else id
    }

    fun className(name: String): String = eventId(name).let { if (it.endsWith("Event")) it else it + "Event" }

    fun parseDefinition(name: String, raw: String): EventDefinition {
        val root = runCatching { JSONObject(raw.trim().removePrefix("﻿")) }.getOrNull() ?: return EventDefinition(name, emptyList())
        val params = root.value("params") as? JSONArray ?: return EventDefinition(name, emptyList())
        val used = HashSet<String>()
        val list = (0 until params.length()).mapNotNull { i ->
            val p = params.value(i) as? JSONObject ?: return@mapNotNull null
            val title = p.value("name").asText().ifBlank { "Value ${i + 1}" }
            var key = keyFor(title, i)
            while (!used.add(key)) key += "_"
            val (type, options) = typeOf(p.value("type").asText())
            EventParam(key, title, type, p.value("defaultValue") ?: defaultFor(type, options), options)
        }
        return EventDefinition(name, list)
    }

    fun definitionFor(name: String, custom: Map<String, EventDefinition>, paramCount: Int): EventDefinition {
        custom[name]?.let { return it }
        builtins[name]?.let { return it }
        return EventDefinition(name, (0 until paramCount).map { EventParam("value${it + 1}", "Value ${it + 1}", ParamType.STRING, "") })
    }

    fun valueFor(definition: EventDefinition, params: List<Any?>): LinkedHashMap<String, Any> {
        val value = LinkedHashMap<String, Any>()
        val count = maxOf(params.size, definition.params.size)
        for (i in 0 until count) {
            val param = definition.params.getOrNull(i)
            val key = param?.key ?: "value${i + 1}"
            val raw = params.getOrNull(i) ?: param?.default ?: continue
            value[key] = if (raw is Number) raw.toDouble() else raw
        }
        return value
    }

    private fun keyFor(title: String, index: Int): String {
        val words = title.replace(Regex("\\(.*?\\)"), " ").split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "value${index + 1}"
        val key = words.mapIndexed { i, w -> if (i == 0) w.lowercase() else w.lowercase().replaceFirstChar { it.uppercaseChar() } }.joinToString("")
        return if (key.first().isDigit()) "v$key" else key
    }

    private fun typeOf(raw: String): Pair<ParamType, List<String>> {
        val text = raw.trim()
        val lower = text.lowercase()
        return when {
            lower.startsWith("bool") -> ParamType.BOOL to emptyList()
            lower.startsWith("int") || lower.startsWith("strumline") -> ParamType.INTEGER to emptyList()
            lower.startsWith("float") -> ParamType.FLOAT to emptyList()
            lower.startsWith("dropdown") -> ParamType.ENUM to Regex("['\"]([^'\"]*)['\"]").findAll(text).map { it.groupValues[1] }.toList()
            else -> ParamType.STRING to emptyList()
        }
    }

    private fun defaultFor(type: ParamType, options: List<String>): Any = when (type) {
        ParamType.BOOL -> false
        ParamType.INTEGER -> 0
        ParamType.FLOAT -> 0.0
        ParamType.ENUM -> options.firstOrNull() ?: ""
        ParamType.STRING -> ""
    }

    fun songEventFile(definition: EventDefinition, scriptRefs: List<String>): ScriptFile {
        val body = when (definition.name) {
            "Camera Flash" -> CAMERA_FLASH
            "Add Camera Zoom" -> ADD_CAMERA_ZOOM
            "Camera Bop" -> CAMERA_BOP
            "Camera Zoom" -> CAMERA_ZOOM
            "HScript Call" -> HSCRIPT_CALL.replace("@@MODULES@@", scriptRefs.joinToString(", ") { LuaToHScript.hxString(it) })
            else -> ""
        }
        val helpers = if (definition.name == "Camera Zoom") EASE_HELPER else ""
        val schema = definition.params.joinToString(",\n") { "      " + schemaField(it) }
        val text = buildString {
            appendLine("import funkin.Conductor;")
            appendLine("import funkin.data.event.SongEventSchema;")
            appendLine("import funkin.modding.module.ModuleHandler;")
            appendLine("import funkin.play.PlayState;")
            appendLine("import funkin.play.event.SongEvent;")
            appendLine()
            appendLine("class ${definition.className} extends SongEvent")
            appendLine("{")
            appendLine("  public function new()")
            appendLine("  {")
            appendLine("    super(${LuaToHScript.hxString(definition.id)});")
            appendLine("  }")
            appendLine()
            appendLine("  override public function handleEvent(data:Dynamic):Void")
            appendLine("  {")
            appendLine("    var game:Dynamic = PlayState.instance;")
            appendLine("    if (data == null || game == null) return;")
            appendLine("    var v:Dynamic = data.value;")
            if (body.isNotEmpty()) {
                appendLine("    try")
                appendLine("    {")
                body.trim().lines().forEach { appendLine("      $it") }
                appendLine("    }")
                appendLine("    catch (e:Dynamic) {}")
            }
            appendLine("  }")
            if (body.contains("number(")) append(NUMBER_HELPER)
            append(helpers)
            appendLine()
            appendLine("  override public function getTitle():String")
            appendLine("  {")
            appendLine("    return ${LuaToHScript.hxString(definition.name)};")
            appendLine("  }")
            appendLine()
            appendLine("  override public function getEventSchema():Dynamic")
            appendLine("  {")
            if (definition.params.isEmpty()) {
                appendLine("    return new SongEventSchema([]);")
            } else {
                appendLine("    return new SongEventSchema([")
                appendLine(schema)
                appendLine("    ]);")
            }
            appendLine("  }")
            appendLine("}")
        }
        return ScriptFile("scripts/events/${definition.className}.hxc", HxImports.complete(text))
    }

    private fun schemaField(p: EventParam): String {
        val parts = mutableListOf(
            "name: ${LuaToHScript.hxString(p.key)}",
            "title: ${LuaToHScript.hxString(p.title)}",
            "type: \"${p.type.schema}\"",
            "defaultValue: ${literal(p.default, p.type)}"
        )
        if (p.type == ParamType.ENUM && p.options.isNotEmpty()) {
            parts += "keys: [" + p.options.joinToString(", ") { "${LuaToHScript.hxString(it)} => ${LuaToHScript.hxString(it)}" } + "]"
        }
        return "{" + parts.joinToString(", ") + "}"
    }

    private fun literal(value: Any?, type: ParamType): String = when (type) {
        ParamType.BOOL -> if (value == true || value?.toString()?.lowercase() == "true") "true" else "false"
        ParamType.INTEGER -> (value?.toString()?.toDoubleOrNull() ?: 0.0).toInt().toString()
        ParamType.FLOAT -> (value?.toString()?.toDoubleOrNull() ?: 0.0).let { if (it % 1.0 == 0.0) "${it.toLong()}.0" else it.toString() }
        else -> LuaToHScript.hxString(value?.toString() ?: "")
    }

    fun altNoteKindFile(): ScriptFile = ScriptFile(
        "scripts/notekinds/AltAnimNoteKind.hxc",
        """
import funkin.play.notes.notekind.NoteKind;

class AltAnimNoteKind extends NoteKind
{
  public function new()
  {
    super("alt", "Alt Animation", null, [], false, "alt");
  }
}
""".trimStart('\n')
    )

    private const val NUMBER_HELPER = """
  function number(value:Dynamic, fallback:Float):Float
  {
    if (value == null) return fallback;
    var parsed:Float = Std.parseFloat(Std.string(value));
    return Math.isNaN(parsed) ? fallback : parsed;
  }
"""

    private const val EASE_HELPER = """
  function easeFor(name:Dynamic, dir:Dynamic):Dynamic
  {
    var base:String = name == null ? "linear" : Std.string(name);
    if (base == "linear" || base == "CLASSIC") return FlxEase.linear;
    var full:Dynamic = Reflect.field(FlxEase, base + (dir == null ? "" : Std.string(dir)));
    if (full == null) full = Reflect.field(FlxEase, base + "InOut");
    return full == null ? FlxEase.linear : full;
  }
"""

    private const val CAMERA_FLASH = """
var useGame:Bool = v != null && Reflect.field(v, "camera") == "camGame";
var cam:Dynamic = useGame ? game.camGame : game.camHUD;
var rawColor:Dynamic = v == null ? null : Reflect.field(v, "color");
var color:Dynamic = rawColor == null ? null : FlxColor.fromString(Std.string(rawColor));
if (color == null) color = FlxColor.WHITE;
var time:Float = number(v == null ? null : Reflect.field(v, "duration"), 4) * Conductor.instance.stepLengthMs / 1000;
if (v != null && Reflect.field(v, "reversed") == true) cam.fade(color, time, false, null, true);
else cam.flash(color, time, null, true);
"""

    private const val ADD_CAMERA_ZOOM = """
var amount:Float = number(v == null ? null : Reflect.field(v, "amount"), 0.05);
if (v != null && Reflect.field(v, "camera") == "camHUD") game.camHUD.zoom += amount;
else game.cameraBopMultiplier += amount / (game.currentCameraZoom == 0 ? 1 : game.currentCameraZoom);
"""

    private const val CAMERA_BOP = """
if (!Preferences.zoomCamera) return;
var amount:Float = number(v == null ? null : Reflect.field(v, "amount"), 0.1);
game.cameraBopMultiplier += amount / (game.currentCameraZoom == 0 ? 1 : game.currentCameraZoom);
game.camHUD.zoom += amount;
"""

    private const val CAMERA_ZOOM = """
var hud:Bool = v != null && Reflect.field(v, "camera") == "camHUD";
var zoom:Float = number(v == null ? null : Reflect.field(v, "zoom"), 1);
var direct:Bool = v == null || Reflect.field(v, "mode") != "stage";
var ease:Dynamic = v == null ? null : Reflect.field(v, "ease");
var instant:Bool = (v != null && Reflect.field(v, "tween") == false) || ease == "CLASSIC";
var time:Float = instant ? 0 : number(v == null ? null : Reflect.field(v, "duration"), 4) * Conductor.instance.stepLengthMs / 1000;
var multiplicative:Bool = v != null && Reflect.field(v, "multiplicative") == true;
if (!hud)
{
  var base:Float = direct ? 1 : game.stageZoom;
  if (multiplicative) zoom *= game.currentCameraZoom / (base == 0 ? 1 : base);
  game.tweenCameraZoom(zoom, time, direct, easeFor(ease, Reflect.field(v, "easeDir")));
  return;
}
if (!direct) zoom *= game.stageZoom;
if (multiplicative) zoom *= game.defaultHUDCameraZoom;
if (instant)
{
  game.defaultHUDCameraZoom = zoom;
  if (ease != "CLASSIC") game.camHUD.zoom = zoom;
  return;
}
FlxTween.num(game.defaultHUDCameraZoom, zoom, time, {ease: easeFor(ease, Reflect.field(v, "easeDir"))}, function(value:Float) {
  game.defaultHUDCameraZoom = value;
});
"""

    private const val HSCRIPT_CALL = """
var name:String = v == null ? "" : Std.string(Reflect.field(v, "function"));
if (name == "" || name == "null") return;
var raw:Dynamic = v == null ? null : Reflect.field(v, "parameters");
var args:Array<Dynamic> = raw == null || Std.string(raw) == "" ? [] : Std.string(raw).split(",");
for (ref in [@@MODULES@@])
{
  var split:Int = ref.indexOf(":");
  var kind:String = split < 0 ? "module" : ref.substr(0, split);
  var id:String = split < 0 ? ref : ref.substr(split + 1);
  var target:Dynamic = null;
  if (kind == "song") target = game.currentSong != null && game.currentSong.id == id ? game.currentSong : null;
  else if (kind == "stage") target = game.currentStage != null && game.currentStageId == id ? game.currentStage : null;
  else if (kind == "notekind") target = NoteKindManager.getNoteKind(id);
  else if (kind == "event") target = SongEventRegistry.getEvent(id);
  else target = ModuleHandler.getModule(id);
  if (target == null) continue;
  try
  {
    target.scriptCall("hscriptCall", [name, args]);
  }
  catch (e:Dynamic) {}
}
"""
}
