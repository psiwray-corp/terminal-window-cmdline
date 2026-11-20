package com.psiwray.organization.terminal_window_cmdline.vt.screen

import com.psiwray.organization.terminal_window_cmdline.vt.limits.VtLimits

/**
 * Struct-of-arrays storage for the visible cell grid.
 *
 * All accessors take zero-based row and column indexes. The buffer does not
 * enforce wide-character pairing; [Screen] owns those invariants.
 */
internal class CellBuffer(
    rows: Int,
    columns: Int,
) {
    var rows: Int = rows
        private set

    var columns: Int = columns
        private set

    private var primary: IntArray = IntArray(rows * columns) { SPACE }
    private var widths: ByteArray = ByteArray(rows * columns) { 1 }
    private var flags: ByteArray = ByteArray(rows * columns)
    private var foreground: IntArray = IntArray(rows * columns)
    private var background: IntArray = IntArray(rows * columns)
    private var underlineColor: IntArray = IntArray(rows * columns)
    private var attributes: IntArray = IntArray(rows * columns)
    private var combining: IntArray = IntArray(rows * columns * VtLimits.COMBINING_CODE_POINTS)
    private var combiningCount: ByteArray = ByteArray(rows * columns)

    fun codePointAt(
        row: Int,
        column: Int,
    ): Int = primary[index(row, column)]

    fun widthAt(
        row: Int,
        column: Int,
    ): Int = widths[index(row, column)].toInt()

    fun flagsAt(
        row: Int,
        column: Int,
    ): Int = flags[index(row, column)].toInt()

    fun foregroundAt(
        row: Int,
        column: Int,
    ): Int = foreground[index(row, column)]

    fun backgroundAt(
        row: Int,
        column: Int,
    ): Int = background[index(row, column)]

    fun underlineColorAt(
        row: Int,
        column: Int,
    ): Int = underlineColor[index(row, column)]

    fun attributesAt(
        row: Int,
        column: Int,
    ): Int = attributes[index(row, column)]

    fun combiningCountAt(
        row: Int,
        column: Int,
    ): Int = combiningCount[index(row, column)].toInt()

    fun combiningAt(
        row: Int,
        column: Int,
        index: Int,
    ): Int {
        require(index >= 0 && index < VtLimits.COMBINING_CODE_POINTS) { "combining index=$index" }
        return combining[index(row, column) * VtLimits.COMBINING_CODE_POINTS + index]
    }

    fun setBlank(
        row: Int,
        column: Int,
        foreground: Int = 0,
        background: Int = 0,
        underlineColor: Int = 0,
        attributes: Int = 0,
    ) {
        val cell = index(row, column)
        primary[cell] = SPACE
        widths[cell] = 1
        flags[cell] = 0
        this.foreground[cell] = foreground
        this.background[cell] = background
        this.underlineColor[cell] = underlineColor
        this.attributes[cell] = attributes
        combiningCount[cell] = 0
    }

    fun setGlyph(
        row: Int,
        column: Int,
        codePoint: Int,
        width: Int,
        flags: Int,
        foreground: Int,
        background: Int,
        underlineColor: Int,
        attributes: Int,
    ) {
        val cell = index(row, column)
        primary[cell] = codePoint
        widths[cell] = width.toByte()
        this.flags[cell] = flags.toByte()
        this.foreground[cell] = foreground
        this.background[cell] = background
        this.underlineColor[cell] = underlineColor
        this.attributes[cell] = attributes
        combiningCount[cell] = 0
    }

    fun orFlags(
        row: Int,
        column: Int,
        flags: Int,
    ) {
        val cell = index(row, column)
        this.flags[cell] = (this.flags[cell].toInt() or flags).toByte()
    }

    fun appendCombining(
        row: Int,
        column: Int,
        codePoint: Int,
    ): Boolean {
        val cell = index(row, column)
        val count = combiningCount[cell].toInt()
        if (count >= VtLimits.COMBINING_CODE_POINTS) {
            return false
        }
        combining[cell * VtLimits.COMBINING_CODE_POINTS + count] = codePoint
        combiningCount[cell] = (count + 1).toByte()
        return true
    }

    fun copyCell(
        sourceRow: Int,
        sourceColumn: Int,
        destinationRow: Int,
        destinationColumn: Int,
    ) {
        val source = index(sourceRow, sourceColumn)
        val destination = index(destinationRow, destinationColumn)
        if (source == destination) {
            return
        }
        primary[destination] = primary[source]
        widths[destination] = widths[source]
        flags[destination] = flags[source]
        foreground[destination] = foreground[source]
        background[destination] = background[source]
        underlineColor[destination] = underlineColor[source]
        attributes[destination] = attributes[source]
        combiningCount[destination] = combiningCount[source]
        val sourceOffset = source * VtLimits.COMBINING_CODE_POINTS
        val destinationOffset = destination * VtLimits.COMBINING_CODE_POINTS
        for (index in 0 until VtLimits.COMBINING_CODE_POINTS) {
            combining[destinationOffset + index] = combining[sourceOffset + index]
        }
    }

    fun moveCells(
        row: Int,
        sourceColumn: Int,
        destinationColumn: Int,
        count: Int,
    ) {
        if (count <= 0 || sourceColumn == destinationColumn) {
            return
        }
        if (destinationColumn > sourceColumn) {
            for (offset in count - 1 downTo 0) {
                copyCell(row, sourceColumn + offset, row, destinationColumn + offset)
            }
        } else {
            for (offset in 0 until count) {
                copyCell(row, sourceColumn + offset, row, destinationColumn + offset)
            }
        }
    }

    fun fillRow(
        row: Int,
        startColumn: Int,
        count: Int,
        foreground: Int = 0,
        background: Int = 0,
        underlineColor: Int = 0,
        attributes: Int = 0,
    ) {
        val start = index(row, startColumn)
        val end = start + count
        primary.fill(SPACE, start, end)
        widths.fill(1, start, end)
        flags.fill(0, start, end)
        this.foreground.fill(foreground, start, end)
        this.background.fill(background, start, end)
        this.underlineColor.fill(underlineColor, start, end)
        this.attributes.fill(attributes, start, end)
        combiningCount.fill(0, start, end)
    }

    fun copyRow(
        sourceRow: Int,
        destinationRow: Int,
    ) {
        if (sourceRow == destinationRow) {
            return
        }
        for (column in 0 until columns) {
            copyCell(sourceRow, column, destinationRow, column)
        }
    }

    fun clearAll() {
        primary.fill(SPACE)
        widths.fill(1)
        flags.fill(0)
        foreground.fill(0)
        background.fill(0)
        underlineColor.fill(0)
        attributes.fill(0)
        combiningCount.fill(0)
    }

    fun resize(
        rows: Int,
        columns: Int,
    ) {
        require(rows > 0 && columns > 0) { "rows=$rows columns=$columns" }
        val oldRows = this.rows
        val oldColumns = this.columns
        val oldPrimary = primary
        val oldWidths = widths
        val oldFlags = flags
        val oldForeground = foreground
        val oldBackground = background
        val oldUnderlineColor = underlineColor
        val oldAttributes = attributes
        val oldCombining = combining
        val oldCombiningCount = combiningCount

        val cellCount = rows * columns
        primary = IntArray(cellCount) { SPACE }
        widths = ByteArray(cellCount) { 1 }
        flags = ByteArray(cellCount)
        foreground = IntArray(cellCount)
        background = IntArray(cellCount)
        underlineColor = IntArray(cellCount)
        attributes = IntArray(cellCount)
        combining = IntArray(cellCount * VtLimits.COMBINING_CODE_POINTS)
        combiningCount = ByteArray(cellCount)
        this.rows = rows
        this.columns = columns

        val copyRows = minOf(oldRows, rows)
        val copyColumns = minOf(oldColumns, columns)
        for (row in 0 until copyRows) {
            for (column in 0 until copyColumns) {
                val source = row * oldColumns + column
                val destination = row * columns + column
                primary[destination] = oldPrimary[source]
                widths[destination] = oldWidths[source]
                flags[destination] = oldFlags[source]
                foreground[destination] = oldForeground[source]
                background[destination] = oldBackground[source]
                underlineColor[destination] = oldUnderlineColor[source]
                attributes[destination] = oldAttributes[source]
                combiningCount[destination] = oldCombiningCount[source]
                val sourceOffset = source * VtLimits.COMBINING_CODE_POINTS
                val destinationOffset = destination * VtLimits.COMBINING_CODE_POINTS
                for (index in 0 until VtLimits.COMBINING_CODE_POINTS) {
                    combining[destinationOffset + index] = oldCombining[sourceOffset + index]
                }
            }
        }
    }

    private fun index(
        row: Int,
        column: Int,
    ): Int {
        require(row in 0 until rows && column in 0 until columns) {
            "row=$row column=$column rows=$rows columns=$columns"
        }
        return row * columns + column
    }

    companion object {
        private const val SPACE: Int = 0x20
    }
}
