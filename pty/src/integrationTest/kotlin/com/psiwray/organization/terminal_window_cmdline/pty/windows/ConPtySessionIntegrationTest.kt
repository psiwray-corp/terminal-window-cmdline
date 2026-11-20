package com.psiwray.organization.terminal_window_cmdline.pty.windows

import com.psiwray.organization.terminal_window_cmdline.pty.ProcessExit
import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Timeout(30)
class ConPtySessionIntegrationTest {
    @Test
    fun echoOutputIsVisibleAndSessionIsTty() {
        val result = runCommand(cmdArgs("echo hello"))
        assertTrue(result.output.contains("hello"), result.output)
        assertEquals(0, result.exit.exitCode)
    }

    @Test
    fun consoleAwareProgramSeesConsole() {
        val result = runCommand(cmdArgs("mode con"))
        assertTrue(result.output.contains("CON"), result.output)
        assertTrue(result.output.contains("Columns"), result.output)
    }

    @Test
    fun resizeChangesGridWithoutDeadlock() {
        val session = ConPtySession(cmdArgs("ping -n 2 127.0.0.1 >nul & mode con"), size = TerminalSize(80, 24))
        val capture = startCapture(session)
        try {
            Thread.sleep(400)
            assertTrue(session.resize(TerminalSize(100, 30)))
            val exit = awaitExit(session, 15_000)
            session.close()
            capture.join()
            val output = capture.output()
            assertTrue(output.contains("100"), output)
            assertTrue(output.contains("30"), output)
            assertEquals(0, exit.exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun ctrlCReachesTheChild() {
        assumeWindows()
        val session = ConPtySession(listOf("ping.exe", "-n", "30", "127.0.0.1"))
        val capture = startCapture(session)
        try {
            capture.awaitOutputContains("127.0.0.1", 10_000)
            session.requestInterrupt()
            assertNotNull(awaitExit(session, 10_000))
            session.close()
            capture.join()
        } catch (failure: AssertionError) {
            throw AssertionError("${failure.message}; output=${capture.output()}", failure)
        } finally {
            session.close()
        }
    }

    @Test
    fun hardTerminateStopsTheChild() {
        val session = ConPtySession(cmdArgs("ping -n 30 127.0.0.1 >nul"))
        val capture = startCapture(session)
        try {
            Thread.sleep(500)
            session.requestTerminate()
            assertNotNull(awaitExit(session, 10_000))
            session.close()
            capture.join()
        } finally {
            session.close()
        }
    }

    @Test
    fun exitCodeIsReported() {
        val result = runCommand(cmdArgs("exit 7"))
        assertEquals(7, result.exit.exitCode)
    }

    @Test
    fun finalOutputIsDrainedBeforeClose() {
        val result = runCommand(cmdArgs("echo FINAL"))
        assertTrue(result.output.contains("FINAL"), result.output)
    }

    private fun runCommand(command: List<String>): Result {
        val session = ConPtySession(command)
        val capture = startCapture(session)
        try {
            val exit = awaitExit(session, 15_000)
            session.close()
            capture.join()
            return Result(exit, capture.output())
        } finally {
            session.close()
        }
    }

    private fun awaitExit(
        session: ConPtySession,
        timeoutMillis: Long,
    ): ProcessExit {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            session.exit?.let { return it }
            Thread.sleep(20)
        }
        session.requestTerminate()
        fail("ConPTY child did not exit within $timeoutMillis ms")
    }

    private fun startCapture(session: ConPtySession): Capture {
        val output = ByteArrayOutputStream()
        val failure = AtomicReference<Throwable?>(null)
        val reader =
            thread(name = "conpty-test-reader", isDaemon = true) {
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
        return Capture(reader, output, failure)
    }

    private fun cmdArgs(command: String): List<String> {
        assumeWindows()
        val shell = System.getenv("ComSpec") ?: "C:\\Windows\\System32\\cmd.exe"
        return listOf(shell, "/c", command)
    }

    private fun assumeWindows() {
        val osName = System.getProperty("os.name", "").lowercase()
        assumeTrue(osName.contains("windows"), "ConPTY integration tests run only on Windows")
    }

    private class Result(
        val exit: ProcessExit,
        val output: String,
    )

    private class Capture(
        private val reader: Thread,
        private val output: ByteArrayOutputStream,
        private val failure: AtomicReference<Throwable?>,
    ) {
        fun join() {
            reader.join(10_000)
            if (reader.isAlive) {
                fail("ConPTY output reader did not finish")
            }
            failure.get()?.let { throw AssertionError("ConPTY reader failed", it) }
        }

        fun awaitOutputContains(
            marker: String,
            timeoutMillis: Long,
        ) {
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
            while (System.nanoTime() < deadline) {
                if (output().contains(marker)) {
                    return
                }
                Thread.sleep(20)
            }
            fail("ConPTY output did not contain '$marker' within $timeoutMillis ms: ${output()}")
        }

        fun output(): String = output.toString(Charsets.UTF_8)
    }
}
