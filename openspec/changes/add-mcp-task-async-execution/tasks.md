## 1. Protocol Scaffolding

- [x] 1.1 Add task-related protocol constants, request parsing, initialize capability output, and
      `tools/list` async metadata.
- [x] 1.2 Add handlers for `tasks/get`, `tasks/result`, `tasks/cancel`, and `tasks/list` only when
      safe ownership rules are implemented.

## 2. Task Runtime

- [x] 2.1 Add task status, record, registry, retention, and cancellation primitives.
- [x] 2.2 Bind task ownership to `MCP-Session-Id` where available and keep `taskId` opaque.

## 3. Task-Aware Tool Execution

- [x] 3.1 Keep the existing sync `tools/call` path intact when task augmentation is not requested.
- [x] 3.2 Add task-augmented `tools/call` handling for approved long-running tools and preserve the
      final result shape through `tasks/result`.
- [x] 3.3 Reject unsupported task usage with actionable errors instead of silently falling back.

## 4. Per-Task Progress And Compatibility Bridge

- [x] 4.1 Replace singleton progress ownership with task-scoped progress/state while preserving a
      focused projection for the EDT UI and `get_active_operation`.
- [x] 4.2 Keep the original `_meta.progressToken` valid throughout task-backed execution.

## 5. Scheduling And Tool Migration

- [x] 5.1 Add conflict-aware scheduling or explicit rejection for mutable long-running tasks.
- [x] 5.2 Migrate `update_database`, `clean_project`, and task-capable full `revalidate_objects`
      into the first task-enabled wave.
- [x] 5.3 Keep `debug_launch` sync-first and document its cleanup path separately.

## 6. Diagnostics, Docs, And Verification

- [x] 6.1 Improve heavy diagnostics through contract shaping instead of forcing task support in this
      rollout.
- [x] 6.2 Update `README.md` and tool metadata to describe task support, compatibility fallbacks,
      and client requirements honestly.
- [x] 6.3 Verify task lifecycle behavior, ownership, conflict control, and sync compatibility from
      real MCP clients.
