package com.psiwray.organization.terminal_window_cmdline.vt.limits

/**
 * Normative v1 limits for parser and screen input.
 *
 * These values are mirrored in `docs/vt-support.md`; tests exercise each one at
 * `limit - 1`, `limit`, and `limit + 1`. Keeping them here prevents parser,
 * screen, and response code from drifting apart.
 */
internal object VtLimits {
    /** Maximum CSI/DCS parameter values per sequence. */
    const val CSI_PARAMETERS: Int = 32

    /** Maximum digits in one parameter value. */
    const val PARAMETER_DIGITS: Int = 10

    /** Maximum intermediate bytes per escape sequence. */
    const val INTERMEDIATE_BYTES: Int = 2

    /** Maximum OSC/DCS/APC/PM/SOS payload retained per event. */
    const val STRING_BYTES: Int = 8192

    /** Maximum combining code points attached to one cell. */
    const val COMBINING_CODE_POINTS: Int = 9

    /** Maximum bytes retained for an OSC title. */
    const val TITLE_BYTES: Int = 1024

    /** Capacity of the terminal query response queue. */
    const val RESPONSE_QUEUE_BYTES: Int = 64 * 1024
}
