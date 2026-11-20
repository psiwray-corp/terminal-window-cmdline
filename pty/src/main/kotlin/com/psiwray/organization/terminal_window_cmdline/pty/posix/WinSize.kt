package com.psiwray.organization.terminal_window_cmdline.pty.posix

import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/** POSIX `struct winsize` helpers. */
internal object WinSize {
    private const val BYTES: Long = 8

    /** Writes [size] into [target] using the native `winsize` layout. */
    fun write(
        target: MemorySegment,
        size: TerminalSize,
    ) {
        target.set(ValueLayout.JAVA_SHORT, 0L, size.rows.toShort())
        target.set(ValueLayout.JAVA_SHORT, 2L, size.columns.toShort())
        target.set(ValueLayout.JAVA_SHORT, 4L, 0.toShort())
        target.set(ValueLayout.JAVA_SHORT, 6L, 0.toShort())
    }

    /** Applies the initial window size to [fd]. */
    fun set(
        fd: Int,
        size: TerminalSize,
        capture: MemorySegment,
        arena: Arena,
    ) {
        val winsize = arena.allocate(BYTES, 8)
        write(winsize, size)
        val result = PosixLibc.ioctlHandle.invokeExact(capture, fd, PosixLibc.tiocswinsz, winsize) as Int
        if (result != 0) {
            throw IllegalStateException(PosixLibc.error("ioctl(TIOCSWINSZ)", PosixLibc.errno(capture)))
        }
    }
}
