# Change: Add task wait and result tools

## Why

The server already has task-backed execution and JSON-RPC task lifecycle methods, but many agents
mostly plan from `tools/list`. They can see that a long-running operation was accepted, yet the
follow-up path to task completion is easy to miss or confuse with the original tool result. That
keeps async flows fragile, especially for database updates, extension apply, YAxUnit runs, and
future bounded debug helpers.

## What Changes

- Add explicit tool wrappers for task lifecycle access: `list_tasks`, `get_task_result`, and
  `wait_task`.
- Make `wait_task` a bounded helper that polls only until terminal state or timeout and then returns
  the latest task snapshot.
- Normalize task/operation evidence fields so agents can distinguish accepted, active, terminal,
  cancelled, failed, and timed-out outcomes consistently.
- Keep session ownership and retained result semantics aligned with the existing JSON-RPC task
  lifecycle APIs.

## Impact

- Affected specs: `long-running-operations`
- Affected code: task registry/tool wrappers, task result DTOs, tool metadata, generated agent docs,
  focused Tycho/JUnit coverage
- Compatibility: additive tool surface over existing task APIs; existing JSON-RPC task clients
  continue to work
