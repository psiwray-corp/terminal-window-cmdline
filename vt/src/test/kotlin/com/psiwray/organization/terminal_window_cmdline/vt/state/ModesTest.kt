package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.attr.Attributes
import com.psiwray.organization.terminal_window_cmdline.vt.attr.Color
import com.psiwray.organization.terminal_window_cmdline.vt.limits.VtLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModesTest {
    @Test
    fun ansiModesSetResetAndReport() {
        val harness = StateHarness()

        harness.feed("\u001B[4h")
        assertTrue(harness.state.insertMode)
        assertAnsiMode(harness, Modes.ANSI_INSERT, 1)
        harness.feed("\u001B[4l")
        assertFalse(harness.state.insertMode)
        assertAnsiMode(harness, Modes.ANSI_INSERT, 2)

        harness.feed("\u001B[20h")
        assertTrue(harness.state.newlineMode)
        assertAnsiMode(harness, Modes.ANSI_NEWLINE, 1)
        harness.feed("\u001B[20l")
        assertFalse(harness.state.newlineMode)
        assertAnsiMode(harness, Modes.ANSI_NEWLINE, 2)
    }

    @Test
    fun everySupportedDecModeSetsResetsAndReports() {
        val cases: List<Pair<Int, (TerminalState) -> Boolean>> =
            listOf(
                Modes.DEC_CURSOR_KEYS to { it.cursorKeysApplication },
                Modes.DEC_REVERSE_SCREEN to { it.screenReverse },
                Modes.DEC_ORIGIN to { it.originMode },
                Modes.DEC_AUTOWRAP to { it.autoWrap },
                Modes.DEC_CURSOR_BLINK to { it.cursorBlink },
                Modes.DEC_CURSOR_VISIBLE to { it.cursorVisible },
                Modes.DEC_KEYPAD to { it.keypadApplication },
                Modes.DEC_BACKSPACE to { it.backspaceSendsBackspace },
                Modes.DEC_ALT_SCREEN to { it.alternateScreen },
                Modes.DEC_ALT_SCREEN_CLEAR to { it.alternateScreen },
                Modes.DEC_ALT_SCREEN_SAVE to { it.alternateScreen },
                Modes.DEC_MOUSE_X10 to { it.mouseTracking == Modes.DEC_MOUSE_X10 },
                Modes.DEC_MOUSE_BUTTON to { it.mouseTracking == Modes.DEC_MOUSE_BUTTON },
                Modes.DEC_MOUSE_DRAG to { it.mouseTracking == Modes.DEC_MOUSE_DRAG },
                Modes.DEC_MOUSE_MOVE to { it.mouseTracking == Modes.DEC_MOUSE_MOVE },
                Modes.DEC_FOCUS to { it.focusReporting },
                Modes.DEC_MOUSE_SGR to { it.mouseSgr },
                Modes.DEC_BRACKETED_PASTE to { it.bracketedPaste },
                Modes.DEC_SYNCHRONIZED_OUTPUT to { it.synchronizedOutput },
            )

        for ((mode, read) in cases) {
            val harness = StateHarness()
            harness.feed("\u001B[?${mode}h")
            assertTrue(read(harness.state), "mode $mode set")
            assertDecMode(harness, mode, 1)
            harness.feed("\u001B[?${mode}l")
            assertFalse(read(harness.state), "mode $mode reset")
            assertDecMode(harness, mode, 2)
        }
    }

    @Test
    fun decrqmReportsUnknownModesAsZero() {
        val harness = StateHarness()

        harness.feed("\u001B[999\$p")
        assertEquals("\u001B[999;0\$y", harness.readResponses())

        harness.feed("\u001B[?999\$p")
        assertEquals("\u001B[?999;0\$y", harness.readResponses())
    }

    @Test
    fun da1IsConservative() {
        val harness = StateHarness()

        harness.feed("\u001B[c")
        assertEquals("\u001B[?1;2c", harness.readResponses())

        harness.feed("\u001B[0c")
        assertEquals("\u001B[?1;2c", harness.readResponses())
    }

    @Test
    fun dsrReportsStatusAndCursorPosition() {
        val harness = StateHarness()

        harness.feed("\u001B[5n")
        assertEquals("\u001B[0n", harness.readResponses())

        harness.feed("\u001B[10;10H\u001B[6n")
        assertEquals("\u001B[10;10R", harness.readResponses())
    }

    @Test
    fun decrqssReportsSgrMarginsAndCursorShape() {
        val harness = StateHarness()

        harness.feed("\u001B[1;5;7m\u001BP\$qm\u001B\\")
        assertEquals("\u001BP1\$r1;5;7m\u001B\\", harness.readResponses())

        harness.feed("\u001B[5;15r\u001BP\$qr\u001B\\")
        assertEquals("\u001BP1\$r5;15r\u001B\\", harness.readResponses())

        harness.feed("\u001B[3 q\u001BP\$q q\u001B\\")
        assertEquals("\u001BP1\$r3 q\u001B\\", harness.readResponses())
    }

    @Test
    fun invalidDecrqssReturnsFailureResponse() {
        val harness = StateHarness()

        harness.feed("\u001BP\$qz\u001B\\")

        assertEquals("\u001BP0\$r\u001B\\", harness.readResponses())
    }

    @Test
    fun oscTitlesAreStoredAndSeparated() {
        val harness = StateHarness()

        harness.feed("\u001B]2;Window\u0007")
        assertEquals("Window", harness.state.windowTitle)
        assertEquals("", harness.state.iconTitle)

        harness.feed("\u001B]1;Icon\u001B\\")
        assertEquals("Icon", harness.state.iconTitle)

        harness.feed("\u001B]0;Both\u001B\\")
        assertEquals("Both", harness.state.windowTitle)
        assertEquals("Both", harness.state.iconTitle)
    }

    @Test
    fun oscTitleIsBounded() {
        val harness = StateHarness()

        harness.feed("\u001B]2;${"a".repeat(2000)}\u0007")

        assertEquals(VtLimits.TITLE_BYTES, harness.state.windowTitle.length)
    }

    @Test
    fun charsetDesignationsAndShiftInOutWork() {
        val harness = StateHarness()

        harness.feed("\u001B(A#")
        assertEquals(0x00A3, harness.state.screen.codePointAt(0, 0))

        harness.feed("\u001B(0a")
        assertEquals(0x2592, harness.state.screen.codePointAt(0, 1))

        harness.feed("\u001B)0\u000Ea")
        assertEquals(0x2592, harness.state.screen.codePointAt(0, 2))

        harness.feed("\u000F\u001B(Ba")
        assertEquals('a'.code, harness.state.screen.codePointAt(0, 3))
    }

    @Test
    fun decstrResetsModesAndPenWithoutClearingContent() {
        val harness = StateHarness()
        harness.feed("A\u001B[3;10r\u001B[?6h\u001B[?1h\u001B[4h\u001B[1;31m\u001B(0\u001B[5;5H")
        harness.feed("\u001B[!p")

        assertEquals('A'.code, harness.state.screen.codePointAt(0, 0))
        assertEquals(0, harness.state.cursorRow)
        assertEquals(0, harness.state.cursorColumn)
        assertEquals(0, harness.state.topMargin)
        assertEquals(23, harness.state.bottomMargin)
        assertFalse(harness.state.originMode)
        assertTrue(harness.state.autoWrap)
        assertFalse(harness.state.insertMode)
        assertFalse(harness.state.cursorKeysApplication)
        assertEquals(Attributes.NONE, harness.state.attributes)
        assertEquals(Color.Default, harness.state.foreground)
    }

    @Test
    fun risClearsContentAndResetsTabs() {
        val harness = StateHarness()
        harness.feed("A\u001B[5G\u001BH\u001B[?1049h\u001Bc")

        assertEquals(0x20, harness.state.screen.codePointAt(0, 0))
        assertFalse(harness.state.alternateScreen)
        assertEquals(0, harness.state.cursorRow)
        assertEquals(0, harness.state.cursorColumn)

        harness.feed("\t")
        assertEquals(8, harness.state.cursorColumn)
    }

    private fun assertAnsiMode(
        harness: StateHarness,
        mode: Int,
        expected: Int,
    ) {
        harness.feed("\u001B[${mode}\$p")
        assertEquals("\u001B[$mode;$expected\$y", harness.readResponses())
    }

    private fun assertDecMode(
        harness: StateHarness,
        mode: Int,
        expected: Int,
    ) {
        harness.feed("\u001B[?${mode}\$p")
        assertEquals("\u001B[?$mode;$expected\$y", harness.readResponses())
    }
}
