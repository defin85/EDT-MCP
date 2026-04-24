# Change: Add MCP tool discovery resources

## Why

`EDT-MCP` now has materially larger runtime capabilities: YAxUnit cold/warm test execution,
persistent test sessions, runtime debug control, and MCP-owned BSL breakpoints. Today those
capabilities are discoverable mostly as separate tools. A capable agent can see the tool names, but
the runtime workflow, safety class, result IDs, and follow-up calls are not machine-discoverable
enough from `tools/list` alone.

## What Changes

- Enrich `tools/list` with standards-aligned discovery metadata for tool annotations and concise
  workflow-aware descriptions.
- Add MCP resource discovery and read support for repo-owned capability and workflow guides.
- Provide focused resources for the YAxUnit runtime testing contour and runtime debug/breakpoint
  contour.
- Update generated agent-facing references and drift checks so tool discovery and resource
  discovery stay aligned with runtime registration.

## Impact

- Affected specs: `mcp-server-core`, `agent-productivity-surface`
- Affected code: MCP protocol dispatch, JSON-RPC result DTOs, tool registry metadata, resource
  registry/handlers, generated docs tooling, README/agent docs
- Compatibility: additive MCP surface; existing `tools/list` and `tools/call` consumers continue to
  work if they ignore annotations and resources
