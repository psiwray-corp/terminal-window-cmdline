package com.psiwray.organization.terminal_window_cmdline.pty.posix

import com.psiwray.organization.terminal_window_cmdline.pty.ProcessExit
import com.psiwray.organization.terminal_window_cmdline.pty.ProcessSession
import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(20)
class PosixPtySessionIntegrationTest {
    @Test
    fun childSeesARealTtyAndInitialSize() {
        assumePosix()
        val session = PosixPtySession(shell("tty; stty size"), size = TerminalSize(80, 24))
        try {
            val output = readAll(session)
            assertTrue(output.contains("/dev/"), output)
            assertTrue(output.contains("24 80"), output)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun reportsExitStatus() {
        assumePosix()
        val session = PosixPtySession(shell("exit 7"))
        try {
            readAll(session)
            assertEquals(7, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun resizeChangesSttySize() {
        assumePosix()
        val session =
            PosixPtySession(
                shell("stty size; read _; stty size"),
                size = TerminalSize(80, 24),
            )
        try {
            readUntil(session, "24 80")
            assertTrue(session.resize(TerminalSize(100, 40)))
            session.write("\n".toByteArray(), 0, 1)
            val output = readAll(session)
            assertTrue(output.contains("40 100"), output)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun resizeDeliversSigwinch() {
        assumePosix()
        val session =
            PosixPtySession(
                shell("trap 'echo WINCH; stty size' WINCH; stty size; read _; echo DONE"),
                size = TerminalSize(80, 24),
            )
        try {
            val reader = BufferedReader(session)
            reader.readUntil("24 80")
            assertTrue(session.resize(TerminalSize(100, 40)))
            writeLine(session, "")
            val winch = reader.readUntil("WINCH")
            assertTrue(winch.contains("WINCH"), winch)
            val size = reader.readUntil("40 100")
            assertTrue(size.contains("40 100"), size)
            reader.readAll()
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun terminateSignalsTheChild() {
        assumePosix()
        val session = PosixPtySession(shell("sleep 30"))
        try {
            session.requestTerminate()
            val exit = awaitExit(session)
            assertEquals(15, exit.signal)
        } finally {
            session.close()
        }
    }

    @Test
    fun childHasAControllingTerminal() {
        assumePosix()
        if (!System.getProperty("os.name").lowercase().contains("linux")) {
            return
        }
        val session = PosixPtySession(shell("ps -o tpgid= -p \$\$; ps -o pgid= -p \$\$"))
        try {
            val output = readAll(session)
            val pids = output.lines().mapNotNull { it.trim().toIntOrNull() }
            assertTrue(pids.size >= 2, output)
            assertEquals(pids[0], pids[1], output)
            assertTrue(pids[0] > 0, output)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun backgroundAndForegroundJobsWork() {
        assumePosix()
        val session = PosixPtySession(shell("set -m; sleep 0.2 & jobs; fg; printf 'fg-done'"))
        try {
            val output = readAll(session)
            assertTrue(output.contains("fg-done"), output)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun vimStartsInFullScreenMode() {
        assumePosix()
        assumeCommand("vim")
        val session = PosixPtySession(listOf("vim", "-u", "NONE", "-n", "-i", "NONE"), environment = termEnvironment())
        try {
            val output = readUntilMarker(session, "\u001b[?1049h")
            assertTrue(output.contains("\u001b[?1049h"), output)
            session.write(":q!\r".toByteArray(), 0, 4)
            readAll(session)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun lessRunsInteractively() {
        assumePosix()
        assumeCommand("less")
        val file = Files.createTempFile("pty-less", ".txt")
        Files.writeString(file, "LESS_MARKER\n")
        val session = PosixPtySession(listOf("less", file.toString()), environment = termEnvironment())
        try {
            readUntilMarker(session, "LESS_MARKER")
            session.write("q".toByteArray(), 0, 1)
            readAll(session)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun topRunsInteractively() {
        assumePosix()
        if (!System.getProperty("os.name").lowercase().contains("linux")) {
            return
        }
        assumeCommand("top")
        // Batch mode exits after one sample; this test proves top can start
        // inside the PTY without depending on a locale-specific header.
        val session = PosixPtySession(listOf("top", "-b", "-n", "1"), environment = termEnvironment())
        try {
            val output = readAll(session)
            assertTrue(output.isNotBlank(), output)
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
        }
    }

    @Test
    fun ctrlZStopsAndResumesAJob() {
        assumePosix()
        assumeCommand("bash")
        val rcFile = Files.createTempFile("pty-bash", ".rc")
        Files.writeString(rcFile, "PS1='P> '\n")
        val environment = mapOf("TERM" to "xterm-256color")
        val session =
            PosixPtySession(
                listOf("bash", "--noprofile", "--rcfile", rcFile.toString(), "-i"),
                environment = environment,
            )
        try {
            val reader = BufferedReader(session)
            reader.readUntil("P> ")
            writeLine(session, "sleep 30")
            Thread.sleep(1_000)
            session.write(byteArrayOf(0x1a), 0, 1)
            val stopped = reader.readUntil("Stopped")
            assertTrue(stopped.contains("sleep 30"), stopped)
            writeLine(session, "jobs")
            reader.readUntil("Stopped")
            writeLine(session, "kill -KILL %1; echo KILLED")
            val killed = reader.readUntil("KILLED")
            assertTrue(killed.contains("KILLED"), killed)
            Thread.sleep(500)
            writeLine(session, "exit")
            reader.readAll()
            assertEquals(0, awaitExit(session).exitCode)
        } finally {
            session.close()
            Files.deleteIfExists(rcFile)
        }
    }

    @Test
    fun closeTerminatesTheChild() {
        assumePosix()
        val session = PosixPtySession(shell("sleep 30"))
        session.close()
        assertTrue(session.exit != null)
    }

    @Test
    fun childHasNoInheritedPtyDescriptors() {
        assumePosix()
        if (!System.getProperty("os.name").lowercase().contains("linux")) {
            return
        }
        val session = PosixPtySession(shell("ls -1 /proc/self/fd"))
        try {
            val output = readAll(session)
            val descriptors = output.lines().filter { it.trim().toIntOrNull() != null }
            assertTrue(descriptors.size <= 6, output)
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

    private fun readUntil(
        session: ProcessSession,
        marker: String,
    ) {
        readUntilMarker(session, marker)
    }

    private fun readUntilMarker(
        session: ProcessSession,
        marker: String,
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!output.toString(Charsets.UTF_8.name()).contains(marker)) {
            val count = session.read(buffer, 0, buffer.size)
            if (count < 0) {
                break
            }
            output.write(buffer, 0, count)
        }
        val text = output.toString(Charsets.UTF_8.name())
        assertTrue(text.contains(marker), text)
        return text
    }

    private fun termEnvironment(): Map<String, String> = mapOf("TERM" to "xterm-256color")

    private fun writeLine(
        session: ProcessSession,
        value: String,
    ) {
        val bytes = (value + "\n").toByteArray()
        session.write(bytes, 0, bytes.size)
    }

    private class BufferedReader(
        private val session: ProcessSession,
    ) {
        private val pending = ByteArrayOutputStream()

        fun readUntil(marker: String): String {
            val buffer = ByteArray(1024)
            while (!text().contains(marker)) {
                val count = session.read(buffer, 0, buffer.size)
                if (count < 0) {
                    break
                }
                pending.write(buffer, 0, count)
            }
            val all = text()
            assertTrue(all.contains(marker), all)
            val end = all.indexOf(marker) + marker.length
            val result = all.substring(0, end)
            val rest = all.substring(end)
            pending.reset()
            pending.write(rest.toByteArray(Charsets.UTF_8))
            return result
        }

        fun readAll(): String {
            val buffer = ByteArray(1024)
            while (true) {
                val count = session.read(buffer, 0, buffer.size)
                if (count < 0) {
                    break
                }
                pending.write(buffer, 0, count)
            }
            return text()
        }

        private fun text(): String = pending.toString(Charsets.UTF_8.name())
    }

    private fun assumeCommand(name: String) {
        val path = System.getenv("PATH").orEmpty()
        val found = path.split(':').any { directory -> Files.isExecutable(Path.of(directory, name)) }
        org.junit.jupiter.api.Assumptions
            .assumeTrue(found, "$name is not installed")
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
