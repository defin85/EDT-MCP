## 1. Baseline And Scope

- [x] 1.1 Confirm the current sync-progress baseline in source and record the explicit non-goals
      for this rollout (`tasks/*` deferred, `update_database` first).

## 2. Shared Progress Core

- [x] 2.1 Add the shared progress snapshot, event history, reporter, and EDT monitor bridge for
      supported synchronous long-running operations.
- [x] 2.2 Add focused active-operation ownership in the server runtime for the initial sync model.

## 3. Request Context And Tool Instrumentation

- [x] 3.1 Extend request parsing/context handling so long-running sync tools can read
      `_meta.progressToken` and related execution context.
- [x] 3.2 Instrument `update_database` and its infobase synchronization callbacks with explicit
      stage/message updates.

## 4. UI And Transport Surface

- [x] 4.1 Update `McpStatusContribution` to render tool, stage/detail, elapsed time, and honest
      progress information from the shared snapshot.
- [x] 4.2 Add session-aware `notifications/progress` delivery for synchronous long-running calls
      without breaking the final sync response path.

## 5. Compatibility Bridge And Docs

- [x] 5.1 Add or complete `get_active_operation` as the compatibility polling snapshot for focused
      sync work.
- [x] 5.2 Update `README.md` and verification notes for EDT UI progress, progress notifications,
      and fallback polling.
- [x] 5.3 Verify a real long `update_database` run in the EDT UI and from an MCP client with and
      without `_meta.progressToken`.
