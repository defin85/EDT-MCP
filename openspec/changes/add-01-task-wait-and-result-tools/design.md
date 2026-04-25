## Context

Task-backed execution is already present, and async-first tools return task creation metadata. The
gap is discoverability and operator ergonomics: an agent that sees only tool descriptions can easily
stop at "accepted" instead of retrieving the terminal result in the same MCP session.

The new surface should not create a second task model. It should be a tool-level adapter over the
existing lifecycle APIs with clearer, bounded behavior for agents.

## Goals / Non-Goals

- Goals:
  - expose task listing and result retrieval as normal MCP tools
  - provide a bounded wait helper for common agent workflows
  - normalize task/operation evidence fields across accepted and terminal outcomes
  - keep same-session ownership and retained-result rules intact
- Non-Goals:
  - replace the JSON-RPC `tasks/*` APIs
  - make wait helpers unbounded
  - re-execute original long-running operations when reading results
  - change scheduling or conflict rules for mutable tasks

## Decisions

- Decision: tool wrappers delegate to the existing task lifecycle registry.
  - `list_tasks` exposes task summaries visible to the current session.
  - `get_task_result` retrieves retained final payload or a current snapshot when the task is not
    terminal.
  - `wait_task` polls the same registry until terminal state or timeout.
  - Rationale: the server should have one authoritative task model.

- Decision: `wait_task` is bounded and returns timeout as a normal outcome.
  - A timeout includes the latest known task snapshot and polling hint.
  - The tool does not keep a background wait after returning.
  - Rationale: bounded helpers are useful for agents only if they cannot silently continue work
    after the caller has moved on.

- Decision: normalize operation evidence without hiding tool-specific result payloads.
  - The common envelope should include `taskId` or `operationId`, `state`, `startedAt`,
    `finishedAt`, `result`, `warnings`, and source tool metadata when available.
  - Tool-specific final payloads remain embedded or linked instead of being flattened away.
  - Rationale: clients need stable lifecycle fields and still need the original domain result.

## Risks / Trade-offs

- Tool wrappers duplicate a protocol concept already present in MCP task APIs, so docs must be
  clear that wrappers are convenience surfaces.
- Long waits can still hold request resources; `timeoutSeconds` needs conservative limits.
- Retained task results can include large reports, so wrappers need truncation or manifest pointers
  when payloads exceed practical tool result size.
- Same-session ownership must be preserved exactly; wrappers must not accidentally broaden task
  visibility.

## Implementation Sketch

1. Add task lifecycle tool classes over the existing task registry.
2. Define a normalized task/operation evidence DTO.
3. Implement bounded polling for `wait_task`.
4. Register tools with clear descriptions and task-support metadata.
5. Update README, static resources, and generated agent docs.
6. Add focused tests for session ownership, terminal result retrieval, timeout, and unknown task
   behavior.
