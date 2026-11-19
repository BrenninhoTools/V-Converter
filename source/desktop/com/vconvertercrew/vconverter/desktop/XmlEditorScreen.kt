package com.vconvertercrew.vconverter.desktop

import androidx.compose.runtime.Composable
import com.vconvertercrew.vconverter.editorview.FnfXmlEditor
import com.vconvertercrew.vconverter.editorview.XmlEditorState
import com.vconvertercrew.vconverter.editorview.XmlExport
import com.vconvertercrew.vconverter.overlay.FunkinTransition
import java.io.File

private fun saveSheet(state: AppState, xml: XmlEditorState, export: XmlExport) {
    val picked = Dialogs.saveFile("Save the spritesheet", export.pngName) ?: return
    val base = picked.name.substringBeforeLast('.').ifBlank { export.name }
    val folder = picked.parentFile ?: return
    val png = File(folder, "$base.png")
    val sheet = File(folder, "$base.xml")
    runCatching {
        png.writeBytes(export.png)
        sheet.writeText(export.xml.replaceFirst("imagePath=\"${export.pngName}\"", "imagePath=\"$base.png\""))
    }.onSuccess {
        state.lastOutput = sheet
        xml.status = "Saved $base.png and $base.xml in ${folder.absolutePath}."
    }.onFailure { xml.status = "Could not save: ${it.message}" }
}

@Composable
fun XmlEditorScreen(state: AppState) {
    val xml = state.xmlEditor
    FnfXmlEditor(
        xml,
        onAddFrames = {
            val files = Dialogs.openFiles("Pick the frames (.png)")
            if (files.isNotEmpty()) xml.addFrames(files.filter { it.isFile }.map { it.name to it.readBytes() })
        },
        onAddSheet = {
            Dialogs.openFile("Pick the image with every frame", setOf("png", "jpg", "jpeg", "webp", "bmp"))?.let { xml.addSheet(it.name, it.readBytes()) }
        },
        onSave = { saveSheet(state, xml, it) },
        onExit = { FunkinTransition.go { MainMenu.open = true } }
    )
}
