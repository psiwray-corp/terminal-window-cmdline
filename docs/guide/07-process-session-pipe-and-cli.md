# 07 — ProcessSession, pipe mode, adapter, and CLI

**Phase:** runtime · **Depends on:** 06 · **Blocks:** 08, 09

## Goal

The first runnable vertical slice: a `--pipe` app that runs a command, parses
its output, and displays it in `terminal-window`. No PTY yet; the same adapter
will be reused by POSIX/ConPTY.

## Context budget

Read: `GUIDE.md`, this file, ADR-0009, ADR-0010, ADR-0011,
`docs/vt-support.md`, and the `terminal-window` API/examples. Do not read sibling
sections. Expected output: process interface, pipe backend, projection, CLI,
app loop, tests.

**Split seam:** if this section exceeds budget, split at “ProcessSession +
PipeSession” versus “adapter + CLI”.

## Deliverables

- `pty/src/main/kotlin/.../ProcessSession.kt`, `ProcessExit.kt`,
  `TerminalSize.kt`
- `pty/src/main/kotlin/.../PipeSession.kt`
- `app/src/main/kotlin/.../ScreenProjection.kt`
- `app/src/main/kotlin/.../TerminalWindowAdapter.kt`
- `app/src/main/kotlin/.../CommandLine.kt`, `AppLoop.kt`, `Main.kt`
- `app/src/test/kotlin/.../*Test.kt`
- A small `.twf` font resource for the app/tests, or a required `--font` path.

## Steps

1. Define `ProcessSession`: `isTty`, blocking `read`, `write`, `resize`,
   `requestInterrupt`, `requestTerminate`, `exit`, `close`.
2. `PipeSession` via `ProcessBuilder`; merge stderr into stdout to preserve
   terminal-like ordering; `isTty = false`; `resize` returns false.
3. Implement `ScreenProjection` from `ScreenView` to `TerminalMatrix`:
   reverse/bold/dim/invisible, wide/continuation, base-only combining, block
   cursor via reverse. Drop underline/italic/strikethrough/hyperlink.
4. Implement `AppLoop`:
   - service main thread owns GLFW and runs `runEventLoop`;
   - owner thread acquires the window context and runs drain/parse/project/
     frame;
   - reader/writer/watcher threads created here;
   - parse budget per frame (bytes/time), input drained first.
5. CLI: command and args after `--`, `--pipe`, `--font`, `--cols`, `--rows`,
   `--theme`, `--term`, `--colorterm`, `--cwd`, `--env`.
6. Child environment defaults: `TERM=xterm-256color`, `COLORTERM=truecolor`,
   `TERM_PROGRAM=terminal-window-cmdline`, `TERM_PROGRAM_VERSION`.
7. On child exit: drain remaining output, then close the window or show exit
   status. No `System.exit`.
8. Tests: projection mapping, CLI validation, pipe read/write/exit, a headless
   parser→screen→matrix case.

## Acceptance

- [x] `./gradlew :app:run --args="--pipe --font <font> -- sh -c 'printf hello'"` shows `hello`.
- [x] Pipe mode reports `isTty = false`; resize is rejected without crashing.
- [x] Output ordering matches the child (stderr merged).
- [x] Child exit closes the app cleanly; no orphan process.
- [x] Projection maps reverse, bold, invisible, wide, and block cursor.
- [x] Underline/italic/strikethrough are stored in `:vt` but absent from the
      matrix.
- [x] No `terminal-window` source or API changes.
- [x] `TERM`/`COLORTERM` defaults are visible to the child.
- [x] No import of `:pty` platform classes into `:vt`.

## Pitfalls

- Do not silently use pipes for `--pty`; pipe mode is explicit.
- Do not parse unbounded output in one frame; use the budget.
- Do not call `System.exit`; close the window and let `main` return.
- Do not write `TerminalMatrix` from a reader thread.
- Do not promise PTY behavior in pipe mode; `isatty` is false.
- Do not use AWT for clipboard or fonts.

## Plan seed

> Plan section 07 only: one `ProcessSession`, `PipeSession`, lossy
> `ScreenProjection`, `terminal-window` adapter, CLI, and a runnable `--pipe`
> app. Multi-threaded terminal-window mode; bounded parse budget; no PTY/ConPTY.
