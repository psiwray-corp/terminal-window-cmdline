package com.psiwray.organization.terminal_window_cmdline.vt.fuzz

import com.psiwray.organization.terminal_window_cmdline.vt.limits.VtLimits
import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserSink
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserFuzzTest {
    @Test
    fun randomBytesNeverThrowAndStayWithinEventLimits() {
        val random = Random(0x5EED)
        repeat(750) {
            val input = random.nextBytes(random.nextInt(0, 513))
            exercise(input)
            exercise(byteArrayOf(0xE2.toByte(), 0x82.toByte()) + random.nextBytes(random.nextInt(0, 32)))
            exercise(byteArrayOf(0xF0.toByte(), 0x9F.toByte(), 0x92.toByte()) + random.nextBytes(random.nextInt(0, 32)))
        }
    }

    @Test
    fun malformedAndPathologicalStreamsRecover() {
        val random = Random(0xC0FFEE)
        val cases =
            listOf(
                ByteArray(4096) { 0x1B },
                ByteArray(4096) { 0x00 },
                ("\u001B[" + "1;".repeat(200) + "m").toByteArray(),
                ("\u001B[" + "1".repeat(5000) + "m").toByteArray(),
                ("\u001B]2;" + "x".repeat(20_000)).toByteArray(),
                ("\u001BPq" + "x".repeat(20_000)).toByteArray(),
                ("\u001B[" + "\u001B[".repeat(500)).toByteArray(),
                ("\u001B_" + "nested-sos".repeat(500) + "\u001B[").toByteArray(),
            )

        cases.forEach { input ->
            val sink = BoundSink()
            val parser = Parser(sink)
            writeInChunks(parser, input, random)
            recover(parser, sink)
        }
    }

    private fun exercise(input: ByteArray) {
        val sink = BoundSink()
        val parser = Parser(sink)
        val random = Random(input.contentHashCode())
        writeInChunks(parser, input, random)
        recover(parser, sink)
    }

    private fun writeInChunks(
        parser: Parser,
        input: ByteArray,
        random: Random,
    ) {
        var offset = 0
        while (offset < input.size) {
            val count = minOf(input.size - offset, random.nextInt(1, 65))
            parser.write(input, offset, count)
            offset += count
        }
    }

    private fun recover(
        parser: Parser,
        sink: BoundSink,
    ) {
        val before = sink.csiEvents
        parser.write("\u001B\\\u001B[1m".toByteArray())
        assertEquals(before + 1, sink.csiEvents)

        parser.write("\u001B]2;ok\u0007".toByteArray())
        assertTrue(sink.oscEvents > 0)
    }

    private class BoundSink : ParserSink {
        var csiEvents: Int = 0
        var oscEvents: Int = 0

        override fun onEvent(event: ParserEvent) {
            when (event) {
                is ParserEvent.Csi -> {
                    csiEvents++
                    assertTrue(event.parameters.size <= VtLimits.CSI_PARAMETERS)
                    assertTrue(event.intermediates.size <= VtLimits.INTERMEDIATE_BYTES)
                }

                is ParserEvent.Esc -> {
                    assertTrue(event.intermediates.size <= VtLimits.INTERMEDIATE_BYTES)
                }

                is ParserEvent.Osc -> {
                    oscEvents++
                    assertTrue(event.bytes.size <= VtLimits.STRING_BYTES)
                }

                is ParserEvent.Dcs -> {
                    assertTrue(event.bytes.size <= VtLimits.STRING_BYTES)
                }

                is ParserEvent.Apc -> {
                    assertTrue(event.bytes.size <= VtLimits.STRING_BYTES)
                }

                is ParserEvent.Pm -> {
                    assertTrue(event.bytes.size <= VtLimits.STRING_BYTES)
                }

                is ParserEvent.Sos -> {
                    assertTrue(event.bytes.size <= VtLimits.STRING_BYTES)
                }

                is ParserEvent.Print,
                is ParserEvent.Execute,
                -> {}
            }
        }
    }
}
