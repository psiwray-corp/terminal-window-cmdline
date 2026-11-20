# 04 — Screen, cursor, scrolling, and editing

**Phase:** core · **Depends on:** 03 · **Blocks:** 05

## Goal

A correct cell grid with cursor movement, margins, tabs, insert/replace, erase,
insert/delete, and scrolling. No colors, attributes, or modes beyond those
needed for placement.

## Context budget

Read: `GUIDE.md`, this file, ADR-0007, `docs/vt-support.md` T0 screen/editing,
and the libvterm state/screen tests named below. Do not read sibling sections.
Expected output: cell buffer, screen, cursor/margins/tabs, edit operations,
tests.

## Deliverables

- `vt/src/main/kotlin/.../screen/CellBuffer.kt`
- `vt/src/main/kotlin/.../screen/CellFlags.kt`
- `vt/src/main/kotlin/.../screen/Screen.kt`
- `vt/src/main/kotlin/.../screen/ScreenDamage.kt`
- `vt/src/main/kotlin/.../screen/WidthTable.kt`
- `vt/src/main/kotlin/.../state/TerminalState.kt` (placement subset)
- `vt/src/test/kotlin/.../screen/*Test.kt`
- `vt/src/test/kotlin/.../state/*Test.kt`
- Adapted libvterm `10state_putglyph`, `11state_movecursor`, `12state_scroll`,
  `13state_edit`, `20state_wrapping`, `21state_tabstops`, `22state_save`,
  `28state_dbl_wh` (unsupported cases marked).

## Steps

1. Struct-of-arrays: code points, flags, fg, bg, attrs; side table for rare
   data. Do not allocate a cell object per cell.
2. Width table from UAX #11. Wide char = primary + `WIDE_CHAR_SPACER`;
   ambiguous width defaults to 1.
3. Combining marks attach to the previous base cell; cap 9; no cursor advance.
4. Cursor state: row, column, pending-wrap flag, saved cursor, origin mode,
   margins, tab stops.
5. Movement: CUU/CUD/CUF/CUB/CNL/CPL/CHA/CUP/HVP/VPA/HPA, BS, HT, LF, CR, IND,
   NEL, RI.
6. DECSTBM sets top/bottom margins; cursor homes according to origin mode.
7. Tabs: default every 8; HTS/TBC set/clear; HT/CBT.
8. IRM insert mode shifts the line; replace overwrites.
9. Editing: ICH/DCH/IL/DL/ECH/ED/EL/SU/SD; erased cells use the current
   background (BCE) unless configured otherwise.
10. Mark damage rectangles; full-grid projection can ignore them, but the screen
    keeps them for future partial updates and scrollback.
11. Test wide overwrite, pending wrap, origin bounds, margins, tab reset, and
    BCE.

## Acceptance

- [x] libvterm screen/state tests for movement, scroll, edit, wrapping, tabs,
      save/restore pass.
- [x] A wide character occupies two cells and overwriting either cell clears
      the pair.
- [x] A combining mark joins the preceding cell; the 10th mark is dropped.
- [x] Pending wrap prints at the right edge once, not twice.
- [x] Origin mode bounds CUP to the scrolling region and homes at the top
      margin.
- [x] DECSTBM with invalid margins is ignored.
- [x] Insert mode shifts the line once per printed character, not once per
      UTF-8 byte.
- [x] Erase uses the current background (BCE).
- [x] No per-cell object allocation in steady state.

## Pitfalls

- Pending wrap is not the same as “cursor at last column”.
- Wide-char spacer must be invalidated when either half is overwritten.
- Erasing a wide pair must clear both cells.
- Origin mode changes cursor addressing, not the physical grid.
- Tab stops are per-screen and reset by RIS.
- Do not implement SGR/colors here; this section stores placeholders only.

## Plan seed

> Plan section 04 only: implement the cell grid, width/wrap flags, cursor,
> margins, tabs, insert/replace, erase, insert/delete, and scroll using
> struct-of-arrays. No SGR/modes/input. Use libvterm screen cases.
