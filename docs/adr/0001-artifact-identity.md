# ADR-0001: Artifact identity and coordinates

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none
- **Revision:** 2026-11-20 — distribution-only app; libraries remain internal;
  first release is `1.0.0`

## Context

This is a new sibling project to `terminal-window`. It needs an executable app
and two reusable libraries: a pure terminal parser/screen and a platform
process/PTY layer. The existing `terminal-window` artifact owns the package
root `com.psiwray.organization.terminal_window`; a second artifact must not
create a split package with it.

The first release is `1.0.0`. The application CLI is the compatibility
surface; the internal libraries remain unpublished and have no public API
stability promise.

## Decision

We will distribute a standalone application. We will not publish the app or
the internal libraries to Maven Central.

| Item | Value |
|---|---|
| Group | `com.psiwray.organization` |
| Distribution | `terminal-window-cmdline-<os>-<arch>` |
| Internal libraries | `terminal-vt`, `terminal-pty` |
| Release version | `1.0.0` |
| Development version | `1.0.0-SNAPSHOT` |
| Repository | `psiwray-corp/terminal-window-cmdline` |
| Modules | `:vt`, `:pty`, `:app`, `:benchmarks` |
| Package roots | `com.psiwray.organization.terminal_window_cmdline.vt`, `.pty`, `.app` |
| Automatic module names | same as package roots |
| Language / target | Kotlin 2.4.21, JVM target 25 |
| License | MIT |

`:vt` and `:pty` are separate modules so the parser can be used without the
platform process layer, and the process layer without `terminal-window`.
`:app` depends on both and on `terminal-window:2.0.0`. The modules are not
released independently; publication can be reconsidered after 1.0.

No package in this project is named `com.psiwray.organization.terminal_window.*`;
the app must not merge into the `terminal-window` artifact's package space.

## Consequences

### Positive

- Clear dependency direction: parser → process → app.
- `:vt` is usable in tests and other UIs without LWJGL or a PTY.
- `:pty` can be reused by a different front end.
- Distinct package roots avoid a split package with `terminal-window`.

### Negative

- A standalone JVM distribution requires a JDK 25 runtime and host natives.
- Internal library APIs are not covered by a public stability promise.
- The app has a hard dependency on the `terminal-window` release line.

### Neutral

- `:vt` and `:pty` remain reusable modules without public publication.
- Maven Central publishing can be added later without reshaping modules.
- Native-image packaging is separate.

## Alternatives

| Alternative | Why rejected |
|---|---|
| One artifact for everything | Forces PTY/FFM code on parser users and mixes platform code into a pure model. |
| Reuse `terminal-window`'s package root | Creates a split package and couples two release lines. |
| Publish all modules to Maven Central | The product is an executable; publication adds signing and API-stability overhead with no consumer. |

## References

- `GUIDE.md` fixed facts.
- ADR-0004 for module boundaries.
- ADR-0012 for packaging.
