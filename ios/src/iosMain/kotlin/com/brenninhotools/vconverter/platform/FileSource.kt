package com.brenninhotools.vconverter.platform

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.FILE
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseeko

class FileSource(private val file: File) : RandomSource {
    private val handle: CPointer<FILE> = fopen(file.path, "rb") ?: throw IOException("Could not open ${file.name}.")

    override val size: Long = file.length()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        if (fseeko(handle, position, SEEK_SET) != 0) throw IOException("Could not read ${file.name}.")
        var done = 0
        buffer.usePinned { pinned ->
            while (done < length) {
                val n = fread(pinned.addressOf(offset + done), 1u, (length - done).toULong(), handle).toInt()
                if (n <= 0) throw IOException("${file.name} ends early.")
                done += n
            }
        }
    }

    override fun close() {
        fclose(handle)
    }
}
