package com.brenninhotools.vconverter.ui.generator

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.brenninhotools.vconverter.generator.Generated
import com.brenninhotools.vconverter.generator.PickedAsset
import com.brenninhotools.vconverter.io.DocumentIO
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class GeneratorViewModel(application: Application) : BaseConverterViewModel(application) {
    protected fun <T> input(initial: T): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value != value) {
                state.value = value
                invalidate()
            }
        }
    }

    protected abstract fun build(): Generated

    protected open fun preview(generated: Generated): String? = null

    fun generate() {
        runTask {
            try {
                val result = withContext(Dispatchers.Default) {
                    val generated = build()
                    ConvertedOutput(
                        generated.files.map { OutputFile.of(it.path, it.text, it.bytes) },
                        generated.report,
                        preview(generated)
                    )
                }
                output = result
                download = null
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Could not generate the files.")
            }
        }
    }

    protected fun pick(uri: Uri, extensions: Set<String>, apply: (PickedAsset) -> Unit) {
        runTask {
            try {
                val asset = withContext(Dispatchers.IO) {
                    val name = DocumentIO.displayName(context, uri)
                    val ext = name.substringAfterLast('.', "").lowercase()
                    require(ext in extensions) { "$name is not a ${extensions.joinToString("/") { ".$it" }} file." }
                    PickedAsset(name, DocumentIO.readBytes(context, uri))
                }
                apply(asset)
                invalidate()
                status = null
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not read the file.")
            }
        }
    }

    protected fun number(text: String, label: String): Double =
        text.trim().toDoubleOrNull() ?: throw IllegalArgumentException("$label must be a number.")

    protected fun whole(text: String, label: String): Int =
        text.trim().toIntOrNull() ?: throw IllegalArgumentException("$label must be a whole number.")
}
