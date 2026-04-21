## Context

`update_database`, `clean_project`, and full-project `revalidate_objects` already have verified
task-backed execution paths. The remaining operational problem is not server capability but client
selection: many wrappers still call these tools synchronously, then blame EDT-MCP for timeouts that
actually come from the legacy call mode.

The desired direction is to stop treating synchronous invocation as the primary contract for the
verified long-running mutable tools.

MCP 2025-11-25 still models task augmentation as requestor-driven. At tool level,
`execution.taskSupport: "required"` means the client must explicitly send `task`, and the server
must reject a non-task call. That means EDT-MCP cannot honestly advertise bare-call auto-promotion
as protocol-level `required` support.

## Goals / Non-Goals

- Goals:
  - make task-backed execution the default contract for the verified long-running mutable tools
  - remove the legacy synchronous contract for that tool set
  - make tool discovery and README wording honest about task-only behavior
- Non-Goals:
  - widen the async-default set beyond `update_database`, `clean_project`, and full-project
    `revalidate_objects`
  - introduce a second duplicate tool name for async variants

## Decisions

- Decision: affected tools become async-first even when the client omits `task`.
  - Rationale: the server already has the safer execution path; the default should match reality.

- Decision: the affected tools still advertise task semantics explicitly through discovery metadata.
  - Rationale: clients that do inspect `tools/list` should know they are expected to use task-based
    result retrieval.

- Decision: legacy synchronous execution is removed for the affected tool set in this rollout.
  - Alternatives considered:
    - keep sync as implicit default
    - keep sync behind a deprecated opt-in override
  - Rationale: implicit sync keeps reproducing timeouts, and keeping a hidden escape hatch would
    preserve the same ambiguity in wrappers and documentation.

- Decision: while bare-call auto-promotion is enabled, affected tools keep
  `execution.taskSupport: "optional"` and describe async-first runtime behavior through tool
  descriptions, README guidance, and any future EDT-MCP-specific discovery hint.
  - Rationale: MCP defines `required` as explicit task augmentation plus rejection of non-task
    invocation; using `required` for auto-promotion would contradict the spec and the current test
    semantics.

- Decision: follow-up `tasks/get`, `tasks/result`, and `tasks/cancel` calls for auto-promoted
  requests continue to rely on the same `MCP-Session-Id` that created the task.
  - Rationale: the current task registry is session-scoped, so making more calls task-backed by
    default also makes that session affinity part of the documented operational contract.

## Risks / Trade-offs

- Some existing wrappers may fail harder after this change because they do not understand
  `CreateTaskResult`.
- Because bare-call auto-promotion is an EDT-MCP extension on top of the MCP requestor-driven task
  model, discovery wording must make that deviation explicit for spec-aware clients.
- Tool discovery and README wording must stay synchronized with the actual promotion behavior, or
  the change will become more confusing rather than less.

## Migration Plan

1. Update the long-running-operation contract and docs to describe async-first behavior.
2. Keep `execution.taskSupport: "optional"` for the affected tools, but update discovery wording
   and tool descriptions to describe server-side auto-promotion and same-session task retrieval.
3. Promote bare calls into task-backed execution in protocol handling unconditionally.
4. Remove the legacy sync branch for the affected tools.
5. Re-run runtime/E2E checks for promoted async calls, including same-session task result polling.

## Open Questions

- Which wrappers or clients in the current ecosystem still assume a final synchronous payload from
  bare `tools/call` requests?
- Do we need an EDT-MCP-specific machine-readable discovery hint for bare-call auto-promotion in
  addition to description text and README guidance?
