package com.brenninhotools.vconverter.ui.stages

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.io.DocumentIO
import com.brenninhotools.vconverter.io.OutputFile
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
    val uri: Uri,
    val fileName: String,
    val id: String,
    val stage: PsychStage
)

class StagesViewModel(application: Application) : BaseConverterViewModel(application) {
    var stages by mutableStateOf(listOf<PickedStage>())
        private set

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val (added, failures) = readEach(uris) { uri, name ->
                val stage = PsychStageParser.parse(DocumentIO.readText(context, uri))
                PickedStage(uri, name, name.substringBeforeLast('.'), stage)
            }
            stages = stages.filterNot { old -> added.any { it.uri == old.uri } } + added
            invalidate()
            loadStatus(added.size, "stage(s)", failures)
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
                val result = withContext(Dispatchers.Default) {
                    val combined = ReportBuilder()
                    val files = snapshot.map { (item, id) ->
                        val converted = PsychToVSliceStage.convert(StageSource(id, item.stage))
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
