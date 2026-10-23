package com.brenninhotools.vconverter.ui.stages

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.stage.LuaStageExtractor
import com.brenninhotools.vconverter.stage.PsychStage
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedStage(
    val key: String,
    val fileName: String,
    val id: String,
    val stage: PsychStage,
    val lua: String? = null,
    val fromJson: Boolean = true
)

class StagesViewModel(application: Application) : BaseConverterViewModel(application) {
    override fun downloadName(): String = "stages-vslice.zip"

    var stages by mutableStateOf(listOf<PickedStage>())
        private set
    var atlases by mutableStateOf(mapOf<String, SparrowAtlas>())
        private set

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val loaded = load(uris, { ((it.extension == "json" || it.extension == "lua") && it.isUnder("stages")) || (it.extension == "xml" && it.isUnder("images")) }) { file ->
                val stem = file.name.substringBeforeLast('.')
                when (file.extension) {
                    "lua" -> PickedStage(file.path, file.name, stem, LuaStageExtractor.defaultStage(), file.readText(), fromJson = false)
                    "xml" -> {
                        val atlas = SparrowAtlas.parse(file.readText())
                        require(atlas.frames.isNotEmpty()) { "not a Sparrow spritesheet" }
                        val parts = file.path.replace('\\', '/').split('/')
                        val start = parts.indexOfLast { it.equals("images", ignoreCase = true) }
                        val image = (if (start >= 0) parts.drop(start + 1) else listOf(parts.last())).joinToString("/").substringBeforeLast('.')
                        atlas to image
                    }
                    else -> PickedStage(file.path, file.name, stem, PsychStageParser.parse(file.readText()))
                }
            }
            atlases = atlases + loaded.items.filterIsInstance<Pair<*, *>>().associate { (it.second as String).lowercase() to it.first as SparrowAtlas }
            val picked = loaded.items.filterIsInstance<PickedStage>()
            var merged = stages.filterNot { old -> picked.any { it.key == old.key } }
            picked.forEach { item ->
                val twin = merged.indexOfFirst { it.id.equals(item.id, ignoreCase = true) }
                merged = when {
                    twin < 0 -> merged + item
                    item.lua != null -> merged.mapIndexed { i, s -> if (i == twin) s.copy(lua = item.lua) else s }
                    else -> merged.mapIndexed { i, s -> if (i == twin) item.copy(lua = s.lua) else s }
                }
            }
            stages = merged
            invalidate()
            loadStatus(loaded, "stage(s)")
        }
    }

    fun setId(index: Int, value: String) {
        stages = stages.mapIndexed { i, item -> if (i == index) item.copy(id = value) else item }
        invalidate()
    }

    fun remove(index: Int) {
        stages = stages.filterIndexed { i, _ -> i != index }
        invalidate()
    }

    fun clear() {
        stages = emptyList()
        atlases = emptyMap()
        status = null
        invalidate()
    }

    fun convert() {
        runTask {
            try {
                require(stages.isNotEmpty()) { "Add at least one Psych Engine stage." }
                val ids = stages.map { it.id.trim() }
                require(ids.none { it.isEmpty() || it.contains('/') }) { "Every stage needs a valid id." }
                val duplicate = ids.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.key
                require(duplicate == null) { "The id \"$duplicate\" is used more than once." }

                val snapshot = stages.zip(ids)
                val sheets = atlases
                val lookup: (String) -> SparrowAtlas? = { image ->
                    val key = image.lowercase()
                    sheets[key] ?: sheets.entries.firstOrNull { it.key.substringAfterLast('/') == key.substringAfterLast('/') }?.value
                }
                val result = withContext(Dispatchers.Default) {
                    val combined = ReportBuilder()
                    val files = snapshot.map { (item, id) ->
                        val props = item.lua?.let { LuaStageExtractor.extract(it, item.stage.objectNames) }
                        val stage = props?.let { LuaStageExtractor.merge(item.stage, it) } ?: item.stage
                        props?.notes?.let { if (it.isNotEmpty()) combined.section("$id · Lua stage", it) }
                        if (!item.fromJson) combined.warn("$id: no stage .json was added; default character positions were used.")
                        val converted = PsychToVSliceStage.convert(StageSource(id, stage, atlas = lookup))
                        converted.report.stats.firstOrNull { it.label == "Props" }?.let { combined.stat("$id props", it.value) }
                        converted.report.sections.forEach { combined.section("$id · ${it.title}", it.lines) }
                        converted.report.warnings.forEach { combined.warn("$id: $it") }
                        OutputFile(listOf("data", "stages"), "$id.json", converted.json)
                    }
                    ConvertedOutput(files, combined.build())
                }
                output = result
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Conversion failed.")
            }
        }
    }
}
