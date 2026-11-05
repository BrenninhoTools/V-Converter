package com.brenninhotools.vconverter.mod

import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ParallelModWriter(
    private val base: ModWriter,
    threads: Int = (Runtime.getRuntime().availableProcessors()).coerceIn(2, 4)
) : ModWriter, AutoCloseable {
    private val pool: ExecutorService = Executors.newFixedThreadPool(threads) { task ->
        Thread(task, "mod-writer").apply { isDaemon = true }
    }
    private val slots = Semaphore(threads * 2)
    private val error = AtomicReference<Throwable?>(null)
    private val pending = HashSet<String>()
    private val lock = Any()

    override fun writeText(path: String, text: String) {
        rethrow()
        waitFor(path)
        base.writeText(path, text)
    }

    override fun copy(path: String, source: () -> InputStream) {
        rethrow()
        waitFor(path)
        val input = source()
        slots.acquire()
        synchronized(lock) { pending += key(path) }
        try {
            pool.execute {
                try {
                    if (error.get() == null) base.copy(path) { input }
                } catch (e: Throwable) {
                    error.compareAndSet(null, e)
                } finally {
                    runCatching { input.close() }
                    synchronized(lock) {
                        pending -= key(path)
                        (lock as Object).notifyAll()
                    }
                    slots.release()
                }
            }
        } catch (e: Throwable) {
            runCatching { input.close() }
            synchronized(lock) { pending -= key(path) }
            slots.release()
            throw e
        }
    }

    private fun key(path: String) = path.replace('\\', '/').trimStart('/').lowercase()

    private fun waitFor(path: String) {
        val key = key(path)
        synchronized(lock) {
            while (key in pending) (lock as Object).wait(50)
        }
    }

    private fun rethrow() {
        error.get()?.let { throw it }
    }

    override fun close() {
        pool.shutdown()
        while (!pool.awaitTermination(100, TimeUnit.MILLISECONDS)) Unit
        rethrow()
    }
}
