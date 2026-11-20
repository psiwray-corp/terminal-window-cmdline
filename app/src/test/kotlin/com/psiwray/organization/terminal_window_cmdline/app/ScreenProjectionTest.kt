package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ScreenProjectionTest {
    private val theme: Theme = Theme.named("amber")

    @Test
    fun reverseBoldInvisibleAndCursorMapToMatrixCells() {
        val state = TerminalState(rows = 2, columns = 12)
        feed(state, "\u001B[1;31mA\u001B[0;7mB\u001B[0;8mC\u001B[0mD")
        val matrix = project(state)

        assertEquals('A'.code, matrix.codePointAt(0, 0))
        assertEquals(theme.palette[9], matrix.foregroundAt(0, 0))
        assertEquals(theme.background, matrix.backgroundAt(0, 0))

        assertEquals(theme.background, matrix.foregroundAt(1, 0))
        assertEquals(theme.foreground, matrix.backgroundAt(1, 0))

        assertEquals(theme.background, matrix.foregroundAt(2, 0))
        assertEquals(theme.background, matrix.backgroundAt(2, 0))

        assertEquals(theme.foreground, matrix.foregroundAt(3, 0))
        assertEquals(theme.background, matrix.backgroundAt(3, 0))

        // The cursor follows D as a block: foreground and background swap.
        assertEquals(' '.code, matrix.codePointAt(4, 0))
        assertEquals(theme.background, matrix.foregroundAt(4, 0))
        assertEquals(theme.foreground, matrix.backgroundAt(4, 0))
    }

    @Test
    fun wideAndCombiningCellsProjectWithoutExtraGlyphs() {
        val state = TerminalState(rows = 2, columns = 12)
        feed(state, "\u001B[?25l界e\u0301")
        val matrix = project(state)

        assertEquals('界'.code, matrix.codePointAt(0, 0))
        assertEquals(' '.code, matrix.codePointAt(1, 0))
        assertEquals('e'.code, matrix.codePointAt(2, 0))
        assertEquals(' '.code, matrix.codePointAt(3, 0))
    }

    @Test
    fun droppedAttributesDoNotChangeMatrixCells() {
        val state = TerminalState(rows = 2, columns = 12)
        feed(state, "\u001B[?25l\u001B[3;4;9mD")
        val matrix = project(state)

        assertEquals('D'.code, matrix.codePointAt(0, 0))
        assertEquals(theme.foreground, matrix.foregroundAt(0, 0))
        assertEquals(theme.background, matrix.backgroundAt(0, 0))
    }

    @Test
    fun headlessParserToScreenToMatrixShowsText() {
        val state = TerminalState(rows = 2, columns = 12)
        feed(state, "\u001B[?25lhello")
        val matrix = project(state)

        assertEquals('h'.code, matrix.codePointAt(0, 0))
        assertEquals('e'.code, matrix.codePointAt(1, 0))
        assertEquals('l'.code, matrix.codePointAt(2, 0))
        assertEquals('l'.code, matrix.codePointAt(3, 0))
        assertEquals('o'.code, matrix.codePointAt(4, 0))
    }

    private fun feed(
        state: TerminalState,
        text: String,
    ) {
        Parser(state).write(text.toByteArray(Charsets.UTF_8))
    }

    private fun project(state: TerminalState): TerminalMatrix {
        val matrix =
            TerminalMatrix(
                columns = state.screen.columns,
                rows = state.screen.rows,
                defaultForeground = theme.foreground,
                defaultBackground = theme.background,
            )
        ScreenProjection.project(
            view = state.screen,
            cursorRow = state.cursorRow,
            cursorColumn = state.cursorColumn,
            cursorVisible = state.cursorVisible,
            screenReverse = state.screenReverse,
            theme = theme,
            target = matrix,
        )
        return matrix
    }
}
