package com.psiwray.organization.terminal_window_cmdline.vt.parser

import com.psiwray.organization.terminal_window_cmdline.vt.limits.VtLimits

/**
 * Incremental DEC ANSI parser.
 *
 * The parser consumes arbitrary bytes and never throws for malformed input.
 * It implements the Williams state machine with 7-bit `ESC` introducers only:
 * bytes `0x80` to `0xFF` are printable input, not 8-bit C1 controls.
 *
 * `OSC` terminates on `BEL` or `ST`; `DCS`, `APC`, `PM`, and `SOS` terminate
 * on `ST` only. Cancelled strings never emit a `final` chunk, so consumers must
 * treat string chunks as provisional until `final` is true.
 *
 * The parser emits byte runs to [ParserSink]. UTF-8 decoding is deliberately
 * separate; see `Utf8Decoder`.
 */
public class Parser(
    private val sink: ParserSink,
) {
    private var state: State = State.GROUND
    private val parameters = ParameterBuffer()
    private val intermediates = IntArray(VtLimits.INTERMEDIATE_BYTES)
    private var intermediateCount: Int = 0
    private var privateMarker: Int = 0
    private var sequenceIgnored: Boolean = false

    private val text = ByteArray(TEXT_CHUNK_BYTES)
    private var textLength: Int = 0

    private val stringBytes = ByteArray(VtLimits.STRING_BYTES)
    private var stringLength: Int = 0
    private var stringSeen: Int = 0
    private var stringLimit: Int = VtLimits.STRING_BYTES
    private var stringInitial: Boolean = false
    private var stringStarted: Boolean = false
    private var pendingStringEscape: Boolean = false
    private var stringKind: StringKind = StringKind.NONE

    private var oscCommand: Int = ParserEvent.NO_COMMAND
    private var oscCommandParsed: Boolean = false
    private var oscCommandDigits: Int = 0
    private var oscCommandValue: Int = 0

    private var dcsPrivateMarker: Int = 0
    private var dcsParameters: IntArray = EMPTY_INTS
    private var dcsParameterMore: BooleanArray = EMPTY_BOOLEANS
    private var dcsIntermediates: ByteArray = EMPTY_BYTES
    private var dcsFinalByte: Int = 0

    /**
     * Consumes a complete byte array.
     *
     * Incomplete UTF-8 and string sequences are retained for the next call.
     */
    public fun write(bytes: ByteArray) {
        write(bytes, 0, bytes.size)
    }

    /**
     * Consumes [length] bytes starting at [offset].
     *
     * @throws IllegalArgumentException when the range is outside [bytes].
     */
    public fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
            "offset=$offset length=$length size=${bytes.size}"
        }

        var index = offset
        val end = offset + length
        while (index < end) {
            val byte = bytes[index].toInt() and 0xFF

            if (pendingStringEscape) {
                pendingStringEscape = false
                if (byte == ESC_ST) {
                    finishString()
                    index++
                    continue
                }
                cancelString()
                beginEscape()
                handleEscapeByte(byte)
                index++
                continue
            }

            if (byte == ESC) {
                if (isStringState()) {
                    pendingStringEscape = true
                } else {
                    flushText()
                    beginEscape()
                }
                index++
                continue
            }

            if (byte == CAN || byte == SUB) {
                if (isStringState()) {
                    cancelString()
                } else {
                    flushText()
                    resetSequence()
                    state = State.GROUND
                }
                index++
                continue
            }

            when (state) {
                State.GROUND -> handleGround(byte)
                State.ESCAPE -> handleEscapeByte(byte)
                State.ESCAPE_INTERMEDIATE -> handleEscapeIntermediate(byte)
                State.CSI_ENTRY -> handleCsiEntry(byte)
                State.CSI_PARAM -> handleCsiParam(byte)
                State.CSI_INTERMEDIATE -> handleCsiIntermediate(byte)
                State.CSI_IGNORE -> handleCsiIgnore(byte)
                State.DCS_ENTRY -> handleDcsEntry(byte)
                State.DCS_PARAM -> handleDcsParam(byte)
                State.DCS_INTERMEDIATE -> handleDcsIntermediate(byte)
                State.DCS_PASSTHROUGH -> handleDcsPassthrough(byte)
                State.DCS_IGNORE -> Unit
                State.OSC_STRING -> handleOsc(byte)
                State.SOS_PM_APC_STRING -> handleSimpleString(byte)
            }
            index++
        }

        flushText()
        when (state) {
            State.OSC_STRING -> flushOsc(final = false)
            State.DCS_PASSTHROUGH -> flushDcs(final = false)
            State.SOS_PM_APC_STRING -> flushSimpleString(final = false)
            else -> Unit
        }
    }

    /** Returns the parser to its initial state without emitting events. */
    public fun reset() {
        state = State.GROUND
        resetSequence()
        textLength = 0
        resetStringState()
        pendingStringEscape = false
        dcsPrivateMarker = 0
        dcsParameters = EMPTY_INTS
        dcsParameterMore = EMPTY_BOOLEANS
        dcsIntermediates = EMPTY_BYTES
        dcsFinalByte = 0
    }

    private fun handleGround(byte: Int) {
        when {
            byte == NUL || byte == DEL -> {
                flushText()
            }

            byte < 0x20 -> {
                flushText()
                sink.onEvent(ParserEvent.Execute(byte))
            }

            else -> {
                appendText(byte)
            }
        }
    }

    private fun appendText(byte: Int) {
        if (textLength == text.size) {
            flushText()
        }
        text[textLength++] = byte.toByte()
    }

    private fun flushText() {
        if (textLength == 0) {
            return
        }
        sink.onEvent(ParserEvent.Print(text.copyOf(textLength)))
        textLength = 0
    }

    private fun beginEscape() {
        resetSequence()
        state = State.ESCAPE
    }

    private fun handleEscapeByte(byte: Int) {
        when {
            byte == ESC -> {
                beginEscape()
            }

            byte == CAN || byte == SUB -> {
                resetSequence()
                state = State.GROUND
            }

            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                sink.onEvent(ParserEvent.Execute(byte))
            }

            byte in 0x20..0x2F -> {
                collectIntermediate(byte)
                state = State.ESCAPE_INTERMEDIATE
            }

            byte in 0x30..0x7E -> {
                dispatchEscape(byte)
            }

            else -> {}
        }
    }

    private fun handleEscapeIntermediate(byte: Int) {
        when {
            byte == ESC -> {
                beginEscape()
            }

            byte == CAN || byte == SUB -> {
                resetSequence()
                state = State.GROUND
            }

            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                sink.onEvent(ParserEvent.Execute(byte))
            }

            byte in 0x20..0x2F -> {
                collectIntermediate(byte)
            }

            byte in 0x30..0x7E -> {
                dispatchEscape(byte)
            }

            else -> {}
        }
    }

    private fun dispatchEscape(finalByte: Int) {
        if (sequenceIgnored) {
            resetSequence()
            state = State.GROUND
            return
        }

        if (intermediateCount == 0) {
            when (finalByte) {
                '['.code -> {
                    startCsi()
                    return
                }

                ']'.code -> {
                    startOsc()
                    return
                }

                'P'.code -> {
                    startDcs()
                    return
                }

                'X'.code -> {
                    startSimpleString(StringKind.SOS)
                    return
                }

                '^'.code -> {
                    startSimpleString(StringKind.PM)
                    return
                }

                '_'.code -> {
                    startSimpleString(StringKind.APC)
                    return
                }

                '\\'.code -> {
                    sink.onEvent(ParserEvent.Execute(ST))
                    resetSequence()
                    state = State.GROUND
                    return
                }
            }
        }

        sink.onEvent(ParserEvent.Esc(intermediatesCopy(), finalByte))
        resetSequence()
        state = State.GROUND
    }

    private fun startCsi() {
        parameters.clear()
        intermediateCount = 0
        privateMarker = 0
        sequenceIgnored = false
        state = State.CSI_ENTRY
    }

    private fun handleCsiEntry(byte: Int) {
        when {
            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                sink.onEvent(ParserEvent.Execute(byte))
            }

            byte in '0'.code..'9'.code -> {
                state = State.CSI_PARAM
                if (!parameters.digit(byte - '0'.code)) {
                    state = State.CSI_IGNORE
                }
            }

            byte == ';'.code -> {
                state = State.CSI_PARAM
                if (!parameters.semicolon()) {
                    state = State.CSI_IGNORE
                }
            }

            byte in 0x3C..0x3F -> {
                if (privateMarker != 0) {
                    state = State.CSI_IGNORE
                } else {
                    privateMarker = byte
                }
            }

            byte == ':'.code -> {
                state = State.CSI_IGNORE
            }

            byte in 0x20..0x2F -> {
                state = State.CSI_INTERMEDIATE
                if (!collectIntermediate(byte)) {
                    state = State.CSI_IGNORE
                }
            }

            byte in 0x40..0x7E -> {
                dispatchCsi(byte)
            }

            else -> {
                state = State.CSI_IGNORE
            }
        }
    }

    private fun handleCsiParam(byte: Int) {
        when {
            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                sink.onEvent(ParserEvent.Execute(byte))
            }

            byte in '0'.code..'9'.code -> {
                if (!parameters.digit(byte - '0'.code)) {
                    state = State.CSI_IGNORE
                }
            }

            byte == ';'.code -> {
                if (!parameters.semicolon()) {
                    state = State.CSI_IGNORE
                }
            }

            byte == ':'.code -> {
                if (!parameters.colon()) {
                    state = State.CSI_IGNORE
                }
            }

            byte in 0x20..0x2F -> {
                state = State.CSI_INTERMEDIATE
                if (!collectIntermediate(byte)) {
                    state = State.CSI_IGNORE
                }
            }

            byte in 0x40..0x7E -> {
                dispatchCsi(byte)
            }

            else -> {
                state = State.CSI_IGNORE
            }
        }
    }

    private fun handleCsiIntermediate(byte: Int) {
        when {
            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                sink.onEvent(ParserEvent.Execute(byte))
            }

            byte in 0x20..0x2F -> {
                if (!collectIntermediate(byte)) {
                    state = State.CSI_IGNORE
                }
            }

            byte in 0x40..0x7E -> {
                dispatchCsi(byte)
            }

            else -> {
                state = State.CSI_IGNORE
            }
        }
    }

    private fun handleCsiIgnore(byte: Int) {
        when {
            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                sink.onEvent(ParserEvent.Execute(byte))
            }

            byte in 0x40..0x7E -> {
                resetSequence()
                state = State.GROUND
            }

            else -> {}
        }
    }

    private fun dispatchCsi(finalByte: Int) {
        parameters.finish()
        if (sequenceIgnored || parameters.overflow) {
            resetSequence()
            state = State.GROUND
            return
        }

        sink.onEvent(
            ParserEvent.Csi(
                privateMarker = privateMarker,
                parameters = parameters.valuesCopy(),
                parameterMore = parameters.moreCopy(),
                intermediates = intermediatesCopy(),
                finalByte = finalByte,
            ),
        )
        resetSequence()
        state = State.GROUND
    }

    private fun startDcs() {
        parameters.clear()
        intermediateCount = 0
        privateMarker = 0
        sequenceIgnored = false
        resetStringState()
        stringInitial = true
        state = State.DCS_ENTRY
    }

    private fun handleDcsEntry(byte: Int) {
        when {
            byte == NUL || byte == DEL || byte < 0x20 -> {}

            byte in '0'.code..'9'.code -> {
                state = State.DCS_PARAM
                if (!parameters.digit(byte - '0'.code)) {
                    state = State.DCS_IGNORE
                }
            }

            byte == ';'.code -> {
                state = State.DCS_PARAM
                if (!parameters.semicolon()) {
                    state = State.DCS_IGNORE
                }
            }

            byte in 0x3C..0x3F -> {
                if (privateMarker != 0) {
                    state = State.DCS_IGNORE
                } else {
                    privateMarker = byte
                }
            }

            byte == ':'.code -> {
                state = State.DCS_IGNORE
            }

            byte in 0x20..0x2F -> {
                state = State.DCS_INTERMEDIATE
                if (!collectIntermediate(byte)) {
                    state = State.DCS_IGNORE
                }
            }

            byte in 0x40..0x7E -> {
                hookDcs(byte)
            }

            else -> {
                state = State.DCS_IGNORE
            }
        }
    }

    private fun handleDcsParam(byte: Int) {
        when {
            byte == NUL || byte == DEL || byte < 0x20 -> {}

            byte in '0'.code..'9'.code -> {
                if (!parameters.digit(byte - '0'.code)) {
                    state = State.DCS_IGNORE
                }
            }

            byte == ';'.code -> {
                if (!parameters.semicolon()) {
                    state = State.DCS_IGNORE
                }
            }

            byte == ':'.code -> {
                if (!parameters.colon()) {
                    state = State.DCS_IGNORE
                }
            }

            byte in 0x20..0x2F -> {
                state = State.DCS_INTERMEDIATE
                if (!collectIntermediate(byte)) {
                    state = State.DCS_IGNORE
                }
            }

            byte in 0x40..0x7E -> {
                hookDcs(byte)
            }

            else -> {
                state = State.DCS_IGNORE
            }
        }
    }

    private fun handleDcsIntermediate(byte: Int) {
        when {
            byte == NUL || byte == DEL || byte < 0x20 -> {}

            byte in 0x20..0x2F -> {
                if (!collectIntermediate(byte)) {
                    state = State.DCS_IGNORE
                }
            }

            byte in 0x40..0x7E -> {
                hookDcs(byte)
            }

            else -> {
                state = State.DCS_IGNORE
            }
        }
    }

    private fun hookDcs(finalByte: Int) {
        parameters.finish()
        if (sequenceIgnored || parameters.overflow) {
            state = State.DCS_IGNORE
            resetSequence()
            return
        }

        dcsPrivateMarker = privateMarker
        dcsParameters = parameters.valuesCopy()
        dcsParameterMore = parameters.moreCopy()
        dcsIntermediates = intermediatesCopy()
        dcsFinalByte = finalByte
        resetSequence()
        stringLength = 0
        stringSeen = 0
        stringLimit = VtLimits.STRING_BYTES
        stringInitial = true
        stringStarted = false
        state = State.DCS_PASSTHROUGH
    }

    private fun handleDcsPassthrough(byte: Int) {
        when {
            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                flushDcs(final = false)
                sink.onEvent(ParserEvent.Execute(byte))
            }

            else -> {
                appendStringByte(byte)
            }
        }
    }

    private fun flushDcs(final: Boolean) {
        if (stringLength == 0 && !final && !stringStarted) {
            return
        }
        sink.onEvent(
            ParserEvent.Dcs(
                privateMarker = dcsPrivateMarker,
                parameters = dcsParameters.copyOf(),
                parameterMore = dcsParameterMore.copyOf(),
                intermediates = dcsIntermediates.copyOf(),
                finalByte = dcsFinalByte,
                bytes = stringBytes.copyOf(stringLength),
                initial = stringInitial,
                final = final,
            ),
        )
        stringInitial = false
        stringStarted = true
        stringLength = 0
        if (final) {
            state = State.GROUND
            resetStringState()
        }
    }

    private fun startOsc() {
        resetStringState()
        oscCommand = ParserEvent.NO_COMMAND
        oscCommandParsed = false
        oscCommandDigits = 0
        oscCommandValue = 0
        stringLimit = VtLimits.STRING_BYTES
        stringInitial = true
        state = State.OSC_STRING
    }

    private fun handleOsc(byte: Int) {
        when {
            byte == BEL -> {
                finishOscCommand()
                finishString()
            }

            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                flushOsc(final = false)
                sink.onEvent(ParserEvent.Execute(byte))
            }

            else -> {
                if (!oscCommandParsed) {
                    if (byte in '0'.code..'9'.code) {
                        appendOscCommandDigit(byte - '0'.code)
                        return
                    }
                    if (byte == ';'.code) {
                        oscCommand =
                            if (oscCommandDigits == 0) {
                                ParserEvent.NO_COMMAND
                            } else {
                                oscCommandValue
                            }
                        oscCommandParsed = true
                        return
                    }
                    oscCommand = ParserEvent.NO_COMMAND
                    oscCommandParsed = true
                }
                appendStringByte(byte)
            }
        }
    }

    private fun appendOscCommandDigit(digit: Int) {
        if (oscCommandDigits < VtLimits.PARAMETER_DIGITS) {
            oscCommandValue =
                if (oscCommandDigits == 0) {
                    digit
                } else if (oscCommandValue > (Int.MAX_VALUE - digit) / 10) {
                    Int.MAX_VALUE
                } else {
                    oscCommandValue * 10 + digit
                }
        }
        oscCommandDigits++
    }

    private fun finishOscCommand() {
        if (!oscCommandParsed) {
            oscCommand =
                if (oscCommandDigits == 0) {
                    ParserEvent.NO_COMMAND
                } else {
                    oscCommandValue
                }
            oscCommandParsed = true
        }
    }

    private fun flushOsc(final: Boolean) {
        if (!oscCommandParsed) {
            if (!final) {
                return
            }
            finishOscCommand()
        }
        if (stringLength == 0 && !final && !stringStarted && !stringInitial) {
            return
        }

        sink.onEvent(
            ParserEvent.Osc(
                command = oscCommand,
                bytes = stringBytes.copyOf(stringLength),
                initial = stringInitial,
                final = final,
            ),
        )
        stringInitial = false
        stringStarted = true
        stringLength = 0
        if (final) {
            state = State.GROUND
            resetStringState()
        }
    }

    private fun startSimpleString(kind: StringKind) {
        resetStringState()
        stringKind = kind
        stringLimit = VtLimits.STRING_BYTES
        stringInitial = true
        state = State.SOS_PM_APC_STRING
    }

    private fun handleSimpleString(byte: Int) {
        when {
            stringKind == StringKind.SOS -> {
                appendStringByte(byte)
            }

            byte == NUL || byte == DEL -> {}

            byte < 0x20 -> {
                flushSimpleString(final = false)
                sink.onEvent(ParserEvent.Execute(byte))
            }

            else -> {
                appendStringByte(byte)
            }
        }
    }

    private fun flushSimpleString(final: Boolean) {
        if (stringLength == 0 && !final && !stringStarted) {
            return
        }
        val payload = stringBytes.copyOf(stringLength)
        val event =
            when (stringKind) {
                StringKind.APC -> ParserEvent.Apc(payload, stringInitial, final)
                StringKind.PM -> ParserEvent.Pm(payload, stringInitial, final)
                StringKind.SOS -> ParserEvent.Sos(payload, stringInitial, final)
                StringKind.NONE -> return
            }
        sink.onEvent(event)
        stringInitial = false
        stringStarted = true
        stringLength = 0
        if (final) {
            state = State.GROUND
            resetStringState()
        }
    }

    private fun appendStringByte(byte: Int) {
        if (stringSeen < stringLimit) {
            stringBytes[stringLength++] = byte.toByte()
        }
        stringSeen++
    }

    private fun finishString() {
        when (state) {
            State.OSC_STRING -> {
                flushOsc(final = true)
            }

            State.DCS_PASSTHROUGH -> {
                flushDcs(final = true)
            }

            State.SOS_PM_APC_STRING -> {
                flushSimpleString(final = true)
            }

            State.DCS_IGNORE -> {
                state = State.GROUND
                resetStringState()
            }

            else -> {
                state = State.GROUND
                resetStringState()
            }
        }
    }

    private fun cancelString() {
        // A cancelled string must never be observable as a normally completed
        // string, regardless of where write() boundaries fell.
        state = State.GROUND
        resetStringState()
    }

    private fun resetStringState() {
        stringLength = 0
        stringSeen = 0
        stringInitial = false
        stringStarted = false
        stringKind = StringKind.NONE
        oscCommand = ParserEvent.NO_COMMAND
        oscCommandParsed = false
        oscCommandDigits = 0
        oscCommandValue = 0
        stringLimit = VtLimits.STRING_BYTES
    }

    private fun collectIntermediate(byte: Int): Boolean {
        if (intermediateCount >= intermediates.size) {
            sequenceIgnored = true
            return false
        }
        intermediates[intermediateCount++] = byte
        return true
    }

    private fun intermediatesCopy(): ByteArray {
        val copy = ByteArray(intermediateCount)
        for (index in 0 until intermediateCount) {
            copy[index] = intermediates[index].toByte()
        }
        return copy
    }

    private fun resetSequence() {
        parameters.clear()
        intermediateCount = 0
        privateMarker = 0
        sequenceIgnored = false
    }

    private fun isStringState(): Boolean =
        state == State.OSC_STRING ||
            state == State.DCS_PASSTHROUGH ||
            state == State.DCS_IGNORE ||
            state == State.SOS_PM_APC_STRING

    private enum class State {
        GROUND,
        ESCAPE,
        ESCAPE_INTERMEDIATE,
        CSI_ENTRY,
        CSI_PARAM,
        CSI_INTERMEDIATE,
        CSI_IGNORE,
        DCS_ENTRY,
        DCS_PARAM,
        DCS_INTERMEDIATE,
        DCS_PASSTHROUGH,
        DCS_IGNORE,
        OSC_STRING,
        SOS_PM_APC_STRING,
    }

    private enum class StringKind {
        NONE,
        APC,
        PM,
        SOS,
    }

    private class ParameterBuffer {
        private val values = IntArray(VtLimits.CSI_PARAMETERS)
        private val more = BooleanArray(VtLimits.CSI_PARAMETERS)
        private var current: Int = -1
        private var digits: Int = 0
        private var trailingSeparator: Boolean = false
        var overflow: Boolean = false
            private set

        fun clear() {
            current = -1
            digits = 0
            trailingSeparator = false
            overflow = false
            count = 0
        }

        var count: Int = 0
            private set

        fun digit(value: Int): Boolean {
            if (!ensureCurrent()) {
                return false
            }
            if (digits >= VtLimits.PARAMETER_DIGITS) {
                overflow = true
                return false
            }
            val existing = values[current]
            values[current] =
                if (existing == ParserEvent.MISSING_PARAMETER) {
                    value
                } else if (existing > (Int.MAX_VALUE - value) / 10) {
                    Int.MAX_VALUE
                } else {
                    existing * 10 + value
                }
            digits++
            trailingSeparator = false
            return true
        }

        fun semicolon(): Boolean {
            if (!ensureCurrent()) {
                return false
            }
            current = -1
            digits = 0
            trailingSeparator = true
            return true
        }

        fun colon(): Boolean {
            if (!ensureCurrent()) {
                return false
            }
            more[current] = true
            if (!newParameter()) {
                return false
            }
            trailingSeparator = false
            return true
        }

        fun finish() {
            if (trailingSeparator && current < 0) {
                ensureCurrent()
            }
            trailingSeparator = false
        }

        fun valuesCopy(): IntArray = values.copyOf(count)

        fun moreCopy(): BooleanArray = more.copyOf(count)

        private fun ensureCurrent(): Boolean {
            if (current >= 0) {
                return true
            }
            return newParameter()
        }

        private fun newParameter(): Boolean {
            if (count >= VtLimits.CSI_PARAMETERS) {
                overflow = true
                return false
            }
            values[count] = ParserEvent.MISSING_PARAMETER
            more[count] = false
            current = count
            digits = 0
            count++
            return true
        }
    }

    private companion object {
        private const val TEXT_CHUNK_BYTES = 4096

        private const val NUL = 0x00
        private const val BEL = 0x07
        private const val CAN = 0x18
        private const val SUB = 0x1A
        private const val ESC = 0x1B
        private const val DEL = 0x7F
        private const val ST = 0x9C
        private const val ESC_ST = '\\'.code

        private val EMPTY_INTS = IntArray(0)
        private val EMPTY_BOOLEANS = BooleanArray(0)
        private val EMPTY_BYTES = ByteArray(0)
    }
}
