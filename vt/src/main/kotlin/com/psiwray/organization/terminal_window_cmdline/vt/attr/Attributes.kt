package com.psiwray.organization.terminal_window_cmdline.vt.attr

/**
 * Per-cell attribute bitmask.
 *
 * Bits zero through six are independent styles. Bits seven through nine hold
 * the underline style (`0` none, `1` single, `2` double, `3` curly, `4` dotted,
 * `5` dashed). Underline style `0` means no underline.
 */
public object Attributes {
    /** No attributes. */
    public const val NONE: Int = 0

    /** Bold or increased intensity. */
    public const val BOLD: Int = 1 shl 0

    /** Faint or decreased intensity. */
    public const val FAINT: Int = 1 shl 1

    /** Italic. */
    public const val ITALIC: Int = 1 shl 2

    /** Blink. */
    public const val BLINK: Int = 1 shl 3

    /** Reverse video. */
    public const val REVERSE: Int = 1 shl 4

    /** Invisible text. */
    public const val INVISIBLE: Int = 1 shl 5

    /** Strikethrough. */
    public const val STRIKE: Int = 1 shl 6

    /** Shift of the three underline-style bits. */
    public const val UNDERLINE_SHIFT: Int = 7

    /** Mask covering the three underline-style bits. */
    public const val UNDERLINE_MASK: Int = 0x7 shl UNDERLINE_SHIFT

    /** Returns the underline style, `0` through `5`. */
    public fun underlineStyle(attributes: Int): Int = (attributes and UNDERLINE_MASK) ushr UNDERLINE_SHIFT

    /** Returns [attributes] with the underline style replaced by [style]. */
    public fun withUnderlineStyle(
        attributes: Int,
        style: Int,
    ): Int {
        require(style in 0..5) { "underline style=$style" }
        return (attributes and UNDERLINE_MASK.inv()) or (style shl UNDERLINE_SHIFT)
    }

    /** Returns true when all bits in [mask] are set in [attributes]. */
    public fun contains(
        attributes: Int,
        mask: Int,
    ): Boolean = attributes and mask == mask
}
