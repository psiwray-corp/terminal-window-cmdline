# ADR-0003: Runtime and toolchain

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

The project must run beside `terminal-window`, which targets JDK 25 and Kotlin
2.4.21. The process layer needs native calls on Linux, macOS, and Windows.
JDK 25 includes the stable Foreign Function & Memory API (FFM), so a single
mechanism can replace JNI/JNA and native helper binaries.

The app needs a window, a bitmap font, and GLFW through `terminal-window`.

## Decision

We will use:

| Item | Value |
|---|---|
| Language | Kotlin 2.4.21 |
| JVM target | 25 |
| JDK toolchain | 25 |
| Build | Gradle 9.9.1, Kotlin DSL, version catalog |
| Window library | `com.psiwray.organization:terminal-window:2.0.0` |
| Native access | Java FFM, `--enable-native-access=ALL-UNNAMED` |
| Tests | JUnit 6.1.3, Kotlin test |
| Formatting | Spotless + ktlint official, 120 columns |

Kotlin is compiled with `explicitApi()` in strict mode. Library code uses
`internal` for implementation, never `!!`, and no coroutines. Threads are
explicit and named.

Java interoperability is best effort. `@JvmStatic`/`@JvmOverloads` are added
only where they do not create ambiguity.

### Why FFM and not JNA

FFM is part of JDK 25, needs no extra dependency, and keeps the native boundary
visible in the source. JNA would add a dependency and hide layouts; a native
helper would add a per-platform build artifact. The cost is hand-written
layouts for Windows ConPTY, validated in CI.

### Not included

No coroutines, DI framework, logging facade, reflection-based configuration, or
native helper. No AWT.

## Consequences

### Positive

- One toolchain and one native-access mechanism across all platforms.
- No JNA/native-helper packaging.
- Kotlin null safety and sealed types fit parser events and screen state.

### Negative

- Consumers must run JDK 25+.
- Windows struct layouts are hand-written and must be validated on Windows CI.
- FFM calls are less ergonomic than JNA for pointer-heavy APIs.

### Neutral

- `terminal-window` supplies LWJGL natives; the app must select host
  classifiers.
- Native-image packaging is not promised by this ADR.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Java 25 | Weaker data/sealed types for parser and screen. |
| Kotlin 2.5 RC | Pre-release compiler. |
| JNA | Extra dependency; less control over layouts. |
| JNI/native helper | Per-platform build artifacts and packaging complexity. |
| Coroutines | Threading is explicit and confined; coroutines add scheduling complexity. |

## References

- JEP 454: Foreign Function & Memory API.
- `terminal-window` ADR-0003.
- `GUIDE.md` fixed facts.
