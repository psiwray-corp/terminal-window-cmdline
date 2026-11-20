package com.psiwray.organization.terminal_window_cmdline.vt.input

/**
 * Per-screen kitty keyboard state and encoder.
 *
 * Only progressive-enhancement flags `1` (disambiguate), `2` (event types),
 * and `8` (report all keys) are supported. Alternate keys and associated text
 * are T1b and are masked out.
 */
public class KittyKeyboard {
    /** Active progressive-enhancement flags, restricted to supported bits. */
    public var flags: Int = 0
        set(value) {
            field = value and SUPPORTED
        }

    private val stack: IntArray = IntArray(MAX_STACK)
    private var depth: Int = 0

    /** Number of saved flag entries, for tests and diagnostics. */
    public val stackDepth: Int
        get() = depth

    /** Pushes the current flags and replaces them with [newFlags]. */
    public fun push(newFlags: Int) {
        if (depth == stack.size) {
            stack.copyInto(stack, 0, 1, depth)
            depth--
        }
        stack[depth++] = flags
        flags = newFlags
    }

    /** Pops [count] entries; emptying the stack resets all flags. */
    public fun pop(count: Int = 1) {
        val remaining = count.coerceAtLeast(1)
        if (remaining >= depth) {
            depth = 0
            flags = 0
            return
        }
        repeat(remaining) {
            flags = stack[--depth]
        }
    }

    /** Applies [newFlags] using kitty mode `1` replace, `2` set, `3` clear. */
    public fun set(
        newFlags: Int,
        mode: Int,
    ) {
        val incoming = newFlags and SUPPORTED
        flags =
            when (mode) {
                2 -> flags or incoming
                3 -> (flags and incoming.inv()) and SUPPORTED
                else -> incoming
            }
    }

    /** Resets flags and the stack. */
    public fun reset() {
        flags = 0
        depth = 0
    }

    /**
     * Encodes [event] under the current flags, or returns null when legacy
     * encoding should handle it.
     */
    public fun encode(
        event: KeyEvent,
        cursorKeysApplication: Boolean = false,
    ): ByteArray? {
        if (!applies(event)) {
            return null
        }
        val eventType = if (flags and REPORT_EVENT_TYPES != 0) eventType(event.action) else 0
        val suffix = if (eventType != 0) ":$eventType" else ""
        val modifiers = event.modifiers.kittyBits()
        val parameters =
            when {
                modifiers != 0 -> ";${modifiers + 1}$suffix"
                eventType != 0 -> ";1$suffix"
                else -> ""
            }
        val key = event.key
        val navigation = NAVIGATION[key]
        if (navigation != null) {
            if (parameters.isEmpty()) {
                val introducer = if (cursorKeysApplication) "\u001BO" else "\u001B["
                return (introducer + navigation).toByteArray(Charsets.US_ASCII)
            }
            return ("\u001B[1$parameters$navigation").toByteArray(Charsets.US_ASCII)
        }
        val letter = LETTER_FUNCTION[key]
        if (letter != null) {
            if (parameters.isEmpty()) {
                return ("\u001BO$letter").toByteArray(Charsets.US_ASCII)
            }
            return ("\u001B[1$parameters$letter").toByteArray(Charsets.US_ASCII)
        }
        val tilde = TILDE_FUNCTION[key]
        if (tilde != null) {
            return ("\u001B[$tilde$parameters~").toByteArray(Charsets.US_ASCII)
        }
        val code = key.unshiftedCodePoint() ?: SPECIAL_CODE[key] ?: return null
        return ("\u001B[$code${parameters}u").toByteArray(Charsets.US_ASCII)
    }

    private fun applies(event: KeyEvent): Boolean {
        if (event.action == Action.MOVE) {
            return false
        }
        if (event.action == Action.RELEASE && flags and REPORT_EVENT_TYPES == 0) {
            return false
        }
        if (flags and REPORT_ALL_KEYS == 0) {
            when (event.key) {
                Key.ENTER, Key.TAB, Key.BACKSPACE -> return false
                else -> Unit
            }
        }
        if (flags and REPORT_ALL_KEYS != 0) {
            return true
        }
        if (event.key == Key.ESCAPE || !event.key.isPrintable) {
            return flags and (DISAMBIGUATE or REPORT_EVENT_TYPES) != 0
        }
        return flags and DISAMBIGUATE != 0 && event.modifiers.xtermBits() != 0
    }

    private fun eventType(action: Action): Int =
        when (action) {
            Action.PRESS -> 1
            Action.REPEAT -> 2
            Action.RELEASE -> 3
            Action.MOVE -> 0
        }

    private companion object {
        private const val DISAMBIGUATE: Int = 1
        private const val REPORT_EVENT_TYPES: Int = 2
        private const val REPORT_ALL_KEYS: Int = 8
        private const val SUPPORTED: Int = DISAMBIGUATE or REPORT_EVENT_TYPES or REPORT_ALL_KEYS
        private const val MAX_STACK: Int = 32

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
                Key.F4 to "S",
            )

        private val TILDE_FUNCTION: Map<Key, Int> =
            mapOf(
                Key.INSERT to 2,
                Key.DELETE to 3,
                Key.PAGE_UP to 5,
                Key.PAGE_DOWN to 6,
                Key.F3 to 13,
                Key.F5 to 15,
                Key.F6 to 17,
                Key.F7 to 18,
                Key.F8 to 19,
                Key.F9 to 20,
                Key.F10 to 21,
                Key.F11 to 23,
                Key.F12 to 24,
            )

        private val SPECIAL_CODE: Map<Key, Int> =
            mapOf(
                Key.MENU to 57363,
                Key.CAPS_LOCK to 57358,
                Key.SCROLL_LOCK to 57359,
                Key.NUM_LOCK to 57360,
                Key.PRINT_SCREEN to 57361,
                Key.PAUSE to 57362,
                Key.KP_0 to 57399,
                Key.KP_1 to 57400,
                Key.KP_2 to 57401,
                Key.KP_3 to 57402,
                Key.KP_4 to 57403,
                Key.KP_5 to 57404,
                Key.KP_6 to 57405,
                Key.KP_7 to 57406,
                Key.KP_8 to 57407,
                Key.KP_9 to 57408,
                Key.KP_DECIMAL to 57409,
                Key.KP_DIVIDE to 57410,
                Key.KP_MULTIPLY to 57411,
                Key.KP_SUBTRACT to 57412,
                Key.KP_ADD to 57413,
                Key.KP_ENTER to 57414,
                Key.KP_EQUAL to 57415,
                Key.LEFT_SHIFT to 57441,
                Key.LEFT_CONTROL to 57442,
                Key.LEFT_ALT to 57443,
                Key.LEFT_SUPER to 57444,
                Key.RIGHT_SHIFT to 57447,
                Key.RIGHT_CONTROL to 57448,
                Key.RIGHT_ALT to 57449,
                Key.RIGHT_SUPER to 57450,
            )
    }
}
