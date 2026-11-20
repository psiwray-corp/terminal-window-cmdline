# Standalone release runbook

`terminal-window-cmdline` ships as a platform-specific JVM application
distribution. It is not published to Maven Central; `:vt` and `:pty` are
internal modules.

## Artifacts

CI runs the unit, GL, PTY, and integration tests on every push and pull
request. Distribution archives are built and uploaded only for tags matching
`v*`.

| Host | Archive |
|---|---|
| Linux x64 | `terminal-window-cmdline-linux-x64-<version>.zip` / `.tar` |
| Linux arm64 | `terminal-window-cmdline-linux-arm64-<version>.zip` / `.tar` |
| macOS x64 | `terminal-window-cmdline-macos-x64-<version>.zip` / `.tar` |
| macOS arm64 | `terminal-window-cmdline-macos-arm64-<version>.zip` / `.tar` |
| Windows x64 | `terminal-window-cmdline-windows-x64-<version>.zip` / `.tar` |
| Windows arm64 | `terminal-window-cmdline-windows-arm64-<version>.zip` / `.tar` |

The current CI matrix builds Linux x64, macOS arm64, and Windows x64. Other
combinations are produced when the corresponding runner is added.

Each archive contains:

- `bin/terminal-window-cmdline` or `bin/terminal-window-cmdline.bat`;
- `lib/terminal-window-cmdline-<version>.jar`;
- runtime dependencies and the host LWJGL native classifiers;
- no tests, benchmarks, or debug-only profile.

## Pre-flight

1. Confirm `version` in `gradle.properties` is the release snapshot
   (`1.0.0-SNAPSHOT` for this release).
2. Confirm `CHANGELOG.md` has the release section.
3. Run `./gradlew build` and `bash ci/integration-test.sh` on the target host.
4. Confirm the GitHub Actions build, GL, and PTY jobs are green.
5. Download the distribution artifacts from the CI run.

## Prepare

From a clean `master` with `1.0.0-SNAPSHOT`:

```bash
scripts/release.sh 1.0.0
```

The script creates `release/1.0`, commits `1.0.0` with a signed commit, creates
the signed `v1.0.0` tag, bumps the release branch to `1.0.1-SNAPSHOT`, and
bumps `master` to `1.1.0-SNAPSHOT`. It does not push. Use `--dry-run` to print
the steps without changing the tree.

## Verify a distribution

```bash
unzip terminal-window-cmdline-linux-x64-1.0.0.zip
terminal-window-cmdline-linux-x64-1.0.0/bin/terminal-window-cmdline --version
```

On a desktop Linux host, run the launcher with a short command and confirm the
window closes after the child exits:

```bash
terminal-window-cmdline-linux-x64-1.0.0/bin/terminal-window-cmdline --pipe -- sh -c 'printf "release-check\n"'
```

Check that the launcher contains `--enable-native-access=ALL-UNNAMED`; on macOS
it must also contain `-XstartOnFirstThread`.

## Publish

1. Push the release branch and the signed `v<version>` tag only after the
   release commit is final.
2. Create the forge release.
3. Attach every platform `.zip` and `.tar`.
4. Attach `SHA256SUMS` generated from the archives.
5. Link the release from `README.md` or the repository release page.

There is no Maven Central upload, artifact-signing key, or Central Portal
token in CI. Release commits and tags are signed locally by the release
scripts. If library publication is added later, it is a separate decision and
requires its own ADR and release machinery.
