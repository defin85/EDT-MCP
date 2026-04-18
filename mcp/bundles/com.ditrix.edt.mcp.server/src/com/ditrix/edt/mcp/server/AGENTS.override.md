# Server Runtime Override

Эти инструкции действуют для core plugin subtree `.../server/`.

## Start Here

- `docs/agent/long-running-ops.md` — для `update_database`, progress, tasks и compatibility fallback
- `docs/agent/generated-tool-catalog.md` — для tool -> implementation/test mapping
- `README.md` — для внешнего MCP contract и client-facing behaviour

## Local Boundaries

- `protocol/` и `protocol/jsonrpc/` — transport contract
- `tools/impl/` — MCP tool logic
- `progress/`, `tasks/`, `ui/` — long-running runtime path
- `McpServer.java`, `McpServerStartup.java`, `Activator.java` — top-level orchestration and lifecycle

## Verify

- Fast compile: `mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server -am -DskipTests compile`
- Broad gate: `mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C`
- Если change затрагивает task/progress runtime contract и live EDT недоступен, фиксируй runtime evidence gap явно
