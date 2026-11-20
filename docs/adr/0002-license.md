# ADR-0002: MIT license

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** project owner
- **Supersedes:** none

## Context

`terminal-window` 2.x is MIT-licensed. This project is a new codebase that
links against it and may reuse documentation and build scaffolding from it.
The project is intended to be embeddable and redistributable without copyleft
obligations.

The libvterm test corpus is MIT-licensed and may be adapted with attribution.
The VT specifications themselves are not copied into the source tree.

## Decision

We will license this repository, including build scripts and documentation,
under the **MIT License**.

Rules:

1. All code is authored for this project. No `terminal-window` v1 source is
   copied; v1 was GPL-3.0.
2. libvterm test files may be adapted under MIT with the original copyright
   notice and attribution.
3. Inbound contributions are accepted under MIT (inbound = outbound).
4. The repository ships `LICENSE` with the MIT text.
5. Dependency licenses are recorded in the repository.
6. No `NOTICE` file is required.

## Consequences

### Positive

- Compatible with `terminal-window` and libvterm test reuse.
- No copyleft barrier for embedding the parser or app.
- Short, familiar license text.

### Negative

- MIT grants no express patent license or trademark rights.
- Redistributors must preserve the copyright and permission notice.

### Neutral

- The license does not affect binary compatibility.
- Documentation copied from `terminal-window` is already MIT.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Apache-2.0 | Adds patent/NOTICE/change-statement obligations this project does not need. |
| GPL-3.0 | Copyleft barrier; incompatible with the sibling project's permissive baseline. |
| No license | Not redistributable. |

## References

- MIT License: <https://opensource.org/license/mit>
- `terminal-window` ADR-0002.
