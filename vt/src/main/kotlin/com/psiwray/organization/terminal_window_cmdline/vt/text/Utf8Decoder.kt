package com.psiwray.organization.terminal_window_cmdline.vt.text

/** Receives decoded Unicode code points without boxing. */
public fun interface CodePointSink {
    /** Handles one decoded code point. */
    public fun onCodePoint(codePoint: Int)
}

/**
 * Incremental UTF-8 decoder.
 *
 * Invalid and overlong sequences become U+FFFD. An incomplete sequence at the
 * end of a [decode] call is retained for the next call; call [finish] at end of
 * stream to replace a retained partial sequence with U+FFFD.
 */
public class Utf8Decoder {
    private var codePoint: Int = 0
    private var remaining: Int = 0
    private var seen: Int = 0
    private var secondMin: Int = 0x80
    private var secondMax: Int = 0xBF
    private var minimumCodePoint: Int = 0x80

    /**
     * Decodes [length] bytes starting at [offset].
     *
     * @throws IllegalArgumentException when the range is outside [bytes].
     */
    public fun decode(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        sink: CodePointSink,
    ) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
            "offset=$offset length=$length size=${bytes.size}"
        }

        var index = offset
        val end = offset + length
        while (index < end) {
            val byte = bytes[index].toInt() and 0xFF

            if (remaining == 0) {
                if (byte < 0x80) {
                    sink.onCodePoint(byte)
                    index++
                    continue
                }
                if (!startSequence(byte)) {
                    sink.onCodePoint(REPLACEMENT)
                }
                index++
                continue
            }

            if (!validContinuation(byte)) {
                sink.onCodePoint(REPLACEMENT)
                reset()
                continue
            }

            codePoint = (codePoint shl 6) or (byte and 0x3F)
            remaining--
            seen++
            index++
            if (remaining == 0) {
                if (codePoint < minimumCodePoint || codePoint > MAX_CODE_POINT || isSurrogate(codePoint)) {
                    sink.onCodePoint(REPLACEMENT)
                } else {
                    sink.onCodePoint(codePoint)
                }
                reset()
            }
        }
    }

    /** Decodes a complete byte array. */
    public fun decode(
        bytes: ByteArray,
        sink: CodePointSink,
    ) {
        decode(bytes, 0, bytes.size, sink)
    }

    /** Emits U+FFFD for any retained incomplete sequence and resets. */
    public fun finish(sink: CodePointSink) {
        if (remaining != 0) {
            sink.onCodePoint(REPLACEMENT)
        }
        reset()
    }

    /** Discards any retained incomplete sequence without emitting. */
    public fun reset() {
        codePoint = 0
        remaining = 0
        seen = 0
        secondMin = 0x80
        secondMax = 0xBF
        minimumCodePoint = 0x80
    }

    private fun startSequence(byte: Int): Boolean {
        when {
            byte in 0xC2..0xDF -> {
                codePoint = byte and 0x1F
                remaining = 1
                seen = 0
                secondMin = 0x80
                secondMax = 0xBF
                minimumCodePoint = 0x80
            }

            byte == 0xE0 -> {
                codePoint = byte and 0x0F
                remaining = 2
                seen = 0
                secondMin = 0xA0
                secondMax = 0xBF
                minimumCodePoint = 0x800
            }

            byte == 0xED -> {
                codePoint = byte and 0x0F
                remaining = 2
                seen = 0
                secondMin = 0x80
                secondMax = 0x9F
                minimumCodePoint = 0x800
            }

            byte in 0xE1..0xEC || byte in 0xEE..0xEF -> {
                codePoint = byte and 0x0F
                remaining = 2
                seen = 0
                secondMin = 0x80
                secondMax = 0xBF
                minimumCodePoint = 0x800
            }

            byte == 0xF0 -> {
                codePoint = byte and 0x07
                remaining = 3
                seen = 0
                secondMin = 0x90
                secondMax = 0xBF
                minimumCodePoint = 0x10000
            }

            byte in 0xF1..0xF3 -> {
                codePoint = byte and 0x07
                remaining = 3
                seen = 0
                secondMin = 0x80
                secondMax = 0xBF
                minimumCodePoint = 0x10000
            }

            byte == 0xF4 -> {
                codePoint = byte and 0x07
                remaining = 3
                seen = 0
                secondMin = 0x80
                secondMax = 0x8F
                minimumCodePoint = 0x10000
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun validContinuation(byte: Int): Boolean {
        if (byte !in 0x80..0xBF) {
            return false
        }
        if (seen == 0 && (byte < secondMin || byte > secondMax)) {
            return false
        }
        return true
    }

    private fun isSurrogate(value: Int): Boolean = value in 0xD800..0xDFFF

    private companion object {
        private const val REPLACEMENT = 0xFFFD
        private const val MAX_CODE_POINT = 0x10FFFF
    }
}
