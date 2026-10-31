package com.brenninhotools.vconverter.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.mod.ModConversionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.brenninhotools.vconverter.platform.File

enum class EditorTool(val title: String, val accent: androidx.compose.ui.graphics.Color, val hint: String) {
    CHART(
        "Chart Editor", Palette.Down,
        "Start a song or open any chart, then click the lanes to place notes. Space plays, the mouse wheel scrolls by the snap, Ctrl+Z / Ctrl+Y undo and redo, Q / E change a hold, Delete removes the selection. The lane on the right holds events and the mini-map jumps around the song."
    ),
    STAGE(
        "Stage Editor", Palette.Up,
        "Open a stage or start from your images. Click a prop or a character to select it and drag it to move it; dragging elsewhere moves the view and the wheel zooms. The arrow keys nudge the selection (Shift for 10), Delete removes a prop, Ctrl+D duplicates, Ctrl+Z / Ctrl+Y undo and redo."
    ),
    CHARACTER(
        "Character Editor", Palette.Left,
        "Open a character or a spritesheet, then drag the sprite to fix the offsets of each animation. Space plays, the arrow keys move the offset (Shift for 10), W / S pick the animation, Ctrl+Z / Ctrl+Y undo and redo, the mouse wheel zooms."
    ),
    PLAYTEST(
        "PlayTest", Palette.Up,
        "Play any song of a mod, a converter's result or the Chart Editor's chart with its stage and characters, with botplay, and get a report of what is broken."
    ),
    ONLINE(
        "Online", Palette.Pink,
        "Browse and download mods shared on the V-Converter server, share your own, and see new versions and announcements. Set the server address in the options (the gear)."
    ),
    WEEK(
        "Week Editor", Palette.Right,
        "Open a week or start a new one. Click a menu character in the story menu preview and drag it to move it; the arrow keys nudge it (Shift for 10), Enter plays the confirm animation, Left / Right change the difficulty shown, Ctrl+Z / Ctrl+Y undo and redo."
    )
}

class AppState(private val scope: CoroutineScope) {
    var direction by mutableStateOf(Direction.PSYCH_TO_VSLICE)
    var tab by mutableStateOf(Tab.MOD)
    var lastOutput by mutableStateOf<File?>(null)
    var tool by mutableStateOf<EditorTool?>(null)
    val editor = IosEditor(scope)
    val characterEditor = IosCharacterEditor(scope)
    val stageEditor = IosStageEditor(scope)
    val weekEditor = IosWeekEditor(scope)
    val online = com.brenninhotools.vconverter.onlineview.OnlineState(scope)
    val playtest = IosPlayTest(scope)

    fun playTestFiles(files: List<Pair<String, ByteArray>>, label: String) {
        playtest.state.open(files, label)
        tool = EditorTool.PLAYTEST
    }

    fun playTestChart(document: com.brenninhotools.vconverter.editor.ChartDocument, audio: List<com.brenninhotools.vconverter.editor.EditorAudio>) {
        if (editor.editor.playing) editor.editor.togglePlay()
        playtest.state.useChart(document, audio, "Chart Editor · ${document.meta.songName.ifBlank { document.id }}")
        tool = EditorTool.PLAYTEST
    }

    private val mods = Direction.entries.associateWith { ModTabState(it) }
    private val tabs = HashMap<Pair<Direction, Tab>, FileTabState>()

    fun mod(): ModTabState = mods.getValue(direction)

    fun files(): FileTabState = tabs.getOrPut(direction to tab) { FileTabState(Workspace.converter(direction, tab)) }

    fun selectSource(engine: Engine) {
        if (engine != direction.from) direction = Direction.from(engine)
    }

    fun selectTarget(engine: Engine) {
        direction = direction.retarget(engine)
    }

    fun swap() {
        direction.swapped()?.let { direction = it }
    }

    fun drop(list: List<File>) {
        if (list.isEmpty()) return
        when (tool) {
            EditorTool.CHART -> editor.open(list)
            EditorTool.CHARACTER -> characterEditor.open(list)
            EditorTool.STAGE -> stageEditor.open(list)
            EditorTool.WEEK -> weekEditor.open(list)
            EditorTool.ONLINE -> Unit
            EditorTool.PLAYTEST -> playtest.open(list)
            null -> if (tab == Tab.MOD) openMod(list.first()) else addFiles(list)
        }
    }

    fun openMod(source: File) {
        val state = mod()
        if (state.busy) return
        state.busy = true
        state.status = null
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { LoadedMod.open(source, state.direction) { state.progress = it } }
                state.accept(loaded, source)
            } catch (e: Exception) {
                state.status = Status.Error(e.message ?: "Could not open ${source.name}.")
            } finally {
                state.progress = null
                state.busy = false
            }
        }
    }

    fun convertMod(zip: Boolean) {
        val state = mod()
        if (state.mod == null || state.busy) return
        Dialogs.pickTarget(zip, "Save the converted mod", "${state.folderName}-${state.suffix}.zip", "Choose the ${state.direction.to.title} mods folder") { picked ->
            runConversion(zip, if (zip) picked else File(picked, state.folderName))
        }
    }

    private fun runConversion(zip: Boolean, target: File) {
        val state = mod()
        val loaded = state.mod ?: return
        val info = state.info()
        state.busy = true
        state.report = null
        state.status = null
        scope.launch {
            try {
                val result: ModConversionResult = withContext(Dispatchers.IO) {
                    var converted: ModConversionResult? = null
                    if (zip) Workspace.writeZip(target, state.folderName) { writer -> converted = loaded.run(writer, info) { state.progress = it } }
                    else Workspace.writeFolder(target) { writer -> converted = loaded.run(writer, info) { state.progress = it } }
                    converted ?: throw IllegalStateException("The conversion did not run.")
                }
                state.report = result.report
                state.status = Status.Success("Done! Wrote ${result.filesWritten} files to ${if (zip) target.name else target.absolutePath}")
                lastOutput = target
                if (zip) Dialogs.export(target)
            } catch (e: Exception) {
                if (zip) target.delete()
                state.status = Status.Error(e.message ?: "The conversion stopped.")
            } finally {
                state.progress = null
                state.busy = false
            }
        }
    }

    fun addFiles(list: List<File>) {
        val state = files()
        if (state.busy || list.isEmpty()) return
        state.busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { state.add(list) }
            } catch (e: Exception) {
                state.status = Status.Error(e.message ?: "Could not read the files.")
            } finally {
                state.busy = false
            }
        }
    }

    fun convertFiles() {
        val state = files()
        if (state.busy) return
        state.busy = true
        scope.launch {
            try {
                withContext(Dispatchers.Default) { state.convert() }
            } catch (e: Exception) {
                state.output = null
                state.status = Status.Error(e.message ?: "Conversion failed.")
            } finally {
                state.busy = false
            }
        }
    }

    fun saveFiles(zip: Boolean) {
        val state = files()
        val output = state.output ?: return
        if (state.busy) return
        Dialogs.pickTarget(zip, "Save the converted files", state.converter.downloadName(), "Choose the ${direction.to.title} mod folder to write into") { target ->
            state.busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (zip) Workspace.writeZip(target, "") { writer -> output.files.forEach { it.writeTo(writer) } }
                        else Workspace.writeFolder(target) { writer -> output.files.forEach { it.writeTo(writer) } }
                    }
                    state.status = Status.Success("Saved ${output.files.size} file(s) to ${target.absolutePath}")
if (zip) Dialogs.export(target)
                    lastOutput = target
                } catch (e: Exception) {
                    state.status = Status.Error(e.message ?: "Could not save the files.")
                } finally {
                    state.busy = false
                }
            }
        }
    }
}
