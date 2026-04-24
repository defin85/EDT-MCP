Beads execution: `EDTMCP-868` with phase tasks `EDTMCP-868.1` through `EDTMCP-868.4` and
concrete child tasks `EDTMCP-868.1.1` through `EDTMCP-868.4.6` mirroring this checklist.

## 1. Protocol Contract

- [x] 1.1 Confirm the MCP protocol version fields currently emitted by `initialize` and the exact
      supported shape for `tools/list` annotations and resource methods.
- [x] 1.2 Define a conservative annotation policy for read-only, mutating, destructive, idempotent,
      and open-world tools.
- [x] 1.3 Define the resource URI, name, description, MIME type, and content ownership policy for
      each initial capability/workflow resource.
- [x] 1.4 Define the additive `initialize.capabilities.resources` shape, explicitly excluding
      `subscribe` and `listChanged` until those handlers/notifications exist.
- [x] 1.5 Define the JSON-RPC error code and payload for unknown resource URIs, using resource
      not-found code `-32002`.

## 2. Runtime Discovery Implementation

- [x] 2.1 Extend the server-side tool abstraction with optional discovery metadata without breaking
      existing tool implementations.
- [x] 2.2 Serialize tool annotations in `tools/list` while preserving existing `execution.taskSupport`.
- [x] 2.3 Update YAxUnit and runtime-debug/breakpoint tool descriptions to include concise
      workflow-aware handoff hints.
- [x] 2.4 Add resource capability advertisement to `initialize`.
- [x] 2.5 Add a static MCP resource registry and JSON-RPC handlers for `resources/list` and
      `resources/read`.
- [x] 2.6 Register capability/workflow resources for YAxUnit runtime testing and runtime debug
      control without exposing live state through resources.
- [x] 2.7 Keep the first rollout limited to static resources; do not implement
      `resources/templates/list`, subscriptions, or list-changed notifications unless a later
      approved requirement adds them.

## 3. Documentation And Generated References

- [x] 3.1 Update README runtime capability documentation to point agents from tool catalog to
      resource catalog.
- [x] 3.2 Extend generated agent-facing catalog output to include annotations and registered
      resources.
- [x] 3.3 Add or update the repo-owned drift check so stale generated discovery documentation fails
      verification.

## 4. Verification

- [x] 4.1 Add focused Tycho tests for `initialize` resource capability advertisement.
- [x] 4.2 Add focused Tycho tests for `tools/list` annotation serialization.
- [x] 4.3 Add focused Tycho tests for `resources/list`, `resources/read`, and unknown-resource
      `-32002` handling.
- [x] 4.4 Run the smallest relevant Maven/Tycho test set for protocol discovery changes.
- [x] 4.5 Run `openspec validate add-mcp-tool-discovery-resources --strict --no-interactive`.
- [x] 4.6 After reinstalling the built plugin, live-check `initialize`, `tools/list`,
      `resources/list`, and
      `resources/read` against the running EDT MCP server.

Verification evidence:

- `mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server,tests/com.ditrix.edt.mcp.server.tests -am -Dtest=McpProtocolHandlerTest verify --batch-mode --no-transfer-progress` passed: 34 tests, 0 failures.
- `python3 scripts/generate_agent_refs.py --check` passed.
- `scripts/verify_agent_surface.sh` passed.
- `openspec validate add-mcp-tool-discovery-resources --strict --no-interactive` passed.
- `mvn -f mcp/pom.xml verify -DskipTests --batch-mode --no-transfer-progress` passed and rebuilt `mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site/` with bundle qualifier `202604241739`.
- Full `mvn -f mcp/pom.xml verify --batch-mode --no-transfer-progress` currently fails outside this change on `YaxUnitRuntimeAdapterCompatibilityTest.testBuildConfigJsonUsesLegacyMinimalShape`; discovery protocol tests pass.
- Live proof on `http://172.24.192.1:8766/mcp` passed after reinstall: `get_server_build_info`
  reports bundle `1.0.0.202604241739`; `initialize` advertises empty `resources` without
  `subscribe`/`listChanged`; `tools/list` returns 56 tools and 20 annotated tools; `resources/list`
  returns 7 static resources including extension/debug workflows; `resources/read` works for
  `edt-mcp://workflows/extension-apply`; unknown resource URI returns `-32002`.
- Note: `http://172.24.192.1:8765/mcp` is a separate older EDT process that reports bundle
  `1.0.0.202604241112`; live proof for this change uses port `8766`.
