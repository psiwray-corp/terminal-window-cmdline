package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window_cmdline.vt.attr.Attributes
import com.psiwray.organization.terminal_window_cmdline.vt.attr.Color
import com.psiwray.organization.terminal_window_cmdline.vt.screen.CellFlags
import com.psiwray.organization.terminal_window_cmdline.vt.screen.ScreenView

/**
 * Projects VT cells into the renderer's three-field matrix.
 *
 * The projection is intentionally lossy: underline, italic, strikethrough,
 * hyperlinks, and combining marks are not representable. Reverse, bold, faint,
 * invisible, wide cells, and the block cursor are represented.
 *
 * Public so the benchmark module can measure the production projection path
 * without duplicating it.
 */
public object ScreenProjection {
    private const val SPACE: Int = 0x20

    /**
     * Projects [view] into [target], applying [theme], cursor, and reverse-screen
     * state. The target is fully overwritten.
     */
    @Suppress("LongParameterList")
    public fun project(
        view: ScreenView,
        cursorRow: Int,
        cursorColumn: Int,
        cursorVisible: Boolean,
        screenReverse: Boolean,
        theme: Theme,
        target: TerminalMatrix,
    ) {
        val rows = minOf(view.rows, target.rows)
        val columns = minOf(view.columns, target.columns)
        var row = 0
        while (row < target.rows) {
            var column = 0
            while (column < target.columns) {
                if (row >= rows || column >= columns) {
                    target.set(column, row, SPACE, theme.foreground, theme.background)
                } else {
                    projectCell(
                        view = view,
                        row = row,
                        column = column,
                        cursorRow = cursorRow,
                        cursorColumn = cursorColumn,
                        cursorVisible = cursorVisible,
                        screenReverse = screenReverse,
                        theme = theme,
                        target = target,
                    )
                }
                column++
            }
            row++
        }
    }

    @Suppress("LongParameterList")
    private fun projectCell(
        view: ScreenView,
        row: Int,
        column: Int,
        cursorRow: Int,
        cursorColumn: Int,
        cursorVisible: Boolean,
        screenReverse: Boolean,
        theme: Theme,
        target: TerminalMatrix,
    ) {
        val flags = view.flagsAt(row, column)
        val continuation =
            flags and CellFlags.WIDE_CHAR_SPACER != 0 ||
                flags and CellFlags.LEADING_WIDE_CHAR_SPACER != 0
        val codePoint = if (continuation) SPACE else view.codePointAt(row, column)
        val attributes = view.attributesAt(row, column)

        val foregroundTag = view.foregroundAt(row, column)
        var foreground = Color.argb(foregroundTag, theme.foreground, theme.palette)
        var background = Color.argb(view.backgroundAt(row, column), theme.background, theme.palette)

        if (Attributes.contains(attributes, Attributes.BOLD)) {
            val index = Color.indexedIndex(foregroundTag)
            if (index in 0..7) {
                foreground = theme.palette[index + 8]
            }
        }
        if (Attributes.contains(attributes, Attributes.FAINT)) {
            foreground = blend(foreground, background)
        }
        if (Attributes.contains(attributes, Attributes.REVERSE)) {
            val swap = foreground
            foreground = background
            background = swap
        }
        if (Attributes.contains(attributes, Attributes.INVISIBLE)) {
            foreground = background
        }
        if (screenReverse) {
            val swap = foreground
            foreground = background
            background = swap
        }
        if (cursorVisible && row == cursorRow && column == cursorColumn) {
            val swap = foreground
            foreground = background
            background = swap
        }

        target.set(column, row, codePoint, foreground, background)
    }

    private fun blend(
        foreground: Int,
        background: Int,
    ): Int {
        val red = ((foreground ushr 16 and 0xFF) + (background ushr 16 and 0xFF)) / 2
        val green = ((foreground ushr 8 and 0xFF) + (background ushr 8 and 0xFF)) / 2
        val blue = ((foreground and 0xFF) + (background and 0xFF)) / 2
        return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }
}
