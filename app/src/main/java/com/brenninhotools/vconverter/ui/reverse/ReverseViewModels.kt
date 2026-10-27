package com.brenninhotools.vconverter.ui.reverse

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.ReverseCharactersConverter
import com.brenninhotools.vconverter.convert.ReverseScriptsConverter
import com.brenninhotools.vconverter.convert.ReverseStagesConverter
import com.brenninhotools.vconverter.convert.ReverseWeeksConverter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias PickedText = com.brenninhotools.vconverter.convert.PickedText

open class ReverseViewModel(application: Application, private val converter: FileConverter) : BaseConverterViewModel(application) {
    var files by mutableStateOf(listOf<PickedText>())
        protected set

    val noun: String get() = converter.noun

    val engine: String get() = converter.engine

    val canConvert: Boolean get() = converter.canConvert(files)

    override fun downloadName(): String = converter.downloadName()

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val loaded = load(uris, converter::accept) { file -> converter.classify(file, converter.read(file)) }
            files = files.filterNot { old -> loaded.items.any { it.key == old.key } } + loaded.items
            invalidate()
            onFilesChanged()
            loadStatus(loaded, "file(s)")
        }
    }

    fun remove(index: Int) {
        files = files.filterIndexed { i, _ -> i != index }
        invalidate()
        onFilesChanged()
    }

    fun setSlot(index: Int, slot: CharacterSlot) {
        files = files.mapIndexed { i, item -> if (i == index) item.copy(slot = slot) else item }
        invalidate()
    }

    fun clear() {
        files = emptyList()
        status = null
        invalidate()
        onFilesChanged()
    }

    protected open fun onFilesChanged() = Unit

    protected open fun produce(snapshot: List<PickedText>): ConvertedOutput = converter.build(snapshot)

    protected open fun onConverted() = Unit

    fun convert() {
        runTask {
            try {
                val snapshot = files
                require(snapshot.isNotEmpty()) { "Add at least one $engine $noun." }
                output = withContext(Dispatchers.Default) { produce(snapshot) }
                onConverted()
                download = null
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Conversion failed.")
            }
        }
    }

}

class ReverseCharactersViewModel(application: Application) : ReverseViewModel(application, ReverseCharactersConverter())

class ReverseStagesViewModel(application: Application) : ReverseViewModel(application, ReverseStagesConverter())

class ReverseWeeksViewModel(application: Application) : ReverseViewModel(application, ReverseWeeksConverter())

class ReverseScriptsViewModel(application: Application) : ReverseViewModel(application, ReverseScriptsConverter())
