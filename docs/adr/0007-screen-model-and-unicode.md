# ADR-0007: Screen model and Unicode policy

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

A terminal cell can contain a base code point, combining marks, a width, a
foreground/background color, attributes, an underline color, and a hyperlink.
Most cells use only a base code point and colors. A Kotlin object per cell
would allocate heavily for scrollback and resize.

Unicode width and grapheme clustering are open-ended. Full UAX #29 clustering,
emoji ZWJ sequences, and bidi shaping are larger than this project's v1.

## Decision

We will store the grid as struct-of-arrays:

- `IntArray` primary code points.
- `ByteArray` widths and flags (`WIDE_CHAR`, `WIDE_CHAR_SPACER`,
  `LEADING_WIDE_CHAR_SPACER`, `WRAPLINE`).
- `IntArray` foreground/background colors in a tagged default/indexed/RGB
  representation.
- `IntArray` attribute bitmask.
- A side table for rare per-cell data: combining code points, underline color,
  hyperlink id. Cells reference the side table by index.
- Zero-width combining code points are capped at **9 per cell**; excess marks
  are dropped. This matches Alacritty's DoS bound.

Unicode policy:

- UTF-8 only.
- Width from an embedded East Asian Width table; ambiguous width defaults to 1
  and is configurable.
- Wide characters occupy two cells: the primary cell plus a
  `WIDE_CHAR_SPACER` continuation.
- Combining marks attach to the preceding base cell and do not advance the
  cursor.
- No full grapheme-cluster segmentation and no ZWJ emoji clustering in v1.
  A ZWJ sequence is stored as its code points; rendering may show separate
  glyphs.
- Invalid UTF-8 becomes U+FFFD.

Colors are kept as default/indexed/RGB until projection. Bold brightening is a
projection option, not a parser mutation.

## Consequences

### Positive

- Predictable memory: one cell is a few primitive arrays, not an object.
- Rare data does not tax every cell.
- Wide/continuation flags make overwrite and wrap behavior explicit.
- The zero-width cap bounds malicious output.

### Negative

- Index bookkeeping is more complex than a `Cell` object.
- Emoji ZWJ sequences and complex graphemes are not correct.
- East Asian width is a table that must be regenerated when Unicode changes.

### Neutral

- Full grapheme clustering is a later model change, not a rendering tweak.
- `terminal-window` cannot render combining marks or wide glyphs correctly in
  v1; projection keeps the base cell.

## Alternatives

| Alternative | Why rejected |
|---|---|
| `data class Cell` per cell | Allocation and pointer chasing at 80×24 and scrollback scale. |
| One `IntArray` per field only | Cannot express rare underline color/hyperlink without a side table. |
| Full UAX #29 now | Too large for v1; renderer cannot use it yet. |
| `wcwidth` per call | Platform/locale-dependent; need a stable embedded table. |
| Unlimited combining marks | DoS vector; no renderer benefit. |

## References

- Alacritty `alacritty_terminal/src/term/cell.rs` (`CellExtra`,
  `MAX_ZEROWIDTH_CHARS = 9`).
- libvterm `VTermScreenCell` (6 code points per cell).
- Unicode UAX #11 East Asian Width.
