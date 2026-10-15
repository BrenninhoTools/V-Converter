package com.brenninhotools.vconverter.ui.characters

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacter
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedCharacter(
    val key: String,
    val fileName: String,
    val id: String,
    val slot: CharacterSlot,
    val character: PsychCharacter
)

class CharactersViewModel(application: Application) : BaseConverterViewModel(application) {
    override fun downloadName(): String = "characters-vslice.zip"

    var characters by mutableStateOf(listOf<PickedCharacter>())
        private set
    var atlases by mutableStateOf(mapOf<String, SparrowAtlas>())
        private set

    private sealed interface Item {
        data class Character(val item: PickedCharacter) : Item
        data class Atlas(val key: String, val atlas: SparrowAtlas) : Item
    }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val loaded = load(uris, ::isCharacterCandidate) { file ->
                when (file.extension) {
                    "xml" -> {
                        Item.Atlas(PsychToVSliceCharacter.atlasKey(file.name.substringBeforeLast('.')), SparrowAtlas.parse(file.readText()))
                    }
                    "json" -> {
                        val character = PsychCharacterParser.parse(file.readText())
                        val id = sanitizeId(file.name.substringBeforeLast('.'))
                        Item.Character(PickedCharacter(file.path, file.name, id, PsychToVSliceCharacter.defaultSlot(id, character), character))
                    }
                    else -> throw IllegalArgumentException("Only .json characters, .xml spritesheets and .zip/.rar/.7z archives are supported.")
                }
            }
            val added = loaded.items.filterIsInstance<Item.Character>().map { it.item }
            atlases = atlases + loaded.items.filterIsInstance<Item.Atlas>().associate { it.key to it.atlas }
            characters = characters.filterNot { old -> added.any { it.key == old.key } } + added
            invalidate()
            loadStatus(loaded, "file(s)")
        }
    }

    private fun isCharacterCandidate(file: ImportedFile): Boolean = when (file.extension) {
        "json" -> file.isUnder("characters")
        "xml" -> file.isUnder("characters")
        else -> false
    }

    fun missingAtlases(item: PickedCharacter): List<String> =
        item.character.atlasImages.ifEmpty { listOf(item.character.image) }
            .filter { PsychToVSliceCharacter.atlasKey(it) !in atlases }

    fun setId(index: Int, value: String) = update(index) { it.copy(id = value) }

    fun setSlot(index: Int, slot: CharacterSlot) = update(index) { it.copy(slot = slot) }

    fun remove(index: Int) {
        characters = characters.filterIndexed { i, _ -> i != index }
        invalidate()
    }

    fun clear() {
        characters = emptyList()
        atlases = emptyMap()
        status = null
        invalidate()
    }

    fun convert() {
        runTask {
            try {
                require(characters.isNotEmpty()) { "Add at least one Psych Engine character." }
                val ids = characters.map { sanitizeId(it.id) }
                require(ids.none { it.isEmpty() }) { "Every character needs an id." }
                val duplicate = ids.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.key
                require(duplicate == null) { "The id \"$duplicate\" is used more than once." }

                val snapshot = characters.zip(ids)
                val sheets = atlases
                val result = withContext(Dispatchers.Default) {
                    val combined = ReportBuilder()
                    val files = snapshot.map { (item, id) ->
                        val converted = PsychToVSliceCharacter.convert(CharacterSource(id, item.character, item.slot, sheets))
                        converted.report.stats.firstOrNull { it.label == "Accuracy" }?.let { combined.stat(id, it.value) }
                        converted.report.sections.forEach { combined.section("$id · ${it.title}", it.lines) }
                        converted.report.warnings.forEach { combined.warn("$id: $it") }
                        OutputFile(listOf("data", "characters"), "$id.json", converted.json)
                    }
                    ConvertedOutput(files, combined.build())
                }
                characters = snapshot.map { (item, id) -> item.copy(id = id) }
                output = result
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Conversion failed.")
            }
        }
    }

    private fun update(index: Int, change: (PickedCharacter) -> PickedCharacter) {
        characters = characters.mapIndexed { i, item -> if (i == index) change(item) else item }
        invalidate()
    }
}
