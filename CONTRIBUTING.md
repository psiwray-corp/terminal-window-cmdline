# Contributing

Read [`GUIDE.md`](GUIDE.md) first. Work is organized into 11 sections; each
section is one unit of work and must be completed before the next begins.

## Rules

- JDK 25, Kotlin 2.4.21, Gradle 9.9.1.
- Use the wrapper: `./gradlew`.
- Run `./gradlew spotlessApply check` before proposing a change; `bash ci/build.sh`
  runs the full CI gate.
- Keep `:vt` pure Kotlin: no LWJGL, `terminal-window`, AWT, FFM, or platform
  classes.
- Do not add JNA, a native helper, or a `forkpty` path. The process layer is
  pure FFM.
- Do not change `terminal-window` as part of this project.
- Do not implement deferred T1b/T2/T3 features without updating the roadmap
  and the support matrix.
- Add tests for consumer-visible behavior, not wiring or source text.
- Do not commit on behalf of the user.
- The release product is the platform application distribution; do not add
  Maven Central, signing, or Portal publication machinery.

## Documentation

- Update the owning section, its acceptance checkboxes, and `GUIDE.md`.
- Keep `docs/vt-support.md`, `docs/compatibility.md`, `docs/roadmap.md`, and
  `README.md` consistent with the change.
- Cite the primary specification (ECMA-48, VT100 guide, xterm ctlseqs, kitty,
  Microsoft ConPTY) when behavior is non-obvious.

## License

Contributions are accepted under the MIT license (inbound = outbound).
