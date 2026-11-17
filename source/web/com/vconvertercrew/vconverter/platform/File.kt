package com.vconvertercrew.vconverter.platform

internal object MemoryDisk {
    val files = HashMap<String, ByteArray>()
    val folders = HashSet<String>()
}

class File(path: String) {
    val path: String = normalize(path)

    constructor(parent: File, child: String) : this(if (child.startsWith("/")) child else parent.path.trimEnd('/') + "/" + child)

    constructor(parent: String, child: String) : this(File(parent), child)

    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val absolutePath: String get() = path
    val absoluteFile: File get() = this
    val canonicalFile: File get() = this
    val canonicalPath: String get() = path
    val parentFile: File? get() = parentOf(path)?.let { File(it) }
    val parent: String? get() = parentFile?.path
    val extension: String get() = name.substringAfterLast('.', "")
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val invariantSeparatorsPath: String get() = path

    val isDirectory: Boolean get() = path == "/" || path in MemoryDisk.folders

    val isFile: Boolean get() = path in MemoryDisk.files

    fun exists(): Boolean = isFile || isDirectory

    fun length(): Long = MemoryDisk.files[path]?.size?.toLong() ?: 0L

    fun mkdirs(): Boolean {
        if (isFile) return false
        var current: String? = path
        while (current != null && current != "/" && current.isNotEmpty()) {
            MemoryDisk.folders.add(current)
            current = parentOf(current)
        }
        return true
    }

    fun delete(): Boolean = exists().also { deleteRecursively() }

    fun deleteRecursively(): Boolean {
        val prefix = path.trimEnd('/') + "/"
        MemoryDisk.files.keys.removeAll { it == path || it.startsWith(prefix) }
        MemoryDisk.folders.removeAll { it == path || it.startsWith(prefix) }
        return true
    }

    fun list(): Array<String>? {
        if (!isDirectory) return null
        val prefix = path.trimEnd('/') + "/"
        val names = LinkedHashSet<String>()
        (MemoryDisk.files.keys.asSequence() + MemoryDisk.folders.asSequence()).forEach { key ->
            if (key.startsWith(prefix) && key.length > prefix.length) names += key.substring(prefix.length).substringBefore('/')
        }
        return names.toTypedArray()
    }

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

    fun readBytes(): ByteArray = MemoryDisk.files[path]?.copyOf() ?: throw IOException("Could not open $name.")

    fun readText(): String = readBytes().decodeToString()

    fun writeBytes(bytes: ByteArray) {
        parentFile?.mkdirs()
        MemoryDisk.files[path] = bytes.copyOf()
    }

    fun writeText(text: String) = writeBytes(text.encodeToByteArray())

    fun inputStream(): InputStream = FileInputStream(this)

    fun outputStream(): OutputStream = FileOutputStream(this)

    override fun equals(other: Any?): Boolean = other is File && other.path == path

    override fun hashCode(): Int = path.hashCode()

    override fun toString(): String = path

    companion object {
        private fun normalize(raw: String): String {
            var clean = raw.removePrefix("file://").replace('\\', '/')
            while ("//" in clean) clean = clean.replace("//", "/")
            return if (clean.length > 1) clean.trimEnd('/') else clean
        }

        private fun parentOf(path: String): String? = path.trimEnd('/').substringBeforeLast('/', "").takeIf { it.isNotEmpty() }

        val temporaryDirectory: File get() = File("/tmp").apply { mkdirs() }

        val documentsDirectory: File get() = File("/documents").apply { mkdirs() }

        internal fun data(file: File): ByteArray? = MemoryDisk.files[file.path]
    }
}

class FileInputStream(file: File) : InputStream() {
    private val inner = ByteArrayInputStream(File.data(file) ?: throw IOException("Could not open ${file.name}."))

    override fun read(): Int = inner.read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = inner.read(buffer, offset, length)

    override fun skip(count: Long): Long = inner.skip(count)

    override fun available(): Int = inner.available()
}

class FileOutputStream(private val file: File) : OutputStream() {
    private val buffer = ByteArrayOutputStream()
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        if (file.isDirectory) throw IOException("Could not write ${file.name}.")
        MemoryDisk.files[file.path] = ByteArray(0)
    }

    override fun write(b: Int) {
        buffer.write(b)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        this.buffer.write(buffer, offset, length)
    }

    override fun flush() {
        MemoryDisk.files[file.path] = buffer.toByteArray()
    }

    override fun close() {
        if (!closed) {
            closed = true
            flush()
        }
    }
}

class FileSource(private val file: File) : RandomSource {
    private val data: ByteArray = File.data(file) ?: throw IOException("Could not open ${file.name}.")

    override val size: Long = data.size.toLong()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        if (position < 0 || position + length > data.size) throw IOException("${file.name} ends early.")
        data.copyInto(buffer, offset, position.toInt(), position.toInt() + length)
    }

    override fun close() {}
}
