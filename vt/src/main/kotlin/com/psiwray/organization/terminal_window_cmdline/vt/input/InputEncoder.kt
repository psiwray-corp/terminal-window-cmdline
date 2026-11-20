package com.psiwray.organization.terminal_window_cmdline.vt.input

import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState

/** Receives encoded input bytes. */
public fun interface InputSink {
    /** Writes one complete encoded event. */
    public fun write(bytes: ByteArray)
}

/**
 * Encodes neutral input events using the modes held by [state].
 *
 * The encoder is stateful only for the `KeyEvent`/`CharEvent` pairing rule:
 * a key event that produced bytes suppresses the next character event.
 *
 * @property state terminal state supplying modes and per-screen kitty flags.
 */
public class InputEncoder(
    public val state: TerminalState = TerminalState(),
    private val sink: InputSink? = null,
) {
    private var suppressNextChar: Boolean = false

    /** Convenience constructor for a sink with a fresh default state. */
    public constructor(sink: InputSink) : this(TerminalState(), sink)

    /** Encodes [event], writes it to the sink, and returns the bytes. */
    public fun encode(event: InputEvent): ByteArray {
        val bytes =
            when (event) {
                is KeyEvent -> encodeKey(event)
                is CharEvent -> encodeChar(event)
                is MouseEvent -> encodeMouse(event)
                is ScrollEvent -> encodeScroll(event)
                is FocusEvent -> encodeFocus(event)
                is PasteEvent -> encodePaste(event)
            }
        if (bytes.isNotEmpty()) {
            sink?.write(bytes)
        }
        return bytes
    }

    /** Clears the pending character-suppression state. */
    public fun reset() {
        suppressNextChar = false
    }

    private fun encodeKey(event: KeyEvent): ByteArray {
        if (event.action != Action.RELEASE && suppressNextChar) {
            suppressNextChar = false
        }
        val bytes =
            state.activeKitty.encode(event, state.cursorKeysApplication)
                ?: ModifyOtherKeys.encode(event, state.modifyOtherKeysLevel)
                ?: LegacyKeymap.encode(
                    event,
                    cursorKeysApplication = state.cursorKeysApplication,
                    keypadApplication = state.keypadApplication,
                    backspaceSendsBackspace = state.backspaceSendsBackspace,
                )
                ?: return EMPTY
        if (event.action != Action.RELEASE && bytes.isNotEmpty()) {
            suppressNextChar = true
        }
        return bytes
    }

    private fun encodeChar(event: CharEvent): ByteArray {
        if (suppressNextChar) {
            suppressNextChar = false
            return EMPTY
        }
        return String(Character.toChars(event.codePoint)).toByteArray(Charsets.UTF_8)
    }

    private fun encodeMouse(event: MouseEvent): ByteArray {
        val mode = state.mouseTracking
        if (mode == 0) {
            return EMPTY
        }
        if (event.action == Action.MOVE) {
            if (mode == 9 || mode == 1000) {
                return EMPTY
            }
            if (mode == 1002 && event.button == null) {
                return EMPTY
            }
        }
        if (event.action == Action.RELEASE && mode == 9) {
            return EMPTY
        }
        if (event.button?.isWheel == true && event.action != Action.PRESS) {
            return EMPTY
        }
        if (event.column < 0 || event.row < 0) {
            return EMPTY
        }
        val motion = event.action == Action.MOVE
        val base = event.button?.xtermCode ?: 3
        val code = (if (motion) base + 32 else base) + event.modifiers.mouseBits()
        return mouseReport(code, event.column + 1, event.row + 1, event.action == Action.RELEASE)
    }

    private fun encodeScroll(event: ScrollEvent): ByteArray {
        if (state.mouseTracking == 0 || event.delta == 0 || event.column < 0 || event.row < 0) {
            return EMPTY
        }
        val button = if (event.delta > 0) MouseButton.WHEEL_UP else MouseButton.WHEEL_DOWN
        val code = button.xtermCode + event.modifiers.mouseBits()
        val count = kotlin.math.abs(event.delta)
        val builder = ByteArrayBuilder()
        repeat(count) {
            builder.append(mouseReport(code, event.column + 1, event.row + 1, release = false))
        }
        return builder.toByteArray()
    }

    private fun mouseReport(
        code: Int,
        column: Int,
        row: Int,
        release: Boolean,
    ): ByteArray {
        if (state.mouseSgr) {
            val finalByte = if (release) 'm' else 'M'
            return "\u001B[<$code;$column;$row$finalByte".toByteArray(Charsets.US_ASCII)
        }
        val x = (column + 32).coerceIn(0, 255)
        val y = (row + 32).coerceIn(0, 255)
        val button = if (release) 3 + (code and 0x1C) else code
        return byteArrayOf(0x1B, 0x5B, 0x4D, (32 + button).toByte(), x.toByte(), y.toByte())
    }

    private fun encodeFocus(event: FocusEvent): ByteArray {
        if (!state.focusReporting) {
            return EMPTY
        }
        return if (event.focused) {
            "\u001B[I".toByteArray(Charsets.US_ASCII)
        } else {
            "\u001B[O".toByteArray(Charsets.US_ASCII)
        }
    }

    private fun encodePaste(event: PasteEvent): ByteArray {
        val text = event.text.toByteArray(Charsets.UTF_8)
        if (!state.bracketedPaste) {
            return text
        }
        val builder = ByteArrayBuilder()
        builder.append("\u001B[200~".toByteArray(Charsets.US_ASCII))
        builder.append(text)
        builder.append("\u001B[201~".toByteArray(Charsets.US_ASCII))
        return builder.toByteArray()
    }

    private class ByteArrayBuilder {
        private var bytes: ByteArray = ByteArray(32)
        private var size: Int = 0

        fun append(source: ByteArray) {
            ensure(size + source.size)
            source.copyInto(bytes, size)
            size += source.size
        }

        fun toByteArray(): ByteArray = bytes.copyOf(size)

        private fun ensure(capacity: Int) {
            if (capacity <= bytes.size) {
                return
            }
            var next = bytes.size
            while (next < capacity) {
                next *= 2
            }
            bytes = bytes.copyOf(next)
        }
    }

    private companion object {
        private val EMPTY: ByteArray = ByteArray(0)
    }
}
