package com.psiwray.organization.terminal_window_cmdline.vt.limits

import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent
import com.psiwray.organization.terminal_window_cmdline.vt.state.Responder
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LimitTest {
    @Test
    fun csiParameterCountAcceptsLimitAndRejectsOneMore() {
        listOf(VtLimits.CSI_PARAMETERS - 1, VtLimits.CSI_PARAMETERS).forEach { count ->
            val csi = parse(csiWithParameters(count)).singleCsi()
            assertEquals(count, csi.parameters.size)
        }

        val ignored = parse(csiWithParameters(VtLimits.CSI_PARAMETERS + 1))
        assertTrue(ignored.none { it is ParserEvent.Csi })

        val recovered = parse(csiWithParameters(VtLimits.CSI_PARAMETERS + 1) + "\u001B[1m")
        assertEquals(1, recovered.singleCsi().parameters.single())
    }

    @Test
    fun parameterDigitsAcceptLimitAndRejectOneMore() {
        listOf(VtLimits.PARAMETER_DIGITS - 1, VtLimits.PARAMETER_DIGITS).forEach { digits ->
            val csi = parse("\u001B[${"1".repeat(digits)}m").singleCsi()
            assertEquals(1, csi.parameters.size)
        }

        val ignored = parse("\u001B[${"1".repeat(VtLimits.PARAMETER_DIGITS + 1)}m")
        assertTrue(ignored.none { it is ParserEvent.Csi })

        val recovered = parse("\u001B[${"1".repeat(VtLimits.PARAMETER_DIGITS + 1)}m\u001B[1m")
        assertEquals(1, recovered.singleCsi().parameters.single())
    }

    @Test
    fun intermediateBytesAcceptLimitAndRejectOneMore() {
        listOf(VtLimits.INTERMEDIATE_BYTES - 1, VtLimits.INTERMEDIATE_BYTES).forEach { count ->
            val csi = parse("\u001B[${"!".repeat(count)}m").singleCsi()
            assertEquals(count, csi.intermediates.size)
        }

        val ignored = parse("\u001B[${"!".repeat(VtLimits.INTERMEDIATE_BYTES + 1)}m")
        assertTrue(ignored.none { it is ParserEvent.Csi })

        val recovered = parse("\u001B[${"!".repeat(VtLimits.INTERMEDIATE_BYTES + 1)}m\u001B[1m")
        assertEquals(1, recovered.singleCsi().parameters.single())
    }

    @Test
    fun oscPayloadTruncatesAtLimit() {
        listOf(VtLimits.STRING_BYTES - 1, VtLimits.STRING_BYTES, VtLimits.STRING_BYTES + 1).forEach { size ->
            val osc = assertIs<ParserEvent.Osc>(parse(oscPayload(size)).single())
            assertEquals(minOf(size, VtLimits.STRING_BYTES), osc.bytes.size)
            assertTrue(osc.final)
        }
    }

    @Test
    fun dcsPayloadTruncatesAtLimit() {
        listOf(VtLimits.STRING_BYTES - 1, VtLimits.STRING_BYTES, VtLimits.STRING_BYTES + 1).forEach { size ->
            val dcs = assertIs<ParserEvent.Dcs>(parse(dcsPayload(size)).single())
            assertEquals(minOf(size, VtLimits.STRING_BYTES), dcs.bytes.size)
            assertTrue(dcs.final)
        }
    }

    @Test
    fun unterminatedOscReturnsToGroundAndIsBounded() {
        val events = mutableListOf<ParserEvent>()
        val parser = Parser { events += it }
        parser.write(("\u001B]2;" + "a".repeat(VtLimits.STRING_BYTES + 100)).toByteArray())
        parser.write("\u001B\\\u001B[1m".toByteArray())

        val csi = events.filterIsInstance<ParserEvent.Csi>().single()
        assertEquals(1, csi.parameters.single())
    }

    @Test
    fun unterminatedDcsReturnsToGroundAndIsBounded() {
        val events = mutableListOf<ParserEvent>()
        val parser = Parser { events += it }
        val prefix = byteArrayOf(0x1B, 'P'.code.toByte(), 'q'.code.toByte())
        parser.write(prefix + ByteArray(VtLimits.STRING_BYTES + 100) { 'a'.code.toByte() })
        parser.write("\u001B\\\u001B[1m".toByteArray())

        val csi = events.filterIsInstance<ParserEvent.Csi>().single()
        assertEquals(1, csi.parameters.single())
    }

    @Test
    fun combiningMarksAcceptLimitAndDropOneMore() {
        listOf(
            VtLimits.COMBINING_CODE_POINTS - 1,
            VtLimits.COMBINING_CODE_POINTS,
            VtLimits.COMBINING_CODE_POINTS + 1,
        ).forEach { count ->
            val state = TerminalState(rows = 1, columns = 10)
            Parser(state).write(("e" + "\u0301".repeat(count)).toByteArray(Charsets.UTF_8))
            assertEquals(minOf(count, VtLimits.COMBINING_CODE_POINTS), state.screen.combiningCountAt(0, 0))
        }
    }

    @Test
    fun titleTruncatesAtLimit() {
        listOf(VtLimits.TITLE_BYTES - 1, VtLimits.TITLE_BYTES, VtLimits.TITLE_BYTES + 1).forEach { size ->
            val state = TerminalState(rows = 1, columns = 10)
            Parser(state).write(
                ("\u001B]2;" + "x".repeat(size) + "\u0007").toByteArray(Charsets.UTF_8),
            )
            assertEquals(minOf(size, VtLimits.TITLE_BYTES), state.windowTitle.length)
        }
    }

    @Test
    fun responseQueueAcceptsLimitAndDropsOneMore() {
        val below = Responder()
        below.write(ByteArray(VtLimits.RESPONSE_QUEUE_BYTES - 1))
        assertEquals(VtLimits.RESPONSE_QUEUE_BYTES - 1, below.available)
        assertEquals(0, below.droppedResponses)

        val exact = Responder()
        exact.write(ByteArray(VtLimits.RESPONSE_QUEUE_BYTES))
        assertEquals(VtLimits.RESPONSE_QUEUE_BYTES, exact.available)
        assertEquals(0, exact.droppedResponses)

        val over = Responder()
        over.write(ByteArray(VtLimits.RESPONSE_QUEUE_BYTES + 1))
        assertEquals(0, over.available)
        assertEquals(1, over.droppedResponses)
    }

    private fun csiWithParameters(count: Int): String =
        buildString {
            append("\u001B[")
            repeat(count) { index ->
                if (index != 0) append(';')
                append('1')
            }
            append('m')
        }

    private fun oscPayload(size: Int): String = "\u001B]2;" + "a".repeat(size) + "\u0007"

    private fun dcsPayload(size: Int): ByteArray {
        val prefix = byteArrayOf(0x1B, 'P'.code.toByte(), 'q'.code.toByte())
        val suffix = byteArrayOf(0x1B, '\\'.code.toByte())
        val payload = ByteArray(size) { 'a'.code.toByte() }
        return prefix + payload + suffix
    }

    private fun parse(text: String): List<ParserEvent> = parse(text.toByteArray(Charsets.UTF_8))

    private fun parse(bytes: ByteArray): List<ParserEvent> {
        val events = mutableListOf<ParserEvent>()
        Parser { events += it }.write(bytes)
        return events
    }

    private fun List<ParserEvent>.singleCsi(): ParserEvent.Csi = assertIs<ParserEvent.Csi>(single())
}
