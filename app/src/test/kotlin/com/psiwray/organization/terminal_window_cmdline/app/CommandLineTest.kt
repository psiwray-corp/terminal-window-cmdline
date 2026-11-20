package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window_cmdline.pty.PipeSession
import com.psiwray.organization.terminal_window_cmdline.pty.ProcessSession
import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandLineTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun parsesPipeCommandAndChildDefaults() {
        val config =
            CommandLine.parse(
                arrayOf(
                    "--pipe",
                    "--cols=40",
                    "--rows",
                    "10",
                    "--theme",
                    "mint",
                    "--term",
                    "xterm",
                    "--colorterm",
                    "truecolor",
                    "--env",
                    "CUSTOM=value",
                    "--",
                    "sh",
                    "-c",
                    "printf hello",
                ),
            )

        assertTrue(config.pipe)
        assertEquals(40, config.columns)
        assertEquals(10, config.rows)
        assertEquals("mint", config.theme.name)
        assertEquals(listOf("sh", "-c", "printf hello"), config.command)
        assertEquals(TerminalSize(40, 10), config.size)
        val environment = config.childEnvironment()
        assertEquals("xterm", environment["TERM"])
        assertEquals("truecolor", environment["COLORTERM"])
        assertEquals(CommandLine.TERM_PROGRAM, environment["TERM_PROGRAM"])
        assertEquals(CommandLine.PROGRAM_VERSION, environment["TERM_PROGRAM_VERSION"])
        assertEquals("value", environment["CUSTOM"])
    }

    @Test
    fun acceptsFontAndWorkingDirectory() {
        val font = Files.createFile(temp.resolve("test.twf"))
        val config =
            CommandLine.parse(
                arrayOf(
                    "--pipe",
                    "--font",
                    font.toString(),
                    "--cwd",
                    temp.toString(),
                    "--",
                    "true",
                ),
            )
        assertEquals(font, config.font)
        assertEquals(temp, config.workingDirectory)
    }

    @Test
    fun defaultsToPtyMode() {
        val config = CommandLine.parse(arrayOf("--", "echo"))
        assertFalse(config.pipe)
    }

    @Test
    fun rejectsMissingCommandSeparator() {
        assertFailsWith<IllegalArgumentException> {
            CommandLine.parse(arrayOf("--pipe", "echo"))
        }
    }

    @Test
    fun rejectsBadDimensions() {
        assertFailsWith<IllegalArgumentException> {
            CommandLine.parse(arrayOf("--pipe", "--cols", "0", "--", "echo"))
        }
        assertFailsWith<IllegalArgumentException> {
            CommandLine.parse(arrayOf("--pipe", "--rows", "nope", "--", "echo"))
        }
    }

    @Test
    fun rejectsMalformedEnvironment() {
        assertFailsWith<IllegalArgumentException> {
            CommandLine.parse(arrayOf("--pipe", "--env", "NO_EQUALS", "--", "echo"))
        }
    }

    @Test
    fun rejectsUnknownTheme() {
        assertFailsWith<IllegalArgumentException> {
            CommandLine.parse(arrayOf("--pipe", "--theme", "missing", "--", "echo"))
        }
    }

    @Test
    fun terminalDefaultsAreVisibleToTheChild() {
        assumeFalse(System.getProperty("os.name").lowercase().contains("windows"))
        val config =
            CommandLine.parse(
                arrayOf(
                    "--pipe",
                    "--",
                    "sh",
                    "-c",
                    "printf '%s|%s|%s|%s' \"\$TERM\" \"\$COLORTERM\" \"\$TERM_PROGRAM\" \"\$TERM_PROGRAM_VERSION\"",
                ),
            )
        val session =
            PipeSession(
                command = config.command,
                workingDirectory = config.workingDirectory,
                environment = config.childEnvironment(),
            )
        try {
            assertEquals(
                "xterm-256color|truecolor|terminal-window-cmdline|1.0.0",
                readAll(session),
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun explicitEnvironmentOverridesTerminalDefaults() {
        val config =
            CommandLine.parse(
                arrayOf(
                    "--pipe",
                    "--env",
                    "TERM=custom",
                    "--",
                    "true",
                ),
            )
        assertEquals("custom", config.childEnvironment()["TERM"])
    }

    private fun readAll(session: ProcessSession): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (true) {
            val count = session.read(buffer, 0, buffer.size)
            if (count < 0) {
                break
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
