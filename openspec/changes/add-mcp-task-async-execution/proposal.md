# Change: Add MCP task-based async execution for long-running tools

## Why

The repository still treats long-running work as blocking `tools/call` execution with one focused
operation slot. That is not a normal async contract for MCP clients. Long EDT jobs need task-based
execution, deferred result retrieval, explicit cancellation, and per-task progress while sync
clients continue to work during the migration.

## What Changes

- Add MCP task capability advertisement, task-aware tool metadata, and task lifecycle handlers.
- Add a dedicated task runtime with retention, ownership, cancellation, and conflict control.
- Migrate the first async wave of real background jobs: `update_database`, `clean_project`, and
  task-capable full `revalidate_objects`.
- Preserve sync compatibility, keep `get_active_operation` as a fallback bridge, and avoid a
  public custom async API.
- Treat heavy diagnostics and `debug_launch` separately instead of forcing them into the first
  task rollout.

## Impact

- Affected specs: `mcp-server-core`, `long-running-operations`
- Affected code: protocol handlers, task runtime, progress runtime, first-wave long-running tools,
  `README.md`, protocol/tool verification
- Validation: strict protocol checks, task lifecycle verification, concurrency/conflict checks, and
  at least one real MCP client that consumes task flows correctly
