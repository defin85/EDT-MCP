# Change: Add runtime debug operator helpers

## Why

The runtime debug bridge can launch, inspect suspended stacks and variables, control execution, and
manage MCP-owned BSL breakpoints. The remaining agent workflow still requires manual stitching:
evaluate a quick expression, place a temporary breakpoint, continue execution, poll for suspend, and
clean up temporary state. That is the useful "warm" operator loop, but today it is spread across
several low-level calls.

## What Changes

- Add `evaluate_debug_expression` for bounded expression evaluation in a suspended frame.
- Add a one-shot run-to-breakpoint helper that creates or reuses a temporary MCP-owned breakpoint,
  starts or continues execution, waits for suspend or timeout, and returns stack/variable evidence.
- Add MCP-owned breakpoint cleanup so long debug sessions can remove temporary breakpoints without
  touching user-owned breakpoints.
- Keep all wait behavior bounded and explicit: no hidden background continuation after a tool
  response.

## Impact

- Affected specs: `runtime-debug-control`
- Affected code: runtime debug bridge, breakpoint ownership tracking, tool registry, tool metadata,
  focused Tycho/JUnit tests, README and generated agent docs
- Validation: strict OpenSpec checks, unit coverage for fail-closed preconditions and cleanup
  safety, and live EDT verification against a suspended runtime session
