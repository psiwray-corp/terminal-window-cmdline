package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.attr.Attributes
import com.psiwray.organization.terminal_window_cmdline.vt.attr.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SgrTest {
    @Test
    fun ansiAndBrightColors() {
        val harness = StateHarness()

        harness.feed("\u001B[31;104m")

        assertEquals(Color.Indexed(1), harness.state.foreground)
        assertEquals(Color.Indexed(12), harness.state.background)
    }

    @Test
    fun everyAnsiAndBrightColorCodeMapsToItsIndex() {
        for (code in 30..37) {
            val harness = StateHarness()
            harness.feed("\u001B[${code}m")
            assertEquals(Color.Indexed(code - 30), harness.state.foreground, "foreground $code")
        }
        for (code in 90..97) {
            val harness = StateHarness()
            harness.feed("\u001B[${code}m")
            assertEquals(Color.Indexed(code - 90 + 8), harness.state.foreground, "foreground $code")
        }
        for (code in 40..47) {
            val harness = StateHarness()
            harness.feed("\u001B[${code}m")
            assertEquals(Color.Indexed(code - 40), harness.state.background, "background $code")
        }
        for (code in 100..107) {
            val harness = StateHarness()
            harness.feed("\u001B[${code}m")
            assertEquals(Color.Indexed(code - 100 + 8), harness.state.background, "background $code")
        }
    }

    @Test
    fun ansiDefaultColorCodesResetOnlyTheirColor() {
        val harness = StateHarness()
        harness.feed("\u001B[1;31;42m")

        harness.feed("\u001B[39m")
        assertEquals(Color.Default, harness.state.foreground)
        assertEquals(Color.Indexed(2), harness.state.background)
        assertTrue(harness.state.attributes and Attributes.BOLD != 0)

        harness.feed("\u001B[49m")
        assertEquals(Color.Default, harness.state.background)
    }

    @Test
    fun blinkCodesSixAndFiveSetTheSameAttribute() {
        val five = StateHarness()
        val six = StateHarness()

        five.feed("\u001B[5m")
        six.feed("\u001B[6m")

        assertEquals(
            five.state.attributes and Attributes.BLINK,
            six.state.attributes and Attributes.BLINK,
        )
    }

    @Test
    fun indexedAndRgbColorsUseSemicolonAndColonForms() {
        val harness = StateHarness()

        harness.feed("\u001B[38;5;56;48:2::1:2:3m")

        assertEquals(Color.Indexed(56), harness.state.foreground)
        assertEquals(Color.Rgb(1, 2, 3), harness.state.background)
    }

    @Test
    fun colonAndSemicolonRgbFormsAreEquivalent() {
        val colon = StateHarness()
        val semicolon = StateHarness()

        colon.feed("\u001B[38:2::24:68:112m")
        semicolon.feed("\u001B[38;2;24;68;112m")
        assertEquals(semicolon.state.foreground, colon.state.foreground)
        assertEquals(Color.Rgb(24, 68, 112), colon.state.foreground)

        val compact = StateHarness()
        compact.feed("\u001B[38:2:24:68:112m")
        assertEquals(Color.Rgb(24, 68, 112), compact.state.foreground)
    }

    @Test
    fun sgrZeroResetsAttributesAndColors() {
        val harness = StateHarness()
        harness.feed("\u001B[1;3;4:3;5;7;8;9;31;42;58;5;200m")

        harness.feed("\u001B[0m")

        assertEquals(Attributes.NONE, harness.state.attributes)
        assertEquals(Color.Default, harness.state.foreground)
        assertEquals(Color.Default, harness.state.background)
        assertEquals(Color.Default, harness.state.underlineColor)
    }

    @Test
    fun resetCodesClearOnlyTheirTargets() {
        val cases =
            listOf(
                22 to (Attributes.BOLD or Attributes.FAINT),
                23 to Attributes.ITALIC,
                24 to Attributes.UNDERLINE_MASK,
                25 to Attributes.BLINK,
                27 to Attributes.REVERSE,
                28 to Attributes.INVISIBLE,
                29 to Attributes.STRIKE,
            )
        for ((code, cleared) in cases) {
            val harness = StateHarness()
            harness.feed("\u001B[1;2;3;4;5;7;8;9;31;42m")

            harness.feed("\u001B[${code}m")

            assertEquals(0, harness.state.attributes and cleared, "reset code $code")
            val remaining = harness.state.allAttributes and cleared.inv()
            assertEquals(remaining, harness.state.attributes and cleared.inv(), "reset code $code kept other bits")
            assertEquals(Color.Indexed(1), harness.state.foreground, "reset code $code kept foreground")
            assertEquals(Color.Indexed(2), harness.state.background, "reset code $code kept background")
        }
    }

    @Test
    fun underlineStylesZeroThroughFiveAreStored() {
        for (style in 0..5) {
            val harness = StateHarness()

            harness.feed("\u001B[4:${style}m")

            assertEquals(style, Attributes.underlineStyle(harness.state.attributes), "style $style")
        }
    }

    @Test
    fun plainUnderlineAndDoubleUnderlineSetStyles() {
        val harness = StateHarness()

        harness.feed("\u001B[4m")
        assertEquals(1, Attributes.underlineStyle(harness.state.attributes))

        harness.feed("\u001B[21m")
        assertEquals(2, Attributes.underlineStyle(harness.state.attributes))

        harness.feed("\u001B[24m")
        assertEquals(0, Attributes.underlineStyle(harness.state.attributes))
    }

    @Test
    fun cellStoresUnderlineStyleAndColor() {
        val harness = StateHarness()

        harness.feed("\u001B[1;4:3;58;5;9mA")

        assertEquals(
            Attributes.BOLD or Attributes.withUnderlineStyle(Attributes.NONE, 3),
            harness.state.screen.attributesAt(0, 0),
        )
        assertEquals(Color.Indexed(9), Color.decode(harness.state.screen.underlineColorAt(0, 0)))
    }

    @Test
    fun underlineColorIsStoredAndReset() {
        val harness = StateHarness()

        harness.feed("\u001B[58;5;200m")
        assertEquals(Color.Indexed(200), harness.state.underlineColor)

        harness.feed("\u001B[59m")
        assertEquals(Color.Default, harness.state.underlineColor)
    }

    @Test
    fun unknownParametersAreIgnored() {
        val harness = StateHarness()

        harness.feed("\u001B[999;31m")

        assertEquals(Color.Indexed(1), harness.state.foreground)
    }

    @Test
    fun malformedColorFormsDoNotThrowOrLeakCodes() {
        val harness = StateHarness()

        harness.feed("\u001B[38;2;1m")
        harness.feed("\u001B[38:5m")
        harness.feed("\u001B[38:2::1:2m")
        harness.feed("\u001B[48;5;999m")

        assertEquals(Color.Default, harness.state.foreground)
        assertEquals(Color.Default, harness.state.background)
    }

    @Test
    fun decrqssRoundTripsCurrentSgr() {
        val harness = StateHarness()
        harness.feed("\u001B[1;4:3;38;5;56;48;2;1;2;3;58;5;9m")

        harness.feed("\u001BP\$qm\u001B\\")

        assertEquals("\u001BP1\$r1;4:3;38;5;56;48;2;1;2;3;58;5;9m\u001B\\", harness.readResponses())
    }

    @Test
    fun decrqssResetSgrIsZero() {
        val harness = StateHarness()

        harness.feed("\u001BP\$qm\u001B\\")

        assertEquals("\u001BP1\$r0m\u001B\\", harness.readResponses())
    }

    private val TerminalState.allAttributes: Int
        get() =
            Attributes.BOLD or
                Attributes.FAINT or
                Attributes.ITALIC or
                Attributes.BLINK or
                Attributes.REVERSE or
                Attributes.INVISIBLE or
                Attributes.STRIKE or
                Attributes.withUnderlineStyle(Attributes.NONE, 1)

    @Test
    fun paletteHasAnsiCubeAndGrayEntries() {
        assertEquals(
            0xFF000000.toInt(),
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette
                .color(0),
        )
        assertEquals(
            0xFFFF0000.toInt(),
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette
                .color(9),
        )
        assertEquals(
            0xFF000000.toInt(),
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette
                .color(16),
        )
        assertEquals(
            0xFF5F0000.toInt(),
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette
                .color(52),
        )
        assertEquals(
            0xFF080808.toInt(),
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette
                .color(232),
        )
        assertEquals(
            0xFFEEEEEE.toInt(),
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette
                .color(255),
        )
        assertTrue(
            com.psiwray.organization.terminal_window_cmdline.vt.attr.Palette
                .argb(Color.Rgb(1, 2, 3), 0) ==
                0xFF010203.toInt(),
        )
    }
}
