# Agent Architecture Map

## Workspace Map

- `mcp/`
  Maven/Tycho parent for the plugin, test fragment, feature и update-site repository.
- `mcp/bundles/com.ditrix.edt.mcp.server/`
  Main plugin bundle with HTTP server, JSON-RPC/MCP protocol, tool registry, EDT integration, UI/status bar, tags/groups/refactoring support.
- `mcp/tests/com.ditrix.edt.mcp.server.tests/`
  Tycho Surefire/JUnit fragment for protocol DTOs, JSON helpers и базовые tool/result contracts.
- `mcp/features/com.ditrix.edt.mcp.server.feature/`
  Eclipse feature packaging.
- `mcp/repositories/com.ditrix.edt.mcp.server.repository/`
  Update-site assembly for release artifacts.
- `tests/e2e/`
  Python HTTP smoke/E2E runner against a live MCP endpoint.
- `TestConfiguration/`
  Minimal 1C test project loaded into EDT for runtime checks.

## Important Code Areas

- `.../server/McpServer.java`
  HTTP server, `/mcp`, `/health` и runtime orchestration.
- `.../server/McpServerStartup.java`, `Activator.java`
  Plugin lifecycle, EDT startup integration и service wiring.
- `.../server/protocol/`
  JSON-RPC dispatcher, protocol constants, request/response handling.
- `.../server/protocol/jsonrpc/`
  Transport DTOs for initialize/tools/tasks/progress payloads.
- `.../server/tools/`, `.../server/tools/impl/`
  Tool abstractions and concrete MCP tools.
- `.../server/progress/`
  Runtime progress model, notifications/progress, status updates.
- `.../server/tasks/`
  Experimental MCP Tasks lifecycle and registry logic.
- `.../server/ui/`
  Status bar contribution and user-interaction surfaces inside EDT.
- `.../server/groups/`, `.../server/tags/`
  Navigator organization, metadata grouping/tagging, related handlers and refactoring helpers.

## Build And Test Boundaries

- `mcp/pom.xml` — root build graph and module aggregation.
- `.github/workflows/build.yml` — canonical CI build gate: `mvn clean verify`.
- `.github/workflows/e2e-tests.yml` — runtime HTTP checks against a live server.
- `tests/TESTING.md` — human-readable test taxonomy and local commands.

## High-Friction Entry Points

- Protocol/method dispatch changes: `protocol/` + `protocol/jsonrpc/` + relevant tool.
- Long-running operations: `progress/`, `tasks/`, `ui/`, `McpServer.java`.
- Feature exposure in docs/client setup: `README.md` + `plugin.xml` + tests.
- EDT-specific behaviour: `Activator.java`, handlers, preference wiring, UI contributions.

## Local Overrides

- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/AGENTS.override.md`
- `mcp/tests/com.ditrix.edt.mcp.server.tests/AGENTS.override.md`
- `tests/e2e/AGENTS.override.md`
