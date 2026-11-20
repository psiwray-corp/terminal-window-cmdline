package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window_cmdline.pty.ProcessExit
import com.psiwray.organization.terminal_window_cmdline.pty.windows.ConPtySession
import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Timeout(30)
class ConPtyParsingIntegrationTest {
    @Test
    fun conPtyOutputParsesIntoTheScreen() {
        val osName = System.getProperty("os.name", "").lowercase()
        assumeTrue(osName.contains("windows"), "ConPTY parsing runs only on Windows")
        val shell = System.getenv("ComSpec") ?: "C:\\Windows\\System32\\cmd.exe"
        val session = ConPtySession(listOf(shell, "/c", "echo hello"))
        val output = ByteArrayOutputStream()
        val failure =
            java.util.concurrent.atomic
                .AtomicReference<Throwable?>(null)
        val reader =
            thread(name = "conpty-parser-reader", isDaemon = true) {
                try {
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = session.read(buffer, 0, buffer.size)
                        if (count < 0) {
                            break
                        }
                        if (count > 0) {
                            output.write(buffer, 0, count)
                        }
                    }
                } catch (cause: Throwable) {
                    failure.set(cause)
                }
            }
        try {
            awaitExit(session)
            session.close()
            reader.join(10_000)
            if (reader.isAlive) {
                fail("ConPTY reader did not finish")
            }
            failure.get()?.let { throw AssertionError("ConPTY reader failed", it) }

            val bytes = output.toByteArray()
            val state = TerminalState(rows = 24, columns = 80)
            Parser(state).write(bytes, 0, bytes.size)
            assertTrue(screenText(state).contains("hello"), screenText(state))
        } finally {
            session.close()
        }
    }

    private fun awaitExit(session: ConPtySession): ProcessExit {
        val deadline = System.nanoTime() + 15_000L * 1_000_000L
        while (System.nanoTime() < deadline) {
            session.exit?.let { return it }
            Thread.sleep(20)
        }
        session.requestTerminate()
        return assertNotNull(session.exit, "ConPTY child did not exit")
    }

    private fun screenText(state: TerminalState): String {
        val builder = StringBuilder()
        for (row in 0 until state.screen.rows) {
            for (column in 0 until state.screen.columns) {
                val codePoint = state.screen.codePointAt(row, column)
                if (codePoint != 0) {
                    builder.appendCodePoint(codePoint)
                }
            }
            builder.append('\n')
        }
        return builder.toString()
    }
}
