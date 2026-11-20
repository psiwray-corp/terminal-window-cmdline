package com.psiwray.organization.terminal_window_cmdline.pty.windows

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets

/**
 * Windows ABI layouts used by the ConPTY backend.
 *
 * The layouts are hand-written because the Windows SDK is not available on the
 * Linux development host. Unit tests assert the x64/arm64 sizes and offsets.
 */
internal object WindowsLayouts {
    /** `COORD` from `wincontypes.h`. */
    val COORD: MemoryLayout =
        MemoryLayout
            .structLayout(
                ValueLayout.JAVA_SHORT.withName("X"),
                ValueLayout.JAVA_SHORT.withName("Y"),
            ).withName("COORD")

    /** `SECURITY_ATTRIBUTES` from `minwindef.h`. */
    val SECURITY_ATTRIBUTES: MemoryLayout =
        MemoryLayout
            .structLayout(
                ValueLayout.JAVA_INT.withName("nLength"),
                MemoryLayout.paddingLayout(4),
                ValueLayout.ADDRESS.withName("lpSecurityDescriptor"),
                ValueLayout.JAVA_INT.withName("bInheritHandle"),
                MemoryLayout.paddingLayout(4),
            ).withName("SECURITY_ATTRIBUTES")

    /** `STARTUPINFOW` from `processthreadsapi.h`. */
    val STARTUPINFOW: MemoryLayout =
        MemoryLayout
            .structLayout(
                ValueLayout.JAVA_INT.withName("cb"),
                MemoryLayout.paddingLayout(4),
                ValueLayout.ADDRESS.withName("lpReserved"),
                ValueLayout.ADDRESS.withName("lpDesktop"),
                ValueLayout.ADDRESS.withName("lpTitle"),
                ValueLayout.JAVA_INT.withName("dwX"),
                ValueLayout.JAVA_INT.withName("dwY"),
                ValueLayout.JAVA_INT.withName("dwXSize"),
                ValueLayout.JAVA_INT.withName("dwYSize"),
                ValueLayout.JAVA_INT.withName("dwXCountChars"),
                ValueLayout.JAVA_INT.withName("dwYCountChars"),
                ValueLayout.JAVA_INT.withName("dwFillAttribute"),
                ValueLayout.JAVA_INT.withName("dwFlags"),
                ValueLayout.JAVA_SHORT.withName("wShowWindow"),
                ValueLayout.JAVA_SHORT.withName("cbReserved2"),
                MemoryLayout.paddingLayout(4),
                ValueLayout.ADDRESS.withName("lpReserved2"),
                ValueLayout.ADDRESS.withName("hStdInput"),
                ValueLayout.ADDRESS.withName("hStdOutput"),
                ValueLayout.ADDRESS.withName("hStdError"),
            ).withName("STARTUPINFOW")

    /** `STARTUPINFOEXW` from `processthreadsapi.h`. */
    val STARTUPINFOEXW: MemoryLayout =
        MemoryLayout
            .structLayout(
                STARTUPINFOW.withName("StartupInfo"),
                ValueLayout.ADDRESS.withName("lpAttributeList"),
            ).withName("STARTUPINFOEXW")

    /** `PROCESS_INFORMATION` from `processthreadsapi.h`. */
    val PROCESS_INFORMATION: MemoryLayout =
        MemoryLayout
            .structLayout(
                ValueLayout.ADDRESS.withName("hProcess"),
                ValueLayout.ADDRESS.withName("hThread"),
                ValueLayout.JAVA_INT.withName("dwProcessId"),
                ValueLayout.JAVA_INT.withName("dwThreadId"),
            ).withName("PROCESS_INFORMATION")
}

/**
 * Pure-FFM Windows bindings for the ConPTY backend.
 *
 * Loading this object on a non-Windows host fails before any native symbol is
 * resolved. A missing `CreatePseudoConsole` is reported as the Windows 10 1809
 * minimum-version diagnostic.
 */
internal object WindowsApi {
    private const val PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE: Long = 0x00020016L
    private const val FORMAT_MESSAGE_FROM_SYSTEM: Int = 0x00001000
    private const val FORMAT_MESSAGE_IGNORE_INSERTS: Int = 0x00000200
    private val osName: String = System.getProperty("os.name", "").lowercase()

    init {
        if (!osName.contains("windows")) {
            throw UnsupportedOperationException("ConPTY is available only on Windows")
        }
    }

    private val linker: Linker = Linker.nativeLinker()

    // The JDK's Windows default lookup exposes the C runtime (ucrtbase/msvcrt),
    // not kernel32. Load kernel32 explicitly for the Win32 console and process
    // entry points below.
    private val lookup: SymbolLookup = SymbolLookup.libraryLookup("kernel32.dll", Arena.global())

    init {
        if (lookup.find("CreatePseudoConsole").isEmpty()) {
            throw UnsupportedOperationException(
                "Windows 10 1809 or newer required: CreatePseudoConsole is unavailable",
            )
        }
    }

    val createPipe: MethodHandle =
        bind(
            "CreatePipe",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )

    val createPseudoConsole: MethodHandle =
        bind(
            "CreatePseudoConsole",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                WindowsLayouts.COORD,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )

    val resizePseudoConsole: MethodHandle =
        bind(
            "ResizePseudoConsole",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                WindowsLayouts.COORD,
            ),
        )

    val closePseudoConsole: MethodHandle =
        bind(
            "ClosePseudoConsole",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
        )

    val initializeProcThreadAttributeList: MethodHandle =
        bind(
            "InitializeProcThreadAttributeList",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )

    val updateProcThreadAttribute: MethodHandle =
        bind(
            "UpdateProcThreadAttribute",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    val deleteProcThreadAttributeList: MethodHandle =
        bind(
            "DeleteProcThreadAttributeList",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
        )

    val createProcessW: MethodHandle =
        bind(
            "CreateProcessW",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    val readFile: MethodHandle =
        bind(
            "ReadFile",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    val writeFile: MethodHandle =
        bind(
            "WriteFile",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    val waitForSingleObject: MethodHandle =
        bind(
            "WaitForSingleObject",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )

    val getExitCodeProcess: MethodHandle =
        bind(
            "GetExitCodeProcess",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    val terminateProcess: MethodHandle =
        bind(
            "TerminateProcess",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )

    val closeHandle: MethodHandle =
        bind(
            "CloseHandle",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )

    val freeConsole: MethodHandle =
        bind("FreeConsole", FunctionDescriptor.of(ValueLayout.JAVA_INT))

    val attachConsole: MethodHandle =
        bind(
            "AttachConsole",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        )

    val generateConsoleCtrlEvent: MethodHandle =
        bind(
            "GenerateConsoleCtrlEvent",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
            ),
        )

    val setConsoleCtrlHandler: MethodHandle =
        bind(
            "SetConsoleCtrlHandler",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )

    val createFileW: MethodHandle =
        bind(
            "CreateFileW",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )

    val getConsoleMode: MethodHandle =
        bind(
            "GetConsoleMode",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    val setConsoleMode: MethodHandle =
        bind(
            "SetConsoleMode",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )

    val getLastError: MethodHandle =
        bind(
            "GetLastError",
            FunctionDescriptor.of(ValueLayout.JAVA_INT),
        )

    val formatMessageW: MethodHandle =
        bind(
            "FormatMessageW",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )

    val procThreadAttributePseudoConsole: Long
        get() = PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE

    val startupInfoExSize: Int
        get() = WindowsLayouts.STARTUPINFOEXW.byteSize().toInt()

    /** Returns the Win32 error code for the calling thread. */
    fun lastError(): Int = getLastError.invokeExact() as Int

    /** Copies a Java string into a null-terminated UTF-16 native string. */
    fun allocateUtf16(
        arena: Arena,
        value: String,
    ): MemorySegment {
        val segment = arena.allocate((value.length + 1).toLong() * 2L, 2)
        var offset = 0L
        for (character in value) {
            segment.set(ValueLayout.JAVA_CHAR, offset, character)
            offset += 2
        }
        segment.set(ValueLayout.JAVA_CHAR, offset, '\u0000')
        return segment
    }

    /** Formats a Win32 error code without requiring a Windows SDK. */
    fun formatError(code: Int): String {
        if (code == 0) {
            return "no error"
        }
        val local = Arena.ofConfined()
        try {
            val buffer = local.allocate(2048L * 2L, 2)
            val written =
                formatMessageW.invokeExact(
                    FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS,
                    MemorySegment.NULL,
                    code,
                    0,
                    buffer,
                    2048,
                    MemorySegment.NULL,
                ) as Int
            if (written <= 0) {
                return "Win32 error $code"
            }
            return buffer.getString(0L, StandardCharsets.UTF_16LE).trimEnd()
        } catch (_: Throwable) {
            return "Win32 error $code"
        } finally {
            local.close()
        }
    }

    private fun bind(
        name: String,
        descriptor: FunctionDescriptor,
    ): MethodHandle {
        val symbol =
            lookup.find(name).orElse(null)
                ?: throw UnsupportedOperationException("Windows API symbol not found: $name")
        return linker.downcallHandle(symbol, descriptor)
    }
}
