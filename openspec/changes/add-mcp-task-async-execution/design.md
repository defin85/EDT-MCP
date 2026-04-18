## Context

The repository already advertises a modern MCP protocol version, but long-running tools still block
the original `tools/call` request. The async surface should align with MCP Tasks instead of adding
another custom status/result API. This change depends conceptually on the shared progress model
from `add-detailed-progress-reporting` or an equivalent implementation that provides task-safe
progress foundations.

## Goals / Non-Goals

- Goals:
  - support task-augmented execution for approved long-running tools
  - preserve synchronous behavior for clients that do not request tasks
  - replace single-operation truth with task-scoped progress, status, and retention
  - prevent unsafe parallel mutation through scheduler/conflict rules
- Non-Goals:
  - publish a custom async API outside MCP Tasks
  - migrate every slow read-only diagnostic into task mode
  - mark any tool as `taskSupport: required` in the first rollout

## Decisions

- Decision: use MCP Tasks as the public async model and keep `get_active_operation` only as a
  compatibility bridge.
  - Alternatives considered:
    - custom `start/status/result` tools
    - leaving long operations sync-only and relying on progress notifications
  - Rationale: MCP Tasks are the standards-aligned long-term contract and avoid protocol debt.

- Decision: keep sync and task-backed execution on the same underlying tool implementations where
  possible.
  - Alternatives considered:
    - separate async-only tool implementations
  - Rationale: shared tool logic reduces drift between sync and async result contracts.

- Decision: migrate only true background jobs in the first wave and keep heavy diagnostics sync.
  - Alternatives considered:
    - task-enable all slow tools
  - Rationale: mutable long-running jobs benefit from retention/cancellation, while diagnostics are
    often better served by paging and output shaping.

- Decision: scope task visibility to the owning session when possible and gate `tasks/list`
  advertisement on safe ownership rules.
  - Alternatives considered:
    - global task visibility for simplicity
  - Rationale: weak task scoping would leak cross-session activity and make async support unsafe.

## Risks / Trade-offs

- MCP task support is still experimental and may evolve.
- Weak task ownership would leak task metadata across sessions.
- Missing scheduler rules would turn current blocking conflicts into harder-to-debug background
  races.
- If the shared progress model diverges from the earlier progress change, the EDT UI and task
  runtime will drift again.

## Migration Plan

1. Add protocol scaffolding and task-aware tool metadata.
2. Add task registry, retention, cancellation, and ownership enforcement.
3. Make `tools/call` task-aware while keeping sync compatibility.
4. Replace single-operation progress truth with task-scoped progress and a focused compatibility
   projection.
5. Migrate the first-wave long-running tools and enforce conflict control.
6. Update docs and verify from both task-capable and sync-only clients.

## Open Questions

- Should conflicting mutable tasks be rejected immediately or queued with status messages in the
  first rollout?
- Is it safe to advertise `tasks.list` initially, or should it remain disabled until ownership is
  proven in real clients?
