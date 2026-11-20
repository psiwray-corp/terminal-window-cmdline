package com.psiwray.organization.terminal_window_cmdline.app

/**
 * Runs a child command in a PTY, or in explicit pipe mode, and displays its output.
 *
 * The process exits normally after the child exits and the final frame is
 * drawn. Invalid command lines print a diagnostic and return without calling
 * `System.exit`.
 */
public fun main(args: Array<String>) {
    if (hasOption(args, "--help") || hasOption(args, "-h")) {
        println(CommandLine.usage())
        return
    }
    if (hasOption(args, "--version")) {
        println(CommandLine.PROGRAM_VERSION)
        return
    }

    val config =
        try {
            CommandLine.parse(args)
        } catch (failure: IllegalArgumentException) {
            System.err.println("error: ${failure.message}")
            System.err.println(CommandLine.usage())
            return
        }

    try {
        AppLoop.run(config)
    } catch (failure: Exception) {
        System.err.println("error: ${failure.message ?: failure::class.simpleName}")
    }
}

private fun hasOption(
    args: Array<String>,
    option: String,
): Boolean {
    for (argument in args) {
        if (argument == "--") {
            return false
        }
        if (argument == option) {
            return true
        }
    }
    return false
}
