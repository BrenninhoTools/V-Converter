package com.vconvertercrew.vconverter.online

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GameBananaDownloads {
    private const val TIMEOUT_MS = 20000

    fun toFile(file: GameBananaFile, folder: File, progress: (Float) -> Unit): File {
        folder.mkdirs()
        folder.listFiles()?.forEach { it.deleteRecursively() }
        val target = File(folder, GameBanana.safeFileName(file))
        val partial = File(folder, target.name + ".part")
        var url = file.url
        var redirects = 0
        while (true) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS * 3
                connection.setRequestProperty("User-Agent", "V-Converter")
                val status = connection.responseCode
                if (status in 300..399) {
                    val next = connection.getHeaderField("Location") ?: throw GameBananaException("GameBanana sent a broken redirect.")
                    url = URL(URL(url), next).toString()
                    if (++redirects > 6) throw GameBananaException("GameBanana redirected too many times.")
                    continue
                }
                if (status !in 200..299) throw GameBananaException("The download failed ($status).")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: file.size
                var done = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) progress(done.toFloat() / total)
                        }
                    }
                }
                if (done == 0L) throw GameBananaException("GameBanana sent an empty file.")
                target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                progress(1f)
                return target
            } finally {
                connection.disconnect()
            }
        }
    }
}
