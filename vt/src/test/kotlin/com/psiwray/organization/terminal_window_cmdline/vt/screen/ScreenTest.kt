package com.psiwray.organization.terminal_window_cmdline.vt.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenTest {
    @Test
    fun widthTableUsesUnambiguousDefaults() {
        assertEquals(1, WidthTable.width('A'.code))
        assertEquals(1, WidthTable.width(0x00A1))
        assertEquals(2, WidthTable.width(0xFF10))
        assertEquals(2, WidthTable.width(0x1F600))
        assertEquals(0, WidthTable.width(0x0301))
    }

    @Test
    fun insertCellsShiftsRight() {
        val screen = Screen(2, 10)
        screen.putGlyph(0, 0, 'A'.code, 1, 0, 0, 0)
        screen.putGlyph(0, 1, 'C'.code, 1, 0, 0, 0)
        screen.putGlyph(0, 2, 'D'.code, 1, 0, 0, 0)

        screen.insertCells(0, 1, 1)

        assertEquals("A CD", rowText(screen, 0))
    }

    @Test
    fun wideCharacterOccupiesTwoCells() {
        val screen = Screen(1, 10)
        screen.putGlyph(0, 0, 0xFF10, 2, 0, 0, 0)

        assertEquals(0xFF10, screen.codePointAt(0, 0))
        assertEquals(2, screen.widthAt(0, 0))
        assertTrue(screen.flagsAt(0, 0) and CellFlags.WIDE_CHAR != 0)
        assertTrue(screen.flagsAt(0, 1) and CellFlags.WIDE_CHAR_SPACER != 0)
    }

    @Test
    fun overwritingEitherHalfClearsWidePair() {
        val screen = Screen(1, 10)
        screen.putGlyph(0, 0, 0xFF10, 2, 0, 0, 0)
        screen.putGlyph(0, 1, 'A'.code, 1, 0, 0, 0)

        assertEquals(0x20, screen.codePointAt(0, 0))
        assertEquals('A'.code, screen.codePointAt(0, 1))
        assertEquals(CellFlags.NONE, screen.flagsAt(0, 0))
        assertEquals(CellFlags.NONE, screen.flagsAt(0, 1))

        screen.putGlyph(0, 2, 0xFF10, 2, 0, 0, 0)
        screen.putGlyph(0, 2, 'B'.code, 1, 0, 0, 0)

        assertEquals('B'.code, screen.codePointAt(0, 2))
        assertEquals(0x20, screen.codePointAt(0, 3))
        assertEquals(CellFlags.NONE, screen.flagsAt(0, 3))
    }

    @Test
    fun combiningMarksAreCappedAtNine() {
        val screen = Screen(1, 10)
        screen.putGlyph(0, 0, 'e'.code, 1, 0, 0, 0)
        repeat(10) { screen.appendCombining(0, 0, 0x301) }

        assertEquals(9, screen.combiningCountAt(0, 0))
        assertEquals(0x301, screen.combiningAt(0, 0, 8))
    }

    @Test
    fun deleteCellsPreservesWidePairAtSource() {
        val screen = Screen(1, 8)
        screen.putGlyph(0, 0, 'A'.code, 1, 0, 0, 0)
        screen.putGlyph(0, 1, 0xFF10, 2, 0, 0, 0)
        screen.putGlyph(0, 3, 'B'.code, 1, 0, 0, 0)

        screen.deleteCells(0, 0, 1)

        assertEquals(0xFF10, screen.codePointAt(0, 0))
        assertEquals(CellFlags.WIDE_CHAR, screen.flagsAt(0, 0))
        assertEquals(CellFlags.WIDE_CHAR_SPACER, screen.flagsAt(0, 1))
        assertEquals('B'.code, screen.codePointAt(0, 2))
    }

    @Test
    fun eraseUsesGivenBackground() {
        val screen = Screen(1, 10)
        screen.putGlyph(0, 0, 'A'.code, 1, 0, 0, 0)
        screen.erase(0, 1, 0, 10, 0, 7, 0)

        assertEquals(0x20, screen.codePointAt(0, 0))
        assertEquals(7, screen.backgroundAt(0, 0))
    }

    @Test
    fun resizePreservesTopLeftCells() {
        val screen = Screen(2, 4)
        screen.putGlyph(0, 0, 'A'.code, 1, 0, 0, 0)
        screen.putGlyph(1, 1, 'B'.code, 1, 0, 0, 0)

        screen.resize(3, 6)

        assertEquals('A'.code, screen.codePointAt(0, 0))
        assertEquals('B'.code, screen.codePointAt(1, 1))
        assertEquals(0x20, screen.codePointAt(2, 5))
    }

    @Test
    fun damageTracksChangedCells() {
        val screen = Screen(2, 10)
        screen.damage.clear()

        screen.putGlyph(1, 2, 'X'.code, 1, 0, 0, 0)

        assertTrue(screen.damage.isDirty)
        assertEquals(1, screen.damage.rectangleCount)
        assertEquals(1, screen.damage.startRow(0))
        assertEquals(2, screen.damage.startColumn(0))
        assertEquals(3, screen.damage.endColumn(0))
    }

    private fun rowText(
        screen: Screen,
        row: Int,
    ): String {
        val builder = StringBuilder()
        for (column in 0 until screen.columns) {
            val codePoint = screen.codePointAt(row, column)
            if (codePoint != 0) {
                builder.appendCodePoint(codePoint)
            }
        }
        return builder.toString().trimEnd()
    }
}
