package com.vconvertercrew.vconverter.converterview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.vconvertercrew.vconverter.convert.ConvertedOutput
import com.vconvertercrew.vconverter.generator.Callback
import com.vconvertercrew.vconverter.generator.CharacterRender
import com.vconvertercrew.vconverter.generator.Generated
import com.vconvertercrew.vconverter.generator.Ids
import com.vconvertercrew.vconverter.generator.ModGenerator
import com.vconvertercrew.vconverter.generator.ModSpec
import com.vconvertercrew.vconverter.generator.PickedAsset
import com.vconvertercrew.vconverter.generator.ScriptGenerator
import com.vconvertercrew.vconverter.generator.ScriptKind
import com.vconvertercrew.vconverter.generator.ScriptSpec
import com.vconvertercrew.vconverter.io.OutputFile
import com.vconvertercrew.vconverter.overlay.MenuOption

data class ScriptForm(
    val kind: ScriptKind = ScriptKind.MODULE,
    val targetId: String = "",
    val className: String = "",
    val callbacks: Set<Callback> = setOf(Callback.CREATE, Callback.UPDATE, Callback.BEAT_HIT),
    val render: CharacterRender = CharacterRender.SPARROW,
    val title: String = "",
    val fields: String = "",
    val priority: String = "1000"
) {
    fun spec(): ScriptSpec = ScriptSpec(
        kind = kind,
        className = className,
        targetId = targetId,
        callbacks = callbacks.filter { it in ScriptGenerator.callbacksFor(kind) }.toSet(),
        render = render,
        title = title,
        fields = ScriptGenerator.parseFields(fields),
        priority = priority.trim().toIntOrNull() ?: throw IllegalArgumentException("Priority must be a whole number.")
    )

    fun downloadName(): String = "${Ids.of(targetId).ifEmpty { "script" }}-${kind.folder}.zip"
}

class ModGenForm {
    var spec by mutableStateOf(ModSpec(""))

    fun update(change: (ModSpec) -> ModSpec) {
        spec = change(spec)
    }
}

class ScriptGenForm {
    var form by mutableStateOf(ScriptForm())

    fun update(change: (ScriptForm) -> ScriptForm) {
        form = change(form)
    }
}

class GeneratorRun {
    var output by mutableStateOf<ConvertedOutput?>(null)
        private set
    var status by mutableStateOf<String?>(null)
    var error by mutableStateOf(false)
    var busy by mutableStateOf(false)
    private var builtFrom: Any? = null

    fun current(key: Any): ConvertedOutput? = if (builtFrom == key) output else null

    fun generate(key: Any, build: () -> Generated) {
        try {
            val generated = build()
            output = ConvertedOutput(generated.files.map { OutputFile.of(it.path, it.text, it.bytes) }, generated.report, generated.files.firstOrNull()?.text)
            builtFrom = key
            status = "Generated ${generated.files.size} file(s)."
            error = false
        } catch (e: Exception) {
            output = null
            builtFrom = null
            status = e.message ?: "Could not generate the files."
            error = true
        }
    }

    fun report(text: String, failed: Boolean) {
        status = text
        error = failed
    }
}

class GeneratorActions(
    val onSaveZip: (ConvertedOutput) -> Unit,
    val onSaveFolder: ((ConvertedOutput) -> Unit)?,
    val zipLabel: String = "Save As Zip",
    val folderLabel: String = "Save To Mod Folder",
    val onTestGame: (() -> Unit)? = null,
    val status: String? = null,
    val statusError: Boolean = false,
    val busy: Boolean = false
)

object GeneratorMenus {
    val modAccent = Color(0xFFFF7CD8)
    val scriptAccent = Color(0xFFB8A6FF)

    fun folderName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").trim().trim('.').ifEmpty { "V-Slice Mod" }

    fun modOptions(spec: () -> ModSpec, update: ((ModSpec) -> ModSpec) -> Unit): List<MenuOption> = listOf(
        MenuOption.TextEntry("Title", "The mod's name in the game.", { spec().title }, { value -> update { it.copy(title = value) } }),
        MenuOption.TextEntry("Description", "One line about the mod.", { spec().description }, { value -> update { it.copy(description = value) } }),
        MenuOption.TextEntry("Authors", "Comma separated names.", { spec().author }, { value -> update { it.copy(author = value) } }),
        MenuOption.TextEntry("Version", "Like 1.0.0.", { spec().version }, { value -> update { it.copy(version = value.trim()) } }, "1.0.0"),
        MenuOption.TextEntry("Homepage", "Optional link.", { spec().homepage }, { value -> update { it.copy(homepage = value) } }),
        MenuOption.TextEntry("License", "Optional license name.", { spec().license }, { value -> update { it.copy(license = value) } }),
        MenuOption.Toggle("Starter Module", "Adds scripts/modules/<Name>Module.hxc ready to edit.", { spec().starterModule }) { value -> update { it.copy(starterModule = value) } }
    )

    fun scriptOptions(form: () -> ScriptForm, update: ((ScriptForm) -> ScriptForm) -> Unit): List<MenuOption> {
        val current = form()
        val kinds = ScriptKind.entries
        val renders = CharacterRender.entries
        return buildList {
            add(MenuOption.Stepper("Type", "LEFT / RIGHT pick the V-Slice class the script extends.", { form().kind.label }) { by ->
                update { it.copy(kind = kinds[((kinds.indexOf(it.kind) + by) % kinds.size + kinds.size) % kinds.size]) }
            })
            add(MenuOption.TextEntry(current.kind.targetLabel, "The id the script attaches to.", { form().targetId }, { value -> update { it.copy(targetId = value) } }))
            add(MenuOption.TextEntry("Class Name", "Empty makes one from the id.", { form().className }, { value -> update { it.copy(className = value) } }))
            when (current.kind) {
                ScriptKind.MODULE -> add(MenuOption.TextEntry("Priority", "Lower runs first.", { form().priority }, { value -> update { it.copy(priority = value) } }, "1000"))
                ScriptKind.CHARACTER -> add(MenuOption.Stepper("Render", "How the character's sprites are drawn.", { form().render.label }) { by ->
                    update { it.copy(render = renders[((renders.indexOf(it.render) + by) % renders.size + renders.size) % renders.size]) }
                })
                ScriptKind.EVENT -> {
                    add(MenuOption.TextEntry("Title", "Shown in the Chart Editor.", { form().title }, { value -> update { it.copy(title = value) } }))
                    add(MenuOption.TextEntry("Values", "Like intensity:float, label, flash:bool.", { form().fields }, { value -> update { it.copy(fields = value) } }))
                }
                ScriptKind.NOTE_KIND -> add(MenuOption.TextEntry("Description", "Shown in the Chart Editor.", { form().title }, { value -> update { it.copy(title = value) } }))
                else -> Unit
            }
            val available = ScriptGenerator.callbacksFor(current.kind)
            if (available.isNotEmpty()) add(MenuOption.Submenu("Callbacks", "Each one becomes an override that already calls super.", "Callbacks") {
                ScriptGenerator.callbacksFor(form().kind).map { callback ->
                    MenuOption.Toggle(callback.label, callback.method, { callback in form().callbacks }) { on ->
                        update { it.copy(callbacks = if (on) it.callbacks + callback else it.callbacks - callback) }
                    }
                }
            })
        }
    }

    fun modSummary(spec: ModSpec): List<String> = listOfNotNull(
        spec.title.takeIf { it.isNotBlank() }?.let { "TITLE: $it" } ?: "NO TITLE YET",
        spec.author.takeIf { it.isNotBlank() }?.let { "BY: $it" },
        "VERSION: ${spec.version}",
        spec.icon?.let { "ICON: ${it.name}" } ?: "NO ICON",
        if (spec.starterModule) "STARTER MODULE: ON" else "STARTER MODULE: OFF"
    )

    fun scriptSummary(form: ScriptForm): List<String> = listOfNotNull(
        "TYPE: ${form.kind.label}",
        form.targetId.takeIf { it.isNotBlank() }?.let { "${form.kind.targetLabel.uppercase()}: $it" } ?: "NO ID YET",
        "CALLBACKS: ${form.callbacks.count { it in ScriptGenerator.callbacksFor(form.kind) }}"
    )

    fun modSpec(
        live: () -> ModSpec,
        update: ((ModSpec) -> ModSpec) -> Unit,
        run: GeneratorRun,
        actions: GeneratorActions,
        onPickIcon: (() -> Unit)?,
        onBack: () -> Unit
    ): ConverterMenuSpec {
        val form = live()
        val ready = run.current(form)
        return ConverterMenuSpec(
            title = "New Mod",
            direction = "V-Slice mod folder",
            accent = modAccent,
            rows = emptyList(),
            busy = run.busy || actions.busy,
            canConvert = form.title.isNotBlank(),
            output = ready,
            status = actions.status ?: run.status,
            statusError = if (actions.status != null) actions.statusError else run.error,
            onAddFiles = {},
            onAddFolder = null,
            onRemove = {},
            onClear = {},
            onSlot = null,
            onConvert = { live().let { now -> run.generate(now) { ModGenerator.generate(now) } } },
            onSaveZip = { run.current(live())?.let(actions.onSaveZip) },
            onSaveFolder = actions.onSaveFolder?.let { save -> { run.current(live())?.let(save); Unit } },
            zipLabel = actions.zipLabel,
            folderLabel = actions.folderLabel,
            onTestGame = actions.onTestGame,
            options = modOptions(live, update),
            onBack = onBack,
            addLabel = null,
            convertLabel = "Generate",
            convertAgainLabel = "Generate Again",
            convertHint = "Makes _polymod_meta.json, the folders and the starter script.",
            busyLabel = "Generating...",
            optionsLabel = "Mod Info",
            optionsHint = "Title, authors, version, homepage, license and the starter module.",
            extraItems = buildList {
                if (onPickIcon != null) add(MenuOption.Action("Pick Icon", "A 150×150 .png that becomes _polymod_icon.png.", onPickIcon))
                if (form.icon != null) add(MenuOption.Action("Remove Icon", "Generates the mod without an icon.") { update { it.copy(icon = null) } })
            },
            countLabel = "V-SLICE MOD",
            infoLines = modSummary(form),
            backHint = "Goes back to the generator menu."
        )
    }

    fun scriptSpec(
        live: () -> ScriptForm,
        update: ((ScriptForm) -> ScriptForm) -> Unit,
        run: GeneratorRun,
        actions: GeneratorActions,
        onBack: () -> Unit
    ): ConverterMenuSpec {
        val form = live()
        val ready = run.current(form)
        return ConverterMenuSpec(
            title = "New Script",
            direction = "V-Slice .hxc class",
            accent = scriptAccent,
            rows = emptyList(),
            busy = run.busy || actions.busy,
            canConvert = form.targetId.isNotBlank(),
            output = ready,
            status = actions.status ?: run.status,
            statusError = if (actions.status != null) actions.statusError else run.error,
            onAddFiles = {},
            onAddFolder = null,
            onRemove = {},
            onClear = {},
            onSlot = null,
            onConvert = { live().let { now -> run.generate(now) { ScriptGenerator.generate(now.spec()) } } },
            onSaveZip = { run.current(live())?.let(actions.onSaveZip) },
            onSaveFolder = actions.onSaveFolder?.let { save -> { run.current(live())?.let(save); Unit } },
            zipLabel = actions.zipLabel,
            folderLabel = actions.folderLabel,
            onTestGame = actions.onTestGame,
            options = scriptOptions(live, update),
            onBack = onBack,
            addLabel = null,
            convertLabel = "Generate",
            convertAgainLabel = "Generate Again",
            convertHint = "Writes the .hxc class into scripts/${form.kind.folder}/.",
            busyLabel = "Generating...",
            optionsLabel = "Script Setup",
            optionsHint = "Type, id, class name and the callbacks it overrides.",
            countLabel = form.kind.label.uppercase() + " SCRIPT",
            infoLines = scriptSummary(form),
            code = ready?.preview,
            backHint = "Goes back to the generator menu."
        )
    }

    fun pickedIcon(name: String, bytes: ByteArray): PickedAsset {
        require(name.substringAfterLast('.', "").equals("png", ignoreCase = true)) { "$name is not a .png file." }
        return PickedAsset(name, bytes)
    }
}
