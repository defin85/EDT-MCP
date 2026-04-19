# Change: Make long-running mutable tools async-first by default

## Why

The repository already supports task-backed execution, but long-running tools still expose a
parallel synchronous path by default. In practice this keeps producing misleading agent behavior:
clients that ignore or do not send `task` remain on the synchronous path and hit wrapper or
transport timeouts even though the server can execute the same operation safely through the task
registry.

The project no longer treats synchronous invocation as the compatibility baseline for these tools.
Task-backed execution should become the only operational contract for the verified long-running
mutable tools in this rollout.

## What Changes

- Make `update_database`, `clean_project`, and full-project `revalidate_objects` async-first by
  default.
- Promote bare `tools/call` requests for these tools into task-backed execution unconditionally.
- Remove the legacy synchronous path for these tools instead of keeping an opt-in escape hatch.
- Update tool discovery and README guidance so clients stop expecting a final synchronous payload
  from bare calls without misrepresenting MCP `execution.taskSupport` semantics.
- Preserve `get_active_operation` and progress notifications as the compatibility observation
  surface for task-backed and detached execution.

## Impact

- Affected specs: `long-running-operations`
- Affected code: `McpProtocolHandler`, task-discovery metadata, long-running tool descriptions,
  README, E2E/runtime verification
- Breaking change: clients that currently expect a final synchronous payload from bare
  `tools/call` on the affected tools will start receiving task creation metadata with no legacy
  synchronous override
