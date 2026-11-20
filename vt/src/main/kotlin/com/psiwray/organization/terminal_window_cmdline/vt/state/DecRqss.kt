package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent

/** DECRQSS (`DCS $ q ... ST`) handling. */
internal object DecRqss {
    private val DOLLAR: ByteArray = byteArrayOf(0x24)

    fun isQuery(event: ParserEvent.Dcs): Boolean =
        event.privateMarker == 0 &&
            event.finalByte == 0x71 &&
            event.intermediates.contentEquals(DOLLAR)

    fun respond(
        state: TerminalState,
        selector: String,
    ) {
        val body =
            when (selector) {
                "m" -> "1\$r${Sgr.serialize(state)}m"
                "r" -> "1\$r${state.topMargin + 1};${state.bottomMargin + 1}r"
                " q" -> "1\$r${state.cursorShape} q"
                else -> "0\$r"
            }
        state.respond("\u001BP$body\u001B\\")
    }
}
