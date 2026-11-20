package com.psiwray.organization.terminal_window_cmdline.pty.windows

/**
 * Converts a command argument vector to the single mutable command line that
 * `CreateProcessW` expects.
 *
 * Windows has no argv array at the API boundary. The quoting rules follow the
 * `CommandLineToArgvW`/CRT convention: double quotes group whitespace,
 * backslashes are doubled before an embedded quote, and a trailing backslash is
 * doubled before a closing quote.
 */
internal object CommandLine {
    /** Builds the command line from [command], quoting every element as needed. */
    fun build(command: List<String>): String {
        require(command.isNotEmpty()) { "command must not be empty" }
        val builder = StringBuilder()
        for ((index, argument) in command.withIndex()) {
            if (index > 0) {
                builder.append(' ')
            }
            appendQuoted(builder, argument)
        }
        return builder.toString()
    }

    /** Appends one quoted argument using the Windows CRT rules. */
    fun appendQuoted(
        builder: StringBuilder,
        argument: String,
    ) {
        val quote = argument.isEmpty() || argument.any { it == ' ' || it == '\t' }
        if (quote) {
            builder.append('"')
        }
        var backslashes = 0
        for (character in argument) {
            when (character) {
                '\\' -> {
                    backslashes += 1
                    builder.append('\\')
                }

                '"' -> {
                    repeat(backslashes + 1) { builder.append('\\') }
                    builder.append('"')
                    backslashes = 0
                }

                else -> {
                    backslashes = 0
                    builder.append(character)
                }
            }
        }
        if (quote) {
            repeat(backslashes) { builder.append('\\') }
            builder.append('"')
        }
    }
}
