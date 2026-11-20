package com.psiwray.organization.terminal_window_cmdline.vt.screen

/**
 * Read-only view of a visible terminal cell grid.
 *
 * Projection code consumes this interface instead of parser or mutable screen
 * state. Coordinates are zero-based and are validated by the implementation.
 */
public interface ScreenView {
    /** Number of visible rows. */
    public val rows: Int

    /** Number of visible columns. */
    public val columns: Int

    /** Returns the primary code point at [row], [column]. */
    public fun codePointAt(
        row: Int,
        column: Int,
    ): Int

    /** Returns the cell width at [row], [column]. */
    public fun widthAt(
        row: Int,
        column: Int,
    ): Int

    /** Returns [CellFlags] at [row], [column]. */
    public fun flagsAt(
        row: Int,
        column: Int,
    ): Int

    /** Returns the tagged foreground color at [row], [column]. */
    public fun foregroundAt(
        row: Int,
        column: Int,
    ): Int

    /** Returns the tagged background color at [row], [column]. */
    public fun backgroundAt(
        row: Int,
        column: Int,
    ): Int

    /** Returns the attribute bitmask at [row], [column]. */
    public fun attributesAt(
        row: Int,
        column: Int,
    ): Int

    /** Returns the tagged underline color at [row], [column]. */
    public fun underlineColorAt(
        row: Int,
        column: Int,
    ): Int
}
