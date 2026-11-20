# VT support matrix

**Status:** normative for `1.0.0`. T0 and T1a are v1; T1b/T2 are roadmap; T3 is
rejected.

`CSI` means the 7-bit form `ESC [`. The 8-bit C1 form `0x9B` is **not**
accepted; bytes `0x80`–`0xFF` are UTF-8 input. `ST` means `ESC \`.

## Tiers

| Tier | Meaning | v1 |
|---|---|---|
| T0 | Protocol core | yes |
| T1a | Practical subset for shells/editors | yes |
| T1b | Practical extensions | no |
| T2 | Later | no |
| T3 | Rejected | never |

## T0 — protocol core

### C0 controls

| Sequence | Effect |
|---|---|
| `BEL` | Bell event; no audio in v1 |
| `BS` | Cursor left one column |
| `HT` | Next tab stop |
| `LF`, `VT`, `FF` | Index; `LNM` adds carriage return |
| `CR` | Column 0 |
| `SO` / `SI` | Select G1 / G0 |
| `ESC` | Enter escape state; cancels a sequence/string |
| `CAN`, `SUB` | Cancel the current sequence |
| `NUL`, `DEL` | Ignored |

### ESC sequences

| Sequence | Effect |
|---|---|
| `ESC 7` / `ESC 8` | DECSC / DECRC |
| `ESC D` / `ESC E` | IND / NEL |
| `ESC H` / `ESC M` | HTS / RI |
| `ESC c` | RIS |
| `ESC # 8` | DECALN |
| `ESC ( B`, `ESC ( 0`, `ESC ( A` | Designate G0: US-ASCII, DEC Special Graphics, UK |
| `ESC ) B`, `ESC ) 0`, `ESC ) A` | Designate G1 |
| `ESC =` / `ESC >` | DECKPAM / DECKPNM |
| `ESC \` | ST |

### CSI cursor and editing

| Sequence | Effect |
|---|---|
| `CSI Ps A/B/C/D` | Cursor up/down/forward/back |
| `CSI Ps E/F` | Next/previous line |
| `CSI Ps G` / `CSI Ps \`` | Column absolute |
| `CSI Ps a/j/k` | Horizontal/vertical position relative |
| `CSI Ps I/Z` | Cursor forward/backward tab |
| `CSI Pl ; Pc H` / `f` | Cursor position |
| `CSI Ps d` / `e` | Row absolute / relative |
| `CSI Ps @` | Insert characters |
| `CSI Ps P` | Delete characters |
| `CSI Ps L` / `M` | Insert/delete lines |
| `CSI Ps S` / `T` | Scroll up/down |
| `CSI Ps X` | Erase characters |
| `CSI Ps J` | Erase in display: 0 cursor→end, 1 start→cursor, 2 all |
| `CSI Ps K` | Erase in line: 0 cursor→end, 1 start→cursor, 2 all |
| `CSI Ps b` | REP (repeat preceding character) |
| `CSI Pt ; Pb r` | DECSTBM |
| `CSI Ps g` | TBC: 0 current, 3 all |

### SGR

| Parameters | Effect |
|---|---|
| `0` | Reset all attributes/colors |
| `1`, `2` | Bold, faint |
| `3`, `23` | Italic on/off |
| `4`, `21`, `24` | Single/double underline on/off |
| `5`, `6`, `25` | Blink on/off; not rendered |
| `7`, `27` | Reverse on/off |
| `8`, `28` | Invisible on/off |
| `9`, `29` | Strikethrough on/off |
| `22` | Normal intensity |
| `30`–`37`, `39` | Foreground 0–7, default |
| `40`–`47`, `49` | Background 0–7, default |

### Modes

| Mode | Effect |
|---|---|
| `CSI 4 h/l` | IRM insert/replace |
| `CSI 20 h/l` | LNM newline mode |
| `CSI ? 1 h/l` | DECCKM cursor keys |
| `CSI ? 5 h/l` | DECSCNM reverse screen |
| `CSI ? 6 h/l` | DECOM origin |
| `CSI ? 7 h/l` | DECAWM autowrap |
| `CSI ? 25 h/l` | DECTCEM cursor visible |
| `CSI ? 66 h/l` | DECNKM keypad |
| `CSI ? 67 h/l` | DECBKM backspace |

Unsupported private modes are accepted and ignored.

### Text and charset

- UTF-8 only; invalid sequences become U+FFFD.
- US-ASCII, UK, DEC Special Graphics.
- G0/G1 via `SO`/`SI`.
- East Asian width table; ambiguous width defaults to 1.
- Combining marks attach to the preceding base cell; cap 9.
- No full grapheme clustering or ZWJ emoji clustering in v1.

## T1a — v1 practical subset

### Screen and editing

| Sequence | Effect |
|---|---|
| `CSI ? 47 h/l` | Alternate screen |
| `CSI ? 1047 h/l` | Alternate screen |
| `CSI ? 1048 h/l` | Save/restore cursor |
| `CSI ? 1049 h/l` | Save cursor, clear alt screen, switch |
| `CSI ? 12 h/l` | Cursor blink flag stored; not rendered |
| `CSI ? 25 h/l` | Show/hide cursor |
| `CSI Ps q` | DECSCUSR shape stored; block cursor rendered |
| `CSI ! p` | DECSTR |
| `CSI Ps $ p` | DECRQM (ANSI mode) |
| `CSI ? Ps $ p` | DECRQM (DEC private mode) |

Unsupported private modes are reported by `DECRQM` as not recognized (`0`);
supported modes report set (`1`) or reset (`2`).

### Color and style

- 16-color SGR is T0; add `90`–`97`, `100`–`107` bright colors.
- `38;5;n`, `48;5;n`, `38;2;r;g;b`, `48;2;r;g;b` indexed/RGB.
- Colon forms: `38:5:n`, `48:5:n`, `38:2::r:g:b`, `38:2:r:g:b`,
  `48:2::r:g:b`, `48:2:r:g:b`.
- `4:0`–`4:5` underline styles stored; not rendered.
- `SGR 58`, `58:5:n`, `58:2::r:g:b`, `59` underline color stored.
- Bold brightening is a projection option, default on.

### OSC

| Sequence | Effect |
|---|---|
| `OSC 0 ; title ST/BEL` | Icon + window title |
| `OSC 1 ; title ST/BEL` | Icon title |
| `OSC 2 ; title ST/BEL` | Window title |
| `OSC 104 ST/BEL` | Reset palette (T1b when palette set exists) |

Title length is capped at 1024 bytes; excess is truncated. No response.

### Mouse

| Mode | Effect |
|---|---|
| `CSI ? 9 h/l` | X10 press-only |
| `CSI ? 1000 h/l` | Press/release |
| `CSI ? 1002 h/l` | Button-motion |
| `CSI ? 1003 h/l` | Any-motion |
| `CSI ? 1006 h/l` | SGR encoding |

Legacy X10 byte encoding is supported for 9/1000/1002/1003. SGR
`CSI < b ; x ; y M/m` is the preferred encoding. Coordinates are 1-based.
Wheel events use buttons 64/65. Motion uses the `+32` flag.

### Focus and paste

| Sequence | Effect |
|---|---|
| `CSI ? 1004 h/l` | Focus reporting |
| `CSI I` / `CSI O` | Focus in/out |
| `CSI ? 2004 h/l` | Bracketed paste |
| `CSI ? 2026 h/l` | Synchronized output |

### Keyboard

- Legacy key encoding is the default.
- `modifyOtherKeys` levels 1–2.
- Kitty flags: `1` disambiguate, `2` event types, `8` report all keys.
- Kitty query `CSI ? u` → `CSI ? flags u`.
- Kitty push `CSI > flags u`, pop `CSI < n u`, set `CSI = flags ; mode u`.
- Kitty flags `4` (alternate keys) and `16` (associated text) are T1b.

### Responses

| Query | Response |
|---|---|
| `CSI 5 n` | `CSI 0 n` |
| `CSI 6 n` | `CSI row ; col R` |
| `CSI c` / `CSI 0 c` | `CSI ? 1 ; 2 c` (conservative VT100+AVO) |
| `CSI Ps $ p` | `CSI Ps ; Pm $ y` |
| `CSI ? Ps $ p` | `CSI ? Ps ; Pm $ y` |
| `DCS $ q m ST` | `DCS 1 $ r 0 m ST` (current SGR) |
| `DCS $ q r ST` | `DCS 1 $ r Pt ; Pb r ST` |
| `DCS $ q SP q ST` | `DCS 1 $ r Ps SP q ST` (DECSCUSR) |
| invalid DECRQSS | `DCS 0 $ r ST` |

DA1 is deliberately conservative. It does not advertise sixel, locator, or
protected-cell erase. T1b/T2 may revise the response when those features are
claimed.

## T1b — roadmap

- `OSC 4`/`5` palette set/query, `OSC 104` reset, palette stack.
- `OSC 10`/`11`/`12` dynamic colors, `OSC 110`/`111`/`112`/`117`/`119`.
- `OSC 8` hyperlinks; `OSC 52` clipboard, off by default; `OSC 133`; `OSC 7`.
- `OSC 9`/`99`/`777` notifications, opt-in.
- `XTGETTCAP` / `XTSETTCAP`; `DA2`, `DA3`, `XTVERSION`.
- `DECRQSS` additional selectors.
- Underline style/color rendering; RGBA parse (`38:6`, `48:6`, `58:6`).
- Mouse `1005`, `1015`, `1016`; alternate scroll `1007`.
- `modifyOtherKeys` level 3 and `formatOtherKeys`.
- Kitty alternate keys, associated text, release/repeat for all keys.
- Meta modes `1034`, `1036`, `1039`.

## T2 — later

- `DECLRMM` (`?69`), `DECSLRM`, `DECIC`, `DECDC`.
- `DECCOLM` (`?3`) with real resize, `DECNCSM` (`?95`).
- `REP` completeness and mode-query completeness.
- Scrollback, selection, copy/paste, search, reflow (product features).
- Theme/cursor/bell configuration.
- Native-image packaging, if viable.

## T3 — rejected

- VT52 (`DECANM` reset).
- `DECDWL`, `DECDHL`, `DECSWL`.
- Tektronix, ReGIS, Sixel, kitty graphics, iTerm2 images.
- DEC Locator (`DECELR`).
- Printing/media copy.
- Bidi, shaping, ligatures.
- National Replacement Character sets beyond UK/DEC Special Graphics.
- 8-bit C1 controls.
- Fonts/ligatures.

## Limits

These are normative for v1:

| Limit | Value |
|---|---:|
| CSI parameters per sequence | 32 |
| Digits per parameter | 10 |
| Intermediate bytes per sequence | 2 |
| OSC/DCS payload | 8192 bytes |
| Combining code points per cell | 9 |
| Title text | 1024 bytes |
| Query response queue | 64 KiB |

Exceeding a parser limit enters an ignore state or truncates the payload; it
never allocates without bound.

## Security

- Parser never throws on arbitrary bytes.
- OSC 52 is off by default and not in v1.
- Hyperlinks are not opened automatically; T1b must cap URL length.
- DCS handlers are a fixed allowlist; unknown DCS is consumed and ignored.
- Image protocols are consumed and ignored; no decoding.
- Title text is truncated and never interpreted as markup.

## References

- ECMA-48, 5th edition (1991).
- DEC VT100 User Guide, chapter 3.
- xterm `ctlseqs`, patch #411.
- kitty keyboard protocol.
- foot `foot-ctlseqs(7)`.
- WezTerm escape sequence reference.
