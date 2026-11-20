package com.psiwray.organization.terminal_window_cmdline.vt.parser

import com.psiwray.organization.terminal_window_cmdline.vt.limits.VtLimits
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun csiSplitAcrossWritesDispatchesOnce() {
        val events = collect(bytes(ESC, '['), ascii("31m"))

        val csi = assertIs<ParserEvent.Csi>(events.single())
        assertContentEquals(intArrayOf(31), csi.parameters)
        assertContentEquals(booleanArrayOf(false), csi.parameterMore)
        assertEquals('m'.code, csi.finalByte)
    }

    @Test
    fun canAndSubCancelAndEscapeRestarts() {
        assertTrue(collect(bytes(ESC, '[', '1', '2', CAN, 'A')).single() is ParserEvent.Print)
        assertTrue(collect(bytes(ESC, '[', '1', '2', SUB, 'A')).single() is ParserEvent.Print)

        val events = collect(bytes(ESC, '[', '1', '2', ESC, '[', '3', '1', 'm'))
        val csi = assertIs<ParserEvent.Csi>(events.single())
        assertContentEquals(intArrayOf(31), csi.parameters)
    }

    @Test
    fun c0InsideCsiExecutesAndSequenceContinues() {
        val events = collect(bytes(ESC, '[', '1', '2', LF, ';', '3', 'X'))

        assertEquals(ParserEvent.Execute(LF), events[0])
        val csi = assertIs<ParserEvent.Csi>(events[1])
        assertContentEquals(intArrayOf(12, 3), csi.parameters)
        assertEquals('X'.code, csi.finalByte)
    }

    @Test
    fun oscTerminatesOnBelAndSt() {
        val bel = collect(bytes(ESC, ']', '2', ';', 't', 'i', 't', 'l', 'e', BEL))
        val belEvent = assertIs<ParserEvent.Osc>(bel.single())
        assertEquals(2, belEvent.command)
        assertContentEquals(ascii("title"), belEvent.bytes)
        assertTrue(belEvent.initial)
        assertTrue(belEvent.final)

        val st = collect(bytes(ESC, ']', '1', ';', 'x', ESC, '\\'))
        val stEvent = assertIs<ParserEvent.Osc>(st.single())
        assertEquals(1, stEvent.command)
        assertContentEquals(ascii("x"), stEvent.bytes)
        assertTrue(stEvent.final)
    }

    @Test
    fun oscSplitStEmitsFinalEmptyChunk() {
        val events = collect(bytes(ESC, ']', '1', ';', 'x', ESC), bytes('\\'))

        val first = assertIs<ParserEvent.Osc>(events[0])
        assertContentEquals(ascii("x"), first.bytes)
        assertTrue(first.initial)
        assertTrue(!first.final)

        val last = assertIs<ParserEvent.Osc>(events[1])
        assertTrue(!last.initial)
        assertTrue(last.final)
        assertContentEquals(ByteArray(0), last.bytes)
    }

    @Test
    fun unterminatedOscIsCapped() {
        val payload = ByteArray(9000) { 'a'.code.toByte() }
        val input =
            bytes(ESC, ']', '2', ';') +
                payload +
                bytes(ESC, '\\')

        val events = collect(input)
        val osc = assertIs<ParserEvent.Osc>(events.single())
        assertEquals(VtLimits.STRING_BYTES, osc.bytes.size)
        assertTrue(osc.final)
    }

    @Test
    fun dcsPassthroughReceivesBytesAndUnhooksOnSt() {
        val events = collect(bytes(ESC, 'P', 'H', 'e', 'l', 'l', 'o', ESC, '\\'))
        val dcs = assertIs<ParserEvent.Dcs>(events.single())
        assertEquals('H'.code, dcs.finalByte)
        assertContentEquals(ascii("ello"), dcs.bytes)
        assertTrue(dcs.initial)
        assertTrue(dcs.final)
    }

    @Test
    fun dcsSplitStUnhooksOnSecondWrite() {
        val events = collect(bytes(ESC, 'P', 'H', 'i', ESC), bytes('\\'))

        val first = assertIs<ParserEvent.Dcs>(events[0])
        assertContentEquals(ascii("i"), first.bytes)
        assertTrue(first.initial)
        assertTrue(!first.final)

        val last = assertIs<ParserEvent.Dcs>(events[1])
        assertTrue(!last.initial)
        assertTrue(last.final)
        assertContentEquals(ByteArray(0), last.bytes)
    }

    @Test
    fun unknownDcsIsDispatchedNotPrinted() {
        val events = collect(bytes(ESC, 'P', '$', 'q', 'x', ESC, '\\'))

        val dcs = assertIs<ParserEvent.Dcs>(events.single())
        assertEquals('q'.code, dcs.finalByte)
        assertContentEquals(ascii("$"), dcs.intermediates)
        assertContentEquals(ascii("x"), dcs.bytes)
        assertTrue(dcs.final)
    }

    @Test
    fun cancelledStringsNeverReportFinalAcrossWrites() {
        val splitOsc = collect(bytes(ESC, ']', '0', ';', 't', 'i', 't', 'l', 'e'), bytes(CAN))
        assertTrue(splitOsc.none { it is ParserEvent.Osc && it.final })

        val oneWriteOsc = collect(bytes(ESC, ']', '0', ';', 't', 'i', 't', 'l', 'e', CAN))
        assertTrue(oneWriteOsc.isEmpty())

        val splitDcs = collect(bytes(ESC, 'P', '$', 'q', 'm'), bytes(CAN))
        assertTrue(splitDcs.none { it is ParserEvent.Dcs && it.final })

        val tailAfterCancel = collect(bytes(ESC, ']', '0', ';', 'a', 'b'), bytes('c', CAN))
        assertTrue(tailAfterCancel.none { it is ParserEvent.Osc && it.final })
    }

    @Test
    fun cancellationHasNoFinalEventAtAnySplitPoint() {
        val osc = bytes(ESC, ']', '0', ';', 't', 'i', 't', 'l', 'e', CAN)
        for (split in 0..osc.size) {
            val events = collect(osc.copyOfRange(0, split), osc.copyOfRange(split, osc.size))
            assertTrue(
                events.none { it is ParserEvent.Osc && it.final },
                "OSC split at $split produced a final chunk",
            )
        }

        val dcs = bytes(ESC, 'P', '$', 'q', 'm', CAN)
        for (split in 0..dcs.size) {
            val events = collect(dcs.copyOfRange(0, split), dcs.copyOfRange(split, dcs.size))
            assertTrue(
                events.none { it is ParserEvent.Dcs && it.final },
                "DCS split at $split produced a final chunk",
            )
        }
    }

    @Test
    fun oscCapIsIndependentOfCommandPrefixLength() {
        val prefix = ascii("1234567890123456;")
        val payload = ByteArray(VtLimits.STRING_BYTES) { 'a'.code.toByte() }
        val input = bytes(ESC, ']') + prefix + payload + bytes(ESC, '\\')

        val osc = assertIs<ParserEvent.Osc>(collect(input).single())
        assertEquals(VtLimits.STRING_BYTES, osc.bytes.size)
        assertTrue(osc.final)
    }

    @Test
    fun parserEventsCompareByContentAndOwnDcsMetadata() {
        assertEquals(ParserEvent.Print(byteArrayOf(0x41)), ParserEvent.Print(byteArrayOf(0x41)))
        assertEquals(
            ParserEvent.Csi(0, intArrayOf(1), booleanArrayOf(false), ByteArray(0), 'm'.code),
            ParserEvent.Csi(0, intArrayOf(1), booleanArrayOf(false), ByteArray(0), 'm'.code),
        )

        val events = collect(bytes(ESC, 'P', '1', ';', '2', '$', 'q', 'a'), bytes('b', ESC, '\\'))
        val first = assertIs<ParserEvent.Dcs>(events[0])
        val second = assertIs<ParserEvent.Dcs>(events[1])
        assertContentEquals(intArrayOf(1, 2), first.parameters)
        assertContentEquals(intArrayOf(1, 2), second.parameters)
        assertTrue(first.parameters !== second.parameters)
        first.parameters[0] = 999
        assertEquals(1, second.parameters[0])
    }

    @Test
    fun csiLimitsAtExactBoundariesDispatch() {
        val thirtyTwo = mutableListOf<Int>()
        thirtyTwo.addAll(bytes(ESC, '[').map { it.toInt() and 0xFF })
        repeat(32) { index ->
            if (index > 0) {
                thirtyTwo.add(';'.code)
            }
            thirtyTwo.add('1'.code)
        }
        thirtyTwo.add('m'.code)
        val params = assertIs<ParserEvent.Csi>(collect(ByteArray(thirtyTwo.size) { thirtyTwo[it].toByte() }).single())
        assertEquals(32, params.parameters.size)

        val tenDigits = collect(bytes(ESC, '[') + ascii("1234567890") + bytes('m')).single()
        val digits = assertIs<ParserEvent.Csi>(tenDigits)
        assertContentEquals(intArrayOf(1234567890), digits.parameters)

        val twoIntermediates = collect(bytes(ESC, '[', '1', 0x20, 0x21, 'p')).single()
        val intermediates = assertIs<ParserEvent.Csi>(twoIntermediates)
        assertContentEquals(byteArrayOf(0x20, 0x21), intermediates.intermediates)
    }

    @Test
    fun malformedDcsIsConsumedUntilSt() {
        val events = collect(bytes(ESC, 'P', ':', 'x', ESC, '\\', 'A'))

        val print = assertIs<ParserEvent.Print>(events.single())
        assertContentEquals(ascii("A"), print.bytes)
    }

    @Test
    fun byte9bIsTextNotCsi() {
        val input = byteArrayOf(0x9B.toByte(), '3'.code.toByte(), '1'.code.toByte(), 'm'.code.toByte())
        val events = collect(input)

        val print = assertIs<ParserEvent.Print>(events.single())
        assertContentEquals(input, print.bytes)
    }

    @Test
    fun csiLimitsIgnoreMalformedSequences() {
        val tooManyParameters = mutableListOf<Int>()
        tooManyParameters.addAll(bytes(ESC, '[').map { it.toInt() and 0xFF })
        repeat(33) { index ->
            if (index > 0) {
                tooManyParameters.add(';'.code)
            }
            tooManyParameters.add('1'.code)
        }
        tooManyParameters.add('m'.code)
        val tooManyBytes = ByteArray(tooManyParameters.size) { tooManyParameters[it].toByte() }
        assertTrue(collect(tooManyBytes).isEmpty())

        val tooManyDigits = bytes(ESC, '[') + ascii("12345678901") + bytes('m')
        assertTrue(collect(tooManyDigits).isEmpty())

        val tooManyIntermediates = bytes(ESC, '[', '1', 0x20, 0x21, 0x22, 'p')
        assertTrue(collect(tooManyIntermediates).isEmpty())
    }

    @Test
    fun privateMarkersAndColonParametersArePreserved() {
        val events = collect(bytes(ESC, '[', '?', '2', '5', 'h'))
        val csi = assertIs<ParserEvent.Csi>(events.single())
        assertEquals('?'.code, csi.privateMarker)
        assertContentEquals(intArrayOf(25), csi.parameters)

        val colon = collect(bytes(ESC, '[', '3', '8', ':', '2', ':', ':', '1', ':', '2', ':', '3', 'm'))
        val sgr = assertIs<ParserEvent.Csi>(colon.single())
        assertContentEquals(
            intArrayOf(38, 2, ParserEvent.MISSING_PARAMETER, 1, 2, 3),
            sgr.parameters,
        )
        assertContentEquals(
            booleanArrayOf(true, true, true, true, true, false),
            sgr.parameterMore,
        )
    }

    @Test
    fun unknownWellFormedCsiFallsThroughAsEvent() {
        val events = collect(bytes(ESC, '[', '9', '9', '~'))
        val csi = assertIs<ParserEvent.Csi>(events.single())
        assertContentEquals(intArrayOf(99), csi.parameters)
        assertEquals('~'.code, csi.finalByte)
    }

    @Test
    fun randomBytesNeverThrowAndStayBounded() {
        val random = Random(0x5EED)
        var maxPayload = 0
        var maxParameters = 0
        var maxIntermediates = 0
        val sink =
            ParserSink { event ->
                when (event) {
                    is ParserEvent.Print -> {
                        maxPayload = maxOf(maxPayload, event.bytes.size)
                    }

                    is ParserEvent.Osc -> {
                        maxPayload = maxOf(maxPayload, event.bytes.size)
                    }

                    is ParserEvent.Dcs -> {
                        maxPayload = maxOf(maxPayload, event.bytes.size)
                        maxParameters = maxOf(maxParameters, event.parameters.size)
                        maxIntermediates = maxOf(maxIntermediates, event.intermediates.size)
                    }

                    is ParserEvent.Csi -> {
                        maxParameters = maxOf(maxParameters, event.parameters.size)
                        maxIntermediates = maxOf(maxIntermediates, event.intermediates.size)
                    }

                    is ParserEvent.Apc -> {
                        maxPayload = maxOf(maxPayload, event.bytes.size)
                    }

                    is ParserEvent.Pm -> {
                        maxPayload = maxOf(maxPayload, event.bytes.size)
                    }

                    is ParserEvent.Sos -> {
                        maxPayload = maxOf(maxPayload, event.bytes.size)
                    }

                    is ParserEvent.Esc -> {
                        maxIntermediates = maxOf(maxIntermediates, event.intermediates.size)
                    }

                    is ParserEvent.Execute -> {}
                }
            }
        val parser = Parser(sink)

        var written = 0
        while (written < 200_000) {
            val length = random.nextInt(1, 65)
            val chunk = ByteArray(length) { random.nextInt(256).toByte() }
            parser.write(chunk)
            written += length
        }

        assertTrue(maxPayload <= VtLimits.STRING_BYTES)
        assertTrue(maxParameters <= 32)
        assertTrue(maxIntermediates <= 2)
    }

    private fun collect(vararg chunks: ByteArray): List<ParserEvent> {
        val events = mutableListOf<ParserEvent>()
        val parser = Parser(ParserSink { events += it })
        for (chunk in chunks) {
            parser.write(chunk)
        }
        return events
    }

    private companion object {
        private const val ESC = 0x1B
        private const val BEL = 0x07
        private const val LF = 0x0A
        private const val CAN = 0x18
        private const val SUB = 0x1A

        private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

        private fun bytes(vararg values: Any): ByteArray =
            ByteArray(values.size) { index ->
                when (val value = values[index]) {
                    is Int -> value.toByte()
                    is Char -> value.code.toByte()
                    else -> error("unsupported byte value: $value")
                }
            }
    }
}
