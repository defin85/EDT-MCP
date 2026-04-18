## Context

Extension projects are distinct platform entities with different lifecycle rules from main
configuration projects. The repository currently detects extension natures in some places, but it
does not expose a project-kind model or enforce tool capability boundaries consistently.

## Goals / Non-Goals

- Goals:
  - distinguish configuration and extension projects explicitly at runtime
  - make read-only and write/refactor extension behavior deliberate instead of accidental
  - reject configuration-only runtime flows clearly on extension projects
- Non-Goals:
  - implement extension lifecycle tooling such as `.cfe` import/export or infobase attachment
  - overload `update_database`, `debug_launch`, or `get_applications` with extension semantics
  - silently broaden existing tool contracts without documenting the new shape

## Decisions

- Decision: introduce a shared `ProjectContext` model with project kind and capability hints.
  - Alternatives considered:
    - keep nature checks scattered across tools
    - treat extension support as a boolean flag
  - Rationale: shared resolution reduces duplicated logic and makes tool boundaries explicit.

- Decision: expose project kind and capability hints through `list_projects` before broad tool
  migration.
  - Alternatives considered:
    - keep project kind implicit and rely on tool failures
  - Rationale: agents should know whether a project is a configuration or extension before choosing
    a tool.

- Decision: keep runtime/application flows configuration-only until a dedicated extension lifecycle
  surface is approved.
  - Alternatives considered:
    - overload `update_database` or `debug_launch` with extension-specific behavior
  - Rationale: infobase runtime flows are a separate domain from extension metadata/refactoring
    flows and should not be mixed.

## Risks / Trade-offs

- EDT may expose extension metadata through the same configuration root in some cases and a
  different shape in others.
- Write and refactor flows may behave differently for native vs adopted extension objects.
- If capability checks are too coarse, valid extension read flows may be blocked unnecessarily.

## Migration Plan

1. Record the current extension behavior matrix from source and a real workspace.
2. Add the shared project-kind and capability resolver.
3. Make `list_projects` project-kind-aware.
4. Migrate read-only metadata/module flows to the shared project context.
5. Add guarded write/refactor support and explicit runtime rejections.
6. Update README/support notes with an honest extension matrix.

## Open Questions

- Should `get_configuration_properties` be broadened explicitly or split into a separate
  extension-aware tool?
- Which write/refactor tools need preview-first behavior before they can be treated as verified on
  extension projects?
