# ADR-0006: Parser architecture

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

Escape-sequence parsing must handle arbitrary, split, malformed, and
adversarial byte streams without throwing or allocating per byte. Ad-hoc
`when`-based parsers miss C0-inside-CSI, CAN/SUB cancellation, OSC termination,
and DCS passthrough.

The VT100/ECMA-48 grammar has a well-known complete state machine: Paul Flo
Williams' DEC ANSI parser, used as the reference model for VT220–VT525.

## Decision

We will use the Williams state machine as the parser core:

- States: `ground`, `escape`, `escape_intermediate`, `csi_entry`,
  `csi_param`, `csi_intermediate`, `csi_ignore`, `dcs_entry`, `dcs_param`,
  `dcs_intermediate`, `dcs_passthrough`, `dcs_ignore`, `osc_string`,
  `sos_pm_apc_string`.
- Actions: `print`, `execute`, `clear`, `collect`, `param`,
  `esc_dispatch`, `csi_dispatch`, `osc_start`, `osc_put`, `osc_end`,
  `hook`, `put`, `unhook`, `ignore`.
- A separate incremental UTF-8 decoder consumes bytes emitted by `print`.
  Bytes `0x80`–`0xFF` are text, not C1 controls.
- Parser output is a small event stream consumed by `TerminalState`; the
  parser does not mutate cells directly.
- Limits: fixed maximum parameters per CSI, fixed maximum OSC/DCS length,
  fixed maximum intermediate count. Exceeding a limit enters `csi_ignore` /
  `dcs_ignore` and consumes until the terminator.
- CAN and SUB cancel the current sequence; ESC always cancels and restarts.
- OSC terminates on BEL or ST; DCS/APC/PM/SOS terminate on ST.
- Unrecognized but well-formed sequences dispatch to a fallback sink and are
  otherwise ignored.

The parser is tested with the libvterm `.test` corpus (MIT, attribution) and a
fuzz corpus of random/split bytes.

## Consequences

### Positive

- Complete behavior for pathological input.
- Split writes, C0 inside CSI, and malformed sequences are covered by design.
- The parser is independent of screen and platform code.

### Negative

- More states than a “simple” parser; the table must be maintained carefully.
- The UTF-8 stage sits between `print` and the screen, so cursor movement can
  lag one incomplete code point.
- Limits must be chosen and documented.

### Neutral

- 8-bit C1 is not supported; the state machine's C1 transitions are not
  exposed to input.
- The event sink may later drive a different screen implementation.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Regex or split-on-ESC | Breaks on split writes, C0 inside CSI, OSC, DCS. |
| Decode UTF-8 first, then parse code points | Loses byte-level control semantics and C1 ambiguity. |
| `vte` crate port | Rust-specific; the state machine is the portable part. |
| Fully hand-written `when` parser | Incomplete error recovery; hard to test exhaustively. |

## References

- Paul Flo Williams, “A parser for DEC’s ANSI-compatible video terminals”:
  <https://vt100.net/emu/dec_ansi_parser>
- libvterm `src/parser.c` and `t/02parser.test` (MIT).
- ECMA-48 §5.4.
