package com.psiwray.organization.terminal_window_cmdline.pty.posix

import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import kotlin.test.assertEquals

class WinSizeTest {
    @Test
    fun writesNativeWinsizeOrder() {
        val arena = Arena.ofConfined()
        try {
            val segment = arena.allocate(8L, 8L)
            WinSize.write(segment, TerminalSize(columns = 100, rows = 40))
            assertEquals(40.toShort(), segment.get(ValueLayout.JAVA_SHORT, 0L))
            assertEquals(100.toShort(), segment.get(ValueLayout.JAVA_SHORT, 2L))
            assertEquals(0.toShort(), segment.get(ValueLayout.JAVA_SHORT, 4L))
            assertEquals(0.toShort(), segment.get(ValueLayout.JAVA_SHORT, 6L))
        } finally {
            arena.close()
        }
    }
}
