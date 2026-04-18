## Context

The current repository line has already moved to a newer EDT generation. The 2024.2.5 port needs a
deliberate compatibility contract instead of a best-effort backport, because both the target
platform and the application update APIs differ from the current line.

## Goals / Non-Goals

- Goals:
  - produce a build that resolves and packages against the official EDT Ruby 2024.2 update site
  - preserve a minimal but honest supported surface on 2024.2.5
  - keep runtime tool behavior explicit where exact parity with newer EDT APIs is not possible
- Non-Goals:
  - preserve every newer EDT feature on the 2024.2 line
  - hide compatibility gaps behind silent fallbacks
  - define a cross-version abstraction layer for every EDT API difference

## Decisions

- Decision: target the official `ruby/2024.2` update site and remove install units that do not
  exist there.
  - Alternatives considered:
    - keep the newer target and attempt ad hoc dependency shims
    - search for a separate public 2024.2.5 p2 endpoint
  - Rationale: the official 2024.2 line is the stable public dependency source that matches the
    requested compatibility target.

- Decision: implement runtime compatibility through
  `IInfobaseSynchronizationManager` and `IInfobaseApplication` rather than trying to emulate the
  newer application update API surface.
  - Alternatives considered:
    - reflection-based compatibility shims for newer update methods
    - keeping 2026.1-only update flows and degrading at runtime
  - Rationale: the older infobase synchronization layer is the documented compatibility path in the
    target EDT line.

- Decision: support only the minimal runtime surface required for this port:
  server startup, baseline project/code tools, `get_applications`, `update_database`, and
  `debug_launch`.
  - Alternatives considered:
    - broad feature parity with the newer line in the first pass
  - Rationale: keeping the first compatibility line narrow reduces drift and makes manual
    verification feasible.

## Risks / Trade-offs

- The public dependency source is published as `2024.2`, not a distinct `2024.2.5` p2 line.
- `fullUpdate` may not map one-to-one onto the older synchronization API and may require an honest
  limitation in the tool result.
- Launch configuration internals may differ across EDT lines and require live verification.
- Additional package-range incompatibilities may surface once the target resolves.

## Migration Plan

1. Choose and document the compatibility base line for the port.
2. Retarget the build and resolve OSGi/package-level incompatibilities.
3. Replace the runtime tool calls that depend on newer application update APIs.
4. Rebuild the feature/repository packaging and verify the plugin in a real EDT 2024.2.5 runtime.
5. Update README/support notes so the supported EDT line and limitations are explicit.

## Open Questions

- Should the 2024.2.5 compatibility line remain a long-lived branch or a one-off maintenance cut?
- If `fullUpdate` cannot preserve newer semantics, should the tool degrade, map to another action,
  or return an explicit unsupported-mode result?
