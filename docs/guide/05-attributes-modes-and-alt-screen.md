# 05 — Attributes, modes, queries, and alternate screen

**Phase:** core · **Depends on:** 04 · **Blocks:** 06

## Goal

Add SGR/color, attributes, mode state, terminal responses, DECRQSS, and
alternate-screen behavior. This is the T1a terminal-state layer; T1b/T2
sequences are not implemented.

## Context budget

Read: `GUIDE.md`, this file, ADR-0005, ADR-0007, `docs/vt-support.md` T0/T1a,
and the libvterm `14state_encoding`, `15state_mode`, `18state_termprops`,
`25state_input`, `26state_query`, `27state_reset` tests. Do not read sibling
sections. Expected output: attributes, colors, palette, modes, state, responder,
alt screen, tests.

**Split seam:** if this section exceeds budget, split at “SGR/color/attributes”
versus “modes/queries/alt screen”.

## Deliverables

- `.../attr/Attributes.kt`, `.../attr/Color.kt`, `.../attr/Palette.kt`
- `.../state/Modes.kt`, `.../state/TerminalState.kt`
- `.../state/Responder.kt`, `.../state/AlternateScreen.kt`
- `.../state/Sgr.kt`, `.../state/DecRqm.kt`, `.../state/DecRqss.kt`
- `vt/src/test/kotlin/.../state/*Test.kt`

## Steps

1. SGR parser: semicolon and colon forms; `38/48/58` with `5` and `2`; defaults
   `39/49/59`; 16/256/truecolor; `0` resets attributes and colors.
2. Attributes: bold, faint, italic, underline style `4:0`–`4:5`, blink,
   reverse, invisible, strike, double underline; reset codes `22`–`25`,
   `27`–`29`.
3. Store underline color and style; do not render them.
4. Palette: 256 entries; default 16 ANSI + 216 cube + 24 gray; default fg/bg.
5. Modes: IRM, LNM, DECCKM, DECSCNM, DECOM, DECAWM, DECTCEM, DECNKM, DECBKM,
   mouse 9/1000/1002/1003/1006, focus 1004, paste 2004, sync 2026.
6. DECSET/DECRST update state; unknown private modes are ignored.
7. DECRQM/DECRPM: `0` unknown, `1` set, `2` reset; ANSI and private forms.
8. DSR 5/6; DA1 conservative `CSI ? 1 ; 2 c`.
9. DECRQSS for SGR, DECSTBM, DECSCUSR; invalid query returns `DCS 0 $ r ST`.
10. Alternate screen 47/1047/1048/1049; save/restore cursor; 1049 clears alt
    screen on entry. Keep separate kitty stacks per screen.
11. Synchronized output sets a flag; the app may coalesce frames. No renderer
    change in this section.
12. DECSTR soft reset; RIS hard reset.
13. Tests: every SGR code, color form, mode, DECRQM, DECRQSS, alt screen
    transition.

## Acceptance

- [x] 16/256/truecolor SGR produce the expected `Color` values.
- [x] `38:2::r:g:b` and `38;2;r;g;b` are equivalent.
- [x] `SGR 0` resets attributes and colors; `22`–`29` reset only their targets.
- [x] Underline styles `4:0`–`4:5` and `58` are stored but not rendered.
- [x] `DECRQM` returns 0/1/2 correctly for supported and unknown modes.
- [x] DA1 is `CSI ? 1 ; 2 c`; it does not advertise sixel/locator.
- [x] `DECRQSS` SGR/DECSTBM/DECSCUSR round-trips.
- [x] `1049` saves cursor, clears alt screen, and restores on exit.
- [x] `47`/`1047`/`1048` follow xterm semantics.
- [x] T1b/T2 sequences are not claimed or half-implemented.

## Pitfalls

- `SGR 0` also clears hyperlinks (when added later).
- Colon and semicolon color forms are not interchangeable after a colon.
- Unknown SGR parameters are ignored, not errors.
- Do not advertise features in DA1 that are not implemented.
- Alternate screen has its own cursor, margins, and tab stops.
- DECSCUSR shape is stored; projection still draws a block cursor.

## Plan seed

> Plan section 05 only: implement SGR/color/attributes, palette, ANSI/DEC modes,
> DECRQM/DECRPM, DSR/DA1, DECRQSS, and alternate screen for T0/T1a. Store
> underline styles but do not render them. No input or PTY.
