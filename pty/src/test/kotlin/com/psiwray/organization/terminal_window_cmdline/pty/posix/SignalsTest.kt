package com.psiwray.organization.terminal_window_cmdline.pty.posix

import com.psiwray.organization.terminal_window_cmdline.pty.ProcessExit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SignalsTest {
    @Test
    fun decodesNormalExit() {
        assertEquals(ProcessExit(7), Signals.decode(7 shl 8))
    }

    @Test
    fun decodesSignalDeath() {
        assertEquals(ProcessExit(143, Signals.SIGTERM), Signals.decode(Signals.SIGTERM))
    }

    @Test
    fun decodesStoppedStatusWithoutClaimingASignal() {
        assertEquals(ProcessExit(0x7f), Signals.decode(0x7f))
    }
}
