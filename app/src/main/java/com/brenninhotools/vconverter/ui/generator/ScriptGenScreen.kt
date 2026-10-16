package com.brenninhotools.vconverter.ui.generator

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.generator.Callback
import com.brenninhotools.vconverter.generator.CharacterRender
import com.brenninhotools.vconverter.generator.Generated
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.generator.ScriptGenerator
import com.brenninhotools.vconverter.generator.ScriptKind
import com.brenninhotools.vconverter.generator.ScriptSpec
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.ChoiceChips
import com.brenninhotools.vconverter.ui.components.FieldLabel
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.NumberField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.theme.Funkin

class ScriptGenViewModel(application: Application) : GeneratorViewModel(application) {
    var kind by input(ScriptKind.MODULE)
    var targetId by input("")
    var className by input("")
    var callbacks by input(setOf(Callback.CREATE, Callback.UPDATE, Callback.BEAT_HIT))
    var render by input(CharacterRender.SPARROW)
    var title by input("")
    var fields by input("")
    var priority by input("1000")

    override fun downloadName(): String = "${Ids.of(targetId).ifEmpty { "script" }}-${kind.folder}.zip"

    fun toggle(callback: Callback) {
        callbacks = if (callback in callbacks) callbacks - callback else callbacks + callback
    }

    override fun build(): Generated = ScriptGenerator.generate(
        ScriptSpec(
            kind = kind,
            className = className,
            targetId = targetId,
            callbacks = callbacks,
            render = render,
            title = title,
            fields = ScriptGenerator.parseFields(fields),
            priority = whole(priority, "Priority")
        )
    )

    override fun preview(generated: Generated): String? = generated.files.firstOrNull()?.text
}

@Composable
fun ScriptGenScreen(viewModel: ScriptGenViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Lavender
    val busy = viewModel.busy
    val kind = viewModel.kind
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Script type", accent, 1)
            ChoiceChips(
                options = ScriptKind.entries,
                label = { it.label },
                isSelected = { it == kind },
                onClick = { viewModel.kind = it },
                accent = accent,
                enabled = !busy
            )
            FunkinTextField(viewModel.targetId, { viewModel.targetId = it }, kind.targetLabel, accent, enabled = !busy)
            FunkinTextField(viewModel.className, { viewModel.className = it }, "Class name (optional)", accent, enabled = !busy)
            when (kind) {
                ScriptKind.MODULE -> NumberField(viewModel.priority, { viewModel.priority = it }, "Priority (lower runs first)", accent, enabled = !busy, decimal = false)
                ScriptKind.CHARACTER -> {
                    FieldLabel("Render type")
                    ChoiceChips(CharacterRender.entries, { it.label }, { it == viewModel.render }, { viewModel.render = it }, accent, !busy)
                }
                ScriptKind.EVENT -> {
                    FunkinTextField(viewModel.title, { viewModel.title = it }, "Title in the Chart Editor", accent, enabled = !busy)
                    FunkinTextField(viewModel.fields, { viewModel.fields = it }, "Values, e.g. intensity:float, label, flash:bool", accent, enabled = !busy)
                }
                ScriptKind.NOTE_KIND -> FunkinTextField(viewModel.title, { viewModel.title = it }, "Description", accent, enabled = !busy)
                else -> Unit
            }
        }
        val available = ScriptGenerator.callbacksFor(kind)
        if (available.isNotEmpty()) {
            FunkinCard {
                SectionTitle("Callbacks", accent, 2)
                ChoiceChips(available, { it.label }, { it in viewModel.callbacks }, viewModel::toggle, accent, !busy)
                Text(
                    "Each one becomes an override that already calls super.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Funkin.Muted
                )
            }
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = if (available.isNotEmpty()) 3 else 2,
            canConvert = viewModel.targetId.isNotBlank(),
            onConvert = viewModel::generate,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Generate",
            buttonText = "Generate script",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
