package com.psiwray.organization.terminal_window_cmdline.vt.state

/**
 * Mode numbers implemented by the v1 terminal-state layer.
 *
 * ANSI modes use `SM`/`RM`; DEC private modes use `DECSET`/`DECRST`.
 */
public object Modes {
    /** ANSI insert/replace mode. */
    public const val ANSI_INSERT: Int = 4

    /** ANSI newline mode. */
    public const val ANSI_NEWLINE: Int = 20

    /** DEC cursor-key application mode. */
    public const val DEC_CURSOR_KEYS: Int = 1

    /** DEC reverse-screen mode. */
    public const val DEC_REVERSE_SCREEN: Int = 5

    /** DEC origin mode. */
    public const val DEC_ORIGIN: Int = 6

    /** DEC autowrap mode. */
    public const val DEC_AUTOWRAP: Int = 7

    /** DEC cursor-blink mode. */
    public const val DEC_CURSOR_BLINK: Int = 12

    /** DEC cursor-visible mode. */
    public const val DEC_CURSOR_VISIBLE: Int = 25

    /** DEC application-keypad mode. */
    public const val DEC_KEYPAD: Int = 66

    /** DEC backspace-sends-backspace mode. */
    public const val DEC_BACKSPACE: Int = 67

    /** Alternate screen without clear or cursor save. */
    public const val DEC_ALT_SCREEN: Int = 47

    /** Alternate screen with clear-on-exit semantics. */
    public const val DEC_ALT_SCREEN_CLEAR: Int = 1047

    /** Save/restore cursor, without switching screens. */
    public const val DEC_SAVE_CURSOR: Int = 1048

    /** Save cursor, switch to a cleared alternate screen, restore on exit. */
    public const val DEC_ALT_SCREEN_SAVE: Int = 1049

    /** X10 press-only mouse tracking. */
    public const val DEC_MOUSE_X10: Int = 9

    /** Press/release mouse tracking. */
    public const val DEC_MOUSE_BUTTON: Int = 1000

    /** Button-motion mouse tracking. */
    public const val DEC_MOUSE_DRAG: Int = 1002

    /** Any-motion mouse tracking. */
    public const val DEC_MOUSE_MOVE: Int = 1003

    /** Focus reporting. */
    public const val DEC_FOCUS: Int = 1004

    /** SGR mouse encoding. */
    public const val DEC_MOUSE_SGR: Int = 1006

    /** Bracketed paste. */
    public const val DEC_BRACKETED_PASTE: Int = 2004

    /** Synchronized output. */
    public const val DEC_SYNCHRONIZED_OUTPUT: Int = 2026

    /** Returns true for an ANSI mode implemented in state. */
    public fun supportsAnsi(mode: Int): Boolean = mode == ANSI_INSERT || mode == ANSI_NEWLINE

    /** Returns true for a DEC private mode implemented in state. */
    public fun supportsDec(mode: Int): Boolean =
        when (mode) {
            DEC_CURSOR_KEYS,
            DEC_REVERSE_SCREEN,
            DEC_ORIGIN,
            DEC_AUTOWRAP,
            DEC_CURSOR_BLINK,
            DEC_CURSOR_VISIBLE,
            DEC_KEYPAD,
            DEC_BACKSPACE,
            DEC_ALT_SCREEN,
            DEC_ALT_SCREEN_CLEAR,
            DEC_ALT_SCREEN_SAVE,
            DEC_MOUSE_X10,
            DEC_MOUSE_BUTTON,
            DEC_MOUSE_DRAG,
            DEC_MOUSE_MOVE,
            DEC_FOCUS,
            DEC_MOUSE_SGR,
            DEC_BRACKETED_PASTE,
            DEC_SYNCHRONIZED_OUTPUT,
            -> true

            else -> false
        }
}
