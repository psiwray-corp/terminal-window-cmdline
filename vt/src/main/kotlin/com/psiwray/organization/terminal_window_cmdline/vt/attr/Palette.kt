package com.psiwray.organization.terminal_window_cmdline.vt.attr

/**
 * The default xterm-compatible 256-color palette.
 *
 * Entries zero through fifteen are the ANSI colors, sixteen through 231 the
 * 6x6x6 cube, and 232 through 255 the 24-step gray ramp. The palette is fixed
 * in this section; OSC palette mutation is T1b.
 */
public object Palette {
    /** Default foreground ARGB. */
    public const val DEFAULT_FOREGROUND: Int = 0xFFFFFFFF.toInt()

    /** Default background ARGB. */
    public const val DEFAULT_BACKGROUND: Int = 0xFF000000.toInt()

    private val entries: IntArray = IntArray(256)

    init {
        val ansi =
            intArrayOf(
                0x000000,
                0x800000,
                0x008000,
                0x808000,
                0x000080,
                0x800080,
                0x008080,
                0xC0C0C0,
                0x808080,
                0xFF0000,
                0x00FF00,
                0xFFFF00,
                0x0000FF,
                0xFF00FF,
                0x00FFFF,
                0xFFFFFF,
            )
        for (index in ansi.indices) {
            entries[index] = 0xFF000000.toInt() or ansi[index]
        }

        val levels = intArrayOf(0, 95, 135, 175, 215, 255)
        for (red in 0..5) {
            for (green in 0..5) {
                for (blue in 0..5) {
                    entries[16 + 36 * red + 6 * green + blue] =
                        argb(levels[red], levels[green], levels[blue])
                }
            }
        }

        for (index in 0..23) {
            val value = 8 + index * 10
            entries[232 + index] = argb(value, value, value)
        }
    }

    /** Returns the ARGB value for palette index [index]. */
    public fun color(index: Int): Int {
        require(index in 0..255) { "palette index=$index" }
        return entries[index]
    }

    /** Resolves [color], using [defaultArgb] for [Color.Default]. */
    public fun argb(
        color: Color,
        defaultArgb: Int,
    ): Int =
        when (color) {
            Color.Default -> defaultArgb
            is Color.Indexed -> entries[color.index]
            is Color.Rgb -> argb(color.red, color.green, color.blue)
        }

    private fun argb(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
}
