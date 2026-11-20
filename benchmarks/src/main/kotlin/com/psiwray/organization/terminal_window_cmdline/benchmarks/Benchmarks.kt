package com.psiwray.organization.terminal_window_cmdline.benchmarks

/**
 * Runs the parser, screen, and projection benchmarks.
 *
 * The default protocol is two warm-up passes and three measured passes. Use
 * `--smoke` for a short CI-friendly run; it changes iteration counts only, not
 * the measured code or thresholds.
 */
public fun main(args: Array<String>) {
    val smoke = args.contains("--smoke")
    val warmup = if (smoke) 1 else 2
    val iterations = if (smoke) 2 else 3
    val screenFrames = if (smoke) 50 else 200
    val projectionFrames = if (smoke) 100 else 1_000

    val byteResults = mutableListOf<ByteBenchResult>()
    ParserBenchmark.run(warmup, iterations, byteResults)
    BenchSupport.printByteResults("parser", byteResults)

    val screenResults = mutableListOf<FrameBenchResult>()
    ScreenBenchmark.run(warmup, screenFrames, screenResults)
    BenchSupport.printFrameResults("screen parse", screenResults)

    val frameResults = mutableListOf<FrameBenchResult>()
    ProjectionBenchmark.run(warmup, projectionFrames, frameResults)
    BenchSupport.printFrameResults("projection", frameResults)
}
