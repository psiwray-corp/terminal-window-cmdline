package com.psiwray.organization.terminal_window_cmdline.pty.posix

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosixLibcTest {
    @Test
    fun reportsWhetherDescriptorsAreOpen() {
        val osName = System.getProperty("os.name", "").lowercase()
        assumeTrue(osName.contains("linux") || osName.contains("mac"), "POSIX only")

        assertTrue(PosixLibc.isOpen(0))
        assertFalse(PosixLibc.isOpen(-1))
    }
}
