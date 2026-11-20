package com.psiwray.organization.terminal_window_cmdline.vt.state

import com.psiwray.organization.terminal_window_cmdline.vt.limits.VtLimits

/**
 * Bounded queue of terminal responses.
 *
 * The owner thread writes query responses here; the writer thread drains them
 * later. A response is written whole or dropped whole, so a truncated CSI can
 * never reach the child. The default capacity is the normative 64 KiB limit.
 */
public class Responder(
    private val capacity: Int = VtLimits.RESPONSE_QUEUE_BYTES,
) {
    init {
        require(capacity > 0) { "capacity=$capacity" }
    }

    private val buffer: ByteArray = ByteArray(capacity)
    private var readIndex: Int = 0
    private var writeIndex: Int = 0

    /** Number of bytes currently queued. */
    public var available: Int = 0
        private set

    /** Number of responses dropped because the queue was full. */
    public var droppedResponses: Int = 0
        private set

    /** Appends one complete response, or drops it when it does not fit. */
    public fun write(bytes: ByteArray) {
        if (bytes.size > capacity - available) {
            droppedResponses++
            return
        }
        for (byte in bytes) {
            buffer[writeIndex] = byte
            writeIndex = (writeIndex + 1) % capacity
        }
        available += bytes.size
    }

    /**
     * Copies up to [length] queued bytes into [destination].
     *
     * @return the number of bytes copied, zero when the queue is empty.
     */
    public fun read(
        destination: ByteArray,
        offset: Int = 0,
        length: Int = destination.size - offset,
    ): Int {
        require(offset >= 0 && length >= 0 && offset <= destination.size - length) {
            "offset=$offset length=$length size=${destination.size}"
        }
        val copied = minOf(length, available)
        for (index in 0 until copied) {
            destination[offset + index] = buffer[readIndex]
            readIndex = (readIndex + 1) % capacity
        }
        available -= copied
        return copied
    }

    /** Removes all queued bytes without changing the drop counter. */
    public fun clear() {
        readIndex = 0
        writeIndex = 0
        available = 0
    }
}
