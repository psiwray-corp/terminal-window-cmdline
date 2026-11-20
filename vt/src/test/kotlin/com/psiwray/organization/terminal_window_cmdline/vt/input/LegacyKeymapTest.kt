package com.psiwray.organization.terminal_window_cmdline.vt.input

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyKeymapTest {
    @Test
    fun plainTextComesFromCharEvent() {
        val harness = InputHarness()

        assertEquals("", harness.encode(KeyEvent(Key.A)))
        assertEquals("a", harness.encode(CharEvent('a'.code)))
    }

    @Test
    fun controlAndAltChordsSuppressTheirCharacter() {
        val harness = InputHarness()

        assertEquals("\u0001", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL)))
        assertEquals("", harness.encode(CharEvent('a'.code)))
        assertEquals("b", harness.encode(CharEvent('b'.code)))
    }

    @Test
    fun altAndShiftAltUseEscapeAndTheShiftedKey() {
        val harness = InputHarness()

        assertEquals("\u001Ba", harness.encode(KeyEvent(Key.A, Modifiers.ALT)))
        assertEquals("", harness.encode(CharEvent('a'.code)))
        assertEquals("\u001BA", harness.encode(KeyEvent(Key.A, Modifiers.ALT or Modifiers.SHIFT)))
    }

    @Test
    fun controlAltAndControlShiftUseTheControlMapping() {
        val harness = InputHarness()

        assertEquals("\u0001", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL or Modifiers.SHIFT)))
        assertEquals("\u001B\u0001", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL or Modifiers.ALT)))
        assertEquals("\u007F", harness.encode(KeyEvent(Key.SLASH, Modifiers.CONTROL or Modifiers.SHIFT)))
        assertEquals("\u0000", harness.encode(KeyEvent(Key.SPACE, Modifiers.CONTROL)))
    }

    @Test
    fun everyControlLetterMapsToItsC0Code() {
        for (index in 0..25) {
            val key = Key.entries.first { it.name == ('A' + index).toString() }
            val harness = InputHarness()
            assertEquals(
                (index + 1).toByte(),
                harness.encoder.encode(KeyEvent(key, Modifiers.CONTROL)).single(),
                "Ctrl+${('A' + index)}",
            )
        }
    }

    @Test
    fun tabAndShiftTab() {
        val harness = InputHarness()

        assertEquals("\t", harness.encode(KeyEvent(Key.TAB)))
        assertEquals("\u001B[Z", harness.encode(KeyEvent(Key.TAB, Modifiers.SHIFT)))
        assertEquals("\u001B\t", harness.encode(KeyEvent(Key.TAB, Modifiers.ALT)))
    }

    @Test
    fun arrowsHomeAndEndFollowCursorKeyMode() {
        val harness = InputHarness()

        assertEquals("\u001B[A", harness.encode(KeyEvent(Key.UP)))
        assertEquals("\u001B[1;3A", harness.encode(KeyEvent(Key.UP, Modifiers.ALT)))
        assertEquals("\u001B[H", harness.encode(KeyEvent(Key.HOME)))
        assertEquals("\u001B[F", harness.encode(KeyEvent(Key.END)))

        harness.feed("\u001B[?1h")

        assertEquals("\u001BOA", harness.encode(KeyEvent(Key.UP)))
        assertEquals("\u001BOH", harness.encode(KeyEvent(Key.HOME)))
        assertEquals("\u001B[1;2A", harness.encode(KeyEvent(Key.UP, Modifiers.SHIFT)))
    }

    @Test
    fun functionKeysUseTerminfoCompatibleSequences() {
        val harness = InputHarness()

        assertEquals("\u001BOP", harness.encode(KeyEvent(Key.F1)))
        assertEquals("\u001BOQ", harness.encode(KeyEvent(Key.F2)))
        assertEquals("\u001BOR", harness.encode(KeyEvent(Key.F3)))
        assertEquals("\u001BOS", harness.encode(KeyEvent(Key.F4)))
        assertEquals("\u001B[15~", harness.encode(KeyEvent(Key.F5)))
        assertEquals("\u001B[17~", harness.encode(KeyEvent(Key.F6)))
        assertEquals("\u001B[18~", harness.encode(KeyEvent(Key.F7)))
        assertEquals("\u001B[19~", harness.encode(KeyEvent(Key.F8)))
        assertEquals("\u001B[20~", harness.encode(KeyEvent(Key.F9)))
        assertEquals("\u001B[21~", harness.encode(KeyEvent(Key.F10)))
        assertEquals("\u001B[23~", harness.encode(KeyEvent(Key.F11)))
        assertEquals("\u001B[24~", harness.encode(KeyEvent(Key.F12)))

        assertEquals("\u001B[1;3P", harness.encode(KeyEvent(Key.F1, Modifiers.ALT)))
        assertEquals("\u001B[15;5~", harness.encode(KeyEvent(Key.F5, Modifiers.CONTROL)))
    }

    @Test
    fun editingKeysUseTheirLegacySequences() {
        val harness = InputHarness()

        assertEquals("\u001B[2~", harness.encode(KeyEvent(Key.INSERT)))
        assertEquals("\u001B[3~", harness.encode(KeyEvent(Key.DELETE)))
        assertEquals("\u001B[5~", harness.encode(KeyEvent(Key.PAGE_UP)))
        assertEquals("\u001B[6~", harness.encode(KeyEvent(Key.PAGE_DOWN)))
        assertEquals("\u001B[2;3~", harness.encode(KeyEvent(Key.INSERT, Modifiers.ALT)))
        assertEquals("\u001B[6;5~", harness.encode(KeyEvent(Key.PAGE_DOWN, Modifiers.CONTROL)))
    }

    @Test
    fun keypadFollowsNumericAndApplicationModes() {
        val harness = InputHarness()

        assertEquals("0", harness.encode(KeyEvent(Key.KP_0)))
        assertEquals("9", harness.encode(KeyEvent(Key.KP_9)))
        assertEquals(".", harness.encode(KeyEvent(Key.KP_DECIMAL)))
        assertEquals("\r", harness.encode(KeyEvent(Key.KP_ENTER)))

        harness.feed("\u001B[?66h")

        assertEquals("\u001BOp", harness.encode(KeyEvent(Key.KP_0)))
        assertEquals("\u001BOy", harness.encode(KeyEvent(Key.KP_9)))
        assertEquals("\u001B[3~", harness.encode(KeyEvent(Key.KP_DECIMAL)))
        assertEquals("\u001BOM", harness.encode(KeyEvent(Key.KP_ENTER)))
        assertEquals("\u001BOo", harness.encode(KeyEvent(Key.KP_DIVIDE)))
        assertEquals("\u001BOj", harness.encode(KeyEvent(Key.KP_MULTIPLY)))
        assertEquals("\u001BOm", harness.encode(KeyEvent(Key.KP_SUBTRACT)))
        assertEquals("\u001BOk", harness.encode(KeyEvent(Key.KP_ADD)))
        assertEquals("\u001BOX", harness.encode(KeyEvent(Key.KP_EQUAL)))
    }

    @Test
    fun backspaceFollowsDecbkm() {
        val harness = InputHarness()

        assertEquals("\u007F", harness.encode(KeyEvent(Key.BACKSPACE)))
        assertEquals("\u001B\u007F", harness.encode(KeyEvent(Key.BACKSPACE, Modifiers.ALT)))

        harness.feed("\u001B[?67h")

        assertEquals("\u0008", harness.encode(KeyEvent(Key.BACKSPACE)))
        assertEquals("\u001B\u0008", harness.encode(KeyEvent(Key.BACKSPACE, Modifiers.ALT)))
    }

    @Test
    fun keypadCharEventIsSuppressed() {
        val harness = InputHarness()

        assertEquals("1", harness.encode(KeyEvent(Key.KP_1)))
        assertEquals("", harness.encode(CharEvent('1'.code)))
    }

    @Test
    fun staleSuppressionDoesNotEatTheNextTextKey() {
        val harness = InputHarness()

        assertEquals("\u001B[A", harness.encode(KeyEvent(Key.UP)))
        assertEquals("", harness.encode(KeyEvent(Key.A)))
        assertEquals("a", harness.encode(CharEvent('a'.code)))
    }

    @Test
    fun legacyReleaseIsIgnoredAndRepeatIsSent() {
        val harness = InputHarness()

        assertEquals("\u0001", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL, Action.REPEAT)))
        assertEquals("", harness.encode(CharEvent('a'.code)))
        assertEquals("", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL, Action.RELEASE)))
    }

    @Test
    fun sinkReceivesTheSameBytes() {
        val writes = mutableListOf<String>()
        val encoder = InputEncoder(InputSink { writes += String(it, Charsets.UTF_8) })

        assertEquals("a", String(encoder.encode(CharEvent('a'.code)), Charsets.UTF_8))
        assertEquals(listOf("a"), writes)
    }

    @Test
    fun everySpecialKeyAndModifierCombinationIsTableDriven() {
        val masks =
            listOf(
                Modifiers.NONE,
                Modifiers.SHIFT,
                Modifiers.CONTROL,
                Modifiers.ALT,
                Modifiers.SHIFT or Modifiers.CONTROL,
                Modifiers.SHIFT or Modifiers.ALT,
                Modifiers.CONTROL or Modifiers.ALT,
                Modifiers.SHIFT or Modifiers.CONTROL or Modifiers.ALT,
            )
        val navigation =
            mapOf(
                Key.UP to "A",
                Key.DOWN to "B",
                Key.RIGHT to "C",
                Key.LEFT to "D",
                Key.HOME to "H",
                Key.END to "F",
            )
        val letters = mapOf(Key.F1 to "P", Key.F2 to "Q", Key.F3 to "R", Key.F4 to "S")
        val tildes =
            mapOf(
                Key.INSERT to 2,
                Key.DELETE to 3,
                Key.PAGE_UP to 5,
                Key.PAGE_DOWN to 6,
                Key.F5 to 15,
                Key.F6 to 17,
                Key.F7 to 18,
                Key.F8 to 19,
                Key.F9 to 20,
                Key.F10 to 21,
                Key.F11 to 23,
                Key.F12 to 24,
            )

        for ((key, finalByte) in navigation) {
            for (mask in masks) {
                val harness = InputHarness()
                val expected =
                    if (mask.isEmpty()) {
                        "\u001B[$finalByte"
                    } else {
                        "\u001B[1;${mask.xtermBits() + 1}$finalByte"
                    }
                assertEquals(expected, harness.encode(KeyEvent(key, mask)), "$key $mask")
            }
        }
        for ((key, finalByte) in letters) {
            for (mask in masks) {
                val harness = InputHarness()
                val expected =
                    if (mask.isEmpty()) {
                        "\u001BO$finalByte"
                    } else {
                        "\u001B[1;${mask.xtermBits() + 1}$finalByte"
                    }
                assertEquals(expected, harness.encode(KeyEvent(key, mask)), "$key $mask")
            }
        }
        for ((key, code) in tildes) {
            for (mask in masks) {
                val harness = InputHarness()
                val expected =
                    if (mask.isEmpty()) {
                        "\u001B[$code~"
                    } else {
                        "\u001B[$code;${mask.xtermBits() + 1}~"
                    }
                assertEquals(expected, harness.encode(KeyEvent(key, mask)), "$key $mask")
            }
        }
    }

    @Test
    fun everyPrintableKeyAndChordModifierIsHandled() {
        val chordMasks =
            listOf(
                Modifiers.CONTROL,
                Modifiers.ALT,
                Modifiers.SUPER,
                Modifiers.SHIFT or Modifiers.CONTROL,
                Modifiers.SHIFT or Modifiers.ALT,
                Modifiers.CONTROL or Modifiers.ALT,
                Modifiers.CONTROL or Modifiers.ALT or Modifiers.SUPER,
            )
        for (key in Key.entries) {
            if (!key.isPrintable) {
                continue
            }
            for (mask in chordMasks) {
                val harness = InputHarness()
                val bytes = harness.encoder.encode(KeyEvent(key, mask))
                assertEquals(true, bytes.isNotEmpty(), "$key $mask")
                assertEquals("", harness.encode(CharEvent('x'.code)), "$key $mask")
            }
        }
    }

    @Test
    fun everyPrintableKeyUsesCharEvent() {
        for (key in Key.entries) {
            if (!key.isPrintable) {
                continue
            }
            val harness = InputHarness()
            assertEquals("", harness.encode(KeyEvent(key)), "key=$key")
            val codePoint = key.unshiftedCodePoint()!!
            assertEquals(String(Character.toChars(codePoint)), harness.encode(CharEvent(codePoint)), "key=$key")
        }
    }
}
