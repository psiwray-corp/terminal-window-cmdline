package com.psiwray.organization.terminal_window_cmdline.vt.screen

/** Per-cell flags stored in the screen buffer. */
public object CellFlags {
    /** No flags. */
    public const val NONE: Int = 0

    /** The primary half of a wide character. */
    public const val WIDE_CHAR: Int = 1

    /** The continuation half of a wide character. */
    public const val WIDE_CHAR_SPACER: Int = 2

    /** A spacer left at the end of a line when a wide character wrapped. */
    public const val LEADING_WIDE_CHAR_SPACER: Int = 4

    /** The last cell of a line that continues at the start of the next line. */
    public const val WRAPLINE: Int = 8
}
