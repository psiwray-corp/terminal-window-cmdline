package com.psiwray.organization.terminal_window_cmdline.vt.conformance

import kotlin.test.Test

class LibVtermScreenHarnessTest {
    @Test
    fun putGlyph() {
        LibVtermScreenHarness.run("/libvterm/10state_putglyph.test")
    }

    @Test
    fun moveCursor() {
        LibVtermScreenHarness.run("/libvterm/11state_movecursor.test")
    }

    @Test
    fun scroll() {
        LibVtermScreenHarness.run("/libvterm/12state_scroll.test")
    }

    @Test
    fun edit() {
        LibVtermScreenHarness.run("/libvterm/13state_edit.test")
    }

    @Test
    fun wrapping() {
        LibVtermScreenHarness.run("/libvterm/20state_wrapping.test")
    }

    @Test
    fun tabStops() {
        LibVtermScreenHarness.run("/libvterm/21state_tabstops.test")
    }

    @Test
    fun saveAndRestore() {
        LibVtermScreenHarness.run("/libvterm/22state_save.test")
    }

    @Test
    fun doubleWidthIsMarkedUnsupported() {
        LibVtermScreenHarness.run("/libvterm/28state_dbl_wh.test")
    }
}
