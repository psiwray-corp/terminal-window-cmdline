package com.psiwray.organization.terminal_window_cmdline.vt.attr

/**
 * A terminal color before projection to ARGB.
 *
 * Cells store the tagged integer form, so a cell does not own a color object.
 */
public sealed interface Color {
    /** The terminal's configured default color. */
    public data object Default : Color

    /** A palette index, `0` through `255`. */
    public data class Indexed(
        /** Palette index. */
        public val index: Int,
    ) : Color {
        init {
            require(index in 0..255) { "palette index=$index" }
        }
    }

    /** A 24-bit RGB color. */
    public data class Rgb(
        /** Red component, `0` through `255`. */
        public val red: Int,
        /** Green component, `0` through `255`. */
        public val green: Int,
        /** Blue component, `0` through `255`. */
        public val blue: Int,
    ) : Color {
        init {
            require(red in 0..255 && green in 0..255 && blue in 0..255) {
                "rgb=$red,$green,$blue"
            }
        }
    }

    /** Tagged cell storage helpers. */
    public companion object {
        private const val TAG_MASK: Int = 0xFF000000.toInt()
        private const val INDEXED_TAG: Int = 0x01000000
        private const val RGB_TAG: Int = 0x02000000

        internal fun encode(color: Color): Int =
            when (color) {
                Color.Default -> 0
                is Color.Indexed -> INDEXED_TAG or color.index
                is Color.Rgb -> RGB_TAG or (color.red shl 16) or (color.green shl 8) or color.blue
            }

        /**
         * Decodes the tagged integer stored in a screen cell.
         *
         * Unknown tags decode to [Default]; callers never need the bit layout.
         */
        public fun decode(value: Int): Color =
            when (value and TAG_MASK) {
                0 -> Color.Default
                INDEXED_TAG -> Color.Indexed(value and 0xFF)
                RGB_TAG -> Color.Rgb((value ushr 16) and 0xFF, (value ushr 8) and 0xFF, value and 0xFF)
                else -> Color.Default
            }

        /**
         * Resolves a tagged cell color to opaque ARGB without allocating.
         *
         * @param encoded tagged value from a screen cell.
         * @param defaultArgb value used for [Default].
         * @param palette 256-entry ARGB palette used for indexed colors.
         */
        public fun argb(
            encoded: Int,
            defaultArgb: Int,
            palette: IntArray,
        ): Int {
            require(palette.size >= 256) { "palette must have 256 entries: ${palette.size}" }
            return when (encoded and TAG_MASK) {
                0 -> defaultArgb
                INDEXED_TAG -> palette[encoded and 0xFF]
                RGB_TAG -> OPAQUE_ALPHA or (encoded and 0xFFFFFF)
                else -> defaultArgb
            }
        }

        /** Returns the palette index for [encoded], or `-1` for a non-indexed color. */
        public fun indexedIndex(encoded: Int): Int =
            if (encoded and TAG_MASK == INDEXED_TAG) {
                encoded and 0xFF
            } else {
                -1
            }

        private const val OPAQUE_ALPHA: Int = 0xFF000000.toInt()
    }
}
