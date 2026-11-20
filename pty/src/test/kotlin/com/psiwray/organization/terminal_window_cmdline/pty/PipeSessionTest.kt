package com.psiwray.organization.terminal_window_cmdline.pty

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@Timeout(15)
class PipeSessionTest {
    @Test
    fun mergesStderrIntoStdoutAndReportsExit() {
        assumePosix()
        val session = PipeSession(shell("printf out; printf err >&2; exit 7"))
        try {
            assertFalse(session.isTty)
            assertFalse(session.resize(TerminalSize(80, 24)))
            assertEquals("outerr", readAll(session))
            assertEquals(7, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun writesBytesToChildStdin() {
        assumePosix()
        val session = PipeSession(shell("read value; printf '%s' \"\$value\""))
        try {
            session.write("hello\n".toByteArray(Charsets.UTF_8), 0, 6)
            assertEquals("hello", readAll(session))
            assertNotNull(awaitExit(session))
        } finally {
            session.close()
        }
    }

    @Test
    fun closeTerminatesTheChild() {
        assumePosix()
        val session = PipeSession(shell("sleep 30"))
        session.close()
        assertNotNull(session.exit)
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

    private fun awaitExit(session: ProcessSession): ProcessExit {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            session.exit?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("child did not exit")
    }

    private fun assumePosix() {
        assumeFalse(System.getProperty("os.name").lowercase().contains("windows"))
    }

    private fun shell(script: String): List<String> = listOf("sh", "-c", script)
}
