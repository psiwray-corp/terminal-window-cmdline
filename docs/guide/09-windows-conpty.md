# 09 — Windows ConPTY

**Phase:** platform · **Depends on:** 07 · **Blocks:** 10

## Goal

A Windows backend using pure FFM and ConPTY. The child gets a console, VT output
is parsed normally, resize uses `ResizePseudoConsole`, and the app works on
Windows 10 1809+.

## Context budget

Read: `GUIDE.md`, this file, ADR-0009, ADR-0010, Alacritty
`tty/windows/conpty.rs`, Microsoft “Creating a Pseudoconsole session”, and the
Windows API docs for the listed calls. Do not read sibling sections. Expected
output: FFM bindings, `ConPtySession`, lifecycle, Windows CI tests.

## Deliverables

- `pty/src/main/kotlin/.../windows/WindowsApi.kt` (FFM bindings, layouts)
- `pty/src/main/kotlin/.../windows/ConPtySession.kt`
- `pty/src/main/kotlin/.../windows/CommandLine.kt`,
  `EnvironmentBlock.kt`
- `pty/src/test/kotlin/.../windows/*Test.kt`
- `.github/workflows` Windows job with ConPTY integration tests.

## Steps

1. FFM-link `kernel32`: `CreatePipe`, `CreatePseudoConsole`,
   `ResizePseudoConsole`, `ClosePseudoConsole`, `CreateProcessW`,
   `InitializeProcThreadAttributeList`, `UpdateProcThreadAttribute`,
   `ReadFile`, `WriteFile`, `WaitForSingleObject`, `GetExitCodeProcess`,
   `CloseHandle`, `GetLastError`, `FormatMessageW`.
2. Define `COORD`, `STARTUPINFOW`, `STARTUPINFOEXW`, `PROCESS_INFORMATION`,
   `SECURITY_ATTRIBUTES` layouts with `MemoryLayout`; assert sizes/offsets.
3. Create input/output pipes; `CreatePseudoConsole` with the correct ends.
4. Build a mutable UTF-16 command line and a sorted, double-null environment
   block; `CreateProcessW` with `EXTENDED_STARTUPINFO_PRESENT`,
   `CREATE_UNICODE_ENVIRONMENT`, `PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE`.
5. Close host-side duplicates after spawn; keep the console-side handles.
6. Reader thread: `ReadFile` output pipe into `ByteRing`.
7. Writer thread: `WriteFile` input pipe from the queue.
8. Watcher: `WaitForSingleObject` + `GetExitCodeProcess`.
9. Resize: `ResizePseudoConsole` with `COORD`.
10. Ctrl-C: write `0x03` through the input stream; if needed, detach from any
    current console, attach to the child console, enable processed input, and
    broadcast `CTRL_C_EVENT`; hard terminate: `TerminateProcess`; close: drain
    output, then `ClosePseudoConsole`.
11. Detect `CreatePseudoConsole`; if absent, fail with “Windows 10 1809 or
    newer required”. Pipe mode remains available.
12. Tests on `windows-latest`: `cmd /c echo hello`, console mode, resize,
    Ctrl-C, exit code, drain-before-close.

## Acceptance

- [x] `cmd /c echo hello` output is parsed and displayed.
- [x] Child sees a console; console-aware programs run.
- [x] `ResizePseudoConsole` changes the grid; no deadlock.
- [x] Ctrl-C reaches the child; hard terminate works.
- [x] Exit code is reported; no zombie process.
- [x] `ClosePseudoConsole` runs after output drain; no deadlock.
- [x] FFM layouts match Windows ABI on x64 (and arm64 if CI covers it).
- [x] No Windows SDK is required on the Linux development host.
- [x] No JNA; Windows classes load only on Windows.
- [x] Pipe mode still works if ConPTY is unavailable.

Layout, quoting, environment-block, and non-Windows diagnostic tests run on
Linux. The parser and ConPTY integration tests run on `windows-latest`.

## Pitfalls

- `STARTUPINFOEXW` layout is the highest-risk part; assert offsets.
- The JDK's Windows default symbol lookup covers the C runtime, not
  `kernel32.dll`; load `kernel32.dll` explicitly.
- Use `STARTF_USESTDHANDLES` with null handles to prevent the child from
  inheriting the host's standard handles.
- Close the correct pipe ends after `CreatePseudoConsole`/`CreateProcessW`.
- `ClosePseudoConsole` can emit final output; drain first.
- ConPTY only converts `0x03` to `CTRL_C_EVENT` while processed input is
  enabled; a headless host also needs the attach-and-broadcast fallback.
- `GetConsoleWindow` can return null while the process is still attached to a
  console. Call `FreeConsole` before `AttachConsole` or the latter fails with
  `ERROR_ACCESS_DENIED`.
- `SetConsoleCtrlHandler(NULL, TRUE)` is inherited by child processes. Clear it
  before `CreateProcessW` so a host that ignores Ctrl-C does not silently make
  the ConPTY child ignore it too.
- Windows has no POSIX signals; do not fake `SIGWINCH`/`SIGTERM`.
- Environment blocks are case-insensitive and double-null-terminated.
- Command-line quoting differs from POSIX; implement and test it.
- Do not initialize the Windows FFM bindings on non-Windows.

## Plan seed

> Plan section 09 only: pure-FFM ConPTY backend with pipes, `CreatePseudoConsole`,
> `STARTUPINFOEXW`/`CreateProcessW`, reader/writer/watcher, resize, Ctrl-C,
> close/drain, and Windows CI tests. Windows 10 1809+.
