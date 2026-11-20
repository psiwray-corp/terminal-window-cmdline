package com.psiwray.organization.terminal_window_cmdline.vt.input

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MouseAndPasteTest {
    @Test
    fun mouseIsSilentWhenTrackingIsOff() {
        val harness = InputHarness()

        assertEquals("", harness.encode(MouseEvent(0, 0, MouseButton.LEFT)))
        assertEquals("", harness.encode(ScrollEvent(0, 0, 1)))
    }

    @Test
    fun sgrPressReleaseMotionAndWheel() {
        val harness = InputHarness()
        harness.feed("\u001B[?1000h\u001B[?1006h")

        assertEquals("\u001B[<0;1;1M", harness.encode(MouseEvent(0, 0, MouseButton.LEFT)))
        assertEquals("\u001B[<0;1;1m", harness.encode(MouseEvent(0, 0, MouseButton.LEFT, Action.RELEASE)))
        assertEquals("\u001B[<64;2;3M", harness.encode(ScrollEvent(1, 2, 1)))
        assertEquals("\u001B[<65;2;3M", harness.encode(ScrollEvent(1, 2, -1)))
        assertEquals("\u001B[<4;1;1M", harness.encode(MouseEvent(0, 0, MouseButton.LEFT, modifiers = Modifiers.SHIFT)))
    }

    @Test
    fun x10EncodingIsOneBasedAndUsesThreeByteReports() {
        val harness = InputHarness()
        harness.feed("\u001B[?1000h")

        assertContentEquals(
            byteArrayOf(0x1B, 0x5B, 0x4D, 32, 33, 33),
            harness.encoder.encode(MouseEvent(0, 0, MouseButton.LEFT)),
        )
        assertContentEquals(
            byteArrayOf(0x1B, 0x5B, 0x4D, 35, 33, 33),
            harness.encoder.encode(MouseEvent(0, 0, MouseButton.LEFT, Action.RELEASE)),
        )
    }

    @Test
    fun buttonMotionAndAnyMotionFollowTheirModes() {
        val drag = InputHarness()
        drag.feed("\u001B[?1002h\u001B[?1006h")

        assertEquals("", drag.encode(MouseEvent(1, 2, null, Action.MOVE)))
        assertEquals("\u001B[<32;2;3M", drag.encode(MouseEvent(1, 2, MouseButton.LEFT, Action.MOVE)))

        val any = InputHarness()
        any.feed("\u001B[?1003h\u001B[?1006h")

        assertEquals("\u001B[<35;2;3M", any.encode(MouseEvent(1, 2, null, Action.MOVE)))
        assertEquals("\u001B[<32;2;3M", any.encode(MouseEvent(1, 2, MouseButton.LEFT, Action.MOVE)))
    }

    @Test
    fun x10AndButtonModesDoNotReportMotion() {
        val x10 = InputHarness()
        x10.feed("\u001B[?9h\u001B[?1006h")

        assertEquals("", x10.encode(MouseEvent(1, 2, MouseButton.LEFT, Action.RELEASE)))
        assertEquals("\u001B[<0;2;3M", x10.encode(MouseEvent(1, 2, MouseButton.LEFT)))

        val button = InputHarness()
        button.feed("\u001B[?1000h\u001B[?1006h")

        assertEquals("", button.encode(MouseEvent(1, 2, MouseButton.LEFT, Action.MOVE)))
    }

    @Test
    fun wheelDeltaProducesOneReportPerNotch() {
        val harness = InputHarness()
        harness.feed("\u001B[?1000h\u001B[?1006h")

        assertEquals("\u001B[<64;1;1M\u001B[<64;1;1M", harness.encode(ScrollEvent(0, 0, 2)))
    }

    @Test
    fun focusReportingObeysMode() {
        val harness = InputHarness()

        assertEquals("", harness.encode(FocusEvent(true)))

        harness.feed("\u001B[?1004h")

        assertEquals("\u001B[I", harness.encode(FocusEvent(true)))
        assertEquals("\u001B[O", harness.encode(FocusEvent(false)))
    }

    @Test
    fun bracketedPasteObeysMode() {
        val harness = InputHarness()

        assertEquals("plain", harness.encode(PasteEvent("plain")))

        harness.feed("\u001B[?2004h")

        assertEquals("\u001B[200~plain\u001B[201~", harness.encode(PasteEvent("plain")))
    }
}
