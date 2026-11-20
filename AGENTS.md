# AGENTS.md

Instructions for coding agents in this repository. The user owns Git history.

## Git: never commit

This rule overrides every other file. If `GUIDE.md`, a section file, or an
automation prompt says to commit, it means "leave the work for the user"; it
never authorizes an agent commit.

- Do **not** run `git commit`, `git add`, `git stash`, `git reset`,
  `git checkout --`, or `git restore`.
- Do **not** use the agent `commit` namespace.
- Do **not** create a todo item, plan step, or acceptance criterion for
  committing.
- Do **not** amend, rebase, squash, tag, or rename branches.
- Leave all work in the working tree. At the end, report changed paths and a
  suggested commit subject/body; the user commits.
- Read-only Git commands (`status`, `diff`, `log`, `show`) are allowed.
- The branch is `master`. Do not rename it.

## How work is scoped

- `GUIDE.md` is the plan, fixed facts, invariants, and progress tracker.
- Read `GUIDE.md`, then exactly one `docs/guide/NN-*.md`.
- Reuse the `terminal-window` files named in the section. Do not re-derive
  bootstrap, CI, documentation, packaging, or release machinery.
- Implement only that section's deliverables. Do not pre-implement later
  sections; add a private seam if a later section needs a hook.
- A section is done only when every `## Acceptance` box is demonstrated, not
  inferred. Update the section checkboxes and `GUIDE.md` progress entry in the
  same pass, but leave the commit to the user.
- If a user instruction conflicts with the guide, the user wins. Remove the
  stale requirement from the guide and the docs it affects.

## Context budget

- One section per conversation. Do not read sibling section files.
- Target at most 30% of a 1M-token context, including reads, code, tests, and
  review. If a section approaches that ceiling, stop and split at the seam in
  its `## Context budget` block.
- At the end of a section, report the files changed, the acceptance evidence,
  and the next section. The user compacts before continuing.

## Project facts

- App distribution: `terminal-window-cmdline-<os>-<arch>`.
- Libraries: internal `terminal-vt` and `terminal-pty` modules; no Maven
  Central publication.
- Development version: `1.0.0-SNAPSHOT`; release `1.0.0`.
- Modules: `:vt`, `:pty`, `:app`, `:benchmarks`.
- Package roots: `com.psiwray.organization.terminal_window_cmdline.vt`, `.pty`,
  `.app`.
- Automatic module names match the package roots.
- Toolchain: JDK 25, Kotlin 2.4.21, Gradle 9.9.1, JUnit 6.1.3.
- Window dependency: `com.psiwray.organization:terminal-window:2.0.0`, only in
  `:app` (and the benchmark module for projection measurements).
- License: MIT.
- Platforms: Linux, macOS, Windows 10 1809+.
- Process layer: pure Java FFM. No JNA, no native helper, no `forkpty`.
- VT scope: T0 + T1a in v1; T1b/T2 are roadmap; T3 is rejected.
- C1 controls: 7-bit `ESC` forms only. No 8-bit C1.
- Child environment defaults: `TERM=xterm-256color`, `COLORTERM=truecolor`,
  `TERM_PROGRAM=terminal-window-cmdline`.
- Zero-width cap: 9 combining code points per cell.
- Test corpus: libvterm `.test` files, MIT, attribution required.

## Architecture invariants

- `:vt` is pure Kotlin. No LWJGL, `terminal-window`, AWT, FFM, or platform
  classes.
- `:pty` owns all platform process/PTY calls and exposes byte streams plus
  lifecycle. It does not know about screen types.
- `:app` is the only production module that depends on `terminal-window`;
  `:benchmarks` may depend on it only for projection measurements.
- The screen model is owner-thread confined. Reader, writer, and process
  watcher threads publish bytes/exit status only.
- Pipe mode is explicit (`--pipe`). Missing PTY/ConPTY must fail with a
  diagnostic; never fall back silently.
- No `terminal-window` changes in v1. Projection is lossy and documented.
- No `System.exit`, no global signal handlers, no global GLFW/AWT hooks.
- Every external input has a bound: CSI parameters, OSC/DCS length, combining
  marks, hyperlink URL length when added.
- No per-cell objects, no per-byte boxing, no unbounded buffers.

## Kotlin and API conventions

- `explicitApi()` is enabled; every public declaration has explicit visibility
  and types.
- No `!!` in library code. `internal` for implementation.
- No coroutines; threads are explicit and named.
- Immutable value types are `data class`; stateless helpers are `object`.
- Colors at the `terminal-window` boundary are `Int` ARGB. The VT model keeps
  default/indexed/RGB colors separate.
- Public declarations need KDoc.
- Hot paths use indexed loops and primitive arrays.
- `use {}` only for `Closeable`; native handles get explicit `close()`.
- Style: UTF-8, LF, 4 spaces, max line length 120, `ktlint_official`.

## Build and verification

- Always use the wrapper: `./gradlew <task>`.
- `./gradlew build` is the baseline.
- Unit tests are headless. PTY/ConPTY tests are tagged integration and run on
  the matching OS.
- `./gradlew minimumAcceptanceTest` is the default suite.
- Long tests are tagged `long`; `./gradlew fullTest` runs them.
- Verify behavior on the surface that changed. For CLI, run it and report what
  was observed. For ConPTY, the Windows CI runner is the execution surface.
- Do not rerun a check to confirm a failure the user already reported.
- Delete throwaway scripts after use. Tests must catch consumer-visible bugs,
  not wiring or source text.
- Configuration cache is enabled. Keep build logic compatible.

## Documentation

- ADRs in `docs/adr/` are decision records. Do not rewrite an accepted decision
  unless the user changes it; note pre-release revisions in the ADR.
- Keep `docs/compatibility.md`, `docs/vt-support.md`, `docs/roadmap.md`, and
  `README.md` in sync with user-visible changes.
- Every numeric limit is set once in the section that owns it and referenced by
  name.
- Do not promise scrollback, selection, images, or native-image packaging in
  v1.
