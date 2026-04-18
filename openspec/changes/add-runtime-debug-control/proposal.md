# Change: Add runtime debug control tools for agents

## Why

The repository can already launch a 1C runtime in debug mode through `debug_launch`, but it still
leaves the actual debugging workflow inside the EDT UI. MCP clients cannot inspect active debug
sessions, read stacks or variables, or issue basic step/resume commands. That makes runtime
debugging a manual handoff instead of an agent-usable capability.

## What Changes

- Add a supported MCP debug-control surface for EDT runtime sessions: discovery, stack inspection,
  variable inspection, and basic execution control.
- Keep `debug_launch` as backward-compatible launch orchestration and make created sessions
  discoverable through the new tools instead of changing the launch contract.
- Add snapshot-based external identifiers and explicit precondition/unsupported-capability errors so
  the debug surface remains stable for MCP clients.
- Exclude breakpoints, expression evaluation, and unrelated Eclipse debug models from the first
  rollout.

## Impact

- Affected specs: `runtime-debug-control`
- Affected code: runtime debug integration, tool registry and implementations, protocol DTOs,
  `README.md`, verification artifacts
- Validation: strict OpenSpec checks, focused unit coverage for snapshot/error handling, and live
  EDT verification against a real suspended runtime session
