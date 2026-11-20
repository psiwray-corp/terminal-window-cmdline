# Compatibility policy

This document defines what “compatible” means for the
`terminal-window-cmdline` application from `1.0.0`. The `terminal-vt` and
`terminal-pty` modules are internal and are not published.

## Versioning

The application uses `MAJOR.MINOR.PATCH` versions:

| Change | Version |
|---|---|
| Breaking CLI or contract change | `MAJOR+1.0.0` |
| Additive CLI behavior | `1.MINOR+1.0` within 1.x |
| Bug fix, docs, performance, no contract change | `1.MINOR.PATCH+1` |

The first release is `1.0.0`. Development builds are `1.0.0-SNAPSHOT` and are
not published as releases.

**Stable surface:** from `1.0.0`, the documented CLI and application contract
remain compatible within the 1.x line. The internal module APIs are not part
of that promise and may change without notice.

## Covered surface

The standalone application distribution is covered:

| Surface | Guarantee |
|---|---|
| Distribution | `terminal-window-cmdline-<os>-<arch>` archives |
| Package roots | `com.psiwray.organization.terminal_window_cmdline.{vt,pty,app}` |
| CLI | Flags, defaults, validation, and exit behavior |
| Threading contract | Owner-thread screen, reader/writer/watcher roles |
| Process contract | Pipe vs PTY/ConPTY capabilities; `isTty` meaning |
| VT support tiers | T0/T1a behavior in `docs/vt-support.md` |
| Configuration | CLI flags and validation rules |

`internal` declarations are not covered. They may change in any release.

## Excluded surface

- Rendered pixels, glyph shapes, font metrics, and driver output.
- Frame timing, startup time, and allocation counts, provided the documented
  limits hold.
- Diagnostic text and exception messages.
- Platform-native library versions and kernels, beyond the documented minimum.
- Unsupported configurations: Windows 7/8, 8-bit C1, VT52, images, bidi,
  fonts/ligatures, native-image, Maven library publication.
- Behavior of T1b/T2/T3 sequences; they may be ignored or change before they
  are claimed.
- Java source compatibility beyond the JVM surface; Kotlin is authoritative.

## Kotlin-specific rules

1. Public data classes are frozen for a given minor; adding a component is a
   breaking change.
2. Default arguments are part of the ABI; adding a parameter changes the
   synthetic `$default` method.
3. Nullability changes are breaking.
4. Public sealed hierarchies are closed; adding a subtype breaks exhaustive
   `when`.
5. No public inline functions.
6. Value classes keep their underlying type.
7. Deprecation is not removal; removal waits for a minor bump.

## Threading and process contract

- The screen model is owner-thread confined.
- Reader/writer/watcher threads publish bytes or exit status only.
- The parser never mutates cells from a non-owner thread.
- Pipe mode has `isTty = false` and `resize()` returns false.
- PTY/ConPTY has `isTty = true`; resize and job-control support are platform
  dependent but must be reported honestly.
- A missing PTY/ConPTY is an error, not a silent pipe fallback.

## JVM and platform requirements

| Requirement | Value |
|---|---|
| JVM | 25 |
| Kotlin | 2.4.21 (consumers may use a newer 2.x compiler) |
| Linux | glibc 2.26+ for `POSIX_SPAWN_SETSID` |
| macOS | versions supported by JDK 25 |
| Windows | Windows 10 1809+ for ConPTY |
| Native access | `--enable-native-access=ALL-UNNAMED` |
| Window | `terminal-window:2.0.0`, OpenGL 3.3 core, host LWJGL natives |

## Deprecation

1. Deprecate with `@Deprecated` at `WARNING` and a `ReplaceWith` expression.
2. Keep the declaration for at least one minor release.
3. Remove only in the next major release.
4. Deprecation does not change the binary signature.

## Experimental APIs

Any API marked `@RequiresOptIn` is excluded from compatibility guarantees and
may change or disappear in a minor release.

## Verification

- `ApiSurfaceTest` inventories public packages and rejects LWJGL/platform types
  in `:vt`/`:pty` public signatures.
- Parser and screen conformance tests run against the libvterm corpus.
- Thread-affinity tests exercise the owner/reader/writer boundary.
- PTY/ConPTY integration tests run on Linux, macOS, and Windows CI.
- `jar --describe-module` checks the automatic module names.

## v1 known gaps

- No scrollback, selection, or clipboard UI.
- No underline/italic/strikethrough rendering; no bar/underline text cursor.
- No 8-bit C1.
- No images, bidi, shaping, fonts/ligatures.
- T1b/T2 sequences are not claimed and may be ignored.
- Native-image is not supported.

## Related

[VT support](vt-support.md) · [Architecture](architecture.md) ·
[Roadmap](roadmap.md)
