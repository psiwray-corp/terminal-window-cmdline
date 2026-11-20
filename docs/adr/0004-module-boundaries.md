# ADR-0004: Module boundaries

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

The project combines three concerns that change for different reasons:

1. VT parsing and screen state, which are pure logic and heavily tested.
2. Platform process/PTY creation, which is native and OS-specific.
3. Window rendering and CLI, which depend on `terminal-window`.

Mixing them would force parser users to load FFM/LWJGL and would make headless
testing harder.

## Decision

We will use three production modules:

| Module | Package | Responsibility | May depend on |
|---|---|---|---|
| `:vt` | `...terminal_window_cmdline.vt` | UTF-8, parser, screen, modes, input encoder, responses | Kotlin stdlib only |
| `:pty` | `...terminal_window_cmdline.pty` | `ProcessSession`, pipe, POSIX PTY, ConPTY, lifecycle | Kotlin stdlib, FFM |
| `:app` | `...terminal_window_cmdline.app` | `terminal-window` adapter, CLI, frame loop | `:vt`, `:pty`, `terminal-window` |

Rules:

1. `:vt` has no LWJGL, `terminal-window`, AWT, FFM, or platform classes.
2. `:pty` exposes bytes and lifecycle; it does not import screen or parser
   types.
3. `:app` is the only production module that imports `org.lwjgl.*` through
   `terminal-window`; `:benchmarks` may import `terminal-window` only for
   projection measurement.
4. `:benchmarks` may use `:vt` internals for measurement.
5. No module cycles.
6. Public API is minimized; `internal` is the default in `:app`.

## Consequences

### Positive

- Parser tests run headless on any OS.
- Platform backends can be replaced without touching the parser.
- The projection layer is the only place that knows about `TerminalMatrix`.

### Negative

- More Gradle modules and build configuration.
- A change that crosses all three layers needs three compilations.
- Shared value types must be duplicated or placed carefully; `:pty` must not
  depend on `:vt` just to reuse `TerminalSize`.

### Neutral

- `TerminalSize` lives in `:pty` or a tiny shared package; `:app` maps between
  the two representations.
- If only one artifact is published, modules remain internal.

## Alternatives

| Alternative | Why rejected |
|---|---|
| One module | Parser users load native/window code; headless tests are harder. |
| `:vt` depends on `:pty` | Reverses the dependency; parser would know about processes. |
| `:pty` depends on `:vt` | Process layer would know screen types for no benefit. |
| Put adapter in `:vt` | Pulls `terminal-window`/LWJGL into the pure module. |

## References

- ADR-0001 for artifacts.
- ADR-0010 for threading.
