package com.psiwray.organization.terminal_window_cmdline.benchmarks

import java.lang.management.ManagementFactory

internal data class ByteBenchResult(
    val name: String,
    val bytes: Long,
    val iterations: Int,
    val nanos: Long,
    val allocatedBytes: Long,
) {
    val megabytesPerSecond: Double get() = bytes.toDouble() * iterations / nanos * 1_000.0
    val nanosPerByte: Double get() = nanos.toDouble() / (bytes * iterations)
    val allocatedBytesPerByte: Double get() = allocatedBytes.toDouble() / (bytes * iterations)
}

internal data class FrameBenchResult(
    val name: String,
    val frames: Int,
    val cells: Int,
    val nanos: Long,
    val allocatedBytes: Long,
) {
    val nanosPerFrame: Double get() = nanos.toDouble() / frames
    val allocatedBytesPerFrame: Double get() = allocatedBytes.toDouble() / frames
    val cellsPerSecond: Double get() = cells.toDouble() * frames / nanos * 1_000_000_000.0
}

internal object BenchSupport {
    private val allocationBean =
        ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean

    fun measureBytes(
        name: String,
        input: ByteArray,
        warmup: Int,
        iterations: Int,
        action: () -> Unit,
    ): ByteBenchResult {
        repeat(warmup) { action() }
        val allocatedBefore = allocatedBytes()
        val started = System.nanoTime()
        repeat(iterations) { action() }
        val elapsed = System.nanoTime() - started
        val allocated = allocatedBytes() - allocatedBefore
        return ByteBenchResult(
            name = name,
            bytes = input.size.toLong(),
            iterations = iterations,
            nanos = elapsed,
            allocatedBytes = allocated,
        )
    }

    fun measureFrames(
        name: String,
        frames: Int,
        cells: Int,
        warmup: Int,
        action: () -> Unit,
    ): FrameBenchResult {
        repeat(warmup) { action() }
        val allocatedBefore = allocatedBytes()
        val started = System.nanoTime()
        repeat(frames) { action() }
        val elapsed = System.nanoTime() - started
        val allocated = allocatedBytes() - allocatedBefore
        return FrameBenchResult(
            name = name,
            frames = frames,
            cells = cells,
            nanos = elapsed,
            allocatedBytes = allocated,
        )
    }

    fun printByteResults(
        title: String,
        results: List<ByteBenchResult>,
    ) {
        println(title)
        println("  scenario                       input B      MB/s   ns/byte   alloc B/byte")
        results.forEach { result ->
            val allocation =
                if (result.allocatedBytes < 0) {
                    "n/a"
                } else {
                    "%.3f".format(result.allocatedBytesPerByte)
                }
            println(
                "  %-28s %10d %9.2f %9.3f %13s".format(
                    result.name,
                    result.bytes,
                    result.megabytesPerSecond,
                    result.nanosPerByte,
                    allocation,
                ),
            )
        }
    }

    fun printFrameResults(
        title: String,
        results: List<FrameBenchResult>,
    ) {
        println(title)
        println("  scenario                       cells   ns/frame   alloc B/frame   M cell/s")
        results.forEach { result ->
            val allocation =
                if (result.allocatedBytes < 0) {
                    "n/a"
                } else {
                    "%.3f".format(result.allocatedBytesPerFrame)
                }
            println(
                "  %-28s %7d %10.2f %15s %10.2f".format(
                    result.name,
                    result.cells,
                    result.nanosPerFrame,
                    allocation,
                    result.cellsPerSecond / 1_000_000.0,
                ),
            )
        }
    }

    private fun allocatedBytes(): Long =
        if (allocationBean?.isThreadAllocatedMemorySupported == true) {
            allocationBean.getCurrentThreadAllocatedBytes()
        } else {
            -1L
        }
}
