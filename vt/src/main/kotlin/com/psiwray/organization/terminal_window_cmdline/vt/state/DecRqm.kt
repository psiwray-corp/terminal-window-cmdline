package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent

/** DECRQM (`CSI Ps $ p` and `CSI ? Ps $ p`) handling. */
internal object DecRqm {
    private const val DOLLAR: Byte = 0x24

    fun isQuery(event: ParserEvent.Csi): Boolean =
        event.finalByte == 0x70 &&
            event.intermediates.size == 1 &&
            event.intermediates[0] == DOLLAR

    fun respond(
        state: TerminalState,
        event: ParserEvent.Csi,
        privateMode: Boolean,
    ) {
        val rawMode = if (event.parameters.isEmpty()) 0 else event.parameters[0]
        val mode = if (rawMode == ParserEvent.MISSING_PARAMETER) 0 else rawMode
        val status = if (privateMode) decStatus(state, mode) else ansiStatus(state, mode)
        val marker = if (privateMode) "?" else ""
        state.respond("\u001B[$marker$mode;$status\$y")
    }

    private fun ansiStatus(
        state: TerminalState,
        mode: Int,
    ): Int =
        when (mode) {
            Modes.ANSI_INSERT -> if (state.insertMode) 1 else 2
            Modes.ANSI_NEWLINE -> if (state.newlineMode) 1 else 2
            else -> 0
        }

    private fun decStatus(
        state: TerminalState,
        mode: Int,
    ): Int {
        if (!Modes.supportsDec(mode) || mode == Modes.DEC_SAVE_CURSOR) {
            return 0
        }
        val value =
            when (mode) {
                Modes.DEC_CURSOR_KEYS -> state.cursorKeysApplication

                Modes.DEC_REVERSE_SCREEN -> state.screenReverse

                Modes.DEC_ORIGIN -> state.originMode

                Modes.DEC_AUTOWRAP -> state.autoWrap

                Modes.DEC_CURSOR_BLINK -> state.cursorBlink

                Modes.DEC_CURSOR_VISIBLE -> state.cursorVisible

                Modes.DEC_KEYPAD -> state.keypadApplication

                Modes.DEC_BACKSPACE -> state.backspaceSendsBackspace

                Modes.DEC_ALT_SCREEN, Modes.DEC_ALT_SCREEN_CLEAR, Modes.DEC_ALT_SCREEN_SAVE -> state.alternateScreen

                Modes.DEC_MOUSE_X10,
                Modes.DEC_MOUSE_BUTTON,
                Modes.DEC_MOUSE_DRAG,
                Modes.DEC_MOUSE_MOVE,
                -> state.mouseTracking == mode

                Modes.DEC_FOCUS -> state.focusReporting

                Modes.DEC_MOUSE_SGR -> state.mouseSgr

                Modes.DEC_BRACKETED_PASTE -> state.bracketedPaste

                Modes.DEC_SYNCHRONIZED_OUTPUT -> state.synchronizedOutput

                else -> false
            }
        return if (value) 1 else 2
    }
}
