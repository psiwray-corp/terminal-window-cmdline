package com.psiwray.organization.terminal_window_cmdline.pty

import java.io.IOException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * Bounded single-producer, single-consumer byte ring.
 *
 * Writers block while the ring is full; readers block while it is empty. This
 * is the backpressure boundary between a blocking child read and the owner
 * thread that parses at a fixed budget. Closing the input side reports EOF
 * after the buffered bytes are drained.
 *
 * @property capacity maximum buffered bytes.
 * @throws IllegalArgumentException if [capacity] is not positive.
 */
public class ByteRing(
    public val capacity: Int,
) {
    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    private val lock: ReentrantLock = ReentrantLock()
    private val notEmpty: Condition = lock.newCondition()
    private val notFull: Condition = lock.newCondition()
    private val buffer: ByteArray = ByteArray(capacity)

    private var readIndex: Int = 0
    private var writeIndex: Int = 0
    private var size: Int = 0
    private var inputClosed: Boolean = false
    private var closed: Boolean = false

    /** Number of bytes currently buffered. */
    public fun available(): Int {
        lock.lock()
        try {
            return size
        } finally {
            lock.unlock()
        }
    }

    /**
     * Appends bytes, blocking while the ring is full.
     *
     * @throws IOException when the ring is closed.
     */
    @Throws(IOException::class)
    public fun write(
        source: ByteArray,
        offset: Int,
        length: Int,
    ) {
        require(offset >= 0 && length >= 0 && offset <= source.size - length) {
            "offset=$offset length=$length size=${source.size}"
        }
        if (length == 0) {
            return
        }
        lock.lock()
        try {
            while (size == capacity) {
                if (closed) {
                    throw IOException("byte ring is closed")
                }
                notFull.awaitUninterruptibly()
            }
            if (closed) {
                throw IOException("byte ring is closed")
            }
            var sourceIndex = offset
            var remaining = length
            while (remaining > 0) {
                val chunk = minOf(remaining, capacity - writeIndex)
                source.copyInto(buffer, writeIndex, sourceIndex, sourceIndex + chunk)
                writeIndex = (writeIndex + chunk) % capacity
                sourceIndex += chunk
                remaining -= chunk
                size += chunk
            }
            notEmpty.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Reads buffered bytes, blocking while the ring is empty.
     *
     * @return number of bytes read, or `-1` after the input side is closed and
     *   the buffered bytes have been drained.
     */
    public fun read(
        destination: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset <= destination.size - length) {
            "offset=$offset length=$length size=${destination.size}"
        }
        if (length == 0) {
            return 0
        }
        lock.lock()
        try {
            while (size == 0) {
                if (inputClosed || closed) {
                    return -1
                }
                notEmpty.awaitUninterruptibly()
            }
            val copied = minOf(length, size)
            var destinationIndex = offset
            var remaining = copied
            while (remaining > 0) {
                val chunk = minOf(remaining, capacity - readIndex)
                buffer.copyInto(destination, destinationIndex, readIndex, readIndex + chunk)
                readIndex = (readIndex + chunk) % capacity
                destinationIndex += chunk
                remaining -= chunk
                size -= chunk
            }
            notFull.signalAll()
            return copied
        } finally {
            lock.unlock()
        }
    }

    /** Marks the input side as complete; buffered bytes remain readable. */
    public fun closeInput() {
        lock.lock()
        try {
            inputClosed = true
            notEmpty.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /** Discards pending input and wakes all blocked readers and writers. */
    public fun close() {
        lock.lock()
        try {
            closed = true
            inputClosed = true
            size = 0
            notEmpty.signalAll()
            notFull.signalAll()
        } finally {
            lock.unlock()
        }
    }
}
