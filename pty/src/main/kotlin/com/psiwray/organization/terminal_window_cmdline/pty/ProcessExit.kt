package com.psiwray.organization.terminal_window_cmdline.pty

/**
 * Immutable child-process exit status.
 *
 * @property exitCode process exit code; on POSIX a signal death may also set
 *   [signal].
 * @property signal POSIX signal number, or `-1` when the child exited normally
 *   or the backend does not report signals.
 */
public data class ProcessExit(
    public val exitCode: Int,
    public val signal: Int = -1,
) {
    /** True when the child exited normally with code zero. */
    public val isSuccess: Boolean
        get() = exitCode == 0 && signal < 0
}
