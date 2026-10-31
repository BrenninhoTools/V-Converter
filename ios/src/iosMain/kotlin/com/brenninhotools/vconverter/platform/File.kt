package com.brenninhotools.vconverter.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.fgetc
import platform.posix.fputc
import platform.posix.EOF as POSIX_EOF
import kotlinx.cinterop.BooleanVar

class File(path: String) {
    val path: String = normalize(path)

    constructor(parent: File, child: String) : this(if (child.startsWith("/")) child else parent.path.trimEnd('/') + "/" + child)

    constructor(parent: String, child: String) : this(File(parent), child)

    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val absolutePath: String get() = path
    val absoluteFile: File get() = this
    val canonicalFile: File get() = this
    val canonicalPath: String get() = path
    val parentFile: File? get() = path.trimEnd('/').substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { File(it) }
    val parent: String? get() = parentFile?.path
    val extension: String get() = name.substringAfterLast('.', "")
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val invariantSeparatorsPath: String get() = path

    val isDirectory: Boolean
        get() = memScoped {
            val flag = alloc<BooleanVar>()
            NSFileManager.defaultManager.fileExistsAtPath(path, flag.ptr) && flag.value
        }

    val isFile: Boolean
        get() = memScoped {
            val flag = alloc<BooleanVar>()
            NSFileManager.defaultManager.fileExistsAtPath(path, flag.ptr) && !flag.value
        }

    fun exists(): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    fun length(): Long {
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return 0L
        return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
    }

    fun mkdirs(): Boolean = NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)

    fun delete(): Boolean = NSFileManager.defaultManager.removeItemAtPath(path, null)

    fun deleteRecursively(): Boolean = !exists() || delete()

    fun list(): Array<String>? = (NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null) as? List<*>)?.map { it.toString() }?.toTypedArray()

    fun listFiles(): Array<File>? = list()?.map { File(this, it) }?.toTypedArray()

    fun walkTopDown(): Sequence<File> = sequence {
        yield(this@File)
        if (isDirectory) {
            listFiles()?.sortedBy { it.name }?.forEach { child -> yieldAll(child.walkTopDown()) }
        }
    }

    fun relativeTo(base: File): File {
        val root = base.path.trimEnd('/') + "/"
        return File(if (path.startsWith(root)) path.removePrefix(root) else path)
    }

    fun readBytes(): ByteArray = inputStream().use { it.readBytes() }

    fun readText(): String = readBytes().decodeToString()

    fun writeBytes(bytes: ByteArray) {
        parentFile?.mkdirs()
        outputStream().use { it.write(bytes) }
    }

    fun writeText(text: String) = writeBytes(text.encodeToByteArray())

    fun inputStream(): InputStream = FileInputStream(this)

    fun outputStream(): OutputStream = FileOutputStream(this)

    override fun equals(other: Any?): Boolean = other is File && other.path == path

    override fun hashCode(): Int = path.hashCode()

    override fun toString(): String = path

    companion object {
        private fun normalize(raw: String): String {
            val clean = raw.removePrefix("file://").replace("//", "/")
            return if (clean.length > 1) clean.trimEnd('/') else clean
        }

        val temporaryDirectory: File get() = File(NSTemporaryDirectory())

        val documentsDirectory: File
            get() = File((NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: NSTemporaryDirectory())
    }
}

class FileInputStream(file: File) : InputStream() {
    private val handle: CPointer<FILE> = fopen(file.path, "rb") ?: throw IOException("Could not open ${file.name}.")
    private var closed = false

    override fun read(): Int {
        if (closed) return -1
        val c = fgetc(handle)
        return if (c == POSIX_EOF) -1 else c and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) return -1
        if (length == 0) return 0
        val n = buffer.usePinned { pinned -> fread(pinned.addressOf(offset), 1u, length.toULong(), handle).toInt() }
        return if (n <= 0) -1 else n
    }

    override fun close() {
        if (!closed) {
            closed = true
            fclose(handle)
        }
    }
}

class FileOutputStream(file: File) : OutputStream() {
    private val handle: CPointer<FILE>
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        handle = fopen(file.path, "wb") ?: throw IOException("Could not write ${file.name}.")
    }

    override fun write(b: Int) {
        fputc(b and 0xFF, handle)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        val written = buffer.usePinned { pinned -> fwrite(pinned.addressOf(offset), 1u, length.toULong(), handle).toInt() }
        if (written < length) throw IOException("The disk is full or the file can't be written.")
    }

    override fun flush() {
        fflush(handle)
    }

    override fun close() {
        if (!closed) {
            closed = true
            fclose(handle)
        }
    }
}
