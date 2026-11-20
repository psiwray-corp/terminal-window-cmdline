package com.psiwray.organization.terminal_window_cmdline.pty.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommandLineTest {
    @Test
    fun buildsSimpleCommandLine() {
        assertEquals(
            "cmd.exe /c echo hello",
            CommandLine.build(listOf("cmd.exe", "/c", "echo", "hello")),
        )
    }

    @Test
    fun quotesWhitespaceAndEmptyArguments() {
        assertEquals("\"\"", quoted(""))
        assertEquals("\" \"", quoted(" "))
        assertEquals("\"ab c\"", quoted("ab c"))
        assertEquals("\"ab\tc\"", quoted("ab\tc"))
    }

    @Test
    fun escapesQuotesAndBackslashes() {
        assertEquals("ab\\c", quoted("ab\\c"))
        assertEquals("ab\\\"c", quoted("ab\"c"))
        assertEquals("\\\"", quoted("\""))
        assertEquals("a\\\"b\\\"c", quoted("a\"b\"c"))
        assertEquals("\"ab \\\"c\"", quoted("ab \"c"))
        assertEquals("\"a \\\"b\\\" c\"", quoted("a \"b\" c"))
        assertEquals("\"C:\\Program Files\\\\\"", quoted("C:\\Program Files\\"))
        assertEquals("\"C:\\Program Files\\a.txt\"", quoted("C:\\Program Files\\a.txt"))
    }

    @Test
    fun quotesExecutablePathWithSpaces() {
        assertEquals(
            "\"C:\\Program Files\\cmd.exe\" /c echo hello",
            CommandLine.build(listOf("C:\\Program Files\\cmd.exe", "/c", "echo", "hello")),
        )
    }

    @Test
    fun rejectsEmptyCommand() {
        assertFailsWith<IllegalArgumentException> { CommandLine.build(emptyList()) }
    }

    private fun quoted(argument: String): String {
        val builder = StringBuilder()
        CommandLine.appendQuoted(builder, argument)
        return builder.toString()
    }
}
