package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.attr.Color
import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.screen.CellFlags
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalStateTest {
    @Test
    fun pendingWrapPrintsAtRightEdgeOnce() {
        val harness = Harness(rows = 2, columns = 4)

        harness.feed("ABCD")
        assertEquals("ABCD", harness.rowText(0))
        assertTrue(harness.state.pendingWrap)
        assertEquals(0, harness.state.cursorRow)
        assertEquals(3, harness.state.cursorColumn)

        harness.feed("E")
        assertEquals("ABCD", harness.rowText(0))
        assertEquals("E", harness.rowText(1))
        assertFalse(harness.state.pendingWrap)
        assertEquals(1, harness.state.cursorRow)
        assertEquals(1, harness.state.cursorColumn)
    }

    @Test
    fun wideCharacterAtRightEdgeWraps() {
        val harness = Harness(rows = 2, columns = 4)

        harness.feed("\u001b[4G\uFF10")

        assertEquals("", harness.rowText(0))
        assertEquals("\uFF10", harness.rowText(1))
        assertEquals(2, harness.state.screen.widthAt(1, 0))
        assertEquals(CellFlags.WIDE_CHAR_SPACER, harness.state.screen.flagsAt(1, 1))
        assertEquals(1, harness.state.cursorRow)
        assertEquals(2, harness.state.cursorColumn)
    }

    @Test
    fun combiningMarksJoinPreviousCellAndDoNotAdvance() {
        val harness = Harness(rows = 1, columns = 10)

        harness.feed("e\u0301Z")

        assertEquals(1, harness.state.screen.combiningCountAt(0, 0))
        assertEquals(0x301, harness.state.screen.combiningAt(0, 0, 0))
        assertEquals('Z'.code, harness.state.screen.codePointAt(0, 1))
        assertEquals(2, harness.state.cursorColumn)
    }

    @Test
    fun tenthCombiningMarkIsDropped() {
        val harness = Harness(rows = 1, columns = 10)

        harness.feed("e" + "\u0301".repeat(10))

        assertEquals(9, harness.state.screen.combiningCountAt(0, 0))
        assertEquals(1, harness.state.cursorColumn)
    }

    @Test
    fun insertModeShiftsOncePerCodePointNotPerByte() {
        val harness = Harness(rows = 1, columns = 8)

        harness.feed("ABC")
        harness.feed("\u001b[2D")
        harness.feed("\u001b[4h")
        harness.feed("\u00E9")

        assertEquals("A\u00E9BC", harness.rowText(0))
        assertEquals(2, harness.state.cursorColumn)
    }

    @Test
    fun eraseUsesCurrentBackground() {
        val harness = Harness(rows = 1, columns = 4)
        harness.feed("\u001B[47mA")

        harness.feed("\u001B[2J")

        assertEquals(0x20, harness.state.screen.codePointAt(0, 0))
        assertEquals(Color.encode(Color.Indexed(7)), harness.state.screen.backgroundAt(0, 0))
    }

    @Test
    fun originModeBoundsCursorAndHomesAtTopMargin() {
        val harness = Harness(rows = 6, columns = 10)

        harness.feed("\u001b[3;5r\u001b[?6h")
        assertEquals(2, harness.state.cursorRow)
        assertEquals(0, harness.state.cursorColumn)

        harness.feed("\u001b[1;1H")
        assertEquals(2, harness.state.cursorRow)

        harness.feed("\u001b[99;99H")
        assertEquals(4, harness.state.cursorRow)
        assertEquals(9, harness.state.cursorColumn)

        harness.feed("\u001b[9A")
        assertEquals(2, harness.state.cursorRow)
    }

    @Test
    fun invalidMarginsAreIgnored() {
        val harness = Harness(rows = 6, columns = 10)

        harness.feed("\u001b[5;2r")

        assertEquals(0, harness.state.topMargin)
        assertEquals(5, harness.state.bottomMargin)
        assertEquals(0, harness.state.cursorRow)
        assertEquals(0, harness.state.cursorColumn)
    }

    @Test
    fun repeatPrecedingCharacterIsBoundedByTheRow() {
        val harness = Harness(rows = 1, columns = 4)

        harness.feed("A")
        harness.feed("\u001b[3b")

        assertEquals("AAAA", harness.rowText(0))
        assertEquals(3, harness.state.cursorColumn)
        assertTrue(harness.state.pendingWrap)
    }

    @Test
    fun wrapMarksThePreviousLine() {
        val harness = Harness(rows = 2, columns = 4)

        harness.feed("ABCDE")

        assertTrue(harness.state.screen.flagsAt(0, 3) and CellFlags.WRAPLINE != 0)
    }

    @Test
    fun arbitraryBytesNeverThrow() {
        val random = Random(1234)
        val harness = Harness(rows = 8, columns = 20)

        repeat(200) {
            val bytes = ByteArray(random.nextInt(0, 256)) { random.nextInt(0, 256).toByte() }
            harness.feed(bytes)
        }
    }

    @Test
    fun eraseClearsBothHalvesOfWidePair() {
        val harness = Harness(rows = 1, columns = 6)
        harness.feed("\uFF10")

        harness.feed("\u001b[2G\u001b[K")

        assertEquals(0x20, harness.state.screen.codePointAt(0, 0))
        assertEquals(CellFlags.NONE, harness.state.screen.flagsAt(0, 1))
    }

    private class Harness(
        rows: Int,
        columns: Int,
    ) {
        val state: TerminalState = TerminalState(rows, columns)
        private val parser: Parser = Parser(state)

        fun feed(text: String) {
            feed(text.toByteArray(Charsets.UTF_8))
        }

        fun feed(bytes: ByteArray) {
            parser.write(bytes)
        }

        fun rowText(row: Int): String {
            val builder = StringBuilder()
            for (column in 0 until state.screen.columns) {
                val codePoint = state.screen.codePointAt(row, column)
                if (codePoint != 0) {
                    builder.appendCodePoint(codePoint)
                }
            }
            return builder.toString().trimEnd()
        }
    }
}
