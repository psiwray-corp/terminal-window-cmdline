package com.psiwray.organization.terminal_window_cmdline.vt.input

import kotlin.test.Test
import kotlin.test.assertEquals

class ModifyOtherKeysTest {
    @Test
    fun levelOneEncodesAltButLeavesControlAndShiftLegacy() {
        val harness = InputHarness()
        harness.feed("\u001B[>4;1m")

        assertEquals("\u001B[27;3;97~", harness.encode(KeyEvent(Key.A, Modifiers.ALT)))
        assertEquals("\u0001", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL)))
        assertEquals("\u001B[Z", harness.encode(KeyEvent(Key.TAB, Modifiers.SHIFT)))
    }

    @Test
    fun levelTwoEncodesAllModifiedOrdinaryKeys() {
        val harness = InputHarness()
        harness.feed("\u001B[>4;2m")

        assertEquals("\u001B[27;3;97~", harness.encode(KeyEvent(Key.A, Modifiers.ALT)))
        assertEquals("\u001B[27;5;97~", harness.encode(KeyEvent(Key.A, Modifiers.CONTROL)))
        assertEquals("\u001B[27;2;65~", harness.encode(KeyEvent(Key.A, Modifiers.SHIFT)))
        assertEquals("\u001B[27;6;65~", harness.encode(KeyEvent(Key.A, Modifiers.SHIFT or Modifiers.CONTROL)))
        assertEquals("\u001B[27;2;9~", harness.encode(KeyEvent(Key.TAB, Modifiers.SHIFT)))
    }

    @Test
    fun queryAndResetRoundTrip() {
        val harness = InputHarness()
        harness.feed("\u001B[>4;2m\u001B[?4m")

        assertEquals("\u001B[>4;2m", harness.responses())

        harness.feed("\u001B[>4m\u001B[?4m")
        assertEquals("\u001B[>4;0m", harness.responses())
        assertEquals("\u001Ba", harness.encode(KeyEvent(Key.A, Modifiers.ALT)))
    }

    @Test
    fun levelThreeIsNotClaimed() {
        val harness = InputHarness()
        harness.feed("\u001B[>4;3m")

        assertEquals("\u001Ba", harness.encode(KeyEvent(Key.A, Modifiers.ALT)))
    }

    @Test
    fun colonSubparameterDoesNotChangeTheLevel() {
        val harness = InputHarness()
        harness.feed("\u001B[>4:1m\u001B[?4m")

        assertEquals("\u001B[>4;0m", harness.responses())
    }
}
