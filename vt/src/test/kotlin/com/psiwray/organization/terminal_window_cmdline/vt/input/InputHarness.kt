package com.psiwray.organization.terminal_window_cmdline.vt.input

import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState

internal class InputHarness {
    val state: TerminalState = TerminalState(rows = 24, columns = 80)
    private val parser: Parser = Parser(state)
    val encoder: InputEncoder = InputEncoder(state)

    fun feed(text: String) {
        parser.write(text.toByteArray(Charsets.UTF_8))
    }

    fun encode(event: InputEvent): String = String(encoder.encode(event), Charsets.UTF_8)

    fun encodeAscii(event: InputEvent): String = String(encoder.encode(event), Charsets.US_ASCII)

    fun responses(): String {
        val bytes = ByteArray(state.responses.available)
        val count = state.responses.read(bytes)
        return String(bytes, 0, count, Charsets.US_ASCII)
    }
}
