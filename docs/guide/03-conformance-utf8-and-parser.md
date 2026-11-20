# 03 — Conformance, UTF-8, and parser

**Phase:** core · **Depends on:** 02 · **Blocks:** 04

## Goal

Turn arbitrary bytes into correct parser events, with split writes, malformed
input, OSC/DCS strings, and bounded memory. Establish the libvterm test DSL as
the parser/screen conformance harness.

## Context budget

Read: `GUIDE.md`, this file, `docs/vt-support.md` T0/T1a, ADR-0006, and the
libvterm test files named below. Do not read sibling sections. Expected output:
parser, UTF-8 decoder, event types, test harness, fixtures, fuzz seed.

## Deliverables

- `vt/src/main/kotlin/.../parser/Parser.kt`
- `vt/src/main/kotlin/.../parser/ParserEvent.kt`
- `vt/src/main/kotlin/.../text/Utf8Decoder.kt`
- `vt/src/test/kotlin/.../parser/*Test.kt`
- `vt/src/test/resources/libvterm/LICENSE` and adapted `.test` files.
- `vt/src/test/kotlin/.../conformance/LibVtermHarness.kt`
- Fuzz/property test for random and split byte streams.

## Steps

1. Port the libvterm `.test` DSL with its MIT notice. Support `INIT`, `UTF8`,
   `WANTPARSER`, `WANTSTATE`, `PUSH`, `RESET`, `text`, `control`, `escape`,
   `csi`, `output`, `putglyph`, `erase`, `moverect`, `?cursor`.
2. Implement the Williams state machine exactly: `ground`, `escape`,
   `escape_intermediate`, `csi_entry`, `csi_param`, `csi_intermediate`,
   `csi_ignore`, `dcs_entry`, `dcs_param`, `dcs_intermediate`,
   `dcs_passthrough`, `dcs_ignore`, `osc_string`, `sos_pm_apc_string`.
3. Implement actions: `print`, `execute`, `clear`, `collect`, `param`,
   `esc_dispatch`, `csi_dispatch`, `osc_start`, `osc_put`, `osc_end`, `hook`,
   `put`, `unhook`, `ignore`.
4. UTF-8: incremental decoder; invalid/truncated sequences become U+FFFD;
   split multibyte sequences across `PUSH` calls are preserved.
5. C0 inside CSI executes immediately; CAN/SUB cancel; ESC cancels and restarts;
   OSC ends on BEL or ST; DCS/APC/PM/SOS end on ST.
6. Enforce limits from `docs/vt-support.md`: 32 parameters, 10 digits,
   2 intermediates, 8192-byte OSC/DCS, 64 KiB response queue.
7. Unknown but well-formed sequences go to a fallback sink; no throw.
8. Run the parser subset of libvterm tests. Add split-write and fuzz tests.

## Acceptance

- [x] libvterm parser tests (`02parser`, split writes, C0-in-CSI, OSC, DCS)
      pass.
- [x] `PUSH "\e["` + `PUSH "31m"` dispatches one `Csi`, not text.
- [x] CAN/SUB cancel an in-progress sequence; ESC restarts.
- [x] OSC with BEL and with ST both terminate; unterminated OSC is capped.
- [x] DCS passthrough receives bytes and unhooks on ST; unknown DCS is ignored.
- [x] `0x9B` is not interpreted as CSI; it is UTF-8 input or U+FFFD.
- [x] Random bytes never throw and never allocate unbounded.
- [x] Limit tests truncate/ignore at the documented thresholds.

## Pitfalls

- Do not decode UTF-8 before C0/C1 handling; `print` bytes feed the decoder.
- Do not treat parameter `0` as always “1”; each CSI handler owns its default.
- Do not lose private markers `?`, `>`, `=`, `<`.
- Do not allocate a `String`/`ByteArray` per byte.
- Do not let OSC/DCS accumulate without a cap.
- Do not copy libvterm tests without the MIT notice.
- Upstream `02parser.test` contains 8-bit C1 cases. The adapted fixture removes
them because this project rejects 8-bit C1; bytes `0x80`–`0xFF` are text.
- Only `OSC` accepts `BEL` as a terminator. `DCS`, `APC`, `PM`, and `SOS`
terminate on `ST` only.
- A cancelled string never emits a `final` chunk. Consumers must not commit a
string until they receive `final = true`.

## Plan seed

> Plan section 03 only: implement the Williams parser, incremental UTF-8, parser
> events, bounded OSC/DCS/CSI handling, and a libvterm-compatible conformance
> harness. T0/T1a sequences only; no screen mutation. Include split-write and
> fuzz tests.
