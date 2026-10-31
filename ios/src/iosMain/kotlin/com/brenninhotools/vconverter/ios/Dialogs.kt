package com.brenninhotools.vconverter.ios

import com.brenninhotools.vconverter.platform.File
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

private class PickerDelegate(private val done: (List<NSURL>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        Dialogs.release(this)
        done(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        Dialogs.release(this)
    }
}

object Dialogs {
    private val active = mutableListOf<NSObject>()

    internal fun release(delegate: NSObject) {
        active.remove(delegate)
    }

    private fun top(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }

    private fun present(picker: UIDocumentPickerViewController, done: (List<NSURL>) -> Unit) {
        val delegate = PickerDelegate(done)
        active += delegate
        picker.delegate = delegate
        top()?.presentViewController(picker, animated = true, completion = null)
    }

    private fun file(url: NSURL): File? = url.path?.let { File(it) }

    val exports: File get() = File(File.temporaryDirectory, "exports").apply { mkdirs() }

    fun openFiles(title: String, onPick: (List<File>) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        picker.allowsMultipleSelection = true
        present(picker) { urls -> onPick(urls.mapNotNull(::file)) }
    }

    fun openFile(title: String, extensions: Set<String>, onPick: (File) -> Unit) {
        val types = extensions.mapNotNull { UTType.typeWithFilenameExtension(it) }.ifEmpty { listOf(UTTypeItem) }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        present(picker) { urls -> urls.firstOrNull()?.let(::file)?.let(onPick) }
    }

    fun chooseFolder(title: String, onPick: (File) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
        present(picker) { urls ->
            val url = urls.firstOrNull() ?: return@present
            url.startAccessingSecurityScopedResource()
            file(url)?.let(onPick)
        }
    }

    fun pickTarget(zip: Boolean, title: String, suggested: String, folderTitle: String, onPick: (File) -> Unit) {
        if (zip) {
            val target = File(exports, suggested.replace('/', '_'))
            target.deleteRecursively()
            onPick(target)
        } else {
            chooseFolder(folderTitle, onPick)
        }
    }

    fun export(file: File) {
        runOnMain {
            val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(file.path)), asCopy = true)
            present(picker) { }
        }
    }

    fun openLink(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any>(), null)
    }
}
