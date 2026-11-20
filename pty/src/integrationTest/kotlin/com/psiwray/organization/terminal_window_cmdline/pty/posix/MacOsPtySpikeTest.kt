package com.psiwray.organization.terminal_window_cmdline.pty.posix

import com.psiwray.organization.terminal_window_cmdline.pty.ProcessSession
import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * macOS controlling-terminal spike.
 *
 * Runs only on macOS CI. Linux skips it; a failure here means the pure-FFM
 * `posix_spawn` + `SETSID` + slave-duplication path does not establish job
 * control on Darwin.
 */
@Timeout(20)
class MacOsPtySpikeTest {
    @Test
    fun macOsChildHasControllingTerminalAndJobControl() {
        assumeTrue(System.getProperty("os.name").lowercase().contains("mac"))
        val session =
            PosixPtySession(
                listOf("sh", "-c", "tty; stty size; ps -o tpgid= -p \$\$; ps -o pgid= -p \$\$"),
                environment = mapOf("TERM" to "xterm-256color"),
                size = TerminalSize(80, 24),
            )
        try {
            val output = readAll(session)
            assertTrue(output.contains("/dev/"), output)
            assertTrue(output.contains("24 80"), output)
            val ids = output.lines().mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
            assertTrue(ids.size >= 2, output)
            assertEquals(ids[0], ids[1], output)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    private fun readAll(session: ProcessSession): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = session.read(buffer, 0, buffer.size)
            if (count < 0) {
                break
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun awaitExit(session: ProcessSession) =
        run {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < deadline) {
                session.exit?.let { return@run it }
                Thread.sleep(10)
            }
            throw AssertionError("child did not exit")
        }
}
