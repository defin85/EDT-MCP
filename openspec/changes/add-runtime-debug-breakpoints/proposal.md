# Change: Add runtime debug breakpoint management

## Why

Agents can now launch and drive a suspended EDT runtime debug session, but they still depend on a
human to place the BSL breakpoint that creates the suspension point. This keeps the runtime-debug
loop partially manual: an MCP client can inspect and step execution only after the EDT UI has already
prepared the breakpoint.

## What Changes

- Add a supported MCP surface for BSL line breakpoint management in EDT workspace resources:
  listing, setting, and removing runtime debug breakpoints.
- Resolve breakpoint requests through project and source-location inputs that map back to EDT
  workspace files, not through opaque UI selections.
- Use a fail-closed bridge over the actual EDT/Eclipse breakpoint model: implementation must first
  live-discover the marker type, attributes, and creation path used by EDT BSL line breakpoints.
- Integrate breakpoint management with the existing runtime debug tools so an agent can set a
  breakpoint, launch/debug a runtime, wait for suspension, inspect stack/variables, and step/resume.
- Exclude conditional breakpoints, watch expressions, expression evaluation, value mutation, and
  arbitrary non-BSL Eclipse debug models from this rollout.

## Impact

- Affected specs: `runtime-debug-control`
- Affected code: runtime debug integration, breakpoint bridge/tool implementations, tool registry,
  focused Tycho/JUnit coverage, README and generated agent docs
- Validation: strict OpenSpec checks, unit coverage for source resolution and fail-closed
  breakpoint preconditions, and live EDT verification that an MCP-created BSL breakpoint suspends a
  runtime session that can then be inspected by the existing debug tools
