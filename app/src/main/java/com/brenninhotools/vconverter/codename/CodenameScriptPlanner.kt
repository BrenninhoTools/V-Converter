package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.script.ScriptBase
import com.brenninhotools.vconverter.script.ScriptBases
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptHost

class CodenameScriptPlanner(modTitle: String = "") {
    data class Planned(val className: String, val host: ScriptHost, val path: String, val note: String?)

    private val prefix = ScriptConverter.pascal(ScriptConverter.slug(modTitle.ifBlank { "Mod" }))
    private val used = HashSet<String>()
    private val claimed = HashSet<String>()
    val refs = mutableListOf<String>()

    private fun unique(base: String): String {
        var name = if (base in ScriptBases.baseGameClasses) prefix + base else base
        val root = name
        var n = 2
        while (!used.add(name.lowercase())) name = root + n++
        return name
    }

    private fun fitsNoteKind(text: String): Boolean {
        val defined = Regex("function\\s+([A-Za-z_][A-Za-z0-9_]*)").findAll(text).map { it.groupValues[1] }.toSet()
        return defined.none { it in callbacks && it !in noteKindCallbacks }
    }

    fun plan(target: CodenameScriptTarget, stem: String, text: String): Planned {
        val key = target.key
        val extra = ScriptConverter.pascal(stem).removePrefix(ScriptConverter.pascal(key)).ifEmpty { "Script" }
        fun module(name: String, note: String?): Planned {
            val className = unique(name)
            val global = target.scope == CodenameScope.GLOBAL
            val id = if (global && className == ScriptConverter.keepName(stem)) stem else ScriptConverter.kebab(className)
            val file = if (global) ScriptConverter.keepFileName(stem) else className
            return Planned(className, ScriptHost(ScriptBase.MODULE, id), "${ScriptBase.MODULE.folder}/$file.hxc", note)
        }
        fun typed(base: ScriptBase, id: String, title: String = id): Planned {
            val className = unique(ScriptBases.classFor(base, key, ScriptConverter::pascal))
            return Planned(className, ScriptHost(base, id, title), "${base.folder}/$className.hxc", null)
        }
        val planned = when (target.scope) {
            CodenameScope.GLOBAL -> module(ScriptConverter.keepName(stem), null)
            CodenameScope.SONG -> when {
                ScriptBases.isBaseGameId(ScriptBase.SONG, key) -> module(ScriptConverter.pascal(key) + extra + "Module", "the base game already has a Song script for \"$key\".")
                !claimed.add("song:$key") -> module(ScriptConverter.pascal(key) + extra + "Module", "song \"$key\" already has a Song script; V-Slice allows one per song.")
                else -> typed(ScriptBase.SONG, key)
            }
            CodenameScope.STAGE -> when {
                ScriptBases.isBaseGameId(ScriptBase.STAGE, key) -> module(ScriptConverter.pascal(key) + extra + "Module", "the base game already has a Stage script for \"$key\".")
                !claimed.add("stage:$key") -> module(ScriptConverter.pascal(key) + extra + "Module", "stage \"$key\" already has a Stage script; V-Slice allows one per stage.")
                else -> typed(ScriptBase.STAGE, key)
            }
            CodenameScope.NOTE_TYPE -> {
                val kind = CodenameToVSliceChart.noteKindFor(key)
                when {
                    ScriptBases.isBaseGameId(ScriptBase.NOTE_KIND, kind) -> module(ScriptConverter.pascal(key) + "NoteTypeModule", "\"$kind\" is a base game note kind.")
                    !fitsNoteKind(text) -> module(ScriptConverter.pascal(key) + "NoteTypeModule", "it uses callbacks a NoteKind never receives (only update, note and create/destroy events reach it).")
                    !claimed.add("notekind:$kind") -> module(ScriptConverter.pascal(key) + extra + "NoteTypeModule", "note kind \"$kind\" already has a NoteKind script.")
                    else -> typed(ScriptBase.NOTE_KIND, kind, key)
                }
            }
            CodenameScope.CHARACTER -> module(ScriptConverter.pascal(key) + "CharacterModule", null)
            CodenameScope.EVENT -> module(CodenameEvents.eventId(key) + "EventModule", null)
        }
        refs += planned.host.ref
        return planned
    }

    companion object {
        private val noteKindCallbacks = setOf(
            "create", "postCreate", "update", "postUpdate", "destroy", "onNoteHit", "onPlayerHit", "onDadHit", "onPostNoteHit",
            "onPlayerMiss", "onPostPlayerMiss", "onNoteCreation", "onPostNoteCreation"
        )
        private val callbacks = setOf(
            "beatHit", "create", "destroy", "measureHit", "onBopZoom", "onCameraMove", "onCountdown", "onDadHit", "onEvent", "onFocus",
            "onFocusLost", "onGameOver", "onGamePause", "onNoteCreation", "onNoteHit", "onPlayerHit", "onPlayerMiss", "onPostCountdown",
            "onPostEvent", "onPostGameOver", "onPostNoteCreation", "onPostNoteHit", "onPostPlayerMiss", "onPostStartCountdown",
            "onSongEnd", "onSongStart", "onStartCountdown", "onStartSong", "onSubstateClose", "onSubstateOpen", "postCreate",
            "postUpdate", "stepHit", "update", "onInputUpdate", "onPostInputUpdate", "onRatingUpdate", "onStrumCreation",
            "onPostStrumCreation", "onSplashShown", "onVocalsResync", "onGetCamPos", "onDance", "onTryDance", "onPlayAnim", "onPlaySingAnim"
        )
    }
}
