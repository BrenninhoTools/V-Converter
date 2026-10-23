package com.brenninhotools.vconverter.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

object Dialogs {
    var owner: Frame? = null

    private var lastFolder: File? = null

    fun openFiles(title: String): List<File> {
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        dialog.isMultipleMode = true
        lastFolder?.let { dialog.directory = it.absolutePath }
        dialog.isVisible = true
        val files = dialog.files?.toList() ?: emptyList()
        files.firstOrNull()?.parentFile?.let { lastFolder = it }
        return files
    }

    fun openFile(title: String, extensions: Set<String>): File? {
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        dialog.file = extensions.joinToString(";") { "*.$it" }
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        lastFolder?.let { dialog.directory = it.absolutePath }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val file = File(dialog.directory, name)
        lastFolder = file.parentFile
        return file
    }

    fun saveFile(title: String, suggested: String): File? {
        val dialog = FileDialog(owner, title, FileDialog.SAVE)
        dialog.file = suggested
        lastFolder?.let { dialog.directory = it.absolutePath }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val file = File(dialog.directory, if (name.contains('.')) name else "$name.zip")
        lastFolder = file.parentFile
        return file
    }

    fun chooseFolder(title: String): File? {
        val chooser = JFileChooser(lastFolder)
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return null
        val folder = chooser.selectedFile ?: return null
        lastFolder = folder.parentFile ?: folder
        return folder
    }

    fun reveal(file: File) {
        runCatching {
            if (System.getProperty("os.name").lowercase().contains("win")) {
                if (file.isDirectory) ProcessBuilder("explorer.exe", file.absolutePath).start()
                else ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            } else {
                java.awt.Desktop.getDesktop().open(if (file.isDirectory) file else file.parentFile)
            }
        }
    }
}
