package com.psiwray.organization.terminal_window_cmdline.vt.text

import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8DecoderTest {
    @Test
    fun decodesAsciiAndTwoByteSequences() {
        assertEquals(listOf('A'.code, 0x00A9), decode(bytes('A'.code, 0xC2, 0xA9)))
    }

    @Test
    fun decodesThreeAndFourByteSequences() {
        assertEquals(listOf(0x20AC), decode(bytes(0xE2, 0x82, 0xAC)))
        assertEquals(listOf(0x1F600), decode(bytes(0xF0, 0x9F, 0x98, 0x80)))
        assertEquals(listOf(0x10FFFF), decode(bytes(0xF4, 0x8F, 0xBF, 0xBF)))
    }

    @Test
    fun preservesSplitSequencesAcrossCalls() {
        val decoder = Utf8Decoder()
        val output = mutableListOf<Int>()
        val sink = CodePointSink { output += it }

        decoder.decode(bytes(0xF0), sink)
        decoder.decode(bytes(0x9F), sink)
        decoder.decode(bytes(0x98), sink)
        assertEquals(emptyList(), output)
        decoder.decode(bytes(0x80), sink)

        assertEquals(listOf(0x1F600), output)
    }

    @Test
    fun truncatedSequenceBecomesReplacementOnFinish() {
        val decoder = Utf8Decoder()
        val output = mutableListOf<Int>()
        val sink = CodePointSink { output += it }

        decoder.decode(bytes(0xE2, 0x82), sink)
        assertEquals(emptyList(), output)
        decoder.finish(sink)

        assertEquals(listOf(0xFFFD), output)
    }

    @Test
    fun invalidSequencesBecomeReplacements() {
        assertEquals(listOf(0xFFFD), decode(byteArrayOf(0x9B.toByte())))
        assertEquals(listOf(0xFFFD, '('.code, 0xFFFD), decode(bytes(0xE2, 0x28, 0xA1)))
        assertEquals(listOf(0xFFFD, 0xFFFD), decode(bytes(0xC0, 0x80)))
        assertEquals(listOf(0xFFFD, 0xFFFD, 0xFFFD), decode(bytes(0xED, 0xA0, 0x80)))
        assertEquals(listOf(0xFFFD), decode(bytes(0xF5)))
        assertEquals(listOf(0xFFFD, 0xFFFD, 0xFFFD, 0xFFFD), decode(bytes(0xF5, 0x80, 0x80, 0x80)))
    }

    @Test
    fun resetDiscardsPartialSequence() {
        val decoder = Utf8Decoder()
        val output = mutableListOf<Int>()
        val sink = CodePointSink { output += it }

        decoder.decode(bytes(0xE2), sink)
        decoder.reset()
        decoder.decode(bytes('A'.code), sink)

        assertEquals(listOf('A'.code), output)
    }

    private fun decode(bytes: ByteArray): List<Int> {
        val output = mutableListOf<Int>()
        val sink = CodePointSink { output += it }
        Utf8Decoder().apply {
            decode(bytes, sink)
            finish(sink)
        }
        return output
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
