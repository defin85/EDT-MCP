# Codex Setup

Root `AGENTS.md` остаётся каноническим policy source. Этот документ фиксирует только минимальный bootstrap для локальной работы.

## Prerequisites

- JDK 17
- Maven
- Python 3.12+ for `tests/e2e/run_e2e_tests.py`
- EDT 2025.2.0+ for runtime/manual/E2E validation

## Minimal Local Workflow

### Build / unit test

```bash
mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C
```

### Fast compile loop

```bash
mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server -am -DskipTests compile
```

### Runtime/E2E prerequisites

- Запусти EDT с установленным plugin.
- Открой `TestConfiguration`.
- Убедись, что MCP server отвечает на `/health`.

## Discovery Notes

- `README.md` — product-level capabilities and client setup.
- `tests/TESTING.md` — current local test taxonomy.
- `.claude/*.md` — recent architectural notes, useful before large changes.
- Repo-owned onboarding scripts:
  - `scripts/codex-onboard.sh` — quick environment + doc surface check
  - `scripts/verify_agent_surface.sh` — doc/reference drift check
  - `scripts/generate_agent_refs.py` — regenerate `docs/agent/generated-tool-catalog.md`
- Repo-scoped Codex defaults live in `.codex/config.toml`.
