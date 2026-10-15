package com.brenninhotools.vconverter.io

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModWriter
import java.io.IOException
import java.io.InputStream

private data class Child(val id: String, val name: String, val isDirectory: Boolean, val size: Long)

private fun ContentResolver.children(treeUri: Uri, parentId: String): List<Child> {
    val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
    val result = mutableListOf<Child>()
    query(uri, columns, null, null, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getString(0) ?: continue
            val name = cursor.getString(1) ?: continue
            val mime = cursor.getString(2)
            val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
            result += Child(id, name, mime == Document.MIME_TYPE_DIR, size)
        }
    }
    return result
}

class TreeModFiles(context: Context, private val treeUri: Uri, onFound: (Int) -> Unit = {}) : ModFiles {
    private val resolver = context.contentResolver
    private val entries = LinkedHashMap<String, Pair<String, Long>>()

    init {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<Pair<String, String>>()
        pending += rootId to ""
        while (pending.isNotEmpty()) {
            val (id, prefix) = pending.removeFirst()
            resolver.children(treeUri, id).forEach { child ->
                if (child.isDirectory) {
                    pending += child.id to "$prefix${child.name}/"
                } else {
                    entries["$prefix${child.name}"] = child.id to child.size
                    if (entries.size % 50 == 0) onFound(entries.size)
                }
            }
        }
    }

    override val paths: List<String> = entries.keys.sorted()

    override fun open(path: String): InputStream {
        val id = entries[path]?.first ?: throw IOException("Missing file $path")
        return resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
            ?: throw IOException("Could not open $path")
    }

    override fun size(path: String): Long = entries[path]?.second ?: 0L
}

class TreeModWriter(context: Context, private val treeUri: Uri) : ModWriter {
    private val resolver = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    private val directories = HashMap<String, String>().apply { put("", rootId) }
    private val listings = HashMap<String, MutableMap<String, Child>>()

    override fun writeText(path: String, text: String) {
        output(path).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun copy(path: String, source: () -> InputStream) {
        source().use { input -> output(path).use { input.copyTo(it, 64 * 1024) } }
    }

    private fun output(path: String) =
        resolver.openOutputStream(fileUri(path), "wt") ?: throw IOException("Could not write $path")

    private fun listing(directoryId: String): MutableMap<String, Child> =
        listings.getOrPut(directoryId) {
            resolver.children(treeUri, directoryId).associateByTo(HashMap()) { it.name }
        }

    private fun directory(path: String): String {
        directories[path]?.let { return it }
        val parentPath = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val parentId = directory(parentPath)
        val existing = listing(parentId)[name]
        val id = when {
            existing == null -> {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                val created = DocumentsContract.createDocument(resolver, parentUri, Document.MIME_TYPE_DIR, name)
                    ?: throw IOException("Could not create the folder $path")
                val createdId = DocumentsContract.getDocumentId(created)
                listing(parentId)[name] = Child(createdId, name, true, 0)
                createdId
            }
            existing.isDirectory -> existing.id
            else -> throw IOException("\"$path\" exists but is not a folder.")
        }
        directories[path] = id
        return id
    }

    private fun fileUri(path: String): Uri {
        val parentId = directory(path.substringBeforeLast('/', ""))
        val name = path.substringAfterLast('/')
        val existing = listing(parentId)[name]
        if (existing != null) {
            if (existing.isDirectory) throw IOException("\"$path\" exists but is a folder.")
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, existing.id)
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        val created = DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", name)
            ?: throw IOException("Could not create $path")
        listing(parentId)[name] = Child(DocumentsContract.getDocumentId(created), name, false, 0)
        return created
    }
}
