package com.brenninhotools.vconverter.generator

import com.brenninhotools.vconverter.core.ReportBuilder

enum class ScriptKind(val label: String, val folder: String, val targetLabel: String) {
    MODULE("Module", "modules", "Module id"),
    SONG("Song", "songs", "Song id"),
    STAGE("Stage", "stages", "Stage id"),
    CHARACTER("Character", "characters", "Character id"),
    EVENT("Song event", "events", "Event id"),
    NOTE_KIND("Note kind", "notekinds", "Note kind id")
}

enum class Callback(val method: String, val eventType: String, val label: String) {
    CREATE("onCreate", "ScriptEvent", "Create"),
    UPDATE("onUpdate", "UpdateScriptEvent", "Update"),
    BEAT_HIT("onBeatHit", "SongTimeScriptEvent", "Beat hit"),
    STEP_HIT("onStepHit", "SongTimeScriptEvent", "Step hit"),
    COUNTDOWN_START("onCountdownStart", "CountdownScriptEvent", "Countdown"),
    SONG_START("onSongStart", "ScriptEvent", "Song start"),
    SONG_END("onSongEnd", "ScriptEvent", "Song end"),
    NOTE_INCOMING("onNoteIncoming", "NoteScriptEvent", "Note spawn"),
    NOTE_HIT("onNoteHit", "HitNoteScriptEvent", "Note hit"),
    NOTE_MISS("onNoteMiss", "NoteScriptEvent", "Note miss"),
    SONG_EVENT("onSongEvent", "SongEventScriptEvent", "Song event"),
    PAUSE("onPause", "PauseScriptEvent", "Pause"),
    GAME_OVER("onGameOver", "ScriptEvent", "Game over"),
    DESTROY("onDestroy", "ScriptEvent", "Destroy")
}

enum class CharacterRender(val label: String, val className: String) {
    SPARROW("Sparrow", "SparrowCharacter"),
    MULTI_SPARROW("Multi-Sparrow", "MultiSparrowCharacter"),
    ATLAS("Animate atlas", "AnimateAtlasCharacter"),
    MULTI_ATLAS("Multi-atlas", "MultiAnimateAtlasCharacter"),
    PACKER("Packer", "PackerCharacter")
}

enum class EventFieldType(val schema: String) {
    STRING("string"),
    INTEGER("integer"),
    FLOAT("float"),
    BOOL("bool")
}

data class EventField(val name: String, val type: EventFieldType)

data class ScriptSpec(
    val kind: ScriptKind,
    val className: String,
    val targetId: String,
    val callbacks: Set<Callback>,
    val render: CharacterRender = CharacterRender.SPARROW,
    val title: String = "",
    val fields: List<EventField> = emptyList(),
    val priority: Int = 1000
)

object ScriptGenerator {
    val noteKindCallbacks = setOf(Callback.CREATE, Callback.UPDATE, Callback.NOTE_INCOMING, Callback.NOTE_HIT, Callback.NOTE_MISS, Callback.DESTROY)

    fun callbacksFor(kind: ScriptKind): List<Callback> = when (kind) {
        ScriptKind.NOTE_KIND -> Callback.entries.filter { it in noteKindCallbacks }
        else -> Callback.entries
    }

    fun parseFields(raw: String): List<EventField> =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
            val name = part.substringBefore(':').trim()
            val type = when (part.substringAfter(':', "string").trim().lowercase()) {
                "int", "integer" -> EventFieldType.INTEGER
                "float", "number" -> EventFieldType.FLOAT
                "bool", "boolean" -> EventFieldType.BOOL
                else -> EventFieldType.STRING
            }
            EventField(name.replace(Regex("[^A-Za-z0-9_]"), ""), type)
        }.filter { it.name.isNotEmpty() && !it.name.first().isDigit() }.distinctBy { it.name }

    fun generate(spec: ScriptSpec): Generated {
        val className = Ids.className(spec.className.ifBlank { spec.targetId + spec.kind.label })
        val target = spec.targetId.trim()
        require(target.isNotEmpty()) { "Enter the ${spec.kind.targetLabel.lowercase()}." }
        require(target.none { it == '"' || it == '\\' }) { "The id can't contain quotes." }
        val source = when (spec.kind) {
            ScriptKind.EVENT -> event(className, target, spec)
            else -> scripted(className, target, spec)
        }
        val fileName = when (spec.kind) {
            ScriptKind.MODULE, ScriptKind.EVENT -> className
            else -> Ids.of(target).ifEmpty { className }
        }
        val path = "scripts/${spec.kind.folder}/$fileName.hxc"
        val report = ReportBuilder()
        report.stat("Callbacks", spec.callbacks.count { it in callbacksFor(spec.kind) })
        report.section(
            "Where it runs",
            listOf(
                when (spec.kind) {
                    ScriptKind.MODULE -> "Always active, in every state. Check PlayState.instance before touching gameplay."
                    ScriptKind.SONG -> "Only while the song \"$target\" is playing."
                    ScriptKind.STAGE -> "Only on the stage \"$target\"; it replaces the stage's default class."
                    ScriptKind.CHARACTER -> "Every time the character \"$target\" is used; its JSON must exist in data/characters/."
                    ScriptKind.EVENT -> "Adds the \"$target\" event to the Chart Editor and runs handleEvent when it fires; its other callbacks run in songs that use the event."
                    ScriptKind.NOTE_KIND -> "Runs for notes whose kind is \"$target\"."
                }
            )
        )
        return Generated(listOf(GenFile(path, source)), report.build())
    }

    private fun scripted(className: String, target: String, spec: ScriptSpec): String {
        val (import, parent, superCall) = when (spec.kind) {
            ScriptKind.MODULE -> Triple("funkin.modding.module.Module", "Module", "super(${str(target)}, ${spec.priority});")
            ScriptKind.SONG -> Triple("funkin.play.song.Song", "Song", "super(${str(target)});")
            ScriptKind.STAGE -> Triple("funkin.play.stage.Stage", "Stage", "super(${str(target)});")
            ScriptKind.CHARACTER -> Triple("funkin.play.character.${spec.render.className}", spec.render.className, "super(${str(target)});")
            ScriptKind.NOTE_KIND -> Triple(
                "funkin.play.notes.notekind.NoteKind",
                "NoteKind",
                "super(${str(target)}, ${str(spec.title.ifBlank { Ids.pretty(target) })});"
            )
            ScriptKind.EVENT -> error("unreachable")
        }
        val callbacks = callbacksFor(spec.kind).filter { it in spec.callbacks }
        val imports = linkedSetOf(import)
        return buildString {
            imports.forEach { appendLine("import $it;") }
            appendLine()
            appendLine("class $className extends $parent")
            appendLine("{")
            appendLine("  public function new()")
            appendLine("  {")
            appendLine("    $superCall")
            appendLine("  }")
            callbacks.forEach { callback ->
                appendLine()
                appendLine("  override public function ${callback.method}(event:${callback.eventType}):Void")
                appendLine("  {")
                appendLine("    super.${callback.method}(event);")
                appendLine("  }")
            }
            appendLine("}")
        }
    }

    private fun event(className: String, target: String, spec: ScriptSpec): String = buildString {
        appendLine("import funkin.data.event.SongEventSchema;")
        appendLine("import funkin.play.PlayState;")
        appendLine("import funkin.play.event.SongEvent;")
        appendLine()
        appendLine("class $className extends SongEvent")
        appendLine("{")
        appendLine("  public function new()")
        appendLine("  {")
        appendLine("    super(${str(target)});")
        appendLine("  }")
        appendLine()
        appendLine("  override public function handleEvent(data:Dynamic):Void")
        appendLine("  {")
        appendLine("    if (PlayState.instance == null || data == null) return;")
        appendLine("    var value:Dynamic = data.value;")
        spec.fields.forEach { field ->
            appendLine("    var ${field.name}:Dynamic = value == null ? ${default(field.type)} : Reflect.field(value, ${str(field.name)});")
        }
        appendLine("  }")
        appendLine()
        callbacksFor(spec.kind).filter { it in spec.callbacks }.forEach { callback ->
            appendLine("  override public function ${callback.method}(event:${callback.eventType}):Void")
            appendLine("  {")
            appendLine("    super.${callback.method}(event);")
            appendLine("  }")
            appendLine()
        }
        appendLine("  override public function getTitle():String")
        appendLine("  {")
        appendLine("    return ${str(spec.title.ifBlank { Ids.pretty(target) })};")
        appendLine("  }")
        appendLine()
        appendLine("  override public function getEventSchema():Dynamic")
        appendLine("  {")
        if (spec.fields.isEmpty()) {
            appendLine("    return new SongEventSchema([]);")
        } else {
            appendLine("    return new SongEventSchema([")
            spec.fields.forEachIndexed { index, field ->
                val comma = if (index < spec.fields.size - 1) "," else ""
                appendLine("      {name: ${str(field.name)}, title: ${str(Ids.pretty(field.name))}, type: ${str(field.schema())}, defaultValue: ${default(field.type)}}$comma")
            }
            appendLine("    ]);")
        }
        appendLine("  }")
        appendLine("}")
    }

    private fun EventField.schema(): String = type.schema

    private fun default(type: EventFieldType): String = when (type) {
        EventFieldType.STRING -> "\"\""
        EventFieldType.INTEGER -> "0"
        EventFieldType.FLOAT -> "0.0"
        EventFieldType.BOOL -> "false"
    }

    private fun str(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
