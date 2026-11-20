package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.attr.Attributes
import com.psiwray.organization.terminal_window_cmdline.vt.attr.Color
import com.psiwray.organization.terminal_window_cmdline.vt.input.KittyKeyboard
import com.psiwray.organization.terminal_window_cmdline.vt.limits.VtLimits
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserSink
import com.psiwray.organization.terminal_window_cmdline.vt.screen.Screen
import com.psiwray.organization.terminal_window_cmdline.vt.screen.WidthTable
import com.psiwray.organization.terminal_window_cmdline.vt.text.CodePointSink
import com.psiwray.organization.terminal_window_cmdline.vt.text.Utf8Decoder

/**
 * Mutable terminal state driven by [ParserEvent]s.
 *
 * The state owns the alternating screen buffers, cursor, margins, tab stops,
 * pen, modes, titles, and query responses. It is confined to one thread and
 * is not thread-safe.
 */
public class TerminalState(
    rows: Int = DEFAULT_ROWS,
    columns: Int = DEFAULT_COLUMNS,
) : ParserSink {
    private val surfaces: AlternateScreen = AlternateScreen(rows, columns)
    private val decoder: Utf8Decoder = Utf8Decoder()
    private val codePointSink: CodePointSink = CodePointSink { printCodePoint(it) }

    private var g0: Charset = Charset.ASCII
    private var g1: Charset = Charset.ASCII
    private var gl: Int = 0

    private var dcsDecRqss: Boolean = false
    private val dcsSelector: ByteArray = ByteArray(MAX_DCS_SELECTOR)
    private var dcsSelectorLength: Int = 0

    private var oscCommand: Int = ParserEvent.NO_COMMAND
    private var oscTitleKind: Int = 0
    private var oscTitleBytes: Int = 0
    private val oscTitle: StringBuilder = StringBuilder()

    internal var penForeground: Int = 0
    internal var penBackground: Int = 0
    internal var penUnderlineColor: Int = 0
    internal var penAttributes: Int = 0

    /** Observable screen buffer. */
    public val screen: Screen
        get() = surfaces.active.screen

    /** True when the alternate screen is active. */
    public val alternateScreen: Boolean
        get() = surfaces.isAlternate

    /** Zero-based cursor row. */
    public var cursorRow: Int
        get() = surfaces.active.cursorRow
        private set(value) {
            surfaces.active.cursorRow = value
        }

    /** Zero-based cursor column. */
    public var cursorColumn: Int
        get() = surfaces.active.cursorColumn
        private set(value) {
            surfaces.active.cursorColumn = value
        }

    /** True when the next printable character wraps to the next line. */
    public var pendingWrap: Boolean
        get() = surfaces.active.pendingWrap
        private set(value) {
            surfaces.active.pendingWrap = value
        }

    /** Inclusive top scrolling-region row. */
    public var topMargin: Int
        get() = surfaces.active.topMargin
        private set(value) {
            surfaces.active.topMargin = value
        }

    /** Inclusive bottom scrolling-region row. */
    public var bottomMargin: Int
        get() = surfaces.active.bottomMargin
        private set(value) {
            surfaces.active.bottomMargin = value
        }

    /** True when cursor addressing is relative to the scrolling region. */
    public var originMode: Boolean = false
        private set

    /** True when printing at the right edge wraps. */
    public var autoWrap: Boolean = true
        private set

    /** True when printing inserts characters instead of overwriting them. */
    public var insertMode: Boolean = false
        private set

    /** True when LF/VT/FF also return to column zero. */
    public var newlineMode: Boolean = false
        private set

    /** True when cursor keys use application mode. */
    public var cursorKeysApplication: Boolean = false
        private set

    /** True when the screen is reversed. */
    public var screenReverse: Boolean = false
        private set

    /** True when the text cursor is visible. */
    public var cursorVisible: Boolean = true
        private set

    /** True when the cursor blink flag is set. */
    public var cursorBlink: Boolean = true
        private set

    /** DECSCUSR shape, `0` through `6`. */
    public var cursorShape: Int = 0
        private set

    /** True when the keypad uses application mode. */
    public var keypadApplication: Boolean = false
        private set

    /** True when Backspace sends BS instead of DEL. */
    public var backspaceSendsBackspace: Boolean = false
        private set

    /** Active mouse tracking mode: zero, 9, 1000, 1002, or 1003. */
    public var mouseTracking: Int = 0
        private set

    /** True when mouse reports use SGR encoding. */
    public var mouseSgr: Boolean = false
        private set

    /** True when focus reporting is enabled. */
    public var focusReporting: Boolean = false
        private set

    /** True when bracketed paste is enabled. */
    public var bracketedPaste: Boolean = false
        private set

    /** True when synchronized output is enabled. */
    public var synchronizedOutput: Boolean = false
        private set

    /** xterm modifyOtherKeys level, `0` through `3`. */
    public var modifyOtherKeysLevel: Int = 0
        private set

    /** Kitty progressive-enhancement flags for the active screen. */
    public val kittyFlags: Int
        get() = surfaces.active.kitty.flags

    /** Current window title. */
    public var windowTitle: String = ""
        private set

    /** Current icon title. */
    public var iconTitle: String = ""
        private set

    /** Current foreground color. */
    public val foreground: Color
        get() = Color.decode(penForeground)

    /** Current background color. */
    public val background: Color
        get() = Color.decode(penBackground)

    /** Current underline color. */
    public val underlineColor: Color
        get() = Color.decode(penUnderlineColor)

    /** Current attribute bitmask. */
    public val attributes: Int
        get() = penAttributes

    /** Bounded queue of terminal query responses. */
    public val responses: Responder = Responder()

    init {
        reset()
    }

    /** Handles one parser event. */
    override fun onEvent(event: ParserEvent) {
        when (event) {
            is ParserEvent.Print -> decoder.decode(event.bytes, 0, event.bytes.size, codePointSink)

            is ParserEvent.Execute -> execute(event.control)

            is ParserEvent.Esc -> escape(event)

            is ParserEvent.Csi -> csi(event)

            is ParserEvent.Osc -> osc(event)

            is ParserEvent.Dcs -> dcs(event)

            is ParserEvent.Apc,
            is ParserEvent.Pm,
            is ParserEvent.Sos,
            -> Unit
        }
    }

    /** Resets both screens and all terminal state. */
    public fun reset() {
        decoder.reset()
        surfaces.reset()
        originMode = false
        autoWrap = true
        insertMode = false
        newlineMode = false
        cursorKeysApplication = false
        screenReverse = false
        cursorVisible = true
        cursorBlink = true
        cursorShape = 0
        keypadApplication = false
        backspaceSendsBackspace = false
        mouseTracking = 0
        mouseSgr = false
        focusReporting = false
        bracketedPaste = false
        synchronizedOutput = false
        modifyOtherKeysLevel = 0
        resetPen()
        g0 = Charset.ASCII
        g1 = Charset.ASCII
        gl = 0
        dcsDecRqss = false
        dcsSelectorLength = 0
        oscCommand = ParserEvent.NO_COMMAND
        oscTitleKind = 0
        oscTitleBytes = 0
        oscTitle.setLength(0)
        windowTitle = ""
        iconTitle = ""
        responses.clear()
    }

    /** Applies a DECSTR soft reset without clearing the screen or tab stops. */
    public fun softReset() {
        val surface = surfaces.active
        surface.topMargin = 0
        surface.bottomMargin = surface.screen.rows - 1
        surface.cursorRow = 0
        surface.cursorColumn = 0
        surface.pendingWrap = false
        originMode = false
        autoWrap = true
        insertMode = false
        cursorKeysApplication = false
        cursorVisible = true
        cursorBlink = true
        cursorShape = 0
        keypadApplication = false
        backspaceSendsBackspace = false
        resetPen()
        g0 = Charset.ASCII
        g1 = Charset.ASCII
        gl = 0
    }

    /** Resizes both screens and resets margins and tab stops. */
    public fun resize(
        rows: Int,
        columns: Int,
    ) {
        require(rows > 0 && columns > 0) { "rows=$rows columns=$columns" }
        surfaces.resize(rows, columns)
        clampCursor()
    }

    internal fun resetPen() {
        penForeground = 0
        penBackground = 0
        penUnderlineColor = 0
        penAttributes = Attributes.NONE
    }

    internal fun respond(text: String) {
        responses.write(text.toByteArray(Charsets.US_ASCII))
    }

    private fun printCodePoint(codePoint: Int) {
        if (codePoint < 0 || codePoint > MAX_CODE_POINT || codePoint < 0x20 || codePoint == 0x7F) {
            return
        }
        val mapped = currentCharset().translate(codePoint)
        val width = WidthTable.width(mapped)
        if (width == 0) {
            appendCombining(mapped)
            return
        }

        if (pendingWrap) {
            if (autoWrap) {
                wrapLine()
            } else {
                pendingWrap = false
            }
        }

        var glyphWidth = width
        if (glyphWidth == 2 && cursorColumn + glyphWidth > screenInstance.columns) {
            if (autoWrap) {
                wrapLine()
            } else {
                glyphWidth = 1
            }
        }

        if (insertMode) {
            screenInstance.insertCells(cursorRow, cursorColumn, glyphWidth)
        }
        screenInstance.putGlyph(
            cursorRow,
            cursorColumn,
            mapped,
            glyphWidth,
            penForeground,
            penBackground,
            penAttributes,
            penUnderlineColor,
        )

        val surface = surfaces.active
        surface.lastCodePoint = mapped
        surface.lastWidth = glyphWidth
        surface.lastGlyphRow = cursorRow
        surface.lastGlyphColumn = cursorColumn
        surface.lastGlyphEnd = cursorColumn + glyphWidth

        if (cursorColumn + glyphWidth >= screenInstance.columns) {
            if (autoWrap) {
                pendingWrap = true
            }
        } else {
            cursorColumn += glyphWidth
        }
    }

    private fun appendCombining(codePoint: Int) {
        val surface = surfaces.active
        if (surface.lastGlyphRow < 0 || surface.lastGlyphColumn < 0) {
            return
        }
        val atEnd = cursorColumn == surface.lastGlyphEnd
        val atPendingEnd = pendingWrap && surface.lastGlyphEnd == screenInstance.columns
        if (cursorRow == surface.lastGlyphRow && (atEnd || atPendingEnd)) {
            screenInstance.appendCombining(surface.lastGlyphRow, surface.lastGlyphColumn, codePoint)
        }
    }

    private fun currentCharset(): Charset = if (gl == 0) g0 else g1

    private fun wrapLine() {
        screenInstance.markWrap(cursorRow)
        cursorColumn = 0
        pendingWrap = false
        index()
    }

    private fun index() {
        pendingWrap = false
        if (cursorRow == bottomMargin) {
            screenInstance.scrollUp(topMargin, bottomMargin + 1, 1)
        } else if (cursorRow < screenInstance.rows - 1) {
            cursorRow++
        }
    }

    private fun reverseIndex() {
        pendingWrap = false
        if (cursorRow == topMargin) {
            screenInstance.scrollDown(topMargin, bottomMargin + 1, 1)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun execute(control: Int) {
        when (control) {
            0x08 -> {
                if (cursorColumn > 0) {
                    cursorColumn--
                }
                pendingWrap = false
            }

            0x09 -> {
                tabForward(1)
            }

            0x0A, 0x0B, 0x0C -> {
                index()
                if (newlineMode) {
                    cursorColumn = 0
                }
            }

            0x0D -> {
                cursorColumn = 0
                pendingWrap = false
            }

            0x0E -> {
                gl = 1
            }

            0x0F -> {
                gl = 0
            }

            else -> {
                return
            }
        }
    }

    private fun escape(event: ParserEvent.Esc) {
        if (event.intermediates.size == 1) {
            val intermediate = event.intermediates[0]
            if (intermediate == HASH) {
                if (event.finalByte == 0x38) {
                    screenInstance.fillWithE()
                }
                return
            }
            if (intermediate == LEFT_PAREN) {
                g0 = charsetFor(event.finalByte) ?: return
                return
            }
            if (intermediate == RIGHT_PAREN) {
                g1 = charsetFor(event.finalByte) ?: return
                return
            }
            return
        }
        if (event.intermediates.isNotEmpty()) {
            return
        }
        when (event.finalByte) {
            0x37 -> {
                saveCursor()
            }

            0x38 -> {
                restoreCursor()
            }

            0x3D -> {
                keypadApplication = true
            }

            0x3E -> {
                keypadApplication = false
            }

            0x44 -> {
                index()
            }

            0x45 -> {
                index()
                cursorColumn = 0
            }

            0x48 -> {
                setTabStop()
            }

            0x4D -> {
                reverseIndex()
            }

            0x63 -> {
                reset()
            }

            0x5C -> {
                return
            }

            else -> {
                return
            }
        }
    }

    private fun charsetFor(finalByte: Int): Charset? =
        when (finalByte) {
            0x41 -> Charset.UK
            0x30 -> Charset.DEC_SPECIAL
            0x42 -> Charset.ASCII
            else -> null
        }

    private fun csi(event: ParserEvent.Csi) {
        if (event.finalByte == 0x75 && event.intermediates.isEmpty() && kittyKeyboard(event)) {
            return
        }
        if (event.finalByte == 0x6D && event.privateMarker == GREATER) {
            setModifyOtherKeys(event)
            return
        }
        if (event.finalByte == 0x6D && event.privateMarker == QUESTION) {
            queryModifyOtherKeys(event)
            return
        }
        if (event.privateMarker == QUESTION) {
            if (DecRqm.isQuery(event)) {
                DecRqm.respond(this, event, privateMode = true)
            } else {
                decCsi(event)
            }
            return
        }
        if (event.privateMarker != 0) {
            return
        }
        if (DecRqm.isQuery(event)) {
            DecRqm.respond(this, event, privateMode = false)
            return
        }
        if (event.intermediates.size == 1 && event.intermediates[0] == BANG && event.finalByte == 0x70) {
            softReset()
            return
        }
        if (event.intermediates.size == 1 && event.intermediates[0] == SPACE && event.finalByte == 0x71) {
            cursorShape = event.optional(0, 0).coerceIn(0, 6)
            return
        }
        var handled = true
        when (event.finalByte) {
            0x40 -> insertCharacters(event.count(0))
            0x41 -> moveUp(event.count(0))
            0x42 -> moveDown(event.count(0))
            0x43 -> moveForward(event.count(0))
            0x44 -> moveBackward(event.count(0))
            0x45 -> nextLine(event.count(0))
            0x46 -> previousLine(event.count(0))
            0x47 -> setColumnAbsolute(event.count(0))
            0x48, 0x66 -> setCursorPosition(event.count(0), event.count(1))
            0x49 -> tabForward(event.count(0))
            0x4A -> eraseInDisplay(event.value(0))
            0x4B -> eraseInLine(event.value(0))
            0x4C -> insertLines(event.count(0))
            0x4D -> deleteLines(event.count(0))
            0x50 -> deleteCharacters(event.count(0))
            0x53 -> screenInstance.scrollUp(topMargin, bottomMargin + 1, event.count(0))
            0x54 -> screenInstance.scrollDown(topMargin, bottomMargin + 1, event.count(0))
            0x58 -> eraseCharacters(event.count(0))
            0x5A -> tabBackward(event.count(0))
            0x60 -> setColumnAbsolute(event.count(0))
            0x61 -> moveForward(event.count(0))
            0x62 -> repeatPreceding(event.count(0))
            0x63 -> responseDeviceAttributes(event)
            0x64 -> setRowAbsolute(event.count(0))
            0x65 -> moveDown(event.count(0))
            0x67 -> clearTabStop(event.value(0))
            0x68 -> setMode(event, true)
            0x6A -> moveBackward(event.count(0))
            0x6B -> moveUp(event.count(0))
            0x6C -> setMode(event, false)
            0x6D -> Sgr.apply(event, this)
            0x6E -> responseStatusReport(event)
            0x72 -> setMargins(event)
            else -> handled = false
        }
        if (handled) {
            clampCursor()
        }
    }

    private fun kittyKeyboard(event: ParserEvent.Csi): Boolean {
        val keyboard = surfaces.active.kitty
        when (event.privateMarker) {
            QUESTION -> respond("\u001B[?${keyboard.flags}u")
            GREATER -> keyboard.push(event.optional(0, 0))
            LESS -> keyboard.pop(event.count(0))
            EQUAL -> keyboard.set(event.optional(0, 0), event.optional(1, 1))
            else -> return false
        }
        return true
    }

    private fun setModifyOtherKeys(event: ParserEvent.Csi) {
        if (event.parameters.isEmpty()) {
            modifyOtherKeysLevel = 0
            return
        }
        if (event.parameters[0] != MODIFY_OTHER_KEYS) {
            return
        }
        if (event.parameterMore.isNotEmpty() && event.parameterMore[0]) {
            return
        }
        modifyOtherKeysLevel = event.optional(1, 0).coerceIn(0, 3)
    }

    private fun queryModifyOtherKeys(event: ParserEvent.Csi) {
        if (event.parameters.isNotEmpty() && event.parameters[0] != MODIFY_OTHER_KEYS) {
            return
        }
        respond("\u001B[>$MODIFY_OTHER_KEYS;${modifyOtherKeysLevel}m")
    }

    private fun decCsi(event: ParserEvent.Csi) {
        if (DecRqm.isQuery(event)) {
            DecRqm.respond(this, event, privateMode = true)
            return
        }
        when (event.finalByte) {
            0x68 -> {
                for (index in event.parameters.indices) {
                    setDecMode(event.parameters[index], true)
                }
                clampCursor()
            }

            0x6C -> {
                for (index in event.parameters.indices) {
                    setDecMode(event.parameters[index], false)
                }
                clampCursor()
            }

            else -> {
                return
            }
        }
    }

    private fun setDecMode(
        mode: Int,
        value: Boolean,
    ) {
        if (mode == ParserEvent.MISSING_PARAMETER) {
            return
        }
        when (mode) {
            Modes.DEC_CURSOR_KEYS -> {
                cursorKeysApplication = value
            }

            Modes.DEC_REVERSE_SCREEN -> {
                screenReverse = value
            }

            Modes.DEC_ORIGIN -> {
                originMode = value
                cursorRow = if (value) topMargin else 0
                cursorColumn = 0
                pendingWrap = false
            }

            Modes.DEC_AUTOWRAP -> {
                autoWrap = value
                if (!value) {
                    pendingWrap = false
                }
            }

            Modes.DEC_CURSOR_BLINK -> {
                cursorBlink = value
            }

            Modes.DEC_CURSOR_VISIBLE -> {
                cursorVisible = value
            }

            Modes.DEC_KEYPAD -> {
                keypadApplication = value
            }

            Modes.DEC_BACKSPACE -> {
                backspaceSendsBackspace = value
            }

            Modes.DEC_ALT_SCREEN -> {
                if (value) surfaces.enter(clearAlternate = false) else surfaces.leave(clearAlternate = false)
            }

            Modes.DEC_ALT_SCREEN_CLEAR -> {
                if (value) surfaces.enter(clearAlternate = false) else surfaces.leave(clearAlternate = true)
            }

            Modes.DEC_SAVE_CURSOR -> {
                if (value) saveCursor() else restoreCursor()
            }

            Modes.DEC_ALT_SCREEN_SAVE -> {
                if (value) {
                    saveCursor()
                    surfaces.enter(clearAlternate = true)
                } else {
                    surfaces.leave(clearAlternate = false)
                    restoreCursor()
                }
            }

            Modes.DEC_MOUSE_X10,
            Modes.DEC_MOUSE_BUTTON,
            Modes.DEC_MOUSE_DRAG,
            Modes.DEC_MOUSE_MOVE,
            -> {
                if (value) {
                    mouseTracking = mode
                } else if (mouseTracking == mode) {
                    mouseTracking = 0
                }
            }

            Modes.DEC_FOCUS -> {
                focusReporting = value
            }

            Modes.DEC_MOUSE_SGR -> {
                mouseSgr = value
            }

            Modes.DEC_BRACKETED_PASTE -> {
                bracketedPaste = value
            }

            Modes.DEC_SYNCHRONIZED_OUTPUT -> {
                synchronizedOutput = value
            }

            else -> {
                return
            }
        }
    }

    private fun setMode(
        event: ParserEvent.Csi,
        value: Boolean,
    ) {
        for (index in event.parameters.indices) {
            when (event.parameters[index]) {
                Modes.ANSI_INSERT -> insertMode = value
                Modes.ANSI_NEWLINE -> newlineMode = value
                else -> Unit
            }
        }
    }

    private fun setMargins(event: ParserEvent.Csi) {
        val top = event.value(0, 1)
        val bottom = event.value(1, screenInstance.rows)
        if (top < 1 || bottom < 1 || top >= bottom || bottom > screenInstance.rows) {
            return
        }
        topMargin = top - 1
        bottomMargin = bottom - 1
        cursorRow = if (originMode) topMargin else 0
        cursorColumn = 0
        pendingWrap = false
    }

    private fun saveCursor() {
        val surface = surfaces.active
        surface.savedRow = cursorRow
        surface.savedColumn = cursorColumn
        surface.savedPendingWrap = pendingWrap
        surface.savedOriginMode = originMode
        surface.savedForeground = penForeground
        surface.savedBackground = penBackground
        surface.savedUnderlineColor = penUnderlineColor
        surface.savedAttributes = penAttributes
        surface.savedCursorVisible = cursorVisible
        surface.savedCursorBlink = cursorBlink
        surface.savedCursorShape = cursorShape
        surface.savedG0 = g0
        surface.savedG1 = g1
        surface.savedGl = gl
    }

    private fun restoreCursor() {
        val surface = surfaces.active
        cursorRow = surface.savedRow
        cursorColumn = surface.savedColumn
        pendingWrap = surface.savedPendingWrap
        originMode = surface.savedOriginMode
        penForeground = surface.savedForeground
        penBackground = surface.savedBackground
        penUnderlineColor = surface.savedUnderlineColor
        penAttributes = surface.savedAttributes
        cursorVisible = surface.savedCursorVisible
        cursorBlink = surface.savedCursorBlink
        cursorShape = surface.savedCursorShape
        g0 = surface.savedG0
        g1 = surface.savedG1
        gl = surface.savedGl
        clampCursor()
    }

    private fun responseDeviceAttributes(event: ParserEvent.Csi) {
        if (event.parameters.isNotEmpty() && event.parameters[0] != ParserEvent.MISSING_PARAMETER &&
            event.parameters[0] != 0
        ) {
            return
        }
        respond("\u001B[?1;2c")
    }

    private fun responseStatusReport(event: ParserEvent.Csi) {
        val mode = event.optional(0, -1)
        when (mode) {
            5 -> respond("\u001B[0n")
            6 -> respond("\u001B[${cursorRow + 1};${cursorColumn + 1}R")
            else -> Unit
        }
    }

    private fun moveUp(count: Int) {
        cursorRow -= count
        pendingWrap = false
    }

    private fun moveDown(count: Int) {
        cursorRow += count
        pendingWrap = false
    }

    private fun moveForward(count: Int) {
        cursorColumn += count
        pendingWrap = false
    }

    private fun moveBackward(count: Int) {
        cursorColumn -= count
        pendingWrap = false
    }

    private fun nextLine(count: Int) {
        cursorRow += count
        cursorColumn = 0
        pendingWrap = false
    }

    private fun previousLine(count: Int) {
        cursorRow -= count
        cursorColumn = 0
        pendingWrap = false
    }

    private fun setColumnAbsolute(column: Int) {
        cursorColumn = column - 1
        pendingWrap = false
    }

    private fun setRowAbsolute(row: Int) {
        cursorRow = row - 1 + if (originMode) topMargin else 0
        pendingWrap = false
    }

    private fun setCursorPosition(
        row: Int,
        column: Int,
    ) {
        cursorRow = row - 1 + if (originMode) topMargin else 0
        cursorColumn = column - 1
        pendingWrap = false
    }

    private fun tabForward(count: Int) {
        var remaining = count
        val stops = surfaces.active.tabStops
        while (remaining > 0) {
            if (cursorColumn >= screenInstance.columns - 1) {
                pendingWrap = false
                return
            }
            cursorColumn++
            if (stops[cursorColumn]) {
                remaining--
            }
        }
        pendingWrap = false
    }

    private fun tabBackward(count: Int) {
        var remaining = count
        val stops = surfaces.active.tabStops
        while (remaining > 0) {
            if (cursorColumn < 1) {
                pendingWrap = false
                return
            }
            cursorColumn--
            if (stops[cursorColumn]) {
                remaining--
            }
        }
        pendingWrap = false
    }

    private fun setTabStop() {
        surfaces.active.tabStops[cursorColumn] = true
    }

    private fun clearTabStop(mode: Int) {
        when (mode) {
            0 -> surfaces.active.tabStops[cursorColumn] = false
            3 -> surfaces.active.tabStops.fill(false)
            else -> Unit
        }
    }

    private fun insertCharacters(count: Int) {
        if (cursorRow !in topMargin..bottomMargin) {
            return
        }
        screenInstance.insertCells(cursorRow, cursorColumn, count)
    }

    private fun deleteCharacters(count: Int) {
        if (cursorRow !in topMargin..bottomMargin) {
            return
        }
        screenInstance.deleteCells(cursorRow, cursorColumn, count)
    }

    private fun eraseCharacters(count: Int) {
        val end = minOf(screenInstance.columns, cursorColumn + count)
        screenInstance.erase(
            cursorRow,
            cursorRow + 1,
            cursorColumn,
            end,
            penForeground,
            penBackground,
            penAttributes,
            penUnderlineColor,
        )
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in topMargin..bottomMargin) {
            return
        }
        screenInstance.scrollDown(cursorRow, bottomMargin + 1, count)
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in topMargin..bottomMargin) {
            return
        }
        screenInstance.scrollUp(cursorRow, bottomMargin + 1, count)
    }

    private fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> eraseLine(cursorColumn, screenInstance.columns)
            1 -> eraseLine(0, cursorColumn + 1)
            2 -> eraseLine(0, screenInstance.columns)
            else -> Unit
        }
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(cursorColumn, screenInstance.columns)
                eraseRows(cursorRow + 1, screenInstance.rows)
            }

            1 -> {
                eraseRows(0, cursorRow)
                eraseLine(0, cursorColumn + 1)
            }

            2 -> {
                eraseRows(0, screenInstance.rows)
            }

            else -> {
                return
            }
        }
    }

    private fun eraseLine(
        startColumn: Int,
        endColumn: Int,
    ) {
        screenInstance.erase(
            cursorRow,
            cursorRow + 1,
            startColumn,
            endColumn,
            penForeground,
            penBackground,
            penAttributes,
            penUnderlineColor,
        )
    }

    private fun eraseRows(
        startRow: Int,
        endRow: Int,
    ) {
        screenInstance.erase(
            startRow,
            endRow,
            0,
            screenInstance.columns,
            penForeground,
            penBackground,
            penAttributes,
            penUnderlineColor,
        )
    }

    private fun repeatPreceding(count: Int) {
        val surface = surfaces.active
        if (surface.lastWidth < 1 || surface.lastCodePoint == 0) {
            return
        }
        val repetitions = minOf(count, screenInstance.columns - cursorColumn)
        repeat(repetitions) {
            printCodePoint(surface.lastCodePoint)
        }
    }

    private fun clampCursor() {
        if (originMode) {
            cursorRow = cursorRow.coerceIn(topMargin, bottomMargin)
        } else {
            cursorRow = cursorRow.coerceIn(0, screenInstance.rows - 1)
        }
        cursorColumn = cursorColumn.coerceIn(0, screenInstance.columns - 1)
    }

    private fun osc(event: ParserEvent.Osc) {
        if (event.initial) {
            oscCommand = event.command
            oscTitleKind = event.command
            oscTitleBytes = 0
            oscTitle.setLength(0)
        }
        if (oscCommand != 0 && oscCommand != 1 && oscCommand != 2) {
            return
        }
        appendTitleBytes(event.bytes)
        if (event.final) {
            val title = oscTitle.toString()
            when (oscTitleKind) {
                0 -> {
                    windowTitle = title
                    iconTitle = title
                }

                1 -> {
                    iconTitle = title
                }

                2 -> {
                    windowTitle = title
                }
            }
            oscCommand = ParserEvent.NO_COMMAND
        }
    }

    private fun appendTitleBytes(bytes: ByteArray) {
        val remaining = VtLimits.TITLE_BYTES - oscTitleBytes
        if (remaining <= 0) {
            return
        }
        val count = minOf(bytes.size, remaining)
        oscTitle.append(String(bytes, 0, count, Charsets.UTF_8))
        oscTitleBytes += count
    }

    private fun dcs(event: ParserEvent.Dcs) {
        if (event.initial) {
            dcsDecRqss = DecRqss.isQuery(event)
            dcsSelectorLength = 0
        }
        if (!dcsDecRqss) {
            return
        }
        if (dcsSelectorLength + event.bytes.size > dcsSelector.size) {
            dcsSelectorLength = dcsSelector.size + 1
        } else {
            event.bytes.copyInto(dcsSelector, dcsSelectorLength)
            dcsSelectorLength += event.bytes.size
        }
        if (event.final) {
            val selector =
                if (dcsSelectorLength <= dcsSelector.size) {
                    String(dcsSelector, 0, dcsSelectorLength, Charsets.US_ASCII)
                } else {
                    ""
                }
            DecRqss.respond(this, selector)
            dcsDecRqss = false
            dcsSelectorLength = 0
        }
    }

    internal val activeKitty: KittyKeyboard
        get() = surfaces.active.kitty

    private val screenInstance: Screen
        get() = surfaces.active.screen

    private fun ParserEvent.Csi.count(index: Int): Int = value(index, 1).coerceAtLeast(1)

    private fun ParserEvent.Csi.value(
        index: Int,
        default: Int = 0,
    ): Int {
        if (index >= parameters.size) {
            return default
        }
        val value = parameters[index]
        if (value == ParserEvent.MISSING_PARAMETER || value == 0) {
            return default
        }
        return value
    }

    private fun ParserEvent.Csi.optional(
        index: Int,
        default: Int,
    ): Int {
        if (index >= parameters.size || parameters[index] == ParserEvent.MISSING_PARAMETER) {
            return default
        }
        return parameters[index]
    }

    private companion object {
        private const val DEFAULT_ROWS: Int = 24
        private const val DEFAULT_COLUMNS: Int = 80
        private const val MAX_CODE_POINT: Int = 0x10FFFF
        private const val MAX_DCS_SELECTOR: Int = 32
        private const val QUESTION: Int = 0x3F
        private const val GREATER: Int = 0x3E
        private const val LESS: Int = 0x3C
        private const val EQUAL: Int = 0x3D
        private const val MODIFY_OTHER_KEYS: Int = 4
        private const val HASH: Byte = 0x23
        private const val LEFT_PAREN: Byte = 0x28
        private const val RIGHT_PAREN: Byte = 0x29
        private const val BANG: Byte = 0x21
        private const val SPACE: Byte = 0x20
    }
}
