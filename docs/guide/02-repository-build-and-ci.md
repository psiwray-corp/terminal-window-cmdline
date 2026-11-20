# 02 — Repository, build, quality gates, and CI

**Phase:** foundations · **Depends on:** 01 · **Blocks:** 03

## Goal

A clone-and-build skeleton: Gradle wrapper, five modules, version catalog,
formatting, documentation build, shared CI scripts, and a GitHub/Forgejo
workflow matrix. `./gradlew build` passes with zero production classes.

## Context budget

Read: `GUIDE.md`, this file, `terminal-window` `docs/guide/02`, `03`, `04`,
`05`, and the build/CI files named below. Do not read sibling sections.
Expected output: build files, CI scripts, workflows, hygiene files.

## Reuse

Copy and adapt:

- `terminal-window/gradlew`, `gradlew.bat`, `gradle/wrapper/*`.
- `terminal-window/settings.gradle.kts`, root `build.gradle.kts`,
  `gradle.properties`, `gradle/libs.versions.toml`.
- `terminal-window/library/build.gradle.kts` Spotless, Dokka, test-suite, and
  manifest blocks.
- `terminal-window/.editorconfig`, `.gitattributes`, `.gitignore`.
- `terminal-window/ci/*.sh`, `.github/workflows/build.yml`,
  `.forgejo/workflows/build.yml`.

Adapt artifact/module names, package roots, OS matrix, and test tags.

## Deliverables

- `gradlew`, `gradlew.bat`, `gradle/wrapper/*` with pinned checksum.
- `settings.gradle.kts` with `:vt`, `:pty`, `:app`, `:benchmarks`.
- Root `build.gradle.kts`, `gradle.properties`.
- `gradle/libs.versions.toml`.
- Module build files for `:vt`, `:pty`, `:app`, `:benchmarks`.
- `.editorconfig`, `.gitattributes`, `.gitignore`.
- `ci/check-jdk.sh`, `ci/build.sh`, `ci/full-test.sh`, `ci/integration-test.sh`,
  `ci/install-gl-deps.sh` adapted for three OSes.
- `.github/workflows/build.yml`, `.forgejo/workflows/build.yml`.
- `README.md` build section updated.

## Steps

1. Copy the wrapper and verify `distributionSha256Sum`.
2. `settings.gradle.kts`: root name `terminal-window-cmdline`; include the four
   modules; `mavenCentral()`.
3. Version catalog: Kotlin 2.4.21, `terminal-window` 2.0.0, JUnit 6.1.3,
   Dokka, ktlint, Spotless.
4. `:vt`: Kotlin JVM, `explicitApi()`, no LWJGL/terminal-window dependency.
5. `:pty`: Kotlin JVM + FFM; no `:vt` dependency.
6. `:app`: depends on `:vt`, `:pty`, `terminal-window`; `application` plugin;
   `Main-Class`; native-access and macOS flags from the shared launcher
   function.
7. `:benchmarks` depends on the public modules.
8. Copy Spotless/ktlint, Dokka, unit/integration test suites, long-test tags.
9. CI: Linux build + Xvfb GL; macOS build + PTY; Windows build + ConPTY.
   Shared logic in `ci/*.sh`; YAML only selects runners.
10. Run `./gradlew build`; delete any placeholder production class.

## Acceptance

- [x] `./gradlew projects` lists `:`, `:vt`, `:pty`, `:app`, `:benchmarks`.
- [x] `./gradlew build` succeeds on a clean clone.
- [x] `./gradlew -q javaToolchains` shows JDK 25.
- [x] `:vt` has no LWJGL/`terminal-window`/FFM dependency.
- [x] `:pty` has no `:vt` dependency.
- [x] `:app` is the only production module with `terminal-window`; the
      benchmark module has it only for projection measurement.
- [x] `./gradlew spotlessApply check` passes.
- [x] CI scripts run on Linux, macOS, and Windows; Linux GL job uses Xvfb.
- [x] No build output or IDE metadata is tracked.
- [x] `jar --describe-module` reports the intended automatic module names.

## Pitfalls

- Do not copy `terminal-window` artifact names or package roots.
- Do not put `terminal-window` in `:vt` or `:pty`.
- Do not add JNA or a native helper.
- Keep `--enable-native-access=ALL-UNNAMED`; macOS needs
  `-XstartOnFirstThread`.
- Do not duplicate CI gates in YAML; call `ci/*.sh`.
- Configuration cache is enabled; keep build logic compatible.

## Plan seed

> Plan section 02 only: bootstrap a Gradle 9.9.1 multi-project repo with
> Kotlin 2.4.21, JDK 25, modules `:vt`/`:pty`/`:app`/`:benchmarks`,
> Spotless/Dokka, and Linux/macOS/Windows CI. Copy `terminal-window` 02–05 and
> adapt names, OS matrix, and PTY tests. No production code.
