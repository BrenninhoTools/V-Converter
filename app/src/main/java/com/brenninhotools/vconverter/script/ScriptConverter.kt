package com.brenninhotools.vconverter.script

import com.brenninhotools.vconverter.chart.PsychEventIds
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.sortedMutableSet
import com.brenninhotools.vconverter.platform.Platform

data class ScriptSource(val path: String, val text: String, val target: ScriptTarget? = null, val skipLines: Set<Int> = emptySet())

data class ScriptFile(val path: String, val content: String)

data class ScriptBundle(val files: List<ScriptFile>, val report: Report, val converted: Int, val noteKinds: Map<String, String> = emptyMap())

object ScriptConverter {
    const val OUTPUT_FOLDER = "scripts/modules"

    private data class Plan(val source: ScriptSource, val target: ScriptTarget, val className: String, val host: ScriptHost, val ownEvent: String?)

    fun detectTarget(path: String): ScriptTarget {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val stem = parts.lastOrNull()?.substringBeforeLast('.') ?: "script"
        val lower = parts.map { it.lowercase() }
        val dataIndex = lower.lastIndexOf("data")
        return when {
            "custom_events" in lower -> ScriptTarget(ScriptScope.EVENT, stem)
            "custom_notetypes" in lower -> ScriptTarget(ScriptScope.NOTE_TYPE, stem)
            "stages" in lower -> ScriptTarget(ScriptScope.STAGE, stem)
            dataIndex >= 0 && dataIndex + 3 < parts.size && lower[dataIndex + 1] == "songs" -> ScriptTarget(ScriptScope.SONG, parts[dataIndex + 2].lowercase())
            dataIndex >= 0 && dataIndex + 2 < parts.size -> ScriptTarget(ScriptScope.SONG, parts[dataIndex + 1].lowercase())
            else -> ScriptTarget(ScriptScope.GLOBAL)
        }
    }

    fun pascal(text: String): String {
        val joined = text.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        return when {
            joined.isEmpty() -> "Script"
            joined.first().isDigit() -> "S$joined"
            else -> joined
        }
    }

    private val takenNames = setOf(
        "Module", "ModuleHandler", "PlayState", "FlxG", "FlxSprite", "FlxText", "FlxTween", "FlxEase", "FlxTimer", "Conductor", "Paths",
        "Preferences", "Highscore", "PlayerSettings", "PlayStatePlaylist", "FunkinSound", "StringMap", "SongEvent", "SongEventSchema", "Song", "Stage", "NoteKind",
        "Std", "Math", "String", "Array", "Reflect", "Type", "Assets", "Constants"
    )

    fun keepName(stem: String): String {
        val cleaned = stem.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "Script" }
        val name = (if (cleaned.first().isDigit()) "S$cleaned" else cleaned).replaceFirstChar { it.uppercaseChar() }
        return if (name in takenNames) name + "Script" else name
    }

    fun keepFileName(stem: String): String = stem.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "script" }

    fun kebab(className: String): String =
        className.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2").lowercase()

    fun slug(text: String): String {
        val cleaned = text.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return cleaned.ifEmpty { "Mod" }.let { if (it.first().isDigit()) "M$it" else it }
    }

    fun eventClass(name: String) = PsychEventIds.className(name)

    fun eventId(name: String) = PsychEventIds.idFor(name)

    fun builtinEvents(): Set<String> = PsychNativeLibrary.builtinEventNames

    fun eventFiles(names: Collection<String>, scripted: Collection<String> = emptyList()): List<ScriptFile> =
        names.filter { it in PsychNativeLibrary.builtinEventNames && scripted.none { s -> eventId(s) == eventId(it) } }.distinct().sorted().map { name ->
            ScriptFile("scripts/events/${eventClass(name)}.hxc", HxImports.complete(eventSource(name)))
        }

    private fun eventSource(name: String): String {
        val body = PsychNativeLibrary.library.builtinEvents.getValue(name).lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { "      " + it.removePrefix("          ") }
        val id = LuaToHScript.hxString(eventId(name))
        val title = LuaToHScript.hxString(name)
        val draft = """
class ${eventClass(name)} extends SongEvent
{
  public function new()
  {
    super($id);
  }

  override public function handleEvent(data:Dynamic):Void
  {
    if (data == null || !ensureGame()) return;
    var raw:Dynamic = data.value;
    var value1:Dynamic = raw == null ? "" : Reflect.field(raw, "value1");
    var value2:Dynamic = raw == null ? "" : Reflect.field(raw, "value2");
    if (value1 == null) value1 = "";
    if (value2 == null) value2 = "";
    try
    {
$body
    }
    catch (e:Dynamic) {}
  }

  override public function getTitle():String
  {
    return $title;
  }

  override public function getEventSchema():Dynamic
  {
    return new SongEventSchema([
      {name: "value1", title: "Value 1", type: "string", defaultValue: ""},
      {name: "value2", title: "Value 2", type: "string", defaultValue: ""}
    ]);
  }
""".trimStart('\n')
        val embedded = PsychNativeLibrary.embed(draft, "[]", emptyList())
        val stubs = StringBuilder()
        val all = draft + embedded.members
        if (Regex("\\bpsychCallback\\(").containsMatchIn(all)) stubs.append("\n  function psychCallback(name:String, args:Array<Dynamic>):Dynamic\n  {\n    return null;\n  }\n")
        if (Regex("\\bpsychClose\\(").containsMatchIn(all)) stubs.append("\n  function psychClose():Void {}\n")
        val imports = (listOf("funkin.data.event.SongEventSchema", "funkin.play.event.SongEvent") + embedded.imports).distinct().sorted()
        return Naturalizer.module(imports.joinToString("") { "import $it;\n" } + "\n" + embedded.moduleText + "\n" + embedded.members + "\n" + stubs + "}\n")
    }

    fun noteTypeMap(names: Collection<String>): Map<String, String> =
        names.filter { it.isNotBlank() }.associateBy { PsychToVSliceChart.noteKindFor(it) }

    private fun callbacksIn(text: String): Set<String> =
        Regex("function\\s+([A-Za-z_][A-Za-z0-9_]*)").findAll(text).map { it.groupValues[1] }.filter { it in PsychApi.callbacks }.toSet()

    private fun fitsNoteKind(text: String): Boolean =
        callbacksIn(text).all { it in ScriptBases.noteKindCallbacks } && !Regex("\\b(mustHitSection|gfSection)\\b").containsMatchIn(text)

    fun convert(
        modSlug: String,
        sources: List<ScriptSource>,
        noteTypes: Map<String, String>,
        chartEvents: Collection<String> = emptyList(),
        noteStyles: Map<String, String> = emptyMap()
    ): ScriptBundle {
        val report = ReportBuilder()
        val files = mutableListOf<ScriptFile>()
        val usedNames = HashSet<String>()
        val claimed = HashSet<String>()
        val fallbacks = mutableListOf<String>()
        var converted = 0
        var hooks = 0
        val apiUsed = sortedMutableSet<String>()
        val prefix = pascal(modSlug)

        fun unique(base: String): String {
            var name = if (base in ScriptBases.baseGameClasses || base in takenNames) prefix + base else base
            val root = name
            var n = 2
            while (!usedNames.add(name.lowercase())) name = root + n++
            return name
        }

        val planned = sources.map { source ->
            val target = source.target ?: detectTarget(source.path)
            val stem = source.path.substringAfterLast('/').substringBeforeLast('.')
            val ext = source.path.substringAfterLast('.', "").lowercase()
            val key = target.key
            fun module(name: String, why: String?): Plan {
                val className = unique(name)
                if (why != null) fallbacks += "${source.path} → $className (Module): $why"
                return Plan(source, target, className, ScriptHost(ScriptBase.MODULE, kebab(className)), null)
            }
            fun extra(): String = pascal(stem).removePrefix(pascal(key)).ifEmpty { pascal(ext) }
            when (target.scope) {
                ScriptScope.GLOBAL -> {
                    val className = unique(keepName(stem))
                    Plan(source, target, className, ScriptHost(ScriptBase.MODULE, if (className == keepName(stem)) stem else kebab(className)), null)
                }
                ScriptScope.SONG -> when {
                    ScriptBases.isBaseGameId(ScriptBase.SONG, key) -> module(pascal(key) + extra() + "Module", "the base game already has a Song script for \"$key\".")
                    !claimed.add("song:$key") -> module(pascal(key) + extra() + "Module", "song \"$key\" already has a Song script; V-Slice allows one per song.")
                    else -> Plan(source, target, unique(ScriptBases.classFor(ScriptBase.SONG, key, ::pascal)), ScriptHost(ScriptBase.SONG, key), null)
                }
                ScriptScope.STAGE -> when {
                    ScriptBases.isBaseGameId(ScriptBase.STAGE, key) -> module(pascal(key) + extra() + "Module", "the base game already has a Stage script for \"$key\".")
                    !claimed.add("stage:$key") -> module(pascal(key) + extra() + "Module", "stage \"$key\" already has a Stage script; V-Slice allows one per stage.")
                    else -> Plan(source, target, unique(ScriptBases.classFor(ScriptBase.STAGE, key, ::pascal)), ScriptHost(ScriptBase.STAGE, key), null)
                }
                ScriptScope.NOTE_TYPE -> {
                    val kind = PsychToVSliceChart.noteKindFor(key)
                    when {
                        ScriptBases.isBaseGameId(ScriptBase.NOTE_KIND, kind) -> module(pascal(key) + "NoteTypeModule", "\"$kind\" is a base game note kind.")
                        !fitsNoteKind(source.text) -> module(pascal(key) + "NoteTypeModule", "it uses callbacks a NoteKind never receives (only update, note and create/destroy events reach it).")
                        !claimed.add("notekind:$kind") -> module(pascal(key) + extra() + "NoteTypeModule", "note kind \"$kind\" already has a NoteKind script.")
                        else -> Plan(source, target, unique(ScriptBases.classFor(ScriptBase.NOTE_KIND, key, ::pascal)), ScriptHost(ScriptBase.NOTE_KIND, kind, key, noteStyles[kind]), null)
                    }
                }
                ScriptScope.EVENT -> {
                    val id = eventId(key)
                    if (key.isBlank() || !claimed.add("event:$id")) module(eventClass(key.ifBlank { stem }) + "Module", if (key.isBlank()) null else "event \"$id\" already has a SongEvent script.")
                    else Plan(source, target, unique(eventClass(key)), ScriptHost(ScriptBase.SONG_EVENT, id, key), id)
                }
            }
        }
        val siblings = planned.map { it.host.ref }
        val eventNames = (builtinEvents() + chartEvents + planned.filter { it.target.scope == ScriptScope.EVENT }.map { it.target.key })
            .filter { it.isNotBlank() }.associateBy { eventId(it) }
        val noteTypeArray = noteTypes.entries.joinToString(", ", "[", "]") { (kind, name) ->
            "[${LuaToHScript.hxString(kind)}, ${LuaToHScript.hxString(name)}]"
        }
        val noteKinds = LinkedHashMap<String, String>()

        planned.forEach { plan ->
            val (source, target, className, host, ownEvent) = plan
            val ext = source.path.substringAfterLast('.', "").lowercase()
            val generated = try {
                if (ext == "lua") {
                    LuaToHScript(className, host.id, target, siblings, noteTypeArray, eventNames, ownEvent, source.skipLines, host).convert(source.text)
                } else {
                    PsychHScriptWrapper(className, host.id, target, siblings, noteTypeArray, eventNames, ownEvent, host = host).convert(source.text)
                }
            } catch (e: Exception) {
                report.warn("${source.path}: could not be converted (${e.message}).")
                return@forEach
            }
            if (generated.source.isEmpty()) {
                report.warn("${source.path}: ${generated.issues.firstOrNull()?.message ?: "could not be converted"}")
                return@forEach
            }
            val fileName = if (target.scope == ScriptScope.GLOBAL) keepFileName(source.path.substringAfterLast('/').substringBeforeLast('.')) else className
            val path = "${host.base.folder}/$fileName.hxc"
            files += ScriptFile(path, HxImports.complete(generated.source))
            if (host.base == ScriptBase.NOTE_KIND) noteKinds[host.id] = className
            val label = "${source.path} · ${target.scope.label}${if (target.key.isNotEmpty()) " \"${target.key}\"" else ""} → $path (${host.base.className} \"${host.id}\")"
            converted++
            hooks += generated.callbacks.size
            apiUsed += generated.apiCalls
            val lines = mutableListOf("Callbacks: ${generated.callbacks.joinToString().ifEmpty { "none" }}")
            generated.issues.take(40).forEach { lines += if (it.line > 0) "Line ${it.line}: ${it.message}" else it.message }
            if (generated.issues.size > 40) lines += "…and ${generated.issues.size - 40} more notes"
            report.section(label, lines)
        }
        if (fallbacks.isNotEmpty()) report.section("Scripts kept as Modules", fallbacks)

        report.stat("Scripts", converted)
        report.stat("Callbacks", hooks)
        report.stat("API calls", apiUsed.size)
        val unsupported = apiUsed.filter { it in PsychApi.notImplemented }
        if (unsupported.isNotEmpty()) {
            report.warn("These Psych functions have no V-Slice equivalent and do nothing: ${unsupported.joinToString()}.")
        }
        report.section(
            "How it runs",
            listOf(
                "Each script became the V-Slice class made for its job: data/<song>/ scripts are Songs (scripts/songs), stages/ scripts are Stages (scripts/stages), custom_notetypes are NoteKinds (scripts/notekinds), custom_events are SongEvents (scripts/events) and global scripts are Modules (scripts/modules).",
                "A script only becomes a Module when its class can't carry it (a second script for the same song or stage, a note type that needs more than note events, or an id the base game already scripts); that Module only runs in its song, stage, event or note type.",
                "The Psych functions a script uses are implemented inside its own class with V-Slice and Flixel code; no extra runtime is needed.",
                "Psych events get V-Slice ids (\"Flash Camera\" → \"FlashCamera\") in charts and scripts alike; your scripts still see the original names in onEvent."
            )
        )
        return ScriptBundle(files, report.build(), converted, noteKinds)
    }

    private fun resource(name: String): String =
        Platform.resourceBytes("vconverter/$name")?.decodeToString()
            ?: throw IllegalStateException("Missing bundled template $name")
}
