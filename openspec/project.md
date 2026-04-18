# Project Context

## Purpose
`EDT-MCP` is an Eclipse/1C:EDT plugin that exposes local EDT workspace capabilities over MCP so AI assistants can inspect projects, read and modify code/metadata, run diagnostic queries, manage applications, and interact with long-running EDT operations through a stable JSON-RPC/HTTP surface.

## Tech Stack
- Java 17
- Eclipse RCP / OSGi / 1C:EDT plugin APIs
- Maven + Tycho for build, test and update-site packaging
- Python for HTTP E2E test runner
- MCP Protocol 2025-11-25 over HTTP/SSE

## Project Conventions

### Code Style

- Production code and UI strings remain in English.
- Follow existing Java/Eclipse style in the touched package instead of reformatting unrelated code.
- Prefer small, targeted changes over broad refactors.
- Keep protocol names, JSON fields and tool identifiers backward-compatible unless an approved change says otherwise.

### Architecture Patterns

- Main implementation lives in `mcp/bundles/com.ditrix.edt.mcp.server/`.
- The plugin is structured around protocol dispatch, tool abstractions, EDT integration handlers, progress/tasks runtime, and UI/status bar surfaces.
- Packaging is split into bundle, test fragment, feature and repository modules under `mcp/`.
- Runtime-oriented changes should preserve the boundary between protocol transport, tool execution, and EDT/UI integration.

### Testing Strategy

- Start with the smallest relevant gate.
- Canonical build/test command is `mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C`.
- For fast feedback on Java-only changes, use `mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server -am -DskipTests compile`.
- Runtime/E2E validation uses `tests/e2e/run_e2e_tests.py` against a live EDT instance with `TestConfiguration` loaded.
- If a change affects live EDT behaviour and runtime validation is not available, report that evidence gap explicitly.

### Git Workflow

- GitHub workflows target `master`, so treat `master` as the default integration branch unless repository practice changes.
- Do not rewrite or discard unrelated user changes in a dirty worktree.
- For planning work, keep OpenSpec intent and Beads execution state synchronized with code reality.

## Domain Context

- The plugin operates in the 1C:EDT ecosystem, so many behaviours depend on Eclipse plugin lifecycle, EDT workspace/project types, and 1C metadata/runtime semantics.
- Repository capabilities span diagnostics, code browsing/search, query validation, content assist, metadata refactoring, form screenshots, application management, progress reporting and experimental task-based execution.
- `TestConfiguration/` is the canonical lightweight fixture for runtime checks.

## Important Constraints

- Full runtime validation requires a real EDT installation and a running plugin; CI cannot fully emulate every EDT scenario.
- Build and unit tests must remain compatible with Tycho/Eclipse test runtime.
- Changes to MCP protocol surface must consider existing clients documented in `README.md`.
- Plugin metadata, feature packaging and update-site assembly must stay consistent across `bundles/`, `features/` and `repositories/`.

## External Dependencies

- 1C:EDT 2025.2.0+ APIs and runtime
- Eclipse/OSGi platform services
- GitHub Actions workflows for build/release/update-site delivery
- MCP clients such as Claude Code, Cursor, Copilot and other HTTP/SSE consumers
