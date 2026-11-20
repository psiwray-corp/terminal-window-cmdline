# ADR-0012: Packaging and release

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none
- **Revision:** 2026-11-20 — standalone distribution only; no Maven Central

## Context

The first release is a barebones executable. It depends on `terminal-window`,
which in turn needs LWJGL natives for the host platform. The process layer uses
JDK 25 FFM and needs `--enable-native-access=ALL-UNNAMED` on the command line.

GraalVM native-image is attractive for startup time, but LWJGL/GLFW plus FFM
plus ConPTY make it a substantial compatibility project. It is not required to
prove the VT processor.

## Decision

We will ship one standalone application distribution per build platform:

1. A thin executable jar for `:app` with `Main-Class`.
2. An application distribution containing the runtime dependencies, the host
   LWJGL native classifier, and a launcher.
3. A launcher that supplies:
   - `--enable-native-access=ALL-UNNAMED`;
   - `-XstartOnFirstThread` on macOS;
   - `-Djava.awt.headless=true`.
4. CI-built archives named
   `terminal-window-cmdline-<os>-<arch>-<version>.zip` and `.tar`.

The application is the product. `:vt` and `:pty` remain internal modules and
are not published to Maven Central. `terminal-window` and its LWJGL/JOML
dependencies remain normal Maven dependencies; natives are not shaded into a
platform-neutral jar.

Native-image, jpackage installers, and Maven Central publishing are not part of
v1. There is no signing or Central Portal machinery.

### Windows SDK

No Windows SDK is required on the development host. FFM resolves `kernel32`
symbols at runtime and the layouts/constants are hand-written. Windows CI
(`windows-latest`) supplies the SDK and the execution environment for ConPTY
tests.

## Consequences

### Positive

- The processor is downloaded and run as a normal application.
- No platform-neutral jar with hidden natives.
- No Maven Central signing or portal release pipeline.

### Negative

- The distribution requires a JDK 25 runtime and matching host natives.
- Startup time is ordinary JVM startup.
- Native-image remains an open packaging project.

### Neutral

- The app is distributed as a zip/tar with a launcher script.
- Publishing the internal libraries can be added later without changing the
  app distribution.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Fat jar with all platform natives | Huge and hides the native selection. |
| GraalVM native-image in v1 | LWJGL/FFM/ConPTY compatibility is unproven; not needed to prove the parser. |
| Publish all modules immediately | Adds release/signing overhead before the API is stable. |
| Require a Windows SDK locally | Not needed for FFM; CI is the Windows execution surface. |

## References

- `terminal-window` ADR-0009 (JVM flags) and release runbook.
- ADR-0003 (FFM).
- ADR-0009 (ConPTY).
