# Performance, limits, and fuzzing

The parser and screen model are measured with a plain Kotlin `main` in
`:benchmarks`; there is no JMH dependency and no timing threshold in CI.
Allocation and timing are reported for the current host, not promised as
hardware-independent guarantees.

## Method

- JDK 25, Kotlin 2.4.21, Gradle 9.9.1.
- Parser scenarios: 2 warm-up passes, 3 measured passes, 1 MiB input each.
- Screen scenarios: 2 warm-up frames, 200 measured frames.
- Projection scenarios: 2 warm-up frames, 1,000 measured frames.
- Parser and screen timing uses `System.nanoTime`; allocation uses
  `com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes` on the
  measured thread.
- Screen scenarios start each frame with `CSI H` and fill exactly one grid, so
  the measurement is not dominated by repeated scrolling.
- `scroll-240x67` deliberately measures a full screen of line feeds, including
  row copies, as a separate case.
- The projection benchmark fills the screen, then repeatedly runs the production
  `ScreenProjection.project` path into a pre-allocated `TerminalMatrix`.
- No benchmark is part of `./gradlew build` or the default test suite.

Run the full report:

```bash
./gradlew :benchmarks:run
```

Run the short report used by the optional script:

```bash
bash ci/benchmark.sh
```

## Parser throughput

| Scenario | Input | MB/s | ns/byte | Alloc B/byte |
|---|---:|---:|---:|---:|
| `parser/ascii` | 1 MiB | 192.23 | 5.202 | 1.008 |
| `parser/utf8` | 1 MiB | 198.36 | 5.041 | 1.008 |
| `parser/sgr` | 1 MiB | 81.04 | 12.339 | 4.573 |
| `parser/cursor` | 1 MiB | 55.94 | 17.875 | 13.760 |
| `parser/osc` | 1 MiB | 40.40 | 24.751 | 1.429 |
| `parser/dcs` | 1 MiB | 71.47 | 13.993 | 6.095 |

Plain ASCII is above the 10 MB/s floor by a wide margin. The escape-heavy
scenarios are slower because they emit more parser events and copies.

## Screen parsing

| Scenario | Cells | ns/frame | Alloc B/frame | M cell/s |
|---|---:|---:|---:|---:|
| `screen/overwrite-80x24` | 1,920 | 303,721.64 | 2,032.32 | 6.32 |
| `screen/overwrite-240x67` | 16,080 | 1,355,278.34 | 16,288.00 | 11.86 |
| `screen/sgr-80x24` | 1,920 | 942,520.68 | 292,901.32 | 2.04 |
| `screen/scroll-240x67` | 16,080 | 1,691,755.57 | 19,692.88 | 9.50 |

A 240×67 overwrite frame takes about 1.36 ms on this host, below the 8 ms
per-tick parse budget. The scroll case is about 1.69 ms, including the row
copies.

The SGR case allocates about 293 KiB per frame. That is not a per-cell object:
it comes from the parser's event snapshots for roughly 1,920 SGR sequences per
frame. The screen model itself uses primitive arrays. Reducing event snapshot
allocation would require a parser event lifetime change and is left as a known
gap, not hidden behind a threshold.

## Projection

| Scenario | Cells | ns/frame | Alloc B/frame | M cell/s |
|---|---:|---:|---:|---:|
| `projection/80x24` | 1,920 | 63,276.36 | 0.472 | 30.34 |
| `projection/240x67` | 16,080 | 127,860.03 | 0.000 | 125.76 |

Projection is allocation-free after warm-up and scales with cell count. The
80×24 sub-byte allocation is measurement noise around class initialization.

## Limits

The normative values are defined once in
`vt/src/main/kotlin/.../vt/limits/VtLimits.kt` and mirrored in
[`vt-support.md`](vt-support.md#limits):

| Limit | Value |
|---|---:|
| CSI/DCS parameters per sequence | 32 |
| Digits per parameter | 10 |
| Intermediate bytes per sequence | 2 |
| OSC/DCS payload | 8,192 bytes |
| Combining code points per cell | 9 |
| Title text | 1,024 bytes |
| Query response queue | 64 KiB |

`LimitTest` exercises each value at `limit - 1`, `limit`, and `limit + 1`;
the parser cases also verify recovery with a following valid sequence.

## Fuzzing

`ParserFuzzTest` uses deterministic seeds and covers:

- random bytes, including truncated UTF-8 and invalid continuations;
- split writes with random chunk sizes;
- repeated `ESC`;
- huge parameter lists and parameter values;
- unterminated OSC and DCS strings;
- nested-looking `ESC` sequences and SOS/PM/APC payloads.

Every event is checked against the parameter, intermediate, and string limits.
The fuzz test then feeds a recovery sequence and requires the parser to process
a valid CSI and OSC again. A hostile stream must not throw, retain unbounded
payloads, or leave the parser stuck.

## Host

| Item | Value |
|---|---|
| CPU | AMD Ryzen 5 5500U (6C/12T) |
| RAM | 7.1 GiB |
| OS | Arch Linux, kernel 7.3.4-arch1-2 |
| JDK | Temurin 25.0.4+7 |
| Gradle | 9.9.1 |
| Measurement date | 2026-11-18 |

These numbers are reproducible with the documented commands on this host.
Absolute values vary with CPU, JVM warm-up, and background load; they are not
CI pass/fail criteria.

## Known gaps

- The parser allocates a snapshot array for each emitted event. Event-heavy
  output therefore allocates even though no per-cell object exists.
- Scroll cost is dominated by copying rows cell-by-cell, including the combining
  side table. The measured 240×67 scroll frame remains within the parse budget.
- No parser or projection threshold is enforced in CI; only the short script is
  available for manual runs.
