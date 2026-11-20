# ADR-0011: terminal-window projection

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

`terminal-window:2.0.0` renders a `TerminalMatrix` whose cells contain one code
point, one ARGB foreground, and one ARGB background. It has no per-cell style
bits and no text-cursor draw path. The cursor API is the OS mouse cursor, not
the text insertion point.

The owner explicitly does not want to change `terminal-window` for this
project. The VT model still needs full style information for future renderers
and for correct semantics (reverse, invisible, wide characters, selection).

## Decision

We will keep full fidelity in `:vt` and project lossily to `TerminalMatrix`:

| VT feature | Projection |
|---|---|
| Reverse video | Swap foreground/background |
| Bold | Brighten indexed colors 0–7; otherwise leave |
| Dim | Blend toward background or leave |
| Invisible | Draw background only |
| Underline / italic / strikethrough | Dropped |
| Underline style/color | Stored in `:vt`; dropped |
| Wide character | Primary glyph in first cell, blank continuation |
| Combining marks | Base glyph only |
| Hyperlink | Dropped |
| Text cursor | Reverse the cursor cell (block cursor) |
| Selection | Reverse selected cells |

Rules:

1. No `terminal-window` source or API changes in v1.
2. Projection is the only place that knows both models.
3. Projection reads a `ScreenView`, never the parser's mutable state.
4. Cursor projection uses reverse video; if the cell is already reversed, the
   two reverses cancel.
5. The projection contract is tested with a matrix snapshot, not pixels.
6. If `terminal-window` later gains style bits and a text cursor, the
   projection can become lossless without changing `:vt`.

## Consequences

### Positive

- v1 ships without a second release train.
- The VT model is future-proof for styles and cursor shapes.
- Projection is a small, testable adapter.

### Negative

- `vim`, `less`, and `git diff` lose underline/italic/strikethrough.
- The text cursor is block-only.
- Wide glyphs and combining marks may render imperfectly with a `.twf` font.

### Neutral

- The loss is documented in the README and `docs/vt-support.md`.
- A future `terminal-window` extension is a cross-project desideratum, not a
  promise.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Change `terminal-window` first | Not wanted for v1; expands scope across two projects. |
| Drop styles from the VT model | Would require reshaping the model later and break semantics. |
| Render styles with custom glyphs | `.twf` has no italic/underline variants; not a real solution. |
| Draw a cursor as a separate GL overlay | Needs `terminal-window` API changes. |

## References

- `terminal-window` `TerminalMatrix`, `TerminalWindow` API.
- ADR-0007 (screen model).
- `docs/architecture.md`.
