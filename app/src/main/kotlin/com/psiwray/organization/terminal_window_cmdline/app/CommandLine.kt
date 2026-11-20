package com.psiwray.organization.terminal_window_cmdline.app

import com.psiwray.organization.terminal_window_cmdline.pty.TerminalSize
import java.nio.file.Files
import java.nio.file.Path

/** Immutable parsed launch configuration. */
internal data class LaunchConfig(
    val pipe: Boolean,
    val font: Path?,
    val columns: Int,
    val rows: Int,
    val theme: Theme,
    val term: String,
    val colorTerm: String,
    val workingDirectory: Path?,
    val environmentOverrides: Map<String, String>,
    val command: List<String>,
) {
    /** Character-cell size passed to resize-capable backends. */
    val size: TerminalSize
        get() = TerminalSize(columns, rows)

    /** Child environment with terminal defaults and explicit overrides applied. */
    fun childEnvironment(): Map<String, String> {
        val result = LinkedHashMap(System.getenv())
        result["TERM"] = term
        result["COLORTERM"] = colorTerm
        result["TERM_PROGRAM"] = CommandLine.TERM_PROGRAM
        result["TERM_PROGRAM_VERSION"] = CommandLine.PROGRAM_VERSION
        result.putAll(environmentOverrides)
        return result
    }
}

/** Parser for the executable's command line. */
internal object CommandLine {
    const val PROGRAM_VERSION: String = "1.0.0"
    const val TERM_PROGRAM: String = "terminal-window-cmdline"

    const val DEFAULT_COLUMNS: Int = 80
    const val DEFAULT_ROWS: Int = 24
    const val DEFAULT_TERM: String = "xterm-256color"
    const val DEFAULT_COLORTERM: String = "truecolor"

    fun parse(args: Array<String>): LaunchConfig {
        var pipe = false
        var font: Path? = null
        var columns = DEFAULT_COLUMNS
        var rows = DEFAULT_ROWS
        var themeName = "amber"
        var term = DEFAULT_TERM
        var colorTerm = DEFAULT_COLORTERM
        var workingDirectory: Path? = null
        val environment = LinkedHashMap<String, String>()
        var commandStart = -1

        var index = 0
        while (index < args.size) {
            val argument = args[index]
            if (argument == "--") {
                commandStart = index + 1
                break
            }
            val option = argument.substringBefore('=')
            val inlineValue = if ('=' in argument) argument.substringAfter('=') else null

            fun value(): String {
                if (inlineValue != null) {
                    return inlineValue
                }
                if (index + 1 >= args.size) {
                    throw IllegalArgumentException("$option requires a value")
                }
                index++
                return args[index]
            }
            when (option) {
                "--pipe" -> {
                    if (inlineValue != null) {
                        throw IllegalArgumentException("--pipe does not take a value")
                    }
                    pipe = true
                }

                "--font" -> {
                    val path = Path.of(value())
                    require(Files.isRegularFile(path)) { "font is not a regular file: $path" }
                    font = path
                }

                "--cols" -> {
                    columns = positiveInt(value(), "--cols")
                }

                "--rows" -> {
                    rows = positiveInt(value(), "--rows")
                }

                "--theme" -> {
                    themeName = value()
                }

                "--term" -> {
                    term = nonBlank(value(), "--term")
                }

                "--colorterm" -> {
                    colorTerm = nonBlank(value(), "--colorterm")
                }

                "--cwd" -> {
                    val path = Path.of(value())
                    require(Files.isDirectory(path)) { "cwd is not a directory: $path" }
                    workingDirectory = path
                }

                "--env" -> {
                    val assignment = value()
                    val separator = assignment.indexOf('=')
                    require(separator > 0) { "--env expects KEY=VALUE: $assignment" }
                    environment[assignment.substring(0, separator)] = assignment.substring(separator + 1)
                }

                else -> {
                    throw IllegalArgumentException("unknown option: $argument")
                }
            }
            index++
        }

        require(commandStart >= 0) { "missing -- before the child command" }
        require(commandStart < args.size) { "missing child command after --" }
        val command = args.copyOfRange(commandStart, args.size).toList()
        require(command.isNotEmpty()) { "missing child command after --" }

        return LaunchConfig(
            pipe = pipe,
            font = font,
            columns = columns,
            rows = rows,
            theme = Theme.named(themeName),
            term = term,
            colorTerm = colorTerm,
            workingDirectory = workingDirectory,
            environmentOverrides = environment,
            command = command,
        )
    }

    fun usage(): String =
        """
        usage: terminal-window-cmdline [--pipe] [options] -- COMMAND [ARG...]

        Runs COMMAND in a PTY by default, parses its output, and displays it.

        options:
          --pipe                 force non-interactive pipe mode
          --font PATH            bitmap font (.twf); uses the bundled font by default
          --cols N               initial columns (default $DEFAULT_COLUMNS)
          --rows N               initial rows (default $DEFAULT_ROWS)
          --theme NAME           ${Theme.names.joinToString()}
          --term VALUE           child TERM (default $DEFAULT_TERM)
          --colorterm VALUE      child COLORTERM (default $DEFAULT_COLORTERM)
          --cwd PATH             child working directory
          --env KEY=VALUE        child environment override; repeatable
          --help                 show this help
          --version              show the version
        """.trimIndent()

    private fun positiveInt(
        raw: String,
        option: String,
    ): Int {
        val value = raw.toIntOrNull() ?: throw IllegalArgumentException("$option expects an integer: $raw")
        require(value > 0) { "$option must be positive: $value" }
        return value
    }

    private fun nonBlank(
        raw: String,
        option: String,
    ): String {
        require(raw.isNotBlank()) { "$option must not be blank" }
        return raw
    }
}
