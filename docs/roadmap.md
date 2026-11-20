# Roadmap and deferred desiderata

**Status:** design notes captured during project discussion. Nothing here is
committed to `1.0.0`.

The normative v1 matrix is [`vt-support.md`](vt-support.md). This file records
what is deliberately deferred so the first release stays a barebones process
input/output handler.

## Release cut

- **v1 (`1.0.0`):** T0 + T1a; pipe mode, POSIX PTY, Windows ConPTY; no
  scrollback, selection, images, or `terminal-window` changes.
- **Post-v1:** T1b, then T2, in the order they become useful.
- **Never:** T3.

## T1b — practical extensions

### Colors and palette

- `OSC 4` palette set/query, `OSC 5` special colors, `OSC 104` reset.
- `OSC 10`/`11` default foreground/background, `OSC 12` cursor color,
  `OSC 110`/`111`/`112` resets, `OSC 117`/`119` selection colors.
- Palette stack: `XTPUSHCOLORS`, `XTPOPCOLORS`, `XTREPORTCOLORS`.
- RGBA parse for `38:6`, `48:6`, `58:6`; alpha may be ignored at render time.
- Render underline styles and underline color when `terminal-window` supports
  them.

### Clipboard, hyperlinks, shell integration

- `OSC 52` clipboard read/write, off by default, with explicit opt-in, size
  limits, and selection allowlist.
- `OSC 8` hyperlinks, URL length capped, never opened automatically.
- `OSC 133` prompt/command/output marks; `OSC 7` current working directory.
- Desktop notifications: `OSC 9`, `OSC 99`, `OSC 777`, opt-in only.

### Queries and terminfo

- `XTGETTCAP` (`DCS + q`) and `XTSETTCAP` (`DCS + p`), including `TN`, `Co`,
  and `RGB`.
- `DA2` (`CSI > c`), `DA3` (`CSI = c`), `XTVERSION` (`CSI > q`).
- Additional `DECRQSS` selectors beyond SGR/DECSTBM/DECSCUSR.
- `XTQMODKEYS`/`XTFMTKEYS` completeness.

### Input and mouse

- `modifyOtherKeys` level 3 and `formatOtherKeys` variants.
- Kitty alternate keys, associated text, and release/repeat for all keys.
- Mouse encodings: UTF-8 (`1005`), urxvt (`1015`), SGR pixels (`1016`).
- Alternate scroll (`1007`) behavior on the alternate screen.
- Configurable `Meta`/`Alt` legacy behavior (`1034`, `1036`, `1039`).

## T2 — later

### Screen and editing

- Left/right margins: `DECLRMM` (`CSI ? 69 h/l`), `DECSLRM`
  (`CSI Ps ; Ps s`), `DECIC`/`DECDC`.
- Character repetition completeness: `REP` (`CSI Ps b`).
- 80/132-column switching: `DECCOLM` (`CSI ? 3 h/l`) with a real
  window/grid resize, and `DECNCSM` (`CSI ? 95 h/l`).
- Soft reset and mode reporting completeness: `DECSTR`, `DECRQM` for every
  supported ANSI and DEC private mode.

### Product features

- Scrollback ring with a configurable line limit.
- Selection, copy/paste, and search.
- Reflow on resize, if measurements show it is worth the complexity.
- Configurable themes, cursor shape, and bell behavior.
- Native-image if the LWJGL/FFM combination proves viable.
- Installers via `jpackage`, if demand exists.

## Cross-project desideratum

`terminal-window` should eventually expose per-cell text style and a text
cursor draw path: underline style, italic, strikethrough, overline, and
block/bar/underline cursor shapes. This project keeps the full style model in
`:vt` and projects what the renderer supports. Changing `terminal-window` is
not part of v1.

## T3 — non-goals

- VT52 mode (`DECANM` reset).
- Double-width/height lines (`DECDWL`, `DECDHL`, `DECSWL`).
- Tektronix 4014, ReGIS, Sixel, kitty graphics, iTerm2 inline images.
- DEC Locator (`DECELR`).
- Printing and media copy.
- Bidi, Arabic/Hebrew shaping, and font shaping/ligatures.
- National Replacement Character sets beyond UK and DEC Special Graphics.
- 8-bit C1 controls; UTF-8 mode accepts 7-bit `ESC` forms only.

## Deferred decisions

- Whether `terminal-vt` and `terminal-pty` are published after 1.0.
- Whether a `jpackage` installer is added after the standalone distribution.
- Whether macOS keeps full job-control parity if the pure-FFM controlling
  terminal spike fails.
- Whether scrollback lives in `:vt` or only in `:app`.
- Whether a custom terminfo entry is worth shipping instead of
  `TERM=xterm-256color`.

## Related

[VT support](vt-support.md) · [Architecture](architecture.md) ·
[Compatibility](compatibility.md)
