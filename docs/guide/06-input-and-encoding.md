# 06 — Input and encoding

**Phase:** core · **Depends on:** 05 · **Blocks:** 07

## Goal

Map neutral key, mouse, focus, and paste events to the bytes a child expects,
using the terminal modes the child enabled. Legacy encoding is the default;
kitty and `modifyOtherKeys` are opt-in.

## Context budget

Read: `GUIDE.md`, this file, ADR-0008, `docs/vt-support.md` T1a input, libvterm
`src/keyboard.c`, kitty keyboard protocol, and xterm mouse sections. Do not read
sibling sections. Expected output: input types, keymap, encoder, tests.

## Deliverables

- `.../input/Key.kt`, `.../input/Modifiers.kt`, `.../input/MouseButton.kt`
- `.../input/InputEvent.kt`, `.../input/Action.kt`
- `.../input/InputEncoder.kt`, `.../input/LegacyKeymap.kt`
- `.../input/KittyKeyboard.kt`, `.../input/ModifyOtherKeys.kt`
- `vt/src/test/kotlin/.../input/*Test.kt`

## Steps

1. Define neutral input types. Do not import `terminal-window` or GLFW.
2. Legacy text: printable `CharEvent` → UTF-8; Ctrl mapping `@A–Z[\]^_?` →
   C0/DEL; Alt → ESC prefix; Shift handled by the text.
3. Special keys: arrows/Home/End CSI or SS3 per DECCKM; F1–F4 SS3 P–S; F5–F12
   `CSI 15~` … `24~`; Insert/Delete/PageUp/PageDown; keypad numeric/application
   per DECKPAM/DECNKM; Backspace DEL/BS per DECBKM.
4. Pairing policy: chords encode from `KeyEvent` and suppress the next
   `CharEvent`; text keys use `CharEvent`; IME uses `CharEvent`; legacy release
   is ignored.
5. Mouse: encode only when a mode is enabled; 1-based coordinates; X10/1000/
   1002/1003; SGR 1006; wheel 64/65; motion `+32`.
6. Focus: `CSI I`/`CSI O` when 1004 is set.
7. Bracketed paste: `CSI 200~` + text + `CSI 201~` when 2004 is set.
8. `modifyOtherKeys` levels 1–2: `CSI 27;m;code~`; level 3/format variants are
   T1b.
9. Kitty: flags 1/2/8; query/push/pop/set; event types `:1/:2/:3`; alternate
   keys/text are T1b.
10. Table-driven keymap; tests enumerate every key and modifier combination.
11. Add an empirical GLFW test for callback ordering, repeat, and Alt behavior;
    document the observed rule.

## Acceptance

- [x] Plain `a` sends `a`; `Ctrl+A` sends `0x01`; `Alt+a` sends `ESC a`.
- [x] Shift+Tab sends `CSI Z`; Tab sends `0x09`.
- [x] Arrows switch between `CSI` and `SS3` with DECCKM.
- [x] F1–F12, Insert/Delete/Page keys match terminfo-compatible sequences.
- [x] Keypad follows DECKPAM/DECNKM; Backspace follows DECBKM.
- [x] A chord does not also send its `CharEvent`.
- [x] Mouse is silent unless a mouse mode is enabled.
- [x] SGR mouse press/release/motion/wheel encodings are correct.
- [x] Focus and bracketed paste obey their modes.
- [x] Kitty flags/query/push/pop and event types round-trip.
- [x] `modifyOtherKeys` levels 1–2 round-trip; T1b variants are not claimed.

## GLFW pairing rule

The regression tests model the observed GLFW ordering: a press `KeyEvent`
arrives before its `CharEvent`; repeat is a `KeyEvent` with `Action.REPEAT`
and repeats the same bytes; release is a `KeyEvent` with `Action.RELEASE`.
The encoder suppresses exactly the next `CharEvent` after a `KeyEvent` that
emitted bytes. A new non-release `KeyEvent` clears any stale suppression, so a
special key without a character cannot swallow a later typed character. On
layouts where Alt is consumed by the OS and only a `CharEvent` arrives, the
text is sent as UTF-8 without an extra ESC prefix.

## Pitfalls

- GLFW emits `KeyEvent` before `CharEvent`; the suppression rule needs a test.
- Alt may arrive as `CharEvent` on some layouts; do not double-send.
- Ctrl+I/Ctrl+M are Tab/Enter in legacy; only disambiguation changes that.
- Keypad keys may also produce `CharEvent`; avoid double-send.
- Mouse coordinates are cells, not pixels, and are 1-based.
- Release events are not sent in legacy; do not invent them.
- Kitty/T1b must not change legacy output before the child opts in.

## Plan seed

> Plan section 06 only: implement neutral input types, legacy keymap, chord/
> CharEvent pairing, mouse/focus/paste, and kitty/modifyOtherKeys levels 1–2.
> No PTY or window adapter. Add exhaustive encoder tests.
