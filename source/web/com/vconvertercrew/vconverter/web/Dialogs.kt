package com.vconvertercrew.vconverter.web

import com.vconvertercrew.vconverter.mod.ZipModWriter
import com.vconvertercrew.vconverter.platform.Browser
import com.vconvertercrew.vconverter.platform.ByteArrayOutputStream
import com.vconvertercrew.vconverter.platform.File
import com.vconvertercrew.vconverter.platform.Platform
import kotlin.js.JsAny
import kotlin.js.js

private fun jsPick(accept: String, multiple: Boolean, folder: Boolean, done: (JsAny) -> Unit): Unit = js("VC.pick(accept, multiple, folder, done)")
private fun jsCount(list: JsAny): Int = js("list.length")
private fun jsItemPath(list: JsAny, index: Int): String = js("list[index].path")
private fun jsItemFile(list: JsAny, index: Int): JsAny = js("list[index].file")
private fun jsRead(file: JsAny, done: (JsAny?) -> Unit): Unit = js("VC.read(file, done)")

object Dialogs {
    private val picked = File("/picked")

    val exports: File get() = File(File.temporaryDirectory, "exports").apply { mkdirs() }

    private fun clean(path: String): String = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")

    private fun load(list: JsAny, done: (File, List<File>) -> Unit) {
        val session = File(picked, Platform.randomId())
        session.mkdirs()
        val count = jsCount(list)
        val files = ArrayList<File>(count)
        if (count == 0) {
            done(session, files)
            return
        }
        var remaining = count
        for (index in 0 until count) {
            val target = File(session, clean(jsItemPath(list, index)).ifEmpty { "file-$index" })
            jsRead(jsItemFile(list, index)) { data ->
                if (data != null) {
                    target.writeBytes(Browser.toBytes(data))
                    files += target
                }
                remaining--
                if (remaining == 0) done(session, files.sortedBy { it.path })
            }
        }
    }

    private fun accept(extensions: Set<String>): String = extensions.joinToString(",") { ".$it" }

    fun openFiles(title: String, onPick: (List<File>) -> Unit) {
        jsPick("", true, false) { list -> load(list) { _, files -> if (files.isNotEmpty()) onPick(files) } }
    }

    fun openFile(title: String, extensions: Set<String>, onPick: (File) -> Unit) {
        jsPick(accept(extensions), false, false) { list -> load(list) { _, files -> files.firstOrNull()?.let(onPick) } }
    }

    fun chooseFolder(title: String, onPick: (File) -> Unit) {
        jsPick("", true, true) { list ->
            load(list) { session, files ->
                if (files.isEmpty()) return@load
                val roots = session.list().orEmpty()
                onPick(if (roots.size == 1) File(session, roots[0]) else session)
            }
        }
    }

    fun pickTarget(zip: Boolean, title: String, suggested: String, folderTitle: String, onPick: (File) -> Unit) {
        val name = suggested.replace('/', '_')
        val target = if (zip) File(exports, name) else File(File(exports, Platform.randomId()), name.removeSuffix(".zip"))
        target.deleteRecursively()
        if (!zip) target.mkdirs()
        onPick(target)
    }

    fun export(file: File) {
        if (file.isFile) {
            Browser.download(file.name, file.readBytes())
            return
        }
        if (!file.isDirectory) return
        val out = ByteArrayOutputStream()
        ZipModWriter(out, file.name).use { writer ->
            file.walkTopDown().filter { it.isFile }.forEach { child ->
                writer.copy(child.relativeTo(file).invariantSeparatorsPath) { child.inputStream() }
            }
        }
        Browser.download("${file.name}.zip", out.toByteArray())
    }

    fun openLink(url: String) = Browser.open(url)
}
