package com.psiwray.organization.terminal_window_cmdline.vt.input

/**
 * Keyboard and mouse modifier bits carried by [InputEvent]s.
 *
 * The bits are stable and independent of any windowing toolkit.
 *
 * @property bits combined modifier bit mask.
 */
@JvmInline
public value class Modifiers(
    public val bits: Int,
) {
    /** Returns true when every bit in [modifier] is set. */
    public fun has(modifier: Modifiers): Boolean = modifier.bits != 0 && (bits and modifier.bits) == modifier.bits

    /** Returns true when no modifier bit is set. */
    public fun isEmpty(): Boolean = bits and MASK == 0

    /** Returns the union of this mask and [other]. */
    public infix fun or(other: Modifiers): Modifiers = Modifiers(bits or other.bits)

    /** Legacy/xterm modifier bits: shift=1, alt=2, control=4, super/meta=8. */
    internal fun xtermBits(): Int =
        (if (has(SHIFT)) 1 else 0) or
            (if (has(ALT)) 2 else 0) or
            (if (has(CONTROL)) 4 else 0) or
            (if (has(SUPER)) 8 else 0)

    /** Kitty modifier bits, including Caps Lock and Num Lock. */
    internal fun kittyBits(): Int =
        xtermBits() or
            (if (has(CAPS_LOCK)) 64 else 0) or
            (if (has(NUM_LOCK)) 128 else 0)

    /** xterm mouse modifier bits: shift=4, meta/alt=8, control=16. */
    internal fun mouseBits(): Int =
        (if (has(SHIFT)) 4 else 0) or
            (if (has(ALT)) 8 else 0) or
            (if (has(CONTROL)) 16 else 0)

    /** Named modifier bits. */
    public companion object {
        /** No modifier keys are active. */
        public val NONE: Modifiers = Modifiers(0)

        /** Shift is active. */
        public val SHIFT: Modifiers = Modifiers(1)

        /** Control is active. */
        public val CONTROL: Modifiers = Modifiers(2)

        /** Alt is active. */
        public val ALT: Modifiers = Modifiers(4)

        /** Super (Windows/Command) is active. */
        public val SUPER: Modifiers = Modifiers(8)

        /** Caps Lock is active. */
        public val CAPS_LOCK: Modifiers = Modifiers(16)

        /** Num Lock is active. */
        public val NUM_LOCK: Modifiers = Modifiers(32)

        internal const val SHIFT_BIT: Int = 1
        internal const val CONTROL_BIT: Int = 2
        internal const val ALT_BIT: Int = 4
        internal const val SUPER_BIT: Int = 8
        internal const val CAPS_LOCK_BIT: Int = 16
        internal const val NUM_LOCK_BIT: Int = 32
        internal const val MASK: Int =
            SHIFT_BIT or CONTROL_BIT or ALT_BIT or SUPER_BIT or CAPS_LOCK_BIT or NUM_LOCK_BIT
    }
}
