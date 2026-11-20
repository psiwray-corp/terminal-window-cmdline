package com.psiwray.organization.terminal_window_cmdline.vt.input

/**
 * xterm `modifyOtherKeys` levels 1 and 2.
 *
 * Level 1 encodes ordinary keys only when Alt or Super is active. Level 2
 * encodes ordinary keys when any of Shift, Control, Alt, or Super is active.
 * Level 3 and `formatOtherKeys` are T1b and are not claimed.
 */
public object ModifyOtherKeys {
    /** Disabled. */
    public const val OFF: Int = 0

    /** Encode Alt/Super-modified ordinary keys. */
    public const val LEVEL_1: Int = 1

    /** Encode all modified ordinary keys. */
    public const val LEVEL_2: Int = 2

    /** Encodes [event] for [level], or returns null when legacy applies. */
    public fun encode(
        event: KeyEvent,
        level: Int,
    ): ByteArray? {
        if (event.action == Action.RELEASE || event.action == Action.MOVE) {
            return null
        }
        val modifiers = event.modifiers
        val code = ordinaryCode(event.key, modifiers) ?: return null
        val applies =
            when (level) {
                LEVEL_1 -> {
                    modifiers.has(Modifiers.ALT) || modifiers.has(Modifiers.SUPER)
                }

                LEVEL_2 -> {
                    modifiers.has(Modifiers.SHIFT) ||
                        modifiers.has(Modifiers.CONTROL) ||
                        modifiers.has(Modifiers.ALT) ||
                        modifiers.has(Modifiers.SUPER)
                }

                else -> {
                    false
                }
            }
        if (!applies) {
            return null
        }
        val parameter = modifiers.xtermBits() + 1
        return ("\u001B[27;$parameter;$code~").toByteArray(Charsets.US_ASCII)
    }

    private fun ordinaryCode(
        key: Key,
        modifiers: Modifiers,
    ): Int? =
        when (key) {
            Key.TAB -> {
                9
            }

            Key.ENTER -> {
                13
            }

            Key.ESCAPE -> {
                27
            }

            Key.BACKSPACE -> {
                127
            }

            else -> {
                if (modifiers.has(Modifiers.SHIFT)) {
                    key.shiftedCodePoint() ?: key.unshiftedCodePoint()
                } else {
                    key.unshiftedCodePoint()
                }
            }
        }
}
