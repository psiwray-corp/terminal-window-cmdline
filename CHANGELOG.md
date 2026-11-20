# Changelog

All notable changes to this project will be documented here. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Initial documentation scaffold: `GUIDE.md`, ADRs, architecture,
  compatibility policy, VT support matrix, and section plan.
- Gradle 9.9.1 multi-project build with `:vt`, `:pty`, `:app`, and
  `:benchmarks`; Spotless, Dokka, test suites, and Linux/macOS/Windows CI.
- Williams DEC ANSI parser with bounded CSI/OSC/DCS handling, incremental
  UTF-8 decoder, parser events, and an adapted libvterm conformance harness.
- Struct-of-arrays screen model with Unicode width classification, wide and
  combining cells, damage tracking, cursor movement, margins, tab stops,
  insert/replace, erase, insert/delete, and scrolling; adapted libvterm screen
  conformance tests.
- SGR attributes and colors, xterm 256-color palette, underline styles and
  colors, ANSI/DEC modes, DECRQM, DSR/DA1, DECRQSS, titles, charsets, DECSTR,
  RIS, bounded query responses, and xterm alternate-screen switching with
  independent cursor, margins, and tab stops.
- Neutral key, modifier, mouse, focus, and paste input events; legacy keymap;
  `KeyEvent`/`CharEvent` pairing; X10/1000/1002/1003 and SGR mouse encoding;
  bracketed paste; kitty keyboard flags 1/2/8 with per-screen stacks; and
  `modifyOtherKeys` levels 1–2.
- Centralized VT input limits with boundary tests, deterministic parser fuzzing,
  parser/screen/projection benchmarks, and the measured performance report.
- Standalone per-platform application distributions; internal libraries are not
  published to Maven Central.

### Fixed

- macOS POSIX PTY startup avoids Darwin `posix_spawn` close-action failure and
  keeps the slave open until the child can produce output.
- ConPTY Ctrl-C delivery works when the host is attached to a windowless console
  or ignores Ctrl-C.

## [1.0.0] - not released

First release: T0 plus T1a, pipe mode, POSIX PTY, and Windows ConPTY.
