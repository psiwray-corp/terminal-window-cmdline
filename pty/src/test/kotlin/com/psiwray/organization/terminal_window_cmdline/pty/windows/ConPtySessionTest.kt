package com.psiwray.organization.terminal_window_cmdline.pty.windows

import org.junit.jupiter.api.Assumptions.assumeFalse
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConPtySessionTest {
    @Test
    fun nonWindowsHostFailsBeforeNativeInitialization() {
        val osName = System.getProperty("os.name", "").lowercase()
        assumeFalse(osName.contains("windows"), "the ConPTY diagnostic is host-specific")
        assertFailsWith<UnsupportedOperationException> {
            ConPtySession(listOf("cmd.exe"))
        }
    }
}
