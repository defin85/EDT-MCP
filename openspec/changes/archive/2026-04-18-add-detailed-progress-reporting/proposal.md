# Change: Add detailed progress reporting for long-running sync operations

## Why

Long EDT operations currently provide too little information to both the EDT UI and MCP clients.
Agents need better stage-aware feedback during blocking calls, while the EDT status bar needs a
real operation model instead of only a tool name and elapsed time. This rollout should improve the
shared progress surface without coupling the first step to full MCP Tasks support.

## What Changes

- Add a shared runtime progress state, reporter, and monitor bridge for supported long-running sync
  operations.
- Enrich the EDT status bar and tooltip with real stage/detail/progress information.
- Emit `notifications/progress` from supported synchronous calls when the client supplied
  `_meta.progressToken` and a writable SSE stream exists.
- Keep `get_active_operation` as a compatibility bridge for clients that cannot consume progress
  notifications.
- Defer `tasks/*` and multi-task runtime semantics to the separate async change.

## Impact

- Affected specs: `long-running-operations`
- Affected code: `progress/`, `UpdateDatabaseTool`, `InfobaseSyncUtils`, `McpServer`,
  `McpProtocolHandler`, `JsonRpcRequest`, `McpStatusContribution`, `README.md`
- Validation: source-level checks plus a real long `update_database` run for EDT UI and MCP
  progress behavior
