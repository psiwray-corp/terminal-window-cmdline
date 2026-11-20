package com.psiwray.organization.terminal_window_cmdline.benchmarks

import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserEvent
import com.psiwray.organization.terminal_window_cmdline.vt.parser.ParserSink

internal object ParserBenchmark {
    private const val TARGET_BYTES: Int = 1 shl 20

    fun run(
        warmup: Int,
        iterations: Int,
        results: MutableList<ByteBenchResult>,
    ) {
        val sink = CountingSink()
        val parser = Parser(sink)

        fun scenario(
            name: String,
            input: ByteArray,
            action: (ByteArray) -> Unit,
        ): ByteBenchResult =
            BenchSupport.measureBytes("parser/$name", input, warmup, iterations) {
                action(input)
            }

        scenario("ascii", asciiInput()) { input ->
            parser.reset()
            parser.write(input)
        }.also(results::add)
        scenario("utf8", utf8Input()) { input ->
            parser.reset()
            parser.write(input)
        }.also(results::add)
        scenario("sgr", sgrInput()) { input ->
            parser.reset()
            parser.write(input)
        }.also(results::add)
        scenario("cursor", cursorInput()) { input ->
            parser.reset()
            parser.write(input)
        }.also(results::add)
        scenario("osc", oscInput()) { input ->
            parser.reset()
            parser.write(input)
        }.also(results::add)
        scenario("dcs", dcsInput()) { input ->
            parser.reset()
            parser.write(input)
        }.also(results::add)

        check(sink.events > 0)
        check(sink.payloadBytes > 0)
    }

    private fun asciiInput(): ByteArray = repeatToTarget("The quick brown fox jumps over the lazy dog.")

    private fun utf8Input(): ByteArray = repeatToTarget("a\u00E9\u20AC\uD83D\uDE00")

    private fun sgrInput(): ByteArray = repeatToTarget("\u001B[1;38;5;196;48;2;10;20;30mX")

    private fun cursorInput(): ByteArray = repeatToTarget("\u001B[10;20H\u001B[2K\u001B[1A\u001B[3D\u001B[4C!")

    private fun oscInput(): ByteArray = repeatToTarget("\u001B]0;terminal-window-cmdline\u0007")

    private fun dcsInput(): ByteArray = repeatToTarget("\u001BPq0123456789abcdef\u001B\\")

    private fun repeatToTarget(pattern: String): ByteArray {
        val bytes = pattern.toByteArray(Charsets.UTF_8)
        val output = ByteArray(TARGET_BYTES)
        var offset = 0
        while (offset < output.size) {
            val count = minOf(bytes.size, output.size - offset)
            bytes.copyInto(output, offset, 0, count)
            offset += count
        }
        return output
    }

    private class CountingSink : ParserSink {
        var events: Long = 0
        var payloadBytes: Long = 0

        override fun onEvent(event: ParserEvent) {
            events++
            when (event) {
                is ParserEvent.Print -> payloadBytes += event.bytes.size
                is ParserEvent.Osc -> payloadBytes += event.bytes.size
                is ParserEvent.Dcs -> payloadBytes += event.bytes.size
                is ParserEvent.Apc -> payloadBytes += event.bytes.size
                is ParserEvent.Pm -> payloadBytes += event.bytes.size
                is ParserEvent.Sos -> payloadBytes += event.bytes.size
                is ParserEvent.Csi -> payloadBytes += event.parameters.size + event.intermediates.size
                is ParserEvent.Esc -> payloadBytes += event.intermediates.size
                is ParserEvent.Execute -> Unit
            }
        }
    }
}
