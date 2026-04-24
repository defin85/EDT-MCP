## Context

The recent YAxUnit and runtime-debug work introduced end-to-end flows rather than isolated
single-call tools. The current runtime catalog exposes tool names, descriptions, input schemas, and
`execution.taskSupport`, but it does not expose MCP tool annotations or MCP resources. This makes it
harder for agents to distinguish read-only inspection from state-changing debug/test actions and
harder to discover the correct workflow chain.

MCP already separates action discovery from context discovery:

- `tools/list` is the low-latency model-facing catalog for actions.
- `resources/list` and `resources/read` are the better fit for longer capability notes, workflow
  guides, limitations, and handoff contracts.

## Goals / Non-Goals

- Goals:
  - keep tool descriptions short enough for `tools/list`
  - make the YAxUnit and runtime-debug tool chains obvious to an agent
  - expose standards-aligned tool annotations where the safety class is clear
  - add first-class MCP resources for workflow context instead of hiding long manuals in tool
    descriptions
  - keep generated docs and drift checks in sync with runtime registration
- Non-goals:
  - change YAxUnit execution semantics
  - change runtime debugger semantics
  - expose live debug/test state through resources when an existing tool is the authoritative live
    state surface
  - require all MCP clients to consume resources before they can call tools
  - add comprehensive `outputSchema` coverage for tool results; that is a separate structured-output
    rollout if needed

## Decisions

### Layered discovery model

- Decision: use three layers:
  1. concise workflow-aware tool descriptions
  2. tool annotations in `tools/list`
  3. separate MCP resources for longer capability/workflow guidance
- Rationale: descriptions are always visible but should stay compact; annotations help planner
  safety; resources carry durable workflow context without bloating every `tools/list` response.

### Tool annotations

- Decision: add an optional tool metadata object to the server-side tool abstraction and serialize
  supported MCP annotations in `tools/list`.
- Initial annotation policy:
  - read-only inspection tools SHOULD advertise `readOnlyHint: true`
  - state-changing tools SHOULD advertise `readOnlyHint: false`
  - non-destructive state-changing tools SHOULD explicitly set `destructiveHint: false` rather than
    relying on protocol defaults
  - destructive hints SHOULD stay conservative and be true when the tool can terminate, recycle, or
    remove runtime/user-visible state
  - idempotent hints SHOULD be used only when repeated identical calls are intentionally safe
  - `openWorldHint` SHOULD be false only for tools whose effects are confined to the local EDT
    workspace/server state; runtime, infobase, and external process tools should omit it or leave it
    true unless the boundary is proven closed
- Rationale: incorrect annotations are worse than missing annotations, especially for runtime debug
  and infobase/test actions.

### Workflow resources

- Decision: add a small in-process resource registry for static repo-owned resources and advertise
  the MCP `resources` capability during `initialize`.
- Initial resources:
  - `edt-mcp://capabilities/yaxunit-runtime-testing`
  - `edt-mcp://capabilities/runtime-debug-control`
  - `edt-mcp://workflows/yaxunit-warm-session`
  - `edt-mcp://workflows/runtime-debug-breakpoint`
  - `edt-mcp://capabilities/extension-lifecycle`
  - `edt-mcp://workflows/extension-apply`
  - `edt-mcp://limitations/runtime-testing-and-debug`
- Rationale: these resources describe which tools compose into an end-to-end flow, which IDs move
  between calls, what is async/task-backed, what is live state, and what is intentionally unsupported.
  Resource content must be packaged with the plugin runtime, not read from mutable repo checkout
  files that may not exist in an installed EDT bundle.

### Resource protocol shape

- Decision: implement only static `resources/list` and `resources/read` in this rollout.
- Consequences:
  - `initialize.capabilities.resources` should be an empty object unless `subscribe` or
    `listChanged` are actually implemented.
  - `resources/list` should accept the standard optional cursor parameter but may return the full
    static list and omit `nextCursor` while the list is small.
  - `resources/read` should return `contents` with `TextResourceContents` entries for markdown
    resources.
  - unknown resource URIs should return a JSON-RPC resource-not-found error with code `-32002`.
  - `edt-mcp://` custom URIs must stay RFC3986-compatible and be resolved only through exact
    registry matches.
- Rationale: this keeps the first resource surface standards-aligned and avoids implying support for
  subscriptions, templates, dynamic pagination, or arbitrary URI reads.

### Live state remains tool-owned

- Decision: resources MUST NOT become a second live state API for sessions, breakpoints, tasks, or
  reports.
- Rationale: live state already has precise tools (`list_debug_sessions`, `list_debug_breakpoints`,
  `get_test_session_status`, `get_test_run_report`, task APIs). Resource content should be stable
  guidance and may mention where to poll live state.

### Generated docs and drift checks

- Decision: extend the generated tool catalog process to include resource catalog and annotation
  metadata.
- Rationale: agent-facing docs are useful only if a repo-owned check can detect drift between
  registered runtime surface and checked-in guidance.

## Implementation Sketch

1. Add metadata classes for tool annotations.
2. Extend `IMcpTool` with default metadata methods so existing tools remain source-compatible.
3. Update `ToolsListResult` and `McpProtocolHandler` to serialize annotations when present.
4. Add resource capability advertisement to `InitializeResult`.
5. Add `McpResource`, `McpResourceRegistry`, and JSON-RPC handlers for `resources/list` and
   `resources/read`.
6. Register static workflow/capability resources from code-owned markdown or compact resource
   builders.
7. Update YAxUnit and debug tools with concise workflow-aware descriptions and conservative
   annotations.
8. Update README and generated `docs/agent/generated-tool-catalog.md`.
9. Add focused Tycho tests for `initialize` resource capability, `tools/list` annotations, and
   resource discovery/read payloads.

## Open Questions

- Should resources expose plain markdown only, or paired markdown plus structured JSON?
  - Initial recommendation: markdown first, with stable headings and tool identifiers; add JSON only
    when a client use case requires machine parsing.
- Do static resources need `resources/templates/list`?
  - Initial recommendation: no. Add templates only when a real parameterized resource is introduced.
