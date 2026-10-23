package com.brenninhotools.vconverter.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.mod.ModConversionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppState(private val scope: CoroutineScope) {
    var direction by mutableStateOf(Direction.PSYCH_TO_VSLICE)
    var tab by mutableStateOf(Tab.MOD)
    var lastOutput by mutableStateOf<File?>(null)

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
        if (tab == Tab.MOD) openMod(list.first()) else addFiles(list)
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
        val loaded = state.mod ?: return
        if (state.busy) return
        val target = if (zip) {
            Dialogs.saveFile("Save the converted mod", "${state.folderName}-${state.suffix}.zip") ?: return
        } else {
            val parent = Dialogs.chooseFolder("Choose the ${state.direction.to.title} mods folder") ?: return
            File(parent, state.folderName)
        }
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
                state.status = Status.Success("Done! Wrote ${result.filesWritten} files to ${target.absolutePath}")
                lastOutput = target
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
        val target = if (zip) Dialogs.saveFile("Save the converted files", state.converter.downloadName()) ?: return
        else Dialogs.chooseFolder("Choose the ${direction.to.title} mod folder to write into") ?: return
        state.busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (zip) Workspace.writeZip(target, "") { writer -> output.files.forEach { it.writeTo(writer) } }
                    else Workspace.writeFolder(target) { writer -> output.files.forEach { it.writeTo(writer) } }
                }
                state.status = Status.Success("Saved ${output.files.size} file(s) to ${target.absolutePath}")
                lastOutput = target
            } catch (e: Exception) {
                state.status = Status.Error(e.message ?: "Could not save the files.")
            } finally {
                state.busy = false
            }
        }
    }
}
