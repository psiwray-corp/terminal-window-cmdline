# ADR-0009: Process and PTY model

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

An interactive terminal cannot use plain `ProcessBuilder` pipes: `isatty()` is
false, there is no line discipline, no job control, no window size, no
`SIGWINCH`, and no `/dev/tty`. Pipes are still useful for non-interactive
commands and for testing the parser.

Java has no PTY API. JDK 25 FFM can call the POSIX and Windows native APIs. The
project requires all three platforms and a pure-FFM policy: no JNA, no native
helper, no `forkpty`.

## Decision

We will expose one `ProcessSession` interface:

```text
isTty: Boolean
read(buffer, offset, length): Int
write(bytes, offset, length)
resize(size): Boolean
requestInterrupt()
requestTerminate()
exit: ProcessExit?
close()
```

Three backends:

| Backend | Platform | Mechanism |
|---|---|---|
| `PipeSession` | All | `ProcessBuilder`, three streams, `isTty = false` |
| `PosixPtySession` | Linux, macOS | FFM `openpty`/`posix_openpt`, `posix_spawn` with `SETSID`, slave dup2, `TIOCSWINSZ`, `waitpid` |
| `ConPtySession` | Windows 10 1809+ | FFM `CreatePseudoConsole`, `STARTUPINFOEXW`, `CreateProcessW`, `ResizePseudoConsole`, `ClosePseudoConsole` |

Rules:

1. Pipe mode is explicit (`--pipe`). A missing PTY/ConPTY fails with a
   diagnostic; it never silently falls back.
2. No JNA, no native helper, no `forkpty`. POSIX uses `posix_spawn` so no JVM
   code runs in a forked child.
3. The child receives the slave/console as fd 0/1/2, so `isatty()` is true.
4. POSIX sets `IUTF8` and the initial window size; resize uses `TIOCSWINSZ`.
5. Windows uses UTF-16 command lines/environment blocks and UTF-8 VT I/O.
6. Windows has no POSIX signals: Ctrl-C is normally `0x03` on input; hard
   termination is `TerminateProcess` or closing the pseudo console.
7. `ClosePseudoConsole` runs only after the output is drained; otherwise it can
   deadlock.
8. macOS is pure FFM. A CI spike must prove that `posix_spawn` + `SETSID` +
   slave dup2 establishes a controlling terminal. If it does not, macOS either
   ships with degraded job control or the pure-FFM constraint is revisited.
9. Windows requires Windows 10 1809+; `CreatePseudoConsole` is resolved
   dynamically and its absence is diagnosed.

The reader, writer, and process watcher are separate threads; the screen model
never sees a platform handle.

## Consequences

### Positive

- One API for pipe, PTY, and ConPTY.
- Pipe mode is simple, cross-platform, and useful for tests.
- FFM avoids JNA and per-platform helper binaries.
- The child sees a real terminal device on all supported platforms.

### Negative

- Windows struct layouts are hand-written and need Windows CI.
- macOS controlling-terminal behavior is a spike, not a guarantee.
- ConPTY close/drain semantics are easy to get wrong.
- Pipe mode cannot run interactive programs, by design.

### Neutral

- `resize` returns false for pipe mode.
- `requestInterrupt` maps to input `0x03` on Windows and a signal on POSIX.
- The interface may grow a `isClosed`/`exit` snapshot but not platform types.

## Alternatives

| Alternative | Why rejected |
|---|---|
| `ProcessBuilder` pipes only | No TTY semantics; `vim`/`top`/`sudo` fail or degrade. |
| JNA | Extra dependency; hides layouts. |
| JNI/native helper | Per-platform artifacts and packaging; violates pure FFM. |
| `forkpty` via FFM | JVM code would run in a forked child; unsafe. |
| Separate `Pty` and `Process` abstractions | Duplicates lifecycle and resize; one interface with capabilities is simpler. |
| Silent pipe fallback | Users discover broken interactivity late. |

## References

- POSIX `openpty(3)`, `posix_spawn(3)`, `termios(3)`.
- Microsoft, “Creating a Pseudoconsole session”.
- Alacritty `tty/unix.rs`, `tty/windows/conpty.rs`.
- ADR-0010 for threading.
