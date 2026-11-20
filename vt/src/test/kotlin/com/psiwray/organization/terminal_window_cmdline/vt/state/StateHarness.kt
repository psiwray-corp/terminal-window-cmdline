package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser

internal class StateHarness(
    rows: Int = 24,
    columns: Int = 80,
) {
    val state: TerminalState = TerminalState(rows, columns)
    private val parser: Parser = Parser(state)

    fun feed(text: String) {
        parser.write(text.toByteArray(Charsets.UTF_8))
    }

    fun readResponses(): String {
        val bytes = ByteArray(state.responses.available)
        val count = state.responses.read(bytes)
        return String(bytes, 0, count, Charsets.US_ASCII)
    }

    fun rowText(row: Int): String {
        val builder = StringBuilder()
        for (column in 0 until state.screen.columns) {
            val codePoint = state.screen.codePointAt(row, column)
            if (codePoint != 0) {
                builder.appendCodePoint(codePoint)
            }
        }
        return builder.toString().trimEnd()
    }
}
