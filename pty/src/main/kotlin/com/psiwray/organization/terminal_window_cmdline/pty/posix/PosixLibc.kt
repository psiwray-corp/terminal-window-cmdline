package com.psiwray.organization.terminal_window_cmdline.pty.posix

import java.lang.foreign.AddressLayout
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.util.Locale

/**
 * Minimal pure-FFM libc surface used by the POSIX PTY backend.
 *
 * The platform is selected once from `os.name`. Unsupported hosts fail in
 * [requireSupported] before any native call is made.
 */
internal object PosixLibc {
    private val osName: String = System.getProperty("os.name").lowercase(Locale.ROOT)

    /** True on Linux. */
    val isLinux: Boolean = osName.contains("linux")

    /** True on macOS (Darwin). */
    val isMac: Boolean = osName.contains("mac") || osName.contains("darwin")

    /** True on a supported POSIX host. */
    val isPosix: Boolean = isLinux || isMac

    /** Human-readable platform name for diagnostics. */
    val platformName: String =
        if (isMac) {
            "macOS"
        } else if (isLinux) {
            "Linux"
        } else {
            osName
        }

    /** Layout of the captured `errno` state. */
    val captureLayout: MemoryLayout = Linker.Option.captureStateLayout()

    private val linker: Linker = Linker.nativeLinker()
    private val lookup = linker.defaultLookup()

    val errnoOffset: Long = captureLayout.byteOffset(MemoryLayout.PathElement.groupElement("errno"))

    // Linux and Darwin differ in termios flags and ioctl request values.
    val oRdwr: Int = 2
    val oNoctty: Int = if (isMac) 0x20000 else 0x100
    val tcsaNow: Int = 0
    val tiocswinsz: Long = if (isMac) 0x80087467L else 0x5414L
    val posixSpawnSetsid: Short = if (isMac) 0x400 else 0x80
    val posixSpawnSetsigdef: Short = 0x04
    val posixSpawnSetsigmask: Short = 0x08

    val eintr: Int = 4
    val eio: Int = 5
    private val fGetFd: Int = 1
    val readHandle: MethodHandle =
        captured(
            "read",
            FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
            ),
        )

    val writeHandle: MethodHandle =
        captured(
            "write",
            FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
            ),
        )

    val closeHandle: MethodHandle =
        handle("close", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))

    private val fcntlHandle: MethodHandle =
        handle(
            "fcntl",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
            ),
        )

    /** True when [fd] is still open in this process. */
    fun isOpen(fd: Int): Boolean = (fcntlHandle.invokeExact(fd, fGetFd) as Int) != -1

    val posixOpenptHandle: MethodHandle =
        captured("posix_openpt", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))

    val grantptHandle: MethodHandle =
        captured("grantpt", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))

    val unlockptHandle: MethodHandle =
        captured("unlockpt", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))

    val ptsnameRHandle: MethodHandle? =
        if (isLinux) {
            captured(
                "ptsname_r",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                ),
            )
        } else {
            null
        }

    val ptsnameHandle: MethodHandle? =
        if (isMac) {
            handle("ptsname", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
        } else {
            null
        }

    val openHandle: MethodHandle =
        capturedVariadic(
            "open",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
            ),
            firstVariadicArg = 2,
        )

    val ioctlHandle: MethodHandle =
        capturedVariadic(
            "ioctl",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
            ),
            firstVariadicArg = 2,
        )

    val tcgetattrHandle: MethodHandle =
        captured(
            "tcgetattr",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
        )

    val tcsetattrHandle: MethodHandle =
        captured(
            "tcsetattr",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )

    val tcgetpgrpHandle: MethodHandle =
        captured("tcgetpgrp", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))

    val killHandle: MethodHandle =
        captured(
            "kill",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        )

    val waitpidHandle: MethodHandle =
        captured(
            "waitpid",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )

    val sigfillsetHandle: MethodHandle =
        captured("sigfillset", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

    val sigemptysetHandle: MethodHandle =
        captured("sigemptyset", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

    val spawnattrInitHandle: MethodHandle =
        handle("posix_spawnattr_init", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

    val spawnattrDestroyHandle: MethodHandle =
        handle("posix_spawnattr_destroy", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

    val spawnattrSetflagsHandle: MethodHandle =
        handle(
            "posix_spawnattr_setflags",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT),
        )

    val spawnattrSetsigdefaultHandle: MethodHandle =
        handle(
            "posix_spawnattr_setsigdefault",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )

    val spawnattrSetsigmaskHandle: MethodHandle =
        handle(
            "posix_spawnattr_setsigmask",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )

    val spawnFileActionsInitHandle: MethodHandle =
        handle("posix_spawn_file_actions_init", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

    val spawnFileActionsDestroyHandle: MethodHandle =
        handle("posix_spawn_file_actions_destroy", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

    val spawnFileActionsAdddup2Handle: MethodHandle =
        handle(
            "posix_spawn_file_actions_adddup2",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
            ),
        )

    val spawnFileActionsAddopenHandle: MethodHandle =
        handle(
            "posix_spawn_file_actions_addopen",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                if (isMac) ValueLayout.JAVA_SHORT else ValueLayout.JAVA_INT,
            ),
        )

    val spawnFileActionsAddcloseHandle: MethodHandle =
        handle(
            "posix_spawn_file_actions_addclose",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        )

    val spawnFileActionsAddclosefromHandle: MethodHandle? =
        lookup.find("posix_spawn_file_actions_addclosefrom_np").orElse(null)?.let { symbol ->
            linker.downcallHandle(
                symbol,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
            )
        }

    val spawnFileActionsAddchdirHandle: MethodHandle? =
        lookup.find("posix_spawn_file_actions_addchdir_np").orElse(null)?.let { symbol ->
            linker.downcallHandle(
                symbol,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
        }

    val spawnpHandle: MethodHandle =
        handle(
            "posix_spawnp",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    private val strerrorHandle: MethodHandle =
        handle("strerror", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT))

    /** Fails fast on a non-POSIX host. */
    fun requireSupported() {
        check(isPosix) { "POSIX PTY is not supported on $platformName; use --pipe" }
    }

    /** Reads the captured `errno` value. */
    fun errno(capture: MemorySegment): Int = capture.get(ValueLayout.JAVA_INT, errnoOffset)

    /** Formats a syscall error with the platform, operation, and errno. */
    fun error(
        operation: String,
        errno: Int,
    ): String {
        val text =
            synchronized(this) {
                runCatching {
                    (strerrorHandle.invokeExact(errno) as MemorySegment)
                        .reinterpret(ERROR_TEXT_BYTES)
                        .getString(0)
                }.getOrElse { "errno $errno" }
            }
        return "$operation failed on $platformName: errno $errno ($text)"
    }

    private fun handle(
        name: String,
        descriptor: FunctionDescriptor,
    ): MethodHandle = linker.downcallHandle(lookup.findOrThrow(name), descriptor)

    private fun captured(
        name: String,
        descriptor: FunctionDescriptor,
    ): MethodHandle =
        linker.downcallHandle(
            lookup.findOrThrow(name),
            descriptor,
            Linker.Option.captureCallState("errno"),
        )

    private fun capturedVariadic(
        name: String,
        descriptor: FunctionDescriptor,
        firstVariadicArg: Int,
    ): MethodHandle =
        linker.downcallHandle(
            lookup.findOrThrow(name),
            descriptor,
            Linker.Option.firstVariadicArg(firstVariadicArg),
            Linker.Option.captureCallState("errno"),
        )

    /** Returns the slave device path for [masterFd]. */
    fun ptyName(
        masterFd: Int,
        capture: MemorySegment,
        arena: Arena,
    ): String {
        if (isLinux) {
            val buffer = arena.allocate(256)
            val result = ptsnameRHandle!!.invokeExact(capture, masterFd, buffer, 256L) as Int
            if (result != 0) {
                throw IllegalStateException(error("ptsname_r", result))
            }
            return buffer.getString(0)
        }

        val ptsname = ptsnameHandle ?: throw IllegalStateException("ptsname is unavailable")
        return synchronized(this) {
            val name = ptsname.invokeExact(masterFd) as MemorySegment
            if (name == MemorySegment.NULL) {
                throw IllegalStateException(error("ptsname", errno(capture)))
            }
            name.reinterpret(PTY_NAME_BYTES).getString(0)
        }
    }

    /** Allocates the opaque payload for a `posix_spawnattr_t`. */
    fun allocateSpawnAttr(arena: Arena): MemorySegment = arena.allocate(SPAWN_OPAQUE_BYTES, 16)

    /** Allocates the opaque payload for a `posix_spawn_file_actions_t`. */
    fun allocateSpawnActions(arena: Arena): MemorySegment = arena.allocate(SPAWN_OPAQUE_BYTES, 16)

    /** Builds a NUL-terminated C string array in [arena]. */
    fun stringArray(
        values: List<String>,
        arena: Arena,
    ): MemorySegment {
        val array =
            arena.allocate(
                AddressLayout.ADDRESS.byteSize() * (values.size + 1),
                AddressLayout.ADDRESS.byteAlignment(),
            )
        for (index in values.indices) {
            val value = values[index]
            require('\u0000' !in value) { "argument contains NUL" }
            array.setAtIndex(AddressLayout.ADDRESS, index.toLong(), arena.allocateFrom(value))
        }
        array.setAtIndex(AddressLayout.ADDRESS, values.size.toLong(), MemorySegment.NULL)
        return array
    }

    private const val SPAWN_OPAQUE_BYTES: Long = 1024
    private const val PTY_NAME_BYTES: Long = 128
    private const val ERROR_TEXT_BYTES: Long = 512
}
