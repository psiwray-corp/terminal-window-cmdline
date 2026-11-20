# 11 — Documentation, packaging, and release

**Phase:** delivery · **Depends on:** 10 · **Blocks:** none

## Goal

A fresh clone can build, run, and understand the project. Ship a
platform-specific application distribution with the correct native
dependencies. Do not promise native-image, Maven Central, or features that v1
does not implement.

## Context budget

Read: `GUIDE.md`, this file, ADR-0012, `docs/vt-support.md`,
`docs/compatibility.md`, `docs/roadmap.md`, and `terminal-window`
`docs/guide/21`–`24` plus release files. Do not read sibling sections. Expected
output: final docs, jar/distribution, release notes.

## Reuse

Copy and adapt:

- `terminal-window/docs/guide/21-documentation.md`
- `terminal-window/docs/guide/24-release-runbook.md`
- `terminal-window/CONTRIBUTING.md`, `CHANGELOG.md`, `README.md` structure

Do not copy Maven Central, GPG signing, or Portal publication files.

Adapt artifact names, package roots, module list, and distribution strategy.

## Deliverables

- Final `README.md` with install, quickstart, pipe vs PTY, limitations.
- Final `CHANGELOG.md`, `CONTRIBUTING.md`, `docs/*` cross-links.
- `:app` jar with `Main-Class`.
- A platform-specific distribution/launcher with host LWJGL natives and JVM
  flags.
- Release checklist for the standalone distribution.
- `docs/release/` runbook.

## Steps

1. Write README command examples against the executable; every command block
   must be runnable or marked `text`.
2. Document the pipe/PTY distinction, `isatty`, and the lossy projection.
3. Document T0/T1a support and link `docs/vt-support.md`; do not claim T1b/T2.
4. Build the app jar; set `Main-Class`; keep `Automatic-Module-Name`.
5. Create a distribution with LWJGL host natives and a launcher that passes
   `--enable-native-access=ALL-UNNAMED`, `-Djava.awt.headless=true`, and
   `-XstartOnFirstThread` on macOS.
6. Keep `:vt`/`:pty` internal and publish no Maven artifacts. Configure CI to
   create the forge release and attach the platform application distributions.
7. Keep native-image out of v1; link `docs/roadmap.md`.
8. Run a fresh-clone build and the README quickstart.
9. Update `GUIDE.md` progress and all section checkboxes.

## Acceptance

- [x] A fresh clone builds with `./gradlew build` using only README
      instructions.
- [x] Every README code block compiles or is marked `text`.
- [x] `./gradlew :app:run --args="--pipe ..."` works from the documented
      command.
- [x] The application distribution runs with the documented native/LWJGL
      setup.
- [x] macOS launcher includes `-XstartOnFirstThread`.
- [x] CI creates one forge release on `v*` tags with one application
      distribution per build host and `SHA256SUMS`.
- [x] The docs state that v1 has no scrollback, selection, images, or
      native-image.
- [x] `docs/vt-support.md`, `docs/compatibility.md`, and `docs/roadmap.md` are
      consistent with the code.
- [x] All internal links resolve.
- [x] Release artifacts contain no debug-only profile.
- [x] `GUIDE.md` progress is complete.

## Pitfalls

- Do not describe the project as a full xterm; v1 is T0 + T1a.
- Do not promise native-image or installers.
- Do not show `--pty` as if it works when only pipe mode is built.
- Do not omit host LWJGL natives from the distribution; users will hit
  `UnsatisfiedLinkError`.
- Do not publish `:vt`/`:pty`; they are internal modules.
- Do not add Maven Central, signing, or Portal machinery.
- Do not copy the `terminal-window` v1 notice.

## Plan seed

> Plan section 11 only: finalize README/CHANGELOG/CONTRIBUTING/docs, build
> platform application distributions with host LWJGL natives and JVM flags,
> publish them from CI as forge release assets, and document the standalone
> release. No Maven Central.
> Native-image deferred.
