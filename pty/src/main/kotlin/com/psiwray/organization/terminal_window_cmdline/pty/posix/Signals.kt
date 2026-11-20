package com.psiwray.organization.terminal_window_cmdline.pty.posix

import com.psiwray.organization.terminal_window_cmdline.pty.ProcessExit

/** POSIX signal constants and wait-status decoding. */
internal object Signals {
    const val SIGHUP: Int = 1
    const val SIGINT: Int = 2
    const val SIGKILL: Int = 9
    const val SIGTERM: Int = 15

    /** Converts a `waitpid` status into a [ProcessExit]. */
    fun decode(status: Int): ProcessExit {
        if ((status and 0x7f) == 0) {
            return ProcessExit((status shr 8) and 0xff)
        }
        val signal = status and 0x7f
        if (signal != 0x7f) {
            return ProcessExit(128 + signal, signal)
        }
        return ProcessExit(status)
    }
}
