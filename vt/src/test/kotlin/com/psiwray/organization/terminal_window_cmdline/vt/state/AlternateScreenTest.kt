package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.attr.Attributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlternateScreenTest {
    @Test
    fun mode47HasIndependentCursorMarginsAndTabs() {
        val harness = StateHarness(rows = 10, columns = 20)
        harness.feed("M\u001B[3;3H")
        assertEquals(2, harness.state.cursorRow)
        assertEquals(2, harness.state.cursorColumn)

        harness.feed("\u001B[?47h")
        assertTrue(harness.state.alternateScreen)
        assertEquals(0, harness.state.cursorRow)
        assertEquals(0, harness.state.cursorColumn)
        harness.feed("\u001B[2;4r\u001B[5;5HA")
        assertEquals(1, harness.state.topMargin)
        assertEquals(3, harness.state.bottomMargin)

        harness.feed("\u001B[?47l")
        assertFalse(harness.state.alternateScreen)
        assertEquals(2, harness.state.cursorRow)
        assertEquals(2, harness.state.cursorColumn)
        assertEquals(0, harness.state.topMargin)
        assertEquals(9, harness.state.bottomMargin)

        harness.feed("\u001B[?47h")
        assertEquals(4, harness.state.cursorRow)
        assertEquals(5, harness.state.cursorColumn)
        assertEquals(1, harness.state.topMargin)
        assertEquals(3, harness.state.bottomMargin)
        assertEquals('A'.code, harness.state.screen.codePointAt(4, 4))
    }

    @Test
    fun mode47DoesNotClearTheAlternateScreen() {
        val harness = StateHarness(rows = 4, columns = 10)

        harness.feed("\u001B[?47hA\u001B[?47l")
        harness.feed("\u001B[?47h")

        assertEquals("A", harness.rowText(0))
    }

    @Test
    fun mode1047ClearsAlternateScreenOnExit() {
        val harness = StateHarness(rows = 4, columns = 10)

        harness.feed("\u001B[?1047hA\u001B[?1047l")
        harness.feed("\u001B[?1047h")

        assertEquals("", harness.rowText(0))
    }

    @Test
    fun mode1049SavesCursorClearsAltAndRestores() {
        val harness = StateHarness(rows = 4, columns = 10)
        harness.feed("M")
        assertEquals(0, harness.state.cursorRow)
        assertEquals(1, harness.state.cursorColumn)

        harness.feed("\u001B[?1049h")
        assertTrue(harness.state.alternateScreen)
        assertEquals("", harness.rowText(0))
        harness.feed("A")
        assertEquals("A", harness.rowText(0))

        harness.feed("\u001B[?1049l")
        assertFalse(harness.state.alternateScreen)
        assertEquals("M", harness.rowText(0))
        assertEquals(0, harness.state.cursorRow)
        assertEquals(1, harness.state.cursorColumn)

        harness.feed("\u001B[?1049h")
        assertEquals("", harness.rowText(0))
    }

    @Test
    fun mode1048SavesAndRestoresCursorShapePenAndVisibility() {
        val harness = StateHarness(rows = 8, columns = 20)
        harness.feed("\u001B[2;2H\u001B[1m\u001B[4 q\u001B[?12l\u001B[?25l\u001B[?1048h")

        harness.feed("\u001B[5;5H\u001B[22;4m\u001B[5 q\u001B[?12h\u001B[?25h")
        harness.feed("\u001B[?1048l")

        assertEquals(1, harness.state.cursorRow)
        assertEquals(1, harness.state.cursorColumn)
        assertTrue(harness.state.attributes and Attributes.BOLD != 0)
        assertEquals(0, Attributes.underlineStyle(harness.state.attributes))
        assertEquals(4, harness.state.cursorShape)
        assertFalse(harness.state.cursorBlink)
        assertFalse(harness.state.cursorVisible)
    }

    @Test
    fun decscAndDecrcRestorePenAndCharset() {
        val harness = StateHarness()

        harness.feed("\u001B[1;31m\u001B(0\u001B7")
        harness.feed("\u001B[22;32m\u001B(B\u001B8")

        assertTrue(harness.state.attributes and Attributes.BOLD != 0)
        assertEquals(
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Color
                .Indexed(1),
            harness.state.foreground,
        )
        harness.feed("a")
        assertEquals(0x2592, harness.state.screen.codePointAt(0, 0))
    }
}
