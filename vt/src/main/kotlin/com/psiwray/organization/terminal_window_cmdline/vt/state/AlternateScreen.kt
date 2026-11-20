package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.input.KittyKeyboard
import com.psiwray.organization.terminal_window_cmdline.vt.screen.Screen

/** Character sets implemented for the GL position. */
internal enum class Charset {
    ASCII,
    UK,
    DEC_SPECIAL,
    ;

    fun translate(codePoint: Int): Int =
        when (this) {
            ASCII -> codePoint
            UK -> if (codePoint == 0x23) 0x00A3 else codePoint
            DEC_SPECIAL -> if (codePoint in 0x5F..0x7E) DEC_SPECIAL_MAP[codePoint - 0x5F] else codePoint
        }

    private companion object {
        private val DEC_SPECIAL_MAP: IntArray =
            intArrayOf(
                0x00A0,
                0x25C6,
                0x2592,
                0x2409,
                0x240C,
                0x240D,
                0x240A,
                0x00B0,
                0x00B1,
                0x2424,
                0x240B,
                0x2518,
                0x2510,
                0x250C,
                0x2514,
                0x253C,
                0x23BA,
                0x23BB,
                0x2500,
                0x23BC,
                0x23BD,
                0x251C,
                0x2524,
                0x2534,
                0x252C,
                0x2502,
                0x2264,
                0x2265,
                0x03C0,
                0x2260,
                0x00A3,
                0x00B7,
            )
    }
}

/**
 * One screen buffer with its own cursor, margins, tabs, and saved cursor.
 *
 * The active [TerminalState] delegates cursor and placement state here so the
 * primary and alternate screens cannot leak positions into each other.
 */
internal class Surface(
    rows: Int,
    columns: Int,
) {
    val screen: Screen = Screen(rows, columns)
    val kitty: KittyKeyboard = KittyKeyboard()

    var cursorRow: Int = 0
    var cursorColumn: Int = 0
    var pendingWrap: Boolean = false
    var topMargin: Int = 0
    var bottomMargin: Int = rows - 1
    var tabStops: BooleanArray = BooleanArray(columns) { it % TAB_WIDTH == 0 }

    var savedRow: Int = 0
    var savedColumn: Int = 0
    var savedPendingWrap: Boolean = false
    var savedOriginMode: Boolean = false
    var savedForeground: Int = 0
    var savedBackground: Int = 0
    var savedUnderlineColor: Int = 0
    var savedAttributes: Int = 0
    var savedCursorVisible: Boolean = true
    var savedCursorBlink: Boolean = true
    var savedCursorShape: Int = 0
    var savedG0: Charset = Charset.ASCII
    var savedG1: Charset = Charset.ASCII
    var savedGl: Int = 0

    var lastCodePoint: Int = 0
    var lastWidth: Int = 0
    var lastGlyphRow: Int = -1
    var lastGlyphColumn: Int = -1
    var lastGlyphEnd: Int = -1

    fun reset() {
        screen.clear()
        kitty.reset()
        cursorRow = 0
        cursorColumn = 0
        pendingWrap = false
        topMargin = 0
        bottomMargin = screen.rows - 1
        resetTabStops()
        savedRow = 0
        savedColumn = 0
        savedPendingWrap = false
        savedOriginMode = false
        savedForeground = 0
        savedBackground = 0
        savedUnderlineColor = 0
        savedAttributes = 0
        savedCursorVisible = true
        savedCursorBlink = true
        savedCursorShape = 0
        savedG0 = Charset.ASCII
        savedG1 = Charset.ASCII
        savedGl = 0
        lastCodePoint = 0
        lastWidth = 0
        lastGlyphRow = -1
        lastGlyphColumn = -1
        lastGlyphEnd = -1
    }

    fun resize(
        rows: Int,
        columns: Int,
    ) {
        screen.resize(rows, columns)
        tabStops = BooleanArray(columns) { it % TAB_WIDTH == 0 }
        topMargin = 0
        bottomMargin = rows - 1
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorColumn = cursorColumn.coerceIn(0, columns - 1)
        savedRow = savedRow.coerceIn(0, rows - 1)
        savedColumn = savedColumn.coerceIn(0, columns - 1)
        pendingWrap = false
        lastGlyphRow = -1
        lastGlyphColumn = -1
        lastGlyphEnd = -1
    }

    fun resetTabStops() {
        for (column in tabStops.indices) {
            tabStops[column] = column % TAB_WIDTH == 0
        }
    }

    private companion object {
        private const val TAB_WIDTH: Int = 8
    }
}

/** Primary and alternate screen buffers with xterm switching semantics. */
internal class AlternateScreen(
    rows: Int,
    columns: Int,
) {
    val main: Surface = Surface(rows, columns)
    val alternate: Surface = Surface(rows, columns)

    var isAlternate: Boolean = false
        private set

    val active: Surface
        get() = if (isAlternate) alternate else main

    fun reset() {
        main.reset()
        alternate.reset()
        isAlternate = false
    }

    fun resize(
        rows: Int,
        columns: Int,
    ) {
        main.resize(rows, columns)
        alternate.resize(rows, columns)
    }

    fun enter(clearAlternate: Boolean) {
        if (clearAlternate) {
            alternate.screen.clear()
        }
        alternate.pendingWrap = false
        isAlternate = true
    }

    fun leave(clearAlternate: Boolean) {
        if (clearAlternate && isAlternate) {
            alternate.screen.clear()
        }
        main.pendingWrap = false
        isAlternate = false
    }
}
