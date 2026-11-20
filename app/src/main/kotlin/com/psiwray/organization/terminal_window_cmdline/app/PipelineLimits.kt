package com.psiwray.organization.terminal_window_cmdline.app

/** Fixed runtime limits for the pipe/app pipeline. */
internal object PipelineLimits {
    /** Reader-to-owner ring capacity in bytes. */
    const val BYTE_RING_CAPACITY: Int = 256 * 1024

    /** Maximum bytes parsed in one owner tick. */
    const val PARSE_BYTE_BUDGET: Int = 64 * 1024

    /** Maximum parse time in one owner tick. */
    const val PARSE_TIME_BUDGET_NANOS: Long = 8_000_000L

    /** Reusable reader chunk size. */
    const val READ_CHUNK_BYTES: Int = 16 * 1024

    /** Number of encoded input/response messages awaiting the writer. */
    const val WRITE_QUEUE_CAPACITY: Int = 256

    /** Exit watcher polling interval. */
    const val WATCH_INTERVAL_MILLIS: Long = 10L

    /** Idle owner-loop delay. */
    const val FRAME_INTERVAL_MILLIS: Long = 8L
}
