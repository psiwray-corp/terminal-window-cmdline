package com.psiwray.organization.terminal_window_cmdline.pty.posix

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/** POSIX termios helpers for the PTY slave. */
internal object Termios {
    private const val TERMIOS_BYTES: Long = 128
    private const val IUTF8: Int = 0x4000

    /**
     * Sets `IUTF8` so the line discipline handles UTF-8 erase correctly.
     *
     * Linux stores `c_iflag` as a 32-bit flag word; Darwin stores `tcflag_t`
     * as an unsigned long. The low bits are the same on both.
     */
    fun setUtf8(
        fd: Int,
        capture: MemorySegment,
        arena: Arena,
    ) {
        val termios = arena.allocate(TERMIOS_BYTES, 8)
        val result = PosixLibc.tcgetattrHandle.invokeExact(capture, fd, termios) as Int
        if (result != 0) {
            throw IllegalStateException(PosixLibc.error("tcgetattr", PosixLibc.errno(capture)))
        }
        if (PosixLibc.isMac) {
            termios.set(ValueLayout.JAVA_LONG, 0L, termios.get(ValueLayout.JAVA_LONG, 0L) or IUTF8.toLong())
        } else {
            termios.set(ValueLayout.JAVA_INT, 0L, termios.get(ValueLayout.JAVA_INT, 0L) or IUTF8)
        }
        val applied = PosixLibc.tcsetattrHandle.invokeExact(capture, fd, PosixLibc.tcsaNow, termios) as Int
        if (applied != 0) {
            throw IllegalStateException(PosixLibc.error("tcsetattr", PosixLibc.errno(capture)))
        }
    }
}
