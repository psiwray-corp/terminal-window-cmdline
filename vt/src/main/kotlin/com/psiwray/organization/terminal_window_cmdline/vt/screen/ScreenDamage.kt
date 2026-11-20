package com.psiwray.organization.terminal_window_cmdline.vt.screen

/**
 * Bounded damage rectangles for the visible screen.
 *
 * Rectangles are half-open: rows and columns include the start and exclude the
 * end. The screen marks every mutation; a full projection can clear the damage
 * after drawing.
 */
internal class ScreenDamage(
    private val rows: Int,
    private val columns: Int,
) {
    private var rectangles: IntArray = IntArray(INITIAL_RECTANGLES * 4)
    private var count: Int = 0
    private var full: Boolean = false

    val isDirty: Boolean
        get() = full || count > 0

    val isFull: Boolean
        get() = full

    val rectangleCount: Int
        get() = if (full) 1 else count

    fun startRow(index: Int): Int = if (full) 0 else rectangle(index * 4)

    fun endRow(index: Int): Int = if (full) rows else rectangle(index * 4 + 1)

    fun startColumn(index: Int): Int = if (full) 0 else rectangle(index * 4 + 2)

    fun endColumn(index: Int): Int = if (full) columns else rectangle(index * 4 + 3)

    fun mark(
        startRow: Int,
        endRow: Int,
        startColumn: Int,
        endColumn: Int,
    ) {
        if (full) {
            return
        }
        val rowStart = startRow.coerceIn(0, rows)
        val rowEnd = endRow.coerceIn(rowStart, rows)
        val columnStart = startColumn.coerceIn(0, columns)
        val columnEnd = endColumn.coerceIn(columnStart, columns)
        if (rowStart == rowEnd || columnStart == columnEnd) {
            return
        }

        var mergedStartRow = rowStart
        var mergedEndRow = rowEnd
        var mergedStartColumn = columnStart
        var mergedEndColumn = columnEnd
        var index = 0
        while (index < count) {
            val offset = index * 4
            val otherStartRow = rectangles[offset]
            val otherEndRow = rectangles[offset + 1]
            val otherStartColumn = rectangles[offset + 2]
            val otherEndColumn = rectangles[offset + 3]
            val touchesRows = mergedStartRow <= otherEndRow && mergedEndRow >= otherStartRow
            val touchesColumns = mergedStartColumn <= otherEndColumn && mergedEndColumn >= otherStartColumn
            if (touchesRows && touchesColumns) {
                mergedStartRow = minOf(mergedStartRow, otherStartRow)
                mergedEndRow = maxOf(mergedEndRow, otherEndRow)
                mergedStartColumn = minOf(mergedStartColumn, otherStartColumn)
                mergedEndColumn = maxOf(mergedEndColumn, otherEndColumn)
                removeAt(index)
                index = 0
            } else {
                index++
            }
        }

        if (count == MAX_RECTANGLES) {
            full = true
            count = 0
            return
        }
        ensureCapacity(count + 1)
        val offset = count * 4
        rectangles[offset] = mergedStartRow
        rectangles[offset + 1] = mergedEndRow
        rectangles[offset + 2] = mergedStartColumn
        rectangles[offset + 3] = mergedEndColumn
        count++
    }

    fun clear() {
        count = 0
        full = false
    }

    private fun rectangle(index: Int): Int {
        require(index >= 0 && index < count * 4) { "damage index=$index count=$count" }
        return rectangles[index]
    }

    private fun removeAt(index: Int) {
        val offset = index * 4
        val next = offset + 4
        val end = count * 4
        if (next < end) {
            rectangles.copyInto(rectangles, offset, next, end)
        }
        count--
    }

    private fun ensureCapacity(required: Int) {
        if (required * 4 <= rectangles.size) {
            return
        }
        rectangles = rectangles.copyOf(maxOf(required * 4, rectangles.size * 2))
    }

    private companion object {
        private const val INITIAL_RECTANGLES: Int = 8
        private const val MAX_RECTANGLES: Int = 32
    }
}
