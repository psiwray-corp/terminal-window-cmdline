package com.psiwray.organization.terminal_window_cmdline.pty

import java.io.IOException

/**
 * Byte-oriented child process or terminal session.
 *
 * A session owns a child process and exposes blocking byte I/O, terminal-size
 * changes, signal-like requests, and an exit snapshot. Implementations never
 * expose platform handles to callers.
 *
 * Reader, writer, and watcher threads may call their respective operations
 * concurrently. The screen model is never shared with this interface.
 */
public interface ProcessSession : AutoCloseable {
    /** True when the child is attached to a PTY or ConPTY. */
    public val isTty: Boolean

    /**
     * Reads up to [length] bytes into [buffer], blocking until data or EOF.
     *
     * @return number of bytes read, or `-1` at end of stream.
     */
    @Throws(IOException::class)
    public fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    /**
     * Writes [length] bytes from [buffer] to the child, blocking until accepted.
     */
    @Throws(IOException::class)
    public fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    )

    /**
     * Requests a new terminal size.
     *
     * @return true when the backend changed the size, false when unsupported.
     */
    public fun resize(size: TerminalSize): Boolean

    /** Requests a graceful interrupt, such as `SIGINT` or input `0x03`. */
    public fun requestInterrupt()

    /** Requests immediate termination. */
    public fun requestTerminate()

    /** Latest exit status, or null while the child is still running. */
    public val exit: ProcessExit?

    /** Closes the session and ensures the child is terminated. */
    override fun close()
}
