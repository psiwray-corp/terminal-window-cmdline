package com.psiwray.organization.terminal_window_cmdline.pty

/**
 * Character-cell size of a terminal.
 *
 * @property columns visible columns.
 * @property rows visible rows.
 * @throws IllegalArgumentException if either dimension is not positive.
 */
public data class TerminalSize(
    public val columns: Int,
    public val rows: Int,
) {
    init {
        require(columns > 0) { "columns must be positive: $columns" }
        require(rows > 0) { "rows must be positive: $rows" }
    }
}
