package com.psiwray.organization.terminal_window_cmdline.pty

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Child-process session backed by ordinary pipes.
 *
 * Standard error is merged into standard output so ordering is preserved.
 * The child is not attached to a terminal, so [isTty] is false and [resize]
 * returns false. This backend is explicit: callers must select pipe mode.
 *
 * @param command command and arguments; the first element is the executable.
 * @param workingDirectory optional child working directory.
 * @param environment variables overlaid on the inherited parent environment.
 * @throws IOException if the process cannot be started.
 */
public class PipeSession(
    command: List<String>,
    workingDirectory: Path? = null,
    environment: Map<String, String> = emptyMap(),
) : ProcessSession {
    private val process: Process
    private val input: InputStream
    private val output: OutputStream
    private val outputLock: Any = Any()
    private val exitLock: Any = Any()
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private var cachedExit: ProcessExit? = null

    init {
        require(command.isNotEmpty()) { "command must not be empty" }
        require(command.first().isNotBlank()) { "executable must not be blank" }
        val builder = ProcessBuilder(command)
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile())
        }
        builder.environment().putAll(environment)
        builder.redirectErrorStream(true)
        process = builder.start()
        input = process.inputStream
        output = process.outputStream
    }

    override val isTty: Boolean
        get() = false

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = input.read(buffer, offset, length)

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (closed.get()) {
            throw IOException("pipe session is closed")
        }
        synchronized(outputLock) {
            output.write(buffer, offset, length)
            output.flush()
        }
    }

    override fun resize(size: TerminalSize): Boolean = false

    override fun requestInterrupt() {
        if (!closed.get()) {
            process.destroy()
        }
    }

    override fun requestTerminate() {
        if (!closed.get()) {
            process.destroyForcibly()
        }
    }

    override val exit: ProcessExit?
        get() =
            synchronized(exitLock) {
                cachedExit ?: run {
                    val snapshot = snapshotExit()
                    if (snapshot != null) {
                        cachedExit = snapshot
                    }
                    snapshot
                }
            }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            output.close()
        } catch (_: IOException) {
            // The child may already have closed its input side.
        }
        process.destroy()
        try {
            if (!process.waitFor(CLOSE_GRACE_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(CLOSE_GRACE_SECONDS, TimeUnit.SECONDS)
            }
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        }
        closeQuietly(input)
        closeQuietly(process.errorStream)
        synchronized(exitLock) {
            if (cachedExit == null) {
                cachedExit = snapshotExit()
            }
        }
    }

    private fun snapshotExit(): ProcessExit? =
        try {
            if (process.isAlive) {
                null
            } else {
                ProcessExit(process.exitValue())
            }
        } catch (_: IllegalThreadStateException) {
            null
        }

    private fun closeQuietly(stream: InputStream) {
        try {
            stream.close()
        } catch (_: IOException) {
            // Closing after process termination is best effort.
        }
    }

    private companion object {
        private const val CLOSE_GRACE_SECONDS: Long = 2L
    }
}
