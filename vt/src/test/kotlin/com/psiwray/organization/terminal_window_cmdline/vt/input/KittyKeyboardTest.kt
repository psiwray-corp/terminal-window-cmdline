package com.psiwray.organization.terminal_window_cmdline.vt.input

import kotlin.test.Test
import kotlin.test.assertEquals

class KittyKeyboardTest {
    @Test
    fun disabledKittyKeepsLegacyEncoding() {
        val harness = InputHarness()

        assertEquals("\u0001", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL)))
        assertEquals("\u001B[A", harness.encode(KeyEvent(Key.UP)))
    }

    @Test
    fun disambiguateEncodesControlChords() {
        val harness = InputHarness()
        harness.feed("\u001B[>1u")

        assertEquals("\u001B[97;5u", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL)))
        assertEquals("", harness.encode(CharEvent('a'.code)))
        assertEquals("a", harness.encode(CharEvent('a'.code)))
    }

    @Test
    fun queryPushPopAndSetRoundTrip() {
        val harness = InputHarness()
        harness.feed("\u001B[>1u\u001B[?u")

        assertEquals("\u001B[?1u", harness.responses())

        harness.feed("\u001B[>3u\u001B[?u")
        assertEquals("\u001B[?3u", harness.responses())

        harness.feed("\u001B[<u\u001B[?u")
        assertEquals("\u001B[?1u", harness.responses())

        harness.feed("\u001B[<9u\u001B[?u")
        assertEquals("\u001B[?0u", harness.responses())

        harness.feed("\u001B[=1;2u\u001B[?u")
        assertEquals("\u001B[?1u", harness.responses())
    }

    @Test
    fun eventTypesRoundTrip() {
        val harness = InputHarness()
        harness.feed("\u001B[>3u")

        assertEquals("\u001B[97;5:1u", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL)))
        assertEquals("\u001B[97;5:2u", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL, Action.REPEAT)))
        assertEquals("\u001B[97;5:3u", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL, Action.RELEASE)))
    }

    @Test
    fun reportAllKeysEncodesTextAndFunctionalKeys() {
        val harness = InputHarness()
        harness.feed("\u001B[>8u")

        assertEquals("\u001B[97u", harness.encode(KeyEvent(Key.A)))
        assertEquals("", harness.encode(CharEvent('a'.code)))
        assertEquals("\u001B[13u", harness.encode(KeyEvent(Key.ENTER)))
        assertEquals("\u001B[9u", harness.encode(KeyEvent(Key.TAB)))
        assertEquals("\u001B[127u", harness.encode(KeyEvent(Key.BACKSPACE)))
        assertEquals("\u001B[1;2A", harness.encode(KeyEvent(Key.UP, Modifiers.SHIFT)))
    }

    @Test
    fun disambiguateLeavesEnterTabAndBackspaceLegacy() {
        val harness = InputHarness()
        harness.feed("\u001B[>1u")

        assertEquals("\r", harness.encode(KeyEvent(Key.ENTER)))
        assertEquals("\t", harness.encode(KeyEvent(Key.TAB)))
        assertEquals("\u007F", harness.encode(KeyEvent(Key.BACKSPACE)))
        assertEquals("\u001B[Z", harness.encode(KeyEvent(Key.TAB, Modifiers.SHIFT)))
    }

    @Test
    fun escapeIsDisambiguated() {
        val harness = InputHarness()
        harness.feed("\u001B[>1u")

        assertEquals("\u001B[27u", harness.encode(KeyEvent(Key.ESCAPE)))
    }

    @Test
    fun stacksAreIndependentForMainAndAlternateScreens() {
        val harness = InputHarness()
        harness.feed("\u001B[>1u\u001B[?1049h\u001B[>8u\u001B[?u")
        assertEquals("\u001B[?8u", harness.responses())

        harness.feed("\u001B[?1049l\u001B[?u")
        assertEquals("\u001B[?1u", harness.responses())
    }

    @Test
    fun unsupportedAlternateKeyAndTextFlagsAreMasked() {
        val harness = InputHarness()
        harness.feed("\u001B[>20u\u001B[?u")

        assertEquals("\u001B[?0u", harness.responses())
    }
}
