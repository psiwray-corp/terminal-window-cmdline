package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette

/** One foreground/background pair and its ANSI palette. */
public class Theme private constructor(
    /** Theme name used on the command line. */
    public val name: String,
    /** Default foreground color as ARGB. */
    public val foreground: Int,
    /** Default background color as ARGB. */
    public val background: Int,
    ansi: IntArray,
) {
    /** ANSI palette as ARGB, indexed by color number. */
    public val palette: IntArray =
        IntArray(256) { index -> if (index < ansi.size) ansi[index] else Palette.color(index) }

    /** Built-in themes and lookup. */
    public companion object {
        private val AMBER =
            Theme(
                name = "amber",
                foreground = argb(255, 183, 77),
                background = argb(18, 12, 8),
                ansi = ansiPalette(),
            )
        private val MINT =
            Theme(
                name = "mint",
                foreground = argb(128, 222, 234),
                background = argb(8, 18, 20),
                ansi = ansiPalette(),
            )
        private val VIOLET =
            Theme(
                name = "violet",
                foreground = argb(206, 147, 216),
                background = argb(16, 8, 20),
                ansi = ansiPalette(),
            )
        private val PAPER =
            Theme(
                name = "paper",
                foreground = argb(38, 50, 56),
                background = argb(245, 245, 245),
                ansi = ansiPalette(),
            )

        private val THEMES = listOf(AMBER, MINT, VIOLET, PAPER).associateBy { it.name }

        /** Available theme names. */
        public val names: List<String> = THEMES.keys.toList()

        /** Returns the named theme or throws when it is unknown. */
        public fun named(name: String): Theme =
            THEMES[name] ?: throw IllegalArgumentException("unknown theme: $name (expected ${names.joinToString()})")

        private fun ansiPalette(): IntArray =
            intArrayOf(
                0xFF1A1610.toInt(),
                0xFFFF8A65.toInt(),
                0xFFC5E1A5.toInt(),
                0xFFFFD54F.toInt(),
                0xFF90CAF9.toInt(),
                0xFFCE93D8.toInt(),
                0xFF80CBC4.toInt(),
                0xFFFFF3E0.toInt(),
                0xFF6D6257.toInt(),
                0xFFFFAB91.toInt(),
                0xFFDCEDC8.toInt(),
                0xFFFFE082.toInt(),
                0xFFBBDEFB.toInt(),
                0xFFE1BEE7.toInt(),
                0xFFB2DFDB.toInt(),
                0xFFFFFFFF.toInt(),
            )

        private fun argb(
            red: Int,
            green: Int,
            blue: Int,
        ): Int = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }
}
