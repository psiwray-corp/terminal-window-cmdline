package com.psiwray.organization.terminal_window_cmdline.vt.input

/**
 * Legacy VT100/xterm key encoding.
 *
 * Printable keys without Control, Alt, or Super are left to [CharEvent] and
 * return null. Special keys and chords are encoded here.
 */
public object LegacyKeymap {
    /** Encodes [event], or returns null when the event is text or unsupported. */
    public fun encode(
        event: KeyEvent,
        cursorKeysApplication: Boolean = false,
        keypadApplication: Boolean = false,
        backspaceSendsBackspace: Boolean = false,
    ): ByteArray? {
        if (event.action == Action.RELEASE || event.action == Action.MOVE) {
            return null
        }
        val key = event.key
        val modifiers = event.modifiers
        val alt = modifiers.has(Modifiers.ALT)
        val control = modifiers.has(Modifiers.CONTROL)
        val shift = modifiers.has(Modifiers.SHIFT)
        val superKey = modifiers.has(Modifiers.SUPER)

        val keypad = keypad(key, keypadApplication)
        if (keypad != null) {
            return keypad.toByteArray(Charsets.US_ASCII)
        }

        when (key) {
            Key.TAB -> {
                val base =
                    when {
                        shift && !control -> "\u001B[Z"
                        control && !shift -> "\u0009"
                        else -> "\u0009"
                    }
                return withAlt(base, alt)
            }

            Key.ENTER -> {
                return withAlt("\u000D", alt)
            }

            Key.ESCAPE -> {
                return withAlt("\u001B", alt)
            }

            Key.BACKSPACE -> {
                val base = if (control || backspaceSendsBackspace) "\u0008" else "\u007F"
                return withAlt(base, alt)
            }

            Key.SPACE -> {
                if (control) {
                    return withAlt("\u0000", alt)
                }
                if (alt) {
                    return "\u001B ".toByteArray(Charsets.US_ASCII)
                }
                if (superKey) {
                    return byteArrayOf(0x20)
                }
                return null
            }

            else -> {}
        }

        val navigation = NAVIGATION[key]
        if (navigation != null) {
            val parameter = modifiers.xtermBits()
            if (parameter == 0) {
                val introducer = if (cursorKeysApplication) "\u001BO" else "\u001B["
                return (introducer + navigation).toByteArray(Charsets.US_ASCII)
            }
            return ("\u001B[1;${parameter + 1}$navigation").toByteArray(Charsets.US_ASCII)
        }

        val letterFunction = LETTER_FUNCTION[key]
        if (letterFunction != null) {
            val parameter = modifiers.xtermBits()
            if (parameter == 0) {
                return ("\u001BO$letterFunction").toByteArray(Charsets.US_ASCII)
            }
            return ("\u001B[1;${parameter + 1}$letterFunction").toByteArray(Charsets.US_ASCII)
        }

        val tildeFunction = TILDE_FUNCTION[key]
        if (tildeFunction != null) {
            val parameter = modifiers.xtermBits()
            if (parameter == 0) {
                return ("\u001B[$tildeFunction~").toByteArray(Charsets.US_ASCII)
            }
            return ("\u001B[$tildeFunction;${parameter + 1}~").toByteArray(Charsets.US_ASCII)
        }

        if (key == Key.MENU) {
            return "\u001B[29~".toByteArray(Charsets.US_ASCII)
        }

        val base = key.unshiftedCodePoint() ?: return null
        if (!control && !alt && !superKey) {
            return null
        }
        val shifted = if (shift) key.shiftedCodePoint() else null
        val character =
            if (control) {
                ctrlByte(shifted ?: base) ?: ctrlByte(base) ?: base
            } else {
                shifted ?: base
            }
        val bytes = byteArrayOf(character.toByte())
        return if (alt) {
            byteArrayOf(0x1B) + bytes
        } else {
            bytes
        }
    }

    private fun keypad(
        key: Key,
        application: Boolean,
    ): String? {
        if (application) {
            return when (key) {
                Key.KP_0 -> "\u001BOp"
                Key.KP_1 -> "\u001BOq"
                Key.KP_2 -> "\u001BOr"
                Key.KP_3 -> "\u001BOs"
                Key.KP_4 -> "\u001BOt"
                Key.KP_5 -> "\u001BOu"
                Key.KP_6 -> "\u001BOv"
                Key.KP_7 -> "\u001BOw"
                Key.KP_8 -> "\u001BOx"
                Key.KP_9 -> "\u001BOy"
                Key.KP_DECIMAL -> "\u001B[3~"
                Key.KP_DIVIDE -> "\u001BOo"
                Key.KP_MULTIPLY -> "\u001BOj"
                Key.KP_SUBTRACT -> "\u001BOm"
                Key.KP_ADD -> "\u001BOk"
                Key.KP_ENTER -> "\u001BOM"
                Key.KP_EQUAL -> "\u001BOX"
                else -> null
            }
        }
        return when (key) {
            Key.KP_0 -> "0"
            Key.KP_1 -> "1"
            Key.KP_2 -> "2"
            Key.KP_3 -> "3"
            Key.KP_4 -> "4"
            Key.KP_5 -> "5"
            Key.KP_6 -> "6"
            Key.KP_7 -> "7"
            Key.KP_8 -> "8"
            Key.KP_9 -> "9"
            Key.KP_DECIMAL -> "."
            Key.KP_DIVIDE -> "/"
            Key.KP_MULTIPLY -> "*"
            Key.KP_SUBTRACT -> "-"
            Key.KP_ADD -> "+"
            Key.KP_ENTER -> "\r"
            Key.KP_EQUAL -> "="
            else -> null
        }
    }

    private fun withAlt(
        text: String,
        alt: Boolean,
    ): ByteArray = if (alt) ("\u001B$text").toByteArray(Charsets.US_ASCII) else text.toByteArray(Charsets.US_ASCII)

    private fun ctrlByte(codePoint: Int): Int? =
        when (codePoint) {
            0x20, 0x40 -> 0x00
            in 0x41..0x5A -> codePoint - 0x40
            in 0x61..0x7A -> codePoint - 0x60
            0x5B -> 0x1B
            0x5C -> 0x1C
            0x5D -> 0x1D
            0x5E -> 0x1E
            0x5F -> 0x1F
            0x3F -> 0x7F
            else -> null
        }

    private val NAVIGATION: Map<Key, String> =
        mapOf(
            Key.UP to "A",
            Key.DOWN to "B",
            Key.RIGHT to "C",
            Key.LEFT to "D",
            Key.HOME to "H",
            Key.END to "F",
        )

    private val LETTER_FUNCTION: Map<Key, String> =
        mapOf(
            Key.F1 to "P",
            Key.F2 to "Q",
            Key.F3 to "R",
            Key.F4 to "S",
        )

    private val TILDE_FUNCTION: Map<Key, Int> =
        mapOf(
            Key.INSERT to 2,
            Key.DELETE to 3,
            Key.PAGE_UP to 5,
            Key.PAGE_DOWN to 6,
            Key.F5 to 15,
            Key.F6 to 17,
            Key.F7 to 18,
            Key.F8 to 19,
            Key.F9 to 20,
            Key.F10 to 21,
            Key.F11 to 23,
            Key.F12 to 24,
        )
}
