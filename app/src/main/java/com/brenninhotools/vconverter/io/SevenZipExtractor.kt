package com.brenninhotools.vconverter.io

import com.brenninhotools.vconverter.mod.SafePaths
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

object SevenZipExtractor {
    fun extract(archive: File, destination: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        destination.mkdirs()
        val root = destination.canonicalFile
        RandomAccessFile(archive, "r").use { file ->
            val stream = RandomAccessFileInStream(file)
            val inArchive = try {
                SevenZip.openInArchive(null, stream)
            } catch (e: SevenZipException) {
                stream.close()
                throw IOException("This archive could not be opened. It may be damaged, split into parts or password protected.", e)
            }
            try {
                val count = inArchive.numberOfItems
                val targets = arrayOfNulls<File>(count)
                val indices = mutableListOf<Int>()
                for (i in 0 until count) {
                    if (inArchive.getProperty(i, PropID.IS_FOLDER) == true) continue
                    val safe = SafePaths.normalize(inArchive.getStringProperty(i, PropID.PATH) ?: continue) ?: continue
                    val target = File(root, safe)
                    if (!target.canonicalPath.startsWith(root.path + File.separator)) continue
                    targets[i] = target
                    indices += i
                }
                val callback = Callback(targets, indices.size, onProgress)
                try {
                    inArchive.extract(indices.toIntArray(), false, callback)
                } catch (e: SevenZipException) {
                    throw IOException(callback.failure ?: "Extraction failed: ${e.message}", e)
                } finally {
                    callback.closeCurrent()
                }
                return callback.done
            } finally {
                inArchive.close()
                stream.close()
            }
        }
    }

    private class Callback(
        private val targets: Array<File?>,
        private val total: Int,
        private val onProgress: (Int, Int) -> Unit
    ) : IArchiveExtractCallback {
        private var current: BufferedOutputStream? = null
        var done = 0
            private set
        var failure: String? = null
            private set

        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            closeCurrent()
            if (extractAskMode != ExtractAskMode.EXTRACT) return null
            val target = targets.getOrNull(index) ?: return null
            target.parentFile?.mkdirs()
            val output = BufferedOutputStream(target.outputStream(), 64 * 1024)
            current = output
            return ISequentialOutStream { data ->
                output.write(data)
                data.size
            }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode) {
        }

        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            closeCurrent()
            if (extractOperationResult != ExtractOperationResult.OK) {
                val code = extractOperationResult.toString().uppercase()
                failure = when {
                    "PASSWORD" in code -> "This archive is password protected."
                    "UNSUPPORTED" in code -> "This archive uses a compression method that is not supported."
                    else -> "The archive is damaged (${code.lowercase()})."
                }
                throw SevenZipException(failure)
            }
            done++
            onProgress(done, total)
        }

        override fun setTotal(total: Long) {
        }

        override fun setCompleted(complete: Long) {
        }

        fun closeCurrent() {
            runCatching { current?.close() }
            current = null
        }
    }
}
