package com.psiwray.organization.terminal_window_cmdline.pty.windows

import com.psiwray.organization.terminal_window_cmdline.pty.ProcessExit
import com.psiwray.organization.terminal_window_cmdline.pty.ProcessSession
import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/**
 * Windows ConPTY session backed by pure FFM.
 *
 * The child is started with `CreateProcessW` inside a pseudoconsole. The host
 * owns the input write end and output read end; the console owns the opposite
 * ends. Bytes are UTF-8 VT output as delivered by ConPTY. Closing the session
 * terminates and reaps the child. When a reader is active, final output is
 * drained before the pseudoconsole is released.
 *
 * @param command command and arguments; the first element is the executable.
 * @param workingDirectory optional child working directory.
 * @param environment variables overlaid on the inherited parent environment.
 * @param size initial console size.
 * @throws IOException if ConPTY or the child cannot be created.
 * @throws UnsupportedOperationException on a non-Windows host or before Windows
 *   10 version 1809.
 */
public class ConPtySession(
    command: List<String>,
    workingDirectory: Path? = null,
    environment: Map<String, String> = emptyMap(),
    size: TerminalSize = TerminalSize(80, 24),
) : ProcessSession {
    private val arena: Arena = Arena.ofShared()
    private val readBuffer: MemorySegment = arena.allocate(READ_BUFFER_BYTES.toLong(), 8)
    private val writeBuffer: MemorySegment = arena.allocate(WRITE_BUFFER_BYTES.toLong(), 8)
    private val readCount: MemorySegment = arena.allocate(ValueLayout.JAVA_INT)
    private val writeCount: MemorySegment = arena.allocate(ValueLayout.JAVA_INT)
    private val readLock: ReentrantLock = ReentrantLock()
    private val writeLock: ReentrantLock = ReentrantLock()
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val readerStarted: AtomicBoolean = AtomicBoolean(false)
    private val readerFinished: CountDownLatch = CountDownLatch(1)
    private val pseudoCloseStarted: AtomicBoolean = AtomicBoolean(false)
    private val exitValue: AtomicReference<ProcessExit?> = AtomicReference(null)

    private val inputHandle: MemorySegment
    private val outputHandle: MemorySegment
    private val processHandle: MemorySegment
    private val processId: Int
    private val pseudoConsole: MemorySegment
    private val watcher: Thread

    init {
        try {
            require(command.isNotEmpty()) { "command must not be empty" }
            require(command.first().isNotBlank()) { "executable must not be blank" }
            require(size.columns <= Short.MAX_VALUE) { "columns exceed ConPTY limit: ${size.columns}" }
            require(size.rows <= Short.MAX_VALUE) { "rows exceed ConPTY limit: ${size.rows}" }
            val spawned = spawn(command, workingDirectory, environment, size)
            inputHandle = spawned.inputHandle
            outputHandle = spawned.outputHandle
            processHandle = spawned.processHandle
            processId = spawned.processId
            pseudoConsole = spawned.pseudoConsole
            watcher =
                thread(name = "terminal-conpty-watcher", isDaemon = false, start = false) {
                    watch()
                }
            watcher.start()
        } catch (failure: Throwable) {
            runCatching { arena.close() }
            throw failure
        }
    }

    override val isTty: Boolean
        get() = true

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length) {
            "offset=$offset length=$length size=${buffer.size}"
        }
        if (length == 0) {
            return 0
        }
        if (closed.get()) {
            return -1
        }
        readerStarted.set(true)
        readLock.lock()
        try {
            if (closed.get()) {
                readerFinished.countDown()
                return -1
            }
            val request = minOf(length, READ_BUFFER_BYTES)
            readCount.set(ValueLayout.JAVA_INT, 0L, 0)
            val success =
                WindowsApi.readFile.invokeExact(
                    outputHandle,
                    readBuffer,
                    request,
                    readCount,
                    MemorySegment.NULL,
                ) as Int
            if (success != 0) {
                val count = readCount.get(ValueLayout.JAVA_INT, 0L)
                if (count <= 0) {
                    readerFinished.countDown()
                    return -1
                }
                MemorySegment.copy(
                    readBuffer,
                    ValueLayout.JAVA_BYTE,
                    0L,
                    buffer,
                    offset,
                    count,
                )
                return count
            }
            val code = WindowsApi.lastError()
            if (closed.get() || isEndOfStream(code)) {
                readerFinished.countDown()
                return -1
            }
            throw IOException(WindowsApi.formatError(code))
        } catch (failure: Throwable) {
            readerFinished.countDown()
            if (closed.get()) {
                return -1
            }
            if (failure is IOException) {
                throw failure
            }
            throw IOException("ReadFile failed: ${failure.message}", failure)
        } finally {
            readLock.unlock()
        }
    }

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length) {
            "offset=$offset length=$length size=${buffer.size}"
        }
        if (length == 0) {
            return
        }
        if (closed.get()) {
            throw IOException("ConPTY session is closed")
        }
        writeLock.lock()
        try {
            if (closed.get()) {
                throw IOException("ConPTY session is closed")
            }
            var sourceOffset = offset
            var remaining = length
            while (remaining > 0) {
                val chunk = minOf(remaining, WRITE_BUFFER_BYTES)
                MemorySegment.copy(buffer, sourceOffset, writeBuffer, ValueLayout.JAVA_BYTE, 0L, chunk)
                writeCount.set(ValueLayout.JAVA_INT, 0L, 0)
                val success =
                    WindowsApi.writeFile.invokeExact(
                        inputHandle,
                        writeBuffer,
                        chunk,
                        writeCount,
                        MemorySegment.NULL,
                    ) as Int
                if (success == 0) {
                    val code = WindowsApi.lastError()
                    throw IOException(WindowsApi.formatError(code))
                }
                val written = writeCount.get(ValueLayout.JAVA_INT, 0L)
                if (written <= 0) {
                    throw IOException("WriteFile accepted no bytes")
                }
                sourceOffset += written
                remaining -= written
            }
        } catch (failure: Throwable) {
            if (failure is IOException) {
                throw failure
            }
            throw IOException("WriteFile failed: ${failure.message}", failure)
        } finally {
            writeLock.unlock()
        }
    }

    override fun resize(size: TerminalSize): Boolean {
        if (closed.get()) {
            return false
        }
        if (size.columns > Short.MAX_VALUE || size.rows > Short.MAX_VALUE) {
            return false
        }
        val local = Arena.ofConfined()
        try {
            val coord = local.allocate(WindowsLayouts.COORD)
            coord.set(ValueLayout.JAVA_SHORT, 0L, size.columns.toShort())
            coord.set(ValueLayout.JAVA_SHORT, 2L, size.rows.toShort())
            val result = WindowsApi.resizePseudoConsole.invokeExact(pseudoConsole, coord) as Int
            return result == 0
        } catch (_: Throwable) {
            return false
        } finally {
            local.close()
        }
    }

    override fun requestInterrupt() {
        if (closed.get()) {
            return
        }
        try {
            write(CTRL_C, 0, CTRL_C.size)
        } catch (_: IOException) {
            // The console may already be gone.
        }
        broadcastConsoleInterrupt()
    }

    /**
     * ConPTY converts input `0x03` into a CTRL_C_EVENT only while the
     * pseudoconsole input mode has ENABLE_PROCESSED_INPUT. Hosts without a
     * console can lose that conversion. Attach briefly to the child's console
     * and deliver the equivalent control event directly.
     */
    private fun broadcastConsoleInterrupt() {
        try {
            val freed = runCatching { WindowsApi.freeConsole.invokeExact() as Int }.getOrDefault(-1)
            if (WindowsApi.attachConsole.invokeExact(processId) as Int == 0) {
                if (freed != 0) {
                    runCatching { WindowsApi.attachConsole.invokeExact(ATTACH_PARENT_PROCESS) as Int }
                }
                return
            }

            var conin = MemorySegment.NULL
            var originalMode: Int? = null
            try {
                val local = Arena.ofConfined()
                try {
                    val path = WindowsApi.allocateUtf16(local, "CONIN$")
                    conin =
                        WindowsApi.createFileW.invokeExact(
                            path,
                            GENERIC_READ_WRITE,
                            FILE_SHARE_READ_WRITE,
                            MemorySegment.NULL,
                            OPEN_EXISTING,
                            0,
                            MemorySegment.NULL,
                        ) as MemorySegment
                } finally {
                    local.close()
                }

                if (conin != MemorySegment.NULL && conin.address() != INVALID_HANDLE_VALUE) {
                    val modeArena = Arena.ofConfined()
                    try {
                        val mode = modeArena.allocate(ValueLayout.JAVA_INT)
                        val read = WindowsApi.getConsoleMode.invokeExact(conin, mode) as Int
                        if (read != 0) {
                            val current = mode.get(ValueLayout.JAVA_INT, 0L)
                            if (current and ENABLE_PROCESSED_INPUT == 0) {
                                originalMode = current
                                WindowsApi.setConsoleMode.invokeExact(
                                    conin,
                                    current or ENABLE_PROCESSED_INPUT,
                                ) as Int
                            }
                        }
                    } finally {
                        modeArena.close()
                    }
                }

                val ignored = WindowsApi.setConsoleCtrlHandler.invokeExact(MemorySegment.NULL, 1) as Int
                if (ignored != 0) {
                    WindowsApi.generateConsoleCtrlEvent.invokeExact(CTRL_C_EVENT, 0) as Int
                    Thread.sleep(50)
                }
            } finally {
                originalMode?.let { mode ->
                    runCatching { WindowsApi.setConsoleMode.invokeExact(conin, mode) as Int }
                }
                runCatching { WindowsApi.closeHandle.invokeExact(conin) as Int }
                runCatching {
                    WindowsApi.setConsoleCtrlHandler.invokeExact(MemorySegment.NULL, 0) as Int
                }
                runCatching { WindowsApi.freeConsole.invokeExact() as Int }
                if (freed != 0) {
                    runCatching { WindowsApi.attachConsole.invokeExact(ATTACH_PARENT_PROCESS) as Int }
                }
            }
        } catch (_: Throwable) {
            // The child may have exited or the host may not permit attaching.
        }
    }

    override fun requestTerminate() {
        if (closed.get()) {
            return
        }
        try {
            WindowsApi.terminateProcess.invokeExact(processHandle, 1) as Int
        } catch (_: Throwable) {
            // The process may have exited between the check and the call.
        }
    }

    override val exit: ProcessExit?
        get() = exitValue.get()

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        closeHandle(inputHandle)
        var outputClosed = false
        val readerActive = readerStarted.get() && readerFinished.count != 0L
        if (!readerActive) {
            // No reader can drain the final frame; close the host read end
            // before closing the pseudoconsole so the close cannot block.
            closeHandle(outputHandle)
            outputClosed = true
        }

        if (exitValue.get() == null) {
            val wait =
                try {
                    WindowsApi.waitForSingleObject.invokeExact(processHandle, CLOSE_GRACE_MILLIS) as Int
                } catch (_: Throwable) {
                    -1
                }
            if (wait != 0) {
                try {
                    WindowsApi.terminateProcess.invokeExact(processHandle, 1) as Int
                } catch (_: Throwable) {
                    // Already gone.
                }
                try {
                    WindowsApi.waitForSingleObject.invokeExact(processHandle, CLOSE_TERMINATE_MILLIS) as Int
                } catch (_: Throwable) {
                    // Best effort.
                }
            }
        }

        outputClosed = closePseudoConsole(readerActive) || outputClosed
        if (!outputClosed) {
            closeHandle(outputHandle)
        }
        if (Thread.currentThread() !== watcher) {
            try {
                watcher.join(WATCHER_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        closeHandle(processHandle)

        var writeLocked = false
        var readLocked = false
        try {
            writeLocked = writeLock.tryLock(CLOSE_DRAIN_MILLIS, TimeUnit.MILLISECONDS)
            if (writeLocked) {
                readLocked = readLock.tryLock(CLOSE_DRAIN_MILLIS, TimeUnit.MILLISECONDS)
            }
            if (readLocked) {
                runCatching { arena.close() }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            if (readLocked) {
                readLock.unlock()
            }
            if (writeLocked) {
                writeLock.unlock()
            }
        }
    }

    private fun watch() {
        val local = Arena.ofConfined()
        var exit = ProcessExit(-1)
        try {
            val wait = WindowsApi.waitForSingleObject.invokeExact(processHandle, INFINITE) as Int
            if (wait == 0) {
                val code = local.allocate(ValueLayout.JAVA_INT)
                val success = WindowsApi.getExitCodeProcess.invokeExact(processHandle, code) as Int
                if (success != 0) {
                    exit = ProcessExit(code.get(ValueLayout.JAVA_INT, 0L))
                }
            }
        } catch (_: Throwable) {
            exit = ProcessExit(-1)
        } finally {
            local.close()
        }
        exitValue.set(exit)
    }

    private fun closePseudoConsole(readerActive: Boolean): Boolean {
        if (!pseudoCloseStarted.compareAndSet(false, true)) {
            return false
        }
        var outputClosed = false
        // ClosePseudoConsole can block while it emits the final frame. Keep
        // the close bounded and let a daemon closer do the native call.
        val closer =
            thread(name = "terminal-conpty-close", isDaemon = true, start = false) {
                try {
                    WindowsApi.closePseudoConsole.invokeExact(pseudoConsole)
                } catch (_: Throwable) {
                    // Best effort.
                }
            }
        closer.start()
        if (readerActive) {
            try {
                readerFinished.await(CLOSE_DRAIN_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (readerFinished.count != 0L) {
                closeHandle(outputHandle)
                outputClosed = true
            }
        } else {
            closeHandle(outputHandle)
            outputClosed = true
        }
        try {
            closer.join(CLOSE_DRAIN_MILLIS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return outputClosed
    }

    private fun spawn(
        command: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>,
        size: TerminalSize,
    ): Spawned {
        val local = Arena.ofConfined()
        var inputRead = MemorySegment.NULL
        var inputWrite = MemorySegment.NULL
        var outputRead = MemorySegment.NULL
        var outputWrite = MemorySegment.NULL
        var pseudo = MemorySegment.NULL
        var process = MemorySegment.NULL
        var processThread = MemorySegment.NULL
        var attributeList: MemorySegment? = null
        try {
            val inputReadPointer = local.allocate(ValueLayout.ADDRESS)
            val inputWritePointer = local.allocate(ValueLayout.ADDRESS)
            val outputReadPointer = local.allocate(ValueLayout.ADDRESS)
            val outputWritePointer = local.allocate(ValueLayout.ADDRESS)
            check(
                "CreatePipe(input)",
                WindowsApi.createPipe.invokeExact(
                    inputReadPointer,
                    inputWritePointer,
                    MemorySegment.NULL,
                    0,
                ) as Int,
            )
            inputRead = inputReadPointer.get(ValueLayout.ADDRESS, 0L)
            inputWrite = inputWritePointer.get(ValueLayout.ADDRESS, 0L)
            check(
                "CreatePipe(output)",
                WindowsApi.createPipe.invokeExact(
                    outputReadPointer,
                    outputWritePointer,
                    MemorySegment.NULL,
                    0,
                ) as Int,
            )
            outputRead = outputReadPointer.get(ValueLayout.ADDRESS, 0L)
            outputWrite = outputWritePointer.get(ValueLayout.ADDRESS, 0L)

            val coord = local.allocate(WindowsLayouts.COORD)
            coord.set(ValueLayout.JAVA_SHORT, 0L, size.columns.toShort())
            coord.set(ValueLayout.JAVA_SHORT, 2L, size.rows.toShort())
            val pseudoPointer = local.allocate(ValueLayout.ADDRESS)
            val createResult =
                WindowsApi.createPseudoConsole.invokeExact(
                    coord,
                    inputRead,
                    outputWrite,
                    0,
                    pseudoPointer,
                ) as Int
            if (createResult != 0) {
                throw IOException("CreatePseudoConsole failed: ${WindowsApi.formatError(createResult)}")
            }
            pseudo = pseudoPointer.get(ValueLayout.ADDRESS, 0L)

            val attributeSize = local.allocate(ValueLayout.JAVA_LONG)
            WindowsApi.initializeProcThreadAttributeList.invokeExact(
                MemorySegment.NULL,
                1,
                0,
                attributeSize,
            ) as Int
            val required = attributeSize.get(ValueLayout.JAVA_LONG, 0L)
            if (required <= 0L) {
                throw IOException("InitializeProcThreadAttributeList returned no size")
            }
            val list = local.allocate(required, 8)
            check(
                "InitializeProcThreadAttributeList",
                WindowsApi.initializeProcThreadAttributeList.invokeExact(
                    list,
                    1,
                    0,
                    attributeSize,
                ) as Int,
            )
            attributeList = list
            check(
                "UpdateProcThreadAttribute",
                WindowsApi.updateProcThreadAttribute.invokeExact(
                    list,
                    0,
                    WindowsApi.procThreadAttributePseudoConsole,
                    pseudo,
                    8L,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                ) as Int,
            )

            val startup = local.allocate(WindowsLayouts.STARTUPINFOEXW)
            startup.set(ValueLayout.JAVA_INT, STARTUPINFO_CB_OFFSET, WindowsApi.startupInfoExSize)
            startup.set(ValueLayout.JAVA_INT, STARTUPINFO_DWFLAGS_OFFSET, STARTF_USESTDHANDLES)
            startup.set(ValueLayout.ADDRESS, STARTUPINFOEX_ATTRIBUTE_OFFSET, list)

            val commandLine = WindowsApi.allocateUtf16(local, CommandLine.build(command))
            val environmentBlock = EnvironmentBlock.build(environment)
            val environmentSegment = local.allocate(environmentBlock.size.toLong() * 2L, 2)
            MemorySegment.copy(
                environmentBlock,
                0,
                environmentSegment,
                ValueLayout.JAVA_SHORT,
                0L,
                environmentBlock.size,
            )
            val directory = workingDirectory?.toString()?.let { WindowsApi.allocateUtf16(local, it) }
            val processInfo = local.allocate(WindowsLayouts.PROCESS_INFORMATION)
            val flags = EXTENDED_STARTUPINFO_PRESENT or CREATE_UNICODE_ENVIRONMENT

            // SetConsoleCtrlHandler(NULL, TRUE) is inherited by child
            // processes. Clear it before CreateProcessW so the child handles
            // CTRL+C even when the host (for example a test worker) ignores it.
            runCatching {
                WindowsApi.setConsoleCtrlHandler.invokeExact(MemorySegment.NULL, 0) as Int
            }

            check(
                "CreateProcessW",
                WindowsApi.createProcessW.invokeExact(
                    MemorySegment.NULL,
                    commandLine,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    0,
                    flags,
                    environmentSegment,
                    directory ?: MemorySegment.NULL,
                    startup,
                    processInfo,
                ) as Int,
            )
            process = processInfo.get(ValueLayout.ADDRESS, PROCESS_INFORMATION_PROCESS_OFFSET)
            processThread = processInfo.get(ValueLayout.ADDRESS, PROCESS_INFORMATION_THREAD_OFFSET)
            val processId = processInfo.get(ValueLayout.JAVA_INT, PROCESS_INFORMATION_PROCESS_ID_OFFSET)
            closeHandle(processThread)
            processThread = MemorySegment.NULL

            val result = Spawned(pseudo, inputWrite, outputRead, process, processId)
            pseudo = MemorySegment.NULL
            inputWrite = MemorySegment.NULL
            outputRead = MemorySegment.NULL
            process = MemorySegment.NULL
            return result
        } catch (failure: IOException) {
            throw failure
        } catch (failure: UnsupportedOperationException) {
            throw failure
        } catch (failure: ExceptionInInitializerError) {
            val cause = failure.cause
            if (cause is UnsupportedOperationException) {
                throw cause
            }
            throw IOException("ConPTY spawn failed: ${failure.message}", failure)
        } catch (failure: Throwable) {
            throw IOException("ConPTY spawn failed: ${failure.message}", failure)
        } finally {
            attributeList?.let { runCatching { WindowsApi.deleteProcThreadAttributeList.invokeExact(it) } }
            closeHandle(processThread)
            if (process != MemorySegment.NULL) {
                runCatching { WindowsApi.terminateProcess.invokeExact(process, 1) as Int }
            }
            closeHandle(inputRead)
            closeHandle(inputWrite)
            closeHandle(outputRead)
            closeHandle(outputWrite)
            if (pseudo != MemorySegment.NULL) {
                runCatching { WindowsApi.closePseudoConsole.invokeExact(pseudo) }
            }
            local.close()
        }
    }

    private fun isEndOfStream(code: Int): Boolean =
        code == 0 || code == ERROR_BROKEN_PIPE || code == ERROR_PIPE_NOT_CONNECTED ||
            code == ERROR_HANDLE_EOF || code == ERROR_OPERATION_ABORTED

    private fun check(
        operation: String,
        result: Int,
    ) {
        if (result == 0) {
            throw IOException("$operation failed: ${WindowsApi.formatError(WindowsApi.lastError())}")
        }
    }

    private fun closeHandle(handle: MemorySegment) {
        if (handle == MemorySegment.NULL) {
            return
        }
        try {
            WindowsApi.closeHandle.invokeExact(handle) as Int
        } catch (_: Throwable) {
            // Best-effort cleanup.
        }
    }

    private data class Spawned(
        val pseudoConsole: MemorySegment,
        val inputHandle: MemorySegment,
        val outputHandle: MemorySegment,
        val processHandle: MemorySegment,
        val processId: Int,
    )

    private companion object {
        private const val READ_BUFFER_BYTES: Int = 64 * 1024
        private const val WRITE_BUFFER_BYTES: Int = 64 * 1024
        private const val STARTF_USESTDHANDLES: Int = 0x00000100
        private const val EXTENDED_STARTUPINFO_PRESENT: Int = 0x00080000
        private const val CREATE_UNICODE_ENVIRONMENT: Int = 0x00000400
        private const val INFINITE: Int = -1
        private const val ERROR_HANDLE_EOF: Int = 38
        private const val ERROR_BROKEN_PIPE: Int = 109
        private const val ERROR_PIPE_NOT_CONNECTED: Int = 233
        private const val ERROR_OPERATION_ABORTED: Int = 995
        private const val CLOSE_GRACE_MILLIS: Int = 250
        private const val CLOSE_TERMINATE_MILLIS: Int = 2_000
        private const val CLOSE_DRAIN_MILLIS: Long = 2_000L
        private const val WATCHER_JOIN_MILLIS: Long = 2_000L
        private const val STARTUPINFO_CB_OFFSET: Long = 0L
        private const val STARTUPINFO_DWFLAGS_OFFSET: Long = 60L
        private const val STARTUPINFOEX_ATTRIBUTE_OFFSET: Long = 104L
        private const val PROCESS_INFORMATION_PROCESS_OFFSET: Long = 0L
        private const val PROCESS_INFORMATION_THREAD_OFFSET: Long = 8L
        private const val PROCESS_INFORMATION_PROCESS_ID_OFFSET: Long = 16L
        private const val ATTACH_PARENT_PROCESS: Int = -1
        private const val GENERIC_READ_WRITE: Int = -0x40000000
        private const val FILE_SHARE_READ_WRITE: Int = 0x00000003
        private const val OPEN_EXISTING: Int = 3
        private const val ENABLE_PROCESSED_INPUT: Int = 0x0001
        private const val CTRL_C_EVENT: Int = 0
        private const val INVALID_HANDLE_VALUE: Long = -1L
        private val CTRL_C: ByteArray = byteArrayOf(3)
    }
}
