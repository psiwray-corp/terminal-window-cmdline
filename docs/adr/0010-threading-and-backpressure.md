# ADR-0010: Threading and backpressure

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

`terminal-window` confines `TerminalMatrix` to the window owner thread and
requires GLFW calls on the service thread. The parser/screen model must not be
mutated concurrently with projection. At the same time, a PTY reader must block
on `read`, a writer must block on `write`, and process exit must be observed.

A parser thread would require immutable snapshots or locks for every frame.
The owner-thread model is simpler and matches the renderer's confinement.

## Decision

We will use:

| Thread | Responsibility |
|---|---|
| **Owner thread** | Drain the byte ring, parse with a budget, mutate `TerminalState`, project to `TerminalMatrix`, call `window.frame()` |
| **PTY reader** | Blocking `read` into a bounded byte ring; blocks when full (backpressure) |
| **PTY writer** | Blocking `write` from a bounded queue |
| **Process watcher** | `waitpid`/`WaitForSingleObject`, publishes exit status |
| **Service thread** | GLFW pump only; no parser or screen work |

Rules:

1. The screen model is owner-thread confined. No locks and no volatile cell
   fields.
2. The reader/writer threads copy bytes only; they never touch screen or mode
   state.
3. The byte ring is bounded. If the owner falls behind, the reader blocks and
   the child blocks on write; output is not dropped.
4. The owner parses at most a fixed byte/time budget per frame, then renders.
   Input is drained before parsing so a flood cannot starve keystrokes.
5. Query responses produced by `TerminalState` go to the writer queue.
6. Input events are encoded on the owner thread, then written to the writer
   queue.
7. Shutdown order: stop input, close the PTY/pipe, drain remaining output,
   publish exit, close the window.
8. No `System.exit`; no global signal handlers. The watcher owns only its
   child pid/handle.

The exact ring capacity and per-frame budget are set once in section 07 and
referenced by name.

## Consequences

### Positive

- No screen locks or snapshot allocation per frame.
- Backpressure is explicit; memory stays bounded.
- Input latency is protected by draining input before parsing.
- Matches `terminal-window`'s owner-thread matrix confinement.

### Negative

- A slow renderer slows the child through the ring.
- A single owner thread does parsing and rendering; a very large paste can
  delay a frame.
- The reader/writer/watcher threads must be joined correctly on shutdown.

### Neutral

- Pipe mode uses the same reader/writer shape but no resize/signals.
- The owner thread may be the `terminal-window` owner thread in multi-threaded
  mode.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Parser thread + immutable snapshots | Per-frame allocation and synchronization; screen model becomes shared. |
| Parser on the service thread | Blocks GLFW polling and input. |
| Unbounded queue | Memory growth on output floods. |
| Drop output when full | Corrupts the screen; never acceptable. |
| Parse everything per frame | A large paste can starve input and rendering. |

## References

- `terminal-window` ADR-0004 (threading).
- ADR-0007 (screen model).
- ADR-0009 (process model).
