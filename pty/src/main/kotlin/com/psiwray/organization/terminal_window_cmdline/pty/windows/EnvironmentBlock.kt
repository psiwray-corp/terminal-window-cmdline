package com.psiwray.organization.terminal_window_cmdline.pty.windows

import java.util.Locale
import java.util.TreeMap

/**
 * Builds a sorted, double-null-terminated UTF-16 environment block for
 * `CreateProcessW`.
 *
 * Windows environment variable names are case-insensitive. Overrides replace
 * inherited variables without producing duplicate keys, and the final block is
 * sorted by the case-insensitive key so the child sees a deterministic
 * environment.
 */
internal object EnvironmentBlock {
    /** Builds a UTF-16 code unit block, including the final null terminator. */
    fun build(
        overrides: Map<String, String>,
        parent: Map<String, String> = System.getenv(),
    ): ShortArray {
        val merged = TreeMap<String, Pair<String, String>>()
        for ((key, value) in parent) {
            if (key.startsWith('=')) {
                // Windows keeps hidden per-drive current-directory variables
                // in the environment block; Java callers do not need them.
                continue
            }
            validate(key, value)
            merged[key.uppercase(Locale.ROOT)] = key to value
        }
        for ((key, value) in overrides) {
            validate(key, value)
            merged[key.uppercase(Locale.ROOT)] = key to value
        }

        val units = ArrayList<Short>()
        for ((key, value) in merged.values) {
            appendString(units, key)
            units.add('='.code.toShort())
            appendString(units, value)
            units.add(0)
        }
        units.add(0)
        return units.toShortArray()
    }

    private fun validate(
        key: String,
        value: String,
    ) {
        require(key.isNotEmpty()) { "environment key must not be empty" }
        require('=' !in key) { "environment key must not contain '=': $key" }
        require('\u0000' !in key) { "environment key must not contain NUL" }
        require('\u0000' !in value) { "environment value for $key must not contain NUL" }
    }

    private fun appendString(
        destination: MutableList<Short>,
        value: String,
    ) {
        for (character in value) {
            destination.add(character.code.toShort())
        }
    }
}
