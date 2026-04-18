## Context

The repository needs a richer progress surface for blocking long-running operations, especially
`update_database`. The shared state should serve both the EDT UI and MCP clients so the first
progress rollout does not create separate, inconsistent status models.

## Goals / Non-Goals

- Goals:
  - introduce one shared progress state for supported synchronous long-running operations
  - expose better progress detail in both the EDT UI and MCP notifications
  - preserve synchronous `tools/call` behavior while improving observability
- Non-Goals:
  - implement `tasks/*` in this change
  - migrate every tool to the new progress surface immediately
  - fabricate exact percentage values when EDT only exposes stage-level progress

## Decisions

- Decision: use one shared runtime state as the source of truth for EDT UI, progress notifications,
  and fallback polling.
  - Alternatives considered:
    - EDT-only UI progress
    - MCP-only progress notifications
    - separate state objects per surface
  - Rationale: one shared state avoids drift and prepares the codebase for later task-backed
    execution.

- Decision: keep the first implementation focused on synchronous long-running calls and postpone
  `tasks/*` to the dedicated async change.
  - Alternatives considered:
    - introduce task support immediately
    - keep progress purely local until tasks land
  - Rationale: a focused progress rollout is easier to verify and creates the shared concepts that
    the later task runtime can reuse.

- Decision: treat stage/message as first-class progress and only show exact percentage when EDT
  provides a trustworthy total.
  - Alternatives considered:
    - interpolate fake percentages for UI polish
  - Rationale: misleading progress is worse than indeterminate progress for long EDT operations.

## Risks / Trade-offs

- EDT callbacks may expose rich messages but no reliable totals.
- SSE notification delivery adds transport complexity and rate-limiting concerns.
- The later async change must either depend on this change or absorb equivalent shared progress
  foundations to avoid duplicate runtime models.

## Migration Plan

1. Add the shared runtime progress classes and wire `update_database`.
2. Enrich EDT UI rendering from the shared snapshot.
3. Parse `_meta.progressToken`, deliver `notifications/progress`, and keep sync final-result
   behavior intact.
4. Add `get_active_operation` as a documented compatibility bridge.
5. Validate the full sync flow in a real EDT runtime and from an MCP client.

## Open Questions

- Which progress updates should be rate-limited or coalesced to avoid noisy SSE output?
- Should `get_active_operation` expose recent event history from day one or only a minimal snapshot?
