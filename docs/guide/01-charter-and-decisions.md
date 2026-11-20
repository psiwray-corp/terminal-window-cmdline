# 01 — Charter, decisions, and support matrix

**Phase:** decisions · **Depends on:** — · **Blocks:** all

## Goal

Lock identity, scope, module boundaries, and the decisions every later section
cites. No build files, no Kotlin, no implementation code.

## Context budget

Read: `GUIDE.md`, this file, `docs/adr/0000-template.md`, and the referenced
`terminal-window` docs. Do not read sibling sections. Expected output: ADRs,
architecture, compatibility, VT support matrix, roadmap, and guide updates.

## Deliverables

- `docs/adr/0000-template.md` (already created).
- `docs/adr/0001-artifact-identity.md` through `0012-packaging-and-release.md`.
- `docs/architecture.md`.
- `docs/compatibility.md`.
- `docs/vt-support.md`.
- `docs/roadmap.md`.
- `GUIDE.md` with 11 sections and progress tracker.
- `AGENTS.md`.
- `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `LICENSE`.

## Steps

1. **Identity.** Fix group, artifacts, version `1.0.0`, package roots,
   automatic module names, repository, and modules. Do not reuse
   `terminal-window`'s package root.
2. **License.** MIT. Note libvterm test attribution.
3. **Runtime.** JDK 25, Kotlin 2.4.21, Gradle 9.9.1, JUnit 6.1.3, pure FFM.
4. **Modules.** `:vt` pure, `:pty` platform, `:app` adapter; no cycles.
5. **VT scope.** T0 + T1a in v1; T1b/T2 roadmap; T3 rejected. Record 8-bit C1
   exclusion, zero-width cap 9, underline style storage.
6. **Parser.** Williams state machine, UTF-8 stage, limits.
7. **Screen.** Struct-of-arrays, width/wrap flags, rare-data side table.
8. **Input.** Neutral events, legacy first, kitty/modifyOtherKeys in T1a.
9. **Process.** One `ProcessSession`; pipe/POSIX/ConPTY; pure FFM; Windows 10
   1809+; macOS spike.
10. **Threading.** Owner-thread parser/screen; reader/writer/watcher; bounded
    ring.
11. **Projection.** Lossy `terminal-window` mapping; no core changes.
12. **Packaging.** Jar first; native-image deferred; no Windows SDK needed
    locally.
13. Write the docs from the decisions, not from aspiration. Keep links valid.

## Acceptance

- [x] Every ADR is `Accepted`, has no `TBD`, and cites its primary sources.
- [x] ADR-0005 matches `docs/vt-support.md` exactly.
- [x] ADR-0009 records pipe/PTY/ConPTY, pure FFM, macOS spike, Windows 10 1809+.
- [x] ADR-0010 records owner-thread confinement and backpressure.
- [x] ADR-0011 records the lossy projection and no `terminal-window` changes.
- [x] `docs/architecture.md` has module, object, thread, and pipeline maps.
- [x] `docs/compatibility.md` states the 1.0 policy and v1 gaps.
- [x] `docs/vt-support.md` has T0/T1a/T1b/T2/T3 and exact sequences.
- [x] `GUIDE.md` lists exactly 11 sections and the reuse map.
- [x] `AGENTS.md` keeps the no-commit rule and the context budget.
- [x] No Gradle or Kotlin files were created.

## Pitfalls

- Do not re-derive `terminal-window` bootstrap/release facts; reference them.
- Do not promise v1 scrollback, selection, images, or native-image.
- Do not claim 8-bit C1, VT52, double-width lines, or sixel.
- Do not let T1b/T2 items appear as implemented in the support matrix.
- Do not use a package root that could split with `terminal-window`.
- Do not add code; this section is documentation only.

## Plan seed

> Plan section 01 only. Finalize identity, ADRs, architecture, compatibility,
> VT support matrix, roadmap, GUIDE, and AGENTS for a Kotlin 2.4.21 / JDK 25 /
> pure-FFM terminal processor with modules `:vt`, `:pty`, `:app`. T0+T1a v1;
> T1b/T2 roadmap; T3 rejected. No code.
