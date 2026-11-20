package com.psiwray.organization.terminal_window_cmdline.benchmarks

import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window_cmdline.app.ScreenProjection
import com.psiwray.organization.terminal_window_cmdline.app.Theme
import com.psiwray.organization.terminal_window_cmdline.vt.parser.Parser
import com.psiwray.organization.terminal_window_cmdline.vt.state.TerminalState

internal object ProjectionBenchmark {
    fun run(
        warmup: Int,
        frames: Int,
        results: MutableList<FrameBenchResult>,
    ) {
        scenario(80, 24, warmup, frames).also(results::add)
        scenario(240, 67, warmup, frames).also(results::add)
    }

    private fun scenario(
        columns: Int,
        rows: Int,
        warmup: Int,
        frames: Int,
    ): FrameBenchResult {
        val state = TerminalState(rows = rows, columns = columns)
        val parser = Parser(state)
        val line = "x".repeat(columns)
        val input =
            buildString(rows * (columns + 2)) {
                repeat(rows) {
                    append(line)
                    append("\r\n")
                }
            }.toByteArray(Charsets.UTF_8)
        parser.write(input)

        val theme = Theme.named("mint")
        val matrix =
            TerminalMatrix(
                columns = columns,
                rows = rows,
                defaultForeground = theme.foreground,
                defaultBackground = theme.background,
            )

        return BenchSupport.measureFrames(
            name = "projection/${columns}x$rows",
            frames = frames,
            cells = columns * rows,
            warmup = warmup,
        ) {
            ScreenProjection.project(
                view = state.screen,
                cursorRow = state.cursorRow,
                cursorColumn = state.cursorColumn,
                cursorVisible = state.cursorVisible,
                screenReverse = state.screenReverse,
                theme = theme,
                target = matrix,
            )
        }
    }
}
