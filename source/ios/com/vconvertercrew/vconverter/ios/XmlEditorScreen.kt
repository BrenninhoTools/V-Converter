package com.vconvertercrew.vconverter.ios

import androidx.compose.runtime.Composable
import com.vconvertercrew.vconverter.editorview.FnfXmlEditor
import com.vconvertercrew.vconverter.editorview.XmlEditorState
import com.vconvertercrew.vconverter.editorview.XmlExport
import com.vconvertercrew.vconverter.overlay.FunkinTransition
import com.vconvertercrew.vconverter.platform.File

private fun saveSheet(state: AppState, xml: XmlEditorState, export: XmlExport) {
    runCatching {
        val folder = File(Dialogs.exports, "xml-" + export.name.replace('/', '_')).apply { mkdirs() }
        val png = File(folder, export.pngName)
        val sheet = File(folder, export.xmlName)
        png.writeBytes(export.png)
        sheet.writeText(export.xml)
        state.lastOutput = sheet
        Dialogs.exportAll(listOf(png, sheet))
    }.onSuccess { xml.status = "Saved ${export.pngName} and ${export.xmlName}." }
        .onFailure { xml.status = "Could not save: ${it.message}" }
}

@Composable
fun XmlEditorScreen(state: AppState) {
    val xml = state.xmlEditor
    FnfXmlEditor(
        xml,
        onAddFrames = { Dialogs.openFiles("Pick the frames (.png)") { files -> xml.addFrames(files.filter { it.isFile }.map { it.name to it.readBytes() }) } },
        onAddSheet = { Dialogs.openFile("Pick the image with every frame", setOf("png", "jpg", "jpeg", "webp", "bmp")) { xml.addSheet(it.name, it.readBytes()) } },
        onSave = { saveSheet(state, xml, it) },
        onExit = { FunkinTransition.go { MainMenu.open = true } }
    )
}
