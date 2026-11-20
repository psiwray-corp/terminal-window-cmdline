package com.psiwray.organization.terminal_window_cmdline.vt.screen

/**
 * Visible terminal cell grid.
 *
 * Rows and columns are zero-based. The screen owns wide-character pairing,
 * combining-mark storage, and damage tracking; cursor, margins, tabs, and
 * modes belong to `TerminalState`.
 */
public class Screen(
    rows: Int,
    columns: Int,
) : ScreenView {
    init {
        require(rows > 0 && columns > 0) { "rows=$rows columns=$columns" }
    }

    private var buffer: CellBuffer = CellBuffer(rows, columns)
    private var damageTracker: ScreenDamage = ScreenDamage(rows, columns)

    /** Number of visible rows. */
    public override val rows: Int
        get() = buffer.rows

    /** Number of visible columns. */
    public override val columns: Int
        get() = buffer.columns

    internal val damage: ScreenDamage
        get() = damageTracker

    /** Returns the primary code point at [row], [column]. */
    public override fun codePointAt(
        row: Int,
        column: Int,
    ): Int = buffer.codePointAt(row, column)

    /** Returns the cell width, 1 for a normal cell and 2 for a wide primary cell. */
    public override fun widthAt(
        row: Int,
        column: Int,
    ): Int = buffer.widthAt(row, column)

    /** Returns [CellFlags] for [row], [column]. */
    public override fun flagsAt(
        row: Int,
        column: Int,
    ): Int = buffer.flagsAt(row, column)

    /** Returns the placeholder foreground color at [row], [column]. */
    public override fun foregroundAt(
        row: Int,
        column: Int,
    ): Int = buffer.foregroundAt(row, column)

    /** Returns the placeholder background color at [row], [column]. */
    public override fun backgroundAt(
        row: Int,
        column: Int,
    ): Int = buffer.backgroundAt(row, column)

    /** Returns the tagged underline color at [row], [column]. */
    public override fun underlineColorAt(
        row: Int,
        column: Int,
    ): Int = buffer.underlineColorAt(row, column)

    /** Returns the placeholder attribute bitmask at [row], [column]. */
    public override fun attributesAt(
        row: Int,
        column: Int,
    ): Int = buffer.attributesAt(row, column)

    /** Returns the number of combining code points attached to a cell. */
    public fun combiningCountAt(
        row: Int,
        column: Int,
    ): Int = buffer.combiningCountAt(row, column)

    /** Returns combining code point [index] attached to [row], [column]. */
    public fun combiningAt(
        row: Int,
        column: Int,
        index: Int,
    ): Int = buffer.combiningAt(row, column, index)

    internal fun resize(
        rows: Int,
        columns: Int,
    ) {
        require(rows > 0 && columns > 0) { "rows=$rows columns=$columns" }
        val oldColumns = buffer.columns
        buffer.resize(rows, columns)
        damageTracker = ScreenDamage(rows, columns)
        if (columns < oldColumns) {
            for (row in 0 until rows) {
                clearWidePairAt(row, columns - 1)
            }
        }
        damageTracker.mark(0, rows, 0, columns)
    }

    internal fun clear() {
        buffer.clearAll()
        damageTracker.mark(0, rows, 0, columns)
    }

    internal fun fillWithE() {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                buffer.setGlyph(row, column, E, 1, CellFlags.NONE, 0, 0, 0, 0)
            }
        }
        damageTracker.mark(0, rows, 0, columns)
    }

    internal fun putGlyph(
        row: Int,
        column: Int,
        codePoint: Int,
        width: Int,
        foreground: Int,
        background: Int,
        attributes: Int,
        underlineColor: Int = 0,
    ) {
        require(row in 0 until rows && column in 0 until columns) {
            "row=$row column=$column rows=$rows columns=$columns"
        }
        require(width == 1 || width == 2) { "width=$width" }
        clearCell(row, column)
        if (width == 2) {
            require(column + 1 < columns) { "wide glyph at row=$row column=$column" }
            clearCell(row, column + 1)
            buffer.setGlyph(
                row,
                column,
                codePoint,
                2,
                CellFlags.WIDE_CHAR,
                foreground,
                background,
                underlineColor,
                attributes,
            )
            buffer.setGlyph(
                row,
                column + 1,
                0,
                1,
                CellFlags.WIDE_CHAR_SPACER,
                foreground,
                background,
                underlineColor,
                attributes,
            )
            damageTracker.mark(row, row + 1, column, column + 2)
        } else {
            buffer.setGlyph(
                row,
                column,
                codePoint,
                1,
                CellFlags.NONE,
                foreground,
                background,
                underlineColor,
                attributes,
            )
            damageTracker.mark(row, row + 1, column, column + 1)
        }
    }

    internal fun markWrap(row: Int) {
        if (row !in 0 until rows || columns == 0) {
            return
        }
        buffer.orFlags(row, columns - 1, CellFlags.WRAPLINE)
        damageTracker.mark(row, row + 1, columns - 1, columns)
    }

    internal fun appendCombining(
        row: Int,
        column: Int,
        codePoint: Int,
    ) {
        if (buffer.appendCombining(row, column, codePoint)) {
            damageTracker.mark(row, row + 1, column, column + 1)
        }
    }

    internal fun erase(
        startRow: Int,
        endRow: Int,
        startColumn: Int,
        endColumn: Int,
        foreground: Int,
        background: Int,
        attributes: Int,
        underlineColor: Int = 0,
    ) {
        val rowStart = startRow.coerceIn(0, rows)
        val rowEnd = endRow.coerceIn(rowStart, rows)
        val columnStart = startColumn.coerceIn(0, columns)
        val columnEnd = endColumn.coerceIn(columnStart, columns)
        if (rowStart == rowEnd || columnStart == columnEnd) {
            return
        }
        damageTracker.mark(rowStart, rowEnd, maxOf(0, columnStart - 1), minOf(columns, columnEnd + 1))
        for (row in rowStart until rowEnd) {
            for (column in columnStart until columnEnd) {
                clearWidePairAt(row, column)
                buffer.setBlank(row, column, foreground, background, underlineColor, attributes)
            }
        }
    }

    internal fun insertCells(
        row: Int,
        column: Int,
        count: Int,
    ) {
        if (count <= 0 || row !in 0 until rows || column !in 0 until columns) {
            return
        }
        val inserted = minOf(count, columns - column)
        clearWidePairAt(row, column)
        clearWidePairAt(row, columns - inserted)
        clearWidePairAt(row, columns - inserted - 1)
        buffer.moveCells(row, column, column + inserted, columns - column - inserted)
        buffer.fillRow(row, column, inserted)
        damageTracker.mark(row, row + 1, column, columns)
    }

    internal fun deleteCells(
        row: Int,
        column: Int,
        count: Int,
    ) {
        if (count <= 0 || row !in 0 until rows || column !in 0 until columns) {
            return
        }
        val deleted = minOf(count, columns - column)
        clearWidePairAt(row, column)
        clearWidePairAt(row, column + deleted - 1)
        buffer.moveCells(row, column + deleted, column, columns - column - deleted)
        buffer.fillRow(row, columns - deleted, deleted)
        damageTracker.mark(row, row + 1, column, columns)
    }

    internal fun scrollUp(
        top: Int,
        bottom: Int,
        count: Int,
    ) {
        val rowTop = top.coerceIn(0, rows)
        val rowBottom = bottom.coerceIn(rowTop, rows)
        val height = rowBottom - rowTop
        if (height <= 0 || count <= 0) {
            return
        }
        if (count >= height) {
            for (row in rowTop until rowBottom) {
                buffer.fillRow(row, 0, columns)
            }
        } else {
            for (row in rowTop until rowBottom - count) {
                buffer.copyRow(row + count, row)
            }
            for (row in rowBottom - count until rowBottom) {
                buffer.fillRow(row, 0, columns)
            }
        }
        damageTracker.mark(rowTop, rowBottom, 0, columns)
    }

    internal fun scrollDown(
        top: Int,
        bottom: Int,
        count: Int,
    ) {
        val rowTop = top.coerceIn(0, rows)
        val rowBottom = bottom.coerceIn(rowTop, rows)
        val height = rowBottom - rowTop
        if (height <= 0 || count <= 0) {
            return
        }
        if (count >= height) {
            for (row in rowTop until rowBottom) {
                buffer.fillRow(row, 0, columns)
            }
        } else {
            for (row in rowBottom - 1 downTo rowTop + count) {
                buffer.copyRow(row - count, row)
            }
            for (row in rowTop until rowTop + count) {
                buffer.fillRow(row, 0, columns)
            }
        }
        damageTracker.mark(rowTop, rowBottom, 0, columns)
    }

    private fun clearCell(
        row: Int,
        column: Int,
    ) {
        if (row !in 0 until rows || column !in 0 until columns) {
            return
        }
        clearWidePairAt(row, column)
        buffer.setBlank(row, column)
    }

    private fun clearWidePairAt(
        row: Int,
        column: Int,
    ) {
        if (row !in 0 until rows || column !in 0 until columns) {
            return
        }
        val flags = buffer.flagsAt(row, column)
        if (flags and CellFlags.WIDE_CHAR != 0) {
            buffer.setBlank(row, column)
            if (column + 1 < columns && buffer.flagsAt(row, column + 1) and CellFlags.WIDE_CHAR_SPACER != 0) {
                buffer.setBlank(row, column + 1)
            }
        } else if (flags and CellFlags.WIDE_CHAR_SPACER != 0) {
            buffer.setBlank(row, column)
            if (column > 0 && buffer.flagsAt(row, column - 1) and CellFlags.WIDE_CHAR != 0) {
                buffer.setBlank(row, column - 1)
            }
        }
    }

    private companion object {
        private const val E: Int = 0x45
    }
}
