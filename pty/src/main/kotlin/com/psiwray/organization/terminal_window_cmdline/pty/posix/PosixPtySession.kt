package com.psiwray.organization.terminal_window_cmdline.pty.posix

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
import kotlin.concurrent.thread

/**
 * POSIX pseudo-terminal session backed by pure FFM libc calls.
 *
 * The child is spawned with `posix_spawnp` in a new session and receives the
 * PTY slave as standard input, output, and error. The master is used for
 * blocking byte I/O, `TIOCSWINSZ` resize, and signals. No platform handles
 * escape this class.
 *
 * @param command command and arguments; the first element is the executable.
 * @param workingDirectory optional child working directory.
 * @param environment variables overlaid on the inherited parent environment.
 * @param size initial PTY window size.
 * @throws IOException if the PTY or child cannot be created.
 */
public class PosixPtySession(
    command: List<String>,
    workingDirectory: Path? = null,
    environment: Map<String, String> = emptyMap(),
    size: TerminalSize = TerminalSize(80, 24),
) : ProcessSession {
    private val arena: Arena = Arena.ofShared()
    private val masterFd: Int
    private val pid: Int
    private val readCapture: MemorySegment
    private val writeCapture: MemorySegment
    private val readBuffer: MemorySegment
    private val writeBuffer: MemorySegment
    private val writeLock: Any = Any()
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val exited: CountDownLatch = CountDownLatch(1)

    @Volatile
    private var exitValue: ProcessExit? = null

    @Volatile
    private var foregroundPgrp: Int = -1
    private val watcher: Thread

    init {
        PosixLibc.requireSupported()
        require(command.isNotEmpty()) { "command must not be empty" }
        require(command.first().isNotBlank()) { "executable must not be blank" }
        val spawned =
            try {
                spawn(command, workingDirectory, environment, size)
            } catch (failure: Throwable) {
                arena.close()
                throw failure
            }
        masterFd = spawned.masterFd
        pid = spawned.pid
        readCapture = arena.allocate(PosixLibc.captureLayout)
        writeCapture = arena.allocate(PosixLibc.captureLayout)
        readBuffer = arena.allocate(READ_BUFFER_BYTES.toLong(), 8)
        writeBuffer = arena.allocate(WRITE_BUFFER_BYTES.toLong(), 8)
        watcher =
            thread(name = "terminal-pty-watcher-$pid", isDaemon = false, start = false) {
                watch()
            }
        watcher.start()
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
        val request = minOf(length, READ_BUFFER_BYTES)
        while (true) {
            val count =
                try {
                    PosixLibc.readHandle.invokeExact(
                        readCapture,
                        masterFd,
                        readBuffer,
                        request.toLong(),
                    ) as Long
                } catch (failure: Throwable) {
                    throw IOException("read failed on ${PosixLibc.platformName}", failure)
                }
            if (count > 0) {
                MemorySegment.copy(readBuffer, ValueLayout.JAVA_BYTE, 0L, buffer, offset, count.toInt())
                return count.toInt()
            }
            if (count == 0L) {
                return -1
            }
            val errno = PosixLibc.errno(readCapture)
            if (errno == PosixLibc.eintr) {
                continue
            }
            if (errno == PosixLibc.eio || closed.get()) {
                return -1
            }
            throw IOException(PosixLibc.error("read", errno))
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
            throw IOException("PTY session is closed")
        }
        synchronized(writeLock) {
            var sourceOffset = offset
            var remaining = length
            while (remaining > 0) {
                val chunk = minOf(remaining, WRITE_BUFFER_BYTES)
                MemorySegment.copy(buffer, sourceOffset, writeBuffer, ValueLayout.JAVA_BYTE, 0L, chunk)
                val written =
                    try {
                        PosixLibc.writeHandle.invokeExact(
                            writeCapture,
                            masterFd,
                            writeBuffer,
                            chunk.toLong(),
                        ) as Long
                    } catch (failure: Throwable) {
                        throw IOException("write failed on ${PosixLibc.platformName}", failure)
                    }
                if (written > 0) {
                    sourceOffset += written.toInt()
                    remaining -= written.toInt()
                    continue
                }
                val errno = PosixLibc.errno(writeCapture)
                if (errno == PosixLibc.eintr) {
                    continue
                }
                throw IOException(PosixLibc.error("write", errno))
            }
        }
    }

    override fun resize(size: TerminalSize): Boolean {
        if (closed.get()) {
            return false
        }
        val local = Arena.ofConfined()
        try {
            val capture = local.allocate(PosixLibc.captureLayout)
            val winsize = local.allocate(WINSIZE_BYTES, 8)
            WinSize.write(winsize, size)
            val result = PosixLibc.ioctlHandle.invokeExact(capture, masterFd, PosixLibc.tiocswinsz, winsize) as Int
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
        val local = Arena.ofConfined()
        try {
            val capture = local.allocate(PosixLibc.captureLayout)
            val foreground = PosixLibc.tcgetpgrpHandle.invokeExact(capture, masterFd) as Int
            val target = if (foreground > 0) -foreground else -pid
            discard(PosixLibc.killHandle.invokeExact(capture, target, Signals.SIGINT) as Int)
        } catch (_: Throwable) {
            // The session may have exited between the check and the syscall.
        } finally {
            local.close()
        }
    }

    override fun requestTerminate() {
        if (!closed.get()) {
            sendSessionSignal(Signals.SIGTERM)
        }
    }

    override val exit: ProcessExit?
        get() = exitValue

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        sendSessionSignal(Signals.SIGHUP)
        if (!awaitExit(CLOSE_GRACE_MILLIS)) {
            sendSessionSignal(Signals.SIGTERM)
            if (!awaitExit(CLOSE_GRACE_MILLIS)) {
                sendSessionSignal(Signals.SIGKILL)
                awaitExit(CLOSE_GRACE_MILLIS)
            }
        }
        if (Thread.currentThread() !== watcher) {
            try {
                watcher.join(WATCHER_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        closeFd(masterFd)
        runCatching { arena.close() }
    }

    private fun awaitExit(millis: Long): Boolean =
        try {
            exited.await(millis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            exitValue != null
        }

    private fun sendSessionSignal(signal: Int) {
        val local = Arena.ofConfined()
        try {
            val capture = local.allocate(PosixLibc.captureLayout)
            val foreground = PosixLibc.tcgetpgrpHandle.invokeExact(capture, masterFd) as Int
            if (foreground > 0) {
                foregroundPgrp = foreground
            }
            val group = foregroundPgrp
            if (group > 0 && group != pid) {
                discard(PosixLibc.killHandle.invokeExact(capture, -group, signal) as Int)
            }
            discard(PosixLibc.killHandle.invokeExact(capture, -pid, signal) as Int)
        } catch (_: Throwable) {
            // ESRCH is the normal outcome after the child has exited.
        } finally {
            local.close()
        }
    }

    private fun watch() {
        val local = Arena.ofConfined()
        try {
            val status = local.allocate(ValueLayout.JAVA_INT)
            val capture = local.allocate(PosixLibc.captureLayout)
            while (true) {
                val result = PosixLibc.waitpidHandle.invokeExact(capture, pid, status, 0) as Int
                if (result == pid) {
                    exitValue = Signals.decode(status.get(ValueLayout.JAVA_INT, 0L))
                    return
                }
                val errno = PosixLibc.errno(capture)
                if (result == -1 && errno == PosixLibc.eintr) {
                    continue
                }
                exitValue = ProcessExit(-1)
                return
            }
        } catch (_: Throwable) {
            exitValue = ProcessExit(-1)
        } finally {
            exited.countDown()
            local.close()
        }
    }

    private fun spawn(
        command: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>,
        size: TerminalSize,
    ): Spawned {
        val local = Arena.ofConfined()
        var master = -1
        try {
            val capture = local.allocate(PosixLibc.captureLayout)
            master = PosixLibc.posixOpenptHandle.invokeExact(capture, PosixLibc.oRdwr) as Int
            if (master < 0) {
                throw IOException(PosixLibc.error("posix_openpt", PosixLibc.errno(capture)))
            }
            checkSyscall("grantpt", PosixLibc.grantptHandle.invokeExact(capture, master) as Int, capture)
            checkSyscall("unlockpt", PosixLibc.unlockptHandle.invokeExact(capture, master) as Int, capture)
            val slavePath = PosixLibc.ptyName(master, capture, local)
            val slave =
                PosixLibc.openHandle.invokeExact(
                    capture,
                    local.allocateFrom(slavePath),
                    PosixLibc.oRdwr or PosixLibc.oNoctty,
                    0,
                ) as Int
            if (slave < 0) {
                throw IOException(PosixLibc.error("open($slavePath)", PosixLibc.errno(capture)))
            }
            try {
                Termios.setUtf8(slave, capture, local)
                WinSize.set(slave, size, capture, local)
                val child = spawnChild(command, workingDirectory, environment, master, slave, slavePath, local, capture)
                return Spawned(master, child)
            } finally {
                closeFd(slave)
            }
        } catch (failure: Throwable) {
            if (master >= 0) {
                closeFd(master)
            }
            throw failure
        } finally {
            local.close()
        }
    }

    private fun spawnChild(
        command: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>,
        master: Int,
        slave: Int,
        slavePath: String,
        arena: Arena,
        capture: MemorySegment,
    ): Int {
        val attrs = PosixLibc.allocateSpawnAttr(arena)
        val actions = PosixLibc.allocateSpawnActions(arena)
        var attrsInitialized = false
        var actionsInitialized = false
        try {
            checkSpawnResult(
                "posix_spawnattr_init",
                PosixLibc.spawnattrInitHandle.invokeExact(attrs) as Int,
            )
            attrsInitialized = true
            checkSpawnResult(
                "posix_spawn_file_actions_init",
                PosixLibc.spawnFileActionsInitHandle.invokeExact(actions) as Int,
            )
            actionsInitialized = true

            val sigDefault = arena.allocate(SIGSET_BYTES, 8)
            val sigMask = arena.allocate(SIGSET_BYTES, 8)
            checkSyscall(
                "sigfillset",
                PosixLibc.sigfillsetHandle.invokeExact(capture, sigDefault) as Int,
                capture,
            )
            checkSyscall(
                "sigemptyset",
                PosixLibc.sigemptysetHandle.invokeExact(capture, sigMask) as Int,
                capture,
            )
            val flags =
                (
                    PosixLibc.posixSpawnSetsid.toInt() or PosixLibc.posixSpawnSetsigdef.toInt() or
                        PosixLibc.posixSpawnSetsigmask.toInt()
                ).toShort()
            checkSpawnResult(
                "posix_spawnattr_setflags",
                PosixLibc.spawnattrSetflagsHandle.invokeExact(attrs, flags) as Int,
            )
            checkSpawnResult(
                "posix_spawnattr_setsigdefault",
                PosixLibc.spawnattrSetsigdefaultHandle.invokeExact(attrs, sigDefault) as Int,
            )
            checkSpawnResult(
                "posix_spawnattr_setsigmask",
                PosixLibc.spawnattrSetsigmaskHandle.invokeExact(attrs, sigMask) as Int,
            )

            if (!PosixLibc.isMac) {
                // Close inherited descriptors before opening the slave. The
                // Linux closefrom action is sufficient and ordered first.
                closeInheritedFds(actions)
            }

            // Open the slave before spawn returns so the parent can read it
            // without racing the child. Darwin gets no close actions; its
            // child-shell wrapper closes the inherited PTY descriptors after
            // SETSID. Linux closes them above.
            addOpen(actions, CHILD_SLAVE_FD, slavePath, arena)
            addDup2(actions, CHILD_SLAVE_FD, 0)
            addDup2(actions, CHILD_SLAVE_FD, 1)
            addDup2(actions, CHILD_SLAVE_FD, 2)
            if (!PosixLibc.isMac) {
                addClose(actions, CHILD_SLAVE_FD)
                if (workingDirectory != null) {
                    val addchdir =
                        PosixLibc.spawnFileActionsAddchdirHandle
                            ?: throw IOException("working directory is not supported by this platform")
                    checkSpawnResult(
                        "posix_spawn_file_actions_addchdir_np",
                        addchdir.invokeExact(actions, arena.allocateFrom(workingDirectory.toString())) as Int,
                    )
                }
            }

            val spawnCommand =
                if (PosixLibc.isMac) {
                    macControllingTerminalWrapper(command, workingDirectory, master, slave, slavePath)
                } else {
                    command
                }
            val arguments = PosixLibc.stringArray(spawnCommand, arena)
            val environmentEntries = LinkedHashMap(System.getenv())
            environmentEntries.putAll(environment)
            val environmentArray =
                PosixLibc.stringArray(
                    environmentEntries.map { (key, value) -> "$key=$value" },
                    arena,
                )
            val pidSegment = arena.allocate(ValueLayout.JAVA_INT)
            val path = arena.allocateFrom(spawnCommand.first())
            val result =
                PosixLibc.spawnpHandle.invokeExact(
                    pidSegment,
                    path,
                    actions,
                    attrs,
                    arguments,
                    environmentArray,
                ) as Int
            if (result != 0) {
                throw IOException(PosixLibc.error("posix_spawnp", result))
            }
            val child = pidSegment.get(ValueLayout.JAVA_INT, 0L)
            if (child <= 0) {
                throw IOException("posix_spawnp returned an invalid pid: $child")
            }
            return child
        } finally {
            if (actionsInitialized) {
                discard(PosixLibc.spawnFileActionsDestroyHandle.invokeExact(actions) as Int)
            }
            if (attrsInitialized) {
                discard(PosixLibc.spawnattrDestroyHandle.invokeExact(attrs) as Int)
            }
        }
    }

    private fun macControllingTerminalWrapper(
        command: List<String>,
        workingDirectory: Path?,
        master: Int,
        slave: Int,
        slavePath: String,
    ): List<String> {
        // Darwin applies POSIX_SPAWN_SETSID after image activation, and its
        // posix_spawn file actions can fail with EBADF for this descriptor
        // set. Let the child shell close the inherited PTY descriptors and
        // open the slave after SETSID instead.
        val closeFds = listOf(master, slave).filter { it >= 3 }.distinct()
        val closeActions = closeFds.joinToString(" ") { "$it>&-" }
        val prefix =
            "trap - INT HUP TERM; " +
                (if (closeActions.isEmpty()) ":" else "exec $closeActions") +
                "; exec 3<> \"\$1\" || exit 1; exec <&3 >&3 2>&3; exec 3>&-; shift; "
        if (workingDirectory == null) {
            val script = prefix + "exec \"\$@\""
            return listOf("/bin/sh", "-c", script, "sh", slavePath) + command
        }
        val script = prefix + "cd \"\$1\" || exit 1; shift; exec \"\$@\""
        return listOf("/bin/sh", "-c", script, "sh", slavePath, workingDirectory.toString()) + command
    }

    private fun addOpen(
        actions: MemorySegment,
        fd: Int,
        path: String,
        arena: Arena,
    ) {
        val pathSegment = arena.allocateFrom(path)
        val result =
            if (PosixLibc.isMac) {
                PosixLibc.spawnFileActionsAddopenHandle.invokeExact(
                    actions,
                    fd,
                    pathSegment,
                    PosixLibc.oRdwr,
                    0.toShort(),
                ) as Int
            } else {
                PosixLibc.spawnFileActionsAddopenHandle.invokeExact(
                    actions,
                    fd,
                    pathSegment,
                    PosixLibc.oRdwr,
                    0,
                ) as Int
            }
        if (result != 0) {
            throw IOException(PosixLibc.error("posix_spawn_file_actions_addopen", result))
        }
    }

    private fun addDup2(
        actions: MemorySegment,
        fd: Int,
        target: Int,
    ) {
        val result = PosixLibc.spawnFileActionsAdddup2Handle.invokeExact(actions, fd, target) as Int
        if (result != 0) {
            throw IOException(PosixLibc.error("posix_spawn_file_actions_adddup2", result))
        }
    }

    private fun addClose(
        actions: MemorySegment,
        fd: Int,
    ) {
        val result = PosixLibc.spawnFileActionsAddcloseHandle.invokeExact(actions, fd) as Int
        if (result != 0) {
            throw IOException(PosixLibc.error("posix_spawn_file_actions_addclose", result))
        }
    }

    private fun closeInheritedFds(actions: MemorySegment) {
        val closeFrom = PosixLibc.spawnFileActionsAddclosefromHandle
        if (!PosixLibc.isMac && closeFrom != null) {
            val result = closeFrom.invokeExact(actions, 3) as Int
            if (result == 0) {
                return
            }
        }

        val directory = if (PosixLibc.isLinux) Path.of("/proc/self/fd") else Path.of("/dev/fd")
        val descriptors = ArrayList<Int>()
        try {
            java.nio.file.Files.newDirectoryStream(directory).use { stream ->
                for (entry in stream) {
                    entry.fileName
                        .toString()
                        .toIntOrNull()
                        ?.let(descriptors::add)
                }
            }
        } catch (_: java.io.IOException) {
            return
        }
        for (fd in descriptors) {
            if (fd >= 3 && PosixLibc.isOpen(fd)) {
                addClose(actions, fd)
            }
        }
    }

    private fun checkSyscall(
        name: String,
        result: Int,
        capture: MemorySegment,
    ) {
        if (result != 0) {
            throw IOException(PosixLibc.error(name, PosixLibc.errno(capture)))
        }
    }

    private fun checkSpawnResult(
        name: String,
        result: Int,
    ) {
        if (result != 0) {
            throw IOException(PosixLibc.error(name, result))
        }
    }

    private fun closeFd(fd: Int) {
        try {
            discard(PosixLibc.closeHandle.invokeExact(fd) as Int)
        } catch (_: Throwable) {
            // Closing a failed or already-closed descriptor is best effort.
        }
    }

    private fun discard(
        @Suppress("UNUSED_PARAMETER") result: Int,
    ) {
        // Best-effort cleanup syscalls do not change the session state.
    }

    private data class Spawned(
        val masterFd: Int,
        val pid: Int,
    )

    private companion object {
        private const val READ_BUFFER_BYTES: Int = 64 * 1024
        private const val WRITE_BUFFER_BYTES: Int = 64 * 1024
        private const val WINSIZE_BYTES: Long = 8
        private const val CHILD_SLAVE_FD: Int = 3
        private const val SIGSET_BYTES: Long = 256
        private const val CLOSE_GRACE_MILLIS: Long = 500
        private const val WATCHER_JOIN_MILLIS: Long = 2_000
    }
}
