# 10 — Performance, limits, and fuzzing

**Phase:** quality · **Depends on:** 08, 09 · **Blocks:** 11

## Goal

Prove the parser/screen can handle real terminal output without unbounded
memory or pathological slowdown, and that hostile byte streams cannot crash or
exhaust the process.

## Context budget

Read: `GUIDE.md`, this file, `docs/vt-support.md` limits, ADR-0006, ADR-0007,
ADR-0010, and the `terminal-window` performance style. Do not read sibling
sections. Expected output: benchmarks, fuzz harness, limit tests, report.

## Deliverables

- `benchmarks/src/main/kotlin/.../ParserBenchmark.kt`,
  `ScreenBenchmark.kt`, `ProjectionBenchmark.kt`
- `vt/src/test/kotlin/.../fuzz/ParserFuzzTest.kt`
- `vt/src/test/kotlin/.../limits/LimitTest.kt`
- `docs/performance.md`
- CI job or script for the short benchmark, not a flaky threshold.

## Steps

1. Measure parser throughput (MB/s), ns/byte, and allocations for ASCII,
   UTF-8, SGR-heavy, cursor-heavy, and OSC/DCS streams.
2. Measure screen edits and projection cost for 80×24 and 240×67.
3. Assert steady-state allocation: no per-byte/per-cell objects; arrays reused.
4. Enforce limits from `docs/vt-support.md`: 32 parameters, 10 digits,
   2 intermediates, 8192-byte OSC/DCS, 9 combining marks, 64 KiB response
   queue, 1024-byte title.
5. Fuzz random bytes, split writes, truncated UTF-8, repeated ESC, huge
   parameters, unterminated OSC/DCS, and nested-looking sequences.
6. Malicious-output tests: giant parameter strings, many combining marks,
   repeated mouse/focus queries, DCS image payloads.
7. Ensure parser never throws, never allocates unbounded, and returns to
   `ground` after malformed input.
8. Document measured numbers and the host/toolchain; do not add a CI
   performance threshold unless it is stable.

## Acceptance

- [x] Parser handles at least 10 MB/s of plain ASCII on the CI host (report the
      measured number; this is a floor, not a promise).
- [x] A 240×67 frame stays within the parse budget on the test host.
- [x] No per-byte boxing or per-cell object allocation in the measured paths.
- [x] Every limit has a test at `limit - 1`, `limit`, and `limit + 1`.
- [x] Fuzz corpus runs for a fixed time/iterations without throw or OOM.
- [x] Unterminated OSC/DCS is capped and returns to `ground`.
- [x] `docs/performance.md` records method, numbers, and known gaps.
- [x] No flaky performance gate in the default CI suite.

## Pitfalls

- Optimize after correctness, not before.
- Do not measure with a debug/JIT-warmup-free harness.
- Do not put hardware-specific thresholds in CI.
- Limits must be defined once in `docs/vt-support.md` and referenced, not
  re-declared per module.
- Fuzzing must include split writes, not only one-shot byte arrays.
- A parser that throws on hostile input is a security bug, not a test failure.

## Plan seed

> Plan section 10 only: add parser/screen/projection benchmarks, fuzz and limit
> tests, and `docs/performance.md`. Enforce the documented caps; no flaky CI
> thresholds.
