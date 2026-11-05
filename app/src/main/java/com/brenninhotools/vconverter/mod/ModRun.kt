package com.brenninhotools.vconverter.mod

import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.platform.InputStream
import com.brenninhotools.vconverter.platform.Platform
import com.brenninhotools.vconverter.platform.PlatformLock

class ModCancelledException : RuntimeException("The conversion was cancelled.")

class ModAbortedException(message: String) : RuntimeException(message)

class ModCancel {
    private val lock = PlatformLock()
    private var flag = false
    private var abort: String? = null

    val cancelled: Boolean get() = lock.withLock { flag }

    fun cancel() {
        lock.withLock { flag = true }
    }

    internal fun abort(message: String) {
        lock.withLock { if (abort == null) abort = message }
    }

    fun check() {
        val (stop, reason) = lock.withLock { flag to abort }
        if (stop) throw ModCancelledException()
        if (reason != null) throw ModAbortedException(reason)
    }
}

enum class ModCategory(val label: String) {
    CHARTS("Charts"),
    AUDIO("Audio"),
    CHARACTERS("Characters"),
    STAGES("Stages"),
    WEEKS("Weeks"),
    SCRIPTS("Scripts"),
    IMAGES("Images"),
    OTHER("Other");

    companion object {
        private val audio = setOf("ogg", "mp3", "wav", "flac", "m4a")
        private val images = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
        private val scripts = setOf("lua", "hx", "hxc", "hxs", "hscript", "hsc")

        fun of(path: String): ModCategory {
            val lower = "/" + path.lowercase().replace('\\', '/').trimStart('/')
            val ext = lower.substringAfterLast('/').substringAfterLast('.', "")
            return when {
                ext in audio -> AUDIO
                ext in scripts -> SCRIPTS
                ext in images || (ext == "xml" && "/images/" in lower) || (ext == "json" && "/images/" in lower) -> IMAGES
                "/characters/" in lower || "/characters_menu/" in lower -> CHARACTERS
                "/stages/" in lower -> STAGES
                "/weeks/" in lower || "/levels/" in lower -> WEEKS
                "/songs/" in lower || "/charts/" in lower || (lower.startsWith("/data/") && ext == "json" && lower.count { it == '/' } == 3) -> CHARTS
                else -> OTHER
            }
        }

        fun phase(label: String): String {
            val word = label.substringBefore(' ')
            return when (word) {
                "Song", "Chart" -> CHARTS.label
                "Character" -> CHARACTERS.label
                "Stage" -> STAGES.label
                "Week", "Level" -> WEEKS.label
                "Script" -> SCRIPTS.label
                "Done" -> "Finishing"
                "Extracting", "Reading", "Opening", "Scanning" -> "Reading"
                else -> if ('/' in label || '.' in label) of(label).label else label
            }
        }
    }
}

class ModRun(
    private val sink: (ModProgress) -> Unit,
    val cancel: ModCancel = ModCancel(),
    private val maxFailuresInRow: Int = 25
) {
    private val lock = PlatformLock()
    private val start = Platform.nanoTime()
    private val counts = LinkedHashMap<ModCategory, Int>()
    private val failures = ArrayList<String>()
    private val recent = ArrayDeque<String>()
    private var written = 0
    private var failuresInRow = 0
    private var done = 0
    private var total = 0
    private var label = "Starting…"
    private var lastEmit = 0L

    val progress: (ModProgress) -> Unit = { update ->
        cancel.check()
        lock.withLock {
            done = update.done
            total = update.total
            label = update.label
        }
        emit(force = update.done >= update.total && update.total > 0)
    }

    fun writer(base: ModWriter): ModWriter = TrackedWriter(base)

    fun execute(
        base: ModWriter,
        wrap: (ModWriter) -> ModWriter = { it },
        convert: (ModWriter, (ModProgress) -> Unit) -> ModConversionResult
    ): ModConversionResult {
        val outer = wrap(writer(base))
        val result = try {
            convert(outer, progress)
        } finally {
            (outer as? AutoCloseable)?.close()
        }
        cancel.check()
        emit(force = true, finished = true)
        return finish(result)
    }

    private fun finish(result: ModConversionResult): ModConversionResult {
        val (skippedLines, byCategory, seconds) = lock.withLock {
            Triple(failures.toList(), counts.toMap(), (Platform.nanoTime() - start) / 1_000_000_000.0)
        }
        val report = ReportBuilder()
        if (skippedLines.isNotEmpty()) {
            report.stat("Files skipped", skippedLines.size)
            val shown = if (skippedLines.size <= 40) skippedLines else skippedLines.take(40) + "…and ${skippedLines.size - 40} more"
            report.section("Files skipped because of errors", shown)
            report.warn("${skippedLines.size} file(s) could not be written and were skipped; the rest of the mod was converted.")
        }
        val breakdown = ModCategory.entries.mapNotNull { category -> byCategory[category]?.takeIf { it > 0 }?.let { "${category.label}: $it" } }
        if (breakdown.isNotEmpty()) report.section("Files written", breakdown + "Time: ${formatSeconds(seconds)}")
        return ModConversionResult(result.filesWritten, result.report + report.build())
    }

    private fun record(path: String) {
        val category = ModCategory.of(path)
        lock.withLock {
            written++
            failuresInRow = 0
            counts[category] = (counts[category] ?: 0) + 1
            recent.addLast(path.substringAfterLast('/'))
            while (recent.size > 6) recent.removeFirst()
        }
        emit()
    }

    private fun fail(path: String, error: Throwable) {
        val stop = lock.withLock {
            failures += "$path: ${error.message ?: error::class.simpleName ?: "error"}"
            failuresInRow++
            failuresInRow >= maxFailuresInRow
        }
        if (stop) {
            cancel.abort("$maxFailuresInRow files in a row could not be written (${error.message ?: "unknown error"}). The output folder or zip may be unavailable.")
            cancel.check()
        }
        emit()
    }

    private fun emit(force: Boolean = false, finished: Boolean = false) {
        val now = Platform.nanoTime()
        val snapshot = lock.withLock {
            if (!force && now - lastEmit < 40_000_000L) return@withLock null
            lastEmit = now
            val elapsed = (now - start) / 1_000_000
            val ratio = if (total > 0) done.toDouble() / total else 0.0
            val eta = if (!finished && ratio in 0.03..0.999 && elapsed > 1200) ((elapsed / ratio) - elapsed).toLong().coerceAtLeast(0) else null
            ModProgress(
                done = if (finished) total else done,
                total = total,
                label = if (finished) "Done" else label,
                phase = if (finished) "Finishing" else ModCategory.phase(label),
                written = written,
                skipped = failures.size,
                counts = counts.toMap(),
                elapsedMs = elapsed,
                etaMs = eta,
                recent = recent.toList()
            )
        }
        sink(snapshot ?: return)
    }

    private inner class TrackedWriter(private val base: ModWriter) : ModWriter {
        override fun writeText(path: String, text: String) {
            cancel.check()
            try {
                base.writeText(path, text)
            } catch (e: ModCancelledException) {
                throw e
            } catch (e: ModAbortedException) {
                throw e
            } catch (e: Exception) {
                fail(path, e)
                return
            }
            record(path)
        }

        override fun copy(path: String, source: () -> InputStream) {
            cancel.check()
            try {
                base.copy(path, source)
            } catch (e: ModCancelledException) {
                throw e
            } catch (e: ModAbortedException) {
                throw e
            } catch (e: Exception) {
                fail(path, e)
                return
            }
            record(path)
        }
    }

    companion object {
        fun formatSeconds(seconds: Double): String {
            val total = seconds.toLong()
            return if (total < 60) "${((seconds * 10).toLong() / 10.0)}s" else "${total / 60}m ${total % 60}s"
        }

        fun formatMillis(ms: Long): String {
            val s = (ms + 999) / 1000
            return if (s < 60) "${s}s" else "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
        }
    }
}
