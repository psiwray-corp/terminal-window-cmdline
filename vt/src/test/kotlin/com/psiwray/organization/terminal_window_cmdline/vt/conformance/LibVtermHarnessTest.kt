package com.psiwray.organization.terminal_window_cmdline.vt.conformance

import kotlin.test.Test

class LibVtermHarnessTest {
    @Test
    fun parser02() {
        LibVtermHarness.run("/libvterm/02parser.test")
    }
}
