# ADR-0005: VT scope and support tiers

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

“VT100” is a small protocol that cannot run modern shells and editors without
extensions. Supporting every xterm extension is a long-tail project. The first
release must be a barebones process input/output handler, but it must still run
`sh`, `vim`, `less`, and similar tools credibly.

The owner selected T0 + T1 as the scope ceiling and accepted a lossy
`terminal-window` projection for v1.

## Decision

We will implement four tiers, with v1 limited to T0 and T1a:

| Tier | Meaning | Contents |
|---|---|---|
| **T0** | Protocol core | C0, ESC, CSI cursor/edit/erase, margins, tabs, origin/wrap/insert, SGR, DECSC/DECRC, DECSTBM, DECTCEM, charsets, UTF-8 |
| **T1a** | v1 practical subset | Alternate screen, DECSCUSR, bracketed paste, focus, mouse 1000/1002/1003 + SGR 1006, 256/truecolor, OSC 0/1/2, DECRQM, synchronized output 2026, kitty keyboard, modifyOtherKeys, DECRQSS for SGR/DECSTBM/DECSCUSR |
| **T1b** | Practical extensions, post-v1 | OSC 4/5/8/10/11/12/52/133/7, XTGETTCAP, DA2/DA3, XTVERSION, underline styles rendering, RGBA, 1005/1015/1016, alternate scroll, meta modes |
| **T2** | Later | DECLRMM/DECSLRM, DECIC/DECDC, REP, DECCOLM/DECNCSM, palette stack, completeness queries, scrollback/selection |
| **T3** | Rejected | VT52, DECDHL/DECDWL, Tektronix, Sixel/ReGIS/kitty graphics, iTerm2 images, locator, printing, bidi/shaping, NRC sets, fonts/ligatures, 8-bit C1 |

Rules:

- 7-bit `ESC` forms only. No 8-bit C1 controls.
- UTF-8 is the only text encoding.
- Underline styles `4:0`–`4:5` are stored in the model but not rendered in v1.
- T1b and T2 are recorded in `docs/roadmap.md`; they are not stubs or hidden
  switches.
- T3 sequences are consumed or ignored without crashing and without being
  advertised in DA1.

The normative sequence matrix is `docs/vt-support.md`.

## Consequences

### Positive

- v1 is implementable and testable as one coherent terminal.
- The model has room for later style/color extensions without reshaping cells.
- Applications see a credible `xterm-256color` target.

### Negative

- Visual fidelity is incomplete: no underline, italic, strikethrough, or
  non-block cursor.
- Some applications that expect T1b/T2 features will degrade or complain.
- The tier boundary must be maintained carefully; “just one more sequence” is
  how scope explodes.

### Neutral

- The owner may revise the tier table before 1.0.
- `terminal-window` style/cursor extensions are a separate future project.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Strict VT100 only | Modern shells/editors degrade; alt screen and mouse are missing. |
| Full xterm | Long-tail scope, images, bidi, fonts; not barebones. |
| VT220 without xterm extensions | Still misses alt screen, paste, mouse, truecolor. |
| Implement T1b/T2 as hidden switches | Untested and unclaimed behavior; worse than deferral. |

## References

- ECMA-48, 5th edition (1991).
- DEC VT100 User Guide, chapter 3.
- xterm `ctlseqs`.
- `docs/vt-support.md`, `docs/roadmap.md`.
