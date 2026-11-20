# ADR-0008: Input encoding

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

`terminal-window` emits separate `KeyEvent` and `CharEvent` values. Text keys
produce both; Ctrl/Alt chords may produce only a `KeyEvent`; IME/dead keys may
produce only a `CharEvent`. The encoder must avoid sending both the character
and its escape sequence.

Legacy terminal input is ambiguous: `Ctrl+I` equals Tab, `Ctrl+M` equals Enter,
and Alt is often an ESC prefix. Kitty's keyboard protocol and xterm's
`modifyOtherKeys` provide disambiguation, but applications opt in.

## Decision

We will implement a stateful input encoder in `:vt` with a neutral input model:

- `Key`, `Modifiers`, `MouseButton`, `Action`, `MouseEncoding`.
- `InputEncoder.encode(event)` writes bytes to a sink; it never calls the PTY
  directly.
- Encoder state is updated by terminal mode changes: DECCKM, DECKPAM/DECNKM,
  DECBKM, modifyOtherKeys level/format, kitty flags and stack, mouse mode and
  encoding, focus reporting, bracketed paste, alternate screen.

Encoding policy:

1. Printable text with no Ctrl/Alt/Super and no kitty “report all keys” is sent
   from `CharEvent` as UTF-8.
2. Ctrl/Alt/Super chords are encoded from `KeyEvent`; the next `CharEvent` for
   that keystroke is suppressed.
3. Special keys are encoded from `KeyEvent`.
4. IME/dead-key text with no usable `KeyEvent` is sent from `CharEvent`.
5. Legacy repeat sends the same bytes as press; kitty with event types sends
   `:2`. Legacy release is ignored; kitty with event types sends `:3`.
6. Legacy first: arrows use CSI or SS3 per DECCKM, function keys use the
   terminfo-compatible sequences, keypad uses numeric/application mode,
   Backspace uses DEL or BS per DECBKM.
7. Kitty and modifyOtherKeys are opt-in by the child. They must never change
   legacy output before the child enables them.
8. Mouse is encoded only when a mouse mode is enabled; 1-based coordinates;
   SGR 1006 is the primary modern encoding, with X10/1000 legacy support.

The keymap is a table, not a chain of `if`s. Tests cover every key, modifier
combination, mode combination, and the `KeyEvent`/`CharEvent` pairing.

## Consequences

### Positive

- Text input respects the OS keyboard layout via `CharEvent`.
- Chords and special keys are deterministic and testable.
- Kitty/modifyOtherKeys can be added without changing the adapter.
- No double-send of character plus escape sequence.

### Negative

- The suppression rule depends on GLFW callback ordering and needs an
  empirical regression test.
- Legacy Alt/ESC encoding remains ambiguous by design.
- Kitty alternate keys/associated text are T1b; v1 is simpler.

### Neutral

- The encoder writes to a byte sink; the app owns the writer thread.
- `terminal-window` may later expose a unified key event, but the adapter can
  keep this policy.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Encode everything from `KeyEvent` | Loses keyboard-layout-specific text and IME. |
| Encode everything from `CharEvent` | Cannot represent arrows, function keys, Ctrl/Alt chords. |
| Always use kitty CSI-u | Breaks legacy applications. |
| Always use modifyOtherKeys | Not all applications use it; kitty is cleaner. |
| Per-key `if` chain | Untestable and easy to miss mode combinations. |

## References

- kitty keyboard protocol.
- xterm `ctlseqs` (modifyOtherKeys, mouse tracking).
- libvterm `src/keyboard.c`.
- `docs/vt-support.md`.
