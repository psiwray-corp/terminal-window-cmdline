package com.psiwray.organization.terminal_window_cmdline.benchmarks

import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState

internal object ScreenBenchmark {
    fun run(
        warmup: Int,
        frames: Int,
        results: MutableList<FrameBenchResult>,
    ) {
        scenario(
            "overwrite-80x24",
            columns = 80,
            rows = 24,
            input = overwriteInput(80, 24),
            warmup = warmup,
            frames = frames,
        ).also(results::add)
        scenario(
            "overwrite-240x67",
            columns = 240,
            rows = 67,
            input = overwriteInput(240, 67),
            warmup = warmup,
            frames = frames,
        ).also(results::add)
        scenario("sgr-80x24", columns = 80, rows = 24, input = coloredInput(80, 24), warmup = warmup, frames = frames)
            .also(results::add)
        scenario(
            "scroll-240x67",
            columns = 240,
            rows = 67,
            input = scrollInput(240, 67),
            warmup = warmup,
            frames = frames,
        ).also(results::add)
    }

    private fun scenario(
        name: String,
        rows: Int,
        columns: Int,
        input: ByteArray,
        warmup: Int,
        frames: Int,
    ): FrameBenchResult {
        val state = TerminalState(rows = rows, columns = columns)
        val parser = Parser(state)
        return BenchSupport.measureFrames(
            name = "screen/$name",
            frames = frames,
            cells = rows * columns,
            warmup = warmup,
        ) {
            parser.reset()
            parser.write(input)
        }
    }

    private fun overwriteInput(
        columns: Int,
        rows: Int,
    ): ByteArray = ("\u001B[H" + "x".repeat(columns * rows)).toByteArray(Charsets.UTF_8)

    private fun coloredInput(
        columns: Int,
        rows: Int,
    ): ByteArray = ("\u001B[H" + "\u001B[38;5;196;48;5;17mX".repeat(columns * rows)).toByteArray(Charsets.UTF_8)

    private fun scrollInput(
        columns: Int,
        rows: Int,
    ): ByteArray {
        val line = "x".repeat(columns) + "\r\n"
        return ("\u001B[H" + line.repeat(rows)).toByteArray(Charsets.UTF_8)
    }
}
