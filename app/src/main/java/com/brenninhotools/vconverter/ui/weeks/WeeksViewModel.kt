package com.brenninhotools.vconverter.ui.weeks

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeek
import com.brenninhotools.vconverter.week.PsychWeekParser
import com.brenninhotools.vconverter.week.WeekSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedWeek(
    val key: String,
    val fileName: String,
    val id: String,
    val week: PsychWeek
)

class WeeksViewModel(application: Application) : BaseConverterViewModel(application) {
    override fun downloadName(): String = "weeks-vslice.zip"

    var weeks by mutableStateOf(listOf<PickedWeek>())
        private set
    var menuCharacters by mutableStateOf(mapOf<String, PsychMenuCharacter>())
        private set

    private sealed interface Item {
        data class Week(val week: PickedWeek) : Item
        data class Menu(val name: String, val character: PsychMenuCharacter) : Item
    }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val loaded = load(uris, ::isWeekCandidate) { file ->
                val text = file.readText()
                val stem = file.name.substringBeforeLast('.')
                if (PsychWeekParser.isMenuCharacter(text)) {
                    Item.Menu(stem, PsychWeekParser.parseMenuCharacter(text))
                } else {
                    Item.Week(PickedWeek(file.path, file.name, stem, PsychWeekParser.parse(text)))
                }
            }
            menuCharacters = menuCharacters + loaded.items.filterIsInstance<Item.Menu>().associate { it.name to it.character }
            val added = loaded.items.filterIsInstance<Item.Week>().map { it.week }
            weeks = weeks.filterNot { old -> added.any { it.key == old.key } } + added
            invalidate()
            loadStatus(loaded, "file(s)")
        }
    }

    private fun isWeekCandidate(file: ImportedFile): Boolean =
        file.extension == "json" && (file.isUnder("weeks") || file.isUnder("menucharacters"))

    fun missingMenuCharacters(item: PickedWeek): List<String> =
        item.week.weekCharacters.take(3).map { it.trim() }
            .filter { it.isNotEmpty() && it !in menuCharacters && it !in setOf("dad", "bf", "gf") }

    fun setId(index: Int, value: String) {
        weeks = weeks.mapIndexed { i, item -> if (i == index) item.copy(id = value) else item }
        invalidate()
    }

    fun remove(index: Int) {
        weeks = weeks.filterIndexed { i, _ -> i != index }
        invalidate()
    }

    fun clear() {
        weeks = emptyList()
        menuCharacters = emptyMap()
        status = null
        invalidate()
    }

    fun convert() {
        runTask {
            try {
                require(weeks.isNotEmpty()) { "Add at least one Psych Engine week." }
                val ids = weeks.map { it.id.trim() }
                require(ids.none { it.isEmpty() || it.contains('/') }) { "Every week needs a valid id." }
                val duplicate = ids.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.key
                require(duplicate == null) { "The id \"$duplicate\" is used more than once." }

                val snapshot = weeks.zip(ids)
                val menu = menuCharacters
                val result = withContext(Dispatchers.Default) {
                    val combined = ReportBuilder()
                    val files = snapshot.flatMap { (item, id) ->
                        val converted = PsychToVSliceLevel.convert(WeekSource(id, item.week, menu))
                        combined.stat("$id songs", converted.songIds.size)
                        converted.report.sections.forEach { combined.section("$id · ${it.title}", it.lines) }
                        converted.report.warnings.forEach { combined.warn("$id: $it") }
                        listOf(OutputFile(listOf("data", "levels"), "$id.json", converted.json)) + converted.extra.map { (path, text) -> OutputFile.of(path, text, null) }
                    }
                    combined.section(
                        "Notes",
                        listOf(
                            "Song ids use Psych's song path format (\"Dad Battle\" → \"dad-battle\"), matching the Charts converter.",
                            "Copy the week's images (storymenu, menubackgrounds, menucharacters) into your mod's images folder."
                        )
                    )
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
